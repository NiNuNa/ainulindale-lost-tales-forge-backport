package com.ninuna.losttales.client.mapmarker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.LOTRTextures;
import lotr.client.gui.LOTRGuiMap;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/**
 * The map's compass rose, moved out of the corner and made to mean something.
 *
 * <p>LOTR draws it at the bottom left, where the Lost Tales control strip now
 * lives, and draws it square whatever the map is doing. Here it sits above the
 * strip, centred, and turns with the map — so once the map can be turned, the
 * rose is the thing that says by how much and which way north now lies.</p>
 *
 * <p>The sprite is LOTR's own, drawn through LOTR's own routine; only where it
 * goes and which way it faces are decided here.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesLotrMapCompass {
    /** Side of the rose LOTR's routine draws, before its scale. */
    private static final double SPRITE_SIZE = 32.0D;
    /** Gap between the rose and whatever sits below it. */
    private static final int BOTTOM_MARGIN = 6;

    private LostTalesLotrMapCompass() {}

    /**
     * Called in place of {@code LOTRTextures.drawMapCompassBottomLeft} from
     * LOTR's own map draw, with the arguments it was about to use.
     *
     * <p>Any map that is not the Lost Tales fullscreen one — the animated menu
     * background, above all — keeps LOTR's placement exactly.</p>
     */
    public static void drawMapCompass(
            double x, double y, double z, double scale) {
        LostTalesLotrMapGui gui = currentMapGui();
        if (gui == null) {
            LOTRTextures.drawMapCompassBottomLeft(x, y, z, scale);
            return;
        }
        try {
            double size = SPRITE_SIZE * scale;
            double centerX = gui.width / 2.0D;
            double bottom = gui.height - reservedBottom(gui)
                    - BOTTOM_MARGIN;
            double centerY = bottom - size / 2.0D;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(centerX, centerY, 0.0D);
                GL11.glRotatef(gui.getMapRotationDegrees(),
                        0.0F, 0.0F, 1.0F);
                GL11.glTranslated(-centerX, -centerY, 0.0D);
                LOTRTextures.drawMapCompassBottomLeft(
                        centerX - size / 2.0D, bottom, z, scale);
            } finally {
                GL11.glPopMatrix();
            }
        } catch (Throwable ignored) {
            // A compass rose is decoration; never take the map down for it.
        }
    }

    /** Height at the bottom of the screen the map's own panels have taken. */
    private static int reservedBottom(LostTalesLotrMapGui gui) {
        if (!LostTalesLotrMapLayout.isControlBarVisible(gui)) {
            return 0;
        }
        return LostTalesLotrMapControlBar.HEIGHT
                + LostTalesLotrMapLegend.getReservedHeight(gui);
    }

    private static LostTalesLotrMapGui currentMapGui() {
        Minecraft minecraft = Minecraft.getMinecraft();
        Object screen = minecraft == null ? null : minecraft.currentScreen;
        if (!(screen instanceof LostTalesLotrMapGui)) {
            return null;
        }
        LOTRGuiMap gui = (LOTRGuiMap)screen;
        return LostTalesLotrMapLayout.isFullscreenLayoutActive(gui)
                ? (LostTalesLotrMapGui)gui : null;
    }
}
