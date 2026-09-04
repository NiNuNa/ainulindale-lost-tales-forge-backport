package com.ninuna.losttales.party.model;

import com.ninuna.losttales.character.identity.PlayableIdentity;

import java.util.UUID;

/**
 * Who a personal "go here" marker belongs to.
 *
 * <p>Normally the player's active roleplaying character, so the marker
 * travels with that character and is shared with their party. A player who
 * has not made a character yet, or has none selected, still needs somewhere
 * to point on the map, so the marker is owned by the player themselves
 * instead.</p>
 *
 * <p>Both are UUIDs and the store treats the key as opaque, so nothing about
 * the saved format changes; only who a marker can be filed under does. The
 * rule lives here because the server writes markers under it and the client
 * has to look them up under the same one.</p>
 */
public final class PartyPersonalMarkerOwner {

    private PartyPersonalMarkerOwner() {}

    /**
     * @param activeCharacterId the player's active character, or null
     * @param playerId          the player's own account UUID
     * @return the owner id, or null when neither is known
     */
    public static UUID resolve(UUID activeCharacterId, UUID playerId) {
        return PlayableIdentity.gameplayId(activeCharacterId, playerId);
    }
}
