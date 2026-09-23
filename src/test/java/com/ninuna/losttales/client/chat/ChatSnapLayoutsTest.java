package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The snap layouts: the parts of the screen a window fills, the layouts
 * a screen has room for, how a panel of them is laid out and answers
 * the pointer, the snap bar at the top of the screen peeking and brought
 * all the way down, where the panel under a fullscreen control opens,
 * and the suggested layouts holding the other windows. Without a
 * Minecraft instance the narrowest window is 166 wide and 88 tall, and a
 * window keeps two pixels inside the part of the screen it fills. A panel
 * wears a chat window's two-pixel frame inside its box, four clear pixels
 * inside that. Motion is off, so every transition and follower lands at
 * once.
 */
public final class ChatSnapLayoutsTest {
    private static final double EPSILON = 1.0E-9D;
    private boolean animations;

    @Before
    public void setUp() {
        this.animations = LostTalesConfig.animations;
        // Motion off: every transition stands where it is bound.
        LostTalesConfig.animations = false;
    }

    @After
    public void tearDown() {
        LostTalesConfig.animations = this.animations;
    }

    /**
     * Every layout's zones share the screen out exactly — no pixel in
     * two zones, none in no zone — at any size, odd ones included, so
     * two windows filling neighbouring zones meet at one edge.
     */
    @Test
    public void everyLayoutSharesTheScreenOutExactly() {
        int[][] screens = {{960, 540}, {853, 480}, {427, 240}, {1001, 563}};
        List<ChatWindow.ScreenFill[]> layouts = ChatSnapLayouts.offered(
                100000, 100000, 0.0D, 0.0D);
        assertEquals(6, layouts.size());
        for (int[] screen : screens) {
            for (ChatWindow.ScreenFill[] layout : layouts) {
                int[][] owner = new int[screen[0]][screen[1]];
                for (int index = 0; index < layout.length; index++) {
                    ChatWindow.ScreenFill zone = layout[index];
                    for (int x = zone.left(screen[0]);
                            x < zone.left(screen[0]) + zone.width(screen[0]);
                            x++) {
                        for (int y = zone.top(screen[1]);
                                y < zone.top(screen[1])
                                        + zone.height(screen[1]); y++) {
                            assertEquals(0, owner[x][y]);
                            owner[x][y] = index + 1;
                        }
                    }
                }
                for (int x = 0; x < screen[0]; x++) {
                    for (int y = 0; y < screen[1]; y++) {
                        assertTrue(owner[x][y] > 0);
                    }
                }
            }
        }
    }

    /** The halves and quarters stand where they always did, odd screens included. */
    @Test
    public void halvesAndQuartersKeepTheirPlaces() {
        assertEquals(426, ChatWindow.ScreenFill.LEFT.width(853));
        assertEquals(426, ChatWindow.ScreenFill.RIGHT.left(853));
        assertEquals(427, ChatWindow.ScreenFill.RIGHT.width(853));
        assertEquals(240, ChatWindow.ScreenFill.BOTTOM_LEFT.top(481));
        assertEquals(241, ChatWindow.ScreenFill.BOTTOM_LEFT.height(481));
        assertEquals(853, ChatWindow.ScreenFill.FULL.width(853));
        assertEquals(ChatWindow.ScreenFill.TOP_RIGHT,
                ChatWindow.ScreenFill.fromId("top_right"));
        assertEquals(ChatWindow.ScreenFill.CENTRE_THIRD,
                ChatWindow.ScreenFill.fromId("centre_third"));
    }

    /**
     * A layout is offered only while every zone of it holds the
     * narrowest window: a wide screen has all six, a small one only the
     * halves and quarters, a short one only the halves.
     */
    @Test
    public void aSmallerScreenOffersFewerLayouts() {
        assertEquals(6, ChatSnapLayouts.offered(null, 960, 540).size());
        List<ChatWindow.ScreenFill[]> small = ChatSnapLayouts.offered(null,
                427, 240);
        assertEquals(3, small.size());
        assertArrayEquals(new ChatWindow.ScreenFill[] {
                ChatWindow.ScreenFill.LEFT, ChatWindow.ScreenFill.RIGHT},
                small.get(0));
        assertEquals(ChatWindow.ScreenFill.TOP_RIGHT, small.get(1)[1]);
        assertEquals(4, small.get(2).length);
        List<ChatWindow.ScreenFill[]> low = ChatSnapLayouts.offered(null,
                427, 180);
        assertEquals(1, low.size());
    }

