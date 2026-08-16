package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatRecipientRule;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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

        ByteBuf trailing = Unpooled.buffer();
        original.toBytes(trailing);
        trailing.writeByte(1);
        LostTalesChatSendPacket rejected =
                new LostTalesChatSendPacket();
        rejected.fromBytes(trailing);
        assertTrue(rejected.isMalformed());
    }

    @Test
    public void presentationRoundTripsBoundedIdentity() {
        UUID sender = UUID.randomUUID();
        LostTalesChatMessagePacket original =
                new LostTalesChatMessagePacket(
                        ChatChannel.ALL, sender, "Arathorn",
                        "RangerOfTheNorth", "Ranger",
                        0x55AA55, 0x336633,
                        "The road is clear.", 123456789L,
                        "losttales:human_ranger_male_2");
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatMessagePacket decoded =
                new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(ChatChannel.ALL, decoded.getChannel());
        assertEquals(sender, decoded.getSenderId());
        assertEquals("Arathorn", decoded.getIdentityName());
        assertEquals("RangerOfTheNorth", decoded.getAccountName());
        assertEquals("Ranger", decoded.getTitle());
        assertEquals(0x55AA55, decoded.getTitleColor());
        assertEquals(0x336633, decoded.getNameColor());
        assertEquals("The road is clear.", decoded.getMessage());
        assertEquals(123456789L, decoded.getTimestampMillis());
        assertEquals("losttales:human_ranger_male_2",
                decoded.getSkinId());
    }

    @Test
    public void messageValidationRejectsFormattingAndControlText() {
        assertTrue(ChatMessageValidator.isValid("Mae govannen!"));
        assertFalse(ChatMessageValidator.isValid(" padded "));
        assertFalse(ChatMessageValidator.isValid("colored \u00a7cmessage"));
        assertFalse(ChatMessageValidator.isValid("line\nbreak"));

        StringBuilder oversized = new StringBuilder();
        for (int index = 0;
             index <= ChatMessageValidator.MAX_CHARACTERS; index++) {
            oversized.append('x');
        }
        assertFalse(ChatMessageValidator.isValid(oversized.toString()));
    }
}
