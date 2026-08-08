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
    /**
     * Room kept clear down the middle. LOTR writes the place the map is
     * looking at and its coordinates there, centred, and those are what a
     * player reads while they move the map — so the controls are pushed out
     * to the two ends rather than allowed to grow into it.
     *
     * <p>A width rather than a share, because what it has to clear is two
     * lines of text that are the same size on every screen.</p>
     */
    private static final int CENTER_RESERVED = 180;
    /**
     * Gap between the last control and the date. Wide enough that the date
     * reads as a separate thing rather than as another control's label.
     */
    private static final int CALENDAR_GAP = 20;
    /** How far the rule between the date and the controls stops short. */
    private static final int SEPARATOR_INSET = 7;
    /** How many hints belong to the left end, before the rest go right. */
    private static final int LEFT_GROUP_SIZE = 3;
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
        String[] calendars = LostTalesLotrMapCalendar.describe();
        int[] calendarWidths = new int[calendars.length];
        for (int index = 0; index < calendars.length; index++) {
            calendarWidths[index] = font.getStringWidth(calendars[index]);
        }
        Layout layout = calculateLayout(gui.width, hints, calendarWidths);

        beginUntranslatedRender();
        try {
            int top = Math.max(0, gui.height - HEIGHT);
            Gui.drawRect(0, top, gui.width, gui.height, BACKGROUND);
            Gui.drawRect(0, top, gui.width, top + 1,
                    LostTalesSkyrimUiStyle.BORDER_DIM);
            drawHints(minecraft, font, gui.width, top, hints, layout);
            if (layout.showCalendar) {
                String calendar = calendars[layout.calendarIndex];
                int calendarX = gui.width - OUTER_PADDING
                        - font.getStringWidth(calendar);
                font.drawStringWithShadow(calendar, calendarX,
                        top + (HEIGHT - font.FONT_HEIGHT) / 2,
                        LostTalesSkyrimUiStyle.TEXT);
                // A rule down the middle of the gap, so the date reads as a
                // separate thing from the controls rather than as one more.
                int ruleX = calendarX - CALENDAR_GAP / 2;
                Gui.drawRect(ruleX, top + SEPARATOR_INSET,
                        ruleX + 1, top + HEIGHT - SEPARATOR_INSET,
                        LostTalesSkyrimUiStyle.BORDER_DIM);
            }
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
            Minecraft minecraft, FontRenderer font, int screenWidth, int top,
            List<Hint> hints, Layout layout) {
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
        // The right group is laid out from its own end inwards, so it stays
        // pinned to the edge whatever it has had to give up. The date takes
        // the end itself, so the group starts inside of it.
        int width = groupWidth(hints, LEFT_GROUP_SIZE,
                layout.rightHints, layout.showLabels);
        x = Math.max(OUTER_PADDING, screenWidth - OUTER_PADDING
                - layout.calendarWidth() - width);
        for (int index = 0; index < layout.rightHints; index++) {
            if (index > 0) {
                x += CONTROL_GAP;
            }
            x = hints.get(LEFT_GROUP_SIZE + index).draw(
                    minecraft, font, x, inputY, layout.showLabels);
        }
    }

    /**
     * Fits the hints into the two ends of the strip.
     *
     * <p>The middle belongs to the place name and the coordinates, so each
     * group gets one side band and neither may grow past it. Within a band
     * the old rule stands: a named key is worth more than an extra unnamed
     * one, because a row of bare icons tells the player which keys do
     * something but not what. So hints are dropped from the end of their own
     * group — they are collected least-needed-last — for as long as that
     * keeps the rest named, and only a strip too narrow for even one named
     * hint falls back to icons alone. Labels stay all-or-nothing across both
     * groups, so the strip never names some controls and not others.</p>
     *
     * <p>The date is informational where the controls are not, so the
     * controls are fitted first and the date takes what is left over at the
     * right end: the longest of its forms that fits beside them, or none of
     * them. Adding it can never cost a control its place on the strip.</p>
     */
    static Layout calculateLayout(
            int screenWidth, List<Hint> hints, int[] calendarWidths) {
        int band = Math.max(0,
                (screenWidth - CENTER_RESERVED) / 2 - OUTER_PADDING);
        if (hints == null || hints.isEmpty()) {
            return new Layout(0, 0, false, -1, 0);
        }
        Layout labelled = fitBothEnds(hints, true, band, calendarWidths);
        return labelled.visibleHints() > 0
                ? labelled
                : fitBothEnds(hints, false, band, calendarWidths);
    }

    private static Layout fitBothEnds(
            List<Hint> hints, boolean withLabels, int band,
            int[] calendarWidths) {
        int leftGroup = Math.min(LEFT_GROUP_SIZE, hints.size());
        int rightGroup = hints.size() - leftGroup;
        int left = fit(hints, 0, leftGroup, withLabels, band);
        int right = fit(hints, leftGroup, rightGroup, withLabels, band);
        // The date shares the right end with that group, and takes only what
        // the controls have left over.
        int spare = band - groupWidth(hints, leftGroup, right, withLabels)
                - (right > 0 ? CALENDAR_GAP : 0);
        int index = -1;
        int width = 0;
        if (calendarWidths != null) {
            for (int candidate = 0; candidate < calendarWidths.length;
                 candidate++) {
                if (calendarWidths[candidate] > 0
                        && calendarWidths[candidate] <= spare) {
                    index = candidate;
                    width = calendarWidths[candidate];
                    break;
                }
            }
        }
        return new Layout(left, right, withLabels && left + right > 0,
                index, width);
    }

    /** How many of a group's hints, from its start, fit in {@code room}. */
    private static int fit(List<Hint> hints, int from, int count,
                           boolean withLabels, int room) {
        for (int taken = count; taken > 0; taken--) {
            if (groupWidth(hints, from, taken, withLabels) <= room) {
                return taken;
            }
        }
        return 0;
    }

    private static int groupWidth(List<Hint> hints, int from, int count,
                                  boolean withLabels) {
        int width = 0;
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                width += CONTROL_GAP;
            }
            width += hints.get(from + index).width(withLabels);
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
        final int leftHints;
        final int rightHints;
        final boolean showLabels;
        final boolean showCalendar;
        /** Which of the date's forms fitted, or -1 when none did. */
        final int calendarIndex;
        private final int calendarTextWidth;

        private Layout(int leftHints, int rightHints, boolean showLabels,
                       int calendarIndex, int calendarTextWidth) {
            this.leftHints = leftHints;
            this.rightHints = rightHints;
            this.showLabels = showLabels;
            this.calendarIndex = calendarIndex;
            this.calendarTextWidth = Math.max(0, calendarTextWidth);
            this.showCalendar =
                    calendarIndex >= 0 && this.calendarTextWidth > 0;
        }

        /** Room the date takes off the right end, gap included. */
        int calendarWidth() {
            return this.calendarTextWidth == 0
                    ? 0 : this.calendarTextWidth + CALENDAR_GAP;
        }

        int visibleHints() {
            return this.leftHints + this.rightHints;
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