    /**
     * Zones of one layout stand the gap apart and the outer ones reach
     * the small screen's edges; the centre of three loses a pixel of gap
     * on both sides.
     */
    @Test
    public void zonesStandAGapApartInsideTheirSmallScreen() {
        assertBox(0, 0, 17, 20, ChatSnapLayouts.zoneBox(
                ChatWindow.ScreenFill.LEFT, 0, 0, 36, 20));
        assertBox(19, 0, 17, 20, ChatSnapLayouts.zoneBox(
                ChatWindow.ScreenFill.RIGHT, 0, 0, 36, 20));
        assertBox(19, 0, 17, 9, ChatSnapLayouts.zoneBox(
                ChatWindow.ScreenFill.TOP_RIGHT, 0, 0, 36, 20));
        assertBox(19, 11, 17, 9, ChatSnapLayouts.zoneBox(
                ChatWindow.ScreenFill.BOTTOM_RIGHT, 0, 0, 36, 20));
        assertBox(0, 0, 8, 20, ChatSnapLayouts.zoneBox(
                ChatWindow.ScreenFill.LEFT_QUARTER, 0, 0, 36, 20));
        assertBox(10, 0, 16, 20, ChatSnapLayouts.zoneBox(
                ChatWindow.ScreenFill.CENTRE_HALF, 0, 0, 36, 20));
        assertBox(13, 0, 10, 20, ChatSnapLayouts.zoneBox(
                ChatWindow.ScreenFill.CENTRE_THIRD, 0, 0, 36, 20));
        assertEquals(27, ChatSnapLayouts.thumbHeight(960, 540));
        assertEquals(ChatSnapLayouts.MIN_THUMB_HEIGHT,
                ChatSnapLayouts.thumbHeight(1000, 200));
    }

    /**
     * A panel answers with the zone under the pointer, counted through
     * the panel: a part of the screen two layouts offer is two zones,
     * and only the one under the pointer lights. Between zones and on
     * the padding nothing answers, though the panel is still there.
     */
    @Test
    public void aPanelAnswersWithTheZoneUnderThePointer() {
        List<ChatWindow.ScreenFill[]> layouts = ChatSnapLayouts.offered(null,
                960, 540);
        ChatSnapLayouts.Panel panel = ChatSnapLayouts.lay(
                Collections.<ChatSnapLayouts.Suggestion>emptyList(), layouts,
                6, 100.0D, 2.0D, 960, 540);
        assertEquals(320.0D, panel.box.width, EPSILON);
        assertEquals(39.0D, panel.box.height, EPSILON);
        // The first layout's small screen starts inside the frame and
        // the padding, at (106, 8); the fourth, the quarters, three
        // layouts on, after seven zones.
        assertEquals(0, panel.zoneAt(110.0D, 12.0D));
        assertEquals(7, panel.zoneAt(265.0D, 12.0D));
        assertEquals(ChatWindow.ScreenFill.LEFT, panel.fillOf(0));
        assertEquals(ChatWindow.ScreenFill.TOP_LEFT, panel.fillOf(7));
        assertEquals(-1, panel.zoneAt(129.0D, 12.0D));
        assertEquals(-1, panel.zoneAt(102.0D, 12.0D));
        assertTrue(panel.contains(102.0D, 12.0D));
        assertEquals(ChatWindow.ScreenFill.NONE, panel.fillOf(-1));
        assertTrue(panel.companionsOf(0).isEmpty());
        // In rows of three the panel is two rows tall.
        assertEquals(164, ChatSnapLayouts.panelWidth(6, 3));
        assertEquals(56, ChatSnapLayouts.panelHeight(6, 3, 20));
    }

