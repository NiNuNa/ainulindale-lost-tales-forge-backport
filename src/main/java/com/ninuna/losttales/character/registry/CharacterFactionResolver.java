package com.ninuna.losttales.character.registry;

/**
 * Resolves stable starting-faction identifiers without exposing third-party
 * mod classes to the character service or persistence model.
 */
public interface CharacterFactionResolver {

    /** True when the backing integration can safely validate new records. */
    boolean isAvailable();

    /**
     * Returns a canonical definition, or null when the identifier is unknown.
     */
    CharacterFactionDefinition resolve(String factionId);

    /**
     * Returns the canonical waypoint ID when it belongs to the faction, or
     * null when it is invalid. A blank request may select a safe server
     * default. With {@code allowAnyRegion}, which the player turns on with
     * the unconventional settings, any public waypoint may be chosen.
     */
    String resolveStartingWaypointId(String factionId, String waypointId,
                                     boolean allowAnyRegion);
}
