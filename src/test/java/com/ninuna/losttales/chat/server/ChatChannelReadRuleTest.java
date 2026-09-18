package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * One read rule for a player here and an account that is not: a staff
 * channel the config names no gate for is the operators', the server's
 * console its readers', and every other channel asks its gate — in
 * character with the roles of the identity reading, out of character
 * with the account's.
 */
public final class ChatChannelReadRuleTest {

    @After
    public void tearDown() {
        ChatRoleCatalog.installServer(null);
    }

    private static ChatChannelPolicy.Reader reader(final boolean operator,
                                                   final boolean console,
                                                   final int accountRoles) {
        return new ChatChannelPolicy.Reader() {
            @Override
            public boolean isOperator() {
                return operator;
            }

            @Override
            public boolean readsConsole() {
                return console;
            }

            @Override
            public int accountRoles() {
                return accountRoles;
            }
        };
    }

    @Test
    public void aStaffChannelWithoutAGateIsTheOperators() {
        ChatChannelGates open = ChatChannelGates.defaults();
        assertTrue(ChatChannelPolicy.canRead(reader(true, false, 0),
                ChatChannel.ADMIN, 0, open));
        assertFalse(ChatChannelPolicy.canRead(reader(false, true, 0),
                ChatChannel.ADMIN, 0, open));
    }

    @Test
    public void theServerConsoleIsItsReaders() {
        ChatChannelGates open = ChatChannelGates.defaults();
        assertTrue(ChatChannelPolicy.canRead(reader(false, true, 0),
                ChatChannel.SERVER_CONSOLE, 0, open));
        assertFalse(ChatChannelPolicy.canRead(reader(true, false, 0),
                ChatChannel.SERVER_CONSOLE, 0, open));
    }

    @Test
    public void aGateAsksTheIdentityInCharacterAndTheAccountOutOfIt() {
        ChatAccountRole herald = ChatAccountRole.custom("herald", "Herald", "",
                "", 0x112233, true, 30, null);
        ChatRoleCatalog.installServer(ChatRoleCatalog.of(Arrays.asList(herald),
                Collections.<String, Set<UUID>>emptyMap(),
                Collections.<String, Set<UUID>>emptyMap()));
        int heraldBit = ChatRoleCatalog.server().byId("herald").bit();
        Set<String> heralds = Collections.singleton("herald");
        Map<ChatChannel, ChatChannelGates.Gate> configured =
                new HashMap<ChatChannel, ChatChannelGates.Gate>();
        configured.put(ChatChannel.ALL, new ChatChannelGates.Gate(heralds, null));
        configured.put(ChatChannel.OOC, new ChatChannelGates.Gate(heralds, null));
        ChatChannelGates gates = ChatChannelGates.of(configured);

        assertTrue(ChatChannelPolicy.canRead(reader(false, false, 0),
                ChatChannel.ALL, heraldBit, gates));
        assertFalse(ChatChannelPolicy.canRead(reader(false, false, heraldBit),
                ChatChannel.ALL, 0, gates));
        assertTrue(ChatChannelPolicy.canRead(reader(false, false, heraldBit),
                ChatChannel.OOC, 0, gates));
        assertFalse(ChatChannelPolicy.canRead(reader(false, false, 0),
                ChatChannel.OOC, heraldBit, gates));
    }
}
