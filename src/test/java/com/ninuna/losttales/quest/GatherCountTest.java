package com.ninuna.losttales.quest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * How a gather objective counts: what is held, the main inventory and
 * the cursor together, taken when an item really arrives, never past the
 * target, and never going down unless it hands its items over; and how a
 * hand-over takes them, all or nothing.
 */
public final class GatherCountTest {
    private final Item wheat = new Item();
    private final Item stone = new Item();

    private final GatherCount.Check isWheat = new GatherCount.Check() {
        @Override
        public boolean matches(ItemStack stack) {
            return stack.getItem() == GatherCountTest.this.wheat;
        }
    };

    @Test
    public void whatIsHeldCountsTheCursorToo() {
        ItemStack[] inventory = new ItemStack[36];
        inventory[0] = new ItemStack(this.wheat, 5);
        inventory[4] = new ItemStack(this.stone, 64);
        inventory[9] = new ItemStack(this.wheat, 3);
        assertEquals(8, GatherCount.held(inventory, null, this.isWheat));
        assertEquals("a craft lands on the cursor first", 12,
                GatherCount.held(inventory, new ItemStack(this.wheat, 4),
                        this.isWheat));
        assertEquals(0, GatherCount.held(null, null, this.isWheat));
    }

    @Test
    public void anEmptiedStackCountsNothing() {
        ItemStack[] inventory = new ItemStack[] {new ItemStack(this.wheat, 0)};
        assertEquals(0, GatherCount.held(inventory, null, this.isWheat));
    }

    /**
     * An item lying on the ground is not held, however often the player
     * touches it: only what reached the inventory counts, so standing on
     * a stack that does not fit adds nothing.
     */
    @Test
    public void onlyWhatArrivedCounts() {
        ItemStack[] full = new ItemStack[] {new ItemStack(this.stone, 64)};
        int recorded = 0;
        for (int touch = 0; touch < 20; touch++) {
            recorded = GatherCount.raised(recorded,
                    GatherCount.held(full, null, this.isWheat), 10);
        }
        assertEquals(0, recorded);
    }

    @Test
    public void aCountNeverGoesDownNorPastItsTarget() {
        assertEquals(6, GatherCount.raised(6, 2, 10));
        assertEquals(9, GatherCount.raised(6, 9, 10));
        assertEquals(10, GatherCount.raised(6, 30, 10));
        assertEquals(6, GatherCount.counted(false, 6, 2, 10));
    }

    /** A gather that hands its items over counts what is held now, so it never pays for what is gone. */
    @Test
    public void aTakingCountFollowsWhatIsHeld() {
        assertEquals(2, GatherCount.counted(true, 6, 2, 10));
        assertEquals(9, GatherCount.counted(true, 6, 9, 10));
        assertEquals(10, GatherCount.counted(true, 0, 30, 10));
    }

    @Test
    public void onlyARequiredGatherThatSaysSoTakes() {
        assertTrue(GatherCount.takes(objective("gather", false, "true")));
        assertFalse(GatherCount.takes(objective("gather", false, "false")));
        assertFalse(GatherCount.takes(objective("gather", false, null)));
        assertFalse(GatherCount.takes(objective("gather", false, "TRUE")));
        assertFalse(GatherCount.takes(objective("gather", true, "true")));
        assertFalse(GatherCount.takes(objective("craft", false, "true")));
    }

    @Test
    public void aHandOverTakesTheMainInventoryFirstThenTheCursor() {
        ItemStack[] inventory = new ItemStack[36];
        inventory[0] = new ItemStack(this.wheat, 5);
        inventory[4] = new ItemStack(this.stone, 64);
        inventory[9] = new ItemStack(this.wheat, 3);
        ItemStack cursor = new ItemStack(this.wheat, 4);
        GatherCount.Taking taking = GatherCount.take(inventory, cursor,
                new GatherCount.Check[] {this.isWheat}, new int[] {10});
        assertTrue(taking.whole);
        assertEquals(10, taking.got[0]);
        assertNull(taking.inventory[0]);
        assertNull(taking.inventory[9]);
        assertEquals(64, taking.inventory[4].stackSize);
        assertEquals(2, taking.cursor.stackSize);
        assertEquals("the player's own stacks are untouched", 5, inventory[0].stackSize);
        assertEquals(4, cursor.stackSize);
    }

    @Test
    public void aHandOverShortOfItemsIsNotWhole() {
        ItemStack[] inventory = new ItemStack[] {new ItemStack(this.wheat, 5)};
        GatherCount.Taking taking = GatherCount.take(inventory, null,
                new GatherCount.Check[] {this.isWheat}, new int[] {8});
        assertFalse(taking.whole);
        assertEquals(5, taking.got[0]);
        assertEquals(5, inventory[0].stackSize);
    }

    /** Two objectives over the same items each take their own share, the first first. */
    @Test
    public void eachObjectiveTakesItsOwnShare() {
        ItemStack[] inventory = new ItemStack[] {new ItemStack(this.wheat, 8)};
        GatherCount.Taking taking = GatherCount.take(inventory, null,
                new GatherCount.Check[] {this.isWheat, this.isWheat},
                new int[] {5, 5});
        assertFalse(taking.whole);
        assertEquals(5, taking.got[0]);
        assertEquals(3, taking.got[1]);
    }

    private static LostTalesQuestObjectiveDefinition objective(String type,
            boolean optional, String take) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("item", "minecraft:wheat");
        if (take != null) {
            params.put(LostTalesQuestParams.TAKE, take);
        }
        return new LostTalesQuestObjectiveDefinition("wheat", type, "",
                optional, Collections.unmodifiableMap(params));
    }
}
