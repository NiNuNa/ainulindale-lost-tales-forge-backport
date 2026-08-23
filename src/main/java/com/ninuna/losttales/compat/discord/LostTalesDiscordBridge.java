package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The server's own Discord bridge, no library behind it: a worker
 * thread polls one Discord channel for new messages through the REST
 * API and posts the game's lines to a webhook, while the server thread
 * only ever touches two bounded queues. Discord messages are handed to
 * {@link LostTalesChatService#sendFromDiscord} on the server tick, so
 * they reach players through the same packet as every other line; game
 * lines are queued here by the chat service and posted off-thread.
 *
 * <p>Everything fails closed. A bad token or a missing permission stops
 * the inbound side for the session with one severe log line; a rate
 * limit is honoured for exactly the time Discord asks; any other failure
 * backs off, doubling up to a minute, and is logged once on the way
 * down and once on the way back. The webhook's own posts come back as
 * bot messages and are ignored, so nothing echoes. The token and the
 * webhook URL are never logged.</p>
 */
public final class LostTalesDiscordBridge {
    private static final LostTalesDiscordBridge INSTANCE =
            new LostTalesDiscordBridge();
    private static final int MAX_QUEUED_INBOUND = 256;
    private static final int MAX_QUEUED_OUTBOUND = 256;
    private static final int MAX_INBOUND_PER_TICK = 8;
    private static final long MIN_BACKOFF_MILLIS = 5000L;
    private static final long MAX_BACKOFF_MILLIS = 60000L;
    private static final long STOP_JOIN_MILLIS = 2000L;

    private final Queue<Inbound> inbound = new ConcurrentLinkedQueue<Inbound>();
    private final AtomicInteger inboundCount = new AtomicInteger();
    private final Queue<Outbound> outbound = new ConcurrentLinkedQueue<Outbound>();
    private final AtomicInteger outboundCount = new AtomicInteger();
    private volatile Worker worker;
    private boolean registered;

    private LostTalesDiscordBridge() {}

    public static LostTalesDiscordBridge getInstance() {
        return INSTANCE;
    }

    /** Starts the worker when the config enables the bridge; idempotent. */
    public synchronized void start() {
        stop();
        if (!LostTalesConfig.discordEnabled) {
            return;
        }
        boolean reads = LostTalesConfig.discordRelayDiscordChat
                && LostTalesConfig.discordBotToken.trim().length() > 0
                && LostTalesConfig.discordChannelId.trim().length() > 0;
        boolean writes = LostTalesConfig.discordRelayGameChat
                && LostTalesConfig.discordWebhookUrl.trim().length() > 0;
        if (!reads && !writes) {
            FMLLog.warning("[%s] Discord bridge is enabled but has neither a "
                    + "bot token and channel id to read with nor a webhook "
                    + "URL to post to; nothing will be relayed",
                    LostTalesMetaData.MOD_ID);
            return;
        }
        if (!this.registered) {
            FMLCommonHandler.instance().bus().register(this);
            this.registered = true;
        }
        Worker started = new Worker(reads, writes);
        this.worker = started;
        started.start();
        FMLLog.info("[%s] Discord bridge started (%s)", LostTalesMetaData.MOD_ID,
                reads && writes ? "both ways" : reads ? "Discord to game"
                        : "game to Discord");
    }

    /** Stops the worker and forgets everything queued. */
    public synchronized void stop() {
        Worker running = this.worker;
        this.worker = null;
        if (running != null) {
            running.shutdown();
            try {
                running.join(STOP_JOIN_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        this.inbound.clear();
        this.inboundCount.set(0);
        this.outbound.clear();
        this.outboundCount.set(0);
    }

    public boolean isRunning() {
        Worker running = this.worker;
        return running != null && running.isAlive();
    }

    /**
     * Queues a game line for Discord; the worker posts it. Dropped when
     * the bridge is off, posting is off, or the queue is full.
     */
    public void relay(String username, String avatarUrl, String message) {
        Worker running = this.worker;
        if (running == null || !running.writes || message == null
                || message.length() == 0) {
            return;
        }
        if (this.outboundCount.get() >= MAX_QUEUED_OUTBOUND) {
            return;
        }
        this.outboundCount.incrementAndGet();
        this.outbound.add(new Outbound(username, avatarUrl, message));
    }

    /** Delivers queued Discord messages on the server thread, a few per tick. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.START
                || this.worker == null) {
            return;
        }
        int delivered = 0;
        Inbound message;
        while (delivered < MAX_INBOUND_PER_TICK
                && (message = this.inbound.poll()) != null) {
            this.inboundCount.decrementAndGet();
            try {
                LostTalesChatService.sendFromDiscord(message.name,
                        message.text);
            } catch (RuntimeException exception) {
                FMLLog.warning("[%s] Could not deliver a Discord message: %s",
                        LostTalesMetaData.MOD_ID, exception.toString());
            }
            delivered++;
        }
    }

    private void enqueueInbound(String name, String text) {
        if (this.inboundCount.get() >= MAX_QUEUED_INBOUND) {
            return;
        }
        this.inboundCount.incrementAndGet();
        this.inbound.add(new Inbound(name, text));
    }

    private static final class Inbound {
        final String name;
        final String text;

        Inbound(String name, String text) {
            this.name = name;
            this.text = text;
        }
    }

    private static final class Outbound {
        final String username;
        final String avatarUrl;
        final String message;

        Outbound(String username, String avatarUrl, String message) {
            this.username = username;
            this.avatarUrl = avatarUrl;
            this.message = message;
        }
    }

    /** The polling and posting thread; one per started bridge. */
    private final class Worker extends Thread {
        final boolean reads;
        final boolean writes;
        private volatile boolean running = true;
        /** The newest message id seen; empty until the channel is primed. */
        private String after = "";
        private boolean primed;
        private boolean readingDisabled;
        private boolean healthy = true;
        private long backoffMillis = MIN_BACKOFF_MILLIS;

        Worker(boolean reads, boolean writes) {
            super("LostTales-Discord");
            setDaemon(true);
            this.reads = reads;
            this.writes = writes;
        }

        void shutdown() {
            this.running = false;
            interrupt();
        }

        @Override
        public void run() {
            while (this.running) {
                long sleepMillis = Math.max(2L, Math.min(60L,
                        LostTalesConfig.discordPollIntervalSeconds)) * 1000L;
                try {
                    if (this.reads && !this.readingDisabled) {
                        poll();
                    }
                    if (this.writes) {
                        flushOutbound();
                    }
                    recovered();
                } catch (RateLimited limited) {
                    sleepMillis = Math.max(sleepMillis, limited.retryAfterMillis);
                } catch (IOException exception) {
                    sleepMillis = failed(exception.toString());
                } catch (RuntimeException exception) {
                    sleepMillis = failed(exception.toString());
                }
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException interrupted) {
                    return;
                }
            }
        }

        /**
         * Reads what arrived since the last look. The first successful
         * read only learns the newest id, so history before the server
         * started is never replayed into the game.
         */
        private void poll() throws IOException {
            String token = LostTalesConfig.discordBotToken.trim();
            String channel = LostTalesConfig.discordChannelId.trim();
            DiscordHttp.Reply reply = DiscordHttp.getMessages(token, channel,
                    this.primed ? this.after : "",
                    this.primed ? DiscordJson.PAGE_SIZE : 1);
            if (reply.status == 401 || reply.status == 403) {
                this.readingDisabled = true;
                FMLLog.severe("[%s] Discord refused the bot (HTTP %d): check "
                        + "the token, that the bot is in the server with "
                        + "access to the channel, and that the Message "
                        + "Content intent is enabled; Discord-to-game relay "
                        + "is off until the server restarts",
                        LostTalesMetaData.MOD_ID, Integer.valueOf(reply.status));
                return;
            }
            if (reply.status == 429) {
                throw new RateLimited(DiscordJson.retryAfterMillis(reply.body));
            }
            if (!reply.isSuccess()) {
                throw new IOException("Discord replied HTTP " + reply.status
                        + " to a channel read");
            }
            List<DiscordJson.Message> messages =
                    DiscordJson.parseMessages(reply.body);
            if (!messages.isEmpty()) {
                this.after = messages.get(messages.size() - 1).id;
            }
            if (!this.primed) {
                this.primed = true;
                return;
            }
            for (DiscordJson.Message message : messages) {
                if (message.bot) {
                    continue;
                }
                String name = DiscordMessageSanitizer.inboundName(
                        message.authorName);
                String text = DiscordMessageSanitizer.inbound(
                        message.content, message.mentionNames);
                if (name.length() > 0 && text.length() > 0) {
                    enqueueInbound(name, text);
                }
            }
        }

        private void flushOutbound() throws IOException {
            String webhook = LostTalesConfig.discordWebhookUrl.trim();
            Outbound next;
            while ((next = outbound.peek()) != null) {
                DiscordHttp.Reply reply = DiscordHttp.postWebhook(webhook,
                        DiscordJson.webhookBody(next.username, next.avatarUrl,
                                next.message));
                if (reply.status == 429) {
                    // The line stays queued and goes out after the wait.
                    throw new RateLimited(DiscordJson.retryAfterMillis(reply.body));
                }
                outbound.poll();
                outboundCount.decrementAndGet();
                if (!reply.isSuccess()) {
                    throw new IOException("Discord replied HTTP " + reply.status
                            + " to a webhook post");
                }
            }
        }

        private long failed(String reason) {
            if (this.healthy) {
                this.healthy = false;
                FMLLog.warning("[%s] Discord bridge failing, retrying with "
                        + "backoff: %s", LostTalesMetaData.MOD_ID, reason);
            }
            long wait = this.backoffMillis;
            this.backoffMillis = Math.min(MAX_BACKOFF_MILLIS,
                    this.backoffMillis * 2L);
            return wait;
        }

        private void recovered() {
            if (!this.healthy) {
                this.healthy = true;
                FMLLog.info("[%s] Discord bridge recovered",
                        LostTalesMetaData.MOD_ID);
            }
            this.backoffMillis = MIN_BACKOFF_MILLIS;
        }
    }

    /** Discord asked for a pause; not a failure. */
    private static final class RateLimited extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final long retryAfterMillis;

        RateLimited(long retryAfterMillis) {
            super("rate limited");
            this.retryAfterMillis = retryAfterMillis > 0L
                    ? retryAfterMillis : MIN_BACKOFF_MILLIS;
        }
    }
}
