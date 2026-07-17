package com.ninuna.losttales.client.camera;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ThirdPersonActionStateMachineTest {

    @Test
    public void newSwingStartsBoundedAttackCommitment() {
        ThirdPersonActionStateMachine machine =
                new ThirdPersonActionStateMachine();
        ThirdPersonGameplayState first = machine.update(
                true, false, false, true,
                false, 2, 0);
        ThirdPersonGameplayState second = machine.update(
                false, false, false, true,
                false, 2, 0);
        ThirdPersonGameplayState third = machine.update(
                false, false, false, true,
                false, 2, 0);

        assertTrue(first.isAttacking());
        assertTrue(first.isAttackCommitted());
        assertTrue(second.isAttackCommitted());
        assertFalse(third.isAttackCommitted());
    }

    @Test
    public void combatHoldExpiresWithoutPermanentWeaponProfile() {
        ThirdPersonActionStateMachine machine =
                new ThirdPersonActionStateMachine();
        assertTrue(machine.update(
                true, false, false, false,
                false, 0, 2).isCombat());
        assertTrue(machine.update(
                false, false, false, false,
                false, 0, 2).isCombat());
        assertTrue(machine.update(
                false, false, false, false,
                false, 0, 2).isCombat());
        assertFalse(machine.update(
                false, false, false, false,
                false, 0, 2).isCombat());
    }

    @Test
    public void weaponHeldProfileIsOptional() {
        ThirdPersonActionStateMachine machine =
                new ThirdPersonActionStateMachine();
        assertFalse(machine.update(
                false, false, false, true,
                false, 0, 0).isCombat());
        assertTrue(machine.update(
                false, false, false, true,
                true, 0, 0).isCombat());
    }

    @Test
    public void aimingOverridesCombatAndCountsAsCombat() {
        ThirdPersonGameplayState state =
                new ThirdPersonActionStateMachine().update(
                        false, true, false, false,
                        false, 0, 0);
        assertTrue(state.isAiming());
        assertTrue(state.isCombat());
    }

    @Test
    public void userFacingSecondsRoundUpToWholeTicks() {
        assertTrue(ThirdPersonGameplayStateTracker.secondsToTicks(
                0.01D) == 1);
        assertTrue(ThirdPersonGameplayStateTracker.secondsToTicks(
                0.25D) == 5);
        assertTrue(ThirdPersonGameplayStateTracker.secondsToTicks(
                0.0D) == 0);
    }
}
