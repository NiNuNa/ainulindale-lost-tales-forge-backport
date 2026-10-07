package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A player with no character yet is a ghost: present, drawable in the
 * account's own skin, never a character, wearing no cape. A character with
 * no race is nothing to draw.
 */
public final class CharacterAppearanceTest {

    private static final UUID PLAYER = UUID.fromString(
        "6c1a8c2e-2b6f-4b7e-9a1e-0f3c6d2a8b22");

    @Test
    public void theAccountIsPresentButHasNoCharacter() {
        CharacterAppearance account = CharacterAppearance.forAccount(
                PLAYER, "Steve", CharacterBodyTypeRegistry.SLIM);
        assertEquals(CharacterAppearanceKind.ACCOUNT, account.getKind());
        assertTrue(account.isPresent());
        assertTrue(account.isAccount());
        assertFalse(account.hasCharacter());
        assertEquals("Steve", account.getAccountName());
        assertEquals("", account.getCharacterName());
        assertEquals(CharacterRaceRegistry.HUMAN, account.getRaceId());
        assertEquals(CharacterSkinRegistry.ACCOUNT_SKIN_ID, account.getSkinId());
        assertEquals(CharacterBodyTypeRegistry.SLIM, account.getBodyTypeId());
        assertEquals(CharacterChestTypeRegistry.NONE, account.getChestTypeId());
        assertFalse(account.isMinecraftCapeVisible());
        assertEquals(CharacterCapeCatalog.NONE_ID, account.getCosmeticCapeId());
    }

    @Test
    public void anUnknownArmWidthOnTheAccountReadsAsWide() {
        assertEquals(CharacterBodyTypeRegistry.WIDE, CharacterAppearance.forAccount(
                PLAYER, "Steve", "losttales:huge").getBodyTypeId());
    }

    @Test
    public void aCharacterWithNoRaceIsARemoval() {
        CharacterAppearance removed = CharacterAppearance.removed(PLAYER);
        assertEquals(CharacterAppearanceKind.NONE, removed.getKind());
        assertFalse(removed.isPresent());
        assertFalse(removed.hasCharacter());
        assertFalse(removed.isAccount());
        CharacterAppearance character = new CharacterAppearance(PLAYER,
                CharacterRaceRegistry.HUMAN, CharacterGenderRegistry.MALE,
                "losttales:human_bree_male_0", "", "");
        assertEquals(CharacterAppearanceKind.CHARACTER, character.getKind());
        assertTrue(character.hasCharacter());
    }

    @Test
    public void aRosterPlayingNoCharacterIsAGhost() {
        CharacterRoster roster = new CharacterRoster(PLAYER);
        CharacterAppearance account = CharacterAppearance.fromRoster(
                PLAYER, "Steve", roster, CharacterBodyTypeRegistry.SLIM);
        assertTrue(account.isAccount());
        assertEquals(CharacterBodyTypeRegistry.SLIM, account.getBodyTypeId());
        assertFalse(account.isMinecraftCapeVisible());
        assertEquals(CharacterCapeCatalog.NONE_ID, account.getCosmeticCapeId());
        // No roster written yet: a ghost all the same.
        assertTrue(CharacterAppearance.fromRoster(PLAYER, "Steve", null,
                CharacterBodyTypeRegistry.WIDE).isAccount());
    }
}
