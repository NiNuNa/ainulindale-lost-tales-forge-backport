package com.ninuna.losttales.quest.missive;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The world's record of missives taken (QUEST-34): a missive is taken
 * once by anyone, the record outlasts a save, forgets what could no
 * longer stand on a board, and fails closed on data it cannot read.
 */
public final class MissivesTakenWorldDataTest {
    private static final String A = "losttales:missive/generated/a";
    private static final String B = "losttales:missive/generated/b";

    private static MissivesTakenWorldData data() {
        return new MissivesTakenWorldData(MissivesTakenWorldData.DATA_NAME);
    }

    private static MissivesTakenWorldData reloaded(MissivesTakenWorldData data) {
        NBTTagCompound tag = new NBTTagCompound();
        data.writeToNBT(tag);
        MissivesTakenWorldData read = data();
        read.readFromNBT(tag);
        return read;
    }

    @Test
    public void aMissiveIsTakenOnceAndRemembered() {
        MissivesTakenWorldData data = data();
        assertFalse(data.wasTaken(A));
        assertTrue(data.take(A, 100L, 200L, 0L));
        assertTrue(data.wasTaken(A));
        MissivesTakenWorldData read = reloaded(data);
        assertTrue(read.isUsable());
        assertTrue("a save keeps it", read.wasTaken(A));
        assertFalse(read.wasTaken(B));
    }

    @Test
    public void whatCouldNoLongerStandOnABoardIsForgotten() {
        MissivesTakenWorldData data = data();
        data.take(A, 100L, 100L, 1000L);
        data.take(B, 900L, 1000L, 1000L);
        assertTrue("posted at 100, still on boards at 1000", data.wasTaken(A));
        assertTrue(data.take("losttales:missive/generated/c", 1500L, 1500L, 1000L));
        assertFalse("gone from boards by 1100", data.wasTaken(A));
        assertTrue(data.wasTaken(B));
    }

    /** Past its bound the record refuses a new missive and forgets none it holds. */
    @Test
    public void theRecordRefusesPastItsBound() {
        MissivesTakenWorldData data = data();
        for (int index = 0; index < MissivesTakenWorldData.MAX_ENTRIES; index++) {
            assertTrue(data.take("losttales:missive/generated/" + index, index,
                    index, 0L));
        }
        assertFalse("a full record takes no more", data.take(
                "losttales:missive/generated/full", 1L, 1L, 0L));
        assertEquals(MissivesTakenWorldData.MAX_ENTRIES, data.size());
        assertTrue("the oldest is still remembered",
                data.wasTaken("losttales:missive/generated/0"));
        assertFalse(data.wasTaken("losttales:missive/generated/full"));
    }

    /**
     * A letter keeps its first posting's days wherever it is pinned
     * (QUEST-34), so the record keeps each missive for the longest days a
     * notice may stand from then, whatever the setting is now, and for
     * good while notices never expire.
     */
    @Test
    public void theRecordLastsAsLongAsACopyCouldStand() {
        long keep = MissivesTaken.keepTicks(true);
        assertEquals(MissivesTaken.LONGEST_NOTICE_DAYS * 24000L, keep);
        assertEquals(0L, MissivesTaken.keepTicks(false));

        MissivesTakenWorldData data = data();
        data.take(A, 100L, 100L, keep);
        data.take(B, keep, keep, keep);
        assertTrue("posted at 100, a copy could still stand", data.wasTaken(A));
        data.take("losttales:missive/generated/c", keep + 100L, keep + 100L, keep);
        assertFalse("past the longest days a notice may stand", data.wasTaken(A));
        assertTrue(data.wasTaken(B));

        MissivesTakenWorldData forGood = data();
        forGood.take(A, 100L, 100L, MissivesTaken.keepTicks(false));
        forGood.take(B, 100L, 100L * keep, MissivesTaken.keepTicks(false));
        assertTrue("notices that never expire are remembered for good",
                forGood.wasTaken(A));
    }

    /** Data of another version is kept as it was, and no missive is taken meanwhile. */
    @Test
    public void unreadableDataFailsClosedAndIsKeptVerbatim() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger(MissivesTakenWorldData.TAG_VERSION, 2);
        newer.setString("Future", "kept");
        MissivesTakenWorldData data = data();
        data.readFromNBT(newer);
        assertFalse(data.isUsable());
        assertFalse(data.take(A, 1L, 1L, 0L));
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        assertEquals(newer, written);
    }

    /** An entry that cannot be read is kept aside, untouched, and the rest still read. */
    @Test
    public void aBrokenEntryIsQuarantined() {
        MissivesTakenWorldData data = data();
        data.take(A, 1L, 1L, 0L);
        NBTTagCompound tag = new NBTTagCompound();
        data.writeToNBT(tag);
        NBTTagCompound broken = new NBTTagCompound();
        broken.setString(MissivesTakenWorldData.TAG_QUEST_ID, B);
        tag.getTagList(MissivesTakenWorldData.TAG_TAKEN, 10).appendTag(broken);
        MissivesTakenWorldData read = data();
        read.readFromNBT(tag);
        assertTrue(read.isUsable());
        assertTrue(read.wasTaken(A));
        assertFalse(read.wasTaken(B));
        NBTTagCompound again = new NBTTagCompound();
        read.writeToNBT(again);
        NBTTagList aside = again.getTagList(MissivesTakenWorldData.TAG_QUARANTINED, 10);
        assertEquals(1, aside.tagCount());
        assertEquals(broken, aside.getCompoundTagAt(0));
    }
}
