package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * The tabs of one chat window, laid out the way a browser lays out
 * its tabs. Tabs stand side by side with a one-pixel seam on the
 * window's top rule; the selected tab is drawn last, lifted, and
 * brighter. A tab is built rather than stretched: the sheet's left and
 * right border pieces at its ends, a line joining their tips, and the
 * tab's interior tone filling the span between them, so a label of any
 * length leaves the artwork undistorted. Switching is a hard cut: the
 * picked tab is forward the frame it is picked, however the pick
 * happened — a click, the keyboard, or a command bringing the console
 * forward — so nothing sweeps across the tabs between. A tab with
 * unread messages carries textual counters after its name:
 * {@code [p]} pings in salmon, then {@code [x]} other unread lines in
 * honey; a muted tab is drawn dim and italic. The channel's colour runs
 * across each tab's face two rows under that line, full at the centre
 * and fading to nothing at both ends.
 *
 * <p>Every tab carries a settings cog and, unless it is the last tab the
 * player could close, a close cross, while the row has room for them;
 * when it has not, only the selected tab keeps its controls and the
 * labels are shortened with an ellipsis, widest first, until the row
 * fits. The tabs never cross their limit: the row ends with the window's
 * lock, a restore control while channels are closed, and the grip that
 * moves the window, and those keep their room however many tabs there
 * are. Geometry is computed once per change of inputs and reused by
 * drawing and hit testing, so a frame allocates nothing.</p>
 *
 * <p>The row is positioned by its window: {@code rowBottom} and
 * {@code offsetX} come from the frame that drew the window, so the row
 * enters, settles and fades with the same shared motion state as the
 * history it stands on.</p>
 */
