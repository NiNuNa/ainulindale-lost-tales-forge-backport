package com.ninuna.losttales.config;

import com.ninuna.losttales.gui.style.LostTalesColors;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * An option that takes one of a few words is defined with its words, so
 * every screen that shows it offers them as a button to step through,
 * and every word is one the option reads as itself; an option that takes
 * any words has none.
 */
public final class ClientConfigChoicesTest {

    private static ConfigCategory definedClientOptions() {
        Configuration definitions = new Configuration();
        LostTalesConfig.defineOptions(definitions);
        return definitions.getCategory(LostTalesConfig.CATEGORY_CLIENT);
    }

    @Test
    public void aFewWordOptionIsDefinedWithItsWords() {
        ConfigCategory client = definedClientOptions();
        assertArrayEquals(new String[] {"LEFT", "CENTRE", "RIGHT"},
                client.get("chatFeedAlignment").getValidValues());
        assertArrayEquals(LostTalesConfig.HUD_PRESET_VALUES,
                client.get("hudPlacementPreset").getValidValues());
        for (String colour : new String[] {"chatBackgroundColor",
                "chatSelectedLineColor", "chatMentionLineColor",
                "chatReplyHighlightColor"}) {
            assertArrayEquals(LostTalesColors.paletteNames(),
                    client.get(colour).getValidValues());
        }
        assertArrayEquals(new String[] {"wide", "slim"},
                client.get("devSkinOverrideBodyType").getValidValues());
        // A sound can be any sound, so it keeps its box.
        String[] anySound = client.get("chatPingSound").getValidValues();
        assertTrue(anySound == null || anySound.length == 0);
    }

    @Test
    public void everyWordIsOneItsOptionReadsAsItself() {
        ConfigCategory client = definedClientOptions();
        for (String alignment : client.get("chatFeedAlignment").getValidValues()) {
            assertEquals(alignment, LostTalesConfig.normalizeFeedAlignment(alignment));
        }
        for (String preset : client.get("hudPlacementPreset").getValidValues()) {
            assertEquals(preset, LostTalesConfig.normalizeHudPreset(preset));
        }
        for (String colour : client.get("chatReplyHighlightColor").getValidValues()) {
            assertTrue(LostTalesColors.isPaletteName(colour));
        }
        String[] selectedMention =
                client.get("chatSelectedMentionColor").getValidValues();
        assertEquals(LostTalesColors.paletteNames().length + 1,
                selectedMention.length);
        for (String colour : selectedMention) {
            assertEquals(colour, LostTalesConfig.automaticOrPaletteName(colour));
        }
    }
}
