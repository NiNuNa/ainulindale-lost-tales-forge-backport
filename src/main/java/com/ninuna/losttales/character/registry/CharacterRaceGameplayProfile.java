package com.ninuna.losttales.character.registry;

/**
 * Immutable resolved gameplay values for one race.
 *
 * Values normally come from representative LOTR Legacy NPCs. Each race also
 * has deterministic common-side fallback values so a failed compatibility
 * probe never silently turns a Hobbit or Half-troll into a vanilla-sized player.
 */
public final class CharacterRaceGameplayProfile {

    private final String raceId;
    private final String representativeEntityClassName;
    private final float width;
    private final float height;
    private final float standingEyeHeight;
    private final float sneakingEyeHeight;
    private final double maxHealth;
    private final double movementSpeedMultiplier;
    private final double attackDamage;
    private final boolean lotrDerived;

    public CharacterRaceGameplayProfile(
            String raceId,
            String representativeEntityClassName,
            float width,
            float height,
            float standingEyeHeight,
            float sneakingEyeHeight,
            double maxHealth,
            double movementSpeedMultiplier,
            double attackDamage,
            boolean lotrDerived) {
        this.raceId = raceId == null ? "" : raceId;
        this.representativeEntityClassName = representativeEntityClassName == null
                ? "" : representativeEntityClassName;
        this.width = width;
        this.height = height;
        this.standingEyeHeight = standingEyeHeight;
        this.sneakingEyeHeight = sneakingEyeHeight;
        this.maxHealth = maxHealth;
        this.movementSpeedMultiplier = movementSpeedMultiplier;
        this.attackDamage = attackDamage;
        this.lotrDerived = lotrDerived;
    }

    public String getRaceId() {
        return this.raceId;
    }

    public String getRepresentativeEntityClassName() {
        return this.representativeEntityClassName;
    }

    public float getWidth() {
        return this.width;
    }

    public float getHeight() {
        return this.height;
    }

    /** Kept for existing GUI callers; this is the standing value. */
    public float getEyeHeight() {
        return this.standingEyeHeight;
    }

    public float getStandingEyeHeight() {
        return this.standingEyeHeight;
    }

    public float getSneakingEyeHeight() {
        return this.sneakingEyeHeight;
    }

    public double getMaxHealth() {
        return this.maxHealth;
    }

    public double getMovementSpeedMultiplier() {
        return this.movementSpeedMultiplier;
    }

    public double getAttackDamage() {
        return this.attackDamage;
    }

    public boolean isLotrDerived() {
        return this.lotrDerived;
    }
}
