package com.ninuna.losttales.character.kit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A starting kit (CHAR-40): what a new character of a race, a faction or
 * both is given, once, into its own inventory as it is made. A kit that
 * names a race fits only that race, one that names a faction only a
 * character starting in it, and one that names both only a character of
 * both.
 */
public final class StartingKit {
    private final String id;
    private final String raceId;
    private final String factionId;
    private final List<KitItem> items;

    public StartingKit(String id, String raceId, String factionId, List<KitItem> items) {
        this.id = id == null ? "" : id;
        this.raceId = raceId == null ? "" : raceId;
        this.factionId = factionId == null ? "" : factionId;
        this.items = Collections.unmodifiableList(items == null
                ? new ArrayList<KitItem>() : new ArrayList<KitItem>(items));
    }

    public String getId() {
        return this.id;
    }

    /** The race it is for; empty when it names none. */
    public String getRaceId() {
        return this.raceId;
    }

    /** The faction it is for; empty when it names none. */
    public String getFactionId() {
        return this.factionId;
    }

    public List<KitItem> getItems() {
        return this.items;
    }

    /**
     * Whether a character of {@code raceId} starting in {@code factionId}
     * is given this kit: every part the kit names is the character's, and
     * it names at least one.
     */
    public boolean fits(String raceId, String factionId) {
        if (this.raceId.length() == 0 && this.factionId.length() == 0) {
            return false;
        }
        return (this.raceId.length() == 0 || this.raceId.equals(raceId))
                && (this.factionId.length() == 0 || this.factionId.equals(factionId));
    }
}
