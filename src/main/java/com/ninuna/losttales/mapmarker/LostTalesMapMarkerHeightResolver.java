package com.ninuna.losttales.mapmarker;

import lotr.common.LOTRMod;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** Resolves marker height without loading an otherwise-unloaded chunk. */
public final class LostTalesMapMarkerHeightResolver {
    public static final double AUTOMATIC_Y = -1.0D;

    private LostTalesMapMarkerHeightResolver() {}

    public static boolean isAutomatic(double y) {
        return y < 0.0D;
    }

    public static double resolve(
            World world, int dimensionId,
            double x, double configuredY, double z) {
        if (!isAutomatic(configuredY)) {
            return configuredY;
        }
        if (world == null || world.provider == null
                || world.provider.dimensionId != dimensionId) {
            return AUTOMATIC_Y;
        }
        int blockX = MathHelper.floor_double(x);
        int blockZ = MathHelper.floor_double(z);
        if (world.getChunkProvider() == null
                || !world.getChunkProvider().chunkExists(
                        blockX >> 4, blockZ >> 4)) {
            return AUTOMATIC_Y;
        }
        try {
            int y = LOTRMod.getTrueTopBlock(world, blockX, blockZ);
            if (isValidHeight(world, y)) {
                return y;
            }
        } catch (LinkageError ignored) {
            // Fall back to the Forge/vanilla surface query.
        } catch (RuntimeException ignored) {
            // Malformed terrain must not break marker rendering or discovery.
        }
        try {
            int y = world.getTopSolidOrLiquidBlock(blockX, blockZ);
            return isValidHeight(world, y) ? y : AUTOMATIC_Y;
        } catch (RuntimeException ignored) {
            return AUTOMATIC_Y;
        }
    }

    public static double resolveOr(
            World world, int dimensionId,
            double x, double configuredY, double z,
            double fallbackY) {
        double resolved = resolve(
                world, dimensionId, x, configuredY, z);
        return isAutomatic(resolved) ? fallbackY : resolved;
    }

    private static boolean isValidHeight(World world, int y) {
        return y >= 0 && y < world.getActualHeight();
    }
}