    /**
     * A carried window's pointer near the top of the screen brings the
     * snap bar out peeking, wherever along the top it is. While the bar
     * peeks it answers nothing, so the screen's own edges decide, the
     * top edge's band below the bar and the edge beside it included.
     */
    @Test
    public void theSnapBarPeeksAsACarriedWindowNearsTheTop() {
        ChatSnapLayouts.Bar bar = new ChatSnapLayouts.Bar();
        assertNull(bar.follow(null, null, 480.0D, 300.0D, 960, 540));
        assertEquals(ChatSnapLayouts.Bar.Stage.HIDDEN, bar.stage());
        // All the way down the bar is 39 tall under a margin of 2, and
        // the pointer brings it out 24 below that: above 65.
        assertNull(bar.follow(null, null, 480.0D, 65.0D, 960, 540));
        assertEquals(ChatSnapLayouts.Bar.Stage.HIDDEN, bar.stage());
        assertNull(bar.follow(null, null, 100.0D, 64.0D, 960, 540));
        assertEquals(ChatSnapLayouts.Bar.Stage.PEEKING, bar.stage());
        // In the top edge's band under the peek nothing is touched yet,
        // and the edge fills the whole screen.
        assertNull(bar.follow(null, null, 480.0D, 12.0D, 960, 540));
        assertNull(bar.follow(null, null, 480.0D, 9.0D, 960, 540));
        assertNull(bar.follow(null, null, 480.0D, 9.0D, 960, 540));
        assertEquals(ChatSnapLayouts.Bar.Stage.PEEKING, bar.stage());
        assertEquals(ChatWindow.ScreenFill.FULL,
                ChatWindowGestures.snapZoneAt(480.0D, 9.0D, 960, 540, true));
        // Beside the bar the top edge is the screen's.
        assertNull(bar.follow(null, null, 200.0D, 0.0D, 960, 540));
        assertEquals(ChatSnapLayouts.Bar.Stage.PEEKING, bar.stage());
        assertNull(bar.litLayout());
    }

    /**
     * The nearer the pointer comes to the bar, the further the peeking
     * bar comes down: its frame and padding where the pointer brings it
     * out, and half the top edge's snap band as the pointer reaches that
     * line, never further, so the rest of the band still snaps as the
     * edge does. Off to the side the pointer is further from it.
     */
    @Test
    public void thePeekDeepensAsThePointerNearsButLeavesTheTopEdgesBand() {
        assertEquals(6, ChatSnapLayouts.Bar.PEEK);
        assertEquals(8, ChatSnapLayouts.Bar.PEEK_MOST);
        assertTrue(ChatSnapLayouts.Bar.PEEK_MOST
                < ChatWindowGestures.SNAP_REACH);
        // The bar of 960 by 540 spans 320 to 640 and comes out above 65.
        assertEquals(ChatSnapLayouts.Bar.PEEK,
                peekDepth(480.0D, 65.0D), EPSILON);
        assertEquals(7.0D, peekDepth(480.0D, 36.5D), EPSILON);
        assertEquals(ChatSnapLayouts.Bar.PEEK_MOST,
                peekDepth(480.0D, 8.0D), EPSILON);
        assertEquals(ChatSnapLayouts.Bar.PEEK_MOST,
                peekDepth(480.0D, 0.0D), EPSILON);
        double above = 0.0D;
        for (int y = 64; y >= 0; y--) {
            double depth = peekDepth(480.0D, y);
            assertTrue(depth >= above);
            assertTrue(depth <= ChatSnapLayouts.Bar.PEEK_MOST);
            above = depth;
        }
        assertEquals(7.0D, peekDepth(320.0D - 28.5D, 5.0D), EPSILON);
        assertEquals(ChatSnapLayouts.Bar.PEEK, peekDepth(100.0D, 0.0D),
                EPSILON);
    }

