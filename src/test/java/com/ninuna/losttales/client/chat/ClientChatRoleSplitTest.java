package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatRoleFixtures;
import java.util.Collections;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A role given to an account is worn by the account and every one of its
 * characters; one given to a character by that character alone. The
 * client resolves each identity's roles from what the server stated.
 */
public final class ClientChatRoleSplitTest {
    private int operator;
    private int team;

    @Before
    public void setUp() {
        ChatRoleCatalog.install(ChatRoleFixtures.catalogue());
        this.operator = ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR);
        this.team = ChatAccountRole.TEAM.bit();
    }

    @After
    public void tearDown() {
        ClientChatChannelState.clear();
        ChatRoleCatalog.resetToBuiltIn();
    }

    @Test
    public void ownCharactersWearTheAccountsRolesAndTheirOwn() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ClientChatChannelState.setRoleMask(this.operator | this.team);
        ClientChatChannelState.setRoleSplit(this.operator,
                Collections.singletonMap(first, Integer.valueOf(this.team)));
        assertEquals(this.operator,
                ClientChatChannelState.getAccountRoleMask());
        assertEquals(this.operator | this.team,
                ClientChatChannelState.ownCharacterRoles(first));
        assertEquals(this.operator,
                ClientChatChannelState.ownCharacterRoles(second));
    }

    /**
     * The roster credits a character's own role to the character its
     * holder is playing, and the account's to the account and to every
     * other character of it.
     */
    @Test
    public void theRosterTellsTheAccountFromThePlayedCharacter() {
        UUID played = UUID.randomUUID();
        ClientChatChannelState.setRoleHolders(
                Collections.singletonMap("Steve",
                        Integer.valueOf(this.operator | this.team)),
                Collections.singletonMap("Steve",
                        Integer.valueOf(this.operator)),
                Collections.singletonMap("Steve", played));
        assertEquals(this.operator | this.team,
                ClientChatChannelState.rosterRolesOf("steve"));
        assertEquals(this.operator,
                ClientChatChannelState.rosterAccountRolesOf("Steve"));
        assertEquals(this.operator | this.team,
                ClientChatChannelState.rosterRolesOf("Steve", played));
        assertEquals(this.operator, ClientChatChannelState.rosterRolesOf(
                "Steve", UUID.randomUUID()));
    }

    @Test
    public void theProximityRadiusIsUnstatedUntilTheServerSaysAndGoesWithTheState() {
        assertEquals(0, ClientChatChannelState.getProximityRadius());
        ClientChatChannelState.setProximityRadius(64);
        assertEquals(64, ClientChatChannelState.getProximityRadius());
        ClientChatChannelState.clear();
        assertEquals(0, ClientChatChannelState.getProximityRadius());
    }
}
