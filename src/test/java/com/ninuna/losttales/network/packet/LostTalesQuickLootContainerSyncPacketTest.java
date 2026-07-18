package com.ninuna.losttales.network.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesQuickLootContainerSyncPacketTest {

    @Test
    public void emptyContainerRoundTrips() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesQuickLootContainerSyncPacket(
                12, 64, -7, "Urn", true, new ItemStack[0])
                .toBytes(buffer);

        LostTalesQuickLootContainerSyncPacket decoded =
                new LostTalesQuickLootContainerSyncPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(12, decoded.getX());
        assertEquals(64, decoded.getY());
        assertEquals(-7, decoded.getZ());
        assertEquals("Urn", decoded.getTitle());
        assertTrue(decoded.isSealed());
        assertEquals(0, decoded.getItems().length);
    }

    @Test
    public void excessiveSlotCountIsRejectedBeforeReadingItems() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(0);
        buffer.writeInt(64);
        buffer.writeInt(0);
        LostTalesPacketCodec.writeUtf8String(
                buffer, "Container",
                LostTalesQuickLootContainerSyncPacket.MAX_TITLE_BYTES);
        buffer.writeBoolean(false);
        buffer.writeShort(
                LostTalesQuickLootContainerSyncPacket.MAX_ITEM_SLOTS + 1);

        LostTalesQuickLootContainerSyncPacket decoded =
                new LostTalesQuickLootContainerSyncPacket();
        decoded.fromBytes(buffer);

        assertTrue(decoded.isMalformed());
        assertEquals(0, decoded.getItems().length);
    }
}
