package com.ninuna.losttales.quest.player;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;

public final class LostTalesQuestPlayerDataLimitTest {

    @Test(expected = IllegalArgumentException.class)
    public void excessiveCurrentDataIsRejectedBySnapshotValidation() {
        NBTTagCompound questData = new NBTTagCompound();
        questData.setInteger("DataVersion",
                LostTalesQuestPlayerData.CURRENT_DATA_VERSION);
        NBTTagList active = new NBTTagList();
        for (int i = 0;
             i < LostTalesQuestPlayerData.MAX_ACTIVE_QUESTS + 1; i++) {
            active.appendTag(new NBTTagCompound());
        }
        questData.setTag("ActiveQuests", active);
        LostTalesQuestPlayerData.validateCharacterState(questData);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonCompoundActiveQuestListIsRejected() {
        NBTTagCompound questData = new NBTTagCompound();
        questData.setInteger("DataVersion",
                LostTalesQuestPlayerData.CURRENT_DATA_VERSION);
        NBTTagList active = new NBTTagList();
        active.appendTag(new NBTTagString("not-a-quest"));
        questData.setTag("ActiveQuests", active);

        LostTalesQuestPlayerData.validateCharacterState(questData);
    }
}
