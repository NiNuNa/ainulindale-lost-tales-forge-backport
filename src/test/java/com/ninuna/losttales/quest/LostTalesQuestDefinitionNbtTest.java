package com.ninuna.losttales.quest;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;

import static org.junit.Assert.assertNull;

public final class LostTalesQuestDefinitionNbtTest {

    @Test
    public void excessiveStageListIsRejected() {
        NBTTagCompound quest = new NBTTagCompound();
        quest.setString("Id", "runtime:test");
        NBTTagList stages = new NBTTagList();
        for (int i = 0;
             i < LostTalesQuestDefinitionNbt.MAX_STAGES + 1; i++) {
            stages.appendTag(new NBTTagCompound());
        }
        quest.setTag("Stages", stages);

        assertNull(LostTalesQuestDefinitionNbt.read(quest));
    }

    @Test
    public void nonCompoundStageListIsRejected() {
        NBTTagCompound quest = new NBTTagCompound();
        quest.setString("Id", "runtime:test");
        NBTTagList stages = new NBTTagList();
        stages.appendTag(new NBTTagString("not-a-stage"));
        quest.setTag("Stages", stages);

        assertNull(LostTalesQuestDefinitionNbt.read(quest));
    }
}
