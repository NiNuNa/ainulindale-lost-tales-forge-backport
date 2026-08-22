package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;

import java.util.UUID;

/**
 * Public projection of an online player's active character: what other
 * clients need to render it and to describe it in the chat player card
 * (name, race, gender, starting faction, level, age, biography). Nothing
 * here is private roster state — slots, experience, waypoints, and the
 * switch state stay in {@link CharacterSummary} for the owner only.
 */
public final class CharacterAppearance {
    /** Biography length bound, the same one character creation enforces. */
    public static final int MAX_DESCRIPTION_LENGTH = 256;

    private final UUID playerId;
    private final String accountName;
    private final String characterName;
    private final String raceId;
    private final String genderId;
    private final String skinId;
    private final boolean showMinecraftCape;
    private final int cosmeticCapeId;
    private final String startingFactionId;
    private final int roleplayLevel;
    private final int age;
    private final String description;

    /** Compatibility constructor for pre-cape callers and previews. */
    public CharacterAppearance(UUID playerId, String raceId,
                               String genderId, String skinId) {
        this(playerId, "", raceId, genderId, skinId,
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID);
    }

    public CharacterAppearance(UUID playerId, String raceId,
                               String genderId, String skinId,
                               boolean showMinecraftCape, int cosmeticCapeId) {
        this(playerId, "", raceId, genderId, skinId,
                showMinecraftCape, cosmeticCapeId);
    }

    public CharacterAppearance(UUID playerId, String characterName,
                               String raceId, String genderId, String skinId,
                               boolean showMinecraftCape, int cosmeticCapeId) {
        this(playerId, "", characterName, raceId, genderId, skinId,
                showMinecraftCape, cosmeticCapeId);
    }

    /**
     * Full projection including the public Minecraft account name, which
     * lets clients pair a tab-list account with its active character without
     * a second roster sync. Account names are already public on the tab list.
     */
    public CharacterAppearance(UUID playerId, String accountName,
                               String characterName,
                               String raceId, String genderId, String skinId,
                               boolean showMinecraftCape, int cosmeticCapeId) {
        this(playerId, accountName, characterName, raceId, genderId, skinId,
                showMinecraftCape, cosmeticCapeId, "", 0, 0, "");
    }

    /**
     * Full projection including the card details. A level or age of zero
     * and an empty faction or description mean "not known", which is what
     * previews and removals carry; the card omits those lines.
     */
    public CharacterAppearance(UUID playerId, String accountName,
                               String characterName,
                               String raceId, String genderId, String skinId,
                               boolean showMinecraftCape, int cosmeticCapeId,
                               String startingFactionId, int roleplayLevel,
                               int age, String description) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId must not be null");
        }
        this.playerId = playerId;
        this.accountName = normalizeName(accountName);
        this.characterName = normalizeName(characterName);
        this.raceId = CharacterRaceRegistry.canonicalizeIdentifier(raceId);
        this.genderId = CharacterGenderRegistry.normalizeIdentifier(genderId);
        this.skinId = CharacterSkinRegistry.normalizeIdentifier(skinId);
        this.showMinecraftCape = showMinecraftCape;
        this.cosmeticCapeId = CharacterCapeCatalog.normalizeSelection(cosmeticCapeId);
        this.startingFactionId = startingFactionId == null
                ? "" : startingFactionId.trim();
        this.roleplayLevel = Math.max(0, roleplayLevel);
        this.age = Math.max(0, age);
        this.description = normalizeDescription(description);
    }

    public static CharacterAppearance fromRoster(UUID playerId, CharacterRoster roster) {
        return fromRoster(playerId, "", roster);
    }

    public static CharacterAppearance fromRoster(UUID playerId, String accountName,
                                                 CharacterRoster roster) {
        RoleplayCharacter active = roster == null ? null : roster.getActiveCharacter();
        return active == null
                ? removed(playerId)
                : new CharacterAppearance(
                        playerId,
                        accountName,
                        active.getName(),
                        active.getRaceId(),
                        active.getGenderId(),
                        active.getSkinId(),
                        active.isMinecraftCapeVisible(),
                        active.getCosmeticCapeId(),
                        active.getStartingFactionId(),
                        active.getRoleplayLevel(),
                        active.getAge(),
                        active.getDescription());
    }

    public static CharacterAppearance removed(UUID playerId) {
        return new CharacterAppearance(playerId, "", "", "", "",
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID);
    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    /** Public Minecraft account name; empty for removals and previews. */
    public String getAccountName() {
        return this.accountName;
    }

    public String getCharacterName() {
        return this.characterName;
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

    /** Selected starting faction id; empty when unknown. */
    public String getStartingFactionId() {
        return this.startingFactionId;
    }

    /** Roleplay level; 0 when unknown. */
    public int getRoleplayLevel() {
        return this.roleplayLevel;
    }

    /** Character age; 0 when unknown. */
    public int getAge() {
        return this.age;
    }

    /** Player-written biography; empty when none. */
    public String getDescription() {
        return this.description;
    }

    public boolean isPresent() {
        return !this.raceId.isEmpty();
    }

    private static String normalizeName(String value) {
        String name = value == null ? "" : value.trim();
        return name.length() > 64 ? name.substring(0, 64) : name;
    }

    private static String normalizeDescription(String value) {
        String description = value == null ? "" : value.trim();
        return description.length() > MAX_DESCRIPTION_LENGTH
                ? description.substring(0, MAX_DESCRIPTION_LENGTH)
                : description;
    }
}
