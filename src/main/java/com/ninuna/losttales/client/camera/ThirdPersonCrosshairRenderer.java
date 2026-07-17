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
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean useThirdPersonCamera = ThirdPersonCameraRuntime
                .shouldUseCamera(
                minecraft, minecraft == null
                ? null : minecraft.renderViewEntity);
        boolean drawChargeIndicator = minecraft != null
                && minecraft.thePlayer != null
                && minecraft.gameSettings != null
                && !minecraft.gameSettings.hideGUI
                && LostTalesThirdPersonConfig.enableChargeTierFeedback
                && ThirdPersonChargeFeedbackController
                .getDisplayTier() > 0;
        if (!useThirdPersonCamera && !drawChargeIndicator) {
            reset();
            return;
        }
        boolean drawCrosshair = useThirdPersonCamera
                && LostTalesThirdPersonConfig
                .enableTargetCrosshair;
        boolean drawLockIndicator = useThirdPersonCamera
                && LostTalesThirdPersonConfig
                .enableTargetLockIndicator
                && ThirdPersonTargetLockController.hasTarget(
                minecraft.thePlayer);
        if (!drawCrosshair && !drawLockIndicator
                && !drawChargeIndicator) {
            reset();
            return;
        }
        if (useThirdPersonCamera
                && ThirdPersonCameraController.getRenderFrame() == null) {
            return;
        }

        int width = event.resolution.getScaledWidth();
        int height = event.resolution.getScaledHeight();
        if (drawCrosshair) {
            event.setCanceled(true);
            INSTANCE.draw(minecraft, width / 2, height / 2);
        }
        if (drawLockIndicator) {
            INSTANCE.drawLockIndicator(width / 2, height / 2);
        }
        if (drawChargeIndicator) {
            INSTANCE.drawChargeIndicator(
                    width / 2, height / 2,
                    ThirdPersonChargeFeedbackController
                            .getDisplayTier());
        }
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

    private void drawLockIndicator(int centerX, int centerY) {
        int color = 0xD9E7C76A;
        int inner = 9;
        int outer = 13;
        int length = 5;
        drawRect(centerX - outer, centerY - outer,
                centerX - outer + length, centerY - inner, color);
        drawRect(centerX + inner, centerY - outer,
                centerX + outer, centerY - inner, color);
        drawRect(centerX - outer, centerY + inner,
                centerX - outer + length, centerY + outer, color);
        drawRect(centerX + inner, centerY + inner,
                centerX + outer, centerY + outer, color);
    }

    private void drawChargeIndicator(
            int centerX, int centerY, int chargeTier) {
        int startX = centerX - 8;
        int y = centerY + 17;
        for (int index = 0; index < 3; index++) {
            int color = index < chargeTier
                    ? 0xE6E7C76A : 0x80403B31;
            drawRect(startX + index * 6, y,
                    startX + index * 6 + 4, y + 2, color);
        }
    }
}
