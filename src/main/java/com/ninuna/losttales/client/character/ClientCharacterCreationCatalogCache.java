package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.sync.CharacterCreationCatalog;

/** Client-only copy of the server's validated character-creation options. */
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
