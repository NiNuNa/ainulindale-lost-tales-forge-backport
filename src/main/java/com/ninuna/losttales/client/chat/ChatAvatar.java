package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.gui.style.LostTalesUiCornerMark;
import com.ninuna.losttales.gui.style.LostTalesUiItemIcon;
import net.minecraft.util.IChatComponent;

/**
 * The avatar a message wears in an open window, the way a messenger
 * stands its speaker's picture beside their messages: the speaker's
 * head at twice the face's size, in the window's timestamp area
 * ({@link ChatTimestampColumn}), beside the row that names them and the
 * first row of what they said. The rest of a group's messages leave the
 * area to their times, so a run reads as one block under one picture.
 * The closed feed keeps the small head in the name's row.
 *
 * <p>A head is pixel art, so the avatar draws each of the face's eight
 * texels as a two-pixel square, and a player's avatar wears the
 * presence sphere in its corner at the sphere's own size
 * ({@link ChatPresenceMark}). A sender with a mark instead of a head —
 * the server, the client, the Discord bridge, the Narrator — has the
 * mark's emoji drawn in the middle of the avatar's square at the whole
 * display pixels per texel nearest the square ({@link #markSize}), so it
 * reads at the avatar's size and every texel stays crisp.</p>
 *
 * <p>The geometry lives here, free of the renderer: the renderer draws
 * each avatar where {@link #top} puts it and records the box it drew,
 * which is where the avatar answers the pointer as the speaker's name
 * does.</p>
 */
final class ChatAvatar {
    /** The face's eight texels, each two pixels square. */
    static final int SIZE = 2 * LostTalesChatOverlayRenderer.HEAD_SIZE;
    /** The avatar as one icon: the face and the sphere standing past its right edge. */
    static final int ICON_WIDTH = SIZE + LostTalesUiCornerMark.OVERHANG_X;
    /** How far past the avatar's square a mark standing for a head may reach, each side. */
    static final int MARK_OVERFLOW = 1;

    private ChatAvatar() {}

    /**
     * Where an avatar's top edge stands, for a speaker's row starting at
     * {@code headerTop} and {@code headerHeight} tall over a first row of
     * words {@code bodyHeight} tall: centred across the two, the odd
     * pixel up, as everything in the chat that cannot centre exactly is.
     */
    static int top(int headerTop, int headerHeight, int bodyHeight) {
        return headerTop + Math.floorDiv(headerHeight + bodyHeight - SIZE, 2);
    }

    /**
     * The edge a mark standing for a head is drawn at in the avatar's
     * square, in the chat's pixels, on a display drawing
     * {@code displayPixelsPerPixel} of its pixels to one of them: its
     * emoji at the whole display pixels per texel that come nearest the
     * square, reaching no more than {@link #MARK_OVERFLOW} past it on
     * each side, and never under one. Ten texels in the sixteen-pixel
     * square: fifteen at GUI scales 2 and 4, sixteen and two thirds at 3,
     * ten at 1.
     */
    static float markSize(float displayPixelsPerPixel) {
        float perPixel = Math.max(0.001F, displayPixelsPerPixel);
        int texels = ChatEmoji.SPRITE_SIZE;
        int ratio = LostTalesUiItemIcon.wholePixelsPerTexel(SIZE * perPixel,
                (SIZE + 2 * MARK_OVERFLOW) * perPixel, texels);
        return Math.max(1, ratio) * texels / perPixel;
    }

    /**
     * The head a speaker's row wears as its avatar, or null for a row
     * that wears none: any other row, and the feed's rows, which carry
     * the head in the row itself.
     */
    static ChatHeadMarker.Data of(IChatComponent row) {
        if (row == null || !ChatLayoutMarker.isHeaderRow(row)) {
            return null;
        }
        for (Object value : row) {
            if (value instanceof IChatComponent) {
                ChatHeadMarker.Data head =
                        ChatHeadMarker.decode((IChatComponent)value);
                if (head != null) {
                    return head.avatar ? head : null;
                }
            }
        }
        return null;
    }

    /** The place on a speaker's row the avatar stands for: its head's own run. */
    static int headIndex(IChatComponent row) {
        int index = -1;
        for (Object value : row) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            index++;
            ChatHeadMarker.Data head =
                    ChatHeadMarker.decode((IChatComponent)value);
            if (head != null && head.avatar) {
                return index;
            }
        }
        return -1;
    }
}