    /**
     * Touching what shows of the peeking bar brings it all the way down,
     * and only then does it answer: on a zone that part of the screen
     * and the zone's layout, on its padding none, and off it nothing.
     * The screen's top edge above it points at the zone standing below
     * the pointer; beside it the edge is the screen's.
     */
    @Test
    public void touchingThePeekBringsTheBarAllTheWayDown() {
        ChatSnapLayouts.Bar bar = revealed();
        // Layout k's small screen starts at (326 + 52k, 8); the half with
        // two quarters is the third layout, the thirds the fifth.
        assertEquals(ChatWindow.ScreenFill.TOP_RIGHT,
                bar.follow(null, null, 460.0D, 10.0D, 960, 540));
        assertArrayEquals(new ChatWindow.ScreenFill[] {
                        ChatWindow.ScreenFill.LEFT,
                        ChatWindow.ScreenFill.TOP_RIGHT,
                        ChatWindow.ScreenFill.BOTTOM_RIGHT},
                bar.litLayout());
        assertTrue(bar.litCompanions().isEmpty());
        assertEquals(ChatWindow.ScreenFill.RIGHT_THIRD,
                bar.follow(null, null, 570.0D, 10.0D, 960, 540));
        assertEquals(ChatWindow.ScreenFill.NONE,
                bar.follow(null, null, 323.0D, 10.0D, 960, 540));
        assertEquals(ChatWindow.ScreenFill.LEFT,
                bar.follow(null, null, 330.0D, 0.5D, 960, 540));
        assertEquals(ChatWindow.ScreenFill.RIGHT_THIRD,
                bar.follow(null, null, 570.0D, 0.0D, 960, 540));
        assertEquals(ChatWindow.ScreenFill.NONE,
                bar.follow(null, null, 323.0D, 0.0D, 960, 540));
        assertEquals(ChatSnapLayouts.Bar.Stage.REVEALED, bar.stage());
        assertNull(bar.follow(null, null, 200.0D, 0.0D, 960, 540));
    }

    /**
     * All the way down, the bar stays while the pointer is within its
     * reach of it, on it or not; further off it peeks again, and only a
     * touch brings it down once more.
     */
    @Test
    public void theBarPeeksAgainOnceThePointerLeavesItsReach() {
        ChatSnapLayouts.Bar bar = revealed();
        // All the way down it spans 320 to 640 and 2 to 41.
        assertNull(bar.follow(null, null, 296.0D, 20.0D, 960, 540));
        assertNull(bar.follow(null, null, 480.0D, 64.0D, 960, 540));
        assertEquals(ChatSnapLayouts.Bar.Stage.REVEALED, bar.stage());
        assertNull(bar.follow(null, null, 295.5D, 20.0D, 960, 540));
        assertEquals(ChatSnapLayouts.Bar.Stage.PEEKING, bar.stage());
        assertNull(bar.follow(null, null, 330.0D, 12.0D, 960, 540));
        assertNull(bar.follow(null, null, 330.0D, 10.0D, 960, 540));
        assertEquals(ChatSnapLayouts.Bar.Stage.PEEKING, bar.stage());
        assertEquals(ChatWindow.ScreenFill.NONE,
                bar.follow(null, null, 330.0D, 3.0D, 960, 540));
        assertEquals(ChatSnapLayouts.Bar.Stage.REVEALED, bar.stage());
    }

    /**
     * The pointer leaving the top of the screen puts the bar away, and
     * so does the carry ending; back at the top the bar peeks first,
     * however near the edge the pointer comes.
     */
    @Test
    public void leavingTheTopOfTheScreenPutsTheBarAway() {
        ChatSnapLayouts.Bar bar = revealed();
        assertNull(bar.follow(null, null, 330.0D, 65.0D, 960, 540));
        assertEquals(ChatSnapLayouts.Bar.Stage.HIDDEN, bar.stage());
        assertNull(bar.follow(null, null, 330.0D, 5.0D, 960, 540));
        assertEquals(ChatSnapLayouts.Bar.Stage.PEEKING, bar.stage());
        // The peek comes down past the pointer: touched.
        assertEquals(ChatWindow.ScreenFill.NONE,
                bar.follow(null, null, 330.0D, 5.0D, 960, 540));
        assertEquals(ChatSnapLayouts.Bar.Stage.REVEALED, bar.stage());
        bar.hide();
        assertEquals(ChatSnapLayouts.Bar.Stage.HIDDEN, bar.stage());
        assertNull(bar.litLayout());
    }