final class ChatChannelTabBar {
    /** Height of a resting tab's border pieces; the selected pair adds
     *  the lift. */
    private static final int PIECE_HEIGHT =
            ChatIconSheet.TAB_LEFT.getHeight();
    /**
     * Body height of a resting tab: the sheet's resting border pieces
     * whole, and under them the window's top rule, which is the strip's
     * last row and the one row a tab does not draw on. The selected tab
     * rises out of the row — its border pieces are exactly the lift
     * taller — and its interior rises with it rather than growing.
     */
    static final int HEIGHT = PIECE_HEIGHT + 1;
    /** Pixels the selected tab rises out of the row. */
    static final int LIFT = 2;
    /** Full height of the row: a resting tab plus the lift. */
    static final int ROW_HEIGHT = HEIGHT + LIFT;
    /** Width of a tab's border pieces; the same in every state. */
    private static final int BORDER_WIDTH = ChatIconSheet.TAB_LEFT.getWidth();
    /**
     * The rows of a tab, measured from its top: a chamfered row, the
     * line joining the border tips, a clear row, the channel accent,
     * another clear row, and then the interior — as tall as the icon it
     * holds, so the label sits level with it whatever either measures.
     */
    private static final int TIP_ROW = 1;
    private static final int ACCENT_ROW = TIP_ROW + 2;
    private static final int INTERIOR_TOP = ACCENT_ROW + 2;
    private static final int INTERIOR_HEIGHT = ChatChannelIcons.SIZE;
    /** Rows a capital letter takes; what text is centred by. */
    private static final int CAP_HEIGHT = 7;
    /** Seam between neighbouring tabs. */
    private static final int TAB_GAP = 1;
    private static final int PADDING_X = 6;
    /** A dragged tab's ghost never grows past this. */
    private static final int GHOST_LABEL_WIDTH = 120;
    /** Gap between the label and a counter, and between the counters. */
    private static final int COUNTER_GAP = 3;
    /** Hit square of a control inside the selected tab. */
    static final int CONTROL_SIZE = 7;
    private static final int CONTROL_GAP = 2;
    /** Hit square of the lock and restore controls after the row. */
    static final int END_CONTROL_SIZE = 9;
    private static final int END_CONTROL_GAP = 3;
    /** Minimum grip width a row keeps for dragging the window. */
    static final int MIN_GRIP_WIDTH = 12;
    /** Inset of the grip glyph from the row's right edge. */
    /**
     * Clear space kept at the row's right end, which the grip stands
     * against and no control crosses.
     */
    private static final int GRIP_INSET = 3;
    /**
     * Opacity of a tab's interior. The border sprites carry that same
     * interior behind them at this alpha, so the span they enclose has
     * to be filled with it exactly or a tab reads as two tones.
     */
    private static final int TAB_SURFACE_ALPHA = 0xAB;
    /** The line joining a resting tab's border tips. */
    private static final int TIP_RGB =
            LostTalesColors.rgb(LostTalesColors.ROSE_BEIGE);
    /** The same line on a hovered or selected tab. */
    private static final int TIP_LIT_RGB =
            LostTalesColors.rgb(LostTalesColors.IVORY);
    private static final int PING_COUNTER_RGB =
            LostTalesColors.rgb(LostTalesColors.SALMON);
    private static final int UNREAD_COUNTER_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);
    private static final int DROP_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);

    private List<Tab> cachedTabs = Collections.emptyList();
    private List<ChatTab> cachedChannels = Collections.emptyList();
    private ChatTab cachedSelected;
    private final Map<ChatTab, String> cachedLabels =
            new HashMap<ChatTab, String>();
    private final Map<ChatTab, Integer> cachedPings =
            new HashMap<ChatTab, Integer>();
    private final Map<ChatTab, Integer> cachedOther =
            new HashMap<ChatTab, Integer>();
    private final Map<ChatTab, Boolean> cachedMuted =
            new HashMap<ChatTab, Boolean>();
    private FontRenderer cachedFont;
    /** The end lock's swing; one row, one lock, one state. */
    private final ChatLockAnimation lockAnimation = new ChatLockAnimation();
    private int cachedLeft = Integer.MIN_VALUE;
    private int cachedRight = Integer.MIN_VALUE;
    private boolean cachedShowClose;
    private boolean cachedShowRestore;
    private int cachedClosedUnread;
    /** {@code [n]} after the restore control, or empty. */
    private String restoreBadge = "";
    /** Width of the restore control together with its badge. */
    private int restoreWidth;
    /** Right edge of the last tab, before the end controls. */
    private int tabsRight;
    private int lockX = -1;
    private int restoreX = -1;
    /** Right edge of the end controls; the grip starts here. */
    private int controlsRight;
    private float alphaScale = 1.0F;

    /** What a point in the row resolves to. */
    enum HitKind { TAB, CLOSE, SETTINGS, LOCK, RESTORE, GRIP }

    static final class Hit {
        final HitKind kind;
        final ChatTab tab;

        Hit(HitKind kind, ChatTab tab) {
            this.kind = kind;
            this.tab = tab;
        }
    }

    /** The per-frame inputs of one window's row, filled by the screen. */
    static final class Row {
        List<ChatTab> tabs = Collections.emptyList();
        ChatTab selected;
        /** Resting left edge of the first tab, screen space. */
        int left;
        /** Right limit the row may not cross, screen space. */
        int right;
        /** Screen y the tab bodies stand on. */
        int rowBottom;
        /**
         * The same edge before it was floored to a whole pixel, which is
         * where the strip is really drawn. The clip that keeps the row's
         * shadows off the rule is measured from this.
         */
        double rowBottomExact;
        /** Horizontal motion the whole row is drawn with. */
        int offsetX;
        boolean locked;
        /** Whether a close cross is offered on the selected tab. */
        boolean closable;
        /** Whether the restore control is offered after the row. */
        boolean showRestore;
        /** Unread lines waiting in closed channels, shown after the +. */
        int closedUnread;
        /** Whether this window is the one being dragged right now. */
        boolean moving;
        /** Tab being dragged out of this row, drawn faint; or null. */
        ChatTab dragging;
        /** Insertion index to indicate during a drag, or -1. */
        int dropIndex = -1;
    }

    /** Highest pixel any tab can reach (the selected tab fully lifted). */
    static int rowTop(int rowBottom) {
        return rowBottom - ROW_HEIGHT;
    }

    /** Whether a GUI-space point lies in the row's vertical band. */
    static boolean inRowBand(Row row, int mouseY) {
        return mouseY >= rowTop(row.rowBottom) && mouseY < row.rowBottom;
    }

    /**
     * What lies under a GUI-space point: a tab, one of the selected tab's
     * controls, an end control, the grip, or nothing.
     */
    Hit hitAt(FontRenderer font, Row row, int mouseX, int mouseY) {
        if (!inRowBand(row, mouseY)) {
            return null;
        }
        List<Tab> tabs = layout(font, row);
        int localX = mouseX - row.offsetX;
        if (tabs.isEmpty()) {
            return null;
        }
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (localX < tab.x || localX >= tab.x + tab.width) {
                continue;
            }
            if (tab.closeX >= 0 && localX >= tab.closeX - 1
                    && localX < tab.closeX + CONTROL_SIZE + 1) {
                return new Hit(HitKind.CLOSE, tab.tab);
            }
            if (tab.settingsX >= 0 && localX >= tab.settingsX - 1
                    && localX < tab.settingsX + CONTROL_SIZE + 1) {
                return new Hit(HitKind.SETTINGS, tab.tab);
            }
            return new Hit(HitKind.TAB, tab.tab);
        }
        if (this.lockX >= 0 && localX >= this.lockX
                && localX < this.lockX + END_CONTROL_SIZE) {
            return new Hit(HitKind.LOCK, null);
        }
        if (this.restoreX >= 0 && localX >= this.restoreX
                && localX < this.restoreX + this.restoreWidth) {
            return new Hit(HitKind.RESTORE, null);
        }
        if (localX >= this.controlsRight && localX < row.right) {
            // A locked window's grip is inert: no hover, no tip, no drag.
            return row.locked ? null : new Hit(HitKind.GRIP, null);
        }
        return null;
    }

    /**
     * Whether the point lies on the grip's own glyph rather than
     * anywhere in the empty stretch that also drags the window. The
     * glyph is what the hover highlight and the move tip answer to, so
     * neither follows a pointer resting on the bare strip.
     */
    boolean isOverGripHandle(FontRenderer font, Row row, int mouseX,
                             int mouseY) {
        if (row == null || row.locked || !inRowBand(row, mouseY)) {
            return false;
        }
        layout(font, row);
        if (row.right - this.controlsRight < MIN_GRIP_WIDTH) {
            return false;
        }
        int localX = mouseX - row.offsetX;
        int right = row.right - GRIP_INSET;
        return localX >= right - ChatIconSheet.GRIP.getWidth()
                && localX < right;
    }

    /**
     * Insertion index for a tab dropped at {@code mouseX}, into the
     * row's <em>full</em> tab list: before the first drawn tab whose
     * centre lies right of the pointer, and past everything — trimmed
     * trailing tabs included — right of the drawn run, so a crowded row
     * can still take a tab to its very end.
     */
    int dropIndexAt(FontRenderer font, Row row, int mouseX) {
        List<Tab> tabs = layout(font, row);
        int localX = mouseX - row.offsetX;
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (localX < tab.x + tab.width / 2) {
                return tab.rowIndex;
            }
        }
        return row.tabs.size();
    }

    /**
     * Draws the row. {@code offsetX} and {@code alphaScale} are the
     * window's own motion, so tabs and lines arrive and fade together;
     * the rectangle registered with {@code regions} is the one painted.
     */
    void draw(FontRenderer font, ChatPointerRegions regions, Row row,
              int mouseX, int mouseY, float alphaScale) {
        List<Tab> tabs = layout(font, row);
        if (tabs.isEmpty()) {
            return;
        }
        this.alphaScale = Math.max(0.0F, Math.min(1.0F, alphaScale));
        Hit hovered = hitAt(font, row, mouseX, mouseY);
        int bottom = row.rowBottom;
        // The window's title strip, ending exactly on the rule row:
        // drawn outside the clip below, so the inward-rounded cut can
        // never open a bright seam — anything a tab gives up to the cut
        // shows this same surface.
        Gui.drawRect(row.offsetX + row.left - 2, rowTop(bottom),
                row.offsetX + row.right + 2, bottom - 1,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_RGB,
                        scaled(LostTalesChatVisualStyle.SURFACE_ALPHA)));
        // Everything else the strip draws stops short of the rule it
        // ends on: a sprite's shadow falls a pixel down and right, and
        // the rule is a hairline the window is bounded by, not something
        // to cast onto. Inward, so not one display pixel of a tab or a
        // shadow ever lands on the rule row; the rule itself is drawn
        // after the clip.
        boolean clipped = LostTalesChatOverlayRenderer.beginVerticalClip(
                Minecraft.getMinecraft(), Double.NaN,
                row.rowBottomExact - 1.0D, true);
        try {
            // A window being dragged holds the grip's highlight wherever the
            // pointer has gone, the way a control keeps its pressed look.
            drawGrip(row.offsetX + this.controlsRight, row.offsetX + row.right,
                    bottom, row.moving
                            || isOverGripHandle(font, row, mouseX, mouseY));
            Tab selectedTab = null;
            for (int index = 0; index < tabs.size(); index++) {
                Tab tab = tabs.get(index);
                if (tab.tab.equals(row.selected)) {
                    selectedTab = tab;
                    continue;
                }
                drawTab(font, tab, row, bottom, hovered, false);
            }
            if (selectedTab != null) {
                drawTab(font, selectedTab, row, bottom, hovered, true);
            }
            if (row.dropIndex >= 0) {
                drawDropIndicator(tabs, row, bottom);
            }
            // The end controls sit centred in the strip, like the selected
            // tab's label; the badge's caps share that centre.
            if (this.lockX >= 0) {
                drawLock(row.offsetX + this.lockX, bottom, row.locked,
                        hovered != null && hovered.kind == HitKind.LOCK);
            }
            if (this.restoreX >= 0) {
                boolean restoreHovered = hovered != null
                        && hovered.kind == HitKind.RESTORE;
                drawRestore(row.offsetX + this.restoreX, bottom, restoreHovered);
                if (this.restoreBadge.length() > 0) {
                    // What waits behind the +, in the tabs' unread honey.
                    LostTalesChatVisualStyle.drawColored(font, this.restoreBadge,
                            row.offsetX + this.restoreX + END_CONTROL_SIZE
                                    + COUNTER_GAP,
                            centredInStrip(bottom, CAP_HEIGHT),
                            UNREAD_COUNTER_RGB, scaled(0xFF));
                }
            }
            // Last of everything the strip holds, so the tabs, the end
            // controls and the bare stretch between them all sink into
            // the rule together rather than each on its own. It is the
            // very shade the window hangs from its own top edge, hung
            // the other way up — same routine, so the two sides of the
            // rule cannot drift apart.
            LostTalesChatOverlayRenderer.drawEdgeFade(
                    row.offsetX + row.left - 2, row.offsetX + row.right + 2,
                    bottom - 1, rowTop(bottom),
                    LostTalesChatOverlayRenderer.TOP_EDGE_FADE_HEIGHT,
                    scaled(LostTalesChatOverlayRenderer.EDGE_FADE_ALPHA));
        } finally {
            LostTalesChatOverlayRenderer.endVerticalClip(clipped);
        }
        // The window's top rule: the strip's last row, over the tabs
        // and controls, the exact width of the strip. It stands on one
        // row of the history's own backdrop, as the bottom rule does, so
        // both edges of a window read the same way.
        LostTalesChatOverlayRenderer.drawBackdropRow(
                row.offsetX + row.left - 2, bottom - 1,
                row.offsetX + row.right + 2, bottom,
                scaled(LostTalesChatOverlayRenderer.backdropRowAlpha(
                        Minecraft.getMinecraft())));
        LostTalesChatOverlayRenderer.drawRule(row.offsetX + row.left - 2,
                row.offsetX + row.right + 2, bottom - 1, bottom,
                scaled(0xFF));
        regions.addWindow(row.offsetX + row.left - 2, rowTop(bottom),
                row.offsetX + row.right + 2, bottom);
    }

    /** A dragged tab following the pointer, above everything else. */
    void drawGhost(FontRenderer font, ChatTab tab, int x, int y) {
        if (font == null || tab == null) {
            return;
        }
        String label = LostTalesSkyrimUiStyle.trimToWidth(font,
                ClientChatChannelState.displayName(tab), GHOST_LABEL_WIDTH);
        ChatEmoji icon = ChatChannelIcons.iconOf(tab);
        int iconWidth = icon == null ? 0 : ChatChannelIcons.SIZE
                + ChatChannelIcons.GAP;
        int width = font.getStringWidth(label) + PADDING_X * 2 + iconWidth;
        int top = y - HEIGHT / 2;
        // The shape a hovered tab wears, so what follows the pointer is
        // plainly the tab that left the row.
        drawTabShape(x, x + width, top, false, true, 0xFF,
                TAB_SURFACE_ALPHA);
        drawAccent(x + BORDER_WIDTH, x + width - BORDER_WIDTH,
                top + ACCENT_ROW,
                ClientChatChannelState.displayColor(tab), 0xFF);
        int interiorTop = top + INTERIOR_TOP;
        if (icon != null) {
            ChatChannelIcons.draw(Minecraft.getMinecraft(), tab,
                    x + PADDING_X,
                    centredInInterior(interiorTop, ChatChannelIcons.SIZE),
                    230);
        }
        LostTalesChatVisualStyle.drawPlain(font, label,
                x + PADDING_X + iconWidth,
                centredInInterior(interiorTop, CAP_HEIGHT), 230);
    }

    /**
     * Where a run of {@code height} rows sits in a tab's interior,
     * centred on the icon that fills it. The icon is an even number of
     * rows and the caps and the control glyphs are odd, so neither can
     * land on its centre; the remainder is spent above the run, which
     * is the half they read as level with the icon from.
     */
    private static int centredInInterior(int interiorTop, int height) {
        return interiorTop + (INTERIOR_HEIGHT - height + 1) / 2;
    }

    private int scaled(int alpha) {
        int result = Math.round(alpha * this.alphaScale);
        return result < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA
                ? 0 : result;
    }

    private void drawTab(FontRenderer font, Tab tab, Row row,
                         int rowBottom, Hit hovered, boolean selected) {
        boolean hoveredTab = hovered != null && tab.tab.equals(hovered.tab);
        boolean faint = tab.tab.equals(row.dragging);
        // Selection is a hard cut: the picked tab is forward at once,
        // nothing sweeps across the tabs between.
        int lift = selected ? LIFT : 0;
        int top = rowBottom - HEIGHT - lift;
        // A tab draws its border pieces whole and stops one row short
        // of the strip's last row, which is the window's top rule; the
        // selected one rises out of the row, it does not sink.
        int left = row.offsetX + tab.x;
        int right = left + tab.width;
        float dim = faint ? 0.4F : 1.0F;
        // The border artwork is opaque and carries the interior with it;
        // the span between the pieces is filled to match.
        drawTabShape(left, right, top, selected, hoveredTab,
                scaled(Math.round(0xFF * dim)),
                scaled(Math.round(TAB_SURFACE_ALPHA * dim)));
        // Channel accent across the tab's face, clear of the border
        // artwork on every side: brighter when forward, full at the
        // centre and gone at the ends like the edge rules.
        int accentAlpha = scaled(Math.round((selected ? 0xFF : 0xA0) * dim));
        drawAccent(left + BORDER_WIDTH, right - BORDER_WIDTH,
                top + ACCENT_ROW,
                ClientChatChannelState.displayColor(tab.tab), accentAlpha);
        // Text is always at full opacity; a muted tab is told by its
        // italics alone.
        int textAlpha = scaled(Math.round(255 * dim));
        // Everything inside the tab stands in the interior, one clear
        // row under the accent: the icon at its own size, the label's
        // caps and the controls level with it. The selected tab's lift
        // carries them up with it rather than re-centring them.
        int interiorTop = top + INTERIOR_TOP;
        int textX = left + PADDING_X;
        int textY = centredInInterior(interiorTop, CAP_HEIGHT);
        if (tab.icon != null) {
            ChatChannelIcons.draw(Minecraft.getMinecraft(), tab.tab, textX,
                    centredInInterior(interiorTop, ChatChannelIcons.SIZE),
                    textAlpha);
            textX += ChatChannelIcons.SIZE + ChatChannelIcons.GAP;
        }
        LostTalesChatVisualStyle.drawPlain(font,
                tab.muted ? "§o" + tab.label : tab.label,
                textX, textY, textAlpha);
        textX += tab.labelWidth;
        if (tab.pingText.length() > 0) {
            textX += COUNTER_GAP;
            LostTalesChatVisualStyle.drawColored(font, tab.pingText,
                    textX, textY, PING_COUNTER_RGB, textAlpha);
            textX += tab.pingWidth;
        }
        if (tab.otherText.length() > 0) {
            textX += COUNTER_GAP;
            LostTalesChatVisualStyle.drawColored(font, tab.otherText,
                    textX, textY, UNREAD_COUNTER_RGB, textAlpha);
        }
        // The controls are never dimmed with the label: only the
        // window's own fade and a dragged tab's faintness reach them.
        // Their hit squares are centred in the interior like the caps,
        // and each sprite is centred in its square in turn.
        int controlTop = centredInInterior(interiorTop, CONTROL_SIZE);
        int controlAlpha = scaled(Math.round(0xFF * dim));
        if (tab.settingsX >= 0) {
            drawCog(row.offsetX + tab.settingsX, controlTop,
                    hovered != null && hovered.tab != null
                            && hovered.kind == HitKind.SETTINGS
                            && hovered.tab.equals(tab.tab),
                    controlAlpha);
        }
        if (tab.closeX >= 0) {
            drawClose(row.offsetX + tab.closeX, controlTop,
                    hovered != null && hovered.tab != null
                            && hovered.kind == HitKind.CLOSE
                            && hovered.tab.equals(tab.tab),
                    controlAlpha);
        }
    }

    /**
     * A tab's shape: the interior filling the span its two border pieces
     * enclose, the line joining their tips, and the pieces themselves.
     * The pieces are drawn at their own size — a longer label widens the
     * span, never the artwork — and the selected pair stands two rows
     * taller than the resting ones, which is the lift its caller has
     * already made room for. Everything that draws a tab comes through
     * here, so the row and a dragged tab's ghost cannot drift apart.
     */
    private static void drawTabShape(int left, int right, int top,
                                     boolean selected, boolean hovered,
                                     int spriteAlpha, int interiorAlpha) {
        ChatIconSheet leftPiece;
        ChatIconSheet rightPiece;
        int interiorRgb;
        int tipRgb;
        if (selected) {
            leftPiece = ChatIconSheet.TAB_SELECTED_LEFT;
            rightPiece = ChatIconSheet.TAB_SELECTED_RIGHT;
            interiorRgb = LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB;
            tipRgb = TIP_LIT_RGB;
        } else if (hovered) {
            leftPiece = ChatIconSheet.TAB_HOVER_LEFT;
            rightPiece = ChatIconSheet.TAB_HOVER_RIGHT;
            interiorRgb = LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB;
            tipRgb = TIP_LIT_RGB;
        } else {
            leftPiece = ChatIconSheet.TAB_LEFT;
            rightPiece = ChatIconSheet.TAB_RIGHT;
            interiorRgb = LostTalesChatVisualStyle.SURFACE_RGB;
            tipRgb = TIP_RGB;
        }
        int spanLeft = left + BORDER_WIDTH;
        int spanRight = right - BORDER_WIDTH;
        int height = leftPiece.getHeight();
        if (spanRight > spanLeft) {
            // As deep as the pieces themselves, so the span cannot end
            // short of the artwork it stands between.
            Gui.drawRect(spanLeft, top, spanRight, top + height,
                    LostTalesChatVisualStyle.argb(interiorRgb, interiorAlpha));
            // The tips are the pieces' innermost lit columns, one pixel
            // outside the span, so the line meets both without a gap.
            Gui.drawRect(spanLeft, top + TIP_ROW, spanRight,
                    top + TIP_ROW + 1,
                    LostTalesChatVisualStyle.argb(tipRgb, spriteAlpha));
        }
        leftPiece.draw(left, top, spriteAlpha);
        rightPiece.draw(spanRight, top, spriteAlpha);
    }

    /**
     * A one-pixel band of the channel's colour from {@code left} to
     * {@code right}: the given alpha at the centre, nothing at either
     * end, the same profile as the window's edge rules.
     */
    private static void drawAccent(int left, int right, int y, int rgb,
                                   int alpha) {
        if (right <= left || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        float centre = (left + right) / 2.0F;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        // Same winding as the chat backdrop: the GUI pass culls back faces.
        tessellator.setColorRGBA_I(rgb, alpha);
        tessellator.addVertex(centre, y + 1, 0.0D);
        tessellator.addVertex(centre, y, 0.0D);
        tessellator.setColorRGBA_I(rgb, 0);
        tessellator.addVertex(left, y, 0.0D);
        tessellator.addVertex(left, y + 1, 0.0D);
        tessellator.setColorRGBA_I(rgb, 0);
        tessellator.addVertex(right, y + 1, 0.0D);
        tessellator.addVertex(right, y, 0.0D);
        tessellator.setColorRGBA_I(rgb, alpha);
        tessellator.addVertex(centre, y, 0.0D);
        tessellator.addVertex(centre, y + 1, 0.0D);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * The insertion bar for a full-list {@code dropIndex}: before the
     * drawn tab holding that index, or after the run's last tab when the
     * index lies past everything drawn.
     */
    private void drawDropIndicator(List<Tab> tabs, Row row, int rowBottom) {
        Tab last = tabs.get(tabs.size() - 1);
        int x = last.x + last.width;
        for (int index = 0; index < tabs.size(); index++) {
            if (tabs.get(index).rowIndex >= row.dropIndex) {
                x = tabs.get(index).x;
                break;
            }
        }
        Gui.drawRect(row.offsetX + x - 1, rowTop(rowBottom),
                row.offsetX + x + 1, rowBottom,
                LostTalesChatVisualStyle.argb(DROP_RGB, scaled(0xFF)));
    }

    /* The controls are the sheet's sprites, drawn 1:1 and centred in
       their hit squares; hovering swaps in the sprite's hover state. */

    private void drawClose(int x, int y, boolean hovered, int alpha) {
        ChatIconSheet icon = hovered
                ? ChatIconSheet.CLOSE_HOVER : ChatIconSheet.CLOSE;
        icon.drawWithShadow(x + (CONTROL_SIZE - icon.getWidth()) / 2,
                y + (CONTROL_SIZE - icon.getHeight()) / 2, alpha);
    }

    private void drawCog(int x, int y, boolean hovered, int alpha) {
        ChatIconSheet icon = hovered
                ? ChatIconSheet.COG_HOVER : ChatIconSheet.COG;
        icon.drawWithShadow(x + (CONTROL_SIZE - icon.getWidth()) / 2,
                y + (CONTROL_SIZE - icon.getHeight()) / 2, alpha);
    }

    /**
     * The y that centres a sprite of the given height in the strip above
     * the window's top rule, which takes the strip's last row.
     */
    /**
     * Where a control of {@code height} pixels stands to sit in the
     * middle of the strip. The strip's last row is the window's top
     * rule, so the rows a control may use are one fewer; an odd
     * remainder is spent above it, which is the side the selected tab's
     * lift takes room from.
     */
    private static int centredInStrip(int rowBottom, int height) {
        return rowTop(rowBottom) + Math.round(
                (ROW_HEIGHT - 1 - height) / 2.0F);
    }

    /**
     * The lock's body keeps its place whichever way the shackle goes:
     * every frame starts at the same left edge and stands on the same
     * row, the open shackle reaching past the square into the gap.
     */
    private void drawLock(int x, int rowBottom, boolean locked,
                          boolean hovered) {
        this.lockAnimation.draw(x + 1,
                centredInStrip(rowBottom, ChatLockAnimation.HEIGHT),
                locked, hovered, scaled(0xFF));
    }

    private void drawRestore(int x, int rowBottom, boolean hovered) {
        ChatIconSheet icon = hovered
                ? ChatIconSheet.PLUS_HOVER : ChatIconSheet.PLUS;
        icon.drawWithShadow(x + (END_CONTROL_SIZE - icon.getWidth()) / 2,
                centredInStrip(rowBottom, icon.getHeight()), scaled(0xFF));
    }

    /** The drag handle at the strip's right end, where a title bar keeps it. */
    private void drawGrip(int left, int right, int rowBottom,
                          boolean hovered) {
        if (right - left < MIN_GRIP_WIDTH) {
            return;
        }
        ChatIconSheet icon = hovered
                ? ChatIconSheet.GRIP_HOVER : ChatIconSheet.GRIP;
        icon.drawWithShadow(right - GRIP_INSET - icon.getWidth(),
                centredInStrip(rowBottom, icon.getHeight()), scaled(0xFF));
    }

    /**
     * Resting geometry for the row, cached until the font, limits, tab
     * list, selection, a label, a counter, a mute or the control set
     * changes. The end controls keep their room at the right; the tabs
     * share what is left: whole, with controls on every tab, while that
     * fits, else with controls on the selected tab only and labels
     * shortened, widest first, to one common width. A tab that still
     * finds no room is left out of the row rather than crossing the
     * limit; it stays open and reachable by cycling.
     */
    List<Tab> layout(FontRenderer font, Row row) {
        if (font == null || row == null || row.tabs == null
                || row.tabs.isEmpty()) {
            this.cachedTabs = Collections.emptyList();
            this.lockX = -1;
            this.restoreX = -1;
            this.tabsRight = row == null ? 0 : row.left;
            this.controlsRight = this.tabsRight;
            return this.cachedTabs;
        }
        boolean showClose = row.closable;
        if (isLayoutCurrent(font, row, showClose)) {
            return this.cachedTabs;
        }
        List<ChatTab> channels = row.tabs;
        int count = channels.size();
        String badge = row.showRestore
                ? ClientChatChannelViews.counterText(row.closedUnread) : "";
        int badgeWidth = badge.length() == 0 ? 0
                : COUNTER_GAP + font.getStringWidth(badge);
        int endControls = END_CONTROL_GAP + END_CONTROL_SIZE + END_CONTROL_GAP
                + (row.showRestore
                        ? END_CONTROL_SIZE + badgeWidth + END_CONTROL_GAP : 0)
                + MIN_GRIP_WIDTH;
        int limit = row.right - endControls;
        int available = limit - row.left - TAB_GAP * (count - 1);
        int controls = controlsWidth(showClose);
        int[] labelWidths = new int[count];
        int[] counters = new int[count];
        int natural = 0;
        for (int index = 0; index < count; index++) {
            ChatTab channel = channels.get(index);
            labelWidths[index] = font.getStringWidth(
                    this.cachedLabels.get(channel));
            counters[index] = countersWidth(
                    font.getStringWidth(ClientChatChannelViews.counterText(
                            count(this.cachedPings, channel))),
                    font.getStringWidth(ClientChatChannelViews.counterText(
                            count(this.cachedOther, channel))));
            natural += PADDING_X * 2 + labelWidths[index] + counters[index]
                    + iconWidth(channels.get(index)) + controls;
        }
        boolean controlsEverywhere = natural <= available;
        int selectedIndex = 0;
        for (int index = 0; index < count; index++) {
            if (channels.get(index).equals(row.selected)) {
                selectedIndex = index;
            }
        }
        int[] labelRoom = labelWidths.clone();
        // Counters are the first thing a crowded row gives up, and only
        // if shortening every label was not enough.
        boolean showCounters = true;
        int first = 0;
        int last = count - 1;
        if (!controlsEverywhere) {
            int[] fixed = fixedWidths(channels, counters, controls,
                    selectedIndex, true);
            if (total(fixed, 0, count - 1) > available) {
                showCounters = false;
                fixed = fixedWidths(channels, counters, controls,
                        selectedIndex, false);
            }
            // Only when even bare tabs do not fit does the row show
            // fewer of them, and then it keeps a run around the tab in
            // front rather than whichever ones happen to be leftmost —
            // so a tab does not come and go as the selection moves.
            while (first <= last && total(fixed, first, last)
                    > available + TAB_GAP * (count - (last - first + 1))) {
                if (last > selectedIndex) {
                    last--;
                } else if (first < selectedIndex) {
                    first++;
                } else {
                    break;
                }
            }
            // The tab in front keeps its whole name — it is the one the
            // player is reading — and the rest share what is left, so a
            // crowded row shortens around it instead of hiding it.
            // Seams are only needed between the tabs the row shows.
            int room = available + TAB_GAP * (count - (last - first + 1))
                    - total(fixed, first, last);
            capLabelsAround(labelRoom, room, selectedIndex - first,
                    first, last);
        }
        List<Tab> tabs = new ArrayList<Tab>(count);
        int x = row.left;
        for (int index = first; index <= last; index++) {
            ChatTab channel = channels.get(index);
            boolean selected = channel.equals(row.selected);
            String label = LostTalesSkyrimUiStyle.trimToWidth(font,
                    this.cachedLabels.get(channel), labelRoom[index]);
            String pingText = showCounters
                    ? ClientChatChannelViews.counterText(
                            count(this.cachedPings, channel)) : "";
            String otherText = showCounters
                    ? ClientChatChannelViews.counterText(
                            count(this.cachedOther, channel)) : "";
            int labelWidth = font.getStringWidth(label);
            int pingWidth = font.getStringWidth(pingText);
            int otherWidth = font.getStringWidth(otherText);
            ChatEmoji icon = ChatChannelIcons.iconOf(channel);
            int width = labelWidth + PADDING_X * 2 + iconWidth(channel)
                    + countersWidth(pingWidth, otherWidth);
            int settingsX = -1;
            int closeX = -1;
            if (controlsEverywhere || selected) {
                settingsX = x + width - PADDING_X + CONTROL_GAP;
                width += CONTROL_GAP + CONTROL_SIZE;
                if (showClose) {
                    closeX = x + width - PADDING_X + CONTROL_GAP;
                    width += CONTROL_GAP + CONTROL_SIZE;
                }
            }
            Boolean muted = this.cachedMuted.get(channel);
            tabs.add(new Tab(channel, index, icon, label, labelWidth,
                    pingText, pingWidth, otherText, otherWidth, x, width,
                    settingsX, closeX, muted != null && muted.booleanValue()));
            x += width + TAB_GAP;
        }
        this.tabsRight = tabs.isEmpty() ? row.left : x - TAB_GAP;
        int controlX = this.tabsRight + END_CONTROL_GAP;
        this.lockX = controlX;
        controlX += END_CONTROL_SIZE + END_CONTROL_GAP;
        if (row.showRestore) {
            this.restoreX = controlX;
            this.restoreBadge = badge;
            this.restoreWidth = END_CONTROL_SIZE + badgeWidth;
            controlX += this.restoreWidth + END_CONTROL_GAP;
        } else {
            this.restoreX = -1;
            this.restoreBadge = "";
            this.restoreWidth = 0;
        }
        this.controlsRight = controlX;
        this.cachedTabs = Collections.unmodifiableList(tabs);
        this.cachedChannels = new ArrayList<ChatTab>(channels);
        this.cachedSelected = row.selected;
        this.cachedFont = font;
        this.cachedLeft = row.left;
        this.cachedRight = row.right;
        this.cachedShowClose = showClose;
        this.cachedShowRestore = row.showRestore;
        this.cachedClosedUnread = row.closedUnread;
        return this.cachedTabs;
    }

    /**
     * What a tab takes before its label: its padding, its icon, its
     * counters while the row still shows them, and the controls of the
     * tab in front.
     */
    private static int[] fixedWidths(List<ChatTab> channels, int[] counters,
                                     int controls, int selectedIndex,
                                     boolean withCounters) {
        int[] fixed = new int[channels.size()];
        for (int index = 0; index < fixed.length; index++) {
            fixed[index] = PADDING_X * 2 + iconWidth(channels.get(index))
                    + (withCounters ? counters[index] : 0)
                    + (index == selectedIndex ? controls : 0);
        }
        return fixed;
    }

    private static int total(int[] widths, int first, int last) {
        int sum = 0;
        for (int index = Math.max(0, first);
             index <= Math.min(widths.length - 1, last); index++) {
            sum += widths[index];
        }
        return sum;
    }

    /**
     * Shortens the widest labels as {@link #capLabels} does, but gives
     * the label at {@code keptIndex} first claim on the room: it keeps
     * its whole width while any of it fits, and the others are capped
     * against what remains. A negative index caps every label alike.
     */
    static void capLabelsAround(int[] widths, int room, int keptIndex) {
        capLabelsAround(widths, room, keptIndex, 0, widths.length - 1);
    }

    /** As above, over the run of labels the row actually shows. */
    static void capLabelsAround(int[] widths, int room, int keptOffset,
                                int first, int last) {
        int from = Math.max(0, first);
        int to = Math.min(widths.length - 1, last);
        if (from > to) {
            return;
        }
        int span = to - from + 1;
        int[] shown = new int[span];
        for (int index = 0; index < span; index++) {
            shown[index] = widths[from + index];
        }
        capShownLabels(shown, room, keptOffset);
        for (int index = 0; index < span; index++) {
            widths[from + index] = shown[index];
        }
    }

    private static void capShownLabels(int[] widths, int room,
                                       int keptIndex) {
        if (keptIndex < 0 || keptIndex >= widths.length) {
            capLabels(widths, room);
            return;
        }
        int kept = Math.max(0, Math.min(widths[keptIndex], room));
        int[] others = new int[widths.length - 1];
        for (int index = 0, target = 0; index < widths.length; index++) {
            if (index != keptIndex) {
                others[target++] = widths[index];
            }
        }
        capLabels(others, room - kept);
        for (int index = 0, source = 0; index < widths.length; index++) {
            widths[index] = index == keptIndex ? kept : others[source++];
        }
    }

    /**
     * Shortens the widest labels to one common cap, the largest at which
     * they all fit {@code room} together; labels already narrower keep
     * their width. With no room at all every label goes to nothing.
     */
    static void capLabels(int[] widths, int room) {
        int widest = 0;
        for (int index = 0; index < widths.length; index++) {
            widest = Math.max(widest, widths[index]);
        }
        for (int cap = widest; cap >= 0; cap--) {
            int total = 0;
            for (int index = 0; index < widths.length; index++) {
                total += Math.min(widths[index], cap);
            }
            if (total <= room) {
                for (int index = 0; index < widths.length; index++) {
                    widths[index] = Math.min(widths[index], cap);
                }
                return;
            }
        }
        for (int index = 0; index < widths.length; index++) {
            widths[index] = 0;
        }
    }

    /** Room the tab's icon takes before the label, with its gap. */
    private static int iconWidth(ChatTab tab) {
        return ChatChannelIcons.iconOf(tab) == null ? 0
                : ChatChannelIcons.SIZE + ChatChannelIcons.GAP;
    }

    private static int controlsWidth(boolean showClose) {
        return CONTROL_GAP + CONTROL_SIZE
                + (showClose ? CONTROL_GAP + CONTROL_SIZE : 0);
    }

    private static int count(Map<ChatTab, Integer> counters, ChatTab tab) {
        Integer value = counters.get(tab);
        return value == null ? 0 : value.intValue();
    }

    /** Refreshes the cache keys and reports whether the layout still holds. */
    private boolean isLayoutCurrent(FontRenderer font, Row row,
                                    boolean showClose) {
        boolean current = font == this.cachedFont
                && row.left == this.cachedLeft
                && row.right == this.cachedRight
                && (row.selected == null ? this.cachedSelected == null
                        : row.selected.equals(this.cachedSelected))
                && showClose == this.cachedShowClose
                && row.showRestore == this.cachedShowRestore
                && row.closedUnread == this.cachedClosedUnread
                && row.tabs.equals(this.cachedChannels);
        for (int index = 0; index < row.tabs.size(); index++) {
            ChatTab tab = row.tabs.get(index);
            String label = ClientChatChannelState.displayName(tab);
            int pings = ClientChatChannelViews.unreadPingCount(tab);
            int other = ClientChatChannelViews.unreadOtherCount(tab);
            boolean muted = ChatWindowLayout.isMuted(tab);
            Boolean cachedMute = this.cachedMuted.get(tab);
            if (!label.equals(this.cachedLabels.get(tab))
                    || pings != count(this.cachedPings, tab)
                    || other != count(this.cachedOther, tab)
                    || cachedMute == null
                    || muted != cachedMute.booleanValue()) {
                this.cachedLabels.put(tab, label);
                this.cachedPings.put(tab, Integer.valueOf(pings));
                this.cachedOther.put(tab, Integer.valueOf(other));
                this.cachedMuted.put(tab, Boolean.valueOf(muted));
                current = false;
            }
        }
        return current;
    }

    private static int countersWidth(int pingWidth, int otherWidth) {
        return (pingWidth > 0 ? COUNTER_GAP + pingWidth : 0)
                + (otherWidth > 0 ? COUNTER_GAP + otherWidth : 0);
    }

    /**
     * Whether the window's row is already shortening its tab names: the
     * same width model {@link #layout} draws with — the end controls'
     * reserved room, each tab's padding, icon and counters (given up
     * first, exactly as the layout gives them up), the controls the
     * crowded row keeps on the selected tab alone — measured against
     * the window's own width. This is what the auto-open policy asks
     * before filing a new tab into a window: a row whose names are
     * whole takes the tab, one already ellipsizing is full and the
     * channel opens elsewhere. The restore control's room is always
     * counted, so the answer does not flap as the {@code +} comes and
     * goes. Without a renderer to measure with the answer is "not
     * crowded", which keeps the layout usable headlessly.
     */
    static boolean rowIsCrowded(Minecraft minecraft, ChatWindow window) {
        if (minecraft == null || minecraft.fontRenderer == null
                || window == null) {
            return false;
        }
        FontRenderer font = minecraft.fontRenderer;
        List<ChatTab> tabs = ChatWindowFrame.visibleTabs(window);
        if (tabs.isEmpty()) {
            return false;
        }
        net.minecraft.client.gui.ScaledResolution resolution =
                new net.minecraft.client.gui.ScaledResolution(minecraft,
                        minecraft.displayWidth, minecraft.displayHeight);
        ChatWindowPlacement.Box box = ChatWindowPlacement.windowBounds(
                window, minecraft, resolution.getScaledWidth(),
                resolution.getScaledHeight());
        // The row spans the window minus the two-pixel insets the screen
        // lays it out with.
        int rowWidth = box.width - 4;
        int endControls = END_CONTROL_GAP + END_CONTROL_SIZE
                + END_CONTROL_GAP + END_CONTROL_SIZE + END_CONTROL_GAP
                + MIN_GRIP_WIDTH;
        int available = rowWidth - endControls
                - TAB_GAP * (tabs.size() - 1);
        int fixedWithCounters = controlsWidth(true);
        int fixedWithout = controlsWidth(true);
        int labels = 0;
        for (int index = 0; index < tabs.size(); index++) {
            ChatTab tab = tabs.get(index);
            int base = PADDING_X * 2 + iconWidth(tab);
            fixedWithCounters += base + countersWidth(
                    font.getStringWidth(
                            ClientChatChannelViews.counterText(
                                    ClientChatChannelViews
                                            .unreadPingCount(tab))),
                    font.getStringWidth(
                            ClientChatChannelViews.counterText(
                                    ClientChatChannelViews
                                            .unreadOtherCount(tab))));
            fixedWithout += base;
            labels += font.getStringWidth(
                    ClientChatChannelState.displayName(tab));
        }
        // Counters go before labels shorten, so a row that only had to
        // give them up still counts as whole-named.
        int fixed = fixedWithCounters > available
                ? fixedWithout : fixedWithCounters;
        return labels > available - fixed;
    }

    static final class Tab {
        final ChatTab tab;
        /** The tab's index in the row's full list; a trimmed run skips some. */
        final int rowIndex;
        /** The channel's emoji before the label, or null. */
        final ChatEmoji icon;
        final String label;
        final int labelWidth;
        /** {@code [p]} unread pings, or empty. */
        final String pingText;
        final int pingWidth;
        /** {@code [x]} other unread lines, or empty. */
        final String otherText;
        final int otherWidth;
        /** Resting left edge before the row's horizontal motion. */
        final int x;
        final int width;
        /** Resting left edge of the cog, or -1 when the tab has none. */
        final int settingsX;
        /** Resting left edge of the close cross, or -1. */
        final int closeX;
        final boolean muted;

        Tab(ChatTab tab, int rowIndex, ChatEmoji icon, String label,
            int labelWidth, String pingText, int pingWidth, String otherText,
            int otherWidth, int x, int width, int settingsX, int closeX,
            boolean muted) {
            this.tab = tab;
            this.rowIndex = rowIndex;
            this.icon = icon;
            this.label = label;
            this.labelWidth = labelWidth;
            this.pingText = pingText;
            this.pingWidth = pingWidth;
            this.otherText = otherText;
            this.otherWidth = otherWidth;
            this.x = x;
            this.width = width;
            this.settingsX = settingsX;
            this.closeX = closeX;
            this.muted = muted;
        }
    }
}
