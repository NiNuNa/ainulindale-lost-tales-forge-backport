package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageValidator;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A draft is a conversation's unsent text on one server. It is written to
 * the account's file and read back the same, unless it is a command or
 * was typed where the place has no name.
 */
public final class ClientChatDraftsTest {
    private static final ConversationPage GLOBAL = ConversationPage.of(ChatChannel.GLOBAL);
    private static final ConversationPage OOC = ConversationPage.of(ChatChannel.OOC);
    private static final String SERVER = "server:play.example";
    private static final UUID ACCOUNT =
            UUID.fromString("0b1f6a9e-5a3c-4c1d-9a44-3f2f7f0a1c11");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        ClientChatDrafts.initialize(null, null);
    }

    @After
    public void tearDown() {
        ClientChatDrafts.initialize(null, null);
    }

    @Test
    public void aDraftIsKeptPerServerAndConversation() {
        ClientChatDrafts.set(SERVER, GLOBAL, "a long turn");
        assertEquals("a long turn", ClientChatDrafts.get(SERVER, GLOBAL));
        assertEquals("", ClientChatDrafts.get(SERVER, OOC));
        assertEquals("", ClientChatDrafts.get("world:My World", GLOBAL));
        assertEquals("", ClientChatDrafts.get(SERVER, null));
        ClientChatDrafts.set(SERVER, GLOBAL, "");
        assertEquals("", ClientChatDrafts.get(SERVER, GLOBAL));
        ClientChatDrafts.set(SERVER, GLOBAL, null);
        ClientChatDrafts.set(SERVER, null, "nowhere");
        assertEquals(1, ClientChatDrafts.describe().size());
    }

    @Test
    public void aWhisperIsTheSameConversationHoweverItsNameIsCased() {
        ClientChatDrafts.set(SERVER, ConversationPage.whisper("Alex", ""), "psst");
        assertEquals("psst",
                ClientChatDrafts.get(SERVER, ConversationPage.whisper("alex", "")));
    }

    @Test
    public void theFileReadsBackToTheSameDrafts() {
        String text = "First line\nsecond\twith a tab \\ and a slash\r";
        ClientChatDrafts.set(SERVER, GLOBAL, text);
        ClientChatDrafts.set("world:My World", OOC, "  spaced  ");
        List<String> lines = ClientChatDrafts.describe();
        assertTrue(lines.get(0).startsWith("#"));
        assertEquals(3, lines.size());
        for (String line : lines) {
            assertFalse(line, line.indexOf('\n') >= 0);
            assertFalse(line, line.indexOf('\r') >= 0);
        }
        ClientChatDrafts.initialize(null, null);
        ClientChatDrafts.load(lines);
        assertEquals(text, ClientChatDrafts.get(SERVER, GLOBAL));
        assertEquals("  spaced  ",
                ClientChatDrafts.get("world:My World", OOC));
        assertEquals(lines, ClientChatDrafts.describe());
    }

    @Test
    public void aCommandIsKeptForTheSessionAndNeverWritten() {
        ClientChatDrafts.set(SERVER, GLOBAL, "/login hunter2");
        assertEquals("/login hunter2", ClientChatDrafts.get(SERVER, GLOBAL));
        assertEquals(1, ClientChatDrafts.describe().size());
        ClientChatDrafts.endSession();
        assertEquals("", ClientChatDrafts.get(SERVER, GLOBAL));
    }

    @Test
    public void textTypedWhereThePlaceHasNoNameIsNeverWritten() {
        ClientChatDrafts.set("", GLOBAL, "nowhere in particular");
        ClientChatDrafts.set(null, OOC, "nor here");
        ClientChatDrafts.set("odd\tkey", OOC, "nor here");
        assertEquals("nowhere in particular",
                ClientChatDrafts.get("", GLOBAL));
        assertEquals(1, ClientChatDrafts.describe().size());
        ClientChatDrafts.endSession();
        assertEquals("", ClientChatDrafts.get("", GLOBAL));
    }

    @Test
    public void leavingThePlaceKeepsWhatIsWritten() {
        ClientChatDrafts.set(SERVER, GLOBAL, "a long turn");
        ClientChatDrafts.endSession();
        assertEquals("a long turn", ClientChatDrafts.get(SERVER, GLOBAL));
    }

    @Test
    public void aLineThatDoesNotParseOrIsNoDraftIsSkipped() {
        StringBuilder overlong = new StringBuilder();
        for (int index = 0;
             index <= ChatMessageValidator.MAX_CHARACTERS; index++) {
            overlong.append('a');
        }
        ClientChatDrafts.load(Arrays.asList(
                "# a comment",
                "",
                "no separators at all",
                SERVER + "\tglobal",
                "\tglobal\tno server",
                SERVER + "\t\tno conversation",
                SERVER + "\tglobal\t",
                SERVER + "\tglobal\t/op somebody",
                SERVER + "\tglobal\t" + overlong,
                SERVER + "\tooc\tthe one good line"));
        assertEquals("", ClientChatDrafts.get(SERVER, GLOBAL));
        assertEquals("the one good line", ClientChatDrafts.get(SERVER, OOC));
        assertEquals(2, ClientChatDrafts.describe().size());
    }

    @Test
    public void pastTheBoundTheOldestDraftsGo() {
        for (int index = 0; index <= ClientChatDrafts.MAX_DRAFTS; index++) {
            ClientChatDrafts.set("server:" + index, GLOBAL, "draft " + index);
        }
        assertEquals("", ClientChatDrafts.get("server:0", GLOBAL));
        assertEquals("draft 1", ClientChatDrafts.get("server:1", GLOBAL));
        assertEquals(ClientChatDrafts.MAX_DRAFTS + 1,
                ClientChatDrafts.describe().size());
    }

    @Test
    public void theAccountsFileIsWrittenAndReadAgain() throws Exception {
        File folder = this.temporaryFolder.newFolder("client");
        ClientChatDrafts.initialize(folder, ACCOUNT);
        ClientChatDrafts.set(SERVER, GLOBAL, "kept over a crash");
        ClientChatDrafts.set(SERVER, OOC, "/secret command");
        ClientChatDrafts.save();
        File file = new File(new File(folder, ClientChatDrafts.FOLDER),
                ACCOUNT + ".txt");
        assertTrue(file.isFile());
        ClientChatDrafts.initialize(folder, ACCOUNT);
        assertEquals("kept over a crash",
                ClientChatDrafts.get(SERVER, GLOBAL));
        assertEquals("", ClientChatDrafts.get(SERVER, OOC));
        // Sent or emptied, it leaves the file too.
        ClientChatDrafts.set(SERVER, GLOBAL, "");
        ClientChatDrafts.save();
        ClientChatDrafts.initialize(folder, ACCOUNT);
        assertEquals("", ClientChatDrafts.get(SERVER, GLOBAL));
        // Another account reads nothing of it.
        ClientChatDrafts.set(SERVER, GLOBAL, "mine");
        ClientChatDrafts.save();
        ClientChatDrafts.initialize(folder, UUID.randomUUID());
        assertEquals("", ClientChatDrafts.get(SERVER, GLOBAL));
    }
}
