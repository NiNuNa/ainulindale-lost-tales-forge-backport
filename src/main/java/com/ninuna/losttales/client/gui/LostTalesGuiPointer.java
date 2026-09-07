package com.ninuna.losttales.client.gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;

/**
 * Where the pointer actually is, in interface units.
 *
 * <p>A screen is handed its pointer position as whole units:
 * {@code EntityRenderer} divides the mouse's window position by the
 * interface scale in integers, so at GUI Scale 3 the number it passes
 * only changes every third screen pixel. Anything that has to keep pace
 * with the pointer rather than with the grid — a window being dragged, a
 * tooltip standing beside the cursor — asks here instead, and gets the
 * position the division rounded away.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesGuiPointer {

    private LostTalesGuiPointer() {}

    /** The pointer's distance from the left edge, in interface units. */
    public static double x(Minecraft minecraft, int screenWidth) {
        return minecraft == null || minecraft.displayWidth <= 0 ? 0.0D
                : Mouse.getX() * (double)screenWidth / minecraft.displayWidth;
    }

    /**
     * The pointer's distance from the top edge, in interface units. The
     * mouse is measured from the bottom of the window and the interface
     * from the top, and the row under the pointer is the last one.
     */
    public static double y(Minecraft minecraft, int screenHeight) {
        return minecraft == null || minecraft.displayHeight <= 0 ? 0.0D
                : screenHeight - Mouse.getY() * (double)screenHeight
                        / minecraft.displayHeight - 1.0D;
    }
}
