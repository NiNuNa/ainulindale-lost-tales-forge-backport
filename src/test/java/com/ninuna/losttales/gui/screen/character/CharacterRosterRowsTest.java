package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.character.sync.DeletedCharacterSummary;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The roster lists the unlocked slots under their heading — a character,
 * or a slot to create one in — and last the way to the lore characters; a
 * search keeps the names that hold its words. The one played is the
 * active character.
 */
public final class CharacterRosterRowsTest {
    private static final UUID OWNER = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");
    private static final UUID OWN = UUID.fromString(
            "00000000-0000-0000-0000-000000000010");
    private static final UUID ALDRIC = UUID.fromString(
            "00000000-0000-0000-0000-000000000011");

    @Test
    public void theSlotsComeFirstThenTheLoreCharacters() {
        List<CharacterRosterRows.Row> rows = CharacterRosterRows.of(
                snapshot(3, null, summary(ALDRIC, 1, "Aldric")), "");
        assertEquals(Arrays.asList(CharacterRosterRows.Kind.HEADING,
                CharacterRosterRows.Kind.EMPTY,
                CharacterRosterRows.Kind.CHARACTER,
                CharacterRosterRows.Kind.EMPTY,
                CharacterRosterRows.Kind.LORE), kinds(rows));
        assertEquals(1, rows.get(2).slot);
        assertEquals(1, CharacterRosterRows.filledSlots(snapshot(3, null,
                summary(ALDRIC, 1, "Aldric"))));
    }

    @Test
    public void aSearchKeepsTheNamesThatHoldItsWordsAndNoEmptySlot() {
        CharacterRosterSnapshot snapshot = snapshot(3, null,
                summary(ALDRIC, 1, "Aldric"));
        List<CharacterRosterRows.Row> rows = CharacterRosterRows.of(snapshot,
                "ald");
        assertEquals(Arrays.asList(CharacterRosterRows.Kind.HEADING,
                CharacterRosterRows.Kind.CHARACTER), kinds(rows));
        assertEquals(1, CharacterRosterRows.found(rows));
        assertTrue(CharacterRosterRows.of(snapshot, "zzz").isEmpty());
    }

    @Test
    public void nothingIsPlayedBeforeTheFirstCharacter() {
        CharacterRosterSnapshot snapshot = snapshot(1, null);
        List<CharacterRosterRows.Row> rows = CharacterRosterRows.of(snapshot, "");
        assertTrue(snapshot.isWaitingForFirstCharacter());
        assertNull(CharacterRosterRows.played(snapshot, rows));
    }

    @Test
    public void theCharacterPlayedIsTheActiveOne() {
        CharacterRosterSnapshot snapshot = snapshot(2, ALDRIC,
                summary(OWN, 1, "Nils"), summary(ALDRIC, 0, "Aldric"));
        List<CharacterRosterRows.Row> rows = CharacterRosterRows.of(snapshot, "");
        assertFalse(CharacterRosterRows.isPlayed(snapshot, rows.get(2)));
        CharacterRosterRows.Row played = CharacterRosterRows.played(snapshot,
                rows);
        assertEquals(0, played.slot);
        assertSame(played, CharacterRosterRows.atKey(rows, played.key()));
    }

    @Test
    public void theFirstEmptySlotIsTheFirstUnlockedWithNobodyInIt() {
        assertEquals(1, CharacterRosterRows.firstEmptySlot(snapshot(3, null,
                summary(ALDRIC, 0, "Aldric"))));
        assertEquals(-1, CharacterRosterRows.firstEmptySlot(snapshot(1, null,
                summary(ALDRIC, 0, "Aldric"))));
        assertEquals(-1, CharacterRosterRows.firstEmptySlot(null));
    }

    private static List<CharacterRosterRows.Kind> kinds(
            List<CharacterRosterRows.Row> rows) {
        List<CharacterRosterRows.Kind> kinds =
                new ArrayList<CharacterRosterRows.Kind>();
        for (CharacterRosterRows.Row row : rows) {
            kinds.add(row.kind);
        }
        return kinds;
    }

    private static CharacterRosterSnapshot snapshot(int unlocked, UUID active,
                                                    CharacterSummary... characters) {
        return new CharacterRosterSnapshot(OWNER, unlocked, active, 1L,
                Arrays.asList(characters),
                Collections.<DeletedCharacterSummary>emptyList());
    }

    private static CharacterSummary summary(UUID id, int slot, String name) {
        return new CharacterSummary(id, slot, name, "losttales:human",
                "losttales:male", "losttales:account_skin",
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, 30,
                "lotr:gondor", "", "");
    }
}
