package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.gui.LOTRGuiMap;
import net.minecraft.client.Minecraft;

/** Small, map-local sound cues for meaningful interaction changes. */
@SideOnly(Side.CLIENT)
final class LostTalesMapInteractionSounds {
    private static final String HOVER_SOUND = "random.click";
    private static final float HOVER_VOLUME = 0.12F;
    private static final float HOVER_PITCH = 1.55F;

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
                        HOVER_SOUND, HOVER_VOLUME, HOVER_PITCH);
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

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

}
