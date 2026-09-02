package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatSystemLineClassifier;
import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * Where the game's events become the bridge's notices. Two sources feed
 * it: FML's login and logout events, for joins and leaves, and the
 * server's own broadcast seam ({@code ServerConfigurationManager.sendChatMsg},
 * patched by the coremod), for the lines every player is sent — of
 * which the death messages and the vanilla and LOTR achievement
 * announcements are turned into notices, by the same translation keys
 * the client files them under Global with. Everything else that crosses
 * the seam — joins and leaves, which the login events already cover, a
 * {@code /say}, another mod's broadcast — is left alone.
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

    DiscordGameEventRelay() {}

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event == null || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP)event.player;
        LostTalesDiscordBridge bridge = LostTalesDiscordBridge.getInstance();
        bridge.announce(DiscordServerNotices.playerJoined(
                player.getCommandSenderName(), avatarOf(player)));
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
                player.getCommandSenderName(), avatarOf(player)));
        // The player is still on the list while this fires; the count
        // is taken on the next tick, when they are gone.
        bridge.requestStatusRefresh();
    }

    /**
     * A line the server is sending to everyone, from the broadcast seam.
     * Cheap when there is nothing to do: a bridge that is not posting
     * answers before the line is even classified.
     */
    public static void onServerBroadcast(IChatComponent message) {
        LostTalesDiscordBridge bridge = LostTalesDiscordBridge.getInstance();
        if (message == null || !bridge.isPosting()) {
            return;
        }
        DiscordNotice notice = noticeFor(message);
        if (notice != null) {
            bridge.announce(notice);
        }
    }

    /**
     * The notice a broadcast line becomes, or null for a line that is
     * none of the bridge's business. The text is the line as the server
     * renders it, in the server's own language.
     */
    static DiscordNotice noticeFor(IChatComponent message) {
        ChatSystemLineClassifier.Kind kind =
                ChatSystemLineClassifier.kindOf(message);
        if (kind != ChatSystemLineClassifier.Kind.DEATH
                && kind != ChatSystemLineClassifier.Kind.ACHIEVEMENT) {
            return null;
        }
        String text = message.getUnformattedText();
        if (text == null || text.trim().length() == 0) {
            return null;
        }
        String icon = avatarOf(findOnlinePlayer(subjectAccountName(message)));
        return kind == ChatSystemLineClassifier.Kind.DEATH
                ? DiscordServerNotices.playerDied(text, icon)
                : DiscordServerNotices.achievement(text, icon);
    }

    /**
     * The account the line is about: the first argument of a death or
     * achievement line is the player's display-name component, whose
     * click event vanilla fills with {@code /msg <account> } — the
     * account name, whatever the visible text was rewritten to. Empty
     * when the line carries no such argument.
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

    /** The head picture for an online account; empty for none. */
    private static String avatarOf(EntityPlayerMP player) {
        if (player == null) {
            return "";
        }
        String accountName = player.getGameProfile() == null
                ? player.getCommandSenderName()
                : player.getGameProfile().getName();
        return DiscordAvatarUrl.of(LostTalesConfig.discordAvatarUrlTemplate,
                accountName, player.getUniqueID());
    }

    private static EntityPlayerMP findOnlinePlayer(String accountName) {
        MinecraftServer server = MinecraftServer.getServer();
        if (accountName == null || accountName.length() == 0
                || server == null || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP candidate : online) {
            if (candidate != null && accountName.equalsIgnoreCase(
                    candidate.getCommandSenderName())) {
                return candidate;
            }
        }
        return null;
    }
}
