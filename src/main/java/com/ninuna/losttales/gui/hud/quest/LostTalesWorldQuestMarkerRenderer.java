package com.ninuna.losttales.gui.hud.quest;

import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerStore;
import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.client.quest.LostTalesClientQuestMarkerHelper;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;
/**
 * Labels in the world over the places tracked quests send the player: the
 * quest's title and the distance, drawn the way a nameplate is, facing the
 * camera. A label shows out to twice the configured distance.
 */
@SideOnly(Side.CLIENT)
public final class LostTalesWorldQuestMarkerRenderer {
    private static final float LABEL_SCALE = 0.02666667F;
    private static final double LABEL_Y_OFFSET = 2.25D;

    private LostTalesWorldQuestMarkerRenderer() {}

    public static void render(Minecraft minecraft, float partialTicks) {
        if (!shouldRender(minecraft)) {
            return;
        }

        EntityPlayer player = minecraft.thePlayer;
        Map<String, String> activeQuestMarkers = LostTalesClientQuestMarkerHelper.collectActiveQuestMarkerLabels();
        Set<LostTalesClientQuestMarkerHelper.ActiveCoordinateMarker> activeCoordinateMarkers = LostTalesClientQuestMarkerHelper.collectActiveCoordinateMarkers();
        if (activeQuestMarkers.isEmpty() && activeCoordinateMarkers.isEmpty()) {
            return;
        }

        List<LostTalesMapMarkerData> markers = LostTalesClientMapMarkerStore.getSharedMarkers();
        if (markers.isEmpty() && activeCoordinateMarkers.isEmpty()) {
            return;
        }

        double cameraX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double cameraY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double cameraZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        int dimension = minecraft.theWorld.provider.dimensionId;
        double maxDistance = Math.max(48.0D, LostTalesConfig.worldQuestMarkerMaxDistance);
        double maxDistanceSq = maxDistance * maxDistance;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        try {
            for (LostTalesMapMarkerData marker : markers) {
                if (marker == null || marker.getId() == null || marker.getDimensionId() != dimension) {
                    continue;
                }

                String label = LostTalesClientQuestMarkerHelper
                        .getActiveQuestMarkerLabel(activeQuestMarkers,
                                marker.getId());
                if (label == null) {
                    continue;
                }

                double dxPlayer = player.posX - marker.getX();
                double markerY = marker.getEffectiveY(
                        minecraft.theWorld, player.posY);
                double dyPlayer = player.posY - markerY;
                double dzPlayer = player.posZ - marker.getZ();
                double distSq = dxPlayer * dxPlayer + dyPlayer * dyPlayer + dzPlayer * dzPlayer;
                if (distSq > maxDistanceSq) {
                    continue;
                }
                renderMarkerLabel(minecraft.fontRenderer, label, marker.getX(), markerY, marker.getZ(), Math.sqrt(distSq), cameraX, cameraY, cameraZ);
            }

            for (LostTalesClientQuestMarkerHelper.ActiveCoordinateMarker marker : activeCoordinateMarkers) {
                if (marker == null || marker.getDimensionId() != dimension) {
                    continue;
                }
                double dxPlayer = player.posX - marker.getX();
                double dyPlayer = player.posY - marker.getY();
                double dzPlayer = player.posZ - marker.getZ();
                double distSq = dxPlayer * dxPlayer + dyPlayer * dyPlayer + dzPlayer * dzPlayer;
                if (distSq > maxDistanceSq) {
                    continue;
                }
                renderMarkerLabel(minecraft.fontRenderer, marker.getLabel(), marker.getX(), marker.getY(), marker.getZ(), Math.sqrt(distSq), cameraX, cameraY, cameraZ);
            }
        } finally {
            GL11.glDepthMask(true);
            GL11.glPopAttrib();
        }
    }

    private static boolean shouldRender(Minecraft minecraft) {
        return LostTalesConfig.showLostTalesHud
                && LostTalesConfig.showQuestHud
                && LostTalesConfig.showWorldQuestMarkers
                && minecraft != null
                && minecraft.thePlayer != null
                && minecraft.theWorld != null
                && minecraft.fontRenderer != null
                && minecraft.gameSettings != null
                && !minecraft.gameSettings.hideGUI;
    }

    /** One place's label: the tracked quest's title over how far away it is. */
    private static void renderMarkerLabel(FontRenderer fontRenderer, String questTitle, double markerX, double markerY, double markerZ, double distance, double cameraX, double cameraY, double cameraZ) {
        double renderX = markerX + 0.5D - cameraX;
        double renderY = markerY + LABEL_Y_OFFSET - cameraY;
        double renderZ = markerZ + 0.5D - cameraZ;

        int textColor = LostTalesColors.TEXT_BRIGHT;
        int shadowColor = LostTalesColors.BLACK_SHADOW;
        String label = StatCollector.translateToLocalFormatted(
                "gui.losttales.quest.label.place", questTitle);
        String distanceLabel = StatCollector.translateToLocalFormatted(
                "gui.losttales.quest.label.distance",
                String.valueOf(Math.round(distance)));

        RenderManager renderManager = RenderManager.instance;
        GL11.glPushMatrix();
        GL11.glTranslated(renderX, renderY, renderZ);
        GL11.glRotatef(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

        int labelWidth = fontRenderer.getStringWidth(label) / 2;
        int distanceWidth = fontRenderer.getStringWidth(distanceLabel) / 2;
        fontRenderer.drawString(label, -labelWidth + 1, 1, shadowColor);
        fontRenderer.drawString(label, -labelWidth, 0, textColor);
        fontRenderer.drawString(distanceLabel, -distanceWidth + 1, 11, shadowColor);
        fontRenderer.drawString(distanceLabel, -distanceWidth, 10, LostTalesColors.TEXT_MUTED);

        GL11.glPopMatrix();
    }
}
