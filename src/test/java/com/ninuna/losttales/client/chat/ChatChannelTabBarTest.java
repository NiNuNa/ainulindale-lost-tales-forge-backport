package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The row's way of fitting too many tabs: every tab is one width, the
 * default while the row holds them all at it and a narrower one they
 * all share once it does not; every tab gives its buttons up together
 * at fixed shares of the full width, the tab in front keeping its cross
 * and cutting its icon and name before it; each tab travels on a glide
 * of its own, tabs set going together meeting all along it; and a tab's
 * buttons ride its edge at every GUI scale.
 */
public final class ChatChannelTabBarTest {

    private static final double EPSILON = 1.0E-6D;
    private static final int CONTROL =
            ChatChannelTabBar.CONTROL_GAP + ChatChannelTabBar.CONTROL_SIZE;

    /**
     * Every control of the strip answers on the box it is drawn in and
     * nowhere else: the search button on its frame, an end control on
     * the nine-pixel square round its glyph, a tab's cross on its
     * square in the tab. The rows of the band above and below a box
     * answer nothing.
     */
    @Test
    public void everyControlAnswersOnItsOwnBox() {
        int rowBottom = 100;
        int rowTop = ChatChannelTabBar.rowTop(rowBottom);
        LostTalesUiHitBox search = ChatChannelTabBar.searchBox(40, rowBottom);
        assertEquals(search.width, search.height, EPSILON);
        // The strip begins two pixels left of the row, on the window's
        // own edge, its one-pixel frame standing just outside it; the
        // button stands three clear pixels inside the frame, and the
        // first tab three past the button.
        int stripInset = 2;
        assertEquals(40 - stripInset + 3, search.left, EPSILON);
        assertEquals(search.right() + 3,
                40 + ChatChannelTabBar.tabRunLeftInset() - stripInset, EPSILON);
        assertEquals(ChatChannelTabBar.centredInStrip(rowBottom,
                (int)search.height), search.top, EPSILON);
        assertTrue(search.top > rowTop);
        assertTrue(search.bottom() < rowBottom);
        double middleX = search.left + search.width / 2.0D;
        assertTrue(search.contains(middleX, search.top));
        assertTrue(search.contains(middleX, search.bottom() - 1));
        assertFalse(search.contains(middleX, search.top - 1));
        assertFalse(search.contains(middleX, search.bottom()));
        assertFalse(search.contains(middleX, rowBottom - 1));
        assertFalse(search.contains(search.left - 1, search.top));

        LostTalesUiHitBox glyph = ChatChannelTabBar.endControlBox(70, 5, 5, rowBottom);
        assertEquals(ChatChannelTabBar.END_CONTROL_SIZE, glyph.width, EPSILON);
        assertEquals(ChatChannelTabBar.END_CONTROL_SIZE, glyph.height, EPSILON);
        assertEquals(68, glyph.left, EPSILON);
        assertEquals(ChatChannelTabBar.centredInStrip(rowBottom,
                ChatChannelTabBar.END_CONTROL_SIZE), glyph.top, EPSILON);
        assertTrue(glyph.contains(72, glyph.top + 4));
        assertFalse(glyph.contains(72, rowBottom - 1));
        assertFalse(glyph.contains(72, rowTop));
        // The ink itself is what the control is drawn with.
        LostTalesUiHitBox ink = ChatChannelTabBar.endControlInk(70, 5, 5, rowBottom);
        assertEquals(70, ink.left, EPSILON);
        assertEquals(glyph.top + 2, ink.top, EPSILON);

        int liftedTop = ChatChannelTabBar.tabTop(rowBottom, true);
        assertEquals(ChatChannelTabBar.tabTop(rowBottom, false)
                - ChatChannelTabBar.LIFT, liftedTop);
        LostTalesUiHitBox control = ChatChannelTabBar.tabControlBox(50, liftedTop);
        assertEquals(ChatChannelTabBar.CONTROL_SIZE, control.width, EPSILON);
        assertEquals(ChatChannelTabBar.CONTROL_SIZE, control.height, EPSILON);
        assertTrue(control.top >= liftedTop);
        assertTrue(control.bottom() <= liftedTop + ChatChannelTabBar.HEIGHT);
        // A resting tab's control stands the lift lower than the selected tab's.
        assertEquals(control.top + ChatChannelTabBar.LIFT,
                ChatChannelTabBar.tabControlBox(50,
                        ChatChannelTabBar.tabTop(rowBottom, false)).top,
                EPSILON);
    }

