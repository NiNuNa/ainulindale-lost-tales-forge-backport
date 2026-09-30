package com.ninuna.losttales.config;

import java.util.Set;
import java.util.TreeSet;
import net.minecraftforge.common.config.Configuration;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * A save writes only the options listed to be written back, so an
 * option Settings sets but the save leaves out would be lost the next
 * time the file is read. Every client option is written back, and a
 * number's bounds are read from its definition.
 */
public final class LostTalesConfigWriteBackTest {
    @Test
    public void everyClientOptionIsWrittenBack() {
        Configuration written = new Configuration();
        LostTalesConfig.writeCurrentValues(written);
        Set<String> missing = new TreeSet<String>(DefinedClientOptions.keys());
        missing.removeAll(written.getCategory(
                LostTalesConfig.CATEGORY_CLIENT).keySet());
        assertEquals("client options a save leaves out",
                new TreeSet<String>(), missing);
    }

    @Test
    public void aNumbersBoundsAreReadFromItsDefinition() {
        Configuration definitions = new Configuration();
        LostTalesConfig.defineOptions(definitions);
        assertArrayEquals(new double[] {100.0D, 5000.0D},
                LostTalesConfigDefinitions.bounds(definitions,
                        LostTalesConfig.CATEGORY_CLIENT, "chatHistoryLines"),
                0.0D);
        assertArrayEquals(new double[] {0.25D, 4.0D},
                LostTalesConfigDefinitions.bounds(definitions,
                        LostTalesConfig.CATEGORY_CLIENT, "animationSpeed"),
                0.0D);
        double[] bounce = LostTalesConfigDefinitions.bounds(definitions,
                LostTalesConfig.CATEGORY_CLIENT, "chestBounce");
        assertEquals("a float's bounds too", 0.0D, bounce[0], 1.0E-6D);
        assertEquals(1.0D, bounce[1], 1.0E-6D);
        assertNull("an option the definitions do not hold",
                LostTalesConfigDefinitions.bounds(definitions,
                        LostTalesConfig.CATEGORY_CLIENT, "noSuchOption"));
        assertNull(LostTalesConfigDefinitions.bounds(null,
                LostTalesConfig.CATEGORY_CLIENT, "chatHistoryLines"));
    }
}
