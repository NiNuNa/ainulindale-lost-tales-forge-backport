package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.config.LostTalesConfig;
import java.util.UUID;
import net.minecraft.util.ChatComponentText;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Grouping is decided per view, so the same history reads differently in
 * a channel's own window and in the interleaved feed.
 */
public final class ChatGroupRunsTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final long START = 1_700_000_000_000L;

    private boolean originalGrouping;

    @Before
    public void setUp() {
        this.originalGrouping = LostTalesConfig.enableChatMessageGrouping;
        LostTalesConfig.enableChatMessageGrouping = true;
        ChatGroupRuns.clear();
    }

    @After
    public void tearDown() {
        LostTalesConfig.enableChatMessageGrouping = this.originalGrouping;
        ChatGroupRuns.clear();
    }

    private static void remember(int lineId, ChatChannel channel,
                                 UUID sender, String identity,
                                 long timestamp) {
        ChatGroupRuns.remember(lineId, ChatTab.of(channel), sender, identity,
                true, timestamp, true, new ChatComponentText("grouped"));
    }

    /** Ids as a view holds them: newest first, like vanilla's history. */
    private static int[] newestFirst(int... oldestFirst) {
        int[] ids = new int[oldestFirst.length];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = oldestFirst[ids.length - 1 - index];
        }
        return ids;
    }

    /**
     * The case the two views disagree on: A speaks, B speaks in another
     * channel, A speaks again. A's window never saw B's message, so A's
     * two messages are one run there; the feed saw it between them, so
     * they are not.
     */
    @Test
    public void anotherChannelBreaksTheFeedsRunButNotTheChannelsOwn() {
        remember(1, ChatChannel.ALL, ALICE, "Alice", START);
        remember(2, ChatChannel.OOC, BOB, "Bob", START + 1000L);
        remember(3, ChatChannel.ALL, ALICE, "Alice", START + 2000L);

        // The Global window shows only its own two messages.
        assertArrayEquals(new boolean[] { true, false },
                ChatGroupRuns.continuationsOf(newestFirst(1, 3)));
        // The feed shows all three, in order.
        assertArrayEquals(new boolean[] { false, false, false },
                ChatGroupRuns.continuationsOf(newestFirst(1, 2, 3)));
    }

    /** Ordinary consecutive messages still group, in either view. */
    @Test
    public void consecutiveMessagesFromOneIdentityGroup() {
        remember(1, ChatChannel.ALL, ALICE, "Alice", START);
        remember(2, ChatChannel.ALL, ALICE, "Alice", START + 1000L);
        remember(3, ChatChannel.ALL, ALICE, "Alice", START + 2000L);
        assertArrayEquals(new boolean[] { true, true, false },
                ChatGroupRuns.continuationsOf(newestFirst(1, 2, 3)));
    }

    /**
     * What ends a run: another sender, the same sender under another
     * identity, too long a silence, and a line with nothing recorded for
     * it at all — a system line, an adopted stray, a vanilla print.
     */
    @Test
    public void runsEndOnAnotherSenderIdentitySilenceOrUntrackedLine() {
        remember(1, ChatChannel.ALL, ALICE, "Alice", START);
        // The same account speaking as a character: another identity.
        remember(2, ChatChannel.ALL, ALICE, "Aldric", START + 1000L);
        remember(3, ChatChannel.ALL, BOB, "Bob", START + 2000L);
        remember(4, ChatChannel.ALL, BOB, "Bob",
                START + 2000L + 121L * 1000L);
        remember(5, ChatChannel.ALL, BOB, "Bob",
                START + 2000L + 122L * 1000L);
        // Line 99 was never recorded: it is not groupable and ends the run.
        // Newest first, so only line 5 continues anything.
        assertArrayEquals(
                new boolean[] { false, true, false, false, false, false },
                ChatGroupRuns.continuationsOf(newestFirst(1, 2, 3, 4, 5, 99)));
        assertNull(ChatGroupRuns.of(99));
        assertNotNull(ChatGroupRuns.of(1));
    }

    /** With grouping switched off every message opens with its header. */
    @Test
    public void groupingOffLeavesEveryMessageWithItsHeader() {
        remember(1, ChatChannel.ALL, ALICE, "Alice", START);
        remember(2, ChatChannel.ALL, ALICE, "Alice", START + 1000L);
        LostTalesConfig.enableChatMessageGrouping = false;
        assertArrayEquals(new boolean[] { false, false },
                ChatGroupRuns.continuationsOf(newestFirst(1, 2)));
    }

    /**
     * The feed fades a whole run at once, on the clock of its newest
     * message, so the group is on screen for as long as that message
     * is: anything arriving inside the fade joins it, however long the
     * run has already been going.
     */
    @Test
    public void aFeedRunContinuesWhileItsGroupIsStillOnScreen() {
        long step = ChatGroupRuns.FEED_RUN_MILLIS * 3L / 4L;
        remember(1, ChatChannel.ALL, ALICE, "Alice", START);
        remember(2, ChatChannel.ALL, ALICE, "Alice", START + step);
        remember(3, ChatChannel.ALL, ALICE, "Alice", START + 2L * step);
        remember(4, ChatChannel.ALL, ALICE, "Alice", START + 3L * step);
        // The last is well past the fade from the first, but every gap
        // is inside it, so the group never went and never split.
        assertArrayEquals(new boolean[] { true, true, true, false },
                ChatGroupRuns.continuationsInFeed(newestFirst(1, 2, 3, 4)));
    }

    /**
     * Only silence long enough for the whole group to fade ends a run
     * there; the next message then opens one of its own, with a name.
     */
    @Test
    public void aFeedRunEndsOnceItsGroupHasFaded() {
        remember(1, ChatChannel.ALL, ALICE, "Alice", START);
        remember(2, ChatChannel.ALL, ALICE, "Alice",
                START + ChatGroupRuns.FEED_RUN_MILLIS);
        assertArrayEquals(new boolean[] { true, false },
                ChatGroupRuns.continuationsInFeed(newestFirst(1, 2)));

        ChatGroupRuns.clear();
        remember(1, ChatChannel.ALL, ALICE, "Alice", START);
        remember(2, ChatChannel.ALL, ALICE, "Alice",
                START + ChatGroupRuns.FEED_RUN_MILLIS + 1L);
        assertArrayEquals(new boolean[] { false, false },
                ChatGroupRuns.continuationsInFeed(newestFirst(1, 2)));
    }

    /**
     * A window keeps its messages instead of fading them, so its runs
     * are measured from the message that opened them: a burst gets one
     * header, and a speaker cannot extend a single run all evening.
     */
    @Test
    public void aWindowRunIsMeasuredFromItsHead() {
        remember(1, ChatChannel.ALL, ALICE, "Alice", START);
        remember(2, ChatChannel.ALL, ALICE, "Alice", START + 100000L);
        remember(3, ChatChannel.ALL, ALICE, "Alice", START + 200000L);
        assertArrayEquals(new boolean[] { false, true, false },
                ChatGroupRuns.continuationsOf(newestFirst(1, 2, 3)));
        // The feed would have kept none of that: every gap is past its
        // own span.
        assertArrayEquals(new boolean[] { false, false, false },
                ChatGroupRuns.continuationsInFeed(newestFirst(1, 2, 3)));
    }

    /**
     * A reply keeps its own header wherever it lands: the quote above it
     * answers for a sender a grouped line would not name. It still opens
     * a run the messages after it may join.
     */
    @Test
    public void aMessageThatCannotGroupStillOpensARun() {
        remember(1, ChatChannel.ALL, ALICE, "Alice", START);
        ChatGroupRuns.remember(2, ChatTab.of(ChatChannel.ALL), ALICE,
                "Alice", true, START + 1000L, false,
                new ChatComponentText("grouped"));
        remember(3, ChatChannel.ALL, ALICE, "Alice", START + 2000L);
        // Newest first: 3 continues 2, but 2 never continues 1.
        assertArrayEquals(new boolean[] { true, false, false },
                ChatGroupRuns.continuationsOf(newestFirst(1, 2, 3)));
    }

    /** A message with no grouped form of its own is never grouped. */
    @Test
    public void aMessageWithoutAGroupedFormIsNotRecorded() {
        ChatGroupRuns.remember(1, ChatTab.of(ChatChannel.ALL), ALICE,
                "Alice", true, START, true, null);
        assertNull(ChatGroupRuns.of(1));
        assertFalse(ChatGroupRuns.continuationsOf(new int[] { 1 })[0]);
    }
}
