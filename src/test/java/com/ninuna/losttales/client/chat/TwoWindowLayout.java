package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.window.WindowLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Two windows to move tabs between, for the tests that need more than a
 * new player's one: w1 top-left with the two consoles and Operator,
 * Client Console in front, and w2 bottom-left with every other channel,
 * Global in front. Nothing is hidden.
 */
public final class TwoWindowLayout {
    private static final List<ChatTab> CONSOLES = Arrays.asList(
            ChatTab.of(ChatChannel.CLIENT_CONSOLE),
            ChatTab.of(ChatChannel.SERVER_CONSOLE),
            ChatTab.of(ChatChannel.OPERATOR));

    private static final Runnable TWO_WINDOWS = new Runnable() {
        @Override
        public void run() {
            WindowLayout.addWindow(CONSOLES, CONSOLES.get(0), 0.0D, 0.0D);
            List<ChatTab> conversations = new ArrayList<ChatTab>();
            for (ChatChannel channel : ChatChannel.presentationOrder()) {
                if (!CONSOLES.contains(ChatTab.of(channel))) {
                    conversations.add(ChatTab.of(channel));
                }
            }
            WindowLayout.addWindow(conversations,
                    ChatTab.of(ChatChannel.GLOBAL), 0.0D, 100.0D);
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
    }
}
