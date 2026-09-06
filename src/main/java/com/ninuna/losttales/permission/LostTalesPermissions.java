package com.ninuna.losttales.permission;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.server.ChatAccountRoleResolver;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * The server's one answer to "may this player do that". Two things can
 * say yes and nothing can say no: vanilla operator status at the level
 * the capability names, exactly as the command handler reads it from
 * the ops list, the single-player owner rule and the server's own
 * settings; or a role the account holds that the config grants the
 * capability to. Roles are resolved by {@link ChatAccountRoleResolver}
 * from server-side facts alone, so nothing a client sends takes part.
 *
 * <p>Every feature asks here at the moment it acts, never earlier and
 * never from a cache: a mute, a removal, a config change, a command. The
 * client only ever learns the answer for itself, to decide which menus
 * to offer, and the server asks again on the request.</p>
 *
 * <p>Server only: the catalogue consulted is the server's own, with its
 * members and sources, never a client's copy.</p>
 */
public final class LostTalesPermissions {

    /**
     * The permission node vanilla is handed. It only tells apart the
     * few commands vanilla lets everyone use ({@code tell}, {@code help},
     * {@code me}, and {@code seed} in single player); anything else is
     * decided by the level alone.
     */
    public static final String NODE = "losttales.permission";
    /** The level vanilla gives an operator by default; what "operator" means here. */
    public static final int OPERATOR_LEVEL = 2;

    private LostTalesPermissions() {}

    /**
     * Whether the player is a server operator: vanilla's own answer for
     * the default operator level. The mod's remaining operator-only
     * decisions read it from here and nowhere else.
     */
    public static boolean isOperator(EntityPlayerMP player) {
        return player != null && player.canCommandSenderUseCommand(OPERATOR_LEVEL, NODE);
    }

    /** Whether the player holds the capability, by operator level or by role. */
    public static boolean has(EntityPlayerMP player, LostTalesCapability capability) {
        if (player == null || capability == null) {
            return false;
        }
        // The level answers on its own, and is asked first: resolving
        // roles reads the config catalogue and, for a faction source,
        // LOTR's player data, and this is asked per recipient on the
        // routing path. An operator never pays for it.
        if (player.canCommandSenderUseCommand(capability.getRequiredOpLevel(), NODE)) {
            return true;
        }
        return decide(false, ChatAccountRoleResolver.resolve(player), capability,
                ChatRoleCatalog.server(), LostTalesPermissionCatalog.current());
    }

    /**
     * The rule itself, once the two facts are known: the operator level
     * the capability names, or a role the account holds that grants it.
     * Pure, so the rule can be checked without a server; the lookups
     * that feed it are {@link #has}'s.
     */
    public static boolean decide(boolean hasOperatorLevel, int roleMask,
                                 LostTalesCapability capability,
                                 ChatRoleCatalog catalog,
                                 LostTalesPermissionCatalog permissions) {
        return hasOperatorLevel || isGranted(roleMask, capability, catalog, permissions);
    }

    /**
     * The same question for any command sender. A player is asked about
     * as above; the console, a command block and anything else that is
     * not a player answers with vanilla's level check alone, which for
     * the console is always yes.
     */
    public static boolean has(ICommandSender sender, LostTalesCapability capability) {
        if (sender == null || capability == null) {
            return false;
        }
        if (sender instanceof EntityPlayerMP) {
            return has((EntityPlayerMP)sender, capability);
        }
        return sender.canCommandSenderUseCommand(capability.getRequiredOpLevel(), NODE);
    }

    /**
     * Whether any role set in the mask reaches the capability: the
     * role's granted ids are read as permissions, and a permission as
     * the capabilities it names. Pure: what {@link #has} decides once
     * operator status has said no, kept apart so it can be checked
     * without a server.
     */
    public static boolean isGranted(int roleMask, LostTalesCapability capability,
                                    ChatRoleCatalog catalog,
                                    LostTalesPermissionCatalog permissions) {
        if (roleMask == 0 || capability == null || catalog == null) {
            return false;
        }
        LostTalesPermissionCatalog defined = permissions == null
                ? LostTalesPermissionCatalog.empty() : permissions;
        for (ChatAccountRole role : catalog.roles()) {
            if ((roleMask & role.bit()) != 0
                    && defined.reachesAny(role.getGrants(), capability)) {
                return true;
            }
        }
        return false;
    }
}
