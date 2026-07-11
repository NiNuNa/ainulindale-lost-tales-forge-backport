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
     * an offset consumed by Forge's player getPosition patch. The default value
     * must remain part of the conversion: default + desired height above feet
     * - yOffset.
     */
    public static void apply(
            EntityPlayer player,
            CharacterRaceGameplayProfile profile,
            boolean hasRace) {
        if (player == null) {
            return;
        }
        if (!hasRace || profile == null || player.isPlayerSleeping()) {
            restoreVanilla(player);
            return;
        }

        float absoluteEyeHeight = player.isSneaking()
                ? profile.getSneakingEyeHeight()
                : profile.getStandingEyeHeight();
        player.eyeHeight = toPlayerEyeHeightField(player, absoluteEyeHeight);
    }

    public static void restoreVanilla(EntityPlayer player) {
        if (player != null) {
            player.eyeHeight = player.getDefaultEyeHeight();
        }
    }

    static float toPlayerEyeHeightField(
            EntityPlayer player, float absoluteEyeHeight) {
        float safeAbsolute = Math.max(
                MINIMUM_ABSOLUTE_EYE_HEIGHT, absoluteEyeHeight);
        return player.getDefaultEyeHeight() + safeAbsolute - player.yOffset;
    }
}
