package com.ninuna.losttales.character.identity;

import com.mojang.authlib.GameProfile;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterActiveResolver;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.UsernameCache;

import java.util.UUID;

/**
 * Narrow compatibility hook for gameplay systems that should identify the
 * currently active roleplay character rather than the authenticated account.
 *
 * <p>This class must never be used for login, commands, permissions, bans, or
 * operators. Those systems must continue to use the player's GameProfile.</p>
 */
public final class RoleplayCharacterIdentityHook {

    private RoleplayCharacterIdentityHook() {}

    /** Returns the active character UUID, or the account UUID as a safe fallback. */
    public static UUID resolveGameplayId(EntityPlayer player) {
        UUID accountId = player == null ? null : player.getUniqueID();
        RoleplayCharacter character = resolveActive(player);
        return character == null ? accountId : character.getCharacterId();
    }

    /** Returns the active character name for roleplay dialogue only. */
    public static String resolveRoleplayName(EntityPlayer player) {
        RoleplayCharacter character = resolveActive(player);
        if (character != null && !isBlank(character.getName())) {
            return character.getName();
        }
        return player == null ? "" : player.getCommandSenderName();
    }

    /**
     * Resolves either a roleplay-character UUID or a normal account UUID.
     * LOTR's bounty target records call this after their identity key has been
     * changed to a character UUID.
     */
    public static String resolveGameplayName(UUID identityId) {
        if (identityId == null) {
            return "";
        }

        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            RoleplayCharacter character = findCharacter(server, identityId);
            if (character != null && !isBlank(character.getName())) {
                return character.getName();
            }

            String onlineName = findOnlineAccountName(
                    server.getConfigurationManager(), identityId);
            if (!isBlank(onlineName)) {
                return onlineName;
            }

            GameProfile profile = server.func_152358_ax()
                    .func_152652_a(identityId);
            if (profile != null && !isBlank(profile.getName())) {
                return profile.getName();
            }
        }

        String cachedName = UsernameCache.getLastKnownUsername(identityId);
        return cachedName == null ? "" : cachedName;
    }

    private static RoleplayCharacter resolveActive(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return null;
        }
        return CharacterActiveResolver.get((EntityPlayerMP)player);
    }

    private static RoleplayCharacter findCharacter(
            MinecraftServer server, UUID characterId) {
        try {
            WorldServer overworld = server.worldServerForDimension(0);
            if (overworld == null) {
                return null;
            }
            CharacterWorldData data = CharacterStorage.get(overworld);
            return data.findCharacter(characterId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String findOnlineAccountName(
            ServerConfigurationManager manager, UUID accountId) {
        if (manager == null || manager.playerEntityList == null) {
            return null;
        }
        for (Object value : manager.playerEntityList) {
            if (!(value instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayerMP player = (EntityPlayerMP)value;
            if (accountId.equals(player.getUniqueID())) {
                return player.getCommandSenderName();
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
