package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

/**
 * The one set of rules every inline chat glyph — emoji, item icon, map
 * marker — is drawn by, in the message lines, the pickers, the completion
 * lists, and the toolbar buttons alike.
 *
 * <ul>
 * <li>Inline, a glyph occupies a {@link #SLOT_WIDTH}-pixel slot (the two
 * bold spaces reserved in the message) and fills a {@link #CONTENT_SIZE}
 * square content box centred in it, so every kind reads at the same
 * apparent size: the emoji sheet is drawn 1:1, an item's 16px sprite is
 * scaled onto the box, and a marker's opaque artwork (not its padded
 * atlas cell) is fitted into it by its larger edge, never stretched.</li>
 * <li>The box sits {@link #CONTENT_TOP_OFFSET} above the text's top edge,
 * which centres it in the eleven-pixel line band, so every kind shares the
 * text's baseline relationship.</li>
 * <li>On the toolbar buttons every glyph fills the same
 * {@link #CONTENT_SIZE} box centred in the button square, fitted by its
 * larger edge exactly as it is inline — the emoji 1:1, the item sprite
 * scaled onto the box, the marker artwork fitted uniformly — so the
 * emoji, item, marker and quest buttons read at one size on one baseline
 * and nothing is stretched.</li>
 * <li>Shadows are flat {@link LostTalesChatVisualStyle#SHADOW}
 * silhouettes offset by {@link LostTalesChatVisualStyle#SHADOW_OFFSET}
 * at {@link LostTalesChatVisualStyle#SHADOW_OPACITY}, exactly like the
 * text's. Markers keep the colour they have on the map, except that the
 * map's plain white becomes the chat's ivory so a white marker and the
 * text around it share one white.</li>
 * </ul>
 *
 * <p>The message pass draws all shadows first and all content second, so
 * the primitives take a {@code silhouette} flag and leave offset and alpha
 * to the caller; the overloads without it draw both passes for the
 * single-glyph callers (pickers, buttons, lists).</p>
 */
final class ChatInlineIcons {
    /** Reserved inline slot: the width of two bold spaces. */
    static final int SLOT_WIDTH = 10;
    /**
     * The slot a head marker reserves: eight pixels of face with two
     * clear either side of it, one of which the glyph before the head
     * already provides as its own trailing space. Declared rather than
     * measured, because a run of spaces cannot be eleven pixels wide —
     * a space is four and a bold one five — and the gaps the head sits
     * between should be even.
     */
    static final int HEAD_SLOT_WIDTH = 11;
    /** Where the face starts inside that slot. */
    static final float HEAD_SLOT_INSET = 1.0F;
    /** The clear space a name keeps from what is written either side. */
    static final int NAME_GAP = 2;
    /** Common inline content box edge; also the emoji sprite's native size. */
    static final float CONTENT_SIZE = 10.0F;
    /** Box top relative to the text top: centred in the 11px line band. */
    static final int CONTENT_TOP_OFFSET = -2;
    private ChatInlineIcons() {}

    /**
     * How far the cursor moves past a component that declares its own
     * width rather than being measured — a head's slot, a plain gap —
     * or -1 for anything measured from its text. Every walk over a line
     * asks here, so drawing, wrapping and hit testing cannot disagree
     * about where the next glyph starts.
     */
    static int declaredWidth(net.minecraft.util.IChatComponent part) {
        if (ChatHeadMarker.isMarker(part)) {
            return HEAD_SLOT_WIDTH;
        }
        return ChatSpacerMarker.decode(part);
    }

    /** Content edge for a slot; a degraded slot narrower than ten shrinks it. */
    static float contentSize(int slotWidth) {
        return Math.min(CONTENT_SIZE, Math.max(0, slotWidth));
    }

    /** Left edge of the content box centred in a slot starting at {@code slotX}. */
    static float boxLeft(float slotX, int slotWidth) {
        return slotX + Math.max(0.0F, (slotWidth - contentSize(slotWidth)) / 2.0F);
    }

    /** Top edge of the content box for text drawn at {@code textY}. */
    static float boxTop(float textY, int slotWidth) {
        return textY + CONTENT_TOP_OFFSET
                + (CONTENT_SIZE - contentSize(slotWidth)) / 2.0F;
    }

