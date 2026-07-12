package com.ninuna.losttales.character.physics;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Common-side world-space spawn origins for entities created by a player.
 *
 * Minecraft 1.7.10 anchors EntityPlayer#posY above the bottom of the player
 * bounding box. Vanilla projectile and item-drop constructors then combine
 * that anchor with the small Forge player eye-height field. The race profile,
 * by contrast, stores an absolute eye height above the feet. These helpers
 * preserve the vanilla default-size origin while applying the same relationship
 * to shorter and taller races.
 */
public final class CharacterSpawnOriginHelper {

    /** Forge 1.7.10's default EntityPlayer eyeHeight field value. */
    private static final double LEGACY_PLAYER_EYE_FIELD = 0.12D;

    /** Downward offset used by vanilla arrows and LOTR projectile constructors. */
    private static final double PROJECTILE_DOWN_OFFSET = 0.10D;

    /** Downward offset used by vanilla player item drops. */
    private static final double ITEM_DROP_DOWN_OFFSET = 0.30D;

    private CharacterSpawnOriginHelper() {}

    public static double getProjectileOriginY(
            EntityPlayer player, CharacterRaceDimensions dimensions) {
        if (player == null || dimensions == null) {
            return player == null ? 0.0D : player.posY;
        }
        return getFeetY(player)
                + dimensions.getProjectileEyeReferenceHeight(player.isSneaking())
                + LEGACY_PLAYER_EYE_FIELD
                - PROJECTILE_DOWN_OFFSET;
    }

    public static double getItemDropOriginY(
            EntityPlayer player, CharacterRaceDimensions dimensions) {
        if (player == null || dimensions == null) {
            return player == null ? 0.0D : player.posY;
        }
        return getFeetY(player)
                + dimensions.getItemDropEyeReferenceHeight(player.isSneaking())
                + LEGACY_PLAYER_EYE_FIELD
                - ITEM_DROP_DOWN_OFFSET;
    }

    /**
     * Uses the actual collision-box floor so riding, ySize interpolation, and
     * the legacy player yOffset convention do not leak into race calculations.
     */
    public static double getFeetY(EntityPlayer player) {
        if (player == null) {
            return 0.0D;
        }
        if (player.boundingBox != null) {
            return player.boundingBox.minY;
        }
        return player.posY - (double)player.yOffset + (double)player.ySize;
    }
}
