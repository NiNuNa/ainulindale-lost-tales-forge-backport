package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only cache of public active-character rendering information. */
public final class ClientCharacterAppearanceCache {

    private static final Map<UUID, CharacterAppearance> APPEARANCES =
            new HashMap<UUID, CharacterAppearance>();
    private static final Map<UUID, CharacterAppearance> PREVIEW_APPEARANCES =
            new HashMap<UUID, CharacterAppearance>();

    private ClientCharacterAppearanceCache() {}

    public static synchronized void replaceAll(Collection<CharacterAppearance> appearances) {
        APPEARANCES.clear();
        apply(appearances);
    }

    public static synchronized void apply(Collection<CharacterAppearance> appearances) {
        if (appearances == null) {
            return;
        }
        for (CharacterAppearance appearance : appearances) {
            if (appearance == null) {
                continue;
            }
            if (appearance.isPresent()) {
                APPEARANCES.put(appearance.getPlayerId(), appearance);
            } else {
                APPEARANCES.remove(appearance.getPlayerId());
            }
        }
    }

    public static synchronized CharacterAppearance get(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        CharacterAppearance preview = PREVIEW_APPEARANCES.get(playerId);
        return preview == null ? getAuthoritative(playerId) : preview;
    }

    /**
     * Returns synchronized state only. GUI preview overrides must never alter
     * collision, camera, eye height, targeting, or other gameplay physics.
     */
    public static synchronized CharacterAppearance getAuthoritative(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        CharacterAppearance appearance = APPEARANCES.get(playerId);
        if (appearance != null) {
            return appearance;
        }

        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot != null && playerId.equals(snapshot.getOwnerId())) {
            CharacterSummary active = snapshot.getActiveCharacter();
            if (active != null) {
                return new CharacterAppearance(
                        playerId,
                        active.getName(),
                        active.getRaceId(),
                        active.getGenderId(),
                        active.getSkinId(),
                        active.isMinecraftCapeVisible(),
                        active.getCosmeticCapeId());
            }
        }
        return null;
    }

    public static synchronized void setPreview(CharacterAppearance appearance) {
        if (appearance != null && appearance.isPresent()) {
            PREVIEW_APPEARANCES.put(appearance.getPlayerId(), appearance);
        }
    }

    public static synchronized void clearPreview(UUID playerId) {
        if (playerId != null) {
            PREVIEW_APPEARANCES.remove(playerId);
        }
    }

    public static synchronized Map<UUID, CharacterAppearance> snapshot() {
        return Collections.unmodifiableMap(
                new HashMap<UUID, CharacterAppearance>(APPEARANCES));
    }

    public static synchronized void clear() {
        APPEARANCES.clear();
        PREVIEW_APPEARANCES.clear();
    }
}
