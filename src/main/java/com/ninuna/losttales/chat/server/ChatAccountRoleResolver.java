package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.compat.lotr.LostTalesWaystonePermissionPolicy;
import com.ninuna.losttales.user.ELostTalesUser;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * The server's word on which {@link ChatAccountRole}s a sender holds,
 * decided from server-side facts only: the operator check the rest of
 * the mod uses, and the recognized-user catalogue keyed by the account
 * id the server authenticated. Nothing the client sends takes part.
 */
final class ChatAccountRoleResolver {
    private ChatAccountRoleResolver() {}

    /** The role mask for a sender; zero for no roles or no player. */
    static int resolve(EntityPlayerMP player) {
        if (player == null) {
            return 0;
        }
        int mask = 0;
        if (LostTalesWaystonePermissionPolicy.isOperator(player)) {
            mask |= ChatAccountRole.OPERATOR.bit();
        }
        mask |= ELostTalesUser.byUniqueId(player.getUniqueID())
                .getRecognition().getChatRole().bit();
        return mask;
    }
}
