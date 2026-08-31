package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.server.ChatMessageLog;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;

/**
 * The server's own Discord bridge, no library behind it: a worker
 * thread polls one Discord channel for new messages through the REST
 * API, posts the game's lines and the server's notices to a webhook,
 * and keeps the channel's topic saying whether the server is up, while
 * the server thread only ever touches two bounded queues and a wanted
 * topic. Discord messages are handed to
 * {@link LostTalesChatService#sendFromDiscord} on the server tick, so
 * they reach players through the same packet as every other line; game
 * lines are queued here by the chat service and posted off-thread.
 *
 * <p>The server's own notices — started, shutting down, a player joined
 * or left — are plain webhook posts under the webhook's own name, and
 * the topic is recomputed from the live player list on the tick after
 * any of those, so a burst of joins is one write. At shutdown the
 * farewell and the offline topic are queued first and the worker is
 * given a bounded moment to send them; nothing waits longer than that.</p>
 *
 * <p>Everything fails closed. A bad token or a missing permission stops
 * the inbound side (or the topic) for the session with one severe log
 * line; a rate limit is honoured for exactly the time Discord asks; any
 * other failure backs off, doubling up to a minute, and is logged once
 * on the way down and once on the way back. The webhook's own posts
 * come back as bot messages and are ignored, so nothing echoes. The
 * token and the webhook URL are never logged.</p>
 */
public final class LostTalesDiscordBridge {
    private static final LostTalesDiscordBridge INSTANCE =
            new LostTalesDiscordBridge();
    private static final int MAX_QUEUED_INBOUND = 256;
    private static final int MAX_QUEUED_OUTBOUND = 256;
    /** Shortest gap between two typing pings; Discord's own lasts ten
     *  seconds, so anything more often is spent for nothing. */
    private static final long TYPING_INTERVAL_MILLIS = 8000L;
    private static final int MAX_INBOUND_PER_TICK = 8;
    private static final long MIN_BACKOFF_MILLIS = 5000L;
    private static final long MAX_BACKOFF_MILLIS = 60000L;
    /**
     * How long a stop waits for the worker's last posts: enough for a
     * webhook post and a topic write on a healthy link, and a bound on
     * a shutdown with a stalled one.
     */
    private static final long STOP_JOIN_MILLIS = 5000L;

