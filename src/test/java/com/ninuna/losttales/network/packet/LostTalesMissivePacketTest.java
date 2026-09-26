package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveObjectiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveRewardData;
import com.ninuna.losttales.quest.missive.MissiveBoardStateReason;
import com.ninuna.losttales.quest.missive.MissiveNotice;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The missive pages' packets: a board's notices going to the client, and
 * the board page's take and pin and both pages' accept coming back. Every
 * layout is exact; a payload of any other shape is malformed.
 */
public final class LostTalesMissivePacketTest {
    private static final String QUEST =
            "losttales:missive/generated/dim0_4_65_9/1200_3";

    private static LostTalesMissiveData missive(String questId) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("entity", "Zombie");
        params.put("count", "5");
        return LostTalesMissiveData.builder(questId, "kill")
                .title("Missive: Clear the Paths")
                .issuer("A Road Warden")
                .description("Travellers have reported zombies.")
                .flavorText("The roads must remain open.")
                .generationWorldTime(1200L)
                .timeLimitTicks(48000L)
                .context("board", "dim0_4_65_9")
                .objective(new LostTalesMissiveObjectiveData("kill_zombies",
                        "kill", "Defeat 5 zombies.", false, params))
                .rewardData(LostTalesMissiveRewardData.experienceAndItems(40,
                        "minecraft:emerald*2"))
                .build();
    }

    private static LostTalesMissiveBoardStatePacket state(
            MissiveBoardStateReason reason, MissiveNotice... notices) {
        return new LostTalesMissiveBoardStatePacket(0, 4, 65, 9, reason, 9,
                Arrays.asList(notices));
    }

    private static ByteBuf bytes(IMessage message) {
        ByteBuf buffer = Unpooled.buffer();
        message.toBytes(buffer);
        return buffer;
    }

    private static LostTalesMissiveBoardStatePacket decodeState(ByteBuf buffer) {
        LostTalesMissiveBoardStatePacket decoded =
                new LostTalesMissiveBoardStatePacket();
        decoded.fromBytes(buffer);
        return decoded;
    }

    private static LostTalesMissiveBoardRequestPacket decodeRequest(
            ByteBuf buffer) {
        LostTalesMissiveBoardRequestPacket decoded =
                new LostTalesMissiveBoardRequestPacket();
        decoded.fromBytes(buffer);
        return decoded;
    }

    private static LostTalesMissiveAcceptPacket decodeAccept(ByteBuf buffer) {
        LostTalesMissiveAcceptPacket decoded = new LostTalesMissiveAcceptPacket();
        decoded.fromBytes(buffer);
        return decoded;
    }

    /* ---- The board's notices ---- */

    @Test
    public void boardStateRoundTripsEveryNoticeAndTheLetterItShows() {
        LostTalesMissiveBoardStatePacket decoded = decodeState(bytes(state(
                MissiveBoardStateReason.OPENED,
                new MissiveNotice(0, 3000L, missive(QUEST)),
                new MissiveNotice(4, MissiveNotice.STAYS_UP, null),
                new MissiveNotice(8, 0L, missive(QUEST + "b")))));

        assertFalse(decoded.isMalformed());
        assertTrue(decoded.isOpening());
        assertTrue(decoded.isAbout(0, 4, 65, 9));
        assertFalse(decoded.isAbout(-1, 4, 65, 9));
        assertEquals(9, decoded.getMaxNotices());
        assertEquals(3, decoded.getNotices().size());
        MissiveNotice first = decoded.getNotices().get(0);
        assertEquals(0, first.getSlot());
        assertEquals(3000L, first.getTicksLeft());
        LostTalesMissiveData letter = first.getMissive();
        assertEquals(QUEST, letter.getQuestId());
        assertEquals("kill", letter.getQuestType());
        assertEquals("Missive: Clear the Paths", letter.getTitle());
        assertEquals("A Road Warden", letter.getIssuer());
        assertEquals("Travellers have reported zombies.",
                letter.getDescription());
        assertEquals("The roads must remain open.", letter.getFlavorText());
        assertEquals(48000L, letter.getTimeLimitTicks());
        assertEquals(1, letter.getObjectives().size());
        assertEquals("Defeat 5 zombies.",
                letter.getObjectives().get(0).getDescription());
        assertEquals("5", letter.getObjectives().get(0).getParams().get("count"));
        assertEquals("40", letter.getRewardData().getRewards().get("experience"));
        assertTrue("what the server keeps to make the quest never travels",
                letter.getGenerationContext().isEmpty());
        MissiveNotice unreadable = decoded.getNotices().get(1);
        assertFalse(unreadable.isReadable());
        assertTrue(unreadable.staysUp());
        assertEquals("", unreadable.getQuestId());
        assertEquals(8, decoded.getNotices().get(2).getSlot());
    }

    @Test
    public void everyReasonTravelsAndOnlyAnOpeningOpens() {
        for (MissiveBoardStateReason reason : MissiveBoardStateReason.values()) {
            LostTalesMissiveBoardStatePacket decoded =
                    decodeState(bytes(state(reason)));
            assertFalse(reason.name(), decoded.isMalformed());
            assertEquals(reason, decoded.getReason());
            assertEquals(reason == MissiveBoardStateReason.OPENED,
                    decoded.isOpening());
            assertEquals(reason.name(), reason,
                    MissiveBoardStateReason.fromNetworkId(reason.getNetworkId()));
        }
        assertNull(MissiveBoardStateReason.fromNetworkId(200));
    }

    @Test
    public void aLetterTooLargeToSendGoesAsOneThatCannotBeRead() {
        StringBuilder title = new StringBuilder();
        while (title.length() <= LostTalesMissiveCodec.MAX_TITLE_BYTES) {
            title.append("Long ");
        }
        LostTalesMissiveData large = LostTalesMissiveData.builder(QUEST, "kill")
                .title(title.toString())
                .objective(new LostTalesMissiveObjectiveData("a", "kill", "",
                        false, null))
                .build();
        assertFalse(LostTalesMissiveCodec.fits(large));
        LostTalesMissiveBoardStatePacket decoded = decodeState(bytes(state(
                MissiveBoardStateReason.CHANGED,
                new MissiveNotice(2, 10L, large))));
        assertFalse(decoded.isMalformed());
        assertFalse(decoded.getNotices().get(0).isReadable());
        assertEquals(10L, decoded.getNotices().get(0).getTicksLeft());
    }

    @Test
    public void theLetterBoundsAreKept() {
        assertTrue(LostTalesMissiveCodec.fits(missive(QUEST)));
        LostTalesMissiveData.Builder many =
                LostTalesMissiveData.builder(QUEST, "kill").title("Many");
        for (int index = 0; index <= LostTalesMissiveCodec.MAX_OBJECTIVES; index++) {
            many.objective(new LostTalesMissiveObjectiveData("o" + index,
                    "kill", "", false, null));
        }
        assertFalse("too many objectives", LostTalesMissiveCodec.fits(many.build()));
        Map<String, String> rewards = new LinkedHashMap<String, String>();
        for (int index = 0; index <= LostTalesMissiveCodec.MAX_ENTRIES; index++) {
            rewards.put("reward" + index, "1");
        }
        assertFalse("too many rewards", LostTalesMissiveCodec.fits(
                LostTalesMissiveData.builder(QUEST, "kill").title("Rich")
                        .objective(new LostTalesMissiveObjectiveData("a",
                                "kill", "", false, null))
                        .rewardData(new LostTalesMissiveRewardData(rewards))
                        .build()));
        assertFalse("no letter", LostTalesMissiveCodec.fits(null));
        assertTrue("the largest letter fits the packet's ceiling",
                LostTalesMissiveBoardStatePacket.MAX_PACKET_BYTES
                        >= 9 * LostTalesMissiveCodec.MAX_MISSIVE_BYTES);
    }

    @Test
    public void theFingerprintFollowsTheLettersAndNotTheirTime() {
        LostTalesMissiveBoardStatePacket one = state(
                MissiveBoardStateReason.CHANGED,
                new MissiveNotice(0, 3000L, missive(QUEST)));
        LostTalesMissiveBoardStatePacket later = state(
                MissiveBoardStateReason.OPENED,
                new MissiveNotice(0, 1200L, missive(QUEST)));
        LostTalesMissiveBoardStatePacket other = state(
                MissiveBoardStateReason.CHANGED,
                new MissiveNotice(0, 3000L, missive(QUEST + "b")));
        LostTalesMissiveBoardStatePacket moved = state(
                MissiveBoardStateReason.CHANGED,
                new MissiveNotice(1, 3000L, missive(QUEST)));
        assertEquals(one.getFingerprint(), later.getFingerprint());
        assertNotEquals(one.getFingerprint(), other.getFingerprint());
        assertNotEquals(one.getFingerprint(), moved.getFingerprint());
        assertNotEquals(one.getFingerprint(),
                state(MissiveBoardStateReason.CHANGED).getFingerprint());
    }

    @Test
    public void aStateThatCannotBeRefusesToBeMade() {
        try {
            state(MissiveBoardStateReason.OPENED,
                    new MissiveNotice(3, 0L, null),
                    new MissiveNotice(3, 0L, null));
            fail("two notices in one slot");
        } catch (IllegalArgumentException expected) {
        }
        try {
            state(MissiveBoardStateReason.OPENED, new MissiveNotice(9, 0L, null));
            fail("a slot past the board's nine");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new LostTalesMissiveBoardStatePacket(0, 4, 300, 9,
                    MissiveBoardStateReason.OPENED, 9,
                    Collections.<MissiveNotice>emptyList());
            fail("a place no block stands");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new LostTalesMissiveBoardStatePacket(0, 4, 65, 9, null, 9,
                    Collections.<MissiveNotice>emptyList());
            fail("no reason");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new LostTalesMissiveBoardStatePacket(0, 4, 65, 9,
                    MissiveBoardStateReason.OPENED, 10,
                    Collections.<MissiveNotice>emptyList());
            fail("a limit past the board's slots");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void aTruncatedStateIsMalformedAtEveryLength() {
        ByteBuf whole = bytes(state(MissiveBoardStateReason.OPENED,
                new MissiveNotice(0, 3000L, missive(QUEST))));
        byte[] all = new byte[whole.readableBytes()];
        whole.readBytes(all);
        for (int length = 0; length < all.length; length++) {
            LostTalesMissiveBoardStatePacket decoded = decodeState(
                    Unpooled.wrappedBuffer(Arrays.copyOf(all, length)));
            assertTrue("cut at " + length, decoded.isMalformed());
            assertTrue(decoded.getNotices().isEmpty());
        }
    }

    @Test
    public void aStateWithTrailingBytesIsMalformed() {
        ByteBuf buffer = bytes(state(MissiveBoardStateReason.OPENED));
        buffer.writeByte(0);
        LostTalesMissiveBoardStatePacket decoded = decodeState(buffer);
        assertTrue(decoded.isMalformed());
        assertFalse("the rest is thrown away", buffer.isReadable());
    }

    @Test
    public void aStatePastItsCeilingIsMalformedUnread() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeZero(LostTalesMissiveBoardStatePacket.MAX_PACKET_BYTES + 1);
        assertTrue(decodeState(buffer).isMalformed());
    }

    @Test
    public void aStateOfAnyOtherShapeIsMalformed() {
        assertTrue("unknown reason", decodeState(head(99, 9, 0)).isMalformed());
        assertTrue("limit past nine", decodeState(head(0, 10, 0)).isMalformed());
        assertTrue("ten notices", decodeState(head(0, 9, 10)).isMalformed());

        ByteBuf outOfOrder = head(0, 9, 2);
        notice(outOfOrder, 5, 0L);
        notice(outOfOrder, 2, 0L);
        assertTrue("slots out of order", decodeState(outOfOrder).isMalformed());

        ByteBuf pastNine = head(0, 9, 1);
        notice(pastNine, 9, 0L);
        assertTrue("slot past nine", decodeState(pastNine).isMalformed());

        ByteBuf badTime = head(0, 9, 1);
        notice(badTime, 1, -2L);
        assertTrue("time below never", decodeState(badTime).isMalformed());

        ByteBuf badFlag = head(0, 9, 1);
        badFlag.writeByte(1);
        badFlag.writeLong(0L);
        badFlag.writeByte(2);
        assertTrue("a flag neither yes nor no", decodeState(badFlag).isMalformed());

        ByteBuf badLetter = head(0, 9, 1);
        badLetter.writeByte(1);
        badLetter.writeLong(0L);
        badLetter.writeBoolean(true);
        LostTalesPacketCodec.writeUtf8String(badLetter, "", 512);
        LostTalesPacketCodec.writeUtf8String(badLetter, "kill", 64);
        LostTalesPacketCodec.writeUtf8String(badLetter, "Title", 512);
        LostTalesPacketCodec.writeUtf8String(badLetter, "", 512);
        LostTalesPacketCodec.writeUtf8String(badLetter, "", 2048);
        LostTalesPacketCodec.writeUtf8String(badLetter, "", 2048);
        badLetter.writeLong(0L);
        badLetter.writeByte(0);
        badLetter.writeByte(0);
        assertTrue("a letter with no quest and no objective",
                decodeState(badLetter).isMalformed());
    }

    private static ByteBuf head(int reason, int limit, int count) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(0);
        buffer.writeInt(4);
        buffer.writeInt(65);
        buffer.writeInt(9);
        buffer.writeByte(reason);
        buffer.writeByte(limit);
        buffer.writeByte(count);
        return buffer;
    }

    private static void notice(ByteBuf buffer, int slot, long ticksLeft) {
        buffer.writeByte(slot);
        buffer.writeLong(ticksLeft);
        buffer.writeBoolean(false);
    }

    /* ---- Take and pin ---- */

    @Test
    public void takeAndPinRoundTrip() {
        LostTalesMissiveBoardRequestPacket take = decodeRequest(bytes(
                LostTalesMissiveBoardRequestPacket.take(-1, 4, 65, 9, 8, QUEST)));
        assertFalse(take.isMalformed());
        assertEquals(LostTalesMissiveBoardRequestPacket.Operation.TAKE,
                take.getOperation());
        assertEquals(-1, take.getDimensionId());
        assertEquals(4, take.getX());
        assertEquals(65, take.getY());
        assertEquals(9, take.getZ());
        assertEquals(8, take.getSlot());
        assertEquals(QUEST, take.getExpectedQuestId());

        LostTalesMissiveBoardRequestPacket pin = decodeRequest(bytes(
                LostTalesMissiveBoardRequestPacket.pin(0, 4, 65, 9, 35, "")));
        assertFalse(pin.isMalformed());
        assertEquals(LostTalesMissiveBoardRequestPacket.Operation.PIN,
                pin.getOperation());
        assertEquals(35, pin.getSlot());
        assertEquals("a letter that cannot be read is named by no id", "",
                pin.getExpectedQuestId());
    }

    @Test
    public void aRequestThatCannotBeRefusesToBeMade() {
        String[] reasons = {"take past the board's slots", "pin a negative slot",
                "a place no block stands", "an id with spaces round it",
                "an id past its bound"};
        StringBuilder longId = new StringBuilder();
        while (longId.length() <= LostTalesMissiveCodec.MAX_QUEST_ID_BYTES) {
            longId.append("x");
        }
        for (int index = 0; index < reasons.length; index++) {
            try {
                switch (index) {
                    case 0:
                        LostTalesMissiveBoardRequestPacket.take(0, 4, 65, 9, 9, QUEST);
                        break;
                    case 1:
                        LostTalesMissiveBoardRequestPacket.pin(0, 4, 65, 9, -1, QUEST);
                        break;
                    case 2:
                        LostTalesMissiveBoardRequestPacket.take(0, 4, -1, 9, 0, QUEST);
                        break;
                    case 3:
                        LostTalesMissiveBoardRequestPacket.take(0, 4, 65, 9, 0, " " + QUEST);
                        break;
                    default:
                        LostTalesMissiveBoardRequestPacket.pin(0, 4, 65, 9, 0,
                                longId.toString());
                }
                fail(reasons[index]);
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @Test
    public void aRequestOfAnyOtherShapeIsMalformed() {
        ByteBuf unknown = request(7, 0, QUEST);
        assertTrue("unknown operation", decodeRequest(unknown).isMalformed());
        assertTrue("take past nine", decodeRequest(request(0, 9, QUEST)).isMalformed());
        assertTrue("pin past any inventory",
                decodeRequest(request(1, 5000, QUEST)).isMalformed());
        assertTrue("untrimmed id", decodeRequest(request(0, 1, QUEST + " ")).isMalformed());

        ByteBuf trailing = bytes(LostTalesMissiveBoardRequestPacket.take(0, 4,
                65, 9, 0, QUEST));
        trailing.writeByte(0);
        assertTrue("trailing", decodeRequest(trailing).isMalformed());
        assertFalse(trailing.isReadable());

        ByteBuf whole = bytes(LostTalesMissiveBoardRequestPacket.take(0, 4, 65,
                9, 0, QUEST));
        byte[] all = new byte[whole.readableBytes()];
        whole.readBytes(all);
        for (int length = 0; length < all.length; length++) {
            assertTrue("cut at " + length, decodeRequest(
                    Unpooled.wrappedBuffer(Arrays.copyOf(all, length))).isMalformed());
        }

        ByteBuf huge = Unpooled.buffer();
        huge.writeZero(LostTalesMissiveBoardRequestPacket.MAX_PACKET_BYTES + 1);
        assertTrue("past its ceiling", decodeRequest(huge).isMalformed());
    }

    private static ByteBuf request(int operation, int slot, String questId) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(operation);
        buffer.writeInt(0);
        buffer.writeInt(4);
        buffer.writeInt(65);
        buffer.writeInt(9);
        buffer.writeInt(slot);
        LostTalesPacketCodec.writeUtf8String(buffer, questId, 512);
        return buffer;
    }

    /* ---- Accept ---- */

    @Test
    public void acceptFromABoardCarriesItsWorldAndPlace() {
        LostTalesMissiveAcceptPacket decoded = decodeAccept(bytes(
                LostTalesMissiveAcceptPacket.fromBoard(-1, 4, 65, 9, 3, QUEST)));
        assertFalse(decoded.isMalformed());
        assertEquals(LostTalesMissiveAcceptPacket.SOURCE_BOARD,
                decoded.getSourceType());
        assertEquals(-1, decoded.getDimensionId());
        assertEquals(4, decoded.getX());
        assertEquals(65, decoded.getY());
        assertEquals(9, decoded.getZ());
        assertEquals(3, decoded.getSlot());
        assertEquals(QUEST, decoded.getExpectedQuestId());
    }

    @Test
    public void acceptFromTheInventoryNamesOnlyItsSlot() {
        LostTalesMissiveAcceptPacket decoded = decodeAccept(bytes(
                LostTalesMissiveAcceptPacket.fromPlayerInventory(7, QUEST)));
        assertFalse(decoded.isMalformed());
        assertEquals(LostTalesMissiveAcceptPacket.SOURCE_PLAYER_INVENTORY,
                decoded.getSourceType());
        assertEquals(7, decoded.getSlot());
        assertEquals(0, decoded.getX());
    }

    @Test
    public void anAcceptOfAnyOtherShapeIsMalformed() {
        assertTrue("unknown source", decodeAccept(accept(2, 0, 0, 0, 0, 0,
                QUEST)).isMalformed());
        assertTrue("no quest named", decodeAccept(accept(0, 0, 4, 65, 9, 0,
                "")).isMalformed());
        assertTrue("a board slot past nine", decodeAccept(accept(0, 0, 4, 65,
                9, 9, QUEST)).isMalformed());
        assertTrue("a board where no block stands", decodeAccept(accept(0, 0,
                4, 256, 9, 0, QUEST)).isMalformed());
        assertTrue("the inventory with a board's place",
                decodeAccept(accept(1, 0, 4, 65, 9, 0, QUEST)).isMalformed());
        assertTrue("the inventory with a world", decodeAccept(accept(1, -1, 0,
                0, 0, 0, QUEST)).isMalformed());
        assertTrue("an untrimmed id", decodeAccept(accept(1, 0, 0, 0, 0, 0,
                " " + QUEST)).isMalformed());

        ByteBuf trailing = bytes(LostTalesMissiveAcceptPacket
                .fromPlayerInventory(0, QUEST));
        trailing.writeByte(1);
        assertTrue("trailing", decodeAccept(trailing).isMalformed());
        assertFalse(trailing.isReadable());

        ByteBuf whole = bytes(LostTalesMissiveAcceptPacket.fromBoard(0, 4, 65,
                9, 0, QUEST));
        byte[] all = new byte[whole.readableBytes()];
        whole.readBytes(all);
        for (int length = 0; length < all.length; length++) {
            assertTrue("cut at " + length, decodeAccept(
                    Unpooled.wrappedBuffer(Arrays.copyOf(all, length))).isMalformed());
        }

        ByteBuf huge = Unpooled.buffer();
        huge.writeZero(LostTalesMissiveAcceptPacket.MAX_PACKET_BYTES + 1);
        assertTrue("past its ceiling", decodeAccept(huge).isMalformed());

        try {
            LostTalesMissiveAcceptPacket.fromPlayerInventory(-1, QUEST);
            fail("a negative slot");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static ByteBuf accept(int source, int dimension, int x, int y,
                                  int z, int slot, String questId) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(source);
        buffer.writeInt(dimension);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeInt(slot);
        LostTalesPacketCodec.writeUtf8String(buffer, questId, 512);
        return buffer;
    }

    /* ---- The reasons ---- */

    @Test
    public void everyAnswerHasWordsAndOnlyRefusalsAreRefusals() {
        List<MissiveBoardStateReason> successes = new ArrayList<MissiveBoardStateReason>(
                Arrays.asList(MissiveBoardStateReason.ACCEPTED,
                        MissiveBoardStateReason.TAKEN,
                        MissiveBoardStateReason.PINNED));
        for (MissiveBoardStateReason reason : MissiveBoardStateReason.values()) {
            boolean answer = reason != MissiveBoardStateReason.OPENED
                    && reason != MissiveBoardStateReason.CHANGED;
            assertEquals(reason.name(), answer, reason.isAnswer());
            assertEquals(reason.name(), answer, reason.getMessageKey().length() > 0);
            assertEquals(reason.name(), answer && !successes.contains(reason),
                    reason.isRefusal());
            if (reason.isSaidInChat()) {
                assertTrue(reason.name(), reason.isRefusal());
            }
        }
        assertFalse("the quest says its own requirements",
                MissiveBoardStateReason.REQUIREMENTS.isSaidInChat());
    }
}
