package com.ninuna.losttales.character.registry;

import com.ninuna.losttales.util.LostTalesIdentifiers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One playable race as its race file writes it (CHAR-38): its id and
 * words, the body it stands on, its size, health, speed and attack, its
 * body types, starting factions, skins and starting skills. The code keeps the
 * rules: the eyes stand at {@link #STANDING_EYE_HEIGHT_RATIO} of the
 * height, and the body decides how the race is drawn at its height.
 *
 * <p>A stand-in is a race the server no longer has that a character still
 * names: it is drawn and sized as its body's own race and offered to
 * nobody.</p>
 */
public final class CharacterRaceDefinition {

    /** Every race's eyes stand at this share of its height. */
    public static final float STANDING_EYE_HEIGHT_RATIO = 0.85F;
    /** How far the eyes drop while sneaking. */
    public static final float SNEAKING_EYE_HEIGHT_DROP = 0.08F;

    private final String id;
    private final String name;
    private final CharacterBody body;
    private final String lotrRaceAssociation;
    private final Set<CharacterFactionCategory> allowedFactionCategories;
    private final Set<String> allowedFactionIds;
    private final Set<String> allowedBodyTypeIds;
    private final float width;
    private final float height;
    private final double maxHealth;
    private final double movementSpeedMultiplier;
    private final double attackDamage;
    private final List<CharacterRaceSkins> skins;
    private final Map<String, Integer> startingSkills;
    private final String standInFor;

    public CharacterRaceDefinition(
            String id,
            String name,
            CharacterBody body,
            String lotrRaceAssociation,
            Set<CharacterFactionCategory> allowedFactionCategories,
            Set<String> allowedFactionIds,
            Set<String> allowedBodyTypeIds,
            float width,
            float height,
            double maxHealth,
            double movementSpeedMultiplier,
            double attackDamage,
            List<CharacterRaceSkins> skins,
            Map<String, Integer> startingSkills,
            String standInFor) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.body = body == null ? CharacterBody.HUMAN : body;
        this.lotrRaceAssociation = lotrRaceAssociation == null
                ? "" : lotrRaceAssociation;
        this.allowedFactionCategories = immutableCategories(allowedFactionCategories);
        this.allowedFactionIds = immutableIdentifiers(allowedFactionIds);
        this.allowedBodyTypeIds = immutableIdentifiers(allowedBodyTypeIds);
        this.width = width;
        this.height = height;
        this.maxHealth = maxHealth;
        this.movementSpeedMultiplier = movementSpeedMultiplier;
        this.attackDamage = attackDamage;
        this.skins = skins == null ? Collections.<CharacterRaceSkins>emptyList()
                : Collections.unmodifiableList(new ArrayList<CharacterRaceSkins>(skins));
        this.startingSkills = startingSkills == null
                ? Collections.<String, Integer>emptyMap()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<String, Integer>(startingSkills));
        this.standInFor = standInFor == null ? "" : standInFor;
    }

    /**
     * A stand-in for {@code id}, a race gone from the server, as {@code own},
     * its body's own race. Nothing else of it is left: it has no words of
     * its own and is called by its id.
     */
    public static CharacterRaceDefinition standIn(String id, CharacterRaceDefinition own) {
        return new CharacterRaceDefinition(id, "", own.body, own.lotrRaceAssociation,
                own.allowedFactionCategories, own.allowedFactionIds, own.allowedBodyTypeIds,
                own.width, own.height, own.maxHealth, own.movementSpeedMultiplier,
                own.attackDamage, Collections.<CharacterRaceSkins>emptyList(),
                Collections.<String, Integer>emptyMap(), own.id);
    }

    public String getId() {
        return this.id;
    }

    /** The race's words as a server's file writes them; empty for a bundled race. */
    public String getName() {
        return this.name;
    }

    public CharacterBody getBody() {
        return this.body;
    }

    /** The LOTR people whose health, speed and attack the race takes; empty for its own. */
    public String getLotrRaceAssociation() {
        return this.lotrRaceAssociation;
    }

    public Set<CharacterFactionCategory> getAllowedFactionCategories() {
        return this.allowedFactionCategories;
    }

    public Set<String> getAllowedFactionIds() {
        return this.allowedFactionIds;
    }

    public Set<String> getAllowedBodyTypeIds() {
        return this.allowedBodyTypeIds;
    }

    public boolean isBodyTypeAllowed(String bodyTypeId) {
        return this.allowedBodyTypeIds.contains(
                LostTalesIdentifiers.normalize(bodyTypeId));
    }

    public float getWidth() {
        return this.width;
    }

    public float getHeight() {
        return this.height;
    }

    public float getStandingEyeHeight() {
        return this.height * STANDING_EYE_HEIGHT_RATIO;
    }

    public float getSneakingEyeHeight() {
        return getStandingEyeHeight() - SNEAKING_EYE_HEIGHT_DROP;
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
        return this.body.rendererScale(this.height);
    }

    public float getGuiPreviewScale() {
        return this.body.previewScale(this.height);
    }

    public int getGuiPreviewVerticalOffset() {
        return this.body.getPreviewOffset();
    }

    /** The skin entries in the file's order. */
    public List<CharacterRaceSkins> getSkins() {
        return this.skins;
    }

    /** Each skill the race starts higher in, and its level (PROG-05). */
    public Map<String, Integer> getStartingSkills() {
        return this.startingSkills;
    }

    /** A race gone from the server that a character still names; offered to nobody. */
    public boolean isStandIn() {
        return this.standInFor.length() > 0;
    }

    /** For a stand-in, the race it is drawn and sized as, whose skins it wears; else empty. */
    public String getStandInFor() {
        return this.standInFor;
    }

    public CharacterRaceGameplayProfile createFallbackGameplayProfile() {
        return new CharacterRaceGameplayProfile(
                this.id,
                this.lotrRaceAssociation,
                this.width,
                this.height,
                getStandingEyeHeight(),
                getSneakingEyeHeight(),
                this.maxHealth,
                this.movementSpeedMultiplier,
                this.attackDamage);
    }

    /**
     * Whether a race may start in a faction: a playable faction the race
     * names itself (the Hobbit's two), else one of the race's faction
     * categories.
     */
    public boolean isCompatibleWith(CharacterFactionDefinition faction) {
        if (faction == null || !faction.isPlayable()) {
            return false;
        }
        String factionId = faction.getId();
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
}
