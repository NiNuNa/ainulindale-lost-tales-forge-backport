package com.ninuna.losttales.client.gui.tooltip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiItemIcon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * A vessel's slots travel through a tooltip as a marked block of its words
 * (CONTENT-09), and stand in one framed button, parted by hairlines (LOOK-38).
 */
public final class LostTalesTooltipSlotsTest {
    private static final Item BREAD = new Item();
    private static final Item SWORD = new Item();
    private static final List<String> WORDS = Arrays.asList("§7Bread x12", "§7Iron Sword x1");

    @Before
    public void patched() {
        System.setProperty(LostTalesTooltipIcons.ACTIVE_PROPERTY, "true");
        LostTalesTooltipSlots.clear();
    }

    @After
    public void unpatched() {
        System.clearProperty(LostTalesTooltipIcons.ACTIVE_PROPERTY);
        LostTalesTooltipSlots.clear();
    }

    private static ItemStack[] urn(int breadCount) {
        return new ItemStack[] {new ItemStack(BREAD, breadCount), new ItemStack(SWORD, 1)};
    }

    @Test
    public void withoutTheTransformerTheWordsStayPlain() {
        System.clearProperty(LostTalesTooltipIcons.ACTIVE_PROPERTY);
        List<String> lines = LostTalesTooltipSlots.mark(urn(12), null, WORDS);

        assertEquals(WORDS, lines);
        assertFalse(LostTalesTooltipSlots.inBlock(lines.get(0)));
    }

    @Test
    public void everyLineOfTheBlockIsMarkedAndTheFirstCarriesTheSlots() {
        List<String> lines = LostTalesTooltipSlots.mark(urn(12), null, WORDS);

        assertEquals(2, lines.size());
        assertTrue(LostTalesTooltipSlots.inBlock(lines.get(0)));
        assertTrue(LostTalesTooltipSlots.inBlock(lines.get(1)));
        LostTalesTooltipSlots.Slots slots = LostTalesTooltipSlots.slotsOf(lines.get(0));
        assertNotNull(slots);
        assertEquals(2, slots.stacks.length);
        assertEquals(12, slots.stacks[0].stackSize);
        assertSame(SWORD, slots.stacks[1].getItem());
        assertNull(slots.more);
        assertNull(LostTalesTooltipSlots.slotsOf(lines.get(1)));
    }

    @Test
    public void anyOtherRendererReadsTheWords() {
        List<String> lines = LostTalesTooltipSlots.mark(urn(12), null, WORDS);

        assertEquals(WORDS.get(0), LostTalesTooltipSlots.words(lines.get(0)));
        assertEquals(WORDS.get(1), LostTalesTooltipSlots.words(lines.get(1)));
        // Vanilla greys every line after the name before drawing it.
        assertEquals("§7" + WORDS.get(0), LostTalesTooltipSlots.words("§7" + lines.get(0)));
        assertNotNull(LostTalesTooltipSlots.slotsOf("§7" + lines.get(0)));
    }

    @Test
    public void anEmptySlotStaysEmpty() {
        List<String> lines = LostTalesTooltipSlots.mark(
                new ItemStack[] {null, new ItemStack(SWORD, 1), null, null},
                null, Arrays.asList("§7Iron Sword x1"));
        LostTalesTooltipSlots.Slots slots = LostTalesTooltipSlots.slotsOf(lines.get(0));

        assertEquals(4, slots.stacks.length);
        assertNull(slots.stacks[0]);
        assertNull(slots.stacks[3]);
        assertEquals(4, slots.grid.slots);
    }

    @Test
    public void theSameTooltipBuiltAgainKeepsItsSlots() {
        String first = LostTalesTooltipSlots.mark(urn(12), null, WORDS).get(0);
        String again = LostTalesTooltipSlots.mark(urn(12), null, WORDS).get(0);

        assertEquals(first, again);
    }

    @Test
    public void anotherVesselIsKeptBesideTheFirst() {
        String first = LostTalesTooltipSlots.mark(urn(12), null, WORDS).get(0);
        String other = LostTalesTooltipSlots.mark(urn(3), null, WORDS).get(0);

        assertNotEquals(first, other);
        assertEquals(12, LostTalesTooltipSlots.slotsOf(first).stacks[0].stackSize);
        assertEquals(3, LostTalesTooltipSlots.slotsOf(other).stacks[0].stackSize);
    }

    @Test
    public void whatIsKeptIsACopy() {
        ItemStack[] stacks = urn(12);
        String line = LostTalesTooltipSlots.mark(stacks, null, WORDS).get(0);
        stacks[0].stackSize = 1;

        assertEquals(12, LostTalesTooltipSlots.slotsOf(line).stacks[0].stackSize);
    }

