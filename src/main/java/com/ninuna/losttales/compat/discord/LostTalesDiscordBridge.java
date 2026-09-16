package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatDeliveryMark;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.server.ChatHistory;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.chat.emoji.ChatForeignEmoji;
import com.ninuna.losttales.compat.discord.gateway.DiscordGatewayClient;
import com.ninuna.losttales.compat.discord.gateway.DiscordGatewayProtocol;
import com.ninuna.losttales.config.LostTalesConfig;
import com.google.gson.JsonObject;
import com.ninuna.losttales.core.LostTalesClassTransformer;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatDeliveryMarkPacket;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import com.ninuna.losttales.chat.profanity.ChatProfanityCatalog;
import com.ninuna.losttales.chat.profanity.ChatProfanityFilter;
import com.ninuna.losttales.chat.profanity.ChatProfanityMode;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * The server's own Discord bridge, no library behind it: the bot sits
 * on Discord's gateway ({@code compat.discord.gateway}) and hands each
 * message in a bound channel over as it is sent, while a worker thread
 * polls those channels through the REST API whenever the gateway is
 * down, posts the game's lines and the server's notices
 * to the webhooks they name, and keeps every bound Discord channel's
 * topic saying whether the server is up, while the server thread only
 * ever touches the running worker's bounded queues and its wanted
 * topics. Which game channel
 * is tied to which Discord channels, and which way, is
 * {@link DiscordChannelBindings}: OOC &amp; Discord by default, and any
 * other channel the channel itself allows, each to as many Discord
 * channels as it is bound to, in any guild the bot is in — every line
 * posted as plain text under the sender's name to each of them — while
 * the Party channel, the console and whispers never leave the game.
 * Discord messages are handed to
 * {@link LostTalesChatService#sendFromDiscord} on the server tick, so
 * they reach players through the same packet as every other line; game
 * lines are queued here by the chat service and posted off-thread.
 *
 * <p>The server's own notices — started, shutting down, a player joined,
 * left, died or earned an achievement — are {@link DiscordNotice}s,
 * posted as one coloured embed each under the webhook's own name, to
 * every destination the bindings post to, once each;
 * {@link DiscordGameEventRelay} turns the game's events into them, and
 * each kind answers to its own config switch here. The topic is
 * recomputed from the live player list on the tick after a start, join
 * or leave, so a burst of joins is one write. At shutdown the farewell
 * and the offline topic are queued first and the worker is given a
 * bounded moment to send them; nothing waits longer than that.</p>
 *
 * <p>Everything fails closed. A bad token or a missing permission stops
 * the inbound side (or the topic) for the session with one severe log
 * line; a rate limit is honoured for exactly the time Discord asks, and
 * on a webhook it holds back that webhook's posts alone, each webhook
 * being a lane of its own ({@link DiscordOutboundLanes}); a lane whose
 * bucket Discord's headers say is spent waits for the reset instead of
 * sending into the limit ({@link DiscordRateBuckets}); any other
 * failure backs off, doubling up to a minute, and is logged once
 * on the way down and once on the way back. The webhook's own posts
 * come back as bot messages and are ignored, so nothing echoes. The
 * token and the webhook URL are never logged.</p>
 *
 * <p>A player's own line that is slow to reach Discord, or will not
 * reach it, is marked on that player's screen alone: the worker follows
 * each line's posts ({@link DiscordDeliveryTracker}), and the server
 * thread sends what it finds to the sender while they are online.</p>
 *
 * <p>Everything done to a message's Discord copy once it exists — the
 * bot's reactions, edits and deletions, reply headers and jump links
 * either way, and word about the copy coming back — asks
 * {@link DiscordCopyLiveness} first: a game channel and a Discord
 * channel that are not bound to each other are left alone both ways,
 * and their links are kept, so binding them again brings it all back.
 * An entry the rule refuses is spent quietly.</p>
 */
public final class LostTalesDiscordBridge {
    private static final LostTalesDiscordBridge INSTANCE =
            new LostTalesDiscordBridge();
    private static final int MAX_QUEUED_INBOUND = 256;
    /**
     * Entries a worker's intake holds. A line with no room for every
     * post it makes queues none of them.
     */
    static final int MAX_QUEUED_OUTBOUND = 256;
    /** Shortest gap between two typing pings; Discord's own lasts ten
     *  seconds, so anything more often is spent for nothing. */
    private static final long TYPING_INTERVAL_MILLIS = 8000L;
    private static final int MAX_INBOUND_PER_TICK = 8;
    /** Delivery marks sent to their senders a tick, at most. */
    private static final int MAX_MARKS_PER_TICK = 16;
    /** Delivery marks a worker holds for the server thread, at most. */
    private static final int MAX_QUEUED_MARKS = 256;
    /** Members remembered by name for the mute command. */
    private static final int MAX_RECENT_AUTHORS = 256;
    private static final long MIN_BACKOFF_MILLIS =
            DiscordOutboundLanes.MIN_RETRY_MILLIS;
    private static final long MAX_BACKOFF_MILLIS =
            DiscordOutboundLanes.MAX_RETRY_MILLIS;
    /**
     * Tries a webhook post, edit or removal gets before it is given up:
     * with its lane's pause doubling from five seconds, about a minute
     * and a quarter of the webhook failing, after which that one entry
     * no longer holds up the ones behind it.
     */
    private static final int MAX_SEND_ATTEMPTS = 5;
    /**
     * The shortest pause between two passes of the worker that nothing
     * woke, so a lane or a read already due cannot spin it.
     */
    private static final long MIN_PASS_GAP_MILLIS = 250L;
    /**
     * What a send answers besides a pause Discord asked for: the entry
     * is done without reaching Discord, or Discord took it. Only a
     * delivery ends a lane's run of failures.
     */
    private static final long SPENT = 0L;
    private static final long DELIVERED = -1L;
    /**
     * How long a stop waits for the worker's last posts: enough for a
     * webhook post and a topic write on a healthy link, and a bound on
     * a shutdown with a stalled one.
     */
    private static final long STOP_JOIN_MILLIS = 5000L;

    private final Queue<Inbound> inbound = new ConcurrentLinkedQueue<Inbound>();
    private final AtomicInteger inboundCount = new AtomicInteger();
    /**
     * Which game message is which Discord message, both ways: filled by
     * the worker as posts are confirmed and by the tick as Discord lines
     * are delivered, read wherever a reply crosses the bridge. A map of
     * its own for every server run, brought back from the world save by
     * {@link #restoreLinks} and left with it by {@link #releaseLinks};
     * a stop or a reload keeps it. Each worker holds the map it was
     * started with.
     */
    private volatile DiscordMessageLinks links = new DiscordMessageLinks();
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
    /**
     * The bot's gateway connection, when the config asks for one: it
     * hands Discord messages in as they are sent and hears the slash
     * commands; the worker's polling stands in while it is down.
     */
    private volatile DiscordGatewayClient gateway;
    private volatile boolean gatewayLive;
    /** The bot's application id, from the gateway's READY; empty until then. */
    private volatile String applicationId = "";
    /** The newest message id seen per Discord channel, by either reader. */
    private final Map<String, String> lastSeenByChannel =
            new ConcurrentHashMap<String, String>();
    /** Message ids already queued, so the two readers never deliver one twice. */
    private final LinkedHashSet<String> recentInboundIds = new LinkedHashSet<String>();
    private static final int MAX_RECENT_INBOUND_IDS = 512;
    private volatile long serverStartedMillis;
    /**
     * Where the bindings' findings go: the log, once each at start — a
     * trimmed entry as a warning, a refused one as an error, so a
     * Discord channel bound into two game channels is not missed.
     */
    private static final DiscordChannelBindings.Warnings LOG_WARNINGS =
            new DiscordChannelBindings.Warnings() {
                @Override
                public void warn(String message) {
                    FMLLog.warning("[%s] %s", LostTalesMetaData.MOD_ID, message);
                }

                @Override
                public void refuse(String message) {
                    FMLLog.severe("[%s] %s", LostTalesMetaData.MOD_ID, message);
                }
            };
    /**
     * Where the chat history says a message was said: what the liveness
     * rule asks of a message on any thread, the history being
     * synchronized.
     */
    private static final DiscordCopyLiveness.Places HISTORY =
            new DiscordCopyLiveness.Places() {
                @Override
                public ChatChannel channelOf(long messageId) {
                    return ChatHistory.channelOf(messageId);
                }

                @Override
                public String factionScopeOf(long messageId) {
                    return ChatHistory.factionScopeOf(messageId);
                }
            };
    private volatile boolean statusRefreshRequested;
    private volatile Worker worker;
    private boolean registered;

    private LostTalesDiscordBridge() {}

    public static LostTalesDiscordBridge getInstance() {
        return INSTANCE;
    }

    /**
     * Starts the worker when the config enables the bridge; idempotent.
     * A channel whose topic the running worker keeps and the new config
     * does not is told the server is offline as that worker stops.
     */
    public synchronized void start() {
        boolean enabled = LostTalesConfig.discordEnabled;
        boolean botPresent = LostTalesConfig.discordBotToken.trim().length() > 0;
        DiscordChannelBindings configured = enabled
                ? DiscordChannelBindings.parse(LostTalesConfig.discordChannelBindings,
                        botPresent, LOG_WARNINGS)
                : DiscordChannelBindings.EMPTY;
        boolean manages = enabled && LostTalesConfig.discordChannelStatus && botPresent
                && !configured.channels().isEmpty();
        releaseTopics(configured, manages, botPresent);
        stop();
        if (!enabled) {
            return;
        }
        this.bindings = configured;
        boolean reads = configured.readsAnything();
        boolean posts = configured.sendsAnything();
        if (!reads && !posts && !manages) {
            FMLLog.warning("[%s] Discord bridge is enabled but no binding "
                    + "reads a Discord channel, posts to a webhook, or keeps "
                    + "a topic; nothing will be relayed", LostTalesMetaData.MOD_ID);
            return;
        }
        // Registered for this run and unregistered with it, so a stopped
        // bridge hears no ticks, logins or logouts: the relay would
        // otherwise go on queueing notices nobody sends.
        FMLCommonHandler.instance().bus().register(this);
        FMLCommonHandler.instance().bus().register(this.relay);
        this.registered = true;
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
        if (botPresent && LostTalesConfig.discordGateway
                && (reads || LostTalesConfig.discordSlashCommands)) {
            DiscordGatewayClient client = new DiscordGatewayClient(
                    LostTalesConfig.discordBotToken.trim(), new GatewayListener(configured));
            this.gateway = client;
            client.start();
        }
        FMLLog.info("[%s] Discord bridge started (%s%s): %s", LostTalesMetaData.MOD_ID,
                reads && posts ? "both ways" : reads ? "Discord to game"
                        : posts ? "game to Discord" : "topic only",
                manages && (reads || posts) ? ", channel topic" : "",
                configured.describeForLog());
    }

    /**
     * Asks the running worker to say the server is offline, once, on
     * every channel whose topic it keeps and the next start will not: a
     * channel that leaves the bindings (unbound, removed or switched
     * off), or whose topic is no longer kept, would otherwise go on
     * saying the server is online. The worker writes it as it stops,
     * within the same bounded wait as a shutdown. The write goes with
     * the token the config holds now, so without one nothing is asked.
     */
    private void releaseTopics(DiscordChannelBindings next, boolean nextManages,
                               boolean botPresent) {
        Worker running = this.worker;
        if (running == null || !botPresent) {
            return;
        }
        for (String channelId : topicsLeft(running.statuses.keySet(), next, nextManages)) {
            running.statuses.get(channelId).request(DiscordServerNotices.offlineTopic());
        }
    }

    /**
     * The channels among {@code kept} whose topics a start with
     * {@code next} does not keep, in order: every one of them when that
     * start keeps no topic at all.
     */
    static List<String> topicsLeft(Collection<String> kept, DiscordChannelBindings next,
                                   boolean nextManages) {
        List<String> left = new ArrayList<String>();
        for (String channelId : kept) {
            if (!nextManages || !next.channels().contains(channelId)) {
                left.add(channelId);
            }
        }
        return left;
    }

    /**
     * Stops the worker after a bounded wait for what it still has to
     * send, then forgets everything queued. The message links stay: they
     * belong to the world, so a reload goes on with them.
     */
    public synchronized void stop() {
        Worker running = this.worker;
        this.worker = null;
        DiscordGatewayClient client = this.gateway;
        this.gateway = null;
        this.gatewayLive = false;
        if (client != null) {
            client.shutdown();
        }
        this.applicationId = "";
        this.lastSeenByChannel.clear();
        synchronized (this.recentInboundIds) {
            this.recentInboundIds.clear();
        }
        if (this.registered) {
            FMLCommonHandler.instance().bus().unregister(this);
            FMLCommonHandler.instance().bus().unregister(this.relay);
            this.registered = false;
        }
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
            // No tick drains a stopped worker, so what it said as it
            // stopped, above all that the lines it never sent will not
            // arrive, goes to their senders now.
            sendMarks(running, MAX_QUEUED_MARKS);
            running.forgetIntake();
        }
        this.bindings = DiscordChannelBindings.EMPTY;
        this.inbound.clear();
        this.inboundCount.set(0);
        synchronized (this.recentAuthors) {
            this.recentAuthors.clear();
        }
        this.statusRefreshRequested = false;
    }

    /**
     * Takes the links this world's save kept, as the server starts:
     * after the chat history is restored, since only links to messages
     * it still holds come back, and before the bridge starts. The map is
     * a new one, so nothing reaches this world from the one before; a
     * worker still finishing for that one writes into the map it was
     * started with. Server thread.
     */
    public synchronized void restoreLinks(MinecraftServer server) {
        DiscordMessageLinks fresh = new DiscordMessageLinks();
        this.links = fresh;
        DiscordMessageLinkStorage.restore(server, fresh);
    }

    /**
     * Leaves the links with the world save as the server stops, once
     * the bridge has stopped, and empties the live map for whatever the
     * process serves next. Server thread.
     */
    public synchronized void releaseLinks() {
        DiscordMessageLinkStorage.release();
        this.links = new DiscordMessageLinks();
    }

    /** Test hook: the live links. */
    DiscordMessageLinks links() {
        return this.links;
    }

    /**
     * Test hook: makes a worker for {@code configured} the current one
     * without starting its thread or stopping the one before, which is
     * how a worker still finishing after a reload is left.
     */
    synchronized Thread installIdleWorker(DiscordChannelBindings configured) {
        Worker idle = new Worker(configured, configured.readsAnything(),
                configured.sendsAnything(), false);
        this.bindings = configured;
        this.worker = idle;
        return idle;
    }

    /** Test hook: the binding ids of the posts waiting in a worker's intake, in order. */
    List<String> queuedPostsOf(Thread worker) {
        List<String> ids = new ArrayList<String>();
        if (worker instanceof Worker) {
            for (Outbound entry : ((Worker) worker).outbound) {
                if (entry.kind == Outbound.Kind.POST) {
                    ids.add(entry.bindingKey);
                }
            }
        }
        return ids;
    }

    /** Test hook: the bindings a worker was asked to show typing in. */
    Set<String> typingOf(Thread worker) {
        return worker instanceof Worker
                ? new HashSet<String>(((Worker) worker).typingRequests)
                : Collections.<String>emptySet();
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

    /** The bindings in force, for the chat service's routing questions. */
    public DiscordChannelBindings bindings() {
        return this.bindings;
    }

    /**
     * Queues a game line for Discord through every binding of its
     * channel — for the Faction channel, of the sender's faction — that
     * posts; dropped where none does. Each post is the line as plain
     * text under the sender's name and picture; a Discord channel holds
     * one game channel's lines, so nothing on the post need say which.
     * {@code messageId} is the line's own name, kept so each post's
     * Discord copy can be linked back to it; {@code reply} is the quote
     * the line was distributed with, opening the post as a line of
     * subtext — pointing at the Discord original in that very channel
     * when the bridge knows it. {@code senderId} is the account that said
     * the line, told alone how its posts go: a clock while they wait, a
     * crimson mark when one will not arrive. A line the queue has no room
     * for is queued nowhere, and its sender is told at once.
     */
    public void relayToDiscord(ChatChannel channel, String factionId,
                               String username, String avatarUrl,
                               String message, long messageId,
                               ChatReplyReference reply, UUID senderId) {
        Worker running = this.worker;
        if (running == null || !running.posts || message == null
                || message.length() == 0) {
            return;
        }
        long queuedAt = System.currentTimeMillis();
        List<Outbound> copies = new ArrayList<Outbound>();
        // Each id is looked up in the bindings of the worker it is
        // queued for, so it names the entry it was taken from.
        for (DiscordChannelBinding binding : running.bindings.forGame(channel, factionId)) {
            if (binding.sendsToDiscord()) {
                copies.add(new Outbound(Outbound.Kind.POST, username,
                        avatarUrl, message, messageId,
                        reply == null ? ChatReplyReference.NONE : reply, null,
                        binding.id(), channel, factionId, senderId, queuedAt));
            }
        }
        if (!copies.isEmpty() && !enqueueOutbound(running, copies)) {
            sendMark(new DiscordDeliveryTracker.Mark(senderId, messageId,
                    ChatDeliveryMark.State.FAILED,
                    ChatDeliveryMark.Reason.QUEUE_FULL));
        }
    }

    /**
     * Queues one of the server's own notices as an embed under the
     * webhook's own name and picture, once for every destination the
     * bindings post to — each Discord channel of every guild the bridge
     * reaches hears the server start, a player join, a death — and to
     * none when nothing posts; dropped when its kind is switched off in
     * the config, and under the same conditions as a line. The
     * destinations are the bindings' own answer, so no event has a
     * routing of its own.
     */
    public void announce(DiscordNotice notice) {
        Worker running = this.worker;
        if (running == null || notice == null || notice.getText().length() == 0
                || !isEnabled(notice.getKind())) {
            return;
        }
        for (DiscordChannelBinding destination : running.bindings.destinations()) {
            enqueueOutbound(running, new Outbound(Outbound.Kind.POST, "", "",
                    notice.getText(), ChatMessageIds.NONE,
                    ChatReplyReference.NONE, notice, destination.id(), null, ""));
        }
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
     * Says that a game message was rewritten, so each of its Discord
     * copies that is live is rewritten too, keeping the reply header it
     * was posted under. Queued behind everything already outbound: the
     * queue is posted in order, so an edit always finds the link its
     * own post registered. Where the message was said is read here, on
     * the server thread, and travels with the entry; which webhook each
     * copy lives behind is the worker's to find. A message with no live
     * copy resolves to nothing there and the entry is spent.
     */
    public void relayEdit(long messageId, String message) {
        if (message == null || message.length() == 0 || !isPosting()) {
            return;
        }
        ChatChannel channel = ChatHistory.channelOf(messageId);
        if (channel != null) {
            enqueueOutbound(new Outbound(Outbound.Kind.EDIT, "", "",
                    message, messageId, ChatReplyReference.NONE, null,
                    "", channel, ChatHistory.factionScopeOf(messageId)));
        }
    }

    /**
     * Says that a game message was taken back, so each of its live
     * Discord copies is deleted too, on the same terms as an edit.
     * {@code channel} and {@code factionScope} say where it was said:
     * the history has forgotten the message by now, so the chat service
     * reads them before it removes it.
     */
    public void relayDelete(long messageId, ChatChannel channel,
                            String factionScope) {
        if (channel != null) {
            enqueueOutbound(new Outbound(Outbound.Kind.DELETE, "", "", "",
                    messageId, ChatReplyReference.NONE, null, "", channel,
                    factionScope));
        }
    }

    /**
     * Says that the players' reaction with an emoji came to a game
     * message, or went from it, so the bot's own reaction on each of its
     * live Discord copies comes or goes with it: one reaction on Discord
     * stands for every player who reacted, since a bot is one member
     * there. {@code emoji} is a reaction key: a registry emoji goes as
     * its Unicode form, a foreign one as Discord named it. An emoji of
     * the mod's own, with no Unicode form, has nothing to be on Discord
     * and stays in the game.
     */
    public void relayReaction(long messageId, String emoji, boolean add) {
        String form = ChatForeignEmoji.discordForm(emoji);
        if (form.length() == 0 || !isPosting()) {
            return;
        }
        ChatChannel channel = ChatHistory.channelOf(messageId);
        if (channel != null) {
            enqueueOutbound(new Outbound(add ? Outbound.Kind.REACT
                    : Outbound.Kind.UNREACT, "", "", form,
                    messageId, ChatReplyReference.NONE, null, "", channel,
                    ChatHistory.factionScopeOf(messageId)));
        }
    }

    private void enqueueOutbound(Outbound entry) {
        enqueueOutbound(this.worker, entry);
    }

    private boolean enqueueOutbound(Worker running, Outbound entry) {
        return enqueueOutbound(running, Collections.singletonList(entry));
    }

    /**
     * Queues entries for {@code running} alone and wakes it, so they go
     * out as soon as they are queued rather than at the worker's next
     * pass: all of them, or none when the intake has no room for all,
     * which is what the posts of one line need. Each worker has an
     * intake of its own, so a worker still finishing after a reload
     * never takes an entry queued for the next one, whose bindings and
     * ids it does not share. Only the server thread adds to an intake,
     * so the room counted here is still there when the entries go in.
     * Answers whether they were queued.
     */
    private boolean enqueueOutbound(Worker running, List<Outbound> entries) {
        if (running == null || !running.posts || entries.isEmpty()
                || running.outboundCount.get() + entries.size()
                        > MAX_QUEUED_OUTBOUND) {
            return false;
        }
        running.outboundCount.addAndGet(entries.size());
        running.outbound.addAll(entries);
        running.wake();
        return true;
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
     * <p>Only this direction crosses. Discord publishes a member's own
     * typing on the gateway as {@code TYPING_START}, which the bridge
     * does not subscribe to (it asks for no typing intent), so a
     * Discord member typing is not shown in game.</p>
     */
    public void relayTyping(ChatChannel channel, String factionId) {
        Worker running = this.worker;
        if (running == null) {
            return;
        }
        boolean asked = false;
        for (DiscordChannelBinding binding : running.bindings.forGame(channel, factionId)) {
            if (binding.sendsToDiscord()) {
                running.typingRequests.add(binding.id());
                asked = true;
            }
        }
        if (asked) {
            running.wake();
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
        this.serverStartedMillis = System.currentTimeMillis();
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
            requestTopic(DiscordServerNotices.offlineTopic());
        }
        this.statusRefreshRequested = false;
    }

    /** States the wanted topic on every channel whose topic the running worker keeps. */
    private void requestTopic(String topic) {
        Worker running = this.worker;
        if (running == null) {
            return;
        }
        for (DiscordChannelStatus status : running.statuses.values()) {
            status.request(topic);
        }
    }

    /**
     * Delivers queued Discord messages on the server thread, a few per
     * tick, sends the worker's delivery marks to their senders, and
     * restates the wanted topic from the live player list when something
     * asked for it.
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
        sendMarks(running, MAX_MARKS_PER_TICK);
        if (this.statusRefreshRequested && running.manages) {
            this.statusRefreshRequested = false;
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                requestTopic(DiscordServerNotices.onlineTopic(
                        server.getCurrentPlayerCount(),
                        server.getMaxPlayers()));
            }
        }
    }

    /**
     * Sends at most {@code limit} of a worker's delivery marks to their
     * senders. Server thread.
     */
    private static void sendMarks(Worker from, int limit) {
        int handled = 0;
        DiscordDeliveryTracker.Mark mark;
        while (handled < limit && (mark = from.marks.poll()) != null) {
            from.markCount.decrementAndGet();
            sendMark(mark);
            handled++;
        }
    }

    /**
     * Tells a line's sender how its post is going while they are online;
     * a sender who has left is not told. Server thread.
     */
    private static void sendMark(DiscordDeliveryTracker.Mark mark) {
        if (mark.senderId == null || !ChatMessageIds.isServerId(mark.messageId)) {
            return;
        }
        EntityPlayerMP sender = LostTalesServerPlayers.findOnline(mark.senderId);
        if (sender == null) {
            return;
        }
        try {
            LostTalesNetworkHandler.CHANNEL.sendTo(new LostTalesChatDeliveryMarkPacket(
                    mark.messageId, mark.state, mark.reason), sender);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Could not tell a player how their Discord post "
                    + "is going: %s", LostTalesMetaData.MOD_ID, exception.toString());
        }
    }

    /** One inbound entry, on the server thread. */
    private void deliver(Inbound message) {
        if (message.kind == Inbound.Kind.COMMAND) {
            answerCommand(message.interaction);
            return;
        }
        if (message.kind == Inbound.Kind.EDIT) {
            // Word about a message already delivered: it reaches the
            // game only while the bridge knows which line it is and the
            // Discord channel it came from is read into that line's own
            // game channel.
            long target = liveTarget(message);
            if (target != ChatMessageIds.NONE) {
                LostTalesChatService.editFromDiscord(target, message.text);
            }
            return;
        }
        if (message.kind == Inbound.Kind.REACT_ADD
                || message.kind == Inbound.Kind.REACT_REMOVE
                || message.kind == Inbound.Kind.REACT_CLEAR) {
            // A reaction reaches the game on the same terms as an edit.
            // The text is the reaction key, empty for an emoji sent
            // without a name and for a clear of every emoji; a custom
            // emoji also carries its id, by which the chat finds the key
            // the message holds for it under any name.
            long target = liveTarget(message);
            if (target != ChatMessageIds.NONE) {
                String emoji = message.text.length() == 0 ? null
                        : message.text;
                if (message.kind == Inbound.Kind.REACT_CLEAR) {
                    LostTalesChatService.clearDiscordReactions(target, emoji,
                            message.emojiId);
                } else {
                    LostTalesChatService.reactFromDiscord(target,
                            message.authorId, message.name, emoji,
                            message.emojiId,
                            message.kind == Inbound.Kind.REACT_ADD);
                }
            }
            return;
        }
        if (message.kind == Inbound.Kind.DELETE) {
            long target = liveTarget(message);
            if (target != ChatMessageIds.NONE) {
                LostTalesChatService.deleteFromDiscord(target);
            }
            return;
        }
        // Delivered into the channel of the binding that reads the
        // Discord channel it came from, and only while one does.
        DiscordChannelBinding binding = this.bindings.readerOf(message.discordChannelId);
        if (binding == null) {
            return;
        }
        // A Discord reply names a Discord id; when that id is a copy
        // the bridge has seen cross — either way — in this very Discord
        // channel, of a line of the game channel this Discord channel is
        // read into now, the line is delivered quoting it, exactly as a
        // player's reply is. One referencing a message the bridge holds
        // no link for, one the history no longer keeps, one living in
        // another Discord channel, or one of a game channel this Discord
        // channel is no longer bound to goes out plain: a quote never
        // carries words from one bound channel into another.
        String destination = channelDestination(binding.getDiscordChannelId());
        long referenced = DiscordCopyLiveness.quotedBy(this.links, this.bindings,
                HISTORY, message.referencedDiscordId, message.discordChannelId);
        ChatReplyReference reply = referenced == ChatMessageIds.NONE
                ? ChatReplyReference.NONE
                : ChatHistory.quoteForDiscordChannel(referenced);
        long messageId = LostTalesChatService.sendFromDiscord(
                binding.getChannel(), binding.getFactionScope(),
                message.name, message.authorId, message.text, reply);
        // A line that came from Discord went through no webhook of ours.
        this.links.link(messageId, message.discordId, "", destination, "");
    }

    /**
     * The game message word from Discord is about, or
     * {@link ChatMessageIds#NONE}: one the bridge links, said in the
     * game channel the Discord channel the word came from is read into
     * now ({@link DiscordCopyLiveness#inboundTarget}).
     */
    private long liveTarget(Inbound message) {
        return DiscordCopyLiveness.inboundTarget(this.links, this.bindings,
                HISTORY, message.discordId, message.discordChannelId);
    }

    /**
     * How a copy names the Discord channel it lives in, for a Discord
     * line read from the channel and a game line posted into it alike,
     * so the two meet whichever way the copy was made.
     */
    private static String channelDestination(String discordChannelId) {
        return DiscordCopyLiveness.CHANNEL_PREFIX + discordChannelId;
    }

    /**
     * How a Discord line's jump links are spelled for the game: a link
     * to a message the bridge carried either way, in a Discord channel
     * one of {@code bound}'s bindings reads into that message's own game
     * channel, becomes {@code #Channel/<id>}; any other stays the URL it
     * is.
     */
    private DiscordMessageLinkRewriter.Resolver linkResolver(
            final DiscordChannelBindings bound) {
        return new DiscordMessageLinkRewriter.Resolver() {
            @Override
            public String jumpUrl(ChatChannel channel, long messageId) {
                return "";
            }

            @Override
            public String gameLink(String guildId, String channelId,
                                   String discordMessageId) {
                return DiscordCopyLiveness.gameLink(links, bound, HISTORY,
                        channelId, discordMessageId);
            }
        };
    }

    private void enqueueInbound(Inbound entry) {
        if (this.inboundCount.get() >= MAX_QUEUED_INBOUND) {
            return;
        }
        if (entry.kind == Inbound.Kind.MESSAGE && !noteInboundId(entry.discordId)) {
            // The other reader already queued it.
            return;
        }
        this.inboundCount.incrementAndGet();
        this.inbound.add(entry);
    }

    /** Whether the message id is new to this session's readers. */
    private boolean noteInboundId(String discordId) {
        if (discordId == null || discordId.length() == 0) {
            return true;
        }
        synchronized (this.recentInboundIds) {
            if (!this.recentInboundIds.add(discordId)) {
                return false;
            }
            while (this.recentInboundIds.size() > MAX_RECENT_INBOUND_IDS) {
                Iterator<String> oldest = this.recentInboundIds.iterator();
                oldest.next();
                oldest.remove();
            }
            return true;
        }
    }

    /**
     * Answers a slash command: the deferred reply already went out from
     * the gateway, so this fills it in with what the live server says,
     * over the gateway's own job thread.
     */
    private void answerCommand(final DiscordJson.Interaction interaction) {
        final DiscordGatewayClient client = this.gateway;
        if (interaction == null || client == null) {
            return;
        }
        final String content = DiscordSlashCommands.answer(interaction.name,
                interaction.options, this.serverStartedMillis);
        if (content.length() == 0) {
            return;
        }
        final String appId = interaction.applicationId.length() > 0
                ? interaction.applicationId : this.applicationId;
        client.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    DiscordHttp.Reply reply = DiscordHttp.patchInteractionOriginal(
                            appId, interaction.token, DiscordJson.followUpBody(content));
                    if (!reply.isSuccess()) {
                        FMLLog.info("[%s] Discord did not take the answer to /%s (HTTP %d)",
                                LostTalesMetaData.MOD_ID, interaction.name,
                                Integer.valueOf(reply.status));
                    }
                } catch (IOException exception) {
                    FMLLog.info("[%s] Could not answer /%s on Discord: %s",
                            LostTalesMetaData.MOD_ID, interaction.name, exception.toString());
                }
            }
        });
    }

    /** The newer of two message ids, which are snowflakes: numeric, and ordered. */
    static String newerId(String left, String right) {
        if (left == null || left.length() == 0) {
            return right == null ? "" : right;
        }
        if (right == null || right.length() == 0) {
            return left;
        }
        try {
            return Long.parseLong(left) >= Long.parseLong(right) ? left : right;
        } catch (NumberFormatException notASnowflake) {
            return left.compareTo(right) >= 0 ? left : right;
        }
    }

    /**
     * What the gateway reports: messages in bound channels become the
     * same inbound entries polling makes, and slash commands are
     * deferred at once and answered on the server thread.
     */
    private final class GatewayListener implements DiscordGatewayClient.Listener {
        private final DiscordChannelBindings bound;
        private boolean readRefusedLogged;

        GatewayListener(DiscordChannelBindings bound) {
            this.bound = bound;
        }

        @Override
        public String fetchGatewayUrl() throws IOException {
            DiscordHttp.Reply reply = DiscordHttp.getGateway(
                    LostTalesConfig.discordBotToken.trim());
            if (reply.status == 401) {
                if (!this.readRefusedLogged) {
                    this.readRefusedLogged = true;
                    FMLLog.severe("[%s] Discord refused the bot (HTTP 401) when asked "
                            + "for its gateway: check the token", LostTalesMetaData.MOD_ID);
                }
                return "";
            }
            return reply.isSuccess() ? DiscordJson.parseGatewayUrl(reply.body) : "";
        }

        @Override
        public void onReady(JsonObject ready, String sessionId) {
            applicationId = DiscordGatewayProtocol.applicationId(ready);
            if (!LostTalesConfig.discordSlashCommands || applicationId.length() == 0) {
                return;
            }
            final DiscordGatewayClient client = gateway;
            if (client == null) {
                return;
            }
            for (final String guildId : DiscordGatewayProtocol.guildIds(ready)) {
                client.submit(new Runnable() {
                    @Override
                    public void run() {
                        registerCommands(guildId);
                    }
                });
            }
        }

        private void registerCommands(String guildId) {
            try {
                DiscordHttp.Reply reply = DiscordHttp.putGuildCommands(
                        LostTalesConfig.discordBotToken.trim(), applicationId, guildId,
                        DiscordSlashCommands.definitionsBody());
                if (reply.isSuccess()) {
                    FMLLog.info("[%s] Discord slash commands registered in guild %s",
                            LostTalesMetaData.MOD_ID, guildId);
                } else {
                    FMLLog.warning("[%s] Discord refused the slash commands for guild %s "
                            + "(HTTP %d); the bot may lack the applications.commands scope "
                            + "there", LostTalesMetaData.MOD_ID, guildId,
                            Integer.valueOf(reply.status));
                }
            } catch (IOException exception) {
                FMLLog.warning("[%s] Could not register the slash commands in guild %s: %s",
                        LostTalesMetaData.MOD_ID, guildId, exception.toString());
            }
        }

        @Override
        public void onEvent(String name, JsonObject data) {
            if ("MESSAGE_CREATE".equals(name)) {
                DiscordJson.Message message = DiscordJson.parseMessage(data);
                DiscordChannelBinding binding = message == null ? null
                        : readingBindingOf(message.channelId);
                if (binding == null) {
                    return;
                }
                lastSeenByChannel.put(message.channelId,
                        newerId(lastSeenByChannel.get(message.channelId), message.id));
                if (message.bot) {
                    return;
                }
                String author = DiscordMessageSanitizer.inboundName(message.authorName);
                String text = DiscordMessageSanitizer.inbound(
                        DiscordMessageLinkRewriter.inbound(message.content,
                                linkResolver(this.bound)),
                        message.mentionNames);
                if (author.length() > 0 && text.length() > 0) {
                    rememberAuthor(author, message.authorId);
                    enqueueInbound(new Inbound(Inbound.Kind.MESSAGE, author,
                            message.authorId, text, message.id,
                            message.referencedMessageId, message.channelId));
                }
            } else if ("MESSAGE_UPDATE".equals(name)) {
                DiscordJson.Message message = DiscordJson.parseMessage(data);
                DiscordChannelBinding binding = message == null ? null
                        : readingBindingOf(message.channelId);
                // An update without an edit stamp is Discord's own —
                // a link's embed unfurling, most often — and the words
                // it carries are the ones already relayed.
                if (binding == null || message.bot || !message.isEdited()) {
                    return;
                }
                String text = DiscordMessageSanitizer.inbound(
                        DiscordMessageLinkRewriter.inbound(message.content,
                                linkResolver(this.bound)),
                        message.mentionNames);
                if (text.length() > 0) {
                    enqueueInbound(new Inbound(Inbound.Kind.EDIT, "", "", text,
                            message.id, "", message.channelId));
                }
            } else if ("MESSAGE_DELETE".equals(name)) {
                String id = data.has("id") && data.get("id").isJsonPrimitive()
                        ? data.get("id").getAsString() : "";
                String channelId = data.has("channel_id")
                        && data.get("channel_id").isJsonPrimitive()
                        ? data.get("channel_id").getAsString() : "";
                DiscordChannelBinding binding = readingBindingOf(channelId);
                if (binding != null && id.length() > 0) {
                    enqueueInbound(new Inbound(Inbound.Kind.DELETE, "", "", "", id, "",
                            channelId));
                }
            } else if (name != null && name.startsWith("MESSAGE_REACTION_")) {
                // A reaction on a message the bridge relayed, either
                // way. A bot's own — the bridge's above all — is not a
                // member's. An emoji the registry lacks crosses by its
                // foreign key. A custom emoji crosses with its id too,
                // since Discord keeps the id through a rename. Discord
                // sends a deleted custom emoji without its name; it
                // crosses by the id alone.
                DiscordJson.Reaction reaction = DiscordJson.parseReaction(data);
                DiscordChannelBinding binding = reaction == null ? null
                        : readingBindingOf(reaction.channelId);
                if (binding == null || reaction.bot) {
                    return;
                }
                Inbound.Kind kind;
                if ("MESSAGE_REACTION_ADD".equals(name)) {
                    kind = Inbound.Kind.REACT_ADD;
                } else if ("MESSAGE_REACTION_REMOVE".equals(name)) {
                    kind = Inbound.Kind.REACT_REMOVE;
                } else if ("MESSAGE_REACTION_REMOVE_ALL".equals(name)
                        || "MESSAGE_REACTION_REMOVE_EMOJI".equals(name)) {
                    kind = Inbound.Kind.REACT_CLEAR;
                } else {
                    return;
                }
                String emoji = reaction.reactionKey();
                boolean all = "MESSAGE_REACTION_REMOVE_ALL".equals(name);
                String emojiId = !all
                        && ChatForeignEmoji.isCustomId(reaction.emojiId)
                        ? reaction.emojiId : "";
                if (!all && emoji == null && emojiId.length() == 0) {
                    return;
                }
                enqueueInbound(new Inbound(kind,
                        DiscordMessageSanitizer.inboundName(reaction.memberName),
                        reaction.userId, all || emoji == null ? "" : emoji,
                        reaction.messageId, "", reaction.channelId, emojiId));
            } else if ("INTERACTION_CREATE".equals(name)) {
                final DiscordJson.Interaction interaction = DiscordJson.parseInteraction(data);
                final DiscordGatewayClient client = gateway;
                if (interaction == null || client == null
                        || !LostTalesConfig.discordSlashCommands) {
                    return;
                }
                // Discord gives three seconds: the deferred answer goes
                // out now, the real one once the server thread has it.
                client.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            DiscordHttp.postInteractionCallback(interaction.id,
                                    interaction.token, DiscordJson.deferredReplyBody(true));
                        } catch (IOException exception) {
                            FMLLog.info("[%s] Could not acknowledge /%s on Discord: %s",
                                    LostTalesMetaData.MOD_ID, interaction.name,
                                    exception.toString());
                        }
                    }
                });
                enqueueInbound(new Inbound(interaction));
            }
        }

        private DiscordChannelBinding readingBindingOf(String channelId) {
            return this.bound.readerOf(channelId);
        }

        @Override
        public void onConnected() {
            if (!gatewayLive) {
                gatewayLive = true;
                FMLLog.info("[%s] Discord gateway connected; messages arrive as sent",
                        LostTalesMetaData.MOD_ID);
            }
        }

        @Override
        public void onDisconnected() {
            if (gatewayLive) {
                gatewayLive = false;
                FMLLog.info("[%s] Discord gateway disconnected; polling reads until "
                        + "it is back", LostTalesMetaData.MOD_ID);
            }
        }
    }

    private static final class Inbound {
        /** What reached the game: a message, word about an old one, or a command. */
        enum Kind { MESSAGE, EDIT, DELETE, COMMAND, REACT_ADD, REACT_REMOVE,
            REACT_CLEAR }

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
        /**
         * The Discord channel it came from, which names the binding that
         * reads it; empty for a command.
         */
        final String discordChannelId;
        /**
         * A reaction's custom emoji's Discord id, by which the chat finds
         * the key a message holds for that emoji; empty for a Unicode
         * emoji, a clear of every emoji and anything but a reaction.
         */
        final String emojiId;
        /** The slash command a COMMAND entry answers; null otherwise. */
        final DiscordJson.Interaction interaction;

        Inbound(Kind kind, String name, String authorId, String text,
                String discordId, String referencedDiscordId,
                String discordChannelId) {
            this(kind, name, authorId, text, discordId, referencedDiscordId,
                    discordChannelId, "", null);
        }

        Inbound(Kind kind, String name, String authorId, String text,
                String discordId, String referencedDiscordId,
                String discordChannelId, String emojiId) {
            this(kind, name, authorId, text, discordId, referencedDiscordId,
                    discordChannelId, emojiId, null);
        }

        Inbound(DiscordJson.Interaction interaction) {
            this(Kind.COMMAND, interaction.name, "", "", "", "", "", "",
                    interaction);
        }

        private Inbound(Kind kind, String name, String authorId, String text,
                        String discordId, String referencedDiscordId,
                        String discordChannelId, String emojiId,
                        DiscordJson.Interaction interaction) {
            this.kind = kind;
            this.name = name;
            this.authorId = authorId;
            this.text = text;
            this.discordId = discordId;
            this.referencedDiscordId = referencedDiscordId;
            this.discordChannelId = discordChannelId == null ? "" : discordChannelId;
            this.emojiId = emojiId == null ? "" : emojiId;
            this.interaction = interaction;
        }
    }

    private static final class Outbound {
        /** What the worker is to do with the entry. */
        enum Kind { POST, EDIT, DELETE, REACT, UNREACT }

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
        /** The id of the binding a post goes through; empty for an edit or a removal. */
        final String bindingKey;
        /**
         * The channel the entry's message was said in, which decides
         * which of its copies are live; null for a notice.
         */
        final ChatChannel channel;
        /** The faction a Faction line was said to; empty otherwise. */
        final String factionScope;
        /**
         * The account that said a player's line, told how its post goes;
         * null for anything else.
         */
        final UUID senderId;
        /** When a player's line was queued, by the server's clock; 0 otherwise. */
        final long queuedAtMillis;

        Outbound(Kind kind, String username, String avatarUrl,
                 String message, long messageId, ChatReplyReference reply,
                 DiscordNotice notice, String bindingKey, ChatChannel channel,
                 String factionScope) {
            this(kind, username, avatarUrl, message, messageId, reply, notice,
                    bindingKey, channel, factionScope, null, 0L);
        }

        Outbound(Kind kind, String username, String avatarUrl,
                 String message, long messageId, ChatReplyReference reply,
                 DiscordNotice notice, String bindingKey, ChatChannel channel,
                 String factionScope, UUID senderId, long queuedAtMillis) {
            this.kind = kind;
            this.username = username;
            this.avatarUrl = avatarUrl;
            this.message = message;
            this.messageId = messageId;
            this.reply = reply;
            this.notice = notice;
            this.bindingKey = bindingKey == null ? "" : bindingKey;
            this.channel = channel;
            this.factionScope = factionScope == null ? "" : factionScope;
            this.senderId = senderId;
            this.queuedAtMillis = queuedAtMillis;
        }

        /** Whether this is a player's line whose sender is told how its post goes. */
        boolean isTracked() {
            return this.kind == Kind.POST && this.notice == null
                    && this.senderId != null;
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
        /**
         * The links of the server run this worker serves, fixed for its
         * life: a worker still finishing after its server stopped writes
         * into the map that run left behind, never the next one's.
         */
        private final DiscordMessageLinks links;
        /**
         * What the server thread queued for this worker to post, and how
         * much of it. The intake is this worker's own, so one still
         * finishing after a reload never takes an entry queued for the
         * next worker, which it would judge by bindings no longer in
         * force.
         */
        final Queue<Outbound> outbound = new ConcurrentLinkedQueue<Outbound>();
        final AtomicInteger outboundCount = new AtomicInteger();
        /**
         * The bindings somebody is typing into, by id, waiting to be said
         * on Discord. Written on the server thread, drained here.
         */
        final Set<String> typingRequests =
                Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        /**
         * The topic of every Discord channel this worker keeps, by channel
         * id, fixed at start. The server thread states the wanted topic on
         * each; this worker writes them, the last time as it stops.
         */
        final Map<String, DiscordChannelStatus> statuses;
        private volatile boolean running = true;
        /** One cursor per Discord channel read, by its channel id. */
        private final Map<String, ChannelCursor> cursors =
                new HashMap<String, ChannelCursor>();
        /** Webhooks Discord refused for the session, by URL. */
        private final Set<String> postingDisabled = new HashSet<String>();
        /** Whether Discord's refusal of a reaction has been said this session. */
        private boolean reactionRefusalLogged;
        /** Whether an emoji the bot may not use has been said this session. */
        private boolean unknownEmojiLogged;
        /**
         * What waits to be sent, one lane per webhook, each on a clock
         * of its own: the intake is sorted into them on every pass.
         */
        private final DiscordOutboundLanes<Outbound> lanes =
                new DiscordOutboundLanes<Outbound>();
        /**
         * What the headers of Discord's replies said of its buckets, so a
         * lane waits for its bucket to reset instead of sending into a
         * 429.
         */
        private final DiscordRateBuckets buckets = new DiscordRateBuckets();
        /** Where the posts of the players' own lines have got. */
        private final DiscordDeliveryTracker tracker = new DiscordDeliveryTracker();
        /**
         * What the senders are to be told, waiting for the server thread,
         * and how much of it: at most {@link #MAX_QUEUED_MARKS}.
         */
        final Queue<DiscordDeliveryTracker.Mark> marks =
                new ConcurrentLinkedQueue<DiscordDeliveryTracker.Mark>();
        final AtomicInteger markCount = new AtomicInteger();
        /** Whether a mark dropped for want of room has been said this session. */
        private boolean markDropLogged;
        /**
         * When a typing ping was last sent per binding. Discord shows its
         * own indicator for about ten seconds from one, so repeating it
         * faster than that buys nothing and only spends the rate limit.
         */
        private final Map<String, Long> typingSentMillis = new HashMap<String, Long>();
        /** The bot itself was refused: nothing is read for the session. */
        private boolean readingDisabled;
        /**
         * Whether reading is going through: said once when it starts
         * failing and once when it comes back.
         */
        private boolean readsHealthy = true;
        /**
         * The lanes whose sends are failing, each said once when it
         * starts and once when it delivers again; the empty name stands
         * for a failure no one lane owns.
         */
        private final Set<String> failingLanes = new HashSet<String>();
        /** The pause after a failed read, doubling with each one in a row. */
        private long backoffMillis = MIN_BACKOFF_MILLIS;
        /** When the bound channels are next read, while the gateway is down. */
        private long nextReadMillis;
        /**
         * Released whenever something is queued for this worker, so a
         * line said in the game is posted at once instead of at the next
         * pass; the passes themselves stay one at a time.
         */
        private final Semaphore wake = new Semaphore(0);
        /**
         * Where each webhook posts, as Discord answered. An answer is
         * kept for the session; a null stands for a webhook Discord
         * refused outright. A lookup that failed any other way is not
         * kept, and is asked again once {@link #webhookLookups} allows.
         */
        private final Map<String, DiscordJson.ChannelInfo> webhookInfos =
                new HashMap<String, DiscordJson.ChannelInfo>();
        /** When a webhook whose lookup failed may be asked again. */
        private final DiscordRetryClock webhookLookups = new DiscordRetryClock();
        /**
         * What this worker knows of its webhooks, for the liveness rule:
         * where each posts, as far as Discord has said, and which
         * Discord refused.
         */
        private final DiscordCopyLiveness.Webhooks known =
                new DiscordCopyLiveness.Webhooks() {
                    @Override
                    public String channelOf(String webhookUrl) {
                        if (webhookUrl == null || webhookUrl.length() == 0) {
                            return "";
                        }
                        DiscordJson.ChannelInfo info = webhookInfo(webhookUrl);
                        return info == null || info.channelId == null
                                ? "" : info.channelId;
                    }

                    @Override
                    public boolean refused(String webhookUrl) {
                        return postingDisabled.contains(webhookUrl);
                    }
                };
        /**
         * Where each read channel is, by its id, learnt as the readers
         * are checked: what a link to a Discord original is built from.
         */
        private final Map<String, DiscordJson.ChannelInfo> readerInfos =
                new ConcurrentHashMap<String, DiscordJson.ChannelInfo>();
        /** Whether the no-PATCH warning has been said this session. */
        private boolean patchWarned;
        /** Posts each full lane has refused, for the warning that says so. */
        private final Map<String, Integer> droppedByLane =
                new HashMap<String, Integer>();
        /** The kinds of request and status Discord refused outright, each said once. */
        private final Set<String> refusalsLogged = new HashSet<String>();

        Worker(DiscordChannelBindings bindings, boolean reads, boolean posts,
               boolean manages) {
            super("LostTales-Discord");
            setDaemon(true);
            this.links = LostTalesDiscordBridge.this.links;
            this.bindings = bindings;
            this.reads = reads;
            this.posts = posts;
            this.manages = manages;
            LinkedHashMap<String, DiscordChannelStatus> kept =
                    new LinkedHashMap<String, DiscordChannelStatus>();
            if (manages) {
                for (String channelId : bindings.channels()) {
                    kept.put(channelId, new DiscordChannelStatus(channelId));
                }
            }
            this.statuses = Collections.unmodifiableMap(kept);
        }

        void shutdown() {
            this.running = false;
            interrupt();
        }

        /** Wakes the worker for a pass now: something is waiting to be sent. */
        void wake() {
            this.wake.release();
        }

        /**
         * Drops what still waits in the intake once the stop has waited
         * as long as it will: what was not sent by then is left, and a
         * typing request is about a moment that has passed.
         */
        void forgetIntake() {
            while (this.outbound.poll() != null) {
                this.outboundCount.decrementAndGet();
            }
            this.typingRequests.clear();
        }

        @Override
        public void run() {
            probe();
            while (this.running) {
                long readMillis = Math.max(2L, Math.min(60L,
                        LostTalesConfig.discordPollIntervalSeconds)) * 1000L;
                // While the gateway is up it hears every message and every
                // edit; reading stands in only while it is down, on a
                // clock of its own, so a line said in the game never waits
                // for a read and a failed read never holds a post back.
                boolean reading = this.reads && !this.readingDisabled
                        && !gatewayLive;
                if (reading && System.currentTimeMillis()
                        >= this.nextReadMillis) {
                    // Timed from the end of the read, however long it took.
                    long wait = read(readMillis);
                    this.nextReadMillis = System.currentTimeMillis() + wait;
                }
                if (this.posts) {
                    try {
                        drainOutbound();
                        flushTyping();
                        flushOutbound(false);
                        postSucceeded("");
                    } catch (RuntimeException exception) {
                        postFailed("", exception.toString());
                    }
                    // A line still waiting when its clock is due gets one,
                    // however the pass went.
                    emitMarks(this.tracker.due(System.currentTimeMillis()));
                }
                // The topic keeps its own clock: a limit on it must not
                // hold the chat back, nor a chat failure the topic.
                if (this.manages) {
                    flushStatus(false);
                }
                if (!this.running) {
                    break;
                }
                // Asleep until the next read, until a webhook held back
                // may be worked again or until a waiting line's clock is
                // due, and woken at once by anything queued: what makes a
                // line said in the game cross as soon as it is said.
                long now = System.currentTimeMillis();
                long sleepMillis = readMillis;
                if (reading) {
                    sleepMillis = Math.min(sleepMillis,
                            this.nextReadMillis - now);
                }
                long nextLane = this.lanes.nextDueMillis();
                if (nextLane != Long.MAX_VALUE) {
                    sleepMillis = Math.min(sleepMillis, nextLane - now);
                }
                long nextClock = this.tracker.nextDueMillis();
                if (nextClock != Long.MAX_VALUE) {
                    sleepMillis = Math.min(sleepMillis, nextClock - now);
                }
                try {
                    if (this.wake.tryAcquire(Math.max(MIN_PASS_GAP_MILLIS,
                            sleepMillis), TimeUnit.MILLISECONDS)) {
                        // One pass answers every wake that came before it.
                        this.wake.drainPermits();
                    }
                } catch (InterruptedException interrupted) {
                    break;
                }
            }
            sendLast();
            // What still waits now will not go out; its senders are told.
            emitMarks(this.tracker.abandon());
        }

        /**
         * Reads every bound channel once, while the gateway is down, and
         * answers how long until the next read: the poll interval, what
         * Discord asked for when it limited the read, or a pause that
         * grows with every failure in a row.
         */
        private long read(long readMillis) {
            try {
                poll();
                sweepChannels();
                readSucceeded();
                return readMillis;
            } catch (RateLimited limited) {
                return Math.max(readMillis, limited.retryAfterMillis);
            } catch (IOException exception) {
                return readFailed(exception.toString());
            } catch (RuntimeException exception) {
                return readFailed(exception.toString());
            }
        }

        /**
         * Sends a typing ping into every bound channel asked for since
         * the last pass whose last ping is old enough and whose words are
         * not already waiting to be posted. A refusal is dropped rather
         * than retried: presence is only worth saying while it is still
         * true.
         */
        private void flushTyping() {
            if (typingRequests.isEmpty()) {
                return;
            }
            String token = LostTalesConfig.discordBotToken.trim();
            Set<String> posting = postingBindings();
            for (String key : new ArrayList<String>(typingRequests)) {
                typingRequests.remove(key);
                DiscordChannelBinding binding = this.bindings.byId(key);
                if (binding == null || token.length() == 0
                        // The words are already on their way, and a
                        // webhook's post does not end the bot's typing on
                        // Discord: an indicator sent now would outlast them.
                        || posting.contains(key)) {
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
                } catch (IOException exception) {
                    // Presence that did not arrive is presence not worth
                    // chasing; the next keystroke asks again. Said once,
                    // so a token that cannot type is not a mystery.
                    noteTypingFailure(exception);
                } catch (RuntimeException exception) {
                    noteTypingFailure(exception);
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
            DiscordJson.ChannelInfo info = webhookInfo(binding.getWebhookUrl());
            return info == null || info.channelId == null ? "" : info.channelId;
        }

        /** The bindings a game line is waiting on a lane to be posted through. */
        private Set<String> postingBindings() {
            Set<String> posting = new HashSet<String>();
            for (String webhook : this.lanes.webhooks()) {
                for (Outbound waiting : this.lanes.items(webhook)) {
                    if (waiting.kind == Outbound.Kind.POST
                            && waiting.notice == null) {
                        posting.add(waiting.bindingKey);
                    }
                }
            }
            return posting;
        }

        /**
         * One best-effort pass at what is still queued when the bridge
         * stops — the farewell, the offline topic — with no retry and no
         * wait: whatever does not go now is left.
         */
        private void sendLast() {
            try {
                if (this.posts) {
                    flushOutbound(true);
                }
            } catch (RuntimeException exception) {
                FMLLog.info("[%s] Discord bridge could not send its last "
                        + "post: %s", LostTalesMetaData.MOD_ID,
                        exception.toString());
            }
            if (this.manages) {
                flushStatus(true);
            }
        }

        /**
         * Writes every channel's topic that is due. Each channel keeps
         * its own clock, so one refused or limited never holds back
         * another — a channel in a guild the bot has lost stays quiet
         * on its own.
         */
        private void flushStatus(boolean finalAttempt) {
            String token = LostTalesConfig.discordBotToken.trim();
            long interval = Math.max(60L, Math.min(3600L,
                    LostTalesConfig.discordChannelStatusIntervalSeconds)) * 1000L;
            for (DiscordChannelStatus status : statuses.values()) {
                status.flush(token, interval, finalAttempt);
            }
        }

        /**
         * Asks Discord about every binding once at start and says what it
         * answered in the log — the guild and the channel by id, never a
         * URL or the token — so a server owner reaching into a second
         * guild sees each channel answer for itself, and a stale id is
         * caught at start rather than at the first line. What cannot be
         * asked — a limit, a network fault — is left to be tried when it
         * is first used.
         */
        private void probe() {
            if (this.reads && !this.readingDisabled) {
                probeReaders();
            }
            if (this.posts) {
                probeDestinations();
            }
        }

        /**
         * Reads every channel the bindings read, by id with the bot. A
         * channel the bot cannot see, or one that is gone, is refused
         * here and now with the same line the first poll would have said
         * — a refused token stops every read at once — and a channel
         * that answers is named with its guild.
         */
        private void probeReaders() {
            String token = LostTalesConfig.discordBotToken.trim();
            for (DiscordChannelBinding binding : this.bindings.reading()) {
                ChannelCursor cursor = cursorFor(binding);
                DiscordHttp.Reply reply;
                try {
                    reply = DiscordHttp.getChannel(token, binding.getDiscordChannelId());
                } catch (IOException exception) {
                    FMLLog.warning("[%s] Discord reader '%s' could not be checked "
                            + "(%s); reading it is tried all the same",
                            LostTalesMetaData.MOD_ID, binding.id(),
                            exception.toString());
                    continue;
                } catch (RuntimeException exception) {
                    FMLLog.warning("[%s] Discord reader '%s' could not be checked "
                            + "(%s); reading it is tried all the same",
                            LostTalesMetaData.MOD_ID, binding.id(),
                            exception.toString());
                    continue;
                }
                if (readRefused(reply.status, binding, cursor)) {
                    if (this.readingDisabled) {
                        return;
                    }
                    continue;
                }
                DiscordJson.ChannelInfo info = reply.isSuccess()
                        ? DiscordJson.parseChannelInfo(reply.body) : null;
                if (info == null) {
                    FMLLog.warning("[%s] Discord reader '%s' did not say where "
                            + "channel %s is (HTTP %d); reading it is tried all "
                            + "the same", LostTalesMetaData.MOD_ID, binding.id(),
                            binding.getDiscordChannelId(),
                            Integer.valueOf(reply.status));
                    continue;
                }
                FMLLog.info("[%s] Discord reader '%s' reads guild %s, channel %s",
                        LostTalesMetaData.MOD_ID, binding.id(), info.guildId,
                        info.channelId);
                readerInfos.put(info.channelId, info);
            }
        }

        /**
         * How a game line's links are spelled for a post through
         * {@code webhookUrl}: the linked message's live copy in the very
         * channel the post goes to is preferred, then its first other
         * live copy, a Discord member's original among them; a message
         * with no live copy stays as typed. Live means its game channel
         * still posts into the copy's Discord channel
         * ({@link DiscordCopyLiveness#jumpTarget}).
         */
        private DiscordMessageLinkRewriter.Resolver outboundResolver(
                final String webhookUrl) {
            return new DiscordMessageLinkRewriter.Resolver() {
                @Override
                public String jumpUrl(ChatChannel channel, long messageId) {
                    DiscordMessageLinks.Copy chosen = DiscordCopyLiveness.jumpTarget(
                            Worker.this.links, Worker.this.bindings, HISTORY,
                            messageId, destinationOf(webhookUrl), Worker.this.known);
                    if (chosen == null) {
                        return "";
                    }
                    DiscordJson.ChannelInfo info = channelInfoOf(chosen);
                    return info == null ? ""
                            : DiscordMessageLinkRewriter.jumpUrl(info.guildId,
                                    info.channelId, chosen.discordId);
                }

                @Override
                public String gameLink(String guildId, String channelId,
                                       String discordMessageId) {
                    return "";
                }
            };
        }

        /**
         * Asks Discord where every webhook posts. A webhook Discord
         * refuses (deleted, or its URL wrong) is off for the session
         * here and now rather than at the first line. So is one that
         * turns out to post into a Discord channel another game channel
         * already has — by a channel id in its entries, or by a webhook
         * answered earlier — since a Discord channel belongs to one game
         * channel; and a second webhook of the same game channel into
         * one Discord channel, which would only post every line twice.
         */
        private void probeDestinations() {
            Map<String, String> byChannel = new HashMap<String, String>();
            for (DiscordChannelBinding destination : this.bindings.destinations()) {
                String webhook = destination.getWebhookUrl();
                DiscordHttp.Reply reply;
                try {
                    reply = DiscordHttp.getWebhookInfo(webhook);
                } catch (IOException exception) {
                    FMLLog.warning("[%s] Discord destination '%s' could not be "
                            + "asked where it posts (%s); posting there is "
                            + "tried all the same", LostTalesMetaData.MOD_ID,
                            destination.id(), exception.toString());
                    continue;
                } catch (RuntimeException exception) {
                    FMLLog.warning("[%s] Discord destination '%s' could not be "
                            + "asked where it posts (%s); posting there is "
                            + "tried all the same", LostTalesMetaData.MOD_ID,
                            destination.id(), exception.toString());
                    continue;
                }
                if (reply.status == 401 || reply.status == 404) {
                    this.postingDisabled.add(webhook);
                    FMLLog.severe("[%s] Discord refused the webhook of binding "
                            + "%s (HTTP %d): the webhook was deleted or its URL "
                            + "is wrong; posting there is off until the server "
                            + "restarts", LostTalesMetaData.MOD_ID,
                            destination.id(), Integer.valueOf(reply.status));
                    continue;
                }
                DiscordJson.ChannelInfo info = reply.isSuccess()
                        ? DiscordJson.parseWebhookInfo(reply.body) : null;
                if (info == null) {
                    FMLLog.warning("[%s] Discord destination '%s' did not say "
                            + "where it posts (HTTP %d); posting there is tried "
                            + "all the same", LostTalesMetaData.MOD_ID,
                            destination.id(), Integer.valueOf(reply.status));
                    continue;
                }
                this.webhookInfos.put(webhook, info);
                FMLLog.info("[%s] Discord destination '%s' posts to guild %s, "
                        + "channel %s", LostTalesMetaData.MOD_ID,
                        destination.id(), info.guildId, info.channelId);
                String owner = this.bindings.ownerOfChannel(info.channelId);
                String earlier = byChannel.get(info.channelId);
                if (owner.length() > 0 && !owner.equals(destination.key())) {
                    this.postingDisabled.add(webhook);
                    FMLLog.severe("[%s] Discord destination '%s' posts into "
                            + "channel %s, which '%s' has: a Discord channel "
                            + "belongs to one game channel; posting there is "
                            + "off until the server restarts",
                            LostTalesMetaData.MOD_ID, destination.id(),
                            info.channelId, owner);
                } else if (earlier != null
                        && !this.bindings.byId(earlier).key().equals(destination.key())) {
                    this.postingDisabled.add(webhook);
                    FMLLog.severe("[%s] Discord destinations '%s' and '%s' are "
                            + "two webhooks into channel %s: a Discord channel "
                            + "belongs to one game channel; posting through the "
                            + "second is off until the server restarts",
                            LostTalesMetaData.MOD_ID, earlier, destination.id(),
                            info.channelId);
                } else if (earlier != null) {
                    this.postingDisabled.add(webhook);
                    FMLLog.warning("[%s] Discord destinations '%s' and '%s' are "
                            + "two webhooks of one game channel into channel %s, "
                            + "which would post every line twice; the second is "
                            + "off until the server restarts",
                            LostTalesMetaData.MOD_ID, earlier, destination.id(),
                            info.channelId);
                } else {
                    byChannel.put(info.channelId, destination.id());
                }
            }
        }

        /**
         * How a copy posted through a webhook names its destination: the
         * Discord channel the webhook posts to when Discord has said,
         * which is where a Discord line read from that channel names
         * its own copy, else the webhook itself.
         */
        private String destinationOf(String webhookUrl) {
            DiscordJson.ChannelInfo info = webhookInfo(webhookUrl);
            return info == null ? webhookUrl
                    : channelDestination(info.channelId);
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
                // Whatever the gateway saw last in this channel is where
                // polling goes on from, so a gateway drop loses nothing.
                String seen = lastSeenByChannel.get(binding.getDiscordChannelId());
                if (seen != null && seen.length() > 0) {
                    cursor.after = newerId(cursor.after, seen);
                    cursor.primed = true;
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
                            + " to a read of " + binding.id());
                }
                List<DiscordJson.Message> messages =
                        DiscordJson.parseMessages(reply.body);
                if (!messages.isEmpty()) {
                    cursor.after = messages.get(messages.size() - 1).id;
                    lastSeenByChannel.put(binding.getDiscordChannelId(), newerId(
                            lastSeenByChannel.get(binding.getDiscordChannelId()),
                            cursor.after));
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
                            DiscordMessageLinkRewriter.inbound(message.content,
                                    linkResolver(this.bindings)),
                            message.mentionNames);
                    if (name.length() > 0 && text.length() > 0) {
                        rememberAuthor(name, message.authorId);
                        enqueueInbound(new Inbound(Inbound.Kind.MESSAGE, name,
                                message.authorId, text, message.id,
                                message.referencedMessageId,
                                binding.getDiscordChannelId()));
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
                // The members' lines relayed before this worker started —
                // before a reload, or before the server restarted — are
                // watched again, so polling still hears of their edits
                // and deletions while they are within its sight: those of
                // the game channel this Discord channel is read into now,
                // and no other.
                for (String discordId : links.discordLinesIn(channelDestination(
                        binding.getDiscordChannelId()))) {
                    if (DiscordCopyLiveness.inboundTarget(links, this.bindings,
                            HISTORY, discordId, binding.getDiscordChannelId())
                            != ChatMessageIds.NONE) {
                        cursor.sweep.watch(discordId);
                    }
                }
                this.cursors.put(binding.getDiscordChannelId(), cursor);
            }
            return cursor;
        }

        /**
         * The webhook an edit or a removal of a copy goes through, empty
         * for none: only the webhook that made a post may change it, and
         * only while the entry's game channel still posts into the
         * copy's Discord channel through it
         * ({@link DiscordCopyLiveness#correctionWebhook}). Empty for a
         * Discord member's line, a pair no longer bound that way, a
         * webhook now posting elsewhere, or a copy whose channel was
         * never learnt and whose webhook is not known.
         */
        private String webhookOf(DiscordMessageLinks.Copy copy, Outbound next) {
            return DiscordCopyLiveness.correctionWebhook(this.bindings,
                    next.channel, next.factionScope, copy, this.known);
        }

        /**
         * The copy of a message that an edit or a removal sent through
         * {@code webhook} corrects, or null for none.
         */
        private DiscordMessageLinks.Copy copyThrough(Outbound next,
                                                     String webhook) {
            for (DiscordMessageLinks.Copy copy : links.copiesOf(next.messageId)) {
                if (webhook.equals(webhookOf(copy, next))) {
                    return copy;
                }
            }
            return null;
        }

        /**
         * Where a copy is, for a link to it: the answer of the webhook it
         * was posted through, else what is known of its channel, as one
         * the bridge reads or as one a webhook of the bindings posts
         * into. Null when neither is known.
         */
        private DiscordJson.ChannelInfo channelInfoOf(DiscordMessageLinks.Copy copy) {
            if (copy.webhookUrl.length() > 0) {
                return webhookInfo(copy.webhookUrl);
            }
            String channelId = DiscordCopyLiveness.channelIdOf(copy.destination);
            if (channelId.length() == 0) {
                return null;
            }
            DiscordJson.ChannelInfo info = readerInfos.get(channelId);
            if (info != null) {
                return info;
            }
            for (DiscordJson.ChannelInfo known : this.webhookInfos.values()) {
                if (known != null && channelId.equals(known.channelId)) {
                    return known;
                }
            }
            return null;
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
                            + " to a sweep of " + binding.id());
                }
                DiscordMessageSweep.Changes changes = cursor.sweep.apply(
                        DiscordJson.parseMessages(reply.body));
                for (DiscordJson.Message message : changes.edited) {
                    String text = DiscordMessageSanitizer.inbound(
                            DiscordMessageLinkRewriter.inbound(message.content,
                                    linkResolver(this.bindings)),
                            message.mentionNames);
                    // Edited down to nothing sayable — an attachment left
                    // alone — keeps the words it was delivered with.
                    if (text.length() > 0) {
                        enqueueInbound(new Inbound(Inbound.Kind.EDIT, "", "",
                                text, message.id, "", binding.getDiscordChannelId()));
                    }
                }
                for (int index = 0; index < changes.deletedIds.size(); index++) {
                    enqueueInbound(new Inbound(Inbound.Kind.DELETE, "", "", "",
                            changes.deletedIds.get(index), "",
                            binding.getDiscordChannelId()));
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
                        binding.id(), Integer.valueOf(status));
                return true;
            }
            return false;
        }

        /**
         * Sends what is queued: the intake is sorted into one lane per
         * webhook, and every lane that is due is worked in order until
         * it is empty, Discord limits its webhook, or a send fails. A
         * limit holds that lane back for exactly the time asked and a
         * failure for a pause that doubles with every failure in a row,
         * that lane alone and never anybody else's. A request whose
         * bucket Discord's headers say is spent waits for the bucket to
         * reset, with everything behind it, rather than going out into
         * the limit. An entry is given up after {@link #MAX_SEND_ATTEMPTS}
         * failed tries at the head of a lane, so no one post can hold its
         * webhook up for good. A final attempt, at shutdown, works every
         * lane once whatever its clock says and leaves what does not go.
         */
        private void flushOutbound(boolean finalAttempt) {
            drainOutbound();
            long now = System.currentTimeMillis();
            for (String webhook : this.lanes.due(finalAttempt ? Long.MAX_VALUE : now)) {
                if (this.postingDisabled.contains(webhook)) {
                    dropLane(webhook);
                    continue;
                }
                Outbound next;
                while ((next = this.lanes.peek(webhook)) != null) {
                    if (!finalAttempt) {
                        long held = heldUntil(webhook, next,
                                System.currentTimeMillis());
                        if (held > 0L) {
                            this.lanes.delay(webhook, held);
                            if (next.isTracked()) {
                                this.tracker.limited(next.messageId);
                            }
                            break;
                        }
                    }
                    long outcome;
                    try {
                        outcome = send(webhook, next);
                    } catch (IOException exception) {
                        sendFailed(webhook, next, exception, finalAttempt);
                        break;
                    } catch (RuntimeException exception) {
                        sendFailed(webhook, next, exception, finalAttempt);
                        break;
                    }
                    if (outcome > 0L) {
                        if (finalAttempt) {
                            FMLLog.info("[%s] Discord limited the bridge's last "
                                    + "post; leaving it", LostTalesMetaData.MOD_ID);
                        }
                        this.lanes.delay(webhook,
                                System.currentTimeMillis() + outcome);
                        if (next.isTracked()) {
                            this.tracker.limited(next.messageId);
                        }
                        break;
                    }
                    this.lanes.poll(webhook);
                    if (outcome == DELIVERED) {
                        this.lanes.succeeded(webhook);
                        postSucceeded(webhook);
                        if (next.isTracked()) {
                            emitMark(this.tracker.delivered(next.messageId));
                        }
                    }
                    if (this.postingDisabled.contains(webhook)) {
                        dropLane(webhook);
                        break;
                    }
                }
            }
        }

        /**
         * A send that threw. On the last attempt at shutdown it is said
         * and left. Otherwise its lane is held back for the lane's next
         * pause, and the entry is given up once it has failed
         * {@link #MAX_SEND_ATTEMPTS} times at the head of that lane —
         * counted by the lane, since one correction can wait in several.
         */
        private void sendFailed(String webhook, Outbound entry,
                                Exception exception, boolean finalAttempt) {
            if (finalAttempt) {
                FMLLog.info("[%s] Discord bridge could not send its last "
                        + "post: %s", LostTalesMetaData.MOD_ID,
                        exception.toString());
                return;
            }
            postFailed(webhook, exception.toString());
            this.lanes.failed(webhook, System.currentTimeMillis());
            if (entry.isTracked()) {
                this.tracker.failed(entry.messageId);
            }
            if (this.lanes.headFailures(webhook) >= MAX_SEND_ATTEMPTS) {
                this.lanes.poll(webhook);
                FMLLog.warning("[%s] Discord bridge gave up on a %s after %d "
                        + "tries: %s", LostTalesMetaData.MOD_ID,
                        entryKind(entry), Integer.valueOf(MAX_SEND_ATTEMPTS),
                        exception.toString());
                if (entry.isTracked()) {
                    emitMark(this.tracker.lost(entry.messageId,
                            ChatDeliveryMark.Reason.GAVE_UP));
                }
            }
        }

        /**
         * Forgets everything waiting for a webhook that is off; the
         * senders of the players' lines among it are told they will not
         * arrive.
         */
        private void dropLane(String webhook) {
            for (Outbound waiting : this.lanes.items(webhook)) {
                if (waiting.isTracked()) {
                    emitMark(this.tracker.lost(waiting.messageId,
                            ChatDeliveryMark.Reason.WEBHOOK_OFF));
                }
            }
            this.lanes.drop(webhook);
        }

        /**
         * When an entry on {@code lane} may be sent, or 0 for now: the
         * reset of its route's bucket while Discord's headers say the
         * bucket is spent, and for the bot's reactions the end of its
         * global limit too.
         */
        private long heldUntil(String lane, Outbound next, long nowMillis) {
            String route = routeOf(next);
            long held = this.buckets.holdUntil(route, lane, nowMillis);
            if (DiscordRateBuckets.ROUTE_REACTION.equals(route)) {
                held = Math.max(held, this.buckets.globalHoldUntil(nowMillis));
            }
            return held;
        }

        /** The Discord route an entry's request goes to, whose bucket limits it. */
        private String routeOf(Outbound entry) {
            switch (entry.kind) {
                case POST:
                    return DiscordRateBuckets.ROUTE_WEBHOOK_POST;
                case EDIT:
                    return DiscordRateBuckets.ROUTE_WEBHOOK_EDIT;
                case DELETE:
                    return DiscordRateBuckets.ROUTE_WEBHOOK_DELETE;
                default:
                    return DiscordRateBuckets.ROUTE_REACTION;
            }
        }

        /**
         * Hands a mark to the server thread. With {@link #MAX_QUEUED_MARKS}
         * already waiting, the mark is dropped, and that is said once.
         */
        private void emitMark(DiscordDeliveryTracker.Mark mark) {
            if (mark == null) {
                return;
            }
            if (this.markCount.get() >= MAX_QUEUED_MARKS) {
                if (!this.markDropLogged) {
                    this.markDropLogged = true;
                    FMLLog.warning("[%s] Discord bridge has %d delivery marks "
                            + "waiting for the server; newer ones are dropped "
                            + "until it catches up", LostTalesMetaData.MOD_ID,
                            Integer.valueOf(MAX_QUEUED_MARKS));
                }
                return;
            }
            this.markCount.incrementAndGet();
            this.marks.add(mark);
        }

        private void emitMarks(List<DiscordDeliveryTracker.Mark> batch) {
            for (DiscordDeliveryTracker.Mark mark : batch) {
                emitMark(mark);
            }
        }

        /** What an entry is, for a log line: never its words or its webhook. */
        private String entryKind(Outbound entry) {
            switch (entry.kind) {
                case POST:
                    return entry.notice != null ? "notice" : "post";
                case EDIT:
                    return "edit";
                case DELETE:
                    return "removal";
                default:
                    return "reaction";
            }
        }

        /**
         * Sorts the intake into lanes. A post goes into its webhook's
         * lane, behind everything already there; an edit or a removal
         * goes into the lane of every webhook that may correct a live
         * copy of its message, or still has its post waiting — the
         * intake is in order, so the post is always ahead of the
         * correction in its lane. A lane that is full refuses the
         * newest, as the intake itself does. The sender of a player's
         * line is told at once when a post of it is refused here, by a
         * full lane or a webhook that is off.
         */
        private void drainOutbound() {
            Outbound next;
            while ((next = outbound.poll()) != null) {
                outboundCount.decrementAndGet();
                if (next.kind == Outbound.Kind.POST) {
                    DiscordChannelBinding binding = this.bindings.byId(next.bindingKey);
                    String webhook = binding == null ? "" : binding.getWebhookUrl();
                    if (next.isTracked()) {
                        this.tracker.queued(next.messageId, next.senderId,
                                next.queuedAtMillis);
                    }
                    if (webhook.length() == 0 || this.postingDisabled.contains(webhook)) {
                        if (next.isTracked()) {
                            emitMark(this.tracker.lost(next.messageId,
                                    ChatDeliveryMark.Reason.WEBHOOK_OFF));
                        }
                    } else if (!queueOnLane(webhook, next) && next.isTracked()) {
                        emitMark(this.tracker.lost(next.messageId,
                                ChatDeliveryMark.Reason.QUEUE_FULL));
                    }
                    continue;
                }
                if (next.kind == Outbound.Kind.REACT
                        || next.kind == Outbound.Kind.UNREACT) {
                    routeReaction(next);
                    continue;
                }
                Set<String> webhooks = new HashSet<String>();
                for (DiscordMessageLinks.Copy copy : links.copiesOf(next.messageId)) {
                    String webhook = webhookOf(copy, next);
                    if (webhook.length() > 0) {
                        webhooks.add(webhook);
                    }
                }
                for (String webhook : this.lanes.webhooks()) {
                    for (Outbound waiting : this.lanes.items(webhook)) {
                        if (waiting.kind == Outbound.Kind.POST
                                && waiting.messageId == next.messageId) {
                            webhooks.add(webhook);
                            break;
                        }
                    }
                }
                for (String webhook : webhooks) {
                    if (!this.postingDisabled.contains(webhook)) {
                        queueOnLane(webhook, next);
                    }
                }
            }
        }

        /**
         * Puts a reaction on the lane of every live copy of its message:
         * a copy posted through a webhook on that webhook's lane, behind
         * its post when the post still waits, so the reaction always
         * finds the copy made; a Discord member's own message on a lane
         * of its channel's own. The reaction is the bot's, made through
         * its token, so the lane is only an order to keep. A copy in a
         * Discord channel the message's game channel no longer posts
         * into gets none.
         */
        private void routeReaction(Outbound next) {
            Set<String> lanesFor = new LinkedHashSet<String>();
            for (DiscordMessageLinks.Copy copy : DiscordCopyLiveness.liveCopies(
                    links, this.bindings, next.messageId, next.channel,
                    next.factionScope, DiscordCopyLiveness.Crossing.TO_DISCORD,
                    this.known)) {
                lanesFor.add(copy.webhookUrl.length() > 0 ? copy.webhookUrl
                        : copy.destination);
            }
            for (String webhook : this.lanes.webhooks()) {
                for (Outbound waiting : this.lanes.items(webhook)) {
                    if (waiting.kind == Outbound.Kind.POST
                            && waiting.messageId == next.messageId) {
                        lanesFor.add(webhook);
                        break;
                    }
                }
            }
            for (String lane : lanesFor) {
                if (lane.length() > 0 && !this.postingDisabled.contains(lane)) {
                    queueOnLane(lane, next);
                }
            }
        }

        /**
         * The bot's own reaction put on, or taken off, the copy of a
         * message that lives on {@code lane}. Answers how long Discord
         * asked to wait when it limited the request, {@link #DELIVERED}
         * when it took the reaction, and {@link #SPENT} when the entry is
         * done without it: no live copy there, no channel to name, no bot
         * token, or Discord refusing — said once, since the one usual
         * cause is a permission the bot lacks. A custom emoji from a
         * server the bot is not in is refused as Unknown Emoji, and said
         * once on its own; the players' reaction stays in the game.
         */
        private long sendReaction(String lane, Outbound next) throws IOException {
            DiscordMessageLinks.Copy copy = null;
            for (DiscordMessageLinks.Copy candidate : links.copiesOf(next.messageId)) {
                if ((lane.equals(candidate.webhookUrl)
                        || (candidate.webhookUrl.length() == 0
                                && lane.equals(candidate.destination)))
                        && DiscordCopyLiveness.isLive(this.bindings, next.channel,
                                next.factionScope, candidate,
                                DiscordCopyLiveness.Crossing.TO_DISCORD, this.known)) {
                    copy = candidate;
                    break;
                }
            }
            if (copy == null) {
                return 0L;
            }
            String channelId = DiscordCopyLiveness.channelIdOf(copy.destination);
            if (channelId.length() == 0 && copy.webhookUrl.length() > 0) {
                DiscordJson.ChannelInfo info = webhookInfo(copy.webhookUrl);
                channelId = info == null ? "" : info.channelId;
            }
            String token = LostTalesConfig.discordBotToken.trim();
            if (channelId.length() == 0 || token.length() == 0) {
                return 0L;
            }
            DiscordHttp.Reply reply = next.kind == Outbound.Kind.REACT
                    ? DiscordHttp.putOwnReaction(token, channelId,
                            copy.discordId, next.message)
                    : DiscordHttp.deleteOwnReaction(token, channelId,
                            copy.discordId, next.message);
            long now = System.currentTimeMillis();
            this.buckets.observe(DiscordRateBuckets.ROUTE_REACTION, lane,
                    reply.limit, now);
            if (reply.status == 429) {
                long asked = DiscordJson.retryAfterMillis(reply.body);
                long wait = asked > 0L ? asked : MIN_BACKOFF_MILLIS;
                if (reply.limit.global) {
                    // The bot's own limit: every reaction waits it out,
                    // whichever lane it is on.
                    this.buckets.holdGlobal(now + wait);
                }
                return wait;
            }
            if (reply.status == 400 && DiscordJson.errorCode(reply.body)
                    == DiscordJson.ERROR_UNKNOWN_EMOJI) {
                if (!this.unknownEmojiLogged) {
                    this.unknownEmojiLogged = true;
                    FMLLog.warning("[%s] Discord refused the bridge's reaction "
                            + "with an emoji the bot cannot use (Unknown "
                            + "Emoji): a custom emoji from a server the bot "
                            + "is not in stays a game reaction only",
                            LostTalesMetaData.MOD_ID);
                }
                return 0L;
            }
            if (reply.status == 400 || reply.status == 403
                    || reply.status == 404) {
                if (!this.reactionRefusalLogged) {
                    this.reactionRefusalLogged = true;
                    FMLLog.warning("[%s] Discord refused the bridge's reaction "
                            + "(HTTP %d): the bot needs Add Reactions and Read "
                            + "Message History in the bound channel",
                            LostTalesMetaData.MOD_ID,
                            Integer.valueOf(reply.status));
                }
                return 0L;
            }
            if (!reply.isSuccess()) {
                throw new IOException("Discord replied HTTP " + reply.status
                        + " to a reaction");
            }
            return DELIVERED;
        }

        /**
         * Puts an entry on a webhook's lane, or drops it when the lane
         * is full — a webhook Discord keeps limiting — saying so once
         * per lane, with a count of what it has cost since, so a
         * silent gap in a channel can be read back to its cause.
         * Answers whether the entry was queued.
         */
        private boolean queueOnLane(String webhook, Outbound entry) {
            if (this.lanes.add(webhook, entry)) {
                return true;
            }
            Integer dropped = this.droppedByLane.get(webhook);
            int count = (dropped == null ? 0 : dropped.intValue()) + 1;
            this.droppedByLane.put(webhook, Integer.valueOf(count));
            if (count == 1) {
                FMLLog.warning("[%s] A Discord webhook has %d posts waiting "
                        + "and is not taking more; newer posts to it are "
                        + "dropped until it catches up", LostTalesMetaData.MOD_ID,
                        Integer.valueOf(DiscordOutboundLanes.MAX_PER_LANE));
            }
            return false;
        }

        /**
         * Sends one entry through a webhook: a line or a notice posted,
         * or the copy that went through this webhook corrected. Answers
         * how long Discord asked the webhook to wait when it limited the
         * request — the entry then stays at its lane's head —
         * {@link #DELIVERED} when Discord took it, and {@link #SPENT}
         * when the entry is done without reaching it: a webhook Discord
         * refuses outright (deleted, or its URL wrong) is off for the
         * session with one log line; a copy Discord no longer has, or a
         * message that never went through this webhook, is nothing to
         * correct; a request Discord refuses is not sent again. Any
         * other failure throws, and its lane backs off.
         */
        private long send(String webhook, Outbound next) throws IOException {
            if (next.kind == Outbound.Kind.REACT
                    || next.kind == Outbound.Kind.UNREACT) {
                return sendReaction(webhook, next);
            }
            DiscordHttp.Reply reply;
            String header = "";
            if (next.kind == Outbound.Kind.POST && next.notice != null) {
                // A notice is an embed under the webhook's own name; it
                // answers nothing and is never edited or linked.
                reply = DiscordHttp.postWebhook(webhook,
                        DiscordJson.webhookEmbedBody(next.notice));
            } else if (next.kind == Outbound.Kind.POST) {
                header = replyHeader(next, webhook);
                reply = DiscordHttp.postWebhook(webhook,
                        DiscordJson.webhookLineBody(next.username,
                                next.avatarUrl, header
                                        + DiscordMessageLinkRewriter.outbound(
                                                filtered(next.message),
                                                outboundResolver(webhook))));
            } else {
                DiscordMessageLinks.Copy copy = copyThrough(next, webhook);
                if (copy == null || (next.kind == Outbound.Kind.EDIT
                        && !patchSupported())) {
                    return 0L;
                }
                reply = next.kind == Outbound.Kind.EDIT
                        ? DiscordHttp.editWebhookMessage(webhook, copy.discordId,
                                DiscordJson.webhookLineEditBody(copy.header
                                        + DiscordMessageLinkRewriter.outbound(
                                                filtered(next.message),
                                                outboundResolver(webhook))))
                        : DiscordHttp.deleteWebhookMessage(webhook, copy.discordId);
            }
            // What the reply says of the webhook's bucket, a limit's own
            // reply among them: what the lane's next request waits by.
            this.buckets.observe(routeOf(next), webhook, reply.limit,
                    System.currentTimeMillis());
            if (next.kind != Outbound.Kind.POST && reply.status == 404) {
                // The copy is gone from Discord: nothing is left to correct.
                return 0L;
            }
            if (reply.status == 429) {
                long asked = DiscordJson.retryAfterMillis(reply.body);
                return asked > 0L ? asked : MIN_BACKOFF_MILLIS;
            }
            if (next.kind == Outbound.Kind.POST
                    && (reply.status == 401 || reply.status == 404)) {
                // The webhook itself is gone or refused: no post through
                // it can succeed until it is fixed and the server
                // restarts. Every other webhook goes on.
                this.postingDisabled.add(webhook);
                DiscordChannelBinding binding = this.bindings.byId(next.bindingKey);
                FMLLog.severe("[%s] Discord refused the webhook of binding "
                        + "%s (HTTP %d): the webhook was deleted or its URL "
                        + "is wrong; posting there is off until the server "
                        + "restarts", LostTalesMetaData.MOD_ID,
                        binding == null ? next.bindingKey : binding.id(),
                        Integer.valueOf(reply.status));
                if (next.isTracked()) {
                    emitMark(this.tracker.lost(next.messageId,
                            ChatDeliveryMark.Reason.WEBHOOK_OFF));
                }
                return 0L;
            }
            if (reply.status >= 400 && reply.status < 500) {
                // Discord refused the request itself — a name or a
                // message it will not take, a copy the webhook may not
                // touch — so sending it again cannot succeed: it is spent
                // here rather than holding the webhook's lane.
                noteRefused(next, reply);
                if (next.isTracked()) {
                    emitMark(this.tracker.lost(next.messageId,
                            ChatDeliveryMark.Reason.REFUSED));
                }
                return 0L;
            }
            if (!reply.isSuccess()) {
                throw new IOException("Discord replied HTTP " + reply.status
                        + " to a webhook " + (next.kind == Outbound.Kind.POST
                                ? "post" : next.kind == Outbound.Kind.EDIT
                                        ? "edit" : "delete"));
            }
            if (next.kind == Outbound.Kind.POST && next.notice == null) {
                // The post's Discord id, from the wait=true body: what a
                // reply from either side finds the message by, and what
                // an edit or a removal follows it by, with the header an
                // edit has to open with again, the webhook it went
                // through, and its binding, which the save keeps in the
                // webhook's place. A body that does not parse leaves the
                // line unlinked.
                links.link(next.messageId,
                        DiscordJson.parseCreatedMessageId(reply.body),
                        header, destinationOf(webhook), webhook,
                        next.bindingKey);
            }
            return DELIVERED;
        }

        /**
         * Says once for each kind of request and status that Discord
         * refused one outright, with the error it gave, since every such
         * refusal is something that does not reach Discord.
         */
        private void noteRefused(Outbound entry, DiscordHttp.Reply reply) {
            String kind = entryKind(entry);
            if (!this.refusalsLogged.add(kind + ':' + reply.status)) {
                return;
            }
            FMLLog.warning("[%s] Discord refused a webhook %s (HTTP %d, "
                    + "error %s) and it was not sent; further refusals like "
                    + "it are not logged. Discord refuses a post whose name "
                    + "or text it will not take", LostTalesMetaData.MOD_ID,
                    kind, Integer.valueOf(reply.status),
                    String.valueOf(DiscordJson.errorCode(reply.body)));
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
            // The copy in the very channel this post goes to, while it is
            // live: a quote points at its own channel's original, never
            // across guilds. A quote names a line of the post's own
            // conversation, so the post's channel is the one asked.
            String destination = destinationOf(webhookUrl);
            String discordId = links.discordIdOf(next.reply.getMessageId(),
                    destination);
            if (discordId.length() > 0 && DiscordCopyLiveness.isLive(this.bindings,
                    next.channel, next.factionScope,
                    DiscordCopyLiveness.channelIdOf(destination), webhookUrl,
                    DiscordCopyLiveness.Crossing.TO_DISCORD, this.known)) {
                DiscordJson.ChannelInfo info = webhookInfo(webhookUrl);
                if (info != null) {
                    jumpUrl = "https://discord.com/channels/" + info.guildId
                            + "/" + info.channelId + "/" + discordId;
                }
            }
            return DiscordMessageSanitizer.replyHeader(
                    next.reply.getAuthor(), filtered(next.reply.getExcerpt()),
                    jumpUrl);
        }

        /**
         * A player's words as the server posts them to Discord: the
         * profanity list's words read as {@code discord.profanityFilter}
         * says, the tokens, emojis and links left alone. What arrives
         * from Discord is not touched here; each client shows it by its
         * own setting.
         */
        private String filtered(String text) {
            return ChatProfanityFilter.filterMessage(text,
                    ChatProfanityMode.of(LostTalesConfig.discordProfanityFilter,
                            ChatProfanityMode.OFF),
                    ChatProfanityCatalog.effective());
        }

        /**
         * The guild and channel the webhook posts to, asked of the
         * webhook's own URL; null while Discord has not said. The
         * liveness rule, reply headers, jump links and typing all go by
         * it, so a lookup that failed is asked again after a pause that
         * doubles up to a minute rather than given up for the session.
         * A 401 or 404 means the webhook is gone or its URL is wrong,
         * and is not asked again.
         */
        private DiscordJson.ChannelInfo webhookInfo(String webhookUrl) {
            if (this.webhookInfos.containsKey(webhookUrl)) {
                return this.webhookInfos.get(webhookUrl);
            }
            long now = System.currentTimeMillis();
            if (!this.webhookLookups.mayAsk(webhookUrl, now)) {
                return null;
            }
            String cause;
            try {
                DiscordHttp.Reply reply = DiscordHttp.getWebhookInfo(webhookUrl);
                if (reply.status == 401 || reply.status == 404) {
                    this.webhookInfos.put(webhookUrl, null);
                    noteWebhookInfoFailure("HTTP " + reply.status, false);
                    return null;
                }
                DiscordJson.ChannelInfo info = reply.isSuccess()
                        ? DiscordJson.parseWebhookInfo(reply.body) : null;
                if (info != null) {
                    this.webhookInfos.put(webhookUrl, info);
                    this.webhookLookups.answered(webhookUrl);
                    return info;
                }
                cause = "HTTP " + reply.status;
            } catch (IOException exception) {
                cause = exception.toString();
            } catch (RuntimeException exception) {
                cause = exception.toString();
            }
            this.webhookLookups.failed(webhookUrl, now);
            noteWebhookInfoFailure(cause, true);
            return null;
        }

        private boolean typingFailureLogged;
        private boolean webhookInfoFailureLogged;

        private void noteTypingFailure(Exception exception) {
            if (this.typingFailureLogged) {
                return;
            }
            this.typingFailureLogged = true;
            FMLLog.warning("[%s] The Discord bot could not show typing "
                    + "presence; it will keep trying quietly: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
        }

        /**
         * Said once a session: what an unanswered lookup holds back
         * otherwise has no explanation anywhere.
         */
        private void noteWebhookInfoFailure(String cause, boolean askedAgain) {
            if (this.webhookInfoFailureLogged) {
                return;
            }
            this.webhookInfoFailureLogged = true;
            FMLLog.warning("[%s] Discord did not say where a webhook posts (%s). "
                    + "While it does not, edits and deletions of copies restored "
                    + "from the save are not sent through it, nor, for an entry "
                    + "naming no Discord channel, the bot's reactions and jump "
                    + "links on them; reply quotes posted through it carry no "
                    + "jump link, and its new posts are saved without their "
                    + "channel. %s", LostTalesMetaData.MOD_ID, cause,
                    askedAgain ? "It is asked again after a pause."
                            : "Discord refused the webhook, so it is not asked again.");
        }

        /**
         * A read that failed: said once until reading goes through again,
         * and answered with the pause before the next read, which doubles
         * with every failure in a row up to a minute.
         */
        private long readFailed(String reason) {
            if (this.readsHealthy) {
                this.readsHealthy = false;
                FMLLog.warning("[%s] Discord bridge failing to read, retrying "
                        + "with backoff: %s", LostTalesMetaData.MOD_ID, reason);
            }
            long wait = this.backoffMillis;
            this.backoffMillis = Math.min(MAX_BACKOFF_MILLIS,
                    this.backoffMillis * 2L);
            return wait;
        }

        private void readSucceeded() {
            if (!this.readsHealthy) {
                this.readsHealthy = true;
                FMLLog.info("[%s] Discord bridge reading again",
                        LostTalesMetaData.MOD_ID);
            }
            this.backoffMillis = MIN_BACKOFF_MILLIS;
        }

        /**
         * A send through {@code lane} that failed: said once until that
         * lane delivers again, so one dead webhook beside a healthy one
         * is not reported over and over. The pause before the next try
         * is the lane's own.
         */
        private void postFailed(String lane, String reason) {
            if (this.failingLanes.add(lane)) {
                FMLLog.warning("[%s] Discord bridge failing to post%s, "
                        + "retrying with backoff: %s", LostTalesMetaData.MOD_ID,
                        laneName(lane), reason);
            }
        }

        private void postSucceeded(String lane) {
            if (this.failingLanes.remove(lane)) {
                FMLLog.info("[%s] Discord bridge posting again%s",
                        LostTalesMetaData.MOD_ID, laneName(lane));
            }
        }

        /**
         * How a lane is named in a log line: the Discord channel its
         * webhook posts to when that is already known, never its URL.
         */
        private String laneName(String lane) {
            DiscordJson.ChannelInfo info = lane.length() == 0 ? null
                    : this.webhookInfos.get(lane);
            return info == null || info.channelId == null
                    || info.channelId.length() == 0 ? ""
                    : " to channel " + info.channelId;
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
