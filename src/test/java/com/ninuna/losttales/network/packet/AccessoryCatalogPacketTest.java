package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.accessory.AccessoryDefinition;
import com.ninuna.losttales.accessory.AccessoryEffect;
import com.ninuna.losttales.accessory.AccessorySlotType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The server's accessories reach a joining player whole, or change nothing (ACC-01). */
public final class AccessoryCatalogPacketTest {

    @Test
    public void theListRoundTrips() {
        List<AccessoryDefinition> sent = new ArrayList<AccessoryDefinition>();
        sent.add(new AccessoryDefinition("losttales:the_one_ring", AccessorySlotType.RING,
                "losttales:the_one_ring",
                EnumSet.of(AccessoryEffect.CONCEALMENT, AccessoryEffect.WRAITH_WORLD)));
        sent.add(new AccessoryDefinition("lotr:gold_ring", AccessorySlotType.RING,
                "lotr:item.goldRing", null));
        ByteBuf buffer = Unpooled.buffer();
        new AccessoryCatalogPacket(sent).toBytes(buffer);

        AccessoryCatalogPacket decoded = new AccessoryCatalogPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(2, decoded.getDefinitions().size());
        AccessoryDefinition oneRing = decoded.getDefinitions().get(0);
        assertEquals("losttales:the_one_ring", oneRing.getId());
        assertEquals(AccessorySlotType.RING, oneRing.getSlotType());
        assertEquals("losttales:the_one_ring", oneRing.getItemId());
        assertEquals(EnumSet.of(AccessoryEffect.CONCEALMENT, AccessoryEffect.WRAITH_WORLD),
                oneRing.getEffects());
        assertEquals("lotr:item.goldRing", decoded.getDefinitions().get(1).getItemId());
        assertTrue(decoded.getDefinitions().get(1).getEffects().isEmpty());
    }

    @Test
    public void anEmptyListRoundTrips() {
        ByteBuf buffer = Unpooled.buffer();
        new AccessoryCatalogPacket(new ArrayList<AccessoryDefinition>()).toBytes(buffer);
        AccessoryCatalogPacket decoded = new AccessoryCatalogPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.getDefinitions().isEmpty());
    }

    @Test
    public void anUnknownEffectOrSlotIsMalformed() {
        assertTrue(decode(0, 1 << 7).isMalformed());
        assertTrue(decode(7, 0).isMalformed());
        assertFalse(decode(0, 1).isMalformed());
    }

    @Test
    public void trailingBytesAndTooManyAreMalformed() {
        ByteBuf trailing = Unpooled.buffer();
        new AccessoryCatalogPacket(new ArrayList<AccessoryDefinition>()).toBytes(trailing);
        trailing.writeByte(1);
        AccessoryCatalogPacket decoded = new AccessoryCatalogPacket();
        decoded.fromBytes(trailing);
        assertTrue(decoded.isMalformed());

        ByteBuf many = Unpooled.buffer();
        many.writeShort(5000);
        AccessoryCatalogPacket tooMany = new AccessoryCatalogPacket();
        tooMany.fromBytes(many);
        assertTrue(tooMany.isMalformed());
        assertTrue(tooMany.getDefinitions().isEmpty());
    }

    /** One accessory as the server writes it, its slot and effect bits as given. */
    private static AccessoryCatalogPacket decode(int slot, int effects) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeShort(1);
        LostTalesPacketCodec.writeUtf8String(buffer, "a:ring", 64);
        buffer.writeByte(slot);
        LostTalesPacketCodec.writeUtf8String(buffer, "a:item", 128);
        buffer.writeByte(effects);
        AccessoryCatalogPacket decoded = new AccessoryCatalogPacket();
        decoded.fromBytes(buffer);
        return decoded;
    }
}