    @Test
    public void theLineUnderTheSlotsIsKeptWithThem() {
        String line = LostTalesTooltipSlots.mark(urn(12), "§7§oand 2 more...", WORDS).get(0);

        assertEquals("§7§oand 2 more...", LostTalesTooltipSlots.slotsOf(line).more);
    }

    @Test
    public void nothingToShowMarksNothing() {
        assertEquals(new ArrayList<String>(), LostTalesTooltipSlots.mark(urn(12), null,
                new ArrayList<String>()));
        assertEquals(WORDS, LostTalesTooltipSlots.mark(new ItemStack[0], null, WORDS));
        assertEquals(WORDS, LostTalesTooltipSlots.mark(null, null, WORDS));
    }

    @Test
    public void slotsLongForgottenAreNotFoundAgain() {
        String old = LostTalesTooltipSlots.mark(urn(1), null, WORDS).get(0);
        for (int count = 2; count <= LostTalesTooltipSlots.KEPT + 1; count++) {
            LostTalesTooltipSlots.mark(urn(count), null, WORDS);
        }

        assertNull(LostTalesTooltipSlots.slotsOf(old));
    }

    @Test
    public void theEndOfASessionForgetsEverySlot() {
        String line = LostTalesTooltipSlots.mark(urn(12), null, WORDS).get(0);
        LostTalesTooltipSlots.clear();

        assertNull(LostTalesTooltipSlots.slotsOf(line));
    }

    @Test
    public void aBlockWhoseSlotsAreKeptIsOneRowOfSlots() {
        List<String> tooltip = new ArrayList<String>();
        tooltip.add("Urn");
        tooltip.addAll(LostTalesTooltipSlots.mark(urn(12), "§7§oand 1 more...", WORDS));
        tooltip.add("Hold CTRL to view item details.");

        List<LostTalesTooltipHooks.Row> rows = LostTalesTooltipHooks.rows(tooltip);

        assertEquals(4, rows.size());
        assertEquals("Urn", rows.get(0).text);
        assertNotNull(rows.get(1).slots);
        assertEquals("§7§oand 1 more...", rows.get(2).text);
        assertEquals("Hold CTRL to view item details.", rows.get(3).text);
    }

    @Test
    public void aBlockWhoseSlotsAreGoneIsItsWords() {
        List<String> tooltip = new ArrayList<String>();
        tooltip.add("Urn");
        tooltip.addAll(LostTalesTooltipSlots.mark(urn(12), null, WORDS));
        LostTalesTooltipSlots.clear();

        List<LostTalesTooltipHooks.Row> rows = LostTalesTooltipHooks.rows(tooltip);

        assertEquals(3, rows.size());
        assertEquals(WORDS.get(0), rows.get(1).text);
        assertEquals(WORDS.get(1), rows.get(2).text);
        assertNull(rows.get(1).slots);
    }

    @Test
    public void aSlotIsAnItemWithAPixelClearRoundIt() {
        assertEquals(LostTalesUiItemIcon.ICON_TEXELS + 2, LostTalesTooltipSlots.SLOT_SIZE);
        assertEquals(18, LostTalesTooltipSlots.SLOT_SIZE);
    }

    @Test
    public void slotsArePartedByOneHairline() {
        assertEquals(WindowStyle.DIVIDER_WIDTH, LostTalesTooltipSlots.SLOT_GAP);
        assertEquals(1, LostTalesTooltipSlots.SLOT_GAP);
    }

    @Test
    public void oneFramedButtonStandsRoundTheWholeRow() {
        // The frame's ring of surface lies outside the row, so the ink
        // lines up with the words; inside the ink, the button's clear pixels.
        assertEquals(LostTalesUiFramedButton.INSET,
                LostTalesTooltipSlots.SLOTS_INSET + LostTalesTooltipSlots.OUTER_RING);
        assertEquals(1, LostTalesTooltipSlots.OUTER_RING);
        assertEquals(3, LostTalesTooltipSlots.SLOTS_INSET);

        LostTalesTooltipSlots.Slots urn = LostTalesTooltipSlots.slotsOf(
                LostTalesTooltipSlots.mark(urn(12), null, WORDS).get(0));
        assertEquals(3 + 18 + 1 + 18 + 3, urn.width);
        assertEquals(3 + 18 + 3, urn.height);
    }

    @Test
    public void theBiggestVesselStandsInOneRow() {
        LostTalesTooltipSlots.Slots loutrophoros = LostTalesTooltipSlots.slotsOf(
                LostTalesTooltipSlots.mark(new ItemStack[] {new ItemStack(BREAD, 1),
                        null, null, null}, null, Arrays.asList("§7Bread x1")).get(0));

        assertEquals(1, loutrophoros.grid.rows);
        assertEquals(3 + 4 * 18 + 3 * 1 + 3, loutrophoros.width);
        assertEquals(3 + 18 + 3, loutrophoros.height);
    }
}
