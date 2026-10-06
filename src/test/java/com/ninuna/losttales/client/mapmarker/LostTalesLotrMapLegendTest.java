package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.WindowStyle;
import org.junit.Test;

public final class LostTalesLotrMapLegendTest {
    private static final int ROW = MenuWindow.ROW_HEIGHT;
    private static final int FRAME = WindowStyle.POPUP_FRAME;
    /** The rows' room across inside the frame, as a font would measure it. */
    private static final int CONTENT = 120;
    private static final int KINDS = 6;

    @Test
    public void aTallMapShowsEveryKindWithoutScrolling() {
        LostTalesLotrMapLegend.Layout layout =
                LostTalesLotrMapLegend.calculateLayout(700, 480,
                        LostTalesLotrMapControlBar.HEIGHT, KINDS, CONTENT,
                        ROW);

        assertTrue(layout.visible);
        assertEquals("the heading and a row for each kind", KINDS + 1,
                layout.rowCount);
        assertEquals(2 * FRAME + 2 * MenuWindow.PADDING_Y
                + (KINDS + 1) * ROW, layout.panelHeight);
        assertEquals(CONTENT + 2 * FRAME, layout.panelWidth);
        assertEquals(0.0D, layout.maxScroll(), 0.0D);
        assertEquals(480 - LostTalesLotrMapControlBar.HEIGHT
                        - LostTalesLotrMapLegend.GAP_ABOVE_CONTROL_BAR
                        - layout.panelHeight,
                layout.panelY);
        assertTrue(layout.panelX >= 0);
        assertTrue(layout.panelX + layout.panelWidth <= 700);
    }

    @Test
    public void aShortMapScrollsTheRowsItHasNoRoomFor() {
        LostTalesLotrMapLegend.Layout layout =
                LostTalesLotrMapLegend.calculateLayout(320, 120,
                        LostTalesLotrMapControlBar.HEIGHT, KINDS, CONTENT,
                        ROW);

        assertTrue(layout.visible);
        assertEquals("one row has no room", ROW, layout.maxScroll(), 0.0D);
        assertEquals(KINDS * ROW, layout.rowsBottom() - layout.rowsTop());
        assertTrue(layout.panelY >= 0);
    }

    @Test
    public void aNarrowMapKeepsTheLegendOnIt() {
        LostTalesLotrMapLegend.Layout layout =
                LostTalesLotrMapLegend.calculateLayout(100, 480, 0, KINDS,
                        200, ROW);

        assertTrue(layout.visible);
        assertTrue(layout.panelX >= 0);
        assertTrue(layout.panelX + layout.panelWidth <= 100);
    }

    /** A press finds the kind whose row is drawn there, the heading none. */
    @Test
    public void rowsAnswerWhereTheyAreDrawn() {
        LostTalesLotrMapLegend.Layout layout =
                LostTalesLotrMapLegend.calculateLayout(700, 480,
                        LostTalesLotrMapControlBar.HEIGHT, KINDS, CONTENT,
                        ROW);
        int x = layout.boxLeft() + 1;
        int top = layout.rowsTop();

        assertEquals(-1, LostTalesLotrMapLegend.categoryAt(
                layout, 0.0D, x, top + 1, KINDS));
        assertEquals(0, LostTalesLotrMapLegend.categoryAt(
                layout, 0.0D, x, top + ROW, KINDS));
        assertEquals(KINDS - 1, LostTalesLotrMapLegend.categoryAt(
                layout, 0.0D, x, top + KINDS * ROW, KINDS));
        assertEquals(-1, LostTalesLotrMapLegend.categoryAt(
                layout, 0.0D, x, layout.rowsBottom(), KINDS));
        assertEquals("the frame is no row", -1,
                LostTalesLotrMapLegend.categoryAt(layout, 0.0D,
                        layout.boxLeft() - 1, top + ROW, KINDS));
    }

    @Test
    public void scrolledRowsAnswerWhereTheyAreDrawn() {
        LostTalesLotrMapLegend.Layout layout =
                LostTalesLotrMapLegend.calculateLayout(320, 120,
                        LostTalesLotrMapControlBar.HEIGHT, KINDS, CONTENT,
                        ROW);

        assertEquals("the first kind stands at the top once the heading "
                        + "has scrolled away", 0,
                LostTalesLotrMapLegend.categoryAt(layout, ROW,
                        layout.boxLeft() + 1, layout.rowsTop(), KINDS));
    }

    @Test
    public void inAWindowTheLegendStandsAtTheMapsOwnFoot() {
        LostTalesLotrMapLegend.Layout layout =
                LostTalesLotrMapLegend.calculateLayout(700, 480, 0, KINDS,
                        CONTENT, ROW);

        assertTrue(layout.visible);
        assertEquals(480 - LostTalesLotrMapLegend.GAP_ABOVE_CONTROL_BAR
                        - layout.panelHeight,
                layout.panelY);
    }

    @Test
    public void layoutHidesWhenTheScaledScreenIsTooShort() {
        LostTalesLotrMapLegend.Layout layout =
                LostTalesLotrMapLegend.calculateLayout(320, 70,
                        LostTalesLotrMapControlBar.HEIGHT, KINDS, CONTENT,
                        ROW);

        assertFalse(layout.visible);
    }
}
