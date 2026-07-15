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
    private final Map<String, List<String>> waypointIdsByFaction;
    private final List<String> allWaypointIds;

    public CharacterCreationCatalog(boolean lotrAvailable,
                                    String unavailableReason,
                                    Map<String, List<String>> factionIdsByRace) {
        this(lotrAvailable, unavailableReason, factionIdsByRace,
                Collections.<String, List<String>>emptyMap(),
                Collections.<String>emptyList());
    }

    public CharacterCreationCatalog(boolean lotrAvailable,
                                    String unavailableReason,
                                    Map<String, List<String>> factionIdsByRace,
                                    Map<String, List<String>> waypointIdsByFaction) {
        this(lotrAvailable, unavailableReason, factionIdsByRace,
                waypointIdsByFaction, null);
    }

    public CharacterCreationCatalog(boolean lotrAvailable,
                                    String unavailableReason,
                                    Map<String, List<String>> factionIdsByRace,
                                    Map<String, List<String>> waypointIdsByFaction,
                                    List<String> allWaypointIds) {
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

        LinkedHashMap<String, List<String>> waypointCopy =
                new LinkedHashMap<String, List<String>>();
        if (waypointIdsByFaction != null) {
            for (Map.Entry<String, List<String>> entry
                    : waypointIdsByFaction.entrySet()) {
                String factionId = LotrCharacterAdapter.normalizeFactionId(entry.getKey());
                if (factionId.length() == 0 || waypointCopy.containsKey(factionId)) {
                    continue;
                }
                ArrayList<String> waypoints = new ArrayList<String>();
                if (entry.getValue() != null) {
                    for (String waypointId : entry.getValue()) {
                        String normalized = LotrCharacterAdapter.normalizeWaypointId(
                                waypointId);
                        if (normalized.length() > 0 && !waypoints.contains(normalized)) {
                            waypoints.add(normalized);
                        }
                    }
                }
                waypointCopy.put(factionId,
                        Collections.unmodifiableList(waypoints));
            }
        }
        this.waypointIdsByFaction = Collections.unmodifiableMap(waypointCopy);

        ArrayList<String> allWaypoints = new ArrayList<String>();
        if (allWaypointIds != null) {
            for (String waypointId : allWaypointIds) {
                String normalized = LotrCharacterAdapter.normalizeWaypointId(waypointId);
                if (normalized.length() > 0 && !allWaypoints.contains(normalized)) {
                    allWaypoints.add(normalized);
                }
            }
        } else {
            for (List<String> factionWaypoints : waypointCopy.values()) {
                for (String waypointId : factionWaypoints) {
                    if (!allWaypoints.contains(waypointId)) {
                        allWaypoints.add(waypointId);
                    }
                }
            }
        }
        Collections.sort(allWaypoints);
        this.allWaypointIds = Collections.unmodifiableList(allWaypoints);
    }

    public static CharacterCreationCatalog fromServer() {
        LotrCharacterAdapter adapter = LotrCharacterAdapter.getInstance();
        boolean available = adapter.isAvailable();
        LinkedHashMap<String, List<String>> compatible =
                new LinkedHashMap<String, List<String>>();
        LinkedHashMap<String, List<String>> waypointsByFaction =
                new LinkedHashMap<String, List<String>>();
        if (available) {
            for (String factionId : adapter.getPlayableFactionIds()) {
                List<String> waypointIds = adapter.getStartingWaypointIds(factionId);
                // Keep empty entries: unconventional mode may pair this
                // faction with a waypoint belonging to another region.
                waypointsByFaction.put(factionId, waypointIds);
            }
        }
        for (CharacterRaceDefinition race : CharacterRaceRegistry.getAll()) {
            ArrayList<String> factionIds = new ArrayList<String>();
            if (available) {
                for (String factionId : adapter.getPlayableFactionIds()) {
                    CharacterFactionDefinition faction = adapter.resolve(factionId);
                    if (faction != null && race.isCompatibleWith(faction)
                            && waypointsByFaction.containsKey(faction.getId())) {
                        factionIds.add(faction.getId());
                    }
                }
            }
            compatible.put(race.getId(), factionIds);
        }
        return new CharacterCreationCatalog(
                available,
                available ? "" : adapter.getUnavailableReason(),
                compatible,
                waypointsByFaction,
                available ? adapter.getAllStartingWaypointIds()
                        : Collections.<String>emptyList());
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

    public Map<String, List<String>> getWaypointIdsByFaction() {
        return this.waypointIdsByFaction;
    }

    public List<String> getWaypointIds(String factionId) {
        List<String> ids = this.waypointIdsByFaction.get(
                LotrCharacterAdapter.normalizeFactionId(factionId));
        return ids == null ? Collections.<String>emptyList() : ids;
    }

    public List<String> getAllPlayableFactionIds() {
        return Collections.unmodifiableList(
                new ArrayList<String>(this.waypointIdsByFaction.keySet()));
    }

    public List<String> getAllWaypointIds() {
        return this.allWaypointIds;
    }
}
