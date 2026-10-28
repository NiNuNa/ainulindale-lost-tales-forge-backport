package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.lore.sync.LoreCharacterSnapshot;
import com.ninuna.losttales.character.lore.sync.LoreCharacterSummary;

import java.util.UUID;

/** The lore figures, who holds each and which are yours, as the server last sent them. */
public final class ClientLoreCharacterCache {

    private static LoreCharacterSnapshot snapshot;

    private ClientLoreCharacterCache() {}

    public static synchronized void accept(LoreCharacterSnapshot incoming) {
        snapshot = incoming;
    }

    public static synchronized LoreCharacterSnapshot getSnapshot() {
        return snapshot;
    }

    public static synchronized LoreCharacterSummary findOwnedCharacter(
            UUID characterId) {
        if (snapshot == null || characterId == null) return null;
        for (LoreCharacterSummary summary : snapshot.getCharacters()) {
            if (characterId.equals(summary.getOwnedCharacterId())) return summary;
        }
        return null;
    }

    public static synchronized void clear() { snapshot = null; }
}
