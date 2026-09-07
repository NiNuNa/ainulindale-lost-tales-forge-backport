package com.ninuna.losttales.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The set of channels is open. A channel a server defines is registered
 * beside the built-in ones and is a channel in every way they are — it is
 * found by its id, walked with the rest, and presented after them — while
 * the built-ins are the code's own and stay in force whatever a config
 * says.
 */
public final class ChatChannelRegistryTest {

    private static final String CUSTOM_ID = "losttales_test_channel";

    @After
    public void tearDown() {
        ChatChannel.resetToBuiltIn();
    }

    @Test
    public void theBuiltInChannelsAreInForceBeforeAnythingIsRegistered() {
        List<ChatChannel> built = Arrays.asList(ChatChannel.ALL,
                ChatChannel.PROXIMITY, ChatChannel.PARTY, ChatChannel.FACTION,
                ChatChannel.OOC, ChatChannel.ADMIN, ChatChannel.CONSOLE,
                ChatChannel.WHISPER);
        List<ChatChannel> inForce = Arrays.asList(ChatChannel.values());

        assertEquals(built.size(), inForce.size());
        assertTrue(inForce.containsAll(built));
    }

    /** A registered channel is found by the id everything names it by. */
    @Test
    public void aRegisteredChannelIsFoundByItsId() {
        ChatChannel custom = register();

        assertSame(custom, ChatChannel.fromId(CUSTOM_ID));
        assertSame("and however it is spelled",
                custom, ChatChannel.fromId("  " + CUSTOM_ID.toUpperCase() + " "));
        assertTrue(Arrays.asList(ChatChannel.values()).contains(custom));
    }

    /** It is presented after the built-ins rather than among them. */
    @Test
    public void aRegisteredChannelIsPresentedAfterTheBuiltIns() {
        ChatChannel custom = register();
        List<ChatChannel> order = ChatChannel.presentationOrder();

        assertEquals("it comes last", custom, order.get(order.size() - 1));
        assertEquals("the built-ins keep their own order",
                ChatChannel.ALL, order.get(0));
        assertFalse("whispers are still not a row of their own",
                order.contains(ChatChannel.WHISPER));
    }

    /** It carries every fact the routing and the gates read a channel by. */
    @Test
    public void aRegisteredChannelCarriesTheFactsAChannelIsDecidedBy() {
        ChatChannel custom = register();

        assertEquals(ChatRecipientRule.GLOBAL, custom.getRecipientRule());
        assertEquals(ChatChannelAccess.NONE, custom.getAccess());
        assertEquals(ChatPresentationMode.OUT_OF_CHARACTER,
                custom.getPresentation());
        assertEquals(ChatChannelScope.NONE, custom.getScope());
        assertFalse(custom.isScoped());
        assertFalse(custom.isBridgeable());
    }

    /**
     * An id is what everything names a channel by, so two channels can
     * never share one: the second is refused rather than replacing the
     * first, whose lines are already filed under it.
     */
    @Test
    public void anIdIsNeverRegisteredTwice() {
        register();
        try {
            register();
            fail("the same id was registered twice");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains(CUSTOM_ID));
        }
        try {
            ChatChannel.register(new ChatChannelDescriptor(
                    ChatChannel.ALL.getId(), "Impostor",
                    ChatPresentationMode.OUT_OF_CHARACTER,
                    ChatRecipientRule.GLOBAL, ChatChannelAccess.NONE, 0, false));
            fail("a built-in id was taken over");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains(ChatChannel.ALL.getId()));
        }
    }

    /**
     * Putting the set back leaves the code's own channels and takes out
     * what a config put there, so reading a config again cannot be
     * refused by what the last read registered.
     */
    @Test
    public void resettingKeepsTheBuiltInsAndDropsTheRest() {
        int builtIn = ChatChannel.values().length;
        register();
        assertEquals(builtIn + 1, ChatChannel.values().length);

        ChatChannel.resetToBuiltIn();

        assertEquals(builtIn, ChatChannel.values().length);
        assertSame("a built-in is the same channel it always was",
                ChatChannel.ALL, ChatChannel.fromId("all"));
        assertEquals("and the one the config named is gone",
                null, ChatChannel.fromId(CUSTOM_ID));

        // And it can be registered again, which a reload does.
        assertSame(ChatChannel.fromId(CUSTOM_ID), null);
        register();
        assertEquals(builtIn + 1, ChatChannel.values().length);
    }

    /** An older build's id for a channel since merged still resolves. */
    @Test
    public void theRetiredDiscordIdStillNamesTheChannelThatTookItIn() {
        assertSame(ChatChannel.OOC, ChatChannel.fromId("discord"));
        assertEquals(null, ChatChannel.fromId("nothing_named_this"));
    }

    private static ChatChannel register() {
        return ChatChannel.register(new ChatChannelDescriptor(
                CUSTOM_ID, "Test", ChatPresentationMode.OUT_OF_CHARACTER,
                ChatRecipientRule.GLOBAL, ChatChannelAccess.NONE,
                LostTalesColors.rgb(LostTalesColors.HONEY), false));
    }
}
