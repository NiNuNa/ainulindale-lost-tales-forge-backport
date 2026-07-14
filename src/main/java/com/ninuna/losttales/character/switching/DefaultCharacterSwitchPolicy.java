package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.event.LostTalesMobAggroEventHandler;
import net.minecraft.entity.player.EntityPlayerMP;

/** Conservative default policy; individual checks can later become providers. */
public final class DefaultCharacterSwitchPolicy implements CharacterSwitchPolicy {

    @Override
    public CharacterSwitchPolicyResult evaluate(EntityPlayerMP player,
                                                CharacterSwitchAccountState accountState,
                                                long safeNow) {
        return evaluate(player, accountState, safeNow, false);
    }

    @Override
    public CharacterSwitchPolicyResult evaluateDuringOwnedSwitch(
            EntityPlayerMP player, CharacterSwitchAccountState accountState, long safeNow) {
        return evaluate(player, accountState, safeNow, true);
    }

    private CharacterSwitchPolicyResult evaluate(EntityPlayerMP player,
                                                  CharacterSwitchAccountState accountState,
                                                  long safeNow,
                                                  boolean allowOwnedSwitch) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || accountState == null) {
            return CharacterSwitchPolicyResult.denied(CharacterErrorId.INVALID_PLAYER);
        }
        if (accountState.isFrozen()) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_ACCOUNT_FROZEN);
        }
        if (accountState.isDeathPending()) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_DEATH_PENDING);
        }
        CharacterSwitchTransaction transaction = accountState.getTransaction();
        if (transaction != null
                && transaction.getStatus()
                == CharacterSwitchTransactionStatus.RECOVERY_REQUIRED) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_RECOVERY_REQUIRED);
        }

        CharacterLifecycleStateTracker.Snapshot lifecycle =
                CharacterLifecycleStateTracker.snapshot(player);
        if (!lifecycle.isPresent() || !lifecycle.isReady()
                || lifecycle.isLoggingOut() || lifecycle.isServerStopping()) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_PLAYER_NOT_READY);
        }
        if (lifecycle.isRespawning()) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_RESPAWNING);
        }
        if (lifecycle.isDimensionChanging()) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_CHANGING_DIMENSION);
        }
        if (lifecycle.isSwitching() && !allowOwnedSwitch) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_ALREADY_IN_PROGRESS);
        }
        if (!player.isEntityAlive() || player.isDead || player.getHealth() <= 0.0F) {
            return CharacterSwitchPolicyResult.denied(CharacterErrorId.PLAYER_DEAD);
        }
        if (player.isPlayerSleeping()) {
            return CharacterSwitchPolicyResult.denied(CharacterErrorId.PLAYER_SLEEPING);
        }
        if (player.ridingEntity != null || player.riddenByEntity != null) {
            return CharacterSwitchPolicyResult.denied(CharacterErrorId.SWITCH_RIDING);
        }
        if (LotrCharacterAdapter.getInstance().isFastTravelActive(player)) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_FAST_TRAVEL);
        }
        if (player.openContainer != null
                && player.openContainer != player.inventoryContainer) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_CONTAINER_OPEN);
        }
        if (player.inventory != null && player.inventory.getItemStack() != null) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_ITEM_IN_CURSOR);
        }
        if (player.isUsingItem()) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_USING_ITEM);
        }

        long combatGrace = Math.max(0L,
                LostTalesConfig.characterSwitchCombatGraceMillis);
        if ((lifecycle.getLastCombatAt() > 0L
                && safeNow - lifecycle.getLastCombatAt() < combatGrace)
                || LostTalesMobAggroEventHandler.hasTrackedCombat(player)) {
            long retryAt = lifecycle.getLastCombatAt() <= 0L
                    ? -1L : safeAdd(lifecycle.getLastCombatAt(), combatGrace);
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_IN_COMBAT, retryAt);
        }

        if (safeNow < lifecycle.getTransitionUntil()) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_TELEPORTING,
                    lifecycle.getTransitionUntil());
        }
        if (player.isInWater()) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_SWIMMING);
        }
        if (player.handleLavaMovement()) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_UNSAFE_MOVEMENT);
        }
        if (player.capabilities != null && player.capabilities.isFlying) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_UNSAFE_MOVEMENT);
        }
        if (!player.onGround || player.fallDistance > 0.0F
                || Math.abs(player.motionY) > 0.08D
                || lifecycle.getStableGroundTicks()
                < Math.max(0, LostTalesConfig.characterSwitchStableGroundTicks)) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_UNSAFE_MOVEMENT);
        }
        if (!isCooldownExempt(player)
                && safeNow < accountState.getNextAllowedAt()) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_COOLDOWN,
                    accountState.getNextAllowedAt());
        }
        return CharacterSwitchPolicyResult.allowed();
    }

    private static long safeAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE : left + right;
    }

    static boolean isCooldownExempt(EntityPlayerMP player) {
        return player != null && player.capabilities != null
                && player.capabilities.isCreativeMode;
    }
}
