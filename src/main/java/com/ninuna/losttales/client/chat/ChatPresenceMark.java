package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.gui.style.LostTalesColors;
import net.minecraft.util.StatCollector;

/**
 * The mark a head wears for a presence other than Online, at its
 * bottom-right corner the way a messenger's status dot sits on an
 * avatar: a two-pixel dot — honey for Away, crimson for Do Not
 * Disturb — inside a one-pixel plum-black edge, overhanging the head by
 * one pixel each way so it reads as laid on the head rather than cut
 * into it. Online wears nothing: it is the resting state.
 */
public final class ChatPresenceMark {
    static final int SIZE = 4;
    /** How far the mark's top-left stands short of the head's bottom-right. */
    static final int INSET = 3;

    private ChatPresenceMark() {}

    /** Draws the mark on a head drawn at {@code headX}, {@code headY}, {@code headSize} square. */
    public static void draw(int headX, int headY, int headSize,
                            ChatPresence presence, int alpha) {
        if (presence == null || presence == ChatPresence.ONLINE || alpha <= 0) {
            return;
        }
        int left = headX + headSize - INSET;
        int top = headY + headSize - INSET;
        int rgb = LostTalesColors.rgb(presence == ChatPresence.AWAY
                ? LostTalesColors.HONEY : LostTalesColors.CRIMSON);
        LostTalesChatOverlayRenderer.fillRect(left, top, left + SIZE, top + SIZE,
                LostTalesChatVisualStyle.argb(
                        LostTalesColors.rgb(LostTalesColors.PLUM_BLACK), alpha));
        LostTalesChatOverlayRenderer.fillRect(left + 1, top + 1,
                left + SIZE - 1, top + SIZE - 1,
                LostTalesChatVisualStyle.argb(rgb, alpha));
    }

    /** What a card says of a presence; empty for Online, which is no news. */
    public static String label(ChatPresence presence) {
        return presence == null || presence == ChatPresence.ONLINE ? ""
                : StatCollector.translateToLocal(presence.labelKey());
    }
}
