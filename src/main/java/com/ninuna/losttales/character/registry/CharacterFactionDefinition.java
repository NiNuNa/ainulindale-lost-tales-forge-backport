package com.ninuna.losttales.character.registry;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Server-side projection of a starting faction supplied by an integration
 * adapter. Only stable identifiers and validation metadata enter core code.
 */
public final class CharacterFactionDefinition {

    private final String id;
    private final boolean playable;
    private final Set<CharacterFactionCategory> categories;

    public CharacterFactionDefinition(String id, boolean playable,
                                      Set<CharacterFactionCategory> categories) {
        if (id == null || id.length() == 0) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
        this.playable = playable;
        if (categories == null || categories.isEmpty()) {
            this.categories = Collections.emptySet();
        } else {
            this.categories = Collections.unmodifiableSet(EnumSet.copyOf(categories));
        }
    }

    public String getId() {
        return this.id;
    }

    public boolean isPlayable() {
        return this.playable;
    }

    public Set<CharacterFactionCategory> getCategories() {
        return this.categories;
    }

    public boolean hasCategory(CharacterFactionCategory category) {
        return category != null && this.categories.contains(category);
    }
}
