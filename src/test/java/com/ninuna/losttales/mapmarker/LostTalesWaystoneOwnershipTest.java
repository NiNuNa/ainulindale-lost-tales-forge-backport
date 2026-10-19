package com.ninuna.losttales.mapmarker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What the server makes of a waystone request once it may be edited:
 * handing it over (WAY-08), sharing with fellowships of ours (WAY-09) and
 * characters, a Save on a public waystone (WAY-13), Access by its shares
 * (WAY-14) and the discovery radius's bound (WAY-15).
 */
public final class LostTalesWaystoneOwnershipTest {
    private static final UUID ARAGORN = UUID.randomUUID();
    private static final UUID FARAMIR = UUID.randomUUID();

    /* ---- Hand over ---- */

    @Test
    public void aManagerHandsAPlacedWaystoneToAnotherCharacter() {
        LostTalesMapMarkerRecord placed = placed();
        LostTalesWaystoneSettingsService.Outcome outcome =
                LostTalesWaystoneSettingsService.handOver(placed, true, FARAMIR);
        assertNull(outcome.refusal());
        LostTalesMapMarkerRecord handed = outcome.updated();
        assertEquals(FARAMIR, handed.getOwnerCharacterId());
        assertEquals("it keeps whom it is called after", "player:Aragorn",
                handed.getNamedAfter());
        assertTrue(handed.getRevision() > placed.getRevision());
        assertTrue(LostTalesMapMarkerVisibilityPolicy.canView(handed,
                FARAMIR, false, Collections.<UUID>emptySet()));
        assertFalse("the old owner keeps nothing unless shared",
                LostTalesMapMarkerVisibilityPolicy.canView(handed, ARAGORN,
                        false, Collections.<UUID>emptySet()));

        LostTalesMapMarkerRecord sharedFirst = placed
                .withSharedCharacters(Collections.singleton(ARAGORN));
        LostTalesMapMarkerRecord handedShared =
                LostTalesWaystoneSettingsService.handOver(
                        sharedFirst, true, FARAMIR).updated();
        assertTrue("a share the old owner had still holds",
                LostTalesMapMarkerVisibilityPolicy.canView(handedShared,
                        ARAGORN, false, Collections.<UUID>emptySet()));
    }

    @Test
    public void onlyAManagerHandsOverAndOnlyToACharacterFound() {
        assertEquals(LostTalesWaystoneStateReason.HAND_OVER_DENIED,
                LostTalesWaystoneSettingsService.handOver(
                        placed(), false, FARAMIR).refusal());
        assertEquals(LostTalesWaystoneStateReason.CHARACTER_NOT_FOUND,
                LostTalesWaystoneSettingsService.handOver(
                        placed(), true, null).refusal());
        assertEquals("a generated waystone belongs to nobody",
                LostTalesWaystoneStateReason.HAND_OVER_DENIED,
                LostTalesWaystoneSettingsService.handOver(
                        generated(16.0D), true, FARAMIR).refusal());
    }

    /* ---- Sharing ---- */

    @Test
    public void aFellowshipShareStoresOurFellowshipFoundAmongTheSharers() {
        UUID company = UUID.randomUUID();
        UUID rangers = UUID.randomUUID();
        Map<UUID, String> sharers = new LinkedHashMap<UUID, String>();
        sharers.put(rangers, "Rangers of the North");
        sharers.put(company, "The Grey Company");

        LostTalesWaystoneSettingsService.Outcome shared =
                LostTalesWaystoneSettingsService.shareFellowship(placed(),
                        sharers, "  the grey company ");
        assertNull(shared.refusal());
        assertEquals(Collections.singleton(company),
                shared.updated().getSharedFellowshipIds());
        assertEquals(LostTalesMapMarkerVisibility.SHARED,
                shared.updated().getVisibility());
        assertTrue("a member's played character sees it",
                LostTalesMapMarkerVisibilityPolicy.canView(shared.updated(),
                        FARAMIR, false, Collections.singleton(company)));

        assertEquals("a name none of the sharer's fellowships goes by",
                LostTalesWaystoneStateReason.FELLOWSHIP_NOT_FOUND,
                LostTalesWaystoneSettingsService.shareFellowship(placed(),
                        sharers, "The Fellowship").refusal());
        assertEquals(LostTalesWaystoneStateReason.FELLOWSHIP_NOT_FOUND,
                LostTalesWaystoneSettingsService.shareFellowship(placed(),
                        Collections.<UUID, String>emptyMap(),
                        "The Grey Company").refusal());
    }

