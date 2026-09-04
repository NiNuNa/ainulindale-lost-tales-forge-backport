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
        // Every id resolves back to its own channel, so no two collide.
        for (ChatChannel channel : ChatChannel.values()) {
            assertEquals(channel, ChatChannel.fromId(channel.getId()));
        }
        assertEquals(null, ChatChannel.fromId("trade"));
        assertEquals(null, ChatChannel.fromId(null));
    }

    /**
     * An older build kept a Discord channel of its own; OOC &amp; Discord
     * took it in, and its id still names that channel wherever a file
     * or a packet from that build carries it.
     */
    @Test
    public void theOldDiscordIdNamesOocAndDiscord() {
        assertEquals(ChatChannel.OOC, ChatChannel.fromId("discord"));
        assertEquals(ChatChannel.OOC, ChatChannel.fromId(" Discord "));
        assertEquals("ooc", ChatChannel.OOC.getId());
        assertEquals("OOC & Discord", ChatChannel.OOC.getDisplayName());
        assertEquals(ChatRecipientRule.SELF,
                ChatChannel.CONSOLE.getRecipientRule());
        assertEquals(ChatRecipientRule.OPERATORS,
                ChatChannel.ADMIN.getRecipientRule());
        assertEquals(ChatIdentityType.ACCOUNT,
                ChatChannel.ADMIN.getIdentityType());
    }

    @Test
    public void accessSaysWhatEachChannelAsksFor() {
        assertEquals(ChatChannelAccess.NONE, ChatChannel.ALL.getAccess());
        assertEquals(ChatChannelAccess.NONE,
                ChatChannel.PROXIMITY.getAccess());
        assertEquals(ChatChannelAccess.NONE, ChatChannel.OOC.getAccess());
        assertEquals(ChatChannelAccess.NONE,
                ChatChannel.CONSOLE.getAccess());
        assertEquals(ChatChannelAccess.NONE,
                ChatChannel.WHISPER.getAccess());
        assertEquals(ChatChannelAccess.CHARACTER_FACTION,
                ChatChannel.FACTION.getAccess());
        assertEquals(ChatChannelAccess.PARTY_MEMBERSHIP,
                ChatChannel.PARTY.getAccess());
        assertEquals(ChatChannelAccess.OPERATOR,
                ChatChannel.ADMIN.getAccess());
        // OOC & Discord is a room everyone is in, bridged or not; the
        // bridge is the server's configuration and never a gate.
        assertEquals(ChatRecipientRule.GLOBAL,
                ChatChannel.OOC.getRecipientRule());
        assertEquals(ChatIdentityType.ACCOUNT,
                ChatChannel.OOC.getIdentityType());
    }

    /** Private conversations never leave the game, whatever the bridge is told. */
    @Test
    public void bridgeableMarksTheChannelsThatMayLeaveTheGame() {
        assertEquals(true, ChatChannel.ALL.isBridgeable());
        assertEquals(true, ChatChannel.PROXIMITY.isBridgeable());
        assertEquals(true, ChatChannel.FACTION.isBridgeable());
        assertEquals(true, ChatChannel.OOC.isBridgeable());
        assertEquals(true, ChatChannel.ADMIN.isBridgeable());
        assertEquals(false, ChatChannel.PARTY.isBridgeable());
        assertEquals(false, ChatChannel.CONSOLE.isBridgeable());
        assertEquals(false, ChatChannel.WHISPER.isBridgeable());
    }

    @Test
    public void descriptorCarriesExactlyWhatTheChannelStates() {
        for (ChatChannel channel : ChatChannel.values()) {
            ChatChannelDescriptor descriptor = channel.getDescriptor();
            assertEquals(channel.isBridgeable(), descriptor.isBridgeable());
            assertEquals(channel.getId(), descriptor.getId());
            assertEquals(channel.getDisplayName(),
                    descriptor.getDisplayName());
            assertEquals(channel.getIdentityType(),
                    descriptor.getIdentityType());
            assertEquals(channel.getRecipientRule(),
                    descriptor.getRecipientRule());
            assertEquals(channel.getAccess(), descriptor.getAccess());
            assertEquals(channel.getDisplayColor(),
                    descriptor.getDisplayColor());
        }
    }
}
