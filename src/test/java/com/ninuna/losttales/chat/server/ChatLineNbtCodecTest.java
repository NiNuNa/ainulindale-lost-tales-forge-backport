package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A kept line comes back from the save as the very line it was, byte for
 * byte on the wire; its roles are kept by id, so a reordered roles.cfg
 * signs it with the same roles and a role gone is left off.
 */
public final class ChatLineNbtCodecTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID ALDRIC = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BEREN = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Before
    public void setUp() {
        ChatRoleCatalog.installServer(catalogue("moderator", "herald"));
    }

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    @Test
    public void aWhisperWithAQuoteComesBackAsItWas() {
        LostTalesChatMessagePacket whisper = new LostTalesChatMessagePacket(
                ChatChannel.WHISPER, ALICE, "Aldric", "alice", "", 0x112233,
                0x445566, "@Beren well met", 1000000L, "skin", null, "", "bob",
                0, false, 42L, ChatReplyReference.of(41L, "Beren", "hail", 0x778899)
                        .withHead(BOB, false, "bskin").asAction(true),
                "Beren", 7L, ALDRIC)
                .withConversation(ALDRIC, BEREN)
                .withAction(true)
                .withNamedPlayers(Collections.singletonList(
                        new ChatNamedPlayer(BOB, "bob", BEREN, "Beren", "bskin")))
                .withMentionedRoles(Arrays.asList("herald"));

        assertSameOnTheWire(whisper, ChatLineNbtCodec.read(ChatLineNbtCodec.write(whisper)));
    }

    /** What the author typed and whether the character was in the world are kept with the line. */
    @Test
    public void whatWasTypedAndTheWorldMarkComeBack() {
        LostTalesChatMessagePacket line = new LostTalesChatMessagePacket(
                ChatChannel.GLOBAL, ALICE, "Beren", "alice", "", 0, 0,
                "hello there", 1000000L, "skin", null, "", "", 0, false,
                43L, ChatReplyReference.NONE, "", 0L, BEREN)
                .withNotInWorld(true)
                .withTyped("hello there :)");
        LostTalesChatMessagePacket read = ChatLineNbtCodec.read(ChatLineNbtCodec.write(line));
        assertSameOnTheWire(line, read);
        assertEquals("hello there :)", read.getTyped());
        assertTrue(read.isNotInWorld());
    }

    @Test
    public void aForwardAndAMarkerComeBackAsTheyWere() {
        LostTalesChatMessagePacket forward = new LostTalesChatMessagePacket(
                ChatChannel.GLOBAL, ALICE, "Aldric", "alice", "", 0, 0,
                "the gate [m:Bree]", 1000000L, "", Collections.singletonList(
                        ChatShowcase.marker(0, "waypoint:bree", "Bree", "flag",
                                "green", 100, 12.5D, -40.25D)),
                "", "", 0, false, 43L,
                ChatReplyReference.forward(40L, "Beren", 0x778899, "#ooc/40")
                        .withHead(BOB, true, ""),
                "", 0L, ALDRIC);

        assertSameOnTheWire(forward, ChatLineNbtCodec.read(ChatLineNbtCodec.write(forward)));
    }

    @Test
    public void rolesAreKeptByIdWhateverTheirOrder() {
        int herald = ChatRoleCatalog.server().byId("herald").bit();
        LostTalesChatMessagePacket signed = new LostTalesChatMessagePacket(
                ChatChannel.OOC, ALICE, "alice", "alice", "", 0, 0, "hello",
                1000000L, "", null, "", "", herald, true, 44L,
                ChatReplyReference.NONE, "", 0L, null);
        NBTTagCompound kept = ChatLineNbtCodec.write(signed);

        ChatRoleCatalog.installServer(catalogue("herald", "moderator"));
        LostTalesChatMessagePacket read = ChatLineNbtCodec.read(kept);

        int heraldNow = ChatRoleCatalog.server().byId("herald").bit();
        assertTrue("the bits moved", herald != heraldNow);
        assertEquals(heraldNow, read.getRoles());
    }

    @Test
    public void aRoleTheServerNoLongerHasIsLeftOff() {
        int herald = ChatRoleCatalog.server().byId("herald").bit();
        LostTalesChatMessagePacket signed = new LostTalesChatMessagePacket(
                ChatChannel.OOC, ALICE, "alice", "alice", "", 0, 0,
                "@herald @moderator", 1000000L, "", null, "", "", herald, true, 45L,
                ChatReplyReference.NONE, "", 0L, null)
                .withMentionedRoles(Arrays.asList("herald", "moderator"));
        NBTTagCompound kept = ChatLineNbtCodec.write(signed);

        ChatRoleCatalog.installServer(catalogue("moderator"));
        LostTalesChatMessagePacket read = ChatLineNbtCodec.read(kept);

        assertEquals(0, read.getRoles());
        assertEquals(Collections.singletonList("moderator"), read.getMentionedRoles());
    }

    @Test
    public void aLineMissingAFieldOrRefusedByTheWireIsNoLine() {
        LostTalesChatMessagePacket line = new LostTalesChatMessagePacket(
                ChatChannel.GLOBAL, ALICE, "Aldric", "alice", "", 0, 0, "hail",
                1000000L, "", null, "", "", 0, false, 46L,
                ChatReplyReference.NONE, "", 0L, ALDRIC);
        NBTTagCompound missing = ChatLineNbtCodec.write(line);
        missing.removeTag("Skin");
        assertNull(ChatLineNbtCodec.read(missing));

        NBTTagCompound unknownChannel = ChatLineNbtCodec.write(line);
        unknownChannel.setString("Channel", "no_such_channel");
        assertNull(ChatLineNbtCodec.read(unknownChannel));

        NBTTagCompound empty = ChatLineNbtCodec.write(line);
        empty.setString("Message", "");
        assertNull(ChatLineNbtCodec.read(empty));
    }

    private static void assertSameOnTheWire(LostTalesChatMessagePacket expected,
                                            LostTalesChatMessagePacket actual) {
        assertArrayEquals(bytesOf(expected), bytesOf(actual));
    }

    private static byte[] bytesOf(LostTalesChatMessagePacket line) {
        ByteBuf buffer = Unpooled.buffer();
        line.toBytes(buffer);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    private static ChatRoleCatalog catalogue(String... ids) {
        ChatAccountRole[] roles = new ChatAccountRole[ids.length];
        for (int index = 0; index < ids.length; index++) {
            roles[index] = ChatAccountRole.custom(ids[index], "", "", 0xA94B54, true,
                    15 + index, null, null, null);
        }
        return ChatRoleCatalog.of(Arrays.asList(roles), null, null);
    }
}
