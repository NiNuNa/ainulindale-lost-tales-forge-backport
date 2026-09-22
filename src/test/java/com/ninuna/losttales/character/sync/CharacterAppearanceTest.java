package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
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
 * The account is an identity of its own: present, drawable, never a
 * character. A character with no race is nothing to draw.
 */
public final class CharacterAppearanceTest {

    private static final UUID PLAYER = UUID.fromString(
        "6c1a8c2e-2b6f-4b7e-9a1e-0f3c6d2a8b22");

    @Test
    public void theAccountIsPresentButHasNoCharacter() {
        CharacterAppearance account = CharacterAppearance.forAccount(
                PLAYER, "Steve", CharacterBodyTypeRegistry.SLIM, false,
                CharacterCapeCatalog.GONDOR);
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
        assertEquals(CharacterCapeCatalog.GONDOR, account.getCosmeticCapeId());
    }

    @Test
    public void anUnknownArmWidthOnTheAccountReadsAsWide() {
        assertEquals(CharacterBodyTypeRegistry.WIDE, CharacterAppearance.forAccount(
                PLAYER, "Steve", "losttales:huge", true, 0).getBodyTypeId());
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
    public void aRosterWithNoActiveCharacterIsTheAccountWithItsCape() {
        CharacterRoster roster = new CharacterRoster(PLAYER);
        roster.setAccountCapeSettings(false, CharacterCapeCatalog.RANGER);
        CharacterAppearance account = CharacterAppearance.fromRoster(
                PLAYER, "Steve", roster, CharacterBodyTypeRegistry.SLIM);
        assertTrue(account.isAccount());
        assertEquals(CharacterBodyTypeRegistry.SLIM, account.getBodyTypeId());
        assertFalse(account.isMinecraftCapeVisible());
        assertEquals(CharacterCapeCatalog.RANGER, account.getCosmeticCapeId());
        // No roster written yet: the account with the defaults.
        CharacterAppearance fresh = CharacterAppearance.fromRoster(PLAYER, "Steve", null);
        assertTrue(fresh.isAccount());
        assertEquals(RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                fresh.isMinecraftCapeVisible());
        assertEquals(RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID,
                fresh.getCosmeticCapeId());
    }
}
