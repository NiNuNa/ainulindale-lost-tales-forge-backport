package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiEasing;
import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Map;
import java.util.WeakHashMap;
import org.lwjgl.opengl.GL11;

/** Independent, fixed-screen entrance shared by every map popup. */
@SideOnly(Side.CLIENT)
final class LostTalesMapPopupAnimation {
    private static final float TRAVEL_PIXELS = 14.0F;
    private static final float START_SCALE = 0.985F;
    private static final float GUI_MODELVIEW_Z = -2000.0F;
    private static final Map<Object, Long> STARTS =
            new WeakHashMap<Object, Long>();

    private LostTalesMapPopupAnimation() {}

    static void restart(Object popup) {
        if (popup == null) {
            return;
        }
        synchronized (STARTS) {
            STARTS.put(popup, Long.valueOf(System.nanoTime()));
        }
    }

    /** Starts an entrance on the first rendered frame, not on a bare hit test. */
    static void begin(Object popup) {
        if (popup == null) {
            return;
        }
        synchronized (STARTS) {
            if (!STARTS.containsKey(popup)) {
                STARTS.put(popup, Long.valueOf(System.nanoTime()));
            }
        }
    }

    static void push(Object popup, int pivotX, int pivotY) {
        Sample sample = sample(popup);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(pivotX, pivotY + sample.offsetY,
                GUI_MODELVIEW_Z);
        GL11.glScalef(sample.scale, sample.scale, 1.0F);
        GL11.glTranslatef(-pivotX, -pivotY, 0.0F);
    }

    static void pushFixed() {
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, GUI_MODELVIEW_Z);
    }

    static void pop() {
        GL11.glPopMatrix();
    }

    static int inverseMouseX(Object popup, int mouseX, int pivotX) {
        Sample sample = sample(popup);
        return Math.round(pivotX
                + (mouseX - pivotX) / sample.scale);
    }

    static int inverseMouseY(Object popup, int mouseY, int pivotY) {
        Sample sample = sample(popup);
        return Math.round(pivotY
                + (mouseY - pivotY - sample.offsetY) / sample.scale);
    }

    static float progress(Object popup) {
        return sample(popup).progress;
    }

    /** The same eased completion share used by the popup's visual motion. */
    static float easedProgress(Object popup) {
        return sample(popup).easedProgress;
    }

    private static Sample sample(Object popup) {
        if (!LostTalesConfig.enableGuiAnimations || popup == null) {
            return Sample.SETTLED;
        }
        long now = System.nanoTime();
        long started;
        synchronized (STARTS) {
            Long known = STARTS.get(popup);
            if (known == null) {
                return Sample.SETTLED;
            }
            started = known.longValue();
        }
        int durationMillis = Math.max(60,
                LostTalesConfig.guiAnimationDurationMillis);
        float travel = TRAVEL_PIXELS;
        if (LostTalesConfig.reducedGuiMotion) {
            durationMillis = Math.min(durationMillis, 90);
            travel = 5.0F;
        }
        float progress = LostTalesGuiEasing.clamp(
                (now - started) / (durationMillis * 1000000.0F));
        float eased = LostTalesGuiEasing.easeOutCubic(progress);
        return new Sample(progress, eased, travel * (1.0F - eased),
                START_SCALE + (1.0F - START_SCALE) * eased);
    }

    private static final class Sample {
        private static final Sample SETTLED =
                new Sample(1.0F, 1.0F, 0.0F, 1.0F);
        private final float progress;
        private final float easedProgress;
        private final float offsetY;
        private final float scale;

        private Sample(float progress, float easedProgress,
                       float offsetY, float scale) {
            this.progress = progress;
            this.easedProgress = easedProgress;
            this.offsetY = offsetY;
            this.scale = scale;
        }
    }
}
