package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatPageIds;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Where a command's answer is kept: under the page its client says the
 * command was typed in only while the player may read that conversation,
 * and in their Console otherwise, so naming a page never files a
 * line under a conversation the player has no place in.
 */
public final class LostTalesServerBroadcastHookAnswerPageTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID FELLOWSHIP = UUID.randomUUID();
    private static final String GONDOR = "lotr:gondor";
    private static final List<ChatChannel> NO_STAFF = Arrays.asList(
            ChatChannel.GLOBAL, ChatChannel.OOC, ChatChannel.FACTION,
            ChatChannel.FELLOWSHIP, ChatChannel.WHISPER, ChatChannel.CONSOLE);

    /** An answer named for a channel the player may not read lands in the Console. */
    @Test
    public void anAnswerForAChannelThePlayerCannotReadLandsInTheConsole() {
        ChatHistory.Requester alice = ChatHistoryRequesters.oneFaction(ALICE, GONDOR,
                0L, FELLOWSHIP, NO_STAFF);
        assertEquals("", LostTalesServerBroadcastHook.answerPage("operator", alice));
        assertEquals("", LostTalesServerBroadcastHook.answerPage("server_log", alice));
        assertNull("no page names no channel, which the hook files as the Console",
                ChatPageIds.channelOf(LostTalesServerBroadcastHook.answerPage(
                        "operator", alice)));
        assertEquals("global", LostTalesServerBroadcastHook.answerPage("global", alice));
        assertEquals("operator", LostTalesServerBroadcastHook.answerPage("operator",
                ChatHistoryRequesters.reader(ALICE)));
    }

    /** A faction's or a fellowship's page holds an answer only for one of its own. */
    @Test
    public void aScopedPageHoldsAnAnswerOnlyForItsOwnMembers() {
        ChatHistory.Requester alice = ChatHistoryRequesters.oneFaction(ALICE, GONDOR,
                0L, FELLOWSHIP, NO_STAFF);
        assertEquals("faction|in:" + GONDOR, LostTalesServerBroadcastHook.answerPage(
                "faction|in:" + GONDOR, alice));
        assertEquals("another faction's page", "", LostTalesServerBroadcastHook.answerPage(
                "faction|in:lotr:mordor", alice));
        assertEquals("a scoped channel naming no conversation", "",
                LostTalesServerBroadcastHook.answerPage("faction", alice));
        assertEquals("fellowship|in:" + FELLOWSHIP, LostTalesServerBroadcastHook.answerPage(
                "fellowship|in:" + FELLOWSHIP, alice));
        assertEquals("a fellowship she is not in", "", LostTalesServerBroadcastHook.answerPage(
                "fellowship|in:" + UUID.randomUUID(), alice));
    }

    /** A whisper's page while the player may read whispers, an NPC's always; anything else nowhere. */
    @Test
    public void whisperAndNpcPagesAreThePlayersOwn() {
        ChatHistory.Requester alice = ChatHistoryRequesters.oneFaction(ALICE, GONDOR,
                0L, null, NO_STAFF);
        assertEquals("whisper:Bob", LostTalesServerBroadcastHook.answerPage(
                "whisper:Bob", alice));
        assertEquals("", LostTalesServerBroadcastHook.answerPage("whisper:Bob",
                ChatHistoryRequesters.oneFaction(ALICE, GONDOR, 0L, null,
                        Collections.singletonList(ChatChannel.GLOBAL))));
        assertEquals("npc:Barliman", LostTalesServerBroadcastHook.answerPage(
                "npc:Barliman", alice));
        assertEquals("", LostTalesServerBroadcastHook.answerPage("no_such_channel", alice));
        assertEquals("", LostTalesServerBroadcastHook.answerPage("", alice));
        assertEquals("", LostTalesServerBroadcastHook.answerPage("global", null));
    }
}
