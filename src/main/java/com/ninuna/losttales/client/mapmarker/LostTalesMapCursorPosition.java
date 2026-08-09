package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.world.map.waypoint.LostTalesMapCoordinateHelper;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.lang.reflect.Field;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;

/** One resolved map position for the pointer and every action that consumes it. */
@SideOnly(Side.CLIENT)
final class LostTalesMapCursorPosition {
    private static Field mouseXCoordField;
    private static Field mouseZCoordField;
    private static boolean reflectionReady;
    private static boolean reflectionFailed;

    private LOTRGuiMap gui;
    private int screenX;
    private int screenY;
    private float mapImageX;
    private float mapImageY;
    private int worldX;
    private int worldZ;
    private boolean valid;

    void beginFrame(LOTRGuiMap currentGui, int mouseX, int mouseY) {
        this.gui = currentGui;
        this.screenX = mouseX;
        this.screenY = mouseY;
        this.valid = false;
    }

    int[] worldPosition(LOTRGuiMap currentGui, int mouseX, int mouseY) {
        if (!resolve(currentGui, mouseX, mouseY)) {
            return null;
        }
        return new int[] { this.worldX, this.worldZ };
    }

    String[] resolveSubtitles(
            LostTalesLotrMapGui currentGui, String[] lines) {
        if (lines == null || lines.length < 2
                || !resolve(currentGui, this.screenX, this.screenY)) {
            return lines;
        }
        String biome = biomeName();
        if (biome.length() == 0) {
            return lines;
        }
        String[] resolved = lines.clone();
        resolved[0] = biome;
        resolved[1] = StatCollector.translateToLocalFormatted(
                "lotr.gui.map.coords",
                Integer.valueOf(this.worldX), Integer.valueOf(this.worldZ));
        return resolved;
    }

    void syncNativeCoordinates(LOTRGuiMap currentGui) {
        if (resolve(currentGui, this.screenX, this.screenY)) {
            writeNativeCoordinates(currentGui);
        }
    }

    private boolean resolve(
            LOTRGuiMap currentGui, int mouseX, int mouseY) {
        if (this.valid && this.gui == currentGui
                && this.screenX == mouseX && this.screenY == mouseY) {
            return true;
        }
        this.gui = currentGui;
        this.screenX = mouseX;
        this.screenY = mouseY;
        this.valid = false;
        float[] mapImage = new float[2];
        if (!LostTalesLotrMapRotation.screenToMapImage(
                currentGui, mouseX, mouseY, mapImage)) {
            return false;
        }
        this.mapImageX = mapImage[0];
        this.mapImageY = mapImage[1];
        this.worldX = LostTalesMapCoordinateHelper
                .renderedMapImageXToWorld(this.mapImageX);
        this.worldZ = LostTalesMapCoordinateHelper
                .renderedMapImageZToWorld(this.mapImageY);
        this.valid = true;
        writeNativeCoordinates(currentGui);
        return true;
    }

    private String biomeName() {
        try {
            LOTRBiome biome = LOTRGenLayerWorld.getBiomeOrOcean(
                    MathHelper.floor_double(this.mapImageX),
                    MathHelper.floor_double(this.mapImageY));
            if (biome == null) {
                return "";
            }
            Minecraft minecraft = Minecraft.getMinecraft();
            if (biome.isHiddenBiome() && minecraft != null
                    && minecraft.thePlayer != null) {
                LOTRPlayerData data = LOTRLevelData.getData(
                        minecraft.thePlayer);
                if (data == null || !data.hasAchievement(
                        biome.getBiomeAchievement())) {
                    biome = LOTRBiome.ocean;
                }
            }
            String name = biome.getBiomeDisplayName();
            return name == null ? "" : name;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void writeNativeCoordinates(LOTRGuiMap currentGui) {
        if (!this.valid || currentGui == null || !ensureReflection()) {
            return;
        }
        try {
            mouseXCoordField.setInt(currentGui, this.worldX);
            mouseZCoordField.setInt(currentGui, this.worldZ);
        } catch (Throwable throwable) {
            markReflectionFailed(throwable);
        }
    }

    private static synchronized boolean ensureReflection() {
        if (reflectionReady) {
            return true;
        }
        if (reflectionFailed) {
            return false;
        }
        try {
            mouseXCoordField = LOTRGuiMap.class.getDeclaredField(
                    "mouseXCoord");
            mouseZCoordField = LOTRGuiMap.class.getDeclaredField(
                    "mouseZCoord");
            mouseXCoordField.setAccessible(true);
            mouseZCoordField.setAccessible(true);
            reflectionReady = true;
            return true;
        } catch (Throwable throwable) {
            markReflectionFailed(throwable);
            return false;
        }
    }

    private static synchronized void markReflectionFailed(Throwable cause) {
        if (reflectionFailed) {
            return;
        }
        reflectionReady = false;
        reflectionFailed = true;
        FMLLog.warning(
                "[%s] Rotated LOTR map cursor synchronization disabled (%s)",
                LostTalesMetaData.MOD_ID,
                cause == null ? "unknown error" : cause.toString());
    }
}
