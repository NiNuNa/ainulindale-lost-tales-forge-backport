package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;

import java.util.UUID;

/**
 * Public projection of an online player's active identity: what other
 * clients need to render it and to describe it in the chat player card
 * (name, race, gender, body and chest type, starting faction, level, age,
 * biography). The account is an identity too: it wears the account skin
 * on the plain human body, with the arm width its skin declares and the
 * cape settings kept on the roster. Nothing here is private roster state
 * — slots, experience, waypoints, and the switch state stay in
 * {@link CharacterSummary} for the owner only.
 */
public final class CharacterAppearance {
    /** Biography length bound, the same one character creation enforces. */
    public static final int MAX_DESCRIPTION_LENGTH = 256;

    private final CharacterAppearanceKind kind;
    private final UUID playerId;
    /**
     * The stable id of the character this is, or null for the account
     * and for an appearance an older server sent without it. Names are
     * for reading; this is what keys the character across renames.
     */
    private final UUID characterId;
    private final String accountName;
    private final String characterName;
    private final String raceId;
    private final String genderId;
    private final String skinId;
    private final String bodyTypeId;
    private final String chestTypeId;
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

    /** Preview constructor carrying a chosen body type. */
    public CharacterAppearance(UUID playerId, String raceId,
                               String genderId, String skinId, String bodyTypeId) {
        this(playerId, raceId, genderId, skinId, bodyTypeId,
                CharacterChestTypeRegistry.defaultFor(genderId));
    }

    /** Preview constructor carrying chosen body and chest types. */
    public CharacterAppearance(UUID playerId, String raceId,
                               String genderId, String skinId, String bodyTypeId,
                               String chestTypeId) {
        this(playerId, "", "", raceId, genderId, skinId,
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID,
                "", 0, 0, "", bodyTypeId, chestTypeId);
    }

    public CharacterAppearance(UUID playerId, String raceId,
                               String genderId, String skinId,
                               boolean showMinecraftCape, int cosmeticCapeId) {
        this(playerId, "", raceId, genderId, skinId,
                showMinecraftCape, cosmeticCapeId);
    }

