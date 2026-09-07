package com.ninuna.losttales.chat;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * What a config-defined channel may not do to the rest of the mod.
 *
 * <p>A channel a config names is the one piece of the chat catalogue that
 * is neither a code constant nor bounded by a packet's own reader: its
 * name and its number come from a file and are written into the access
 * packet, which carries every gate, role and capability a player has. A
 * payload that cannot be encoded costs that player all of them, and a
 * gate that stops matching its channel reads as no gate at all.</p>
 */
public final class ChatChannelDefinitionBoundsTest {

    private final List<String> warnings = new ArrayList<String>();
    private final ChatRoleConfig.Warnings collector =
            new ChatRoleConfig.Warnings() {
                @Override
                public void warn(String message) {
                    ChatChannelDefinitionBoundsTest.this.warnings.add(message);
                }
            };

    @After
    public void tearDown() {
        ChatChannel.resetToBuiltIn();
        ChatChannelGates.resetToDefaults();
    }

    @Test
    public void aNameTooLongToCarryIsCutAndReported() {
        List<ChatChannelDescriptor> defined = ChatRoleConfig
                .parseChannelDefinitions(new String[] {
                        "trade=name:A Very Long Channel Name Indeed;rule:global"
                }, this.collector);

        assertEquals(1, defined.size());
        assertEquals(ChatChannelDescriptor.MAX_DISPLAY_NAME_LENGTH,
                defined.get(0).getDisplayName().length());
        assertTrue(warned("named longer than"));
    }

    @Test
    public void moreChannelsThanCanBeCarriedAreRefusedAndReported() {
        int over = ChatChannel.MAX_DEFINED_CHANNELS + 3;
        String[] entries = new String[over];
        for (int index = 0; index < over; index++) {
            entries[index] = "chan" + index + "=rule:global";
        }

        List<ChatChannelDescriptor> defined =
                ChatRoleConfig.parseChannelDefinitions(entries, this.collector);

        assertEquals(ChatChannel.MAX_DEFINED_CHANNELS, defined.size());
        assertTrue(warned("past the"));
    }

    @Test
    public void aGateFollowsItsChannelThroughAReregistration() {
        // The client re-registers the catalogue from every access packet,
        // and on an integrated server that is the same registry the
        // server routes with. A gate keyed by instance would stop
        // matching, and a channel with no gate is open to everybody.
        ChatChannelDescriptor descriptor = ChatRoleConfig
                .parseChannelDefinitions(new String[] { "trade=rule:global" },
                        this.collector).get(0);
        ChatChannel.installDefined(
                Collections.singletonList(descriptor), null);
        ChatChannel first = ChatChannel.fromId("trade");
        assertNotNull(first);

        Map<ChatChannel, ChatChannelGates.Gate> gates =
                new HashMap<ChatChannel, ChatChannelGates.Gate>();
        gates.put(first, new ChatChannelGates.Gate(
                Collections.singleton("mod"), Collections.singleton("mod")));
        ChatChannelGates.install(ChatChannelGates.of(gates));

        ChatChannel.installDefined(
                Collections.singletonList(descriptor), null);
        ChatChannel second = ChatChannel.fromId("trade");
        assertNotNull(second);
        assertNotSame("the registry hands out a new instance each time",
                first, second);

        assertTrue(ChatChannelGates.current().hasEntry(second));
        assertTrue(ChatChannelGates.current().gateOf(second).asksAnything());
    }

    @Test
    public void installingAChannelSetLeavesTheBuiltInsAlone() {
        ChatChannel.installDefined(ChatRoleConfig.parseChannelDefinitions(
                new String[] { "trade=rule:global" }, this.collector), null);

        assertNotNull(ChatChannel.fromId("trade"));
        assertNotNull(ChatChannel.fromId("all"));
        assertNotNull(ChatChannel.fromId("whisper"));

        // A later set replaces the previous one rather than adding to it.
        ChatChannel.installDefined(ChatRoleConfig.parseChannelDefinitions(
                new String[] { "market=rule:global" }, this.collector), null);

        assertNotNull(ChatChannel.fromId("market"));
        assertEquals(null, ChatChannel.fromId("trade"));
        assertNotNull(ChatChannel.fromId("all"));
    }

    @Test
    public void aBuiltInIdIsNotAConfigsToTake() {
        ChatChannel builtIn = ChatChannel.fromId("all");
        ChatChannel.installDefined(Collections.singletonList(
                new ChatChannelDescriptor("all", "Stolen",
                        ChatPresentationMode.IN_CHARACTER,
                        ChatRecipientRule.GLOBAL, ChatChannelAccess.NONE,
                        0xFFFFFF, false)), this.channelWarnings());

        assertEquals(builtIn, ChatChannel.fromId("all"));
        assertFalse(this.warnings.isEmpty());
    }

    private ChatChannel.Warnings channelWarnings() {
        return new ChatChannel.Warnings() {
            @Override
            public void warn(String message) {
                ChatChannelDefinitionBoundsTest.this.warnings.add(message);
            }
        };
    }

    private boolean warned(String fragment) {
        for (String warning : this.warnings) {
            if (warning.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
