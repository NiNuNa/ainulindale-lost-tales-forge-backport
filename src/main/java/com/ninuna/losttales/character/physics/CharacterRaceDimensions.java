package com.ninuna.losttales.character.physics;

import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;

/**
 * Immutable common-side measurements for one active player race.
 *
 * Heights in this class are absolute distances above the base of the player's
 * bounding box. They are not EntityPlayer#eyeHeight field values and they are
 * not world-space Y coordinates. Keeping that distinction in one type avoids
 * repeating the unusual Forge 1.7.10 player-position conversion at call sites.
 */
public final class CharacterRaceDimensions {

    private static final float MINIMUM_PHYSICAL_SIZE = 0.1F;
    private static final float DEFAULT_MODEL_SCALE = 1.0F;

    private final String raceId;
    private final float width;
    private final float height;
    private final float standingEyeHeight;
    private final float sneakingEyeHeight;
    private final float modelScale;
    private final boolean lotrDerived;

    private CharacterRaceDimensions(
            String raceId,
            float width,
            float height,
            float standingEyeHeight,
            float sneakingEyeHeight,
            float modelScale,
            boolean lotrDerived) {
        this.raceId = CharacterRaceRegistry.canonicalizeIdentifier(raceId);
        this.width = width;
        this.height = height;
        this.standingEyeHeight = standingEyeHeight;
        this.sneakingEyeHeight = sneakingEyeHeight;
        this.modelScale = modelScale;
        this.lotrDerived = lotrDerived;
    }

    /**
     * Creates a sanitized measurement snapshot from the resolved gameplay
     * profile. Valid profiles retain their values exactly.
     */
    public static CharacterRaceDimensions fromProfile(
            String raceId, CharacterRaceGameplayProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }

        String canonicalRaceId = CharacterRaceRegistry.canonicalizeIdentifier(raceId);
        float width = positiveOr(profile.getWidth(), MINIMUM_PHYSICAL_SIZE);
        float height = positiveOr(profile.getHeight(), MINIMUM_PHYSICAL_SIZE);
        float standingEyeHeight = clampEyeHeight(
                profile.getStandingEyeHeight(), height, height);
        float sneakingEyeHeight = clampEyeHeight(
                profile.getSneakingEyeHeight(), standingEyeHeight,
                standingEyeHeight);

        CharacterRaceDefinition definition =
                CharacterRaceRegistry.get(canonicalRaceId);
        float modelScale = definition == null
                ? DEFAULT_MODEL_SCALE
                : positiveOr(definition.getRendererScale(), DEFAULT_MODEL_SCALE);

        return new CharacterRaceDimensions(
                canonicalRaceId,
                width,
                height,
                standingEyeHeight,
                sneakingEyeHeight,
                modelScale,
                profile.isLotrDerived());
    }

    /**
     * Reconstructs a snapshot from locally synchronized entity data. Invalid
     * transient values fall back to the already validated supplied snapshot.
     */
    static CharacterRaceDimensions fromEntityData(
            String raceId,
            float width,
            float height,
            float standingEyeHeight,
            float sneakingEyeHeight,
            boolean lotrDerived,
            CharacterRaceDimensions fallback) {
        if (fallback == null) {
            throw new IllegalArgumentException("fallback must not be null");
        }

        String canonicalRaceId = CharacterRaceRegistry.canonicalizeIdentifier(raceId);
        float safeWidth = positiveOr(width, fallback.getWidth());
        float safeHeight = positiveOr(height, fallback.getHeight());
        float safeStandingEyeHeight = clampEyeHeight(
                standingEyeHeight, safeHeight,
                Math.min(fallback.getStandingEyeHeight(), safeHeight));
        float safeSneakingEyeHeight = clampEyeHeight(
                sneakingEyeHeight, safeStandingEyeHeight,
                Math.min(fallback.getSneakingEyeHeight(), safeStandingEyeHeight));

        CharacterRaceDefinition definition =
                CharacterRaceRegistry.get(canonicalRaceId);
        float modelScale = definition == null
                ? fallback.getModelScale()
                : positiveOr(definition.getRendererScale(), fallback.getModelScale());

        return new CharacterRaceDimensions(
                canonicalRaceId,
                safeWidth,
                safeHeight,
                safeStandingEyeHeight,
                safeSneakingEyeHeight,
                modelScale,
                lotrDerived);
    }

    public String getRaceId() {
        return this.raceId;
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

    public float getEyeHeight(boolean sneaking) {
        return sneaking ? this.sneakingEyeHeight : this.standingEyeHeight;
    }

    /**
     * Projectile constructors commonly begin from the shooter's eye reference,
     * then apply their own forward/downward offsets. This named accessor is kept
     * separate so Stage 2 can specialize projectile origins without making
     * unrelated camera or item-drop code depend on that policy.
     */
    public float getProjectileEyeReferenceHeight(boolean sneaking) {
        return getEyeHeight(sneaking);
    }

    /**
     * Item-drop code also starts from an eye-relative reference in vanilla
     * 1.7.10. It intentionally has a separate accessor from projectiles because
     * the final drop offset is a different gameplay policy.
     */
    public float getItemDropEyeReferenceHeight(boolean sneaking) {
        return getEyeHeight(sneaking);
    }

    /** Common renderer scale metadata; no client-only class is referenced. */
    public float getModelScale() {
        return this.modelScale;
    }

    public float getSneakingEyeDrop() {
        return this.standingEyeHeight - this.sneakingEyeHeight;
    }

    public boolean isLotrDerived() {
        return this.lotrDerived;
    }

    private static float positiveOr(float value, float fallback) {
        return isPositiveFinite(value) ? value : fallback;
    }

    private static float clampEyeHeight(
            float value, float maximum, float fallback) {
        float safeMaximum = Math.max(MINIMUM_PHYSICAL_SIZE, maximum);
        float safeFallback = Math.max(
                MINIMUM_PHYSICAL_SIZE, Math.min(fallback, safeMaximum));
        if (!isPositiveFinite(value)) {
            return safeFallback;
        }
        return Math.max(MINIMUM_PHYSICAL_SIZE, Math.min(value, safeMaximum));
    }

    private static boolean isPositiveFinite(float value) {
        return !Float.isNaN(value)
                && !Float.isInfinite(value)
                && value > 0.0F;
    }
}
