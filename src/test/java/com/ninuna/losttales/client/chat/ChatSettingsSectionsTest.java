package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.window.MenuWindow;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatSettingsSectionsTest {
    @After
    public void cleanUp() {
        ClientChatChannelState.clear();
        ChatLayout.reset();
    }

    /**
     * The Channels section lists every channel the player can see, in
     * the order the chat shows them. The whisper channel has no tab of
     * its own, only its conversations, and a gate open to it must not
     * crash the window as it opens.
     */
    @Test
    public void theChannelsSectionPassesOverTheWhisperChannel() {
        List<String> every = new ArrayList<String>();
        for (ChatChannel channel : ChatChannel.values()) {
            every.add(channel.getId());
        }
        assertTrue(every.contains(ChatChannel.WHISPER.getId()));
        ClientChatChannelState.setChannelGates(every, every);

        List<String> listed = new ArrayList<String>();
        for (MenuWindow.Entry row
                : new ChatSettingsSections.ChannelsSection().rows()) {
            if (row.id != null && row.id.startsWith("channel:notify:")) {
                listed.add(row.id.substring("channel:notify:".length()));
            }
        }
        List<String> shown = new ArrayList<String>();
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            if (ClientChatChannelState.isAvailable(channel)) {
                shown.add(ChatTab.of(channel).id());
            }
        }
        assertFalse(shown.isEmpty());
        assertEquals(shown, listed);
    }

    /**
     * A conversation's notification choice cycles as every few-word
     * option does: a click steps it on, a right-click back, round the
     * three, and the row reads the choice it stands on.
     */
    @Test
    public void theNotificationRowStepsOnAndBack() {
        List<String> every = new ArrayList<String>();
        for (ChatChannel channel : ChatChannel.values()) {
            every.add(channel.getId());
        }
        ClientChatChannelState.setChannelGates(every, every);
        ChatSettingsSections.ChannelsSection section =
                new ChatSettingsSections.ChannelsSection();
        MenuWindow.Entry row = notifyRow(section, ChatChannel.OOC);
        assertEquals(ChatNotification.EVERYTHING,
                ChatLayout.notification(ChatTab.of(ChatChannel.OOC)));
        section.take(row, false);
        assertEquals(ChatNotification.ONLY_MENTIONS,
                ChatLayout.notification(ChatTab.of(ChatChannel.OOC)));
        section.take(row, false);
        assertEquals(ChatNotification.NOTHING,
                ChatLayout.notification(ChatTab.of(ChatChannel.OOC)));
        section.take(row, false);
        assertEquals(ChatNotification.EVERYTHING,
                ChatLayout.notification(ChatTab.of(ChatChannel.OOC)));
        section.take(row, true);
        assertEquals(ChatNotification.NOTHING,
                ChatLayout.notification(ChatTab.of(ChatChannel.OOC)));
        assertEquals(ChatNotification.NOTHING.labelKey(),
                notifyRow(section, ChatChannel.OOC).value);
    }

    private static MenuWindow.Entry notifyRow(
            ChatSettingsSections.ChannelsSection section, ChatChannel channel) {
        String id = "channel:notify:" + ChatTab.of(channel).id();
        for (MenuWindow.Entry row : section.rows()) {
            if (id.equals(row.id)) {
                return row;
            }
        }
        throw new AssertionError("no notification row for " + channel.getId());
    }
}
