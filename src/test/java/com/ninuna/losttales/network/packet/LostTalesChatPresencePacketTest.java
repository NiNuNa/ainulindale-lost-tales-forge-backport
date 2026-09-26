package com.ninuna.losttales.network.packet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.chat.ChatRoleplayStatus;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

/**
 * A player's stated presence and the server's word on what accounts
 * show both survive the wire, role-play statuses with them; anything
 * outside their bounds is refused whole, and neither carries a status it
 * has no business carrying.
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
        Map<ChatPresenceIdentity, String> lines =
                new LinkedHashMap<ChatPresenceIdentity, String>();
        lines.put(ChatPresenceIdentity.character(ALDRIC), "Out  hunting \u00a7corcs ");
        // An identity may have a line and no choice of status.
        lines.put(ChatPresenceIdentity.character(ALEX), "Brewing");
        lines.put(ChatPresenceIdentity.ACCOUNT, "   ");
        Map<ChatPresenceIdentity, ChatRoleplayStatus> roleplay =
                new LinkedHashMap<ChatPresenceIdentity, ChatRoleplayStatus>();
        roleplay.put(ChatPresenceIdentity.ACCOUNT,
                ChatRoleplayStatus.LOOKING_FOR_SCENE);
        // An identity's own default is no choice, and does not travel.
        roleplay.put(ChatPresenceIdentity.character(ALDRIC),
                ChatRoleplayStatus.IN_CHARACTER);
        roleplay.put(ChatPresenceIdentity.character(ALEX),
                ChatRoleplayStatus.OUT_OF_CHARACTER);
        ByteBuf wire = Unpooled.buffer();
        new LostTalesChatPresencePacket(true, choices, lines, roleplay)
                .toBytes(wire);
        LostTalesChatPresencePacket decoded = new LostTalesChatPresencePacket();
        decoded.fromBytes(wire);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.isIdle());
        assertEquals(choices, decoded.getChoices());
        assertEquals(ChatPresenceIdentity.ACCOUNT,
                decoded.getChoices().keySet().iterator().next());
        // Lines travel cleaned, and an empty one not at all.
        assertEquals(2, decoded.getLines().size());
        assertEquals("Out hunting orcs",
                decoded.getLines().get(ChatPresenceIdentity.character(ALDRIC)));
        assertEquals("Brewing",
                decoded.getLines().get(ChatPresenceIdentity.character(ALEX)));
        assertEquals(2, decoded.getRoleplay().size());
        assertEquals(ChatRoleplayStatus.LOOKING_FOR_SCENE,
                decoded.getRoleplay().get(ChatPresenceIdentity.ACCOUNT));
        assertEquals(ChatRoleplayStatus.OUT_OF_CHARACTER, decoded.getRoleplay()
                .get(ChatPresenceIdentity.character(ALEX)));
    }

    @Test
    public void offlineIsNeverSentAsAChoice() {
        LostTalesChatPresencePacket packet = new LostTalesChatPresencePacket(
                false, Collections.singletonMap(ChatPresenceIdentity.ACCOUNT,
                        ChatPresence.OFFLINE), null, null);
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
        // An empty line.
        assertBadStatement(Unpooled.buffer().writeByte(0).writeByte(0)
                .writeByte(1)
                .writeByte(LostTalesChatPresencePacket.KIND_ACCOUNT)
                .writeByte(0));
        // One identity's line twice.
        assertBadStatement(Unpooled.buffer().writeByte(0).writeByte(0)
                .writeByte(2)
                .writeByte(LostTalesChatPresencePacket.KIND_ACCOUNT)
                .writeByte(1).writeByte('a')
                .writeByte(LostTalesChatPresencePacket.KIND_ACCOUNT)
                .writeByte(1).writeByte('b'));
        // A line longer than a line may be.
        ByteBuf long_ = Unpooled.buffer().writeByte(0).writeByte(0)
                .writeByte(1)
                .writeByte(LostTalesChatPresencePacket.KIND_ACCOUNT);
        int over = com.ninuna.losttales.chat.ChatStatusLine.MAX_BYTES + 1;
        long_.writeByte(0x80 | (over & 0x7F)).writeByte(over >>> 7);
        long_.writeBytes(new byte[over]);
        assertBadStatement(long_);
        // A role-play status this build does not know.
        assertBadStatement(Unpooled.buffer().writeByte(0).writeByte(0)
                .writeByte(0).writeByte(1)
                .writeByte(LostTalesChatPresencePacket.KIND_ACCOUNT)
                .writeByte(9));
        // Something after the end.
        assertBadStatement(Unpooled.buffer().writeByte(0).writeByte(0)
                .writeByte(0).writeByte(0).writeByte(0));
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
        Map<ChatPresenceIdentity, String> steveLines =
                new LinkedHashMap<ChatPresenceIdentity, String>();
        steveLines.put(ChatPresenceIdentity.character(ALDRIC), "Out hunting");
        // A line of an identity not shown is not told.
        steveLines.put(ChatPresenceIdentity.character(ALEX), "Hidden");
        ByteBuf wire = Unpooled.buffer();
        new LostTalesChatPresenceSyncPacket(accounts,
                Collections.singletonMap(STEVE, steveLines),
                Collections.singletonMap(STEVE, Collections.singletonMap(
                        ChatPresenceIdentity.character(ALDRIC),
                        ChatRoleplayStatus.LOOKING_FOR_SCENE))).toBytes(wire);
        LostTalesChatPresenceSyncPacket decoded = new LostTalesChatPresenceSyncPacket();
        decoded.fromBytes(wire);
        assertFalse(decoded.isMalformed());
        assertEquals(accounts, decoded.getAccounts());
        assertEquals(STEVE, decoded.getAccounts().keySet().iterator().next());
        assertEquals(Collections.singletonMap(
                        ChatPresenceIdentity.character(ALDRIC), "Out hunting"),
                decoded.getLines().get(STEVE));
        assertTrue(decoded.getLines().get(ALEX).isEmpty());
        // Every identity shown travels with its status, its default where
        // none was given.
        assertEquals(ChatRoleplayStatus.LOOKING_FOR_SCENE, decoded
                .getRoleplay().get(STEVE).get(ChatPresenceIdentity.character(
                        ALDRIC)));
        assertEquals(ChatRoleplayStatus.OUT_OF_CHARACTER, decoded
                .getRoleplay().get(STEVE).get(ChatPresenceIdentity.ACCOUNT));
        assertTrue(decoded.getRoleplay().get(ALEX).isEmpty());
    }

    /** A full batch stays inside what one payload may carry. */
    @Test
    public void aFullBatchFitsOnePayload() {
        StringBuilder widest = new StringBuilder();
        for (int index = 0;
                index < com.ninuna.losttales.chat.ChatStatusLine.MAX_CHARACTERS;
                index++) {
            widest.append('\u2603');
        }
        Map<UUID, Map<ChatPresenceIdentity, ChatPresence>> accounts =
                new LinkedHashMap<UUID, Map<ChatPresenceIdentity, ChatPresence>>();
        Map<UUID, Map<ChatPresenceIdentity, String>> lines =
                new LinkedHashMap<UUID, Map<ChatPresenceIdentity, String>>();
        for (int account = 0;
                account < LostTalesChatPresenceSyncPacket.MAX_ACCOUNTS;
                account++) {
            Map<ChatPresenceIdentity, ChatPresence> shown =
                    new LinkedHashMap<ChatPresenceIdentity, ChatPresence>();
            Map<ChatPresenceIdentity, String> said =
                    new LinkedHashMap<ChatPresenceIdentity, String>();
            for (int identity = 0;
                    identity < LostTalesChatPresenceSyncPacket.MAX_SHOWN;
                    identity++) {
                ChatPresenceIdentity who = ChatPresenceIdentity.character(
                        new UUID(account, identity));
                shown.put(who, ChatPresence.ONLINE);
                said.put(who, widest.toString());
            }
            UUID id = new UUID(account, 99L);
            accounts.put(id, shown);
            lines.put(id, said);
        }
        ByteBuf wire = Unpooled.buffer();
        new LostTalesChatPresenceSyncPacket(accounts, lines, null)
                .toBytes(wire);
        assertTrue(wire.readableBytes() < 32767);
        LostTalesChatPresenceSyncPacket decoded = new LostTalesChatPresenceSyncPacket();
        decoded.fromBytes(wire);
        assertFalse(decoded.isMalformed());
    }

    @Test
    public void invisibleAndOfflineNeverTravel() {
        Map<ChatPresenceIdentity, ChatPresence> shown =
                new LinkedHashMap<ChatPresenceIdentity, ChatPresence>();
        shown.put(ChatPresenceIdentity.ACCOUNT, ChatPresence.INVISIBLE);
        shown.put(ChatPresenceIdentity.character(ALDRIC), ChatPresence.OFFLINE);
        LostTalesChatPresenceSyncPacket packet = new LostTalesChatPresenceSyncPacket(
                Collections.singletonMap(STEVE, shown), null, null);
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
        repeated.writeByte(LostTalesChatPresencePacket.KIND_ACCOUNT)
                .writeByte(ChatPresence.ONLINE.code()).writeByte(0);
        repeated.writeByte(LostTalesChatPresencePacket.KIND_ACCOUNT)
                .writeByte(ChatPresence.AWAY.code()).writeByte(0);
        assertBadSync(repeated);
        ByteBuf crowded = Unpooled.buffer().writeShort(1);
        writeAccount(crowded, STEVE, LostTalesChatPresenceSyncPacket.MAX_SHOWN + 1);
        assertBadSync(crowded);
        assertBadSync(Unpooled.buffer().writeShort(
                LostTalesChatPresenceSyncPacket.MAX_ACCOUNTS + 1));
        assertBadSync(Unpooled.buffer().writeShort(0).writeByte(0));
        ByteBuf unknown = Unpooled.buffer().writeShort(1);
        writeAccount(unknown, STEVE, 1);
        unknown.writeByte(LostTalesChatPresencePacket.KIND_ACCOUNT)
                .writeByte(ChatPresence.ONLINE.code()).writeByte(0)
                .writeByte(9);
        assertBadSync(unknown);
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
