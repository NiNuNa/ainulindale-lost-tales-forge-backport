package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.window.Unlocking;
import com.ninuna.losttales.client.window.WindowLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Two windows to move tabs between, for the tests that need more than a
 * new player's one: w1 with the two consoles and Operator, Client
 * Console in front, and w2 with every other channel, Global in front.
 * Nothing is hidden, and both are unlocked, so their tabs can be moved
 * and closed by hand; a test about the padlock locks them itself.
 */
public final class TwoWindowLayout {
    private static final List<ConversationPage> CONSOLES = Arrays.asList(
            ConversationPage.of(ChatChannel.CLIENT_CONSOLE),
            ConversationPage.of(ChatChannel.SERVER_CONSOLE),
            ConversationPage.of(ChatChannel.OPERATOR));

    private static final Runnable TWO_WINDOWS = new Runnable() {
        @Override
        public void run() {
            WindowLayout.addWindow(CONSOLES, CONSOLES.get(0));
            List<ConversationPage> conversations = new ArrayList<ConversationPage>();
            for (ChatChannel channel : ChatChannel.presentationOrder()) {
                if (!CONSOLES.contains(ConversationPage.of(channel))) {
                    conversations.add(ConversationPage.of(channel));
                }
            }
            WindowLayout.addWindow(conversations,
                    ConversationPage.of(ChatChannel.GLOBAL));
        }
    };

    private TwoWindowLayout() {}

    /** The chat's state cleared and the two windows laid out. */
    public static void reset() {
        ChatLayout.reset();
        WindowLayout.setDefaults(TWO_WINDOWS);
        try {
            ChatLayout.reset();
        } finally {
            WindowLayout.setDefaults(ChatLayout.DEFAULT_WINDOWS);
        }
        Unlocking.all();
    }
}
