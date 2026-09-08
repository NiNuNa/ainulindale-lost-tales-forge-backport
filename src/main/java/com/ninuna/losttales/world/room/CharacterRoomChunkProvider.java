package com.ninuna.losttales.world.room;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Generates the character room and empty air everywhere else.
 *
 * <p>Every chunk is asked of {@link CharacterRoomLayout} block by block;
 * a chunk the room does not reach is handed back empty, which is what
 * keeps the world small and its spawn area quick to prepare. No ores, no
 * structures, no decoration, and no creature may spawn in it.</p>
 */
public final class CharacterRoomChunkProvider implements IChunkProvider {

    private static final String NAME = "LostTalesCharacterRoom";

    private final World world;

    public CharacterRoomChunkProvider(World world) {
        this.world = world;
    }

    @Override
    public Chunk provideChunk(int chunkX, int chunkZ) {
        Chunk chunk = new Chunk(this.world, chunkX, chunkZ);
        if (CharacterRoomLayout.touchesChunk(chunkX, chunkZ)) {
            fillRoom(chunk, chunkX, chunkZ);
        }
        chunk.generateSkylightMap();
        Arrays.fill(chunk.getBiomeArray(), (byte) BiomeGenBase.plains.biomeID);
        return chunk;
    }

    private void fillRoom(Chunk chunk, int chunkX, int chunkZ) {
        ExtendedBlockStorage[] storages = chunk.getBlockStorageArray();
        boolean storesSkylight = !this.world.provider.hasNoSky;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = (chunkX << 4) + localX;
                int z = (chunkZ << 4) + localZ;
                for (int y = CharacterRoomLayout.MIN_Y; y <= CharacterRoomLayout.MAX_Y; y++) {
                    Block block = blockFor(CharacterRoomLayout.materialAt(x, y, z));
                    if (block == null) {
                        continue;
                    }
                    int section = y >> 4;
                    ExtendedBlockStorage storage = storages[section];
                    if (storage == null) {
                        storage = new ExtendedBlockStorage(section << 4, storesSkylight);
                        storages[section] = storage;
                    }
                    storage.func_150818_a(localX, y & 15, localZ, block);
                }
            }
        }
    }

    /** The block a material is built from; null for air. */
    static Block blockFor(CharacterRoomLayout.Material material) {
        switch (material) {
            case FLOOR:
                return Blocks.planks;
            case WALL:
            case CEILING:
                return Blocks.stonebrick;
            case LIGHT:
                return Blocks.glowstone;
            default:
                return null;
        }
    }

    @Override
    public Chunk loadChunk(int chunkX, int chunkZ) {
        return provideChunk(chunkX, chunkZ);
    }

    @Override
    public boolean chunkExists(int chunkX, int chunkZ) {
        return true;
    }

    /**
     * Lights the ceiling lamps. A freshly generated chunk carries no
     * block light until something asks for it, and a sealed room has no
     * daylight to fall back on, so each lamp in the chunk is lit here,
     * once the chunk is in the world and light can spread from it.
     */
    @Override
    public void populate(IChunkProvider provider, int chunkX, int chunkZ) {
        if (!CharacterRoomLayout.touchesChunk(chunkX, chunkZ)) {
            return;
        }
        int y = CharacterRoomLayout.CEILING_Y;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = (chunkX << 4) + localX;
                int z = (chunkZ << 4) + localZ;
                if (CharacterRoomLayout.materialAt(x, y, z)
                        == CharacterRoomLayout.Material.LIGHT) {
                    this.world.func_147451_t(x, y, z);
                }
            }
        }
    }

    @Override
    public boolean saveChunks(boolean all, IProgressUpdate progress) {
        return true;
    }

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public String makeString() {
        return NAME;
    }

    /** Nothing lives in the room but the player. */
    @Override
    public List getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return Collections.emptyList();
    }

    @Override
    public ChunkPosition func_147416_a(World world, String structure,
                                       int x, int y, int z) {
        return null;
    }

    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    @Override
    public void recreateStructures(int chunkX, int chunkZ) {}

    @Override
    public void saveExtraData() {}
}
