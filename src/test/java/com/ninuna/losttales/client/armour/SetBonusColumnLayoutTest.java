package com.ninuna.losttales.client.armour;

import com.ninuna.losttales.client.window.WindowStyle;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Where the set bonuses stand in the effect column (CONTENT-12): in line
 * with vanilla's potion boxes and under them, one popup each, narrower
 * where the inventory leaves less room, folded to their names where the
 * screen is short, and never past its foot.
 */
public final class SetBonusColumnLayoutTest {
    /** The survival inventory, moved over for the column, at 1024×768 and GUI scale 3. */
    private static final int GUI_LEFT = 143;
    private static final int GUI_TOP = 45;
    private static final int SCREEN_HEIGHT = 256;

    @Test
    public void anEntryStandsWherePotionBoxesStand() {
        List<SetBonusColumnLayout.Box> boxes = SetBonusColumnLayout.place(GUI_LEFT, GUI_TOP,
                SCREEN_HEIGHT, 0, new int[] { 1 });
        assertEquals(1, boxes.size());
        SetBonusColumnLayout.Box box = boxes.get(0);
        assertEquals("from where vanilla starts its boxes", GUI_LEFT - 124, box.left);
        assertEquals("as wide as a potion box", 120, box.width);
        assertEquals("four clear of the inventory", GUI_LEFT - 4, box.right());
        assertEquals("from the inventory's top", GUI_TOP, box.top);
        assertEquals("a popup of two lines", WindowStyle.POPUP_LINE_HEIGHT
                + WindowStyle.LINE_HEIGHT, box.height);
    }

    @Test
    public void theIconAndTheWordsStandInsideTheFrame() {
        SetBonusColumnLayout.Box box = SetBonusColumnLayout.place(GUI_LEFT, GUI_TOP,
                SCREEN_HEIGHT, 0, new int[] { 1 }).get(0);
        assertEquals(box.left + WindowStyle.POPUP_INSET, box.iconLeft());
        assertEquals("the icon centred down the two lines", box.top + 6, box.iconTop());
        assertEquals(box.iconLeft() + SetBonusColumnLayout.ICON + WindowStyle.BUTTON_GAP,
                box.textLeft());
        assertEquals("the name's capitals the inset down", box.top + WindowStyle.POPUP_INSET,
                box.textTop());
        assertEquals(box.right() - WindowStyle.POPUP_INSET, box.textLeft() + box.textRoom());
        assertTrue("the last line's shadow inside the inset",
                box.textTop() + WindowStyle.LINE_HEIGHT + 8
                        <= box.bottom() - WindowStyle.POPUP_INSET);
    }

    @Test
    public void entriesStandUnderThePotionBoxes() {
        assertEquals(GUI_TOP, SetBonusColumnLayout.firstTop(GUI_TOP, 0));
        assertEquals("one box", GUI_TOP + 32 + WindowStyle.BUTTON_GAP,
                SetBonusColumnLayout.firstTop(GUI_TOP, 1));
        assertEquals("five boxes a step apart", GUI_TOP + 4 * 33 + 32 + WindowStyle.BUTTON_GAP,
                SetBonusColumnLayout.firstTop(GUI_TOP, 5));
        assertEquals("seven squeezed into vanilla's room", GUI_TOP + 6 * (132 / 6) + 32
                + WindowStyle.BUTTON_GAP, SetBonusColumnLayout.firstTop(GUI_TOP, 7));
        SetBonusColumnLayout.Box box = SetBonusColumnLayout.place(GUI_LEFT, GUI_TOP,
                SCREEN_HEIGHT, 2, new int[] { 1 }).get(0);
        assertTrue("clear of the second box", box.top > GUI_TOP + 33 + 32);
    }

