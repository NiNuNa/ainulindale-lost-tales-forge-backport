package com.ninuna.losttales.character.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent roster owned by one Minecraft account UUID.
 *
 * Mutating methods enforce structural invariants but do not implement gameplay
 * permissions or creation rules. Those belong in the server-side character
 * service introduced in the next implementation stage.
 */
public class CharacterRoster {

    public static final int CURRENT_DATA_VERSION = 1;
    public static final int MAX_SLOTS = 9;
    public static final int INITIAL_UNLOCKED_SLOTS = 1;

    private final UUID ownerId;
    private final Map<Integer, RoleplayCharacter> charactersBySlot = new HashMap<Integer, RoleplayCharacter>();
    private final Map<UUID, RoleplayCharacter> charactersById = new HashMap<UUID, RoleplayCharacter>();

    private int unlockedSlotCount;
    private UUID activeCharacterId;
    private long revision;
    private int dataVersion;

    public CharacterRoster(UUID ownerId) {
        this(ownerId, INITIAL_UNLOCKED_SLOTS, null, 0L, CURRENT_DATA_VERSION);
    }

    public CharacterRoster(UUID ownerId, int unlockedSlotCount, UUID activeCharacterId,
                           long revision, int dataVersion) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        this.ownerId = ownerId;
        this.unlockedSlotCount = clampUnlockedSlotCount(unlockedSlotCount);
        this.activeCharacterId = activeCharacterId;
        this.revision = Math.max(0L, revision);
        this.dataVersion = dataVersion <= 0 ? CURRENT_DATA_VERSION : dataVersion;
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public int getUnlockedSlotCount() {
        return this.unlockedSlotCount;
    }

    public void setUnlockedSlotCount(int unlockedSlotCount) {
        int highestOccupiedSlot = getHighestOccupiedSlot();
        int minimumForExistingCharacters = highestOccupiedSlot < 0
                ? INITIAL_UNLOCKED_SLOTS
                : highestOccupiedSlot + 1;
        this.unlockedSlotCount = clampUnlockedSlotCount(Math.max(unlockedSlotCount, minimumForExistingCharacters));
    }

    public boolean unlockNextSlot() {
        if (this.unlockedSlotCount >= MAX_SLOTS) {
            return false;
        }
        this.unlockedSlotCount++;
        return true;
    }

    public UUID getActiveCharacterId() {
        return this.activeCharacterId;
    }

    public RoleplayCharacter getActiveCharacter() {
        return this.activeCharacterId == null ? null : this.charactersById.get(this.activeCharacterId);
    }

    public void setActiveCharacterId(UUID activeCharacterId) {
        if (activeCharacterId != null && !this.charactersById.containsKey(activeCharacterId)) {
            throw new IllegalArgumentException("active character must belong to the roster");
        }
        this.activeCharacterId = activeCharacterId;
    }

    public void clearInvalidActiveCharacter() {
        if (this.activeCharacterId != null && !this.charactersById.containsKey(this.activeCharacterId)) {
            this.activeCharacterId = null;
        }
    }

    public long getRevision() {
        return this.revision;
    }

    public long incrementRevision() {
        if (this.revision < Long.MAX_VALUE) {
            this.revision++;
        }
        return this.revision;
    }

    public int getDataVersion() {
        return this.dataVersion;
    }

    public int getCharacterCount() {
        return this.charactersById.size();
    }

    public RoleplayCharacter getCharacter(UUID characterId) {
        return characterId == null ? null : this.charactersById.get(characterId);
    }

    public RoleplayCharacter getCharacterAtSlot(int slotIndex) {
        return this.charactersBySlot.get(Integer.valueOf(slotIndex));
    }

    public List<RoleplayCharacter> getCharacters() {
        ArrayList<RoleplayCharacter> characters = new ArrayList<RoleplayCharacter>(this.charactersById.values());
        Collections.sort(characters, new Comparator<RoleplayCharacter>() {
            @Override
            public int compare(RoleplayCharacter left, RoleplayCharacter right) {
                return left.getSlotIndex() - right.getSlotIndex();
            }
        });
        return Collections.unmodifiableList(characters);
    }

    public CharacterSlotState getSlotState(int slotIndex) {
        validateSlotIndex(slotIndex);
        if (slotIndex >= this.unlockedSlotCount) {
            return CharacterSlotState.HIDDEN;
        }
        if (this.charactersBySlot.containsKey(Integer.valueOf(slotIndex))) {
            return CharacterSlotState.OCCUPIED;
        }
        return CharacterSlotState.UNLOCKED;
    }

    public boolean addCharacter(RoleplayCharacter character) {
        if (character == null) {
            throw new IllegalArgumentException("character must not be null");
        }
        if (!this.ownerId.equals(character.getOwnerId())) {
            throw new IllegalArgumentException("character owner does not match roster owner");
        }
        validateSlotIndex(character.getSlotIndex());
        if (this.charactersById.containsKey(character.getCharacterId())) {
            return false;
        }
        if (this.charactersBySlot.containsKey(Integer.valueOf(character.getSlotIndex()))) {
            return false;
        }
        if (this.charactersById.size() >= MAX_SLOTS) {
            return false;
        }

        this.charactersById.put(character.getCharacterId(), character);
        this.charactersBySlot.put(Integer.valueOf(character.getSlotIndex()), character);
        if (character.getSlotIndex() >= this.unlockedSlotCount) {
            this.unlockedSlotCount = character.getSlotIndex() + 1;
        }
        return true;
    }

    public RoleplayCharacter removeCharacter(UUID characterId) {
        RoleplayCharacter removed = this.charactersById.remove(characterId);
        if (removed == null) {
            return null;
        }
        this.charactersBySlot.remove(Integer.valueOf(removed.getSlotIndex()));
        if (characterId.equals(this.activeCharacterId)) {
            this.activeCharacterId = null;
        }
        return removed;
    }

    public static boolean isValidSlotIndex(int slotIndex) {
        return slotIndex >= 0 && slotIndex < MAX_SLOTS;
    }

    private static void validateSlotIndex(int slotIndex) {
        if (!isValidSlotIndex(slotIndex)) {
            throw new IllegalArgumentException("slotIndex must be between 0 and " + (MAX_SLOTS - 1));
        }
    }

    private int getHighestOccupiedSlot() {
        int highest = -1;
        for (Integer slot : this.charactersBySlot.keySet()) {
            if (slot != null && slot.intValue() > highest) {
                highest = slot.intValue();
            }
        }
        return highest;
    }

    private static int clampUnlockedSlotCount(int count) {
        if (count < INITIAL_UNLOCKED_SLOTS) {
            return INITIAL_UNLOCKED_SLOTS;
        }
        return Math.min(MAX_SLOTS, count);
    }
}
