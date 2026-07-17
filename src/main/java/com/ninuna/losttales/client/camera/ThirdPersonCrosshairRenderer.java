package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

/** Draws a stable screen-center reticle for the validated camera-intent ray. */
public final class ThirdPersonCrosshairRenderer extends Gui {
    private static final ThirdPersonCrosshairRenderer INSTANCE =
            new ThirdPersonCrosshairRenderer();

    private ThirdPersonCrosshairRenderer() {}

    public static void render(RenderGameOverlayEvent.Pre event) {
        if (event == null || event.type
                != RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            return;
        }
        if (event.isCanceled()) {
            reset();
            return;
        }
        if (!LostTalesThirdPersonConfig.enableTargetCrosshair) {
            reset();
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!ThirdPersonCameraRuntime.shouldUseCamera(
                minecraft, minecraft == null
                ? null : minecraft.renderViewEntity)) {
            reset();
            return;
        }
        CameraRenderFrame frame =
                ThirdPersonCameraController.getRenderFrame();
        if (frame == null) {
            return;
        }

        int width = event.resolution.getScaledWidth();
        int height = event.resolution.getScaledHeight();
        event.setCanceled(true);
        INSTANCE.draw(minecraft, width / 2, height / 2);
    }

    public static void reset() {}

    private void draw(Minecraft minecraft, int centerX, int centerY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        minecraft.getTextureManager().bindTexture(Gui.icons);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(
                GL11.GL_ONE_MINUS_DST_COLOR,
                GL11.GL_ONE_MINUS_SRC_COLOR, 1, 0);
        this.zLevel = -90.0F;
        this.drawTexturedModalRect(
                centerX - 7, centerY - 7, 0, 0, 16, 16);
        OpenGlHelper.glBlendFunc(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glDisable(GL11.GL_BLEND);
    }
}
