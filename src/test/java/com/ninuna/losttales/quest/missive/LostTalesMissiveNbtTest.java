package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.network.packet.LostTalesMissiveCodec;
import java.util.Collections;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LostTalesMissiveNbtTest {

    private static LostTalesMissiveData letter() {
        return LostTalesMissiveData.builder(
                "losttales:missive/test", "kill")
                .titleId("clear_the_paths")
                .generationWorldTime(1200L)
                .objective(new LostTalesMissiveObjectiveData("kill_wolves",
                        "kill", false,
                        Collections.singletonMap("count", "3")))
                .rewardData(LostTalesMissiveRewardData.experienceAndCoins(10, 0))
                .build();
    }

    @Test
    public void aWrittenLetterReadsBackAsItWasWritten() {
        LostTalesMissiveData read = LostTalesMissiveNbt.readFromNBT(
                LostTalesMissiveNbt.writeToNBT(letter()));
        assertNotNull(read);
        assertEquals(1200L, read.getGenerationWorldTime());
    }

    /** A missive is taken once and never again: its quest is neither repeatable nor restartable (QUEST-34). */
    @Test
    public void aMissivesQuestIsNeverTakenAgain() {
        com.ninuna.losttales.quest.LostTalesQuestDefinition quest =
                LostTalesMissiveQuestFactory.createQuestDefinition(letter());
        assertNotNull(quest);
        assertFalse(quest.isRepeatable());
        assertFalse(quest.isRestartable());
    }

    /** A letter holds no more objectives than its page is sent. */
    @Test
    public void excessiveObjectiveListIsRejected() {
        NBTTagCompound missive = LostTalesMissiveNbt.writeToNBT(letter());
        NBTTagList objectives = missive.getTagList("Objectives", 10);
        NBTTagCompound one = objectives.getCompoundTagAt(0);
        for (int i = 1; i < LostTalesMissiveCodec.MAX_OBJECTIVES; i++) {
            NBTTagCompound more = (NBTTagCompound) one.copy();
            more.setString("Id", "kill_wolves_" + i);
            objectives.appendTag(more);
        }
        assertNotNull("as many as the page is sent read",
                LostTalesMissiveNbt.readFromNBT(missive));
        NBTTagCompound past = (NBTTagCompound) one.copy();
        past.setString("Id", "kill_wolves_past");
        objectives.appendTag(past);

        assertNull(LostTalesMissiveNbt.readFromNBT(missive));
    }

    /** An objective that cannot be read makes the whole letter one that cannot be read. */
    @Test
    public void anUnreadableObjectiveRefusesTheLetter() {
        NBTTagCompound missive = LostTalesMissiveNbt.writeToNBT(letter());
        NBTTagCompound blank = new NBTTagCompound();
        blank.setString("Id", " ");
        blank.setString("Type", "kill");
        missive.getTagList("Objectives", 10).appendTag(blank);

        assertNull(LostTalesMissiveNbt.readFromNBT(missive));
    }

    @Test
    public void nonCompoundObjectiveListIsRejected() {
        NBTTagCompound missive = new NBTTagCompound();
        missive.setString("QuestId", "losttales:missive/test");
        missive.setString("QuestType", "gather");
        missive.setBoolean("Written", false);
        missive.setString("TitleId", "clear_the_paths");
        NBTTagList objectives = new NBTTagList();
        objectives.appendTag(new NBTTagString("not-an-objective"));
        missive.setTag("Objectives", objectives);

        assertNull(LostTalesMissiveNbt.readFromNBT(missive));
    }
}
