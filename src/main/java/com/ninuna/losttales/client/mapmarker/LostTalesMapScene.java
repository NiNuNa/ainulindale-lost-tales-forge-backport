package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.core.LostTalesClassTransformer;
import cpw.mods.fml.common.FMLLog;
import java.util.List;
import lotr.client.gui.LOTRGuiMainMenu;
import lotr.client.gui.LOTRGuiMap;
import lotr.client.gui.LOTRGuiRendererMap;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.Minecraft;

/** One map pipeline, with view-local camera and presentation settings. */
public final class LostTalesMapScene {
    private static final long ANIMATION_START = System.nanoTime();
    // Rendering is on Minecraft's GL thread. Nested views restore their caller.
    private static BackgroundView background;
    private static boolean backgroundFailureLogged;

    private LostTalesMapScene() {}

    public static boolean renderMap(LOTRGuiMap gui, boolean sepia,
                                    float alpha, boolean overlay) {
        return LostTalesLotrSmoothMapRenderer.render(gui, sepia, alpha, overlay);
    }

    /** Retains LOTR's flyover path, palette and caller-owned vignette. */
    public static boolean renderBackground(LOTRGuiRendererMap renderer,
            GuiScreen screen, LOTRGuiMap gui, float partialTicks,
            int left, int top, int right, int bottom, boolean sepia) {
        if (renderer == null || gui == null || screen == null
                || right <= left || bottom <= top) {
            return false;
        }
        BackgroundView previous = background;
        int[] previousBounds = null;
        int[] previousImageBounds = null;
        boolean previousFading = gui.enableZoomOutWPFading;
        try {
            previousImageBounds = LostTalesLotrSmoothMapRenderer.captureImageBounds();
            float partial = Math.max(0.0F, Math.min(1.0F, partialTicks));
            gui.setFakeMapProperties(
                    (float)(renderer.prevMapX + (renderer.mapX - renderer.prevMapX) * partial),
                    (float)(renderer.prevMapY + (renderer.mapY - renderer.prevMapY) * partial),
                    (float)Math.pow(2.0D, renderer.zoomExp), renderer.zoomExp, renderer.zoomStable);
            previousBounds = LOTRGuiMap.setFakeStaticProperties(
                    right - left, bottom - top, left, right, top, bottom);
            background = new BackgroundView(gui, presentationFor(screen), animationSeconds());
            gui.enableZoomOutWPFading = false;
            if (!renderMap(gui, sepia, 1.0F, true)) {
                // Retain native ground as a fallback, but keep ownership of
                // markers: LOTR's background path includes hidden waypoints.
                gui.renderMapAndOverlay(sepia, 1.0F, true);
            }
            renderRoadsBelowScenery(gui);
            // Current catalog, discovered appearance, never personal/hidden markers.
            LostTalesLotrMapMarkerIconOverlay.renderDecorativeBackgroundMarkers(gui, sepia);
            return true;
        } catch (Throwable failure) {
            if (!backgroundFailureLogged) {
                backgroundFailureLogged = true;
                FMLLog.warning("[losttales] Background map layer failed; skipping remaining layers: %s", failure);
            }
            return true;
        } finally {
            background = previous;
            gui.enableZoomOutWPFading = previousFading;
            if (previousBounds != null) {
                LOTRGuiMap.setFakeStaticProperties(previousBounds[0], previousBounds[1],
                        previousBounds[2], previousBounds[3], previousBounds[4], previousBounds[5]);
            }
            if (previousImageBounds != null) {
                try {
                    LostTalesLotrSmoothMapRenderer.restoreImageBounds(previousImageBounds);
                } catch (IllegalAccessException failure) {
                    if (!backgroundFailureLogged) {
                        backgroundFailureLogged = true;
                        FMLLog.warning("[losttales] Could not restore shared map image bounds: %s", failure);
                    }
                }
            }
        }
    }

    static LostTalesMapPresentation presentationFor(GuiScreen screen) {
        return screen instanceof LOTRGuiMainMenu
                ? LostTalesMapPresentation.MAIN_MENU : LostTalesMapPresentation.LOADING;
    }

    static LostTalesMapPresentation presentationOf(LOTRGuiMap gui) {
        if (background != null && background.gui == gui) {
            return background.presentation;
        }
        return gui instanceof LostTalesLotrMapGui
                ? LostTalesMapPresentation.GAMEPLAY : LostTalesMapPresentation.INSET;
    }

    static List<LostTalesMapMarkerData> backgroundMarkers(LOTRGuiMap gui) {
        Minecraft minecraft = Minecraft.getMinecraft();
        return presentationOf(gui) == LostTalesMapPresentation.MAIN_MENU
                || minecraft == null || minecraft.theWorld == null
                ? LostTalesClientMapMarkerStore.getDecorativeMarkers()
                : LostTalesClientMapMarkerStore.getAllMarkers();
    }

    static boolean isBackground(LOTRGuiMap gui) {
        return background != null && background.gui == gui;
    }

    static float backgroundDegrees(LOTRGuiMap gui) {
        return isBackground(gui) && LostTalesLotrMapRotation.isSupported()
                && Boolean.getBoolean(LostTalesClassTransformer.LOTR_MAP_SCENE_ACTIVE_PROPERTY)
                ? (float)Math.sin(background.seconds / 24.0D) * 3.0F : 0.0F;
    }

    static float backgroundLean(LOTRGuiMap gui) {
        return isBackground(gui) && LostTalesLotrMapRotation.isSupported()
                && Boolean.getBoolean(LostTalesClassTransformer.LOTR_MAP_SCENE_ACTIVE_PROPERTY)
                ? background.presentation.lean : 0.0F;
    }

    static long animationTicks() {
        return (long)(animationSeconds() * 20.0D);
    }

    private static double animationSeconds() {
        return (System.nanoTime() - ANIMATION_START) / 1.0E9D;
    }

    static void renderRoadsBelowScenery(LOTRGuiMap gui) {
        if (isBackground(gui)) {
            if (!background.roadsRendered) {
                gui.renderRoads(false);
                background.roadsRendered = true;
            }
        } else if (gui instanceof LostTalesLotrMapGui) {
            ((LostTalesLotrMapGui)gui).renderRoadsBelowClouds();
        }
    }

    private static final class BackgroundView {
        final LOTRGuiMap gui;
        final LostTalesMapPresentation presentation;
        final double seconds;
        boolean roadsRendered;

        BackgroundView(LOTRGuiMap gui, LostTalesMapPresentation presentation, double seconds) {
            this.gui = gui;
            this.presentation = presentation;
            this.seconds = seconds;
        }
    }
}
