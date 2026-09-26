package com.ninuna.losttales.storage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class NbtTagsTest {
    private static final UUID ID = new UUID(0x0102030405060708L, -77L);

    @Test
    public void aUuidIsTwoLongsUnderItsKey() throws IOException {
        NBTTagCompound written = new NBTTagCompound();
        NbtTags.writeUuid(written, "OwnerUUID", ID);

        NBTTagCompound expected = new NBTTagCompound();
        expected.setLong("OwnerUUIDMost", ID.getMostSignificantBits());
        expected.setLong("OwnerUUIDLeast", ID.getLeastSignificantBits());

        assertArrayEquals(bytes(expected), bytes(written));
        assertEquals(ID, NbtTags.readUuid(written, "OwnerUUID"));
    }

    @Test
    public void aNullIdWritesNothing() {
        NBTTagCompound written = new NBTTagCompound();
        NbtTags.writeUuid(written, "OwnerUUID", null);
        NbtTags.writeUuid(null, "OwnerUUID", ID);

        assertTrue(written.hasNoTags());
    }

    @Test
    public void anIdReadsOnlyWithBothHalvesAsLongs() {
        NBTTagCompound half = new NBTTagCompound();
        half.setLong("IdMost", 1L);
        assertNull(NbtTags.readUuid(half, "Id"));

        NBTTagCompound wrongType = new NBTTagCompound();
        wrongType.setInteger("IdMost", 1);
        wrongType.setLong("IdLeast", 2L);
        assertNull(NbtTags.readUuid(wrongType, "Id"));

        assertNull(NbtTags.readUuid(null, "Id"));
        assertNull(NbtTags.readUuid(new NBTTagCompound(), null));
    }

    @Test
    public void copiedContentsAreCopiesOfEveryTag() {
        NBTTagCompound source = new NBTTagCompound();
        source.setString("Name", "value");
        NBTTagCompound nested = new NBTTagCompound();
        nested.setInteger("Inner", 3);
        source.setTag("Nested", nested);
        NBTTagCompound destination = new NBTTagCompound();
        destination.setInteger("Kept", 1);

        NbtTags.copyContents(source, destination);

        assertEquals("value", destination.getString("Name"));
        assertEquals(3, destination.getCompoundTag("Nested").getInteger("Inner"));
        assertEquals(1, destination.getInteger("Kept"));
        assertNotSame(nested, destination.getCompoundTag("Nested"));
        nested.setInteger("Inner", 4);
        assertEquals(3, destination.getCompoundTag("Nested").getInteger("Inner"));
    }

    @Test
    public void aCompoundListIsAbsentOrCompoundsWithinTheLimit() {
        NBTTagCompound owner = new NBTTagCompound();
        assertTrue(NbtTags.hasCompoundListWithinLimit(owner, "List", 2));

        owner.setTag("List", new NBTTagList());
        assertTrue(NbtTags.hasCompoundListWithinLimit(owner, "List", 2));

        NBTTagList compounds = new NBTTagList();
        compounds.appendTag(new NBTTagCompound());
        compounds.appendTag(new NBTTagCompound());
        owner.setTag("List", compounds);
        assertTrue(NbtTags.hasCompoundListWithinLimit(owner, "List", 2));
        assertFalse(NbtTags.hasCompoundListWithinLimit(owner, "List", 1));

        NBTTagList strings = new NBTTagList();
        strings.appendTag(new NBTTagString("x"));
        owner.setTag("List", strings);
        assertFalse(NbtTags.hasCompoundListWithinLimit(owner, "List", 2));

        owner.setString("List", "x");
        assertFalse(NbtTags.hasCompoundListWithinLimit(owner, "List", 2));
        assertFalse(NbtTags.hasCompoundListWithinLimit(null, "List", 2));
    }

    @Test
    public void aReasonableStringIsWithinItsLengthAndThereWhenRequired() {
        NBTTagCompound owner = new NBTTagCompound();
        assertTrue(NbtTags.hasReasonableString(owner, "Text", 3, false));
        assertFalse(NbtTags.hasReasonableString(owner, "Text", 3, true));

        owner.setString("Text", "");
        assertTrue(NbtTags.hasReasonableString(owner, "Text", 3, false));
        assertFalse(NbtTags.hasReasonableString(owner, "Text", 3, true));

        owner.setString("Text", "abc");
        assertTrue(NbtTags.hasReasonableString(owner, "Text", 3, true));
        owner.setString("Text", "abcd");
        assertFalse(NbtTags.hasReasonableString(owner, "Text", 3, false));

        owner.setInteger("Text", 1);
        assertFalse(NbtTags.hasReasonableString(owner, "Text", 3, false));
        assertFalse(NbtTags.hasReasonableString(null, "Text", 3, true));
        assertTrue(NbtTags.hasReasonableString(null, "Text", 3, false));
    }

    static byte[] bytes(NBTTagCompound tag) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompressedStreamTools.write(tag, new DataOutputStream(out));
        return out.toByteArray();
    }
}