    /**
     * The strip's band, which moves the window, is the row and the tool
     * strip under its rule together, down to the window's top rule; the
     * tabs and controls answer in the row alone.
     */
    @Test
    public void theToolStripIsPartOfTheStripsBand() {
        ChatChannelTabBar.Row row = new ChatChannelTabBar.Row();
        row.rowBottom = 100;
        int rowTop = ChatChannelTabBar.rowTop(100);
        int toolBottom = 100 + ChatWindowPlacement.TOOL_STRIP_HEIGHT;
        assertEquals(17, ChatWindowPlacement.TOOL_STRIP_HEIGHT);
        assertTrue(ChatChannelTabBar.inStripBand(row, rowTop));
        assertFalse(ChatChannelTabBar.inStripBand(row, rowTop - 1));
        assertTrue(ChatChannelTabBar.inRowBand(row, 99));
        assertFalse(ChatChannelTabBar.inRowBand(row, 100));
        assertTrue(ChatChannelTabBar.inStripBand(row, 100));
        assertTrue(ChatChannelTabBar.inStripBand(row, toolBottom - 1));
        assertFalse(ChatChannelTabBar.inStripBand(row, toolBottom));
    }

    /** The marquee: still, out, rest, back, rest — on the clock alone. */
    @Test
    public void marqueeWaitsThenSlidesOutRestsAndSlidesBack() {
        int overflow = 40;
        double delay = ChatChannelTabBar.MARQUEE_START_DELAY_SECONDS;
        double speed = ChatChannelTabBar.MARQUEE_SPEED_PX_PER_SECOND;
        double pause = ChatChannelTabBar.MARQUEE_END_PAUSE_SECONDS;
        double slide = overflow / speed;
        assertEquals(0.0D, ChatChannelTabBar.marqueeOffset(0.0D, overflow), EPSILON);
        assertEquals(0.0D, ChatChannelTabBar.marqueeOffset(delay * 0.8D, overflow), EPSILON);
        // Half a second in: half a second's worth of sliding.
        assertEquals(0.5D * speed,
                ChatChannelTabBar.marqueeOffset(delay + 0.5D, overflow), EPSILON);
        // At the end of the slide and through the rest: the whole overflow.
        assertEquals(overflow,
                ChatChannelTabBar.marqueeOffset(delay + slide, overflow), EPSILON);
        assertEquals(overflow,
                ChatChannelTabBar.marqueeOffset(delay + slide + pause * 0.5D, overflow),
                EPSILON);
        // Halfway back.
        assertEquals(overflow / 2.0D,
                ChatChannelTabBar.marqueeOffset(delay + slide + pause + slide / 2.0D,
                        overflow), EPSILON);
        // Home, resting, and round again.
        double cycle = 2.0D * (slide + pause);
        assertEquals(0.0D,
                ChatChannelTabBar.marqueeOffset(delay + cycle - pause * 0.5D, overflow),
                EPSILON);
        assertEquals(0.5D * speed,
                ChatChannelTabBar.marqueeOffset(delay + cycle + 0.5D, overflow), EPSILON);
    }

    @Test
    public void marqueeIsStillWhenNothingOverflows() {
        assertEquals(0.0D, ChatChannelTabBar.marqueeOffset(5.0D, 0), EPSILON);
        assertEquals(0.0D, ChatChannelTabBar.marqueeOffset(5.0D, -3), EPSILON);
    }

    @Test
    public void marqueeNeverLeavesTheOverflow() {
        int overflow = 17;
        for (double time = 0.0D; time < 20.0D; time += 0.037D) {
            double offset = ChatChannelTabBar.marqueeOffset(time, overflow);
            assertTrue(offset >= -EPSILON);
            assertTrue(offset <= overflow + EPSILON);
        }
    }

