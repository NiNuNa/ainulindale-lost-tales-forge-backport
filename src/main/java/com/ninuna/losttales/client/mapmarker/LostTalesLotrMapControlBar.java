package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.input.LostTalesInputBinding.Type;
import com.ninuna.losttales.client.input.LostTalesInputIconRenderer;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Draws the non-interactive input and status strip over the map.
 *
 * <p>Hints are described once and then fitted to whatever width is going. A
 * narrow screen first drops the labels, then the least important hints, so
 * the strip degrades instead of overflowing.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesLotrMapControlBar {
    public static final int HEIGHT = 30;

    private static final int OUTER_PADDING = 6;
    private static final int INPUT_TEXT_GAP = 3;
    private static final int CONTROL_GAP = 10;
    private static final int BACKGROUND = 0xA0000000;
    private static final float INPUT_SCALE = 1.0F;
    private static final float GUI_MODELVIEW_Z = -2000.0F;
    /** Share of the screen the strip may occupy before it starts dropping. */
    private static final int WIDTH_DIVISOR = 2;
    /** LOTR's operator teleport key, which its own subtitle used to name. */
    private static final int TELEPORT_KEY = Keyboard.KEY_M;

    private LostTalesLotrMapControlBar() {
    }

    static boolean render(LostTalesLotrMapGui gui) {
        if (!LostTalesLotrMapLayout.isControlBarVisible(gui)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft == null ? null : minecraft.fontRenderer;
        if (minecraft == null || font == null || gui.width <= 0
                || gui.height <= 0) {
            return false;
        }

        List<Hint> hints = collectHints(minecraft, font, gui);
        Layout layout = calculateLayout(gui.width, hints);

        beginUntranslatedRender();
        try {
            int top = Math.max(0, gui.height - HEIGHT);
            Gui.drawRect(0, top, gui.width, gui.height, BACKGROUND);
            Gui.drawRect(0, top, gui.width, top + 1,
                    LostTalesSkyrimUiStyle.BORDER_DIM);
            drawHints(minecraft, font, top, hints, layout);
        } finally {
            endUntranslatedRender();
        }
        return true;
    }

    /**
     * The strip's hints, in the order they are drawn. Later entries are the
     * first to go when the space runs out, so the ones a player needs most
     * are listed first.
     */
    private static List<Hint> collectHints(
            Minecraft minecraft, FontRenderer font,
            LostTalesLotrMapGui gui) {
        ArrayList<Hint> hints = new ArrayList<Hint>();
        hints.add(Hint.key(minecraft, font, Keyboard.KEY_ESCAPE,
                I18n.format("gui.losttales.map.control.close")));
        hints.add(Hint.wheel(minecraft, font,
                I18n.format("gui.losttales.map.control.zoom")));
        hints.add(Hint.binding(minecraft, font,
                LostTalesKeyBindings.getMapLegendKeyBinding(),
                I18n.format("gui.losttales.map.control.legend")));
        hints.add(Hint.key(minecraft, font,
                LostTalesLotrMapGui.FIND_LOCATION_KEY,
                I18n.format("gui.losttales.map.control.find")));
        hints.add(Hint.key(minecraft, font,
                LostTalesLotrMapGui.CURRENT_LOCATION_KEY,
                I18n.format("gui.losttales.map.control.location")));
        hints.add(Hint.key(minecraft, font,
                LostTalesLotrMapGui.CREATE_WAYPOINT_KEY,
                I18n.format("gui.losttales.map.control.waypoint")));
        if (gui != null && gui.isPlayerOp) {
            // LOTR's own sentence for this is filtered out of the map, so
            // the action is named once, in the strip, like every other.
            hints.add(Hint.key(minecraft, font, TELEPORT_KEY,
                    I18n.format("gui.losttales.map.control.teleport")));
        }
        return hints;
    }

    private static void drawHints(
            Minecraft minecraft, FontRenderer font, int top,
            List<Hint> hints, Layout layout) {
        int inputY = top
                + (HEIGHT - LostTalesInputIconRenderer.BASE_ICON_HEIGHT) / 2;
        int x = OUTER_PADDING;
        for (int index = 0; index < layout.visibleHints; index++) {
            if (x > OUTER_PADDING) {
                x += CONTROL_GAP;
            }
            x = hints.get(index).draw(
                    minecraft, font, x, inputY, layout.showLabels);
        }
    }

    /**
     * Fits as many hints as the left half of the strip allows.
     *
     * <p>A named key is worth more than an extra unnamed one: a row of bare
     * key icons tells the player which keys do something but not what, which
     * is close to telling them nothing. So hints are dropped from the end —
     * they are collected least-needed-last — for as long as that keeps the
     * rest named, and only a strip too narrow for even one named hint falls
     * back to icons alone. Labels are still all-or-nothing, so the strip
     * never shows some hints named and others not.</p>
     */
    static Layout calculateLayout(int screenWidth, List<Hint> hints) {
        int available = Math.max(0,
                Math.max(0, screenWidth / WIDTH_DIVISOR) - OUTER_PADDING);
        if (hints == null || hints.isEmpty()) {
            return new Layout(0, false, OUTER_PADDING);
        }
        for (int count = hints.size(); count > 0; count--) {
            int width = totalWidth(hints, count, true);
            if (width <= available) {
                return new Layout(count, true, OUTER_PADDING + width);
            }
        }
        for (int count = hints.size(); count > 0; count--) {
            int width = totalWidth(hints, count, false);
            if (width <= available) {
                return new Layout(count, false, OUTER_PADDING + width);
            }
        }
        return new Layout(0, false, OUTER_PADDING);
    }

    private static int totalWidth(
            List<Hint> hints, int count, boolean withLabels) {
        int width = 0;
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                width += CONTROL_GAP;
            }
            width += hints.get(index).width(withLabels);
        }
        return width;
    }

    private static void beginUntranslatedRender() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, GUI_MODELVIEW_Z);
    }

    private static void endUntranslatedRender() {
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /** One input icon and the action it performs. */
    static final class Hint {
        private final Type type;
        private final int keyCode;
        private final KeyBinding binding;
        private final boolean wheel;
        private final String label;
        private final int iconWidth;
        private final int labelWidth;

        private Hint(Type type, int keyCode, KeyBinding binding,
                     boolean wheel, String label,
                     int iconWidth, int labelWidth) {
            this.type = type;
            this.keyCode = keyCode;
            this.binding = binding;
            this.wheel = wheel;
            this.label = label == null ? "" : label;
            this.iconWidth = Math.max(0, iconWidth);
            this.labelWidth = Math.max(0, labelWidth);
        }

        static Hint key(Minecraft minecraft, FontRenderer font,
                        int keyCode, String label) {
            return new Hint(Type.KEYBOARD, keyCode, null, false, label,
                    LostTalesInputIconRenderer.measureInput(
                            minecraft, Type.KEYBOARD, keyCode,
                            INPUT_SCALE),
                    font.getStringWidth(label));
        }

        static Hint binding(Minecraft minecraft, FontRenderer font,
                            KeyBinding binding, String label) {
            return new Hint(Type.KEYBOARD, 0, binding, false, label,
                    LostTalesInputIconRenderer.measureKeyBinding(
                            minecraft, binding, INPUT_SCALE),
                    font.getStringWidth(label));
        }

        static Hint wheel(Minecraft minecraft, FontRenderer font,
                          String label) {
            return new Hint(null, 0, null, true, label,
                    LostTalesInputIconRenderer.measureMouseWheel(
                            minecraft, INPUT_SCALE),
                    font.getStringWidth(label));
        }

        int width(boolean withLabel) {
            return this.iconWidth
                    + (withLabel && this.labelWidth > 0
                            ? INPUT_TEXT_GAP + this.labelWidth : 0);
        }

        /** @return the x coordinate just past this hint */
        int draw(Minecraft minecraft, FontRenderer font,
                 int x, int inputY, boolean withLabel) {
            int drawn;
            if (this.wheel) {
                drawn = LostTalesInputIconRenderer.drawMouseWheel(
                        minecraft, x, inputY, INPUT_SCALE);
            } else if (this.binding != null) {
                drawn = LostTalesInputIconRenderer.drawKeyBinding(
                        minecraft, this.binding, x, inputY, INPUT_SCALE);
            } else {
                drawn = LostTalesInputIconRenderer.drawInput(
                        minecraft, this.type, this.keyCode,
                        x, inputY, INPUT_SCALE);
            }
            int end = x + drawn;
            if (withLabel && this.label.length() > 0) {
                int textX = end + INPUT_TEXT_GAP;
                int textY = inputY
                        + (LostTalesInputIconRenderer.BASE_ICON_HEIGHT
                                - font.FONT_HEIGHT) / 2;
                font.drawStringWithShadow(this.label, textX, textY,
                        LostTalesSkyrimUiStyle.TEXT);
                end = textX + font.getStringWidth(this.label);
            }
            return end;
        }
    }

    static final class Layout {
        final int visibleHints;
        final boolean showLabels;
        final int leftEnd;

        private Layout(int visibleHints, boolean showLabels, int leftEnd) {
            this.visibleHints = visibleHints;
            this.showLabels = showLabels;
            this.leftEnd = leftEnd;
        }
    }

    /** Test seam: hints measured without a running client. */
    static List<Hint> measuredHints(int... widths) {
        ArrayList<Hint> hints = new ArrayList<Hint>();
        for (int width : widths) {
            hints.add(new Hint(Type.KEYBOARD, 0, null, false, "x",
                    width, width));
        }
        return Collections.unmodifiableList(hints);
    }
}
