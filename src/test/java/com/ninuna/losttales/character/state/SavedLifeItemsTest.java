package com.ninuna.losttales.character.state;

import com.ninuna.losttales.accessory.player.AccessoryPlayerData;
import com.ninuna.losttales.storage.NbtTags;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What a saved life holds that the game no longer takes is taken out as it
 * is read, each stack named with the character, generation and part it
 * came from; the rest stays as it was (CHAR-46). A stand-in check marks
 * the stacks the game would refuse.
 */
public final class SavedLifeItemsTest {

    private static final UUID CHARACTER =
            UUID.fromString("46000000-0000-0000-0000-000000000046");

    /** The stand-in game: a stack's {@code Test} word says what it would answer. */
    private static final SavedLifeItems.Check CHECK = new SavedLifeItems.Check() {
        @Override
        public String refusal(NBTTagCompound itemTag) {
            String word = itemTag.getString("Test");
            return word.length() == 0 ? null : word;
        }
    };

    @Test
    public void unknownAndOverstackedStacksAreTakenOutAndTheRestStays() {
        CharacterPlayerStateSnapshot snapshot = snapshot(7L,
                inventory(item(0, ""), item(1, SavedLifeItems.WHY_UNKNOWN)),
                enderChest(item(2, SavedLifeItems.WHY_OVERSTACKED), item(3, "")),
                accessory(null, 0));
        List<NBTTagCompound> setAside = new ArrayList<NBTTagCompound>();

        CharacterPlayerStateSnapshot cleaned = SavedLifeItems.clean(snapshot, CHECK, setAside);

        assertNotSame(snapshot, cleaned);
        assertEquals(1, items(cleaned, "vanilla_inventory").tagCount());
        assertEquals(0, items(cleaned, "vanilla_inventory").getCompoundTagAt(0).getByte("Slot"));
        assertEquals(1, items(cleaned, "vanilla_ender_chest").tagCount());
        assertEquals(3, items(cleaned, "vanilla_ender_chest").getCompoundTagAt(0).getByte("Slot"));
        assertEquals("the rest of the part stays", 4,
                cleaned.getComponent("vanilla_inventory").getInteger("CurrentItem"));
        assertEquals(7L, cleaned.getGeneration());

        assertEquals(2, setAside.size());
        NBTTagCompound first = setAside.get(0);
        assertEquals("vanilla_inventory", first.getString(SavedLifeItems.TAG_COMPONENT));
        assertEquals(SavedLifeItems.WHY_UNKNOWN, first.getString(SavedLifeItems.TAG_WHY));
        assertEquals(CHARACTER, NbtTags.readUuid(first, SavedLifeItems.TAG_CHARACTER));
        assertEquals(7L, first.getLong(SavedLifeItems.TAG_GENERATION));
        assertEquals(1, first.getCompoundTag(SavedLifeItems.TAG_ITEM).getByte("Slot"));
        assertEquals("vanilla_ender_chest",
                setAside.get(1).getString(SavedLifeItems.TAG_COMPONENT));
        assertEquals(SavedLifeItems.WHY_OVERSTACKED,
                setAside.get(1).getString(SavedLifeItems.TAG_WHY));
    }

    @Test
    public void aLifeTheGameTakesWholeIsKeptAsItWas() {
        CharacterPlayerStateSnapshot snapshot = snapshot(1L,
                inventory(item(0, "")), enderChest(), accessory(ring("", 1), 0));
        List<NBTTagCompound> setAside = new ArrayList<NBTTagCompound>();

        assertSame(snapshot, SavedLifeItems.clean(snapshot, CHECK, setAside));
        assertTrue(setAside.isEmpty());
    }

    @Test
    public void aWornRingTheGameLacksIsTakenOut() {
        CharacterPlayerStateSnapshot snapshot = snapshot(2L, inventory(), enderChest(),
                accessory(ring(SavedLifeItems.WHY_UNKNOWN, 1), 0));
        List<NBTTagCompound> setAside = new ArrayList<NBTTagCompound>();

        CharacterPlayerStateSnapshot cleaned = SavedLifeItems.clean(snapshot, CHECK, setAside);

        assertFalse(cleaned.getComponent("losttales_accessory").hasKey("Equipped"));
        assertEquals(1, setAside.size());
        assertEquals("losttales_accessory", setAside.get(0).getString(SavedLifeItems.TAG_COMPONENT));
    }

    /** A worn stack of rings goes among the rings kept aside, which hand it back. */
    @Test
    public void aWornStackOfRingsIsKeptAsideForTheInventory() {
        CharacterPlayerStateSnapshot snapshot = snapshot(2L, inventory(), enderChest(),
                accessory(ring("", 3), 2));
        List<NBTTagCompound> setAside = new ArrayList<NBTTagCompound>();

        CharacterPlayerStateSnapshot cleaned = SavedLifeItems.clean(snapshot, CHECK, setAside);

        NBTTagCompound accessory = cleaned.getComponent("losttales_accessory");
        assertFalse(accessory.hasKey("Equipped"));
        NBTTagList kept = accessory.getTagList("Rejected", Constants.NBT.TAG_COMPOUND);
        assertEquals(3, kept.tagCount());
        assertEquals(3, kept.getCompoundTagAt(2).getByte("Count"));
        assertTrue(setAside.isEmpty());
    }