    @Test
    public void entriesStackAButtonGapApart() {
        List<SetBonusColumnLayout.Box> boxes = SetBonusColumnLayout.place(GUI_LEFT, GUI_TOP,
                SCREEN_HEIGHT, 0, new int[] { 1, 3, 2 });
        assertEquals(3, boxes.size());
        assertEquals(28, boxes.get(0).height);
        assertEquals(52, boxes.get(1).height);
        assertEquals(40, boxes.get(2).height);
        for (int index = 1; index < boxes.size(); index++) {
            assertEquals(boxes.get(index - 1).bottom() + WindowStyle.BUTTON_GAP,
                    boxes.get(index).top);
            assertEquals(boxes.get(0).left, boxes.get(index).left);
        }
        assertEquals(3, boxes.get(1).effectLines);
    }

    @Test
    public void lessRoomBesideTheInventoryMakesItNarrower() {
        // Not moved over: the inventory centred on a screen 341 wide.
        List<SetBonusColumnLayout.Box> boxes = SetBonusColumnLayout.place(82, GUI_TOP,
                SCREEN_HEIGHT, 0, new int[] { 1 });
        SetBonusColumnLayout.Box box = boxes.get(0);
        assertEquals("four clear of the screen's edge", 4, box.left);
        assertEquals(78, box.right());
        assertEquals(74, box.width);
        assertTrue(box.textRoom() >= SetBonusColumnLayout.MIN_WORDS);
    }

    @Test
    public void tooLittleRoomShowsNothing() {
        int narrowest = SetBonusColumnLayout.MIN_WIDTH + 8;
        assertEquals(1, SetBonusColumnLayout.place(narrowest, GUI_TOP, SCREEN_HEIGHT, 0,
                new int[] { 1 }).size());
        assertTrue(SetBonusColumnLayout.place(narrowest - 1, GUI_TOP, SCREEN_HEIGHT, 0,
                new int[] { 1 }).isEmpty());
        assertTrue(SetBonusColumnLayout.place(GUI_LEFT, GUI_TOP, SCREEN_HEIGHT, 0,
                new int[0]).isEmpty());
        assertTrue(SetBonusColumnLayout.place(GUI_LEFT, GUI_TOP, SCREEN_HEIGHT, 0,
                null).isEmpty());
    }

    @Test
    public void aShortScreenFoldsEveryEntryToItsName() {
        List<SetBonusColumnLayout.Box> boxes = SetBonusColumnLayout.place(GUI_LEFT, GUI_TOP,
                120, 0, new int[] { 1, 1, 1 });
        assertEquals("the third would reach past the foot", 2, boxes.size());
        for (SetBonusColumnLayout.Box box : boxes) {
            assertEquals(0, box.effectLines);
            assertEquals("the icon and the inset", 24, box.height);
            assertEquals("the name centred on the icon", box.top + 8, box.textTop());
            assertEquals(box.top + WindowStyle.POPUP_INSET, box.iconTop());
            assertTrue(box.bottom() <= 120 - 4);
        }
    }

    @Test
    public void entriesThatFitAreNotFolded() {
        List<SetBonusColumnLayout.Box> boxes = SetBonusColumnLayout.place(GUI_LEFT, GUI_TOP,
                GUI_TOP + 28 + 5 + 28 + 4, 0, new int[] { 1, 1 });
        assertEquals(2, boxes.size());
        assertEquals(1, boxes.get(1).effectLines);
        boxes = SetBonusColumnLayout.place(GUI_LEFT, GUI_TOP, GUI_TOP + 28 + 5 + 28 + 3, 0,
                new int[] { 1, 1 });
        assertEquals("a pixel short: both folded", 0, boxes.get(0).effectLines);
        assertEquals(0, boxes.get(1).effectLines);
    }

    @Test
    public void everyPlaceIsAWholePixel() {
        // The column is laid out in integers alone, so nothing it draws lands between pixels.
        assertEquals(24, SetBonusColumnLayout.height(0));
        assertEquals(28, SetBonusColumnLayout.height(1));
        assertEquals(52, SetBonusColumnLayout.height(3));
    }
}
