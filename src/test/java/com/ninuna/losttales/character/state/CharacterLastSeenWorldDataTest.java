package com.ninuna.losttales.character.state;

import com.ninuna.losttales.storage.NbtTags;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** When each identity was last played or heard, kept safely and failing closed. */
public final class CharacterLastSeenWorldDataTest {
    private static final long DAY = 24L * 3600000L;

    @Test
    public void anIdentitySeenLatelyIsSeenAndOthersAreNot() {
        CharacterLastSeenWorldData data = new CharacterLastSeenWorldData(CharacterLastSeenWorldData.DATA_NAME);
        UUID aldric = UUID.randomUUID();
        long now = System.currentTimeMillis();
        data.saw(aldric, now - 3 * DAY);
        assertTrue(data.seenWithin(aldric, 30 * DAY, now));
        assertFalse(data.seenWithin(aldric, 2 * DAY, now));
        assertFalse(data.seenWithin(UUID.randomUUID(), 30 * DAY, now));
    }

    @Test
    public void aTimeIsWrittenAtMostOnceAnHour() {
        CharacterLastSeenWorldData data = new CharacterLastSeenWorldData(CharacterLastSeenWorldData.DATA_NAME);
        UUID aldric = UUID.randomUUID();
        long first = 1000000000L;
        data.saw(aldric, first);
        data.saw(aldric, first + CharacterLastSeenWorldData.RECORD_EVERY_MILLIS - 1);
        assertFalse(data.seenWithin(aldric, 0L,
                first + CharacterLastSeenWorldData.RECORD_EVERY_MILLIS - 1));
        data.saw(aldric, first + CharacterLastSeenWorldData.RECORD_EVERY_MILLIS);
        assertTrue(data.seenWithin(aldric, 0L,
                first + CharacterLastSeenWorldData.RECORD_EVERY_MILLIS));
    }

    @Test
    public void itComesBackFromTheSaveAndForgetsTheLongGone() {
        CharacterLastSeenWorldData data = new CharacterLastSeenWorldData(CharacterLastSeenWorldData.DATA_NAME);
        UUID recent = UUID.randomUUID();
        UUID longGone = UUID.randomUUID();
        long now = System.currentTimeMillis();
        data.saw(recent, now - DAY);
        data.saw(longGone, now - CharacterLastSeenWorldData.FORGET_AFTER_MILLIS - DAY);
        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);

        CharacterLastSeenWorldData loaded = new CharacterLastSeenWorldData(CharacterLastSeenWorldData.DATA_NAME);
        loaded.readFromNBT(saved);
        assertTrue(loaded.seenWithin(recent, 30 * DAY, now));
        assertFalse(loaded.seenWithin(longGone, Long.MAX_VALUE, now));
    }

    @Test
    public void anotherVersionIsKeptWholeAndSeesNobody() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger("DataVersion", CharacterLastSeenWorldData.DATA_VERSION + 1);
        NBTTagList seen = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        UUID aldric = UUID.randomUUID();
        NbtTags.writeUuid(entry, "Id", aldric);
        entry.setLong("At", System.currentTimeMillis());
        seen.appendTag(entry);
        newer.setTag("Seen", seen);

        CharacterLastSeenWorldData data = new CharacterLastSeenWorldData(CharacterLastSeenWorldData.DATA_NAME);
        data.readFromNBT(newer);
        assertFalse(data.seenWithin(aldric, Long.MAX_VALUE, System.currentTimeMillis()));
        data.saw(aldric, System.currentTimeMillis());

        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        assertEquals(CharacterLastSeenWorldData.DATA_VERSION + 1, written.getInteger("DataVersion"));
    }

    @Test
    public void anUnreadableRecordIsQuarantinedNotLost() {
        NBTTagCompound stored = new NBTTagCompound();
        stored.setInteger("DataVersion", CharacterLastSeenWorldData.DATA_VERSION);
        NBTTagList seen = new NBTTagList();
        NBTTagCompound broken = new NBTTagCompound();
        broken.setString("Id", "not an id");
        seen.appendTag(broken);
        stored.setTag("Seen", seen);

        CharacterLastSeenWorldData data = new CharacterLastSeenWorldData(CharacterLastSeenWorldData.DATA_NAME);
        data.readFromNBT(stored);
        NBTTagCompound written = new NBTTagCompound();
        data.writeToNBT(written);
        assertEquals(1, written.getCompoundTag("Quarantine").getTagList("Entries", 10).tagCount());
    }
}
