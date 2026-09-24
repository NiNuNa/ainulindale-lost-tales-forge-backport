package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.character.identity.PlayableIdentityResolver;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.chat.ChatBroadcastMarkers;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatPresentationMode;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatSystemLineClassifier;
import com.ninuna.losttales.chat.ChatTabIds;
import com.ninuna.losttales.compat.discord.DiscordGameEventRelay;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * Where every server-wide line passes on its way out — an achievement,
 * a death, a join or a leave, {@code /say}, another mod's announcement —
 * patched by the coremod into the head of
 * {@code ServerConfigurationManager.sendChatMsg}, and where every line
 * sent to one player passes, patched into the head of
 * {@code EntityPlayerMP.addChatMessage}. Three things happen to a
 * server-wide line here: a join or a leave is made to name the account
 * rather than the character the game's display name gives, since it is
 * out-of-character news about an account ({@link #namingTheAccount});
 * the line is given a message id of the server's own and recorded in
 * the chat history under the Server's name, so a reply, a reaction or a
 * link to it names the same message on every client; and the Discord
 * relay is told, with that id, so the embed it posts is linked to the
 * line. The id rides the component as an empty run, and beside it a run
 * for each player the line names ({@link ChatBroadcastMarkers}), so a
 * mention in the line reaches the right person on every client and
 * still opens their card once they have gone. A line the game hands
 * each player on its own that is still shared news, as LOTR's
 * travelling trader is, gets one record and one id for every copy. A
 * line sent to one player that answers a command they typed is recorded
 * for that account alone, under the tab the command was typed in
 * ({@link #onPlayerLine}), so the answer comes back with the rest of the
 * tab's history. A line is never delayed or refused: whatever fails,
 * the component goes out as it came.
 *
 * <p>A join line's id is also kept for the login replay of the player
 * it announces ({@link #takeJoinLine}). The game announces a join just
 * before the player's login event, so the line is the first thing said
 * once they are there: everything with a smaller id is history to them,
 * the line itself and everything after it is not.</p>
 */
public final class LostTalesServerBroadcastHook {
    private static volatile boolean failureLogged;

    /**
     * How long a join line waits for its player's login replay. The two
     * happen on the same tick; the margin only keeps a line whose replay
     * never ran from marking a later visit.
     */
    private static final long JOIN_LINE_WAIT_MILLIS = 60000L;
    /** More joins waiting than this is more than a tick holds; the oldest go. */
    private static final int MAX_WAITING_JOIN_LINES = 64;
    /** The waiting join lines, by the joining account's name in lower case. */
    private static final Map<String, JoinLine> JOIN_LINES =
            new LinkedHashMap<String, JoinLine>();
    /** What vanilla's display name suggests on a click, before the account. */
    private static final String WHISPER_SUGGESTION = "/msg ";
    /**
     * How long a shared line handed to each player on its own keeps its
     * id for the next copy. The copies go out in one loop, on one tick.
     */
    private static final long SHARED_COPY_MILLIS = 1000L;
    /** More shared lines going out at once than this is more than a tick holds. */
    private static final int MAX_SHARED_COPIES = 16;
    /** The shared lines whose copies are going out, by their chat JSON. */
    private static final Map<String, SharedCopy> SHARED_COPIES =
            new LinkedHashMap<String, SharedCopy>();

    private LostTalesServerBroadcastHook() {}

    /**
     * Sees a line about to be broadcast and hands back the one to send:
     * a join or a leave naming the account, with its id and its named
     * players appended as empty runs. The relay is told after, with the
     * id; the runs are empty, so the relay reads the line's words as they
     * go out.
     */
    public static IChatComponent onBroadcast(IChatComponent message) {
        if (message == null) {
            return null;
        }
        IChatComponent line = message;
        long messageId = ChatMessageIds.NONE;
        try {
            line = namingTheAccount(message);
            ChatChannel channel = ChatSystemLineClassifier.classify(line);
            if (channel != null) {
                messageId = stamp(line, channel);
            }
        } catch (Throwable throwable) {
            logOnce("name", throwable);
        }
        try {
            DiscordGameEventRelay.onServerBroadcast(line, messageId);
        } catch (Throwable throwable) {
            logOnce("relay", throwable);
        }
        return line;
    }

    /**
     * A join or a leave rebuilt to name the account where the game wrote
     * the player's display name, which is their character's: who comes
     * and goes is an account, and the line stands in OOC. The account is
     * read off the name's own {@code /msg <account>} click, which vanilla
     * puts on every player name it announces. Any other line, or a join
     * whose name carries no such click, is handed back as it came. The
     * rebuilt line keeps the line's style, the name's click, and every
     * run appended to the line.
     */
    static IChatComponent namingTheAccount(IChatComponent message) {
        ChatSystemLineClassifier.Kind kind = ChatSystemLineClassifier.kindOf(message);
        if (kind != ChatSystemLineClassifier.Kind.JOIN
                && kind != ChatSystemLineClassifier.Kind.LEAVE) {
            return message;
        }
        ChatComponentTranslation translation = (ChatComponentTranslation)message;
        Object[] arguments = translation.getFormatArgs();
        if (arguments == null || arguments.length == 0
                || !(arguments[0] instanceof IChatComponent)) {
            return message;
        }
        IChatComponent name = (IChatComponent)arguments[0];
        String account = whisperedAccount(name);
        if (account == null) {
            return message;
        }
        ChatComponentText accountName = new ChatComponentText(account);
        accountName.setChatStyle(name.getChatStyle().createShallowCopy());
        Object[] renamed = arguments.clone();
        renamed[0] = accountName;
        ChatComponentTranslation rebuilt =
                new ChatComponentTranslation(translation.getKey(), renamed);
        rebuilt.setChatStyle(message.getChatStyle().createShallowCopy());
        // The arguments inherit from the line's style, as the game's own
        // line's do; setting a style re-parents the siblings alone.
        for (Object argument : renamed) {
            if (argument instanceof IChatComponent) {
                ((IChatComponent)argument).getChatStyle()
                        .setParentStyle(rebuilt.getChatStyle());
            }
        }
        for (Object sibling : message.getSiblings()) {
            if (sibling instanceof IChatComponent) {
                rebuilt.appendSibling((IChatComponent)sibling);
            }
        }
        return rebuilt;
    }

    /**
     * Sees a line about to be sent to one player and hands back the one
     * to send, or null for one this player is not sent. Vanilla's notice
     * to operators of what a command did — {@code [Server: Opped Nils]} —
     * is not sent to a reader of the Server Console when the console has
     * just recorded that command: it holds it already. A shared line the
     * game hands each player on its own is stamped as a broadcast is, once
     * for all its copies. A line that answers a command the player typed
     * from a chat tab — their running command's context names the tab —
     * gets an id and its named players and is recorded for that account
     * alone under that tab. Any other line to one player — a countdown, a
     * notice, another mod's word — passes unrecorded: it is this player's
     * alone.
     */
    public static IChatComponent onPlayerLine(EntityPlayerMP player,
                                              IChatComponent message) {
        if (player == null || message == null) {
            return message;
        }
        try {
            UUID account = player.getUniqueID();
            if (account == null
                    || (player.worldObj != null && player.worldObj.isRemote)) {
                return message;
            }
            if (ChatSystemLineClassifier.isAdminNotice(message)
                    && ChatConsoleCommandHandler.recordedJustNow(
                            ChatSystemLineClassifier.adminNoticeActor(message),
                            System.currentTimeMillis())
                    && ChatChannelPolicy.readsConsole(player)) {
                return null;
            }
            ChatChannel shared = ChatSystemLineClassifier.classify(message);
            if (shared == ChatChannel.GLOBAL || shared == ChatChannel.OOC) {
                stampCopy(message, shared);
                return message;
            }
            if (shared != ChatChannel.CLIENT_CONSOLE) {
                return message;
            }
            String tabId = ChatCommandContexts.answerLine(account,
                    System.currentTimeMillis());
            if (tabId.length() == 0) {
                return message;
            }
            ChatChannel channel = ChatTabIds.channelOf(tabId);
            if (channel == null) {
                channel = ChatChannel.CLIENT_CONSOLE;
            }
            List<UUID> self = Collections.singletonList(account);
            List<ChatNamedPlayer> named = namedPlayers(message, channel);
            long messageId = record(message, channel, tabId, self,
                    ChatHistory.Audience.accounts(self, false), named);
            if (messageId != ChatMessageIds.NONE) {
                mark(message, messageId, named);
            }
        } catch (Throwable throwable) {
            logOnce("keep", throwable);
        }
        return message;
    }

    /**
     * Gives a server-wide line of {@code channel} an id and records it
     * for everyone online: they are the ones who can be shown it, so they
     * are the ones who may reply to it by that id. A line of an open
     * channel may be shown to anyone who comes later; one filed in the
     * Client Console, which is each player's own, only to those it was
     * sent to. Answers the id, or none for a line that could not be
     * recorded.
     */
    private static long stamp(IChatComponent message, ChatChannel channel) {
        List<UUID> recipients = onlineAccounts();
        if (recipients == null) {
            return ChatMessageIds.NONE;
        }
        List<ChatNamedPlayer> named = namedPlayers(message, channel);
        long messageId = record(message, channel, "", recipients,
                channel == ChatChannel.CLIENT_CONSOLE
                        ? ChatHistory.Audience.accounts(recipients, false)
                        : ChatHistory.Audience.everyone(), named);
        if (messageId == ChatMessageIds.NONE) {
            return ChatMessageIds.NONE;
        }
        if (ChatSystemLineClassifier.kindOf(message)
                == ChatSystemLineClassifier.Kind.JOIN) {
            noteJoinLine(joinerAccount(message), messageId);
        }
        mark(message, messageId, named);
        return messageId;
    }

    /**
     * Stamps one copy of a shared line the game hands each player on its
     * own: the first copy is recorded as a broadcast is, and every copy
     * after it within the same moment wears that record's id and names.
     */
    private static void stampCopy(IChatComponent message, ChatChannel channel) {
        String json = componentJson(message);
        if (json.length() == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        SharedCopy copy;
        synchronized (SHARED_COPIES) {
            copy = SHARED_COPIES.get(json);
            if (copy != null && now - copy.stampedMillis > SHARED_COPY_MILLIS) {
                SHARED_COPIES.remove(json);
                copy = null;
            }
        }
        if (copy != null) {
            mark(message, copy.messageId, copy.named);
            return;
        }
        List<ChatNamedPlayer> named = namedPlayers(message, channel);
        List<UUID> recipients = onlineAccounts();
        if (recipients == null) {
            return;
        }
        long messageId = record(message, channel, "", recipients,
                ChatHistory.Audience.everyone(), named);
        if (messageId == ChatMessageIds.NONE) {
            return;
        }
        mark(message, messageId, named);
        synchronized (SHARED_COPIES) {
            SHARED_COPIES.put(json, new SharedCopy(messageId, named, now));
            Iterator<String> oldest = SHARED_COPIES.keySet().iterator();
            while (SHARED_COPIES.size() > MAX_SHARED_COPIES && oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
        }
    }

    /** The ids of everyone online, or null with no server to ask. */
    private static List<UUID> onlineAccounts() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return null;
        }
        List<UUID> accounts = new ArrayList<UUID>();
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : online) {
            if (player != null && player.getUniqueID() != null) {
                accounts.add(player.getUniqueID());
            }
        }
        return accounts;
    }

    /**
     * Records a line of the server's own in {@code channel} for the
     * audience, under the tab it answers when it answers one, and
     * answers its new id — or none for a line with no words or no
     * server to record it on. The history keeps the words, cleaned as
     * a message is, under the Server's name in the Console's colour,
     * and beside them the component itself as the game's own chat JSON
     * — its hover, its colours, its links — with the players it names as
     * the channel presents them right now: an out-of-character channel
     * by their account, an in-character one by the identity they are
     * playing. A replay shows the line as the live one was shown, naming
     * players who may be long gone as they were named then.
     */
    private static long record(IChatComponent message, ChatChannel channel,
                               String tabId, List<UUID> recipients,
                               ChatHistory.Audience audience,
                               List<ChatNamedPlayer> named) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return ChatMessageIds.NONE;
        }
        String text = ChatMessageValidator.cleaned(message.getUnformattedText());
        if (text.length() == 0) {
            return ChatMessageIds.NONE;
        }
        long messageId = ChatMessageIdAllocator.next();
        LostTalesChatMessagePacket record = new LostTalesChatMessagePacket(
                channel, LostTalesChatMessagePacket.SERVER_SENDER_ID,
                SERVER_NAME, SERVER_NAME, "",
                LostTalesColors.rgb(LostTalesColors.HUD_LABEL),
                ChatChannel.CLIENT_CONSOLE.getDisplayColor(), text,
                System.currentTimeMillis(), "", null, "", "", 0, true,
                messageId, ChatReplyReference.NONE, "")
                .withServerBody(componentJson(message), named)
                .withTabId(tabId);
        ChatHistory.record(messageId, LostTalesChatMessagePacket.SERVER_SENDER_ID,
                SERVER_NAME, null, record, recipients, audience);
        return messageId;
    }

    /**
     * The runs appended to a recorded line, the server's word to every
     * client: its id, and each player it names.
     */
    private static void mark(IChatComponent message, long messageId,
                             List<ChatNamedPlayer> named) {
        appendMark(message, ChatBroadcastMarkers.value(messageId));
        for (ChatNamedPlayer player : named) {
            String value = ChatBroadcastMarkers.namedValue(player);
            if (value != null) {
                appendMark(message, value);
            }
        }
    }

    private static void appendMark(IChatComponent message, String value) {
        ChatComponentText mark = new ChatComponentText("");
        mark.setChatStyle(mark.getChatStyle().setChatClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND, value)));
        message.appendSibling(mark);
    }

    /** The name the server's lines are recorded under; the client shows its own word for it. */
    static final String SERVER_NAME = "Server";

    /**
     * The id of the line that announced {@code account}'s arrival, no
     * longer waiting once taken, or {@link ChatMessageIds#NONE} when no
     * line is waiting for them: another mod may have silenced the game's
     * announcement.
     */
    public static synchronized long takeJoinLine(String account) {
        if (account == null) {
            return ChatMessageIds.NONE;
        }
        JoinLine line = JOIN_LINES.remove(account.toLowerCase(Locale.ROOT));
        if (line == null || System.currentTimeMillis() - line.notedMillis
                > JOIN_LINE_WAIT_MILLIS) {
            return ChatMessageIds.NONE;
        }
        return line.messageId;
    }

    /** Cleared with the rest of the server's chat state. */
    public static synchronized void clear() {
        JOIN_LINES.clear();
        synchronized (SHARED_COPIES) {
            SHARED_COPIES.clear();
        }
    }

    /** Keeps a join line for the login replay of the account it announces. */
    static synchronized void noteJoinLine(String account, long messageId) {
        if (account == null || account.length() == 0) {
            return;
        }
        String key = account.toLowerCase(Locale.ROOT);
        JOIN_LINES.remove(key);
        JOIN_LINES.put(key, new JoinLine(messageId, System.currentTimeMillis()));
        Iterator<String> oldest = JOIN_LINES.keySet().iterator();
        while (JOIN_LINES.size() > MAX_WAITING_JOIN_LINES && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * The account a join line announces. The game writes the joining
     * player in as their display name, which suggests
     * {@code /msg <account> } on a click, and that is read first; the
     * name's own words stand in when the click says nothing. Null for a
     * line that is no translation or names nobody.
     */
    static String joinerAccount(IChatComponent message) {
        if (!(message instanceof ChatComponentTranslation)) {
            return null;
        }
        Object[] args = ((ChatComponentTranslation)message).getFormatArgs();
        if (args == null || args.length == 0 || args[0] == null) {
            return null;
        }
        if (!(args[0] instanceof IChatComponent)) {
            return String.valueOf(args[0]).trim();
        }
        IChatComponent name = (IChatComponent)args[0];
        String account = whisperedAccount(name);
        return account != null ? account : name.getUnformattedText().trim();
    }

    /**
     * The account a player's name component suggests whispering to —
     * {@code /msg <account> }, which vanilla puts on every name it
     * announces, whatever the name's words were rewritten to — or null
     * when the name carries no such click.
     */
    private static String whisperedAccount(IChatComponent name) {
        ClickEvent click = name.getChatStyle() == null ? null
                : name.getChatStyle().getChatClickEvent();
        String suggestion = click == null
                || click.getAction() != ClickEvent.Action.SUGGEST_COMMAND
                ? null : click.getValue();
        if (suggestion == null || !suggestion.startsWith(WHISPER_SUGGESTION)) {
            return null;
        }
        String account = suggestion.substring(WHISPER_SUGGESTION.length()).trim();
        return account.length() > 0 ? account : null;
    }

    /**
     * The component as the JSON the game itself sends chat in, or
     * empty when it cannot be written. Whether it fits the packet is
     * the packet's own check.
     */
    private static String componentJson(IChatComponent message) {
        try {
            String json = IChatComponent.Serializer.func_150696_a(message);
            return json == null ? "" : json;
        } catch (RuntimeException unwritable) {
            return "";
        }
    }

    /**
     * Every player the line names, whole: by their account on an
     * out-of-character channel, else as the identity they are playing,
     * the name their own Global line would be signed with; so the line
     * names them the same on every client and in every replay. The player
     * a join line announces is not listed among those online yet when it
     * goes out, so they are found among those arriving
     * ({@link ChatArrivals}).
     */
    private static List<ChatNamedPlayer> namedPlayers(IChatComponent message,
                                                      ChatChannel channel) {
        List<ChatNamedPlayer> named = new ArrayList<ChatNamedPlayer>();
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return named;
        }
        String text = ChatMessageValidator.cleaned(message.getUnformattedText());
        boolean asAccounts = channel.getPresentation()
                == ChatPresentationMode.OUT_OF_CHARACTER;
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : online) {
            if (named.size() >= ChatNamedPlayer.MAX_PER_LINE) {
                break;
            }
            // The game writes a player in by their display name, which
            // is their character's; an account name still names them.
            if (player != null && (ChatNamedPlayer.names(text, accountOf(player))
                    || ChatNamedPlayer.names(text, player.getDisplayName()))) {
                named.add(asAccounts ? namedAccount(player) : namedPlayer(player));
            }
        }
        if (ChatSystemLineClassifier.kindOf(message)
                == ChatSystemLineClassifier.Kind.JOIN
                && named.size() < ChatNamedPlayer.MAX_PER_LINE) {
            ChatNamedPlayer joiner = ChatArrivals.take(joinerAccount(message));
            if (joiner != null
                    && ChatNamedPlayer.find(named, joiner.getAccount()) == null) {
                named.add(joiner);
            }
        }
        return named;
    }

    /**
     * The player as a line names them: their account, and the identity
     * they are playing right now with its skin — what a replay shows in
     * place of the account's name, and what a card about them shows once
     * they have gone.
     */
    public static ChatNamedPlayer namedPlayer(EntityPlayerMP player) {
        String account = accountOf(player);
        PlayableIdentityResolver.Resolution identity =
                PlayableIdentityResolver.resolve(player);
        RoleplayCharacter character = identity.isAvailable()
                ? identity.getCharacter() : null;
        String identityName = character == null ? account
                : PlayableIdentity.displayName(character, account);
        return new ChatNamedPlayer(player.getUniqueID(), account,
                character == null ? null : character.getCharacterId(),
                identityName,
                character == null ? "" : character.getSkinId());
    }

    /**
     * The player's account as an out-of-character line names it: how the
     * Server Console names whoever did what it records.
     */
    public static ChatNamedPlayer namedAccount(EntityPlayerMP player) {
        return ChatNamedPlayer.account(player.getUniqueID(), accountOf(player));
    }

    private static String accountOf(EntityPlayerMP player) {
        return player.getGameProfile() == null
                ? player.getCommandSenderName()
                : player.getGameProfile().getName();
    }

    private static void logOnce(String what, Throwable throwable) {
        if (!failureLogged) {
            failureLogged = true;
            FMLLog.warning("[LostTales] Could not %s a server broadcast: %s",
                    what, throwable);
        }
    }

    /** A shared line handed to each player on its own, while its copies go out. */
    private static final class SharedCopy {
        final long messageId;
        final List<ChatNamedPlayer> named;
        final long stampedMillis;

        SharedCopy(long messageId, List<ChatNamedPlayer> named,
                   long stampedMillis) {
            this.messageId = messageId;
            this.named = named;
            this.stampedMillis = stampedMillis;
        }
    }

    /** A join line waiting for its player's login replay. */
    private static final class JoinLine {
        final long messageId;
        final long notedMillis;

        JoinLine(long messageId, long notedMillis) {
            this.messageId = messageId;
            this.notedMillis = notedMillis;
        }
    }
}
