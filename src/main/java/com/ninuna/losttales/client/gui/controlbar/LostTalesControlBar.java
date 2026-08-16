package com.ninuna.losttales.client.gui.controlbar;

import com.ninuna.losttales.client.gui.animation.LostTalesControlBarAnimation;
import com.ninuna.losttales.client.input.LostTalesInputBinding.Type;
import com.ninuna.losttales.client.input.LostTalesInputIconRenderer;
import com.ninuna.losttales.gui.style.LostTalesColors;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.opengl.GL11;

/**
 * Reusable animated control strip for Lost Tales screens.
 *
 * <p>Screens only describe their input hints. This class owns their visual
 * language, responsive fitting, input-icon animation, ivory labels, and the
 * independently delayed entrance used by every bottom control bar.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesControlBar {
    public static final int HEIGHT = 30;
    public static final int OUTER_PADDING = 6;
    public static final int INPUT_TEXT_GAP = 3;
    public static final int CONTROL_GAP = 10;

    private static final int BACKGROUND = 0xA0000000;
    private static final int STATUS_GAP = 20;
    private static final int SEPARATOR_INSET = 7;
    private static final float INPUT_SCALE = 1.0F;
    private static final float GUI_MODELVIEW_Z = -2000.0F;

    private LostTalesControlBar() {}

    public static boolean render(
            Object screen, Minecraft minecraft, FontRenderer font,
            int screenWidth, int screenHeight, List<Hint> hints,
            int leftGroupSize, int centerReserved,
            List<String> statusCandidates, boolean fixedCoordinates) {
        if (screen == null || minecraft == null || font == null
                || screenWidth <= 0 || screenHeight <= 0) {
            return false;
        }
        List<Hint> safeHints = hints == null
                ? Collections.<Hint>emptyList() : hints;
        List<String> safeStatuses = statusCandidates == null
                ? Collections.<String>emptyList() : statusCandidates;
        int[] statusWidths = new int[safeStatuses.size()];
        for (int index = 0; index < safeStatuses.size(); index++) {
            String status = safeStatuses.get(index);
            statusWidths[index] = status == null
                    ? 0 : font.getStringWidth(status);
        }
        Layout layout = calculateLayout(screenWidth, safeHints,
                leftGroupSize, centerReserved, statusWidths);
        float offset = LostTalesControlBarAnimation.offsetY(screen);
        beginRender(offset, fixedCoordinates);
        try {
            int top = Math.max(0, screenHeight - HEIGHT);
            // The content retains its full fly-in/follow-through. Extending
            // the fill in the opposite direction keeps the bottom edge
            // covered even while the translated strip overshoots.
            int fillTop = top;
            int fillBottom = screenHeight
                    + (int)Math.ceil(Math.max(0.0F, -offset)) + 1;
            Gui.drawRect(0, fillTop, screenWidth, fillBottom, BACKGROUND);
            Gui.drawRect(0, top, screenWidth, top + 1,
                    LostTalesColors.BORDER_DIM);
            drawHints(minecraft, font, screenWidth, top, safeHints,
                    Math.min(Math.max(0, leftGroupSize), safeHints.size()),
                    layout);
            drawStatus(font, screenWidth, top, safeStatuses, layout);
        } finally {
            endRender();
        }
        return true;
    }

    public static Layout calculateLayout(
            int screenWidth, List<Hint> hints, int leftGroupSize,
            int centerReserved, int[] statusWidths) {
        if (hints == null || hints.isEmpty()) {
            return new Layout(0, 0, false, -1, 0);
        }
        int split = Math.min(Math.max(0, leftGroupSize), hints.size());
        int leftRoom;
        int rightRoom;
        if (split == hints.size()) {
            leftRoom = Math.max(0, screenWidth - OUTER_PADDING * 2
                    - Math.max(0, centerReserved));
            rightRoom = 0;
        } else {
            int band = Math.max(0,
                    (screenWidth - Math.max(0, centerReserved)) / 2
                            - OUTER_PADDING);
            leftRoom = band;
            rightRoom = band;
        }
        Layout labelled = fitBothEnds(
                hints, split, true, leftRoom, rightRoom, statusWidths);
        return labelled.visibleHints() > 0
                ? labelled : fitBothEnds(
                        hints, split, false, leftRoom, rightRoom,
                        statusWidths);
    }

    private static Layout fitBothEnds(
            List<Hint> hints, int split, boolean withLabels,
            int leftRoom, int rightRoom, int[] statusWidths) {
        int left = fit(hints, 0, split, withLabels, leftRoom);
        int right = fit(hints, split, hints.size() - split,
                withLabels, rightRoom);
        int spare = rightRoom
                - groupWidth(hints, split, right, withLabels)
                - (right > 0 ? STATUS_GAP : 0);
        int statusIndex = -1;
        int statusWidth = 0;
        if (statusWidths != null) {
            for (int candidate = 0; candidate < statusWidths.length;
                 candidate++) {
                if (statusWidths[candidate] > 0
                        && statusWidths[candidate] <= spare) {
                    statusIndex = candidate;
                    statusWidth = statusWidths[candidate];
                    break;
                }
            }
        }
        return new Layout(left, right, withLabels && left + right > 0,
                statusIndex, statusWidth);
    }

    private static int fit(List<Hint> hints, int from, int count,
                           boolean labels, int room) {
        for (int taken = count; taken > 0; taken--) {
            if (groupWidth(hints, from, taken, labels) <= room) {
                return taken;
            }
        }
        return 0;
    }

    private static int groupWidth(List<Hint> hints, int from, int count,
                                  boolean labels) {
        int width = 0;
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                width += CONTROL_GAP;
            }
            width += hints.get(from + index).width(labels);
        }
        return width;
    }

    private static void drawHints(
            Minecraft minecraft, FontRenderer font, int screenWidth,
            int top, List<Hint> hints, int split, Layout layout) {
        int inputY = top
                + (HEIGHT - LostTalesInputIconRenderer.BASE_ICON_HEIGHT) / 2;
        int x = OUTER_PADDING;
        for (int index = 0; index < layout.leftHints; index++) {
            if (index > 0) {
                x += CONTROL_GAP;
            }
            x = hints.get(index).draw(
                    minecraft, font, x, inputY, layout.showLabels);
        }
        int width = groupWidth(
                hints, split, layout.rightHints, layout.showLabels);
        x = Math.max(OUTER_PADDING, screenWidth - OUTER_PADDING
                - layout.statusWidth() - width);
        for (int index = 0; index < layout.rightHints; index++) {
            if (index > 0) {
                x += CONTROL_GAP;
            }
            x = hints.get(split + index).draw(
                    minecraft, font, x, inputY, layout.showLabels);
        }
    }

    private static void drawStatus(
            FontRenderer font, int screenWidth, int top,
            List<String> statuses, Layout layout) {
        if (!layout.showStatus || layout.statusIndex >= statuses.size()) {
            return;
        }
        String status = statuses.get(layout.statusIndex);
        int statusX = screenWidth - OUTER_PADDING
                - font.getStringWidth(status);
        font.drawStringWithShadow(status, statusX,
                top + (HEIGHT - font.FONT_HEIGHT) / 2,
                LostTalesColors.HUD_LABEL);
        int ruleX = statusX - STATUS_GAP / 2;
        Gui.drawRect(ruleX, top + SEPARATOR_INSET,
                ruleX + 1, top + HEIGHT - SEPARATOR_INSET,
                LostTalesColors.BORDER_DIM);
    }

    private static void beginRender(float offset, boolean fixedCoordinates) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        if (fixedCoordinates) {
            GL11.glLoadIdentity();
            GL11.glTranslatef(0.0F, offset, GUI_MODELVIEW_Z);
        } else {
            GL11.glTranslatef(0.0F, offset, 0.0F);
        }
    }

    private static void endRender() {
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /** One input icon and its action label. */
    public static final class Hint {
        private final List<Part> parts;
        private final String label;
        private final int iconWidth;
        private final int labelWidth;

        private Hint(List<Part> parts, String label,
                     int iconWidth, int labelWidth) {
            this.parts = parts == null
                    ? Collections.<Part>emptyList() : parts;
            this.label = label == null ? "" : label;
            this.iconWidth = Math.max(0, iconWidth);
            this.labelWidth = Math.max(0, labelWidth);
        }

        public static Hint key(Minecraft minecraft, FontRenderer font,
                               int keyCode, String label) {
            return input(minecraft, font, Type.KEYBOARD,
                    keyCode, label);
        }

        public static Hint mouseButton(
                Minecraft minecraft, FontRenderer font,
                int buttonIndex, String label) {
            return input(minecraft, font, Type.MOUSE_BUTTON,
                    -100 + Math.max(0, buttonIndex), label);
        }

        public static Hint wheel(Minecraft minecraft, FontRenderer font,
                                 String label) {
            return input(minecraft, font, Type.MOUSE_WHEEL, 0, label);
        }

        public static Hint binding(
                Minecraft minecraft, FontRenderer font,
                KeyBinding binding, String label) {
            ArrayList<Part> parts = new ArrayList<Part>();
            parts.add(Part.binding(minecraft, binding, 0));
            return create(font, parts, label);
        }

        public static Hint keyCluster(
                Minecraft minecraft, FontRenderer font, int[] keyCodes,
                String prefix, String label) {
            ArrayList<Part> parts = new ArrayList<Part>();
            if (prefix != null && prefix.length() > 0) {
                parts.add(Part.text(font, prefix, 0, true));
            }
            if (keyCodes != null) {
                for (int index = 0; index < keyCodes.length; index++) {
                    parts.add(Part.input(minecraft, Type.KEYBOARD,
                            keyCodes[index], index == 0
                                    ? INPUT_TEXT_GAP : 1, false));
                }
            }
            return create(font, parts, label);
        }

        public static Hint alternative(
                Minecraft minecraft, FontRenderer font,
                KeyBinding binding, int keyCode, String label) {
            ArrayList<Part> parts = new ArrayList<Part>();
            parts.add(Part.binding(minecraft, binding, 0));
            parts.add(Part.text(font, "/", 2, false));
            parts.add(Part.input(minecraft, Type.KEYBOARD,
                    keyCode, 2, false));
            return create(font, parts, label);
        }

        private static Hint input(
                Minecraft minecraft, FontRenderer font, Type type,
                int keyCode, String label) {
            ArrayList<Part> parts = new ArrayList<Part>();
            parts.add(Part.input(minecraft, type, keyCode, 0, false));
            return create(font, parts, label);
        }

        private static Hint create(
                FontRenderer font, List<Part> parts, String label) {
            int width = visiblePartsWidth(parts, true);
            return new Hint(parts, label, width,
                    font == null || label == null
                            ? 0 : font.getStringWidth(label));
        }

        int width(boolean withLabel) {
            int partsWidth = this.parts.isEmpty()
                    ? this.iconWidth
                    : visiblePartsWidth(this.parts, withLabel);
            return partsWidth + (withLabel && this.labelWidth > 0
                    ? (partsWidth > 0 ? INPUT_TEXT_GAP : 0)
                            + this.labelWidth : 0);
        }

        int draw(Minecraft minecraft, FontRenderer font, int x, int inputY,
                 boolean withLabel) {
            int end = x;
            boolean drewPart = false;
            for (Part part : this.parts) {
                if (part.labelOnly && !withLabel) {
                    continue;
                }
                if (drewPart) {
                    end += part.gapBefore;
                }
                end = part.draw(minecraft, font, end, inputY);
                drewPart = true;
            }
            if (this.parts.isEmpty()) {
                end += this.iconWidth;
            }
            if (withLabel && this.label.length() > 0) {
                int textX = end + (end > x ? INPUT_TEXT_GAP : 0);
                int textY = inputY
                        + (LostTalesInputIconRenderer.BASE_ICON_HEIGHT
                                - font.FONT_HEIGHT) / 2;
                font.drawStringWithShadow(this.label, textX, textY,
                        LostTalesColors.HUD_LABEL);
                end = textX + font.getStringWidth(this.label);
            }
            return end;
        }

        private static int visiblePartsWidth(
                List<Part> parts, boolean withLabels) {
            int width = 0;
            boolean visible = false;
            if (parts != null) {
                for (Part part : parts) {
                    if (part.labelOnly && !withLabels) {
                        continue;
                    }
                    if (visible) {
                        width += part.gapBefore;
                    }
                    width += part.width;
                    visible = true;
                }
            }
            return width;
        }

        private static final class Part {
            private final Type type;
            private final int keyCode;
            private final KeyBinding binding;
            private final String text;
            private final int width;
            private final int gapBefore;
            private final boolean labelOnly;

            private Part(Type type, int keyCode, KeyBinding binding,
                         String text, int width, int gapBefore,
                         boolean labelOnly) {
                this.type = type;
                this.keyCode = keyCode;
                this.binding = binding;
                this.text = text;
                this.width = Math.max(0, width);
                this.gapBefore = Math.max(0, gapBefore);
                this.labelOnly = labelOnly;
            }

            private static Part input(
                    Minecraft minecraft, Type type, int keyCode,
                    int gap, boolean labelOnly) {
                return new Part(type, keyCode, null, null,
                        LostTalesInputIconRenderer.measureInput(
                                minecraft, type, keyCode, INPUT_SCALE),
                        gap, labelOnly);
            }

            private static Part binding(
                    Minecraft minecraft, KeyBinding binding, int gap) {
                return new Part(null, 0, binding, null,
                        LostTalesInputIconRenderer.measureKeyBinding(
                                minecraft, binding, INPUT_SCALE),
                        gap, false);
            }

            private static Part text(
                    FontRenderer font, String text, int gap,
                    boolean labelOnly) {
                String safe = text == null ? "" : text;
                return new Part(null, 0, null, safe,
                        font == null ? 0 : font.getStringWidth(safe),
                        gap, labelOnly);
            }

            private int draw(
                    Minecraft minecraft, FontRenderer font,
                    int x, int inputY) {
                if (this.text != null) {
                    int textY = inputY
                            + (LostTalesInputIconRenderer.BASE_ICON_HEIGHT
                                    - font.FONT_HEIGHT) / 2;
                    font.drawStringWithShadow(this.text, x, textY,
                            LostTalesColors.HUD_LABEL);
                    return x + this.width;
                }
                int drawn = this.binding == null
                        ? LostTalesInputIconRenderer.drawInput(
                                minecraft, this.type, this.keyCode,
                                x, inputY, INPUT_SCALE)
                        : LostTalesInputIconRenderer.drawKeyBinding(
                                minecraft, this.binding,
                                x, inputY, INPUT_SCALE);
                return x + drawn;
            }
        }
    }

    /** Responsive layout result, public so narrow-screen behavior is testable. */
    public static final class Layout {
        public final int leftHints;
        public final int rightHints;
        public final boolean showLabels;
        public final boolean showStatus;
        public final int statusIndex;
        private final int statusTextWidth;

        private Layout(int leftHints, int rightHints, boolean showLabels,
                       int statusIndex, int statusTextWidth) {
            this.leftHints = leftHints;
            this.rightHints = rightHints;
            this.showLabels = showLabels;
            this.statusIndex = statusIndex;
            this.statusTextWidth = Math.max(0, statusTextWidth);
            this.showStatus = statusIndex >= 0 && this.statusTextWidth > 0;
        }

        public int statusWidth() {
            return this.statusTextWidth == 0
                    ? 0 : this.statusTextWidth + STATUS_GAP;
        }

        public int visibleHints() {
            return this.leftHints + this.rightHints;
        }
    }

    /** Test seam: creates deterministic hints without a running client. */
    public static List<Hint> measuredHints(int... widths) {
        ArrayList<Hint> result = new ArrayList<Hint>();
        for (int width : widths) {
            result.add(new Hint(Collections.<Hint.Part>emptyList(), "x",
                    width, width));
        }
        return Collections.unmodifiableList(result);
    }
}
