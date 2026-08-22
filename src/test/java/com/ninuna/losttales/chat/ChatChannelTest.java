package com.ninuna.losttales.chat;

import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ChatChannelTest {

    @Test
    public void presentationOrderIsGlobalProximityFactionOocParty() {
        assertEquals(Arrays.asList(ChatChannel.ALL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.OOC, ChatChannel.PARTY,
                ChatChannel.ADMIN, ChatChannel.CONSOLE),
                ChatChannel.presentationOrder());
        // Every channel but Whisper is presented exactly once (whispers
        // are tabs per conversation, never a channel tab); declaration
        // order (the ordinals client view state is indexed by) is left
        // alone.
        HashSet<ChatChannel> presented = new HashSet<ChatChannel>(
                Arrays.asList(ChatChannel.values()));
        presented.remove(ChatChannel.WHISPER);
        assertEquals(presented,
                new HashSet<ChatChannel>(ChatChannel.presentationOrder()));
        assertEquals(ChatChannel.values().length - 1,
                ChatChannel.presentationOrder().size());
        assertEquals("whisper", ChatChannel.WHISPER.getId());
    }

    @Test
    public void wireIdsAreUnchanged() {
        assertEquals("all", ChatChannel.ALL.getId());
        assertEquals("proximity", ChatChannel.PROXIMITY.getId());
        assertEquals("party", ChatChannel.PARTY.getId());
        assertEquals("faction", ChatChannel.FACTION.getId());
        assertEquals("ooc", ChatChannel.OOC.getId());
        assertEquals("admin", ChatChannel.ADMIN.getId());
        assertEquals("console", ChatChannel.CONSOLE.getId());
        assertEquals(ChatChannel.PARTY, ChatChannel.fromId(" Party "));
        // The new channels were appended so existing ordinals are stable.
        assertEquals(4, ChatChannel.OOC.ordinal());
        assertEquals(ChatRecipientRule.SELF,
                ChatChannel.CONSOLE.getRecipientRule());
        assertEquals(ChatRecipientRule.OPERATORS,
                ChatChannel.ADMIN.getRecipientRule());
        assertEquals(ChatIdentityType.ACCOUNT,
                ChatChannel.ADMIN.getIdentityType());
    }
}
