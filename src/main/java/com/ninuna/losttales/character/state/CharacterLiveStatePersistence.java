package com.ninuna.losttales.character.state;

import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.StatisticsFile;
import net.minecraft.world.storage.IPlayerFileData;

/**
 * Forces the live account files which Minecraft normally writes during an
 * autosave or logout. Character-switch journals must not be cleared until
 * these files agree with the committed roster identity.
 */
public final class CharacterLiveStatePersistence {

    private CharacterLiveStatePersistence() {}

    public static void save(EntityPlayerMP player) {
        if (player == null || player.getUniqueID() == null
                || player.worldObj == null || player.worldObj.isRemote) {
            throw new IllegalArgumentException(
                    "Live character state can only be saved for a server player");
        }

        IPlayerFileData playerFiles = player.worldObj.getSaveHandler().getSaveHandler();
        if (playerFiles == null) {
            throw new IllegalStateException("The server player-data writer is unavailable");
        }
        playerFiles.writePlayerData(player);

        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            throw new IllegalStateException("The server player manager is unavailable");
        }
        StatisticsFile statistics = server.getConfigurationManager()
                .func_152602_a(player);
        if (statistics == null) {
            throw new IllegalStateException("The server statistics writer is unavailable");
        }
        statistics.func_150883_b();

        LotrCharacterAdapter.getInstance().savePlayerData(player);
    }
}
