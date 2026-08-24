package com.ninuna.losttales.faction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The demonym table is language data, so the test reads the shipped
 * lang file rather than a Minecraft runtime: it proves the keys the
 * resolver builds are the keys the file carries.
 */
public final class FactionDemonymsTest {

    @Test
    public void keysFollowTheFactionCodeName() {
        assertEquals("losttales.faction.demonym.LOTHLORIEN",
                FactionDemonyms.keyFor("lotr:lothlorien"));
        assertEquals("losttales.faction.demonym.NEAR_HARAD",
                FactionDemonyms.keyFor("  LOTR:Near_Harad  "));
        assertEquals("", FactionDemonyms.keyFor("lothlorien"));
        assertEquals("", FactionDemonyms.keyFor("lotr:"));
        assertEquals("", FactionDemonyms.keyFor(null));
    }

    /** Without a loaded language every faction keeps its own name. */
    @Test
    public void anUntranslatedKeyFallsBackToTheFactionName() {
        assertEquals("Lothlórien",
                FactionDemonyms.of("lotr:lothlorien", "Lothlórien"));
        assertEquals("Gondor", FactionDemonyms.of("lotr:gondor", "  Gondor "));
        assertEquals("Gondor", FactionDemonyms.of("nonsense", "Gondor"));
        assertEquals("", FactionDemonyms.of("lotr:gondor", null));
    }

    @Test
    public void theShippedLanguageNamesThePeoplesThatDifferFromTheirRealm() {
        Map<String, String> lang = readLang();
        assertEquals("Galadhrim", lang.get(
                FactionDemonyms.keyFor("lotr:lothlorien")));
        assertEquals("Rohirrim", lang.get(
                FactionDemonyms.keyFor("lotr:rohan")));
        assertEquals("Dunlending", lang.get(
                FactionDemonyms.keyFor("lotr:dunland")));
        assertEquals("Ithildhrim", lang.get(
                FactionDemonyms.keyFor("lotr:moon_elves")));
        // A realm whose name is already the adjective needs no entry.
        assertTrue(!lang.containsKey(FactionDemonyms.keyFor("lotr:gondor")));
        assertTrue(!lang.containsKey(FactionDemonyms.keyFor("lotr:mordor")));
    }

    /** Every demonym is one plain line of text, none of it left empty. */
    @Test
    public void everyDemonymIsUsableAsAnAdjective() {
        Map<String, String> lang = readLang();
        int seen = 0;
        for (Map.Entry<String, String> entry : lang.entrySet()) {
            if (!entry.getKey().startsWith(
                    "losttales.faction.demonym.")) {
                continue;
            }
            seen++;
            String value = entry.getValue();
            assertTrue(entry.getKey() + " is blank", value.trim().length() > 0);
            assertEquals(entry.getKey() + " is padded", value.trim(), value);
            assertTrue(entry.getKey() + " carries formatting",
                    value.indexOf('§') < 0);
        }
        assertTrue("no demonyms are shipped", seen > 0);
    }

    private static Map<String, String> readLang() {
        Map<String, String> entries = new HashMap<String, String>();
        InputStream stream = FactionDemonymsTest.class.getResourceAsStream(
                "/assets/losttales/lang/en_US.lang");
        assertTrue("en_US.lang is not on the test classpath", stream != null);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    stream, Charset.forName("UTF-8")));
            String line;
            while ((line = reader.readLine()) != null) {
                int equals = line.indexOf('=');
                if (line.startsWith("#") || equals <= 0) {
                    continue;
                }
                entries.put(line.substring(0, equals),
                        line.substring(equals + 1));
            }
        } catch (IOException failure) {
            throw new AssertionError(failure);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                } else {
                    stream.close();
                }
            } catch (IOException ignored) {
                // Nothing useful to do while closing a test resource.
            }
        }
        return entries;
    }
}
