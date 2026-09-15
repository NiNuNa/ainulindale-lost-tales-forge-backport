package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.share.ChatShareKind;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * An item's icon keeps every texel whole: it is drawn at the whole
 * display pixels per texel nearest its box, reaching at most one GUI
 * pixel past the box on each side, and its inline slot widens to hold
 * it. Sixteen texels beside a ten-pixel emoji, GUI scale by GUI scale.
 */
public final class ChatInlineIconsTest {

    private static final int TEXELS = ChatInlineIcons.ICON_TEXELS;
    private static final double BOX = ChatInlineIcons.CONTENT_SIZE;
    private static final double ROOM = BOX + 2 * ChatInlineIcons.ITEM_OVERFLOW;

    /** Display pixels per texel for the ten-pixel box at a GUI scale. */
    private static int pixelsPerTexel(int guiScale) {
        return ChatInlineIcons.wholePixelsPerTexel(BOX * guiScale,
                ROOM * guiScale, TEXELS);
    }

    @Test
    public void theNearestWholeSizeWinsAndATieGoesToTheSmaller() {
        // Sixteen does not fit a twelve-pixel row: squeezed.
        assertEquals(0, pixelsPerTexel(1));
        // Eight GUI pixels.
        assertEquals(1, pixelsPerTexel(2));
        // Ten and two thirds, one display pixel past the box each side.
        assertEquals(2, pixelsPerTexel(3));
        // Eight and twelve are as far from ten: the smaller, which fits.
        assertEquals(2, pixelsPerTexel(4));
        // Nine and three fifths.
        assertEquals(3, pixelsPerTexel(5));
        // Ten and two thirds again.
        assertEquals(4, pixelsPerTexel(6));
        assertEquals(6, pixelsPerTexel(9));
    }

    @Test
    public void aSizePastTheRoomGivesWayToTheLargestThatFits() {
        // Thirty-two is nearest a box of thirty, but a room of thirty
        // has no place for it.
        assertEquals(1, ChatInlineIcons.wholePixelsPerTexel(30, 30, TEXELS));
        assertEquals(2, ChatInlineIcons.wholePixelsPerTexel(30, 32, TEXELS));
        // An exact fit is its own nearest.
        assertEquals(2, ChatInlineIcons.wholePixelsPerTexel(32, 34, TEXELS));
        // Nothing fits nothing.
        assertEquals(0, ChatInlineIcons.wholePixelsPerTexel(10, 12, TEXELS));
        assertEquals(0, ChatInlineIcons.wholePixelsPerTexel(0, 12, TEXELS));
        assertEquals(0, ChatInlineIcons.wholePixelsPerTexel(10, 12, 0));
    }

    @Test
    public void theSlotWidensOnlyWhereTheIconReachesPastTheBox() {
        assertEquals(10, ChatInlineIcons.itemSlotWidth(1));
        assertEquals(10, ChatInlineIcons.itemSlotWidth(2));
        assertEquals(11, ChatInlineIcons.itemSlotWidth(3));
        assertEquals(10, ChatInlineIcons.itemSlotWidth(4));
        assertEquals(10, ChatInlineIcons.itemSlotWidth(5));
        assertEquals(11, ChatInlineIcons.itemSlotWidth(6));
        assertEquals(11, ChatInlineIcons.itemSlotWidth(9));
        // A display that cannot be measured reads as scale 1.
        assertEquals(10, ChatInlineIcons.itemSlotWidth(0));
        assertEquals(10, ChatInlineIcons.itemSlotWidth(-1));
        // Never past the box and its clear rows on both sides, which
        // is what the twelve-pixel row keeps between two boxes.
        assertTrue(2 * ChatInlineIcons.ITEM_OVERFLOW
                <= LostTalesChatOverlayRenderer.LINE_HEIGHT
                        - LostTalesChatOverlayRenderer.CONTENT_BOX_HEIGHT);
        for (int factor = 1; factor <= 12; factor++) {
            int slot = ChatInlineIcons.itemSlotWidth(factor);
            assertTrue("factor " + factor,
                    slot >= ChatInlineIcons.SLOT_WIDTH
                            && slot <= ChatInlineIcons.SLOT_WIDTH
                                    + 2 * ChatInlineIcons.ITEM_OVERFLOW);
        }
    }

    @Test
    public void anItemSlotDeclaresItsWidthAndAMarkerSlotIsMeasured() {
        ChatComponentText item =
                ChatShowcaseMarker.createIcon(ChatShareKind.ITEM, 7);
        assertEquals(11, ChatInlineIcons.declaredWidth(item, 3));
        assertEquals(10, ChatInlineIcons.declaredWidth(item, 2));
        ChatComponentText marker =
                ChatShowcaseMarker.createIcon(ChatShareKind.MARKER, 7);
        assertEquals(-1, ChatInlineIcons.declaredWidth(marker, 3));
        // The bracketed name beside the icon is text like any other.
        ChatComponentText name = ChatShowcaseMarker.createText(
                ChatShareKind.ITEM, 7, "Iron Sword", EnumChatFormatting.WHITE,
                0xFFFFFF);
        assertEquals(-1, ChatInlineIcons.declaredWidth(name, 3));
        assertEquals(-1, ChatInlineIcons.declaredWidth(
                new ChatComponentText("plain"), 3));
    }
}
