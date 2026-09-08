package com.ninuna.losttales.client.gui;

import net.minecraft.client.gui.GuiButton;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MainMenuButtonLayoutTest {
    @Test
    public void standardMenuHasEqualGapsAndCharacterSpansFinalRows() {
        GuiButton single = button(1, 100, 80, 200);
        GuiButton multi = button(2, 100, 104, 200);
        GuiButton realms = button(14, 100, 128, 98);
        GuiButton mods = button(6, 202, 128, 98);
        GuiButton options = button(0, 100, 164, 98);
        GuiButton quit = button(4, 202, 164, 98);
        GuiButton language = new LostTalesLanguageButton(5, 76, 164, 20, 21, "");
        List<GuiButton> buttons = Arrays.asList(single, multi, realms, mods,
                options, quit, language);
        // Reinitializing an already styled list must not move it again.
        for (int pass = 0; pass < 2; pass++) {
            MainMenuButtonLayout.arrange(buttons);
            assertEquals(80, single.yPosition);
            assertEquals(100, single.xPosition);
            assertEquals(4, multi.yPosition - single.yPosition - single.height);
            assertEquals(4, realms.yPosition - multi.yPosition - multi.height);
            assertEquals(4, options.yPosition - realms.yPosition - realms.height);
            assertEquals(4, mods.xPosition - realms.xPosition - realms.width);
            assertEquals(4, quit.xPosition - options.xPosition - options.width);
            assertEquals(4, options.xPosition - language.xPosition - language.width);
            assertEquals(options.yPosition, language.yPosition);
            assertEquals(options.yPosition, quit.yPosition);
            for (GuiButton button : buttons) {
                assertEquals(21, button.height);
            }
            CharacterMenuButtonPlacement character = CharacterMenuButtonPlacement.beside(
                    400, single.xPosition, single.xPosition + single.width,
                    single.yPosition, multi.yPosition + multi.height);
            assertNotNull(character);
            assertEquals(46, character.getHeight());
            assertEquals(multi.yPosition + multi.height,
                    character.getY() + character.getHeight());
            assertEquals(4, character.getX() - single.xPosition - single.width);
        }
    }

    @Test
    public void demoMenuDoesNotLeaveAnEmptyRealmsRow() {
        GuiButton play = button(11, 100, 80, 200);
        GuiButton reset = button(12, 100, 104, 200);
        GuiButton options = button(0, 100, 164, 98);
        GuiButton quit = button(4, 202, 164, 98);
        reset.enabled = false;
        MainMenuButtonLayout.arrange(Arrays.asList(play, reset, options, quit));
        assertEquals(4, options.yPosition - reset.yPosition - reset.height);
        assertEquals(130, options.yPosition);
        assertEquals(false, reset.enabled);
    }

    private static GuiButton button(int id, int x, int y, int width) {
        return new LostTalesButton(id, x, y, width, 21, "Button");
    }
}
