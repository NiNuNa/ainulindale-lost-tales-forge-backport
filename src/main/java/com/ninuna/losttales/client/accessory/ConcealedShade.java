package com.ninuna.losttales.client.accessory;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GLContext;

/**
 * Draws the concealed wearer as a faint shade of themselves (ACC-02), in
 * third person and in the inventory's figure. While their body is drawn
 * the game reads them as not concealed, so the body, armour, held item and
 * cape are drawn as ever; every fragment is blended at
 * {@link ConcealmentView#SHADE_ALPHA} whatever colour the renderer gives
 * it, and writes no depth, so what stands behind the shade is still drawn,
 * as vanilla draws a teammate made invisible. The renderer turns blending
 * off after each armour piece and depth writes on before the held items,
 * so the shade is laid on again before each; the blend and depth state
 * found is put back once the body is drawn.
 */
@SideOnly(Side.CLIENT)
public final class ConcealedShade {
    private static Entity drawing;
    private static boolean blendWasOn;
    private static boolean depthWasWritten = true;
    private static int sourceFactor = GL11.GL_SRC_ALPHA;
    private static int destinationFactor = GL11.GL_ONE_MINUS_SRC_ALPHA;
    private static Boolean drawable;

    private ConcealedShade() {}

    /** How {@code player} is drawn for the player at this screen. */
    public static ConcealmentView viewOf(EntityPlayer player) {
        Minecraft minecraft = Minecraft.getMinecraft();
        return ConcealmentView.of(ClientAccessoryEffectCache.wearsConcealment(player),
                minecraft != null && player == minecraft.thePlayer, isDrawable());
    }

    /** Whether {@code entity}'s shade is being drawn now. */
    public static boolean isDrawing(Entity entity) {
        return entity != null && drawing == entity;
    }

    /** Starts the shade for the body about to be drawn. */
    public static void begin(Entity entity) {
        end();
        blendWasOn = GL11.glIsEnabled(GL11.GL_BLEND);
        depthWasWritten = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        sourceFactor = GL11.glGetInteger(GL11.GL_BLEND_SRC);
        destinationFactor = GL11.glGetInteger(GL11.GL_BLEND_DST);
        drawing = entity;
        layOn();
    }

    /** Lays the shade's blending on again, after the renderer took it off. */
    public static void layOn() {
        if (drawing == null) {
            return;
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendColor(1.0F, 1.0F, 1.0F, ConcealmentView.SHADE_ALPHA);
        GL11.glBlendFunc(GL11.GL_CONSTANT_ALPHA, GL11.GL_ONE_MINUS_CONSTANT_ALPHA);
        GL11.glDepthMask(false);
    }

    /** Ends the shade and puts back the blend state it found. */
    public static void end() {
        if (drawing == null) {
            return;
        }
        drawing = null;
        GL14.glBlendColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glBlendFunc(sourceFactor, destinationFactor);
        GL11.glDepthMask(depthWasWritten);
        if (blendWasOn) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    /** Forgets a shade left open, as the client leaves a world. */
    public static void reset() {
        drawing = null;
    }

    private static boolean isDrawable() {
        if (drawable == null) {
            boolean supported;
            try {
                supported = GLContext.getCapabilities().OpenGL14;
            } catch (RuntimeException unavailable) {
                supported = false;
            }
            drawable = Boolean.valueOf(supported);
        }
        return drawable.booleanValue();
    }
}
