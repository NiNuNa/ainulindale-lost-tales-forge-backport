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
}
