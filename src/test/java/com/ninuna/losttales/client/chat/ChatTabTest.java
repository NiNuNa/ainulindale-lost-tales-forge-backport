package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.PageOption;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.window.Tearing;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowLayoutStore;
import com.ninuna.losttales.client.window.WindowTab;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ChatTabTest {

    @After
    public void cleanUp() {
        TwoWindowLayout.reset();
        ClientChatChannelState.clear();
        ChatChannel.resetToBuiltIn();
    }

    /**
     * A channel a server defines has a tab like any other. The set of
     * channels is open and they are registered long after this class is
     * first read, so a tab made once for each channel up front would
     * leave every server-defined one with none at all.
     */
    @Test
    public void aChannelTheServerDefinesHasATab() {
        ChatChannel.installDefined(java.util.Collections.singletonList(
                tradeDescriptor()), null);
        ChatChannel trade = ChatChannel.fromId("trade");
        assertNotNull(trade);

        ChatTab tab = ChatTab.of(trade);
        assertNotNull("a server's own channel is a tab like any other", tab);
        assertEquals("trade", tab.id());
        assertSame(tab, ChatTab.of(trade));
        assertEquals(tab, ChatTab.fromId("trade"));
        // It starts as every channel does: chiming for mentions alone,
        // every line in the feed.
        assertEquals(ChatLineChoice.ONLY_MENTIONS, ChatLayout.notification(tab));
        assertEquals(ChatLineChoice.EVERYTHING, ChatLayout.feedChoice(tab));
    }

    /**
     * A conversation's options, with nothing unread: Mark as Read and Jump
     * to First Unread greyed with their reason, then its own settings,
     * Notifications and Show in Feed, in a group of their own; each setting
     * steps on with a click and back with a right-click round the same
     * three words, reads the choice it stands on, and keeps the menu open.
     */
    @Test
    public void aConversationsOptionsStepBothChoices() {
        ClientChatChannelViews.clear();
        TwoWindowLayout.reset();
        ChatTab ooc = ChatTab.of(ChatChannel.OOC);
        List<String> ids = new java.util.ArrayList<String>();
        for (PageOption option : ooc.options()) {
            ids.add(option.id);
        }
        assertEquals(Arrays.asList("mark_read", "jump_unread", "notify",
                "feed"), ids);
        assertFalse(ooc.options().get(0).isAvailable());
        assertFalse(ooc.options().get(1).isAvailable());
        assertFalse(ooc.options().get(1).group().equals(
                ooc.options().get(2).group()));
        assertEquals(ChatLineChoice.ONLY_MENTIONS.labelKey(),
                ooc.options().get(2).value);
        assertEquals(ChatLineChoice.EVERYTHING.labelKey(),
                ooc.options().get(3).value);
        // Show in Feed at Everything rests lit.
        assertTrue(ooc.options().get(3).on);
        assertTrue(ooc.takeOption("feed", false));
        assertEquals(ChatLineChoice.ONLY_MENTIONS, ChatLayout.feedChoice(ooc));
        assertTrue(ooc.takeOption("notify", true));
        assertEquals(ChatLineChoice.EVERYTHING, ChatLayout.notification(ooc));
        assertEquals(ChatLineChoice.ONLY_MENTIONS, ChatLayout.feedChoice(ooc));
        assertTrue(ooc.takeOption("notify", false));
        assertTrue(ooc.takeOption("notify", false));
        assertTrue(ooc.isMuted());
    }

    /**
     * The registry hands out a new object for a channel every time it is
     * filled, and an access broadcast fills it on every login, logout,
     * mute and role change. A tab has to survive that, or a window's
     * tabs and its choices all quietly stop matching.
     */
    @Test
    public void aTabSurvivesItsChannelBeingRegisteredAgain() {
        ChatChannel.installDefined(java.util.Collections.singletonList(
                tradeDescriptor()), null);
        ChatTab before = ChatTab.of(ChatChannel.fromId("trade"));

        ChatChannel.installDefined(java.util.Collections.singletonList(
                tradeDescriptor()), null);
        ChatTab after = ChatTab.of(ChatChannel.fromId("trade"));

        assertEquals("the same tab, whichever object names the channel",
                before, after);
        assertEquals(before.hashCode(), after.hashCode());
    }

    private static com.ninuna.losttales.chat.ChatChannelDescriptor tradeDescriptor() {
        return new com.ninuna.losttales.chat.ChatChannelDescriptor("trade",
                "Trade", com.ninuna.losttales.chat.ChatPresentationMode.IN_CHARACTER,
                com.ninuna.losttales.chat.ChatRecipientRule.EVERYONE,
                com.ninuna.losttales.chat.ChatChannelAccess.NONE,
                0xC9A227, false,
                com.ninuna.losttales.chat.ChatChannelScope.NONE);
    }

    @Test
    public void plainTabsAreSharedAndWhispersCompareByPartner() {
        assertSame(ChatTab.of(ChatChannel.OOC), ChatTab.of(ChatChannel.OOC));
        assertFalse(ChatTab.of(ChatChannel.OOC).isWhisper());
        assertEquals("ooc", ChatTab.of(ChatChannel.OOC).id());
        assertSame(ChatTab.of(ChatChannel.OOC), ChatTab.fromId("ooc"));
        ChatTab steve = ChatTab.whisper("Steve", "");
        assertNotNull(steve);
        assertTrue(steve.isWhisper());
        assertEquals(ChatChannel.WHISPER, steve.getChannel());
        assertEquals("Steve", steve.getPartner());
        assertEquals(steve, ChatTab.whisper("steve ", ""));
        assertEquals(steve.hashCode(), ChatTab.whisper("STEVE", "").hashCode());
        assertFalse(steve.equals(ChatTab.whisper("Alex", "")));
        assertEquals("whisper:Steve", steve.id());
        assertEquals(steve, ChatTab.fromId("whisper:Steve"));
        assertNull(ChatTab.whisper(" ", ""));
        assertNull(ChatTab.fromId("whisper:"));
        assertNull(ChatTab.fromId("whisper"));
        assertNull(ChatTab.fromId("nope"));
        assertNull(ChatTab.fromId(null));
        assertEquals("Steve", ClientChatChannelState.displayName(steve));
        // An NPC of the same name is a conversation of its own.
        ChatTab npc = ChatTab.npc("Steve");
        assertTrue(npc.isNpc());
        assertTrue(npc.isWhisper());
        assertFalse(npc.equals(steve));
        assertEquals("npc:Steve", npc.id());
        assertEquals(npc, ChatTab.fromId("npc:steve"));
        assertFalse(steve.isNpc());
        assertEquals("Steve", ClientChatChannelState.displayName(npc));
        assertTrue(ClientChatChannelState.canSend(npc));
        assertTrue(ClientChatChannelState.isAvailable(steve));
        assertTrue(ClientChatChannelState.canSend(steve));
    }

    @Test
    public void whispersOpenOnceInTheAskingWindowAndPersist() {
        ChatTab steve = ChatLayout.openWhisper("Steve", "", "w2");
        assertNotNull(steve);
        assertTrue(WindowLayout.window("w2").contains(steve));
        // Opened again, the same tab with its original casing, wherever
        // it was asked for; the front tab is left alone.
        ChatTab again = ChatLayout.openWhisper("steve", "", "w1");
        assertEquals(steve, again);
        assertEquals("Steve", again.getPartner());
        assertFalse(WindowLayout.window("w1").contains(steve));
        assertEquals(ChatTab.of(ChatChannel.GLOBAL),
                WindowLayout.window("w2").getActiveTab());
        assertEquals(1, countWhispers());
        // A locked window asked for takes nothing: the whisper opens in
        // the unlocked window that holds conversations.
        assertTrue(WindowLayout.setLocked("w1", true));
        ChatTab alex = ChatLayout.openWhisper("Alex", "", "w1");
        assertFalse(WindowLayout.window("w1").contains(alex));
        assertTrue(WindowLayout.window("w2").contains(alex));
        // Whispers cycle like any tab and never count as closed channels.
        ClientChatChannelState.select(steve);
        assertEquals(steve, ClientChatChannelState.getSelected());
        assertTrue(ChatLayout.closedChannels().isEmpty());
        ChatLayout.close(steve);
        assertTrue(ChatLayout.closedChannels().isEmpty());
        assertFalse(ChatLayout.isOpen(steve));
        assertNull(ChatLayout.openWhisper("", "", "w2"));
        // A conversation's tab is not a window's: no window line names
        // it, and an older file's whisper tab is dropped on load. Its
        // Notifications is kept, on a line of its own; an NPC
        // conversation's never is.
        ChatLayout.setNotification(alex, ChatLineChoice.NOTHING);
        assertTrue(ChatLayout.isMuted(alex));
        List<String> lines = WindowLayoutStore.describe();
        assertTrue(lines.contains("notify\tnothing\twhisper:Alex"));
        for (String line : lines) {
            assertFalse(line.startsWith("window ")
                    && line.contains("whisper:"));
        }
        lines.add("notify\tnothing\tnpc:Grey Wanderer");
        TwoWindowLayout.reset();
        WindowLayoutStore.load(lines);
        assertFalse(ChatLayout.isOpen(alex));
        assertTrue(ChatLayout.isMuted(alex));
        assertFalse(ChatLayout.isMuted(ChatTab.npc("Grey Wanderer")));
        ChatLayout.setNotification(alex, ChatLineChoice.EVERYTHING);
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.OOC,
                ChatChannel.PARTY), ChatLayoutViews.channelsOf(WindowLayout.window("w2")));
        // They also end with the session: closed along with the history.
        ChatTab wanderer = ChatLayout.openTab(
                ChatTab.npc("Grey Wanderer"), "w2");
        Tearing.off(wanderer, 0.0D, 0.0D);
        assertEquals(3, WindowLayout.windows().size());
        ChatLayout.closeConversations();
        assertEquals(2, WindowLayout.windows().size());
        assertFalse(ChatLayout.isOpen(wanderer));
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.OOC,
                ChatChannel.PARTY), ChatLayoutViews.channelsOf(WindowLayout.window("w2")));
    }

    private static int countWhispers() {
        int count = 0;
        for (WindowTab each : WindowLayout.order()) {
            ChatTab tab = ChatTab.from(each);
            if (tab != null && tab.isWhisper()) {
                count++;
            }
        }
        return count;
    }

    /**
     * A conversation is with a person as they present themselves: one
     * player's characters are separate threads, and neither is the one
     * with their account.
     */
    @Test
    public void eachIdentityIsItsOwnConversation() {
        ChatTab account = ChatTab.whisper("Steve", "");
        ChatTab aldric = ChatTab.whisper("Steve", "Aldric");
        ChatTab beren = ChatTab.whisper("Steve", "Beren");
        assertFalse(account.equals(aldric));
        assertFalse(aldric.equals(beren));
        assertEquals("Steve", aldric.getPartner());
        assertEquals("Aldric", aldric.getPartnerIdentity());
        // Naming the account as the identity is the account's own.
        assertEquals(account, ChatTab.whisper("Steve", "Steve"));
        assertEquals(account, ChatTab.whisper("Steve", ""));
        assertEquals("Steve", account.getPartnerIdentity());
    }

    /** Ids round-trip, and one stored before identities existed still reads. */
    @Test
    public void identityIdsRoundTripAndOlderIdsStillRead() {
        ChatTab aldric = ChatTab.whisper("Steve", "Aldric");
        assertEquals(aldric, ChatTab.fromId(aldric.id()));
        assertEquals(ChatTab.whisper("Steve", ""),
                ChatTab.fromId("whisper:Steve"));
        // An identity may hold the separator; the account never can.
        ChatTab odd = ChatTab.whisper("Steve", "A|B");
        assertEquals(odd, ChatTab.fromId(odd.id()));
        assertEquals("A|B", ChatTab.fromId(odd.id()).getPartnerIdentity());
    }

    /**
     * What this player says as one character is that character's
     * conversation: held as another identity, the same partner is
     * another tab, and the key survives the id.
     */
    @Test
    public void eachOwnIdentityHoldsItsOwnConversations() {
        java.util.UUID mine = java.util.UUID.fromString(
                "d0000000-0000-0000-0000-00000000000d");
        ChatTab asAccount = ChatTab.whisper("Steve", "Aldric");
        ChatTab asMine = ChatTab.whisper("Steve", "Aldric", ChatTab.ownerKeyOf(mine));
        assertFalse(asAccount.equals(asMine));
        assertEquals("", asAccount.getOwnerKey());
        assertEquals(mine.toString(), asMine.getOwnerKey());
        assertEquals(asMine, ChatTab.whisper("steve", "aldric",
                mine.toString().toUpperCase(java.util.Locale.ROOT)));
        assertEquals("whisper:Steve|Aldric|own:" + mine, asMine.id());
        assertEquals(asMine, ChatTab.fromId(asMine.id()));
        // Held as a character, a conversation with the account too.
        ChatTab accountAsMine = ChatTab.whisper("Steve", "", ChatTab.ownerKeyOf(mine));
        assertEquals("Steve", accountAsMine.getPartnerIdentity());
        assertEquals("whisper:Steve|Steve|own:" + mine, accountAsMine.id());
        assertEquals(accountAsMine, ChatTab.fromId(accountAsMine.id()));
        assertEquals("", ChatTab.ownerKeyOf(null));
        assertEquals("", ChatTab.whisper("Steve", "Aldric", null).getOwnerKey());
        // An identity may hold the owner mark; only the last segment is one.
        ChatTab odd = ChatTab.whisper("Steve", "own:x", ChatTab.ownerKeyOf(mine));
        assertEquals(odd, ChatTab.fromId(odd.id()));
        assertEquals("own:x", ChatTab.fromId(odd.id()).getPartnerIdentity());
        assertEquals(mine.toString(), ChatTab.fromId(odd.id()).getOwnerKey());
    }
}
