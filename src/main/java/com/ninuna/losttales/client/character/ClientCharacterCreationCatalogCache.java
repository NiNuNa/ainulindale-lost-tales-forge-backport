package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.sync.CharacterCreationCatalog;

/** The choices the server offers a new character (factions, waypoints), as it last sent them. */
public final class ClientCharacterCreationCatalogCache {

    private static CharacterCreationCatalog catalog;

    private ClientCharacterCreationCatalogCache() {}

    public static synchronized void accept(CharacterCreationCatalog value) {
        catalog = value;
    }

    public static synchronized CharacterCreationCatalog get() {
        return catalog;
    }

    public static synchronized void clear() {
        catalog = null;
    }
}
