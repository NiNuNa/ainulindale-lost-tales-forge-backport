package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.input.LostTalesKeyPress;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A menu's window first opens where the menu always hung: toward the
 * middle of the window its control belongs to, turning round only where
 * the other side holds more of it, and never more than twelve rows long.
 * Its rows answer the pointer where they are drawn, and a header or a
 * row that cannot be taken is nobody's press.
 */
public final class MenuWindowTest {
    private static final int SCREEN_WIDTH = 480;
    private static final int SCREEN_HEIGHT = 270;
    private static final int STRIP = SubWindow.STRIP_HEIGHT;
    /** The room a menu opens in: here the whole screen. */
    private static final LostTalesUiHitBox SCREEN =
            new LostTalesUiHitBox(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
    /** From the control to the window's box: the gap and the frame. */
    private static final int REACH = SubWindowAnchor.REACH;
    /** An owner that takes nothing. */
    private static final MenuWindow.Owner NOBODY = new MenuWindow.Owner() {
        @Override
        public void take(MenuWindow menu, MenuWindow.Entry entry,
                         boolean back) {}

        @Override
        public void keyTyped(MenuWindow menu, LostTalesKeyPress press) {}
    };

    @Test
    public void aMenuHangsBelowAControlInTheUpperHalfFromItsLeftEdge() {
        MenuWindow menu = menuOf(4);
        SubWindowAnchor anchor = SubWindowAnchor.inward(100, 20, 108, 30,
                0.0D, 0.0D, SCREEN_WIDTH, SCREEN_HEIGHT, null);
        LostTalesUiHitBox box = menu.firstContentBox(anchor, 80, SCREEN);
        assertEquals(100.0D, box.left, 1.0E-9D);
        assertEquals(30 + REACH + STRIP, box.top, 1.0E-9D);
        assertEquals(80.0D, box.width, 1.0E-9D);
        assertEquals(menu.naturalHeight(80), box.height, 1.0E-9D);
    }

    @Test
    public void aMenuHangsAboveAControlInTheLowerHalfFromItsRightEdge() {
        MenuWindow menu = menuOf(4);
        SubWindowAnchor anchor = SubWindowAnchor.inward(400, 240, 408, 250,
                0.0D, 0.0D, SCREEN_WIDTH, SCREEN_HEIGHT, null);
        LostTalesUiHitBox box = menu.firstContentBox(anchor, 80, SCREEN);
        assertEquals(408 - 80, box.left, 1.0E-9D);
        assertEquals("its frame ends the gap above the control", 240 - REACH,
                box.top + box.height, 1.0E-9D);
    }

    @Test
    public void aMenuTurnsRoundOnlyWhereTheOtherSideHoldsMoreOfIt() {
        MenuWindow menu = menuOf(8);
        // In the upper half of its window, but near the screen's foot: the
        // room below holds no row, the room above all of them.
        SubWindowAnchor anchor = SubWindowAnchor.inward(100, 230, 108, 240,
                0.0D, 200.0D, SCREEN_WIDTH, 400.0D, null);
        LostTalesUiHitBox box = menu.firstContentBox(anchor, 80, SCREEN);
        assertEquals(230 - REACH, box.top + box.height, 1.0E-9D);
        assertEquals(menu.naturalHeight(80), box.height, 1.0E-9D);
    }

    @Test
    public void aLongMenuOpensTwelveRowsLong() {
        MenuWindow menu = menuOf(30);
        SubWindowAnchor anchor = SubWindowAnchor.inward(100, 20, 108, 30,
                0.0D, 0.0D, SCREEN_WIDTH, SCREEN_HEIGHT, null);
        LostTalesUiHitBox box = menu.firstContentBox(anchor, 80, SCREEN);
        assertEquals(menuOf(MenuWindow.MAX_VISIBLE_ROWS).naturalHeight(80),
                box.height, 1.0E-9D);
    }

    @Test
    public void aRowAnswersWhereItIsDrawnAndOnlyARowThatActsIsPressed() {
        MenuWindow menu = new MenuWindow(SubWindowKind.TAB, NOBODY);
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        rows.add(MenuWindow.Entry.header("Channels"));
        MenuWindow.Entry open = new MenuWindow.Entry("a", "Open");
        rows.add(open);
        rows.add(new MenuWindow.Entry("b", "Closed").unavailable("Not here"));
        menu.setRows(rows);
        LostTalesUiHitBox box = new LostTalesUiHitBox(10, 20, 80,
                3 * MenuWindow.ROW_HEIGHT + 6);
        int rowsTop = 20 + 3;
        WindowHover header = menu.hoverAt(box, 20, rowsTop + 5);
        assertEquals(WindowHover.Kind.SUB_WINDOW, header.kind);
        assertNull(header.menuEntry);
        assertFalse(header.acts());
        WindowHover row = menu.hoverAt(box, 20,
                rowsTop + MenuWindow.ROW_HEIGHT + 5);
        assertSame(open, row.menuEntry);
        assertTrue(row.acts());
        WindowHover closed = menu.hoverAt(box, 20,
                rowsTop + 2 * MenuWindow.ROW_HEIGHT + 5);
        assertNull(closed.menuEntry);
        assertEquals("it says why", "Not here", closed.tip);
        assertNull("the padding is nobody's",
                menu.hoverAt(box, 20, 21).menuEntry);
    }

    /** A row a page found keeps everything it shows under the id the quick switcher gives it. */
    @Test
    public void aRenamedRowKeepsItsLook() {
        MenuWindow.Entry found = new MenuWindow.Entry("q1", "The Lost Ring")
                .withValue("Active").withLabelColor(7).unavailable("Not now");
        MenuWindow.Entry renamed = found.renamed("find:journal:q1");
        assertEquals("find:journal:q1", renamed.id);
        assertEquals("The Lost Ring", renamed.label);
        assertEquals("Active", renamed.value);
        assertEquals(7, renamed.labelColor);
        assertEquals("Not now", renamed.unavailable);
        assertFalse(renamed.isTakeable());
    }

    private static MenuWindow menuOf(int count) {
        MenuWindow menu = new MenuWindow(SubWindowKind.TAB, NOBODY);
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        for (int index = 0; index < count; index++) {
            rows.add(new MenuWindow.Entry("row" + index, "Row " + index));
        }
        menu.setRows(rows);
        return menu;
    }
}
