package com.ninuna.losttales.gui.screen.waystone;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.Settings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The waystone page's rows, made from its draft (Q10 a): Marker,
 * Location, Rules and Sharing as sections of Settings' own rows; every
 * row greyed with the reason on a waystone the player may not edit; the
 * typed rows Settings' own lines and numbers, kept on the page; and the
 * well's search keeping what holds its words. The lang file is not read
 * here, so every word is its key.
 */
public final class WaystoneRowsTest {
    private static final String LANG = "gui.losttales.waystone.";

    /** The page as the rows see it, set by hand. */
    private static final class FakeHost implements WaystoneRows.Host {
        WaystoneDraft draft = new WaystoneDraft(WaystoneDraftTest.settings(
                "Bree Gate", true, false, 0));
        boolean canEdit = true;
        boolean waiting;
        boolean fellowship;
        String target = "";
        String lore = "";
        boolean stands = true;

        @Override public WaystoneDraft draft() { return this.draft; }
        @Override public boolean canEdit() { return this.canEdit; }
        @Override public boolean isWaiting() { return this.waiting; }
        @Override public String markerId() { return "losttales:player/bree"; }
        @Override public int sharedPlayers() { return 3; }
        @Override public int sharedFellowships() { return 1; }
        @Override public boolean sharesWithFellowship() { return this.fellowship; }
        @Override public String shareTarget() { return this.target; }
        @Override public void setShareTarget(String name) { this.target = name; }
        @Override public String nativeLore() { return this.lore; }
        @Override public List<String> offers(String typed) {
            return Arrays.asList("Nils", "Ninuna");
        }
        @Override public boolean stands() { return this.stands; }
        @Override public MenuWindow.Picture iconPicture() { return null; }
        @Override public int colorRgb(String color) { return 0x123456; }
    }

    private static MenuWindow.Entry row(List<MenuWindow.Entry> rows,
                                        String label) {
        for (MenuWindow.Entry entry : rows) {
            if (!entry.header && entry.label.equals(label)) {
                return entry;
            }
        }
        return null;
    }

    private static List<String> headers(List<MenuWindow.Entry> rows) {
        List<String> headers = new ArrayList<String>();
        for (MenuWindow.Entry entry : rows) {
            if (entry.header) {
                headers.add(entry.label);
            }
        }
        return headers;
    }

    @Test
    public void theFourSectionsStandInTheirOrder() {
        List<MenuWindow.Entry> rows = new WaystoneRows(new FakeHost(), 13)
                .build("");
        assertEquals(Arrays.asList(LANG + "section.marker",
                LANG + "section.location", LANG + "section.rules",
                LANG + "section.sharing"), headers(rows));
    }

    @Test
    public void theRowsReadTheDraft() {
        FakeHost host = new FakeHost();
        List<MenuWindow.Entry> rows = new WaystoneRows(host, 13).build("");
        assertEquals("Bree Gate", row(rows, LANG + "name").value);
        assertEquals("an icon without words reads as it is kept", "camp",
                row(rows, LANG + "icon").value);
        assertEquals("gold", row(rows, LANG + "color").value);
        assertEquals("256", row(rows, LANG + "compass_radius").value);
        assertEquals("16", row(rows, LANG + "radius").value);
        assertEquals(LANG + "relevance.medium",
                row(rows, LANG + "relevance").value);
        assertEquals(LANG + "visibility.private",
                row(rows, LANG + "visibility").value);
        assertEquals("losttales:player/bree",
                row(rows, LANG + "marker_id").value);
        assertEquals("3", row(rows, LANG + "shared.players").value);
        assertEquals("1", row(rows, LANG + "shared.fellowships").value);
        assertTrue("a count only reads", row(rows,
                LANG + "shared.players").passive);
        assertTrue(row(rows, LANG + "name").isTakeable());
        assertTrue(row(rows, LANG + "fast_travel").isTakeable());
    }

    @Test
    public void anEmptyDescriptionShowsTheLoreLotrGivesThePlace() {
        FakeHost host = new FakeHost();
        WaystoneRows rows = new WaystoneRows(host, 13);
        assertEquals("an empty line reads None",
                "gui.losttales.window.settings.value.none",
                row(rows.build(""), LANG + "description").value);
        host.lore = "An old gate of Bree.";
        MenuWindow.Entry description = row(rows.build(""),
                LANG + "description");
        assertSame(rows.description, rows.typedFor(description.id));
        assertEquals("An old gate of Bree.", description.value);
        assertFalse("the lore is shown, not written into the draft",
                host.draft.isChanged());
    }

