package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowTab;
import java.util.ArrayList;
import java.util.List;

/**
 * The chat's layout read and changed by channel, for tests that name
 * channels rather than tabs. Each answer is read off the live layout.
 */
public final class ChatLayoutViews {
    private ChatLayoutViews() {}

    /** The channels of a window's conversations, in row order; whispers as WHISPER. */
    public static List<ChatChannel> channelsOf(Window window) {
        List<ChatChannel> result = new ArrayList<ChatChannel>();
        for (WindowTab tab : window.getTabs()) {
            ChatTab conversation = ChatTab.from(tab);
            if (conversation != null) {
                result.add(conversation.getChannel());
            }
        }
        return result;
    }

    /** The channel of the conversation in front of a window, or null. */
    public static ChatChannel frontChannelOf(Window window) {
        ChatTab front = ChatTab.frontOf(window);
        return front == null ? null : front.getChannel();
    }

    /** The channels of the conversations open, in window and row order. */
    public static List<ChatChannel> orderChannels() {
        List<ChatChannel> result = new ArrayList<ChatChannel>();
        for (WindowTab each : WindowLayout.order()) {
            ChatTab tab = ChatTab.from(each);
            if (tab != null) {
                result.add(tab.getChannel());
            }
        }
        return result;
    }

    /**
     * Reopens a closed channel as the last tab of the window that takes
     * it, its front tab left alone: false for one already open, or with
     * no window to take it.
     */
    public static boolean reopen(ChatChannel channel) {
        return reopenIn(channel, null);
    }

    /**
     * Reopens a closed channel as the given window's last tab, or where a
     * conversation opens while that window is locked: false for a missing
     * window or a channel already open.
     */
    public static boolean reopen(ChatChannel channel, String windowId) {
        return WindowLayout.window(windowId) != null
                && reopenIn(channel, windowId);
    }

    private static boolean reopenIn(ChatChannel channel, String windowId) {
        ChatTab tab = ChatTab.of(channel);
        return tab != null && !WindowLayout.isOpen(tab)
                && WindowLayout.openTab(tab, windowId) != null;
    }
}
