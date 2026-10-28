package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.character.sync.DeletedCharacterSummary;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The private roster: a full one, the nine slots, goes whole, each
 * character with its faction and without a level, and the deleted
 * characters still to be restored close it.
 */
public final class CharacterRosterSyncPacketTest {

    private static final UUID OWNER =
            UUID.fromString("d9000000-0000-0000-0000-00000000009d");

    @Test
    public void aFullRosterAndItsDeletedCharactersRoundTrip() {
        List<CharacterSummary> characters = new ArrayList<CharacterSummary>();
        for (int slot = 0; slot < CharacterRoster.MAX_SLOTS; slot++) {
            characters.add(summary(UUID.randomUUID(), slot, "Hero " + slot,
                    "lotr:rohan"));
        }
        UUID deletedId = UUID.randomUUID();
        CharacterRosterSnapshot snapshot = new CharacterRosterSnapshot(OWNER,
                CharacterRoster.MAX_SLOTS, null, 12L, characters,
                Arrays.asList(new DeletedCharacterSummary(deletedId, "Aldric",
                        "losttales:human", "losttales:human_bree_masculine_0", 12)));

        CharacterRosterSyncPacket decoded = roundTrip(snapshot);
        assertFalse(decoded.isMalformed());
        CharacterRosterSnapshot read = decoded.getSnapshot();
        assertEquals(CharacterRoster.MAX_SLOTS, read.getCharacterCount());
        assertEquals("lotr:rohan", read.getCharacterAtSlot(3).getFactionId());
        assertEquals(1, read.getDeleted().size());
        DeletedCharacterSummary deleted = read.getDeleted().get(0);
        assertEquals(deletedId, deleted.getCharacterId());
        assertEquals("Aldric", deleted.getName());
        assertEquals(12, deleted.getDaysLeft());
        assertEquals("losttales:human_bree_masculine_0", deleted.getSkinId());
    }

    @Test
    public void aRosterWithoutItsDeletedCountIsMalformed() {
        CharacterRosterSnapshot snapshot = new CharacterRosterSnapshot(OWNER,
                1, null, 1L, Collections.<CharacterSummary>emptyList(),
                Collections.<DeletedCharacterSummary>emptyList());
        ByteBuf buffer = Unpooled.buffer();
        new CharacterRosterSyncPacket(1, snapshot).toBytes(buffer);
        CharacterRosterSyncPacket shortened = new CharacterRosterSyncPacket();
        // The deleted count and the two flags after it.
        shortened.fromBytes(buffer.slice(0, buffer.readableBytes() - 3));
        assertTrue(shortened.isMalformed());
        assertNull(shortened.getSnapshot());
    }

    /** A store the server cannot read is said so, with nothing in it (CHAR-01). */
    @Test
    public void anUnreadableStoreIsSaidSoAndHoldsNothing() {
        CharacterRosterSnapshot unreadable = CharacterRosterSnapshot.unreadable(OWNER);
        assertTrue(unreadable.isStoreUnreadable());
        assertTrue(unreadable.isWaitingForFirstCharacter());
        CharacterRosterSyncPacket decoded = roundTrip(unreadable);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.getSnapshot().isStoreUnreadable());
        assertTrue(decoded.getSnapshot().getCharacters().isEmpty());
        UUID id = UUID.randomUUID();
        CharacterRosterSnapshot held = new CharacterRosterSnapshot(OWNER, 1, id, 2L,
                Collections.singletonList(summary(id, 0, "Aldric", "lotr:gondor")),
                Collections.<DeletedCharacterSummary>emptyList());
        assertFalse("a roster with characters is read", held.withStoreUnreadable(true)
                .isStoreUnreadable());
        assertFalse(roundTrip(held).getSnapshot().isStoreUnreadable());
    }

    /** A player whose last switch waits for an administrator is said so, roster and all (CHAR-45). */
    @Test
    public void aHeldJournalIsSaidSoWithTheRoster() {
        UUID id = UUID.randomUUID();
        CharacterRosterSnapshot roster = new CharacterRosterSnapshot(OWNER, 1, id, 4L,
                Collections.singletonList(summary(id, 0, "Aldric", "lotr:gondor")),
                Collections.<DeletedCharacterSummary>emptyList());
        assertFalse(roster.isJournalHeld());
        assertFalse(roundTrip(roster).getSnapshot().isJournalHeld());

        CharacterRosterSyncPacket decoded = roundTrip(roster.withJournalHeld(true));
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.getSnapshot().isJournalHeld());
        assertEquals(id, decoded.getSnapshot().getActiveCharacterId());
        assertEquals(1, decoded.getSnapshot().getCharacterCount());
    }

    @Test
    public void aDeletedCharacterNamedTwiceIsMalformed() {
        UUID id = UUID.randomUUID();
        CharacterRosterSnapshot snapshot = new CharacterRosterSnapshot(OWNER,
                1, null, 1L, Collections.<CharacterSummary>emptyList(),
                Arrays.asList(
                        new DeletedCharacterSummary(id, "A", "", "", 1),
                        new DeletedCharacterSummary(id, "B", "", "", 2)));
        CharacterRosterSyncPacket decoded = roundTrip(snapshot);
        assertTrue(decoded.isMalformed());
    }

    private static CharacterRosterSyncPacket roundTrip(
            CharacterRosterSnapshot snapshot) {
        ByteBuf buffer = Unpooled.buffer();
        new CharacterRosterSyncPacket(3, snapshot).toBytes(buffer);
        CharacterRosterSyncPacket decoded = new CharacterRosterSyncPacket();
        decoded.fromBytes(buffer);
        return decoded;
    }

    private static CharacterSummary summary(UUID id, int slot, String name,
                                            String faction) {
        return new CharacterSummary(id, slot, name, "losttales:human",
                "losttales:masculine", "losttales:human_bree_masculine_0",
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, 30, faction, "", "");
    }
}
