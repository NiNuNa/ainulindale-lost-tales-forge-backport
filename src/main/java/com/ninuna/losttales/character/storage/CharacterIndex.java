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
 * nobody, since the id is what fellowships, markers and bounties key on.
 */
public final class CharacterIndex {

    private final Map<UUID, RoleplayCharacter> characters;
    private final Set<UUID> ambiguousCharacterIds;

    private CharacterIndex(Map<UUID, RoleplayCharacter> characters,
                           Set<UUID> ambiguousCharacterIds) {
        this.characters = Collections.unmodifiableMap(characters);
        this.ambiguousCharacterIds = Collections.unmodifiableSet(ambiguousCharacterIds);
    }

    static CharacterIndex build(Collection<CharacterRoster> rosters) {
        Map<UUID, RoleplayCharacter> characters = new HashMap<UUID, RoleplayCharacter>();
        Set<UUID> ambiguous = new HashSet<UUID>();
        for (CharacterRoster roster : rosters) {
            if (roster == null) {
                continue;
            }
            for (RoleplayCharacter character : roster.getCharacters()) {
                UUID characterId = character.getCharacterId();
                if (ambiguous.contains(characterId)) {
                    continue;
                }
                if (characters.containsKey(characterId)) {
                    characters.remove(characterId);
                    ambiguous.add(characterId);
                } else {
                    characters.put(characterId, character);
                }
            }
        }
        return new CharacterIndex(characters, ambiguous);
    }

    /** The character with this id, or null when there is none or more than one. */
    public RoleplayCharacter find(UUID characterId) {
        return characterId == null ? null : this.characters.get(characterId);
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

}
