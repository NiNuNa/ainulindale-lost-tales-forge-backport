package com.ninuna.losttales.client.chat;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The time behind a name holds no text of its own: it carries its words
 * and the width they take at the small size in the name's row, which
 * every walk over the row reads, and a click on it is spent.
 */
public final class ChatStampMarkerTest {

    @Test
    public void aStampCarriesItsWordsAndItsWidth() {
        IChatComponent stamp = ChatStampMarker.of(
                "Yesterday at §o9:54 PM§r", 57);
        assertTrue(ChatStampMarker.isMarker(stamp));
        assertEquals(57, ChatStampMarker.widthOf(stamp));
        assertEquals("Yesterday at §o9:54 PM§r",
                ChatStampMarker.textOf(stamp));
        assertEquals("", stamp.getUnformattedTextForChat());
        assertEquals(57, ChatInlineIcons.declaredWidth(stamp));
        assertEquals(ChatInteractions.Action.CONSUMED,
                ChatInteractions.actionOf(stamp, true));
        assertNull(ChatInteractions.genuineClick(stamp));
    }

    @Test
    public void anOrdinaryRunIsNoStamp() {
        ChatComponentText plain = new ChatComponentText("9:54 PM");
        assertFalse(ChatStampMarker.isMarker(plain));
        assertEquals(-1, ChatStampMarker.widthOf(plain));
        assertNull(ChatStampMarker.textOf(plain));
        assertFalse(ChatStampMarker.isMarker(ChatSpacerMarker.of(4)));
    }

    /**
     * The stamp's capitals stand centred on the name's lowercase letters,
     * the odd display pixel up: at GUI scale 3 the name is four display
     * pixels a font pixel and the stamp two, and the stamp starts eleven
     * below the name's top; at 2 ten, at 4 twelve; with the name at the
     * words' own size, six and a half, which goes up to six.
     */
    @Test
    public void aStampIsCentredOnTheNamesLowercase() {
        assertEquals(11, LostTalesChatOverlayRenderer.stampDrop(4, 2));
        assertEquals(10, LostTalesChatOverlayRenderer.stampDrop(3, 1));
        assertEquals(12, LostTalesChatOverlayRenderer.stampDrop(5, 3));
        assertEquals(6, LostTalesChatOverlayRenderer.stampDrop(3, 2));
    }

    /**
     * The stamp is the chat's small text in a row drawn at the speaker's
     * size, so it takes its small width in that row's own pixels,
     * rounded up: at GUI scale 4 a stamp thirty-five pixels wide at the
     * font's size is three quarters of that, in a row drawn at five
     * quarters.
     */
    @Test
    public void aStampTakesItsSmallWidthInTheNameRow() {
        assertEquals(21, ChatMessageWrapper.stampWidth(35, 0.75F, 1.25F));
        assertEquals(14, ChatMessageWrapper.stampWidth(40, 0.5F, 1.5F));
        assertEquals(10, ChatMessageWrapper.stampWidth(10, 1.0F, 1.0F));
    }
}
