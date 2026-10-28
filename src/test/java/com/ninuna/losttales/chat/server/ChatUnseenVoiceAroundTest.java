package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatReactionSummary;
import com.ninuna.losttales.chat.ChatReportReason;
import com.ninuna.losttales.chat.ChatUnseenVoice;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What stands around an unseen voice's line names nobody either (CHAT-84):
 * the wearer leaves a list heard nearby, their reactions read as the
 * voice, and the Server Log names them to staff alone. The wearer and
 * staff holding {@code chat.moderate} see who it was.
 */
public final class ChatUnseenVoiceAroundTest {
    private static final UUID WEARER = new UUID(9L, 1L);
    private static final UUID READER = new UUID(9L, 2L);

    @Test
    public void aWearerLeavesTheListsOfThoseNearButTheirOwnAndStaffs() {
        assertTrue(ChatMemberDirectory.hidesUnseen(false, false, true));
        assertFalse(ChatMemberDirectory.hidesUnseen(true, false, true));
        assertFalse(ChatMemberDirectory.hidesUnseen(false, true, true));
        assertFalse(ChatMemberDirectory.hidesUnseen(false, false, false));
    }

    @Test
    public void aReactionMadeUnseenNamesTheVoiceToAllButItsReactorAndStaff() {
        ChatReactions reactions = new ChatReactions();
        assertTrue(reactions.set("smile", WEARER, "Aldric", "", true, true));
        assertTrue(reactions.set("smile", READER, "Bera", "", true, false));
        ChatReactionSummary others = reactions.summaryFor(
                Collections.singleton(READER), false);
        assertEquals(ChatUnseenVoice.NAME, others.find("smile").names.get(0));
        assertEquals("Bera", others.find("smile").names.get(1));
        assertEquals("Aldric", reactions.summaryFor(
                Collections.singleton(WEARER), false).find("smile").names.get(0));
        assertEquals("Aldric", reactions.summaryFor(
                Collections.singleton(READER), true).find("smile").names.get(0));
        // Taken back, the mark goes with it.
        assertTrue(reactions.set("smile", WEARER, "Aldric", "", false, false));
        assertFalse(reactions.isUnseen("smile", WEARER));
    }

    @Test
    public void theServerLogNamesTheSpeakerToStaffAlone() {
        ChatConsoleEvent removal = new ChatConsoleEvent(5L, 1L,
                ChatConsoleEvent.Kind.MODERATION, ChatConsoleEvent.Severity.NOTICE,
                "Ops", "removed a message of " + ChatUnseenVoice.NAME, "", null, null)
                .withStaffText("removed a message of Aldric");
        assertEquals("removed a message of Aldric",
                removal.forReader(true).getText());
        assertEquals("removed a message of " + ChatUnseenVoice.NAME,
                removal.forReader(false).getText());
        assertEquals("", removal.forReader(true).getStaffText());

        ChatConsoleEvent.Report report = new ChatConsoleEvent.Report(
                ChatReportReason.values()[0], "", 7L, ChatUnseenVoice.NAME, 0,
                "Turn back", "#proximity/7").withStaffAuthor("Aldric");
        ChatConsoleEvent reported = new ChatConsoleEvent(6L, 1L,
                ChatConsoleEvent.Kind.REPORT, ChatConsoleEvent.Severity.NOTICE,
                "Bera", "reported a message", "", null, report);
        assertEquals("Aldric", reported.forReader(true).getReport().getAuthor());
        assertEquals(ChatUnseenVoice.NAME,
                reported.forReader(false).getReport().getAuthor());
        assertEquals("", reported.forReader(false).getReport().getStaffAuthor());

        ChatConsoleEvent plain = new ChatConsoleEvent(8L, 1L,
                ChatConsoleEvent.Kind.MODERATION, ChatConsoleEvent.Severity.NOTICE,
                "Ops", "removed a message of Bera", "", null, null);
        assertSame(plain, plain.forReader(false));
    }
}
