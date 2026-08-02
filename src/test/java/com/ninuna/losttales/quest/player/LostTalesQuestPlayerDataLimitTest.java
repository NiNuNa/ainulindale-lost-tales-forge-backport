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

    @Test(expected = IllegalArgumentException.class)
    public void invalidDynamicMarkerPriorityIsRejected() {
        NBTTagCompound questData = new NBTTagCompound();
        questData.setInteger("DataVersion",
                LostTalesQuestPlayerData.CURRENT_DATA_VERSION);
        NBTTagCompound marker = new NBTTagCompound();
        marker.setString("MarkerId", "losttales:invalid_priority");
        marker.setDouble("X", 1.0D);
        marker.setDouble("Y", 64.0D);
        marker.setDouble("Z", 2.0D);
        marker.setDouble("CompassFadeInRadius", 128.0D);
        marker.setDouble("DiscoveryRadius", 8.0D);
        marker.setInteger("Priority",
                com.ninuna.losttales.mapmarker
                        .LostTalesMapMarkerDefinition.MAX_PRIORITY + 1);
        NBTTagList markers = new NBTTagList();
        markers.appendTag(marker);
        questData.setTag("DynamicMapMarkers", markers);

        LostTalesQuestPlayerData.validateCharacterState(questData);
    }
}
