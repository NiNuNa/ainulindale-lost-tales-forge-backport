package com.ninuna.losttales.client.window;

import com.ninuna.losttales.config.LostTalesConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A few-word setting steps forward on a click and back on a right-click,
 * round from either end, and the game's scale and opacity step a tenth
 * at a time, never under a tenth. The search
 * keeps what holds its words under its section's header and its group's
 * name.
 */
public final class SettingsTest {
    @Test
    public void aStepGoesRoundFromEitherEnd() {
        assertEquals(1, Settings.nextIndex(0, 3, false));
        assertEquals(0, Settings.nextIndex(2, 3, false));
        assertEquals(2, Settings.nextIndex(0, 3, true));
        assertEquals(1, Settings.nextIndex(2, 3, true));
        assertEquals("a value the words do not hold steps to the first",
                0, Settings.nextIndex(-1, 3, false));
        assertEquals("or back to the last", 2,
                Settings.nextIndex(-1, 3, true));
    }

    @Test
    public void theSearchKeepsWhatHoldsItsWordsUnderItsHeaderAndGroup() {
        MenuWindow.Entry global = MenuWindow.Entry.group("Global", null, 0);
        MenuWindow.Entry globalMute = new MenuWindow.Entry("a", "Mute");
        MenuWindow.Entry globalHide = new MenuWindow.Entry("b", "Hide");
        MenuWindow.Entry fellowship = MenuWindow.Entry.group("Fellowship", null, 0);
        MenuWindow.Entry fellowshipMute = new MenuWindow.Entry("c", "Mute");
        MenuWindow.Entry fellowshipHide = new MenuWindow.Entry("d", "Hide");
        List<MenuWindow.Entry> members = Arrays.asList(global, globalMute,
                globalHide, fellowship, fellowshipMute, fellowshipHide);

        List<MenuWindow.Entry> all = new ArrayList<MenuWindow.Entry>();
        Settings.section(all, "Channels", members, "");
        assertEquals("nothing typed keeps everything under its header",
                members.size() + 1, all.size());
        assertTrue(all.get(0).header);

        List<MenuWindow.Entry> byGroup = new ArrayList<MenuWindow.Entry>();
        Settings.section(byGroup, "Channels", members, "fellowship");
        assertEquals("a group whose name holds the words is kept whole",
                Arrays.asList(fellowship, fellowshipMute, fellowshipHide),
                byGroup.subList(1, byGroup.size()));

        List<MenuWindow.Entry> byRow = new ArrayList<MenuWindow.Entry>();
        Settings.section(byRow, "Channels", members, "hide");
        assertEquals("a row is kept under its group's name",
                Arrays.asList(global, globalHide, fellowship, fellowshipHide),
                byRow.subList(1, byRow.size()));

        List<MenuWindow.Entry> bySection = new ArrayList<MenuWindow.Entry>();
        Settings.section(bySection, "Channels", members, "chan");
        assertEquals("a section whose name holds the words is kept whole",
                members.size() + 1, bySection.size());

        List<MenuWindow.Entry> none = new ArrayList<MenuWindow.Entry>();
        Settings.section(none, "Channels", members, "nothing here");
        assertTrue("a section with nothing left is left out", none.isEmpty());
    }

    @Test
    public void aShortcutIsFoundByItsKeys() {
        MenuWindow.Entry search = MenuWindow.Entry.passive("Search the window")
                .withKeys(Integer.valueOf(Keyboard.KEY_F));
        List<MenuWindow.Entry> kept = new ArrayList<MenuWindow.Entry>();
        Settings.section(kept, "Shortcuts", Arrays.asList(search), "f");
        assertEquals(2, kept.size());
    }

    /**
     * Blur the World Behind Windows stands greyed while Blur Behind Screens
     * is off, a press saying why and changing nothing (WIN-51).
     */
    @Test
    public void theWindowsBlurWaitsOnBlurBehindScreens() {
        boolean screens = LostTalesConfig.enableGuiBackgroundBlur;
        boolean windows = LostTalesConfig.windowBackgroundBlur;
        try {
            Settings.WindowBlur blur = new Settings.WindowBlur();
            LostTalesConfig.windowBackgroundBlur = true;
            LostTalesConfig.enableGuiBackgroundBlur = false;
            MenuWindow.Entry greyed = blur.row();
            assertFalse(greyed.isTakeable());
            assertTrue("a press says why", greyed.unavailable.length() > 0);
            blur.step(false);
            blur.step(true);
            assertTrue("a press changes nothing",
                    LostTalesConfig.windowBackgroundBlur);

            LostTalesConfig.enableGuiBackgroundBlur = true;
            MenuWindow.Entry open = blur.row();
            assertTrue(open.isTakeable());
            assertEquals("", open.unavailable);
            blur.step(false);
            assertFalse(LostTalesConfig.windowBackgroundBlur);
        } finally {
            LostTalesConfig.enableGuiBackgroundBlur = screens;
            LostTalesConfig.windowBackgroundBlur = windows;
        }
    }

    @Test
    public void aShareReadsAsTheTenthItLandsOn() {
        assertEquals(100, Settings.percentOf(1.0F));
        assertEquals(70, Settings.percentOf(0.73F));
        assertEquals(80, Settings.percentOf(0.76F));
        assertEquals("never under a tenth", 10, Settings.percentOf(0.0F));
        assertEquals(100, Settings.percentOf(1.4F));
    }
}
