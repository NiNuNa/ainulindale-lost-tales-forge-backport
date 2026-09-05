package com.ninuna.losttales.util;

import java.util.Locale;
import lotr.common.LOTRDimension;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

/**
 * Dimension names and the one world every Lost Tales store lives in.
 * World-scoped data is kept in dimension 0's {@link MapStorage} whatever
 * dimension the player is in, so every storage accessor resolves the
 * overworld through {@link #overworld} and flushes through
 * {@link #flushOverworldStorage}.
 */
public final class LostTalesDimensionHelper {
    private LostTalesDimensionHelper() {}

    /**
     * The server's overworld: the running server's dimension 0, or the
     * given world when it is that dimension itself. Throws when neither
     * can be had, since a store cannot be read from nowhere.
     */
    public static WorldServer overworld(World world) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            WorldServer overworld = server.worldServerForDimension(0);
            if (overworld != null) {
                return overworld;
            }
        }
        if (world instanceof WorldServer && world.provider.dimensionId == 0) {
            return (WorldServer)world;
        }
        throw new IllegalStateException(
                "Unable to resolve the server overworld for world storage");
    }

    /**
     * Writes every dirty store of the overworld to disk now. There is no
     * narrower write in 1.7.10 — {@code MapStorage.saveData} is private —
     * so a caller asking for one store's durability gets them all.
     */
    public static void flushOverworldStorage(World world) {
        overworld(world).mapStorage.saveAllData();
    }

    public static int parseDimensionId(String dimensionName, int fallback) {
        if (dimensionName == null || dimensionName.length() == 0) {
            return fallback;
        }

        String normalized = dimensionName.toLowerCase(Locale.ROOT).trim();
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {}

        if ("minecraft:overworld".equals(normalized) || "overworld".equals(normalized)) {
            return 0;
        }
        if ("minecraft:the_nether".equals(normalized) || "minecraft:nether".equals(normalized) || "the_nether".equals(normalized) || "nether".equals(normalized)) {
            return -1;
        }
        if ("minecraft:the_end".equals(normalized) || "minecraft:end".equals(normalized) || "the_end".equals(normalized) || "end".equals(normalized)) {
            return 1;
        }
        if ("lotr:middle_earth".equals(normalized) || "lotr:middle-earth".equals(normalized) || "middle_earth".equals(normalized) || "middle-earth".equals(normalized) || "middleearth".equals(normalized)) {
            return LOTRDimension.MIDDLE_EARTH.dimensionID;
        }
        if ("lotr:utumno".equals(normalized) || "utumno".equals(normalized)) {
            return LOTRDimension.UTUMNO.dimensionID;
        }

        return fallback;
    }
}
