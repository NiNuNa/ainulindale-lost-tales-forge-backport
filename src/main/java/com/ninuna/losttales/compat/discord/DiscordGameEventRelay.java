package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.character.identity.RoleplayCharacterIdentityHook;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatSystemLineClassifier;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * Where the game's announcements become the bridge's notices, each
 * linked to the game's own line announcing the same thing. Every
 * announcement passes the server's broadcast seam
 * ({@code ServerConfigurationManager.sendChatMsg}, patched by the
 * coremod), where the chat gives the line its message id: the death
 * messages, the vanilla and LOTR achievement announcements, and the
 * server's own lines for starting and stopping are posted from there,
 * by the same translation keys the client files them under OOC &amp;
 * Discord with. Joins and leaves are posted from FML's login and logout
 * events, which follow their broadcast on the same tick and carry the
 * player — the join line goes out before the server lists the player —
 * so the line's id waits here for its event. Everything else that
 * crosses the seam — a {@code /say}, another mod's broadcast — is left
 * alone.
 *
 * <p>A broadcast is taken as it was sent: the death message is vanilla's
 * (or LOTR's, or another mod's) and already names the character where
 * the identity patch renamed the victim. The player's head beside it is
 * resolved from the display-name component's own {@code /msg <account>}
 * click event, which vanilla puts on every player name it announces, so
 * the picture follows the account and never a guess from the text. No
 * account there — a name a mod built by hand — means no picture and
 * nothing else lost.</p>
 *
 * <p>Every method here runs on the server thread and does nothing but
 * build a notice and queue it; the bridge's worker posts it. A notice of
 * a kind the config has switched off is dropped by the bridge.</p>
 */
public final class DiscordGameEventRelay {
    /** What {@code EntityPlayer.func_145748_c_} puts on a player name. */
    private static final String WHISPER_COMMAND_PREFIX = "/msg ";
    /** More announced joins and leaves waiting than a tick holds; the oldest go. */
    private static final int MAX_WAITING_LINES = 64;
    /**
     * The ids of join and leave lines waiting for their login or logout
     * event, by kind and account. Cleared with the server's chat state.
     */
    private static final Map<String, Long> WAITING_LINES =
            new LinkedHashMap<String, Long>();

