package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.input.LostTalesInputBinding.Type;
import com.ninuna.losttales.client.input.LostTalesInputIconRenderer;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Draws the non-interactive input and status strip over the map. */
@SideOnly(Side.CLIENT)
public final class LostTalesLotrMapControlBar {
    public static final int HEIGHT = 30;

    private static final int OUTER_PADDING = 6;
    private static final int INPUT_TEXT_GAP = 3;
    private static final int CONTROL_GAP = 10;
    private static final int BACKGROUND = 0xA0000000;
    private static final float INPUT_SCALE = 1.0F;
    private static final float GUI_MODELVIEW_Z = -2000.0F;

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

        String close = I18n.format("gui.losttales.map.control.close");
        String zoom = I18n.format("gui.losttales.map.control.zoom");
        String legend = I18n.format("gui.losttales.map.control.legend");
        int closeIconWidth = LostTalesInputIconRenderer.measureInput(
                minecraft, Type.KEYBOARD, Keyboard.KEY_ESCAPE, INPUT_SCALE);
        int zoomIconWidth = LostTalesInputIconRenderer.measureMouseWheel(
                minecraft, INPUT_SCALE);
        KeyBinding legendBinding =
                LostTalesKeyBindings.getMapLegendKeyBinding();
        int legendIconWidth = LostTalesInputIconRenderer.measureKeyBinding(
                minecraft, legendBinding, INPUT_SCALE);
        Layout layout = calculateLayout(
                gui.width,
                closeIconWidth,
                font.getStringWidth(close),
                zoomIconWidth,
                font.getStringWidth(zoom),
                legendIconWidth,
                font.getStringWidth(legend));

