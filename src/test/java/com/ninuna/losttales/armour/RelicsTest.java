package com.ninuna.losttales.armour;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The relic mark and what a relic reward hands out (CONTENT-11): the mark
 * rides in the item's data, a reward written {@code set} gives all four
 * pieces and {@code set/slot} one, each the first item its slot takes
 * that the game has, and only a relic set is handed out so.
 */
public final class RelicsTest {
    private static final Relics.Game EVERY_ITEM = new Relics.Game() {
        @Override
        public boolean hasItem(String itemId) {
            return true;
        }
    };

    @Test
    public void theMarkRidesInTheItemsData() {
        ItemStack stack = new ItemStack(new Item(), 1);
        assertEquals("", Relics.relicOf(stack));
        Relics.mark(stack, "losttales:striders_garb");
        assertEquals("losttales:striders_garb", Relics.relicOf(stack));
        ItemStack copy = stack.copy();
        assertEquals("a copy keeps the mark", "losttales:striders_garb", Relics.relicOf(copy));
        ItemStack unmarked = new ItemStack(new Item(), 1);
        Relics.mark(unmarked, "Not An Id");
        assertEquals("a mark that is no id is not made", "", Relics.relicOf(unmarked));
        assertEquals("", Relics.relicOf(null));
    }

    @Test
    public void aRewardIsWrittenAsASetOrASetAndASlot() {
        assertTrue(Relics.isWritten("losttales:striders_garb"));
        assertTrue(Relics.isWritten("losttales:striders_garb/helmet"));
        assertFalse(Relics.isWritten("losttales:striders_garb/cloak"));
        assertFalse(Relics.isWritten("striders garb"));
        assertFalse(Relics.isWritten(""));
    }

    @Test
    public void aSetGivesFourPiecesAndASlotOne() {
        ArmourCatalogue catalogue = ArmourRulesTest.catalogue();
        List<Relics.Piece> all = Relics.pieces(catalogue, "test:isildurs", EVERY_ITEM);
        assertEquals(4, all.size());
        assertEquals("test:heavy_helmet", all.get(0).getItemId());
        assertEquals(ArmourSlot.HELMET, all.get(0).getSlot());
        List<Relics.Piece> one = Relics.pieces(catalogue, "test:isildurs/boots", EVERY_ITEM);
        assertEquals(1, one.size());
        assertEquals("test:heavy_boots", one.get(0).getItemId());
        assertTrue("an ordinary set is no relic",
                Relics.pieces(catalogue, "test:gondorian", EVERY_ITEM).isEmpty());
        assertTrue(Relics.pieces(catalogue, "test:unknown", EVERY_ITEM).isEmpty());
        assertTrue(Relics.pieces(catalogue, "test:isildurs/cloak", EVERY_ITEM).isEmpty());
    }

    @Test
    public void aSlotGivesTheFirstItemTheGameHas() {
        final Set<String> has = new HashSet<String>(Arrays.asList("lotr:item.helmetRanger"));
        Relics.Game game = new Relics.Game() {
            @Override
            public boolean hasItem(String itemId) {
                return has.contains(itemId);
            }
        };
        ArmourCatalogue catalogue = BundledArmourFilesTest.catalogue();
        List<Relics.Piece> pieces = Relics.pieces(catalogue, "losttales:striders_garb", game);
        assertEquals("the pieces the game lacks are not handed out", 1, pieces.size());
        assertEquals("lotr:item.helmetRanger", pieces.get(0).getItemId());
    }

    @Test
    public void aRelicSetTakesTheItemsItsSlotsName() {
        ArmourCatalogue catalogue = ArmourRulesTest.catalogue();
        assertTrue(Relics.takes(catalogue, "test:isildurs", "test:heavy_legs"));
        assertFalse(Relics.takes(catalogue, "test:isildurs", "test:light_legs"));
        assertFalse(Relics.takes(catalogue, "test:gondorian", "test:heavy_legs"));
        assertFalse(Relics.takes(catalogue, "test:unknown", "test:heavy_legs"));
    }
}
