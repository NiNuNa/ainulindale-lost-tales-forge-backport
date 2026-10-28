package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.state.SavedLifeItems;
import com.ninuna.losttales.storage.NbtTags;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The line a player is told names each character's stacks set aside from
 * its newest generation only: the one before holds a copy of the same
 * things (CHAR-46).
 */
public final class SavedLifeNoticesTest {

    private static final UUID ALDRIC = UUID.fromString("46000000-0000-0000-0000-0000000000a1");
    private static final UUID BERIC = UUID.fromString("46000000-0000-0000-0000-0000000000b2");

    @Test
    public void onlyTheNewestGenerationOfEachCharacterIsTold() {
        NBTTagList items = new NBTTagList();
        items.appendTag(kept(ALDRIC, 5L, SavedLifeItems.WHY_UNKNOWN));
        items.appendTag(kept(ALDRIC, 5L, SavedLifeItems.WHY_OVERSTACKED));
        items.appendTag(kept(ALDRIC, 4L, SavedLifeItems.WHY_UNKNOWN));
        items.appendTag(kept(BERIC, 2L, SavedLifeItems.WHY_UNKNOWN));
        items.appendTag(kept(BERIC, 2L, SavedLifeItems.WHY_UNKNOWN));
        Map<UUID, SavedLifeNotices.Kept> byCharacter =
                new LinkedHashMap<UUID, SavedLifeNotices.Kept>();

        SavedLifeNotices.collect(items, byCharacter);

        assertEquals(2, byCharacter.size());
        SavedLifeNotices.Kept aldric = byCharacter.get(ALDRIC);
        assertEquals(1, aldric.unknown);
        assertEquals(1, aldric.known.size());
        SavedLifeNotices.Kept beric = byCharacter.get(BERIC);
        assertEquals(2, beric.unknown);
        assertTrue(beric.known.isEmpty());
    }

    @Test
    public void anEntryNamingNoCharacterIsLeftOut() {
        NBTTagList items = new NBTTagList();
        NBTTagCompound nameless = new NBTTagCompound();
        nameless.setString(SavedLifeItems.TAG_WHY, SavedLifeItems.WHY_UNKNOWN);
        items.appendTag(nameless);
        Map<UUID, SavedLifeNotices.Kept> byCharacter =
                new LinkedHashMap<UUID, SavedLifeNotices.Kept>();

        SavedLifeNotices.collect(items, byCharacter);

        assertTrue(byCharacter.isEmpty());
    }

    private static NBTTagCompound kept(UUID character, long generation, String why) {
        NBTTagCompound entry = new NBTTagCompound();
        NbtTags.writeUuid(entry, SavedLifeItems.TAG_CHARACTER, character);
        entry.setLong(SavedLifeItems.TAG_GENERATION, generation);
        entry.setString(SavedLifeItems.TAG_WHY, why);
        NBTTagCompound item = new NBTTagCompound();
        item.setShort("id", (short)31000);
        item.setByte("Count", (byte)70);
        entry.setTag(SavedLifeItems.TAG_ITEM, item);
        return entry;
    }
}
