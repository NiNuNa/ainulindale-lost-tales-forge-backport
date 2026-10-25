package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatFellowship;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.network.packet.LostTalesChatIdentitySyncPacket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A fellowship's conversation stays in its window, hidden, while any
 * character of the account is in the fellowship, the one played or
 * another, and leaves the windows once none is (WIN-18). While the
 * server has not said, nothing leaves.
 */
public final class FellowshipConversationsKeptTest {
    private static final UUID GREY = new UUID(31L, 1L);
    private static final UUID RANGERS = new UUID(31L, 2L);

    @Before
    public void setUp() {
        tearDown();
    }

    @After
    public void tearDown() {
        ClientChatIdentities.clear();
        ClientChatIdentitySelection.clear();
        ClientCharacterRosterCache.clear();
        ClientChatChannelViews.clear();
        ClientChatChannelState.clear();
        TwoWindowLayout.reset();
    }

    @Test
    public void theRuleKeepsWhatAnyCharacterOfTheAccountIsIn() {
        List<String> account = Collections.singletonList("a");
        assertTrue("the character played is in it",
                ClientChatIdentitySelection.keeps("b", true, true, account));
        assertTrue("another character is in it",
                ClientChatIdentitySelection.keeps("a", false, true, account));
        assertFalse("no character is",
                ClientChatIdentitySelection.keeps("b", false, true, account));
        assertTrue("the server has not said",
                ClientChatIdentitySelection.keeps("b", false, false,
                        Collections.<String>emptyList()));
    }

    @Test
    public void aFellowshipLeftByEveryCharacterLeavesTheWindows() {
        ConversationPage grey = conversation(GREY);
        ConversationPage rangers = conversation(RANGERS);
        ClientChatIdentitySelection.accept(sync(Arrays.asList(GREY, RANGERS),
                Arrays.asList(GREY, RANGERS)));
        assertTrue(ChatLayout.openTab(grey, null) != null);
        assertTrue(ChatLayout.openTab(rangers, null) != null);

        // A switch to a character not in the Rangers; another of the
        // account's characters is still in it.
        ClientChatIdentitySelection.accept(sync(Collections.singletonList(GREY),
                Arrays.asList(GREY, RANGERS)));
        assertTrue("it stays in its window", WindowLayout.holds(rangers));
        assertFalse("hidden", ClientChatChannelState.isAvailable(rangers));

        // The server could not say: nothing leaves on a guess.
        ClientChatIdentitySelection.accept(new LostTalesChatIdentitySyncPacket(
                Collections.<UUID>emptyList(), fellowships(Collections.singletonList(GREY)),
                null));
        assertTrue(WindowLayout.holds(rangers));

        // No character of the account is in it any more.
        ClientChatIdentitySelection.accept(sync(Collections.singletonList(GREY),
                Collections.singletonList(GREY)));
        assertFalse("it leaves the windows", WindowLayout.holds(rangers));
        assertTrue("the one still travelled with stays", WindowLayout.holds(grey));
        assertTrue(ClientChatChannelState.isAvailable(grey));
    }

    private static ConversationPage conversation(UUID fellowship) {
        return ConversationPage.of(ChatChannel.FELLOWSHIP, fellowship.toString());
    }

    /** The server's answer: the character played in {@code played}, the account's characters in {@code account}. */
    private static LostTalesChatIdentitySyncPacket sync(List<UUID> played,
                                                        List<UUID> account) {
        return new LostTalesChatIdentitySyncPacket(Collections.<UUID>emptyList(),
                fellowships(played), account);
    }

    private static List<ChatFellowship> fellowships(List<UUID> ids) {
        ChatFellowship[] fellowships = new ChatFellowship[ids.size()];
        for (int index = 0; index < ids.size(); index++) {
            fellowships[index] = new ChatFellowship(ids.get(index),
                    "Company " + index, 0x123456);
        }
        return Arrays.asList(fellowships);
    }
}
