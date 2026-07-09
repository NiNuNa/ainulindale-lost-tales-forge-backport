package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarker;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.LOTRDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Draws Lost Tales map-marker icons over LOTR's normal waypoint dots.
 *
 * <p>The LOTR map still owns selection, tooltips, and fast travel. This overlay
 * only replaces the tiny visual dot with the same icon used by the compass for
 * markers that opt into LOTR waypoints.</p>
 */
public final class LostTalesLotrMapMarkerIconOverlay {
    private static final int ICON_DRAW_SIZE = 13;
    private static final int HOVER_SKIP_RADIUS = 18;

    private static Method transformCoordsMethod;
    private static Field mapXMinField;
    private static Field mapXMaxField;
    private static Field mapYMinField;
    private static Field mapYMaxField;
    private static boolean reflectionReady;
    private static boolean reflectionFailed;

    private LostTalesLotrMapMarkerIconOverlay() {}

    public static void render(GuiScreen gui, int mouseX, int mouseY) {
        if (!(gui instanceof LOTRGuiMap)) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null || minecraft.theWorld == null) {
            return;
        }
        if (minecraft.theWorld.provider.dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return;
        }
        if (!ensureReflection()) {
            return;
        }

        try {
            int mapXMin = mapXMinField.getInt(null);
            int mapXMax = mapXMaxField.getInt(null);
            int mapYMin = mapYMinField.getInt(null);
            int mapYMax = mapYMaxField.getInt(null);

            GL11.glPushMatrix();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            minecraft.getTextureManager().bindTexture(LostTalesCompassMarkerIcon.TEXTURE);

            for (LostTalesMapMarkerData marker : LostTalesClientMapMarkerStore.getAllMarkers()) {
                if (!shouldRenderMarker(marker)) {
                    continue;
                }
                float[] pos = (float[]) transformCoordsMethod.invoke(gui, Float.valueOf((float) marker.getX()), Float.valueOf((float) marker.getZ()));
                if (pos == null || pos.length < 2) {
                    continue;
                }
                float screenX = pos[0];
                float screenY = pos[1];
                int half = ICON_DRAW_SIZE / 2;
                if (screenX < mapXMin + half || screenX > mapXMax - half || screenY < mapYMin + half || screenY > mapYMax - half) {
                    continue;
                }
                if (Math.abs(screenX - mouseX) <= HOVER_SKIP_RADIUS && Math.abs(screenY - mouseY) <= HOVER_SKIP_RADIUS) {
                    // LOTR draws the waypoint tooltip after selecting/hovering.
                    // Since this overlay is drawn from the GUI post event, skip
                    // the icon under the mouse so it never covers the tooltip.
                    continue;
                }

                LostTalesCompassMarkerIcon icon = LostTalesCompassMarkerIcon.fromName(marker.getIconName());
                float[] color = LostTalesCompassMarker.parseColor(marker.getColorName());
                drawIcon(screenX - half, screenY - half, icon, color[0], color[1], color[2], 1.0F);
            }

            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glPopMatrix();
        } catch (Throwable ignored) {
            // If LOTR changes the GUI internals, fail closed and keep the map usable.
        }
    }

    private static boolean shouldRenderMarker(LostTalesMapMarkerData marker) {
        if (marker == null || !marker.isWaypoint()) {
            return false;
        }
        if (marker.getDimensionId() != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return false;
        }
        if (marker.isHiddenUntilDiscovered() && !LostTalesClientQuestProgressStore.isMarkerDiscovered(marker.getId())) {
            return false;
        }
        return true;
    }

    private static boolean ensureReflection() {
        if (reflectionReady) {
            return true;
        }
        if (reflectionFailed) {
            return false;
        }
        try {
            transformCoordsMethod = LOTRGuiMap.class.getDeclaredMethod("transformCoords", float.class, float.class);
            transformCoordsMethod.setAccessible(true);
            mapXMinField = LOTRGuiMap.class.getDeclaredField("mapXMin");
            mapXMaxField = LOTRGuiMap.class.getDeclaredField("mapXMax");
            mapYMinField = LOTRGuiMap.class.getDeclaredField("mapYMin");
            mapYMaxField = LOTRGuiMap.class.getDeclaredField("mapYMax");
            mapXMinField.setAccessible(true);
            mapXMaxField.setAccessible(true);
            mapYMinField.setAccessible(true);
            mapYMaxField.setAccessible(true);
            reflectionReady = true;
            return true;
        } catch (Throwable ignored) {
            reflectionFailed = true;
            return false;
        }
    }

    private static void drawIcon(float x, float y, LostTalesCompassMarkerIcon icon, float red, float green, float blue, float alpha) {
        float u0 = (float) icon.getU() / (float) LostTalesCompassMarkerIcon.TEXTURE_WIDTH;
        float v0 = (float) icon.getV() / (float) LostTalesCompassMarkerIcon.TEXTURE_HEIGHT;
        float u1 = (float) (icon.getU() + LostTalesCompassMarkerIcon.WIDTH) / (float) LostTalesCompassMarkerIcon.TEXTURE_WIDTH;
        float v1 = (float) (icon.getV() + LostTalesCompassMarkerIcon.HEIGHT) / (float) LostTalesCompassMarkerIcon.TEXTURE_HEIGHT;

        GL11.glColor4f(red, green, blue, alpha);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + ICON_DRAW_SIZE, 0.0D, u0, v1);
        tessellator.addVertexWithUV(x + ICON_DRAW_SIZE, y + ICON_DRAW_SIZE, 0.0D, u1, v1);
        tessellator.addVertexWithUV(x + ICON_DRAW_SIZE, y, 0.0D, u1, v0);
        tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
        tessellator.draw();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
