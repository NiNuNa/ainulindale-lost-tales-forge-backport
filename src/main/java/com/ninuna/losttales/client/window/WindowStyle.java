package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiRules;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GLContext;

/**
 * How every window looks, whatever it holds: its surfaces and how
 * opaque they are, the landing light, the dividers between controls,
 * the popups that follow the pointer, and the pace a control answers
 * the pointer at.
 */
public final class WindowStyle {
    private WindowStyle() {}

    /**
     * A line of words in a window at scale 1: the 10 px emoji sprite and
     * a clear row above and below it. A window's room is counted in it,
     * and the chat's messages stand on it.
     */
    public static final int LINE_HEIGHT = 12;
    /**
     * Where a line's text's top edge stands below the line's top edge:
     * its capitals centred in the line, two clear rows above them and
     * three below, the descender and its shadow taking the first two of
     * those three. Seven rows cannot be centred in twelve, so the
     * capitals stand half a pixel above the line's middle.
     *
     * <p>Everything else in the line is centred on those capitals
     * ({@link #centredBoxTop}), since the words are what the eye reads a
     * line by: a box of odd height lands on their middle exactly, and one
     * of even height, which cannot, stands half a pixel above it rather
     * than below.</p>
     */
    public static final int ROW_TEXT_TOP = (LINE_HEIGHT - LostTalesUiInk.CAP_HEIGHT) / 2;

    /**
     * Where a box {@code height} pixels tall starts against the text's
     * top edge: centred on the capitals, half a pixel above their middle
     * when the two heights differ by an odd number of rows, and never
     * past the line's own edges, so a box as tall as the line fills it.
     * The ten-row box of an emoji, an item or a marker starts two rows
     * above the text's top, an eight-row head one, and the six-row speech
     * bubble on it.
     */
    public static int centredBoxTop(int height) {
        int top = Math.floorDiv(LostTalesUiInk.CAP_HEIGHT - height, 2);
        return Math.max(-ROW_TEXT_TOP,
                Math.min(LINE_HEIGHT - ROW_TEXT_TOP - height, top));
    }

    /** Where the aside tone comes from. */
    public interface Tone {
        int rgb();
    }

    private static volatile Tone asideTone = new Tone() {
        @Override
        public int rgb() {
            return LostTalesColors.rgb(LostTalesColors.ROSE_GRAY);
        }
    };

    /**
     * The tone of the words that stand aside: what a window's empty well
     * says, a count that found nothing. The chat hands over its own aside
     * tone when it is installed, so its Console colour colours these too;
     * rose grey before.
     */
    public static int asideRgb() {
        return asideTone.rgb();
    }

    /** See {@link #asideRgb}. */
    public static void setAsideTone(Tone tone) {
        if (tone != null) {
            asideTone = tone;
        }
    }

    /**
     * How deep the shade hanging from a window's top edge reaches: one
     * line, so a line passing under the top rule has faded most of the
     * way out before the clip cuts it.
     */
    public static final float TOP_EDGE_FADE_HEIGHT = LINE_HEIGHT;
    /**
     * How deep the shade rising from a window's bottom edge reaches: two
     * lines, through the trailing strip and over the newest line.
     */
    public static final float BOTTOM_EDGE_FADE_HEIGHT = LINE_HEIGHT * 2.0F;

    /**
     * The one opacity of every surface a window lays out — backdrop,
     * strips, tabs, bars: half, thinned by the game's chat opacity.
     * Text and icons are always fully opaque.
     */
    public static final int SURFACE_ALPHA = 0x80;

    /**
     * Where a carried window will land, lit: the edge of the window it
     * sticks to, and the zone of a snap layout it fills. Honey, the
     * palette's yellow.
     */
    public static final int LANDING_RGB = LostTalesColors.rgb(LostTalesColors.HONEY);

