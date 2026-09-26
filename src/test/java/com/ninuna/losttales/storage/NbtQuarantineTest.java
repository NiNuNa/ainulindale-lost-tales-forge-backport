package com.ninuna.losttales.storage;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public final class NbtQuarantineTest {

    @Test
    public void theQuarantineIsWrittenInTheStoresLayout() throws IOException {
        NBTTagCompound first = record("missing_account", 3);
        NBTTagCompound second = record("over_capacity", 9);
        NBTTagCompound root = new NBTTagCompound();
        NbtQuarantine.write(root, Arrays.asList(first, null, second));

        NBTTagCompound expected = new NBTTagCompound();
        NBTTagCompound quarantine = new NBTTagCompound();
        quarantine.setInteger("DataVersion", 1);
        NBTTagList entries = new NBTTagList();
        entries.appendTag(first.copy());
        entries.appendTag(second.copy());
        quarantine.setTag("Entries", entries);
        expected.setTag("Quarantine", quarantine);

        assertArrayEquals(NbtTagsTest.bytes(expected), NbtTagsTest.bytes(root));
        assertNotSame(first, root.getCompoundTag("Quarantine")
                .getTagList("Entries", 10).getCompoundTagAt(0));
    }

    @Test
    public void aWrittenQuarantineReadsBackUnchanged() throws IOException {
        NBTTagCompound root = new NBTTagCompound();
        NbtQuarantine.write(root, Arrays.asList(record("a", 0), record("b", 1)));

        NbtQuarantine.Read read = NbtQuarantine.read(root);

        assertTrue(read.isSupported());
        assertFalse(read.isRepaired());
        assertEquals(2, read.getEntries().size());
        assertEquals("b", read.getEntries().get(1).getString("Reason"));
        NBTTagCompound again = new NBTTagCompound();
        NbtQuarantine.write(again, read.getEntries());
        assertArrayEquals(NbtTagsTest.bytes(root), NbtTagsTest.bytes(again));
        assertTrue(NbtQuarantine.readCurrentVersionOnly(root).isSupported());
    }

    @Test
    public void aMissingQuarantineReadsEmptyAndIsWrittenAgain() {
        NbtQuarantine.Read read = NbtQuarantine.read(new NBTTagCompound());

        assertTrue(read.isSupported());
        assertTrue(read.isRepaired());
        assertTrue(read.getEntries().isEmpty());
    }

    @Test
    public void anOlderQuarantineIsReadAndWrittenAgainUnlessOnlyTheCurrentIsRead() {
        NBTTagCompound root = new NBTTagCompound();
        NbtQuarantine.write(root, Collections.singletonList(record("a", 0)));
        root.getCompoundTag("Quarantine").removeTag("DataVersion");

        NbtQuarantine.Read read = NbtQuarantine.read(root);
        assertTrue(read.isSupported());
        assertTrue(read.isRepaired());
        assertEquals(1, read.getEntries().size());

        NbtQuarantine.Read strict = NbtQuarantine.readCurrentVersionOnly(root);
        assertFalse(strict.isSupported());
        assertEquals(0, strict.getUnsupportedVersion());
    }

    @Test
    public void aNewerOrNegativeVersionIsUnsupported() {
        NBTTagCompound root = new NBTTagCompound();
        NbtQuarantine.write(root, Collections.<NBTTagCompound>emptyList());
        root.getCompoundTag("Quarantine").setInteger("DataVersion", 2);
        NbtQuarantine.Read newer = NbtQuarantine.read(root);
        assertFalse(newer.isSupported());
        assertEquals(2, newer.getUnsupportedVersion());

        root.getCompoundTag("Quarantine").setInteger("DataVersion", -1);
        assertEquals(-1, NbtQuarantine.read(root).getUnsupportedVersion());
        assertFalse(NbtQuarantine.read(root).isSupported());
    }

    @Test
    public void aQuarantineOfTheWrongShapeIsUnsupported() {
        NBTTagCompound notCompound = new NBTTagCompound();
        notCompound.setString("Quarantine", "x");
        assertFalse(NbtQuarantine.read(notCompound).isSupported());
        assertEquals(-1, NbtQuarantine.read(notCompound).getUnsupportedVersion());

        NBTTagCompound notList = new NBTTagCompound();
        NbtQuarantine.write(notList, Collections.<NBTTagCompound>emptyList());
        notList.getCompoundTag("Quarantine").setString("Entries", "x");
        assertFalse(NbtQuarantine.read(notList).isSupported());

        // A list of anything but compounds would read back empty and be lost.
        NBTTagCompound strings = new NBTTagCompound();
        NbtQuarantine.write(strings, Collections.<NBTTagCompound>emptyList());
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagString("x"));
        strings.getCompoundTag("Quarantine").setTag("Entries", list);
        assertFalse(NbtQuarantine.read(strings).isSupported());
    }

    @Test
    public void aQuarantineWithoutItsListReadsEmptyAndIsWrittenAgain() {
        NBTTagCompound root = new NBTTagCompound();
        NbtQuarantine.write(root, Collections.<NBTTagCompound>emptyList());
        root.getCompoundTag("Quarantine").removeTag("Entries");

        NbtQuarantine.Read read = NbtQuarantine.read(root);

        assertTrue(read.isSupported());
        assertTrue(read.isRepaired());
        assertTrue(read.getEntries().isEmpty());
    }

    @Test
    public void anEntryKeepsItsReasonIndexAndACopyOfWhatItHeld() throws IOException {
        NBTTagCompound original = new NBTTagCompound();
        original.setInteger("A", 1);

        NBTTagCompound entry = NbtQuarantine.entry("duplicate", "MuteIndex", 4, original);

        NBTTagCompound expected = new NBTTagCompound();
        expected.setString("Reason", "duplicate");
        expected.setInteger("MuteIndex", 4);
        expected.setTag("OriginalData", original.copy());
        assertArrayEquals(NbtTagsTest.bytes(expected), NbtTagsTest.bytes(entry));
        assertNotSame(original, entry.getCompoundTag("OriginalData"));

        NBTTagCompound bare = NbtQuarantine.entry(null, "EntryIndex", 0, null);
        assertEquals("unknown", bare.getString("Reason"));
        assertEquals(0, bare.getInteger("EntryIndex"));
        assertFalse(bare.hasKey("OriginalData"));
    }

    private static NBTTagCompound record(String reason, int index) {
        NBTTagCompound record = new NBTTagCompound();
        record.setString("Reason", reason);
        record.setInteger("EntryIndex", index);
        return record;
    }
}