    DiscordGameEventRelay() {}

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event == null || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP)event.player;
        LostTalesDiscordBridge bridge = LostTalesDiscordBridge.getInstance();
        bridge.announce(DiscordServerNotices.playerJoined(
                player.getCommandSenderName(),
                DiscordAvatarUrl.forPlayer(player)),
                takeLine(ChatSystemLineClassifier.Kind.JOIN,
                        player.getCommandSenderName()));
        bridge.requestStatusRefresh();
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event == null || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP)event.player;
        LostTalesDiscordBridge bridge = LostTalesDiscordBridge.getInstance();
        bridge.announce(DiscordServerNotices.playerLeft(
                player.getCommandSenderName(),
                DiscordAvatarUrl.forPlayer(player)),
                takeLine(ChatSystemLineClassifier.Kind.LEAVE,
                        player.getCommandSenderName()));
        // The player is still on the list while this fires; the count
        // is taken on the next tick, when they are gone.
        bridge.requestStatusRefresh();
    }

    /**
     * A line the server is sending to everyone, from the broadcast seam,
     * with the message id the chat gave it ({@link ChatMessageIds#NONE}
     * for none). Cheap when there is nothing to do: a bridge that is not
     * posting answers before the line is even classified.
     */
    public static void onServerBroadcast(IChatComponent message,
                                         long messageId) {
        LostTalesDiscordBridge bridge = LostTalesDiscordBridge.getInstance();
        if (message == null || !bridge.isPosting()) {
            return;
        }
        ChatSystemLineClassifier.Kind kind =
                ChatSystemLineClassifier.kindOf(message);
        switch (kind) {
            case JOIN:
            case LEAVE:
                noteLine(kind, subjectAccountName(message), messageId);
                return;
            case SERVER_STARTED:
                bridge.announce(DiscordServerNotices.serverStarted(),
                        messageId);
                return;
            case SERVER_STOPPING:
                bridge.announce(DiscordServerNotices.serverStopping(),
                        messageId);
                return;
            default:
                DiscordNotice notice = noticeFor(message);
                if (notice != null) {
                    bridge.announce(notice, messageId);
                }
        }
    }

    /** Forgets every waiting line; with the rest of the server's chat state. */
    public static synchronized void clear() {
        WAITING_LINES.clear();
    }

    /** Keeps a join's or a leave's line for the event that announces it. */
    static synchronized void noteLine(ChatSystemLineClassifier.Kind kind,
                                      String account, long messageId) {
        if (account == null || account.length() == 0
                || !ChatMessageIds.isServerId(messageId)) {
            return;
        }
        String key = keyOf(kind, account);
        WAITING_LINES.remove(key);
        WAITING_LINES.put(key, Long.valueOf(messageId));
        Iterator<String> oldest = WAITING_LINES.keySet().iterator();
        while (WAITING_LINES.size() > MAX_WAITING_LINES && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * The id of the line announcing {@code account}'s join or leave, no
     * longer waiting once taken; {@link ChatMessageIds#NONE} when the
     * game announced nothing, as when another mod silenced it.
     */
    static synchronized long takeLine(ChatSystemLineClassifier.Kind kind,
                                      String account) {
        if (account == null) {
            return ChatMessageIds.NONE;
        }
        Long line = WAITING_LINES.remove(keyOf(kind, account));
        return line == null ? ChatMessageIds.NONE : line.longValue();
    }

    private static String keyOf(ChatSystemLineClassifier.Kind kind,
                                String account) {
        return kind.name() + ':' + account.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The notice a death or achievement line becomes, or null for a line
     * that is neither. The text is the line as the server renders it, in
     * the server's own language.
     */
    static DiscordNotice noticeFor(IChatComponent message) {
        ChatSystemLineClassifier.Kind kind =
                ChatSystemLineClassifier.kindOf(message);
        if (kind != ChatSystemLineClassifier.Kind.DEATH
                && kind != ChatSystemLineClassifier.Kind.ACHIEVEMENT) {
            return null;
        }
        // The line names its subject by account; the client shows it
        // naming the character the subject is playing, and so does the
        // notice. A death line was already renamed where it was made.
        EntityPlayerMP subject = LostTalesServerPlayers.findOnline(
                subjectAccountName(message));
        String text = RoleplayCharacterIdentityHook.resolveSubjectName(
                message, subject).getUnformattedText();
        if (text == null || text.trim().length() == 0) {
            return null;
        }
        String icon = DiscordAvatarUrl.forPlayer(subject);
        return kind == ChatSystemLineClassifier.Kind.DEATH
                ? DiscordServerNotices.playerDied(text, icon)
                : DiscordServerNotices.achievement(text, icon);
    }

    /**
     * The account the line is about: the first argument of a join, a
     * leave, a death or an achievement line is the player's display-name
     * component, whose click event vanilla fills with
     * {@code /msg <account> } — the account name, whatever the visible
     * text was rewritten to. Empty when the line carries no such
     * argument.
     */
    static String subjectAccountName(IChatComponent message) {
        if (!(message instanceof ChatComponentTranslation)) {
            return "";
        }
        Object[] arguments = ((ChatComponentTranslation)message).getFormatArgs();
        if (arguments == null || arguments.length == 0
                || !(arguments[0] instanceof IChatComponent)) {
            return "";
        }
        IChatComponent subject = (IChatComponent)arguments[0];
        ClickEvent click = subject.getChatStyle() == null ? null
                : subject.getChatStyle().getChatClickEvent();
        String value = click == null ? null : click.getValue();
        if (value == null || !value.startsWith(WHISPER_COMMAND_PREFIX)) {
            return "";
        }
        return value.substring(WHISPER_COMMAND_PREFIX.length()).trim();
    }
}
