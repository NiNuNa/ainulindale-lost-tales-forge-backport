package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.server.ChatMessageLog;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.core.LostTalesClassTransformer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;

/**
 * The server's own Discord bridge, no library behind it: a worker
 * thread polls the Discord channels the bindings read for new messages
 * through the REST API, posts the game's lines and the server's notices
 * to the webhooks they name, and keeps the Discord channel's topic
 * saying whether the server is up, while the server thread only ever
 * touches two bounded queues and a wanted topic. Which game channel is
 * tied to which Discord channel, and which way, is
 * {@link DiscordChannelBindings}: the game's own Discord channel both
 * ways, and any other channel the channel itself allows — each line
 * posted as a card in its channel's colour, headed by the channel's
 * name — while the Party channel, the console and whispers never leave
 * the game. Discord messages are handed to
 * {@link LostTalesChatService#sendFromDiscord} on the server tick, so
 * they reach players through the same packet as every other line; game
 * lines are queued here by the chat service and posted off-thread.
 *
 * <p>The server's own notices — started, shutting down, a player joined,
 * left, died or earned an achievement — are {@link DiscordNotice}s,
 * posted as one coloured embed each under the webhook's own name;
 * {@link DiscordGameEventRelay} turns the game's events into them, and
 * each kind answers to its own config switch here. The topic is
 * recomputed from the live player list on the tick after a start, join
 * or leave, so a burst of joins is one write. At shutdown the farewell
 * and the offline topic are queued first and the worker is given a
 * bounded moment to send them; nothing waits longer than that.</p>
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
    /** Members remembered by name for the mute command. */
    private static final int MAX_RECENT_AUTHORS = 256;
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
    /**
     * Display name, lower-cased, to Discord id for the members whose
     * lines have been relayed this session, newest last and bounded to
     * {@link #MAX_RECENT_AUTHORS}: what lets an operator mute a member
     * by the name they see in the chat. Written by the worker, read on
     * the server thread by the mute command.
     */
    private final LinkedHashMap<String, String> recentAuthors =
            new LinkedHashMap<String, String>();
    /** Turns joins, leaves and the server's broadcasts into notices. */
    private final DiscordGameEventRelay relay = new DiscordGameEventRelay();
    /** The bindings read at the last start; empty while the bridge is stopped. */
    private volatile DiscordChannelBindings bindings = DiscordChannelBindings.EMPTY;
    /** Where the bindings' warnings go: the log, once each at start. */
    private static final DiscordChannelBindings.Warnings LOG_WARNINGS =
            new DiscordChannelBindings.Warnings() {
                @Override
                public void warn(String message) {
                    FMLLog.warning("[%s] %s", LostTalesMetaData.MOD_ID, message);
                }
            };
    private volatile boolean statusRefreshRequested;
    /**
     * The bindings somebody is typing into, by key, waiting for the
     * worker to say so on Discord. Written on the server thread, drained
     * by the worker.
     */
    private final Set<String> typingRequests =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
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
        boolean botPresent = LostTalesConfig.discordBotToken.trim().length() > 0;
        DiscordChannelBindings configured = DiscordChannelBindings.parse(
                LostTalesConfig.discordChannelBindings, botPresent, LOG_WARNINGS);
        this.bindings = configured;
        boolean reads = configured.readsAnything();
        boolean notices = LostTalesConfig.discordServerEvents
                || LostTalesConfig.discordDeathMessages
                || LostTalesConfig.discordAchievements;
        boolean posts = configured.sendsAnything()
                || notices && configured.noticeWebhookUrl().length() > 0;
        DiscordChannelBinding discord = configured.discordChannel();
        boolean manages = LostTalesConfig.discordChannelStatus && botPresent
                && discord != null && discord.getDiscordChannelId().length() > 0;
        if (!reads && !posts && !manages) {
            FMLLog.warning("[%s] Discord bridge is enabled but no binding "
                    + "reads a Discord channel, posts to a webhook, or keeps "
                    + "a topic; nothing will be relayed", LostTalesMetaData.MOD_ID);
            return;
        }
        if (!this.registered) {
            FMLCommonHandler.instance().bus().register(this);
            FMLCommonHandler.instance().bus().register(this.relay);
            this.registered = true;
        }
        if (posts && (LostTalesConfig.discordDeathMessages
                || LostTalesConfig.discordAchievements)
                && !Boolean.getBoolean(LostTalesClassTransformer
                        .SERVER_BROADCAST_ACTIVE_PROPERTY)) {
            // Deaths and achievements are read off the server's broadcast
            // seam, which only the coremod can open; without it they are
            // simply never heard of.
            FMLLog.warning("[%s] The server-broadcast transformer is not "
                    + "active; deaths and achievements will not reach "
                    + "Discord", LostTalesMetaData.MOD_ID);
        }
        Worker started = new Worker(configured, reads, posts, manages);
        this.worker = started;
        started.start();
        FMLLog.info("[%s] Discord bridge started (%s%s): %s", LostTalesMetaData.MOD_ID,
                reads && posts ? "both ways" : reads ? "Discord to game"
                        : posts ? "game to Discord" : "topic only",
                manages && (reads || posts) ? ", channel topic" : "",
                configured.describeForLog());
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
        this.bindings = DiscordChannelBindings.EMPTY;
        this.inbound.clear();
        this.inboundCount.set(0);
        this.outbound.clear();
        this.outboundCount.set(0);
        this.links.clear();
        synchronized (this.recentAuthors) {
            this.recentAuthors.clear();
        }
        this.status.reset();
        this.statusRefreshRequested = false;
        // Presence is about a moment that has passed: a request left
        // standing must not reach the next server this bridge serves.
        this.typingRequests.clear();
    }

    public boolean isRunning() {
        Worker running = this.worker;
        return running != null && running.isAlive();
    }

    /** Whether anything queued for Discord would be posted at all. */
    public boolean isPosting() {
        Worker running = this.worker;
        return running != null && running.posts;
    }

    /**
     * Whether the game's own Discord channel exists for players: the
     * bridge is on and a binding ties that channel to Discord in at
     * least one direction. What the access packet and the send gate ask.
     */
    public boolean offersDiscordChannel() {
        DiscordChannelBinding discord = this.bindings.discordChannel();
        return LostTalesConfig.discordEnabled && this.worker != null
                && discord != null
                && discord.getDirection() != DiscordBridgeDirection.DISABLED;
    }

    /** The bindings in force, for the chat service's routing questions. */
    public DiscordChannelBindings bindings() {
        return this.bindings;
    }

    /**
     * Queues a game line for Discord through the binding of its channel
     * — for the Faction channel, of the sender's faction — when that
     * binding posts; dropped otherwise, and when the queue is full. The
     * post wears the sender's name and picture, and the line itself is
     * a card: an embed in {@code color} headed by {@code channelLabel}
     * — the channel's mark and name, a faction's own name for Faction
     * chat — so a Discord channel that several game channels share
     * still says where each line was spoken, footed by
     * {@code accountName} where the line was spoken as a character
     * (empty on an account line) and stamped with the time it was said.
     * The name is never tagged: Discord refuses a webhook name that says
     * "discord". {@code messageId} is the line's own name, kept so the
     * post's Discord copy can be linked back to it; {@code reply} is the
     * quote the line was distributed with, opening the card's text as a
     * block quote — pointing at the Discord original when the bridge
     * knows it.
     */
    public void relayToDiscord(ChatChannel channel, String factionId,
                               String channelLabel, int color, String username,
                               String avatarUrl, String accountName,
                               String message, long messageId,
                               ChatReplyReference reply) {
        DiscordChannelBinding binding = this.bindings.forGame(channel, factionId);
        if (binding == null || !binding.sendsToDiscord()
                || message == null || message.length() == 0) {
            return;
        }
        enqueueOutbound(new Outbound(Outbound.Kind.POST, username, avatarUrl,
                message, messageId,
                reply == null ? ChatReplyReference.NONE : reply, null,
                binding.key(), new DiscordJson.LineCard(channelLabel, color,
                        accountName, System.currentTimeMillis(), "")));
    }

    /**
     * Queues one of the server's own notices as an embed under the
     * webhook's own name and picture; dropped when its kind is switched
     * off in the config, and under the same conditions as a line.
     */
    public void announce(DiscordNotice notice) {
        if (notice == null || notice.getText().length() == 0
                || !isEnabled(notice.getKind())) {
            return;
        }
        enqueueOutbound(new Outbound(Outbound.Kind.POST, "", "",
                notice.getText(), ChatMessageIds.NONE,
                ChatReplyReference.NONE, notice, "", null));
    }

    /** The config switch a kind of notice answers to. */
    private static boolean isEnabled(DiscordNotice.Kind kind) {
        switch (kind) {
            case PLAYER_DIED:
                return LostTalesConfig.discordDeathMessages;
            case ACHIEVEMENT:
                return LostTalesConfig.discordAchievements;
            default:
                return LostTalesConfig.discordServerEvents;
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
        if (message != null && message.length() > 0) {
            // Which webhook the copy lives behind is only known to the
            // worker, from the link its post registered; the entry is
            // resolved there, and dropped when there is no copy.
            enqueueOutbound(new Outbound(Outbound.Kind.EDIT, "", "",
                    message, messageId, ChatReplyReference.NONE, null,
                    "", null));
        }
    }

    /**
     * Says that a game message was taken back, so its Discord copy — if
     * it has one — is deleted too, on the same terms as an edit.
     */
    public void relayDelete(long messageId) {
        enqueueOutbound(new Outbound(Outbound.Kind.DELETE, "", "", "",
                messageId, ChatReplyReference.NONE, null, "", null));
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
     * Says that somebody in the game is typing into a bound channel, so
     * Discord shows its indicator in that channel. Presence only, and
     * only for a binding that posts: no text crosses with it, and what
     * Discord sees is the bot typing rather than a name it has no
     * account for. Cheap and idempotent — the worker sends at most one
     * ping per binding per {@link #TYPING_INTERVAL_MILLIS}, and drops
     * the rest. A binding that names no Discord channel is asked its
     * webhook's channel once, since the bot must know where to type.
     *
     * <p>Only this direction is possible over the bridge as it is built.
     * Discord publishes a user's own typing on its gateway alone, which
     * is a WebSocket the bridge deliberately does not open — it is
     * plain HTTP and the polling API carries no typing at all — so a
     * Discord member typing cannot be shown in game.</p>
     */
    public void relayTyping(ChatChannel channel, String factionId) {
        DiscordChannelBinding binding = this.bindings.forGame(channel, factionId);
        if (binding != null && binding.sendsToDiscord()) {
            this.typingRequests.add(binding.key());
        }
    }

    /**
     * The Discord id of a member whose line was relayed this session,
     * by the display name the chat showed; empty for a name not seen.
     * Case-insensitive, the newest bearer of a name winning.
     */
    public String findDiscordUserId(String displayName) {
        String key = displayName == null ? ""
                : displayName.trim().toLowerCase(Locale.ROOT);
        if (key.length() == 0) {
            return "";
        }
        synchronized (this.recentAuthors) {
            String id = this.recentAuthors.get(key);
            return id == null ? "" : id;
        }
    }

    private void rememberAuthor(String displayName, String authorId) {
        if (authorId == null || authorId.length() == 0) {
            return;
        }
        String key = displayName.toLowerCase(Locale.ROOT);
        synchronized (this.recentAuthors) {
            this.recentAuthors.remove(key);
            this.recentAuthors.put(key, authorId);
            while (this.recentAuthors.size() > MAX_RECENT_AUTHORS) {
                Iterator<String> oldest =
                        this.recentAuthors.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
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
        // Delivered into the channel its binding names, and only while
        // that binding still reads: the bindings are read once at start,
        // so this is a guard on the queue, not a policy of its own.
        DiscordChannelBinding binding = this.bindings.byKey(message.bindingKey);
        if (binding == null || !binding.readsFromDiscord()) {
            return;
        }
        // A Discord reply names a Discord id; when that id is a
        // message the bridge has seen cross — either way — through
        // this same binding, the line is delivered quoting it, exactly
        // as a player's reply is. One referencing anything older than
        // the session, deleted since, or belonging to another bound
        // channel goes out plain: a quote never carries words from one
        // bound channel into another.
        ChatReplyReference reply = ChatReplyReference.NONE;
        long referenced = this.links.messageIdOf(
                message.referencedDiscordId);
        if (referenced != ChatMessageIds.NONE
                && binding.key().equals(this.links.bindingKeyOf(referenced))) {
            reply = ChatMessageLog.quoteForDiscordChannel(referenced);
        }
        long messageId = LostTalesChatService.sendFromDiscord(
                binding.getChannel(), binding.getFactionScope(),
                message.name, message.authorId, message.text, reply);
        this.links.link(messageId, message.discordId, "", binding.key());
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
        /** The author's Discord id; empty for word about an old message. */
        final String authorId;
        /** The message's text, or an edit's new text. */
        final String text;
        /** The message's own Discord id, for the link a reply follows. */
        final String discordId;
        /** The Discord id this message replies to; empty for none. */
        final String referencedDiscordId;
        /** The binding it was read through; empty for word about an old message. */
        final String bindingKey;

        Inbound(Kind kind, String name, String authorId, String text,
                String discordId, String referencedDiscordId,
                String bindingKey) {
            this.kind = kind;
            this.name = name;
            this.authorId = authorId;
            this.text = text;
            this.discordId = discordId;
            this.referencedDiscordId = referencedDiscordId;
            this.bindingKey = bindingKey;
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
        /** The server's own notice a post is, posted as an embed; null for a line. */
        final DiscordNotice notice;
        /** The binding a post goes through; empty for a notice, an edit or a removal. */
        final String bindingKey;
        /** The card a line is posted as, its reply header still empty; null otherwise. */
        final DiscordJson.LineCard card;

        Outbound(Kind kind, String username, String avatarUrl,
                 String message, long messageId, ChatReplyReference reply,
                 DiscordNotice notice, String bindingKey,
                 DiscordJson.LineCard card) {
            this.kind = kind;
            this.username = username;
            this.avatarUrl = avatarUrl;
            this.message = message;
            this.messageId = messageId;
            this.reply = reply;
            this.notice = notice;
            this.bindingKey = bindingKey == null ? "" : bindingKey;
            this.card = card;
        }
    }

    /** What the worker knows of one Discord channel it reads. */
    private static final class ChannelCursor {
        /** The newest message id seen; empty until the channel is primed. */
        String after = "";
        /** The watch over relayed messages for edits and deletions. */
        final DiscordMessageSweep sweep = new DiscordMessageSweep();
        boolean primed;
        /** Reading refused for the session: the bot cannot see the channel. */
        boolean disabled;
    }

    /** The polling, posting and topic-writing thread; one per started bridge. */
    private final class Worker extends Thread {
        final boolean reads;
        final boolean posts;
        final boolean manages;
        /** The bindings this worker serves, fixed for its life. */
        private final DiscordChannelBindings bindings;
        private volatile boolean running = true;
        /** One cursor per Discord channel read, by its channel id. */
        private final Map<String, ChannelCursor> cursors =
                new HashMap<String, ChannelCursor>();
        /** Bindings whose webhook Discord refused for the session. */
        private final Set<String> postingDisabled = new HashSet<String>();
        /**
         * When a typing ping was last sent per binding. Discord shows its
         * own indicator for about ten seconds from one, so repeating it
         * faster than that buys nothing and only spends the rate limit.
         */
        private final Map<String, Long> typingSentMillis = new HashMap<String, Long>();
        /** The bot itself was refused: nothing is read for the session. */
        private boolean readingDisabled;
        private boolean healthy = true;
        private long backoffMillis = MIN_BACKOFF_MILLIS;
        /**
         * Where each webhook posts, for jump links; asked once per
         * webhook URL and kept, a null standing for an answer that
         * never came.
         */
        private final Map<String, DiscordJson.WebhookInfo> webhookInfos =
                new HashMap<String, DiscordJson.WebhookInfo>();
        /** Whether the no-PATCH warning has been said this session. */
        private boolean patchWarned;

        Worker(DiscordChannelBindings bindings, boolean reads, boolean posts,
               boolean manages) {
            super("LostTales-Discord");
            setDaemon(true);
            this.bindings = bindings;
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
                        sweepChannels();
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
         * Sends a typing ping into every bound channel asked for since
         * the last pass whose last ping is old enough. A refusal is
         * dropped rather than retried: presence is only worth saying
         * while it is still true.
         */
        private void flushTyping() {
            if (typingRequests.isEmpty()) {
                return;
            }
            String token = LostTalesConfig.discordBotToken.trim();
            for (String key : new ArrayList<String>(typingRequests)) {
                typingRequests.remove(key);
                DiscordChannelBinding binding = this.bindings.byKey(key);
                if (binding == null || token.length() == 0) {
                    continue;
                }
                long now = System.currentTimeMillis();
                Long last = this.typingSentMillis.get(key);
                if (last != null && now - last.longValue() < TYPING_INTERVAL_MILLIS) {
                    continue;
                }
                String channel = typingChannelOf(binding);
                if (channel.length() == 0) {
                    continue;
                }
                this.typingSentMillis.put(key, Long.valueOf(now));
                try {
                    DiscordHttp.postTyping(token, channel);
                } catch (IOException ignored) {
                    // Presence that did not arrive is presence not worth
                    // chasing; the next keystroke asks again.
                } catch (RuntimeException ignored) {
                    // As above.
                }
            }
        }

        /**
         * Where the bot types for a binding: the Discord channel it
         * reads, or the channel its webhook posts to, asked once.
         */
        private String typingChannelOf(DiscordChannelBinding binding) {
            if (binding.getDiscordChannelId().length() > 0) {
                return binding.getDiscordChannelId();
            }
            DiscordJson.WebhookInfo info = webhookInfo(binding.getWebhookUrl());
            return info == null || info.channelId == null ? "" : info.channelId;
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
            DiscordChannelBinding discord = this.bindings.discordChannel();
            if (discord == null) {
                return;
            }
            status.flush(LostTalesConfig.discordBotToken.trim(),
                    discord.getDiscordChannelId(),
                    Math.max(60L, Math.min(3600L,
                            LostTalesConfig.discordChannelStatusIntervalSeconds))
                            * 1000L,
                    finalAttempt);
        }

        /**
         * Reads what arrived in every bound Discord channel since the
         * last look. The first successful read of a channel only learns
         * its newest id, so history before the server started is never
         * replayed into the game. A channel the bot cannot see is given
         * up for the session on its own; the others go on.
         */
        private void poll() throws IOException {
            String token = LostTalesConfig.discordBotToken.trim();
            for (DiscordChannelBinding binding : this.bindings.reading()) {
                ChannelCursor cursor = cursorFor(binding);
                if (cursor.disabled) {
                    continue;
                }
                DiscordHttp.Reply reply = DiscordHttp.getMessages(token,
                        binding.getDiscordChannelId(),
                        cursor.primed ? cursor.after : "",
                        cursor.primed ? DiscordJson.PAGE_SIZE : 1);
                if (readRefused(reply.status, binding, cursor)) {
                    continue;
                }
                if (reply.status == 429) {
                    throw new RateLimited(DiscordJson.retryAfterMillis(reply.body));
                }
                if (!reply.isSuccess()) {
                    throw new IOException("Discord replied HTTP " + reply.status
                            + " to a read of " + binding.key());
                }
                List<DiscordJson.Message> messages =
                        DiscordJson.parseMessages(reply.body);
                if (!messages.isEmpty()) {
                    cursor.after = messages.get(messages.size() - 1).id;
                }
                if (!cursor.primed) {
                    cursor.primed = true;
                    continue;
                }
                for (DiscordJson.Message message : messages) {
                    // The webhooks' own posts come back as bot messages:
                    // the second guard against a line going round, after
                    // the chat service's own rule that a line from
                    // Discord is never posted back.
                    if (message.bot) {
                        continue;
                    }
                    String name = DiscordMessageSanitizer.inboundName(
                            message.authorName);
                    String text = DiscordMessageSanitizer.inbound(
                            message.content, message.mentionNames);
                    if (name.length() > 0 && text.length() > 0) {
                        rememberAuthor(name, message.authorId);
                        enqueueInbound(new Inbound(Inbound.Kind.MESSAGE, name,
                                message.authorId, text, message.id,
                                message.referencedMessageId, binding.key()));
                        // Watched from now on, so a later edit or deletion
                        // of it follows the message into the game.
                        cursor.sweep.track(message);
                    }
                }
            }
        }

        private ChannelCursor cursorFor(DiscordChannelBinding binding) {
            ChannelCursor cursor = this.cursors.get(binding.getDiscordChannelId());
            if (cursor == null) {
                cursor = new ChannelCursor();
                this.cursors.put(binding.getDiscordChannelId(), cursor);
            }
            return cursor;
        }

        /**
         * Re-reads each read channel's newest page and compares it with
         * the messages relayed from it, so a Discord member's own edits
         * and deletions follow their messages into the game — the
         * polling API says nothing about either, which makes looking
         * again the only way to hear of them. Only the page's reach is
         * watched: a message older than the newest
         * {@link DiscordJson#PAGE_SIZE} is out of sight and stays as it
         * was delivered. A channel with nothing watched costs no second
         * request.
         */
        private void sweepChannels() throws IOException {
            String token = LostTalesConfig.discordBotToken.trim();
            for (DiscordChannelBinding binding : this.bindings.reading()) {
                ChannelCursor cursor = cursorFor(binding);
                if (cursor.disabled || cursor.sweep.isEmpty()) {
                    continue;
                }
                DiscordHttp.Reply reply = DiscordHttp.getMessages(token,
                        binding.getDiscordChannelId(), "", DiscordJson.PAGE_SIZE);
                if (readRefused(reply.status, binding, cursor)) {
                    continue;
                }
                if (reply.status == 429) {
                    throw new RateLimited(DiscordJson.retryAfterMillis(reply.body));
                }
                if (!reply.isSuccess()) {
                    throw new IOException("Discord replied HTTP " + reply.status
                            + " to a sweep of " + binding.key());
                }
                DiscordMessageSweep.Changes changes = cursor.sweep.apply(
                        DiscordJson.parseMessages(reply.body));
                for (DiscordJson.Message message : changes.edited) {
                    String text = DiscordMessageSanitizer.inbound(
                            message.content, message.mentionNames);
                    // Edited down to nothing sayable — an attachment left
                    // alone — keeps the words it was delivered with.
                    if (text.length() > 0) {
                        enqueueInbound(new Inbound(Inbound.Kind.EDIT, "", "",
                                text, message.id, "", binding.key()));
                    }
                }
                for (int index = 0; index < changes.deletedIds.size(); index++) {
                    enqueueInbound(new Inbound(Inbound.Kind.DELETE, "", "", "",
                            changes.deletedIds.get(index), "", binding.key()));
                }
            }
        }

        /**
         * Whether Discord refused a read. A refused token stops reading
         * everywhere for the session; a channel the bot cannot see, or
         * one that is gone, stops reading that binding alone. Each is
         * said once.
         */
        private boolean readRefused(int status, DiscordChannelBinding binding,
                                    ChannelCursor cursor) {
            if (status == 401) {
                this.readingDisabled = true;
                FMLLog.severe("[%s] Discord refused the bot (HTTP 401): check "
                        + "the token; Discord-to-game relay is off until the "
                        + "server restarts", LostTalesMetaData.MOD_ID);
                return true;
            }
            if (status == 403 || status == 404) {
                cursor.disabled = true;
                FMLLog.severe("[%s] Discord refused a read of channel %s for "
                        + "binding %s (HTTP %d): check that the bot is in the "
                        + "server with access to that channel and the Message "
                        + "Content intent, and that the channel still exists; "
                        + "reading it is off until the server restarts",
                        LostTalesMetaData.MOD_ID, binding.getDiscordChannelId(),
                        binding.key(), Integer.valueOf(status));
                return true;
            }
            return false;
        }

        private void flushOutbound() throws IOException {
            Outbound next;
            while ((next = outbound.peek()) != null) {
                DiscordJson.LineCard card = null;
                DiscordHttp.Reply reply;
                // A post goes where its binding says; a correction goes
                // where its post went, which only the link remembers.
                String key = next.kind == Outbound.Kind.POST
                        ? next.bindingKey : links.bindingKeyOf(next.messageId);
                String webhook = webhookFor(next, key);
                if (webhook.length() == 0 || this.postingDisabled.contains(key)) {
                    // Nowhere to send it: a binding without a webhook, or
                    // one Discord has refused. Nothing to retry.
                    outbound.poll();
                    outboundCount.decrementAndGet();
                    continue;
                }
                if (next.kind == Outbound.Kind.POST && next.notice != null) {
                    // A notice is an embed under the webhook's own name;
                    // it answers nothing and is never edited or linked.
                    reply = DiscordHttp.postWebhook(webhook,
                            DiscordJson.webhookEmbedBody(next.notice));
                } else if (next.kind == Outbound.Kind.POST) {
                    card = (next.card == null ? DiscordJson.LineCard.NONE
                            : next.card).withHeader(replyHeader(next, webhook));
                    reply = DiscordHttp.postWebhook(webhook,
                            DiscordJson.webhookLineBody(next.username,
                                    next.avatarUrl, card, next.message));
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
                                    discordId, DiscordJson.webhookLineEditBody(
                                            links.cardOf(next.messageId),
                                            next.message))
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
                if (next.kind == Outbound.Kind.POST
                        && (reply.status == 401 || reply.status == 404)) {
                    // The webhook itself is gone or refused: no post of
                    // this binding can succeed until it is fixed and the
                    // server restarts. The other bindings go on.
                    this.postingDisabled.add(key);
                    FMLLog.severe("[%s] Discord refused the webhook of binding "
                            + "%s (HTTP %d): the webhook was deleted or its URL "
                            + "is wrong; posting there is off until the server "
                            + "restarts", LostTalesMetaData.MOD_ID,
                            key.length() == 0 ? "the notices" : key,
                            Integer.valueOf(reply.status));
                    continue;
                }
                if (!reply.isSuccess()) {
                    throw new IOException("Discord replied HTTP " + reply.status
                            + " to a webhook "
                            + (next.kind == Outbound.Kind.POST ? "post"
                                    : next.kind == Outbound.Kind.EDIT
                                            ? "edit" : "delete"));
                }
                if (next.kind == Outbound.Kind.POST && next.notice == null) {
                    // The post's Discord id, from the wait=true body:
                    // what a reply from either side finds the message
                    // by, and what an edit or a removal follows it by,
                    // with the card an edit has to draw again. A body
                    // that does not parse leaves the line unlinked.
                    links.link(next.messageId,
                            DiscordJson.parseCreatedMessageId(reply.body),
                            next.bindingKey, card);
                }
            }
        }

        /**
         * The webhook an entry goes through: the notices' own for a
         * notice, else the webhook of the binding the entry names.
         * Empty for a binding that has none or is not known.
         */
        private String webhookFor(Outbound next, String key) {
            if (next.notice != null) {
                return this.bindings.noticeWebhookUrl();
            }
            DiscordChannelBinding binding = this.bindings.byKey(key);
            return binding == null ? "" : binding.getWebhookUrl();
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
            if (!this.webhookInfos.containsKey(webhookUrl)) {
                DiscordJson.WebhookInfo info = null;
                try {
                    DiscordHttp.Reply reply =
                            DiscordHttp.getWebhookInfo(webhookUrl);
                    if (reply.isSuccess()) {
                        info = DiscordJson.parseWebhookInfo(reply.body);
                    }
                } catch (IOException ignored) {
                    // As above: plain quotes, not a bridge failure.
                } catch (RuntimeException ignored) {
                    // As above.
                }
                this.webhookInfos.put(webhookUrl, info);
            }
            return this.webhookInfos.get(webhookUrl);
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
