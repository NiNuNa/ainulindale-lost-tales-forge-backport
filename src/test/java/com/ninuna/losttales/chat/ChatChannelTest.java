package com.ninuna.losttales.chat;

import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ChatChannelTest {

    @Test
    public void presentationOrderIsGlobalProximityFactionOocParty() {
        assertEquals(Arrays.asList(ChatChannel.GLOBAL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.OOC, ChatChannel.PARTY,
                ChatChannel.OPERATOR, ChatChannel.CLIENT_CONSOLE,
                ChatChannel.SERVER_CONSOLE),
                ChatChannel.presentationOrder());
        // Every channel but Whisper is presented exactly once (whispers
        // are tabs per conversation, never a channel tab).
        HashSet<ChatChannel> presented = new HashSet<ChatChannel>(
                Arrays.asList(ChatChannel.values()));
        presented.remove(ChatChannel.WHISPER);
        assertEquals(presented,
                new HashSet<ChatChannel>(ChatChannel.presentationOrder()));
        assertEquals(ChatChannel.values().length - 1,
                ChatChannel.presentationOrder().size());
        assertEquals("whisper", ChatChannel.WHISPER.getId());
    }

    /** Each id is the channel's name in lower case, and Out of Character's is ooc. */
    @Test
    public void idsAreTheChannelsNames() {
        assertEquals("global", ChatChannel.GLOBAL.getId());
        assertEquals("proximity", ChatChannel.PROXIMITY.getId());
        assertEquals("party", ChatChannel.PARTY.getId());
        assertEquals("faction", ChatChannel.FACTION.getId());
        assertEquals("ooc", ChatChannel.OOC.getId());
        assertEquals("operator", ChatChannel.OPERATOR.getId());
        assertEquals("client_console", ChatChannel.CLIENT_CONSOLE.getId());
        assertEquals("server_console", ChatChannel.SERVER_CONSOLE.getId());
        assertEquals(null, ChatChannel.fromId("all"));
        assertEquals(null, ChatChannel.fromId("admin"));
        assertEquals(null, ChatChannel.fromId("console"));
        assertEquals(ChatChannel.PARTY, ChatChannel.fromId(" Party "));
        // Every id resolves back to its own channel, so no two collide.
        for (ChatChannel channel : ChatChannel.values()) {
            assertEquals(channel, ChatChannel.fromId(channel.getId()));
        }
        assertEquals(null, ChatChannel.fromId("trade"));
        assertEquals(null, ChatChannel.fromId(null));
    }

    /** Out of Character is one channel under the id {@code ooc}; the bare word Discord names nothing. */
    @Test
    public void outOfCharacterIsOneChannel() {
        assertEquals(null, ChatChannel.fromId("discord"));
        assertEquals(ChatChannel.OOC, ChatChannel.fromId(" OOC "));
        assertEquals("ooc", ChatChannel.OOC.getId());
        assertEquals("Out of Character", ChatChannel.OOC.getDisplayName());
        assertEquals(ChatRecipientRule.SELF,
                ChatChannel.CLIENT_CONSOLE.getRecipientRule());
        assertEquals(ChatRecipientRule.CONSOLE_READERS,
                ChatChannel.SERVER_CONSOLE.getRecipientRule());
        assertEquals("Client Console", ChatChannel.CLIENT_CONSOLE.getDisplayName());
        assertEquals("Server Console",
                ChatChannel.SERVER_CONSOLE.getDisplayName());
        // Neither console ever leaves the game.
        assertEquals(false, ChatChannel.CLIENT_CONSOLE.isBridgeable());
        assertEquals(false, ChatChannel.SERVER_CONSOLE.isBridgeable());
        assertEquals(ChatRecipientRule.OPERATORS,
                ChatChannel.OPERATOR.getRecipientRule());
        assertEquals(ChatPresentationMode.OUT_OF_CHARACTER,
                ChatChannel.OPERATOR.getPresentation());
    }

    @Test
    public void accessSaysWhatEachChannelAsksFor() {
        assertEquals(ChatChannelAccess.NONE, ChatChannel.GLOBAL.getAccess());
        assertEquals(ChatChannelAccess.NONE,
                ChatChannel.PROXIMITY.getAccess());
        assertEquals(ChatChannelAccess.NONE, ChatChannel.OOC.getAccess());
        assertEquals(ChatChannelAccess.NONE,
                ChatChannel.CLIENT_CONSOLE.getAccess());
        assertEquals(ChatChannelAccess.NONE,
                ChatChannel.WHISPER.getAccess());
        assertEquals(ChatChannelAccess.CHARACTER_FACTION,
                ChatChannel.FACTION.getAccess());
        assertEquals(ChatChannelAccess.PARTY_MEMBERSHIP,
                ChatChannel.PARTY.getAccess());
        assertEquals(ChatChannelAccess.NONE,
                ChatChannel.OPERATOR.getAccess());
        // OOC is a room everyone is in, bridged or not; the
        // bridge is the server's configuration and never a gate.
        assertEquals(ChatRecipientRule.EVERYONE,
                ChatChannel.OOC.getRecipientRule());
        assertEquals(ChatPresentationMode.OUT_OF_CHARACTER,
                ChatChannel.OOC.getPresentation());
    }

    /** Private conversations never leave the game, whatever the bridge is told. */
    @Test
    public void bridgeableMarksTheChannelsThatMayLeaveTheGame() {
        assertEquals(true, ChatChannel.GLOBAL.isBridgeable());
        assertEquals(true, ChatChannel.PROXIMITY.isBridgeable());
        assertEquals(true, ChatChannel.FACTION.isBridgeable());
        assertEquals(true, ChatChannel.OOC.isBridgeable());
        assertEquals(true, ChatChannel.OPERATOR.isBridgeable());
        assertEquals(false, ChatChannel.PARTY.isBridgeable());
        assertEquals(false, ChatChannel.CLIENT_CONSOLE.isBridgeable());
        assertEquals(false, ChatChannel.WHISPER.isBridgeable());
    }

}
