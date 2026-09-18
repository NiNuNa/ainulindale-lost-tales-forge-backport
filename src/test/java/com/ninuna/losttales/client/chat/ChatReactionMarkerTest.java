package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A chip carries its emoji's reaction key whole, a custom emoji's colon
 * included, and names it on its card as a known emoji is named.
 */
public final class ChatReactionMarkerTest {
    private static final long MESSAGE = 12345L;

    @Test
    public void aForeignEmojisChipCarriesItsKeyAndItsDiscordName() {
        ChatReactionMarker.Data custom = ChatReactionMarker.decode(
                ChatReactionMarker.create("partyparrot:556", 2, false,
                        MESSAGE, 6));
        assertNotNull(custom);
        assertEquals("partyparrot:556", custom.key);
        assertNull("no sprite to draw", custom.emoji);
        assertEquals(":partyparrot:", custom.label());
        assertEquals(2, custom.count);
        assertFalse(custom.mine);
        assertEquals(MESSAGE, custom.messageId);

        ChatReactionMarker.Data unicode = ChatReactionMarker.decode(
                ChatReactionMarker.create("🦄", 1, true, MESSAGE, 6));
        assertNotNull(unicode);
        assertEquals(":unicorn:", unicode.label());
        assertTrue(unicode.mine);
    }

    @Test
    public void aKnownEmojisChipIsNamedAsBefore() {
        IChatComponent chip = ChatReactionMarker.create(ChatEmoji.SMILE, 3,
                true, MESSAGE, 6);
        ChatReactionMarker.Data data = ChatReactionMarker.decode(chip);
        assertNotNull(data);
        assertEquals("smile", data.key);
        assertEquals(ChatEmoji.SMILE, data.emoji);
        assertEquals(":smile:", data.label());
        assertEquals(ChatReactionMarker.PAD + ChatReactionMarker.ICON
                + ChatReactionMarker.GAP + 6 + ChatReactionMarker.TRAIL,
                ChatReactionMarker.widthOf(chip));
    }

    @Test
    public void aChipWithNoReactionKeyIsNoChip() {
        assertNull(ChatReactionMarker.decode(ChatReactionMarker.create(
                "partyparrot", 1, false, MESSAGE, 6)));
        assertNull(ChatReactionMarker.decode(ChatReactionMarker.create(
                "partyparrot:0556", 1, false, MESSAGE, 6)));
        assertFalse(ChatReactionMarker.isMarker(ChatReactionMarker.create(
                "", 1, false, MESSAGE, 6)));
    }

    /**
     * The button a reaction row ends on is a square as tall as a chip,
     * aimed at its message; it is no chip, and a press on it is a click
     * of its own, opening the picker.
     */
    @Test
    public void theAddButtonIsASquareAimedAtItsMessage() {
        IChatComponent button = ChatReactionMarker.addButton(MESSAGE);
        assertTrue(ChatReactionMarker.isAddButton(button));
        assertEquals(MESSAGE, ChatReactionMarker.addButtonMessageId(button));
        assertFalse(ChatReactionMarker.isMarker(button));
        assertEquals(ChatReactionMarker.HEIGHT, ChatReactionMarker.ADD_WIDTH);
        assertEquals(ChatReactionMarker.ADD_WIDTH,
                ChatReactionMarker.widthOf(button));
        assertEquals(ChatReactionMarker.ADD_WIDTH,
                ChatInlineIcons.declaredWidth(button));
        assertEquals(ChatInteractions.Action.ADD_REACTION,
                ChatInteractions.actionOf(button, true));
        assertTrue(ChatInteractions.isClick(
                ChatInteractions.actionOf(button, false)));
        assertNull(ChatInteractions.genuineClick(button));
        // A chip is not the button.
        assertFalse(ChatReactionMarker.isAddButton(ChatReactionMarker.create(
                ChatEmoji.SMILE, 1, false, MESSAGE, 6)));
        // Nor is a button aimed at a message the server never named.
        assertFalse(ChatReactionMarker.isAddButton(
                ChatReactionMarker.addButton(-4L)));
    }
}
