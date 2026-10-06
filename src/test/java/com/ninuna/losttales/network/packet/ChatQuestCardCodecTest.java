package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.share.ChatQuestCard;
import com.ninuna.losttales.chat.share.ChatShowcase;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A quest card on the wire, which is also the chat history's save format:
 * each field read back as written, and a card out of any bound, with a
 * code it does not know, a count it cannot have or bytes left over,
 * refused whole.
 */
public final class ChatQuestCardCodecTest {

    private static ChatQuestCard bundledCard() {
        Map<String, String> targets = new LinkedHashMap<String, String>();
        targets.put("entity", "losttales.Nia");
        targets.put("item", "minecraft:stick");
        Map<String, String> rewards = new LinkedHashMap<String, String>();
        rewards.put("experience", "20");
        rewards.put("items", "minecraft:bread*2,minecraft:emerald*1");
        return new ChatQuestCard(ChatQuestCard.Source.BUNDLED, "", "tutorials",
                Arrays.asList(new ChatQuestCard.Objective("give_sticks_to_nia",
                        "deliver", targets, 4, 1, false, "")), rewards);
    }

    @Test
    public void aCardReadsBackAsItWasWritten() {
        ByteBuf buffer = Unpooled.buffer();
        LostTalesChatMessagePacket.writeQuestCard(buffer, bundledCard());
        ChatQuestCard read = LostTalesChatMessagePacket.readQuestCard(buffer);
        assertEquals(0, buffer.readableBytes());
        assertEquals(ChatQuestCard.Source.BUNDLED, read.getSource());
        assertEquals("", read.getTitle());
        assertEquals("tutorials", read.getCategory());
        ChatQuestCard.Objective objective = read.getObjectives().get(0);
        assertEquals("give_sticks_to_nia", objective.getId());
        assertEquals("deliver", objective.getType());
        assertEquals("losttales.Nia", objective.getTargets().get("entity"));
        assertEquals("minecraft:stick", objective.getTargets().get("item"));
        assertEquals(4, objective.getCount());
        assertEquals(1, objective.getProgress());
        assertEquals("20", read.getRewards().get("experience"));
        assertEquals(bundledCard().serializedBytes(), read.serializedBytes());
    }

    /** A whole line with a card, as the chat sends and keeps it, trailing bytes refused. */
    @Test
    public void aLineWithACardRoundTripsAndRefusesTrailingData() {
        LostTalesChatMessagePacket original = ChatPacketFixtures.line(
                ChatChannel.OOC, "Steve", "Steve", "see [q:Nia]").at(5L)
                .showcases(Collections.singletonList(ChatShowcase.quest(0,
                        "losttales:tutorial/meet_nia", bundledCard(), true)))
                .build();
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(ChatQuestCard.Source.BUNDLED, decoded.getShowcases()
                .get(0).getQuestCard().getSource());

        ByteBuf trailing = Unpooled.buffer();
        original.toBytes(trailing);
        trailing.writeByte(0);
        LostTalesChatMessagePacket refused = new LostTalesChatMessagePacket();
        refused.fromBytes(trailing);
        assertTrue(refused.isMalformed());
    }

    @Test
    public void aCardWithAnUnknownSourceIsRefused() {
        ByteBuf buffer = Unpooled.buffer();
        LostTalesChatMessagePacket.writeQuestCard(buffer, bundledCard());
        buffer.setByte(0, 9);
        assertRefused(buffer);
    }

    @Test
    public void aCardPastItsBoundsIsRefused() {
        ByteBuf many = cardHead();
        many.writeByte(ChatQuestCard.MAX_OBJECTIVES + 1);
        assertRefused(many);

        ByteBuf unknownTarget = cardHead();
        unknownTarget.writeByte(1);
        objectiveHead(unknownTarget);
        unknownTarget.writeByte(1);
        LostTalesPacketCodec.writeUtf8String(unknownTarget, "command", 32);
        LostTalesPacketCodec.writeUtf8String(unknownTarget, "op @a", 128);
        objectiveTail(unknownTarget, 4, 1);
        unknownTarget.writeByte(0);
        assertRefused(unknownTarget);

        ByteBuf overDone = cardHead();
        overDone.writeByte(1);
        objectiveHead(overDone);
        overDone.writeByte(0);
        objectiveTail(overDone, 4, 5);
        overDone.writeByte(0);
        assertRefused(overDone);

        ByteBuf longTitle = Unpooled.buffer();
        longTitle.writeByte(ChatQuestCard.Source.SERVER.getCode());
        StringBuilder title = new StringBuilder();
        while (title.length() <= ChatQuestCard.MAX_TITLE_BYTES) {
            title.append("Long ");
        }
        LostTalesPacketCodec.writeUtf8String(longTitle, title.toString(), 4096);
        assertRefused(longTitle);
    }

    @Test
    public void aCardOutOfItsBoundsIsNeverMade() {
        List<ChatQuestCard.Objective> none =
                Collections.<ChatQuestCard.Objective>emptyList();
        try {
            new ChatQuestCard.Objective("o", "gather",
                    Collections.singletonMap("item", ""), 3, 1, false, "");
            fail("an empty target");
        } catch (IllegalArgumentException expected) {
            // refused
        }
        try {
            new ChatQuestCard(ChatQuestCard.Source.LOTR, "", "", none,
                    Collections.singletonMap("", "x"));
            fail("a nameless reward");
        } catch (IllegalArgumentException expected) {
            // refused
        }
    }

    private static ByteBuf cardHead() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(ChatQuestCard.Source.BUNDLED.getCode());
        LostTalesPacketCodec.writeUtf8String(buffer, "", 256);
        LostTalesPacketCodec.writeUtf8String(buffer, "tutorials", 128);
        return buffer;
    }

    private static void objectiveHead(ByteBuf buffer) {
        LostTalesPacketCodec.writeUtf8String(buffer, "o", 64);
        LostTalesPacketCodec.writeUtf8String(buffer, "gather", 16);
    }

    private static void objectiveTail(ByteBuf buffer, int count, int progress) {
        buffer.writeInt(count);
        buffer.writeInt(progress);
        buffer.writeByte(0);
        LostTalesPacketCodec.writeUtf8String(buffer, "", 256);
    }

    private static void assertRefused(ByteBuf buffer) {
        try {
            LostTalesChatMessagePacket.readQuestCard(buffer);
            fail("the card was read");
        } catch (RuntimeException expected) {
            // a decode exception or a card refused by its own bounds
        }
    }
}