    /**
     * The frame a popup wears inside its footprint: a window's, a
     * pixel of surface with the ink inside it (Nils, 2026-09-24).
     */
    public static final int POPUP_FRAME = LostTalesUiWindowFrame.WIDTH;

    /**
     * From a popup's edge to what it shows: the frame and two clear
     * pixels, as round a framed button's content.
     */
    public static final int POPUP_INSET = POPUP_FRAME + 2;

    /** A one-line popup's height: the capitals and their shadow, the inset above and below. */
    public static final int POPUP_LINE_HEIGHT = POPUP_INSET * 2
            + LostTalesUiInk.CAP_HEIGHT
            + LostTalesUiInk.SHADOW_OFFSET;

    /**
     * A margin, a well or a tab has a surface of its own, a step darker
     * than the half-opacity surface beside it: two thirds. The surface
     * beside it stops where it begins rather than running under it, so
     * each area is one flat colour and no two backgrounds are ever laid
     * over each other — the timestamp column beside the panel, the
     * typing well in a hole cut out of the input bar, a tab in the hole
     * the tab strip leaves for it.
     */
    public static final int INSET_ALPHA = Math.round(255.0F * 2.0F / 3.0F);

    /**
     * The windows' inset surface: plum black, the palette's darkest, at two
     * thirds. The typing well, the timestamp column and a tab nobody has
     * picked or is pointing at all wear exactly this.
     */
    public static final int SURFACE_INSET = LostTalesSkyrimUiStyle.withAlpha(
            LostTalesSkyrimUiStyle.PLUM_BLACK, INSET_ALPHA);

    /**
     * A window's panel and the rows framing it, in the
     * palette colour the client chose; plum black until it chooses.
     * Read on every draw, so a choice made in a window's menu shows the
     * same frame.
     */
    public static int backdropRgb() {
        return LostTalesColors.rgb(LostTalesColors.paletteColor(LostTalesConfig.chatBackgroundColor,
                LostTalesColors.PLUM_BLACK));
    }

    /**
     * The game's chat opacity as the chat applies it, never below a
     * tenth: what the panel and every surface beside it are thinned by.
     */
    public static float opacity(Minecraft minecraft) {
        return minecraft.gameSettings.chatOpacity * 0.9F + 0.1F;
    }

    /**
     * A window's surface at {@code share} of its opacity: the panel's
     * own colour at half, so a window's strips and its bar are one flat
     * stretch of exactly what its history is drawn in.
     */
    public static int surfaceArgb(float share) {
        return LostTalesUiInk.argb(backdropRgb(), Math.round(SURFACE_ALPHA * share));
    }

    /** The inset surface at {@code share} of its opacity. */
    public static int insetArgb(float share) {
        return LostTalesUiInk.argb(LostTalesUiInk.SURFACE_RGB, Math.round(INSET_ALPHA * share));
    }

    /** How much of its opacity a control that cannot be taken here is drawn at. */
    public static final float UNAVAILABLE_OPACITY = 0.5F;

    /** Width of the hairline between two controls. */
    public static final int DIVIDER_WIDTH = 1;

    /** Quiet enough to divide without reading as an edge of its own. */
    public static final int DIVIDER_ALPHA = 0x66;

    /**
     * A divider between two controls: a one-pixel column of the
     * palette's ivory, strongest at its middle and fading to nothing at both ends,
     * so it parts them without drawing an edge across their strip. The
     * tab row and the input bar both come through here, so their
     * dividers cannot drift apart.
     */
    public static void drawDivider(int x, int top, int height, int alpha) {
        LostTalesUiRules.drawVerticalRule(x, x + DIVIDER_WIDTH,
                top, top + height, alpha);
    }