    /** Preview projection carrying capes and chosen body and chest types. */
    public static CharacterAppearance preview(UUID playerId, String raceId,
                                              String genderId, String skinId,
                                              String bodyTypeId, String chestTypeId,
                                              boolean showMinecraftCape,
                                              int cosmeticCapeId) {
        return new CharacterAppearance(playerId, "", "", raceId, genderId, skinId,
                showMinecraftCape, cosmeticCapeId, "", 0, 0, "", bodyTypeId, chestTypeId);
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
     * previews and removals carry; the card omits those lines. Body and
     * chest types default from the sex.
     */
    public CharacterAppearance(UUID playerId, String accountName,
                               String characterName,
                               String raceId, String genderId, String skinId,
                               boolean showMinecraftCape, int cosmeticCapeId,
                               String startingFactionId, int roleplayLevel,
                               int age, String description) {
        this(playerId, accountName, characterName, raceId, genderId, skinId,
                showMinecraftCape, cosmeticCapeId, startingFactionId,
                roleplayLevel, age, description,
                CharacterBodyTypeRegistry.defaultFor(genderId));
    }

    /** Chest type defaults from the sex. */
    public CharacterAppearance(UUID playerId, String accountName,
                               String characterName,
                               String raceId, String genderId, String skinId,
                               boolean showMinecraftCape, int cosmeticCapeId,
                               String startingFactionId, int roleplayLevel,
                               int age, String description, String bodyTypeId) {
        this(playerId, accountName, characterName, raceId, genderId, skinId,
                showMinecraftCape, cosmeticCapeId, startingFactionId,
                roleplayLevel, age, description, bodyTypeId,
                CharacterChestTypeRegistry.defaultFor(genderId));
    }

    /**
     * A character projection; one with no race is a removal. The kind
     * follows from the race so every existing caller keeps its meaning.
     */
    public CharacterAppearance(UUID playerId, String accountName,
                               String characterName,
                               String raceId, String genderId, String skinId,
                               boolean showMinecraftCape, int cosmeticCapeId,
                               String startingFactionId, int roleplayLevel,
                               int age, String description, String bodyTypeId,
                               String chestTypeId) {
        this(CharacterAppearanceKind.CHARACTER, playerId, accountName,
                characterName, raceId, genderId, skinId, showMinecraftCape,
                cosmeticCapeId, startingFactionId, roleplayLevel, age,
                description, bodyTypeId, chestTypeId);
    }

    /**
     * The canonical projection. A character kind with no race, and any
     * kind whose race the registry does not know, becomes {@link
     * CharacterAppearanceKind#NONE}: nothing can be drawn from it.
     */
    public CharacterAppearance(CharacterAppearanceKind kind, UUID playerId,
                               String accountName, String characterName,
                               String raceId, String genderId, String skinId,
                               boolean showMinecraftCape, int cosmeticCapeId,
                               String startingFactionId, int roleplayLevel,
                               int age, String description, String bodyTypeId,
                               String chestTypeId) {
        this(kind, playerId, null, accountName, characterName, raceId, genderId,
                skinId, showMinecraftCape, cosmeticCapeId, startingFactionId,
                roleplayLevel, age, description, bodyTypeId, chestTypeId);
    }

    /** The canonical projection with the character's stable id; see {@link #getCharacterId}. */
    public CharacterAppearance(CharacterAppearanceKind kind, UUID playerId,
                               UUID characterId,
                               String accountName, String characterName,
                               String raceId, String genderId, String skinId,
                               boolean showMinecraftCape, int cosmeticCapeId,
                               String startingFactionId, int roleplayLevel,
                               int age, String description, String bodyTypeId,
                               String chestTypeId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId must not be null");
        }
        this.playerId = playerId;
        this.characterId = kind == CharacterAppearanceKind.CHARACTER
                ? characterId : null;
        this.accountName = normalizeName(accountName);
        this.characterName = normalizeName(characterName);
        this.raceId = CharacterRaceRegistry.canonicalizeIdentifier(raceId);
        this.genderId = CharacterGenderRegistry.normalizeIdentifier(genderId);
        this.skinId = CharacterSkinRegistry.normalizeIdentifier(skinId);
        this.bodyTypeId = CharacterBodyTypeRegistry.contains(bodyTypeId)
                ? CharacterBodyTypeRegistry.normalizeIdentifier(bodyTypeId)
                : CharacterBodyTypeRegistry.defaultFor(this.genderId);
        this.chestTypeId = CharacterChestTypeRegistry.contains(chestTypeId)
                ? CharacterChestTypeRegistry.normalizeIdentifier(chestTypeId)
                : CharacterChestTypeRegistry.defaultFor(this.genderId);
        this.showMinecraftCape = showMinecraftCape;
        this.cosmeticCapeId = CharacterCapeCatalog.normalizeSelection(cosmeticCapeId);
        this.startingFactionId = startingFactionId == null
                ? "" : startingFactionId.trim();
        this.roleplayLevel = Math.max(0, roleplayLevel);
        this.age = Math.max(0, age);
        this.description = normalizeDescription(description);
        this.kind = kind == null || this.raceId.isEmpty()
                ? CharacterAppearanceKind.NONE : kind;
    }

    /**
     * The account as an identity: a plain human on the Lost Tales body,
     * wearing the account skin with the arm width the skin declares, and
     * the cape settings the roster keeps for it.
     */
    public static CharacterAppearance forAccount(UUID playerId, String accountName,
                                                 String bodyTypeId,
                                                 boolean showMinecraftCape,
                                                 int cosmeticCapeId) {
        return new CharacterAppearance(CharacterAppearanceKind.ACCOUNT, playerId,
                accountName, "", CharacterRaceRegistry.HUMAN, "",
                CharacterSkinRegistry.ACCOUNT_SKIN_ID, showMinecraftCape,
                cosmeticCapeId, "", 0, 0, "",
                CharacterBodyTypeRegistry.normalizeOrWide(bodyTypeId),
                CharacterChestTypeRegistry.NONE);
    }

