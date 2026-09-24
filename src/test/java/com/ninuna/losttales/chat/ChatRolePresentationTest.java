package com.ninuna.losttales.chat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Roles are worn out of character and never in character; the name's
 * colour follows the same line.
 */
public final class ChatRolePresentationTest {

    private static final int GONDOR = 0x4F6FA8;

    @Before
    public void setUp() {
        ChatRoleCatalog.install(ChatRoleFixtures.catalogue());
    }

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    @Test
    public void rolesShowOutOfCharacterOnly() {
        for (ChatChannel channel : ChatChannel.values()) {
            boolean outOfCharacter = channel.getPresentation()
                    == ChatPresentationMode.OUT_OF_CHARACTER;
            assertEquals(outOfCharacter, ChatRolePresentation.showsRoles(channel));
            assertEquals(!outOfCharacter, ChatRolePresentation.isInCharacter(channel));
        }
        assertFalse(ChatRolePresentation.showsRoles(null));
        assertFalse(ChatRolePresentation.isInCharacter(null));
        // Whispers are roleplay: no role is worn there.
        assertTrue(ChatRolePresentation.isInCharacter(ChatChannel.WHISPER));
        int held = ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR);
        assertEquals(held, ChatRolePresentation.rolesShown(ChatChannel.OOC, held));
        assertEquals(0, ChatRolePresentation.rolesShown(ChatChannel.WHISPER, held));
        assertEquals(0, ChatRolePresentation.rolesShown(ChatChannel.GLOBAL, held));
        assertEquals(0, ChatRolePresentation.rolesShown(ChatChannel.FACTION, held));
    }

    /** One tag, never a stack: only the highest-ranked role is worn. */
    @Test
    public void onlyThePrimaryRoleIsWorn() {
        int both = ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR, ChatAccountRole.TEAM);
        assertEquals(ChatAccountRole.TEAM.bit(),
                ChatRolePresentation.rolesShown(ChatChannel.OOC, both));
        assertEquals(ChatAccountRole.TEAM.bit(),
                ChatRolePresentation.rolesShown(ChatChannel.OPERATOR, both));
        assertEquals(0, ChatRolePresentation.rolesShown(ChatChannel.OOC, 0));
    }

    /** Out of character the primary role colours the name, whoever is worn. */
    @Test
    public void outOfCharacterTheRoleColoursTheName() {
        int held = ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR);
        assertEquals(ChatRoleFixtures.OPERATOR.getColor(),
                ChatRolePresentation.nameColor(ChatChannel.OOC, held, true, GONDOR));
        assertEquals(ChatRoleFixtures.OPERATOR.getColor(),
                ChatRolePresentation.nameColor(ChatChannel.OOC, held, false, GONDOR));
        assertEquals(ChatRolePresentation.unassignedColor(),
                ChatRolePresentation.nameColor(ChatChannel.OPERATOR, 0, true, GONDOR));
        assertEquals(ChatRolePresentation.unassignedColor(),
                ChatRolePresentation.nameColor(ChatChannel.OPERATOR, 0, false, GONDOR));
    }

    /** In character the faction colours the name; the account is unassigned. */
    @Test
    public void inCharacterTheFactionColoursTheNameAndRolesAreNotWorn() {
        int held = ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR, ChatAccountRole.TEAM);
        assertEquals(GONDOR,
                ChatRolePresentation.nameColor(ChatChannel.GLOBAL, held, false, GONDOR));
        assertEquals(GONDOR,
                ChatRolePresentation.nameColor(ChatChannel.PARTY, 0, false, GONDOR));
        assertEquals(ChatRolePresentation.unassignedColor(),
                ChatRolePresentation.nameColor(ChatChannel.GLOBAL, held, true, GONDOR));
        assertEquals(ChatRolePresentation.unassignedColor(),
                ChatRolePresentation.nameColor(ChatChannel.PROXIMITY, 0, true, GONDOR));
        assertTrue(ChatRolePresentation.unassignedColor() != GONDOR);
    }
}
