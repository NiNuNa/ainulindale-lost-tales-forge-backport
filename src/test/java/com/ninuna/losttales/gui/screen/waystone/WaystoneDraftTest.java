package com.ninuna.losttales.gui.screen.waystone;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerEditableSettings;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRelevance;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibility;
import java.util.Arrays;
import java.util.EnumSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The waystone page's draft: what changed lights Save and nothing else
 * does, a newer state keeps the player's edits, what Save sends keeps the
 * exact relevance, and the icon's and colour's picks never lose what the
 * waystone was read with.
 */
public final class WaystoneDraftTest {
    static LostTalesMapMarkerEditableSettings settings(String name,
                                                       boolean discoverable,
                                                       boolean hidden,
                                                       int priority) {
        return settings(name, "camp", "gold", priority,
                LostTalesMapMarkerVisibility.PRIVATE, discoverable, hidden);
    }

    static LostTalesMapMarkerEditableSettings settings(
            String name, String icon, String color, int priority,
            LostTalesMapMarkerVisibility access, boolean discoverable,
            boolean hidden) {
        return new LostTalesMapMarkerEditableSettings(
                name, icon, color, "Waystones", "",
                true, 256.0D, 16.0D, hidden, discoverable, false,
                priority, access);
    }

    private static LostTalesMapMarkerEditableSettings bree() {
        return settings("Bree Gate", true, false, 73);
    }

    @Test
    public void nothingIsChangedUntilTheSettingsAreEdited() {
        WaystoneDraft draft = new WaystoneDraft(bree());
        assertFalse(draft.isChanged());

        draft.setName("Bree West Gate");
        draft.setIcon("quest");
        draft.setCompassRadius(300.0D);
        assertEquals(EnumSet.of(WaystoneDraft.Field.NAME,
                WaystoneDraft.Field.ICON,
                WaystoneDraft.Field.COMPASS_RADIUS), draft.changed());

        draft.setName("Bree Gate");
        draft.setIcon("camp");
        draft.setCompassRadius(256.0D);
        assertFalse("edited back, nothing is left to save",
                draft.isChanged());
    }

    @Test
    public void aWaystoneThatCannotBeFoundIsNeverHidden() {
        WaystoneDraft draft = new WaystoneDraft(
                settings("Hidden Stone", true, true, 0));
        draft.setDiscoverable(false);
        assertFalse(draft.isHidden());
        draft.setHidden(true);
        assertFalse("hidden needs discoverable", draft.isHidden());
        draft.setDiscoverable(true);
        draft.setHidden(true);
        assertTrue(draft.isHidden());
    }

    @Test
    public void aNewerStateKeepsTheEditsAndTakesTheRest() {
        WaystoneDraft draft = new WaystoneDraft(bree());
        draft.setName("Bree West Gate");
        LostTalesMapMarkerEditableSettings newer =
                new LostTalesMapMarkerEditableSettings(
                        "Bree Gate", "camp", "red", "Towns", "",
                        true, 256.0D, 16.0D, false, true, false, 73,
                        LostTalesMapMarkerVisibility.SHARED);
        draft.rebase(bree(), newer);
        assertEquals("the player's edit stays", "Bree West Gate",
                draft.name());
        assertEquals("what the player left follows", "red", draft.color());
        assertEquals("Towns", draft.category());
        assertFalse("shared is not public, as private is not",
                draft.isPublic());
        assertEquals(EnumSet.of(WaystoneDraft.Field.NAME), draft.changed());
    }

    @Test
    public void aSaveAnsweredTakesWhatTheServerKeptOfWhatWasSent() {
        WaystoneDraft draft = new WaystoneDraft(bree());
        draft.setName("  Bree West Gate  ");
        LostTalesMapMarkerEditableSettings sent = draft.toSettings();
        // Edited while the save was on its way: that edit stays.
        draft.setCategory("Gates");
        LostTalesMapMarkerEditableSettings saved = settings(
                "Bree West Gate", true, false, 73);
        draft.rebase(sent, saved);
        assertEquals("the server's trimmed name", "Bree West Gate",
                draft.name());
        assertEquals("Gates", draft.category());
        assertEquals(EnumSet.of(WaystoneDraft.Field.CATEGORY),
                draft.changed());
    }

    @Test
    public void aRebaseNeverLeavesAnUndiscoverableWaystoneHidden() {
        WaystoneDraft draft = new WaystoneDraft(bree());
        draft.setDiscoverable(false);
        draft.rebase(bree(), settings("Bree Gate", true, true, 73));
        assertFalse(draft.isDiscoverable());
        assertFalse(draft.isHidden());
    }

    @Test
    public void aResetLetsEveryEditGo() {
        WaystoneDraft draft = new WaystoneDraft(bree());
        draft.setName("Elsewhere");
        draft.setFastTravel(false);
        draft.reset(settings("Stale Stone", true, false, 0));
        assertFalse(draft.isChanged());
        assertEquals("Stale Stone", draft.name());
    }

