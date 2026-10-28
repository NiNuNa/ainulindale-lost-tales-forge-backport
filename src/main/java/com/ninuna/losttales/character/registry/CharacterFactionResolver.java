package com.ninuna.losttales.character.registry;

/**
 * The starting factions and waypoints by id, so the character code needs
 * no LOTR class.
 */
public interface CharacterFactionResolver {

    /** Whether LOTR can be asked, so a new character can be checked. */
    boolean isAvailable();

    /** The faction of that id; null when there is none. */
    CharacterFactionDefinition resolve(String factionId);

    /**
     * The waypoint's id when a character of that faction may start there,
     * else null. An empty request takes the faction's first. With
     * {@code allowAnyRegion} (the Unconventional settings) any public
     * waypoint may be chosen.
     */
    String resolveStartingWaypointId(String factionId, String waypointId,
                                     boolean allowAnyRegion);
}
