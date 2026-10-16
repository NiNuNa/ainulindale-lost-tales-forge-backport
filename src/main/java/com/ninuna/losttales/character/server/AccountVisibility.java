package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.lore.sync.LoreCharacterSyncManager;
import com.ninuna.losttales.chat.server.ChatMemberWatches;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.fellowship.server.FellowshipSyncManager;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissions;
import java.util.ArrayList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Whether a player is shown which account plays a character. With the
 * server's Show Accounts on, everyone is; off, only who holds
 * {@code character.see_accounts}. Anyone else is told of accounts only out
 * of character: a character's lines, whispers, mentions, member rows,
 * role holders and appearance, the lore list and the fellowships carry no
 * account's name to them, and never pair a character with the account or
 * another character of it. Their own account they always see.
 */
public final class AccountVisibility {
    private AccountVisibility() {}

    /** Whether {@code viewer} is shown accounts. */
    public static boolean seesAccounts(EntityPlayerMP viewer) {
        // Asked for every line and every reader: the option answers first.
        if (LostTalesConfig.showAccounts) {
            return true;
        }
        return decide(false, viewer != null
                && LostTalesPermissions.has(viewer, LostTalesCapability.CHARACTER_SEE_ACCOUNTS));
    }

    /** Whether a viewer is shown accounts: the option on, or staff. */
    static boolean decide(boolean shown, boolean staff) {
        return shown || staff;
    }

    /**
     * Tells one player again what names an account: their chat access and
     * role holders, everyone's appearance, the lore list, their fellowships
     * and the member lists they watch. Lines already on their screen stay
     * as they arrived.
     */
    public static void resendTo(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        LostTalesChatService.sendAccess(player);
        CharacterAppearanceSyncManager.sendFullSnapshot(player);
        LoreCharacterSyncManager.send(player);
        FellowshipSyncManager.sendState(player, FellowshipSyncManager.UNSOLICITED_REQUEST_ID);
        ChatMemberWatches.markChanged();
    }

    /** Tells every player again: what a change of the option calls. */
    public static void resendToAll() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        for (Object value : new ArrayList<Object>(
                server.getConfigurationManager().playerEntityList)) {
            if (value instanceof EntityPlayerMP) {
                resendTo((EntityPlayerMP)value);
            }
        }
    }
}
