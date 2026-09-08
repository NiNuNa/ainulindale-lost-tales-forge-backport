package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.server.CharacterCreationRequest;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.validation.CharacterValidator;

import java.util.UUID;

/**
 * What an account wants its default character to start as: the same
 * choices the creation form asks for, kept on this installation for the
 * account that made them.
 *
 * <p>It is a starting point and never an answer. A server decides which
 * races, factions and starting waypoints it offers and owns every rule
 * about them, so a template is read as the form's opening values and the
 * player confirms them there; nothing here crosses the wire, and nothing
 * a server is asked for is decided by it.</p>
 *
 * <p>Every field is stored as the player left it, unvalidated beyond the
 * bounds a string has to respect to be written at all. What a particular
 * server will accept is that server's to say, and is asked when the form
 * opens against it.</p>
 */
public final class CharacterTemplate {

    /** A template with nothing chosen; what an account starts with. */
    public static final CharacterTemplate EMPTY = new CharacterTemplate(
            "", "", "", "", "", "", "", "", 0, false);

    private final String name;
    private final String raceId;
    private final String genderId;
    private final String skinId;
    private final String bodyTypeId;
    private final String chestTypeId;
    private final String startingFactionId;
    private final String description;
    private final int age;
    private final boolean unconventionalSettings;
    private final boolean showMinecraftCape;
    private final int cosmeticCapeId;

    /** The Minecraft cape shown and no cosmetic cape, as a character starts unless asked otherwise. */
    public CharacterTemplate(String name, String raceId, String genderId,
                             String skinId, String bodyTypeId,
                             String chestTypeId, String startingFactionId,
                             String description, int age,
                             boolean unconventionalSettings) {
        this(name, raceId, genderId, skinId, bodyTypeId, chestTypeId,
                startingFactionId, description, age, unconventionalSettings,
                true, 0);
    }

    public CharacterTemplate(String name, String raceId, String genderId,
                             String skinId, String bodyTypeId,
                             String chestTypeId, String startingFactionId,
                             String description, int age,
                             boolean unconventionalSettings,
                             boolean showMinecraftCape, int cosmeticCapeId) {
        this.showMinecraftCape = showMinecraftCape;
        this.cosmeticCapeId = Math.max(0, cosmeticCapeId);
        this.name = trimmed(name);
        this.raceId = trimmed(raceId);
        this.genderId = trimmed(genderId);
        this.skinId = trimmed(skinId);
        this.bodyTypeId = trimmed(bodyTypeId);
        this.chestTypeId = trimmed(chestTypeId);
        this.startingFactionId = trimmed(startingFactionId);
        this.description = trimmed(description);
        this.age = Math.max(0, age);
        this.unconventionalSettings = unconventionalSettings;
    }

    /**
     * The template a creation the player just made would be remembered
     * as. The slot and the roster revision are a particular world's and
     * are not part of it; neither is the starting waypoint, which every
     * server resolves against its own map.
     */
    public static CharacterTemplate of(CharacterCreationRequest request) {
        if (request == null) {
            return EMPTY;
        }
        return new CharacterTemplate(request.getName(), request.getRaceId(),
                request.getGenderId(), request.getSkinId(),
                request.getBodyTypeId(), request.getChestTypeId(),
                request.getStartingFactionId(), request.getDescription(),
                request.getAge(), request.hasUnconventionalSettings(),
                request.isMinecraftCapeVisible(), request.getCosmeticCapeId());
    }

    public String getName() { return this.name; }
    public String getRaceId() { return this.raceId; }
    public String getGenderId() { return this.genderId; }
    public String getSkinId() { return this.skinId; }
    public String getBodyTypeId() { return this.bodyTypeId; }
    public String getChestTypeId() { return this.chestTypeId; }
    public String getStartingFactionId() { return this.startingFactionId; }
    public String getDescription() { return this.description; }
    public int getAge() { return this.age; }
    public boolean hasUnconventionalSettings() {
        return this.unconventionalSettings;
    }

