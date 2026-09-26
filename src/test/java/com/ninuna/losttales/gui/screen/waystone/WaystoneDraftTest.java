package com.ninuna.losttales.gui.screen.waystone;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerEditableSettings;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRelevance;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibility;
import java.util.EnumSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The waystone page's draft: what changed lights Save and nothing else
 * does, a newer state keeps the player's edits, and what Save sends keeps
 * the waystone's place, structure and exact relevance.
 */
public final class WaystoneDraftTest {
    static LostTalesMapMarkerEditableSettings settings(String name,
                                                       boolean discoverable,
                                                       boolean hidden,
                                                       int priority) {
        return new LostTalesMapMarkerEditableSettings(
                name, "camp", "gold", "Waystones", "",
                true, 100, 12.0D, 64.0D, -8.0D, 256.0D, 16.0D,
                hidden, discoverable, false, true,
                "losttales:glowstone_house", priority,
                LostTalesMapMarkerVisibility.PRIVATE);
    }

    private static LostTalesMapMarkerEditableSettings bree() {
        return settings("Bree Gate", true, false, 73);
    }

    @Test
    public void nothingIsChangedUntilTheSettingsAreEdited() {
        WaystoneDraft draft = new WaystoneDraft(bree());
        assertFalse(draft.isChanged());

        draft.setName("Bree West Gate");
        draft.stepIcon(false);
        draft.setCompassRadius(300.0D);
        assertEquals(EnumSet.of(WaystoneDraft.Field.NAME,
                WaystoneDraft.Field.ICON,
                WaystoneDraft.Field.COMPASS_RADIUS), draft.changed());

        draft.setName("Bree Gate");
        draft.stepIcon(true);
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
                        true, 100, 12.0D, 64.0D, -8.0D, 256.0D, 16.0D,
                        false, true, false, true,
                        "losttales:glowstone_house", 73,
                        LostTalesMapMarkerVisibility.SHARED);
        draft.rebase(draft.base(), newer);
        assertEquals("the player's edit stays", "Bree West Gate",
                draft.name());
        assertEquals("what the player left follows", "red", draft.color());
        assertEquals("Towns", draft.category());
        assertEquals(LostTalesMapMarkerVisibility.SHARED,
                draft.visibility());
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
    public void saveSendsTheEditsOnTheWaystonesOwnPlace() {
        WaystoneDraft draft = new WaystoneDraft(bree());
        draft.setDiscoveryRadius(24.0D);
        draft.setRequiresRegion(true);
        LostTalesMapMarkerEditableSettings sent = draft.toSettings();
        assertEquals(24.0D, sent.getDiscoveryRadius(), 0.0D);
        assertTrue(sent.requiresRegionUnlock());
        assertEquals(100, sent.getDimensionId());
        assertEquals(12.0D, sent.getX(), 0.0D);
        assertEquals(64.0D, sent.getY(), 0.0D);
        assertEquals(-8.0D, sent.getZ(), 0.0D);
        assertTrue(sent.hasWaystone());
        assertEquals("losttales:glowstone_house",
                sent.getWaystoneStructureType());
        assertEquals("a relevance left alone keeps its exact rank", 73,
                sent.getPriority());
    }

    @Test
    public void theIconsAndColoursStepRoundAndOntoTheListFromOffIt() {
        WaystoneDraft draft = new WaystoneDraft(bree());
        draft.stepIcon(false);
        assertEquals("camp is last; a step goes round", "quest",
                draft.icon());
        draft.stepIcon(true);
        assertEquals("camp", draft.icon());
        assertEquals("tavern is off the list", "quest",
                WaystoneDraft.step(WaystoneDraft.ICONS, "tavern", false));
        assertEquals("black",
                WaystoneDraft.step(WaystoneDraft.COLORS, "#aabbcc", true));
        draft.stepColor(false);
        assertEquals("orange", draft.color());
    }

    @Test
    public void relevanceStepsRoundFromTheNearestLevel() {
        WaystoneDraft draft = new WaystoneDraft(bree());
        assertEquals(LostTalesMapMarkerRelevance.VERY_HIGH,
                draft.relevance());
        draft.stepRelevance(false);
        assertEquals(LostTalesMapMarkerRelevance.VERY_LOW,
                draft.relevance());
        draft.stepRelevance(true);
        assertEquals(LostTalesMapMarkerRelevance.VERY_HIGH,
                draft.relevance());
        assertTrue(draft.changed().contains(WaystoneDraft.Field.RELEVANCE));
    }

    @Test
    public void onlyAPlayerWhoMayMakeItPublicStepsOntoPublic() {
        WaystoneDraft draft = new WaystoneDraft(bree());
        draft.stepVisibility(false, false);
        assertEquals(LostTalesMapMarkerVisibility.SHARED, draft.visibility());
        draft.stepVisibility(false, false);
        assertEquals(LostTalesMapMarkerVisibility.PRIVATE,
                draft.visibility());
        draft.stepVisibility(true, true);
        assertEquals(LostTalesMapMarkerVisibility.PUBLIC, draft.visibility());
        draft.stepVisibility(false, false);
        assertEquals("public, for a player who may not, steps back in",
                LostTalesMapMarkerVisibility.PRIVATE, draft.visibility());
    }
}