    /** With the rings kept aside at their bound, a worn stack is set aside in the store instead. */
    @Test
    public void aWornStackPastTheBoundGoesToTheStore() {
        CharacterPlayerStateSnapshot snapshot = snapshot(2L, inventory(), enderChest(),
                accessory(ring("", 3), AccessoryPlayerData.MAX_KEPT_ENTRIES));
        List<NBTTagCompound> setAside = new ArrayList<NBTTagCompound>();

        CharacterPlayerStateSnapshot cleaned = SavedLifeItems.clean(snapshot, CHECK, setAside);

        assertEquals(AccessoryPlayerData.MAX_KEPT_ENTRIES, cleaned.getComponent(
                "losttales_accessory").getTagList("Rejected", Constants.NBT.TAG_COMPOUND)
                .tagCount());
        assertEquals(1, setAside.size());
    }

    /** Both generations are cleaned; a record holding nothing to take out comes back as it was. */
    @Test
    public void aRecordIsCleanedInBothGenerations() {
        CharacterPlayerStateRecord record = new CharacterPlayerStateRecord(CHARACTER,
                snapshot(5L, inventory(item(0, SavedLifeItems.WHY_UNKNOWN)), enderChest(),
                        accessory(null, 0)),
                snapshot(4L, inventory(item(0, SavedLifeItems.WHY_UNKNOWN)), enderChest(),
                        accessory(null, 0)));
        List<NBTTagCompound> setAside = new ArrayList<NBTTagCompound>();

        CharacterPlayerStateRecord cleaned = SavedLifeItems.clean(record, CHECK, setAside);

        assertEquals(0, items(cleaned.getCurrent(), "vanilla_inventory").tagCount());
        assertEquals(0, items(cleaned.getPrevious(), "vanilla_inventory").tagCount());
        assertEquals(5L, cleaned.getCurrentGeneration());
        assertEquals(2, setAside.size());
        assertEquals(5L, setAside.get(0).getLong(SavedLifeItems.TAG_GENERATION));
        assertEquals(4L, setAside.get(1).getLong(SavedLifeItems.TAG_GENERATION));

        CharacterPlayerStateRecord whole = new CharacterPlayerStateRecord(CHARACTER,
                snapshot(1L, inventory(), enderChest(), accessory(null, 0)), null);
        assertSame(whole, SavedLifeItems.clean(whole, CHECK, new ArrayList<NBTTagCompound>()));
    }

    private static NBTTagList items(CharacterPlayerStateSnapshot snapshot, String component) {
        return snapshot.getComponent(component).getTagList("Items", Constants.NBT.TAG_COMPOUND);
    }

    private static CharacterPlayerStateSnapshot snapshot(long generation,
                                                         NBTTagCompound inventory,
                                                         NBTTagCompound enderChest,
                                                         NBTTagCompound accessory) {
        LinkedHashMap<String, NBTTagCompound> components =
                new LinkedHashMap<String, NBTTagCompound>();
        components.put("vanilla_inventory", inventory);
        components.put("vanilla_ender_chest", enderChest);
        components.put("losttales_accessory", accessory);
        return new CharacterPlayerStateSnapshot(CHARACTER, generation, 10L,
                CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION, components);
    }

    private static NBTTagCompound inventory(NBTTagCompound... items) {
        NBTTagCompound state = withItems(items);
        state.setInteger("CurrentItem", 4);
        return state;
    }

    private static NBTTagCompound enderChest(NBTTagCompound... items) {
        return withItems(items);
    }

    private static NBTTagCompound withItems(NBTTagCompound... items) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("Version", 1);
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound item : items) {
            list.appendTag(item);
        }
        state.setTag("Items", list);
        return state;
    }

    private static NBTTagCompound accessory(NBTTagCompound worn, int keptAside) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("Version", 1);
        if (worn != null) {
            state.setTag("Equipped", worn);
        }
        if (keptAside > 0) {
            NBTTagList rejected = new NBTTagList();
            for (int index = 0; index < keptAside; index++) {
                rejected.appendTag(ring("", 1));
            }
            state.setTag("Rejected", rejected);
        }
        return state;
    }

    private static NBTTagCompound item(int slot, String test) {
        NBTTagCompound item = new NBTTagCompound();
        item.setByte("Slot", (byte)slot);
        item.setShort("id", (short)1);
        item.setByte("Count", (byte)1);
        item.setString("Test", test);
        return item;
    }

    private static NBTTagCompound ring(String test, int count) {
        NBTTagCompound ring = new NBTTagCompound();
        ring.setShort("id", (short)2);
        ring.setByte("Count", (byte)count);
        ring.setString("Test", test);
        return ring;
    }
}
