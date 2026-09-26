package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.character.CharacterProfilePacket;
import com.ninuna.losttales.network.packet.character.CharacterProfileRequestPacket;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The profiles this client has been sent, by character: its own
 * characters', and those of the people whose card or profile it opened.
 * The server sends one only when asked for, so whatever shows a profile
 * asks for it as it shows it ({@link #want}); an answer stands for a while
 * before the next look asks again. Cleared when the connection ends.
 */
public final class ClientCharacterProfileCache {
    /** The most profiles kept; the one looked at longest ago goes first. */
    private static final int MAX_KEPT = 128;
    /** How long an answer stands before a look asks again. */
    private static final long FRESH_NANOS = 30L * 1000L * 1000L * 1000L;

    private static final LinkedHashMap<UUID, CharacterProfile> PROFILES =
            new LinkedHashMap<UUID, CharacterProfile>(16, 0.75F, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<UUID, CharacterProfile> eldest) {
                    return size() > MAX_KEPT;
                }
            };
    /** The characters the server said this player may not read. */
    private static final Map<UUID, Boolean> UNAVAILABLE =
            new HashMap<UUID, Boolean>();
    /** When each character's profile was last asked for. */
    private static final Map<UUID, Long> ASKED = new HashMap<UUID, Long>();

    private ClientCharacterProfileCache() {}

    /** The profile as last sent, or null while none has been. */
    public static synchronized CharacterProfile get(UUID characterId) {
        return characterId == null ? null : PROFILES.get(characterId);
    }

    /** Whether the server said this player may not read the character's profile. */
    public static synchronized boolean isUnavailable(UUID characterId) {
        return characterId != null && UNAVAILABLE.containsKey(characterId);
    }

    /**
     * Asks for the character's profile unless it was asked for a moment
     * ago: what shows a profile calls this each time it draws one.
     */
    public static void want(UUID characterId) {
        if (characterId == null || !markAsked(characterId, false)) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new CharacterProfileRequestPacket(characterId));
    }

    /** Asks for the character's profile now, as a view opening does. */
    public static void refresh(UUID characterId) {
        if (characterId == null || !markAsked(characterId, true)) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new CharacterProfileRequestPacket(characterId));
    }

    private static synchronized boolean markAsked(UUID characterId,
                                                  boolean force) {
        long now = System.nanoTime();
        Long asked = ASKED.get(characterId);
        if (!force && asked != null && now - asked.longValue() < FRESH_NANOS) {
            return false;
        }
        if (ASKED.size() >= MAX_KEPT * 4) {
            ASKED.clear();
        }
        ASKED.put(characterId, Long.valueOf(now));
        return true;
    }

    public static synchronized void accept(CharacterProfilePacket packet) {
        if (packet == null || packet.getCharacterId() == null) {
            return;
        }
        UUID characterId = packet.getCharacterId();
        if (packet.isAvailable()) {
            UNAVAILABLE.remove(characterId);
            PROFILES.put(characterId, packet.getProfile());
        } else {
            PROFILES.remove(characterId);
            if (UNAVAILABLE.size() >= MAX_KEPT) {
                UNAVAILABLE.clear();
            }
            UNAVAILABLE.put(characterId, Boolean.TRUE);
        }
    }

    public static synchronized void clear() {
        PROFILES.clear();
        UNAVAILABLE.clear();
        ASKED.clear();
    }
}
