package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.StringTranslate;
import org.junit.Before;
import org.junit.Test;

public final class LostTalesMapSearchPromptTest {
    /** The name each test entry was made with. */
    private static final Map<LostTalesMapSearchPrompt.Entry, String> NAMES =
            new IdentityHashMap<LostTalesMapSearchPrompt.Entry, String>();

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

    /** The map's words for a waypoint's kind, as the lang file has them. */
    @Before
    public void readTheKindsInEnglish() {
        StringBuilder lines = new StringBuilder();
        for (String key : Arrays.asList(LostTalesMapCustomWaypoint.PERSONAL_KEY,
                LostTalesMapCustomWaypoint.SHARED_KEY)) {
            lines.append(key).append('=')
                    .append(LostTalesLangFile.english().get(key)).append('\n');
        }
        StringTranslate.inject(new ByteArrayInputStream(
                lines.toString().getBytes(Charset.forName("UTF-8"))));
    }

    /**
     * Your own waypoints and the ones shared with you stand among the
     * places, each after its kind as the map names it (MAP-13).
     */
    @Test
    public void waypointsAreListedWithTheirKind() {
        List<LostTalesMapSearchPrompt.Entry> places =
                LostTalesMapSearchPrompt.places(
                        Collections.<LostTalesMapMarkerData>emptyList(),
                        Arrays.asList(
                                waypoint(3, "Weathertop Camp", false, false),
                                waypoint(7, "Bree Gate", true, false)),
                        false);

        assertEquals(Arrays.asList("Shared: Bree Gate",
                        "Personal: Weathertop Camp"),
                labels(places));
        LostTalesMapMarkerData own = places.get(1).getMarker();
        // The map draws a waypoint under this id; the frame finds it so.
        assertEquals(LostTalesLotrMapMarkerIconOverlay
                .PERSONAL_WAYPOINT_ID_PREFIX + 3, own.getId());
        assertEquals(LostTalesLotrMapMarkerIconOverlay
                .SHARED_WAYPOINT_ID_PREFIX + 7,
                places.get(0).getMarker().getId());
        assertEquals(1200.0D, own.getX(), 0.0D);
        assertEquals(-340.0D, own.getZ(), 0.0D);
    }

    /** Waypoints sort among the places by their own names. */
    @Test
    public void waypointsSortAmongThePlaces() {
        List<LostTalesMapSearchPrompt.Entry> places =
                LostTalesMapSearchPrompt.places(
                        Arrays.asList(place("losttales:bree", "Bree"),
                                place("losttales:rivendell", "Rivendell")),
                        Arrays.asList(waypoint(1, "Old Forest Edge", false,
                                false)),
                        false);

        assertEquals(Arrays.asList("Bree", "Personal: Old Forest Edge",
                "Rivendell"), labels(places));
    }

    /**
     * A shared waypoint the player hid is listed only while LOTR's map
     * shows the hidden ones, as the map draws it; one without a name never.
     */
    @Test
    public void aHiddenSharedWaypointFollowsTheMap() {
        List<LostTalesMapCustomWaypoint> waypoints = Arrays.asList(
                waypoint(1, "Hidden Ford", true, true),
                waypoint(2, "   ", false, false),
                waypoint(3, "Own Ford", false, true));

        assertEquals(Arrays.asList("Personal: Own Ford"), labels(
                LostTalesMapSearchPrompt.places(null, waypoints, false)));
        assertEquals(Arrays.asList("Shared: Hidden Ford",
                        "Personal: Own Ford"), labels(
                LostTalesMapSearchPrompt.places(null, waypoints, true)));
    }

    /**
     * A waypoint is found by its name, which ranks first when typed from
     * its start, and by its kind's words anywhere.
     */
    @Test
    public void aWaypointIsFoundByItsNameAndItsKind() {
        List<LostTalesMapSearchPrompt.Entry> places =
                LostTalesMapSearchPrompt.places(
                        Arrays.asList(place("losttales:personal_rest",
                                "Personal Rest")),
                        Arrays.asList(waypoint(4, "Ford", false, false),
                                waypoint(5, "Persimmon Hill", true, false)),
                        false);

        assertEquals(Arrays.asList("Personal: Ford"), labels(
                LostTalesMapSearchPrompt.filter(places, "ford")));
        // Typed from a name's start first; a kind's words only contain it.
        assertEquals(Arrays.asList("Shared: Persimmon Hill", "Personal Rest",
                        "Personal: Ford"),
                labels(LostTalesMapSearchPrompt.filter(places, "pers")));
        assertEquals(Arrays.asList("Shared: Persimmon Hill"), labels(
                LostTalesMapSearchPrompt.filter(places, "shared")));
    }

    private static List<String> labels(
            List<LostTalesMapSearchPrompt.Entry> entries) {
        ArrayList<String> labels = new ArrayList<String>();
        for (LostTalesMapSearchPrompt.Entry entry : entries) {
            labels.add(entry.getLabel());
        }
        return labels;
    }

    private static LostTalesMapCustomWaypoint waypoint(int id, String name,
                                                       boolean shared,
                                                       boolean hidden) {
        return new LostTalesMapCustomWaypoint(id, name, shared, hidden, 100,
                1200, 70, -340);
    }

    /** A place the map always shows: no discovery, no region. */
    private static LostTalesMapMarkerData place(String id, String name) {
        return new LostTalesMapMarkerData(id, name, "point_of_interest",
                "white", 100, 0.0D, 64.0D, 0.0D, 0.0D, 0.0D);
    }

    private static List<String> names(
            List<LostTalesMapSearchPrompt.Entry> entries) {
        ArrayList<String> names = new ArrayList<String>();
        for (LostTalesMapSearchPrompt.Entry entry : entries) {
            names.add(NAMES.get(entry));
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
            LostTalesMapSearchPrompt.Entry entry =
                    new LostTalesMapSearchPrompt.Entry(null, name);
            NAMES.put(entry, name);
            entries.add(entry);
        }
        return entries;
    }
}
