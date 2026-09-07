package com.ninuna.losttales.client.gui.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import com.ninuna.losttales.client.gui.LostTalesGuiPointer;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/**
 * Moves a tooltip with the pointer instead of with the interface grid.
 *
 * <p>Every screen is handed the pointer as a whole number of interface
 * units: {@code EntityRenderer} works it out as
 * {@code Mouse.getX() * scaledWidth / displayWidth}, in integers. At GUI
 * Scale 3 one of those units is three screen pixels, so the pointer
 * crosses three pixels before anything drawn at that position moves at
 * all — and then it jumps all three at once. The cursor itself is drawn
 * by the window system at screen resolution, so the tooltip visibly
 * stutters along behind a pointer that is gliding.</p>
 *
 * <p>The remainder that division threw away is put back here, rounded to
 * a whole screen pixel. That is as fine as the pointer itself moves, so
 * the tooltip keeps pace with it exactly; and because the offset is a
 * whole number of screen pixels, every glyph and every border row still
 * lands on one, so nothing is drawn blurred between two.</p>
 *
 * <p>The offset is only applied when the position it is given really is
 * the pointer's — a caller that draws a tooltip somewhere else of its own
 * choosing is left alone.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesTooltipSmoothing {

    /**
     * Set by the coremod once {@code GuiScreen.drawHoveringText} is
     * wrapped. Nothing reads it to decide whether to smooth — the wrap
     * either happened or the hooks are never called — but a runtime log
     * can say which patches took.
     */
    public static final String ACTIVE_PROPERTY =
            "losttales.tooltipSmoothingTransformer.active";

    /** Past this the position is somebody's own choice, not the pointer. */
    private static final float POINTER_TOLERANCE = 1.0F;

    private LostTalesTooltipSmoothing() {}

    /**
     * Shifts what follows onto the pointer's true position. Always pushes
     * a matrix, so {@link #end()} always has exactly one to take off
     * however the offset works out.
     */
    public static void begin(int mouseX, int mouseY) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(offsetX(mouseX), offsetY(mouseY), 0.0F);
        } catch (RuntimeException unavailable) {
            // No display, no resolution to measure against. The tooltip
            // is drawn where it would have been, and the matrix still
            // balances.
        }
    }

    /** Ends what {@link #begin} started. */
    public static void end() {
        GL11.glPopMatrix();
    }

    /**
     * How far right of {@code mouseX} the pointer really is, in interface
     * units, rounded to a whole screen pixel; zero when that position is
     * not the pointer's.
     */
    public static float offsetX(int mouseX) {
        ScaledResolution resolution = resolution();
        if (resolution == null) {
            return 0.0F;
        }
        double exact = LostTalesGuiPointer.x(
                Minecraft.getMinecraft(), resolution.getScaledWidth());
        return quantize((float)(exact - mouseX), resolution.getScaleFactor());
    }

    /** As {@link #offsetX}, downward. */
    public static float offsetY(int mouseY) {
        ScaledResolution resolution = resolution();
        if (resolution == null) {
            return 0.0F;
        }
        double exact = LostTalesGuiPointer.y(
                Minecraft.getMinecraft(), resolution.getScaledHeight());
        return quantize((float)(exact - mouseY), resolution.getScaleFactor());
    }

    /**
     * The remainder as whole screen pixels, or nothing when the position
     * was not the pointer's. Package visible: that the answer is always a
     * whole number of screen pixels is the whole point of it.
     */
    static float quantize(float delta, int scaleFactor) {
        if (scaleFactor <= 0 || Math.abs(delta) >= POINTER_TOLERANCE) {
            return 0.0F;
        }
        return Math.round(delta * scaleFactor) / (float)scaleFactor;
    }

    private static ScaledResolution resolution() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null || minecraft.displayWidth <= 0
                    || minecraft.displayHeight <= 0) {
                return null;
            }
            return new ScaledResolution(minecraft,
                    minecraft.displayWidth, minecraft.displayHeight);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }
}
