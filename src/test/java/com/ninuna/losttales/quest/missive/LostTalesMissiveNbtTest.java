package com.ninuna.losttales.quest.missive;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;

import static org.junit.Assert.assertNull;

public final class LostTalesMissiveNbtTest {

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
