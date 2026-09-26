package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.input.LostTalesInputBinding;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiItemIcon;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * A page's input bar: the chat's bar strip at every window's foot, with
 * what the page puts on it ({@link BarItem}). It wears the chat bar's
 * surface, holes, well, dividers, framed buttons and glyphs, arrives with
 * the same entrance from below, and carries the window frame's lower
 * edges, so a page's window and a conversation's are one shape.
 *
 * <p>The layout is worked out from the page's items whenever it is asked
 * — to draw, to find what the pointer is on, to press — so the three
 * never read an edge differently.</p>
 */
public final class WindowBar {
    /** Clear space before the bar's first control, after its last and between groups, as on the chat's bar. */
    public static final int GAP = 3;
    /** Clear space between two framed buttons side by side. */
    public static final int BUTTON_GAP = 2;
    /** A glyph button's square and the room between two of them. */
    public static final int GLYPH_SIZE = 12;
    public static final int GLYPH_MARGIN = 2;
    /** Clear pixels between a divider and the well beside it. */
    public static final int WELL_GAP = 2;
    /** The narrowest the field is ever squeezed to. */
    public static final int MIN_FIELD_WIDTH = 40;
    /** The most rows a field's list shows at once. */
    public static final int MAX_OFFERS = 8;

    /** Measures words, so the layout can be worked out without a font in the tests. */
    public interface Measure {
        int width(String text);
    }

    /** One item where it stands on the bar, in the bar's own whole pixels. */
    public static final class Placed {
        public final BarItem item;
        public final int left;
        public final int right;
        /** A button shown as its icon alone, the room being short. */
        public final boolean compact;

        Placed(BarItem item, int left, int right, boolean compact) {
            this.item = item;
            this.left = left;
            this.right = right;
            this.compact = compact;
        }
    }

    /** What a point of the bar is. */
    public static final class Hit {
        public final BarItem item;
        /** A row of a field's list; -1 for the item itself. */
        public final int offer;

        public Hit(BarItem item, int offer) {
            this.item = item;
            this.offer = offer;
        }
    }

    private final Map<String, LostTalesUiButtonMotion> motions =
            new HashMap<String, LostTalesUiButtonMotion>();

    /* ---- The layout ---- */

    /**
     * The items laid on a bar from {@code left} to {@code right}: the
     * buttons before the field from the left, the field in the room left
     * over, the buttons after it, the words, the glyphs and an ending
     * button from the right, the ending one last. Where the room is short
     * every button gives up its word and keeps its icon, then the words
     * go, then what no longer fits from the left group's end.
     */
    public static List<Placed> layOut(List<BarItem> items, int left, int right,
                                      Measure measure) {
        List<BarItem> leading = new ArrayList<BarItem>();
        List<BarItem> trailing = new ArrayList<BarItem>();
        List<BarItem> atRight = new ArrayList<BarItem>();
        List<BarItem> ending = new ArrayList<BarItem>();
        BarItem field = null;
        for (BarItem item : items) {
            if (item.kind == BarItem.Kind.FIELD && field == null) {
                field = item;
            } else if (item.ending) {
                ending.add(item);
            } else if (item.right) {
                atRight.add(item);
            } else if (field == null) {
                leading.add(item);
            } else {
                trailing.add(item);
            }
        }
        atRight.addAll(ending);
        int room = right - left;
        boolean compact = false;
        boolean words = true;
        while (true) {
            int needed = width(leading, compact, words, measure)
                    + width(trailing, compact, words, measure)
                    + width(atRight, compact, words, measure)
                    + (field == null ? 0 : MIN_FIELD_WIDTH + 2 * (GAP
                            + WindowStyle.DIVIDER_WIDTH + WELL_GAP));
            if (needed <= room) {
                break;
            }
            if (!compact) {
                compact = true;
            } else if (words) {
                words = false;
            } else if (!trailing.isEmpty()) {
                trailing.remove(trailing.size() - 1);
            } else if (!leading.isEmpty()) {
                leading.remove(leading.size() - 1);
            } else {
                break;
            }
        }
        List<Placed> placed = new ArrayList<Placed>();
        int x = left + GAP;
        BarItem previous = null;
        for (BarItem item : leading) {
            x += gapBefore(previous, item);
            int width = widthOf(item, compact, measure);
            placed.add(new Placed(item, x, x + width, compact));
            x += width;
            previous = item;
        }
        int leadingRight = x;
        int rightX = right - GAP;
        List<Placed> fromRight = new ArrayList<Placed>();
        BarItem next = null;
        List<BarItem> rightToLeft = new ArrayList<BarItem>(atRight);
        rightToLeft.addAll(0, trailing);
        for (int index = rightToLeft.size() - 1; index >= 0; index--) {
            BarItem item = rightToLeft.get(index);
            if (item.kind == BarItem.Kind.WORDS && !words) {
                continue;
            }
            rightX -= gapBefore(item, next);
            int width = widthOf(item, compact, measure);
            fromRight.add(0, new Placed(item, rightX - width, rightX,
                    compact));
            rightX -= width;
            next = item;
        }
        if (field != null) {
            // The well stands between two dividers, each a gap from what
            // stands beside it; at the bar's own edge there is no divider.
            int wellLeft = leading.isEmpty() ? left + GAP
                    : leadingRight + GAP + WindowStyle.DIVIDER_WIDTH
                            + WELL_GAP;
            int wellRight = fromRight.isEmpty() ? right - GAP
                    : rightX - GAP - WindowStyle.DIVIDER_WIDTH - WELL_GAP;
            placed.add(new Placed(field, wellLeft,
                    Math.max(wellLeft, wellRight), false));
        }
        placed.addAll(fromRight);
        return placed;
    }

