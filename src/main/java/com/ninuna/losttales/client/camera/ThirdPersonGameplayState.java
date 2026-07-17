package com.ninuna.losttales.client.camera;

/** Immutable client action state used by camera and visual body rotation. */
public final class ThirdPersonGameplayState {
    public static final ThirdPersonGameplayState INACTIVE =
            new ThirdPersonGameplayState(
                    false, false, false, false, false);

    private final boolean aiming;
    private final boolean faceAim;
    private final boolean attacking;
    private final boolean attackCommitted;
    private final boolean combat;

    ThirdPersonGameplayState(
            boolean aiming, boolean faceAim, boolean attacking,
            boolean attackCommitted, boolean combat) {
        this.aiming = aiming;
        this.faceAim = faceAim;
        this.attacking = attacking;
        this.attackCommitted = attackCommitted;
        this.combat = combat;
    }

    public boolean isAiming() {
        return aiming;
    }

    public boolean shouldFaceAim() {
        return faceAim;
    }

    public boolean isAttacking() {
        return attacking;
    }

    public boolean isAttackCommitted() {
        return attackCommitted;
    }

    public boolean isCombat() {
        return combat;
    }
}
