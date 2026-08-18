package com.ninuna.losttales.client.gui.tooltip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

public final class LostTalesTooltipIconsTest {
    @After
    public void clearTransformerFlag() {
        System.clearProperty(LostTalesTooltipIcons.ACTIVE_PROPERTY);
    }

    @Test
    public void withoutTheTransformerTheHintKeepsItsPlainLabel() {
        System.clearProperty(LostTalesTooltipIcons.ACTIVE_PROPERTY);
        String hint = LostTalesTooltipIcons.key(
                Keyboard.KEY_LSHIFT, "§f[SHIFT]");
        assertEquals("§f[SHIFT]", hint);
        assertFalse(LostTalesTooltipIcons.hasIcon(hint));
    }

    @Test
    public void aMarkedSpanCarriesItsKeyAndKeepsItsLabelReadable() {
        System.setProperty(LostTalesTooltipIcons.ACTIVE_PROPERTY, "true");
        String hint = LostTalesTooltipIcons.key(
                Keyboard.KEY_LCONTROL, "§f[CTRL]");
        assertTrue(LostTalesTooltipIcons.hasIcon(hint));
        assertEquals(LostTalesTooltipIcons.SPAN_START, hint.charAt(0));
        assertEquals(Keyboard.KEY_LCONTROL,
                LostTalesTooltipIcons.decodeKeyCode(hint.charAt(1)));
        assertEquals(LostTalesTooltipIcons.SPAN_END,
                hint.charAt(hint.length() - 1));
        // Anything that draws the line without knowing about the markers
        // still shows the label the icon replaced.
        assertTrue(hint.contains("§f[CTRL]"));
    }

    @Test
    public void everyDrawableKeyCodeSurvivesTheRoundTrip() {
        System.setProperty(LostTalesTooltipIcons.ACTIVE_PROPERTY, "true");
        int[] codes = {
                Keyboard.KEY_LSHIFT, Keyboard.KEY_LCONTROL, Keyboard.KEY_F12,
                Keyboard.KEY_PERIOD, Keyboard.KEY_COMMA, 0, 255,
                // Mouse buttons are encoded as negative codes by LWJGL.
                -100, -99, -256
        };
        for (int index = 0; index < codes.length; index++) {
            String hint = LostTalesTooltipIcons.key(codes[index], "x");
            assertTrue(LostTalesTooltipIcons.hasIcon(hint));
            assertEquals(codes[index],
                    LostTalesTooltipIcons.decodeKeyCode(hint.charAt(1)));
        }
    }

    @Test
    public void codesOutsideTheEncodableRangeStayAsText() {
        System.setProperty(LostTalesTooltipIcons.ACTIVE_PROPERTY, "true");
        assertEquals("x", LostTalesTooltipIcons.key(256, "x"));
        assertEquals("x", LostTalesTooltipIcons.key(-257, "x"));
    }

    @Test
    public void formattingCarriesAcrossAnIconTheWayItCarriesAcrossText() {
        assertEquals("", LostTalesTooltipIcons.carryFormat("", "Hold "));
        assertEquals("§7",
                LostTalesTooltipIcons.carryFormat("", "§7Hold "));
        // A colour clears the styles riding on the previous one.
        assertEquals("§f",
                LostTalesTooltipIcons.carryFormat("§7§o", "§f"));
        // A style rides along with the colour in force.
        assertEquals("§7§o",
                LostTalesTooltipIcons.carryFormat("§7", "§oit"));
        assertEquals("",
                LostTalesTooltipIcons.carryFormat("§7§o", "§r"));
    }
}