    /**
     * Every tab of a run is one width: the default while the row holds
     * them all at it, else the widest they can share once the seams
     * between them are paid; never less than nothing.
     */
    @Test
    public void everyTabIsOneWidth() {
        int gap = ChatChannelTabBar.TAB_GAP;
        int fallback = ChatChannelTabBar.DEFAULT_TAB_WIDTH;
        assertEquals(fallback, ChatChannelTabBar.uniformTabWidth(1000, 3),
                EPSILON);
        assertEquals(fallback, ChatChannelTabBar.uniformTabWidth(
                fallback * 3 + gap * 2, 3), EPSILON);
        assertEquals(fallback - 1, ChatChannelTabBar.uniformTabWidth(
                fallback * 3 + gap * 2 - 3, 3), EPSILON);
        // Fractions of a pixel are kept: a window's edge dragged a third
        // of a pixel narrows every tab by a ninth of one.
        assertEquals((200.0D - 2 * gap) / 3,
                ChatChannelTabBar.uniformTabWidth(200, 3), EPSILON);
        assertEquals((200.0D - 2 * gap) / 3 - 1.0D / 9.0D,
                ChatChannelTabBar.uniformTabWidth(200 - 1.0D / 3.0D, 3),
                EPSILON);
        assertEquals(0, ChatChannelTabBar.uniformTabWidth(0, 3), EPSILON);
        assertEquals(0, ChatChannelTabBar.uniformTabWidth(50, 0), EPSILON);
        assertEquals(0, ChatChannelTabBar.uniformTabWidth(-5, 2), EPSILON);
    }


    /**
     * Every tab gives its buttons up at fixed shares of the full width,
     * all tabs at once since they share one width: the draft mark under
     * two thirds, the cross under a third.
     */
    @Test
    public void buttonsGoAtFixedSharesOfTheFullWidth() {
        double full = ChatChannelTabBar.DEFAULT_TAB_WIDTH;
        assertTrue(ChatChannelTabBar.draftStands(full * 2.0D / 3.0D));
        assertFalse(ChatChannelTabBar.draftStands(full * 2.0D / 3.0D - 0.01D));
        assertTrue(ChatChannelTabBar.closeStands(full / 3.0D));
        assertFalse(ChatChannelTabBar.closeStands(full / 3.0D - 0.01D));
        // In that order, so a narrowing row never shows a draft mark
        // without a cross after it.
        assertTrue(ChatChannelTabBar.DRAFT_SHARE > ChatChannelTabBar.CLOSE_SHARE);
    }

    /**
     * A cross fading out hands its room to the name as it goes: the
     * name's room grows smoothly with the fade, never in a step, and is
     * never more than the name is wide.
     */
    @Test
    public void aFadingButtonHandsItsRoomToTheName() {
        int icon = 13;
        int name = 90;
        ChatChannelTabBar.TabRoom shown = ChatChannelTabBar.roomFor(100.0D,
                icon, name, 0.0D, 1.0F);
        assertEquals(100 - ChatChannelTabBar.PADDING_X
                - ChatChannelTabBar.CONTROL_SIZE, shown.closeLeft, EPSILON);
        assertEquals(100 - ChatChannelTabBar.PADDING_X - CONTROL,
                shown.contentRight, EPSILON);
        assertEquals(shown.contentRight - ChatChannelTabBar.PADDING_X - icon,
                shown.labelRoom, EPSILON);
        double previous = shown.labelRoom;
        for (float close = 0.9F; close >= 0.0F; close -= 0.1F) {
            ChatChannelTabBar.TabRoom fading = ChatChannelTabBar.roomFor(
                    100.0D, icon, name, 0.0D, close);
            assertTrue(fading.labelRoom > previous);
            assertTrue(fading.labelRoom - previous <= CONTROL * 0.1D + EPSILON);
            previous = fading.labelRoom;
        }
        // A short name never takes more than it is wide.
        assertEquals(12.0D, ChatChannelTabBar.roomFor(100.0D, icon, 12, 0.0D,
                0.0F).labelRoom, EPSILON);
        // The draft mark stands between the name and the cross.
        assertEquals(shown.labelRoom - 20.0D, ChatChannelTabBar.roomFor(100.0D,
                icon, name, 20.0D, 1.0F).labelRoom, EPSILON);
    }

