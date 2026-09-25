package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.SubWindow;
import com.ninuna.losttales.client.window.SubWindowAnchor;
import com.ninuna.losttales.client.window.SubWindowKind;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * A menu's window first opens where the menu always hung: toward the
 * middle of the window its control belongs to, turning round only where
 * the other side holds more of it, and never more than twelve rows long.
 * Its rows answer the pointer where they are drawn, and a header or a
 * row that cannot be taken is nobody's press.
 */
public final class ChatMenuTest {
    private static final int SCREEN_WIDTH = 480;
    private static final int SCREEN_HEIGHT = 270;
    private static final int STRIP = SubWindow.STRIP_HEIGHT;
    /** The room a menu opens in: here the whole screen. */
    private static final LostTalesUiHitBox SCREEN =
            new LostTalesUiHitBox(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
    /** From the control to the window's box: the gap and the frame. */
    private static final int REACH = SubWindowAnchor.REACH;

    @Test
    public void aMenuHangsBelowAControlInTheUpperHalfFromItsLeftEdge() {
        ChatMenu menu = menuOf(4);
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
        ChatMenu menu = menuOf(4);
        SubWindowAnchor anchor = SubWindowAnchor.inward(400, 240, 408, 250,
                0.0D, 0.0D, SCREEN_WIDTH, SCREEN_HEIGHT, null);
        LostTalesUiHitBox box = menu.firstContentBox(anchor, 80, SCREEN);
        assertEquals(408 - 80, box.left, 1.0E-9D);
        assertEquals("its frame ends the gap above the control", 240 - REACH,
                box.top + box.height, 1.0E-9D);
    }

    @Test
    public void aMenuTurnsRoundOnlyWhereTheOtherSideHoldsMoreOfIt() {
        ChatMenu menu = menuOf(8);
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
        ChatMenu menu = menuOf(30);
        SubWindowAnchor anchor = SubWindowAnchor.inward(100, 20, 108, 30,
                0.0D, 0.0D, SCREEN_WIDTH, SCREEN_HEIGHT, null);
        LostTalesUiHitBox box = menu.firstContentBox(anchor, 80, SCREEN);
        assertEquals(menuOf(ChatMenu.MAX_VISIBLE_ROWS).naturalHeight(80),
                box.height, 1.0E-9D);
    }

    @Test
    public void aRowAnswersWhereItIsDrawnAndOnlyARowThatActsIsPressed() {
        ChatMenu menu = new ChatMenu(SubWindowKind.TAB);
        List<ChatMenu.Entry> rows = new ArrayList<ChatMenu.Entry>();
        rows.add(ChatMenu.Entry.header("Channels"));
        ChatMenu.Entry open = new ChatMenu.Entry("a", "Open");
        rows.add(open);
        rows.add(new ChatMenu.Entry("b", "Closed").unavailable("Not here"));
        menu.setRows(rows);
        LostTalesUiHitBox box = new LostTalesUiHitBox(10, 20, 80,
                3 * ChatMenu.ROW_HEIGHT + 6);
        int rowsTop = 20 + 3;
        ChatHover header = menu.hoverAt(box, 20, rowsTop + 5);
        assertEquals(ChatHover.Kind.MENU, header.chatKind);
        assertNull(header.menuEntry);
        ChatHover row = menu.hoverAt(box, 20, rowsTop + ChatMenu.ROW_HEIGHT + 5);
        assertEquals(ChatHover.Kind.MENU_ENTRY, row.chatKind);
        assertSame(open, row.menuEntry);
        ChatHover closed = menu.hoverAt(box, 20,
                rowsTop + 2 * ChatMenu.ROW_HEIGHT + 5);
        assertEquals(ChatHover.Kind.MENU, closed.chatKind);
        assertEquals("it says why", "Not here", closed.menuTip);
        assertEquals("the padding is nobody's", ChatHover.Kind.MENU,
                menu.hoverAt(box, 20, 21).chatKind);
    }

    private static ChatMenu menuOf(int count) {
        ChatMenu menu = new ChatMenu(SubWindowKind.TAB);
        List<ChatMenu.Entry> rows = new ArrayList<ChatMenu.Entry>();
        for (int index = 0; index < count; index++) {
            rows.add(new ChatMenu.Entry("row" + index, "Row " + index));
        }
        menu.setRows(rows);
        return menu;
    }
}
