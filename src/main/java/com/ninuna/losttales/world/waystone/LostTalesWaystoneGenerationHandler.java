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

/** Lazy backfill for existing chunks plus post-population placement for new ones. */
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
     * Forge calls registered world generators from the server chunk
     * population pipeline even when a custom provider does not post the
     * corresponding terrain event. This matches how LOTR fixed structures
     * are selected during biome decoration.
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
     * LOTR's legacy chunk provider does not consistently emit Forge's
     * post-population event for newly generated Middle-earth chunks. A
     * bounded proximity pass makes lazy generation reliable after terrain
     * has finished loading, without requesting or forcing any chunks.
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
