package com.ninuna.losttales.character.physics;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Where a projectile or a dropped item starts, for a player of any race.
 * The game starts them from the player's {@code posY}, which stands above
 * the feet, plus Forge's small eye-height field; a race keeps its eye
 * height above the feet. These give a player of the default size the
 * game's own start, and shorter and taller races the same start for their
 * eyes.
 */
public final class CharacterSpawnOriginHelper {

    /** Forge's {@code EntityPlayer.eyeHeight} field at its default. */
    private static final double PLAYER_EYE_FIELD = 0.12D;

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
                + PLAYER_EYE_FIELD
                - PROJECTILE_DOWN_OFFSET;
    }

    public static double getItemDropOriginY(
            EntityPlayer player, CharacterRaceDimensions dimensions) {
        if (player == null || dimensions == null) {
            return player == null ? 0.0D : player.posY;
        }
        return getFeetY(player)
                + dimensions.getItemDropEyeReferenceHeight(player.isSneaking())
                + PLAYER_EYE_FIELD
                - ITEM_DROP_DOWN_OFFSET;
    }

    /**
     * The bottom of the player's hitbox, so riding, {@code ySize} and the
     * player's {@code yOffset} never move a race's numbers.
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
