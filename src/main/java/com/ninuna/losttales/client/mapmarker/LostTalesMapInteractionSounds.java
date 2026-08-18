package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import java.util.Random;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.gui.LOTRGuiMap;
import net.minecraft.client.Minecraft;

/** Small, map-local sound cues for meaningful interaction changes. */
@SideOnly(Side.CLIENT)
final class LostTalesMapInteractionSounds {
    private static final String HOVER_SOUND =
            LostTalesMetaData.MOD_ID + ":map_marker.hover";
    private static final float HOVER_VOLUME_MIN = 0.17F;
    private static final float HOVER_VOLUME_MAX = 0.21F;
    private static final float HOVER_PITCH_MIN = 0.96F;
    private static final float HOVER_PITCH_MAX = 1.04F;
    private static final Random HOVER_VARIATION = new Random();

    private static LOTRGuiMap hoverGui;
    private static String hoverKey = "";

    private LostTalesMapInteractionSounds() {}

    /**
     * Sounds only after the delayed hover owner has actually changed. The
     * hover resolver provides the debounce; this class remembers the audible
     * transition so holding the pointer still is silent.
     */
    static void updateHover(LOTRGuiMap gui, String candidateKey) {
        String next = normalize(candidateKey);
        if (hoverGui != gui) {
            hoverGui = gui;
            hoverKey = "";
        }
        String previous = hoverKey;
        if (next.equals(previous)) {
            return;
        }
        hoverKey = next;
        if (!isAudibleHoverTransition(previous, next)) {
            return;
        }
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null && minecraft.thePlayer != null) {
                minecraft.thePlayer.playSound(
                        HOVER_SOUND,
                        hoverVolume(HOVER_VARIATION.nextFloat()),
                        hoverPitch(HOVER_VARIATION.nextFloat()));
            }
        } catch (Throwable ignored) {
            // A presentation cue must never interrupt map interaction.
        }
    }

    static void clear(LOTRGuiMap gui) {
        if (gui == null || hoverGui == gui) {
            hoverGui = null;
            hoverKey = "";
        }
    }

    static boolean isAudibleHoverTransition(String previous, String next) {
        String normalizedNext = normalize(next);
        return normalizedNext.length() > 0
                && !normalizedNext.equals(normalize(previous));
    }

    /**
     * Minecraft 1.7.10 exposes volume and pitch for entity sounds, but no
     * independent playback-speed control. The small pitch change supplies
     * the requested timing variation without depending on a newer API.
     */
    static float hoverVolume(float unitVariation) {
        return interpolateVariation(
                HOVER_VOLUME_MIN, HOVER_VOLUME_MAX, unitVariation);
    }

    static float hoverPitch(float unitVariation) {
        return interpolateVariation(
                HOVER_PITCH_MIN, HOVER_PITCH_MAX, unitVariation);
    }

    private static float interpolateVariation(
            float minimum, float maximum, float unitVariation) {
        float clamped = Math.max(0.0F, Math.min(1.0F, unitVariation));
        return minimum + (maximum - minimum) * clamped;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

}