    /**
     * The tab in front keeps its cross against its right padding however
     * narrow it is; its icon and name are cut before the cross like any
     * name, so at the narrowest the cross stands where the icon was.
     */
    @Test
    public void theTabInFrontCutsItsIconBeforeItsCross() {
        int width = ChatChannelTabBar.PADDING_X * 2 + 10;
        ChatChannelTabBar.TabRoom narrow = ChatChannelTabBar.roomFor(width,
                13, 40, 0.0D, 1.0F);
        assertEquals(width - ChatChannelTabBar.PADDING_X
                - ChatChannelTabBar.CONTROL_SIZE, narrow.closeLeft, EPSILON);
        assertEquals(narrow.closeLeft - ChatChannelTabBar.CONTROL_GAP,
                narrow.contentRight, EPSILON);
        assertEquals(0.0D, narrow.labelRoom, EPSILON);
    }

    /**
     * The whole row travels on one glide: every edge the same share of
     * the way from where it set out to where it is bound. Tabs that meet
     * at both ends therefore meet all along it — a tab joining between
     * two others included, since it sets out a seam short of nothing
     * where the tab before it ends — so no seam opens, shuts or steps.
     */
    @Test
    public void tabsThatMeetAtBothEndsMeetAllAlongTheGlide() {
        double gap = ChatChannelTabBar.TAB_GAP;
        ChatChannelTabBar.Tab first = tab(0.0D, 50.0D, 0.0D, 33.0D);
        ChatChannelTabBar.Tab joining = tab(50.0D + gap, -gap,
                33.0D + gap, 33.0D);
        ChatChannelTabBar.Tab last = tab(50.0D + gap, 50.0D,
                66.0D + 2.0D * gap, 33.0D);
        for (float share = 0.0F; share <= 1.0F; share += 0.05F) {
            ChatChannelTabBar.glide(first, share, 1.0D / 3.0D);
            ChatChannelTabBar.glide(joining, share, 1.0D / 3.0D);
            ChatChannelTabBar.glide(last, share, 1.0D / 3.0D);
            assertEquals(first.leftExact + first.widthExact + gap,
                    joining.leftExact, EPSILON);
            assertEquals(joining.leftExact + joining.widthExact + gap,
                    last.leftExact, 1.0E-5D);
            // Laid on display pixels, the edges still meet exactly.
            assertEquals(first.drawnLeftOffset + first.drawnWidthSnapped + gap,
                    joining.drawnLeftOffset, 1.0E-4D);
            if (joining.widthExact >= 0.0D) {
                assertEquals(joining.drawnLeftOffset
                                + joining.drawnWidthSnapped + gap,
                        last.drawnLeftOffset, 1.0E-4D);
            }
        }
    }

    /**
     * A tab's cross rides its right edge exactly at every GUI scale. At
     * scale three a third of a GUI pixel has no exact float, and a cross
     * laid on the display pixel at or before its place dropped a pixel
     * against the tab's edge on some frames and not others; every place
     * of the edge, as the row lays it on display pixels, keeps its cross
     * the same distance in.
     */
    @Test
    public void theButtonsRideTheTabsEdgeAtEveryScale() {
        for (int factor = 1; factor <= 4; factor++) {
            double step = 1.0D / factor;
            for (int index = 0; index < 3000; index++) {
                ChatChannelTabBar.Tab tab = tab(0.0D, 0.0D, 0.0D, 0.0D);
                tab.standAt(31.0D + index * 0.1373D,
                        60.0D + (index % 97) * 0.2111D, step);
                float left = 212 + tab.drawnLeftOffset;
                float width = tab.drawnWidthSnapped;
                ChatChannelTabBar.TabRoom room = ChatChannelTabBar.roomFor(
                        width, 13, 40, 0.0D, 1.0F);
                double edge = left + width;
                assertEquals(edge - ChatChannelTabBar.PADDING_X
                        - ChatChannelTabBar.CONTROL_SIZE,
                        ChatChannelTabBar.snappedLeft(left + room.closeLeft,
                                step), 1.0E-4D);
            }
        }
    }

