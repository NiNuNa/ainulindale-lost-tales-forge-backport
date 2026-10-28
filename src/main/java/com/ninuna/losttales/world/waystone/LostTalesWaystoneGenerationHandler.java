package com.ninuna.losttales.world.waystone;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneGenerationState;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.IWorldGenerator;
import java.util.Collection;
import java.util.Random;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.world.ChunkEvent;

/**
 * Builds the generated waystones: as a new chunk is populated, as a
 * chunk near a marker loads, and in a sweep round each player every
 * second. A marker {@code retry} marked waits as not attempted, saved
 * with its record, and is built here as its chunk loads, as one never
 * tried is (WAY-18).
 */
public final class LostTalesWaystoneGenerationHandler
        implements IWorldGenerator {
    private static final int PLAYER_RETRY_INTERVAL_TICKS = 20;
    private static final int PLAYER_RETRY_RADIUS_CHUNKS = 3;

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event == null || event.world == null
                || event.world.isRemote
                || !event.getChunk().isTerrainPopulated) {
            return;
        }
        attemptChunk(event.world,
                event.getChunk().xPosition,
                event.getChunk().zPosition);
    }

    @SubscribeEvent
    public void onChunkPopulated(PopulateChunkEvent.Post event) {
        if (event == null || event.world == null
                || event.world.isRemote) {
            return;
        }
        attemptChunk(event.world, event.chunkX, event.chunkZ);
    }

    /**
     * Forge calls this as a chunk is populated, even where the chunk
     * provider posts no population event; LOTR places its fixed
     * structures the same way.
     */
    @Override
    public void generate(
            Random random, int chunkX, int chunkZ, World world,
            IChunkProvider chunkGenerator,
            IChunkProvider chunkProvider) {
        if (world == null || world.isRemote) {
            return;
        }
        attemptChunk(world, chunkX, chunkZ);
    }

    /**
     * LOTR's chunk provider does not always post Forge's population event
     * for a new Middle-earth chunk, so every second the chunks round each
     * player are tried as well. It loads no chunk.
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END
                || !(event.player instanceof EntityPlayerMP)
                || event.player.worldObj == null
                || event.player.worldObj.isRemote
                || event.player.ticksExisted
                        % PLAYER_RETRY_INTERVAL_TICKS != 0) {
            return;
        }
        attemptChunk(event.player.worldObj,
                ((int)Math.floor(event.player.posX)) >> 4,
                ((int)Math.floor(event.player.posZ)) >> 4,
                PLAYER_RETRY_RADIUS_CHUNKS);
    }

    private static void attemptChunk(
            World world, int chunkX, int chunkZ) {
        attemptChunk(world, chunkX, chunkZ, 1);
    }

    static void attemptChunk(
            World world, int chunkX, int chunkZ, int radiusChunks) {
        if (!(world instanceof WorldServer)) {
            return;
        }
        LostTalesMapMarkerWorldData data;
        try {
            data = LostTalesMapMarkerStorage.get(world);
        } catch (RuntimeException exception) {
            return;
        }
        Collection<LostTalesMapMarkerRecord> candidates =
                data.getRecordsNearChunks(
                        world.provider.dimensionId,
                        chunkX, chunkZ, radiusChunks);
        for (LostTalesMapMarkerRecord record : candidates) {
            if (!record.hasWaystone() || record.isLinked()
                    || record.getGenerationState()
                            != LostTalesWaystoneGenerationState.NOT_ATTEMPTED) {
                continue;
            }
            LostTalesWaystonePlacementService.attempt(
                    (WorldServer)world, record);
        }
    }
}
