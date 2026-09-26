package com.ninuna.losttales.quest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.StringTranslate;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * One reading of an item a quest file writes, so the reward the server
 * grants and the reward every screen names are the same item, count and
 * meta. No item is registered here, so each is named by its id.
 */
public final class LostTalesQuestItemSpecTest {

    @BeforeClass
    public static void loadTheModsWords() {
        StringTranslate.inject(LostTalesQuestItemSpecTest.class
                .getResourceAsStream("/assets/losttales/lang/en_US.lang"));
    }

    @Test
    public void anItemIsReadWithItsMetaAndCount() {
        assertSpec("minecraft:bread", 1, 0,
                LostTalesQuestItemSpec.parse("minecraft:bread", 1, 0));
        assertSpec("minecraft:bread", 3, 0,
                LostTalesQuestItemSpec.parse("bread*3", 1, 0));
        assertSpec("minecraft:wool", 2, 14,
                LostTalesQuestItemSpec.parse("minecraft:wool@14*2", 1, 0));
        assertSpec("minecraft:wool", 1, 14,
                LostTalesQuestItemSpec.parse("minecraft:wool@14", 1, 0));
        // Spaces around any part are not part of it.
        assertSpec("minecraft:wool", 2, 14,
                LostTalesQuestItemSpec.parse("  minecraft:wool @ 14 * 2 ", 1, 0));
        // The id is kept as written, for the registry to look up.
        assertSpec("lotr:item.silverCoin", 5, 0,
                LostTalesQuestItemSpec.parse("lotr:item.silverCoin*5", 1, 0));
    }

    @Test
    public void aBadNumberFallsBackToTheDefaultsAndNothingIsNone() {
        assertSpec("minecraft:bread", 4, 2,
                LostTalesQuestItemSpec.parse("minecraft:bread@many*few", 4, 2));
        assertSpec("minecraft:bread", 1, 0,
                LostTalesQuestItemSpec.parse("minecraft:bread@-3*-2", 1, 0));
        assertSpec("minecraft:bread", 1, 0,
                LostTalesQuestItemSpec.parse("minecraft:bread", -5, -5));
        assertTrue(LostTalesQuestItemSpec.parse("", 1, 0).isEmpty());
        assertTrue(LostTalesQuestItemSpec.parse(null, 1, 0).isEmpty());
        assertTrue(LostTalesQuestItemSpec.parse("*3", 1, 0).isEmpty());
        assertNull(LostTalesQuestItemSpec.parse("*3", 1, 0).item());
    }

    @Test
    public void theTextNamesWhatTheServerGrants() {
        List<Map<String, String>> maps = new ArrayList<Map<String, String>>();
        maps.add(rewards("item", "losttales_test:silver_coin",
                "count", "3", "meta", "2"));
        maps.add(rewards("item", "losttales_test:plain_thing*-1",
                "count", "many", "meta", "-3"));
        maps.add(rewards("item", "losttales_test:marked_thing@4*2",
                "count", "9"));
        maps.add(rewards("items", "losttales_test:no_such_thing*2; "
                + "losttales_test:other_thing@4 , bread_crumb*x,,"));
        maps.add(rewards("item", " losttales_test:first_thing ",
                "items", "losttales_test:second_thing*6"));
        maps.add(rewards("item", "  ", "items", " , ;"));
        maps.add(rewards("item", "*3", "count", "2"));

        for (Map<String, String> map : maps) {
            List<String> named = new ArrayList<String>();
            for (LostTalesQuestItemSpec granted
                    : LostTalesQuestRewardHelper.itemsGranted(map)) {
                String id = granted.getItemId();
                named.add((granted.getCount() > 1 ? granted.getCount() + "x " : "")
                        + LostTalesQuestRewardText.prettify(
                                id.substring(id.indexOf(':') + 1)));
            }
            assertEquals(map.toString(), named,
                    LostTalesQuestRewardText.phrases(map));
        }
    }

    @Test
    public void theServerGrantsTheItemCountAndMetaWritten() {
        assertGranted(rewards("item", "losttales_test:silver_coin",
                "count", "3", "meta", "2"),
                "losttales_test:silver_coin", 3, 2);
        // The item's own count and meta stand before the map's.
        assertGranted(rewards("item", "losttales_test:marked_thing@4*2",
                "count", "9", "meta", "1"),
                "losttales_test:marked_thing", 2, 4);
        assertGranted(rewards("item", "losttales_test:plain_thing*-1",
                "count", "many", "meta", "-3"),
                "losttales_test:plain_thing", 1, 0);
        assertEquals(Arrays.asList("minecraft:bread_crumb"),
                ids(LostTalesQuestRewardHelper.itemsGranted(
                        rewards("items", "bread_crumb*x,,"))));
        assertTrue(LostTalesQuestRewardHelper.itemsGranted(
                rewards("item", "*3", "items", " ; ")).isEmpty());
    }

    @Test
    public void theTextStillSaysTheWordsItDid() {
        assertEquals(Arrays.asList("3x Silver coin", "40 experience"),
                LostTalesQuestRewardText.phrases(rewards(
                        "item", "losttales_test:silver_coin", "count", "3",
                        "experience", "40")));
        // Without an item a count is not one of its parts.
        assertEquals(Arrays.asList("Count: 3"),
                LostTalesQuestRewardText.phrases(rewards("count", "3")));
    }

    private static void assertGranted(Map<String, String> rewards,
                                      String id, int count, int meta) {
        List<LostTalesQuestItemSpec> granted =
                LostTalesQuestRewardHelper.itemsGranted(rewards);
        assertEquals(1, granted.size());
        assertSpec(id, count, meta, granted.get(0));
    }

    private static void assertSpec(String id, int count, int meta,
                                   LostTalesQuestItemSpec spec) {
        assertEquals(id, spec.getItemId());
        assertEquals(id + " count", count, spec.getCount());
        assertEquals(id + " meta", meta, spec.getMeta());
    }

    private static List<String> ids(List<LostTalesQuestItemSpec> specs) {
        List<String> ids = new ArrayList<String>();
        for (LostTalesQuestItemSpec spec : specs) {
            ids.add(spec.getItemId());
        }
        return ids;
    }

    private static Map<String, String> rewards(String... keysAndValues) {
        Map<String, String> rewards = new LinkedHashMap<String, String>();
        for (int index = 0; index + 1 < keysAndValues.length; index += 2) {
            rewards.put(keysAndValues[index], keysAndValues[index + 1]);
        }
        return rewards;
    }
}
