package com.ninuna.losttales.quest;

import com.ninuna.losttales.network.packet.LostTalesQuestDefinitionCodec;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveObjectiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveQuestFactory;
import com.ninuna.losttales.quest.missive.LostTalesMissiveRewardData;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The keys a quest writes beside its blocks (QUEST-31, QUEST-32): where it
 * is handed in, its time limits and what fails it; read, checked, carried
 * on the wire and kept with a missive; and what a running quest keeps of
 * its giver and its hand-in.
 */
public final class LostTalesQuestRulesTest {
    private static final String GIVER_QUEST = "{\"id\":\"losttales:test/back\","
            + "\"startMode\":\"interaction\",\"interaction\":{\"entity\":\"losttales.Nia\"},"
            + "\"dialogue\":{\"offer\":\"Help?\",\"handIn\":\"You came back!\"},"
            + "\"returnTo\":\"giver\",\"timeLimit\":\"2.5\",\"failOn\":\"death,giverKilled\","
            + "\"stages\":[{\"id\":\"10\",\"timeLimit\":\"1\",\"objectives\":[{\"id\":\"a\","
            + "\"type\":\"gather\",\"params\":{\"item\":\"minecraft:log\"}}]}]}";

    private static LostTalesQuestDefinition parse(String json, List<String> unread) {
        return LostTalesQuestDefinitionJsonParser.parseQuest(new StringReader(json),
                "quests/test/q.json", unread);
    }

    private static List<String> warnings(String json) {
        List<String> unread = new ArrayList<String>();
        return LostTalesQuestDefinitionValidator.warningsOf(parse(json, unread), unread);
    }

    @Test
    public void theRulesAreReadAndHeldToTheirWords() {
        assertEquals(Collections.<String>emptyList(), warnings(GIVER_QUEST));
        LostTalesQuestDefinition quest = parse(GIVER_QUEST, null);
        assertTrue(LostTalesQuestRules.returnsToGiver(quest));
        assertTrue(LostTalesQuestRules.waitsForHandIn(quest));
        assertEquals(60000L, LostTalesQuestRules.timeLimitTicks(quest.getRules()));
        assertEquals(24000L, LostTalesQuestRules.timeLimitTicks(
                quest.getStages().get(0).getRules()));
        assertTrue(LostTalesQuestRules.failsOn(quest, LostTalesQuestRules.FAIL_DEATH));
        assertTrue(LostTalesQuestRules.failsOn(quest,
                LostTalesQuestRules.FAIL_GIVER_KILLED));
    }

    @Test
    public void aRuleWrittenWrongIsAWarning() {
        assertWarns(GIVER_QUEST.replace("\"returnTo\":\"giver\"", "\"returnTo\":\"board\""),
                "returns only to its giver");
        assertWarns(GIVER_QUEST.replace("\"handIn\":\"You came back!\"", "\"more\":\"Hm.\""),
                "no 'handIn' line");
        assertWarns(GIVER_QUEST.replace("\"timeLimit\":\"2.5\"", "\"timeLimit\":\"forever\""),
                "'timeLimit' forever");
        assertWarns(GIVER_QUEST.replace("\"timeLimit\":\"1\"", "\"timeLimit\":\"999\""),
                "stage=10 has 'timeLimit' 999");
        assertWarns(GIVER_QUEST.replace("death,giverKilled", "sunset"),
                "failOn names 'sunset'");
    }

    private static final String BRANCHING = "{\"id\":\"losttales:test/mill\","
            + "\"startMode\":\"interaction\",\"interaction\":{\"entity\":\"losttales.Nia\"},"
            + "\"dialogue\":{\"offer\":\"Thieves at the mill.\"},"
            + "\"stages\":[{\"id\":\"look\",\"ask\":\"Will you warn him?\","
            + "\"choices\":[{\"say\":\"I'll warn the miller.\",\"next\":\"miller\"},"
            + "{\"say\":\"Let him learn.\",\"next\":\"thieves\"}],"
            + "\"objectives\":[{\"id\":\"a\",\"type\":\"goto\",\"params\":{\"x\":\"1\",\"y\":\"2\",\"z\":\"3\"}}]},"
            + "{\"id\":\"miller\",\"next\":\"end\",\"objectives\":[{\"id\":\"b\",\"type\":\"talk\","
            + "\"params\":{\"entity\":\"losttales.Nia\"}}]},"
            + "{\"id\":\"thieves\",\"objectives\":[{\"id\":\"c\",\"type\":\"kill\","
            + "\"params\":{\"group\":\"enemy\",\"count\":\"3\"}}]}]}";

    /** A stage may end on a choice made with the giver, and a stage leads on by its next (QUEST-32). */
    @Test
    public void aBranchIsWrittenAndRead() {
        assertEquals(Collections.<String>emptyList(), warnings(BRANCHING));
        LostTalesQuestDefinition quest = parse(BRANCHING, null);
        LostTalesQuestStageDefinition look = quest.getStages().get(0);
        List<LostTalesQuestRules.Choice> choices = LostTalesQuestRules.choices(look);
        assertEquals(2, choices.size());
        assertEquals("thieves", choices.get(1).getNext());
        assertEquals("I'll warn the miller.", LostTalesQuestWords.choiceWords(quest, look, 0));
        assertEquals("Will you warn him?", LostTalesQuestWords.askLine(quest, look));
        assertEquals("the miller's stage ends the quest", 3,
                LostTalesQuestRules.nextStageIndex(quest, 1));
        assertEquals("the thieves' stage falls through to the end", 3,
                LostTalesQuestRules.nextStageIndex(quest, 2));
        assertEquals(2, LostTalesQuestRules.stageIndexOf(quest, "thieves"));
    }