    /**
     * The stage follows the pointer, where the bar rests all the way
     * down and where it is drawn: away from its reach below, down once
     * what is drawn of it is touched and for as long as the pointer
     * stays within its reach, peeking anywhere else.
     */
    @Test
    public void theBarsStageFollowsThePointer() {
        LostTalesUiHitBox resting = new LostTalesUiHitBox(320.0D, 2.0D,
                320.0D, 39.0D);
        LostTalesUiHitBox peeking = new LostTalesUiHitBox(320.0D, -33.0D,
                320.0D, 39.0D);
        assertEquals(ChatSnapLayouts.Bar.Stage.HIDDEN,
                ChatSnapLayouts.Bar.stageFor(ChatSnapLayouts.Bar.Stage.REVEALED,
                        480.0D, 65.0D, resting, resting));
        assertEquals(ChatSnapLayouts.Bar.Stage.PEEKING,
                ChatSnapLayouts.Bar.stageFor(ChatSnapLayouts.Bar.Stage.HIDDEN,
                        480.0D, 64.0D, resting, peeking));
        assertEquals(ChatSnapLayouts.Bar.Stage.PEEKING,
                ChatSnapLayouts.Bar.stageFor(ChatSnapLayouts.Bar.Stage.PEEKING,
                        480.0D, 6.0D, resting, peeking));
        assertEquals(ChatSnapLayouts.Bar.Stage.PEEKING,
                ChatSnapLayouts.Bar.stageFor(ChatSnapLayouts.Bar.Stage.PEEKING,
                        319.5D, 0.0D, resting, peeking));
        assertEquals(ChatSnapLayouts.Bar.Stage.REVEALED,
                ChatSnapLayouts.Bar.stageFor(ChatSnapLayouts.Bar.Stage.PEEKING,
                        480.0D, 5.5D, resting, peeking));
        assertEquals(ChatSnapLayouts.Bar.Stage.REVEALED,
                ChatSnapLayouts.Bar.stageFor(ChatSnapLayouts.Bar.Stage.REVEALED,
                        663.5D, 64.5D, resting, resting));
        assertEquals(ChatSnapLayouts.Bar.Stage.PEEKING,
                ChatSnapLayouts.Bar.stageFor(ChatSnapLayouts.Bar.Stage.REVEALED,
                        664.0D, 20.0D, resting, resting));
        // A peeking bar is not held down by its reach.
        assertEquals(ChatSnapLayouts.Bar.Stage.PEEKING,
                ChatSnapLayouts.Bar.stageFor(ChatSnapLayouts.Bar.Stage.PEEKING,
                        480.0D, 20.0D, resting, peeking));
    }

    /**
     * The arrows walk a panel's zones by where they stand: along a
     * layout and on into the next, down into the layout below, and
     * nowhere past the panel's edge.
     */
    @Test
    public void theArrowsWalkThePanelsZonesByWhereTheyStand() {
        List<ChatWindow.ScreenFill[]> layouts = ChatSnapLayouts.offered(null,
                960, 540);
        ChatSnapLayouts.Panel panel = ChatSnapLayouts.lay(
                Collections.<ChatSnapLayouts.Suggestion>emptyList(), layouts,
                3, 0.0D, 0.0D, 960, 540);
        // Zones in order: the halves 0-1, two thirds and a third 2-3, a
        // half and two quarters 4-6, the quarters 7-10, the thirds 11-13,
        // a half between quarter columns 14-16.
        assertEquals(1, panel.step(0, ChatSnapKeys.Direction.RIGHT));
        assertEquals(2, panel.step(1, ChatSnapKeys.Direction.RIGHT));
        assertEquals(0, panel.step(0, ChatSnapKeys.Direction.LEFT));
        assertEquals(0, panel.step(0, ChatSnapKeys.Direction.UP));
        assertEquals(7, panel.step(0, ChatSnapKeys.Direction.DOWN));
        assertEquals(ChatWindow.ScreenFill.TOP_LEFT, panel.fillOf(7));
        assertEquals(ChatWindow.ScreenFill.BOTTOM_LEFT,
                panel.fillOf(panel.step(7, ChatSnapKeys.Direction.DOWN)));
    }

