package com.ninuna.losttales.progression.statue;

import com.ninuna.losttales.network.packet.ProgressionStatuePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A reset costs coins that double with each reset a character makes (PROG-12, Nils 2026-10-19). */
public final class ResetPriceTest {

    @Test
    public void thePriceDoublesWithEachReset() {
        assertEquals(100, ResetPrice.of(0, 100, 2.0D));
        assertEquals(200, ResetPrice.of(1, 100, 2.0D));
        assertEquals(400, ResetPrice.of(2, 100, 2.0D));
        assertEquals("a factor of one keeps one price", 300, ResetPrice.of(7, 300, 1.0D));
    }

    @Test
    public void thePriceNeverPassesItsBoundAndNothingCostsNothing() {
        assertEquals(ResetPrice.MOST, ResetPrice.of(1000, 100, 2.0D));
        assertEquals(0, ResetPrice.of(3, 0, 2.0D));
    }

    @Test
    public void theStatuePacketReadsBackAndRefusesAPriceOutOfBounds() {
        ByteBuf buf = Unpooled.buffer();
        new ProgressionStatuePacket(10, 64, -20, 400, "lotr:item.mithril").toBytes(buf);
        ProgressionStatuePacket read = new ProgressionStatuePacket();
        read.fromBytes(buf);
        assertFalse(read.isMalformed());
        assertEquals(400, read.getCoins());
        assertEquals("lotr:item.mithril", read.getItem());
        ByteBuf wrong = Unpooled.buffer();
        wrong.writeInt(10);
        wrong.writeInt(64);
        wrong.writeInt(-20);
        wrong.writeInt(-5);
        wrong.writeByte(0);
        read.fromBytes(wrong);
        assertTrue(read.isMalformed());
    }
}
