package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiRegionBlur;
import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
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
            LostTalesColors.rgb(LostTalesColors.DARK_MULBERRY);
    /** The unread divider's rule and date: the palette's red. */
    private static final int UNREAD_DIVIDER_RGB =
            LostTalesColors.rgb(LostTalesColors.CRIMSON);
    /**
     * Vertical distance between chat lines. Vanilla uses the 9px font
     * height, which cannot contain a 10px emoji sprite; an 11px stride
     * gives the sprite room. Bands are contiguous — each line's backdrop
     * fills the full stride.
     */
    static final int LINE_HEIGHT = 11;
    /**
     * Text baseline offset inside a band. Two rows sit above the glyph
     * caps and two below them, so the 7px cap height is centred in the
     * 11px band; heads (8px at -0.5) land exactly centred as well.
     */
    static final int TEXT_OFFSET = 9;
    /**
     * Where a head sits against the text it stands beside: a pixel above
     * the glyph box, which reads level once the glyphs' own bearing is
     * accounted for. Whole pixels either way — a head is pixel art at
     * one texel to one pixel, and half a pixel of centring costs more
     * than it buys.
     */
    private static final float HEAD_TOP_OFFSET = -1.0F;
    /**
     * Where the face sits inside the slot the head marker reserves: one
     * pixel in, so it has two clear either side — the glyph before it
     * lends one of those from its own trailing space.
     */
    private static final float HEAD_LEFT_OFFSET =
            ChatInlineIcons.HEAD_SLOT_INSET;
    private static final float BACKDROP_FADE_START = 2.0F / 3.0F;
    /**
     * Depth of the shade hanging from the window's top edge: one line,
     * so the line passing under the top rule has faded most of the way
     * out before the clip cuts it.
     */
    private static final float TOP_EDGE_FADE_HEIGHT = LINE_HEIGHT;
    /**
     * Depth of the shade hanging from the bottom edge: two lines, so it
     * reaches through the trailing strip and over the newest message.
     */
    private static final float BOTTOM_EDGE_FADE_HEIGHT = LINE_HEIGHT * 2.0F;
    /** Opacity of an edge fade on the edge it hangs from: half. */
    private static final int EDGE_FADE_ALPHA = 0x80;
    /**
     * The faint diagonal hatch on message rows the history does not
     * reach yet: a white 45° line every eight texels, tinted the
     * backdrop's own plum black at draw time so it deepens the panel a
     * step instead of bringing a colour of its own. The tile is 16
     * texels square and the pattern's period divides it, so whole
     * tiles meet seamlessly and a partial edge tile is cut by its UVs.
     */
    private static final ResourceLocation EMPTY_HATCH_TEXTURE =
            new ResourceLocation("losttales",
                    "textures/gui/chat_empty_hatch.png");
    private static final int EMPTY_HATCH_TILE = 16;
    /** The hatch's opacity at full chat opacity: barely there. */
    private static final int EMPTY_HATCH_ALPHA = 0x30;
    /**
     * Mesh resolution of an edge fade. The horizontal ramp is linear, so
     * two columns carry it exactly; the vertical one is eased, and each
     * row is a straight segment of that curve, so the rows are what
     * decides whether the gradient bands. Against the blurred, flat
     * backdrop the chat now opens over, a coarse ramp shows its seams,
     * so the curve is cut finely — and, with the columns gone, into
     * fewer quads than the coarse mesh took.
     */
    private static final int EDGE_FADE_ROWS = 32;
    private static final int EDGE_FADE_COLUMNS = 1;
    /**
     * Slack for the clip's display-pixel conversion: far above any
     * floating-point error the conversion can accumulate, far below the
     * smallest genuine fraction of a display pixel an edge can carry.
     */
    private static final double CLIP_EDGE_EPSILON = 1.0E-3D;
    private static final Field DRAWN_LINES = findField("field_146253_i");

    private LostTalesChatOverlayRenderer() {}

    static boolean draw(Minecraft minecraft, float partialTicks) {
        if (minecraft == null || minecraft.ingameGUI == null
                || minecraft.gameSettings.chatVisibility
                == EntityPlayer.EnumChatVisibility.HIDDEN
                || DRAWN_LINES == null) {
            return false;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        // The hotbar's item icons are drawn with depth testing on, at a
        // raised z, and they leave it on. Anything the HUD draws after
        // them at z 0 — this chat included — is rejected where it
        // overlaps an icon while passing over the hotbar's flat parts,
        // which is why items alone appeared through the feed. The chat
        // is flat overlay content drawn after the whole hotbar, so it
        // takes no part in depth testing and puts the state back.
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        if (depthTest) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
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
                drawFeed(minecraft, chat, drawn, screenWidth, screenHeight,
                        partialTicks);
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
        } finally {
            if (depthTest) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            }
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
        // An open window lays its own lines out: at its own width when
        // it has one, and always without the channel prefix the shared
        // history reserves room for, which the open screen does not
        // draw. Only a window whose history cannot be read falls back to
        // that shared list, which is what the closed feed reads.
        int chatWidth = ChatWindowPlacement.chatWidth(window, minecraft);
        List<ChatLine> own = ChatWindowLines.forWindow(minecraft, chat,
                window, filter, chatWidth);
        List<ChatLine> lines = own != null ? own
                : ClientChatChannelViews.visibleLines(drawn, filter);
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
        float scale = chat.func_146244_h();
        frame.begin(box, scale, opening.getTranslationX(),
                opening.getTranslationY());
        // The frame's message room says how many lines the window shows:
        // the box's, laid on whole display pixels against the drawn
        // baseline. The room is the height the player dragged the window
        // to rather than a whole number of lines, so the topmost line
        // can be a partial one: it is drawn and clipped where the room
        // ends.
        float room = (float)frame.room;
        int lineLimit = linesForRoom(room, scale);
        double roomLines = room / (double)(LINE_HEIGHT * scale);
        double scroll = view == null ? 0.0D
                : ClientChatChannelViews.renderedScroll(view,
                        ClientChatChannelViews.getScroll(view, lines.size(),
                                roomLines));
        frame.renderedScrollLines = scroll;
        frame.drawn = true;
        // The window's own rectangle of the blurred frame, under the
        // backdrop; drawn only while the chat screen captured one this
        // frame, so every other path keeps the plain backdrop. The
        // history band thins out to the right exactly as its backdrop
        // does; the tab row's band and the bar's stay whole.
        LostTalesGuiRegionBlur blur = LostTalesGuiRegionBlur.getInstance();
        double blurLeft = frame.drawnLeft();
        double blurRight = blurLeft + (frame.boxRight - frame.boxLeft);
        double blurTop = frame.boxTop + frame.motionY;
        double blurBottom = frame.boxBottom + frame.motionY;
        double historyTop = frame.drawnBaseline() - room;
        double historyBottom = frame.drawnBaseline()
                + ChatWindowPlacement.lineHeight(minecraft);
        float blurOpacity = opening.getOpacity();
        blur.drawRegion(blurLeft, blurTop, blurRight, historyTop,
                blurOpacity);
        blur.drawFadedRegion(blurLeft, historyTop, blurRight, historyBottom,
                blurLeft + (blurRight - blurLeft) * BACKDROP_FADE_START,
                blurOpacity);
        blur.drawRegion(blurLeft, historyBottom, blurRight, blurBottom,
                blurOpacity);
        // The newest line sits on the baseline; the whole window rides
        // the opening motion, tabs and bar included. The origin is the
        // frame's, not the placement box's: the box is where the window
        // was dragged to, in fractions of a pixel, and the frame is
        // where it is drawn, on whole display pixels. Measuring the
        // messages from the box would leave every head and emoji in
        // them half a pixel off its own texels. The origin is the
        // message text's own left edge; the timestamp column, when it
        // is on, lies between it and the window edge.
        ChatTimestampColumn columns =
                ChatTimestampColumn.current(minecraft.fontRenderer);
        float originX = (float)ChatWindowFrame.snapToDisplayPixels(
                frame.drawnLeft() + columns.messageX() * scale);
        float originY = (float)frame.drawnBaseline();
        drawWindow(minecraft, chat, frame, filter, lines, scroll, lineLimit,
                room, originX, originY, true, opening, chatWidth,
                columns);
    }

    /**
     * The window's bottom hairline, drawn by the chat screen after the
     * bar so it lies over the edge shade: one GUI pixel tall exactly
     * like the top rule — the bar strip's first row, mirroring the tab
     * strip whose last row is the top rule. Between the baseline and
     * this row lies the window's trailing strip, one line of always
     * visible room the typing line lives in; the message clip ends on
     * the baseline, so no message ever enters the strip.
     */
    static void drawBottomRule(Minecraft minecraft, ChatWindowFrame frame,
                               LostTalesGuiAnimationSample opening) {
        if (minecraft == null || minecraft.ingameGUI == null
                || frame == null || !frame.drawn || opening == null) {
            return;
        }
        // The window's own width, not the game's: the rule is the same
        // rule the strip draws along the top, so it ends where that ends.
        float left = (float)frame.drawnLeft();
        float right = left + (float)(frame.boxRight - frame.boxLeft);
        float top = (float)frame.drawnBaseline()
                + ChatWindowPlacement.lineHeight(minecraft);
        drawBackdropRow(left, top, right, top + 1.0F,
                Math.round(backdropRowAlpha(minecraft)
                        * opening.getOpacity()));
        drawRule(left, right, top, top + 1.0F,
                Math.round(255.0F * opening.getOpacity()));
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
                                 int screenHeight, float partialTicks) {
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
        // The frame is captured and blurred only while the feed has a
        // line still on screen; the rest of the time gameplay pays
        // nothing for the feed's blur.
        if (LostTalesConfig.enableChatBackgroundBlur
                && LostTalesConfig.enableGuiBackgroundBlur
                && !lines.isEmpty() && lines.get(0) != null
                && minecraft.ingameGUI.getUpdateCounter()
                        - lines.get(0).getUpdatedCounter() < 200) {
            LostTalesGuiRegionBlur.getInstance().capture(minecraft,
                    partialTicks, (float)LostTalesConfig.guiBlurStrength);
        }
        ChatWindowPlacement.Box box = ChatWindowPlacement.feedBounds(
                minecraft, screenWidth, screenHeight);
        float scale = chat.func_146244_h();
        frame.begin(box, scale, 0.0F, 0.0F);
        frame.drawn = true;
        // The feed never shows the timestamp column; its lines begin the
        // same edge gap from the window edge that a column-less window's
        // do.
        ChatTimestampColumn columns = ChatTimestampColumn.disabled();
        drawWindow(minecraft, chat, frame, filter, lines, 0.0D,
                visibleLineCount(chat), (float)frame.room,
                (float)ChatWindowFrame.snapToDisplayPixels(
                        frame.drawnLeft() + columns.messageX() * scale),
                (float)frame.drawnBaseline(), false,
                LostTalesGuiAnimationSample.SETTLED,
                ChatWindowPlacement.chatWidth(minecraft), columns);
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
     * math no longer matches what is on screen. The position is
     * fractional: callers pass the pointer's exact GUI coordinate, so the
     * answer matches the drawn cursor tip rather than the whole pixel the
     * integer conversion truncated it to.
     */
    static Hit hitAt(Minecraft minecraft, float mouseX, float mouseY) {
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
                if (ChatPrefixMarker.isHidden(part, true)) {
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

    /**
     * Lines a box with {@code room} pixels of message space shows: the
     * whole ones, plus the partial one the room ends inside, which the
     * draw clips.
     */
    static int linesForRoom(float room, float scale) {
        float stride = LINE_HEIGHT * (scale <= 0.0F ? 1.0F : scale);
        return Math.max(1, MathHelper.ceiling_float_int(room / stride));
    }

    /**
     * Scissors drawing to the band between two GUI-space y values: a
     * window that ends part-way through a line cuts it cleanly, a stack
     * sliding under a scroll never reaches past the baseline, and a
     * sprite's shadow never crosses the rule its strip ends on. Either
     * edge may be {@code NaN}, meaning nothing is cut on that side. The
     * rectangle is converted with the display's own pixels per GUI
     * pixel, which is exact at every GUI scale and window size; the
     * whole scissor state is pushed so nothing outlives the clip.
     *
     * <p>A fractional edge is rounded to a whole display pixel, never to
     * the nearest — that would flip between two rows as the stack
     * slides, and the row it gave up would flicker. {@code inward} says
     * which way: the message stack rounds inward, so not one display
     * pixel of a line ever lands on a rule (what it gives up shows the
     * window's own panel, drawn unclipped behind it); a caller whose
     * background continues past the clip rounds outward. The epsilon
     * absorbs floating-point noise, so an edge that lands exactly on a
     * display pixel is cut exactly there either way.</p>
     */
    static boolean beginVerticalClip(Minecraft minecraft, double topY,
                                     double bottomY, boolean inward) {
        try {
            ScaledResolution resolution = new ScaledResolution(minecraft,
                    minecraft.displayWidth, minecraft.displayHeight);
            // The GUI ortho maps its exact fractional height onto the
            // display, so one GUI pixel is exactly the scale factor of
            // display pixels — never the display over the ceil-rounded
            // integer height, which drifts a pixel at display sizes the
            // factor does not divide and opened hairline gaps between
            // clipped content and the rules it should meet.
            double pixelsPerGuiPixel =
                    Math.max(1, resolution.getScaleFactor());
            // Scissor space counts up from the bottom of the display, so
            // the GUI's lower edge is the rectangle's origin.
            double lowEdge = minecraft.displayHeight
                    - bottomY * pixelsPerGuiPixel;
            double highEdge = minecraft.displayHeight
                    - topY * pixelsPerGuiPixel;
            int top = Double.isNaN(bottomY) ? 0 : (int)(inward
                    ? Math.ceil(lowEdge - CLIP_EDGE_EPSILON)
                    : Math.floor(lowEdge + CLIP_EDGE_EPSILON));
            int bottom = Double.isNaN(topY) ? minecraft.displayHeight
                    : (int)(inward
                            ? Math.floor(highEdge + CLIP_EDGE_EPSILON)
                            : Math.ceil(highEdge - CLIP_EDGE_EPSILON));
            top = Math.max(0, top);
            bottom = Math.min(minecraft.displayHeight, bottom);
            if (bottom <= top) {
                return false;
            }
            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(0, top, Math.max(0, minecraft.displayWidth),
                    bottom - top);
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    static void endVerticalClip(boolean clipped) {
        if (clipped) {
            GL11.glPopAttrib();
        }
    }

    private static void drawWindow(
            Minecraft minecraft, GuiNewChat chat, ChatWindowFrame frame,
            ChatLineFilter filter, List<ChatLine> lines,
            double scrollLines, int lineLimit, float room, float restingX,
            float restingY, boolean open,
            LostTalesGuiAnimationSample opening, int chatWidth,
            ChatTimestampColumn columns) {
        // The offset is in lines and fractions of one: whole lines pick
        // where the stack starts, the fraction slides it, and one more
        // line is drawn so the gap the slide opens is filled.
        int scrollPosition = (int)Math.floor(Math.max(0.0D, scrollLines));
        float scrollSlide = (float)(Math.max(0.0D, scrollLines)
                - scrollPosition);
        int visibleLineCount = Math.max(1, lineLimit)
                + (scrollSlide > 0.0F ? 1 : 0);
        int eligibleLineCount = 0;
        int totalLineCount = lines.size();
        float opacity = minecraft.gameSettings.chatOpacity * 0.9F + 0.1F;
        float scale = chat.func_146244_h();
        ChatLineBands bands = frame.bands;
        bands.reset(lines, totalLineCount, scale);
        // A window with a height of its own keeps it whatever it holds:
        // its stack top is its box's edge, and rows it has no messages
        // for stay as empty panel instead of shrinking the window.
        ChatWindow boxWindow = ChatWindowLayout.window(frame.windowId);
        boolean fixedHeight = open && boxWindow != null
                && boxWindow.getMaxLines() > 0.0D;
        if (totalLineCount <= 0) {
            frame.setStackTop(restingY - room);
            if (!open) {
                // The feed simply shows nothing while it is empty.
                return;
            }
            // An open window keeps its whole panel — backdrop, column,
            // separator, shades — and shows a small invitation where the
            // newest line would be, so the first message changes nothing
            // but the words: no backdrop pops in around it.
        }

        int unscaledWidth = MathHelper.ceiling_float_int(chatWidth / scale);
        FontRenderer font = minecraft.fontRenderer;
        // Switching tabs is a hard cut for the lines: only the tabs
        // themselves ease. The stack rises with a new message only while
        // the window is still growing; full, it would only look like it
        // is trying to.
        boolean growing = totalLineCount <= visibleLineCount;
        float originX = restingX;
        float originY = restingY;
        // Everything the message stack is moved by, and nothing else is:
        // the entrance of a new message, and the scroll's part of a
        // line. Rounded to whole display pixels, because the heads and
        // the emoji sprites are pixel art sampled one texel to one
        // pixel, and at a fraction of a pixel their texels crawl; a
        // display pixel is finer than a GUI pixel at every scale above
        // one, so the motion stays smooth.
        float stackOffset = snapToDisplayPixels(minecraft,
                (growing ? entryDisplacement(filter, scrollPosition) : 0.0F)
                        + scrollSlide * LINE_HEIGHT * scale);
        float offset = stackOffset / scale;

        // The stack the loop is about to draw, so the cut is known
        // before it runs.
        int plannedLineCount = Math.max(0, Math.min(visibleLineCount,
                totalLineCount - scrollPosition));
        float roomUnscaled = room / scale;
        // The window's rules are where content ends: everything the
        // stack draws is cut on the top rule and on the bottom rule, and
        // nowhere earlier, so a glyph's descender or shadow below the
        // newest line and a stack sliding down under a scroll both
        // continue into the trailing strip and disappear behind the
        // bottom rule exactly as the topmost line disappears behind the
        // top one. The strip — one line of room between the baseline
        // and the bottom rule, where the typing line lives — is part of
        // the panel, so the backdrop and the bottom shade always span
        // it. In the window's own units the resting baseline is zero,
        // which is where the stack stands before it is offset.
        // The content clip reaches the rules on both sides. Above the
        // room lies the head-room band: the topmost line's own band
        // extends up through it to the rule, so its backdrop — a
        // mention's tint above all — is not cut flush on the glyphs.
        float clipTop = restingY - room
                - ChatWindowPlacement.HISTORY_TOP_MARGIN;
        float clipBottom = restingY
                + ChatWindowPlacement.lineHeight(minecraft);
        float topEdge = -roomUnscaled
                - ChatWindowPlacement.HISTORY_TOP_MARGIN / scale;
        float bottomEdge = LINE_HEIGHT;
        // The panel reaches from the window's left edge — past the
        // timestamp column when there is one — to its right; the text
        // origin this method draws from lies messageX inside it.
        float panelLeft = -(float)columns.messageX();
        float panelRight = panelLeft + unscaledWidth + 6.0F;
        // A window whose history can fill its room keeps its row on the
        // window's own top edge, so it does not move as the stack
        // scrolls under it — the trailing scroll room included; so does
        // one with a height of its own, whose empty rows are part of the
        // window. Only a window following the game setting and still
        // filling up carries its row down onto its last line.
        boolean full = fixedHeight || totalLineCount <= 0
                || totalLineCount * (float)LINE_HEIGHT
                        >= roomUnscaled - 0.01F;
        frame.setStackTop(full ? restingY - room
                : restingY + stackOffset
                        - plannedLineCount * LINE_HEIGHT * scale);

        GL11.glPushMatrix();
        boolean clipped = false;
        try {
            GL11.glTranslatef(originX, originY, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            if (open) {
                // One backdrop for the whole area between the rules,
                // laid before the stack. The lines used to bring a band
                // each and two strips carried the colour out to the
                // rules; every one of those edges was somewhere a seam
                // could open, and a stack sliding under a scroll opened
                // them. A window has one panel, and the messages are
                // drawn on it.
                drawChatBackdrop(panelLeft, topEdge, panelRight,
                        bottomEdge, backdropAlpha(opacity, opening) / 2,
                        CHAT_BACKDROP_RGB);
                if (columns.enabled) {
                    // The timestamp column's own band, darker than the
                    // panel it lies on, so the timestamps read as a
                    // margin rather than as part of the messages.
                    fillRect(panelLeft, topEdge,
                            panelLeft + columns.separatorX(), bottomEdge,
                            (Math.round(0x80 * opacity
                                    * opening.getOpacity()) << 24)
                                    | CHAT_BACKDROP_RGB);
                }
                // Rows the history does not reach: hatched, so the
                // region reads as holding no messages rather than as a
                // gap. The hatch hangs from the panel's top and its
                // lower edge follows the stack's top exactly, entry
                // motion included. Only a history too short for the room
                // has such rows; anything that can scroll fills it, so
                // the head-room band above a scrolled stack stays plain
                // panel.
                if (totalLineCount * (float)LINE_HEIGHT
                        < roomUnscaled - 0.01F) {
                    drawEmptyRowsHatch(minecraft,
                            columns.enabled
                                    ? panelLeft + columns.separatorX()
                                            + ChatTimestampColumn
                                                    .SEPARATOR_WIDTH
                                    : panelLeft,
                            topEdge, panelRight,
                            Math.min(0.0F,
                                    offset - plannedLineCount * LINE_HEIGHT),
                            Math.round(EMPTY_HATCH_ALPHA * opacity
                                    * opening.getOpacity()));
                }
            }
            // Only the message stack is offset; the panel and the shades
            // belong to the window's own edges and stay on them.
            // The unread divider takes a whole row of its own between
            // the last read message and the first unread one; everything
            // older shifts up by it, and the stack top follows.
            int dividerRows = 0;
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(0.0F, offset, 0.0F);
                // Inward: a line never touches a rule row; whatever the
                // cut gives up shows the panel drawn unclipped above.
                clipped = open && beginVerticalClip(minecraft, clipTop,
                        clipBottom, true);

                // A scrolled view starts one line earlier: the line the
                // first turn of scroll slid into the trailing strip,
                // clipped where the strip's reveal ends.
                int firstLine = Math.max(0, scrollPosition - 1);
                Integer dividerLine = open && frame.view != null
                        ? ClientChatChannelViews.unreadDividerLine(
                                frame.view)
                        : null;
                String dividerLabel = dividerLine == null ? ""
                        : ClientChatChannelViews.unreadDividerLabel(
                                frame.view);
                // The line in the topmost slot owns the head-room above
                // it: its band reaches up to the rule instead of being
                // cut flush on its glyphs. A window with a fixed height
                // and empty rows has no line at its top, so nothing
                // there is extended.
                int topmostIndex = Math.min(lines.size(),
                        scrollPosition + visibleLineCount) - 1;
                for (int lineIndex = firstLine;
                     lineIndex < lines.size()
                             && lineIndex - scrollPosition < visibleLineCount;
                     lineIndex++) {
                    ChatLine line = lines.get(lineIndex);
                    if (line == null) {
                        continue;
                    }
                    float headroom = open && lineIndex == topmostIndex
                            && (!fixedHeight
                                    || totalLineCount >= visibleLineCount)
                            ? ChatWindowPlacement.HISTORY_TOP_MARGIN : 0.0F;
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
                    int y = -(lineIndex - scrollPosition + dividerRows)
                            * LINE_HEIGHT;
                    float entry = entrySlide(line);
                    if (open) {
                        // Recorded exactly as drawn: the same translate, slide
                        // and scale the quads below use. Recorded even while
                        // the line is still too faint to paint, so the tabs
                        // standing on the bands exist from the first frame of
                        // the opening fade instead of popping in later.
                        float bandLeft = originX + entry * scale;
                        // A line the room ends inside is recorded as the
                        // part of it that survives the clip, so hit testing
                        // answers for exactly what is on screen.
                        float bandTop = Math.max(clipTop, originY
                                + stackOffset + (y - LINE_HEIGHT) * scale
                                - headroom);
                        float bandBottom = Math.min(clipBottom,
                                originY + stackOffset + y * scale);
                        if (bandBottom > bandTop) {
                            bands.add(lineIndex, bandLeft,
                                    bandLeft + unscaledWidth * scale, bandTop,
                                    bandBottom);
                        }
                    }
                    if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
                        continue;
                    }
                    boolean dividerHere = dividerLine != null
                            && line.getChatLineID() == dividerLine.intValue()
                            && (lineIndex + 1 >= lines.size()
                                    || lines.get(lineIndex + 1) == null
                                    || lines.get(lineIndex + 1)
                                            .getChatLineID()
                                            != dividerLine.intValue());
                    int color = LostTalesChatPresentation.isPingedLine(
                            line.getChatLineID())
                            ? PING_BACKDROP_RGB : CHAT_BACKDROP_RGB;
                    // The open window has one panel behind every line, so
                    // a line only paints where it differs from it: a
                    // mention of this player is tinted on top. The closed
                    // feed has no panel — each of its lines fades on its
                    // own — so there each brings its own band, sliding
                    // with its text so a new message enters as one piece.
                    GL11.glPushMatrix();
                    GL11.glTranslatef(entry, 0.0F, 0.0F);
                    if (!open) {
                        // A feed line softens the world behind its own
                        // band: the blur rides the line, fades with its
                        // age, and thins to the right like its backdrop.
                        // Without a fresh capture nothing is drawn.
                        LostTalesGuiRegionBlur.getInstance()
                                .drawFadedRegionInTransform(
                                        panelLeft, y - LINE_HEIGHT,
                                        panelRight, y,
                                        panelLeft
                                                + (panelRight - panelLeft)
                                                * BACKDROP_FADE_START,
                                        originX + entry * scale,
                                        originY + stackOffset, scale,
                                        alpha / 255.0F);
                    }
                    if (!open || color != CHAT_BACKDROP_RGB) {
                        drawChatBackdrop(panelLeft,
                                y - LINE_HEIGHT - headroom / scale,
                                panelRight, y, alpha / 2, color);
                    }
                    GL11.glPopMatrix();
                    if (dividerHere) {
                        // The unread divider's own row, directly above
                        // the first unread message: it rides the stack
                        // but not the line's entry slide, like the
                        // timestamps.
                        drawUnreadDivider(font, columns, panelLeft,
                                panelRight, y - 2 * LINE_HEIGHT,
                                dividerLabel, alpha);
                    }
                    // The line's timestamp lives in the column at the
                    // window's edge: it rides the stack's vertical
                    // motion and the line's fade, but not the entry
                    // slide — the column does not move sideways. Drawn
                    // after the line's band, so on a highlighted line
                    // the digits stand on the tint instead of being
                    // darkened under it.
                    if (open && columns.enabled) {
                        drawTimestampRuns(font, line.func_151461_a(),
                                Math.round(panelLeft) + columns.timestampX(),
                                y - TEXT_OFFSET, alpha);
                    }
                    GL11.glPushMatrix();
                    GL11.glTranslatef(entry, 0.0F, 0.0F);
                    GL11.glEnable(GL11.GL_BLEND);
                    IChatComponent component = line.func_151461_a();
                    GL11.glPushMatrix();
                    GL11.glTranslatef(0.0F, y - (float)TEXT_OFFSET, 0.0F);
                    ChatHeadMarker.Data marker = findMarker(component);
                    if (ChatGroupMarker.isGroupedLine(component)) {
                        // The grouping junction in the slot the line
                        // reserves: an elbow on the run's last message,
                        // a connecting tee while another follows below,
                        // in the run's own name colour.
                        drawGroupJunction(
                                groupRunContinuesBelow(lines, lineIndex),
                                groupJunctionColor(lines, lineIndex),
                                alpha);
                    }
                    LostTalesChatVisualStyle.drawFormatted(font,
                            component, marker, 0, 0, alpha, open);
                    drawHead(minecraft, font, component,
                            HEAD_TOP_OFFSET, alpha, open);
                    GL11.glPopMatrix();
                    GL11.glPopMatrix();
                    GL11.glDisable(GL11.GL_ALPHA_TEST);
                    if (dividerHere) {
                        dividerRows++;
                    }
                }

            } finally {
                endVerticalClip(clipped);
                clipped = false;
                GL11.glPopMatrix();
            }
            if (!full) {
                // Lines the draw passed over: the stack ends lower than
                // the room allows, so the row follows it down. The
                // unread divider's row is part of the stack.
                frame.setStackTop(restingY + stackOffset
                        - (eligibleLineCount + dividerRows)
                                * LINE_HEIGHT * scale);
            }

            if (open && totalLineCount <= 0) {
                // The invitation, where the newest line would be, in the
                // timestamps' quiet grey, trimmed to the message area so
                // a narrow window never lets it run out under its edge.
                int inviteAlpha = Math.round(255.0F * opacity
                        * opening.getOpacity());
                GL11.glEnable(GL11.GL_BLEND);
                LostTalesChatVisualStyle.drawColored(font,
                        "§o" + font.trimStringToWidth(
                                StatCollector.translateToLocal(
                                        "gui.losttales.chat.empty"),
                                Math.max(20, Math.round(panelRight) - 4)),
                        0, -TEXT_OFFSET,
                        LostTalesColors.rgb(LostTalesColors.ROSE_BEIGE),
                        inviteAlpha);
            }
            if (open) {
                int fadeAlpha = Math.round(EDGE_FADE_ALPHA * opacity
                        * opening.getOpacity());
                // The shades hang from the rules themselves, so a line
                // passing under one fades out before it is cut.
                drawEdgeFade(panelLeft, panelRight, topEdge, bottomEdge,
                        TOP_EDGE_FADE_HEIGHT, fadeAlpha);
                drawEdgeFade(panelLeft, panelRight, bottomEdge, topEdge,
                        BOTTOM_EDGE_FADE_HEIGHT, fadeAlpha);
                if (columns.enabled) {
                    // The separator stands over the lines the way the
                    // rules do, so a message sliding past never crosses
                    // it.
                    drawVerticalRule(panelLeft + columns.separatorX(),
                            panelLeft + columns.separatorX()
                                    + ChatTimestampColumn.SEPARATOR_WIDTH,
                            topEdge, bottomEdge,
                            Math.round(255.0F * opening.getOpacity()));
                }
            }
            // A view scrolled away from the newest line grows a small
            // arrow button at the panel's right edge, flying in from
            // below the bottom rule; clicking it glides the view home.
            // Recorded on the frame exactly as drawn, so the click and
            // the pixels cannot disagree.
            frame.jumpPillLeft = 0.0F;
            frame.jumpPillTop = 0.0F;
            frame.jumpPillRight = 0.0F;
            frame.jumpPillBottom = 0.0F;
            if (open) {
                boolean wanted = frame.view != null && scrollLines > 0.5D;
                long buttonNow = System.nanoTime();
                if (!LostTalesConfig.enableChatAnimations) {
                    frame.jumpButtonProgress = wanted ? 1.0F : 0.0F;
                } else {
                    double elapsed = frame.jumpButtonNanos == 0L ? 0.0D
                            : (buttonNow - frame.jumpButtonNanos) / 1.0E9D;
                    frame.jumpButtonProgress =
                            (float)LostTalesChatMotion.approach(
                                    frame.jumpButtonProgress,
                                    wanted ? 1.0D : 0.0D, elapsed, 0.1D);
                }
                frame.jumpButtonNanos = buttonNow;
                if (frame.jumpButtonProgress > 0.02F) {
                    // The button lives inside the history: the same
                    // clip that cuts a scrolling line on the bottom
                    // rule cuts it while it flies in, so it emerges
                    // through the rule instead of fading in over it.
                    boolean buttonClipped = beginVerticalClip(minecraft,
                            clipTop, clipBottom, true);
                    try {
                        drawJumpButton(frame, panelRight, bottomEdge,
                                Math.round(255.0F * opacity
                                        * opening.getOpacity()),
                                originX, originY, scale);
                    } finally {
                        endVerticalClip(buttonClipped);
                    }
                }
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }
    }

    /**
     * Rounds a GUI-space distance to a whole number of display pixels,
     * so pixel art moved by it lands on its own texels. Falls back to
     * whole GUI pixels when the display cannot be measured.
     */
    private static float snapToDisplayPixels(Minecraft minecraft,
                                             float distance) {
        try {
            ScaledResolution resolution = new ScaledResolution(minecraft,
                    minecraft.displayWidth, minecraft.displayHeight);
            float factor = Math.max(1, resolution.getScaleFactor());
            return Math.round(distance * factor) / factor;
        } catch (RuntimeException unavailable) {
            return Math.round(distance);
        }
    }


    /**
     * The shade along one edge of the backdrop: the backdrop's plum
     * black at half opacity on the edge, fading to nothing
     * {@code height} pixels inward — downward from the top edge the tab
     * row stands on, upward from the bottom edge the bottom rule stands
     * on — and never past {@code limit}, the opposite
     * edge. That vertical fade is then masked by a second, horizontal
     * one — full at the band's left edge, nothing at its right, across
     * the whole width — the two multiplied, so the shade is strongest in
     * the left corner and thins out to the right. It is drawn as a fine
     * mesh with the opacity worked out at every vertex: a single shaded
     * quad would put a visible seam along its diagonal.
     */
    private static void drawEdgeFade(float left, float right, float edge,
                                     float limit, float height, int alpha) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (safeAlpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        boolean downward = limit > edge;
        float far = downward
                ? Math.min(limit, edge + height)
                : Math.max(limit, edge - height);
        // The caller passes exactly the backdrop band's span, so the two
        // ramps coincide.
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
     * Whether the message directly below (newer than) the grouped line
     * at {@code lineIndex} continues the same run: its first visual
     * line — the first different-id line met walking down the list —
     * also opens with a group marker. A run can only be broken by an
     * ungrouped line between, so adjacency is all there is to check.
     */
    private static boolean groupRunContinuesBelow(List<ChatLine> lines,
                                                  int lineIndex) {
        int id = lines.get(lineIndex).getChatLineID();
        int index = lineIndex - 1;
        while (index >= 0 && lines.get(index) != null
                && lines.get(index).getChatLineID() == id) {
            index--;
        }
        return index >= 0 && lines.get(index) != null
                && ChatGroupMarker.isGroupedLine(
                        lines.get(index).func_151461_a());
    }

    /**
     * Where the junction's stem stands: centred under the header's
     * head — the opening bracket's six pixels, the slot's one-pixel
     * inset, and half the eight-pixel face.
     */
    private static final float JUNCTION_STEM_X = 11.0F;

    /**
     * The run's name colour for its junctions, read from the header
     * above: the first line up the list carrying a head marker (the
     * header's own first line) or a colour-carrying indent marker (one
     * of its wrapped lines). The shared muted mauve stands in when the
     * header has left the history.
     */
    private static int groupJunctionColor(List<ChatLine> lines,
                                          int lineIndex) {
        int limit = Math.min(lines.size(), lineIndex + 33);
        for (int index = lineIndex + 1; index < limit; index++) {
            ChatLine line = lines.get(index);
            if (line == null) {
                continue;
            }
            for (Object value : line.func_151461_a()) {
                if (!(value instanceof IChatComponent)) {
                    continue;
                }
                IChatComponent part = (IChatComponent)value;
                ChatHeadMarker.Data head = ChatHeadMarker.decode(part);
                if (head != null) {
                    return head.nameColor;
                }
                ChatLayoutMarker.Data layout = ChatLayoutMarker.decode(part);
                if (layout != null && layout.hasColors()) {
                    return layout.nameColor;
                }
            }
        }
        return LostTalesColors.rgb(LostTalesColors.MAUVE);
    }

    /**
     * The grouping junction, drawn in the slot the grouped line
     * reserves — placeholder art built from rectangles until authored
     * sprites take its place: an elbow on the run's last grouped
     * message, and a tee whose stem runs on through the band while
     * another grouped message follows below, so a run's junctions
     * connect into one line down the margin, centred under the
     * header's head and wearing the run's name colour. Drawn in the
     * text's local space, with the chat's standard shadow under it.
     */
    private static void drawGroupJunction(boolean continues, int color,
                                          int alpha) {
        if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        int shadow = LostTalesChatVisualStyle.shadowAlpha(alpha);
        if (shadow > 0) {
            drawGroupJunctionShape(continues,
                    LostTalesChatVisualStyle.SHADOW_OFFSET,
                    (shadow << 24) | LostTalesChatVisualStyle.SHADOW);
        }
        drawGroupJunctionShape(continues, 0.0F,
                (alpha << 24) | (color & 0xFFFFFF));
    }

    private static void drawGroupJunctionShape(boolean continues,
                                               float offset, int argb) {
        float bandTop = TEXT_OFFSET - LINE_HEIGHT + offset;
        float bandBottom = TEXT_OFFSET + offset;
        float arm = bandTop + 5.0F;
        float stem = JUNCTION_STEM_X + offset;
        // The stem hangs from the band top toward the run above; a tee
        // carries it on through the band bottom to the junction below.
        fillRect(stem, bandTop, stem + 1.0F,
                continues ? bandBottom : arm + 1.0F, argb);
        fillRect(stem, arm, stem + 4.0F, arm + 1.0F, argb);
        fillRect(stem + 2.0F, arm - 1.0F, stem + 3.0F, arm, argb);
        fillRect(stem + 2.0F, arm + 1.0F, stem + 3.0F, arm + 2.0F,
                argb);
    }

    /**
     * The unread divider's row, Discord-style: a crimson rule on the
     * row's centre, strongest beside the date standing in a gap at the
     * middle and falling off to nothing at the sides, starting clear of
     * the timestamp column. {@code top} is the row's top edge in the
     * caller's stack space.
     */
    private static void drawUnreadDivider(FontRenderer font,
                                          ChatTimestampColumn columns,
                                          float panelLeft, float panelRight,
                                          float top, String label,
                                          int alpha) {
        if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        float left = panelLeft + 3.0F + (columns.enabled
                ? columns.separatorX() + ChatTimestampColumn.SEPARATOR_WIDTH
                : 0.0F);
        float right = panelRight - 3.0F;
        if (right <= left) {
            return;
        }
        float ruleTop = top + 5.0F;
        int textWidth = label.length() == 0 ? 0
                : font.getStringWidth(label);
        if (textWidth > 0 && textWidth < right - left - 24.0F) {
            // Everything is anchored on the date's whole-pixel x, so the
            // gap is exactly three empty columns on either side whatever
            // fraction the window's centre falls on; the font's measured
            // width carries one trailing spacing column, so the right
            // rule starts one short of width-plus-three.
            int textX = Math.round((left + right - textWidth) / 2.0F);
            float gapLeft = textX - 3.0F;
            float gapRight = textX + textWidth + 2.0F;
            drawHorizontalFade(left, gapLeft, ruleTop,
                    UNREAD_DIVIDER_RGB, 0, alpha);
            drawHorizontalFade(gapRight, right, ruleTop,
                    UNREAD_DIVIDER_RGB, alpha, 0);
            // Each half's starting pixel — where the rule is strongest,
            // beside the date — carries a small cap: one pixel above
            // and one below it, so the rule opens toward the date the
            // way Discord's does.
            int cap = (alpha << 24) | UNREAD_DIVIDER_RGB;
            fillRect(gapLeft - 1.0F, ruleTop - 1.0F, gapLeft, ruleTop, cap);
            fillRect(gapLeft - 1.0F, ruleTop + 1.0F, gapLeft,
                    ruleTop + 2.0F, cap);
            fillRect(gapRight, ruleTop - 1.0F, gapRight + 1.0F, ruleTop,
                    cap);
            fillRect(gapRight, ruleTop + 1.0F, gapRight + 1.0F,
                    ruleTop + 2.0F, cap);
            LostTalesChatVisualStyle.drawColored(font, label, textX,
                    Math.round(top + 2.0F), UNREAD_DIVIDER_RGB, alpha);
        } else {
            // No room for the date: the rule alone, strongest at the
            // centre exactly as the halves would meet.
            float centre = (left + right) / 2.0F;
            drawHorizontalFade(left, centre, ruleTop,
                    UNREAD_DIVIDER_RGB, 0, alpha);
            drawHorizontalFade(centre, right, ruleTop,
                    UNREAD_DIVIDER_RGB, alpha, 0);
        }
    }

    /**
     * One pixel row of colour whose opacity runs from {@code leftAlpha}
     * to {@code rightAlpha} across its width: the divider's fade, built
     * exactly like the edge fades' shaded quads.
     */
    private static void drawHorizontalFade(float left, float right,
                                           float top, int rgb,
                                           int leftAlpha, int rightAlpha) {
        if (right <= left || Math.max(leftAlpha, rightAlpha)
                < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        // Same winding as the backdrop: the GUI pass culls back faces.
        tessellator.setColorRGBA_I(rgb, rightAlpha);
        tessellator.addVertex(right, top + 1.0F, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.setColorRGBA_I(rgb, leftAlpha);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.addVertex(left, top + 1.0F, 0.0D);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** Edge of the jump-to-present button's square. */
    private static final int JUMP_BUTTON_SIZE = 12;

    /**
     * The jump-to-present button of a scrolled-back view: a small square
     * with a downward arrow at the panel's right edge, flying in from
     * below the bottom rule as {@code jumpButtonProgress} rises — the
     * bar drawn over the strip hides whatever still lies beyond the rule
     * — and sliding back out when the view comes home. Drawn in the
     * window's local space; the screen rectangle it lands on is recorded
     * on the frame, so the click resolves against exactly what is on
     * screen.
     */
    private static void drawJumpButton(ChatWindowFrame frame,
                                       float panelRight, float bottomEdge,
                                       int alpha, float originX,
                                       float originY, float scale) {
        if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        float slide = (1.0F - Math.min(1.0F, frame.jumpButtonProgress))
                * (JUMP_BUTTON_SIZE + 3.0F);
        float right = panelRight - 2.0F;
        float left = right - JUMP_BUTTON_SIZE;
        float top = bottomEdge - 1.0F - JUMP_BUTTON_SIZE + slide;
        float bottom = top + JUMP_BUTTON_SIZE;
        fillRect(left, top, right, bottom,
                (Math.round(alpha * 0.92F) << 24)
                        | LostTalesChatVisualStyle.SURFACE_RGB);
        // A honey outline says it is a control, the way the tabs'
        // accents do.
        int outline = (alpha << 24)
                | LostTalesColors.rgb(LostTalesColors.HONEY);
        fillRect(left, top, right, top + 1.0F, outline);
        fillRect(left, bottom - 1.0F, right, bottom, outline);
        fillRect(left, top + 1.0F, left + 1.0F, bottom - 1.0F, outline);
        fillRect(right - 1.0F, top + 1.0F, right, bottom - 1.0F, outline);
        // The arrow: a pixel chevron pointing down, centred.
        int ivory = (alpha << 24) | LostTalesChatVisualStyle.IVORY;
        float centre = left + JUMP_BUTTON_SIZE / 2.0F;
        float arrowTop = top + 4.0F;
        fillRect(centre - 3.0F, arrowTop, centre + 3.0F,
                arrowTop + 1.0F, ivory);
        fillRect(centre - 2.0F, arrowTop + 1.0F, centre + 2.0F,
                arrowTop + 2.0F, ivory);
        fillRect(centre - 1.0F, arrowTop + 2.0F, centre + 1.0F,
                arrowTop + 3.0F, ivory);
        frame.jumpPillLeft = originX + left * scale;
        frame.jumpPillTop = originY + top * scale;
        frame.jumpPillRight = originX + right * scale;
        // The clip cuts the flying-in button on the bottom rule; the
        // hitbox ends where the pixels do.
        frame.jumpPillBottom = Math.min(originY + bottom * scale,
                originY + bottomEdge * scale);
    }

    /**
     * Tiles the empty-rows hatch over one region, tinted the backdrop's
     * plum black. Whole tiles first, the partial last column and row cut
     * by their UVs, so the pattern never stretches and the region's edge
     * never samples texels beyond it. One texture bind and a handful of
     * quads, only while the window actually has empty rows.
     */
    private static void drawEmptyRowsHatch(Minecraft minecraft, float left,
                                           float top, float right,
                                           float bottom, int alpha) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (safeAlpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA
                || right <= left || bottom <= top) {
            return;
        }
        minecraft.getTextureManager().bindTexture(EMPTY_HATCH_TEXTURE);
        LostTalesChatVisualStyle.beginContent();
        GL11.glColor4f((CHAT_BACKDROP_RGB >> 16 & 255) / 255.0F,
                (CHAT_BACKDROP_RGB >> 8 & 255) / 255.0F,
                (CHAT_BACKDROP_RGB & 255) / 255.0F, safeAlpha / 255.0F);
        try {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            for (float y = top; y < bottom; y += EMPTY_HATCH_TILE) {
                float y1 = Math.min(bottom, y + EMPTY_HATCH_TILE);
                float v1 = (y1 - y) / EMPTY_HATCH_TILE;
                for (float x = left; x < right; x += EMPTY_HATCH_TILE) {
                    float x1 = Math.min(right, x + EMPTY_HATCH_TILE);
                    float u1 = (x1 - x) / EMPTY_HATCH_TILE;
                    tessellator.addVertexWithUV(x, y1, 0.0D, 0.0D, v1);
                    tessellator.addVertexWithUV(x1, y1, 0.0D, u1, v1);
                    tessellator.addVertexWithUV(x1, y, 0.0D, u1, 0.0D);
                    tessellator.addVertexWithUV(x, y, 0.0D, 0.0D, 0.0D);
                }
            }
            tessellator.draw();
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /**
     * The history's own backdrop for one row: the same plum black at the
     * same opacity, fading out to the right the same way. A window's
     * rules stand on this rather than on whatever is behind the window,
     * so a hairline reads as the window's edge instead of as a line
     * across the world.
     */
    static void drawBackdropRow(float left, float top, float right,
                                float bottom, int alpha) {
        drawChatBackdrop(left, top, right, bottom, alpha,
                CHAT_BACKDROP_RGB);
    }

    /**
     * The opacity a window's backdrop is drawn at, before the opening
     * fade is applied to it. Both rules ask here, so the row each of
     * them stands on is the same one the messages between them lie on.
     */
    static int backdropRowAlpha(Minecraft minecraft) {
        float opacity = minecraft.gameSettings.chatOpacity * 0.9F + 0.1F;
        return Math.max(0, Math.min(255, Math.round(255.0F * opacity))) / 2;
    }

    /**
     * The chat's rule: a band of the chat's ivory between {@code left}
     * and {@code right}, opaque at the centre and fading to nothing at
     * either end, so the edges the messages stand between read as
     * edges. The tab row draws the window's top rule with this as its
     * last pixel row; the bottom rule is drawn directly under the
     * baseline as the bar strip's first row.
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

    /**
     * The vertical counterpart of {@link #drawRule}: a one-pixel-wide
     * column of the chat's ivory between {@code top} and {@code bottom},
     * opaque at its vertical centre and fading to nothing toward both
     * ends. The timestamp column's separator is drawn with this.
     */
    static void drawVerticalRule(float left, float right, float top,
                                 float bottom, int alpha) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (safeAlpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA
                || right <= left || bottom <= top) {
            return;
        }
        float centre = (top + bottom) / 2.0F;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        // Same winding as the backdrop: the GUI pass culls back faces.
        // Top half: transparent end to opaque centre.
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, safeAlpha);
        tessellator.addVertex(left, centre, 0.0D);
        tessellator.addVertex(right, centre, 0.0D);
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, 0);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        // Bottom half: opaque centre to transparent end.
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, 0);
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, safeAlpha);
        tessellator.addVertex(right, centre, 0.0D);
        tessellator.addVertex(left, centre, 0.0D);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * The line's timestamp runs, drawn in the column: each run keeps the
     * colour and the decorations it was composed with — the sand, the
     * italic time — exactly as the inline header once drew them, only in
     * the column's own place. Only a message's first line carries them,
     * so a message is stamped once.
     */
    private static void drawTimestampRuns(FontRenderer font,
                                          IChatComponent line, int x,
                                          int y, int alpha) {
        if (font == null || line == null
                || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        int cursor = x;
        boolean colours = LostTalesChatVisualStyle.chatColoursEnabled();
        for (Object value : line) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            if (!ChatPrefixMarker.isTimestamp(part)) {
                continue;
            }
            String text = part.getUnformattedTextForChat();
            String formatting = part.getChatStyle().getFormattingCode();
            String rendered;
            int rgb;
            if (!colours) {
                rendered = LostTalesChatVisualStyle.stripCodes(
                        formatting + text);
                rgb = LostTalesChatVisualStyle.IVORY;
            } else {
                Integer color = ChatPrefixMarker.decode(part);
                rendered = LostTalesChatVisualStyle.styleCodesOnly(formatting)
                        + LostTalesChatVisualStyle.removeColorCodes(text);
                rgb = color != null ? color.intValue()
                        : LostTalesChatVisualStyle.IVORY;
            }
            LostTalesChatVisualStyle.drawColored(font, rendered, cursor, y,
                    rgb, alpha);
            cursor += font.getStringWidth(rendered);
        }
    }

    /**
     * The stretch of the window's left frame edge above the input bar:
     * one pixel of ivory just inside its left border, part of one linear
     * ramp that is strongest at the window's bottom-left corner and
     * gone at its top. The stretch beside the bar belongs to the bar and
     * is drawn with it — over the bar's own fill, so nothing darkens it
     * — as is the bottom edge; both ride the bar's entrance.
     */
    static void drawWindowLeftEdge(Minecraft minecraft, ChatWindowFrame frame,
                                   LostTalesGuiAnimationSample opening) {
        if (minecraft == null || frame == null || !frame.drawn
                || opening == null) {
            return;
        }
        int alpha = Math.round(255.0F * opening.getOpacity());
        if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        float left = (float)frame.drawnLeft();
        float top = (float)(frame.boxTop + frame.motionY);
        float rampBottom = (float)(frame.boxBottom + frame.motionY) - 1.0F;
        drawLeftEdgeSegment(left, top, (float)frame.barTop(), rampBottom,
                rampBottom - top, alpha);
    }

    /**
     * One stretch of a window's left frame edge: a one-pixel column from
     * {@code top} to {@code bottom} whose per-row opacity follows the
     * window's whole ramp — {@code alpha} at {@code rampBottom}, nothing
     * {@code rampSpan} above it — so the window's part and the bar's
     * part read as one unbroken edge.
     */
    static void drawLeftEdgeSegment(float x, float top, float bottom,
                                    float rampBottom, float rampSpan,
                                    int alpha) {
        if (bottom <= top || rampSpan <= 0.0F
                || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        int topAlpha = rampAlpha(alpha, rampBottom, rampSpan, top);
        int bottomAlpha = rampAlpha(alpha, rampBottom, rampSpan, bottom);
        if (Math.max(topAlpha, bottomAlpha)
                < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        // Same winding as the backdrop: the GUI pass culls back faces.
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY,
                bottomAlpha);
        tessellator.addVertex(x, bottom, 0.0D);
        tessellator.addVertex(x + 1.0F, bottom, 0.0D);
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, topAlpha);
        tessellator.addVertex(x + 1.0F, top, 0.0D);
        tessellator.addVertex(x, top, 0.0D);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** The ramp's opacity at {@code y}; clamped, linear. */
    private static int rampAlpha(int alpha, float rampBottom, float rampSpan,
                                 float y) {
        float share = 1.0F - (rampBottom - y) / rampSpan;
        return Math.round(alpha
                * Math.max(0.0F, Math.min(1.0F, share)));
    }

    /**
     * The bottom frame edge a window's input bar carries as its last
     * pixel row: ivory at the left, fading linearly to nothing at the
     * right. Drawn by the bar — active or inactive — inside the bar's
     * own transform, so it enters with the bar's fly-in.
     */
    static void drawBarBottomEdge(float left, float right, float top,
                                  int alpha) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (safeAlpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA
                || right <= left) {
            return;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        // Same winding as the backdrop: the GUI pass culls back faces.
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, safeAlpha);
        tessellator.addVertex(left, top + 1.0F, 0.0D);
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, 0);
        tessellator.addVertex(right, top + 1.0F, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, safeAlpha);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** A flat quad at fractional edges; {@code Gui.drawRect} is whole. */
    private static void fillRect(float left, float top, float right,
                                 float bottom, int argb) {
        int alpha = argb >>> 24;
        if (alpha <= 0 || right <= left || bottom <= top) {
            return;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(argb & 0xFFFFFF, alpha);
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.draw();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /**
     * The opacity a window's panel and its lines share while the chat
     * screen is open: the game's chat opacity, carried by the opening
     * fade. Every line is at it, so the panel can be one piece.
     */
    private static int backdropAlpha(float opacity,
                                     LostTalesGuiAnimationSample opening) {
        return Math.max(0, Math.min(255,
                Math.round(255.0F * opacity * opening.getOpacity())));
    }

    static float backdropFadeStart(float width) {
        return Math.max(0.0F, width * BACKDROP_FADE_START);
    }

    /** As above in whole units, which is how the layout measures it. */
    static int backdropFadeStart(int width) {
        return Math.max(0, Math.round(width * BACKDROP_FADE_START));
    }

    /**
     * Palette backdrop band with a smooth transparent right edge. Edges
     * are fractional: a band has to meet its neighbour and the window's
     * rules exactly, and a stack moved by a scroll lands between whole
     * units of the window's own space.
     */
    private static void drawChatBackdrop(
            float left, float top, float right, float bottom, int alpha,
            int backdropRgb) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (right <= left || bottom <= top || safeAlpha <= 0) {
            return;
        }
        float fadeStart = Math.min(right,
                left + backdropFadeStart(right - left));
        fillRect(left, top, fadeStart, bottom,
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
        // (kept together with the solid half below)
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
            if (ChatPrefixMarker.isHidden(part, chatOpen)) {
                continue;
            }
            ChatHeadMarker.Data marker = ChatHeadMarker.decode(part);
            if (marker != null) {
                float opacity = alpha / 255.0F;
                if (marker.isDiscordSender()) {
                    // A line from the bridge has no account behind it;
                    // the Discord mark stands where the head would,
                    // drawn 1:1 — its slot is declared two pixels wider
                    // than a head's, so it keeps the same clear pixels
                    // either side instead of eating into them. Centred
                    // in the line band exactly as an inline emoji is.
                    float markTop = y - HEAD_TOP_OFFSET
                            + ChatInlineIcons.CONTENT_TOP_OFFSET;
                    ChatEmojiRenderer.drawShadow(minecraft,
                            ChatEmoji.DISCORD,
                            x + HEAD_LEFT_OFFSET
                                    + LostTalesChatVisualStyle.SHADOW_OFFSET,
                            markTop + LostTalesChatVisualStyle.SHADOW_OFFSET,
                            ChatEmoji.SPRITE_SIZE,
                            LostTalesChatVisualStyle.SHADOW,
                            Math.round(alpha
                                    * LostTalesChatVisualStyle.SHADOW_OPACITY));
                    ChatEmojiRenderer.draw(minecraft, ChatEmoji.DISCORD,
                            x + HEAD_LEFT_OFFSET, markTop,
                            ChatEmoji.SPRITE_SIZE, alpha);
                    return;
                }
                drawHeadShadow(minecraft, marker, x, y,
                        opacity * LostTalesChatVisualStyle.SHADOW_OPACITY);
                if (marker.npcIdentity) {
                    LostTalesCharacterHeadIconRenderer.drawNpcHead(
                            minecraft, marker.skinId,
                            x + HEAD_LEFT_OFFSET, y, 8.0F, 1.0F, opacity);
                } else if (marker.accountIdentity) {
                    LostTalesCharacterHeadIconRenderer.drawAccountHead(
                            minecraft, marker.senderId,
                            x + HEAD_LEFT_OFFSET, y, 8.0F, 1.0F, opacity);
                } else {
                    LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                            minecraft, marker.senderId, marker.skinId,
                            x + HEAD_LEFT_OFFSET, y, 8.0F, 1.0F, opacity);
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
        float shadowX = x + HEAD_LEFT_OFFSET
                + LostTalesChatVisualStyle.SHADOW_OFFSET;
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
        // A wrapped line has no head of its own; the indent that opens it
        // carries the sender's colours instead.
        for (Object value : line) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            ChatLayoutMarker.Data layout = ChatLayoutMarker.decode(
                    (IChatComponent)value);
            if (layout != null && layout.hasColors()) {
                return ChatHeadMarker.colorsOnly(layout.nameColor,
                        layout.titleColor);
            }
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
