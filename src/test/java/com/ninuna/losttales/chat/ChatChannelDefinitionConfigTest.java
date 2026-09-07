package com.ninuna.losttales.chat;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The channels a server defines for itself. The config says which and
 * how they route; everything a config cannot answer for — a party, a
 * faction, the two parties of a whisper, one player's own console — it
 * is not allowed to name, and the code's own channels are not its to
 * redefine.
 */
public final class ChatChannelDefinitionConfigTest {

    private final List<String> warnings = new ArrayList<String>();
    private final ChatRoleConfig.Warnings collector =
            new ChatRoleConfig.Warnings() {
                @Override
                public void warn(String message) {
                    ChatChannelDefinitionConfigTest.this.warnings.add(message);
                }
            };

    @After
    public void tearDown() {
        ChatChannel.resetToBuiltIn();
    }

    @Test
    public void anEntryDescribesAChannel() {
        List<ChatChannelDescriptor> defined = parse(
                "trade=name:Trade;rule:global;colour:C9A227;ooc:true;bridge:true");

        assertEquals(1, defined.size());
        ChatChannelDescriptor channel = defined.get(0);
        assertEquals("trade", channel.getId());
        assertEquals("Trade", channel.getDisplayName());
        assertEquals(ChatRecipientRule.GLOBAL, channel.getRecipientRule());
        assertEquals(ChatPresentationMode.OUT_OF_CHARACTER,
                channel.getPresentation());
        assertEquals(0xC9A227, channel.getDisplayColor());
        assertTrue(channel.isBridgeable());
        assertEquals("a config names no conversation of its own",
                ChatChannelScope.NONE, channel.getScope());
        assertTrue(this.warnings.isEmpty());
    }

    /** Left to itself an entry is a global, in-character, unbridged channel. */
    @Test
    public void anEntryLeftToItselfTakesTheQuietDefaults() {
        ChatChannelDescriptor channel = parse("tavern").get(0);

        assertEquals("tavern", channel.getId());
        assertEquals("named by its id when it names nothing else",
                "tavern", channel.getDisplayName());
        assertEquals(ChatRecipientRule.GLOBAL, channel.getRecipientRule());
        assertEquals(ChatPresentationMode.IN_CHARACTER, channel.getPresentation());
        assertFalse("nothing is bridged unless the file says so",
                channel.isBridgeable());
    }

    @Test
    public void proximityAndOperatorRoutingMayBeNamed() {
        assertEquals(ChatRecipientRule.PROXIMITY,
                parse("near=rule:proximity").get(0).getRecipientRule());
        assertEquals(ChatRecipientRule.OPERATORS,
                parse("staff=rule:operators").get(0).getRecipientRule());
    }

    /**
     * A rule that needs something the config cannot supply is refused
     * rather than quietly becoming a global channel: a party channel with
     * no party would reach everyone.
     */
    @Test
    public void aRoutingAConfigCannotDescribeIsRefused() {
        assertTrue(parse("mine=rule:party").isEmpty());
        assertTrue(parse("kin=rule:faction").isEmpty());
        assertTrue(parse("quiet=rule:whisper").isEmpty());
        assertTrue(parse("notes=rule:self").isEmpty());
        assertEquals(4, this.warnings.size());
        for (String warning : this.warnings) {
            assertTrue(warning, warning.contains("routing"));
        }
    }

    /** The code's own channels are not a config's to take over. */
    @Test
    public void aBuiltInChannelIsNeverRedefined() {
        assertTrue(parse("all=name:Mine;rule:global").isEmpty());
        assertTrue(parse("admin=name:Mine;rule:global").isEmpty());
        assertEquals(2, this.warnings.size());
        assertTrue(this.warnings.get(0).contains("already has"));
    }

    /** An id is what everything names a channel by, so it is bounded. */
    @Test
    public void anIdThatNothingCouldCarryIsRefused() {
        assertTrue(parse("=name:Nameless").isEmpty());
        assertTrue(parse("Has Spaces=rule:global").isEmpty());
        assertTrue(parse("way_too_long_a_channel_id=rule:global").isEmpty());
        assertTrue(parse("bad-punctuation=rule:global").isEmpty());
        assertEquals(4, this.warnings.size());
    }

    /** The same id twice is one channel and a warning, not two. */
    @Test
    public void anIdDefinedTwiceIsTakenOnce() {
        List<ChatChannelDescriptor> defined = parse(
                "trade=name:First;rule:global", "trade=name:Second;rule:global");

        assertEquals(1, defined.size());
        assertEquals("First", defined.get(0).getDisplayName());
        assertEquals(1, this.warnings.size());
        assertTrue(this.warnings.get(0).contains("twice"));
    }

    /** A colour that is not one is reported and the default used. */
    @Test
    public void aColourThatIsNotOneIsReported() {
        ChatChannelDescriptor channel = parse("trade=colour:notacolour").get(0);

        assertEquals(1, this.warnings.size());
        assertTrue(this.warnings.get(0).contains("no colour"));
        assertTrue("a channel still comes out of it",
                channel.getDisplayColor() != 0);
    }

    /** Blank lines and comments are not entries. */
    @Test
    public void blankLinesAndCommentsAreSkippedInSilence() {
        assertTrue(parse("", "   ", "# a note").isEmpty());
        assertTrue(this.warnings.isEmpty());
    }

    /** A defined channel is a channel: registered, it behaves as one. */
    @Test
    public void aDefinedChannelBecomesAChannel() {
        ChatChannel trade = ChatChannel.register(
                parse("trade=name:Trade;rule:global").get(0));

        assertEquals(trade, ChatChannel.fromId("trade"));
        assertFalse("and is known not to be the code's own",
                ChatChannel.isBuiltIn(trade));
        assertTrue(ChatChannel.isBuiltIn(ChatChannel.ALL));
    }

    private List<ChatChannelDescriptor> parse(String... entries) {
        return ChatRoleConfig.parseChannelDefinitions(entries, this.collector);
    }
}
