package com.ninuna.losttales.permission;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatRoleConfig;
import com.ninuna.losttales.chat.ChatRoleFixtures;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A capability is reached by any held role that grants a permission
 * naming it. The built-in roles grant nothing by themselves, rank takes
 * no part, and a grant naming neither a permission nor a capability
 * reaches nothing at all.
 */
public final class LostTalesPermissionsTest {

    private static final LostTalesPermissionCatalog NO_PERMISSIONS =
            LostTalesPermissionCatalog.empty();

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
        LostTalesPermissionCatalog.resetToEmpty();
    }

    private static Set<String> grants(String... ids) {
        return new LinkedHashSet<String>(Arrays.asList(ids));
    }

    private static ChatAccountRole role(String id, int rank, String... granted) {
        return ChatAccountRole.custom(id, id, "", 0xA94B54, true, rank, null,
                grants(granted));
    }

    @Test
    public void everyCapabilityHasAPermanentIdAndAnOperatorLevel() {
        for (LostTalesCapability capability : LostTalesCapability.all()) {
            assertEquals(capability, LostTalesCapability.byId(capability.getId()));
            assertEquals(capability, LostTalesCapability.byId(
                    capability.getId().toUpperCase(java.util.Locale.ROOT)));
            assertTrue(capability.getRequiredOpLevel() >= 1
                    && capability.getRequiredOpLevel() <= 4);
            assertTrue(capability.getDescription().length() > 0);
        }
        assertNull(LostTalesCapability.byId("nothing.at.all"));
        assertNull(LostTalesCapability.byId(null));
    }

    /** The set is open, and registering an id twice states the same capability. */
    @Test
    public void aCapabilityMayBeRegisteredAndIsNeverRedefined() {
        LostTalesCapability first = LostTalesCapability.register(
                "test.registry.open", 2, "something a test does");
        assertEquals(first, LostTalesCapability.byId("test.registry.open"));
        assertTrue(LostTalesCapability.all().contains(first));
        LostTalesCapability again = LostTalesCapability.register(
                "test.registry.open", 4, "a different description");
        assertEquals("the same capability answers, unchanged", first, again);
        assertEquals(2, again.getRequiredOpLevel());
    }

    /** A grant naming a capability directly needs no permission defined. */
    @Test
    public void aGrantMayNameACapabilityWithNoPermissionDefined() {
        ChatRoleCatalog catalog = ChatRoleCatalog.of(
                Arrays.asList(role("moderator", 15, "chat.moderate")), null, null);
        int bit = catalog.byId("moderator").bit();
        assertTrue(LostTalesPermissions.isGranted(bit,
                LostTalesCapability.CHAT_MODERATE, catalog, NO_PERMISSIONS));
        assertFalse(LostTalesPermissions.isGranted(bit,
                LostTalesCapability.ROLES_MANAGE, catalog, NO_PERMISSIONS));
    }

    /** A permission the server defines is what a role grants, and it expands. */
    @Test
    public void aPermissionExpandsToTheCapabilitiesItNames() {
        LostTalesPermissionCatalog permissions = ChatRoleConfig.parsePermissions(
                new String[] {"keeper=capability:chat.moderate;"
                        + "capability:chat.console.read;desc:Keeps the peace."},
                ChatRoleConfig.SILENT);
        ChatRoleCatalog catalog = ChatRoleCatalog.of(
                Arrays.asList(role("moderator", 15, "keeper")), null, null);
        int bit = catalog.byId("moderator").bit();
        assertTrue(LostTalesPermissions.isGranted(bit,
                LostTalesCapability.CHAT_MODERATE, catalog, permissions));
        assertTrue(LostTalesPermissions.isGranted(bit,
                LostTalesCapability.CHAT_CONSOLE_READ, catalog, permissions));
        assertFalse("nothing the permission does not name",
                LostTalesPermissions.isGranted(bit,
                        LostTalesCapability.SERVER_CONFIG, catalog, permissions));
        assertEquals("Keeps the peace.", permissions.descriptionOf("keeper"));
    }

    /**
     * A permission of a capability's own id decides for it: the server's
     * word is what a grant of that id means.
     */
    @Test
    public void aPermissionMayNarrowAnIdThatNamesACapability() {
        LostTalesPermissionCatalog permissions = ChatRoleConfig.parsePermissions(
                new String[] {"chat.moderate=capability:chat.console.read"},
                ChatRoleConfig.SILENT);
        ChatRoleCatalog catalog = ChatRoleCatalog.of(
                Arrays.asList(role("moderator", 15, "chat.moderate")), null, null);
        int bit = catalog.byId("moderator").bit();
        assertTrue(LostTalesPermissions.isGranted(bit,
                LostTalesCapability.CHAT_CONSOLE_READ, catalog, permissions));
        assertFalse("the permission, not the capability of the same id",
                LostTalesPermissions.isGranted(bit,
                        LostTalesCapability.CHAT_MODERATE, catalog, permissions));
    }

    /** Several held roles unite what they reach, whatever their rank. */
    @Test
    public void heldRolesUniteAndRankTakesNoPart() {
        ChatRoleCatalog catalog = ChatRoleCatalog.of(Arrays.asList(
                role("moderator", 15, "chat.moderate"),
                role("keeper", 30, "server.config")), null, null);
        int moderator = catalog.byId("moderator").bit();
        int keeper = catalog.byId("keeper").bit();
        assertTrue(LostTalesPermissions.isGranted(moderator | keeper,
                LostTalesCapability.CHAT_MODERATE, catalog, NO_PERMISSIONS));
        assertTrue("the lower-ranked role still reaches its own",
                LostTalesPermissions.isGranted(moderator | keeper,
                        LostTalesCapability.SERVER_CONFIG, catalog, NO_PERMISSIONS));
        assertFalse(LostTalesPermissions.isGranted(0,
                LostTalesCapability.CHAT_MODERATE, catalog, NO_PERMISSIONS));
    }

    /**
     * A grant naming neither a permission nor a capability is kept and
     * reaches nothing, and starts working when a permission of that id
     * is defined — the roles are never rewritten for it.
     */
    @Test
    public void anUnknownGrantIsKeptAndReachesNothingUntilItIsDefined() {
        ChatRoleCatalog catalog = ChatRoleCatalog.of(
                Arrays.asList(role("builder", 30, "build.everything")), null, null);
        int bit = catalog.byId("builder").bit();
        assertEquals(Collections.singleton("build.everything"),
                catalog.byId("builder").getGrants());
        for (LostTalesCapability capability : LostTalesCapability.all()) {
            assertFalse(capability.getId() + " is not reached",
                    LostTalesPermissions.isGranted(bit, capability, catalog,
                            NO_PERMISSIONS));
        }
        LostTalesPermissionCatalog defined = ChatRoleConfig.parsePermissions(
                new String[] {"build.everything=capability:mapmarker.manage"},
                ChatRoleConfig.SILENT);
        assertTrue("the same role now reaches it, unrewritten",
                LostTalesPermissions.isGranted(bit,
                        LostTalesCapability.MAPMARKER_MANAGE, catalog, defined));
    }

    /** A permission naming no capability the code has never means everything. */
    @Test
    public void aPermissionWithoutCapabilitiesReachesNothing() {
        LostTalesPermissionCatalog permissions = ChatRoleConfig.parsePermissions(
                new String[] {"hollow=capability:nothing.the.code.has"},
                ChatRoleConfig.SILENT);
        assertTrue("the permission is still defined", permissions.isDefined("hollow"));
        ChatRoleCatalog catalog = ChatRoleCatalog.of(
                Arrays.asList(role("builder", 30, "hollow")), null, null);
        int bit = catalog.byId("builder").bit();
        for (LostTalesCapability capability : LostTalesCapability.all()) {
            assertFalse(capability.getId() + " is not reached",
                    LostTalesPermissions.isGranted(bit, capability, catalog, permissions));
        }
    }

    /**
     * The rule the live check applies once the two facts are known.
     * Either says yes on its own and nothing says no.
     */
    @Test
    public void eitherTheLevelOrARoleGrantsAndNothingRefuses() {
        ChatRoleCatalog catalog = ChatRoleCatalog.of(
                Arrays.asList(role("moderator", 15, "chat.moderate")), null, null);
        int held = catalog.byId("moderator").bit();
        assertTrue("the operator level alone holds it",
                LostTalesPermissions.decide(true, 0,
                        LostTalesCapability.SERVER_CONFIG, catalog, NO_PERMISSIONS));
        assertTrue("a role that reaches it alone holds it",
                LostTalesPermissions.decide(false, held,
                        LostTalesCapability.CHAT_MODERATE, catalog, NO_PERMISSIONS));
        assertTrue(LostTalesPermissions.decide(true, held,
                LostTalesCapability.CHAT_MODERATE, catalog, NO_PERMISSIONS));
        assertFalse("neither is not holding it",
                LostTalesPermissions.decide(false, held,
                        LostTalesCapability.SERVER_CONFIG, catalog, NO_PERMISSIONS));
        assertFalse(LostTalesPermissions.decide(false, 0,
                LostTalesCapability.CHAT_MODERATE, catalog, NO_PERMISSIONS));
    }

    /** The catalogues asked are the ones handed in, never whatever is installed. */
    @Test
    public void theCataloguesAskedAreTheOnesGiven() {
        ChatRoleCatalog granted = ChatRoleCatalog.of(
                Arrays.asList(role("moderator", 15, "chat.moderate")), null, null);
        ChatRoleCatalog withheld = ChatRoleCatalog.of(
                Arrays.asList(role("moderator", 15)), null, null);
        int bit = granted.byId("moderator").bit();
        assertEquals(bit, withheld.byId("moderator").bit());
        assertTrue(LostTalesPermissions.isGranted(bit,
                LostTalesCapability.CHAT_MODERATE, granted, NO_PERMISSIONS));
        assertFalse(LostTalesPermissions.isGranted(bit,
                LostTalesCapability.CHAT_MODERATE, withheld, NO_PERMISSIONS));
    }

    /** A catalogue read off the wire carries no grants, so it authorizes nothing. */
    @Test
    public void grantsNeverReachTheWire() {
        ChatRoleCatalog fromWire = ChatRoleCatalog.fromWire(
                Arrays.asList(ChatAccountRole.fromWire("moderator", 1, "",
                        "Moderator", "", 0xA94B54, true, false, 15, null)));
        assertTrue(fromWire.byId("moderator").getGrants().isEmpty());
        assertFalse(LostTalesPermissions.isGranted(
                fromWire.byId("moderator").bit(),
                LostTalesCapability.CHAT_MODERATE, fromWire, NO_PERMISSIONS));
    }

    /** The team mark and the seeded operator role grant nothing of themselves. */
    @Test
    public void theBuiltInRolesGrantNothing() {
        ChatRoleCatalog catalog = ChatRoleFixtures.catalogue();
        assertTrue(ChatAccountRole.TEAM.getGrants().isEmpty());
        assertTrue(catalog.byId(ChatRoleFixtures.OPERATOR_ID).getGrants().isEmpty());
        int both = ChatAccountRole.TEAM.bit()
                | catalog.byId(ChatRoleFixtures.OPERATOR_ID).bit();
        for (LostTalesCapability capability : LostTalesCapability.all()) {
            assertFalse(LostTalesPermissions.isGranted(both, capability, catalog,
                    NO_PERMISSIONS));
        }
    }

    /** A bit no role in the catalogue owns reaches nothing. */
    @Test
    public void anUnknownBitGrantsNothing() {
        ChatRoleCatalog catalog = ChatRoleFixtures.catalogue();
        assertFalse(LostTalesPermissions.decide(false, 1 << 30,
                LostTalesCapability.CHAT_MODERATE, catalog, NO_PERMISSIONS));
        assertFalse(LostTalesPermissions.isGranted(1 << 30,
                LostTalesCapability.CHAT_MODERATE, catalog, NO_PERMISSIONS));
    }

    /** Grants round-trip through the config entry, unknown ids included. */
    @Test
    public void grantsRoundTripThroughTheConfigEntry() {
        ChatRoleCatalog parsed = ChatRoleConfig.parse(new String[] {
                "moderator=name:Moderator;grant:chat.moderate;grant:build.everything"},
                null, ChatRoleConfig.SILENT);
        String entry = ChatRoleConfig.formatRole(parsed.byId("moderator"));
        assertTrue(entry, entry.endsWith(";grant:chat.moderate;grant:build.everything"));
        ChatRoleCatalog again = ChatRoleConfig.parse(new String[] {entry}, null,
                ChatRoleConfig.SILENT);
        assertEquals(parsed.byId("moderator").getGrants(),
                again.byId("moderator").getGrants());
    }
}
