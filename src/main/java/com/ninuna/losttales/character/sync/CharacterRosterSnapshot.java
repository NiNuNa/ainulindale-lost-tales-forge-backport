package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.identity.PlayableIdentity;
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
    private final boolean accountShowMinecraftCape;
    private final int accountCosmeticCapeId;
    private final boolean templateTaken;

    public CharacterRosterSnapshot(UUID ownerId, int unlockedSlotCount,
                                   UUID activeCharacterId, long revision,
                                   int dataVersion,
                                   List<CharacterSummary> characters) {
        this(ownerId, unlockedSlotCount, activeCharacterId, revision, dataVersion,
                characters, RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, true);
    }

    public CharacterRosterSnapshot(UUID ownerId, int unlockedSlotCount,
                                   UUID activeCharacterId, long revision,
                                   int dataVersion,
                                   List<CharacterSummary> characters,
                                   boolean accountShowMinecraftCape,
                                   int accountCosmeticCapeId) {
        this(ownerId, unlockedSlotCount, activeCharacterId, revision, dataVersion,
                characters, accountShowMinecraftCape, accountCosmeticCapeId, true);
    }

    public CharacterRosterSnapshot(UUID ownerId, int unlockedSlotCount,
                                   UUID activeCharacterId, long revision,
                                   int dataVersion,
                                   List<CharacterSummary> characters,
                                   boolean accountShowMinecraftCape,
                                   int accountCosmeticCapeId,
                                   boolean templateTaken) {
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
        this.accountShowMinecraftCape = accountShowMinecraftCape;
        this.accountCosmeticCapeId = CharacterCapeCatalog.normalizeSelection(accountCosmeticCapeId);
        this.templateTaken = templateTaken;
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
                summaries,
                roster.isAccountMinecraftCapeVisible(),
                roster.getAccountCosmeticCapeId(),
                roster.isTemplateTaken()
        );
    }

    /**
     * Whether this world has already taken the account's template. A
     * snapshot built without an answer says it has, so nothing older
     * than the flag asks a client to send one.
     */
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

    /** The id gameplay keys on: the active character's, or the owner's own when on the account. */
    public UUID getActiveGameplayId() {
        return PlayableIdentity.gameplayId(this.activeCharacterId, this.ownerId);
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

    /**
     * How many characters the player made. The account's own identity is
     * always there and is not one of them, so this is what answers
     * "has this player made anyone yet".
     */
    public int getRoleplayCharacterCount() {
        int count = 0;
        for (CharacterSummary character : this.characters) {
            if (character != null && !character.isDefault()) {
                count++;
            }
        }
        return count;
    }

    /** The account's own identity, or null on a roster that has none. */
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
