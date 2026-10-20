package com.ninuna.losttales.config;

import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.gui.hud.layout.HudLayoutFiles;
import com.ninuna.losttales.progression.ProgressionFiles;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The client file's HUD places and the layouts that loaded (MAP-10, MAP-11). */
public final class LostTalesHudPlacementConfigTest {
    private static final String[] PANELS = {"compass", "fellowship", "quickloot",
            "quest", "notifications"};

    private String chosen;
    private double[][] before;

    @Before
    public void remember() {
        this.chosen = LostTalesConfig.hudPlacementPreset;
        this.before = new double[][] {
                {LostTalesConfig.compassHudOffsetX, LostTalesConfig.compassHudOffsetY},
                {LostTalesConfig.fellowshipHudOffsetX, LostTalesConfig.fellowshipHudOffsetY},
                {LostTalesConfig.quickLootHudOffsetX, LostTalesConfig.quickLootHudOffsetY},
                {LostTalesConfig.questHudOffsetX, LostTalesConfig.questHudOffsetY},
                {LostTalesConfig.notificationHudOffsetX,
                        LostTalesConfig.notificationHudOffsetY}};
    }

    @After
    public void restore() {
        LostTalesConfig.forgetHudLayouts();
        for (int panel = 0; panel < PANELS.length; panel++) {
            LostTalesConfig.updateHudOffset(PANELS[panel], this.before[panel][0],
                    this.before[panel][1]);
        }
        LostTalesConfig.hudPlacementPreset = this.chosen;
    }

    private static Map<String, double[][]> bundled() {
        return HudLayoutFiles.placesById(DataFiles.readBundled(ProgressionFiles.CLASSPATH,
                HudLayoutFiles.INDEX, HudLayoutFiles.LIST, HudLayoutFiles.KIND).values);
    }

    @Test
    public void everyPanelStartsInTheDefaultLayout() {
        Configuration definitions = new Configuration();
        LostTalesConfig.defineOptions(definitions);
        ConfigCategory client = definitions.getCategory(LostTalesConfig.CATEGORY_CLIENT);
        assertEquals(LostTalesConfig.HUD_PRESET_DEFAULT,
                client.get("hudPlacementPreset").getString());

        LostTalesConfig.hudPlacementPreset = LostTalesConfig.HUD_PRESET_DEFAULT;
        LostTalesConfig.useHudLayouts(bundled());
        double[][] places = bundled().get(LostTalesConfig.HUD_PRESET_DEFAULT);
        assertArrayEquals(places[0], new double[] {LostTalesConfig.compassHudOffsetX,
                LostTalesConfig.compassHudOffsetY}, 0.0D);
        assertArrayEquals(places[3], new double[] {LostTalesConfig.questHudOffsetX,
                LostTalesConfig.questHudOffsetY}, 0.0D);
        for (int panel = 0; panel < PANELS.length; panel++) {
            assertArrayEquals(PANELS[panel], places[panel],
                    LostTalesConfig.defaultHudOffset(PANELS[panel]), 0.0D);
        }
        assertNull(LostTalesConfig.defaultHudOffset("tracker"));
    }

    @Test
    public void noLayoutIsKnownBeforeTheFilesAreRead() {
        LostTalesConfig.forgetHudLayouts();
        assertNull(LostTalesConfig.defaultHudOffset("compass"));
        assertFalse(LostTalesConfig.isKnownHudPreset("default"));
        assertTrue(LostTalesConfig.hudLayoutIds().isEmpty());
        assertFalse(LostTalesConfig.applyHudPreset("minimal"));
    }

    @Test
    public void theLayoutsListInTheirFilesOrder() {
        LostTalesConfig.useHudLayouts(bundled());
        assertEquals(Arrays.asList("default", "lotr-safe", "compact", "minimal"),
                LostTalesConfig.hudLayoutIds());
        Configuration definitions = new Configuration();
        LostTalesConfig.defineOptions(definitions);
        assertArrayEquals(new String[] {"custom", "default", "lotr-safe", "compact",
                "minimal"}, definitions.getCategory(LostTalesConfig.CATEGORY_CLIENT)
                .get("hudPlacementPreset").getValidValues());
    }

