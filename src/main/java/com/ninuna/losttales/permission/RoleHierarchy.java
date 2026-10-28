package com.ninuna.losttales.permission;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleCatalog;

/**
 * Who manages which role, as a Discord server's role hierarchy has it
 * (DATA-14). A role stands by its rank: rank 1 is the highest, and a
 * greater number stands lower, the order the roles are listed and
 * coloured in. Someone holding {@code roles.manage} through a role stands
 * at their highest role, the one of lowest rank among the roles their
 * account holds, and creates, edits, deletes, assigns and takes away
 * only the roles ranked below it; two roles of one rank stand side by
 * side, and neither is below the other. An operator who holds
 * {@code roles.manage} by level, and the server console, stand above
 * every role, as a Discord server's owner does. Pure, so the rule is
 * checked without a server.
 */
public final class RoleHierarchy {

    /** Where someone stands who stands above every role. */
    public static final int ABOVE_EVERY_ROLE = Integer.MIN_VALUE;
    /** Where someone stands who holds no role: below every one. */
    public static final int BELOW_EVERY_ROLE = Integer.MAX_VALUE;

    private RoleHierarchy() {}

    /**
     * The highest of the roles set in {@code roleMask}: the one of lowest
     * rank, the earlier in the catalogue's order on a tie. The Lost Tales
     * Team mark is a vanity mark and stands nowhere. Null for none.
     */
    public static ChatAccountRole highestOf(int roleMask, ChatRoleCatalog catalog) {
        if (roleMask == 0 || catalog == null) {
            return null;
        }
        for (ChatAccountRole role : catalog.roles()) {
            if (!role.isLocked() && (roleMask & role.bit()) != 0) {
                return role;
            }
        }
        return null;
    }

    /**
     * Where the roles of {@code roleMask} place their holder: their
     * highest role's rank; {@link #BELOW_EVERY_ROLE} for none.
     */
    public static int placeOf(int roleMask, ChatRoleCatalog catalog) {
        ChatAccountRole highest = highestOf(roleMask, catalog);
        return highest == null ? BELOW_EVERY_ROLE : highest.getRank();
    }

    /**
     * Whether someone standing at {@code place} manages a role of
     * {@code rank}: it ranks strictly below them. Above every role, every
     * rank is below; below every role, none is.
     */
    public static boolean manages(int place, int rank) {
        return place != BELOW_EVERY_ROLE && rank > place;
    }
}
