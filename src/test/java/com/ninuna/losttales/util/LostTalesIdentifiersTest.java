package com.ninuna.losttales.util;

import java.util.Locale;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LostTalesIdentifiersTest {

    @Test
    public void anIdentifierIsTrimmedAndLowerCased() {
        assertEquals("high_elf", LostTalesIdentifiers.normalize("  High_Elf "));
        assertEquals("", LostTalesIdentifiers.normalize(null));
        assertEquals("", LostTalesIdentifiers.normalize("   "));
    }

    @Test
    public void lowerCasingIgnoresTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals("dwarf_i", LostTalesIdentifiers.normalize("DWARF_I"));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
