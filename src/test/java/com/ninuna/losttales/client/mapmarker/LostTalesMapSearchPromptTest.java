package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class LostTalesMapSearchPromptTest {
    @Test
    public void anEmptyQueryOffersEverything() {
        List<LostTalesMapSearchPrompt.Entry> entries =
                entries("Bree", "Rivendell", "Minas Tirith");

        assertEquals(entries,
                LostTalesMapSearchPrompt.filter(entries, ""));
        assertEquals(entries,
                LostTalesMapSearchPrompt.filter(entries, "   "));
        assertEquals(entries,
                LostTalesMapSearchPrompt.filter(entries, null));
    }

    /** What was typed is what is meant, whatever case it was typed in. */
    @Test
    public void searchingIgnoresCaseAndMatchesAnywhereInTheName() {
        List<LostTalesMapSearchPrompt.Entry> entries =
                entries("Bree", "Rivendell", "Minas Tirith",
                        "Minas Morgul");

        assertEquals(Arrays.asList("Minas Morgul", "Minas Tirith"),
                names(LostTalesMapSearchPrompt.filter(entries, "minas")));
        assertEquals(Arrays.asList("Minas Tirith"),
                names(LostTalesMapSearchPrompt.filter(entries, "TIRITH")));
        assertTrue(LostTalesMapSearchPrompt.filter(
                entries, "moria").isEmpty());
    }

    /**
     * A name that begins with what was typed is what the player meant; one
     * that merely contains it comes after, so pressing Enter takes the
     * obvious answer.
     */
    @Test
    public void namesThatStartWithTheQueryComeFirst() {
        List<LostTalesMapSearchPrompt.Entry> entries =
                entries("East Osgiliath", "Osgiliath", "West Osgiliath");

        assertEquals(
                Arrays.asList("Osgiliath", "East Osgiliath",
                        "West Osgiliath"),
                names(LostTalesMapSearchPrompt.filter(
                        entries, "osgiliath")));
    }

    private static List<String> names(
            List<LostTalesMapSearchPrompt.Entry> entries) {
        ArrayList<String> names = new ArrayList<String>();
        for (LostTalesMapSearchPrompt.Entry entry : entries) {
            names.add(entry.getName());
        }
        return names;
    }

    /** Sorted by name, as {@code open} leaves them. */
    private static List<LostTalesMapSearchPrompt.Entry> entries(
            String... names) {
        ArrayList<String> sorted = new ArrayList<String>(
                Arrays.asList(names));
        java.util.Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        ArrayList<LostTalesMapSearchPrompt.Entry> entries =
                new ArrayList<LostTalesMapSearchPrompt.Entry>();
        for (String name : sorted) {
            entries.add(new LostTalesMapSearchPrompt.Entry(null, name));
        }
        return entries;
    }
}
