package com.ninuna.losttales.character.registry;

/**
 * One race's numbers as they play: health, speed and attack from the LOTR
 * NPC its file names, else the file's own; size and eye height always from
 * the file, so an NPC that cannot be read never resizes a player.
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

    public CharacterRaceGameplayProfile(
            String raceId,
            String representativeEntityClassName,
            float width,
            float height,
            float standingEyeHeight,
            float sneakingEyeHeight,
            double maxHealth,
            double movementSpeedMultiplier,
            double attackDamage) {
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
}
