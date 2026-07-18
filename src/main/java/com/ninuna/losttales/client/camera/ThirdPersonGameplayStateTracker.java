package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.compat.minecraft.PlayerItemUseAccess;
import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import com.ninuna.losttales.gameplay.item.ThirdPersonItemUsePolicy;
import com.ninuna.losttales.gameplay.projectile.ThirdPersonProjectileItemPolicy;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;

/** Owns client action-profile hysteresis and lifecycle resets. */
public final class ThirdPersonGameplayStateTracker {
    private static final ThirdPersonActionStateMachine MACHINE =
            new ThirdPersonActionStateMachine();

    private static String contextKey;
    private static ThirdPersonGameplayState state =
            ThirdPersonGameplayState.INACTIVE;

    private ThirdPersonGameplayStateTracker() {}

    public static void update(EntityPlayerSP player) {
        if (player == null || player.worldObj == null
                || !player.isEntityAlive() || player.isPlayerSleeping()) {
            reset();
            return;
        }
        String newContext = contextKey(player);
        if (!newContext.equals(contextKey)) {
            MACHINE.reset();
            contextKey = newContext;
        }

        ItemStack held = player.inventory.getCurrentItem();
        boolean usingItem = PlayerItemUseAccess.isUsingItem(player);
        boolean aiming = ThirdPersonProjectileItemPolicy
                .isActivelyAiming(held, usingItem);
        boolean targetLocked = ThirdPersonTargetLockController
                .hasTarget(player);
        boolean faceAim = ThirdPersonItemUsePolicy.shouldFaceAim(
                held, usingItem) || targetLocked;
        state = MACHINE.update(
                player.isSwingInProgress,
                aiming, faceAim, targetLocked,
                player.hurtTime > 0,
                ThirdPersonCombatItemPolicy.isCombatItem(held),
                LostTalesThirdPersonConfig
                        .combatProfileWithWeaponHeld,
                secondsToTicks(LostTalesThirdPersonConfig
                        .attackCommitmentSeconds),
                secondsToTicks(LostTalesThirdPersonConfig
                        .combatProfileHoldSeconds));
    }

    public static ThirdPersonGameplayState get(EntityPlayerSP player) {
        return player != null && contextKey(player).equals(contextKey)
                ? state : ThirdPersonGameplayState.INACTIVE;
    }

    public static void reset() {
        MACHINE.reset();
        contextKey = null;
        state = ThirdPersonGameplayState.INACTIVE;
    }

    static int secondsToTicks(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds)
                || seconds <= 0.0D) {
            return 0;
        }
        return (int)Math.min(Integer.MAX_VALUE,
                Math.ceil(seconds * 20.0D));
    }

    private static String contextKey(EntityPlayerSP player) {
        if (player == null || player.worldObj == null) {
            return "none";
        }
        int dimension = player.worldObj.provider == null
                ? 0 : player.worldObj.provider.dimensionId;
        return player.getEntityId() + "@" + dimension;
    }
}
