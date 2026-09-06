package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatRoleSource;
import com.ninuna.losttales.compat.lotr.LotrFactionRankAdapter;
import com.ninuna.losttales.user.ELostTalesUser;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Which roles a player holds, from server-side facts alone: the team
 * mark from the account id the code recognises, and every config role
 * whose members list the account, whose op level the account has, or
 * whose LOTR faction rank the played identity has reached. A
 * character-scoped assignment is worn by that character alone: it
 * counts for the identity a line wears and for the identity being
 * played, and never for the account's other characters.
 *
 * <p>Two questions, two answers: {@link #resolve(EntityPlayerMP)} is the
 * account's roles, what a capability is granted through; {@link
 * #resolve(EntityPlayerMP, UUID)} adds the roles assigned to one of the
 * account's characters, what a line is signed with and a gate is passed
 * with. Nothing a client sends takes part in either.</p>
 */
public final class ChatAccountRoleResolver {
    private ChatAccountRoleResolver() {}

    /** The account's own role mask; zero for no roles or no player. */
    public static int resolve(EntityPlayerMP player) {
        return resolve(player, null);
    }

    /**
     * The account's roles together with those assigned to {@code
     * characterId}, one of the account's own characters; the account's
     * alone when null.
     */
    public static int resolve(EntityPlayerMP player, UUID characterId) {
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
                    || (characterId != null
                            && catalog.characterMembersOf(role.getId()).contains(characterId))
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
