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
 * with. Nothing a client sends takes part in either. {@link #absentMask}
 * answers the second question for an identity whose player is not here,
 * which a member list asks of everyone absent.</p>
 */
public final class ChatAccountRoleResolver {
    /** The operator level of an account the server does not list as an operator. */
    public static final int NOT_OPERATOR = -1;

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
        int mask = assignedMask(catalog, player.getUniqueID(), characterId)
                | teamMask(player.getUniqueID());
        for (ChatAccountRole role : catalog.roles()) {
            if (!role.isLocked() && grantedBySource(player, role)) {
                mask |= role.bit();
            }
        }
        return mask;
    }

    /**
     * The roles of an identity whose player is not here, from what the
     * server knows without them: the catalogue's assignments, the team
     * mark, and every role an operator level grants, for {@code opLevel} —
     * the account's level on the server's operator list, or
     * {@link #NOT_OPERATOR}. A role a LOTR faction rank grants is read
     * from LOTR's data for the identity being played, which only a player
     * here has, so it is not counted.
     */
    public static int absentMask(ChatRoleCatalog catalog, UUID accountId,
                                 UUID characterId, int opLevel) {
        int mask = assignedMask(catalog, accountId, characterId)
                | teamMask(accountId);
        if (catalog == null || opLevel == NOT_OPERATOR) {
            return mask;
        }
        for (ChatAccountRole role : catalog.roles()) {
            if (role.isLocked()) {
                continue;
            }
            for (ChatRoleSource source : role.getSources()) {
                if (source.getKind() == ChatRoleSource.Kind.OP_LEVEL
                        && opLevel >= source.getLevel()) {
                    mask |= role.bit();
                    break;
                }
            }
        }
        return mask;
    }

    /** The team mark's bit for an account the code recognises; zero otherwise. */
    private static int teamMask(UUID accountId) {
        return accountId != null && ELostTalesUser.byUniqueId(accountId)
                .getRecognition().getChatRole() == ChatAccountRole.TEAM
                ? ChatAccountRole.TEAM.bit() : 0;
    }

    /**
     * The bits the catalogue's own assignments give: every role the
     * account is listed in, and — only when a character is named — every
     * role that character is listed in. A character's assignment is the
     * character's alone, so asking without one, as a capability check
     * does, never sees it. Pure, so the scoping rule can be checked
     * without a server; the team mark and the role sources are
     * {@link #resolve}'s, since both are the player's own facts.
     */
    static int assignedMask(ChatRoleCatalog catalog, UUID accountId, UUID characterId) {
        if (catalog == null) {
            return 0;
        }
        int mask = 0;
        for (ChatAccountRole role : catalog.roles()) {
            if (role.isLocked()) {
                continue;
            }
            if ((accountId != null && catalog.membersOf(role.getId()).contains(accountId))
                    || (characterId != null
                            && catalog.characterMembersOf(role.getId()).contains(characterId))) {
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
