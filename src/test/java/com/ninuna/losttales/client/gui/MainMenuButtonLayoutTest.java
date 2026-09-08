package com.ninuna.losttales.client.gui;

import net.minecraft.client.gui.GuiButton;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MainMenuButtonLayoutTest {
    @Test
    public void mainColumnIsCenteredWithModernVerticalAnchorAtEvenAndOddScreenSizes() {
        for (int screenWidth : new int[] {320, 427, 854, 1920}) {
            for (int screenHeight : new int[] {240, 349, 480, 1080}) {
                GuiButton single = button(1, 100, 80, 200);
                GuiButton multi = button(2, 100, 105, 200);
                GuiButton realms = button(14, 100, 130, 98);
                GuiButton mods = button(6, 202, 130, 98);
                GuiButton options = button(0, 100, 155, 98);
                GuiButton quit = button(4, 202, 155, 98);
                GuiButton language = new LostTalesLanguageButton(5, 76, 155, 20, 20, "");
                GuiButton character = new LostTalesCharacterMenuButton(99, 304, 80,
                        44, null, null, "Character");
                List<GuiButton> buttons = Arrays.asList(single, multi, realms, mods, options, quit,
                        language, character);
                MainMenuButtonLayout.arrange(buttons);
                for (int pass = 0; pass < 2; pass++) {
                    MainMenuButtonLayout.position(buttons, screenWidth, screenHeight);
                    assertTrue(Math.abs(single.xPosition * 2 + single.width - screenWidth) <= 1);
                    assertEquals(screenHeight / 4 + 48, single.yPosition);
                    assertTrue(options.yPosition + options.height <= screenHeight - 12);
                    assertEquals(4, single.xPosition - language.xPosition - language.width);
                    assertEquals(4, character.xPosition - single.xPosition - single.width);
                    assertEquals(4, multi.yPosition - single.yPosition - single.height);
                    assertEquals(multi.yPosition + multi.height,
                            character.yPosition + character.height);
                    assertEquals(options.yPosition, language.yPosition);
                    assertEquals(16, options.yPosition - realms.yPosition - realms.height);
                }
            }
        }
    }

    @Test
    public void centersMenusWithoutACharacterAndIgnoresHiddenControls() {
        GuiButton options = button(0, 100, 164, 98);
        GuiButton quit = button(4, 202, 164, 98);
        GuiButton hidden = button(1, -1000, -1000, 200);
        hidden.visible = false;
        MainMenuButtonLayout.position(Arrays.asList(options, quit, hidden), 854, 480);
        assertEquals(327, options.xPosition);
        // One row deep, centred where the standard column's middle falls.
        assertEquals(168 + MainMenuButtonLayout.baselineShift(20), options.yPosition);
        assertEquals(168 + (MainMenuButtonLayout.BASELINE_COLUMN_HEIGHT - 20) / 2,
                options.yPosition);
        assertEquals(4, quit.xPosition - options.xPosition - options.width);
        assertEquals(-1000, hidden.xPosition);
        assertEquals(-1000, hidden.yPosition);
    }

    @Test
    public void standardMenuSeparatesUtilityRowAndCharacterSpansPlayRows() {
        GuiButton single = button(1, 100, 80, 200);
        GuiButton multi = button(2, 100, 104, 200);
        GuiButton realms = button(14, 100, 128, 98);
        GuiButton mods = button(6, 202, 128, 98);
        GuiButton options = button(0, 100, 164, 98);
        GuiButton quit = button(4, 202, 164, 98);
        GuiButton language = new LostTalesLanguageButton(5, 76, 164, 20, 20, "");
        List<GuiButton> buttons = Arrays.asList(single, multi, realms, mods,
                options, quit, language);
        // Reinitializing an already styled list must not move it again.
        for (int pass = 0; pass < 2; pass++) {
            MainMenuButtonLayout.arrange(buttons);
            assertEquals(80, single.yPosition);
            assertEquals(100, single.xPosition);
            assertEquals(4, multi.yPosition - single.yPosition - single.height);
            assertEquals(4, realms.yPosition - multi.yPosition - multi.height);
            assertEquals(16, options.yPosition - realms.yPosition - realms.height);
            assertEquals(single.xPosition, mods.xPosition);
            assertEquals(4, realms.xPosition - mods.xPosition - mods.width);
            assertEquals(4, quit.xPosition - options.xPosition - options.width);
            assertEquals(4, options.xPosition - language.xPosition - language.width);
            assertEquals(options.yPosition, language.yPosition);
            assertEquals(options.yPosition, quit.yPosition);
            for (GuiButton button : buttons) {
                assertEquals(20, button.height);
            }
            CharacterMenuButtonPlacement character = CharacterMenuButtonPlacement.beside(
                    400, single.xPosition, single.xPosition + single.width,
                    single.yPosition, multi.yPosition + multi.height);
            assertNotNull(character);
            assertEquals(44, character.getHeight());
            assertEquals(multi.yPosition + multi.height,
                    character.getY() + character.getHeight());
            assertEquals(4, character.getX() - single.xPosition - single.width);
        }
    }

    @Test
    public void shortScreenKeepsUtilityRowAboveBottomMargin() {
        GuiButton single = button(1, 100, 80, 200);
        GuiButton multi = button(2, 100, 104, 200);
        GuiButton realms = button(14, 100, 128, 200);
        GuiButton options = button(0, 100, 164, 200);
        List<GuiButton> buttons = Arrays.asList(single, multi, realms, options);
        MainMenuButtonLayout.arrange(buttons);
        MainMenuButtonLayout.position(buttons, 320, 180);
        assertEquals(64, single.yPosition);
        assertEquals(168, options.yPosition + options.height);
        assertEquals(16, options.yPosition - realms.yPosition - realms.height);
    }

    @Test
    public void demoMenuDoesNotLeaveAnEmptyRealmsRow() {
        GuiButton play = button(11, 100, 80, 200);
        GuiButton reset = button(12, 100, 104, 200);
        GuiButton options = button(0, 100, 164, 98);
        GuiButton quit = button(4, 202, 164, 98);
        reset.enabled = false;
        MainMenuButtonLayout.arrange(Arrays.asList(play, reset, options, quit));
        assertEquals(16, options.yPosition - reset.yPosition - reset.height);
        assertEquals(140, options.yPosition);
        assertEquals(false, reset.enabled);
    }

    @Test
    public void menuWithoutACharacterReplacesThePlayColumnAndWidensMods() {
        GuiButton single = button(1, 100, 80, 200);
        GuiButton multi = button(2, 100, 104, 200);
        GuiButton realms = button(14, 202, 128, 98);
        GuiButton mods = button(6, 100, 128, 98);
        GuiButton options = button(0, 100, 164, 98);
        GuiButton quit = button(4, 202, 164, 98);
        List<GuiButton> buttons = Arrays.asList(single, multi, realms, mods, options, quit);
        MainMenuButtonLayout.withholdPlayButtons(buttons);
        MainMenuButtonLayout.arrange(buttons);
        MainMenuButtonLayout.position(buttons, 854, 480);
        int[] frame = MainMenuButtonLayout.replacePlayButtons(buttons);
        assertNotNull(frame);
        assertEquals(false, realms.visible);
        assertEquals(false, single.visible);
        assertEquals(false, multi.visible);
        // The frame is Singleplayer's own row; the rows below close up.
        assertEquals(single.xPosition, frame[0]);
        assertEquals(single.yPosition, frame[1]);
        assertEquals(200, frame[2]);
        assertEquals(20, frame[3]);
        // Mods, alone in its row, takes the column's width under it.
        assertEquals(single.xPosition, mods.xPosition);
        assertEquals(200, mods.width);
        assertEquals(4, mods.yPosition - frame[1] - frame[3]);
        assertEquals(16, options.yPosition - mods.yPosition - mods.height);
        assertTrue(Math.abs(frame[0] * 2 + frame[2] - 854) <= 1);
    }

    @Test
    public void shorterColumnIsCentredWhereTheStandardColumnsMiddleFalls() {
        for (int screenHeight : new int[] {349, 480, 1080}) {
            GuiButton single = button(1, 100, 80, 200);
            GuiButton multi = button(2, 100, 104, 200);
            GuiButton realms = button(14, 202, 128, 98);
            GuiButton mods = button(6, 100, 128, 98);
            GuiButton options = button(0, 100, 164, 98);
            GuiButton quit = button(4, 202, 164, 98);
            List<GuiButton> standard = Arrays.asList(single, multi, realms, mods, options, quit);
            MainMenuButtonLayout.arrange(standard);
            MainMenuButtonLayout.position(standard, 854, screenHeight);
            int standardMiddle = (single.yPosition + quit.yPosition + quit.height) / 2;
            assertEquals(MainMenuButtonLayout.BASELINE_COLUMN_HEIGHT,
                    quit.yPosition + quit.height - single.yPosition);

            GuiButton gatedSingle = button(1, 100, 80, 200);
            GuiButton gatedMulti = button(2, 100, 104, 200);
            GuiButton gatedRealms = button(14, 202, 128, 98);
            GuiButton gatedMods = button(6, 100, 128, 98);
            GuiButton gatedOptions = button(0, 100, 164, 98);
            GuiButton gatedQuit = button(4, 202, 164, 98);
            List<GuiButton> gated = Arrays.asList(gatedSingle, gatedMulti, gatedRealms,
                    gatedMods, gatedOptions, gatedQuit);
            MainMenuButtonLayout.withholdPlayButtons(gated);
            MainMenuButtonLayout.arrange(gated);
            MainMenuButtonLayout.position(gated, 854, screenHeight);
            int gatedMiddle = (gatedSingle.yPosition
                    + gatedQuit.yPosition + gatedQuit.height) / 2;
            assertEquals(standardMiddle, gatedMiddle);
            assertTrue(gatedSingle.yPosition > single.yPosition);
        }
    }

    @Test
    public void menuWithoutPlayButtonsIsLeftAsItIs() {
        GuiButton play = button(11, 100, 80, 200);
        GuiButton options = button(0, 100, 164, 98);
        List<GuiButton> buttons = Arrays.asList(play, options);
        MainMenuButtonLayout.withholdPlayButtons(buttons);
        assertEquals(null, MainMenuButtonLayout.replacePlayButtons(buttons));
        assertEquals(true, play.visible);
    }

    private static GuiButton button(int id, int x, int y, int width) {
        return new LostTalesButton(id, x, y, width, 20, "Button");
    }
}
