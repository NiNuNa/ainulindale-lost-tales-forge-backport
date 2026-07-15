package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.lore.sync.LoreCharacterSnapshot;
import com.ninuna.losttales.character.lore.sync.LoreCharacterSummary;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LoreCharacterSyncPacketTest {

    private static final UUID CHARACTER_ID = UUID.fromString(
            "b1000000-0000-0000-0000-00000000001b");

    @Test
    public void viewerOwnedCharacterRoundTripsWithoutLosingState() {
        LoreCharacterSummary gandalf = new LoreCharacterSummary(
                "gandalf", "Gandalf", "A wandering wizard.",
                "losttales:human", "losttales:male", "lotr:human",
                "losttales:human_gondor_male_0", true, true, true,
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
            LoreCharacterSummary value = decoded.getSnapshot().get("gandalf");
            assertEquals("A wandering wizard.", value.getDescription());
            assertEquals("AccountName", value.getOwnerName());
            assertEquals(CHARACTER_ID, value.getOwnedCharacterId());
            assertEquals(7L, value.getOwnershipRevision());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void foreignCharacterIdIsNeverExposedToViewer() {
        LoreCharacterSummary frodo = new LoreCharacterSummary(
                "frodo", "Frodo", "", "losttales:hobbit",
                "losttales:male", "lotr:hobbit", "losttales:hobbit_0",
                true, true, false, false, "AnotherPlayer",
                CHARACTER_ID, 2L);

        assertNull(frodo.getOwnedCharacterId());
    }

    @Test
    public void negativeOwnershipRevisionMakesPacketMalformed() {
        LoreCharacterSummary eomer = new LoreCharacterSummary(
                "eomer", "Eomer", "", "losttales:human",
                "losttales:male", "lotr:human", "losttales:rohan_0",
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
