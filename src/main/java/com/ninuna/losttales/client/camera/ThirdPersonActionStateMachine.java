package com.ninuna.losttales.client.camera;

/** Tick-based action hysteresis independent of Minecraft classes. */
final class ThirdPersonActionStateMachine {
    private boolean swingingLastTick;
    private int attackCommitmentTicks;
    private int combatHoldTicks;

    ThirdPersonGameplayState update(
            boolean swinging, boolean aiming, boolean hurt,
            boolean combatItemHeld,
            boolean combatProfileWithWeaponHeld,
            int configuredAttackCommitmentTicks,
            int configuredCombatHoldTicks) {
        int commitment = Math.max(0,
                configuredAttackCommitmentTicks);
        int combatHold = Math.max(0, configuredCombatHoldTicks);
        if (swinging && !swingingLastTick) {
            attackCommitmentTicks = commitment;
        }
        if (swinging || aiming || hurt) {
            combatHoldTicks = combatHold;
        }

        boolean committed = attackCommitmentTicks > 0;
        boolean attacking = swinging || committed;
        boolean combat = aiming || attacking || combatHoldTicks > 0
                || combatProfileWithWeaponHeld && combatItemHeld;
        ThirdPersonGameplayState result = new ThirdPersonGameplayState(
                aiming, attacking, committed, combat);

        if (attackCommitmentTicks > 0) {
            --attackCommitmentTicks;
        }
        if (!swinging && !aiming && !hurt && combatHoldTicks > 0) {
            --combatHoldTicks;
        }
        swingingLastTick = swinging;
        return result;
    }

    void reset() {
        swingingLastTick = false;
        attackCommitmentTicks = 0;
        combatHoldTicks = 0;
    }
}