    @Test
    public void sharingAgainIsRefusedBeforeTheLimitIsCounted() {
        Set<UUID> full = new LinkedHashSet<UUID>();
        UUID company = UUID.randomUUID();
        full.add(company);
        while (full.size() < LostTalesMapMarkerRecord.MAX_SHARED_FELLOWSHIPS) {
            full.add(UUID.randomUUID());
        }
        LostTalesMapMarkerRecord atLimit = placed().withSharedFellowships(full);
        assertEquals("already shared, not at the limit",
                LostTalesWaystoneStateReason.ALREADY_SHARED,
                LostTalesWaystoneSettingsService.shareFellowship(atLimit,
                        Collections.singletonMap(company, "The Grey Company"),
                        "The Grey Company").refusal());
        assertEquals(LostTalesWaystoneStateReason.SHARE_LIMIT,
                LostTalesWaystoneSettingsService.shareFellowship(atLimit,
                        Collections.singletonMap(UUID.randomUUID(), "Rangers"),
                        "Rangers").refusal());

        LostTalesMapMarkerRecord sharedWithFaramir = placed()
                .withSharedCharacters(Collections.singleton(FARAMIR));
        assertEquals(LostTalesWaystoneStateReason.ALREADY_SHARED,
                LostTalesWaystoneSettingsService.shareCharacter(
                        sharedWithFaramir, FARAMIR).refusal());
        assertEquals(LostTalesWaystoneStateReason.CHARACTER_NOT_FOUND,
                LostTalesWaystoneSettingsService.shareCharacter(
                        sharedWithFaramir, null).refusal());
    }

    @Test
    public void unsharingFindsAShareByItsNameAndRefusesOneNotShared() {
        UUID company = UUID.randomUUID();
        LostTalesMapMarkerRecord shared = placed()
                .withSharedFellowships(Collections.singleton(company));
        LostTalesWaystoneSettingsService.Outcome unshared =
                LostTalesWaystoneSettingsService.unshareFellowship(shared,
                        Collections.singletonMap(company, "The Grey Company"),
                        "THE GREY COMPANY");
        assertNull(unshared.refusal());
        assertTrue(unshared.updated().getSharedFellowshipIds().isEmpty());
        assertEquals("the last share gone, it reads private",
                LostTalesMapMarkerVisibility.PRIVATE,
                unshared.updated().getVisibility());

        assertEquals(LostTalesWaystoneStateReason.NOT_SHARED,
                LostTalesWaystoneSettingsService.unshareFellowship(shared,
                        Collections.singletonMap(company, "The Grey Company"),
                        "Rangers").refusal());
        assertEquals("a fellowship not among the shares",
                LostTalesWaystoneStateReason.NOT_SHARED,
                LostTalesWaystoneSettingsService.unshareFellowship(placed(),
                        Collections.singletonMap(company, "The Grey Company"),
                        "The Grey Company").refusal());
        assertEquals(LostTalesWaystoneStateReason.NOT_SHARED,
                LostTalesWaystoneSettingsService.unshareCharacter(
                        placed(), FARAMIR).refusal());
        assertEquals(LostTalesWaystoneStateReason.NOT_SHARED,
                LostTalesWaystoneSettingsService.unshareCharacter(
                        placed(), null).refusal());
        LostTalesMapMarkerRecord characterShared = placed()
                .withSharedCharacters(Collections.singleton(FARAMIR));
        assertEquals(LostTalesMapMarkerVisibility.PRIVATE,
                LostTalesWaystoneSettingsService.unshareCharacter(
                        characterShared, FARAMIR).updated().getVisibility());
    }

    /* ---- Save ---- */

