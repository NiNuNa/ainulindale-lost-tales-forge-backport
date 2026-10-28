package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterArmsRegistry;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinDefinition;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Everyone is told a player's look again only when the played character's
 * look changes: a profile saved, or another character changed, leaves it
 * the same.
 */
public final class CharacterAppearanceChangeTest {

    private static final UUID PLAYER = UUID.fromString(
            "7c1a8c2e-2b6f-4b7e-9a1e-0f3c6d2a8b33");
    private static final UUID PLAYED = UUID.fromString(
            "8c1a8c2e-2b6f-4b7e-9a1e-0f3c6d2a8b44");
    private static final UUID RESTING = UUID.fromString(
            "9c1a8c2e-2b6f-4b7e-9a1e-0f3c6d2a8b55");

    @Test
    public void aProfileSavedLeavesTheLookAsItWas() {
        CharacterRoster roster = roster();
        CharacterAppearance before = look(roster);
        RoleplayCharacter played = roster.getCharacter(PLAYED);
        assertTrue(roster.replaceCharacter(RoleplayCharacter.builder(played)
                .profile(CharacterProfile.EMPTY.withSection(
                        CharacterProfile.Section.HISTORY, "Rode north."))
                .build()));
        assertTrue(before.sameAs(look(roster)));
    }

    @Test
    public void anotherCharacterChangedLeavesTheLookAsItWas() {
        CharacterRoster roster = roster();
        CharacterAppearance before = look(roster);
        RoleplayCharacter resting = roster.getCharacter(RESTING);
        assertTrue(roster.replaceCharacter(RoleplayCharacter.builder(resting)
                .skin(otherSkin(resting.getSkinId())).build()));
        assertTrue(before.sameAs(look(roster)));
    }

    @Test
    public void thePlayedCharactersSkinOrASwitchChangesTheLook() {
        CharacterRoster roster = roster();
        CharacterAppearance before = look(roster);
        RoleplayCharacter played = roster.getCharacter(PLAYED);
        assertTrue(roster.replaceCharacter(RoleplayCharacter.builder(played)
                .skin(otherSkin(played.getSkinId())).build()));
        assertFalse(before.sameAs(look(roster)));

        CharacterRoster switched = roster();
        switched.setActiveCharacterId(RESTING);
        assertFalse(before.sameAs(look(switched)));
    }

    private static CharacterAppearance look(CharacterRoster roster) {
        return CharacterAppearance.fromRoster(PLAYER, "Steve", roster,
                CharacterArmsRegistry.BROAD);
    }

    private static CharacterRoster roster() {
        CharacterRoster roster = new CharacterRoster(PLAYER);
        roster.unlockNextSlotAfter(0);
        assertTrue(roster.addCharacter(character(PLAYED, 0, "Aldric")));
        assertTrue(roster.addCharacter(character(RESTING, 1, "Beren")));
        roster.setActiveCharacterId(PLAYED);
        return roster;
    }

    private static RoleplayCharacter character(UUID id, int slot, String name) {
        return RoleplayCharacter.builder(id, PLAYER)
                .slot(slot).name(name).race(CharacterRaceRegistry.HUMAN)
                .bodyType(CharacterBodyTypeRegistry.MASCULINE)
                .skin(skins().get(0)).age(30)
                .startingFaction("lotr:bree").createdAt(1L)
                .build();
    }

    private static String otherSkin(String skin) {
        for (String each : skins()) {
            if (!each.equals(skin)) {
                return each;
            }
        }
        throw new AssertionError("only one skin to choose from");
    }

    private static List<String> skins() {
        List<String> ids = new ArrayList<String>();
        for (CharacterSkinDefinition skin
                : CharacterSkinRegistry.getCompatibleSkins(CharacterRaceRegistry.HUMAN,
                        CharacterBodyTypeRegistry.MASCULINE)) {
            ids.add(skin.getId());
        }
        return ids;
    }
}
