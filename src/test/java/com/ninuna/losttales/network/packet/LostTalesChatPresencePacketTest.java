package com.ninuna.losttales.network.packet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

/**
 * A player's stated presence and the server's word on what accounts
 * show both survive the wire; anything outside their bounds is refused
 * whole, and neither carries a status it has no business carrying.
 */
public final class LostTalesChatPresencePacketTest {
    private static final UUID STEVE = new UUID(1L, 2L);
    private static final UUID ALEX = new UUID(3L, 4L);
    private static final UUID ALDRIC = new UUID(5L, 6L);

    @Test
    public void aStatementRoundTripsInOrder() {
        Map<ChatPresenceIdentity, ChatPresence> choices =
                new LinkedHashMap<ChatPresenceIdentity, ChatPresence>();
        choices.put(ChatPresenceIdentity.ACCOUNT, ChatPresence.DO_NOT_DISTURB);
        choices.put(ChatPresenceIdentity.character(ALDRIC),
                ChatPresence.INVISIBLE);
        // The default character's id is the account's own, and it is
        // still an identity of its own.
        choices.put(ChatPresenceIdentity.character(STEVE), ChatPresence.AWAY);
        ByteBuf wire = Unpooled.buffer();
        new LostTalesChatPresencePacket(true, choices).toBytes(wire);
        LostTalesChatPresencePacket decoded = new LostTalesChatPresencePacket();
        decoded.fromBytes(wire);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.isIdle());
        assertEquals(choices, decoded.getChoices());
        assertEquals(ChatPresenceIdentity.ACCOUNT,
                decoded.getChoices().keySet().iterator().next());
    }

    @Test
    public void offlineIsNeverSentAsAChoice() {
        LostTalesChatPresencePacket packet = new LostTalesChatPresencePacket(
                false, Collections.singletonMap(ChatPresenceIdentity.ACCOUNT,
                        ChatPresence.OFFLINE));
        assertTrue(packet.getChoices().isEmpty());
    }

    @Test
    public void aBadStatementIsMalformed() {
        assertBadStatement(Unpooled.buffer());
        // An unknown flag.
        assertBadStatement(Unpooled.buffer().writeByte(2).writeByte(0));
        // Offline chosen.
        assertBadStatement(Unpooled.buffer().writeByte(0).writeByte(1)
                .writeByte(LostTalesChatPresencePacket.KIND_ACCOUNT)
                .writeByte(ChatPresence.OFFLINE.code()));
        // An unknown identity kind.
        assertBadStatement(Unpooled.buffer().writeByte(0).writeByte(1)
                .writeByte(7).writeByte(0));
        // One identity twice.
        assertBadStatement(Unpooled.buffer().writeByte(0).writeByte(2)
                .writeByte(LostTalesChatPresencePacket.KIND_ACCOUNT).writeByte(1)
                .writeByte(LostTalesChatPresencePacket.KIND_ACCOUNT).writeByte(2));
        // More than a payload may carry.
        assertBadStatement(Unpooled.buffer().writeByte(0)
                .writeByte(LostTalesChatPresencePacket.MAX_CHOICES + 1));
        // Something after the end.
        assertBadStatement(Unpooled.buffer().writeByte(0).writeByte(0)
                .writeByte(0));
    }

    @Test
    public void whatAccountsShowRoundTripsInOrder() {
        Map<ChatPresenceIdentity, ChatPresence> steve =
                new LinkedHashMap<ChatPresenceIdentity, ChatPresence>();
        steve.put(ChatPresenceIdentity.ACCOUNT, ChatPresence.ONLINE);
        steve.put(ChatPresenceIdentity.character(ALDRIC), ChatPresence.AWAY);
        Map<UUID, Map<ChatPresenceIdentity, ChatPresence>> accounts =
                new LinkedHashMap<UUID, Map<ChatPresenceIdentity, ChatPresence>>();
        accounts.put(STEVE, steve);
        // An account showing nothing: it left, or hides everywhere.
        accounts.put(ALEX, Collections.<ChatPresenceIdentity, ChatPresence>emptyMap());
        ByteBuf wire = Unpooled.buffer();
        new LostTalesChatPresenceSyncPacket(accounts).toBytes(wire);
        LostTalesChatPresenceSyncPacket decoded = new LostTalesChatPresenceSyncPacket();
        decoded.fromBytes(wire);
        assertFalse(decoded.isMalformed());
        assertEquals(accounts, decoded.getAccounts());
        assertEquals(STEVE, decoded.getAccounts().keySet().iterator().next());
    }

    @Test
    public void invisibleAndOfflineNeverTravel() {
        Map<ChatPresenceIdentity, ChatPresence> shown =
                new LinkedHashMap<ChatPresenceIdentity, ChatPresence>();
        shown.put(ChatPresenceIdentity.ACCOUNT, ChatPresence.INVISIBLE);
        shown.put(ChatPresenceIdentity.character(ALDRIC), ChatPresence.OFFLINE);
        LostTalesChatPresenceSyncPacket packet = new LostTalesChatPresenceSyncPacket(
                Collections.singletonMap(STEVE, shown));
        assertTrue(packet.getAccounts().get(STEVE).isEmpty());
    }

    @Test
    public void aBadSyncIsRefusedWhole() {
        ByteBuf twice = Unpooled.buffer().writeShort(2);
        writeAccount(twice, STEVE, 0);
        writeAccount(twice, STEVE, 0);
        assertBadSync(twice);
        ByteBuf invisible = Unpooled.buffer().writeShort(1);
        writeAccount(invisible, STEVE, 1);
        invisible.writeByte(LostTalesChatPresencePacket.KIND_ACCOUNT);
        invisible.writeByte(ChatPresence.INVISIBLE.code());
        assertBadSync(invisible);
        ByteBuf repeated = Unpooled.buffer().writeShort(1);
        writeAccount(repeated, STEVE, 2);
        repeated.writeByte(LostTalesChatPresencePacket.KIND_ACCOUNT).writeByte(0);
        repeated.writeByte(LostTalesChatPresencePacket.KIND_ACCOUNT).writeByte(1);
        assertBadSync(repeated);
        ByteBuf crowded = Unpooled.buffer().writeShort(1);
        writeAccount(crowded, STEVE, LostTalesChatPresenceSyncPacket.MAX_SHOWN + 1);
        assertBadSync(crowded);
        assertBadSync(Unpooled.buffer().writeShort(
                LostTalesChatPresenceSyncPacket.MAX_ACCOUNTS + 1));
        assertBadSync(Unpooled.buffer().writeShort(0).writeByte(0));
    }

    private static void writeAccount(ByteBuf buffer, UUID account, int shown) {
        buffer.writeLong(account.getMostSignificantBits());
        buffer.writeLong(account.getLeastSignificantBits());
        buffer.writeByte(shown);
    }

    private static void assertBadStatement(ByteBuf wire) {
        LostTalesChatPresencePacket decoded = new LostTalesChatPresencePacket();
        decoded.fromBytes(wire);
        assertTrue(decoded.isMalformed());
        assertFalse(decoded.isIdle());
        assertTrue(decoded.getChoices().isEmpty());
        assertEquals(0, wire.readableBytes());
    }

    private static void assertBadSync(ByteBuf wire) {
        LostTalesChatPresenceSyncPacket decoded = new LostTalesChatPresenceSyncPacket();
        decoded.fromBytes(wire);
        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getAccounts().isEmpty());
        assertEquals(0, wire.readableBytes());
    }
}
