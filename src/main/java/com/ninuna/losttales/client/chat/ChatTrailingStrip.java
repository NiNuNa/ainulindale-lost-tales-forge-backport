package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import net.minecraft.client.gui.FontRenderer;

/**
 * Where a window's trailing strip, the row under its newest message,
 * starts its words: where the messages above it start, past the
 * timestamp area. The typing line and the reply chip stand there. The
 * place is laid on the display's grid exactly as the history's text
 * origin is, so the strip slides with the words as the area goes; a
 * caller draws at the whole pixels here inside a matrix moved by the
 * fractions.
 */
final class ChatTrailingStrip {
    /** The words' left edge and the strip's text row, in whole GUI pixels. */
    final int x;
    final int y;
    /** How far the matrix moves to put the whole pixels where the strip really is. */
    final float fractionX;
    final float fractionY;
    /** The width the words may take before the member list. */
    final int room;

    private ChatTrailingStrip(int x, int y, float fractionX, float fractionY,
                              int room) {
        this.x = x;
        this.y = y;
        this.fractionX = fractionX;
        this.fractionY = fractionY;
        this.room = room;
    }

    static ChatTrailingStrip of(ChatFrame frame, FontRenderer font) {
        ChatTimestampColumn columns = ChatTimestampColumn.of(frame, font);
        double originX = LostTalesDisplayPixels.snap(
                frame.drawnLeft() + columns.messageX() * frame.scale);
        double originY = frame.drawnBaseline();
        int wholeX = (int)Math.floor(originX);
        int wholeY = (int)Math.floor(originY);
        int room = (int)Math.round(frame.boxRight - frame.boxLeft
                - ChatMemberList.drawnWidth(frame) * frame.scale
                - (originX - frame.drawnLeft())) - 6;
        return new ChatTrailingStrip(wholeX,
                wholeY + LostTalesChatOverlayRenderer.LINE_HEIGHT
                        - LostTalesChatOverlayRenderer.TEXT_OFFSET,
                (float)(originX - wholeX), (float)(originY - wholeY), room);
    }
}
