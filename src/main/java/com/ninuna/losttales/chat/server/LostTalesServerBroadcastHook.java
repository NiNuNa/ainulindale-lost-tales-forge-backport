package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.character.identity.PlayableIdentityResolver;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.chat.ChatBroadcastIdMarkers;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMentions;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.chat.ChatSystemLineClassifier;
import com.ninuna.losttales.chat.ChatTabIds;
import com.ninuna.losttales.compat.discord.DiscordGameEventRelay;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.Arrays;
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
 * a death, a join or a leave, {@code /say} — patched by the coremod
 * into the head of {@code ServerConfigurationManager.sendChatMsg}, and
 * where every line sent to one player passes, patched into the head of
 * {@code EntityPlayerMP.addChatMessage}. Two things happen to a
 * server-wide line here: the Discord relay is told, and a line that
 * will land in Global is given a message id of the server's own,
 * carried as an empty run on the component and recorded in the chat
 * history under the Server's name, so a reply to it on any client
 * names the same message and a click on the quote finds it. A line
 * sent to one player that answers a command they typed is recorded the
 * same way, for that account alone, under the tab the command was
 * typed in ({@link #onPlayerLine}), so the answer comes back with the
 * rest of the tab's history. The words are never changed, and a line
 * is never delayed or refused: whatever fails, the component goes out
 * as it came.
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

    private LostTalesServerBroadcastHook() {}

    /**
     * Sees a line about to be broadcast and hands back the one to send:
     * the same component, with an id run appended when the line is
     * Global's. The relay is told first, of the line as it came.
     */
    public static IChatComponent onBroadcast(IChatComponent message) {
        if (message == null) {
            return null;
        }
        try {
            DiscordGameEventRelay.onServerBroadcast(message);
        } catch (Throwable throwable) {
            logOnce("relay", throwable);
        }
        try {
            if (ChatSystemLineClassifier.classify(message) == ChatChannel.ALL) {
                stamp(message);
            }
        } catch (Throwable throwable) {
            logOnce("name", throwable);
        }
        return message;
    }

    /**
     * Sees a line about to be sent to one player and hands back the one
     * to send: the same component, with an id run appended when the
     * line answers a command the player typed from a chat tab — their
     * running command's context names the tab — and is then recorded
     * for that account alone under that tab. Any other line to one
     * player — a countdown, a notice, another mod's word — passes
     * unrecorded, as it did before.
     */
    public static IChatComponent onPlayerLine(EntityPlayerMP player,
                                              IChatComponent message) {
        if (player == null || message == null) {
            return message;
        }
        try {
            UUID account = player.getUniqueID();
            if (account == null
                    || (player.worldObj != null && player.worldObj.isRemote)
                    || ChatSystemLineClassifier.classify(message)
                            != ChatChannel.CONSOLE) {
                return message;
            }
            String tabId = ChatCommandContexts.answerLine(account,
                    System.currentTimeMillis());
            if (tabId.length() == 0) {
                return message;
            }
            ChatChannel channel = ChatTabIds.channelOf(tabId);
            List<UUID> self = Collections.singletonList(account);
            long messageId = record(message,
                    channel == null ? ChatChannel.CONSOLE : channel, tabId,
                    self, ChatHistory.Audience.accounts(self, false));
            if (messageId != ChatMessageIds.NONE) {
                mark(message, messageId);
            }
        } catch (Throwable throwable) {
            logOnce("keep", throwable);
        }
        return message;
    }

    /**
     * Gives a server-wide line an id and records it for everyone
     * online: they are the ones who can be shown it, so they are the
     * ones who may reply to it by that id.
     */
    private static void stamp(IChatComponent message) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return;
        }
        List<UUID> recipients = new ArrayList<UUID>();
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP player : online) {
            if (player != null && player.getUniqueID() != null) {
                recipients.add(player.getUniqueID());
            }
        }
        long messageId = record(message, ChatChannel.ALL, "", recipients,
                ChatHistory.Audience.everyone());
        if (messageId == ChatMessageIds.NONE) {
            return;
        }
        if (ChatSystemLineClassifier.kindOf(message)
                == ChatSystemLineClassifier.Kind.JOIN) {
            noteJoinLine(joinerAccount(message), messageId);
        }
        mark(message, messageId);
    }

    /**
     * Records a line of the server's own in {@code channel} for the
     * audience, under the tab it answers when it answers one, and
     * answers its new id — or none for a line with no words or no
     * server to record it on. The history keeps the words, cleaned as
     * a message is, under the Server's name in the Console's colour,
     * and beside them the component itself as the game's own chat JSON
     * — its hover, its colours, its links — with the players it names
     * as they are playing right now, so a replay shows the line as the
     * live one was shown, naming players who may be long gone by the
     * identity they had.
     */
    private static long record(IChatComponent message, ChatChannel channel,
                               String tabId, List<UUID> recipients,
                               ChatHistory.Audience audience) {
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
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online = server.getConfigurationManager().playerEntityList;
        LostTalesChatMessagePacket record = new LostTalesChatMessagePacket(
                channel, LostTalesChatMessagePacket.SERVER_SENDER_ID,
                SERVER_NAME, SERVER_NAME, "",
                LostTalesColors.rgb(LostTalesColors.HUD_LABEL),
                ChatChannel.CONSOLE.getDisplayColor(), text,
                System.currentTimeMillis(), "", null, "", "", 0, true,
                messageId, ChatReplyReference.NONE, "")
                .withServerBody(componentJson(message),
                        namedPlayers(text, online))
                .withTabId(tabId);
        ChatHistory.record(messageId, LostTalesChatMessagePacket.SERVER_SENDER_ID,
                SERVER_NAME, null, record, recipients, audience);
        return messageId;
    }

    /** The id run appended to a recorded line: the server's word to every client. */
    private static void mark(IChatComponent message, long messageId) {
        ChatComponentText mark = new ChatComponentText("");
        mark.setChatStyle(mark.getChatStyle().setChatClickEvent(new ClickEvent(
                ClickEvent.Action.SUGGEST_COMMAND,
                ChatBroadcastIdMarkers.value(messageId))));
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
        ClickEvent click = name.getChatStyle() == null ? null
                : name.getChatStyle().getChatClickEvent();
        String suggestion = click == null
                || click.getAction() != ClickEvent.Action.SUGGEST_COMMAND
                ? null : click.getValue();
        if (suggestion != null && suggestion.startsWith(WHISPER_SUGGESTION)) {
            String account = suggestion.substring(
                    WHISPER_SUGGESTION.length()).trim();
            if (account.length() > 0) {
                return account;
            }
        }
        return name.getUnformattedText().trim();
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
     * Every online player the line names, whole, as the identity they
     * are playing — the name and colour their own Global line would be
     * signed with — so a replay names them as the live line did.
     */
    /**
     * The listed players a line names. The player a join line announces
     * is not listed yet when it goes out; their login replay names them
     * on it ({@link ChatHistory#namePlayer}).
     */
    private static List<ChatNamedPlayer> namedPlayers(String text,
                                                      List<EntityPlayerMP> online) {
        List<ChatNamedPlayer> named = new ArrayList<ChatNamedPlayer>();
        for (EntityPlayerMP player : online) {
            if (named.size() >= ChatNamedPlayer.MAX_PER_LINE) {
                break;
            }
            // The game writes a player in by their display name, which
            // is their character's; an account name still names them.
            if (player != null && (ChatNamedPlayer.names(text, accountOf(player))
                    || ChatNamedPlayer.names(text, player.getDisplayName()))) {
                named.add(namedPlayer(player));
            }
        }
        return named;
    }

    /**
     * The online players a message's {@code @names} reach, each as a
     * line names them ({@link #namedPlayer}): kept with the message, so
     * a replay shows every mention the live line showed, the players
     * long gone included. Only a player the message names is resolved,
     * and at most {@link ChatNamedPlayer#MAX_PER_LINE} are kept.
     */
    public static List<ChatNamedPlayer> mentionedPlayers(String message) {
        MinecraftServer server = MinecraftServer.getServer();
        if (message == null || message.indexOf('@') < 0 || server == null
                || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return Collections.emptyList();
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        List<ChatNamedPlayer> named = new ArrayList<ChatNamedPlayer>();
        for (EntityPlayerMP player : online) {
            if (named.size() >= ChatNamedPlayer.MAX_PER_LINE) {
                break;
            }
            // Their account or their character's name, as a mention
            // of either reaches them.
            if (player != null && ChatMentions.mentionsAny(message,
                    Arrays.asList(accountOf(player),
                            player.getDisplayName()))) {
                named.add(namedPlayer(player));
            }
        }
        return named;
    }

    /**
     * The player as a line names them: their account, the identity they
     * are playing right now, and the colour that identity wears in
     * Global — what a replay shows in place of the account's name.
     */
    public static ChatNamedPlayer namedPlayer(EntityPlayerMP player) {
        String account = accountOf(player);
        PlayableIdentityResolver.Resolution identity =
                PlayableIdentityResolver.resolve(player);
        RoleplayCharacter character = identity.isAvailable()
                ? identity.getCharacter() : null;
        String identityName = character == null ? account
                : PlayableIdentity.displayName(character, account);
        LostTalesChatPresentationResolver.Presentation presentation =
                LostTalesChatPresentationResolver.resolve(player, character);
        int roles = ChatAccountRoleResolver.resolve(player,
                character == null ? null : character.getCharacterId());
        return new ChatNamedPlayer(account, identityName,
                ChatRolePresentation.nameColor(ChatChannel.ALL, roles,
                        character == null, presentation.nameColor));
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
