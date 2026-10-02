package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatTabIds;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Where a command's answer is kept: under the tab its client says the
 * command was typed in only while the player may read that conversation,
 * and in their Console otherwise, so naming a tab never files a
 * line under a conversation the player has no place in.
 */
public final class LostTalesServerBroadcastHookAnswerTabTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID FELLOWSHIP = UUID.randomUUID();
    private static final String GONDOR = "lotr:gondor";
    private static final List<ChatChannel> NO_STAFF = Arrays.asList(
            ChatChannel.GLOBAL, ChatChannel.OOC, ChatChannel.FACTION,
            ChatChannel.FELLOWSHIP, ChatChannel.WHISPER, ChatChannel.CLIENT_CONSOLE);

    /** An answer named for a channel the player may not read lands in the Console. */
    @Test
    public void anAnswerForAChannelThePlayerCannotReadLandsInTheClientConsole() {
        ChatHistory.Requester alice = ChatHistoryRequesters.oneFaction(ALICE, GONDOR,
                0L, FELLOWSHIP, NO_STAFF);
        assertEquals("", LostTalesServerBroadcastHook.answerTab("operator", alice));
        assertEquals("", LostTalesServerBroadcastHook.answerTab("server_console", alice));
        assertNull("no tab names no channel, which the hook files as the Console",
                ChatTabIds.channelOf(LostTalesServerBroadcastHook.answerTab(
                        "operator", alice)));
        assertEquals("global", LostTalesServerBroadcastHook.answerTab("global", alice));
        assertEquals("operator", LostTalesServerBroadcastHook.answerTab("operator",
                ChatHistoryRequesters.reader(ALICE)));
    }

    /** A faction's or a fellowship's tab holds an answer only for one of its own. */
    @Test
    public void aScopedTabHoldsAnAnswerOnlyForItsOwnMembers() {
        ChatHistory.Requester alice = ChatHistoryRequesters.oneFaction(ALICE, GONDOR,
                0L, FELLOWSHIP, NO_STAFF);
        assertEquals("faction|in:" + GONDOR, LostTalesServerBroadcastHook.answerTab(
                "faction|in:" + GONDOR, alice));
        assertEquals("another faction's tab", "", LostTalesServerBroadcastHook.answerTab(
                "faction|in:lotr:mordor", alice));
        assertEquals("a scoped channel naming no conversation", "",
                LostTalesServerBroadcastHook.answerTab("faction", alice));
        assertEquals("fellowship|in:" + FELLOWSHIP, LostTalesServerBroadcastHook.answerTab(
                "fellowship|in:" + FELLOWSHIP, alice));
        assertEquals("a fellowship she is not in", "", LostTalesServerBroadcastHook.answerTab(
                "fellowship|in:" + UUID.randomUUID(), alice));
    }

    /** A whisper's tab while the player may read whispers, an NPC's always; anything else nowhere. */
    @Test
    public void whisperAndNpcTabsAreThePlayersOwn() {
        ChatHistory.Requester alice = ChatHistoryRequesters.oneFaction(ALICE, GONDOR,
                0L, null, NO_STAFF);
        assertEquals("whisper:Bob", LostTalesServerBroadcastHook.answerTab(
                "whisper:Bob", alice));
        assertEquals("", LostTalesServerBroadcastHook.answerTab("whisper:Bob",
                ChatHistoryRequesters.oneFaction(ALICE, GONDOR, 0L, null,
                        Collections.singletonList(ChatChannel.GLOBAL))));
        assertEquals("npc:Barliman", LostTalesServerBroadcastHook.answerTab(
                "npc:Barliman", alice));
        assertEquals("", LostTalesServerBroadcastHook.answerTab("no_such_channel", alice));
        assertEquals("", LostTalesServerBroadcastHook.answerTab("", alice));
        assertEquals("", LostTalesServerBroadcastHook.answerTab("global", null));
    }
}