    /**
     * A button holds its whole room for as long as any of it shows: going,
     * its ink fades first and its room goes after; coming, its room comes
     * first and its ink after. The name therefore never runs under a
     * button, and both halves together make one smooth beat.
     */
    @Test
    public void aButtonHoldsItsRoomWhileAnyOfItShows() {
        for (float fade = 0.0F; fade <= 1.0F; fade += 0.01F) {
            if (ChatChannelTabBar.inkPhase(fade) > 0.0F) {
                assertEquals(1.0F, ChatChannelTabBar.roomPhase(fade), 0.0F);
            }
        }
        assertEquals(0.0F, ChatChannelTabBar.roomPhase(0.0F), 0.0F);
        assertEquals(0.5F, ChatChannelTabBar.roomPhase(0.25F), 1.0E-6F);
        assertEquals(0.0F, ChatChannelTabBar.inkPhase(0.5F), 0.0F);
        assertEquals(0.5F, ChatChannelTabBar.inkPhase(0.75F), 1.0E-6F);
        assertEquals(1.0F, ChatChannelTabBar.inkPhase(1.0F), 0.0F);
    }

    /**
     * A tab growing in lays its contents out at the width it is bound for,
     * and one shrinking away at the width it had as it began to go, so its
     * moving edge cuts them rather than squeezing them together; settled,
     * a tab lays them out at the width it is drawn at.
     */
    @Test
    public void aTabOpeningOrClosingKeepsItsContentsWhereTheyStand() {
        ChatChannelTabBar.Tab tab = tab(0.0D, -1.0D, 0.0D, 90.0D);
        tab.joining = true;
        assertEquals(90.0D, tab.laidWidth(12.0D), EPSILON);
        tab.joining = false;
        assertEquals(12.0D, tab.laidWidth(12.0D), EPSILON);
        tab.leavingWidth = 75.0D;
        assertEquals(75.0D, tab.laidWidth(20.0D), EPSILON);
    }

    /**
     * A tab set out again leaves from where it is drawn, on a glide of its
     * own that starts over; one carried on into a new layout keeps the
     * glide it was on, so a tab the row does not move is never slowed.
     */
    @Test
    public void aTabKeepsItsGlideUnlessItsOwnTargetMoves() {
        ChatChannelTabBar.Tab tab = tab(0.0D, 60.0D, 100.0D, 60.0D);
        tab.leg.settle(false);
        long start = 1000000000L;
        long half = com.ninuna.losttales.client.motion.Motions.nanos(
                com.ninuna.losttales.client.motion.MotionIds.CHAT_TAB_MOVE)
                / 2L;
        float early = tab.leg.advance(start, true);
        float halfway = tab.leg.advance(start + half, true);
        assertEquals(0.0F, early, 1.0E-6F);
        // Fast away: past three quarters of the way at half the time.
        assertTrue(halfway > 0.8F);
        ChatChannelTabBar.Tab relaid = tab(0.0D, 0.0D, 100.0D, 60.0D);
        relaid.carryOn(tab);
        assertTrue(relaid.leg == tab.leg);
        ChatChannelTabBar.glide(relaid, halfway, 1.0D / 3.0D);
        relaid.setOut();
        assertEquals(relaid.leftExact, relaid.fromLeft, EPSILON);
        assertEquals(0.0F, relaid.leg.value(), 0.0F);
    }

    /** A tab's pings are the count tile; past nine it shows the plus. */
    @Test
    public void aTabsPingsAreItsCountTile() {
        assertEquals(com.ninuna.losttales.gui.style.LostTalesUiSheet.COUNT_3,
                ChatIconMark.pings(3).figure());
        assertEquals(com.ninuna.losttales.gui.style.LostTalesUiSheet.COUNT_MORE,
                ChatIconMark.pings(42).figure());
        assertEquals(com.ninuna.losttales.gui.style.LostTalesUiSheet.COUNT_1,
                ChatIconMark.pings(1).figure());
    }

    /** A tab standing at {@code fromLeft}, bound for {@code toLeft}. */
    private static ChatChannelTabBar.Tab tab(double fromLeft, double fromWidth,
                                            double toLeft, double toWidth) {
        ChatChannelTabBar.Tab tab = new ChatChannelTabBar.Tab(
                ChatTab.of(com.ninuna.losttales.chat.ChatChannel.GLOBAL), 0, null,
                "Global", 30, 30, false, 0, (int)toWidth, -1, -1, false);
        tab.standAt(fromLeft, fromWidth, 1.0D / 3.0D);
        tab.toLeft = toLeft;
        tab.exactWidth = toWidth;
        return tab;
    }