    @Test
    public void aLayoutPlacesEveryPanelAndStaysTheLayout() {
        LostTalesConfig.useHudLayouts(bundled());
        assertTrue(LostTalesConfig.applyHudPreset("minimal"));
        double[][] places = LostTalesConfig.hudLayoutPlaces("minimal");
        assertEquals("minimal", LostTalesConfig.hudPlacementPreset);
        assertEquals(places[0][1], LostTalesConfig.compassHudOffsetY, 0.0D);
        assertEquals(places[3][1], LostTalesConfig.questHudOffsetY, 0.0D);
        assertEquals(places[4][1], LostTalesConfig.notificationHudOffsetY, 0.0D);
        LostTalesConfig.updateHudOffset("quest", 140.0D, -5.0D);
        assertEquals("a panel moved makes the layout the panels' own",
                LostTalesConfig.HUD_PRESET_CUSTOM, LostTalesConfig.hudPlacementPreset);
        assertEquals(100.0D, LostTalesConfig.questHudOffsetX, 0.0D);
        assertEquals(0.0D, LostTalesConfig.questHudOffsetY, 0.0D);
        assertFalse("a layout that did not load is no choice",
                LostTalesConfig.applyHudPreset("low"));
    }

    @Test
    public void aPlayersLayoutIsChosenLikeABundledOne() {
        Map<String, double[][]> layouts = new LinkedHashMap<String, double[][]>(bundled());
        layouts.put("low", new double[][] {{50, 90}, {0, 0}, {100, 100}, {0, 100},
                {50, 50}});
        LostTalesConfig.useHudLayouts(layouts);
        assertTrue(LostTalesConfig.applyHudPreset(" Low "));
        assertEquals("low", LostTalesConfig.hudPlacementPreset);
        assertEquals(90.0D, LostTalesConfig.compassHudOffsetY, 0.0D);
    }

    @Test
    public void theChosenLayoutFollowsItsFileWhenTheFilesAreReadAgain() {
        LostTalesConfig.useHudLayouts(bundled());
        assertTrue(LostTalesConfig.applyHudPreset("compact"));
        Map<String, double[][]> changed = new LinkedHashMap<String, double[][]>(bundled());
        changed.put("compact", new double[][] {{10, 20}, {0, 0}, {100, 100}, {0, 100},
                {50, 50}});
        LostTalesConfig.useHudLayouts(changed);
        assertEquals("compact", LostTalesConfig.hudPlacementPreset);
        assertEquals(10.0D, LostTalesConfig.compassHudOffsetX, 0.0D);
        assertEquals(20.0D, LostTalesConfig.compassHudOffsetY, 0.0D);
    }

    @Test
    public void aChosenLayoutThatNoLongerLoadsReadsAsCustom() {
        Map<String, double[][]> layouts = new LinkedHashMap<String, double[][]>(bundled());
        layouts.put("low", new double[][] {{50, 90}, {0, 0}, {100, 100}, {0, 100},
                {50, 50}});
        LostTalesConfig.useHudLayouts(layouts);
        assertTrue(LostTalesConfig.applyHudPreset("low"));
        LostTalesConfig.useHudLayouts(bundled());
        assertEquals(LostTalesConfig.HUD_PRESET_CUSTOM, LostTalesConfig.hudPlacementPreset);
        assertEquals("the panels stay where they stood", 90.0D,
                LostTalesConfig.compassHudOffsetY, 0.0D);
    }

    @Test
    public void aLayoutOfAnotherShapeIsPassedBy() {
        Map<String, double[][]> layouts = new LinkedHashMap<String, double[][]>(bundled());
        layouts.put("short", new double[][] {{50, 90}});
        layouts.put("gappy", new double[][] {{50, 90}, null, {1, 1}, {1, 1}, {1, 1}});
        LostTalesConfig.useHudLayouts(layouts);
        assertFalse(LostTalesConfig.isKnownHudPreset("short"));
        assertFalse(LostTalesConfig.isKnownHudPreset("gappy"));
        assertTrue(LostTalesConfig.isKnownHudPreset("default"));
    }

    @Test
    public void aLayoutIsReadByItsIdAlone() {
        assertEquals("lotr-safe", LostTalesConfig.normalizeHudPreset(" LOTR-Safe "));
        assertEquals("low_light", LostTalesConfig.normalizeHudPreset("low_light"));
        for (String other : new String[] {"two words", "dots.in.it", "", null}) {
            assertEquals(String.valueOf(other), LostTalesConfig.HUD_PRESET_CUSTOM,
                    LostTalesConfig.normalizeHudPreset(other));
        }
    }

    @Test
    public void everyPlacementScreenElementHasAStableConfigKey() {
        for (String element : PANELS) {
            assertEquals(element, LostTalesConfig.normalizeHudElement(element));
        }
        assertEquals("quest", LostTalesConfig.normalizeHudElement(" Quest "));
        assertEquals("a name no panel goes by is none",
                "", LostTalesConfig.normalizeHudElement("tracker"));
    }
}
