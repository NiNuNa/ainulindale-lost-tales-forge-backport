package com.ninuna.losttales.character.physics;

import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/** Client camera adjustment invoked from the small EntityRenderer transformer. */
public final class CharacterCameraHook {

    private static final float MINIMUM_EYE_HEIGHT = 0.1F;

    private CharacterCameraHook() {}

    /**
     * EntityRenderer 1.7.10 computes a camera subtraction from yOffset using a
     * hard-coded vanilla player height. Replace only that subtraction while a
     * synchronized roleplay race is active.
     */
    public static float resolveCameraOffset(
            EntityLivingBase viewEntity, float vanillaOffset) {
        if (!(viewEntity instanceof EntityPlayer)) {
            return vanillaOffset;
        }
        EntityPlayer player = (EntityPlayer)viewEntity;
        String raceId = CharacterRaceEntityData.getRaceId(player);
        if (player.isPlayerSleeping() || raceId.length() == 0) {
            return vanillaOffset;
        }

        float fallback = Math.max(MINIMUM_EYE_HEIGHT,
                player.yOffset - vanillaOffset);
        CharacterRaceDefinition definition = CharacterRaceRegistry.get(raceId);
        float desiredEyeHeight;
        if (definition != null) {
            desiredEyeHeight = player.isSneaking()
                    ? definition.getSneakingEyeHeight()
                    : definition.getStandingEyeHeight();
        } else {
            desiredEyeHeight = player.isSneaking()
                    ? CharacterRaceEntityData.getSneakingEyeHeight(
                    player, fallback)
                    : CharacterRaceEntityData.getStandingEyeHeight(
                    player, fallback);
        }
        desiredEyeHeight = Math.max(MINIMUM_EYE_HEIGHT, desiredEyeHeight);
        return player.yOffset - desiredEyeHeight;
    }
}
