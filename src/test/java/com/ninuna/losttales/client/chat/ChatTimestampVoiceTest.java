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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The timestamp column stamps each speaker's turn once: a line is
 * stamped when it opens its minute, or when another voice spoke the
 * line above it inside the same minute. Two people talking at once
 * each carry their time, as the messages themselves are grouped per
 * voice; one voice's burst inside a minute is stamped once.
 */
public final class ChatTimestampVoiceTest {

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
    public void twoVoicesInsideOneMinuteAreBothStamped() {
        remember(1, ALICE, "Alice");
        remember(2, BOB, "Bob");
        remember(3, ALICE, "Alice");
        // Newest first: Alice(3), Bob(2), Alice(1), all at 15:13.
        List<ChatLine> lines = new ArrayList<ChatLine>();
        lines.add(stamped(3, "[15:13] "));
        lines.add(stamped(2, "[15:13] "));
        lines.add(stamped(1, "[15:13] "));
        assertTrue(LostTalesChatOverlayRenderer.opensItsMinute(lines, 2));
        assertTrue(LostTalesChatOverlayRenderer.opensItsMinute(lines, 1));
        assertTrue(LostTalesChatOverlayRenderer.opensItsMinute(lines, 0));
    }

    @Test
    public void oneVoiceInsideOneMinuteIsStampedOnce() {
        remember(1, ALICE, "Alice");
        remember(2, ALICE, "Alice");
        remember(3, ALICE, "Alice");
        List<ChatLine> lines = new ArrayList<ChatLine>();
        lines.add(stamped(3, "[15:14] "));
        lines.add(stamped(2, "[15:13] "));
        lines.add(stamped(1, "[15:13] "));
        assertTrue(LostTalesChatOverlayRenderer.opensItsMinute(lines, 2));
        assertFalse(LostTalesChatOverlayRenderer.opensItsMinute(lines, 1));
        // A new minute is stamped even inside one voice's run.
        assertTrue(LostTalesChatOverlayRenderer.opensItsMinute(lines, 0));
    }

    /** Wrapped continuation rows carry no timestamp and are skipped over. */
    @Test
    public void continuationRowsAreNeitherStampedNorInTheWay() {
        remember(1, ALICE, "Alice");
        remember(2, ALICE, "Alice");
        List<ChatLine> lines = new ArrayList<ChatLine>();
        lines.add(stamped(2, "[15:13] "));
        lines.add(new ChatLine(0, new ChatComponentText("wrapped"), 1));
        lines.add(stamped(1, "[15:13] "));
        assertFalse(LostTalesChatOverlayRenderer.opensItsMinute(lines, 1));
        assertFalse(LostTalesChatOverlayRenderer.opensItsMinute(lines, 0));
    }

    /**
     * A day's rule opens every turn again: the same clock reading on
     * another day is another minute.
     */
    @Test
    public void aDaysRuleOpensTheTurnAgain() {
        remember(1, ALICE, "Alice");
        remember(2, ALICE, "Alice");
        List<ChatLine> lines = new ArrayList<ChatLine>();
        lines.add(stamped(2, "[15:13] "));
        lines.add(stamped(1, "[15:13] "));
        assertFalse(LostTalesChatOverlayRenderer.opensItsMinute(lines, 0));
        // Newest first: 2, gap, the rule, gap, 1.
        lines.add(1, new ChatLine(0, ChatWindowLines.SPACER, 0));
        lines.add(2, new ChatLine(0, ChatWindowLines.dateDivider("Tuesday"), 0));
        lines.add(3, new ChatLine(0, ChatWindowLines.SPACER, 0));
        assertTrue(LostTalesChatOverlayRenderer.opensItsMinute(lines, 0));
        assertTrue(LostTalesChatOverlayRenderer.opensItsMinute(lines, 4));
    }

    /**
     * The unread divider opens the turn again while it stands, as a
     * day's rule does: the message under it opens a run of its own, and
     * a run is stamped once.
     */
    @Test
    public void theUnreadDividerOpensTheTurnAgain() {
        remember(1, ALICE, "Alice");
        remember(2, ALICE, "Alice");
        List<ChatLine> lines = new ArrayList<ChatLine>();
        lines.add(stamped(2, "[15:13] "));
        lines.add(new ChatLine(0, new ChatComponentText("wrapped"), 1));
        lines.add(stamped(1, "[15:13] "));
        assertFalse(LostTalesChatOverlayRenderer.opensItsMinute(lines, 0,
                -1));
        // The divider over message 2's oldest row.
        assertTrue(LostTalesChatOverlayRenderer.opensItsMinute(lines, 0, 0));
        // Over a message above the stamped one, it is no business of it.
        assertFalse(LostTalesChatOverlayRenderer.opensItsMinute(lines, 1,
                0));
    }

    /** System lines have no voice; beside a player's line they are a change of voice. */
    @Test
    public void systemLinesAreOneVoiceAmongThemselvesAndAnotherBesideAPlayer() {
        remember(2, ALICE, "Alice");
        assertTrue(ChatGroupRuns.sameVoice(7, 8));
        assertFalse(ChatGroupRuns.sameVoice(2, 8));
        assertFalse(ChatGroupRuns.sameVoice(8, 2));
        assertTrue(ChatGroupRuns.sameVoice(2, 2));
        remember(3, ALICE, "Alice");
        remember(4, ALICE, "Other Name");
        assertTrue(ChatGroupRuns.sameVoice(2, 3));
        assertFalse(ChatGroupRuns.sameVoice(2, 4));
    }

    private static void remember(int chatLineId, UUID sender, String name) {
        ChatGroupRuns.remember(chatLineId, GLOBAL, sender, name, false,
                1000L * chatLineId, true, new ChatComponentText("grouped"));
    }

    private static ChatLine stamped(int chatLineId, String time) {
        ChatComponentText root = new ChatComponentText("");
        root.appendSibling(ChatPrefixMarker.timestamp(
                new ChatComponentText(time), 0xFFFFFF));
        root.appendSibling(new ChatComponentText("body"));
        return new ChatLine(0, root, chatLineId);
    }
}