    /**
     * The layout a part of the screen shares it by, for snap assist: the
     * halves for a half, the quarters for a quarter, the thirds for a
     * third; none for the whole screen or a part the player shaped.
     */
    @Test
    public void everyZoneKnowsItsLayout() {
        assertArrayEquals(new ChatWindow.ScreenFill[] {
                        ChatWindow.ScreenFill.LEFT,
                        ChatWindow.ScreenFill.RIGHT},
                ChatSnapLayouts.layoutFor(ChatWindow.ScreenFill.RIGHT));
        assertEquals(4, ChatSnapLayouts.layoutFor(
                ChatWindow.ScreenFill.BOTTOM_LEFT).length);
        assertEquals(ChatWindow.ScreenFill.RIGHT_THIRD,
                ChatSnapLayouts.layoutFor(
                        ChatWindow.ScreenFill.LEFT_TWO_THIRDS)[1]);
        assertNull(ChatSnapLayouts.layoutFor(ChatWindow.ScreenFill.FULL));
        assertNull(ChatSnapLayouts.layoutFor(
                ChatWindow.ScreenFill.free(0.2D, 0.0D, 0.7D, 1.0D)));
    }

    /**
     * A free part stands exactly where a named part with the same edges
     * does, to the pixel, at every screen size: two windows sharing an
     * edge the player dragged meet as named ones do.
     */
    @Test
    public void aFreePartStandsWhereTheNamedPartWithItsEdgesDoes() {
        ChatWindow.ScreenFill[] named = {ChatWindow.ScreenFill.FULL,
                ChatWindow.ScreenFill.RIGHT, ChatWindow.ScreenFill.TOP_LEFT,
                ChatWindow.ScreenFill.CENTRE_THIRD,
                ChatWindow.ScreenFill.RIGHT_TWO_THIRDS,
                ChatWindow.ScreenFill.RIGHT_QUARTER};
        int[] sizes = {427, 480, 540, 853, 960, 961, 1001};
        for (ChatWindow.ScreenFill fill : named) {
            ChatWindow.ScreenFill free = ChatWindow.ScreenFill.free(
                    fill.leftShare(), fill.topShare(), fill.rightShare(),
                    fill.bottomShare());
            assertTrue(free.isFree());
            for (int size : sizes) {
                assertEquals(fill + " at " + size, fill.left(size),
                        free.left(size));
                assertEquals(fill + " at " + size, fill.width(size),
                        free.width(size));
                assertEquals(fill + " at " + size, fill.top(size),
                        free.top(size));
                assertEquals(fill + " at " + size, fill.height(size),
                        free.height(size));
            }
        }
    }

    /**
     * A free part is written as its four edges and read back as itself;
     * a named part keeps its name, and anything unreadable is none.
     */
    @Test
    public void aFreePartIsWrittenAsItsEdges() {
        ChatWindow.ScreenFill free = ChatWindow.ScreenFill.free(0.625D, 0.0D,
                1.0D, 0.5D);
        assertEquals("free:0.62500,0.00000,1.00000,0.50000", free.id());
        assertEquals(free, ChatWindow.ScreenFill.fromId(free.id()));
        assertTrue(ChatWindow.ScreenFill.fromId(free.id()) != free);
        assertEquals("left", ChatWindow.ScreenFill.LEFT.id());
        assertTrue(ChatWindow.ScreenFill.LEFT
                == ChatWindow.ScreenFill.fromId("LEFT"));
        assertEquals(ChatWindow.ScreenFill.NONE,
                ChatWindow.ScreenFill.fromId("free:0.5,0.2"));
        assertEquals(ChatWindow.ScreenFill.NONE,
                ChatWindow.ScreenFill.fromId("free:0.5,0.5,0.5,1.0"));
        assertEquals(ChatWindow.ScreenFill.NONE,
                ChatWindow.ScreenFill.fromId("free:a,b,c,d"));
        // A named part is never equal to a free one with its edges: it
        // is a zone of the layouts, and the free one is the player's.
        assertFalse(ChatWindow.ScreenFill.RIGHT.equals(
                ChatWindow.ScreenFill.free(0.5D, 0.0D, 1.0D, 1.0D)));
    }

