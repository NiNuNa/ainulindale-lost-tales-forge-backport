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
        assertEquals(168, options.yPosition);
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

    private static GuiButton button(int id, int x, int y, int width) {
        return new LostTalesButton(id, x, y, width, 20, "Button");
    }
}
