package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.lore.LoreCharacterDefinition;
import com.ninuna.losttales.character.lore.LoreCharacterFiles;
import com.ninuna.losttales.character.lore.LoreTestGame;
import com.ninuna.losttales.character.lore.sync.LoreCharacterSnapshot;
import com.ninuna.losttales.character.lore.sync.LoreCharacterSummary;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LoreCharacterSyncPacketTest {

    /** A lore file read and checked as the server does; {@code warnings} takes what it raises. */
    private static LoreCharacterDefinition read(String json, List<String> warnings) {
        LoreCharacterDefinition definition = LoreCharacterFiles.parse(
                new StringReader(json), "lore.json", warnings);
        warnings.addAll(LoreCharacterFiles.warningsOf(definition, new LoreTestGame()));
        return definition;
    }

    private static final UUID CHARACTER_ID = UUID.fromString(
            "b1000000-0000-0000-0000-00000000001b");

    @Test
    public void viewerOwnedCharacterRoundTripsWithoutLosingState() {
        LoreCharacterSummary gandalf = new LoreCharacterSummary(
                "gandalf", "Gandalf", "A wandering wizard.",
                "losttales:human", true, true, true,
                false, "AccountName", CHARACTER_ID, 7L);
        LoreCharacterSnapshot snapshot = new LoreCharacterSnapshot(
                Arrays.asList(gandalf), false, true);

        ByteBuf buffer = Unpooled.buffer();
        try {
            new LoreCharacterSyncPacket(snapshot).toBytes(buffer);
            LoreCharacterSyncPacket decoded = new LoreCharacterSyncPacket();
            decoded.fromBytes(buffer);

            assertFalse(decoded.isMalformed());
            assertTrue(decoded.getSnapshot().isTransferReadOnly());
            assertEquals(1, decoded.getSnapshot().getCharacters().size());
            LoreCharacterSummary value = decoded.getSnapshot().getCharacters().get(0);
            assertEquals("gandalf", value.getId());
            assertEquals("A wandering wizard.", value.getDescription());
            assertEquals("losttales:human", value.getRaceId());
            assertEquals("AccountName", value.getOwnerName());
            assertEquals(CHARACTER_ID, value.getOwnedCharacterId());
            assertEquals(7L, value.getOwnershipRevision());
        } finally {
            buffer.release();
        }
    }

    /**
     * Whatever a lore file may hold travels: an id at its longest and a
     * name of multi-byte letters at its longest, so one server file never
     * stops the lore list reaching anyone.
     */
    @Test
    public void aLoreFigureAtTheFilesBoundsTravels() {
        StringBuilder id = new StringBuilder("myserver:");
        while (id.length() < LoreCharacterDefinition.MAX_ID_LENGTH) {
            id.append('a');
        }
        StringBuilder name = new StringBuilder();
        while (name.toString().getBytes(StandardCharsets.UTF_8).length + 4
                <= LoreCharacterDefinition.MAX_NAME_BYTES) {
            name.append("𐍈");
        }
        List<String> warnings = new ArrayList<String>();
        LoreCharacterDefinition parsed = read("{\"id\":\"" + id + "\",\"name\":\""
                + name + "\",\"faction\":\"lotr:gondor\"}", warnings);
        assertEquals(new ArrayList<String>(), warnings);
        LoreCharacterSummary longest = new LoreCharacterSummary(
                parsed.getId(), parsed.getName(),
                "", "", false, false, false, false, "", null, 0L);
        ByteBuf buffer = Unpooled.buffer();
        try {
            new LoreCharacterSyncPacket(new LoreCharacterSnapshot(
                    Arrays.asList(longest), false, false)).toBytes(buffer);
            LoreCharacterSyncPacket decoded = new LoreCharacterSyncPacket();
            decoded.fromBytes(buffer);
            assertFalse(decoded.isMalformed());
            assertEquals(name.toString(),
                    decoded.getSnapshot().getCharacters().get(0).getName());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void aLoreFileWithAnIdOrNamePastTheWiresBoundsIsRefused() {
        StringBuilder id = new StringBuilder("myserver:");
        while (id.length() <= LoreCharacterDefinition.MAX_ID_LENGTH) {
            id.append('a');
        }
        List<String> longId = new ArrayList<String>();
        read("{\"id\":\"" + id + "\",\"name\":\"Long\",\"faction\":\"lotr:gondor\"}",
                longId);
        assertFalse(longId.isEmpty());
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < LoreCharacterDefinition.MAX_NAME_BYTES / 3 + 1;
                index++) {
            name.append('ლ');
        }
        assertTrue(name.length() <= LoreCharacterDefinition.MAX_NAME_LENGTH);
        List<String> longName = new ArrayList<String>();
        read("{\"id\":\"myserver:long\",\"name\":\"" + name
                + "\",\"faction\":\"lotr:gondor\"}", longName);
        assertFalse(longName.isEmpty());
    }

    @Test
    public void foreignCharacterIdIsNeverExposedToViewer() {
        LoreCharacterSummary frodo = new LoreCharacterSummary(
                "frodo", "Frodo", "", "losttales:hobbit",
                true, true, false, false, "AnotherPlayer",
                CHARACTER_ID, 2L);

        assertNull(frodo.getOwnedCharacterId());
    }

    @Test
    public void negativeOwnershipRevisionMakesPacketMalformed() {
        LoreCharacterSummary eomer = new LoreCharacterSummary(
                "eomer", "Eomer", "", "losttales:human",
                true, false, false, false, "", null, 0L);
        ByteBuf buffer = Unpooled.buffer();
        try {
            new LoreCharacterSyncPacket(new LoreCharacterSnapshot(
                    Arrays.asList(eomer), false, false)).toBytes(buffer);
            buffer.setLong(buffer.writerIndex() - 8, -1L);

            LoreCharacterSyncPacket decoded = new LoreCharacterSyncPacket();
            decoded.fromBytes(buffer);
            assertTrue(decoded.isMalformed());
            assertNull(decoded.getSnapshot());
        } finally {
            buffer.release();
        }
    }
}
