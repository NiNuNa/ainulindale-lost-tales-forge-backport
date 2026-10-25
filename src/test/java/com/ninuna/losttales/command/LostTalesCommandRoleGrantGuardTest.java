package com.ninuna.losttales.command;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.permission.LostTalesCapability;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Managing roles is not a way around the capabilities. A role may only
 * be written or handed on by someone who could already do everything it
 * allows, so holding {@code roles.manage} alone never becomes holding
 * the rest.
 */
public final class LostTalesCommandRoleGrantGuardTest {

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
        com.ninuna.losttales.permission.LostTalesPermissionCatalog.install(
                com.ninuna.losttales.permission.LostTalesPermissionCatalog.empty());
    }

    private static ChatAccountRole granting(LostTalesCapability... capabilities) {
        LinkedHashSet<String> grants = new LinkedHashSet<String>();
        for (LostTalesCapability capability : capabilities) {
            grants.add(capability.getId());
        }
        return ChatAccountRole.custom("moderator", "Moderator", "",
                0xA94B54, true, 15, null, grants, null);
    }

    /** A role granting a configured permission is refused the same way. */
    private static ChatAccountRole grantingPermission(String permissionId) {
        return ChatAccountRole.custom("moderator", "Moderator", "",
                0xA94B54, true, 15, null,
                new LinkedHashSet<String>(Arrays.asList(permissionId)), null);
    }

    /**
     * An operator of level 3 holds every built-in capability by level, so
     * nothing is withheld from them.
     */
    @Test
    public void anAdministratorMayWriteAnyGrant() {
        assertNull(LostTalesCommandRole.withheldGrant(
                FakeCommandSender.administrator("Admin"),
                granting(LostTalesCapability.CHAT_MODERATE,
                        LostTalesCapability.SERVER_CONFIG)));
    }

    /**
     * An operator of level 2 does not hold the server's settings (DATA-07),
     * so a role granting them is withheld from them too.
     */
    @Test
    public void anOperatorIsWithheldTheServersSettings() {
        assertNull(LostTalesCommandRole.withheldGrant(
                FakeCommandSender.operator("Ops"),
                granting(LostTalesCapability.CHAT_MODERATE)));
        assertEquals(LostTalesCapability.SERVER_CONFIG.getId(),
                LostTalesCommandRole.withheldGrant(
                        FakeCommandSender.operator("Ops"),
                        granting(LostTalesCapability.CHAT_MODERATE,
                                LostTalesCapability.SERVER_CONFIG)));
    }

    /**
     * A permission reaches its capabilities, so a role granting one is
     * withheld exactly as a role naming the capability itself is.
     */
    @Test
    public void aPermissionIsWithheldByWhatItReaches() {
        com.ninuna.losttales.permission.LostTalesPermissionCatalog.install(
                com.ninuna.losttales.chat.ChatRoleConfig.parsePermissions(
                        new String[] {"keeper=capability:server.config"},
                        com.ninuna.losttales.chat.ChatRoleConfig.SILENT));
        assertEquals(LostTalesCapability.SERVER_CONFIG.getId(),
                LostTalesCommandRole.withheldGrant(
                        FakeCommandSender.player("Someone"),
                        grantingPermission("keeper")));
        assertNull("an administrator holds what the permission reaches",
                LostTalesCommandRole.withheldGrant(
                        FakeCommandSender.administrator("Admin"),
                        grantingPermission("keeper")));
    }

    /**
     * A grant that reaches nothing withholds nothing: it allows nothing,
     * so writing it hands nobody anything.
     */
    @Test
    public void aGrantThatReachesNothingIsWithheldFromNobody() {
        assertNull(LostTalesCommandRole.withheldGrant(
                FakeCommandSender.player("Someone"),
                grantingPermission("build.everything")));
    }

    /** A role that grants nothing is anyone's to write. */
    @Test
    public void aRoleThatGrantsNothingIsWithheldFromNobody() {
        assertNull(LostTalesCommandRole.withheldGrant(
                FakeCommandSender.player("Someone"),
                ChatAccountRole.custom("herald", "Herald", "", 0x112233,
                        true, 30, null, null, null)));
    }

    /**
     * Someone who does not hold a capability is refused the role that
     * grants it, and told which one stopped them.
     */
    @Test
    public void aCapabilityNotHeldIsWithheld() {
        assertEquals(LostTalesCapability.SERVER_CONFIG.getId(),
                LostTalesCommandRole.withheldGrant(
                        FakeCommandSender.player("Someone"),
                        granting(LostTalesCapability.SERVER_CONFIG)));
    }

}
