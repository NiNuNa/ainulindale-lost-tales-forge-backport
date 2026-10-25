package com.ninuna.losttales.quest;

import com.ninuna.losttales.chat.share.QuestShareInvite;
import com.ninuna.losttales.network.packet.LostTalesQuestFellowsPacket;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A quest shared in a fellowship (QUEST-25): each member who could join is
 * asked under the card, Join sending the join and Not now nothing; the
 * journal shows where the fellowship's members stand in the quests the
 * player runs too.
 */
public final class QuestFellowsTest {
    private static final String QUEST = "losttales:server/cheese_cache";

    private static LostTalesQuestPlayerData running(String stageId) {
        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        data.startQuest(QUEST, "1", 1L, 0L);
        if (!"1".equals(stageId)) {
            data.setQuestStage(QUEST, 1, stageId);
        }
        return data;
    }

    @Test
    public void membersAtTheStageShowTheirCountsAndThosePastItDone() {
        LostTalesQuestPlayerData own = running("1");
        LostTalesQuestPlayerData tobin = running("1");
        tobin.setObjectiveProgress(QUEST, "wheels", 2);
        LostTalesQuestPlayerData dior = running("2");
        LostTalesQuestPlayerData elsewhere = new LostTalesQuestPlayerData();
        elsewhere.startQuest("losttales:server/other", "1", 1L, 0L);
        List<QuestFellowCount> counts = QuestFellows.countsFor(own, Arrays.asList(
                new QuestFellows.Fellow("Tobin", tobin, true),
                new QuestFellows.Fellow("Dior", dior, false),
                new QuestFellows.Fellow("Nia", elsewhere, true)));
        assertEquals(2, counts.size());
        assertEquals("Tobin", counts.get(0).getName());
        assertFalse(counts.get(0).isPast());
        assertEquals(2, counts.get(0).countOf("wheels"));
        assertEquals("Dior", counts.get(1).getName());
        assertTrue(counts.get(1).isPast());

        assertTrue("a member behind the player is not shown", QuestFellows.countsFor(
                dior, Collections.singletonList(new QuestFellows.Fellow("Aldric", own, true)))
                .isEmpty());
        assertTrue(QuestFellows.countsFor(own,
                Collections.<QuestFellows.Fellow>emptyList()).isEmpty());
    }

    @Test
    public void eachCountSaysWhetherTheMemberTravelsWithThePlayer() {
        LostTalesQuestPlayerData own = running("1");
        LostTalesQuestPlayerData tobin = running("1");
        LostTalesQuestPlayerData dior = running("2");
        List<QuestFellowCount> counts = QuestFellows.countsFor(own, Arrays.asList(
                new QuestFellows.Fellow("Tobin", tobin, true),
                new QuestFellows.Fellow("Dior", dior, false)));
        assertEquals(2, counts.size());
        assertTrue("travelling with the player, sharing its credit",
                counts.get(0).isTravelling());
        assertFalse("a member of another fellowship of the player's",
                counts.get(1).isTravelling());
        assertTrue(counts.get(1).isPast());

        QuestFellowCount sharing = new QuestFellowCount(QUEST, "Tobin", false,
                true, Collections.singletonMap("wheels", Integer.valueOf(2)));
        QuestFellowCount apart = new QuestFellowCount(QUEST, "Tobin", false,
                false, Collections.singletonMap("wheels", Integer.valueOf(2)));
        assertFalse("a change of company is a change to send", sharing.equals(apart));
    }

