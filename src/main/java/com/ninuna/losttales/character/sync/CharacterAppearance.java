package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
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
    private final boolean showMinecraftCape;
    private final int cosmeticCapeId;

    /** Compatibility constructor for pre-cape callers and previews. */
    public CharacterAppearance(UUID playerId, String raceId,
                               String genderId, String skinId) {
        this(playerId, raceId, genderId, skinId,
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID);
    }

    public CharacterAppearance(UUID playerId, String raceId,
                               String genderId, String skinId,
                               boolean showMinecraftCape, int cosmeticCapeId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId must not be null");
        }
        this.playerId = playerId;
        this.raceId = CharacterRaceRegistry.canonicalizeIdentifier(raceId);
        this.genderId = CharacterGenderRegistry.normalizeIdentifier(genderId);
        this.skinId = CharacterSkinRegistry.normalizeIdentifier(skinId);
        this.showMinecraftCape = showMinecraftCape;
        this.cosmeticCapeId = CharacterCapeCatalog.normalizeSelection(cosmeticCapeId);
    }

    public static CharacterAppearance fromRoster(UUID playerId, CharacterRoster roster) {
        RoleplayCharacter active = roster == null ? null : roster.getActiveCharacter();
        return active == null
                ? removed(playerId)
                : new CharacterAppearance(
                        playerId,
                        active.getRaceId(),
                        active.getGenderId(),
                        active.getSkinId(),
                        active.isMinecraftCapeVisible(),
                        active.getCosmeticCapeId());
    }

    public static CharacterAppearance removed(UUID playerId) {
        return new CharacterAppearance(playerId, "", "", "",
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID);
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

    public boolean isMinecraftCapeVisible() {
        return this.showMinecraftCape;
    }

    public int getCosmeticCapeId() {
        return this.cosmeticCapeId;
    }

    public boolean isPresent() {
        return !this.raceId.isEmpty();
    }
}
