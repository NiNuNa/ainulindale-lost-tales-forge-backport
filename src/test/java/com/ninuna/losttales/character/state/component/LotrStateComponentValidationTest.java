package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Structural validation of the six LOTR character-state components.
 *
 * <p>Every case is decided by the component envelope or by the adapter's own
 * shape check, so none of them reaches LOTR player data. The envelope checks
 * are repeated per component on purpose: each component carries its own copy
 * of the version and key test, and a slip in one of them would let a snapshot
 * from another build through unnoticed.</p>
 */
public final class LotrStateComponentValidationTest {

    private final LotrProgressionStateComponent progression =
            new LotrProgressionStateComponent();
    private final LotrQuestStateComponent quests =
            new LotrQuestStateComponent();
    private final LotrCharacterDetailsStateComponent details =
            new LotrCharacterDetailsStateComponent();
    private final LotrCustomWaypointStateComponent customWaypoints =
            new LotrCustomWaypointStateComponent();
    private final LotrWaypointUseStateComponent waypointUses =
            new LotrWaypointUseStateComponent();
    private final LotrFastTravelRegionStateComponent regions =
            new LotrFastTravelRegionStateComponent();

    /** Component ids key the stored snapshot, so they are save surface. */
    @Test
    public void componentIdentifiersAreStable() {
        assertEquals("lotr_progression", this.progression.getId());
        assertEquals("lotr_quests", this.quests.getId());
        assertEquals("lotr_character_details", this.details.getId());
        assertEquals("lotr_custom_waypoints", this.customWaypoints.getId());
        assertEquals("lotr_waypoint_uses", this.waypointUses.getId());
        assertEquals("lotr_fast_travel_regions", this.regions.getId());
    }

    @Test
    public void progressionEnvelopeFailsClosed() {
        assertEnvelopeFailsClosed(this.progression, "Progression");
    }

    @Test
    public void questEnvelopeFailsClosed() {
        assertEnvelopeFailsClosed(this.quests, "Quests");
    }

    @Test
    public void characterDetailsEnvelopeFailsClosed() {
        assertEnvelopeFailsClosed(this.details, "Details");
    }

    @Test
    public void customWaypointEnvelopeFailsClosed() {
        assertEnvelopeFailsClosed(this.customWaypoints, "Waypoints");
    }

    @Test
    public void waypointUseEnvelopeFailsClosed() {
        assertEnvelopeFailsClosed(this.waypointUses, "Uses");
    }

    @Test
    public void fastTravelRegionEnvelopeFailsClosed() {
        assertEnvelopeFailsClosed(this.regions, "Regions");
    }

    /** The allowlist is required, not optional: an empty slice is not a state. */
    @Test
    public void emptyProgressionPayloadIsRejected() {
        assertRejected(this.progression,
                envelope("Progression", new NBTTagCompound()));
    }

    @Test
    public void unknownProgressionFieldIsRejected() {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger("Unexpected", 1);

        assertRejected(this.progression, envelope("Progression", payload));
    }

    @Test
    public void emptyQuestPayloadIsRejected() {
        assertRejected(this.quests, envelope("Quests", new NBTTagCompound()));
    }

    @Test
    public void negativeQuestCounterIsRejected() {
        NBTTagCompound payload = questPayload();
        payload.setInteger("MQCompleteCount", -1);

        assertRejected(this.quests, envelope("Quests", payload));
    }

    @Test
    public void duplicatePlacedBountyFactionIsRejected() {
        NBTTagList placed = new NBTTagList();
        placed.appendTag(new NBTTagString("hobbit"));
        placed.appendTag(new NBTTagString("hobbit"));
        NBTTagCompound payload = questPayload();
        payload.setTag("BountiesPlaced", placed);

        assertRejected(this.quests, envelope("Quests", payload));
    }

    @Test
    public void nonCanonicalPouchFlagIsRejected() {
        NBTTagCompound questData = new NBTTagCompound();
        questData.setByte("Pouches", (byte) 2);
        NBTTagCompound payload = questPayload();
        payload.setTag("QuestData", questData);

        assertRejected(this.quests, envelope("Quests", payload));
    }

    /** A list of lists survives the shape check but not the expanded tree. */
    @Test
    public void nestedQuestListIsRejected() {
        NBTTagList nested = new NBTTagList();
        nested.appendTag(new NBTTagList());
        NBTTagCompound quest = new NBTTagCompound();
        quest.setTag("Nested", nested);
        NBTTagList miniQuests = new NBTTagList();
        miniQuests.appendTag(quest);
        NBTTagCompound payload = questPayload();
        payload.setTag("MiniQuests", miniQuests);

        assertRejected(this.quests, envelope("Quests", payload));
    }

    @Test
    public void emptyCharacterDetailsPayloadIsRejected() {
        assertRejected(this.details, envelope("Details", new NBTTagCompound()));
    }

    @Test
    public void negativeAlcoholToleranceIsRejected() {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger("Alcohol", -1);

        assertRejected(this.details, envelope("Details", payload));
    }

