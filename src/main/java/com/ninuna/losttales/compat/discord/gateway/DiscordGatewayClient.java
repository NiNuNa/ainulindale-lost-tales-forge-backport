package com.ninuna.losttales.compat.discord.gateway;

import com.google.gson.JsonObject;
import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The bot's Gateway connection: a thread that connects, identifies or
 * resumes, keeps the heartbeat, reads events until the server closes or
 * the link fails, and connects again with backoff — or stops for the
 * session when Discord's close code says the token or the intents are
 * wrong. The member intents are the exception: refused, they are dropped
 * and the bot connects again without them. Events are handed to a
 * {@link Listener} on this thread; the listener must not block. A second
 * thread runs the small HTTP jobs the listener hands back
 * ({@link #submit}), so an answer to Discord never holds the reader up.
 */
public final class DiscordGatewayClient extends Thread {

    /** What the connection reports. Called on the gateway thread. */
    public interface Listener {
        /** The gateway URL, from Discord; empty on failure. */
        String fetchGatewayUrl() throws IOException;

        void onReady(JsonObject ready, String sessionId);

        void onEvent(String name, JsonObject data);

        void onConnected();

        void onDisconnected();

        /** Discord refused the member intents; the bot goes on without them. */
        void onMembersRefused();
    }

    private static final long MIN_BACKOFF_MILLIS = 2000L;
    private static final long MAX_BACKOFF_MILLIS = 60000L;
    private static final long INVALID_SESSION_WAIT_MILLIS = 3000L;
    private static final int MAX_JOBS = 64;

    private final DiscordGatewayProtocol protocol;
    private final Listener listener;
    private final LinkedBlockingQueue<Runnable> jobs = new LinkedBlockingQueue<Runnable>(MAX_JOBS);
    private final Thread jobRunner;
    private volatile boolean running = true;
    private volatile DiscordWebSocket socket;
    private volatile Thread heartbeat;
    private volatile boolean connected;
    private volatile boolean fatal;
    private long backoffMillis = MIN_BACKOFF_MILLIS;
    private String gatewayUrl = "";

    /** {@code members} asks for the member intents as well. */
    public DiscordGatewayClient(String token, boolean members, Listener listener) {
        super("LostTales-Discord-Gateway");
        setDaemon(true);
        this.protocol = new DiscordGatewayProtocol(token, DiscordGatewayProtocol.INTENTS
                | (members ? DiscordGatewayProtocol.MEMBER_INTENTS : 0));
        this.listener = listener;
        this.jobRunner = new Thread("LostTales-Discord-Gateway-Jobs") {
            @Override
            public void run() {
                runJobs();
            }
        };
        this.jobRunner.setDaemon(true);
    }

    /** Whether a session is up and events are flowing. */
    public boolean isConnected() {
        return this.connected && !this.fatal;
    }

    /** Whether Discord refused the session for good this run. */
    public boolean isFatal() {
        return this.fatal;
    }

    /** Whether the bot hears who is in its servers and what they are doing. */
    public boolean followsMembers() {
        return (this.protocol.getIntents() & DiscordGatewayProtocol.MEMBER_INTENTS)
                == DiscordGatewayProtocol.MEMBER_INTENTS;
    }

    /**
     * Asks Discord for every member of a server, on the connection that
     * is open now; answers false when none is, or the ask did not go out.
     */
    public boolean requestMembers(String guildId) {
        DiscordWebSocket opened = this.socket;
        if (opened == null || opened.isClosed() || !followsMembers()) {
            return false;
        }
        try {
            opened.sendText(DiscordGatewayProtocol.requestMembersPayload(guildId));
            return true;
        } catch (IOException failure) {
            note("Could not ask Discord for a server's members: " + failure.getMessage());
            return false;
        }
    }

    /** Runs a short HTTP job off the reader thread; dropped when the queue is full. */
    public boolean submit(Runnable job) {
        return this.running && this.jobs.offer(job);
    }

    public void shutdown() {
        this.running = false;
        this.jobRunner.interrupt();
        closeSocket();
        interrupt();
    }

    @Override
    public synchronized void start() {
        this.jobRunner.start();
        super.start();
    }

    @Override
    public void run() {
        while (this.running && !this.fatal) {
            boolean resumed = false;
            try {
                String url = this.protocol.getResumeGatewayUrl();
                if (url.length() == 0) {
                    if (this.gatewayUrl.length() == 0) {
                        this.gatewayUrl = this.listener.fetchGatewayUrl();
                    }
                    url = this.gatewayUrl;
                }
                if (url == null || url.length() == 0) {
                    throw new IOException("Discord did not say where its gateway is");
                }
                resumed = session(url);
            } catch (IOException failure) {
                note("Discord gateway link failed: " + failure.getMessage());
            } catch (RuntimeException failure) {
                note("Discord gateway link failed: " + failure);
            } finally {
                stopHeartbeat();
                closeSocket();
                if (this.connected) {
                    this.connected = false;
                    this.listener.onDisconnected();
                }
            }
            if (!this.running || this.fatal) {
                break;
            }
            long wait = resumed ? MIN_BACKOFF_MILLIS : this.backoffMillis;
            this.backoffMillis = Math.min(MAX_BACKOFF_MILLIS, this.backoffMillis * 2L);
            try {
                Thread.sleep(wait);
            } catch (InterruptedException interrupted) {
                break;
            }
        }
    }

    /**
     * One connection, from handshake to close. Answers whether it ended
     * the way a resume can follow (a server close or reconnect request
     * with the session kept) rather than by a failure.
     */
    private boolean session(String url) throws IOException {
        String address = url + (url.indexOf('?') < 0 ? "/?" : "&") + "v=10&encoding=json";
        DiscordWebSocket opened = DiscordWebSocket.connect(URI.create(address));
        this.socket = opened;
        this.protocol.onConnected();
        while (this.running) {
            String text = opened.readText();
            if (text == null) {
                return closed(opened.getCloseCode());
            }
            for (DiscordGatewayProtocol.Action action : this.protocol.onPayload(text)) {
                if (!perform(action, opened)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Carries out one action; answers false when the connection is to be dropped. */
    private boolean perform(DiscordGatewayProtocol.Action action, DiscordWebSocket opened)
            throws IOException {
        switch (action.type) {
            case SEND:
                opened.sendText(action.payload);
                return true;
            case HEARTBEAT_EVERY:
                startHeartbeat(action.intervalMillis, opened);
                return true;
            case RECONNECT:
                if (!action.resume) {
                    this.protocol.forgetSession();
                    sleepQuietly(INVALID_SESSION_WAIT_MILLIS);
                }
                return false;
            case READY:
                this.backoffMillis = MIN_BACKOFF_MILLIS;
                this.connected = true;
                this.listener.onConnected();
                this.listener.onReady(action.data, this.protocol.getSessionId());
                return true;
            case EVENT:
                if ("RESUMED".equals(action.name)) {
                    this.backoffMillis = MIN_BACKOFF_MILLIS;
                    this.connected = true;
                    this.listener.onConnected();
                } else {
                    this.listener.onEvent(action.name, action.data);
                }
                return true;
            default:
                return true;
        }
    }

    /** What a server close means: a fatal code stops the client for the session. */
    private boolean closed(int code) {
        switch (code) {
            case 4004:
                fatal("Discord refused the bot's token on the gateway (close 4004); "
                        + "the gateway is off until the config is fixed and reloaded");
                return false;
            case 4010:
            case 4011:
            case 4012:
                fatal("Discord refused the gateway session (close " + code
                        + "); the gateway is off for this run");
                return false;
            case 4014:
                if (followsMembers()) {
                    // The member intents are the ones a server switches
                    // on for the member lists; without them the bridge
                    // still relays.
                    this.protocol.dropIntents(DiscordGatewayProtocol.MEMBER_INTENTS);
                    FMLLog.warning("[%s] Discord refused the Server Members or Presence "
                            + "intent (close 4014): switch both on for the bot in the "
                            + "Discord developer portal, or turn memberList off; the "
                            + "gateway goes on without Discord members",
                            LostTalesMetaData.MOD_ID);
                    this.listener.onMembersRefused();
                    return false;
                }
                fatal("Discord refused the bot's intents (close 4014): enable the "
                        + "Message Content intent on the application; the gateway is "
                        + "off until then");
                return false;
            case 4013:
                fatal("Discord refused the bot's intents (close 4013); the gateway "
                        + "is off for this run");
                return false;
            default:
                if (code == 4007 || code == 4009) {
                    // Bad sequence or timed out: the session is gone.
                    this.protocol.forgetSession();
                }
                note("Discord gateway closed (" + code + "); reconnecting");
                return code >= 0 && code < 4000 || code == 4000 || code == 4001
                        || code == 4002 || code == 4003 || code == 4005 || code == 4008;
        }
    }

    private void startHeartbeat(final long intervalMillis, final DiscordWebSocket opened) {
        stopHeartbeat();
        Thread beat = new Thread("LostTales-Discord-Gateway-Heartbeat") {
            @Override
            public void run() {
                // The first beat after a random part of the interval, as
                // the Gateway asks, then one per interval.
                long wait = (long)(intervalMillis * Math.random());
                while (running && !opened.isClosed() && !isInterrupted()) {
                    try {
                        Thread.sleep(Math.max(1000L, wait));
                    } catch (InterruptedException interrupted) {
                        return;
                    }
                    wait = intervalMillis;
                    try {
                        List<DiscordGatewayProtocol.Action> actions = protocol.onHeartbeatDue();
                        for (DiscordGatewayProtocol.Action action : actions) {
                            if (action.type == DiscordGatewayProtocol.Action.Type.SEND) {
                                opened.sendText(action.payload);
                            } else if (action.type
                                    == DiscordGatewayProtocol.Action.Type.RECONNECT) {
                                note("Discord gateway missed a heartbeat acknowledgement; "
                                        + "reconnecting");
                                opened.close();
                                return;
                            }
                        }
                    } catch (IOException failure) {
                        opened.close();
                        return;
                    }
                }
            }
        };
        beat.setDaemon(true);
        this.heartbeat = beat;
        beat.start();
    }

    private void stopHeartbeat() {
        Thread beat = this.heartbeat;
        this.heartbeat = null;
        if (beat != null) {
            beat.interrupt();
        }
    }

    private void closeSocket() {
        DiscordWebSocket opened = this.socket;
        this.socket = null;
        if (opened != null) {
            opened.close();
        }
    }

    private void runJobs() {
        while (this.running) {
            try {
                Runnable job = this.jobs.take();
                try {
                    job.run();
                } catch (RuntimeException failure) {
                    note("A Discord gateway job failed: " + failure);
                }
            } catch (InterruptedException interrupted) {
                return;
            }
        }
    }

    private void fatal(String message) {
        this.fatal = true;
        FMLLog.severe("[%s] %s", LostTalesMetaData.MOD_ID, message);
    }

    private static void note(String message) {
        FMLLog.info("[%s] %s", LostTalesMetaData.MOD_ID, message);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
