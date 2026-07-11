package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-built character-creation options that may depend on server config and
 * the installed LOTR faction catalogue. Stable identifiers only are sent.
 */
public final class CharacterCreationCatalog {

    private final boolean lotrAvailable;
    private final String unavailableReason;
    private final Map<String, List<String>> factionIdsByRace;

    public CharacterCreationCatalog(boolean lotrAvailable,
                                    String unavailableReason,
                                    Map<String, List<String>> factionIdsByRace) {
        this.lotrAvailable = lotrAvailable;
        this.unavailableReason = unavailableReason == null ? "" : unavailableReason;
        LinkedHashMap<String, List<String>> copy =
                new LinkedHashMap<String, List<String>>();
        if (factionIdsByRace != null) {
            for (Map.Entry<String, List<String>> entry : factionIdsByRace.entrySet()) {
                String raceId = CharacterRaceRegistry.normalizeIdentifier(entry.getKey());
                if (CharacterRaceRegistry.get(raceId) == null || copy.containsKey(raceId)) {
                    continue;
                }
                ArrayList<String> factions = new ArrayList<String>();
                if (entry.getValue() != null) {
                    for (String factionId : entry.getValue()) {
                        String normalized = LotrCharacterAdapter.normalizeFactionId(factionId);
                        if (normalized.length() > 0 && !factions.contains(normalized)) {
                            factions.add(normalized);
                        }
                    }
                }
                copy.put(raceId, Collections.unmodifiableList(factions));
            }
        }
        this.factionIdsByRace = Collections.unmodifiableMap(copy);
    }

    public static CharacterCreationCatalog fromServer() {
        LotrCharacterAdapter adapter = LotrCharacterAdapter.getInstance();
        boolean available = adapter.isAvailable();
        LinkedHashMap<String, List<String>> compatible =
                new LinkedHashMap<String, List<String>>();
        for (CharacterRaceDefinition race : CharacterRaceRegistry.getAll()) {
            ArrayList<String> factionIds = new ArrayList<String>();
            if (available) {
                for (String factionId : adapter.getPlayableFactionIds()) {
                    CharacterFactionDefinition faction = adapter.resolve(factionId);
                    if (faction != null && race.isCompatibleWith(faction)) {
                        factionIds.add(faction.getId());
                    }
                }
            }
            compatible.put(race.getId(), factionIds);
        }
        return new CharacterCreationCatalog(
                available,
                available ? "" : adapter.getUnavailableReason(),
                compatible);
    }

    public boolean isLotrAvailable() {
        return this.lotrAvailable;
    }

    public String getUnavailableReason() {
        return this.unavailableReason;
    }

    public Map<String, List<String>> getFactionIdsByRace() {
        return this.factionIdsByRace;
    }

    public List<String> getFactionIds(String raceId) {
        List<String> ids = this.factionIdsByRace.get(
                CharacterRaceRegistry.normalizeIdentifier(raceId));
        return ids == null ? Collections.<String>emptyList() : ids;
    }
}
