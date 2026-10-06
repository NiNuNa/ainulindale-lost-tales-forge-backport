package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.fellowship.model.FellowshipMark;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Persistence of the fellowships' marks. */
public final class FellowshipMarkNbtCodecTest {

    private static final UUID FELLOWSHIP =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID PLACER =
            UUID.fromString("30000000-0000-0000-0000-000000000003");

    @Test
    public void aMarkRoundTrips() {
        FellowshipMark mark = mark(1, "Weathertop", 99L);
        NBTTagCompound encoded = encoded(Collections.singletonList(mark));

        FellowshipMarkNbtCodec.ReadResult result =
                FellowshipMarkNbtCodec.read(encoded);

        assertFalse(result.isReadOnly());
        assertFalse(result.wasRepaired());
        FellowshipMark decoded = result.getMarks().get(mark.getMarkId());
        assertEquals("Weathertop", decoded.getName());
        assertEquals(FELLOWSHIP, decoded.getFellowshipId());
        assertEquals(PLACER, decoded.getPlacedBy());
        assertEquals(mark.getX(), decoded.getX(), 0.0D);
        assertEquals(mark.getZ(), decoded.getZ(), 0.0D);
        assertEquals(99L, decoded.getPlacedAt());
    }

    /** A fellowship never holds more marks than it may: the newest past the limit is quarantined, not dropped. */
    @Test
    public void aMarkPastTheLimitIsQuarantined() {
        List<FellowshipMark> marks = new ArrayList<FellowshipMark>();
        for (int index = 0; index <= FellowshipMark.MAX_PER_FELLOWSHIP; index++) {
            marks.add(mark(index + 1, "Camp " + index, 100L + index));
        }

        FellowshipMarkNbtCodec.ReadResult result =
                FellowshipMarkNbtCodec.read(encoded(marks));

        assertEquals(FellowshipMark.MAX_PER_FELLOWSHIP, result.getMarks().size());
        assertTrue(result.wasRepaired());
        assertEquals(1, result.getQuarantineEntriesCopy().size());
        assertEquals("too_many_marks",
                result.getQuarantineEntriesCopy().get(0).getString("Reason"));
    }

    /** A mark lacking a key this build always writes goes to the quarantine whole; nothing is made up for it. */
    @Test
    public void aMarkWithoutItsNameIsQuarantined() {
        NBTTagCompound encoded = encoded(Collections.singletonList(
                mark(1, "Bree", 99L)));
        markTag(encoded, 0).removeTag("Name");

        FellowshipMarkNbtCodec.ReadResult result =
                FellowshipMarkNbtCodec.read(encoded);

        assertTrue(result.getMarks().isEmpty());
        assertTrue(result.wasRepaired());
        assertEquals("missing_mark_key",
                result.getQuarantineEntriesCopy().get(0).getString("Reason"));
    }

    /** A name no mark may carry, from an edited save, is quarantined rather than shown. */
    @Test
    public void aMarkWithAFormattedNameIsQuarantined() {
        NBTTagCompound encoded = encoded(Collections.singletonList(
                mark(1, "Bree", 99L)));
        markTag(encoded, 0).setString("Name", "§cBree");

        FellowshipMarkNbtCodec.ReadResult result =
                FellowshipMarkNbtCodec.read(encoded);

        assertTrue(result.getMarks().isEmpty());
        assertEquals("invalid_mark_data",
                result.getQuarantineEntriesCopy().get(0).getString("Reason"));
    }

    /** A mark or root at another version than this build writes keeps the whole store as it is, read-only. */
    @Test
    public void anotherVersionHoldsTheWholeStoreReadOnly() {
        NBTTagCompound newerMark = encoded(Collections.singletonList(
                mark(1, "Bree", 99L)));
        markTag(newerMark, 0).setInteger("DataVersion",
                FellowshipMark.CURRENT_DATA_VERSION + 1);
        FellowshipMarkNbtCodec.ReadResult markResult =
                FellowshipMarkNbtCodec.read(newerMark);
        assertTrue(markResult.isReadOnly());
        assertTrue(markResult.getMarks().isEmpty());
        assertEquals(newerMark, markResult.getOriginalDataCopy());

        NBTTagCompound newerRoot = encoded(Collections.singletonList(
                mark(1, "Bree", 99L)));
        newerRoot.setInteger("DataVersion",
                FellowshipMarkNbtCodec.CURRENT_ROOT_DATA_VERSION + 1);
        assertTrue(FellowshipMarkNbtCodec.read(newerRoot).isReadOnly());
    }

    private static FellowshipMark mark(int number, String name, long placedAt) {
        return new FellowshipMark(
                UUID.fromString("40000000-0000-0000-0000-00000000000" + number),
                FELLOWSHIP, name, PLACER, 100, 1200.5D + number, -300.0D,
                placedAt);
    }

    private static NBTTagCompound encoded(List<FellowshipMark> marks) {
        NBTTagCompound encoded = new NBTTagCompound();
        FellowshipMarkNbtCodec.write(encoded, marks,
                Collections.<NBTTagCompound>emptyList());
        return encoded;
    }

    private static NBTTagCompound markTag(NBTTagCompound encoded, int index) {
        NBTTagList list = encoded.getTagList("Marks", Constants.NBT.TAG_COMPOUND);
        return list.getCompoundTagAt(index);
    }
}