    /** A public waystone saves its other changes for its owner; turning one public is a manager's (WAY-13). */
    @Test
    public void onlyTurningAWaystonePublicNeedsAManager() {
        LostTalesMapMarkerRecord publicRecord = placed().toBuilder()
                .visibility(LostTalesMapMarkerVisibility.PUBLIC).build();
        LostTalesWaystoneSettingsService.Outcome renamed =
                LostTalesWaystoneSettingsService.applySettings(publicRecord,
                        settings(publicRecord, "Bree Gate", 8.0D,
                                LostTalesMapMarkerVisibility.PUBLIC),
                        false);
        assertNull(renamed.refusal());
        assertEquals("Bree Gate", renamed.updated().getName());
        assertEquals(LostTalesMapMarkerVisibility.PUBLIC,
                renamed.updated().getVisibility());

        assertEquals(LostTalesWaystoneStateReason.PUBLIC_DENIED,
                LostTalesWaystoneSettingsService.applySettings(placed(),
                        settings(placed(), "", 8.0D,
                                LostTalesMapMarkerVisibility.PUBLIC),
                        false).refusal());
        assertEquals(LostTalesMapMarkerVisibility.PUBLIC,
                LostTalesWaystoneSettingsService.applySettings(placed(),
                        settings(placed(), "", 8.0D,
                                LostTalesMapMarkerVisibility.PUBLIC),
                        true).updated().getVisibility());

        LostTalesWaystoneSettingsService.Outcome madePrivate =
                LostTalesWaystoneSettingsService.applySettings(publicRecord,
                        settings(publicRecord, "", 8.0D,
                                LostTalesMapMarkerVisibility.PRIVATE),
                        false);
        assertEquals("an owner may take its waystone private",
                LostTalesMapMarkerVisibility.PRIVATE,
                madePrivate.updated().getVisibility());
    }

    /** A Save naming Shared or Private saves a waystone that is not public; its shares say which it reads. */
    @Test
    public void aSaveKeepsAccessDerivedFromTheShares() {
        LostTalesMapMarkerRecord shared = placed()
                .withSharedCharacters(Collections.singleton(FARAMIR));
        assertEquals(LostTalesMapMarkerVisibility.SHARED,
                LostTalesWaystoneSettingsService.applySettings(shared,
                        settings(shared, "", 8.0D,
                                LostTalesMapMarkerVisibility.PRIVATE),
                        false).updated().getVisibility());
    }

    /** A placed waystone's discovery radius stops at 1,024 blocks; a bundled marker keeps its own (WAY-15). */
    @Test
    public void aPlacedWaystonesDiscoveryRadiusStopsAtTheScansReach() {
        assertEquals(1024.0D,
                LostTalesMapMarkerRecord.MAX_PLACED_DISCOVERY_RADIUS, 0.0D);
        assertNull(LostTalesWaystoneSettingsService.applySettings(placed(),
                settings(placed(), "", 1024.0D,
                        LostTalesMapMarkerVisibility.PRIVATE), false)
                .refusal());
        assertEquals(LostTalesWaystoneStateReason.INVALID_SETTINGS,
                LostTalesWaystoneSettingsService.applySettings(placed(),
                        settings(placed(), "", 1025.0D,
                                LostTalesMapMarkerVisibility.PRIVATE), false)
                        .refusal());

        LostTalesMapMarkerRecord bundled = generated(5000.0D);
        assertEquals("a bundled marker keeps a radius past the bound",
                5000.0D, bundled.getDiscoveryRadius(), 0.0D);
        assertNull(LostTalesWaystoneSettingsService.applySettings(bundled,
                settings(bundled, "Bree", 6000.0D,
                        LostTalesMapMarkerVisibility.PUBLIC), false)
                .refusal());
    }

    @Test(expected = IllegalArgumentException.class)
    public void noPlacedWaystoneRecordHoldsARadiusPastTheBound() {
        placed().toBuilder().radii(128.0D, 1025.0D).build();
    }

    private static LostTalesMapMarkerRecord placed() {
        return LostTalesMapMarkerRecord.createPlayerMarker(
                "losttales:player/bree", "Aragorn", ARAGORN,
                0, 10.0D, 64.0D, 20.0D, UUID.fromString(
                        "30000000-0000-0000-0000-000000000003"));
    }

    private static LostTalesMapMarkerRecord generated(double radius) {
        return LostTalesMapMarkerRecord.builder("losttales:bree",
                LostTalesMapMarkerSource.CUSTOM_PRESET)
                .name("Bree")
                .position(0, 10.0D, 64.0D, 20.0D)
                .radii(128.0D, radius)
                .waystone(true, "losttales:glowstone_house")
                .visibility(LostTalesMapMarkerVisibility.PUBLIC)
                .link(0, 10, 64, 20, UUID.randomUUID())
                .build();
    }

    /** The record's own settings with a name, a discovery radius and an access of the test's. */
    private static LostTalesMapMarkerEditableSettings settings(
            LostTalesMapMarkerRecord record, String name,
            double discoveryRadius, LostTalesMapMarkerVisibility access) {
        return new LostTalesMapMarkerEditableSettings(
                name, "camp", "white", "", "", true,
                record.getDimensionId(), record.getX(), record.getY(),
                record.getZ(), 128.0D, discoveryRadius, true, true, false,
                record.hasWaystone(), record.getWaystoneStructureType(),
                record.getPriority(), access);
    }
}
