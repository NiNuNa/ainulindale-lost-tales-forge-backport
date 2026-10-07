package com.ninuna.losttales.character.server;

import com.mojang.authlib.GameProfile;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

/**
 * Accounts this world knows by name, found in the server's own memory and
 * never by asking Mojang: an online player first, then the owner of a roster
 * whose last known name it is. A name nobody here has used finds nobody.
 */
public final class KnownAccounts {

    private KnownAccounts() {}

    /** The account known here by that name, case-insensitively; null for none. */
    public static UUID find(World world, String name) {
        String wanted = name == null ? "" : name.trim();
        if (wanted.length() == 0) {
            return null;
        }
        EntityPlayerMP online = LostTalesServerPlayers.findOnline(wanted);
        return online != null ? online.getUniqueID() : ownerNamed(world, wanted);
    }

    /** The roster owner whose last known name is {@code name}, or null. */
    public static UUID ownerNamed(World world, String name) {
        if (world == null || name == null) {
            return null;
        }
        try {
            for (CharacterRoster roster : CharacterStorage.get(world).getRosters()) {
                UUID owner = roster == null ? null : roster.getOwnerId();
                if (owner != null && name.equalsIgnoreCase(nameOf(owner))) {
                    return owner;
                }
            }
        } catch (RuntimeException unreadable) {
            return null;
        }
        return null;
    }

    /**
     * An account's name while its player is not here: the one the server last
     * saw it log in with; empty when the server does not know it.
     */
    public static String nameOf(UUID account) {
        MinecraftServer server = MinecraftServer.getServer();
        GameProfile profile = null;
        try {
            profile = server == null || server.func_152358_ax() == null ? null
                    : server.func_152358_ax().func_152652_a(account);
        } catch (RuntimeException unavailable) {
            profile = null;
        }
        return profile == null || profile.getName() == null ? ""
                : profile.getName().trim();
    }
}
