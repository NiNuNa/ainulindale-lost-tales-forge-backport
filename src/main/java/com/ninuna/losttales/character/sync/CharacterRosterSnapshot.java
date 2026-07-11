package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.CharacterSlotState;
import com.ninuna.losttales.character.model.RoleplayCharacter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable private roster snapshot synchronized to the owning client only. */
public final class CharacterRosterSnapshot {

    private final UUID ownerId;
    private final int unlockedSlotCount;
    private final UUID activeCharacterId;
    private final long revision;
    private final int dataVersion;
    private final List<CharacterSummary> characters;
    private final Map<UUID, CharacterSummary> charactersById;
    private final Map<Integer, CharacterSummary> charactersBySlot;

    public CharacterRosterSnapshot(UUID ownerId, int unlockedSlotCount,
                                   UUID activeCharacterId, long revision,
                                   int dataVersion,
                                   List<CharacterSummary> characters) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        this.ownerId = ownerId;
        this.unlockedSlotCount = Math.max(CharacterRoster.INITIAL_UNLOCKED_SLOTS,
                Math.min(CharacterRoster.MAX_SLOTS, unlockedSlotCount));
        this.revision = Math.max(0L, revision);
        this.dataVersion = Math.max(1, dataVersion);

        ArrayList<CharacterSummary> accepted = new ArrayList<CharacterSummary>();
        HashMap<UUID, CharacterSummary> byId = new HashMap<UUID, CharacterSummary>();
        HashMap<Integer, CharacterSummary> bySlot = new HashMap<Integer, CharacterSummary>();
        if (characters != null) {
            for (CharacterSummary character : characters) {
                if (character == null
                        || !CharacterRoster.isValidSlotIndex(character.getSlotIndex())
                        || byId.containsKey(character.getCharacterId())
                        || bySlot.containsKey(Integer.valueOf(character.getSlotIndex()))) {
                    continue;
                }
                accepted.add(character);
                byId.put(character.getCharacterId(), character);
                bySlot.put(Integer.valueOf(character.getSlotIndex()), character);
            }
        }
        Collections.sort(accepted, new Comparator<CharacterSummary>() {
            @Override
            public int compare(CharacterSummary left, CharacterSummary right) {
                return left.getSlotIndex() - right.getSlotIndex();
            }
        });
        this.characters = Collections.unmodifiableList(accepted);
        this.charactersById = Collections.unmodifiableMap(byId);
        this.charactersBySlot = Collections.unmodifiableMap(bySlot);
        this.activeCharacterId = activeCharacterId != null && byId.containsKey(activeCharacterId)
                ? activeCharacterId : null;
    }

    public static CharacterRosterSnapshot fromRoster(CharacterRoster roster) {
        if (roster == null) {
            throw new IllegalArgumentException("roster must not be null");
        }
        ArrayList<CharacterSummary> summaries = new ArrayList<CharacterSummary>();
        for (RoleplayCharacter character : roster.getCharacters()) {
            summaries.add(CharacterSummary.fromCharacter(character));
        }
        return new CharacterRosterSnapshot(
                roster.getOwnerId(),
                roster.getUnlockedSlotCount(),
                roster.getActiveCharacterId(),
                roster.getRevision(),
                roster.getDataVersion(),
                summaries
        );
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public int getUnlockedSlotCount() {
        return this.unlockedSlotCount;
    }

    public UUID getActiveCharacterId() {
        return this.activeCharacterId;
    }

    public CharacterSummary getActiveCharacter() {
        return getCharacter(this.activeCharacterId);
    }

    public long getRevision() {
        return this.revision;
    }

    public int getDataVersion() {
        return this.dataVersion;
    }

    public List<CharacterSummary> getCharacters() {
        return this.characters;
    }

    public int getCharacterCount() {
        return this.characters.size();
    }

    public CharacterSummary getCharacter(UUID characterId) {
        return characterId == null ? null : this.charactersById.get(characterId);
    }

    public CharacterSummary getCharacterAtSlot(int slotIndex) {
        return this.charactersBySlot.get(Integer.valueOf(slotIndex));
    }

    public CharacterSlotState getSlotState(int slotIndex) {
        if (!CharacterRoster.isValidSlotIndex(slotIndex)) {
            throw new IllegalArgumentException("slotIndex must be between 0 and " + (CharacterRoster.MAX_SLOTS - 1));
        }
        if (slotIndex >= this.unlockedSlotCount) {
            return CharacterSlotState.HIDDEN;
        }
        return this.charactersBySlot.containsKey(Integer.valueOf(slotIndex))
                ? CharacterSlotState.OCCUPIED : CharacterSlotState.UNLOCKED;
    }
}