    @Test
    public void unknownCharacterDetailsFieldIsRejected() {
        NBTTagCompound payload = detailsPayload();
        payload.setInteger("Unexpected", 1);

        assertRejected(this.details, envelope("Details", payload));
    }

    @Test
    public void shieldIdentifierMustBeAString() {
        NBTTagCompound payload = detailsPayload();
        payload.setInteger("Shield", 5);

        assertRejected(this.details, envelope("Details", payload));
    }

    /** All four last-death fields travel together or not at all. */
    @Test
    public void partialLastDeathMarkerIsRejected() {
        NBTTagCompound payload = detailsPayload();
        payload.setInteger("DeathX", 100);

        assertRejected(this.details, envelope("Details", payload));
    }

    @Test
    public void lastDeathMarkerCoordinateBoundsAreEnforced() {
        assertRejected(this.details,
                envelope("Details", deathMarker(30000001, 64, 0)));
        assertRejected(this.details,
                envelope("Details", deathMarker(0, 4097, 0)));
        assertRejected(this.details,
                envelope("Details", deathMarker(0, 64, -30000001)));
    }

    @Test
    public void emptyCustomWaypointPayloadIsRejected() {
        assertRejected(this.customWaypoints,
                envelope("Waypoints", new NBTTagCompound()));
    }

    /** A character with no custom waypoints is legitimate and must validate. */
    @Test
    public void emptyCustomWaypointStateIsAccepted()
            throws CharacterStateValidationException {
        this.customWaypoints.validate(
                envelope("Waypoints", customWaypointPayload(20000)));
    }

    @Test
    public void nextCustomWaypointIdBelowTheFirstIdIsRejected() {
        assertRejected(this.customWaypoints,
                envelope("Waypoints", customWaypointPayload(19999)));
    }

    /** The next id has to clear every id already spent, or a save reuses one. */
    @Test
    public void nextCustomWaypointIdMustClearRecordedUses()
            throws CharacterStateValidationException {
        NBTTagCompound colliding = customWaypointPayload(20000);
        colliding.setTag("CWPUses", useCounts(20005, 1));
        assertRejected(this.customWaypoints, envelope("Waypoints", colliding));

        NBTTagCompound accepted = customWaypointPayload(20006);
        accepted.setTag("CWPUses", useCounts(20005, 1));
        this.customWaypoints.validate(envelope("Waypoints", accepted));
    }

    @Test
    public void negativeCustomWaypointUseCountIsRejected() {
        NBTTagCompound payload = customWaypointPayload(20002);
        payload.setTag("CWPUses", useCounts(20001, -1));

        assertRejected(this.customWaypoints, envelope("Waypoints", payload));
    }

    @Test
    public void customWaypointUseIdBelowOneIsRejected() {
        NBTTagCompound payload = customWaypointPayload(20000);
        payload.setTag("CWPUses", useCounts(0, 1));

        assertRejected(this.customWaypoints, envelope("Waypoints", payload));
    }

    @Test
    public void emptyWaypointUsePayloadIsRejected() {
        assertRejected(this.waypointUses, envelope("Uses", new NBTTagCompound()));
    }

    @Test
    public void emptyWaypointUseStateIsAccepted()
            throws CharacterStateValidationException {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("WPUses", new NBTTagList());

        this.waypointUses.validate(envelope("Uses", payload));
    }

    @Test
    public void unknownWaypointUseFieldIsRejected() {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("WPUses", new NBTTagList());
        payload.setInteger("Unexpected", 1);

        assertRejected(this.waypointUses, envelope("Uses", payload));
    }

    @Test
    public void waypointUseEntryWithoutACountIsRejected() {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("WPName", "SHIRE");
        NBTTagList uses = new NBTTagList();
        uses.appendTag(entry);
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("WPUses", uses);

        assertRejected(this.waypointUses, envelope("Uses", payload));
    }

    @Test
    public void waypointUseListOfStringsIsRejected() {
        NBTTagList uses = new NBTTagList();
        uses.appendTag(new NBTTagString("SHIRE"));
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("WPUses", uses);

        assertRejected(this.waypointUses, envelope("Uses", payload));
    }

    @Test
    public void emptyFastTravelRegionPayloadIsRejected() {
        assertRejected(this.regions, envelope("Regions", new NBTTagCompound()));
    }

    /** A character who has visited nothing yet still has a valid region slice. */
    @Test
    public void emptyFastTravelRegionStateIsAccepted()
            throws CharacterStateValidationException {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("UnlockedFTRegions", new NBTTagList());

        this.regions.validate(envelope("Regions", payload));
    }

    @Test
    public void unknownFastTravelRegionFieldIsRejected() {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("UnlockedFTRegions", new NBTTagList());
        payload.setInteger("Unexpected", 1);

        assertRejected(this.regions, envelope("Regions", payload));
    }

    @Test
    public void fastTravelRegionEntryWithAnExtraFieldIsRejected() {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Name", "SHIRE");
        entry.setInteger("Unexpected", 1);

        assertRejected(this.regions, envelope("Regions", regionState(entry)));
    }