    /**
     * A row is asked for every tab at its narrowest, no tab reserved
     * more: the tab in front is the width of the rest, whichever it is.
     */
    @Test
    public void aRowIsReservedEveryTabAtItsNarrowest() {
        assertEquals(25 + 25 + 22,
                ChatChannelTabBar.reservedRowWidth(new int[] { 25, 25, 22 }));
        assertEquals(25, ChatChannelTabBar.reservedRowWidth(new int[] { 25 }));
        assertEquals(0, ChatChannelTabBar.reservedRowWidth(new int[0]));
    }

    /**
     * A tab rising off the row stretches along one seam row of its
     * border pieces, so every piece must hold plain side line there —
     * the rows either side of the seam the same texels as the seam — or
     * the stretch would show as a smear.
     */
    @Test
    public void everyTabPieceStretchesOnPlainSideLine() throws Exception {
        BufferedImage sheet = readSheet();
        LostTalesUiSheet[] pieces = {LostTalesUiSheet.TAB_LEFT,
                LostTalesUiSheet.TAB_RIGHT, LostTalesUiSheet.TAB_HOVER_LEFT,
                LostTalesUiSheet.TAB_HOVER_RIGHT,
                LostTalesUiSheet.TAB_SELECTED_LEFT,
                LostTalesUiSheet.TAB_SELECTED_RIGHT,
                LostTalesUiSheet.TAB_LIFTED_LEFT,
                LostTalesUiSheet.TAB_LIFTED_RIGHT};
        int seam = ChatChannelTabBar.LIFT_SEAM_ROW;
        for (LostTalesUiSheet piece : pieces) {
            for (int row = seam - 1; row <= seam + 1; row++) {
                for (int x = 0; x < piece.getWidth(); x++) {
                    assertEquals(piece + " is not plain side line at row "
                                    + row + ", column " + x,
                            texel(sheet, piece, x, seam),
                            texel(sheet, piece, x, row));
                }
            }
        }
    }

    /**
     * The lifted pair is the selected pair a row taller, the extra row
     * one more of the side line at the seam: without it, the two draw
     * the same ink on the same texels, so the one crosses to the other
     * in colour alone while the tab rises.
     */
    @Test
    public void theLiftedPairIsTheSelectedShapeARowTallerAtTheSeam()
            throws Exception {
        BufferedImage sheet = readSheet();
        LostTalesUiSheet[][] pairs = {
                {LostTalesUiSheet.TAB_SELECTED_LEFT,
                        LostTalesUiSheet.TAB_LIFTED_LEFT},
                {LostTalesUiSheet.TAB_SELECTED_RIGHT,
                        LostTalesUiSheet.TAB_LIFTED_RIGHT}};
        int seam = ChatChannelTabBar.LIFT_SEAM_ROW;
        for (LostTalesUiSheet[] pair : pairs) {
            LostTalesUiSheet selected = pair[0];
            LostTalesUiSheet lifted = pair[1];
            assertEquals(selected.getWidth(), lifted.getWidth());
            assertEquals(selected.getHeight() + 1, lifted.getHeight());
            for (int row = 0; row < selected.getHeight(); row++) {
                int liftedRow = row < seam ? row : row + 1;
                for (int x = 0; x < selected.getWidth(); x++) {
                    assertEquals(lifted + " and " + selected
                                    + " differ in shape at row " + row
                                    + ", column " + x,
                            isInk(texel(sheet, selected, x, row)),
                            isInk(texel(sheet, lifted, x, liftedRow)));
                }
            }
        }
    }

    private static int texel(BufferedImage sheet, LostTalesUiSheet piece,
                             int x, int y) {
        return sheet.getRGB(piece.getTextureU() + x, piece.getTextureV() + y);
    }

    private static boolean isInk(int argb) {
        return (argb >>> 24) >= Math.floor(
                LostTalesUiSheet.INK_THRESHOLD * 255.0F);
    }

    private static BufferedImage readSheet() throws Exception {
        InputStream stream = ChatChannelTabBarTest.class.getResourceAsStream(
                "/assets/losttales/" + LostTalesUiSheet.TEXTURE_PATH);
        assertNotNull("Chat icon sheet is missing", stream);
        try {
            return ImageIO.read(stream);
        } finally {
            stream.close();
        }
    }

}
