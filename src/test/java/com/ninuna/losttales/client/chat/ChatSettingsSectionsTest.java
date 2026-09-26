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
     * its own, only its conversations, and a gate open to it once
     * crashed the window as it opened (2026-09-24).
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
            if (row.id != null && row.id.startsWith("channel:mute:")) {
                listed.add(row.id.substring("channel:mute:".length()));
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
}