    @Test
    public void aBranchWrittenWrongIsAWarning() {
        assertWarns(BRANCHING.replace("\"next\":\"thieves\"", "\"next\":\"look\""),
                "leads back to stage 'look'");
        assertWarns(BRANCHING.replace("\"next\":\"end\"", "\"next\":\"nowhere\""),
                "names stage 'nowhere'");
        assertWarns(BRANCHING.replace("\"ask\":\"Will you warn him?\",", ""),
                "no 'ask' line");
        assertWarns(BRANCHING.replace(",{\"say\":\"Let him learn.\",\"next\":\"thieves\"}", ""),
                "fewer than two choices");
    }

    /** A quest waiting for a choice says so in its save and to the client. */
    @Test
    public void aQuestWaitingForAChoiceKeepsWaiting() {
        LostTalesQuestProgress progress = new LostTalesQuestProgress(
                "losttales:test/mill", 0, "look", null, 1L, 0L);
        progress.setChoosing(true);
        assertTrue(LostTalesQuestProgress.readFromNBT(progress.writeToNBT()).isChoosing());
        com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket sent =
                new com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket(
                        Collections.singletonList(progress),
                        Collections.<com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry>emptyList(),
                        Collections.<String>emptySet(), Collections.<String>emptySet(),
                        "", null, null);
        ByteBuf buffer = Unpooled.buffer();
        sent.toBytes(buffer);
        com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket read =
                new com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket();
        read.fromBytes(buffer);
        assertFalse(read.isMalformed());
        assertTrue(read.getActiveQuests().get(0).isChoosing());
    }

    @Test
    public void theRulesTravelWithTheQuest() {
        LostTalesQuestDefinition quest = parse(GIVER_QUEST, null);
        ByteBuf buffer = Unpooled.buffer();
        LostTalesQuestDefinitionCodec.write(buffer, quest);
        LostTalesQuestDefinition read = LostTalesQuestDefinitionCodec.read(buffer);
        assertEquals(quest.getRules(), read.getRules());
        assertEquals(quest.getStages().get(0).getRules(),
                read.getStages().get(0).getRules());
    }

    /** A missive ends at any missive board, and keeps saying so in the log (QUEST-31). */
    @Test
    public void aMissiveIsHandedInAtABoard() {
        LostTalesMissiveData letter = LostTalesMissiveData.builder(
                "losttales:missive/generated/test", "kill")
                .titleId("clear_the_paths")
                .objective(new LostTalesMissiveObjectiveData("kill_wolves", "kill",
                        false, Collections.singletonMap("count", "3")))
                .rewardData(LostTalesMissiveRewardData.experienceAndCoins(10, 20))
                .build();
        LostTalesQuestDefinition quest =
                LostTalesMissiveQuestFactory.createQuestDefinition(letter);
        assertTrue(LostTalesQuestRules.returnsToBoard(quest));
        LostTalesQuestDefinition kept = LostTalesQuestDefinitionNbt.read(
                LostTalesQuestDefinitionNbt.write(quest));
        assertNotNull(kept);
        assertTrue(LostTalesQuestRules.returnsToBoard(kept));
    }

    /** A running quest keeps who gave it, its stage's clock and that it waits for its hand-in. */
    @Test
    public void aRunningQuestKeepsItsGiverAndItsHandIn() {
        LostTalesQuestProgress progress = new LostTalesQuestProgress(
                "losttales:test/back", 0, "10", null, 100L, 60100L);
        progress.setGiver("8d6b2a51-0a4c-4a39-9a3a-1b7c2a0b6c11", "Nia",
                "losttales.Nia");
        progress.setStageDeadlineWorldTime(24100L);
        progress.setReturning(true);
        LostTalesQuestProgress read = LostTalesQuestProgress.readFromNBT(
                progress.writeToNBT());
        assertNotNull(read);
        assertEquals("Nia", read.getGiverName());
        assertEquals("losttales.Nia", read.getGiverKind());
        assertTrue(read.isReturning());
        assertEquals("the nearer limit counts", 100L, read.getRemainingTicks(24000L));
        assertTrue(read.isExpired(24100L));
        assertEquals(read.getGiverId(), read.copy().getGiverId());
        assertTrue(read.withoutCounts().isReturning());

        NBTTagCompound tooLong = progress.writeToNBT();
        StringBuilder name = new StringBuilder();
        while (name.length() <= LostTalesQuestProgress.MAX_GIVER_NAME_CHARACTERS) {
            name.append('n');
        }
        tooLong.setString("GiverName", name.toString());
        assertNull(LostTalesQuestProgress.readFromNBT(tooLong));
    }

    /** An answer clicked in the chat reads back as written; anything else is no answer (QUEST-37). */
    @Test
    public void aChatAnswerReadsBackAsWritten() {
        QuestChatAnswer answer = QuestChatAnswer.parse(QuestChatAnswer.encode(
                QuestChatAnswer.HAND_IN, 1, "losttales:test/back"));
        assertNotNull(answer);
        assertEquals(QuestChatAnswer.HAND_IN, answer.action);
        assertEquals(1, answer.choice);
        assertEquals("losttales:test/back", answer.questId);
        assertNull(QuestChatAnswer.parse("losttales-quest:steal:0:x"));
        assertNull(QuestChatAnswer.parse("losttales-quest:accept:7:losttales:a"));
        assertNull(QuestChatAnswer.parse("losttales-fellowship-invitation:accept:x"));
        assertFalse(QuestChatAnswer.parse(QuestChatAnswer.encode(
                QuestChatAnswer.ACCEPT, -1, "losttales:a")).choice >= 0);
    }

    private static void assertWarns(String json, String about) {
        List<String> found = warnings(json);
        assertEquals(found.toString(), 1, found.size());
        assertTrue(found.get(0), found.get(0).contains(about));
    }
}
