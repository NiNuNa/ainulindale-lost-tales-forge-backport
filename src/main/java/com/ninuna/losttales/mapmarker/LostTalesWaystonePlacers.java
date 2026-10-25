package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.character.deletion.CharacterDeletionStorage;
import com.ninuna.losttales.character.deletion.CharacterDeletionTombstone;
import com.ninuna.losttales.character.deletion.CharacterDeletionWorldData;
import com.ninuna.losttales.character.lore.LoreCharacterDefinition;
import com.ninuna.losttales.character.lore.LoreCharacterRegistry;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipRecord;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipStorage;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipWorldData;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.World;

/**
 * The names of the characters waystones are called after, as they stand
 * now (WAY-01): a character a roster holds by its name, a deleted one not
 * yet purged by the name it went with (WAY-03), a lore figure nobody holds
 * by the name its story gives it. A purged character has none, and its
 * waystone reads {@code Waystone}.
 *
 * <p>One lookup serves one snapshot or one line and keeps each name it
 * found, so a snapshot of many waystones asks each store once per
 * character. A store that cannot be read names nobody.</p>
 */
public final class LostTalesWaystonePlacers
        implements LostTalesMapMarkerNamedAfter.Names {
    private final CharacterWorldData rosters;
    private final CharacterDeletionWorldData deletions;
    private final LoreCharacterOwnershipWorldData loreFigures;
    private final Map<UUID, String> found = new HashMap<UUID, String>();

    LostTalesWaystonePlacers(CharacterWorldData rosters,
                             CharacterDeletionWorldData deletions,
                             LoreCharacterOwnershipWorldData loreFigures) {
        this.rosters = rosters;
        this.deletions = deletions;
        this.loreFigures = loreFigures;
    }

    /** This world's characters, deleted ones and lore figures, as waystones are called after them. */
    public static LostTalesWaystonePlacers of(World world) {
        CharacterWorldData rosters = null;
        CharacterDeletionWorldData deletions = null;
        LoreCharacterOwnershipWorldData loreFigures = null;
        if (world != null && !world.isRemote) {
            try {
                rosters = CharacterStorage.get(world);
            } catch (RuntimeException unreadable) {
                rosters = null;
            }
            try {
                deletions = CharacterDeletionStorage.get(world);
            } catch (RuntimeException unreadable) {
                deletions = null;
            }
            try {
                loreFigures = LoreCharacterOwnershipStorage.get(world);
            } catch (RuntimeException unreadable) {
                loreFigures = null;
            }
        }
        return new LostTalesWaystonePlacers(rosters, deletions, loreFigures);
    }

    /**
     * What a marker is called after as a player's game is sent it: the
     * placing character by its name as it stands
     * ({@link LostTalesMapMarkerNamedAfter#shown}).
     */
    public static String shown(World world, String namedAfter) {
        return LostTalesMapMarkerNamedAfter.characterId(namedAfter) == null
                ? (namedAfter == null ? "" : namedAfter)
                : LostTalesMapMarkerNamedAfter.shown(namedAfter, of(world));
    }

    @Override
    public String nameOf(UUID characterId) {
        if (characterId == null) {
            return null;
        }
        if (this.found.containsKey(characterId)) {
            return this.found.get(characterId);
        }
        String name = look(characterId);
        this.found.put(characterId, name);
        return name;
    }

    private String look(UUID characterId) {
        if (this.rosters != null) {
            RoleplayCharacter character =
                    this.rosters.findCharacter(characterId);
            if (character != null) {
                return character.getName();
            }
        }
        if (this.deletions != null
                && !this.deletions.isReadOnlyForNewerVersion()) {
            CharacterDeletionTombstone tombstone =
                    this.deletions.getTombstone(characterId);
            if (tombstone != null) {
                return tombstone.getCharacterName();
            }
        }
        if (this.loreFigures != null) {
            LoreCharacterOwnershipRecord figure =
                    this.loreFigures.getRecordByCharacterId(characterId);
            LoreCharacterDefinition story = figure == null ? null
                    : LoreCharacterRegistry.get(figure.getLoreCharacterId());
            if (story != null) {
                return story.getName();
            }
        }
        return null;
    }
}
