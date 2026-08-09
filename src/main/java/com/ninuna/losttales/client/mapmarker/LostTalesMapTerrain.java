package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.genlayer.LOTRGenLayerWorld;

/**
 * What kind of ground the map image has at a pixel.
 *
 * <p>The biome image LOTR builds the map from is a plain byte per pixel and is
 * fixed for the session, so asking it what is somewhere costs one array read
 * and can be done freely. Every decoration is placed from this and nothing
 * else — no world access, no chunk loading, no guessing from colours.</p>
 *
 * <p>Each kind is decided from data LOTR already publishes rather than from a
 * list of biome names kept here. Forest is "this biome plants a lot of trees",
 * which is what a forest is; mountain is taken from LOTR's own naming of its
 * mountain biomes, collected once by reflection so that adding a range to the
 * base mod needs no change here. Both fail closed: if the shape LOTR publishes
 * ever changes, the answer is "not this kind" and the decoration simply is not
 * drawn.</p>
 */
@SideOnly(Side.CLIENT)
enum LostTalesMapTerrain implements
        LostTalesMapDecorationPlacement.GroundSampler {
    /** Sea and lakes. Rivers are excluded: they are too thin to break on. */
    OPEN_WATER,
    /** Anything a hull could sit on, rivers included. */
    NAVIGABLE_WATER,
    FOREST,
    MOUNTAIN;

    /**
     * Trees per chunk at which a biome counts as wooded.
     *
     * <p>Open country plants a handful; a forest plants many times that. The
     * threshold sits well above the first and well below the second, so it
     * does not have to be retuned every time LOTR adjusts a biome.</p>
     */
    private static final int FOREST_TREES_PER_CHUNK = 6;
    /** How LOTR names the fields holding its mountain biomes. */
    private static final String MOUNTAIN_FIELD_MARKER = "mountain";

    private static Set<Integer> mountainBiomeIds;
    private static boolean mountainIdsResolved;

    @Override
    public boolean matches(int mapX, int mapY) {
        LOTRBiome biome = biomeAt(mapX, mapY);
        if (biome == null) {
            return false;
        }
        switch (this) {
            case OPEN_WATER:
                return biome == LOTRBiome.ocean || biome == LOTRBiome.lake;
            case NAVIGABLE_WATER:
                return biome == LOTRBiome.ocean || biome == LOTRBiome.lake
                        || biome == LOTRBiome.river;
            case FOREST:
                return treesPerChunk(biome) >= FOREST_TREES_PER_CHUNK;
            case MOUNTAIN:
                return isMountain(biome);
            default:
                return false;
        }
    }

    private static LOTRBiome biomeAt(int mapX, int mapY) {
        try {
            return LOTRGenLayerWorld.getBiomeOrOcean(mapX, mapY);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Known map land, with rivers retained as part of their surrounding land. */
    static boolean isLand(int mapX, int mapY) {
        LOTRBiome biome = biomeAt(mapX, mapY);
        return biome != null && biome != LOTRBiome.ocean
                && biome != LOTRBiome.lake;
    }

    private static int treesPerChunk(LOTRBiome biome) {
        try {
            return biome.decorator == null
                    ? 0 : biome.decorator.treesPerChunk;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean isMountain(LOTRBiome biome) {
        Set<Integer> ids = mountainIds();
        try {
            return ids != null
                    && ids.contains(Integer.valueOf(biome.biomeID));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Every biome LOTR names as a mountain, by its own field names.
     *
     * <p>Resolved once. Naming them here instead would be a list of twenty
     * that goes stale the first time a range is added, and a hard reference to
     * each would turn a renamed field into a crash rather than into one
     * missing decoration.</p>
     */
    private static synchronized Set<Integer> mountainIds() {
        if (mountainIdsResolved) {
            return mountainBiomeIds;
        }
        mountainIdsResolved = true;
        try {
            HashSet<Integer> ids = new HashSet<Integer>();
            Field[] fields = LOTRBiome.class.getFields();
            for (int index = 0; index < fields.length; index++) {
                Field field = fields[index];
                if (!Modifier.isStatic(field.getModifiers())
                        || !LOTRBiome.class.isAssignableFrom(
                                field.getType())
                        || !field.getName().toLowerCase()
                                .contains(MOUNTAIN_FIELD_MARKER)) {
                    continue;
                }
                Object value = field.get(null);
                if (value instanceof LOTRBiome) {
                    ids.add(Integer.valueOf(((LOTRBiome)value).biomeID));
                }
            }
            mountainBiomeIds = ids.isEmpty() ? null : ids;
        } catch (Throwable ignored) {
            mountainBiomeIds = null;
        }
        return mountainBiomeIds;
    }

    /** Whether the map image is loaded and worth asking about at all. */
    static boolean isMapImageReady() {
        try {
            return LOTRGenLayerWorld.loadedBiomeImage()
                    && LOTRGenLayerWorld.imageWidth > 0
                    && LOTRGenLayerWorld.imageHeight > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Forgets what was worked out about a world that is being left. */
    static synchronized void clear() {
        mountainBiomeIds = null;
        mountainIdsResolved = false;
    }
}
