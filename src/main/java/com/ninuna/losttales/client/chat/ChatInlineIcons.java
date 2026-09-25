package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesUiCornerMark;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiItemIcon;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarker;
import net.minecraft.client.Minecraft;

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
 * drawn at the whole display pixels per texel nearest the box, in a
 * slot that widens to hold it ({@link #itemSlotWidth}), and a marker's
 * opaque artwork (not its padded atlas cell) is fitted into it by its
 * larger edge, never stretched.</li>
 * <li>Wherever a glyph stands beside text — a message row, the input
 * field, the pickers, the completion lists — its box sits
 * {@link #CONTENT_TOP_OFFSET} above the text's top edge: centred on the
 * capitals by the chat's one rule
 * ({@link LostTalesChatOverlayRenderer#centredBoxTop}), half a pixel
 * above their middle, since ten rows cannot be centred on seven.</li>
 * <li>On the toolbar buttons every glyph fills the same
 * {@link #CONTENT_SIZE} box centred in the button square, fitted by its
 * larger edge exactly as it is inline — the emoji 1:1, the item sprite
 * scaled onto the box, the marker artwork fitted uniformly — so the
 * emoji, item, marker and quest buttons read at one size on one baseline
 * and nothing is stretched.</li>
 * <li>Shadows are flat {@link LostTalesUiInk#SHADOW}
 * silhouettes offset by {@link LostTalesUiInk#SHADOW_OFFSET}
 * at {@link LostTalesUiInk#SHADOW_OPACITY}, exactly like the
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
    /**
     * The slot a head marker reserves when a mark stands for the head —
     * the Discord mark on a bridge line, the console mark on the
     * server's: a 10px emoji drawn 1:1 — never scaled — with the same
     * clear pixels around it a head keeps, so the slot is two pixels
     * wider.
     */
    static final int MARK_HEAD_SLOT_WIDTH = 13;
    /**
     * The slot a head wearing a presence sphere reserves: the sphere
     * stands past the face, and the head and its sphere are one icon, so
     * what follows keeps its clear space from the sphere.
     */
    static final int PRESENCE_HEAD_SLOT_WIDTH =
            HEAD_SLOT_WIDTH + LostTalesUiCornerMark.OVERHANG_X;
    /** Where the face starts inside that slot. */
    static final float HEAD_SLOT_INSET = 1.0F;
    /** The clear space a name keeps from what is written either side. */
    static final int NAME_GAP = 2;
    /** Common inline content box edge; also the emoji sprite's native size. */
    static final float CONTENT_SIZE = 10.0F;
    /**
     * Box top relative to the text's top edge wherever a glyph stands
     * beside text: two rows above the glyphs, the box's middle half a
     * pixel above the capitals' middle — the chat's one rule for a
     * ten-row box ({@link LostTalesChatOverlayRenderer#centredBoxTop}).
     */
    static final int CONTENT_TOP_OFFSET = -2;
    private ChatInlineIcons() {}

    /**
     * How far the cursor moves past a component that declares its own
     * width rather than being measured — a head's slot, a plain gap, the
     * stamp behind a name — or -1 for anything measured from its text. Every walk over a line
     * asks here, so drawing, wrapping and hit testing cannot disagree
     * about where the next glyph starts.
     */
    static int declaredWidth(net.minecraft.util.IChatComponent part) {
        return declaredWidth(part, -1);
    }

    /**
     * The same, with an item slot's width taken for the given display
     * scale factor rather than the display's own; a negative factor asks
     * the display.
     */
    static int declaredWidth(net.minecraft.util.IChatComponent part,
                             int displayScaleFactor) {
        ChatHeadMarker.Data head = ChatHeadMarker.headOf(part);
        if (head != null) {
            if (head.avatar) {
                // Drawn in the open window's timestamp area, not the row.
                return 0;
            }
            // A head and its sphere are one icon: a head that wears one
            // takes the room the sphere reaches past it, a mark standing
            // for a head as a face does.
            int slot = head.mark() != null ? MARK_HEAD_SLOT_WIDTH
                    : HEAD_SLOT_WIDTH;
            return ChatPresenceMark.wears(head)
                    ? slot + LostTalesUiCornerMark.OVERHANG_X : slot;
        }
        if (ChatReplyMarker.isIconSlot(part)) {
            return ChatReplyMarker.ICON_SLOT_WIDTH;
        }
        int chip = ChatReactionMarker.widthOf(part);
        if (chip >= 0) {
            return chip;
        }
        // An item's slot is as wide as the icon's nearest crisp size,
        // so the icon never stands in a neighbour's pixels; a marker's
        // slot stays its two spaces.
        ChatShowcaseMarker.Data share = ChatShowcaseMarker.decode(part);
        if (share != null && share.icon && share.kind == ChatShareKind.ITEM) {
            return displayScaleFactor < 0 ? itemSlotWidth()
                    : itemSlotWidth(displayScaleFactor);
        }
        int stamp = ChatStampMarker.widthOf(part);
        if (stamp >= 0) {
            return stamp;
        }
        return ChatSpacerMarker.decode(part);
    }

    /**
     * The slot an inline item icon reserves: the emoji's, widened to the
     * whole GUI pixels the icon's nearest crisp size needs wherever that
     * reaches past the box — eleven at GUI scales 3 and 6 — so what
     * follows starts clear of it.
     */
    static int itemSlotWidth() {
        return itemSlotWidth(LostTalesDisplayPixels.scaleFactor());
    }

    static int itemSlotWidth(int displayScaleFactor) {
        int factor = Math.max(1, displayScaleFactor);
        int ratio = LostTalesUiItemIcon.wholePixelsPerTexel(CONTENT_SIZE * factor,
                (CONTENT_SIZE + 2 * LostTalesUiItemIcon.ITEM_OVERFLOW) * factor, LostTalesUiItemIcon.ICON_TEXELS);
        if (ratio <= 0) {
            return SLOT_WIDTH;
        }
        return Math.max(SLOT_WIDTH, (int)Math.ceil(
                ratio * LostTalesUiItemIcon.ICON_TEXELS / (double)factor - 1.0E-6D));
    }

    /** Content edge for a slot; a degraded slot narrower than ten shrinks it. */
    static float contentSize(int slotWidth) {
        return Math.min(CONTENT_SIZE, Math.max(0, slotWidth));
    }

    /** Left edge of the content box centred in a slot starting at {@code slotX}. */
    static float boxLeft(float slotX, int slotWidth) {
        return slotX + Math.max(0.0F, (slotWidth - contentSize(slotWidth)) / 2.0F);
    }

    /**
     * Top edge of the content box for text drawn at {@code textY}, in a
     * message row and wherever else a glyph stands beside text. A slot
     * too narrow for the whole box centres the smaller box on the whole
     * box's middle.
     */
    static float boxTop(float textY, int slotWidth) {
        return textY + CONTENT_TOP_OFFSET
                + (CONTENT_SIZE - contentSize(slotWidth)) / 2.0F;
    }

    /**
     * The colour a marker's artwork is drawn with in chat: its exact map
     * colour. Plain white is a real choice, not a placeholder — a white
     * marker keeps its sprite's own pixels untinted.
     */
    static int markerRgb(String colorName) {
        float[] color = LostTalesCompassMarker.parseColor(colorName);
        return (Math.round(color[0] * 255.0F) << 16)
                | (Math.round(color[1] * 255.0F) << 8)
                | Math.round(color[2] * 255.0F);
    }

    /**
     * The colour a marker is <em>named</em> with — its name in a message
     * and in the input preview: the same map colour, with
     * plain white swapped for the chat's ivory, so a white marker's text
     * reads in the one white every other word does. Only the artwork
     * keeps pure white.
     */
    static int markerTextRgb(String colorName) {
        int rgb = markerRgb(colorName);
        return rgb == 0xFFFFFF ? LostTalesUiInk.IVORY : rgb;
    }

    static void drawEmoji(Minecraft minecraft, ChatEmoji emoji,
                          float boxX, float boxY, float size, int alpha,
                          boolean silhouette) {
        if (silhouette) {
            ChatEmojiRenderer.drawShadow(minecraft, emoji, boxX, boxY, size,
                    LostTalesUiInk.SHADOW, alpha);
        } else {
            ChatEmojiRenderer.draw(minecraft, emoji, boxX, boxY, size, alpha);
        }
    }


    /** Marker artwork fitted into the box, in {@code rgb}. */
    static void drawMarker(Minecraft minecraft, String iconName, int rgb,
                           float boxX, float boxY, float size, int alpha,
                           boolean silhouette) {
        if (silhouette) {
            ChatMapMarkerRenderer.drawShadow(minecraft, iconName, boxX, boxY,
                    size, LostTalesUiInk.SHADOW, alpha);
        } else {
            ChatMapMarkerRenderer.draw(minecraft, iconName, boxX, boxY, size,
                    rgb, alpha);
        }
    }

    /**
     * Where a sprite {@code extent} pixels long starts when it is centred
     * in a box {@code size} pixels long starting at {@code boxStart}:
     * rounded down to a whole pixel, so from a whole-pixel box an odd
     * leftover pixel goes after the sprite.
     */
    static float spriteStart(float boxStart, float size, int extent) {
        return (float)Math.floor(boxStart + (size - extent) / 2.0F);
    }

    /**
     * Where a sprite {@code extent} rows tall starts when it stands in a
     * content box {@code size} rows tall starting at {@code boxTop}: on
     * the capitals the box stands beside. The box's middle is half a
     * pixel above theirs, so the sprite's top is rounded down the
     * screen, which lands a sprite of odd height on the capitals' middle
     * exactly and one of even height half a pixel above it, as the row's
     * rule places everything ({@link LostTalesChatOverlayRenderer#centredBoxTop}).
     */
    static float spriteTop(float boxTop, float size, int extent) {
        return (float)Math.ceil(boxTop + (size - extent) / 2.0F - 0.001F);
    }

    /**
     * A cell of the chat's own icon sheet as an inline glyph: drawn 1:1
     * on whole pixels, centred across the slot and on the capitals the
     * box stands beside, as a flat silhouette in {@code rgb} — the shadow
     * tone on the shadow pass — so it takes the colour of the run it
     * stands in. The sheet's cells are smaller than the box; the box is
     * never scaled to them.
     */
    static void drawSheetSprite(LostTalesUiSheet sprite, float boxX,
                                float boxY, float size, int rgb, int alpha,
                                boolean silhouette) {
        if (sprite == null || size <= 0.0F) {
            return;
        }
        float x = spriteStart(boxX, size, sprite.getWidth());
        float y = spriteTop(boxY, size, sprite.getHeight());
        sprite.drawSilhouette(silhouette
                ? LostTalesUiInk.SHADOW : rgb, x, y, alpha);
    }

    /* Two-pass conveniences for callers that draw one glyph at a time. */

    static void drawSheetSprite(LostTalesUiSheet sprite, float boxX,
                                float boxY, float size, int rgb, int alpha) {
        int shadow = LostTalesUiInk.shadowAlpha(alpha);
        if (shadow > 0) {
            drawSheetSprite(sprite,
                    boxX + LostTalesUiInk.SHADOW_OFFSET,
                    boxY + LostTalesUiInk.SHADOW_OFFSET, size, rgb,
                    shadow, true);
        }
        drawSheetSprite(sprite, boxX, boxY, size, rgb, alpha, false);
    }

    static void drawEmoji(Minecraft minecraft, ChatEmoji emoji,
                          float boxX, float boxY, float size, int alpha) {
        int shadow = LostTalesUiInk.shadowAlpha(alpha);
        if (shadow > 0) {
            drawEmoji(minecraft, emoji, boxX + LostTalesUiInk.SHADOW_OFFSET,
                    boxY + LostTalesUiInk.SHADOW_OFFSET, size, shadow,
                    true);
        }
        drawEmoji(minecraft, emoji, boxX, boxY, size, alpha, false);
    }

    static void drawMarker(Minecraft minecraft, String iconName, int rgb,
                           float boxX, float boxY, float size, int alpha) {
        int shadow = LostTalesUiInk.shadowAlpha(alpha);
        if (shadow > 0) {
            drawMarker(minecraft, iconName, rgb,
                    boxX + LostTalesUiInk.SHADOW_OFFSET,
                    boxY + LostTalesUiInk.SHADOW_OFFSET, size, shadow,
                    true);
        }
        drawMarker(minecraft, iconName, rgb, boxX, boxY, size, alpha, false);
    }
}
