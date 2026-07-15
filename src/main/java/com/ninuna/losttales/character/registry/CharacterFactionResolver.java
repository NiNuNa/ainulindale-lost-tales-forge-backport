package com.ninuna.losttales.character.registry;

import java.util.Collections;
import java.util.List;

/**
 * Resolves stable starting-faction identifiers without exposing third-party
 * mod classes to the character service or persistence model.
 */
public interface CharacterFactionResolver {

    /** True when the backing integration can safely validate new records. */
    boolean isAvailable();

    /** Stable diagnostic identifier used for server logging and tests. */
    String getUnavailableReason();

    /**
     * Returns a canonical definition, or null when the identifier is unknown.
     */
    CharacterFactionDefinition resolve(String factionId);

    /** Server-authoritative static waypoint choices for a starting faction. */
    default List<String> getStartingWaypointIds(String factionId) {
        return Collections.emptyList();
    }

    /**
     * Returns the canonical waypoint ID when it belongs to the faction, or
     * null when it is invalid. A blank request may select a safe server default.
     */
    default String resolveStartingWaypointId(String factionId,
                                             String waypointId) {
        return null;
    }

    /** Variant used only when the player explicitly enables cross-region starts. */
    default String resolveStartingWaypointId(String factionId,
                                             String waypointId,
                                             boolean allowAnyRegion) {
        return allowAnyRegion ? null
                : resolveStartingWaypointId(factionId, waypointId);
    }
}
