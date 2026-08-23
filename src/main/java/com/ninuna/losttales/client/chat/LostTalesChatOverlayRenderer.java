package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

/**
 * Vanilla-compatible chat draw pass with heads and optional time-based entry
 * easing, run once per chat window. Lines are read from vanilla's single
 * history; each window shows the subset its {@link ChatLineFilter} selects
 * — with the chat screen open, its front tab's channel with that channel's
 * own scroll offset from {@link ClientChatChannelViews} and without the
 * channel prefix. With the chat closed the windows are not drawn at all;
 * one feed shows every unmuted channel's messages, fading as
 * vanilla's do, at its own position. While the Lost Tales chat screen is
 * open the screen draws the windows itself, one complete window after
 * another, so the open chat lies above every HUD element and a front
 * window covers the whole of one behind it; the HUD pass then only
 * cancels vanilla's. Every window is drawn inside the box
 * {@link ChatWindowPlacement} gives it, with the opening motion every
 * other Lost Tales screen uses, and records the screen band of every
 * line it draws in its
 * {@link ChatWindowFrame}; all mouse-to-line mapping (hover card,
 * clipboard, component hits, the vanilla hit-test hook) resolves against
 * those recorded bands, so it always matches what is on screen.
 */
final class LostTalesChatOverlayRenderer {
    static final int CHAT_BACKDROP_RGB =
            LostTalesColors.rgb(LostTalesColors.PLUM_BLACK);
    /** Backdrop for lines that @-mention the local player. */
    static final int PING_BACKDROP_RGB =
            LostTalesColors.rgb(LostTalesColors.WINE);
    /**
     * Vertical distance between chat lines. Vanilla uses the 9px font
     * height, which cannot contain a 10px emote sprite; an 11px stride
     * gives the sprite room. Bands are contiguous — each line's backdrop
     * fills the full stride.
     */
    static final int LINE_HEIGHT = 11;
    /**
     * Text baseline offset inside a band. Two rows sit above the glyph
     * caps and two below them, so the 7px cap height is centred in the
     * 11px band; heads (8px at -0.5) land exactly centred as well.
     */
    private static final int TEXT_OFFSET = 9;
    private static final float BACKDROP_FADE_START = 2.0F / 3.0F;
    /**
     * Depth of the shade along the backdrop's top and bottom edges — one
     * line, measured inward from the edge. Independent of
     * {@link #LINE_PADDING}: the padding moves where a fade starts,
     * never how far it reaches.
     */
    private static final int EDGE_FADE_HEIGHT = LINE_HEIGHT;
    /**
     * Backdrop above the topmost line and below the newest one — a third
     * of a line — so the text does not touch the tab row or the window's
     * bottom edge.
     */
    static final int LINE_PADDING = LINE_HEIGHT / 3;
    /** Opacity of an edge fade on the edge it hangs from: a third. */
    private static final int EDGE_FADE_ALPHA = 0x55;
    /** Mesh resolution of an edge fade: rows inward, columns across the
     *  band's width. */
    private static final int EDGE_FADE_ROWS = 8;
    private static final int EDGE_FADE_COLUMNS = 16;
    private static final Field DRAWN_LINES = findField("field_146253_i");

    private LostTalesChatOverlayRenderer() {}