    @Test
    public void whetherAMemberTravelsWithThePlayerTravelsToo() {
        List<QuestFellowCount> sent = new ArrayList<QuestFellowCount>();
        sent.add(new QuestFellowCount(QUEST, "Tobin", false, false,
                Collections.singletonMap("wheels", Integer.valueOf(1))));
        sent.add(new QuestFellowCount(QUEST, "Dior", true, true, null));
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesQuestFellowsPacket(sent).toBytes(buffer);
        LostTalesQuestFellowsPacket read = new LostTalesQuestFellowsPacket();
        read.fromBytes(buffer);
        assertFalse(read.isMalformed());
        assertFalse(read.getCounts().get(0).isTravelling());
        assertTrue(read.getCounts().get(1).isTravelling());
        assertEquals(sent, read.getCounts());

        ByteBuf extra = Unpooled.buffer();
        new LostTalesQuestFellowsPacket(sent).toBytes(extra);
        extra.writeBoolean(true);
        LostTalesQuestFellowsPacket longer = new LostTalesQuestFellowsPacket();
        longer.fromBytes(extra);
        assertTrue("a payload of another shape is malformed", longer.isMalformed());
        assertTrue(longer.getCounts().isEmpty());
    }

    @Test
    public void theCountsTravelWholeAndABadListChangesNothing() {
        List<QuestFellowCount> sent = new ArrayList<QuestFellowCount>();
        sent.add(new QuestFellowCount(QUEST, "Tobin", false, true,
                Collections.singletonMap("wheels", Integer.valueOf(2))));
        sent.add(new QuestFellowCount(QUEST, "Dior", true, false, null));
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesQuestFellowsPacket(sent).toBytes(buffer);
        LostTalesQuestFellowsPacket read = new LostTalesQuestFellowsPacket();
        read.fromBytes(buffer);
        assertFalse(read.isMalformed());
        assertEquals(sent, read.getCounts());

        ByteBuf cut = Unpooled.buffer();
        new LostTalesQuestFellowsPacket(sent).toBytes(cut);
        cut.writerIndex(cut.writerIndex() - 3);
        LostTalesQuestFellowsPacket broken = new LostTalesQuestFellowsPacket();
        broken.fromBytes(cut);
        assertTrue(broken.isMalformed());
        assertTrue(broken.getCounts().isEmpty());

        ByteBuf tooMany = Unpooled.buffer();
        tooMany.writeInt(QuestFellows.MAX_COUNTS + 1);
        LostTalesQuestFellowsPacket refused = new LostTalesQuestFellowsPacket();
        refused.fromBytes(tooMany);
        assertTrue(refused.isMalformed());
    }

    @Test
    public void aLongNameIsCutAndAPastMemberCarriesNoCounts() {
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 100; index++) {
            name.append('a');
        }
        QuestFellowCount count = new QuestFellowCount(QUEST, name.toString(), true, true,
                Collections.singletonMap("wheels", Integer.valueOf(9)));
        assertEquals(QuestFellowCount.MAX_NAME_CHARACTERS, count.getName().length());
        assertTrue(count.getCounts().isEmpty());
    }

    @Test
    public void theInvitationCarriesTheCardAndNotNowNothing() {
        IChatComponent line = QuestShareInvite.line("Aldric", "The Cheese Cache",
                4242L, 1);
        assertTrue(QuestShareInvite.isNotice(line));
        Object[] arguments = ((ChatComponentTranslation) line).getFormatArgs();
        ClickEvent join = ((IChatComponent) arguments[2]).getChatStyle()
                .getChatClickEvent();
        QuestShareInvite.Answer answer = QuestShareInvite.parse(join.getValue());
        assertTrue(answer.join);
        assertEquals(4242L, answer.messageId);
        assertEquals(1, answer.tokenIndex);
        ClickEvent later = ((IChatComponent) arguments[3]).getChatStyle()
                .getChatClickEvent();
        assertFalse(QuestShareInvite.parse(later.getValue()).join);

        assertNull(QuestShareInvite.parse("losttales-quest-join:join:-5:1"));
        assertNull(QuestShareInvite.parse("losttales-quest-join:join:4242:99"));
        assertNull(QuestShareInvite.parse("losttales-quest-join:join:x:1"));
        assertNull(QuestShareInvite.parse("/say hello"));
    }
}