    /**
     * A popup's surface over {@code [left, right)} by {@code [top,
     * bottom)}: what follows the pointer or the caret and never moves
     * wears the sub-windows' surface — the inset plum black at two
     * thirds, thinned by the game's chat opacity and by {@code opacity} —
     * and a window's frame inside its footprint, as the snap panels
     * do (Nils, 2026-09-24). The lit row — {@code [rowLeft, rowRight)} by
     * {@code [rowTop, rowBottom)}, what a press or Enter would take — is
     * the surface in the highlight's tone, cut to the frame's inside;
     * surface and row lie side by side, never one over another, and the
     * frame's edges lie over its ring as on a window. A row with no area
     * lights nothing.
     */
    public static void drawPopup(float left, float top, float right, float bottom,
                          float opacity, float rowLeft, float rowTop,
                          float rowRight, float rowBottom) {
        float share = Math.max(0.0F, Math.min(1.0F, opacity));
        float boxLeft = left + POPUP_FRAME;
        float boxTop = top + POPUP_FRAME;
        float boxRight = right - POPUP_FRAME;
        float boxBottom = bottom - POPUP_FRAME;
        int surfaceAlpha = Math.round(INSET_ALPHA * share
                * opacity(Minecraft.getMinecraft()));
        if (surfaceAlpha < LostTalesUiInk.MIN_VISIBLE_ALPHA || boxRight <= boxLeft
                || boxBottom <= boxTop) {
            return;
        }
        int surface = LostTalesUiInk.argb(LostTalesUiInk.SURFACE_RGB, surfaceAlpha);
        float litLeft = Math.max(boxLeft, rowLeft);
        float litTop = Math.max(boxTop, rowTop);
        float litRight = Math.min(boxRight, rowRight);
        float litBottom = Math.min(boxBottom, rowBottom);
        fillAround(boxLeft, boxTop, boxRight, boxBottom, litLeft, litTop,
                litRight, litBottom, surface);
        if (litRight > litLeft && litBottom > litTop) {
            LostTalesUiInk.fillRect(litLeft, litTop, litRight, litBottom,
                    LostTalesUiInk.argb(LostTalesUiInk.SURFACE_HIGHLIGHT_RGB, surfaceAlpha));
        }
        LostTalesUiWindowFrame.drawSurface(boxLeft, boxTop, boxRight,
                boxBottom, surface);
        LostTalesUiWindowFrame.drawEdges(boxLeft, boxTop, boxRight,
                boxBottom, Math.round(255.0F * share));
    }

    /** Whether the blend equations a recolour needs are there; asked once. */
    private static Boolean recolourSupported;

    /** Whether a surface can be recoloured in place rather than laid over. */
    public static boolean canRecolour() {
        if (recolourSupported == null) {
            boolean supported;
            try {
                supported = GLContext.getCapabilities().OpenGL14;
            } catch (RuntimeException unavailable) {
                supported = false;
            }
            recolourSupported = Boolean.valueOf(supported);
        }
        return recolourSupported.booleanValue();
    }

