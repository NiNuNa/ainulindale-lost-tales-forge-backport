package com.ninuna.losttales.character.registry;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Stable race metadata used only for server-side validation in the initial
 * implementation. Visual models and skins are deliberately outside this type.
 */
public final class CharacterRaceDefinition {

    private final String id;
    private final Set<CharacterFactionCategory> allowedFactionCategories;
    private final Set<String> allowedFactionIds;

    public CharacterRaceDefinition(String id,
                                   Set<CharacterFactionCategory> allowedFactionCategories,
                                   Set<String> allowedFactionIds) {
        if (id == null || id.length() == 0) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
        if (allowedFactionCategories == null || allowedFactionCategories.isEmpty()) {
            this.allowedFactionCategories = Collections.emptySet();
        } else {
            this.allowedFactionCategories = Collections.unmodifiableSet(
                    EnumSet.copyOf(allowedFactionCategories));
        }
        if (allowedFactionIds == null || allowedFactionIds.isEmpty()) {
            this.allowedFactionIds = Collections.emptySet();
        } else {
            this.allowedFactionIds = Collections.unmodifiableSet(
                    new HashSet<String>(allowedFactionIds));
        }
    }

    public String getId() {
        return this.id;
    }

    public boolean isCompatibleWith(CharacterFactionDefinition faction) {
        if (faction == null || !faction.isPlayable()) {
            return false;
        }
        if (this.allowedFactionIds.contains(faction.getId())) {
            return true;
        }
        for (CharacterFactionCategory category : faction.getCategories()) {
            if (this.allowedFactionCategories.contains(category)) {
                return true;
            }
        }
        return false;
    }
}
