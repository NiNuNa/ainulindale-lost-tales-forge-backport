package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every character in the world by id, built once from the rosters and
 * kept by {@link CharacterWorldData} until a roster is written again. A
 * character id found in more than one roster is ambiguous and answers to
 * nobody, since the id is what parties, markers and bounties key on.
 */
public final class CharacterIndex {

    private final Map<UUID, RoleplayCharacter> characters;
    private final Map<UUID, CharacterRoster> rostersByCharacter;
    private final Set<UUID> ambiguousCharacterIds;
    /**
     * The players who own a roster. A personal marker may be filed under
     * one of these instead of under a character, because a player who has
     * no character selected owns their marker themselves.
     */
    private final Set<UUID> rosterOwnerIds;

    private CharacterIndex(Map<UUID, RoleplayCharacter> characters,
                           Map<UUID, CharacterRoster> rostersByCharacter,
                           Set<UUID> ambiguousCharacterIds,
                           Set<UUID> rosterOwnerIds) {
        this.characters = Collections.unmodifiableMap(characters);
        this.rostersByCharacter = Collections.unmodifiableMap(rostersByCharacter);
        this.ambiguousCharacterIds = Collections.unmodifiableSet(ambiguousCharacterIds);
        this.rosterOwnerIds = Collections.unmodifiableSet(rosterOwnerIds);
    }

    static CharacterIndex build(Collection<CharacterRoster> rosters) {
        Map<UUID, RoleplayCharacter> characters = new HashMap<UUID, RoleplayCharacter>();
        Map<UUID, CharacterRoster> rostersByCharacter = new HashMap<UUID, CharacterRoster>();
        Set<UUID> ambiguous = new HashSet<UUID>();
        Set<UUID> rosterOwners = new HashSet<UUID>();
        for (CharacterRoster roster : rosters) {
            if (roster == null) {
                continue;
            }
            if (roster.getOwnerId() != null) {
                rosterOwners.add(roster.getOwnerId());
            }
            for (RoleplayCharacter character : roster.getCharacters()) {
                UUID characterId = character.getCharacterId();
                if (ambiguous.contains(characterId)) {
                    continue;
                }
                if (characters.containsKey(characterId)) {
                    characters.remove(characterId);
                    rostersByCharacter.remove(characterId);
                    ambiguous.add(characterId);
                } else {
                    characters.put(characterId, character);
                    rostersByCharacter.put(characterId, roster);
                }
            }
        }
        return new CharacterIndex(characters, rostersByCharacter, ambiguous, rosterOwners);
    }

    /** The character with this id, or null when there is none or more than one. */
    public RoleplayCharacter find(UUID characterId) {
        return characterId == null ? null : this.characters.get(characterId);
    }

    /** The roster holding the character, or null as {@link #find}. */
    public CharacterRoster rosterOf(UUID characterId) {
        return characterId == null ? null : this.rostersByCharacter.get(characterId);
    }

    /** Whether the id is held by more than one roster. */
    public boolean isAmbiguous(UUID characterId) {
        return characterId != null && this.ambiguousCharacterIds.contains(characterId);
    }

    /** Whether any roster holds the id at all, ambiguous ones included. */
    public boolean contains(UUID characterId) {
        return find(characterId) != null || isAmbiguous(characterId);
    }

    /** How many rosters hold the id: zero, one, or "more than one" as two. */
    public int countOf(UUID characterId) {
        return isAmbiguous(characterId) ? 2 : find(characterId) == null ? 0 : 1;
    }

    /** Whether a personal marker filed under this id still has an owner. */
    public boolean hasOwner(UUID ownerId) {
        return ownerId != null
                && (this.characters.containsKey(ownerId)
                        || this.rosterOwnerIds.contains(ownerId));
    }

    /** Whether the id is a player's own: the account as a playable identity. */
    public boolean isAccountOwner(UUID id) {
        return id != null && this.rosterOwnerIds.contains(id);
    }
}
