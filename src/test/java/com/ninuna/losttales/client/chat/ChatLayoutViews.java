package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowPage;
import java.util.ArrayList;
import java.util.List;

/**
 * The chat's layout read and changed by channel, for tests that name
 * channels rather than pages. Each answer is read off the live layout.
 */
public final class ChatLayoutViews {
    private ChatLayoutViews() {}

    /** The channels of a window's conversations, in row order; whispers as WHISPER. */
    public static List<ChatChannel> channelsOf(Window window) {
        List<ChatChannel> result = new ArrayList<ChatChannel>();
        for (WindowPage page : window.getTabs()) {
            ConversationPage conversation = ConversationPage.from(page);
            if (conversation != null) {
                result.add(conversation.getChannel());
            }
        }
        return result;
    }

    /** The channel of the conversation in front of a window, or null. */
    public static ChatChannel frontChannelOf(Window window) {
        ConversationPage front = ConversationPage.frontOf(window);
        return front == null ? null : front.getChannel();
    }

    /** The channels of the conversations open, in window and row order. */
    public static List<ChatChannel> orderChannels() {
        List<ChatChannel> result = new ArrayList<ChatChannel>();
        for (WindowPage each : WindowLayout.order()) {
            ConversationPage page = ConversationPage.from(each);
            if (page != null) {
                result.add(page.getChannel());
            }
        }
        return result;
    }

    /**
     * Reopens a closed channel as the last page of the window that takes
     * it, its front page left alone: false for one already open, or with
     * no window to take it.
     */
    public static boolean reopen(ChatChannel channel) {
        return reopenIn(channel, null);
    }

    /**
     * Reopens a closed channel as the given window's last page, or where a
     * conversation opens while that window is locked: false for a missing
     * window or a channel already open.
     */
    public static boolean reopen(ChatChannel channel, String windowId) {
        return WindowLayout.window(windowId) != null
                && reopenIn(channel, windowId);
    }

    private static boolean reopenIn(ChatChannel channel, String windowId) {
        ConversationPage page = ConversationPage.of(channel);
        return page != null && !WindowLayout.isOpen(page)
                && WindowLayout.openTab(page, windowId) != null;
    }
}
