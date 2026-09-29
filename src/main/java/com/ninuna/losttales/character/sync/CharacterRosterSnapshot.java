package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
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

    /** The most deleted characters a snapshot carries: as many as an account may keep. */
    public static final int MAX_DELETED = 128;

    private final UUID ownerId;
    private final int unlockedSlotCount;
    private final UUID activeCharacterId;
    private final long revision;
    private final List<CharacterSummary> characters;
    private final Map<UUID, CharacterSummary> charactersById;
    private final Map<Integer, CharacterSummary> charactersBySlot;
    private final boolean accountShowMinecraftCape;
    private final int accountCosmeticCapeId;
    private final boolean templateTaken;
    private final List<DeletedCharacterSummary> deleted;

    /**
     * {@code deleted} are the owner's deleted characters the server still
     * keeps, soonest purged first; past {@link #MAX_DELETED} the rest are
     * left out.
     */
    public CharacterRosterSnapshot(UUID ownerId, int unlockedSlotCount,
                                   UUID activeCharacterId, long revision,
                                   List<CharacterSummary> characters,
                                   boolean accountShowMinecraftCape,
                                   int accountCosmeticCapeId,
                                   boolean templateTaken,
                                   List<DeletedCharacterSummary> deleted) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        this.ownerId = ownerId;
        this.unlockedSlotCount = Math.max(CharacterRoster.INITIAL_UNLOCKED_SLOTS,
                Math.min(CharacterRoster.MAX_SLOTS, unlockedSlotCount));
        this.revision = Math.max(0L, revision);

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
        this.accountShowMinecraftCape = accountShowMinecraftCape;
        this.accountCosmeticCapeId = CharacterCapeCatalog.normalizeSelection(accountCosmeticCapeId);
        this.templateTaken = templateTaken;
        List<DeletedCharacterSummary> kept = new ArrayList<DeletedCharacterSummary>();
        if (deleted != null) {
            for (DeletedCharacterSummary each : deleted) {
                if (each != null && kept.size() < MAX_DELETED
                        && !byId.containsKey(each.getCharacterId())) {
                    kept.add(each);
                }
            }
        }
        this.deleted = Collections.unmodifiableList(kept);
    }

    public static CharacterRosterSnapshot fromRoster(CharacterRoster roster,
            List<DeletedCharacterSummary> deleted) {
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
                summaries,
                roster.isAccountMinecraftCapeVisible(),
                roster.getAccountCosmeticCapeId(),
                roster.isTemplateTaken(),
                deleted
        );
    }

    /** Whether this world has already taken the account character's look. */
    public boolean isTemplateTaken() {
        return this.templateTaken;
    }

    /** The cape the account wears when played as itself. */
    public boolean isAccountMinecraftCapeVisible() {
        return this.accountShowMinecraftCape;
    }

    public int getAccountCosmeticCapeId() {
        return this.accountCosmeticCapeId;
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

    /** The owner's deleted characters the server still keeps, soonest purged first. */
    public List<DeletedCharacterSummary> getDeleted() {
        return this.deleted;
    }

    public List<CharacterSummary> getCharacters() {
        return this.characters;
    }

    public int getCharacterCount() {
        return this.characters.size();
    }

    /** The account character, or null on a roster that has none. */
    public CharacterSummary getDefaultCharacter() {
        for (CharacterSummary character : this.characters) {
            if (character != null && character.isDefault()) {
                return character;
            }
        }
        return null;
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
