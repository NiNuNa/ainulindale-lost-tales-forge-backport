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
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.UsernameCache;

import java.util.LinkedHashMap;
import java.util.Map;
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

    /** Rewrites only online account-name arguments in a death component. */
    public static IChatComponent resolveDeathMessage(
            IChatComponent vanillaMessage, EntityPlayerMP victim) {
        if (vanillaMessage == null) {
            return null;
        }
        Map<String, String> replacements =
                new LinkedHashMap<String, String>();
        MinecraftServer server = victim == null
                ? MinecraftServer.getServer() : victim.mcServer;
        ServerConfigurationManager manager = server == null
                ? null : server.getConfigurationManager();
        if (manager != null && manager.playerEntityList != null) {
            for (Object value : manager.playerEntityList) {
                if (value instanceof EntityPlayerMP) {
                    addRoleplayReplacement(
                            replacements, (EntityPlayerMP)value);
                }
            }
        }
        addRoleplayReplacement(replacements, victim);
        return replaceAccountNames(vanillaMessage, replacements);
    }

    static IChatComponent replaceAccountNames(
            IChatComponent source, Map<String, String> replacements) {
        return replaceAccountNames(source, replacements, 0);
    }

    private static IChatComponent replaceAccountNames(
            IChatComponent source, Map<String, String> replacements,
            int depth) {
        if (source == null || replacements == null
                || replacements.isEmpty() || depth > 16) {
            return source == null ? null : source.createCopy();
        }
        IChatComponent copy;
        if (source instanceof ChatComponentTranslation) {
            ChatComponentTranslation translation =
                    (ChatComponentTranslation)source;
            Object[] sourceArguments = translation.getFormatArgs();
            Object[] arguments = new Object[sourceArguments.length];
            for (int index = 0; index < sourceArguments.length; index++) {
                Object argument = sourceArguments[index];
                if (argument instanceof IChatComponent) {
                    arguments[index] = replaceAccountNames(
                            (IChatComponent)argument,
                            replacements, depth + 1);
                } else if (argument instanceof String) {
                    arguments[index] = replacement(
                            (String)argument, replacements);
                } else {
                    arguments[index] = argument;
                }
            }
            copy = new ChatComponentTranslation(
                    translation.getKey(), arguments);
        } else if (source instanceof ChatComponentText) {
            copy = new ChatComponentText(replacement(
                    ((ChatComponentText)source)
                            .getChatComponentText_TextValue(),
                    replacements));
        } else {
            return source.createCopy();
        }
        copy.setChatStyle(source.getChatStyle().createShallowCopy());
        for (Object sibling : source.getSiblings()) {
            if (sibling instanceof IChatComponent) {
                copy.appendSibling(replaceAccountNames(
                        (IChatComponent)sibling,
                        replacements, depth + 1));
            }
        }
        return copy;
    }

    private static void addRoleplayReplacement(
            Map<String, String> replacements, EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        String accountName = player.getCommandSenderName();
        String roleplayName = resolveRoleplayName(player);
        if (!isBlank(accountName) && !isBlank(roleplayName)
                && !accountName.equals(roleplayName)) {
            replacements.put(accountName, roleplayName);
        }
    }

    private static String replacement(
            String value, Map<String, String> replacements) {
        String replacement = replacements.get(value);
        return replacement == null ? value : replacement;
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