    /**
     * The colour a marker is drawn and named with in chat: its map colour,
     * with the map's plain white swapped for the chat's ivory.
     */
    static int markerRgb(String colorName) {
        float[] color = LostTalesCompassMarker.parseColor(colorName);
        int rgb = (Math.round(color[0] * 255.0F) << 16)
                | (Math.round(color[1] * 255.0F) << 8)
                | Math.round(color[2] * 255.0F);
        return rgb == 0xFFFFFF ? LostTalesChatVisualStyle.IVORY : rgb;
    }

    static void drawEmoji(Minecraft minecraft, ChatEmoji emoji,
                          float boxX, float boxY, float size, int alpha,
                          boolean silhouette) {
        if (silhouette) {
            ChatEmojiRenderer.drawShadow(minecraft, emoji, boxX, boxY, size,
                    LostTalesChatVisualStyle.SHADOW, alpha);
        } else {
            ChatEmojiRenderer.draw(minecraft, emoji, boxX, boxY, size, alpha);
        }
    }

    static void drawItem(Minecraft minecraft, ItemStack stack,
                         float boxX, float boxY, float size, int alpha,
                         boolean silhouette) {
        if (silhouette) {
            ChatItemRenderer.drawShadow(minecraft, stack, boxX, boxY, size,
                    LostTalesChatVisualStyle.SHADOW, alpha);
        } else {
            ChatItemRenderer.draw(minecraft, stack, boxX, boxY, size, alpha);
        }
    }

    /** Marker artwork fitted into the box, in {@code rgb}. */
    static void drawMarker(Minecraft minecraft, String iconName, int rgb,
                           float boxX, float boxY, float size, int alpha,
                           boolean silhouette) {
        if (silhouette) {
            ChatMapMarkerRenderer.drawShadow(minecraft, iconName, boxX, boxY,
                    size, LostTalesChatVisualStyle.SHADOW, alpha);
        } else {
            ChatMapMarkerRenderer.draw(minecraft, iconName, boxX, boxY, size,
                    rgb, alpha);
        }
    }

    /* Two-pass conveniences for callers that draw one glyph at a time. */

    static void drawEmoji(Minecraft minecraft, ChatEmoji emoji,
                          float boxX, float boxY, float size, int alpha) {
        int shadow = LostTalesChatVisualStyle.shadowAlpha(alpha);
        if (shadow > 0) {
            drawEmoji(minecraft, emoji, boxX + LostTalesChatVisualStyle.SHADOW_OFFSET,
                    boxY + LostTalesChatVisualStyle.SHADOW_OFFSET, size, shadow,
                    true);
        }
        drawEmoji(minecraft, emoji, boxX, boxY, size, alpha, false);
    }

    static void drawItem(Minecraft minecraft, ItemStack stack,
                         float boxX, float boxY, float size, int alpha) {
        int shadow = LostTalesChatVisualStyle.shadowAlpha(alpha);
        if (shadow > 0) {
            drawItem(minecraft, stack, boxX + LostTalesChatVisualStyle.SHADOW_OFFSET,
                    boxY + LostTalesChatVisualStyle.SHADOW_OFFSET, size, shadow,
                    true);
        }
        drawItem(minecraft, stack, boxX, boxY, size, alpha, false);
    }

    static void drawMarker(Minecraft minecraft, String iconName, int rgb,
                           float boxX, float boxY, float size, int alpha) {
        int shadow = LostTalesChatVisualStyle.shadowAlpha(alpha);
        if (shadow > 0) {
            drawMarker(minecraft, iconName, rgb,
                    boxX + LostTalesChatVisualStyle.SHADOW_OFFSET,
                    boxY + LostTalesChatVisualStyle.SHADOW_OFFSET, size, shadow,
                    true);
        }
        drawMarker(minecraft, iconName, rgb, boxX, boxY, size, alpha, false);
    }

    /* Toolbar buttons: one content box, centred in the button square. */

    static void drawItemButton(Minecraft minecraft, ItemStack stack,
                               int left, int top, int buttonSize) {
        float inset = (buttonSize - CONTENT_SIZE) / 2.0F;
        drawItem(minecraft, stack, left + inset, top + inset,
                CONTENT_SIZE, 255);
    }

    /** Marker artwork fitted into the content box, uniformly scaled. */
    static void drawMarkerButton(Minecraft minecraft, String iconName,
                                 int rgb, int left, int top, int buttonSize) {
        float inset = (buttonSize - CONTENT_SIZE) / 2.0F;
        drawMarker(minecraft, iconName, rgb, left + inset, top + inset,
                CONTENT_SIZE, 255);
    }
}
