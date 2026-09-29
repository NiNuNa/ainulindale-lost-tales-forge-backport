package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.model.CharacterRoster;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;

import com.ninuna.losttales.util.LostTalesIdentifiers;
import java.util.UUID;

/** Immutable client-safe projection of one private roleplaying character. */
public final class CharacterSummary {

    private final UUID characterId;
    private final int slotIndex;
    private final String name;
    private final String raceId;
    private final String genderId;
    private final String skinId;
    private final String bodyTypeId;
    private final String chestTypeId;
    private final boolean showMinecraftCape;
    private final int cosmeticCapeId;
    private final int age;
    private final String factionId;

    /**
     * A body or chest type the registry does not know takes the sex's
     * default. The faction is the character's own: its pledge while it
     * has one, else its starting faction.
     */
    public CharacterSummary(UUID characterId, int slotIndex, String name,
                            String raceId, String genderId, String skinId,
                            boolean showMinecraftCape, int cosmeticCapeId,
                            int age, String factionId, String bodyTypeId,
                            String chestTypeId) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId must not be null");
        }
        this.characterId = characterId;
        this.slotIndex = slotIndex;
        this.name = name == null ? "" : name;
        this.raceId = raceId == null ? "" : raceId;
        this.genderId = genderId == null ? "" : genderId;
        this.skinId = skinId == null ? "" : skinId;
        this.bodyTypeId = CharacterBodyTypeRegistry.contains(bodyTypeId)
                ? LostTalesIdentifiers.normalize(bodyTypeId)
                : CharacterBodyTypeRegistry.defaultFor(genderId);
        this.chestTypeId = CharacterChestTypeRegistry.contains(chestTypeId)
                ? LostTalesIdentifiers.normalize(chestTypeId)
                : CharacterChestTypeRegistry.defaultFor(genderId);
        this.showMinecraftCape = showMinecraftCape;
        this.cosmeticCapeId = CharacterCapeCatalog.normalizeSelection(cosmeticCapeId);
        this.age = age;
        this.factionId = factionId == null ? "" : factionId;
    }

    public static CharacterSummary fromCharacter(RoleplayCharacter character) {
        if (character == null) {
            throw new IllegalArgumentException("character must not be null");
        }
        return new CharacterSummary(
                character.getCharacterId(),
                character.getSlotIndex(),
                character.getName(),
                character.getRaceId(),
                character.getGenderId(),
                character.getSkinId(),
                character.isMinecraftCapeVisible(),
                character.getCosmeticCapeId(),
                character.getAge(),
                character.getFactionId(),
                character.getBodyTypeId(),
                character.getChestTypeId()
        );
    }

    /**
     * Whether this is the account character. The slot says so: the
     * account character is the one outside the nine, which is also what
     * puts it first when the roster is ordered. Nothing else can be
     * stored there — creation and lore claims both refuse the slot, and
     * the codec quarantines a record whose slot and kind disagree — so
     * the slot answers the question the stored kind answers on the
     * server, without a second field on the wire to fall out of step
     * with it.
     */
    public boolean isDefault() {
        return this.slotIndex == CharacterRoster.DEFAULT_SLOT_INDEX;
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public int getSlotIndex() {
        return this.slotIndex;
    }

    public String getName() {
        return this.name;
    }

    public String getRaceId() {
        return this.raceId;
    }

    public String getGenderId() {
        return this.genderId;
    }

    public String getSkinId() {
        return this.skinId;
    }

    public String getBodyTypeId() {
        return this.bodyTypeId;
    }

    public String getChestTypeId() {
        return this.chestTypeId;
    }

    public boolean isMinecraftCapeVisible() {
        return this.showMinecraftCape;
    }

    public int getCosmeticCapeId() {
        return this.cosmeticCapeId;
    }

    public int getAge() {
        return this.age;
    }

    /** The character's faction: its pledge while it has one, else its starting faction. */
    public String getFactionId() {
        return this.factionId;
    }
}
