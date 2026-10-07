package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.CharacterSlotState;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.character.sync.DeletedCharacterSummary;
import com.ninuna.losttales.client.window.PageSearch;
import java.util.ArrayList;
import java.util.List;

/**
 * The rows of the Characters page's roster, as the page draws them and the
 * pointer hits them: the slots under a
 * heading counting them — a character, or an unlocked slot waiting to be
 * filled — then the way to the lore characters, and at the foot the
 * deleted characters the player may still restore, under a heading of
 * their own. A search keeps the characters whose names hold its words and
 * drops the empty slots and the way to the lore characters.
 */
final class CharacterRosterRows {
    /** What a row is. */
    enum Kind { HEADING, CHARACTER, EMPTY, LORE, DELETED_HEADING, DELETED }

    /** The slot a row stands for when it stands for none. */
    static final int NO_SLOT = Integer.MIN_VALUE;

    /** One row: its kind, its slot, and the character in it where there is one. */
    static final class Row {
        final Kind kind;
        /** The slot the row stands for; {@link #NO_SLOT} for a row that stands for none. */
        final int slot;
        /** The character in the slot; null for an empty one. */
        final CharacterSummary character;
        /** The deleted character a deleted row names; null on every other row. */
        final DeletedCharacterSummary deleted;

        Row(Kind kind, int slot, CharacterSummary character) {
            this(kind, slot, character, null);
        }

        Row(Kind kind, int slot, CharacterSummary character,
            DeletedCharacterSummary deleted) {
            this.kind = kind;
            this.slot = slot;
            this.character = character;
            this.deleted = deleted;
        }

        /** Whether a press picks the row. */
        boolean pickable() {
            return this.kind == Kind.CHARACTER || this.kind == Kind.EMPTY
                    || this.kind == Kind.DELETED;
        }

        /** What the page remembers the pick by: the slot, or the deleted character's id. */
        String key() {
            return this.kind == Kind.DELETED
                    ? "deleted:" + this.deleted.getCharacterId()
                    : "slot:" + this.slot;
        }
    }

    private CharacterRosterRows() {}

    /** The roster's rows for {@code snapshot}, the search held; none while no roster is known. */
    static List<Row> of(CharacterRosterSnapshot snapshot, String query) {
        List<Row> rows = new ArrayList<Row>();
        if (snapshot == null) {
            return rows;
        }
        PageSearch search = PageSearch.of(query);
        boolean searching = query != null && query.trim().length() > 0;
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
            rows.add(new Row(Kind.HEADING, NO_SLOT, null));
            rows.addAll(slots);
        }
        if (!searching) {
            rows.add(new Row(Kind.LORE, NO_SLOT, null));
        }
        List<Row> deleted = new ArrayList<Row>();
        for (DeletedCharacterSummary each : snapshot.getDeleted()) {
            if (search.matches(each.getName())) {
                deleted.add(new Row(Kind.DELETED, NO_SLOT, null, each));
            }
        }
        if (!deleted.isEmpty()) {
            rows.add(new Row(Kind.DELETED_HEADING, NO_SLOT, null));
            rows.addAll(deleted);
        }
        return rows;
    }

    /** The characters the heading counts: those in the slots. */
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
            if (row.kind == Kind.CHARACTER || row.kind == Kind.DELETED) {
                found++;
            }
        }
        return found;
    }

    /** Whether the row's character is the one played. */
    static boolean isPlayed(CharacterRosterSnapshot snapshot, Row row) {
        return snapshot != null && row != null && row.character != null
                && row.character.getCharacterId().equals(
                        snapshot.getActiveCharacterId());
    }

    /** The row the page remembers by {@code key}; null while none stands for it. */
    static Row atKey(List<Row> rows, String key) {
        if (key == null) {
            return null;
        }
        for (Row row : rows) {
            if (row.pickable() && key.equals(row.key())) {
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