    static boolean draw(Minecraft minecraft) {
        if (minecraft == null || minecraft.ingameGUI == null
                || minecraft.gameSettings.chatVisibility
                == EntityPlayer.EnumChatVisibility.HIDDEN
                || DRAWN_LINES == null) {
            return false;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        try {
            List<ChatLine> drawn = getDrawnLines(chat);
            boolean open = chat.getChatOpen();
            ScaledResolution resolution = new ScaledResolution(minecraft,
                    minecraft.displayWidth, minecraft.displayHeight);
            int screenWidth = resolution.getScaledWidth();
            int screenHeight = resolution.getScaledHeight();
            List<ChatWindow> windows = ChatWindowLayout.windows();
            ChatWindowFrame.prune(windows);
            if (!open) {
                drawFeed(minecraft, chat, drawn, screenWidth, screenHeight);
                return true;
            }
            ChatWindowFrame.feed().drawn = false;
            if (minecraft.currentScreen instanceof LostTalesChatGui) {
                // The screen draws its windows after the HUD, each one
                // whole; vanilla's chat pass is still cancelled here.
                return true;
            }
            // Another chat screen is open: the windows are drawn here,
            // back to front, the window in use over the others.
            LostTalesGuiAnimationSample opening =
                    ClientChatChannelViews.openSample();
            List<ChatWindow> stacked = ChatWindowLayout.stacked();
            for (int index = 0; index < stacked.size(); index++) {
                drawOpenWindow(minecraft, chat, drawn, stacked.get(index),
                        screenWidth, screenHeight, opening);
            }
            return true;
        } catch (IllegalAccessException ignored) {
            return false;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * One window of the open chat, for the chat screen: its history,
     * backdrop and edge shades inside its placement box, its bands
     * recorded for the row the screen draws next. Nothing is drawn, and
     * the frame is marked undrawn, when the history cannot be read.
     */
    static void drawWindowForScreen(Minecraft minecraft, ChatWindow window,
                                    int screenWidth, int screenHeight,
                                    LostTalesGuiAnimationSample opening) {
        if (minecraft == null || minecraft.ingameGUI == null
                || window == null || opening == null) {
            return;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        try {
            drawOpenWindow(minecraft, chat, getDrawnLines(chat), window,
                    screenWidth, screenHeight, opening);
        } catch (IllegalAccessException ignored) {
            ChatWindowFrame.of(window).drawn = false;
        } catch (RuntimeException ignored) {
            ChatWindowFrame.of(window).drawn = false;
        }
    }

    /**
     * Opening the screen brings the history in with the same sampler as
     * every other Lost Tales screen; the tabs follow because they stand
     * on the bands.
     */
    private static void drawOpenWindow(Minecraft minecraft, GuiNewChat chat,
                                       List<ChatLine> drawn,
                                       ChatWindow window, int screenWidth,
                                       int screenHeight,
                                       LostTalesGuiAnimationSample opening) {
        ChatWindowFrame frame = ChatWindowFrame.of(window);
        List<ChatTab> tabs = ChatWindowFrame.visibleTabs(window);
        ChatTab view = ChatWindowFrame.activeTab(window, tabs);
        ChatLineFilter filter = ChatLineFilter.of(view);
        List<ChatLine> lines = ClientChatChannelViews.visibleLines(
                drawn, filter);
        frame.lines = lines;
        frame.view = view;
        if (tabs.isEmpty()) {
            // Nothing the player can see lives here right now.
            frame.drawn = false;
            frame.bands.reset(lines, 0, 1.0F);
            return;
        }
        ChatWindowPlacement.Box box = ChatWindowPlacement.windowBounds(
                window, minecraft, screenWidth, screenHeight);
        // The box says how many lines the window shows, never more than
        // the chat height setting.
        int lineLimit = Math.max(1, Math.min(visibleLineCount(chat),
                box.lines));
        int scroll = view != null
                ? ClientChatChannelViews.getScroll(view, lines.size(),
                        lineLimit)
                : 0;
        float scale = chat.func_146244_h();
        frame.begin(box, scale, opening.getTranslationX(),
                opening.getTranslationY());
        frame.drawn = true;
        // The newest line sits on the baseline; the whole window rides
        // the opening motion, tabs and bar included.
        float originX = (float)box.x + 2.0F * scale
                + opening.getTranslationX();
        float originY = (float)box.baseline() + opening.getTranslationY();
        drawWindow(minecraft, chat, frame, filter, lines, scroll, lineLimit,
                originX, originY, true, opening);
    }

    /**
     * The window's bottom hairline, drawn by the chat screen after the
     * bar so it lies over the edge shade: the row directly under the
     * bottom padding. The top one is the tab row's, drawn with the row
     * as its last pixel row so row and rule never drift apart.
     */
    static void drawBottomRule(Minecraft minecraft, ChatWindowFrame frame,
                               LostTalesGuiAnimationSample opening) {
        if (minecraft == null || minecraft.ingameGUI == null
                || frame == null || !frame.drawn || opening == null) {
            return;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        float scale = chat.func_146244_h();
        int unscaledWidth = MathHelper.ceiling_float_int(
                chat.func_146228_f() / scale);
        float originX = (float)frame.drawnLeft() + 2.0F * scale;
        float originY = (float)(frame.baseline + frame.motionY);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(originX, originY, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            drawEdgeRule(unscaledWidth, LINE_PADDING,
                    Math.round(255.0F * opening.getOpacity()));
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * The closed-chat feed: every unmuted channel's lines, open or
     * closed, as one fading stack at the feed's own position, with the
     * channel prefixes that tell the channels apart. Each window marks
     * itself undrawn so nothing hit-tests against a window that is not
     * on screen.
     */
    private static void drawFeed(Minecraft minecraft, GuiNewChat chat,
                                 List<ChatLine> drawn, int screenWidth,
                                 int screenHeight) {
        List<ChatWindow> windows = ChatWindowLayout.windows();
        for (int index = 0; index < windows.size(); index++) {
            ChatWindowFrame.of(windows.get(index)).drawn = false;
        }
        ChatWindowFrame frame = ChatWindowFrame.feed();
        ChatLineFilter filter = ChatWindowFrame.feedFilter();
        List<ChatLine> lines = ClientChatChannelViews.visibleLines(
                drawn, filter);
        frame.lines = lines;
        frame.view = null;
        ChatWindowPlacement.Box box = ChatWindowPlacement.feedBounds(
                minecraft, screenWidth, screenHeight);
        float scale = chat.func_146244_h();
        frame.begin(box, scale, 0.0F, 0.0F);
        frame.drawn = true;
        drawWindow(minecraft, chat, frame, filter, lines, 0,
                visibleLineCount(chat), (float)box.x + 2.0F * scale,
                (float)box.baseline(), false,
                LostTalesGuiAnimationSample.SETTLED);
    }

    static List<ChatLine> getDrawnLines(GuiNewChat chat)
            throws IllegalAccessException {
        if (chat == null || DRAWN_LINES == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<ChatLine> lines = (List<ChatLine>)DRAWN_LINES.get(chat);
        return lines;
    }

    /** Number of wrapped lines at the head of history with this id. */
    static int countLeadingLines(GuiNewChat chat, int chatLineId) {
        try {
            List<ChatLine> drawn = getDrawnLines(chat);
            int count = 0;
            while (drawn != null && count < drawn.size()
                    && drawn.get(count) != null
                    && drawn.get(count).getChatLineID() == chatLineId) {
                count++;
            }
            return count;
        } catch (IllegalAccessException ignored) {
            return 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    /**
     * The drawn band under a GUI-space point in any window, or null.
     * Resolved against the bands recorded by the last draw; the band
     * carries the line list it indexes into.
     */
    static Band bandAt(Minecraft minecraft, float mouseX, float mouseY) {
        if (minecraft == null || minecraft.ingameGUI == null) {
            return null;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        if (chat == null || !chat.getChatOpen()) {
            return null;
        }
        try {
            List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
            for (int index = 0; index < frames.size(); index++) {
                ChatWindowFrame frame = frames.get(index);
                ChatLineBands bands = frame.bands;
                List<ChatLine> lines = frame.lines;
                if (lines == null || !bands.describes(lines, lines.size())) {
                    continue;
                }
                int band = bands.find(mouseX, mouseY);
                if (band < 0) {
                    continue;
                }
                return new Band(frame, lines, bands.viewIndexOf(band),
                        bands.localX(band, mouseX), bands.topOf(band),
                        bands.bottomOf(band), bands.scale());
            }
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Maps a GUI-space mouse position onto the component under it, using
     * the bands this renderer drew and skipping the components it did not
     * draw. Replaces {@code GuiNewChat.func_146236_a}, whose hardcoded 9px
     * math no longer matches what is on screen.
     */
    static Hit hitAt(Minecraft minecraft, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.ingameGUI == null
                || minecraft.fontRenderer == null) {
            return null;
        }
        Band band = bandAt(minecraft, mouseX, mouseY);
        if (band == null) {
            return null;
        }
        try {
            List<ChatLine> lines = band.lines;
            if (lines == null || band.viewIndex >= lines.size()
                    || lines.get(band.viewIndex) == null) {
                return null;
            }
            IChatComponent lineRoot =
                    lines.get(band.viewIndex).func_151461_a();
            int cursor = 0;
            for (Object value : lineRoot) {
                if (!(value instanceof IChatComponent)) {
                    continue;
                }
                IChatComponent part = (IChatComponent)value;
                if (ChatChannelPrefixMarker.isHidden(part, true)) {
                    continue;
                }
                cursor += LostTalesChatVisualStyle.partWidth(
                        minecraft.fontRenderer, part, true);
                if (band.localX < cursor) {
                    return new Hit(part, lineRoot);
                }
            }
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** A clicked component together with the wrapped line that holds it. */
    static final class Hit {
        final IChatComponent component;
        final IChatComponent line;

        private Hit(IChatComponent component, IChatComponent line) {
            this.component = component;
            this.line = line;
        }
    }

    /** A drawn line band with the pointer already mapped into text space. */
    static final class Band {
        final ChatWindowFrame frame;
        /** The view's line list the band indexes into. */
        final List<ChatLine> lines;
        /** Index into {@link #lines}. */
        final int viewIndex;
        /** Pointer x in the line's own unscaled text space. */
        final float localX;
        final float top;
        final float bottom;
        final float scale;

        private Band(ChatWindowFrame frame, List<ChatLine> lines,
                     int viewIndex, float localX, float top, float bottom,
                     float scale) {
            this.frame = frame;
            this.lines = lines;
            this.viewIndex = viewIndex;
            this.localX = localX;
            this.top = top;
            this.bottom = bottom;
            this.scale = scale;
        }
    }

    /**
     * Right edge (GUI coordinates) of the history's backdrop band before
     * its fade: the chat width setting at the chat scale, plus the band's
     * own margins, as drawn from the resting origin.
     */
    static int historyRight(GuiNewChat chat) {
        if (chat == null) {
            return 0;
        }
        float scale = chat.func_146244_h();
        int unscaledWidth = MathHelper.ceiling_float_int(
                chat.func_146228_f() / scale);
        return Math.round(2.0F + (unscaledWidth + 4) * scale);
    }

    /** Lines that fit the user's configured chat pixel height at 11px. */
    static int visibleLineCount(GuiNewChat chat) {
        return Math.max(1, chat.func_146232_i() * 9 / LINE_HEIGHT);
    }

    private static void drawWindow(
            Minecraft minecraft, GuiNewChat chat, ChatWindowFrame frame,
            ChatLineFilter filter, List<ChatLine> lines, int scrollPosition,
            int lineLimit, float restingX, float restingY, boolean open,
            LostTalesGuiAnimationSample opening) {
        int visibleLineCount = Math.max(1, lineLimit);
        int eligibleLineCount = 0;
        int totalLineCount = lines.size();
        float opacity = minecraft.gameSettings.chatOpacity * 0.9F + 0.1F;
        float scale = chat.func_146244_h();
        ChatLineBands bands = frame.bands;
        bands.reset(lines, totalLineCount, scale);
        if (totalLineCount <= 0) {
            if (open) {
                drawEmptyPlaceholder(minecraft, chat, restingX, restingY,
                        opacity * opening.getOpacity());
            }
            return;
        }

        int unscaledWidth = MathHelper.ceiling_float_int(
                chat.func_146228_f() / scale);
        FontRenderer font = minecraft.fontRenderer;
        // Switching tabs is a hard cut for the lines: only the tabs
        // themselves ease. The stack rises with a new message only while
        // the window is still growing; full, it would only look like it
        // is trying to.
        boolean growing = totalLineCount <= visibleLineCount;
        float originX = restingX;
        float originY = restingY
                + (growing ? entryDisplacement(filter, scrollPosition) : 0.0F);
        // The padding strips take the alpha, slide and colour of the line
        // they extend: the newest one below, the topmost drawn one above.
        int bottomAlpha = -1;
        float bottomSlide = 0.0F;
        int bottomColor = CHAT_BACKDROP_RGB;
        int topAlpha = -1;
        float topSlide = 0.0F;
        int topColor = CHAT_BACKDROP_RGB;

        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(originX, originY, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);

            for (int index = 0;
                 index + scrollPosition < lines.size()
                         && index < visibleLineCount;
                 index++) {
                ChatLine line = lines.get(index + scrollPosition);
                if (line == null) {
                    continue;
                }
                int age = minecraft.ingameGUI.getUpdateCounter()
                        - line.getUpdatedCounter();
                if (age >= 200 && !open) {
                    continue;
                }
                double fade = 1.0D - age / 200.0D;
                fade = Math.max(0.0D,
                        Math.min(1.0D, fade * 10.0D));
                fade *= fade;
                int alpha = open ? 255 : (int)(255.0D * fade);
                alpha = (int)(alpha * opacity);
                alpha = (int)(alpha * entryOpacity(line));
                alpha = (int)(alpha * opening.getOpacity());
                eligibleLineCount++;
                int y = -index * LINE_HEIGHT;
                float slide = entrySlide(line);
                if (open) {
                    // Recorded exactly as drawn: the same translate, slide
                    // and scale the quads below use. Recorded even while
                    // the line is still too faint to paint, so the tabs
                    // standing on the bands exist from the first frame of
                    // the opening fade instead of popping in later.
                    float bandLeft = originX + slide * scale;
                    bands.add(index + scrollPosition, bandLeft,
                            bandLeft + unscaledWidth * scale,
                            originY + (y - LINE_HEIGHT) * scale,
                            originY + y * scale);
                }
                if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
                    continue;
                }
                int color = LostTalesChatPresentation.isPingedLine(
                        line.getChatLineID())
                        ? PING_BACKDROP_RGB : CHAT_BACKDROP_RGB;
                if (bottomAlpha < 0) {
                    bottomAlpha = alpha;
                    bottomSlide = slide;
                    bottomColor = color;
                }
                topAlpha = alpha;
                topSlide = slide;
                topColor = color;
                // Backdrop and text slide together so the newest message
                // enters as one piece instead of text moving over a static
                // panel. Bands connect seamlessly: each fills the full
                // stride, meeting the next line's band edge-to-edge.
                GL11.glPushMatrix();
                GL11.glTranslatef(slide, 0.0F, 0.0F);
                drawChatBackdrop(
                        -2, y - LINE_HEIGHT, unscaledWidth + 4, y,
                        alpha / 2, color);
                GL11.glEnable(GL11.GL_BLEND);
                IChatComponent component = line.func_151461_a();
                GL11.glPushMatrix();
                GL11.glTranslatef(0.0F, y - (float)TEXT_OFFSET, 0.0F);
                ChatHeadMarker.Data marker = findMarker(component);
                LostTalesChatVisualStyle.drawFormatted(font,
                        component, marker, 0, 0, alpha, open);
                drawHead(minecraft, font, component,
                        -0.5F, alpha, open);
                GL11.glPopMatrix();
                GL11.glPopMatrix();
                GL11.glDisable(GL11.GL_ALPHA_TEST);
            }

            if (open) {
                int stackTop = -eligibleLineCount * LINE_HEIGHT;
                if (topAlpha >= 0) {
                    GL11.glPushMatrix();
                    GL11.glTranslatef(bottomSlide, 0.0F, 0.0F);
                    drawChatBackdrop(-2, 0, unscaledWidth + 4, LINE_PADDING,
                            bottomAlpha / 2, bottomColor);
                    GL11.glPopMatrix();
                    GL11.glPushMatrix();
                    GL11.glTranslatef(topSlide, 0.0F, 0.0F);
                    drawChatBackdrop(-2, stackTop - LINE_PADDING,
                            unscaledWidth + 4, stackTop,
                            topAlpha / 2, topColor);
                    GL11.glPopMatrix();
                }
                int fadeAlpha = Math.round(EDGE_FADE_ALPHA * opacity
                        * opening.getOpacity());
                drawEdgeFade(unscaledWidth, stackTop - LINE_PADDING,
                        LINE_PADDING, fadeAlpha);
                drawEdgeFade(unscaledWidth, LINE_PADDING,
                        stackTop - LINE_PADDING, fadeAlpha);
                GL11.glTranslatef(-3.0F, 0.0F, 0.0F);
                int fullHeight = totalLineCount * LINE_HEIGHT;
                int visibleHeight = eligibleLineCount * LINE_HEIGHT;
                int scrollOffset = scrollPosition * visibleHeight
                        / Math.max(1, totalLineCount);
                int thumbHeight = visibleHeight * visibleHeight
                        / Math.max(1, fullHeight);
                if (fullHeight != visibleHeight) {
                    // The scroll thumb in the palette: honey once the
                    // view has left the newest line, rose grey at rest,
                    // with an ivory edge.
                    int thumbAlpha = scrollOffset > 0 ? 0xFF
                            : LostTalesChatVisualStyle.SURFACE_ALPHA;
                    int thumbColor = scrollPosition > 0
                            ? LostTalesColors.rgb(LostTalesColors.HONEY)
                            : LostTalesColors.rgb(LostTalesColors.ROSE_GRAY);
                    Gui.drawRect(0, -scrollOffset, 2,
                            -scrollOffset - thumbHeight,
                            LostTalesChatVisualStyle.argb(thumbColor,
                                    thumbAlpha));
                    Gui.drawRect(2, -scrollOffset, 1,
                            -scrollOffset - thumbHeight,
                            LostTalesChatVisualStyle.argb(
                                    LostTalesChatVisualStyle.IVORY,
                                    thumbAlpha));
                }
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }
    }

    /**
     * One dim, italic line where the newest message would be, so an
     * empty channel reads as empty rather than as a window that failed
     * to draw. Shown only while the screen is open; the closed overlay
     * stays clean.
     */
    private static void drawEmptyPlaceholder(Minecraft minecraft,
                                             GuiNewChat chat,
                                             float originX, float originY,
                                             float opacity) {
        int alpha = Math.round(255.0F * Math.max(0.0F,
                Math.min(1.0F, opacity)));
        if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        float scale = chat.func_146244_h();
        int unscaledWidth = MathHelper.ceiling_float_int(
                chat.func_146228_f() / scale);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(originX, originY, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            drawChatBackdrop(-2, -LINE_HEIGHT - LINE_PADDING,
                    unscaledWidth + 4, LINE_PADDING,
                    alpha / 2, CHAT_BACKDROP_RGB);
            int fadeAlpha = Math.round(EDGE_FADE_ALPHA * opacity);
            drawEdgeFade(unscaledWidth, -LINE_HEIGHT - LINE_PADDING,
                    LINE_PADDING, fadeAlpha);
            drawEdgeFade(unscaledWidth, LINE_PADDING,
                    -LINE_HEIGHT - LINE_PADDING, fadeAlpha);
            GL11.glEnable(GL11.GL_BLEND);
            LostTalesChatVisualStyle.drawColored(minecraft.fontRenderer,
                    "\u00a7o" + StatCollector.translateToLocal(
                            "gui.losttales.chat.empty"),
                    0, -TEXT_OFFSET, LostTalesChatVisualStyle.IVORY,
                    alpha);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        } finally {
            GL11.glPopMatrix();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }
    }

    /**
     * The shade along one edge of the backdrop: the backdrop's plum
     * black at half opacity on the edge, fading to nothing two lines
     * inward ({@link #EDGE_FADE_HEIGHT}) — downward from the top edge
     * the tab row stands on, upward from the bottom edge the newest
     * message stands on — and never past {@code limit}, the opposite
     * edge. That vertical fade is then masked by a second, horizontal
     * one — full at the band's left edge, nothing at its right, across
     * the whole width — the two multiplied, so the shade is strongest in
     * the left corner and thins out to the right. It is drawn as a fine
     * mesh with the opacity worked out at every vertex: a single shaded
     * quad would put a visible seam along its diagonal.
     */
    private static void drawEdgeFade(int unscaledWidth, int edge, int limit,
                                     int alpha) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (safeAlpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        boolean downward = limit > edge;
        int far = downward
                ? Math.min(limit, edge + EDGE_FADE_HEIGHT)
                : Math.max(limit, edge - EDGE_FADE_HEIGHT);
        // Exactly the backdrop band's span, so the two ramps coincide.
        int left = -2;
        int right = unscaledWidth + 4;
        if (far == edge || right <= left) {
            return;
        }
        float[] columnX = new float[EDGE_FADE_COLUMNS + 1];
        float[] columnWeight = new float[columnX.length];
        for (int column = 0; column <= EDGE_FADE_COLUMNS; column++) {
            float t = column / (float)EDGE_FADE_COLUMNS;
            columnX[column] = left + (right - left) * t;
            columnWeight[column] = 1.0F - t;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        for (int rowIndex = 0; rowIndex < EDGE_FADE_ROWS; rowIndex++) {
            // Each band runs from nearer the edge to farther from it;
            // its top and bottom are then taken in screen order so the
            // winding is the backdrop's either way.
            float near = edge + (far - edge)
                    * (rowIndex / (float)EDGE_FADE_ROWS);
            float away = edge + (far - edge)
                    * ((rowIndex + 1) / (float)EDGE_FADE_ROWS);
            float nearWeight = 1.0F - LostTalesChatMotion.smoothStep(
                    rowIndex / (float)EDGE_FADE_ROWS);
            float awayWeight = 1.0F - LostTalesChatMotion.smoothStep(
                    (rowIndex + 1) / (float)EDGE_FADE_ROWS);
            float y0 = downward ? near : away;
            float y1 = downward ? away : near;
            float v0 = downward ? nearWeight : awayWeight;
            float v1 = downward ? awayWeight : nearWeight;
            for (int column = 0; column + 1 < columnX.length; column++) {
                float x0 = columnX[column];
                float x1 = columnX[column + 1];
                if (x1 <= x0) {
                    continue;
                }
                float h0 = columnWeight[column];
                float h1 = columnWeight[column + 1];
                // Same winding as the backdrop: the GUI pass culls back
                // faces.
                tessellator.setColorRGBA_I(CHAT_BACKDROP_RGB,
                        Math.round(safeAlpha * h1 * v1));
                tessellator.addVertex(x1, y1, 0.0D);
                tessellator.setColorRGBA_I(CHAT_BACKDROP_RGB,
                        Math.round(safeAlpha * h1 * v0));
                tessellator.addVertex(x1, y0, 0.0D);
                tessellator.setColorRGBA_I(CHAT_BACKDROP_RGB,
                        Math.round(safeAlpha * h0 * v0));
                tessellator.addVertex(x0, y0, 0.0D);
                tessellator.setColorRGBA_I(CHAT_BACKDROP_RGB,
                        Math.round(safeAlpha * h0 * v1));
                tessellator.addVertex(x0, y1, 0.0D);
            }
        }
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /**
     * A hairline along one edge of the backdrop band — the row directly
     * under the bottom padding — its full width, in the window's own
     * units: {@code top} is the row's y.
     */
    private static void drawEdgeRule(int unscaledWidth, int top, int alpha) {
        drawRule(-2, unscaledWidth + 4, top, top + 1, alpha);
    }

    /**
     * The chat's rule: a band of the chat's ivory between {@code left}
     * and {@code right}, opaque at the centre and fading to nothing at
     * either end, so the edges the messages stand between read as
     * edges. The tab row draws the window's top rule with this as its
     * last pixel row; the bottom rule is drawn under the bottom padding.
     */
    static void drawRule(float left, float right, float top, float bottom,
                         int alpha) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (safeAlpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA
                || right <= left || bottom <= top) {
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
        // Same winding as the backdrop: the GUI pass culls back faces.
        // Left half: transparent edge to opaque centre.
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, safeAlpha);
        tessellator.addVertex(centre, bottom, 0.0D);
        tessellator.addVertex(centre, top, 0.0D);
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, 0);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.addVertex(left, bottom, 0.0D);
        // Right half: opaque centre to transparent edge.
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, 0);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, safeAlpha);
        tessellator.addVertex(centre, top, 0.0D);
        tessellator.addVertex(centre, bottom, 0.0D);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    static int backdropFadeStart(int width) {
        return Math.max(0, Math.round(width * BACKDROP_FADE_START));
    }

    /** Palette backdrop band with a smooth transparent right edge. */
    private static void drawChatBackdrop(
            int left, int top, int right, int bottom, int alpha,
            int backdropRgb) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (right <= left || bottom <= top || safeAlpha <= 0) {
            return;
        }
        int fadeStart = Math.min(right,
                left + backdropFadeStart(right - left));
        Gui.drawRect(left, top, fadeStart, bottom,
                (safeAlpha << 24) | backdropRgb);
        if (fadeStart >= right) {
            return;
        }

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(backdropRgb, 0);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.setColorRGBA_I(backdropRgb, safeAlpha);
        tessellator.addVertex(fadeStart, top, 0.0D);
        tessellator.addVertex(fadeStart, bottom, 0.0D);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void drawHead(Minecraft minecraft, FontRenderer font,
                                 IChatComponent line, float y, int alpha,
                                 boolean chatOpen) {
        int x = 0;
        for (Object value : line) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            if (ChatChannelPrefixMarker.isHidden(part, chatOpen)) {
                continue;
            }
            ChatHeadMarker.Data marker = ChatHeadMarker.decode(part);
            if (marker != null) {
                float opacity = alpha / 255.0F;
                drawHeadShadow(minecraft, marker, x, y,
                        opacity * LostTalesChatVisualStyle.SHADOW_OPACITY);
                if (marker.npcIdentity) {
                    LostTalesCharacterHeadIconRenderer.drawNpcHead(
                            minecraft, marker.skinId,
                            x + 0.5F, y, 8.0F, 1.0F, opacity);
                } else if (marker.accountIdentity) {
                    LostTalesCharacterHeadIconRenderer.drawAccountHead(
                            minecraft, marker.senderId,
                            x + 0.5F, y, 8.0F, 1.0F, opacity);
                } else {
                    LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                            minecraft, marker.senderId, marker.skinId,
                            x + 0.5F, y, 8.0F, 1.0F, opacity);
                }
                return;
            }
            // getFormattedText() recursively includes a component's siblings.
            // Measuring only this node keeps the marker aligned after the
            // structured message has been split into wrapped ChatLines.
            x += LostTalesChatVisualStyle.partWidth(font, part, chatOpen);
        }
    }

    /**
     * Flat shadow of the head's base face, one pixel down-right like the
     * text shadow (the half-pixel portrait offset lands it on the same
     * grid). Silhouette mode gives every skin the same shadow colour
     * instead of a darkened copy of its own pixels.
     */
    private static void drawHeadShadow(
            Minecraft minecraft, ChatHeadMarker.Data marker,
            int x, float y, float opacity) {
        if (opacity * 255.0F < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        float shadowX = x + 0.5F + LostTalesChatVisualStyle.SHADOW_OFFSET;
        float shadowY = y + LostTalesChatVisualStyle.SHADOW_OFFSET;
        LostTalesSilhouetteRenderState.begin(LostTalesChatVisualStyle.SHADOW);
        try {
            if (marker.npcIdentity) {
                LostTalesCharacterHeadIconRenderer.drawTintedNpcHeadBase(
                        minecraft, marker.skinId, shadowX, shadowY, 8.0F,
                        1.0F, 1.0F, 1.0F, opacity);
            } else if (marker.accountIdentity) {
                LostTalesCharacterHeadIconRenderer.drawTintedAccountHeadBase(
                        minecraft, marker.senderId, shadowX, shadowY, 8.0F,
                        1.0F, 1.0F, 1.0F, opacity);
            } else {
                LostTalesCharacterHeadIconRenderer.drawTintedSnapshotHeadBase(
                        minecraft, marker.senderId, marker.skinId,
                        shadowX, shadowY, 8.0F,
                        1.0F, 1.0F, 1.0F, opacity);
            }
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
    }

    private static ChatHeadMarker.Data findMarker(IChatComponent line) {
        if (line == null) {
            return null;
        }
        for (Object value : line) {
            if (value instanceof IChatComponent) {
                ChatHeadMarker.Data marker = ChatHeadMarker.decode(
                        (IChatComponent)value);
                if (marker != null) {
                    return marker;
                }
            }
        }
        return null;
    }

    /**
     * Stack rise for the newest message. Only applies to a window whose
     * view actually received it, so a message in another window's channel
     * does not nudge the history currently being read.
     */
    private static float entryDisplacement(ChatLineFilter filter,
                                           int scrollPosition) {
        if (!LostTalesConfig.enableChatAnimations || scrollPosition != 0) {
            return 0.0F;
        }
        long started = LostTalesChatPresentation.getLastMessageNanos();
        if (started <= 0L) {
            return 0.0F;
        }
        ChatTab lastTab = LostTalesChatPresentation.getLastMessageTab();
        if (filter != null && !filter.accepts(lastTab)) {
            return 0.0F;
        }
        long duration = Math.max(1,
                LostTalesConfig.chatAnimationDurationMillis) * 1000000L;
        float progress = Math.min(1.0F,
                (System.nanoTime() - started) / (float)duration);
        return LostTalesChatMotion.message(progress).stackOffsetY;
    }

    /** Horizontal entry offset for lines of the newest message only. */
    private static float entrySlide(ChatLine line) {
        if (!LostTalesConfig.enableChatAnimations || line == null
                || line.getUpdatedCounter()
                != LostTalesChatPresentation.getLastMessageUpdateCounter()) {
            return 0.0F;
        }
        long started = LostTalesChatPresentation.getLastMessageNanos();
        if (started <= 0L) {
            return 0.0F;
        }
        long duration = Math.max(1,
                LostTalesConfig.chatAnimationDurationMillis) * 1000000L;
        return LostTalesChatMotion.message(
                (System.nanoTime() - started) / (float)duration)
                .slideOffsetX;
    }

    private static float entryOpacity(ChatLine line) {
        if (!LostTalesConfig.enableChatAnimations || line == null
                || line.getUpdatedCounter()
                != LostTalesChatPresentation.getLastMessageUpdateCounter()) {
            return 1.0F;
        }
        long started = LostTalesChatPresentation.getLastMessageNanos();
        if (started <= 0L) {
            return 1.0F;
        }
        long duration = Math.max(1,
                LostTalesConfig.chatAnimationDurationMillis) * 1000000L;
        return LostTalesChatMotion.message(
                (System.nanoTime() - started) / (float)duration).opacity;
    }

    private static Field findField(String name) {
        try {
            Field field = GuiNewChat.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
