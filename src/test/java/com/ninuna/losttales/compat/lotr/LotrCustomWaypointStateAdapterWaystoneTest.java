package com.ninuna.losttales.compat.lotr;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

public final class LotrCustomWaypointStateAdapterWaystoneTest {

    @Test
    public void reservedWaystoneUseIdsRemainValidCharacterState() {
        NBTTagCompound state = new NBTTagCompound();
        state.setTag("CustomWaypoints", new NBTTagList());
        NBTTagList uses = new NBTTagList();
        NBTTagCompound use = new NBTTagCompound();
        use.setInteger("CustomID", 1);
        use.setInteger("Count", 3);
        uses.appendTag(use);
        state.setTag("CWPUses", uses);

        new LotrCustomWaypointStateAdapter().validate(state);
    }
}
