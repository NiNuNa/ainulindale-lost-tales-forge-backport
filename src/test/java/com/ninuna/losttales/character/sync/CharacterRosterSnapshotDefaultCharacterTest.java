package com.ninuna.losttales.character.sync;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What the client can tell about the account's own identity from the
 * roster it is sent. The slot says which one it is, so no extra field
 * crosses the wire to say so.
 */
public final class CharacterRosterSnapshotDefaultCharacterTest {

    private static final UUID OWNER =
            UUID.fromString("d0000000-0000-0000-0000-00000000000d");
    private static final UUID MADE =
            UUID.fromString("d1000000-0000-0000-0000-00000000001d");

    @Test
    public void theSlotSaysWhichSummaryIsTheAccountsOwn() {
        assertTrue(summary(OWNER, CharacterRoster.DEFAULT_SLOT_INDEX,
                "Steve").isDefault());
        assertFalse(summary(MADE, 0, "Aldric").isDefault());
    }

    @Test
    public void theDefaultCharacterIsFound() {
        CharacterRosterSnapshot snapshot = snapshot(Arrays.asList(
                summary(OWNER, CharacterRoster.DEFAULT_SLOT_INDEX, "Steve"),
                summary(MADE, 0, "Aldric")));

        assertEquals(2, snapshot.getCharacterCount());
        CharacterSummary account = snapshot.getDefaultCharacter();
        assertNotNull(account);
        assertEquals("Steve", account.getName());
    }

    /** A roster from a world that has not made it yet answers with none. */
    @Test
    public void aRosterWithoutTheDefaultCharacterAnswersWithNone() {
        CharacterRosterSnapshot snapshot = snapshot(
                Collections.singletonList(summary(MADE, 0, "Aldric")));

        assertNull(snapshot.getDefaultCharacter());
    }

    private static CharacterRosterSnapshot snapshot(
            java.util.List<CharacterSummary> characters) {
        return new CharacterRosterSnapshot(OWNER, 2, null, 1L,
                RoleplayCharacter.CURRENT_DATA_VERSION, characters,
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, true);
    }

    private static CharacterSummary summary(UUID id, int slot, String name) {
        return new CharacterSummary(id, slot, name, "losttales:human",
                "losttales:male", "losttales:account_skin",
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, 30,
                "lotr:gondor", 1, 0L, 1L,
                RoleplayCharacter.CURRENT_DATA_VERSION, "", "", "");
    }
}