    private final Queue<Inbound> inbound = new ConcurrentLinkedQueue<Inbound>();
    private final AtomicInteger inboundCount = new AtomicInteger();
    private final Queue<Outbound> outbound = new ConcurrentLinkedQueue<Outbound>();
    private final AtomicInteger outboundCount = new AtomicInteger();
    private final DiscordChannelStatus status = new DiscordChannelStatus();
    /**
     * Which game message is which Discord message, both ways: filled by
     * the worker as posts are confirmed and by the tick as Discord lines
     * are delivered, read wherever a reply crosses the bridge.
     */
    private final DiscordMessageLinks links = new DiscordMessageLinks();
    private volatile boolean statusRefreshRequested;
    /**
     * When a typing ping was last sent to Discord. Discord shows its own
     * indicator for about ten seconds from one, so repeating it faster
     * than that buys nothing and only spends the bridge's rate limit.
     */
    private final AtomicLong typingSentMillis = new AtomicLong();
    private volatile boolean typingRequested;
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
        boolean bot = LostTalesConfig.discordBotToken.trim().length() > 0
                && LostTalesConfig.discordChannelId.trim().length() > 0;
        boolean reads = LostTalesConfig.discordRelayDiscordChat && bot;
        boolean posts = LostTalesConfig.discordWebhookUrl.trim().length() > 0
                && (LostTalesConfig.discordRelayGameChat
                        || LostTalesConfig.discordServerEvents);
        boolean manages = LostTalesConfig.discordChannelStatus && bot;
        if (!reads && !posts && !manages) {
            FMLLog.warning("[%s] Discord bridge is enabled but has neither a "
                    + "bot token and channel id to read or manage with nor a "
                    + "webhook URL to post to; nothing will be relayed",
                    LostTalesMetaData.MOD_ID);
            return;
        }
        if (!this.registered) {
            FMLCommonHandler.instance().bus().register(this);
            this.registered = true;
        }
        Worker started = new Worker(reads, posts, manages);
        this.worker = started;
        started.start();
        FMLLog.info("[%s] Discord bridge started (%s%s)", LostTalesMetaData.MOD_ID,
                reads && posts ? "both ways" : reads ? "Discord to game"
                        : posts ? "game to Discord" : "topic only",
                manages && (reads || posts) ? ", channel topic" : "");
    }

    /**
     * Stops the worker after a bounded wait for what it still has to
     * send, then forgets everything queued.
     */
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
            if (running.isAlive()) {
                FMLLog.warning("[%s] Discord bridge did not finish its last "
                        + "posts within %d ms; leaving them", LostTalesMetaData.MOD_ID,
                        Long.valueOf(STOP_JOIN_MILLIS));
            }
        }
        this.inbound.clear();
        this.inboundCount.set(0);
        this.outbound.clear();
        this.outboundCount.set(0);
        this.links.clear();
        this.status.reset();
        this.statusRefreshRequested = false;
        // Presence is about a moment that has passed: a request left
        // standing must not reach the next server this bridge serves.
        this.typingRequested = false;
        this.typingSentMillis.set(0L);
    }

    public boolean isRunning() {
        Worker running = this.worker;
        return running != null && running.isAlive();
    }

    /**
     * Queues a game line for Discord; the worker posts it. Dropped when
     * the bridge is off, posting is off, or the queue is full.
     * {@code messageId} is the line's own name, kept so the post's
     * Discord copy can be linked back to it; {@code reply} is the quote
     * the line was distributed with, rendered onto the post as a header
     * — pointing at the Discord original when the bridge knows it.
     */
    public void relay(String username, String avatarUrl, String message,
                      long messageId, ChatReplyReference reply) {
        if (LostTalesConfig.discordRelayGameChat) {
            post(username, avatarUrl, message, messageId, reply);
        }
    }

    /**
     * Queues one of the server's own notices, under the webhook's own
     * name and picture; dropped under the same conditions as a line.
     */
    public void announce(String message) {
        if (LostTalesConfig.discordServerEvents) {
            post("", "", message, ChatMessageIds.NONE,
                    ChatReplyReference.NONE);
        }
    }

    /**
     * Says that a game message was rewritten, so its Discord copy — if
     * it has one — is rewritten too, keeping the reply header it was
     * posted under. Queued behind everything already outbound: the
     * queue is posted in order, so an edit always finds the link its
     * own post registered. A message never carried to Discord resolves
     * to no link on the worker and the entry is simply dropped.
     */
    public void relayEdit(long messageId, String message) {
        if (LostTalesConfig.discordRelayGameChat && message != null
                && message.length() > 0) {
            enqueueOutbound(new Outbound(Outbound.Kind.EDIT, "", "",
                    message, messageId, ChatReplyReference.NONE));
        }
    }

    /**
     * Says that a game message was taken back, so its Discord copy — if
     * it has one — is deleted too, on the same terms as an edit.
     */
    public void relayDelete(long messageId) {
        if (LostTalesConfig.discordRelayGameChat) {
            enqueueOutbound(new Outbound(Outbound.Kind.DELETE, "", "", "",
                    messageId, ChatReplyReference.NONE));
        }
    }

    private void post(String username, String avatarUrl, String message,
                      long messageId, ChatReplyReference reply) {
        if (message == null || message.length() == 0) {
            return;
        }
        enqueueOutbound(new Outbound(Outbound.Kind.POST, username, avatarUrl,
                message, messageId,
                reply == null ? ChatReplyReference.NONE : reply));
    }

    private void enqueueOutbound(Outbound entry) {
        Worker running = this.worker;
        if (running == null || !running.posts
                || this.outboundCount.get() >= MAX_QUEUED_OUTBOUND) {
            return;
        }
        this.outboundCount.incrementAndGet();
        this.outbound.add(entry);
    }

    /**
     * Says that somebody in the game is typing into the bridged channel,
     * so Discord shows its indicator there. Presence only, and only
     * while game chat is being relayed at all: no text crosses with it,
     * and what Discord sees is the bot typing rather than a name it has
     * no account for. Cheap and idempotent — the worker sends at most
     * one ping per {@link #TYPING_INTERVAL_MILLIS}, and drops the rest.
     *
     * <p>Only this direction is possible over the bridge as it is built.
     * Discord publishes a user's own typing on its gateway alone, which
     * is a WebSocket the bridge deliberately does not open — it is
     * plain HTTP and the polling API carries no typing at all — so a
     * Discord member typing cannot be shown in game.</p>
     */
    public void relayTyping() {
        if (LostTalesConfig.discordRelayGameChat) {
            this.typingRequested = true;
        }
    }

    /**
     * Asks for the topic to be recomputed from the live server on the
     * next tick. Cheap and idempotent, so every join and leave may call it.
     */
    public void requestStatusRefresh() {
        this.statusRefreshRequested = true;
    }

    /** The server is up and accepting players; say so. Server thread. */
    public void onServerStarted() {
        announce(DiscordServerNotices.serverStarted());
        requestStatusRefresh();
    }

    /**
     * The server is going down: queue the farewell and the offline topic
     * ahead of {@link #stop()}, which gives the worker its bounded
     * moment to send them. Server thread.
     */
    public void onServerStopping() {
        announce(DiscordServerNotices.serverStopping());
        Worker running = this.worker;
        if (running != null && running.manages) {
            this.status.request(DiscordServerNotices.offlineTopic());
        }
        this.statusRefreshRequested = false;
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event == null || event.player == null || this.worker == null) {
            return;
        }
        announce(DiscordServerNotices.playerJoined(
                event.player.getCommandSenderName()));
        requestStatusRefresh();
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event == null || event.player == null || this.worker == null) {
            return;
        }
        announce(DiscordServerNotices.playerLeft(
                event.player.getCommandSenderName()));
        // The player is still on the list while this fires; the count
        // is taken on the next tick, when they are gone.
        requestStatusRefresh();
    }

    /**
     * Delivers queued Discord messages on the server thread, a few per
     * tick, and restates the wanted topic from the live player list when
     * something asked for it.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        Worker running = this.worker;
        if (event == null || event.phase != TickEvent.Phase.START
                || running == null) {
            return;
        }
        int delivered = 0;
        Inbound message;
        while (delivered < MAX_INBOUND_PER_TICK
                && (message = this.inbound.poll()) != null) {
            this.inboundCount.decrementAndGet();
            try {
                deliver(message);
            } catch (RuntimeException exception) {
                FMLLog.warning("[%s] Could not deliver a Discord message: %s",
                        LostTalesMetaData.MOD_ID, exception.toString());
            }
            delivered++;
        }
        if (this.statusRefreshRequested && running.manages) {
            this.statusRefreshRequested = false;
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                this.status.request(DiscordServerNotices.onlineTopic(
                        server.getCurrentPlayerCount(),
                        server.getMaxPlayers()));
            }
        }
    }

    /** One inbound entry, on the server thread. */
    private void deliver(Inbound message) {
        if (message.kind == Inbound.Kind.EDIT) {
            // Word about a message already delivered: it reaches the
            // game only while the bridge still knows which line it is.
            long target = this.links.messageIdOf(message.discordId);
            if (target != ChatMessageIds.NONE) {
                LostTalesChatService.editFromDiscord(target, message.text);
            }
            return;
        }
        if (message.kind == Inbound.Kind.DELETE) {
            long target = this.links.messageIdOf(message.discordId);
            if (target != ChatMessageIds.NONE) {
                LostTalesChatService.deleteFromDiscord(target);
            }
            return;
        }
        // A Discord reply names a Discord id; when that id is a
        // message the bridge has seen cross — either way — the
        // line is delivered quoting it, exactly as a player's
        // reply is. One referencing anything older than the
        // session, or deleted since, goes out plain.
        ChatReplyReference reply = ChatReplyReference.NONE;
        long referenced = this.links.messageIdOf(
                message.referencedDiscordId);
        if (referenced != ChatMessageIds.NONE) {
            reply = ChatMessageLog.quoteForDiscordChannel(referenced);
        }
        long messageId = LostTalesChatService.sendFromDiscord(
                message.name, message.text, reply);
        this.links.link(messageId, message.discordId);
    }

    private void enqueueInbound(Inbound entry) {
        if (this.inboundCount.get() >= MAX_QUEUED_INBOUND) {
            return;
        }
        this.inboundCount.incrementAndGet();
        this.inbound.add(entry);
    }

    private static final class Inbound {
        /** What reached the game: a message, or word about an old one. */
        enum Kind { MESSAGE, EDIT, DELETE }

        final Kind kind;
        final String name;
        /** The message's text, or an edit's new text. */
        final String text;
        /** The message's own Discord id, for the link a reply follows. */
        final String discordId;
        /** The Discord id this message replies to; empty for none. */
        final String referencedDiscordId;

        Inbound(Kind kind, String name, String text, String discordId,
                String referencedDiscordId) {
            this.kind = kind;
            this.name = name;
            this.text = text;
            this.discordId = discordId;
            this.referencedDiscordId = referencedDiscordId;
        }
    }

    private static final class Outbound {
        /** What the worker is to do with the entry. */
        enum Kind { POST, EDIT, DELETE }

        final Kind kind;
        final String username;
        final String avatarUrl;
        /** The text to post, or the new text of an edit. */
        final String message;
        /** The game message the entry is about; NONE for a notice. */
        final long messageId;
        /** What a posted message replies to; NONE for an ordinary line. */
        final ChatReplyReference reply;

        Outbound(Kind kind, String username, String avatarUrl,
                 String message, long messageId, ChatReplyReference reply) {
            this.kind = kind;
            this.username = username;
            this.avatarUrl = avatarUrl;
            this.message = message;
            this.messageId = messageId;
            this.reply = reply;
        }
    }

    /** The polling, posting and topic-writing thread; one per started bridge. */
    private final class Worker extends Thread {
        final boolean reads;
        final boolean posts;
        final boolean manages;
        private volatile boolean running = true;
        /** The newest message id seen; empty until the channel is primed. */
        private String after = "";
        /** The watch over relayed messages for edits and deletions. */
        private final DiscordMessageSweep sweep = new DiscordMessageSweep();
        private boolean primed;
        private boolean readingDisabled;
        private boolean healthy = true;
        private long backoffMillis = MIN_BACKOFF_MILLIS;
        /** Where the webhook posts, for jump links; asked for once. */
        private DiscordJson.WebhookInfo webhookInfo;
        private boolean webhookInfoAsked;
        /** Whether the no-PATCH warning has been said this session. */
        private boolean patchWarned;

        Worker(boolean reads, boolean posts, boolean manages) {
            super("LostTales-Discord");
            setDaemon(true);
            this.reads = reads;
            this.posts = posts;
            this.manages = manages;
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
                        sweepChannel();
                    }
                    if (this.posts) {
                        flushOutbound();
                        flushTyping();
                    }
                    recovered();
                } catch (RateLimited limited) {
                    sleepMillis = Math.max(sleepMillis, limited.retryAfterMillis);
                } catch (IOException exception) {
                    sleepMillis = failed(exception.toString());
                } catch (RuntimeException exception) {
                    sleepMillis = failed(exception.toString());
                }
                // The topic keeps its own clock: a limit on it must not
                // hold the chat back, nor a chat failure the topic.
                if (this.manages) {
                    flushStatus(false);
                }
                if (!this.running) {
                    break;
                }
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException interrupted) {
                    break;
                }
            }
            sendLast();
        }

        /**
         * Sends the typing ping if one has been asked for and the last
         * is old enough. A refusal is dropped rather than retried:
         * presence is only worth saying while it is still true.
         */
        private void flushTyping() {
            if (!typingRequested) {
                return;
            }
            typingRequested = false;
            long now = System.currentTimeMillis();
            long last = typingSentMillis.get();
            if (last != 0L && now - last < TYPING_INTERVAL_MILLIS) {
                return;
            }
            String token = LostTalesConfig.discordBotToken.trim();
            String channel = LostTalesConfig.discordChannelId.trim();
            if (token.length() == 0 || channel.length() == 0) {
                return;
            }
            typingSentMillis.set(now);
            try {
                DiscordHttp.postTyping(token, channel);
            } catch (IOException ignored) {
                // Presence that did not arrive is presence not worth
                // chasing; the next keystroke asks again.
            } catch (RuntimeException ignored) {
                // As above.
            }
        }

        /**
         * One best-effort pass at what is still queued when the bridge
         * stops — the farewell, the offline topic — with no retry and no
         * wait: whatever does not go now is left.
         */
        private void sendLast() {
            try {
                if (this.posts) {
                    flushOutbound();
                }
            } catch (RateLimited limited) {
                FMLLog.info("[%s] Discord limited the bridge's last post; "
                        + "leaving it", LostTalesMetaData.MOD_ID);
            } catch (IOException exception) {
                FMLLog.info("[%s] Discord bridge could not send its last "
                        + "post: %s", LostTalesMetaData.MOD_ID,
                        exception.toString());
            } catch (RuntimeException exception) {
                FMLLog.info("[%s] Discord bridge could not send its last "
                        + "post: %s", LostTalesMetaData.MOD_ID,
                        exception.toString());
            }
            if (this.manages) {
                flushStatus(true);
            }
        }

        private void flushStatus(boolean finalAttempt) {
            status.flush(LostTalesConfig.discordBotToken.trim(),
                    LostTalesConfig.discordChannelId.trim(),
                    Math.max(60L, Math.min(3600L,
                            LostTalesConfig.discordChannelStatusIntervalSeconds))
                            * 1000L,
                    finalAttempt);
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
            if (readRefused(reply.status)) {
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
                    enqueueInbound(new Inbound(Inbound.Kind.MESSAGE, name,
                            text, message.id, message.referencedMessageId));
                    // Watched from now on, so a later edit or deletion
                    // of it follows the message into the game.
                    this.sweep.track(message);
                }
            }
        }

        /**
         * Re-reads the channel's newest page and compares it with the
         * messages relayed, so a Discord member's own edits and
         * deletions follow their messages into the game — the polling
         * API says nothing about either, which makes looking again the
         * only way to hear of them. Only the page's reach is watched:
         * a message older than the newest {@link DiscordJson#PAGE_SIZE}
         * is out of sight and stays as it was delivered. Skipped
         * entirely while nothing is watched, so a quiet channel costs
         * no second request.
         */
        private void sweepChannel() throws IOException {
            if (this.sweep.isEmpty()) {
                return;
            }
            DiscordHttp.Reply reply = DiscordHttp.getMessages(
                    LostTalesConfig.discordBotToken.trim(),
                    LostTalesConfig.discordChannelId.trim(), "",
                    DiscordJson.PAGE_SIZE);
            if (readRefused(reply.status)) {
                return;
            }
            if (reply.status == 429) {
                throw new RateLimited(DiscordJson.retryAfterMillis(reply.body));
            }
            if (!reply.isSuccess()) {
                throw new IOException("Discord replied HTTP " + reply.status
                        + " to a channel sweep");
            }
            DiscordMessageSweep.Changes changes = this.sweep.apply(
                    DiscordJson.parseMessages(reply.body));
            for (DiscordJson.Message message : changes.edited) {
                String text = DiscordMessageSanitizer.inbound(
                        message.content, message.mentionNames);
                // Edited down to nothing sayable — an attachment left
                // alone — keeps the words it was delivered with.
                if (text.length() > 0) {
                    enqueueInbound(new Inbound(Inbound.Kind.EDIT, "", text,
                            message.id, ""));
                }
            }
            for (int index = 0; index < changes.deletedIds.size(); index++) {
                enqueueInbound(new Inbound(Inbound.Kind.DELETE, "", "",
                        changes.deletedIds.get(index), ""));
            }
        }

        /**
         * Whether Discord refused the bot outright; said once, and
         * reading stays off for the session.
         */
        private boolean readRefused(int status) {
            if (status != 401 && status != 403) {
                return false;
            }
            this.readingDisabled = true;
            FMLLog.severe("[%s] Discord refused the bot (HTTP %d): check "
                    + "the token, that the bot is in the server with "
                    + "access to the channel, and that the Message "
                    + "Content intent is enabled; Discord-to-game relay "
                    + "is off until the server restarts",
                    LostTalesMetaData.MOD_ID, Integer.valueOf(status));
            return true;
        }

        private void flushOutbound() throws IOException {
            String webhook = LostTalesConfig.discordWebhookUrl.trim();
            Outbound next;
            while ((next = outbound.peek()) != null) {
                String header = "";
                DiscordHttp.Reply reply;
                if (next.kind == Outbound.Kind.POST) {
                    header = replyHeader(next, webhook);
                    reply = DiscordHttp.postWebhook(webhook,
                            DiscordJson.webhookBody(next.username,
                                    next.avatarUrl, header + next.message));
                } else {
                    // An edit or a removal follows the message it names:
                    // the queue is worked in order, so the post that
                    // registered the link has always gone out first. A
                    // message with no link never reached Discord —
                    // another channel's, or older than the session — and
                    // there is nothing there to correct.
                    String discordId = links.discordIdOf(next.messageId);
                    if (discordId.length() == 0
                            || (next.kind == Outbound.Kind.EDIT
                                    && !patchSupported())) {
                        outbound.poll();
                        outboundCount.decrementAndGet();
                        continue;
                    }
                    reply = next.kind == Outbound.Kind.EDIT
                            ? DiscordHttp.editWebhookMessage(webhook,
                                    discordId, DiscordJson.webhookEditBody(
                                            links.headerOf(next.messageId)
                                                    + next.message))
                            : DiscordHttp.deleteWebhookMessage(webhook,
                                    discordId);
                }
                if (reply.status == 429) {
                    // The entry stays queued and goes out after the wait.
                    throw new RateLimited(DiscordJson.retryAfterMillis(reply.body));
                }
                outbound.poll();
                outboundCount.decrementAndGet();
                if (reply.status == 404 && next.kind != Outbound.Kind.POST) {
                    // The copy is already gone on Discord's side —
                    // deleted by hand, or the webhook was recreated.
                    // There is nothing left to correct there.
                    continue;
                }
                if (!reply.isSuccess()) {
                    throw new IOException("Discord replied HTTP " + reply.status
                            + " to a webhook "
                            + (next.kind == Outbound.Kind.POST ? "post"
                                    : next.kind == Outbound.Kind.EDIT
                                            ? "edit" : "delete"));
                }
                if (next.kind == Outbound.Kind.POST) {
                    // The post's Discord id, from the wait=true body:
                    // what a reply from either side finds the message
                    // by, and what an edit or a removal follows it by,
                    // with the header an edit has to open with again. A
                    // body that does not parse leaves the line unlinked.
                    links.link(next.messageId,
                            DiscordJson.parseCreatedMessageId(reply.body),
                            header);
                }
            }
        }

        /**
         * Whether this JVM can send the PATCH a webhook edit needs; said
         * once when it cannot, after which edit entries are dropped
         * quietly. Removals are ordinary DELETEs and still cross.
         */
        private boolean patchSupported() {
            if (DiscordHttpPatch.isAvailable()) {
                return true;
            }
            if (!this.patchWarned) {
                this.patchWarned = true;
                FMLLog.warning("[%s] This JVM cannot send a PATCH, so "
                        + "in-game edits will not reach Discord; removals "
                        + "still do", LostTalesMetaData.MOD_ID);
            }
            return false;
        }

        /**
         * The reply header a post opens with when the line answers
         * something, empty when it does not. The header points at the
         * Discord original when the bridge knows it — the queue is
         * posted in order, so a reply to a line still queued finds its
         * link once that line has gone out first — and stands alone when
         * it does not: a webhook post cannot be a native Discord reply
         * (the execute endpoint takes no message reference), so the
         * header is the reply, and who it quotes matters more than the
         * link.
         */
        private String replyHeader(Outbound next, String webhookUrl) {
            if (next.reply == null || !next.reply.exists()) {
                return "";
            }
            String jumpUrl = "";
            String discordId = links.discordIdOf(next.reply.getMessageId());
            if (discordId.length() > 0) {
                DiscordJson.WebhookInfo info = webhookInfo(webhookUrl);
                if (info != null) {
                    jumpUrl = "https://discord.com/channels/" + info.guildId
                            + "/" + info.channelId + "/" + discordId;
                }
            }
            return DiscordMessageSanitizer.replyHeader(
                    next.reply.getAuthor(), next.reply.getExcerpt(), jumpUrl);
        }

        /**
         * The guild and channel the webhook posts to, asked of the
         * webhook's own URL once per session and kept. Best-effort: the
         * jump link is decoration on a quote that already says who and
         * what, so a failure here silently leaves the quotes plain.
         */
        private DiscordJson.WebhookInfo webhookInfo(String webhookUrl) {
            if (!this.webhookInfoAsked) {
                this.webhookInfoAsked = true;
                try {
                    DiscordHttp.Reply reply =
                            DiscordHttp.getWebhookInfo(webhookUrl);
                    if (reply.isSuccess()) {
                        this.webhookInfo =
                                DiscordJson.parseWebhookInfo(reply.body);
                    }
                } catch (IOException ignored) {
                    // As above: plain quotes, not a bridge failure.
                } catch (RuntimeException ignored) {
                    // As above.
                }
            }
            return this.webhookInfo;
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
