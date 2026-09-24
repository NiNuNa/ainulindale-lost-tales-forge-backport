package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A few-word setting steps forward on a click and back on a right-click,
 * round from either end, and the game's scale and opacity step a tenth
 * at a time, never under a tenth (Nils, 2026-09-24, S5 a).
 */
public final class ChatSettingsTest {
    @After
    public void cleanUp() {
        ClientChatChannelState.clear();
        ChatWindowLayout.reset();
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
        for (ChatMenu.Entry row : ChatSettings.channelRows()) {
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
        assertTrue(!shown.isEmpty());
        assertEquals(shown, listed);
    }
    @Test
    public void aStepGoesRoundFromEitherEnd() {
        assertEquals(1, ChatSettings.nextIndex(0, 3, false));
        assertEquals(0, ChatSettings.nextIndex(2, 3, false));
        assertEquals(2, ChatSettings.nextIndex(0, 3, true));
        assertEquals(1, ChatSettings.nextIndex(2, 3, true));
        assertEquals("a value the words do not hold steps to the first",
                0, ChatSettings.nextIndex(-1, 3, false));
        assertEquals("or back to the last", 2,
                ChatSettings.nextIndex(-1, 3, true));
    }

    @Test
    public void theSearchKeepsWhatHoldsItsWordsUnderItsHeaderAndGroup() {
        ChatMenu.Entry global = ChatMenu.Entry.group("Global", null, 0);
        ChatMenu.Entry globalMute = new ChatMenu.Entry("a", "Mute");
        ChatMenu.Entry globalHide = new ChatMenu.Entry("b", "Hide");
        ChatMenu.Entry party = ChatMenu.Entry.group("Party", null, 0);
        ChatMenu.Entry partyMute = new ChatMenu.Entry("c", "Mute");
        ChatMenu.Entry partyHide = new ChatMenu.Entry("d", "Hide");
        List<ChatMenu.Entry> members = Arrays.asList(global, globalMute,
                globalHide, party, partyMute, partyHide);

        List<ChatMenu.Entry> all = new ArrayList<ChatMenu.Entry>();
        ChatSettings.section(all, "channels", members, "");
        assertEquals("nothing typed keeps everything under its header",
                members.size() + 1, all.size());
        assertTrue(all.get(0).header);

        List<ChatMenu.Entry> byGroup = new ArrayList<ChatMenu.Entry>();
        ChatSettings.section(byGroup, "channels", members, "party");
        assertEquals("a group whose name holds the words is kept whole",
                Arrays.asList(party, partyMute, partyHide),
                byGroup.subList(1, byGroup.size()));

        List<ChatMenu.Entry> byRow = new ArrayList<ChatMenu.Entry>();
        ChatSettings.section(byRow, "channels", members, "hide");
        assertEquals("a row is kept under its group's name",
                Arrays.asList(global, globalHide, party, partyHide),
                byRow.subList(1, byRow.size()));

        List<ChatMenu.Entry> none = new ArrayList<ChatMenu.Entry>();
        ChatSettings.section(none, "channels", members, "nothing here");
        assertTrue("a section with nothing left is left out", none.isEmpty());
    }

    @Test
    public void aShortcutIsFoundByItsKeys() {
        ChatMenu.Entry search = ChatMenu.Entry.passive("Search the window")
                .withKeys(Integer.valueOf(org.lwjgl.input.Keyboard.KEY_F));
        List<ChatMenu.Entry> kept = new ArrayList<ChatMenu.Entry>();
        ChatSettings.section(kept, "shortcuts",
                Arrays.asList(search), "f");
        assertEquals(2, kept.size());
    }

    @Test
    public void aShareReadsAsTheTenthItLandsOn() {
        assertEquals(100, ChatSettings.percentOf(1.0F));
        assertEquals(70, ChatSettings.percentOf(0.73F));
        assertEquals(80, ChatSettings.percentOf(0.76F));
        assertEquals("never under a tenth", 10, ChatSettings.percentOf(0.0F));
        assertEquals(100, ChatSettings.percentOf(1.4F));
    }
}