    /**
     * Stretched to the screen's height, a window fills its own column:
     * its own width, the room every part keeps for the frame round it.
     */
    @Test
    public void aWindowStretchedToTheScreensHeightFillsItsOwnColumn() {
        ChatWindow.ScreenFill column = ChatWindowPlacement.columnFill(
                100.0D, 400.0D, 1000);
        assertEquals((100.0D - ChatWindowPlacement.EDGE_MARGIN) / 1000.0D,
                column.leftShare(), EPSILON);
        assertEquals((400.0D + ChatWindowPlacement.EDGE_MARGIN) / 1000.0D,
                column.rightShare(), EPSILON);
        assertEquals(0.0D, column.topShare(), EPSILON);
        assertEquals(1.0D, column.bottomShare(), EPSILON);
        assertEquals(ChatWindow.ScreenFill.NONE,
                ChatWindowPlacement.columnFill(0.0D, 10.0D, 0));
    }

    /**
     * The panel under a fullscreen control opens as a menu does: toward
     * the middle, its right edge lined up with the control's near the
     * right of the screen, below a control near the top and above one
     * near the bottom.
     */
    @Test
    public void theFlyoutOpensTowardTheMiddle() {
        List<ChatWindow.ScreenFill[]> layouts = ChatSnapLayouts.offered(null,
                960, 540);
        List<ChatSnapLayouts.Suggestion> none =
                Collections.<ChatSnapLayouts.Suggestion>emptyList();
        ChatSnapLayouts.Panel below = ChatSnapLayouts.Flyout.hangFrom(none,
                layouts, new LostTalesUiHitBox(900.0D, 10.0D, 11.0D, 11.0D),
                null, 960, 540);
        assertBox(747, 23, 164, 70, below.box);
        ChatSnapLayouts.Panel above = ChatSnapLayouts.Flyout.hangFrom(none,
                layouts, new LostTalesUiHitBox(900.0D, 520.0D, 11.0D, 11.0D),
                null, 960, 540);
        assertBox(747, 448, 164, 70, above.box);
        assertFalse(above.contains(900.0D, 525.0D));
    }

    /**
     * The suggested layouts hold the windows in front beside the one in
     * hand: the halves with the window in front on the right once there
     * is one, and a half with the two in front stacked on the right once
     * there are two, each only where the screen offers its layout.
     */
    @Test
    public void aSuggestionHoldsTheWindowsInFrontBesideTheOneInHand() {
        List<ChatWindow.ScreenFill[]> layouts = ChatSnapLayouts.offered(null,
                960, 540);
        assertTrue(ChatSnapLayouts.suggested(layouts, "hand",
                Collections.<String>emptyList()).isEmpty());
        List<ChatSnapLayouts.Suggestion> one = ChatSnapLayouts.suggested(
                layouts, "hand", Arrays.asList("front"));
        assertEquals(1, one.size());
        assertArrayEquals(new String[] {"hand", "front"}, one.get(0).windows);
        assertEquals(ChatWindow.ScreenFill.RIGHT, one.get(0).layout[1]);
        List<ChatSnapLayouts.Suggestion> two = ChatSnapLayouts.suggested(
                layouts, "hand", Arrays.asList("front", "behind", "further"));
        assertEquals(2, two.size());
        assertArrayEquals(new String[] {"hand", "front", "behind"},
                two.get(1).windows);
        assertEquals(ChatWindow.ScreenFill.BOTTOM_RIGHT, two.get(1).layout[2]);
        // A screen too short for the quarters is suggested the halves.
        assertEquals(1, ChatSnapLayouts.suggested(ChatSnapLayouts.offered(
                null, 427, 180), "hand", Arrays.asList("front", "behind"))
                .size());
    }

