package com.ninuna.losttales.chat;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The icons a server puts on its channels. The file names a channel in
 * force and an emoji or an item for it; anything else is reported and
 * skipped, and the catalogue takes what is left.
 */
public final class ChatChannelIconConfigTest {

    private final List<String> warnings = new ArrayList<String>();
    private final ChatRoleConfig.Warnings collector =
            new ChatRoleConfig.Warnings() {
                @Override
                public void warn(String message) {
                    ChatChannelIconConfigTest.this.warnings.add(message);
                }
            };

    @After
    public void tearDown() {
        ChatChannel.resetToBuiltIn();
        ChatChannelIconCatalog.resetToDefaults();
    }

    @Test
    public void anEntryPutsAnIconOnAChannel() {
        Map<String, ChatChannelIconSpec> icons = parse(
                "operator=item:minecraft:iron_sword",
                "Party = emoji:joy",
                "ooc=slight_smile");

        assertEquals(3, icons.size());
        assertEquals(ChatChannelIconSpec.parse("item:minecraft:iron_sword"),
                icons.get(ChatChannel.OPERATOR.getId()));
        assertEquals(ChatChannelIconSpec.parse("emoji:joy"),
                icons.get(ChatChannel.PARTY.getId()));
        assertEquals(ChatChannelIconSpec.parse("emoji:slight_smile"),
                icons.get(ChatChannel.OOC.getId()));
        assertTrue(this.warnings.isEmpty());
    }

    @Test
    public void aChannelTheFileDefinesMayWearOne() {
        ChatChannel.installDefined(ChatRoleConfig.parseChannelDefinitions(
                new String[] {"trade=name:Trade;rule:everyone"}, this.collector),
                new ChatChannel.Warnings() {
                    @Override
                    public void warn(String message) {
                        ChatChannelIconConfigTest.this.warnings.add(message);
                    }
                });
        Map<String, ChatChannelIconSpec> icons = parse(
                "trade=item:minecraft:emerald");

        assertEquals(1, icons.size());
        assertEquals(ChatChannelIconSpec.parse("item:minecraft:emerald"),
                icons.get("trade"));
        assertTrue(this.warnings.isEmpty());
    }

    @Test
    public void aChannelNotInForceIsReported() {
        assertTrue(parse("bazaar=emoji:joy").isEmpty());
        assertEquals(1, this.warnings.size());
        assertTrue(this.warnings.get(0).contains("no channel in force"));
    }

    @Test
    public void textThatIsNoIconIsReported() {
        assertTrue(parse("operator=item:").isEmpty());
        assertTrue(parse("operator=").isEmpty());
        assertTrue(parse("operator").isEmpty());
        assertEquals(3, this.warnings.size());
        for (String warning : this.warnings) {
            assertTrue(warning, warning.contains("no icon"));
        }
    }

    @Test
    public void anEmojiThisBuildDoesNotHaveIsReported() {
        assertTrue(parse("operator=emoji:no_such_face").isEmpty());
        assertEquals(1, this.warnings.size());
        assertTrue(this.warnings.get(0).contains("does not have"));
    }

    @Test
    public void aChannelNamedTwiceKeepsItsFirstIcon() {
        Map<String, ChatChannelIconSpec> icons = parse(
                "operator=emoji:joy", "operator=emoji:smile");
        assertEquals(1, icons.size());
        assertEquals(ChatChannelIconSpec.parse("emoji:joy"),
                icons.get(ChatChannel.OPERATOR.getId()));
        assertEquals(1, this.warnings.size());
        assertTrue(this.warnings.get(0).contains("twice"));
    }

    @Test
    public void blankLinesAndCommentsAreSkippedInSilence() {
        assertTrue(parse("", "   ", "# operator=emoji:joy").isEmpty());
        assertTrue(this.warnings.isEmpty());
    }

    @Test
    public void theCatalogueTakesWhatWasParsed() {
        ChatChannelIconCatalog.install(parse("operator=item:minecraft:iron_sword"));
        assertEquals(ChatChannelIconSpec.parse("item:minecraft:iron_sword"),
                ChatChannelIconCatalog.current().get(ChatChannel.OPERATOR.getId()));

        ChatChannelIconCatalog.install(Collections.<String, ChatChannelIconSpec>emptyMap());
        assertTrue(ChatChannelIconCatalog.current().isEmpty());

        ChatChannelIconCatalog.install(null);
        assertTrue(ChatChannelIconCatalog.current().isEmpty());
        assertFalse(ChatChannelIconCatalog.current().containsKey("operator"));
    }

    private Map<String, ChatChannelIconSpec> parse(String... entries) {
        return ChatRoleConfig.parseChannelIcons(entries, this.collector);
    }
}
