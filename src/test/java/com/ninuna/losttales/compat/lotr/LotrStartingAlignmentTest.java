package com.ninuna.losttales.compat.lotr;

import lotr.common.LOTRPlayerData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class LotrStartingAlignmentTest {

    @Test
    public void startingFactionGetsExactlyOneHundredWithoutPledge() {
        LotrProgressionStateAdapter adapter = new LotrProgressionStateAdapter();
        NBTTagCompound progression = adapter.createDefault(LOTRFaction.MORDOR);
        adapter.validate(progression);
        LOTRPlayerData data = new LOTRPlayerData(UUID.fromString(
                "80000000-0000-0000-0000-000000000008"));
        data.load(progression);

        assertEquals(100.0F, data.getAlignment(LOTRFaction.MORDOR), 0.0F);
        assertEquals(0.0F, data.getAlignment(LOTRFaction.GONDOR), 0.0F);
        assertNull(data.getPledgeFaction());
    }
}