    @Test
    public void saveSendsTheEditsAndTheExactRelevance() {
        WaystoneDraft draft = new WaystoneDraft(bree());
        draft.setDiscoveryRadius(24.0D);
        draft.setRequiresRegion(true);
        LostTalesMapMarkerEditableSettings sent = draft.toSettings();
        assertEquals(24.0D, sent.getDiscoveryRadius(), 0.0D);
        assertTrue(sent.requiresRegionUnlock());
        assertEquals("a relevance left alone keeps its exact rank", 73,
                sent.getPriority());
    }

    /** The picks offer the fourteen icons and twelve colours, after the waystone's own where it is none of them. */
    @Test
    public void thePicksKeepTheIconAndColourTheWaystoneWasReadWith() {
        WaystoneDraft listed = new WaystoneDraft(bree());
        assertEquals(Arrays.asList(WaystoneDraft.ICONS), listed.icons());
        assertEquals(Arrays.asList(WaystoneDraft.COLORS), listed.colors());

        WaystoneDraft town = new WaystoneDraft(settings("Bree", "town",
                "#aabbcc", 0, LostTalesMapMarkerVisibility.PRIVATE, true,
                false));
        assertEquals("town", town.icons().get(0));
        assertEquals(WaystoneDraft.ICONS.length + 1, town.icons().size());
        assertEquals("#aabbcc", town.colors().get(0));
        town.setIcon("camp");
        assertTrue(town.isChanged());
        assertEquals("the saved icon stays offered", "town",
                town.icons().get(0));
        town.setIcon("town");
        assertFalse("picked back, nothing changed", town.isChanged());
        assertEquals(1, WaystoneDraft.indexOf(Arrays.asList("a", "Camp"),
                "camp"));
    }

    @Test
    public void relevanceIsPickedByLevelAndKeepsItsRankWhereTheLevelStays() {
        WaystoneDraft draft = new WaystoneDraft(bree());
        assertEquals("73 is nearest very high",
                LostTalesMapMarkerRelevance.VERY_HIGH, draft.relevance());
        draft.setRelevance(LostTalesMapMarkerRelevance.VERY_HIGH);
        assertFalse("the level it reads keeps the exact rank",
                draft.isChanged());
        draft.setRelevance(LostTalesMapMarkerRelevance.LOW);
        assertEquals(LostTalesMapMarkerRelevance.LOW, draft.relevance());
        assertEquals(LostTalesMapMarkerRelevance.LOW.getRank(),
                draft.toSettings().getPriority());
        assertTrue(draft.changed().contains(WaystoneDraft.Field.RELEVANCE));
    }

    /** Access is picked Private or Public; only a manager makes a waystone public (WAY-13, WAY-14). */
    @Test
    public void onlyAManagerPicksPublicForAPrivateWaystone() {
        WaystoneDraft draft = new WaystoneDraft(bree());
        draft.setPublic(true, false);
        assertFalse("a player who does not manage markers stays private",
                draft.isPublic());
        assertFalse(draft.mayBePublic(false));
        assertFalse(draft.isChanged());
        draft.setPublic(true, true);
        assertTrue(draft.isPublic());
        assertEquals(LostTalesMapMarkerVisibility.PUBLIC,
                draft.toSettings().getVisibility());
        draft.setPublic(false, true);
        assertFalse("public goes back to private, never to shared",
                draft.isPublic());
        assertEquals(LostTalesMapMarkerVisibility.PRIVATE,
                draft.toSettings().getVisibility());
    }

    /** An owner may take a public waystone private and pick it public again before saving. */
    @Test
    public void aWaystoneSavedPublicIsPickedBackByItsOwner() {
        WaystoneDraft draft = new WaystoneDraft(settings("Bree Gate", "camp",
                "gold", 73, LostTalesMapMarkerVisibility.PUBLIC, true,
                false));
        assertTrue(draft.isPublic());
        draft.setPublic(false, false);
        assertFalse(draft.isPublic());
        assertTrue(draft.changed().contains(WaystoneDraft.Field.VISIBILITY));
        assertTrue(draft.mayBePublic(false));
        draft.setPublic(true, false);
        assertTrue(draft.isPublic());
        assertFalse(draft.isChanged());
    }

    /** A shared waystone is not public: its draft reads as not changed and sends Private. */
    @Test
    public void aSharedWaystoneIsReadAsNotPublic() {
        WaystoneDraft draft = new WaystoneDraft(settings("Bree Gate", "camp",
                "gold", 73, LostTalesMapMarkerVisibility.SHARED, true,
                false));
        assertFalse(draft.isPublic());
        assertFalse(draft.isChanged());
        assertEquals(LostTalesMapMarkerVisibility.PRIVATE,
                draft.toSettings().getVisibility());
    }
}
