package com.ninuna.losttales.gui.hud.compass;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarker;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerBatchBuilder;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerProvider;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerRenderItem;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesDirectionCompassMarkerProvider;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesHostileCompassMarkerProvider;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesStaticCompassMarkerProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class LostTalesCompassHudRenderer {
    public static final ResourceLocation COMPASS_HUD_TEXTURE = new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/gui/compasshud.png");

    public static final int COMPASS_HUD_TEXTURE_WIDTH = 257;
    public static final int COMPASS_HUD_TEXTURE_HEIGHT = 64;
    public static final int COMPASS_WIDTH = COMPASS_HUD_TEXTURE_WIDTH;
    public static final int COMPASS_HEIGHT = 24;

    public static final int MAP_MARKER_OFFSET_Y = 8;
    public static final int MAP_MARKER_NAME_LABEL_OFFSET_Y = 3;
    public static final int MAP_MARKER_DISTANCE_LABEL_OFFSET_Y = 4;
    public static final float MAP_MARKER_SCALE_MODIFIER = 0.18F;
    /** Minimum opacity for positioned markers that are still inside their configured distance threshold. */
    public static final float MAP_MARKER_DISTANCE_FADE_IN_FLOOR_ALPHA = 0.4F;
    public static final float MAP_MARKER_SHADOW_ALPHA = 0.6F;
    public static final int MAP_MARKER_VERTICAL_ARROW_INDICATOR_OFFSET_X = 2;
    public static final int MAP_MARKER_BEGIN_EDGE_FADE_OUT_OFFSET = 24;
    public static final int MAP_MARKER_BEGIN_CENTER_FOCUS_OFFSET = 26;
    /**
     * Width of the focused-label fade-out band before the normal marker edge
     * fade begins. Keeping this separate from the icon edge fade lets labels
     * disappear before markers naturally fade out at the compass border.
     */
    public static final int MAP_MARKER_FOCUS_EDGE_FADE_OUT_WIDTH = 22;
    /**
     * The focused-marker label becomes latched once it is nearly centered. This
     * avoids missing the exact center pixel on fast or low-FPS camera movement,
     * while still using MAP_MARKER_BEGIN_CENTER_FOCUS_OFFSET for the visible
     * fade-in interval.
     */
    private static final float MAP_MARKER_FOCUS_LATCH_ALPHA = 0.98F;
    /**
     * Minecraft 1.7.10 FontRenderer treats colors whose top alpha bits are zero
     * as legacy RGB colors and forces them back to fully opaque. Keep compass
     * marker text below this byte alpha invisible instead of letting it flash.
     */
    private static final int MAP_MARKER_MIN_TEXT_ALPHA = 4;

    private static final List<LostTalesCompassMarkerProvider> MARKER_PROVIDERS = createMarkerProviders();
    private static String focusedMarkerStateKey;
    private static boolean focusedMarkerLabelLatched;
    private static float focusedMarkerLastEffectAlpha;

    private LostTalesCompassHudRenderer() {}

    public static void render(Minecraft minecraft, float partialTicks) {
        if (!LostTalesConfig.showLostTalesHud || !LostTalesConfig.showCompassHud || minecraft == null || minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.gameSettings.hideGUI) {
            return;
        }

        ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();

        int offsetX = LostTalesConfig.compassHudOffsetX;
        int offsetY = LostTalesConfig.compassHudOffsetY;
        int displayRadiusDeg = MathHelper.clamp_int(LostTalesConfig.compassHudDisplayRadius, 45, 225);

        int compassX = (width - COMPASS_WIDTH) * offsetX / 100;
        int compassY = height * offsetY / 100 + minecraft.fontRenderer.FONT_HEIGHT + MAP_MARKER_DISTANCE_LABEL_OFFSET_Y;
        int centerX = compassX + COMPASS_WIDTH / 2;

        float pxPerDeg = (float) COMPASS_WIDTH / (float) displayRadiusDeg;
        float viewYaw = minecraft.thePlayer.prevRotationYaw + (minecraft.thePlayer.rotationYaw - minecraft.thePlayer.prevRotationYaw) * partialTicks;
        float normalizedYaw = LostTalesCompassHudRenderHelper.normalizeViewYaw(viewYaw);

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        /*
         * Draw the original compass HUD PNG as a single sprite.  The texture
         * already contains both the soft dark fade and the sharp guide line;
         * forcing linear filtering blurs the line, while alpha testing clips
         * the low-alpha fade pixels.  Temporarily disabling alpha test lets the
         * PNG's own transparency render as authored, without changing texture
         * filtering or splitting the art into separate files.
         */
        LostTalesCompassHudRenderHelper.drawTexturedRectNoAlphaTest(
                minecraft,
                COMPASS_HUD_TEXTURE,
                compassX,
                compassY,
                0,
                0,
                COMPASS_WIDTH,
                COMPASS_HEIGHT,
                COMPASS_HUD_TEXTURE_WIDTH,
                COMPASS_HUD_TEXTURE_HEIGHT,
                1.0F
        );

        renderMarkers(minecraft, compassY, centerX, normalizedYaw, pxPerDeg, displayRadiusDeg, partialTicks);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

    private static void renderMarkers(Minecraft minecraft, int compassY, int centerX, float yawDeg, float pxPerDeg, int visibleDeg, float partialTicks) {
        List<LostTalesCompassMarker> markers = collectMarkers(minecraft, partialTicks);
        if (markers.isEmpty()) {
            resetFocusedMarkerState();
            return;
        }

        LostTalesCompassHudRenderHelper.PlayerPos playerPos = LostTalesCompassHudRenderHelper.lerpPlayerPos(minecraft.thePlayer, partialTicks);
        LostTalesCompassMarkerBatchBuilder.LostTalesCompassMarkerBatch batch = LostTalesCompassMarkerBatchBuilder.build(markers, playerPos, yawDeg, pxPerDeg, visibleDeg, centerX, focusedMarkerStateKey);
        if (batch.renderItems.isEmpty()) {
            resetFocusedMarkerState();
            return;
        }

        float focusedEffectAlpha = updateFocusedMarkerEffectAlpha(batch);

        for (LostTalesCompassMarkerRenderItem item : batch.renderItems) {
            LostTalesCompassMarkerIcon icon = item.marker.getIcon();
            float scale = item.marker == batch.focusedMarker && item.marker.isScaleWithCenterFocus()
                    ? 1.0F + MAP_MARKER_SCALE_MODIFIER * focusedEffectAlpha
                    : 1.0F;
            float shadowAlpha = Math.min(MAP_MARKER_SHADOW_ALPHA, item.alpha);

            GL11.glPushMatrix();
            GL11.glTranslatef(item.x, compassY + MAP_MARKER_OFFSET_Y + LostTalesCompassMarkerIcon.HEIGHT / 2.0F, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            LostTalesCompassHudRenderHelper.drawTexturedRectWithShadowTinted(
                    minecraft,
                    LostTalesCompassMarkerIcon.TEXTURE,
                    -LostTalesCompassMarkerIcon.WIDTH / 2.0F,
                    -LostTalesCompassMarkerIcon.HEIGHT,
                    icon.getU(),
                    icon.getV(),
                    LostTalesCompassMarkerIcon.WIDTH,
                    LostTalesCompassMarkerIcon.HEIGHT,
                    LostTalesCompassMarkerIcon.TEXTURE_WIDTH,
                    LostTalesCompassMarkerIcon.TEXTURE_HEIGHT,
                    item.marker.getRed(),
                    item.marker.getGreen(),
                    item.marker.getBlue(),
                    item.alpha,
                    shadowAlpha
            );
            GL11.glPopMatrix();
        }

        renderFocusedMarkerLabel(minecraft, compassY, batch, focusedEffectAlpha);
    }

    private static float updateFocusedMarkerEffectAlpha(LostTalesCompassMarkerBatchBuilder.LostTalesCompassMarkerBatch batch) {
        LostTalesCompassMarker marker = batch.focusedMarker;
        if (marker == null || batch.focusFadeOutAlpha <= 0.0F) {
            resetFocusedMarkerState();
            return 0.0F;
        }

        String stateKey = LostTalesCompassMarkerBatchBuilder.getFocusStateKey(marker);
        boolean targetChanged = focusedMarkerStateKey == null || !focusedMarkerStateKey.equals(stateKey);
        if (targetChanged) {
            /*
             * If another map marker becomes the closest marker while the old
             * label is still visible, keep the label/highlight alive instead
             * of forcing a second green-zone fade-in. The orange-zone fade-out
             * still applies to the new target below.
             */
            focusedMarkerLabelLatched = focusedMarkerLastEffectAlpha >= (float) MAP_MARKER_MIN_TEXT_ALPHA / 255.0F;
            focusedMarkerStateKey = stateKey;
        }

        if (!focusedMarkerLabelLatched && batch.focusFadeInAlpha >= MAP_MARKER_FOCUS_LATCH_ALPHA) {
            focusedMarkerLabelLatched = true;
        }

        float alpha = focusedMarkerLabelLatched
                ? batch.focusFadeOutAlpha
                : Math.min(batch.focusFadeInAlpha, batch.focusFadeOutAlpha);
        alpha = MathHelper.clamp_float(alpha, 0.0F, 1.0F);

        if (alpha <= 0.0F) {
            resetFocusedMarkerState();
        } else {
            focusedMarkerLastEffectAlpha = alpha;
        }
        return alpha;
    }

    private static void resetFocusedMarkerState() {
        focusedMarkerStateKey = null;
        focusedMarkerLabelLatched = false;
        focusedMarkerLastEffectAlpha = 0.0F;
    }

    private static void renderFocusedMarkerLabel(Minecraft minecraft, int compassY, LostTalesCompassMarkerBatchBuilder.LostTalesCompassMarkerBatch batch, float focusedEffectAlpha) {
        LostTalesCompassMarker marker = batch.focusedMarker;
        if (marker == null || focusedEffectAlpha <= 0.0F || marker.getName() == null || marker.getName().length() == 0) {
            return;
        }

        FontRenderer fontRenderer = minecraft.fontRenderer;
        float labelAlphaF = MathHelper.clamp_float(focusedEffectAlpha * batch.focusDistanceAlpha, 0.0F, 1.0F);
        int alpha = MathHelper.clamp_int((int) (labelAlphaF * 255.0F), 0, 255);

        /*
         * FontRenderer in Minecraft 1.7.10 does not only special-case an
         * alpha byte of 0. It checks the top alpha bits and treats alpha values
         * 0..3 as if no alpha was supplied, then ORs in full opacity. The
         * center-focus fade can legitimately produce those tiny alpha values
         * at the edge of the label visibility radius, which makes the marker
         * text flash back at full opacity for one frame or a very small angle.
         *
         * The textured height indicator does not flicker because it uses GL
         * float alpha directly. For text, skip the draw while the quantized
         * alpha is in FontRenderer's unsafe legacy-color range.
         */
        if (alpha < MAP_MARKER_MIN_TEXT_ALPHA) {
            return;
        }

        int color = (alpha << 24) | 0xFFFFFF;
        int nameY = compassY + COMPASS_HEIGHT + MAP_MARKER_NAME_LABEL_OFFSET_Y;
        int distY = compassY - fontRenderer.FONT_HEIGHT - MAP_MARKER_DISTANCE_LABEL_OFFSET_Y;

        LostTalesCompassHudRenderHelper.drawCenteredString(fontRenderer, marker.getName(), batch.focusedPx, nameY, color, true);

        if (marker.isShowDistanceLabel()) {
            double distBlocks = Math.sqrt(batch.dx * batch.dx + batch.dy * batch.dy + batch.dz * batch.dz);
            String distLabel = Math.round(distBlocks) + "m";
            LostTalesCompassHudRenderHelper.drawCenteredString(fontRenderer, distLabel, batch.focusedPx, distY, color, true);

            double deltaY = batch.dy;
            if (Math.abs(deltaY) >= 5.0D) {
                int u = 0;
                int v = 26;
                int w = 5;
                int h = 3;
                if (deltaY >= 10.0D) {
                    h = 7;
                } else if (deltaY <= -10.0D) {
                    u = 6;
                    h = 7;
                } else if (deltaY <= -5.0D) {
                    u = 6;
                }

                float arrowX = batch.focusedPx + fontRenderer.getStringWidth(distLabel) / 2.0F + MAP_MARKER_VERTICAL_ARROW_INDICATOR_OFFSET_X;
                LostTalesCompassHudRenderHelper.drawTexturedRectWithShadow(
                        minecraft,
                        COMPASS_HUD_TEXTURE,
                        arrowX,
                        distY,
                        u,
                        v,
                        w,
                        h,
                        COMPASS_HUD_TEXTURE_WIDTH,
                        COMPASS_HUD_TEXTURE_HEIGHT,
                        labelAlphaF,
                        Math.min(MAP_MARKER_SHADOW_ALPHA, labelAlphaF)
                );
            }
        }
    }

    private static List<LostTalesCompassMarker> collectMarkers(Minecraft minecraft, float partialTicks) {
        List<LostTalesCompassMarker> markers = new ArrayList<LostTalesCompassMarker>();
        for (LostTalesCompassMarkerProvider provider : MARKER_PROVIDERS) {
            try {
                List<LostTalesCompassMarker> collected = provider.collectMarkers(minecraft, partialTicks);
                if (collected != null && !collected.isEmpty()) {
                    markers.addAll(collected);
                }
            } catch (Throwable ignored) {
                // A HUD provider should never crash the whole render tick.
            }
        }
        return markers;
    }

    private static List<LostTalesCompassMarkerProvider> createMarkerProviders() {
        List<LostTalesCompassMarkerProvider> providers = new ArrayList<LostTalesCompassMarkerProvider>();
        providers.add(new LostTalesDirectionCompassMarkerProvider());
        providers.add(new LostTalesStaticCompassMarkerProvider());
        providers.add(new LostTalesHostileCompassMarkerProvider());
        return Collections.unmodifiableList(providers);
    }
}