    /**
     * A stretch of a surface laid in one flat colour — a menu's row, the
     * timestamp area, the member list — given another colour in place:
     * the surface's own share there is taken back out, its colour
     * subtracted at {@code alpha}, and {@code toRgb} at the same alpha
     * added in its place, so the stretch reads as the surface painted in
     * the new colour and the world behind it is darkened once. Only for
     * area the surface covers and nothing has been drawn over since;
     * where the blend equations are missing the new colour is laid over
     * the surface instead.
     */
    public static void recolourFlat(float left, float top, float right,
                                    float bottom, int alpha, int fromRgb,
                                    int toRgb) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (right <= left || bottom <= top || safeAlpha <= 0) {
            return;
        }
        if (!canRecolour()) {
            LostTalesUiInk.fillRect(left, top, right, bottom,
                    LostTalesUiInk.argb(toRgb, safeAlpha));
            return;
        }
        try {
            GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
            flatQuad(left, top, right, bottom, safeAlpha, fromRgb);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            flatQuad(left, top, right, bottom, safeAlpha, toRgb);
        } finally {
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        }
    }

    /** One quad added to the framebuffer at its own alpha, leaving the framebuffer's alpha as it is. */
    private static void flatQuad(float left, float top, float right,
                                 float bottom, int alpha, int rgb) {
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(true);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE,
                GL11.GL_ZERO, GL11.GL_ONE);
        // The GUI pass culls back faces: the backdrops' own winding.
        tessellator.setColorRGBA_I(rgb, alpha);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.addVertex(left, bottom, 0.0D);
        LostTalesSkyrimUiStyle.endQuads(tessellator, true);
    }

    /** A popup with no row lit. */
    public static void drawPopup(float left, float top, float right, float bottom,
                          float opacity) {
        drawPopup(left, top, right, bottom, opacity, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    /** A one-line popup's width for {@code text}: the text, the inset either side. */
    public static int popupLineWidth(FontRenderer font, String text) {
        return font.getStringWidth(text) + POPUP_INSET * 2;
    }

    /**
     * A one-line popup with its top left at {@code x}, {@code y},
     * {@link #popupLineWidth} wide and {@link #POPUP_LINE_HEIGHT} tall:
     * the framed surface at {@code opacity}, and {@code text} two clear
     * pixels inside the frame. The pointer's tip, a picker's tip and the
     * bar's notice are each one.
     */
    public static void drawPopupLine(FontRenderer font, String text, int x, int y,
                              float opacity) {
        drawPopup(x, y, x + popupLineWidth(font, text), y + POPUP_LINE_HEIGHT,
                opacity);
        LostTalesUiInk.drawText(font, text, x + POPUP_INSET, y + POPUP_INSET, LostTalesUiInk.IVORY,
                Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, opacity))));
    }

    /**
     * A list's popup: rows {@code rowHeight} apart from {@code rowsTop},
     * which stand {@link #POPUP_INSET} inside its edges, row
     * {@code litRow} lit across the frame's inside; none for -1.
     */
    public static void drawPopupList(int left, int top, int right, int bottom,
                              int rowsTop, int rowHeight, int litRow) {
        int rowTop = rowsTop + litRow * rowHeight;
        drawPopup(left, top, right, bottom, 1.0F, left, rowTop, right,
                litRow < 0 ? rowTop : rowTop + rowHeight);
    }

    /**
     * A surface over {@code [left, right)} by {@code [top, bottom)} with
     * a rectangular hole left unpainted: the full height either side of
     * the hole, and between them the rows above and below it. A hole
     * with no area inside the surface leaves the surface whole.
     */
    public static void fillAround(float left, float top, float right, float bottom,
                           float holeLeft, float holeTop, float holeRight,
                           float holeBottom, int argb) {
        float cutLeft = Math.max(left, holeLeft);
        float cutRight = Math.min(right, holeRight);
        float cutTop = Math.max(top, holeTop);
        float cutBottom = Math.min(bottom, holeBottom);
        if (cutRight <= cutLeft || cutBottom <= cutTop) {
            LostTalesUiInk.fillRect(left, top, right, bottom,
                    argb);
            return;
        }
        LostTalesUiInk.fillRect(left, top, cutLeft, bottom,
                argb);
        LostTalesUiInk.fillRect(cutRight, top, right, bottom,
                argb);
        LostTalesUiInk.fillRect(cutLeft, top, cutRight, cutTop,
                argb);
        LostTalesUiInk.fillRect(cutLeft, cutBottom, cutRight,
                bottom, argb);
    }

    /**
     * One step of a control's crossfade toward {@code hovered}: 0 while
     * it rests, 1 while the pointer is on it, and on the way between
     * them the share of the hovered artwork to lay over the resting one.
     * Every control drawn in two states steps with this, so
     * they all answer the pointer at the same pace.
     */
    public static float hoverFade(float progress, boolean hovered,
                           double elapsed) {
        float target = hovered ? 1.0F : 0.0F;
        float value = (float)Motions.follow(MotionIds.CHAT_HOVER_FADE,
                progress, target, elapsed);
        return Math.abs(target - value) < 0.02F ? target : value;
    }
}
