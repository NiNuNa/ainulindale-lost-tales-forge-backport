package com.ninuna.losttales.chat;

import com.ninuna.losttales.permission.LostTalesCapability;
import com.ninuna.losttales.permission.LostTalesPermissionCatalog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The permissions a server defines: what a role may grant, in the
 * server's own words. Every problem is reported and costs the entry the
 * smallest piece it can, and a name with nothing behind it never reads
 * as a name with everything behind it.
 */
public final class ChatPermissionConfigTest {

    private final List<String> warnings = new ArrayList<String>();
    private final ChatRoleConfig.Warnings collect = new ChatRoleConfig.Warnings() {
        @Override
        public void warn(String message) {
            warnings.add(message);
        }
    };

    @Before
    public void setUp() {
        warnings.clear();
    }

    @After
    public void tearDown() {
        LostTalesPermissionCatalog.resetToEmpty();
    }

    /** A permission names capabilities, may describe itself, and keeps its order. */
    @Test
    public void aPermissionNamesTheCapabilitiesItReaches() {
        LostTalesPermissionCatalog catalog = ChatRoleConfig.parsePermissions(new String[] {
                "keeper=capability:chat.moderate;capability:chat.console.read;"
                        + "desc:Keeps the peace.",
                "builder=capability:waystone.manage",
        }, collect);
        assertTrue(warnings.toString(), warnings.isEmpty());
        assertEquals(Arrays.asList("keeper", "builder"), catalog.ids());
        assertEquals(new LinkedHashSet<String>(Arrays.asList("chat.moderate",
                        "chat.console.read")),
                catalog.capabilityIdsOf("keeper"));
        assertEquals("Keeps the peace.", catalog.descriptionOf("keeper"));
        assertEquals("", catalog.descriptionOf("builder"));
        assertTrue(catalog.reaches("keeper", LostTalesCapability.CHAT_MODERATE));
        assertFalse(catalog.reaches("keeper", LostTalesCapability.WAYSTONE_MANAGE));
    }

    /** Ids are read case-insensitively and answered the same way. */
    @Test
    public void idsAreReadWhateverTheirCase() {
        LostTalesPermissionCatalog catalog = ChatRoleConfig.parsePermissions(
                new String[] {"Keeper=capability:Chat.Moderate"}, collect);
        assertTrue(warnings.toString(), warnings.isEmpty());
        assertTrue(catalog.isDefined("keeper"));
        assertTrue(catalog.isDefined("KEEPER"));
        assertTrue(catalog.reaches("KeEpEr", LostTalesCapability.CHAT_MODERATE));
    }

