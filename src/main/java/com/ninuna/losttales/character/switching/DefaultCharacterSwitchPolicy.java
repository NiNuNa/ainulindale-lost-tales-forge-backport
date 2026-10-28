package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.compat.minecraft.PlayerItemUseAccess;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.entity.combat.LostTalesMobAggroEventHandler;
import com.ninuna.losttales.util.LostTalesMath;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.util.AxisAlignedBB;

/** Every check a switch must pass, each refusal with its reason. */
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

    @Override
    public CharacterSwitchPolicyResult evaluateFirstCharacter(
            EntityPlayerMP player, CharacterSwitchAccountState accountState,
            long safeNow) {
        CharacterSwitchPolicyResult session =
                sessionRefusal(player, accountState, false);
        return session != null ? session : CharacterSwitchPolicyResult.allowed();
    }

    private CharacterSwitchPolicyResult evaluate(EntityPlayerMP player,
                                                  CharacterSwitchAccountState accountState,
                                                  long safeNow,
                                                  boolean allowOwnedSwitch) {
        CharacterSwitchPolicyResult session =
                sessionRefusal(player, accountState, allowOwnedSwitch);
        if (session != null) {
            return session;
        }
        CharacterLifecycleStateTracker.Snapshot lifecycle =
                CharacterLifecycleStateTracker.snapshot(player);
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
                && player.openContainer != player.inventoryContainer
                || holdsCraftingItems(player)) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_CONTAINER_OPEN);
        }
        if (player.inventory != null && player.inventory.getItemStack() != null) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_ITEM_IN_CURSOR);
        }
        if (PlayerItemUseAccess.isUsingItem(player)) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_USING_ITEM);
        }

        long combatGrace = Math.max(0L,
                LostTalesConfig.characterSwitchCombatGraceMillis);
        if ((lifecycle.getLastCombatAt() > 0L
                && safeNow - lifecycle.getLastCombatAt() < combatGrace)
                || LostTalesMobAggroEventHandler.hasTrackedCombat(player)) {
            long retryAt = lifecycle.getLastCombatAt() <= 0L
                    ? -1L : LostTalesMath.saturatingAdd(lifecycle.getLastCombatAt(), combatGrace);
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
                < Math.max(0, LostTalesConfig.characterSwitchStableGroundTicks)
                || !standsOnGround(player)) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_UNSAFE_MOVEMENT);
        }
        CharacterSwitchPolicyResult cooldownRefusal = cooldownRefusal(
                accountState, safeNow, isCooldownExempt(player));
        if (cooldownRefusal != null) {
            return cooldownRefusal;
        }
        return CharacterSwitchPolicyResult.allowed();
    }

    /**
     * Whether the inventory's own crafting grid holds anything. No saved
     * life keeps the grid, so its items would fall out at the next
     * character's feet when the inventory closes.
     */
    private static boolean holdsCraftingItems(EntityPlayerMP player) {
        if (!(player.inventoryContainer instanceof ContainerPlayer)) {
            return false;
        }
        InventoryCrafting grid = ((ContainerPlayer)player.inventoryContainer).craftMatrix;
        if (grid == null) {
            return false;
        }
        for (int slot = 0; slot < grid.getSizeInventory(); slot++) {
            if (grid.getStackInSlot(slot) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the world itself holds the player up: something solid lies
     * just under their feet. The ground and the fall the player's game
     * reports are not taken on its word alone.
     */
    private static boolean standsOnGround(EntityPlayerMP player) {
        if (player.boundingBox == null) {
            return false;
        }
        AxisAlignedBB underFeet = player.boundingBox.copy().offset(0.0D, -0.0625D, 0.0D);
        List collisions = player.worldObj.getCollidingBoundingBoxes(player, underFeet);
        return collisions != null && !collisions.isEmpty();
    }

    /**
     * Why the account or the session refuses a switch, or null when
     * neither does: the account's own refusals ({@link #accountRefusal}),
     * a session not ready or ending, a respawn or a change of dimension
     * under way, another switch, and a player not alive.
     */
    private static CharacterSwitchPolicyResult sessionRefusal(
            EntityPlayerMP player, CharacterSwitchAccountState accountState,
            boolean allowOwnedSwitch) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || accountState == null) {
            return CharacterSwitchPolicyResult.denied(CharacterErrorId.INVALID_PLAYER);
        }
        CharacterSwitchPolicyResult accountRefusal = accountRefusal(accountState);
        if (accountRefusal != null) {
            return accountRefusal;
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
        return null;
    }

    /**
     * Why the account itself refuses a switch before the player is
     * looked at, or null when it does not: an account an operator froze,
     * one whose death has not been settled, and one holding a journal
     * awaiting recovery or its restore. The three are the account's own
     * facts, so they are decided here without a player and in the order
     * the evaluation asks them.
     */
    static CharacterSwitchPolicyResult accountRefusal(
            CharacterSwitchAccountState accountState) {
        if (accountState == null) {
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
        if (transaction != null && transaction.isHeld()) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_RECOVERY_REQUIRED);
        }
        return null;
    }

    /**
     * Why the cooldown refuses a switch, with the moment it may be tried
     * again, or null when it does not. Asked last, after everything about
     * the player, so a refusal names the cooldown only when nothing else
     * was in the way.
     */
    static CharacterSwitchPolicyResult cooldownRefusal(
            CharacterSwitchAccountState accountState, long safeNow,
            boolean cooldownExempt) {
        if (accountState == null) {
            return CharacterSwitchPolicyResult.denied(CharacterErrorId.INVALID_PLAYER);
        }
        if (!cooldownExempt && safeNow < accountState.getNextAllowedAt()) {
            return CharacterSwitchPolicyResult.denied(
                    CharacterErrorId.SWITCH_COOLDOWN,
                    accountState.getNextAllowedAt());
        }
        return null;
    }

    static boolean isCooldownExempt(EntityPlayerMP player) {
        return player != null && player.capabilities != null
                && player.capabilities.isCreativeMode;
    }
}
