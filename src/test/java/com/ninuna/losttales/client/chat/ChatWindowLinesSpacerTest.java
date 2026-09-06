package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.ChatComponentText;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A blank row stands between runs and nowhere else, and a blank row is
 * never taken for a message.
 */
public final class ChatWindowLinesSpacerTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final ChatTab GLOBAL = ChatTab.of(ChatChannel.ALL);

    @Before
    public void setUp() {
        ChatGroupRuns.clear();
    }

    @After
    public void tearDown() {
        ChatGroupRuns.clear();
    }

    @Test
    public void aBlankRowFollowsEachRunHeadWithAnOlderMessageBehindIt() {
        // Oldest to newest: Alice, Alice (grouped), Bob, system line, Alice.
        remember(1, ALICE, 1000L);
        remember(2, ALICE, 2000L);
        remember(3, BOB, 3000L);
        // 4 is a system line: no entry.
        remember(5, ALICE, 5000L);
        int[] newestFirst = {5, 4, 3, 2, 1};
        boolean[] grouped = ChatGroupRuns.continuationsOf(newestFirst);
        boolean[] spacers = ChatWindowLines.spacersAfter(newestFirst, grouped);
        // Alice(5) opens a run over the system line: spaced.
        assertTrue(spacers[0]);
        // The system line beside Bob's run: spaced, Bob has an identity.
        assertTrue(spacers[1]);
        // Bob over Alice's run: spaced.
        assertTrue(spacers[2]);
        // Alice(2) continues Alice(1): no row between them.
        assertFalse(spacers[3]);
        // The oldest message has nothing older behind it.
        assertFalse(spacers[4]);
    }

    /** The same sender after a long silence opens a new group, and is spaced. */
    @Test
    public void aNewRunFromTheSameSenderIsSpaced() {
        remember(1, ALICE, 1000L);
        remember(2, ALICE, 1000L + 10L * 60L * 1000L);
        int[] newestFirst = {2, 1};
        boolean[] grouped = ChatGroupRuns.continuationsOf(newestFirst);
        assertFalse(grouped[0]);
        assertTrue(ChatWindowLines.spacersAfter(newestFirst, grouped)[0]);
    }

    /** Console output, a run of notices: system lines are not groups. */
    @Test
    public void systemLinesSideBySideAreNotSpaced() {
        int[] newestFirst = {9, 8, 7};
        boolean[] spacers = ChatWindowLines.spacersAfter(newestFirst,
                ChatGroupRuns.continuationsOf(newestFirst));
        assertFalse(spacers[0]);
        assertFalse(spacers[1]);
        assertFalse(spacers[2]);
        assertEquals(0, ChatWindowLines.spacersAfter(null, null).length);
        assertEquals(1, ChatWindowLines.spacersAfter(new int[] {1}, null).length);
    }

    @Test
    public void aBlankRowIsKnownByIdentityAndNeverHeld() {
        ChatLine message = new ChatLine(1, new ChatComponentText("hello"), 40);
        ChatLine spacer = new ChatLine(1, ChatWindowLines.SPACER, 0);
        ChatLine another = new ChatLine(2, new ChatComponentText("there"), 41);
        assertTrue(ChatWindowLines.isSpacer(spacer));
        assertFalse(ChatWindowLines.isSpacer(message));
        assertFalse(ChatWindowLines.isSpacer(new ChatLine(1, new ChatComponentText(""), 0)));
        assertFalse(ChatWindowLines.isSpacer(null));
        List<ChatLine> lines = new ArrayList<ChatLine>();
        lines.add(message);
        lines.add(spacer);
        lines.add(another);
        assertEquals(0, ChatWindowLines.nearestMessageRow(lines, 0));
        // The newer side first, then the older.
        assertEquals(0, ChatWindowLines.nearestMessageRow(lines, 1));
        assertEquals(2, ChatWindowLines.nearestMessageRow(lines, 2));
        assertEquals(2, ChatWindowLines.nearestMessageRow(lines, 9));
        List<ChatLine> blanks = new ArrayList<ChatLine>();
        blanks.add(spacer);
        assertEquals(-1, ChatWindowLines.nearestMessageRow(blanks, 0));
        assertEquals(-1, ChatWindowLines.nearestMessageRow(null, 0));
    }

    private static void remember(int chatLineId, UUID sender, long timestamp) {
        ChatGroupRuns.remember(chatLineId, GLOBAL, sender, "Name", false,
                timestamp, true, new ChatComponentText("grouped"));
    }
}
