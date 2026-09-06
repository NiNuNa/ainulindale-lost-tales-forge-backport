package com.ninuna.losttales.permission;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatRoleConfig;
import java.util.Collections;
import java.util.EnumSet;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A capability is granted by any held role that names it; the built-in
 * roles grant nothing by themselves, and rank takes no part.
 */
public final class LostTalesPermissionsTest {

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    @Test
    public void everyCapabilityHasAPermanentIdAndAnOperatorLevel() {
        for (LostTalesCapability capability : LostTalesCapability.values()) {
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

    @Test
    public void aRoleGrantsWhatItNamesAndNothingElse() {
        ChatAccountRole moderator = ChatAccountRole.custom("moderator", "Moderator", "", "",
                0xA94B54, true, 15, null,
                EnumSet.of(LostTalesCapability.CHAT_MODERATE));
        ChatAccountRole builder = ChatAccountRole.custom("builder", "Builder", "", "",
                0x112233, true, 30, null, null);
        ChatRoleCatalog catalog = ChatRoleCatalog.of(
                java.util.Arrays.asList(moderator, builder), null, null);
        int moderatorBit = catalog.byId("moderator").bit();
        int builderBit = catalog.byId("builder").bit();
        assertTrue(LostTalesPermissions.isGranted(moderatorBit,
                LostTalesCapability.CHAT_MODERATE, catalog));
        assertFalse(LostTalesPermissions.isGranted(moderatorBit,
                LostTalesCapability.ROLES_MANAGE, catalog));
        assertFalse(LostTalesPermissions.isGranted(builderBit,
                LostTalesCapability.CHAT_MODERATE, catalog));
        // Holding several roles unites their grants: the builder gains
        // the moderator's grant through the moderator role, not through
        // any ordering of the two.
        assertTrue(LostTalesPermissions.isGranted(moderatorBit | builderBit,
                LostTalesCapability.CHAT_MODERATE, catalog));
        assertFalse(LostTalesPermissions.isGranted(0,
                LostTalesCapability.CHAT_MODERATE, catalog));
    }

    /** Display precedence never decides authority: a lower-ranked role still grants. */
    @Test
    public void theHighestRankedRoleDoesNotDecideAuthority() {
        ChatAccountRole vanity = ChatAccountRole.custom("veteran", "Veteran", "", "",
                0x112233, true, 5, null, null);
        ChatAccountRole moderator = ChatAccountRole.custom("moderator", "Moderator", "", "",
                0xA94B54, true, 50, null,
                EnumSet.of(LostTalesCapability.CHAT_MODERATE));
        ChatRoleCatalog catalog = ChatRoleCatalog.of(
                java.util.Arrays.asList(vanity, moderator), null, null);
        int both = catalog.byId("veteran").bit() | catalog.byId("moderator").bit();
        assertEquals("veteran", primaryOf(both, catalog));
        assertTrue(LostTalesPermissions.isGranted(both,
                LostTalesCapability.CHAT_MODERATE, catalog));
        assertFalse(LostTalesPermissions.isGranted(catalog.byId("veteran").bit(),
                LostTalesCapability.CHAT_MODERATE, catalog));
    }

    @Test
    public void theBuiltInRolesGrantNothingByThemselves() {
        ChatRoleCatalog catalog = ChatRoleCatalog.builtIn();
        int both = ChatAccountRole.TEAM.bit() | ChatAccountRole.OPERATOR.bit();
        for (LostTalesCapability capability : LostTalesCapability.values()) {
            assertFalse(LostTalesPermissions.isGranted(both, capability, catalog));
        }
        assertTrue(ChatAccountRole.TEAM.getGrants().isEmpty());
        assertTrue(ChatAccountRole.OPERATOR.getGrants().isEmpty());
    }

    /** What the wire carries about a role never includes its grants. */
    @Test
    public void grantsNeverReachTheWire() {
        ChatAccountRole moderator = ChatAccountRole.custom("moderator", "Moderator", "", "",
                0xA94B54, true, 15, null,
                EnumSet.of(LostTalesCapability.CHAT_MODERATE));
        ChatAccountRole wire = ChatAccountRole.fromWire(moderator.getId(), 2, "", "",
                moderator.getName(), moderator.getTag(), moderator.getDescription(),
                moderator.getColor(), moderator.isMentionable(), false,
                moderator.getRank());
        assertTrue(wire.getGrants().isEmpty());
        ChatRoleCatalog clientCopy = ChatRoleCatalog.fromWire(
                Collections.singletonList(wire));
        assertFalse(LostTalesPermissions.isGranted(wire.bit(),
                LostTalesCapability.CHAT_MODERATE, clientCopy));
    }

    /** The config is the one place a grant is written and read. */
    @Test
    public void grantsRoundTripThroughTheConfigEntry() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(new String[] {
                "moderator=name:Moderator;grant:chat.moderate;grant:roles.manage",
        }, null, ChatRoleConfig.SILENT);
        ChatAccountRole moderator = catalog.byId("moderator");
        assertEquals(EnumSet.of(LostTalesCapability.CHAT_MODERATE,
                LostTalesCapability.ROLES_MANAGE), moderator.getGrants());
        String written = ChatRoleConfig.formatRole(moderator);
        assertTrue(written.contains(";grant:chat.moderate"));
        assertTrue(written.contains(";grant:roles.manage"));
        ChatRoleCatalog read = ChatRoleConfig.parse(new String[] {written}, null,
                ChatRoleConfig.SILENT);
        assertEquals(moderator.getGrants(), read.byId("moderator").getGrants());
    }

    private static String primaryOf(int mask, ChatRoleCatalog catalog) {
        for (ChatAccountRole role : catalog.roles()) {
            if ((mask & role.bit()) != 0) {
                return role.getId();
            }
        }
        return "";
    }
}