    /**
     * A suggestion answers the pointer as one: anywhere on it is the zone
     * the window in hand takes, and taking it sends the others to their
     * zones. The arrows stand on it there, never on a zone of another
     * window.
     */
    @Test
    public void aSuggestionAnswersAsOneAndSendsItsWindowsAlong() {
        List<ChatWindow.ScreenFill[]> layouts = ChatSnapLayouts.offered(null,
                960, 540);
        ChatSnapLayouts.Panel panel = ChatSnapLayouts.lay(
                ChatSnapLayouts.suggested(layouts, "hand",
                        Arrays.asList("front", "behind")),
                layouts, 8, 100.0D, 2.0D, 960, 540);
        assertEquals(424.0D, panel.box.width, EPSILON);
        // The first suggestion's small screen at (106, 8): the left half
        // is the window in hand's, the right half the window in front's.
        assertEquals(0, panel.zoneAt(110.0D, 12.0D));
        assertEquals(0, panel.zoneAt(140.0D, 12.0D));
        assertEquals(ChatWindow.ScreenFill.LEFT, panel.fillOf(0));
        Map<String, ChatWindow.ScreenFill> halves =
                new HashMap<String, ChatWindow.ScreenFill>();
        halves.put("front", ChatWindow.ScreenFill.RIGHT);
        assertEquals(halves, panel.companionsOf(0));
        // The second, one small screen on at (158, 8): zones 2 to 4.
        assertEquals(2, panel.zoneAt(190.0D, 25.0D));
        Map<String, ChatWindow.ScreenFill> stacked =
                new HashMap<String, ChatWindow.ScreenFill>();
        stacked.put("front", ChatWindow.ScreenFill.TOP_RIGHT);
        stacked.put("behind", ChatWindow.ScreenFill.BOTTOM_RIGHT);
        assertEquals(stacked, panel.companionsOf(2));
        // The plain layouts follow, and take the window alone.
        assertEquals(6, panel.zoneAt(245.0D, 12.0D));
        assertTrue(panel.companionsOf(6).isEmpty());
        assertEquals(2, panel.step(0, ChatSnapKeys.Direction.RIGHT));
    }

    /**
     * A bar on a screen of 960 by 540 brought all the way down: the
     * pointer holds under its peek until the peek comes down to eight,
     * then touches it at seven, on its padding above the first zone.
     */
    private static ChatSnapLayouts.Bar revealed() {
        ChatSnapLayouts.Bar bar = new ChatSnapLayouts.Bar();
        assertNull(bar.follow(null, null, 330.0D, 9.0D, 960, 540));
        assertNull(bar.follow(null, null, 330.0D, 9.0D, 960, 540));
        assertEquals(ChatSnapLayouts.Bar.Stage.PEEKING, bar.stage());
        assertEquals(ChatWindow.ScreenFill.NONE,
                bar.follow(null, null, 330.0D, 7.0D, 960, 540));
        assertEquals(ChatSnapLayouts.Bar.Stage.REVEALED, bar.stage());
        return bar;
    }

    /** How deep the bar of a 960 by 540 screen peeks for a pointer at ({@code x}, {@code y}). */
    private static double peekDepth(double x, double y) {
        return ChatSnapLayouts.Bar.peekDepth(x, y, 320.0D, 640.0D, 65.0D);
    }

    private static void assertBox(double left, double top, double width,
                                  double height, LostTalesUiHitBox box) {
        assertEquals(Arrays.asList(left, top, width, height),
                Arrays.asList(box.left, box.top, box.width, box.height));
    }
}