    /** The room between two items side by side: framed buttons stand close, anything else a gap apart. */
    private static int gapBefore(BarItem previous, BarItem item) {
        if (previous == null || item == null) {
            return 0;
        }
        if (previous.kind == BarItem.Kind.GLYPH
                && item.kind == BarItem.Kind.GLYPH) {
            return GLYPH_MARGIN;
        }
        return previous.kind == BarItem.Kind.BUTTON
                && item.kind == BarItem.Kind.BUTTON ? BUTTON_GAP : GAP;
    }

    private static int width(List<BarItem> items, boolean compact,
                             boolean words, Measure measure) {
        int total = 0;
        BarItem previous = null;
        for (BarItem item : items) {
            if (item.kind == BarItem.Kind.WORDS && !words) {
                continue;
            }
            total += gapBefore(previous, item) + widthOf(item, compact,
                    measure);
            previous = item;
        }
        return total + (items.isEmpty() ? 0 : GAP);
    }

    /**
     * An item's width: a framed button its icon, the gap and its word
     * inside the wide inset, as the channel's button on the chat's bar,
     * or a square round its icon alone; a glyph its square; words their
     * ink.
     */
    static int widthOf(BarItem item, boolean compact, Measure measure) {
        switch (item.kind) {
            case BUTTON: {
                boolean icon = item.icon != null || item.glyph != null;
                if (compact && icon) {
                    return LostTalesUiFramedButton.HEIGHT;
                }
                // A word's last column is spacing, not ink.
                int word = Math.max(0, measure.width(item.label) - 1);
                return 2 * LostTalesUiFramedButton.WIDE_INSET
                        + (icon ? TabIcons.SIZE + TabIcons.GAP : 0) + word;
            }
            case GLYPH:
                return GLYPH_SIZE;
            case WORDS:
                return Math.max(0, measure.width(item.label) - 1);
            default:
                return 0;
        }
    }

    /**
     * What an action does, with its key after it, as every tip on a bar
     * names the key: {@code Current Location (R)}. An unbound key adds
     * nothing.
     */
    public static String withKey(String words, int keyCode) {
        LostTalesInputBinding.Type type = LostTalesInputBinding.getType(keyCode);
        if (type != LostTalesInputBinding.Type.KEYBOARD
                && type != LostTalesInputBinding.Type.MOUSE_BUTTON) {
            return words;
        }
        return words + " (" + LostTalesInputBinding.getFallbackLabel(type,
                keyCode) + ")";
    }

