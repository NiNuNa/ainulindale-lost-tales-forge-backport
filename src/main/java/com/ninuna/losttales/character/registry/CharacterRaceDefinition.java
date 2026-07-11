package com.ninuna.losttales.character.registry;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Immutable common-side metadata for one playable roleplay race.
 *
 * Renderer classes deliberately do not appear here. Client code consumes only
 * the stable renderer scale/preview numbers while the logical server consumes
 * dimensions, eye heights, attributes, and faction validation metadata.
 */
public final class CharacterRaceDefinition {

    private final String id;
    private final String lotrRaceAssociation;
    private final Set<CharacterFactionCategory> allowedFactionCategories;
    private final Set<String> allowedFactionIds;
    private final Set<String> deniedFactionIds;
    private final Set<String> allowedGenderIds;
    private final float width;
    private final float height;
    private final float standingEyeHeight;
    private final float sneakingEyeHeight;
    private final double maxHealth;
    private final double movementSpeedMultiplier;
    private final double attackDamage;
    private final float rendererScale;
    private final float guiPreviewScale;
    private final int guiPreviewVerticalOffset;

    public CharacterRaceDefinition(
            String id,
            String lotrRaceAssociation,
            Set<CharacterFactionCategory> allowedFactionCategories,
            Set<String> allowedFactionIds,
            Set<String> deniedFactionIds,
            Set<String> allowedGenderIds,
            float width,
            float height,
            float standingEyeHeight,
            float sneakingEyeHeight,
            double maxHealth,
            double movementSpeedMultiplier,
            double attackDamage,
            float rendererScale,
            float guiPreviewScale,
            int guiPreviewVerticalOffset) {
        if (id == null || id.length() == 0) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (allowedGenderIds == null || allowedGenderIds.isEmpty()) {
            throw new IllegalArgumentException("allowedGenderIds must not be empty");
        }
        validatePositive("width", width);
        validatePositive("height", height);
        validatePositive("standingEyeHeight", standingEyeHeight);
        validatePositive("sneakingEyeHeight", sneakingEyeHeight);
        validatePositive("maxHealth", maxHealth);
        validatePositive("movementSpeedMultiplier", movementSpeedMultiplier);
        validateNonNegative("attackDamage", attackDamage);
        validatePositive("rendererScale", rendererScale);
        validatePositive("guiPreviewScale", guiPreviewScale);
        if (standingEyeHeight > height || sneakingEyeHeight > standingEyeHeight) {
            throw new IllegalArgumentException("invalid eye heights for " + id);
        }

        this.id = id;
        this.lotrRaceAssociation = lotrRaceAssociation == null
                ? "" : lotrRaceAssociation;
        this.allowedFactionCategories = immutableCategories(allowedFactionCategories);
        this.allowedFactionIds = immutableIdentifiers(allowedFactionIds);
        this.deniedFactionIds = immutableIdentifiers(deniedFactionIds);
        this.allowedGenderIds = immutableIdentifiers(allowedGenderIds);
        this.width = width;
        this.height = height;
        this.standingEyeHeight = standingEyeHeight;
        this.sneakingEyeHeight = sneakingEyeHeight;
        this.maxHealth = maxHealth;
        this.movementSpeedMultiplier = movementSpeedMultiplier;
        this.attackDamage = attackDamage;
        this.rendererScale = rendererScale;
        this.guiPreviewScale = guiPreviewScale;
        this.guiPreviewVerticalOffset = guiPreviewVerticalOffset;
    }

    public String getId() {
        return this.id;
    }

    public String getLotrRaceAssociation() {
        return this.lotrRaceAssociation;
    }

    public Set<String> getAllowedGenderIds() {
        return this.allowedGenderIds;
    }

    public boolean isGenderAllowed(String genderId) {
        return this.allowedGenderIds.contains(
                CharacterGenderRegistry.normalizeIdentifier(genderId));
    }

    public boolean hasGenderedModels() {
        return this.allowedGenderIds.contains(CharacterGenderRegistry.MALE)
                && this.allowedGenderIds.contains(CharacterGenderRegistry.FEMALE);
    }

    public String getDefaultGenderId() {
        return this.allowedGenderIds.iterator().next();
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

    public float getRendererScale() {
        return this.rendererScale;
    }

    public float getGuiPreviewScale() {
        return this.guiPreviewScale;
    }

    public int getGuiPreviewVerticalOffset() {
        return this.guiPreviewVerticalOffset;
    }

    public CharacterRaceGameplayProfile createFallbackGameplayProfile() {
        return new CharacterRaceGameplayProfile(
                this.id,
                this.lotrRaceAssociation,
                this.width,
                this.height,
                this.standingEyeHeight,
                this.sneakingEyeHeight,
                this.maxHealth,
                this.movementSpeedMultiplier,
                this.attackDamage,
                false);
    }

    /**
     * Explicit deny rules win, then explicit allow rules, then category rules.
     * This permits configuration and future race-specific overrides without
     * turning the registry into a hard-coded faction list.
     */
    public boolean isCompatibleWith(CharacterFactionDefinition faction) {
        if (faction == null || !faction.isPlayable()) {
            return false;
        }
        String factionId = faction.getId();
        if (this.deniedFactionIds.contains(factionId)) {
            return false;
        }
        if (this.allowedFactionIds.contains(factionId)) {
            return true;
        }
        for (CharacterFactionCategory category : faction.getCategories()) {
            if (this.allowedFactionCategories.contains(category)) {
                return true;
            }
        }
        return false;
    }

    private static Set<CharacterFactionCategory> immutableCategories(
            Set<CharacterFactionCategory> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    private static Set<String> immutableIdentifiers(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<String>(values));
    }

    private static void validatePositive(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0D) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    private static void validateNonNegative(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be non-negative and finite");
        }
    }
}