        beginUntranslatedRender();
        try {
            int top = Math.max(0, gui.height - HEIGHT);
            Gui.drawRect(0, top, gui.width, gui.height, BACKGROUND);
            Gui.drawRect(0, top, gui.width, top + 1,
                    LostTalesSkyrimUiStyle.BORDER_DIM);
            renderLeftControls(
                    minecraft, font, top, layout,
                    close, zoom, legendBinding, legend);
        } finally {
            endUntranslatedRender();
        }
        return true;
    }

    private static void renderLeftControls(
            Minecraft minecraft, FontRenderer font, int top,
            Layout layout, String close, String zoom,
            KeyBinding legendBinding, String legend) {
        int inputY = top
                + (HEIGHT - LostTalesInputIconRenderer.BASE_ICON_HEIGHT) / 2;
        int x = OUTER_PADDING;
        if (layout.showClose) {
            x = drawInputHint(
                    minecraft, font, Type.KEYBOARD, Keyboard.KEY_ESCAPE,
                    close, x, inputY, layout.showCloseLabel);
        }
        if (layout.showZoom) {
            x += x > OUTER_PADDING ? CONTROL_GAP : 0;
            x = drawMouseWheelHint(
                    minecraft, font, zoom,
                    x, inputY, layout.showZoomLabel);
        }
        if (layout.showLegend) {
            x += x > OUTER_PADDING ? CONTROL_GAP : 0;
            drawBindingHint(
                    minecraft, font, legendBinding, legend,
                    x, inputY, layout.showLegendLabel);
        }
    }

    private static int drawInputHint(
            Minecraft minecraft, FontRenderer font,
            Type type, int keyCode, String label,
            int x, int y, boolean showLabel) {
        int width = LostTalesInputIconRenderer.drawInput(
                minecraft, type, keyCode, x, y, INPUT_SCALE);
        int end = x + width;
        if (showLabel) {
            int textX = end + INPUT_TEXT_GAP;
            drawHintText(font, label, textX, y);
            end = textX + font.getStringWidth(label);
        }
        return end;
    }

    private static int drawMouseWheelHint(
            Minecraft minecraft, FontRenderer font, String label,
            int x, int y, boolean showLabel) {
        int width = LostTalesInputIconRenderer.drawMouseWheel(
                minecraft, x, y, INPUT_SCALE);
        int end = x + width;
        if (showLabel) {
            int textX = end + INPUT_TEXT_GAP;
            drawHintText(font, label, textX, y);
            end = textX + font.getStringWidth(label);
        }
        return end;
    }

    private static int drawBindingHint(
            Minecraft minecraft, FontRenderer font, KeyBinding binding,
            String label, int x, int y, boolean showLabel) {
        int width = LostTalesInputIconRenderer.drawKeyBinding(
                minecraft, binding, x, y, INPUT_SCALE);
        int end = x + width;
        if (showLabel) {
            int textX = end + INPUT_TEXT_GAP;
            drawHintText(font, label, textX, y);
            end = textX + font.getStringWidth(label);
        }
        return end;
    }

    private static void drawHintText(
            FontRenderer font, String label, int x, int inputY) {
        int textY = inputY
                + (LostTalesInputIconRenderer.BASE_ICON_HEIGHT
                - font.FONT_HEIGHT) / 2;
        font.drawStringWithShadow(
                label, x, textY, LostTalesSkyrimUiStyle.TEXT);
    }

    static Layout calculateLayout(
            int screenWidth,
            int closeIconWidth, int closeLabelWidth,
            int zoomIconWidth, int zoomLabelWidth,
            int legendIconWidth, int legendLabelWidth) {
        int leftLimit = Math.max(0, screenWidth / 3);
        int available = Math.max(0, leftLimit - OUTER_PADDING);
        int closeFull = hintWidth(
                closeIconWidth, closeLabelWidth, true);
        int zoomFull = hintWidth(
                zoomIconWidth, zoomLabelWidth, true);
        int legendFull = hintWidth(
                legendIconWidth, legendLabelWidth, true);

        if (closeFull + CONTROL_GAP + zoomFull
                + CONTROL_GAP + legendFull <= available) {
            return new Layout(true, true, true, true,
                    true, true,
                    OUTER_PADDING + closeFull + CONTROL_GAP + zoomFull
                            + CONTROL_GAP + legendFull);
        }
        if (closeFull + CONTROL_GAP + zoomIconWidth
                + CONTROL_GAP + legendFull <= available) {
            return new Layout(true, true, true, false,
                    true, true,
                    OUTER_PADDING + closeFull + CONTROL_GAP + zoomIconWidth
                            + CONTROL_GAP + legendFull);
        }
        if (closeIconWidth + CONTROL_GAP + zoomIconWidth
                + CONTROL_GAP + legendIconWidth <= available) {
            return new Layout(true, false, true, false,
                    true, false,
                    OUTER_PADDING + closeIconWidth
                            + CONTROL_GAP + zoomIconWidth
                            + CONTROL_GAP + legendIconWidth);
        }
        if (closeIconWidth + CONTROL_GAP + legendIconWidth <= available) {
            return new Layout(true, false, false, false,
                    true, false,
                    OUTER_PADDING + closeIconWidth
                            + CONTROL_GAP + legendIconWidth);
        }
        if (legendFull <= available) {
            return new Layout(false, false, false, false,
                    true, true, OUTER_PADDING + legendFull);
        }
        if (legendIconWidth <= available) {
            return new Layout(false, false, false, false,
                    true, false, OUTER_PADDING + legendIconWidth);
        }
        return new Layout(false, false, false, false,
                false, false, OUTER_PADDING);
    }

    private static int hintWidth(
            int iconWidth, int labelWidth, boolean showLabel) {
        return Math.max(0, iconWidth)
                + (showLabel ? INPUT_TEXT_GAP + Math.max(0, labelWidth) : 0);
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

    static final class Layout {
        final boolean showClose;
        final boolean showCloseLabel;
        final boolean showZoom;
        final boolean showZoomLabel;
        final boolean showLegend;
        final boolean showLegendLabel;
        final int leftEnd;

        private Layout(
                boolean showClose, boolean showCloseLabel,
                boolean showZoom, boolean showZoomLabel,
                boolean showLegend, boolean showLegendLabel,
                int leftEnd) {
            this.showClose = showClose;
            this.showCloseLabel = showCloseLabel;
            this.showZoom = showZoom;
            this.showZoomLabel = showZoomLabel;
            this.showLegend = showLegend;
            this.showLegendLabel = showLegendLabel;
            this.leftEnd = leftEnd;
        }
    }
}
