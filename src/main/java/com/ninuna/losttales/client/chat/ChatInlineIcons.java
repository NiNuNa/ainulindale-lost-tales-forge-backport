package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;

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
            HEAD_SLOT_WIDTH + ChatPresenceMark.OVERHANG_X;
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
    /** An item icon's sixteen texels; vanilla's item icon size. */
    static final int ICON_TEXELS = 16;
    /**
     * How far past its box an item icon may reach on each side, in GUI
     * pixels, to keep every texel whole: the clear rows a message row
     * keeps around the content box and a tab keeps around its icon.
     */
    static final int ITEM_OVERFLOW = 1;
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
            if (head.mark() != null) {
                return MARK_HEAD_SLOT_WIDTH;
            }
            return ChatPresenceMark.wears(head)
                    ? PRESENCE_HEAD_SLOT_WIDTH : HEAD_SLOT_WIDTH;
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
     * The whole display pixels per texel an icon of {@code texels} is
     * drawn at in a box {@code box} display pixels wide: the count whose
     * icon is nearest the box in size, a tie going to the smaller, unless
     * that icon would reach past {@code room}, in which case the largest
     * that fits the box; zero when not even one pixel per texel fits the
     * room. Sixteen texels in a ten-pixel box: half size at GUI scale 2,
     * one and a third at 3, eight tenths at 4, close to one at 5.
     */
    static int wholePixelsPerTexel(double box, double room, int texels) {
        if (texels <= 0 || box <= 0.0D) {
            return 0;
        }
        int lower = (int)Math.floor(box / texels + 1.0E-6D);
        int upper = lower + 1;
        int nearest = upper * texels - box < box - lower * texels
                ? upper : lower;
        if (nearest * texels > room + 1.0E-6D) {
            nearest = lower;
        }
        return Math.max(0, nearest);
    }

    /**
     * The slot an inline item icon reserves: the emoji's, widened to the
     * whole GUI pixels the icon's nearest crisp size needs wherever that
     * reaches past the box — eleven at GUI scales 3 and 6 — so what
     * follows starts clear of it.
     */
    static int itemSlotWidth() {
        return itemSlotWidth(ChatWindowFrame.displayScaleFactor());
    }

    static int itemSlotWidth(int displayScaleFactor) {
        int factor = Math.max(1, displayScaleFactor);
        int ratio = wholePixelsPerTexel(CONTENT_SIZE * factor,
                (CONTENT_SIZE + 2 * ITEM_OVERFLOW) * factor, ICON_TEXELS);
        if (ratio <= 0) {
            return SLOT_WIDTH;
        }
        return Math.max(SLOT_WIDTH, (int)Math.ceil(
                ratio * ICON_TEXELS / (double)factor - 1.0E-6D));
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
     * The colour a marker is <em>named</em> with — its bracketed name in
     * a message and in the input preview: the same map colour, with
     * plain white swapped for the chat's ivory, so a white marker's text
     * reads in the one white every other word does. Only the artwork
     * keeps pure white.
     */
    static int markerTextRgb(String colorName) {
        int rgb = markerRgb(colorName);
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

    /**
     * An item's icon in its box, crisp and never cut: drawn at the whole
     * display pixels per texel nearest the box's size, a tie going to the
     * smaller, from an origin on the display grid, so its pixel art keeps
     * every texel whole the way the emoji do rather than being squeezed
     * into the box. The nearest size may reach {@link #ITEM_OVERFLOW}
     * past the box on each side, into the clear rows around it; one that
     * would reach further gives way to the largest that fits the box.
     * Only where not even one pixel per texel fits the room — GUI scale
     * 1, sixteen texels in a ten-pixel box — is the icon squeezed to the
     * box as it always was, uneven pixels and all, rather than cut. The
     * faction banner and a channel's chosen item are drawn the same way.
     */
    static void drawItem(Minecraft minecraft, ItemStack stack,
                         float boxX, float boxY, float size, int alpha,
                         boolean silhouette) {
        if (minecraft == null || stack == null || size <= 0.0F) {
            return;
        }
        // The matrix the caller draws in only scales and translates:
        // the chat scale over the lines, a fraction of a pixel under the
        // strips and the bar. Its scale and offset give the box's place
        // on the screen, and with the GUI scale, how many display pixels
        // one of the caller's units is.
        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
        float scaleX = matrix.get(0);
        float scaleY = matrix.get(5);
        float shiftX = matrix.get(12);
        float shiftY = matrix.get(13);
        if (scaleX <= 0.0F || scaleY <= 0.0F) {
            return;
        }
        int factor = ChatWindowFrame.displayScaleFactor();
        double unit = factor * scaleX;
        int ratio = wholePixelsPerTexel(size * unit,
                (size + 2 * ITEM_OVERFLOW) * unit, ICON_TEXELS);
        // Not one whole pixel per texel fits the room: squeezed to the
        // box, as the box is all the room there is.
        float drawn = ratio <= 0 ? size : (float)(ratio * ICON_TEXELS / unit);
        // Centred in the box, on the display grid.
        double originX = ChatWindowFrame.snapToDisplayPixels(
                scaleX * boxX + shiftX + (size - drawn) / 2.0D * scaleX);
        double originY = ChatWindowFrame.snapToDisplayPixels(
                scaleY * boxY + shiftY + (size - drawn) / 2.0D * scaleY);
        float x = (float)((originX - shiftX) / scaleX);
        float y = (float)((originY - shiftY) / scaleY);
        if (silhouette) {
            ChatItemRenderer.drawShadow(minecraft, stack, x, y, drawn,
                    LostTalesChatVisualStyle.SHADOW, alpha);
        } else {
            ChatItemRenderer.draw(minecraft, stack, x, y, drawn, alpha);
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
                ? LostTalesChatVisualStyle.SHADOW : rgb, x, y, alpha);
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
}
