package com.ninuna.losttales.client.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.chat.ChatRoleplayStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

/**
 * The status chosen for each identity is kept per server, survives the
 * file, keeps Online as no line, and skips what it cannot read.
 */
public final class ClientChatPresenceChoicesTest {
    private static final UUID ALDRIC = new UUID(7L, 8L);

    @After
    public void forget() {
        ClientChatPresenceChoices.clear();
    }

    @Test
    public void aChoiceIsKeptForItsServerAlone() {
        ClientChatPresenceChoices.initialize(null, null);
        ClientChatPresenceChoices.remember("play.example.org",
                ChatPresenceIdentity.character(ALDRIC), ChatPresence.INVISIBLE);
        ClientChatPresenceChoices.remember("play.example.org",
                ChatPresenceIdentity.ACCOUNT, ChatPresence.DO_NOT_DISTURB);
        Map<ChatPresenceIdentity, ChatPresence> here =
                ClientChatPresenceChoices.forPlace("play.example.org");
        assertEquals(ChatPresence.INVISIBLE,
                here.get(ChatPresenceIdentity.character(ALDRIC)));
        assertEquals(ChatPresence.DO_NOT_DISTURB,
                here.get(ChatPresenceIdentity.ACCOUNT));
        assertTrue(ClientChatPresenceChoices.forPlace("elsewhere").isEmpty());
    }

    @Test
    public void onlineIsKeptAsNoLine() {
        ClientChatPresenceChoices.initialize(null, null);
        ClientChatPresenceChoices.remember("server",
                ChatPresenceIdentity.ACCOUNT, ChatPresence.AWAY);
        ClientChatPresenceChoices.remember("server",
                ChatPresenceIdentity.ACCOUNT, ChatPresence.ONLINE);
        assertTrue(ClientChatPresenceChoices.forPlace("server").isEmpty());
        assertEquals(1, ClientChatPresenceChoices.describe().size());
    }

    /**
     * A status line is kept per server and identity, cleaned, apart from
     * the status, and an empty one is forgotten; the file keeps it.
     */
    @Test
    public void aStatusLineIsKeptBesideTheStatus() {
        ClientChatPresenceChoices.initialize(null, null);
        ClientChatPresenceChoices.rememberLine("server",
                ChatPresenceIdentity.character(ALDRIC), "  Out \u00a7chunting ");
        ClientChatPresenceChoices.rememberLine("server",
                ChatPresenceIdentity.ACCOUNT, "Brewing");
        ClientChatPresenceChoices.rememberLine("server",
                ChatPresenceIdentity.ACCOUNT, "");
        Map<ChatPresenceIdentity, String> here =
                ClientChatPresenceChoices.linesForPlace("server");
        assertEquals(1, here.size());
        assertEquals("Out hunting",
                here.get(ChatPresenceIdentity.character(ALDRIC)));
        assertTrue(ClientChatPresenceChoices.forPlace("server").isEmpty());
        List<String> lines = ClientChatPresenceChoices.describe();
        ClientChatPresenceChoices.clear();
        ClientChatPresenceChoices.load(lines);
        assertEquals("Out hunting", ClientChatPresenceChoices
                .linesForPlace("server")
                .get(ChatPresenceIdentity.character(ALDRIC)));
        ClientChatPresenceChoices.clear();
        ClientChatPresenceChoices.load(Arrays.asList(
                "server\taccount\tline\t   ",
                "server\tnobody\tline\tHello",
                "server\taccount\tnote\tHello"));
        assertTrue(ClientChatPresenceChoices.linesForPlace("server").isEmpty());
    }

    /**
     * A role-play status is kept per server and identity beside the
     * status and the line, an identity's own default is no record, and
     * the file keeps it.
     */
    @Test
    public void aRolePlayStatusIsKeptUnlessItIsTheDefault() {
        ClientChatPresenceChoices.initialize(null, null);
        ClientChatPresenceChoices.rememberRoleplay("server",
                ChatPresenceIdentity.character(ALDRIC),
                ChatRoleplayStatus.LOOKING_FOR_SCENE);
        ClientChatPresenceChoices.rememberRoleplay("server",
                ChatPresenceIdentity.ACCOUNT, ChatRoleplayStatus.IN_CHARACTER);
        ClientChatPresenceChoices.rememberRoleplay("server",
                ChatPresenceIdentity.ACCOUNT,
                ChatRoleplayStatus.OUT_OF_CHARACTER);
        Map<ChatPresenceIdentity, ChatRoleplayStatus> here =
                ClientChatPresenceChoices.roleplayForPlace("server");
        assertEquals(1, here.size());
        assertEquals(ChatRoleplayStatus.LOOKING_FOR_SCENE,
                here.get(ChatPresenceIdentity.character(ALDRIC)));
        List<String> lines = ClientChatPresenceChoices.describe();
        ClientChatPresenceChoices.clear();
        ClientChatPresenceChoices.load(lines);
        assertEquals(here, ClientChatPresenceChoices.roleplayForPlace("server"));
        ClientChatPresenceChoices.clear();
        ClientChatPresenceChoices.load(Arrays.asList(
                "server\taccount\troleplay\tout_of_character",
                "server\taccount\troleplay\tdancing",
                "server\tnobody\troleplay\tin_character"));
        assertTrue(ClientChatPresenceChoices.roleplayForPlace("server")
                .isEmpty());
    }

    @Test
    public void theFileRoundTripsAndSkipsWhatItCannotRead() {
        ClientChatPresenceChoices.initialize(null, null);
        ClientChatPresenceChoices.remember("server",
                ChatPresenceIdentity.character(ALDRIC), ChatPresence.AWAY);
        List<String> lines = ClientChatPresenceChoices.describe();
        ClientChatPresenceChoices.clear();
        ClientChatPresenceChoices.load(lines);
        assertEquals(ChatPresence.AWAY, ClientChatPresenceChoices
                .forPlace("server").get(ChatPresenceIdentity.character(ALDRIC)));
        ClientChatPresenceChoices.clear();
        ClientChatPresenceChoices.load(Arrays.asList(
                "server\taccount\toffline",
                "server\tnobody\taway",
                "server\taccount\tbusy",
                "\taccount\taway",
                "server\taccount"));
        assertTrue(ClientChatPresenceChoices.forPlace("server").isEmpty());
    }
}
