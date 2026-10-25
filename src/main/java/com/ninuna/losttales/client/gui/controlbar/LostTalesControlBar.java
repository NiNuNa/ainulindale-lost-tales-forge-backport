package com.ninuna.losttales.client.gui.controlbar;

import com.ninuna.losttales.client.gui.animation.LostTalesControlBarAnimation;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiOrigin;
import com.ninuna.losttales.client.input.LostTalesInputBinding.Type;
import com.ninuna.losttales.client.input.LostTalesInputIconRenderer;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiRules;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.opengl.GL11;

/**
 * The key hints at the foot of a screen of the mod's own, the map's where
 * it stands outside a window, on a window's bar (LOOK-03, MAP-18): the
 * bar's height and its surface, the windows' rule as its first row, the
 * keys on the row its framed buttons stand on, the words in the windows'
 * ink and the date in the bar's words' sand. It spans the screen as a
 * full-screen window's bar does, its frame past the screen's edges.
 *
 * <p>Screens only describe their input hints; this fits them, the left
 * group, the right group and the date, and draws them. The bar is drawn
 * from the screen's origin and arrives with its screen
 * ({@link LostTalesControlBarAnimation}).</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesControlBar {
    public static final int HEIGHT = WindowPlacement.BAR_STRIP_HEIGHT;
    /** From the screen's side to the first key, as a button stands from a window's frame. */
    public static final int OUTER_PADDING = WindowStyle.EDGE_GAP;
    public static final int INPUT_TEXT_GAP = 3;
    public static final int CONTROL_GAP = 10;

    private static final int STATUS_GAP = 20;
    private static final float INPUT_SCALE = 1.0F;

    private LostTalesControlBar() {}

    public static boolean render(
            Object screen, Minecraft minecraft, FontRenderer font,
            int screenWidth, int screenHeight, List<Hint> hints,
            int leftGroupSize, int centerReserved,
            List<String> statusCandidates) {
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
        float offset = LostTalesControlBarAnimation.shiftY(screen);
        beginRender(screen);
        try {
            int top = Math.max(0, screenHeight - HEIGHT);
            // The strip keeps its full fly-in and follow-through. Extending
            // the fill the other way keeps the screen's bottom edge covered
            // while the moved strip stands above its place.
            int fillTop = top;
            int fillBottom = screenHeight
                    + (int)Math.ceil(Math.max(0.0F, -offset)) + 1;
            // The bar's surface under its first row, which is the
            // windows' rule, as a window's bottom rule is its bar's.
            LostTalesUiInk.fillRect(0, fillTop + 1, screenWidth, fillBottom,
                    WindowStyle.stripArgb(WindowStyle.opacity(minecraft)));
            LostTalesUiRules.drawRule(0, screenWidth, top, top + 1, 255);
            LostTalesUiInk.beginContent();
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
        // The keys stand on the row a bar's framed buttons stand on.
        int inputY = WindowBar.buttonTop(top) + LostTalesUiInk.centredStart(
                LostTalesUiFramedButton.HEIGHT,
                LostTalesInputIconRenderer.BASE_ICON_HEIGHT);
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
        // The date as a bar's quiet words stand, after a bar's divider.
        LostTalesUiInk.drawText(font, status, statusX,
                WindowBar.textTop(top),
                LostTalesColors.rgb(LostTalesColors.SAND), 255);
        int ruleX = statusX - STATUS_GAP / 2;
        WindowStyle.drawDivider(ruleX, WindowBar.glyphTop(top),
                WindowStyle.LINE_HEIGHT, WindowStyle.DIVIDER_ALPHA);
    }

    /** The strip's own state and matrix: from the screen's origin, where the strip stands now. */
    private static void beginRender(Object screen) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        LostTalesGuiOrigin.load();
        LostTalesControlBarAnimation.place(screen);
    }

    private static void endRender() {
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /** One input icon and its action label. */
    public static final class Hint {
        private final List<Part> parts;
        private final String label;
        private final int labelWidth;

        private Hint(List<Part> parts, String label, int labelWidth) {
            this.parts = parts == null
                    ? Collections.<Part>emptyList() : parts;
            this.label = label == null ? "" : label;
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
            return new Hint(parts, label,
                    font == null || label == null
                            ? 0 : font.getStringWidth(label));
        }

        int width(boolean withLabel) {
            int partsWidth = visiblePartsWidth(this.parts, withLabel);
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
            if (withLabel && this.label.length() > 0) {
                int textX = end + (end > x ? INPUT_TEXT_GAP : 0);
                LostTalesUiInk.drawText(font, this.label, textX,
                        labelTop(inputY), LostTalesUiInk.IVORY, 255);
                end = textX + font.getStringWidth(this.label);
            }
            return end;
        }

        /** Where words beside a key stand: their capitals centred on it. */
        private static int labelTop(int inputY) {
            return inputY + LostTalesUiInk.centredStart(
                    LostTalesInputIconRenderer.BASE_ICON_HEIGHT,
                    LostTalesUiInk.CAP_HEIGHT);
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
                    LostTalesUiInk.drawText(font, this.text, x,
                            labelTop(inputY), LostTalesUiInk.IVORY, 255);
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

    /**
     * What a strip of one width shows: how many hints at each end, whether
     * they are named, and which status fits beside them.
     */
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
}
