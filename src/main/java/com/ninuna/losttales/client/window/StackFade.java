package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How strongly each window shows while others lie over it, as a stack of
 * papers reads: the window in front shows whole, and a window shows an
 * eighth less for each window in front of it that overlaps it, never less
 * than half. Windows count only windows, and sub-windows only sub-windows:
 * a sub-window never fades a window, nor a window a sub-window. The window
 * the pointer rests on shows whole, so what you point at reads, and fades
 * back as the pointer leaves. A window glides to its new strength as
 * windows come and go over it, and one seen for the first time stands at
 * its strength at once.
 *
 * <p>A window fades as one picture ({@link WindowDrawing#beginStackFade}),
 * over the world it cut itself onto, so the world shows through a faded
 * window and another window never does.</p>
 */
final class StackFade {
    /** What each window lying over another takes from it. */
    static final float STEP = 0.125F;
    /** The least a window shows, however many lie over it. */
    static final float LEAST = 0.5F;

    private Map<Object, Float> shares = new HashMap<Object, Float>();
    private long lastNanos;

    /** How strongly a window shows with {@code over} windows overlapping it from in front. */
    static float strengthUnder(int over) {
        return Math.max(LEAST, 1.0F - STEP * Math.max(0, over));
    }

    /**
     * For boxes listed back to front, how many of the boxes after each one
     * overlap it. A null box, a window not on screen, neither counts nor is
     * counted; boxes that only touch do not overlap.
     */
    static int[] overlapsInFront(List<LostTalesUiHitBox> boxes) {
        int[] over = new int[boxes.size()];
        for (int index = 0; index < boxes.size(); index++) {
            LostTalesUiHitBox box = boxes.get(index);
            if (box == null) {
                continue;
            }
            for (int front = index + 1; front < boxes.size(); front++) {
                LostTalesUiHitBox other = boxes.get(front);
                if (other != null && overlap(box, other)) {
                    over[index]++;
                }
            }
        }
        return over;
    }

    private static boolean overlap(LostTalesUiHitBox one, LostTalesUiHitBox other) {
        return one.left < other.right() && other.left < one.right()
                && one.top < other.bottom() && other.top < one.bottom();
    }

    /**
     * Each key's share this frame, the keys and their boxes listed back to
     * front: eased toward what the windows over it leave it, or toward
     * whole for {@code pointed}, the key the pointer rests on (null for
     * none). A key seen for the first time stands at its share at once;
     * keys not listed are forgotten.
     */
    void advance(List<?> keys, List<LostTalesUiHitBox> boxes, Object pointed,
                 long nowNanos) {
        int[] over = overlapsInFront(boxes);
        double elapsed = this.lastNanos == 0L ? 0.0D
                : Math.max(0L, nowNanos - this.lastNanos) / 1.0E9D;
        this.lastNanos = nowNanos;
        Map<Object, Float> next = new HashMap<Object, Float>();
        for (int index = 0; index < keys.size(); index++) {
            float target = keys.get(index).equals(pointed) ? 1.0F
                    : strengthUnder(over[index]);
            Float current = this.shares.get(keys.get(index));
            next.put(keys.get(index), current == null ? target
                    : (float)Motions.follow(MotionIds.WINDOW_STACK_FADE,
                            current.floatValue(), target, elapsed));
        }
        this.shares = next;
    }

    /** The share a key showed at when last advanced; whole for one not listed. */
    float shareOf(Object key) {
        Float share = this.shares.get(key);
        return share == null ? 1.0F : share.floatValue();
    }
}
