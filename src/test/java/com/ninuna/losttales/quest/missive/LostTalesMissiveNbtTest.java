package com.ninuna.losttales.quest.missive;

import java.util.Collections;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LostTalesMissiveNbtTest {

    private static LostTalesMissiveData letter() {
        return LostTalesMissiveData.builder(
                "losttales:missive/generated/test", "kill")
                .title("Test missive")
                .repeatable(false)
                .firstComeFirstServed(true)
                .generationWorldTime(1200L)
                .objective(new LostTalesMissiveObjectiveData("kill_wolves",
                        "kill", "Defeat 3 wolves.", false,
                        Collections.singletonMap("count", "3")))
                .rewardData(LostTalesMissiveRewardData.experienceAndItems(10, ""))
                .build();
    }

    @Test
    public void aWrittenLetterReadsBackAsItWasWritten() {
        LostTalesMissiveData read = LostTalesMissiveNbt.readFromNBT(
                LostTalesMissiveNbt.writeToNBT(letter()));
        assertNotNull(read);
        assertFalse(read.isRepeatable());
        assertTrue(read.isFirstComeFirstServed());
    }

    /** The writer always writes who may take a letter, so a letter without it is malformed. */
    @Test
    public void aLetterMissingWhoMayTakeItIsMalformed() {
        NBTTagCompound noRepeatable = LostTalesMissiveNbt.writeToNBT(letter());
        noRepeatable.removeTag("Repeatable");
        assertNull(LostTalesMissiveNbt.readFromNBT(noRepeatable));

        NBTTagCompound noFirstCome = LostTalesMissiveNbt.writeToNBT(letter());
        noFirstCome.removeTag("FirstComeFirstServed");
        assertNull(LostTalesMissiveNbt.readFromNBT(noFirstCome));
    }

    @Test
    public void excessiveObjectiveListIsRejected() {
        NBTTagCompound missive = new NBTTagCompound();
        missive.setString("QuestId", "losttales:missive/generated/test");
        missive.setString("QuestType", "gather");
        missive.setString("Title", "Test missive");
        NBTTagList objectives = new NBTTagList();
        for (int i = 0; i < LostTalesMissiveNbt.MAX_OBJECTIVES + 1; i++) {
            objectives.appendTag(new NBTTagCompound());
        }
        missive.setTag("Objectives", objectives);

        assertNull(LostTalesMissiveNbt.readFromNBT(missive));
    }

    @Test
    public void nonCompoundObjectiveListIsRejected() {
        NBTTagCompound missive = new NBTTagCompound();
        missive.setString("QuestId", "losttales:missive/generated/test");
        missive.setString("QuestType", "gather");
        missive.setString("Title", "Test missive");
        NBTTagList objectives = new NBTTagList();
        objectives.appendTag(new NBTTagString("not-an-objective"));
        missive.setTag("Objectives", objectives);

        assertNull(LostTalesMissiveNbt.readFromNBT(missive));
    }
}
