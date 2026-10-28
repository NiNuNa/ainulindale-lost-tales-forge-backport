package com.ninuna.losttales.gui.hud;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderer;
import com.ninuna.losttales.gui.hud.fellowship.FellowshipHudLayout;
import com.ninuna.losttales.gui.hud.layout.HudLayoutFiles;
import com.ninuna.losttales.gui.hud.loot.LostTalesQuickLootHudRenderer;
import com.ninuna.losttales.gui.hud.quest.LostTalesQuestTrackerRenderer;
import com.ninuna.losttales.progression.ProgressionFiles;
import java.util.Arrays;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The bundled layouts' numbers (MAP-11), each a share of the room a
 * panel's box can move in (MAP-10), keep every panel where the layouts
 * stood when their numbers were shares of the screen, on the reference
 * screen: 1920 by 1080 at the automatic GUI scale, 480 by 270.
 */
public final class HudLayoutPlacesTest {
    private static final String[] LAYOUTS = {"default", "lotr-safe", "compact", "minimal"};
    private static final String[] PANELS = {
            "compass", "fellowship", "quick_loot", "quest", "notifications"};
    private static final int SCREEN_WIDTH = 480;
    private static final int SCREEN_HEIGHT = 270;

    /**
     * Each panel's box at the shipped settings: the compass with its
     * distance label's line, the fellowship panel's three rows, quick loot's
     * five rows and key hint, the tracker's two quests of one line, the
     * notification slot.
     */
    private static final int[][] BOXES = {
            {257, 55}, {166, 95}, {280, 162}, {238, 73}, {272, 72}};

    /**
     * Where each panel's top left stood on the reference screen while its
     * numbers were shares of the screen ({x, y}): the compass's distance
     * label line, the drawn panel's corner for the rest.
     */
    private static final int[][][] PLACES_BEFORE = {
            {{112, 5}, {10, 49}, {200, 86}, {10, 103}, {104, 69}},
            {{112, 32}, {10, 76}, {200, 92}, {10, 140}, {104, 63}},
            {{112, 19}, {5, 70}, {200, 100}, {5, 154}, {104, 59}},
            {{112, 11}, {5, 49}, {200, 108}, {5, 189}, {104, 55}}};

    private static Map<String, double[][]> bundled() {
        return HudLayoutFiles.placesById(DataFiles.readBundled(ProgressionFiles.CLASSPATH,
                HudLayoutFiles.INDEX, HudLayoutFiles.LIST, HudLayoutFiles.KIND).values);
    }

    @Test
    public void theBoxesAreThePanelsShippedSizes() {
        assertEquals(BOXES[0][0], LostTalesCompassHudRenderer.getPlacementWidth());
        assertEquals(BOXES[0][1], LostTalesCompassHudRenderer.getPlacementHeight());
        assertEquals(BOXES[1][0], FellowshipHudLayout.PANEL_WIDTH);
        assertEquals(BOXES[1][1], FellowshipHudLayout.height(
                FellowshipHudLayout.BOX_ROWS));
        assertEquals(BOXES[2][0], LostTalesQuickLootHudRenderer.getPlacementWidth());
        assertEquals(BOXES[2][1], LostTalesQuickLootHudRenderer.getPlacementHeight());
        assertEquals(BOXES[3][0], LostTalesQuestTrackerRenderer.getTrackerPlacementWidth());
        assertEquals(BOXES[3][1], LostTalesQuestTrackerRenderer.getTrackerPlacementHeight());
        assertEquals(BOXES[4][0], LostTalesNotificationHud.WIDTH);
        assertEquals(BOXES[4][1], LostTalesNotificationHud.HEIGHT);
    }

    @Test
    public void theLayoutsPlaceThePanelsTheClientFileKeeps() {
        assertEquals(Arrays.asList(PANELS), LostTalesConfig.hudElements());
    }

    @Test
    public void everyNumberIsAWholeShare() {
        for (double[][] places : bundled().values()) {
            for (double[] place : places) {
                for (double number : place) {
                    assertEquals(Math.rint(number), number, 0.0D);
                    assertTrue(number >= 0.0D && number <= 100.0D);
                }
            }
        }
    }

    @Test
    public void everyPanelStaysWithinAPixelOfWhereItStood() {
        Map<String, double[][]> bundled = bundled();
        for (int layout = 0; layout < LAYOUTS.length; layout++) {
            double[][] places = bundled.get(LAYOUTS[layout]);
            for (int panel = 0; panel < PANELS.length; panel++) {
                HudPlacementLayout.Bounds box = HudPlacementLayout.calculate(
                        SCREEN_WIDTH, SCREEN_HEIGHT, BOXES[panel][0],
                        BOXES[panel][1], places[panel][0], places[panel][1]);
                int[] before = PLACES_BEFORE[layout][panel];
                String what = LAYOUTS[layout] + " " + PANELS[panel];
                assertTrue(what + " across: " + box.x + " for " + before[0],
                        Math.abs(box.x - before[0]) <= 1);
                assertTrue(what + " down: " + box.y + " for " + before[1],
                        Math.abs(box.y - before[1]) <= 1);
            }
        }
    }
}
