package com.ninuna.losttales.client.map;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.LOTRTextures;
import net.minecraft.client.gui.Gui;

/** Draws LOTR's ocean color beneath map previews clipped by the map edge. */
@SideOnly(Side.CLIENT)
public final class LostTalesLotrMapEdgeRenderer {

    private LostTalesLotrMapEdgeRenderer() {
    }

    public static void fillClippedMapBackground(
            boolean sepia,
            int mapXMin, int mapXMax, int mapYMin, int mapYMax,
            int drawnXMin, int drawnXMax, int drawnYMin, int drawnYMax) {
        if (mapXMin == drawnXMin && mapXMax == drawnXMax
                && mapYMin == drawnYMin && mapYMax == drawnYMax) {
            return;
        }

        int color = LOTRTextures.getMapOceanColor(sepia);
        int contentXMin = Math.min(
                mapXMax, Math.max(mapXMin, drawnXMin));
        int contentXMax = Math.max(
                mapXMin, Math.min(mapXMax, drawnXMax));
        if (contentXMin > mapXMin) {
            Gui.drawRect(
                    mapXMin, mapYMin, contentXMin, mapYMax, color);
        }
        if (contentXMax < mapXMax) {
            Gui.drawRect(
                    contentXMax, mapYMin, mapXMax, mapYMax, color);
        }
        if (contentXMin >= contentXMax) {
            return;
        }
        if (drawnYMin > mapYMin) {
            Gui.drawRect(
                    contentXMin, mapYMin, contentXMax,
                    Math.min(drawnYMin, mapYMax), color);
        }
        if (drawnYMax < mapYMax) {
            Gui.drawRect(
                    contentXMin, Math.max(drawnYMax, mapYMin),
                    contentXMax, mapYMax, color);
        }
    }
}
