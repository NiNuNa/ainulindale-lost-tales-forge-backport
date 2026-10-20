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
 * asks for it as it shows it ({@link #want}), and a profile kept is not
 * asked for again; a view opening asks afresh ({@link #refresh}). The
 * server sends the owner a profile again once it is saved. Cleared when
 * the connection ends.
 */
public final class ClientCharacterProfileCache {
    /** The most profiles kept; the one looked at longest ago goes first. */
    private static final int MAX_KEPT = 128;
    /** How long a question waits for its answer before a look asks again. */
    private static final long ANSWER_NANOS = 5L * 1000L * 1000L * 1000L;

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

    /** Whether the server said there is no such character to read the profile of. */
    public static synchronized boolean isUnavailable(UUID characterId) {
        return characterId != null && UNAVAILABLE.containsKey(characterId);
    }

    /**
     * Asks for the character's profile while none is kept and no question
     * is waiting for its answer: what shows a profile calls this each time
     * it draws one.
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

    /** Whether to ask now, noting the question when so; {@code force} asks whatever is kept. */
    static synchronized boolean markAsked(UUID characterId, boolean force) {
        long now = System.nanoTime();
        if (!force && (PROFILES.containsKey(characterId)
                || UNAVAILABLE.containsKey(characterId))) {
            return false;
        }
        Long asked = ASKED.get(characterId);
        if (!force && asked != null && now - asked.longValue() < ANSWER_NANOS) {
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
