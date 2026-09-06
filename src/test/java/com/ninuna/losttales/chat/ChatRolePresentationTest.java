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
        ChatRoleCatalog.resetToBuiltIn();
    }

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    @Test
    public void rolesShowOnTheAccountChannelsOnly() {
        for (ChatChannel channel : ChatChannel.values()) {
            assertEquals(channel.getIdentityType() == ChatIdentityType.ACCOUNT,
                    ChatRolePresentation.showsRoles(channel));
        }
        assertFalse(ChatRolePresentation.showsRoles(null));
        int held = ChatAccountRole.maskOf(ChatAccountRole.OPERATOR);
        assertEquals(held, ChatRolePresentation.rolesShown(ChatChannel.OOC, held));
        assertEquals(held, ChatRolePresentation.rolesShown(ChatChannel.WHISPER, held));
        assertEquals(0, ChatRolePresentation.rolesShown(ChatChannel.ALL, held));
        assertEquals(0, ChatRolePresentation.rolesShown(ChatChannel.FACTION, held));
    }

    /** Out of character the primary role colours the name, whoever is worn. */
    @Test
    public void outOfCharacterTheRoleColoursTheName() {
        int held = ChatAccountRole.maskOf(ChatAccountRole.OPERATOR);
        assertEquals(ChatAccountRole.OPERATOR.getColor(),
                ChatRolePresentation.nameColor(ChatChannel.OOC, held, true, GONDOR));
        assertEquals(ChatAccountRole.OPERATOR.getColor(),
                ChatRolePresentation.nameColor(ChatChannel.OOC, held, false, GONDOR));
        assertEquals(ChatRolePresentation.unassignedColor(),
                ChatRolePresentation.nameColor(ChatChannel.ADMIN, 0, true, GONDOR));
        assertEquals(ChatRolePresentation.unassignedColor(),
                ChatRolePresentation.nameColor(ChatChannel.ADMIN, 0, false, GONDOR));
    }

    /** In character the faction colours the name; the account is unassigned. */
    @Test
    public void inCharacterTheFactionColoursTheNameAndRolesAreNotWorn() {
        int held = ChatAccountRole.maskOf(ChatAccountRole.OPERATOR, ChatAccountRole.TEAM);
        assertEquals(GONDOR,
                ChatRolePresentation.nameColor(ChatChannel.ALL, held, false, GONDOR));
        assertEquals(GONDOR,
                ChatRolePresentation.nameColor(ChatChannel.PARTY, 0, false, GONDOR));
        assertEquals(ChatRolePresentation.unassignedColor(),
                ChatRolePresentation.nameColor(ChatChannel.ALL, held, true, GONDOR));
        assertEquals(ChatRolePresentation.unassignedColor(),
                ChatRolePresentation.nameColor(ChatChannel.PROXIMITY, 0, true, GONDOR));
        assertTrue(ChatRolePresentation.unassignedColor() != GONDOR);
    }
}