    @Test
    public void aWaystoneThePlayerMayNotEditIsGreyedSayingWhy() {
        FakeHost host = new FakeHost();
        host.canEdit = false;
        host.target = "Nils";
        List<MenuWindow.Entry> rows = new WaystoneRows(host, 13).build("");
        for (String label : new String[] {"name", "icon", "color",
                "category", "description", "compass_radius", "radius",
                "relevance", "discoverable", "hidden", "region",
                "fast_travel", "visibility", "share_with",
                "share_target.player", "share", "unshare"}) {
            MenuWindow.Entry entry = row(rows, LANG + label);
            assertNotNull(label, entry);
            assertFalse(label, entry.isTakeable());
            assertEquals(label, LANG + "read_only", entry.unavailable);
        }
    }

    @Test
    public void hiddenNeedsDiscoverableAndShareNeedsANameAndNoRequestOnItsWay() {
        FakeHost host = new FakeHost();
        host.draft.setDiscoverable(false);
        List<MenuWindow.Entry> rows = new WaystoneRows(host, 13).build("");
        assertEquals(LANG + "why.hidden", row(rows, LANG + "hidden")
                .unavailable);
        assertEquals(LANG + "why.no_target", row(rows, LANG + "share")
                .unavailable);

        host.target = "Nils";
        rows = new WaystoneRows(host, 13).build("");
        assertTrue(row(rows, LANG + "share").isTakeable());
        assertTrue(row(rows, LANG + "unshare").isTakeable());

        host.waiting = true;
        rows = new WaystoneRows(host, 13).build("");
        assertEquals(LANG + "why.waiting", row(rows, LANG + "unshare")
                .unavailable);
    }

    @Test
    public void theShareFieldIsNamedForWhatItSharesWith() {
        FakeHost host = new FakeHost();
        host.fellowship = true;
        WaystoneRows rows = new WaystoneRows(host, 13);
        List<MenuWindow.Entry> built = rows.build("");
        assertNotNull(row(built, LANG + "share_target.fellowship"));
        assertEquals(LANG + "share_target.fellowship",
                row(built, LANG + "share_with").value);
        assertEquals(LANG + "share_target.fellowship", rows.target.label());
    }

    @Test
    public void theTypedRowsAreSettingsLinesAndNumbersKeptOnThePage() {
        FakeHost host = new FakeHost();
        WaystoneRows rows = new WaystoneRows(host, 13);
        List<MenuWindow.Entry> built = rows.build("");
        MenuWindow.Entry name = row(built, LANG + "name");
        MenuWindow.Entry radius = row(built, LANG + "radius");
        assertSame(rows.name, rows.typedFor(name.id));
        assertSame(rows.discoveryRadius, rows.typedFor(radius.id));
        assertNull("a switch is the page's own row",
                rows.typedFor(row(built, LANG + "region").id));
        assertEquals(Settings.Store.NONE, rows.name.store());
        assertEquals(Settings.Store.NONE, rows.discoveryRadius.store());

        assertTrue(rows.discoveryRadius.move(false, false));
        assertEquals(15.0D, host.draft.discoveryRadius(), 0.0D);
        assertTrue(rows.discoveryRadius.take("1"));
        assertFalse("a discovery radius is one block at the least",
                rows.discoveryRadius.move(false, false));
        assertFalse(rows.discoveryRadius.take("0"));
        assertTrue(rows.compassRadius.take("0"));
        assertEquals(0.0D, host.draft.compassRadius(), 0.0D);

        assertTrue(rows.name.stands());
        host.stands = false;
        assertFalse("a field opened for a waystone the page let go closes",
                rows.name.stands());
    }

    @Test
    public void theSearchKeepsWhatHoldsItsWordsUnderItsSection() {
        List<MenuWindow.Entry> rows = new WaystoneRows(new FakeHost(), 13)
                .build("radius");
        assertEquals(Collections.singletonList(LANG + "section.location"),
                headers(rows));
        assertNotNull(row(rows, LANG + "radius"));
        assertNotNull(row(rows, LANG + "compass_radius"));
        assertNull(row(rows, LANG + "relevance"));

        List<MenuWindow.Entry> section = new WaystoneRows(new FakeHost(), 13)
                .build("section.rules");
        assertNotNull("a section named by the words is kept whole",
                row(section, LANG + "fast_travel"));
        assertTrue(new WaystoneRows(new FakeHost(), 13)
                .build("nothing holds this").isEmpty());
    }
}