    public static CharacterAppearance fromRoster(UUID playerId, CharacterRoster roster) {
        return fromRoster(playerId, "", roster, CharacterBodyTypeRegistry.WIDE);
    }

    public static CharacterAppearance fromRoster(UUID playerId, String accountName,
                                                 CharacterRoster roster) {
        return fromRoster(playerId, accountName, roster, CharacterBodyTypeRegistry.WIDE);
    }

    /**
     * The identity the roster says is being played: its active character,
     * else the account with the arm width its skin declares and the
     * account cape settings the roster keeps. A missing roster is an
     * account that has not been written yet.
     */
    public static CharacterAppearance fromRoster(UUID playerId, String accountName,
                                                 CharacterRoster roster,
                                                 String accountBodyTypeId) {
        RoleplayCharacter active = roster == null ? null : roster.getActiveCharacter();
        return active == null
                ? forAccount(playerId, accountName, accountBodyTypeId,
                        roster == null ? RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE
                                : roster.isAccountMinecraftCapeVisible(),
                        roster == null ? RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID
                                : roster.getAccountCosmeticCapeId())
                : new CharacterAppearance(
                        CharacterAppearanceKind.CHARACTER,
                        playerId,
                        active.getCharacterId(),
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
                        active.getDescription(),
                        active.getBodyTypeId(),
                        active.getChestTypeId());
    }

    /** The same appearance naming the character by its stable id. */
    public CharacterAppearance withCharacterId(UUID characterId) {
        return new CharacterAppearance(this.kind, this.playerId, characterId,
                this.accountName, this.characterName, this.raceId, this.genderId,
                this.skinId, this.showMinecraftCape, this.cosmeticCapeId,
                this.startingFactionId, this.roleplayLevel, this.age,
                this.description, this.bodyTypeId, this.chestTypeId);
    }

    public static CharacterAppearance removed(UUID playerId) {
        return new CharacterAppearance(playerId, "", "", "", "",
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID);
    }

    public CharacterAppearanceKind getKind() {
        return this.kind;
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

    /** Arm width of the body; only bodies that offer a choice read it. */
    public String getBodyTypeId() {
        return this.bodyTypeId;
    }

    /** Chest shape and size; only bodies with a chest variant read it. */
    public String getChestTypeId() {
        return this.chestTypeId;
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

    /** Whether there is an identity to draw at all; false for a removal. */
    public boolean isPresent() {
        return this.kind != CharacterAppearanceKind.NONE;
    }

    /** Whether the identity is a roleplay character rather than the account. */
    public boolean hasCharacter() {
        return this.kind == CharacterAppearanceKind.CHARACTER;
    }

    /**
     * The character's stable id, or null for the account, a removal, and
     * a character an older server described by name alone.
     */
    public UUID getCharacterId() {
        return this.characterId;
    }

    /** Whether the identity is the Minecraft account played as itself. */
    public boolean isAccount() {
        return this.kind == CharacterAppearanceKind.ACCOUNT;
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

    /**
     * Whether the other appearance shows the same thing: every field the
     * same, so a renderer handed this one after the other has nothing to
     * rebuild.
     */
    public boolean sameAs(CharacterAppearance other) {
        return other != null
                && this.kind == other.kind
                && same(this.playerId, other.playerId)
                && same(this.characterId, other.characterId)
                && same(this.accountName, other.accountName)
                && same(this.characterName, other.characterName)
                && same(this.raceId, other.raceId)
                && same(this.genderId, other.genderId)
                && same(this.skinId, other.skinId)
                && same(this.bodyTypeId, other.bodyTypeId)
                && same(this.chestTypeId, other.chestTypeId)
                && this.showMinecraftCape == other.showMinecraftCape
                && this.cosmeticCapeId == other.cosmeticCapeId
                && same(this.startingFactionId, other.startingFactionId)
                && this.roleplayLevel == other.roleplayLevel
                && this.age == other.age
                && same(this.description, other.description);
    }

    private static boolean same(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
