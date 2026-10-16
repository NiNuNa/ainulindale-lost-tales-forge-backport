package com.ninuna.losttales.util;

import java.util.List;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

/**
 * The players of the running server: whether a player is one the server
 * holds, the online ones looked up by name or id, and the part of a
 * player's saved data the mod keeps its own marks in.
 */
public final class LostTalesServerPlayers {

    private LostTalesServerPlayers() {}

    /** The part of a player's saved data Forge keeps across deaths. */
    public static NBTTagCompound persistedData(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    /** Whether the player is one the server holds: present, with an id, in a server world. */
    public static boolean isServerPlayer(EntityPlayerMP player) {
        return player != null && player.getUniqueID() != null
                && player.worldObj != null && !player.worldObj.isRemote;
    }

    /**
     * The online player with that account name, case-insensitively and
     * with surrounding whitespace ignored; null for no such player, an
     * empty name, or no running server.
     */
    public static EntityPlayerMP findOnline(String accountName) {
        MinecraftServer server = MinecraftServer.getServer();
        String wanted = accountName == null ? "" : accountName.trim();
        if (wanted.length() == 0 || server == null
                || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP candidate : online) {
            if (candidate != null && wanted.equalsIgnoreCase(
                    candidate.getCommandSenderName())) {
                return candidate;
            }
        }
        return null;
    }

    /** The online player with that account id; null for none or no running server. */
    public static EntityPlayerMP findOnline(UUID accountId) {
        MinecraftServer server = MinecraftServer.getServer();
        if (accountId == null || server == null
                || server.getConfigurationManager() == null
                || server.getConfigurationManager().playerEntityList == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> online =
                server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP candidate : online) {
            if (candidate != null && accountId.equals(candidate.getUniqueID())) {
                return candidate;
            }
        }
        return null;
    }
}
