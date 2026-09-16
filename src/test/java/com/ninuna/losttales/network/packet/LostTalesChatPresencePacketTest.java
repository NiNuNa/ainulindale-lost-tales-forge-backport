package com.ninuna.losttales.network.packet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.chat.ChatPresence;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

/** A presence and a roster of presences survive the wire; bad ones are refused whole. */
public final class LostTalesChatPresencePacketTest {
    private static final UUID STEVE = new UUID(1L, 2L);
    private static final UUID ALEX = new UUID(3L, 4L);

    @Test
    public void aChoiceRoundTripsAsOneByte() {
        for (ChatPresence presence : ChatPresence.values()) {
            ByteBuf wire = Unpooled.buffer();
            new LostTalesChatPresencePacket(presence).toBytes(wire);
            assertEquals(1, wire.readableBytes());
            LostTalesChatPresencePacket decoded = new LostTalesChatPresencePacket();
            decoded.fromBytes(wire);
            assertFalse(decoded.isMalformed());
            assertEquals(presence, decoded.getPresence());
        }
    }

    @Test
    public void anUnknownEmptyOrTrailingChoiceIsMalformed() {
        assertBadChoice(Unpooled.buffer());
        assertBadChoice(Unpooled.buffer().writeByte(ChatPresence.values().length));
        assertBadChoice(Unpooled.buffer().writeByte(0).writeByte(0));
    }

    @Test
    public void aRosterRoundTripsInOrder() {
        Map<UUID, ChatPresence> roster = new LinkedHashMap<UUID, ChatPresence>();
        roster.put(STEVE, ChatPresence.AWAY);
        roster.put(ALEX, ChatPresence.DO_NOT_DISTURB);
        ByteBuf wire = Unpooled.buffer();
        new LostTalesChatPresenceSyncPacket(roster).toBytes(wire);
        LostTalesChatPresenceSyncPacket decoded = new LostTalesChatPresenceSyncPacket();
        decoded.fromBytes(wire);
        assertFalse(decoded.isMalformed());
        assertEquals(roster, decoded.getEntries());
        assertEquals(STEVE, decoded.getEntries().keySet().iterator().next());
    }

    @Test
    public void aRepeatedAccountAnUnknownStatusOrAnOverfullRosterIsRefused() {
        ByteBuf twice = Unpooled.buffer().writeShort(2);
        writeEntry(twice, STEVE, 1);
        writeEntry(twice, STEVE, 2);
        assertBadRoster(twice);
        ByteBuf unknown = Unpooled.buffer().writeShort(1);
        writeEntry(unknown, STEVE, ChatPresence.values().length);
        assertBadRoster(unknown);
        assertBadRoster(Unpooled.buffer().writeShort(
                LostTalesChatPresenceSyncPacket.MAX_ENTRIES + 1));
        ByteBuf trailing = Unpooled.buffer().writeShort(0).writeByte(0);
        assertBadRoster(trailing);
    }

    private static void writeEntry(ByteBuf buffer, UUID account, int code) {
        buffer.writeLong(account.getMostSignificantBits());
        buffer.writeLong(account.getLeastSignificantBits());
        buffer.writeByte(code);
    }

    private static void assertBadChoice(ByteBuf wire) {
        LostTalesChatPresencePacket decoded = new LostTalesChatPresencePacket();
        decoded.fromBytes(wire);
        assertTrue(decoded.isMalformed());
        assertEquals(ChatPresence.ONLINE, decoded.getPresence());
        assertEquals(0, wire.readableBytes());
    }

    private static void assertBadRoster(ByteBuf wire) {
        LostTalesChatPresenceSyncPacket decoded = new LostTalesChatPresenceSyncPacket();
        decoded.fromBytes(wire);
        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getEntries().isEmpty());
        assertEquals(0, wire.readableBytes());
    }
}
