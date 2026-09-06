package com.ninuna.losttales.command;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.permission.LostTalesCapability;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Who may run a Lost Tales command. A command that names a capability
 * is open to whoever holds it, by operator level or by a role the
 * server's config grants it to; one that names none keeps vanilla's own
 * level check. The root opens for anyone who may run at least one
 * sub-command and asks again for the sub-command itself.
 */
public final class LostTalesCommandAuthorizationTest {

    private static final UUID MODERATOR_ACCOUNT =
            UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    /** Installs a server catalogue with one role granting one capability. */
    private static void installRoleGranting(LostTalesCapability capability, UUID member) {
        ChatAccountRole role = ChatAccountRole.custom("moderator", "Moderator", "", "",
                0xA94B54, true, 15, null, new LinkedHashSet<String>(
                        Collections.singletonList(capability.getId())));
        Map<String, Set<UUID>> members = new LinkedHashMap<String, Set<UUID>>();
        members.put("moderator", new HashSet<UUID>(Collections.singletonList(member)));
        ChatRoleCatalog.installServer(ChatRoleCatalog.of(
                Arrays.asList(role), members, null));
    }

    /**
     * A capability-bearing command asks the capability and nothing else,
     * so an operator runs it because of the level the capability names.
     */
    @Test
    public void aCapabilityCommandOpensForAnOperator() {
        LostTalesCommandBase moderation = new LostTalesCommandChatModeration();
        assertEquals(LostTalesCapability.CHAT_MODERATE, moderation.getCapability());
        assertTrue(moderation.canCommandSenderUseCommand(
                FakeCommandSender.operator("Ops")));
        assertFalse(moderation.canCommandSenderUseCommand(
                FakeCommandSender.player("Someone")));
    }

    /**
     * A command names its level once: the capability's. The two can no
     * longer say different things.
     */
    @Test
    public void theCommandLevelIsTheCapabilitysOwn() {
        LostTalesCommandBase moderation = new LostTalesCommandChatModeration();
        assertEquals(LostTalesCapability.CHAT_MODERATE.getRequiredOpLevel(),
                moderation.getRequiredPermissionLevel());
        LostTalesCommandBase role = new LostTalesCommandRole();
        assertEquals(LostTalesCapability.ROLES_MANAGE.getRequiredOpLevel(),
                role.getRequiredPermissionLevel());
    }

    /**
     * The root names no capability of its own and keeps vanilla's level
     * check; it opens besides for anyone who may run a sub-command.
     */
    @Test
    public void aCommandWithoutACapabilityKeepsTheLevelCheck() {
        LostTalesCommandRoot root = new LostTalesCommandRoot();
        assertEquals(null, root.getCapability());
        assertTrue(root.canCommandSenderUseCommand(FakeCommandSender.operator("Ops")));
        assertFalse(root.canCommandSenderUseCommand(FakeCommandSender.player("Someone")));
    }

    /**
     * Every area that was the operators' alone now names a capability,
     * so a server may delegate it to a role instead of handing out
     * operator status.
     */
    @Test
    public void theOperatorOnlyCommandsCanBeDelegated() {
        assertEquals(LostTalesCapability.QUEST_ADMIN,
                new LostTalesCommandQuest().getCapability());
        assertEquals(LostTalesCapability.CHARACTER_ADMIN,
                new LostTalesCommandCharacterAdmin().getCapability());
        assertEquals(LostTalesCapability.PARTY_ADMIN,
                new LostTalesCommandPartyAdmin().getCapability());
        assertEquals(LostTalesCapability.MAPMARKER_MANAGE,
                new LostTalesCommandMapMarker().getCapability());
        assertEquals(LostTalesCapability.HUD_ADMIN,
                new LostTalesCommandHud().getCapability());
    }

    /**
     * A role the server grants the capability to opens the command for a
     * player with no operator level at all: this is the whole point of
     * the role system, and it is the server's catalogue that is asked.
     */
    @Test
    public void aRoleGrantOpensACommandWithoutOperatorLevel() {
        installRoleGranting(LostTalesCapability.CHAT_MODERATE, MODERATOR_ACCOUNT);
        // A plain ICommandSender is not a player, so it cannot hold a
        // role: the level is all it has, and the role grants it nothing.
        assertFalse(new LostTalesCommandChatModeration().canCommandSenderUseCommand(
                FakeCommandSender.player("Someone")));
        // The rule the player path uses, with the two facts stated.
        assertTrue(com.ninuna.losttales.permission.LostTalesPermissions.decide(false,
                ChatRoleCatalog.server().byId("moderator").bit(),
                LostTalesCapability.CHAT_MODERATE, ChatRoleCatalog.server(),
                com.ninuna.losttales.permission.LostTalesPermissionCatalog.current()));
        assertFalse(com.ninuna.losttales.permission.LostTalesPermissions.decide(false,
                ChatRoleCatalog.server().byId("moderator").bit(),
                LostTalesCapability.SERVER_CONFIG, ChatRoleCatalog.server(),
                com.ninuna.losttales.permission.LostTalesPermissionCatalog.current()));
    }

    /**
     * The root opens for an operator, and for nobody who may run no
     * sub-command; every sub-command it lists is asked for itself.
     */
    @Test
    public void theRootOpensNoWiderThanItsSubCommands() {
        LostTalesCommandRoot root = new LostTalesCommandRoot();
        assertTrue(root.canCommandSenderUseCommand(FakeCommandSender.operator("Ops")));
        assertFalse(root.canCommandSenderUseCommand(FakeCommandSender.player("Someone")));
        for (ELostTalesSubCommand subCommand : ELostTalesSubCommand.values()) {
            assertFalse("no sub-command opens for a player with no level and no role",
                    subCommand.getCommand().canCommandSenderUseCommand(
                            FakeCommandSender.player("Someone")));
        }
    }

    /**
     * Every capability-bearing sub-command reaches the same decision,
     * so none of them keeps a check of its own.
     */
    @Test
    public void everySubCommandAsksTheSameQuestion() {
        FakeCommandSender operator = FakeCommandSender.operator("Ops");
        FakeCommandSender player = FakeCommandSender.player("Someone");
        for (ELostTalesSubCommand subCommand : ELostTalesSubCommand.values()) {
            net.minecraft.command.CommandBase command = subCommand.getCommand();
            if (!(command instanceof LostTalesCommandBase)) {
                continue;
            }
            LostTalesCapability capability =
                    ((LostTalesCommandBase)command).getCapability();
            assertTrue(subCommand.name() + " must open for an operator",
                    command.canCommandSenderUseCommand(operator));
            assertFalse(subCommand.name() + " must refuse a plain player",
                    command.canCommandSenderUseCommand(player));
            if (capability != null) {
                assertEquals(subCommand.name() + " states one level",
                        capability.getRequiredOpLevel(),
                        command.getRequiredPermissionLevel());
            }
        }
    }
}
