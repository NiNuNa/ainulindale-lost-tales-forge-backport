package com.ninuna.losttales.chat.server;

import com.mojang.authlib.GameProfile;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissionCatalog;
import com.ninuna.losttales.permission.LostTalesPermissions;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.server.management.UserListOpsEntry;

/**
 * What the server knows of an account whose player is not here, asked as
 * {@link ChatChannelPolicy#canRead} asks a player who is: whether the
 * server would let the account in at all — not banned, and on the
 * whitelist where one is enforced — its level on the operator list, read
 * as vanilla reads a player's, and the roles and capabilities that follow
 * from them ({@link ChatAccountRoleResolver#absentMask}). Everything is
 * read from the server's own lists and the role catalogue, never from
 * Mojang, and each answer is worked out once. Server thread.
 */
final class ChatAbsentReader implements ChatChannelPolicy.Reader {
    private final MinecraftServer server;
    private final GameProfile profile;
    private boolean levelRead;
    private int opLevel;
    private boolean rolesRead;
    private int accountRoles;

    /** {@code profile} names the account by its id and its last known name. */
    ChatAbsentReader(MinecraftServer server, GameProfile profile) {
        this.server = server;
        this.profile = profile;
    }

    /** Whether the server would let the account in: not banned, and whitelisted where that is asked. */
    boolean mayJoin() {
        try {
            ServerConfigurationManager players = this.server.getConfigurationManager();
            return players != null
                    && !players.func_152608_h().func_152702_a(this.profile)
                    && players.func_152607_e(this.profile);
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    @Override
    public boolean isOperator() {
        return opLevel() >= LostTalesPermissions.OPERATOR_LEVEL;
    }

    @Override
    public boolean readsConsole() {
        LostTalesCapability capability = LostTalesCapability.CHAT_CONSOLE_READ;
        int level = opLevel();
        return LostTalesPermissions.decide(
                level != ChatAccountRoleResolver.NOT_OPERATOR
                        && level >= capability.getRequiredOpLevel(),
                accountRoles(), capability, ChatRoleCatalog.server(),
                LostTalesPermissionCatalog.current());
    }

    @Override
    public int accountRoles() {
        if (!this.rolesRead) {
            this.accountRoles = ChatAccountRoleResolver.absentMask(
                    ChatRoleCatalog.server(), this.profile.getId(), null, opLevel());
            this.rolesRead = true;
        }
        return this.accountRoles;
    }

    /** The roles of one of the account's characters: the account's, and the character's own. */
    int rolesAs(UUID characterId) {
        return characterId == null ? accountRoles()
                : ChatAccountRoleResolver.absentMask(ChatRoleCatalog.server(),
                        this.profile.getId(), characterId, opLevel());
    }

    /**
     * The account's operator level as vanilla reads a player's: an
     * operator's own level on the list, the server's default for an
     * operator without one (the owner of an open single-player world),
     * and {@link ChatAccountRoleResolver#NOT_OPERATOR} for anyone else.
     */
    private int opLevel() {
        if (!this.levelRead) {
            this.levelRead = true;
            this.opLevel = ChatAccountRoleResolver.NOT_OPERATOR;
            try {
                ServerConfigurationManager players = this.server.getConfigurationManager();
                if (players != null && players.func_152596_g(this.profile)) {
                    UserListOpsEntry entry = (UserListOpsEntry)players.func_152603_m()
                            .func_152683_b(this.profile);
                    this.opLevel = entry != null ? entry.func_152644_a()
                            : this.server.getOpPermissionLevel();
                }
            } catch (RuntimeException unreadable) {
                this.opLevel = ChatAccountRoleResolver.NOT_OPERATOR;
            }
        }
        return this.opLevel;
    }
}
