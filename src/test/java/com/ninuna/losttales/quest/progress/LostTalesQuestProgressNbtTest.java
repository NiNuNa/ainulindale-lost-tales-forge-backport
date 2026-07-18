package com.ninuna.losttales.quest.progress;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class LostTalesQuestProgressNbtTest {

    @Test
    public void excessiveObjectiveListIsRejected() {
        NBTTagCompound quest = new NBTTagCompound();
        quest.setString("QuestId", "losttales:test");
        NBTTagList objectives = new NBTTagList();
        for (int i = 0;
             i < LostTalesQuestProgress.MAX_OBJECTIVE_ENTRIES + 1; i++) {
            NBTTagCompound objective = new NBTTagCompound();
            objective.setString("ObjectiveId", "objective_" + i);
            objectives.appendTag(objective);
        }
        quest.setTag("ObjectiveProgress", objectives);

        assertNull(LostTalesQuestProgress.readFromNBT(quest));
    }

    @Test
    public void nonCompoundObjectiveListIsRejected() {
        NBTTagCompound quest = new NBTTagCompound();
        quest.setString("QuestId", "losttales:test");
        NBTTagList objectives = new NBTTagList();
        objectives.appendTag(new NBTTagString("not-a-compound"));
        quest.setTag("ObjectiveProgress", objectives);

        assertNull(LostTalesQuestProgress.readFromNBT(quest));
    }

    @Test
    public void objectiveProgressCannotOverflow() {
        LostTalesQuestProgress progress = new LostTalesQuestProgress(
                "losttales:test", 0, "stage", null);
        progress.setObjectiveProgress("objective", Integer.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE,
                progress.addObjectiveProgress("objective", 1, 0));
    }
}
