package com.ninuna.losttales.character.physics;

import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import net.minecraft.entity.player.EntityPlayer;

/** Applies race eye positions through Forge's public player eyeHeight field. */
public final class CharacterPlayerEyeHeightHelper {

    private static final float MINIMUM_ABSOLUTE_EYE_HEIGHT = 0.1F;

    private CharacterPlayerEyeHeightHelper() {}

    /**
     * Applies the current standing or sneaking eye position.
     *
     * Forge 1.7.10 deliberately exposes EntityPlayer#eyeHeight. Its value is
     * added directly to posY by suffocation and material checks. The feet are
     * at posY - yOffset + ySize, so the field contains only the offset from
     * posY to the race's eyes. The server's default eye height is already a
     * complete offset, not a correction to add to the race height.
     */
    public static void apply(
            EntityPlayer player,
            CharacterRaceDimensions dimensions,
            boolean hasRace) {
        if (player == null) {
            return;
        }
        if (!hasRace || dimensions == null || player.isPlayerSleeping()) {
            restoreVanilla(player);
            return;
        }

        float absoluteEyeHeight = dimensions.getEyeHeight(player.isSneaking());
        player.eyeHeight = toPlayerEyeHeightField(player, absoluteEyeHeight);
    }

    /** Backwards-compatible entry point for callers not yet using the snapshot. */
    public static void apply(
            EntityPlayer player,
            CharacterRaceGameplayProfile profile,
            boolean hasRace) {
        CharacterRaceDimensions dimensions = profile == null
                ? null
                : CharacterRaceDimensions.fromProfile(profile.getRaceId(), profile);
        apply(player, dimensions, hasRace);
    }

    public static void restoreVanilla(EntityPlayer player) {
        if (player != null) {
            player.eyeHeight = player.getDefaultEyeHeight();
        }
    }

    static float toPlayerEyeHeightField(
            EntityPlayer player, float absoluteEyeHeight) {
        return toPlayerEyeHeightField(
                absoluteEyeHeight, player.yOffset, player.ySize);
    }

    static float toPlayerEyeHeightField(
            float absoluteEyeHeight, float yOffset, float ySize) {
        float safeAbsolute = Math.max(
                MINIMUM_ABSOLUTE_EYE_HEIGHT, absoluteEyeHeight);
        return safeAbsolute - yOffset + ySize;
    }
}
