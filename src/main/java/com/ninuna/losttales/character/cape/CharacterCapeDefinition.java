package com.ninuna.losttales.character.cape;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One cape the creator offers (CHAR-39), as its file writes it: its id,
 * its picture, the races and factions that may wear it (none named: every
 * character), and for a server's own cape the words it is shown by. A
 * bundled cape's words are the lang line
 * {@code gui.losttales.character.cape.<path>}.
 */
public final class CharacterCapeDefinition {

    private final String id;
    private final String name;
    private final String picture;
    private final Set<String> races;
    private final Set<String> factions;

    public CharacterCapeDefinition(String id, String name, String picture,
                                   Set<String> races, Set<String> factions) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.picture = picture == null ? "" : picture;
        this.races = races == null ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<String>(races));
        this.factions = factions == null ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<String>(factions));
    }

    public String getId() {
        return this.id;
    }

    /** The cape's words as a server's file writes them; empty for a bundled cape. */
    public String getName() {
        return this.name;
    }

    /** The lang key of a bundled cape's words. */
    public String getTranslationKey() {
        int colon = this.id.indexOf(':');
        return "gui.losttales.character.cape." + (colon < 0 ? this.id : this.id.substring(colon + 1));
    }

    /** LOTR's cape by its name ({@code lotr:GONDOR}), or a picture of the mod's own. */
    public String getPicture() {
        return this.picture;
    }

    /** The races that may wear it; empty for every race. */
    public Set<String> getRaces() {
        return this.races;
    }

    /** The factions that may wear it; empty for every faction. */
    public Set<String> getFactions() {
        return this.factions;
    }

    /**
     * Whether a character of {@code raceId} in {@code factionId} may wear
     * it: each list the cape names has to hold the character's.
     */
    public boolean mayWear(String raceId, String factionId) {
        return (this.races.isEmpty() || this.races.contains(raceId))
                && (this.factions.isEmpty() || this.factions.contains(factionId));
    }
}
