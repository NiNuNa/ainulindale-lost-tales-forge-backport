package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.gui.animation.LostTalesUiEasing;
import com.ninuna.losttales.client.gui.animation.LostTalesUiTransition;
import com.ninuna.losttales.config.LostTalesConfig;

/**
 * A control whose sheet holds its motion as a run of frames: the
 * insert-toolbar chevron and the tab search chevron are the same thing
 * turned through a right angle, and both play their run between an open
 * and a closed state while a pointer crossfades them to their lit
 * artwork.
 *
 * <p>The frame is picked from a {@link LostTalesUiTransition}, so a
 * control flipped back before its run has finished travels on from the
 * frame it is showing instead of jumping to the far end and walking
 * back. Both runs are authored with their end frames mirrored and each
 * cell exactly its own artwork, so the flip is drawn centred in the
 * control's box rather than pinned to a corner.</p>
 */
final class ChatIconFlipbook {
    private final ChatIconSheet[] frames;
    private final ChatIconSheet[] hoverFrames;
    private final LostTalesUiTransition flip = new LostTalesUiTransition();
    private float hoverFade;
    private long hoverNanos;

    ChatIconFlipbook(ChatIconSheet[] frames, ChatIconSheet[] hoverFrames) {
        this.frames = frames;
        this.hoverFrames = hoverFrames;
    }

    /**
     * Advances both the flip and the pointer crossfade to this instant.
     * {@code on} is the state the control is in — the run's last frame —
     * and the run plays backward toward its first when it is dropped.
     */
    void advance(boolean on, boolean hovered) {
        long now = System.nanoTime();
        this.flip.advance(now, on,
                LostTalesConfig.enableChatAnimations
                        ? Math.max(1, LostTalesConfig
                                .chatAnimationDurationMillis)
                        : 0,
                LostTalesUiEasing.SMOOTH);
        double elapsed = this.hoverNanos == 0L ? 0.0D
                : (now - this.hoverNanos) / 1.0E9D;
        this.hoverNanos = now;
        this.hoverFade = LostTalesChatVisualStyle.hoverFade(this.hoverFade,
                hovered, elapsed);
    }

    /** The frame the run stands on this instant. */
    private int frameIndex() {
        return Math.round(this.flip.clamped() * (this.frames.length - 1));
    }

    /**
     * The frame centred in the control's box, with the lit artwork laid
     * over it as far as the pointer has brought it. The box is the
     * control's whole hit square: the frames differ in size along the
     * axis the chevron folds on, and centring each one in the box is
     * what keeps the run on one spot instead of walking it across the
     * control.
     */
    void draw(int boxLeft, int boxTop, int boxWidth, int boxHeight,
              int alpha) {
        int index = frameIndex();
        ChatIconSheet icon = this.frames[index];
        ChatIconSheet.drawPairWithShadow(icon, this.hoverFrames[index],
                this.hoverFade,
                boxLeft + (boxWidth - icon.getWidth()) / 2,
                boxTop + (boxHeight - icon.getHeight()) / 2, alpha);
    }
}
