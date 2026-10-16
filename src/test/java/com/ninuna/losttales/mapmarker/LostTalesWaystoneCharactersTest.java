package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A waystone names characters: who it is called after, and who it is shared with. */
public final class LostTalesWaystoneCharactersTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    public void aWaystoneIsCalledAfterTheCharacterThatPlacedIt() {
        assertEquals("Aragorn", LostTalesMapMarkerRepository.placerName(character(" Aragorn ")));
        assertEquals("player:Aragorn", LostTalesMapMarkerNamedAfter.player(
                LostTalesMapMarkerRepository.placerName(character("Aragorn"))));
        // Nobody played leaves nothing to call it after, and no record takes that.
        assertEquals("", LostTalesMapMarkerRepository.placerName(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aWaystoneCalledAfterNobodyIsNotMade() {
        LostTalesMapMarkerRecord.createPlayerMarker("losttales:player/abc",
                LostTalesMapMarkerRepository.placerName(null),
                OWNER, 0, 1.0D, 64.0D, 1.0D, UUID.randomUUID());
    }

    /** The owner's own characters stay on the list as any share would. */
    @Test
    public void aShareKeepsTheCharacterWhoeverOwnsIt() {
        UUID ownersCharacter = UUID.randomUUID();
        LostTalesMapMarkerRecord record = LostTalesMapMarkerRecord.createPlayerMarker(
                "losttales:player/abc", "Aragorn", OWNER, 0, 1.0D, 64.0D, 1.0D,
                UUID.randomUUID()).withSharedCharacters(
                Collections.singleton(ownersCharacter),
                LostTalesMapMarkerVisibility.SHARED);
        assertTrue(record.getSharedCharacterIds().contains(ownersCharacter));
    }

    /**
     * Naming a character of the owner's own account is turned down only
     * for whoever knows it is theirs: the owner, or a sharer shown
     * accounts.
     */
    @Test
    public void theOwnersOwnCharacterIsRefusedOnlyWhereThatTellsNothing() {
        assertTrue(LostTalesWaystoneSettingsService.refusesOwnerShare(true, false));
        assertTrue(LostTalesWaystoneSettingsService.refusesOwnerShare(true, true));
        assertTrue(LostTalesWaystoneSettingsService.refusesOwnerShare(false, true));
        assertFalse(LostTalesWaystoneSettingsService.refusesOwnerShare(false, false));
    }

    private static RoleplayCharacter character(String name) {
        return RoleplayCharacter.builder(UUID.randomUUID(), OWNER)
                .slot(0)
                .name(name)
                .race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin(CharacterSkinRegistry.ACCOUNT_SKIN_ID)
                .startingFaction("lotr:gondor")
                .build();
    }
}