    /* ---- Where the bar stands ---- */

    /** The bar's entrance from below, the chat's bar's own: how far below its place it stands now. */
    public static float entranceOffset() {
        return WindowOpening.barOffset();
    }

    /** Top of a window's bar strip in whole pixels; its first row is the window's bottom rule. */
    static int top(WindowFrame frame) {
        return (int)Math.floor(frame.barTop());
    }

    static int left(WindowFrame frame) {
        return (int)Math.floor(frame.boxLeft);
    }

    static int right(WindowFrame frame) {
        return left(frame) + (int)Math.round(frame.boxRight - frame.boxLeft);
    }

    /** Top of the framed buttons on a bar starting at {@code top}: the clear rows below the rule. */
    static int buttonTop(int top) {
        return top + 1 + 2;
    }

    /** Top of the glyphs' squares and of the well, centred on the framed buttons. */
    static int glyphTop(int top) {
        return buttonTop(top) + (LostTalesUiFramedButton.HEIGHT - GLYPH_SIZE) / 2;
    }

    /** The row a word stands on in the bar, as a message row puts its text. */
    static int textTop(int top) {
        return glyphTop(top) + WindowStyle.ROW_TEXT_TOP;
    }

    /* ---- What the pointer is on ---- */

    /** What a screen point is on the bar of a page's window; null off it. */
    public Hit hitAt(FontRenderer font, WindowFrame frame, PageContent content,
                     double x, double y) {
        if (font == null || frame == null || content == null || !frame.drawn) {
            return null;
        }
        int top = top(frame);
        double barX = x - fraction(frame);
        double barY = y - (frame.barTop() - top) - entranceOffset();
        List<Placed> placed = layOut(content.barItems(), left(frame),
                right(frame), measure(font));
        for (Placed each : placed) {
            if (each.item.kind == BarItem.Kind.FIELD) {
                int offer = offerAt(font, each, top, barX, barY);
                if (offer >= 0) {
                    return new Hit(each.item, offer);
                }
            }
        }
        if (barY < top || barY >= top + WindowPlacement.BAR_STRIP_HEIGHT
                || barX < left(frame) || barX >= right(frame)) {
            return null;
        }
        for (Placed each : placed) {
            if (each.item.kind != BarItem.Kind.WORDS && barX >= each.left
                    && barX < each.right) {
                return new Hit(each.item, -1);
            }
        }
        return new Hit(null, -1);
    }

    /** The row of a field's list under a point; -1 off the list. */
    private static int offerAt(FontRenderer font, Placed field, int top,
                               double x, double y) {
        int[] list = offerList(font, field, top);
        if (list == null || x < list[0] || x >= list[2] || y < list[1]
                || y >= list[3]) {
            return -1;
        }
        int row = (int)Math.floor((y - list[1] - WindowStyle.POPUP_INSET)
                / WindowStyle.LINE_HEIGHT);
        int shown = Math.min(MAX_OFFERS, field.item.offers.size());
        return row >= 0 && row < shown ? firstOffer(field.item) + row : -1;
    }

    /**
     * The field's list over the bar, as left, top, right and bottom: at
     * the well's left, as wide as the well or its widest name, standing
     * two rows above the bar; null while it offers nothing.
     */
    private static int[] offerList(FontRenderer font, Placed field, int top) {
        List<String> offers = field.item.offers;
        if (offers.isEmpty()) {
            return null;
        }
        int rows = Math.min(MAX_OFFERS, offers.size());
        int widest = 0;
        for (String offer : offers) {
            widest = Math.max(widest, font.getStringWidth(offer));
        }
        int width = Math.max(field.right - field.left,
                widest + 2 * WindowStyle.POPUP_INSET);
        int height = rows * WindowStyle.LINE_HEIGHT
                + 2 * WindowStyle.POPUP_INSET;
        int bottom = top - 2;
        return new int[] {field.left, bottom - height, field.left + width,
                bottom};
    }

