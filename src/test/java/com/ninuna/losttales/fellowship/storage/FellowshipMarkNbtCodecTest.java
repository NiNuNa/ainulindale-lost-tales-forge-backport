package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.fellowship.model.FellowshipMark;
import com.ninuna.losttales.fellowship.model.FellowshipMarkKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Persistence of the marks of both kinds. */
public final class FellowshipMarkNbtCodecTest {

    private static final UUID FELLOWSHIP =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID PLACER =
            UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID OWNER =
            UUID.fromString("50000000-0000-0000-0000-000000000005");

    @Test
    public void aMarkRoundTrips() {
        FellowshipMark mark = mark(1, "Weathertop", 99L);
        NBTTagCompound encoded = encoded(Collections.singletonList(mark));

        FellowshipMarkNbtCodec.ReadResult result =
                FellowshipMarkNbtCodec.read(encoded);

        assertFalse(result.isReadOnly());
        assertFalse(result.wasRepaired());
        FellowshipMark decoded = result.getMarks().get(mark.getMarkId());
        assertEquals(FellowshipMarkKind.FELLOWSHIP, decoded.getKind());
        assertEquals("Weathertop", decoded.getName());
        assertEquals(FELLOWSHIP, decoded.getFellowshipId());
        assertEquals(PLACER, decoded.getPlacedBy());
        assertEquals(mark.getX(), decoded.getX(), 0.0D);
        assertEquals(mark.getZ(), decoded.getZ(), 0.0D);
        assertEquals(99L, decoded.getPlacedAt());
    }

    /** A go-here mark keeps its owner and place, and no fellowship or name. */
    @Test
    public void aGoHereMarkRoundTrips() {
        FellowshipMark goHere = goHere(7, OWNER);
        NBTTagCompound encoded = encoded(Arrays.asList(mark(1, "Bree", 99L), goHere));
        assertFalse(markTag(encoded, 0).hasKey("FellowshipUUID"));

        FellowshipMarkNbtCodec.ReadResult result = FellowshipMarkNbtCodec.read(encoded);

        assertFalse(result.wasRepaired());
        FellowshipMark decoded = result.getMarks().get(goHere.getMarkId());
        assertTrue(decoded.isGoHere());
        assertEquals(OWNER, decoded.getPlacedBy());
        assertNull(decoded.getFellowshipId());
        assertEquals("", decoded.getName());
        assertEquals(-22000.25D, decoded.getX(), 0.0D);
        assertEquals(2, result.getMarks().size());
    }

    /** One owner holds one go-here mark: a second, from an edited save, is quarantined. */
    @Test
    public void aSecondGoHereMarkOfOneOwnerIsQuarantined() {
        FellowshipMarkNbtCodec.ReadResult result = FellowshipMarkNbtCodec.read(
                encoded(Arrays.asList(goHere(7, OWNER), goHere(8, OWNER))));

        assertEquals(1, result.getMarks().size());
        assertTrue(result.wasRepaired());
        assertEquals("duplicate_go_here",
                result.getQuarantineEntriesCopy().get(0).getString("Reason"));
    }

    /** Go-here marks never count against a fellowship's limit. */
    @Test
    public void goHereMarksDoNotFillAFellowship() {
        List<FellowshipMark> marks = new ArrayList<FellowshipMark>();
        for (int index = 0; index < FellowshipMark.MAX_PER_FELLOWSHIP; index++) {
            marks.add(mark(index + 1, "Camp " + index, 100L + index));
        }
        marks.add(goHere(9, OWNER));

        FellowshipMarkNbtCodec.ReadResult result = FellowshipMarkNbtCodec.read(encoded(marks));

        assertEquals(FellowshipMark.MAX_PER_FELLOWSHIP + 1, result.getMarks().size());
        assertFalse(result.wasRepaired());
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

    /** A mark with no kind, as one saved before marks had kinds, or a kind this build does not know, is quarantined. */
    @Test
    public void aMarkWithoutAKnownKindIsQuarantined() {
        NBTTagCompound noKind = encoded(Collections.singletonList(mark(1, "Bree", 99L)));
        markTag(noKind, 0).removeTag("Kind");
        FellowshipMarkNbtCodec.ReadResult result = FellowshipMarkNbtCodec.read(noKind);
        assertTrue(result.getMarks().isEmpty());
        assertEquals("missing_mark_key",
                result.getQuarantineEntriesCopy().get(0).getString("Reason"));

        NBTTagCompound otherKind = encoded(Collections.singletonList(mark(1, "Bree", 99L)));
        markTag(otherKind, 0).setString("Kind", "camp");
        result = FellowshipMarkNbtCodec.read(otherKind);
        assertTrue(result.getMarks().isEmpty());
        assertEquals("unknown_mark_kind",
                result.getQuarantineEntriesCopy().get(0).getString("Reason"));
    }

    /** A go-here mark that names a fellowship, from an edited save, is quarantined. */
    @Test
    public void aGoHereMarkNamingAFellowshipIsQuarantined() {
        NBTTagCompound encoded = encoded(Collections.singletonList(goHere(7, OWNER)));
        markTag(encoded, 0).setString("Name", "Bree");

        FellowshipMarkNbtCodec.ReadResult result = FellowshipMarkNbtCodec.read(encoded);

        assertTrue(result.getMarks().isEmpty());
        assertEquals("invalid_mark_data",
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

    /**
     * A save whose quarantine would hold more than its bound is kept as it
     * is, read-only; a store whose quarantine is full closes before it
     * takes another mark out.
     */
    @Test
    public void aQuarantinePastItsBoundKeepsTheStoreReadOnly() {
        List<NBTTagCompound> full = new ArrayList<NBTTagCompound>();
        for (int index = 0; index < FellowshipNbtCodec.MAX_QUARANTINED; index++) {
            full.add(FellowshipMarkNbtCodec.createQuarantineEntry("unregistered_dimension",
                    mark(1, "Bree", index)));
        }
        NBTTagCompound atBound = new NBTTagCompound();
        FellowshipMarkNbtCodec.write(atBound, Collections.singletonList(mark(2, "Bree", 1L)),
                full);
        assertFalse(FellowshipMarkNbtCodec.read(atBound).isReadOnly());

        NBTTagCompound pastBound = (NBTTagCompound) atBound.copy();
        markTag(pastBound, 0).setString("Name", "§cBree");
        FellowshipMarkNbtCodec.ReadResult past = FellowshipMarkNbtCodec.read(pastBound);
        assertTrue(past.isReadOnly());
        assertEquals(pastBound, past.getOriginalDataCopy());

        FellowshipMarkWorldData data = new FellowshipMarkWorldData(
                FellowshipMarkWorldData.DATA_NAME);
        data.readFromNBT(atBound);
        assertFalse(data.hasQuarantineRoomFor(1));
        assertTrue("closed", data.isReadOnlyForNewerVersion());
        assertEquals(1, data.getMarkCount());
    }

    private static FellowshipMark mark(int number, String name, long placedAt) {
        return FellowshipMark.ofFellowship(
                UUID.fromString("40000000-0000-0000-0000-00000000000" + number),
                FELLOWSHIP, name, PLACER, 100, 1200.5D + number, -300.0D,
                placedAt);
    }

    private static FellowshipMark goHere(int number, UUID owner) {
        return FellowshipMark.goHere(
                UUID.fromString("60000000-0000-0000-0000-00000000000" + number),
                owner, 100, -22000.25D, -7278.5D, 1234L);
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