    @Test
    public void fastTravelRegionNameMustBeAString() {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("Name", 1);

        assertRejected(this.regions, envelope("Regions", regionState(entry)));
    }

    @Test
    public void fastTravelRegionListOfStringsIsRejected() {
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagString("SHIRE"));
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("UnlockedFTRegions", list);

        assertRejected(this.regions, envelope("Regions", payload));
    }

    private static void assertEnvelopeFailsClosed(
            CharacterStateComponent component, String payloadKey) {
        assertRejected(component, null);
        assertRejected(component, new NBTTagCompound());

        NBTTagCompound versionOnly = new NBTTagCompound();
        versionOnly.setInteger("Version", 1);
        assertRejected(component, versionOnly);

        NBTTagCompound newerVersion = envelope(payloadKey, new NBTTagCompound());
        newerVersion.setInteger("Version", 2);
        assertRejected(component, newerVersion);

        NBTTagCompound wrongPayloadType = new NBTTagCompound();
        wrongPayloadType.setInteger("Version", 1);
        wrongPayloadType.setString(payloadKey, "payload");
        assertRejected(component, wrongPayloadType);

        NBTTagCompound extraField = envelope(payloadKey, new NBTTagCompound());
        extraField.setInteger("Unexpected", 1);
        assertRejected(component, extraField);
    }

    /**
     * A well-formed state at the version the component writes is
     * accepted, and the same state one version on is not. Without the
     * accepted half, bumping a component's version would leave every
     * refusal here passing on a later rule and say nothing about the
     * version at all.
     */
    @Test
    public void aQuestStateIsAcceptedAtItsOwnVersionAndRefusedAtTheNext()
            throws Exception {
        NBTTagCompound state = envelope("Quests", questPayload());
        this.quests.validate(state);

        NBTTagCompound newer = envelope("Quests", questPayload());
        newer.setInteger("Version", 2);
        assertRejectedBecause(this.quests, newer, "version 2");
    }

    @Test
    public void aCharacterDetailsStateIsAcceptedAtItsOwnVersionAndRefusedAtTheNext()
            throws Exception {
        NBTTagCompound state = envelope("Details", detailsPayload());
        this.details.validate(state);

        NBTTagCompound newer = envelope("Details", detailsPayload());
        newer.setInteger("Version", 2);
        assertRejected(this.details, newer);
    }

    /**
     * Refused, and refused for the reason named. An envelope carrying a
     * version this build does not know is refused by the version rule,
     * not by whatever the payload happens to say.
     */
    private static void assertRejectedBecause(
            CharacterStateComponent component, NBTTagCompound state,
            String reason) {
        try {
            component.validate(state);
            fail("Unsupported state was accepted by " + component.getId());
        } catch (CharacterStateValidationException expected) {
            String message = expected.getMessage() == null
                    ? "" : expected.getMessage();
            assertTrue(component.getId() + " refused for \"" + message
                    + "\" rather than because of the " + reason,
                    message.contains(reason));
        }
    }

    private static void assertRejected(
            CharacterStateComponent component, NBTTagCompound state) {
        try {
            component.validate(state);
            fail("Unsupported state was accepted by " + component.getId());
        } catch (CharacterStateValidationException expected) {
            // Refusing the state is the point.
        }
    }

    private static NBTTagCompound envelope(
            String payloadKey, NBTTagCompound payload) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("Version", 1);
        state.setTag(payloadKey, payload);
        return state;
    }

    private static NBTTagCompound questPayload() {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("MiniQuests", new NBTTagList());
        payload.setTag("MiniQuestsCompleted", new NBTTagList());
        payload.setInteger("MQCompleteCount", 0);
        payload.setInteger("MQCompletedBounties", 0);
        payload.setTag("BountiesPlaced", new NBTTagList());
        NBTTagCompound questData = new NBTTagCompound();
        questData.setBoolean("Pouches", false);
        payload.setTag("QuestData", questData);
        return payload;
    }

    private static NBTTagCompound detailsPayload() {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger("Alcohol", 0);
        return payload;
    }

    private static NBTTagCompound deathMarker(int x, int y, int z) {
        NBTTagCompound payload = detailsPayload();
        payload.setInteger("DeathX", x);
        payload.setInteger("DeathY", y);
        payload.setInteger("DeathZ", z);
        payload.setInteger("DeathDim", 0);
        return payload;
    }

    private static NBTTagCompound customWaypointPayload(int nextId) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("CustomWaypoints", new NBTTagList());
        payload.setTag("CWPUses", new NBTTagList());
        payload.setInteger("NextCWPID", nextId);
        return payload;
    }

    private static NBTTagList useCounts(int customId, int count) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("CustomID", customId);
        entry.setInteger("Count", count);
        NBTTagList uses = new NBTTagList();
        uses.appendTag(entry);
        return uses;
    }

    private static NBTTagCompound regionState(NBTTagCompound entry) {
        NBTTagList list = new NBTTagList();
        list.appendTag(entry);
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("UnlockedFTRegions", list);
        return payload;
    }
}
