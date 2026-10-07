package com.ninuna.losttales.character.model;

import com.ninuna.losttales.character.identity.PlayableIdentity;

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
 * permissions or creation rules; those are the server-side character
 * service's ({@code CharacterService}).
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

    public CharacterRoster(UUID ownerId) {
        this(ownerId, INITIAL_UNLOCKED_SLOTS, null, 0L);
    }

    public CharacterRoster(UUID ownerId, int unlockedSlotCount, UUID activeCharacterId,
                           long revision) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        this.ownerId = ownerId;
        this.unlockedSlotCount = clampUnlockedSlotCount(unlockedSlotCount);
        this.activeCharacterId = activeCharacterId;
        this.revision = Math.max(0L, revision);
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

    /**
     * Opens the next slot when {@code slotIndex}, just filled, was the
     * last open one, so an empty slot is always there to fill until all
     * are open. Making a character and claiming a lore character both
     * fill one. Answers whether a slot opened.
     */
    public boolean unlockNextSlotAfter(int slotIndex) {
        if (slotIndex != this.unlockedSlotCount - 1
                || this.unlockedSlotCount >= MAX_SLOTS) {
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

    /**
     * The id gameplay keys on: the active character's, or the owner's own
     * before the first character is made.
     */
    public UUID getActiveGameplayId() {
        return PlayableIdentity.gameplayId(this.activeCharacterId, this.ownerId);
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

    public RoleplayCharacter getCharacter(UUID characterId) {
        return characterId == null ? null : this.charactersById.get(characterId);
    }

    /** How many of the nine slots are filled. */
    public int characterCount() {
        return this.charactersById.size();
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

    /**
     * Puts a changed copy of a character the roster already holds in its
     * place. The copy keeps the id and the slot — those are what the rest
     * of the world files everything under — and only the record itself
     * changes. Answers false when no such character is here, or when the
     * copy is not the same one.
     */
    public boolean replaceCharacter(RoleplayCharacter character) {
        if (character == null) {
            throw new IllegalArgumentException("character must not be null");
        }
        RoleplayCharacter existing = this.charactersById.get(character.getCharacterId());
        if (existing == null
                || existing.getSlotIndex() != character.getSlotIndex()
                || !existing.getOwnerId().equals(character.getOwnerId())) {
            return false;
        }
        this.charactersById.put(character.getCharacterId(), character);
        this.charactersBySlot.put(
                Integer.valueOf(character.getSlotIndex()), character);
        return true;
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
        if (characterCount() >= MAX_SLOTS) {
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

    /** Whether a slot index names one of the nine places a character stands in. */
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
