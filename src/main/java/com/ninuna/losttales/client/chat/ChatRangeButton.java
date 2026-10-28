package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatSpeechRange;
import com.ninuna.losttales.client.window.BarLead;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import net.minecraft.util.StatCollector;

/**
 * The range button on the bar of a conversation heard nearby (CHAT-37),
 * beside the identity button: a framed button of the identity button's
 * size holding how far the copy speaks. A click steps out (whisper, talk,
 * shout, round to whisper), a right-click back in, the mod's cycle rule.
 *
 * <p>Its picture is a stand-in until Nils paints the three: the sheet's
 * speech bubble with one, two or three pips under it for whisper, talk
 * and shout.</p>
 */
final class ChatRangeButton {
    /** The button's frame: square, as tall as the bar's framed buttons. */
    static final int SIZE = BarLead.IDENTITY_SIZE;
    /** The gap before it: the bar's gap between framed buttons. */
    static final int GAP = BarLead.BUTTON_GAP;
    /** The stand-in's pips: one pixel each, a pixel apart, a pixel under the bubble. */
    private static final int PIP = 1;

    /** How the live bar's button answers the pointer. */
    private final LostTalesUiButtonMotion motion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    /** The pose a resting bar's button is drawn in: never stepped, never lit. */
    private static final LostTalesUiButtonMotion RESTING =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);

    /** How much room the button takes after the identity button on {@code copy}'s bar: none where it has no range. */
    static int room(ConversationPage copy) {
        return ChatSpeechRanges.hasRange(copy) ? GAP + SIZE : 0;
    }

    /** The button's left edge after the identity button ending at {@code identityRight}. */
    static int leftAfter(int identityRight) {
        return identityRight + GAP;
    }

    /** Whether the point is on the frame of the button standing at {@code left}, {@code top}. */
    static boolean contains(double x, double y, int left, int top) {
        return LostTalesUiHitBox.contains(x, y, left, top, SIZE, SIZE);
    }

    /** Steps the live button's motion: lit under the pointer, dropping while pressed. */
    void advance(boolean pointed, boolean pressed) {
        this.motion.advance(System.nanoTime(), pointed, pointed, pressed);
    }

    /** The live bar's button for {@code range}. */
    void draw(int left, int top, ChatSpeechRange range, int surfaceAlpha,
              int alpha) {
        draw(left, top, range, this.motion, surfaceAlpha, alpha);
    }

    /** A resting bar's button: unlit, its picture where it was laid out. */
    static void drawAtRest(int left, int top, ChatSpeechRange range,
                           int surfaceAlpha, int alpha) {
        draw(left, top, range, RESTING, surfaceAlpha, alpha);
    }

    private static void draw(int left, int top, ChatSpeechRange range,
                             LostTalesUiButtonMotion motion, int surfaceAlpha,
                             int alpha) {
        float lit = motion.lit();
        LostTalesUiFramedButton.drawSurface(left, top, SIZE, SIZE, lit,
                surfaceAlpha);
        LostTalesUiSheet bubble = lit > 0.5F ? LostTalesUiSheet.SPEECH_BUBBLE_HOVER
                : LostTalesUiSheet.SPEECH_BUBBLE;
        int pips = range == null ? 2 : range.ordinal() + 1;
        int width = bubble.getWidth();
        int height = bubble.getHeight() + PIP + PIP;
        // Centred, the odd pixel up and to the left.
        int x = left + (SIZE - width) / 2;
        int y = top + (SIZE - height) / 2;
        LostTalesUiInk.beginContent();
        LostTalesUiButton.beginPose(motion, x, y, width, height);
        try {
            bubble.drawWithShadow(x, y, alpha);
            int pipsWidth = pips * PIP + (pips - 1) * PIP;
            int pipLeft = x + (width - pipsWidth) / 2;
            int pipTop = y + bubble.getHeight() + PIP;
            int shadow = LostTalesUiInk.argb(LostTalesUiInk.SHADOW,
                    LostTalesUiInk.shadowAlpha(alpha));
            int ink = LostTalesUiInk.argb(LostTalesUiInk.IVORY, alpha);
            for (int pip = 0; pip < pips; pip++) {
                int pipX = pipLeft + pip * 2 * PIP;
                LostTalesUiInk.fillRect(pipX + LostTalesUiInk.SHADOW_OFFSET,
                        pipTop + LostTalesUiInk.SHADOW_OFFSET,
                        pipX + PIP + LostTalesUiInk.SHADOW_OFFSET,
                        pipTop + PIP + LostTalesUiInk.SHADOW_OFFSET, shadow);
                LostTalesUiInk.fillRect(pipX, pipTop, pipX + PIP, pipTop + PIP,
                        ink);
            }
        } finally {
            LostTalesUiButton.endPose();
        }
        LostTalesUiFramedButton.drawInk(left, top, SIZE, SIZE, lit, alpha);
    }

    /**
     * The button's tip: the range it speaks at and how far that carries,
     * a click stepping it on; {@code blocks} zero until the server says.
     */
    static String tip(ChatSpeechRange range, int blocks) {
        String name = StatCollector.translateToLocal(
                "gui.losttales.chat.range." + range.id);
        return blocks > 0 ? StatCollector.translateToLocalFormatted(
                "gui.losttales.chat.range.tip", name, Integer.toString(blocks))
                : name;
    }
}