    /** The first row the list shows: the chosen one stays in sight. */
    private static int firstOffer(BarItem field) {
        int size = field.offers.size();
        if (size <= MAX_OFFERS || field.offered < MAX_OFFERS) {
            return 0;
        }
        return Math.min(size - MAX_OFFERS, field.offered - MAX_OFFERS + 1);
    }

    /* ---- Drawing ---- */

    /**
     * A page window's bar: the surface with holes for its framed buttons
     * and its well, the window frame's edges beside and under it, and
     * what the page put on it. {@code hit} is what the pointer is on,
     * null while it is elsewhere.
     */
    public void draw(Minecraft minecraft, FontRenderer font, WindowFrame frame,
                     PageContent content, PointerRegions regions,
                     LostTalesGuiAnimationSample shown, Hit hit) {
        if (minecraft == null || font == null || frame == null
                || content == null || !frame.drawn) {
            return;
        }
        int top = top(frame);
        int left = left(frame);
        int right = right(frame);
        float share = shown == null ? 1.0F : shown.getOpacity();
        int alpha = Math.round(255.0F * share);
        List<Placed> placed = layOut(content.barItems(), left, right,
                measure(font));
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(fraction(frame),
                    (float)(frame.barTop() - top) + entranceOffset(), 0.0F);
            drawSurface(minecraft, frame, placed, top, left, right, share);
            long now = System.nanoTime();
            for (Placed each : placed) {
                boolean pointed = hit != null && hit.item != null
                        && hit.item.id.equals(each.item.id)
                        && hit.offer < 0;
                switch (each.item.kind) {
                    case BUTTON:
                        drawButton(minecraft, font, frame, each, top, pointed,
                                now, share, alpha);
                        break;
                    case GLYPH:
                        drawGlyph(frame, each, top, pointed, now, alpha);
                        break;
                    case WORDS:
                        LostTalesUiInk.drawText(font, each.item.label,
                                each.left, textTop(top),
                                LostTalesColors.rgb(LostTalesColors.SAND),
                                alpha);
                        break;
                    case FIELD:
                        drawField(font, each, top, alpha);
                        break;
                    default:
                        break;
                }
            }
            drawDividers(placed, top, share);
            for (Placed each : placed) {
                if (each.item.kind == BarItem.Kind.FIELD) {
                    drawOffers(font, each, top, hit, share, regions);
                }
            }
        } finally {
            GL11.glPopMatrix();
        }
        regions.addWindow(left, top, right,
                top + WindowPlacement.BAR_STRIP_HEIGHT);
    }

    /**
     * The bar's surface: the tool strip's plum grey at two thirds, one
     * flat stretch with a hole for each framed button and the well (the
     * well filled with the darker inset), then the window frame's
     * surface beside and under the bar and its edges over it.
     */
    private static void drawSurface(Minecraft minecraft, WindowFrame frame,
                                    List<Placed> placed, int top, int left,
                                    int right, float share) {
        float opacity = share * WindowStyle.opacity(minecraft);
        int surface = LostTalesUiInk.argb(LostTalesUiInk.SURFACE_HIGHLIGHT_RGB,
                Math.round(WindowStyle.INSET_ALPHA * opacity));
        int bottom = top + WindowPlacement.BAR_STRIP_HEIGHT;
        List<int[]> holes = new ArrayList<int[]>();
        for (Placed each : placed) {
            if (each.item.kind == BarItem.Kind.BUTTON) {
                holes.add(new int[] {each.left, buttonTop(top), each.right,
                        buttonTop(top) + LostTalesUiFramedButton.HEIGHT, 1});
            } else if (each.item.kind == BarItem.Kind.FIELD) {
                holes.add(new int[] {each.left, glyphTop(top), each.right,
                        glyphTop(top) + WindowStyle.LINE_HEIGHT, 0});
            }
        }
        fillWithHoles(left, top + 1, right, bottom, holes, surface);
        for (Placed each : placed) {
            if (each.item.kind == BarItem.Kind.FIELD
                    && each.right > each.left) {
                LostTalesUiInk.fillRect(each.left, glyphTop(top), each.right,
                        glyphTop(top) + WindowStyle.LINE_HEIGHT,
                        WindowStyle.insetArgb(opacity));
            }
        }
        drawFoot(left, top, right, surface,
                (float)(frame.boxBottom - frame.boxTop), Math.round(255.0F * share));
    }

    /**
     * A strip from {@code left} to {@code right} in one surface, leaving
     * each hole unpainted; a framed button's hole (its fifth number 1)
     * keeps its corner pixels, which lie outside the frame's rounding.
     * The input bars of the chat and of every page are painted through
     * here.
     */
    public static void fillWithHoles(int left, int top, int right, int bottom,
                                     List<int[]> holes, int argb) {
        int x = left;
        for (int[] hole : holes) {
            int holeLeft = Math.max(x, hole[0]);
            int holeRight = Math.min(right, hole[2]);
            if (holeRight <= holeLeft) {
                continue;
            }
            LostTalesUiInk.fillRect(x, top, holeLeft, bottom, argb);
            WindowStyle.fillAround(holeLeft, top, holeRight, bottom,
                    hole[0], hole[1], hole[2], hole[3], argb);
            if (hole[4] == 1) {
                LostTalesUiFramedButton.fillCorners(hole[0], hole[1],
                        hole[2] - hole[0], hole[3] - hole[1], argb);
            }
            x = holeRight;
        }
        if (x < right) {
            LostTalesUiInk.fillRect(x, top, right, bottom, argb);
        }
    }

    /**
     * The window frame's surface beside and under a bar strip, a frame
     * wide, in the bar's own surface, the row the window's bottom rule
     * stands on included, and the frame's edges beside and under the bar
     * with its two bottom corners over it. {@code windowHeight} is how far
     * the window's box reaches above the bar's foot, for the edges' ramps.
     */
    public static void drawFoot(int left, int top, int right, int surface,
                                float windowHeight, int alpha) {
        int bottom = top + WindowPlacement.BAR_STRIP_HEIGHT;
        int ring = WindowPlacement.FRAME_WIDTH;
        LostTalesUiInk.fillRect(left - ring, top, left, bottom, surface);
        LostTalesUiInk.fillRect(right, top, right + ring, bottom, surface);
        LostTalesUiInk.fillRect(left - ring, bottom, right + ring,
                bottom + ring - 1, surface);
        // The ring's outermost corner pixels lie outside the frame's
        // rounding, as a framed button's footprint corners do.
        LostTalesUiInk.fillRect(left - ring + 1, bottom + ring - 1,
                right + ring - 1, bottom + ring, surface);
        LostTalesUiWindowFrame.drawEdgesBelow(left, bottom - windowHeight,
                right, bottom, top, alpha);
    }

    /**
     * A framed button: its surface in the hole, lit as far as the
     * pointer, a press or its own state light it; its icon and word
     * lifted in its pose while the frame stands still; greyed, with the
     * word in the aside tone and the icon faint, while it cannot be taken.
     */
    private void drawButton(Minecraft minecraft, FontRenderer font,
                            WindowFrame frame, Placed placed, int top,
                            boolean pointed, long now, float share,
                            int alpha) {
        BarItem item = placed.item;
        boolean available = item.isAvailable();
        LostTalesUiButtonMotion motion = motion(frame, item);
        boolean hovered = pointed && available;
        motion.advance(now, hovered || item.lit, hovered,
                hovered && Mouse.isButtonDown(0));
        float lit = available ? motion.lit() : 0.0F;
        int frameTop = buttonTop(top);
        int width = placed.right - placed.left;
        LostTalesUiFramedButton.drawSurface(placed.left, frameTop, width,
                LostTalesUiFramedButton.HEIGHT, lit,
                Math.round(WindowStyle.INSET_ALPHA * share
                        * WindowStyle.opacity(minecraft)));
        boolean icon = item.icon != null || item.glyph != null;
        int contentLeft = placed.compact
                ? placed.left + (width - TabIcons.SIZE) / 2
                : placed.left + LostTalesUiFramedButton.WIDE_INSET;
        int iconAlpha = available ? alpha
                : Math.round(alpha * WindowStyle.UNAVAILABLE_OPACITY);
        if (available) {
            LostTalesUiButton.beginPose(motion, contentLeft, frameTop,
                    width, LostTalesUiFramedButton.HEIGHT);
        }
        try {
            if (icon) {
                drawIcon(minecraft, item, contentLeft,
                        frameTop + LostTalesUiFramedButton.INSET, lit,
                        iconAlpha);
            }
            if (!placed.compact) {
                int wordLeft = contentLeft + (icon ? TabIcons.SIZE
                        + TabIcons.GAP : 0);
                LostTalesUiInk.drawText(font, item.label, wordLeft,
                        textTop(top), wordRgb(item, lit), alpha);
            }
        } finally {
            if (available) {
                LostTalesUiButton.endPose();
            }
        }
        LostTalesUiFramedButton.drawInk(placed.left, frameTop, width,
                LostTalesUiFramedButton.HEIGHT, lit, alpha);
    }

    /** A button's word: ivory, an ending one red; lit, both ivory; greyed, the aside tone. */
    static int wordRgb(BarItem item, float lit) {
        if (!item.isAvailable()) {
            return WindowStyle.asideRgb();
        }
        return item.ending ? LostTalesUiInk.blend(
                LostTalesColors.rgb(LostTalesColors.RED), LostTalesUiInk.IVORY,
                lit) : LostTalesUiInk.IVORY;
    }

    /** A button's icon in its ten-pixel box: an item's picture, or a glyph centred there. */
    private static void drawIcon(Minecraft minecraft, BarItem item, int left,
                                 int top, float lit, int alpha) {
        if (item.icon != null) {
            LostTalesUiItemIcon.drawFitted(minecraft, item.icon, left, top,
                    TabIcons.SIZE, alpha);
            return;
        }
        LostTalesUiSheet glyph = item.glyph;
        LostTalesUiSheet.drawPairWithShadow(glyph,
                item.glyphLit == null ? glyph : item.glyphLit, lit,
                left + (TabIcons.SIZE - glyph.getWidth()) / 2,
                top + (TabIcons.SIZE - glyph.getHeight()) / 2, alpha);
    }

    /** A glyph button: the bare glyph in its square with the one shadow, lifted and lit under the pointer. */
    private void drawGlyph(WindowFrame frame, Placed placed, int top,
                           boolean pointed, long now, int alpha) {
        BarItem item = placed.item;
        LostTalesUiSheet glyph = item.glyph;
        int glyphLeft = placed.left + (GLYPH_SIZE - glyph.getWidth()) / 2;
        int glyphTop = glyphTop(top) + (GLYPH_SIZE - glyph.getHeight()) / 2;
        if (!item.isAvailable()) {
            LostTalesUiSheet.drawPairWithShadow(glyph, glyph, 0.0F, glyphLeft,
                    glyphTop, Math.round(alpha
                            * WindowStyle.UNAVAILABLE_OPACITY));
            return;
        }
        LostTalesUiButtonMotion motion = motion(frame, item);
        motion.advance(now, pointed || item.lit, pointed,
                pointed && Mouse.isButtonDown(0));
        LostTalesUiButton.drawGlyph(glyph,
                item.glyphLit == null ? glyph : item.glyphLit, motion,
                glyphLeft, glyphTop, alpha);
    }

    /**
     * The field in its well, a gap inside each side and a caret short at
     * the end, its words where a message row puts them; the hint in the
     * aside tone and italics while it is empty.
     */
    private static void drawField(FontRenderer font, Placed placed, int top,
                                  int alpha) {
        BarItem item = placed.item;
        int fieldLeft = placed.left + GAP;
        int fieldRight = placed.right - GAP - LostTalesUiCaret.WIDTH;
        if (item.field == null || fieldRight <= fieldLeft) {
            return;
        }
        item.field.xPosition = fieldLeft;
        item.field.yPosition = textTop(top);
        item.field.width = fieldRight - fieldLeft;
        if (item.field.getText().length() == 0) {
            int x = fieldLeft + LostTalesUiCaret.WIDTH + 1;
            LostTalesUiInk.drawText(font, "§o" + font.trimStringToWidth(
                    item.label, Math.max(0, fieldRight - x)), x,
                    textTop(top), WindowStyle.asideRgb(), alpha);
        }
        item.field.drawTextBox();
    }

    /** The dividers either side of the well, as the chat's bar parts its well from its buttons. */
    private static void drawDividers(List<Placed> placed, int top,
                                     float share) {
        int alpha = Math.round(WindowStyle.DIVIDER_ALPHA * share);
        for (int index = 0; index < placed.size(); index++) {
            Placed field = placed.get(index);
            if (field.item.kind != BarItem.Kind.FIELD) {
                continue;
            }
            if (hasItemBefore(placed, field)) {
                WindowStyle.drawDivider(field.left - WELL_GAP
                        - WindowStyle.DIVIDER_WIDTH, glyphTop(top),
                        WindowStyle.LINE_HEIGHT, alpha);
            }
            if (index + 1 < placed.size()) {
                WindowStyle.drawDivider(field.right + WELL_GAP, glyphTop(top),
                        WindowStyle.LINE_HEIGHT, alpha);
            }
        }
    }

    private static boolean hasItemBefore(List<Placed> placed, Placed field) {
        for (Placed each : placed) {
            if (each != field && each.right <= field.left) {
                return true;
            }
        }
        return false;
    }

    /** A field's list, over the bar at its well: the chosen row lit, the pointed one too. */
    private static void drawOffers(FontRenderer font, Placed field, int top,
                                   Hit hit, float share,
                                   PointerRegions regions) {
        int[] list = offerList(font, field, top);
        if (list == null) {
            return;
        }
        int first = firstOffer(field.item);
        int pointed = hit != null && hit.offer >= 0 && hit.item != null
                && hit.item.id.equals(field.item.id)
                ? hit.offer : field.item.offered;
        int rowsTop = list[1] + WindowStyle.POPUP_INSET;
        WindowStyle.drawPopupList(list[0], list[1], list[2], list[3], rowsTop,
                WindowStyle.LINE_HEIGHT, pointed - first);
        int rows = Math.min(MAX_OFFERS, field.item.offers.size());
        for (int row = 0; row < rows; row++) {
            String name = field.item.offers.get(first + row);
            LostTalesUiInk.drawText(font, font.trimStringToWidth(name,
                    list[2] - list[0] - 2 * WindowStyle.POPUP_INSET),
                    list[0] + WindowStyle.POPUP_INSET,
                    rowsTop + row * WindowStyle.LINE_HEIGHT
                            + WindowStyle.ROW_TEXT_TOP,
                    LostTalesUiInk.IVORY, Math.round(255.0F * share));
        }
        regions.add(list[0], list[1], list[2], list[3]);
    }

    private LostTalesUiButtonMotion motion(WindowFrame frame, BarItem item) {
        String key = frame.windowId + "/" + item.id;
        LostTalesUiButtonMotion motion = this.motions.get(key);
        if (motion == null) {
            motion = new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
            this.motions.put(key, motion);
        }
        return motion;
    }

    /** The fraction of a pixel the window stands on across. */
    private static float fraction(WindowFrame frame) {
        return (float)(frame.boxLeft - Math.floor(frame.boxLeft));
    }

    static Measure measure(final FontRenderer font) {
        return new Measure() {
            @Override
            public int width(String text) {
                return font.getStringWidth(text == null ? "" : text);
            }
        };
    }
}