    /**
     * A capability the code does not have costs that one capability and
     * nothing else; the permission and its other capabilities stand.
     */
    @Test
    public void anUnknownCapabilityCostsOnlyItself() {
        LostTalesPermissionCatalog catalog = ChatRoleConfig.parsePermissions(
                new String[] {"keeper=capability:chat.moderate;"
                        + "capability:fly.to.the.moon"}, collect);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0), warnings.get(0).contains("fly.to.the.moon"));
        assertTrue(catalog.isDefined("keeper"));
        assertEquals(java.util.Collections.singleton("chat.moderate"),
                catalog.capabilityIdsOf("keeper"));
    }

    /**
     * A permission that reaches nothing is kept exactly as written and
     * allows nothing: the empty set must never read as every capability.
     */
    @Test
    public void aPermissionReachingNothingIsKeptAndAllowsNothing() {
        LostTalesPermissionCatalog catalog = ChatRoleConfig.parsePermissions(
                new String[] {"hollow=capability:fly.to.the.moon", "bare="}, collect);
        // hollow is told twice: the capability it names is unknown, and
        // it then reaches nothing; bare reaches nothing.
        assertEquals(warnings.toString(), 3, warnings.size());
        assertTrue(warnings.get(0), warnings.get(0).contains("fly.to.the.moon"));
        assertTrue(warnings.get(1), warnings.get(1).contains("allows nothing"));
        assertTrue(warnings.get(2), warnings.get(2).contains("allows nothing"));
        assertTrue(catalog.isDefined("hollow"));
        assertTrue(catalog.isDefined("bare"));
        for (LostTalesCapability capability : LostTalesCapability.all()) {
            assertFalse(catalog.reaches("hollow", capability));
            assertFalse(catalog.reaches("bare", capability));
        }
    }

    /** A permission listed twice keeps the first entry, and says so. */
    @Test
    public void aPermissionListedTwiceKeepsTheFirst() {
        LostTalesPermissionCatalog catalog = ChatRoleConfig.parsePermissions(new String[] {
                "keeper=capability:chat.moderate",
                "keeper=capability:server.config",
        }, collect);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0), warnings.get(0).contains("twice"));
        assertTrue(catalog.reaches("keeper", LostTalesCapability.CHAT_MODERATE));
        assertFalse(catalog.reaches("keeper", LostTalesCapability.SERVER_CONFIG));
    }

    /** An id nothing can name is skipped, and the entries around it stand. */
    @Test
    public void anUnusableIdIsSkipped() {
        LostTalesPermissionCatalog catalog = ChatRoleConfig.parsePermissions(new String[] {
                "a keeper=capability:chat.moderate",
                "keeper=capability:chat.moderate",
        }, collect);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0), warnings.get(0).contains("usable id"));
        assertEquals(Arrays.asList("keeper"), catalog.ids());
    }

    /** Blank lines and comments are not entries. */
    @Test
    public void blanksAndCommentsAreSkippedSilently() {
        LostTalesPermissionCatalog catalog = ChatRoleConfig.parsePermissions(new String[] {
                "", "   ", "# keeper=capability:chat.moderate",
                "keeper=capability:chat.moderate",
        }, collect);
        assertTrue(warnings.toString(), warnings.isEmpty());
        assertEquals(Arrays.asList("keeper"), catalog.ids());
    }

    /** Nothing at all is a catalogue too, and every grant is read as a capability. */
    @Test
    public void noEntriesLeaveEveryGrantReadAsACapability() {
        LostTalesPermissionCatalog catalog = ChatRoleConfig.parsePermissions(null, collect);
        assertTrue(warnings.isEmpty());
        assertTrue(catalog.ids().isEmpty());
        assertTrue(catalog.reaches("chat.moderate", LostTalesCapability.CHAT_MODERATE));
        assertFalse(catalog.reaches("chat.moderate", LostTalesCapability.SERVER_CONFIG));
        assertTrue(catalog.isKnown("chat.moderate"));
        assertFalse(catalog.isKnown("build.everything"));
    }

    /** The entry a permission is written as reads back as the same permission. */
    @Test
    public void aPermissionRoundTripsThroughItsEntry() {
        String entry = ChatRoleConfig.formatPermission("keeper",
                new LinkedHashSet<String>(Arrays.asList("chat.moderate",
                        "chat.console.read")),
                "Keeps the peace.");
        assertEquals("keeper=capability:chat.moderate;capability:chat.console.read;"
                + "desc:Keeps the peace.", entry);
        LostTalesPermissionCatalog catalog = ChatRoleConfig.parsePermissions(
                new String[] {entry}, collect);
        assertTrue(warnings.toString(), warnings.isEmpty());
        assertEquals(new LinkedHashSet<String>(Arrays.asList("chat.moderate",
                        "chat.console.read")),
                catalog.capabilityIdsOf("keeper"));
        assertEquals("Keeps the peace.", catalog.descriptionOf("keeper"));
    }

    /** More permissions than the catalogue holds are dropped, with one warning. */
    @Test
    public void tooManyPermissionsAreDropped() {
        List<String> entries = new ArrayList<String>();
        for (int index = 0; index <= LostTalesPermissionCatalog.MAX_PERMISSIONS; index++) {
            entries.add("p" + index + "=capability:chat.moderate");
        }
        LostTalesPermissionCatalog catalog = ChatRoleConfig.parsePermissions(
                entries.toArray(new String[entries.size()]), collect);
        assertEquals(LostTalesPermissionCatalog.MAX_PERMISSIONS, catalog.ids().size());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0), warnings.get(0).contains("permissions are configured"));
    }
}
