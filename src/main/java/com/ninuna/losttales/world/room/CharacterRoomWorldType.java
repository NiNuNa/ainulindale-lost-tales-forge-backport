package com.ninuna.losttales.world.room;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * The world type of the character room: a world that holds the room and
 * nothing else, as {@link CharacterRoomLayout} describes it.
 *
 * <p>Registered once from the common proxy on both sides. A world names
 * its type, so the client joining an integrated server sees the same
 * type the server generated with, and every side can ask
 * {@link #isRoom(World)} about a world it holds. The type stays out of
 * the world-creation screen's list: the room is entered from the main
 * menu, not made by hand.</p>
 */
public final class CharacterRoomWorldType extends WorldType {

    /** The name a world file records; save surface. */
    public static final String NAME = "losttales_room";

    private static CharacterRoomWorldType instance;

    private CharacterRoomWorldType() {
        super(NAME);
    }

    /** Registers the type, once; later calls return the same one. */
    public static synchronized CharacterRoomWorldType register() {
        if (instance == null) {
            instance = new CharacterRoomWorldType();
        }
        return instance;
    }

    /** The registered type, or null before registration. */
    public static synchronized CharacterRoomWorldType get() {
        return instance;
    }

    /** Whether that world, on either side, is a character room. */
    public static boolean isRoom(World world) {
        CharacterRoomWorldType type = get();
        return type != null && world != null && world.getWorldInfo() != null
                && world.getWorldInfo().getTerrainType() == type;
    }

    /** Whether that server is hosting a character room. */
    public static boolean isRoomServer(MinecraftServer server) {
        if (server == null || server.worldServers == null) {
            return false;
        }
        for (int index = 0; index < server.worldServers.length; index++) {
            World world = server.worldServers[index];
            if (world != null && world.provider != null
                    && world.provider.dimensionId == 0) {
                return isRoom(world);
            }
        }
        return false;
    }

    @Override
    public WorldChunkManager getChunkManager(World world) {
        return new WorldChunkManagerHell(BiomeGenBase.plains, 0.0F);
    }

    @Override
    public IChunkProvider getChunkGenerator(World world, String generatorOptions) {
        return new CharacterRoomChunkProvider(world);
    }

    /** Kept out of the world-creation screen's cycle of types. */
    @Override
    public boolean getCanBeCreated() {
        return false;
    }

    @Override
    public int getMinimumSpawnHeight(World world) {
        return CharacterRoomLayout.SPAWN_Y;
    }

    /** There is no ground outside the room for a horizon to sit on. */
    @Override
    public double getHorizon(World world) {
        return 0.0D;
    }

    @Override
    public boolean hasVoidParticles(boolean flag) {
        return false;
    }
}
