package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;

import java.util.UUID;

/** Public rendering projection of an online player's active character. */
public final class CharacterAppearance {

    private final UUID playerId;
    private final String raceId;
    private final String genderId;
    private final String skinId;

    public CharacterAppearance(UUID playerId, String raceId,
                               String genderId, String skinId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId must not be null");
        }
        this.playerId = playerId;
        this.raceId = CharacterRaceRegistry.canonicalizeIdentifier(raceId);
        this.genderId = CharacterGenderRegistry.normalizeIdentifier(genderId);
        this.skinId = CharacterSkinRegistry.normalizeIdentifier(skinId);
    }

    public static CharacterAppearance fromRoster(UUID playerId, CharacterRoster roster) {
        RoleplayCharacter active = roster == null ? null : roster.getActiveCharacter();
        return active == null
                ? removed(playerId)
                : new CharacterAppearance(
                        playerId,
                        active.getRaceId(),
                        active.getGenderId(),
                        active.getSkinId());
    }

    public static CharacterAppearance removed(UUID playerId) {
        return new CharacterAppearance(playerId, "", "", "");
    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    public String getRaceId() {
        return this.raceId;
    }

    public String getGenderId() {
        return this.genderId;
    }

    public String getAppearanceGenderId() {
        return CharacterGenderRegistry.appearanceGender(this.genderId);
    }

    public String getSkinId() {
        return this.skinId;
    }

    public boolean isPresent() {
        return !this.raceId.isEmpty();
    }
}
