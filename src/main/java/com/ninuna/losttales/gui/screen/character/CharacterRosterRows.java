package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.CharacterSlotState;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.window.PageSearch;
import java.util.ArrayList;
import java.util.List;

/**
 * The rows of the Characters tab's roster, as the page draws them and the
 * pointer hits them: the account's own character first, then the slots
 * under a heading counting them — a character, or an unlocked slot
 * waiting to be filled — and last the way to the lore characters. A
 * search keeps the characters whose names hold its words and drops the
 * empty slots.
 */
final class CharacterRosterRows {
    /** What a row is. */
    enum Kind { ACCOUNT, HEADING, CHARACTER, EMPTY, LORE }

    /** One row: its kind, its slot, and the character in it where there is one. */
    static final class Row {
        final Kind kind;
        /** The slot the row stands for; the default slot for the account's row. */
        final int slot;
        /** The character in the slot; null for an empty one, and for the account's row before a world made its record. */
        final CharacterSummary character;

        Row(Kind kind, int slot, CharacterSummary character) {
            this.kind = kind;
            this.slot = slot;
            this.character = character;
        }

        /** Whether a press picks the row. */
        boolean pickable() {
            return this.kind == Kind.ACCOUNT || this.kind == Kind.CHARACTER
                    || this.kind == Kind.EMPTY;
        }
    }

    private CharacterRosterRows() {}

    /**
     * The roster's rows for {@code snapshot}, the search held; the
     * account's row matched by {@code accountName} while its record is not
     * made. None while no roster is known.
     */
    static List<Row> of(CharacterRosterSnapshot snapshot, String accountName,
                        String query) {
        List<Row> rows = new ArrayList<Row>();
        if (snapshot == null) {
            return rows;
        }
        PageSearch search = PageSearch.of(query);
        boolean searching = query != null && query.trim().length() > 0;
        CharacterSummary own = snapshot.getCharacterAtSlot(
                CharacterRoster.DEFAULT_SLOT_INDEX);
        if (search.matches(own == null ? accountName : own.getName())) {
            rows.add(new Row(Kind.ACCOUNT, CharacterRoster.DEFAULT_SLOT_INDEX,
                    own));
        }
        List<Row> slots = new ArrayList<Row>();
        for (int slot = 0; slot < CharacterRoster.MAX_SLOTS; slot++) {
            CharacterSlotState state = snapshot.getSlotState(slot);
            CharacterSummary character = snapshot.getCharacterAtSlot(slot);
            if (character != null) {
                if (search.matches(character.getName())) {
                    slots.add(new Row(Kind.CHARACTER, slot, character));
                }
            } else if (state == CharacterSlotState.UNLOCKED && !searching) {
                slots.add(new Row(Kind.EMPTY, slot, null));
            }
        }
        if (!slots.isEmpty()) {
            rows.add(new Row(Kind.HEADING, CharacterRoster.DEFAULT_SLOT_INDEX,
                    null));
            rows.addAll(slots);
        }
        if (!searching) {
            rows.add(new Row(Kind.LORE, CharacterRoster.DEFAULT_SLOT_INDEX,
                    null));
        }
        return rows;
    }

    /** The characters the heading counts: those in the slots, the account's own left out. */
    static int filledSlots(CharacterRosterSnapshot snapshot) {
        int filled = 0;
        for (int slot = 0; slot < CharacterRoster.MAX_SLOTS; slot++) {
            if (snapshot.getCharacterAtSlot(slot) != null) {
                filled++;
            }
        }
        return filled;
    }

    /** How many characters a search found: the rows that name one. */
    static int found(List<Row> rows) {
        int found = 0;
        for (Row row : rows) {
            if (row.kind == Kind.CHARACTER
                    || row.kind == Kind.ACCOUNT) {
                found++;
            }
        }
        return found;
    }

    /**
     * Whether a character is the one played. A world that has not made
     * the account's record names no character played, and the account is
     * what is played then, so its row answers for it.
     */
    static boolean isPlayed(CharacterRosterSnapshot snapshot, Row row) {
        if (snapshot == null || row == null) {
            return false;
        }
        if (row.kind == Kind.ACCOUNT && row.character == null) {
            return snapshot.getActiveCharacterId() == null;
        }
        if (row.character == null) {
            return false;
        }
        return snapshot.getActiveCharacterId() == null
                ? row.character.isDefault()
                : row.character.getCharacterId().equals(
                        snapshot.getActiveCharacterId());
    }

    /** The row standing for {@code slot}; null while none does. */
    static Row atSlot(List<Row> rows, int slot) {
        for (Row row : rows) {
            if (row.pickable() && row.slot == slot) {
                return row;
            }
        }
        return null;
    }

    /** The row played; null while no row shown is. */
    static Row played(CharacterRosterSnapshot snapshot, List<Row> rows) {
        for (Row row : rows) {
            if (row.pickable() && isPlayed(snapshot, row)) {
                return row;
            }
        }
        return null;
    }

    /** The first unlocked slot with nothing in it; -1 for none. */
    static int firstEmptySlot(CharacterRosterSnapshot snapshot) {
        if (snapshot == null) {
            return -1;
        }
        for (int slot = 0; slot < CharacterRoster.MAX_SLOTS; slot++) {
            if (snapshot.getSlotState(slot) == CharacterSlotState.UNLOCKED) {
                return slot;
            }
        }
        return -1;
    }
}
