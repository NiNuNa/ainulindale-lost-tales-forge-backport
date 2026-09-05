package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatRoleSource;
import com.ninuna.losttales.compat.lotr.LotrFactionRankAdapter;
import com.ninuna.losttales.user.ELostTalesUser;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * The server's word on which {@link ChatAccountRole}s a sender holds,
 * decided from server-side facts only: the recognized-user catalogue
 * keyed by the account id the server authenticated for the team mark,
 * and for every other role of the server's own catalogue its assigned
 * members and its sources — the operator permission check the rest of
 * the mod uses, or a LOTR faction rank the played identity has reached.
 * Nothing the client sends takes part, and neither does the client's
 * copy of the catalogue: on an integrated server that copy shares the
 * JVM, and it carries no sources or members.
 */
public final class ChatAccountRoleResolver {
    private ChatAccountRoleResolver() {}

    /** The role mask for a sender; zero for no roles or no player. */
    public static int resolve(EntityPlayerMP player) {
        if (player == null) {
            return 0;
        }
        ChatRoleCatalog catalog = ChatRoleCatalog.server();
        int mask = 0;
        if (ELostTalesUser.byUniqueId(player.getUniqueID()).getRecognition()
                .getChatRole() == ChatAccountRole.TEAM) {
            mask |= ChatAccountRole.TEAM.bit();
        }
        for (ChatAccountRole role : catalog.roles()) {
            if (role.isLocked()) {
                continue;
            }
            if (catalog.membersOf(role.getId()).contains(player.getUniqueID())
                    || grantedBySource(player, role)) {
                mask |= role.bit();
            }
        }
        return mask;
    }

    private static boolean grantedBySource(EntityPlayerMP player, ChatAccountRole role) {
        for (ChatRoleSource source : role.getSources()) {
            if (source.getKind() == ChatRoleSource.Kind.OP_LEVEL) {
                if (player.canCommandSenderUseCommand(source.getLevel(),
                        "losttales.role." + role.getId())) {
                    return true;
                }
            } else if (source.getKind() == ChatRoleSource.Kind.FACTION_RANK
                    && LotrFactionRankAdapter.hasRank(player, source.getFaction(),
                            source.getRank())) {
                return true;
            }
        }
        return false;
    }
}
