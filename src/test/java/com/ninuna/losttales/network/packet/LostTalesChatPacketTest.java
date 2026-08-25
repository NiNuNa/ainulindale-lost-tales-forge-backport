package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatRecipientRule;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.chat.share.ChatShowcase;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LostTalesChatPacketTest {

    @Test
    public void channelCatalogueHasStableSemantics() {
        assertEquals(ChatIdentityType.CHARACTER,
                ChatChannel.ALL.getIdentityType());
        assertEquals(ChatRecipientRule.PROXIMITY,
                ChatChannel.PROXIMITY.getRecipientRule());
        assertEquals(ChatRecipientRule.PARTY,
                ChatChannel.PARTY.getRecipientRule());
        assertEquals(ChatRecipientRule.FACTION,
                ChatChannel.FACTION.getRecipientRule());
        assertEquals(ChatIdentityType.ACCOUNT,
                ChatChannel.OOC.getIdentityType());
        assertEquals(ChatChannel.PROXIMITY,
                ChatChannel.fromId("Proximity"));
        assertNull(ChatChannel.fromId("trade"));
        assertEquals("Global", ChatChannel.ALL.getDisplayName());
        assertEquals("all", ChatChannel.ALL.getId());
    }

    @Test
    public void sendRequestRoundTripsAndRejectsTrailingData() {
        LostTalesChatSendPacket original =
                new LostTalesChatSendPacket(
                        ChatChannel.PARTY, "Meet at the western gate.");
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatSendPacket decoded =
                new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(ChatChannel.PARTY, decoded.getChannel());
        assertEquals("Meet at the western gate.", decoded.getMessage());
        assertTrue(decoded.getReferences().isEmpty());

        ByteBuf trailing = Unpooled.buffer();
        original.toBytes(trailing);
        trailing.writeByte(1);
        LostTalesChatSendPacket rejected =
                new LostTalesChatSendPacket();
        rejected.fromBytes(trailing);
        assertTrue(rejected.isMalformed());
    }

    @Test
    public void sendRequestCarriesTheAskedForAppearance() {
        assertEquals(LostTalesChatSendPacket.APPEARANCE_DEFAULT,
                new LostTalesChatSendPacket(ChatChannel.ALL, "hello")
                        .getAppearanceKind());
        java.util.UUID characterId = java.util.UUID.randomUUID();
        LostTalesChatSendPacket original = new LostTalesChatSendPacket(
                ChatChannel.OOC, "hello", null, "",
                LostTalesChatSendPacket.APPEARANCE_CHARACTER, characterId);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(LostTalesChatSendPacket.APPEARANCE_CHARACTER,
                decoded.getAppearanceKind());
        assertEquals(characterId, decoded.getAppearanceCharacterId());

        LostTalesChatSendPacket account = new LostTalesChatSendPacket(
                ChatChannel.ALL, "hello", null, "",
                LostTalesChatSendPacket.APPEARANCE_ACCOUNT, null);
        ByteBuf accountBuffer = Unpooled.buffer();
        account.toBytes(accountBuffer);
        LostTalesChatSendPacket decodedAccount =
                new LostTalesChatSendPacket();
        decodedAccount.fromBytes(accountBuffer);
        assertFalse(decodedAccount.isMalformed());
        assertEquals(LostTalesChatSendPacket.APPEARANCE_ACCOUNT,
                decodedAccount.getAppearanceKind());
        assertNull(decodedAccount.getAppearanceCharacterId());

        // A kind the catalogue does not know is malformed on arrival.
        ByteBuf badKind = Unpooled.buffer();
        account.toBytes(badKind);
        badKind.setByte(badKind.writerIndex() - 1, 9);
        LostTalesChatSendPacket rejected = new LostTalesChatSendPacket();
        rejected.fromBytes(badKind);
        assertTrue(rejected.isMalformed());
    }

    @Test
    public void sendRequestCarriesBoundedShareReferences() {
        LostTalesChatSendPacket original = new LostTalesChatSendPacket(
                ChatChannel.ALL, "see [i:Sword] [m:Bree] [i:Bow]",
                Arrays.asList(ChatShareReference.item(4),
                        ChatShareReference.marker("losttales:bree"),
                        ChatShareReference.unresolved(ChatShareKind.ITEM)));
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(3, decoded.getReferences().size());
        assertEquals(4, decoded.getReferences().get(0).getSlot());
        assertEquals("losttales:bree",
                decoded.getReferences().get(1).getMarkerId());
        assertEquals(ChatShareKind.MARKER,
                decoded.getReferences().get(1).getKind());
        assertFalse(decoded.getReferences().get(2).isResolved());

        boolean rejectedSlot = false;
        try {
            ChatShareReference.item(40);
        } catch (IllegalArgumentException expected) {
            rejectedSlot = true;
        }
        assertTrue(rejectedSlot);

        // A hand-built payload with an out-of-range slot is discarded.
        ByteBuf forged = Unpooled.buffer();
        new LostTalesChatSendPacket(ChatChannel.ALL, "[i:Sword]")
                .toBytes(forged);
        forged.writerIndex(forged.writerIndex() - 1);
        forged.writeByte(1);
        forged.writeByte('i');
        forged.writeByte(77);
        LostTalesChatSendPacket rejected = new LostTalesChatSendPacket();
        rejected.fromBytes(forged);
        assertTrue(rejected.isMalformed());
        assertTrue(rejected.getReferences().isEmpty());
    }

    @Test
    public void presentationCarriesValidatedShowcasesAndRejectsBadOnes() {
        byte[] data = new byte[] {31, -117, 8, 0, 1, 2, 3, 4};
        LostTalesChatMessagePacket original =
                new LostTalesChatMessagePacket(
                        ChatChannel.OOC, UUID.randomUUID(), "Steve",
                        "Steve", "", 0xFFFFFF, 0xFFFFFF,
                        "look [i:Sword] near [m:Bree]", 5L, "",
                        Arrays.asList(ChatShowcase.item(0, data),
                                ChatShowcase.marker(1, "losttales:bree",
                                        "Bree", "town", "orange", 100,
                                        512.5D, -384.0D)));
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(2, decoded.getShowcases().size());
        assertEquals(ChatShareKind.ITEM,
                decoded.getShowcases().get(0).getKind());
        assertTrue(Arrays.equals(data,
                decoded.getShowcases().get(0).getStackData()));
        ChatShowcase marker = decoded.getShowcases().get(1);
        assertEquals(ChatShareKind.MARKER, marker.getKind());
        assertEquals("losttales:bree", marker.getMarkerId());
        assertEquals("Bree", marker.getMarkerName());
        assertEquals("town", marker.getMarkerIcon());
        assertEquals(100, marker.getMarkerDimension());
        assertEquals(512.5D, marker.getMarkerX(), 0.0D);
        assertEquals(-384.0D, marker.getMarkerZ(), 0.0D);

        // A showcase whose kind does not match its token is refused.
        boolean rejectedKind = false;
        try {
            new LostTalesChatMessagePacket(
                    ChatChannel.OOC, UUID.randomUUID(), "Steve",
                    "Steve", "", 0xFFFFFF, 0xFFFFFF,
                    "only [m:Bree]", 5L, "",
                    Arrays.asList(ChatShowcase.item(0, data)));
        } catch (IllegalArgumentException expected) {
            rejectedKind = true;
        }
        assertTrue(rejectedKind);

        boolean rejectedIndex = false;
        try {
            new LostTalesChatMessagePacket(
                    ChatChannel.OOC, UUID.randomUUID(), "Steve",
                    "Steve", "", 0xFFFFFF, 0xFFFFFF,
                    "only [i:Sword]", 5L, "",
                    Arrays.asList(ChatShowcase.item(1, data)));
        } catch (IllegalArgumentException expected) {
            rejectedIndex = true;
        }
        assertTrue(rejectedIndex);

        boolean rejectedSize = false;
        try {
            ChatShowcase.item(0, new byte[ChatShowcase.MAX_STACK_BYTES + 1]);
        } catch (IllegalArgumentException expected) {
            rejectedSize = true;
        }
        assertTrue(rejectedSize);

        boolean rejectedCoordinate = false;
        try {
            ChatShowcase.marker(0, "id", "Name", "", "", 0,
                    Double.NaN, 0.0D);
        } catch (IllegalArgumentException expected) {
            rejectedCoordinate = true;
        }
        assertTrue(rejectedCoordinate);
    }

    @Test
    public void whisperPacketsCarryTheirTargetAndPartner() {
        LostTalesChatSendPacket send = new LostTalesChatSendPacket(
                ChatChannel.WHISPER, "psst", null, " Steve ");
        ByteBuf buffer = Unpooled.buffer();
        send.toBytes(buffer);
        LostTalesChatSendPacket decodedSend = new LostTalesChatSendPacket();
        decodedSend.fromBytes(buffer);
        assertFalse(decodedSend.isMalformed());
        assertEquals("Steve", decodedSend.getTarget());
        assertEquals(ChatChannel.WHISPER, decodedSend.getChannel());
        // A whisper without a target is refused; other channels need none.
        boolean rejected = false;
        try {
            new LostTalesChatSendPacket(ChatChannel.WHISPER, "psst", null, "");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected);
        assertEquals("", new LostTalesChatSendPacket(ChatChannel.OOC, "hi")
                .getTarget());

        LostTalesChatMessagePacket message = new LostTalesChatMessagePacket(
                ChatChannel.WHISPER, UUID.randomUUID(), "Alex", "Alex", "",
                0xFFFFFF, 0xFFFFFF, "psst", 5L, "", null, "", "Steve");
        buffer = Unpooled.buffer();
        message.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals("Steve", decoded.getPartner());
        assertEquals("", new LostTalesChatMessagePacket(
                ChatChannel.OOC, UUID.randomUUID(), "Alex", "Alex", "",
                0xFFFFFF, 0xFFFFFF, "hi", 5L, "").getPartner());
    }

    @Test
    public void messageValidationRejectsFormattingAndControlText() {
        assertTrue(ChatMessageValidator.isValid("Mae govannen!"));
        assertFalse(ChatMessageValidator.isValid(" padded "));
        assertFalse(ChatMessageValidator.isValid("colored §cmessage"));
        assertFalse(ChatMessageValidator.isValid("line\nbreak"));

        StringBuilder oversized = new StringBuilder();
        for (int index = 0;
             index <= ChatMessageValidator.MAX_CHARACTERS; index++) {
            oversized.append('x');
        }
        assertFalse(ChatMessageValidator.isValid(oversized.toString()));
    }

    @Test
    public void shareTokensCountAsOneVisibleCharacter() {
        StringBuilder filler = new StringBuilder();
        for (int index = 0;
             index < ChatMessageValidator.MAX_CHARACTERS - 1; index++) {
            filler.append('x');
        }
        String withToken = filler + "[m:Northgate Test City]";
        assertEquals(ChatMessageValidator.MAX_CHARACTERS,
                ChatMessageValidator.visibleLength(withToken));
        assertTrue(ChatMessageValidator.isValid(withToken));
        assertFalse(ChatMessageValidator.isValid(withToken + "y"));
        assertTrue(withToken.length() > ChatMessageValidator.MAX_CHARACTERS);
        assertEquals(5, ChatMessageValidator.visibleLength("ab[i:Sword]cd"));

        LostTalesChatSendPacket packet = new LostTalesChatSendPacket(
                ChatChannel.OOC, withToken,
                Arrays.asList(ChatShareReference.marker("losttales:x")));
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(withToken, decoded.getMessage());
    }
}
