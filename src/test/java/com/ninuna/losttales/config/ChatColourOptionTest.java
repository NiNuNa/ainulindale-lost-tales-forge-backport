package com.ninuna.losttales.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A colour option that may follow another colour holds automatic or a
 * palette entry's name, and reads anything else as automatic.
 */
public final class ChatColourOptionTest {

    @Test
    public void automaticAndPaletteNamesAreKeptAndTheRestIsAutomatic() {
        assertEquals(LostTalesConfig.CHAT_COLOR_AUTOMATIC,
                LostTalesConfig.automaticOrPaletteName(" auto "));
        assertEquals("ORCHID", LostTalesConfig.automaticOrPaletteName("orchid"));
        assertEquals(LostTalesConfig.CHAT_COLOR_AUTOMATIC,
                LostTalesConfig.automaticOrPaletteName("hot pink"));
        assertEquals(LostTalesConfig.CHAT_COLOR_AUTOMATIC,
                LostTalesConfig.automaticOrPaletteName(null));
    }
}
