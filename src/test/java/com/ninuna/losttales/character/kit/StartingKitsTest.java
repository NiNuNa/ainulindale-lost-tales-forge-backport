package com.ninuna.losttales.character.kit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Which kits a new character is given and how they fill its inventory
 * (CHAR-40): every kit that fits, the race's and the faction's alike, laid
 * into the main inventory with like items stacked, what has no room left
 * out.
 */
public final class StartingKitsTest {
    private static final String HOBBIT = "losttales:hobbit";
    private static final String DWARF = "losttales:dwarf";
    private static final String SHIRE = "lotr:hobbit";
    private static final String DURIN = "lotr:durins_folk";

    private static final StartingKit HOBBIT_KIT = kit("losttales:hobbit", HOBBIT, "",
            new KitItem("minecraft:bread", 8, 0), new KitItem("lotr:item.hobbitPipe", 1, 0));
    private static final StartingKit SHIRE_KIT = kit("myserver:shire", "", SHIRE,
            new KitItem("minecraft:bread", 4, 0), new KitItem("lotr:item.daggerIron", 1, 0));
    private static final StartingKit SHIRE_HOBBIT_KIT = kit("myserver:shire_hobbit", HOBBIT, SHIRE,
            new KitItem("minecraft:apple", 2, 0));
    private static final List<StartingKit> ALL =
            Arrays.asList(HOBBIT_KIT, SHIRE_KIT, SHIRE_HOBBIT_KIT);

    private static final StartingKitFiles.Game STACKS = new StartingKitFiles.Game() {
        @Override
        public boolean hasRace(String raceId) {
            return true;
        }

        @Override
        public boolean hasFaction(String factionId) {
            return true;
        }

        @Override
        public int stackLimit(String itemId, int damage) {
            if (itemId.startsWith("lotr:item.dagger") || itemId.equals("lotr:item.hobbitPipe")) {
                return 1;
            }
            return itemId.equals("minecraft:gone") ? 0 : 64;
        }

        @Override
        public boolean wornThrough(String itemId, int damage) {
            return false;
        }
    };

    @Test
    public void aHobbitOfTheShireGetsTheRacesKitAndTheFactions() {
        assertEquals(Arrays.asList(HOBBIT_KIT, SHIRE_KIT, SHIRE_HOBBIT_KIT),
                StartingKits.fitting(ALL, HOBBIT, SHIRE));
    }

    @Test
    public void aKitFitsOnlyWhatItNames() {
        assertEquals(Arrays.asList(SHIRE_KIT), StartingKits.fitting(ALL, DWARF, SHIRE));
        assertEquals(Arrays.asList(HOBBIT_KIT), StartingKits.fitting(ALL, HOBBIT, DURIN));
        assertEquals(new ArrayList<StartingKit>(), StartingKits.fitting(ALL, DWARF, DURIN));
        assertFalse(kit("a:b", "", "", new KitItem("minecraft:bread", 1, 0)).fits(HOBBIT, SHIRE));
    }

    @Test
    public void likeItemsShareAStackAndTheHotbarFillsFirst() {
        StartingKits.Packed packed = StartingKits.pack(
                StartingKits.fitting(ALL, HOBBIT, SHIRE), STACKS);
        assertEquals(Arrays.asList("minecraft:bread@0*12", "lotr:item.hobbitPipe@0*1",
                "lotr:item.daggerIron@0*1", "minecraft:apple@0*2"), texts(packed.slots));
        assertTrue(packed.leftOut.isEmpty());
    }

    @Test
    public void whatFindsNoRoomIsLeftOut() {
        List<KitItem> items = new ArrayList<KitItem>();
        for (int index = 0; index < 35; index++) {
            items.add(new KitItem("lotr:item.daggerIron", 1, 0));
        }
        items.add(new KitItem("minecraft:bread", 64, 0));
        items.add(new KitItem("minecraft:bread", 10, 0));
        items.add(new KitItem("minecraft:gone", 3, 0));
        StartingKits.Packed packed = StartingKits.pack(Arrays.asList(
                new StartingKit("a:b", HOBBIT, "", items)), STACKS);
        assertEquals(StartingKits.SLOTS, packed.slots.size());
        assertEquals("minecraft:bread@0*64", packed.slots.get(35).toString());
        assertEquals(Arrays.asList("minecraft:bread@0*10", "minecraft:gone@0*3"),
                texts(packed.leftOut));
    }

    @Test
    public void aDifferentDamageIsADifferentStack() {
        StartingKits.Packed packed = StartingKits.pack(Arrays.asList(kit("a:b", HOBBIT, "",
                new KitItem("minecraft:wool", 2, 14), new KitItem("minecraft:wool", 2, 0),
                new KitItem("minecraft:wool", 2, 14))), STACKS);
        assertEquals(Arrays.asList("minecraft:wool@14*4", "minecraft:wool@0*2"),
                texts(packed.slots));
    }

    private static StartingKit kit(String id, String race, String faction, KitItem... items) {
        return new StartingKit(id, race, faction, Arrays.asList(items));
    }

    private static List<String> texts(List<KitItem> items) {
        List<String> texts = new ArrayList<String>();
        for (KitItem item : items) {
            texts.add(item.toString());
        }
        return texts;
    }
}