    /** Whether the account's own Minecraft cape is worn when no cosmetic cape is. */
    public boolean isMinecraftCapeVisible() { return this.showMinecraftCape; }

    /** The cosmetic cape's catalogue id; zero for none. */
    public int getCosmeticCapeId() { return this.cosmeticCapeId; }

    /** Whether anything at all has been chosen. */
    public boolean isEmpty() {
        return this.name.length() == 0 && this.raceId.length() == 0
                && this.genderId.length() == 0 && this.skinId.length() == 0
                && this.bodyTypeId.length() == 0
                && this.chestTypeId.length() == 0
                && this.startingFactionId.length() == 0
                && this.description.length() == 0 && this.age == 0
                && this.cosmeticCapeId == 0;
    }

    /**
     * Whether the name is long enough and short enough to be worth
     * offering. Which characters a name may hold, and everything else
     * about it, is the server's to answer when the form is submitted;
     * this only decides whether the account has a template worth
     * filling a form in from.
     */
    public boolean hasUsableName() {
        int length = CharacterValidator.normalizeName(this.name).length();
        return length >= CharacterValidator.MIN_NAME_LENGTH
                && length <= CharacterValidator.MAX_NAME_LENGTH;
    }

    /**
     * Whether this is a character the account can start playing as: a
     * usable name and a race, sex and skin all chosen. The main menu
     * opens the play controls only once this holds, and the character
     * room offers them on the same rule. A template the creator saved
     * always satisfies it; one edited by hand may not.
     */
    public boolean isSetUp() {
        return hasUsableName() && this.raceId.length() > 0
                && this.genderId.length() > 0 && this.skinId.length() > 0;
    }

    /**
     * The template as an appearance the body model can be built from,
     * for that account. A choice not made resolves to what a world would
     * make the account's default character as: a human of the account's
     * own skin.
     */
    public CharacterAppearance toAppearance(UUID accountId) {
        String race = this.raceId.length() > 0
                ? this.raceId : CharacterRaceRegistry.HUMAN;
        String gender = this.genderId.length() > 0
                ? this.genderId
                : CharacterRaceRegistry.normalizeGenderForRace(
                        race, CharacterGenderRegistry.MALE);
        String skin = this.skinId.length() > 0
                ? this.skinId
                : CharacterSkinRegistry.getDefaultSkinId(race, gender, accountId);
        return new CharacterAppearance(accountId, race, gender, skin,
                this.bodyTypeId, this.chestTypeId);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CharacterTemplate)) {
            return false;
        }
        CharacterTemplate that = (CharacterTemplate) other;
        return this.age == that.age
                && this.unconventionalSettings == that.unconventionalSettings
                && this.showMinecraftCape == that.showMinecraftCape
                && this.cosmeticCapeId == that.cosmeticCapeId
                && this.name.equals(that.name)
                && this.raceId.equals(that.raceId)
                && this.genderId.equals(that.genderId)
                && this.skinId.equals(that.skinId)
                && this.bodyTypeId.equals(that.bodyTypeId)
                && this.chestTypeId.equals(that.chestTypeId)
                && this.startingFactionId.equals(that.startingFactionId)
                && this.description.equals(that.description);
    }

    @Override
    public int hashCode() {
        int result = this.name.hashCode();
        result = 31 * result + this.raceId.hashCode();
        result = 31 * result + this.genderId.hashCode();
        result = 31 * result + this.skinId.hashCode();
        result = 31 * result + this.bodyTypeId.hashCode();
        result = 31 * result + this.chestTypeId.hashCode();
        result = 31 * result + this.startingFactionId.hashCode();
        result = 31 * result + this.description.hashCode();
        result = 31 * result + this.age;
        result = 31 * result + this.cosmeticCapeId;
        result = 31 * result + (this.showMinecraftCape ? 1 : 0);
        return 31 * result + (this.unconventionalSettings ? 1 : 0);
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
