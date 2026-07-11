package com.ninuna.losttales.character.registry;

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
}
