package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatDeliveryMark;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.gui.animation.LostTalesUiEasing;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiRegionBlur;
import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ScaledResolution;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.GL14;

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
    /** Width of the solid bar a mention wears on the window's left edge. */
    private static final float MENTION_BAR_WIDTH = 1.0F;
    /** The unread divider's rule and date: the palette's red. */
    private static final int UNREAD_DIVIDER_RGB =
            LostTalesColors.rgb(LostTalesColors.CRIMSON);
    /**
     * Vertical distance between chat lines: the 10px emoji sprite, which
     * vanilla's 9px stride cannot contain, and a clear row above and
     * below it. Bands are contiguous — each line's backdrop fills the
     * full stride.
     */
    static final int LINE_HEIGHT = 12;
    /** Height of the font's capitals, the part of a glyph a row centres. */
    static final int GLYPH_CAP_HEIGHT = 7;
    /** Height of the content box every inline emoji, item and marker fills. */
    static final int CONTENT_BOX_HEIGHT = (int)ChatInlineIcons.CONTENT_SIZE;
    /** Height of a head's face, drawn one texel to one pixel. */
    static final int HEAD_SIZE = 8;
    /**
     * Where a row's text's top edge stands below the row's top edge: its
     * capitals centred in the row, two clear rows above them and three
     * below, the descender and its shadow taking the first two of those
     * three. Seven rows cannot be centred in twelve, so the capitals
     * stand half a pixel above the row's middle.
     *
     * <p>Everything else in the row is centred on those capitals
     * ({@link #centredBoxTop}), since the words are what the eye reads a
     * row by: a box of odd height lands on their middle exactly, and one
     * of even height, which cannot, stands half a pixel above it rather
     * than below.</p>
     */
    static final int ROW_TEXT_TOP = (LINE_HEIGHT - GLYPH_CAP_HEIGHT) / 2;
    /**
     * How far above a row's bottom edge its text's top edge stands.
     * Everything drawn against the text — heads, emoji, items,
     * timestamps — is placed from there; a reaction row centres its
     * chips in the row instead ({@link #reactionTextTop}).
     */
    static final int TEXT_OFFSET = LINE_HEIGHT - ROW_TEXT_TOP;
    /**
     * Where a divider's rule stands below its row's top edge at the
     * words' own size: on the middle row of the capitals a message's
     * text would have in the row, so the date written on the rule is
     * centred on it exactly. In an even row that is half a pixel above
     * the row's middle, as the capitals are, with the odd clear row below
     * the rule. Drawn as small text, the rule runs on the middle row of
     * the small capitals, which are centred on these.
     */
    static final int DIVIDER_RULE_OFFSET = ROW_TEXT_TOP + GLYPH_CAP_HEIGHT / 2;
    /**
     * Where a head sits against the text it stands beside: centred on the
     * capitals by the row's rule ({@link #centredBoxTop}), on whole
     * pixels — a head is pixel art at one texel to one pixel. Eight rows
     * against seven, so the face starts a row above the capitals and its
     * middle stands half a pixel above theirs.
     */
    static final float HEAD_TOP_OFFSET = centredBoxTop(HEAD_SIZE);

    /**
     * Where a box {@code height} pixels tall starts against the text's
     * top edge: centred on the capitals, half a pixel above their middle
     * when the two heights differ by an odd number of rows, and never
     * past the row's own edges, so a box as tall as the row fills it.
     * The ten-row content box of an emoji, an item or a marker starts two
     * rows above the text's top, an eight-row head one, and the six-row
     * speech bubble on it.
     */
    static int centredBoxTop(int height) {
        int top = Math.floorDiv(GLYPH_CAP_HEIGHT - height, 2);
        return Math.max(-ROW_TEXT_TOP,
                Math.min(LINE_HEIGHT - ROW_TEXT_TOP - height, top));
    }
    /**
     * Where the face sits inside the slot the head marker reserves: one
     * pixel in, so it has two clear either side — the glyph before it
     * lends one of those from its own trailing space.
     */
    private static final float HEAD_LEFT_OFFSET =
            ChatInlineIcons.HEAD_SLOT_INSET;
    /**
     * How a line's backdrop thins out across its width: it leans away
     * from the very first pixel along a curve that is flat where it
     * starts, so there is no corner anywhere to see — the eye reads a
     * corner in the opacity, not the opacity itself — and the total
     * opacity across the band is what a two-thirds plateau with a
     * straight fall-off would spend.
     */
    private static final int BACKDROP_FADE_POWER = 5;
    /**
     * Steps the curve is drawn in. A quad blends in a straight line
     * between its edges, so a curve is a run of short straight pieces,
     * as the edge fades are.
     */
    private static final int BACKDROP_FADE_STEPS = 32;
    /**
     * The curve sampled once, evenly across the band: the opacity at
     * each step's edge as a share of the backdrop's own. Shared with the
     * blur behind the band so the two thin out together.
     */
    static final float[] BACKDROP_FADE_WEIGHTS = backdropFadeWeights();
    /**
     * The opacity profile of a surface laid in one flat colour, as the
     * timestamp column is: the same at both ends.
     */
    private static final float[] FLAT_WEIGHTS = {1.0F, 1.0F};

    private static float[] backdropFadeWeights() {
        float[] weights = new float[BACKDROP_FADE_STEPS + 1];
        for (int step = 0; step < weights.length; step++) {
            float across = step / (float)BACKDROP_FADE_STEPS;
            float falloff = across;
            for (int power = 1; power < BACKDROP_FADE_POWER; power++) {
                falloff *= across;
            }
            weights[step] = 1.0F - falloff;
        }
        return weights;
    }
    /**
     * Depth of the shade hanging from the window's top edge: one line,
     * so the line passing under the top rule has faded most of the way
     * out before the clip cuts it.
     */
    static final float TOP_EDGE_FADE_HEIGHT = LINE_HEIGHT;
    /**
     * Depth of the shade hanging from the bottom edge: two lines, so it
     * reaches through the trailing strip and over the newest message.
     */
    private static final float BOTTOM_EDGE_FADE_HEIGHT = LINE_HEIGHT * 2.0F;
    /** Opacity of an edge fade on the edge it hangs from: a third. */
    static final int EDGE_FADE_ALPHA = Math.round(255.0F / 3.0F);
    /**
     * How long a line stays on screen in the closed feed, in the update
     * counter's own ticks: vanilla's own ten seconds, held to full
     * opacity for the first nine and falling to nothing over the last.
     * A line older than this is not drawn at all, which is also what
     * decides how long a run may go on there — see
     * {@link ChatGroupRuns}.
     */
    static final int FEED_FADE_TICKS = 200;
    /**
     * The opacity of the hatch laid over message rows the history does
     * not reach yet, at the middle of the hatched region: a third, since
     * it lies over the panel's own surface. The chat sheet's own hatch
     * cell, drawn in the colours it was authored in, falling off to
     * nothing at the region's top and bottom edges.
     */
    private static final int EMPTY_HATCH_ALPHA = Math.round(255.0F / 3.0F);
    /**
     * Mesh resolution of an edge fade. The horizontal ramp is linear, so
     * two columns carry it exactly; the vertical one is eased, and each
     * row is a straight segment of that curve, so the rows are what
     * decides whether the gradient bands. Against the blurred, flat
     * backdrop the chat opens over, a coarse ramp shows its seams, so
     * the curve is cut finely; one column keeps the quad count small.
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
            // Both per-window caches are let go together: the frame that
            // records where a window drew, and the lines it laid out for
            // itself.
            ChatWindowFrame.prune(windows);
            ChatWindowLines.prune(windows);
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
        // Only the stamps and marks this draw puts on screen answer the
        // pointer.
        frame.clearStamps();
        frame.clearMarks();
        List<ChatTab> tabs = ChatWindowFrame.visibleTabs(window);
        ChatTab view = ChatWindowFrame.activeTab(window, tabs);
        ChatLineFilter filter = ChatLineFilter.of(view);
        // An open window lays its own lines out: at its own width when
        // it has one, with the grouping its own tab's sequence gives,
        // and always without the channel prefix the shared history
        // reserves room for, which the open screen does not draw. Only
        // a window whose history cannot be read falls back to that
        // shared list.
        // A window filling a part of the screen, or gliding to or from
        // it, is laid out at the width it is drawn at this instant.
        frame.advanceFill(window.getFill());
        int chatWidth = ChatWindowPlacement.drawnChatWidth(window, minecraft,
                screenWidth);
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
        // The scroll range is taken from the rows the window will draw,
        // the unread divider's own row included, so the oldest message
        // can always be scrolled to. The rows are laid out here, before
        // the box is measured: a window following the game's chat height
        // is as tall as its stack, and the stack's blank rows are
        // shorter than a line.
        frame.resolveDividerRow(lines, view == null ? null
                : ClientChatChannelViews.unreadDividerLine(view));
        frame.resolveRows();
        frame.peakContentLines = Math.max(frame.peakContentLines,
                frame.contentLines());
        ChatWindowPlacement.Box box = ChatWindowPlacement.windowBounds(
                window, minecraft, screenWidth, screenHeight);
        float scale = chat.func_146244_h();
        frame.begin(box, scale, opening.getTranslationX(),
                opening.getTranslationY());
        // The frame's message room says how much of the stack the window
        // shows: the box's, laid on whole display pixels against the
        // drawn baseline. The room is the height the player dragged the
        // window to rather than a whole number of lines, so the topmost
        // line can be a partial one: it is drawn and clipped where the
        // room ends.
        float room = (float)frame.room;
        // A view scrolled back is put back on the message it is reading
        // before its offset is clamped, so a message arriving, a divider
        // opening or the window re-wrapping never moves the page under
        // the eye.
        ClientChatChannelViews.holdPosition(view, frame);
        double roomLines = frame.roomLines();
        double scroll = view == null ? 0.0D
                : ClientChatChannelViews.renderedScroll(view,
                        ClientChatChannelViews.getScroll(view,
                                frame.contentRows(), roomLines));
        frame.renderedScrollLines = scroll;
        frame.drawn = true;
        if (view != null) {
            // A conversation of a scoped channel read for the first time
            // asks for what was said in it before; a view scrolled to its
            // oldest line asks for the page before that. Both from here,
            // where the view and its lines are exactly what is drawn.
            String scope = ClientChatContextHistory.scopeOf(ChatTab.viewed(view));
            if (scope.length() > 0) {
                ClientChatContextHistory.request(ChatTab.viewed(view), scope);
            }
            double maximum = Math.max(0.0D,
                    frame.contentRows() - Math.max(1.0D, roomLines));
            ClientChatOlderHistory.requestIfAtTop(minecraft, view, lines,
                    maximum > 0.0D && scroll >= maximum - 0.01D);
        }
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
                BACKDROP_FADE_WEIGHTS, blurOpacity);
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
        drawWindow(minecraft, chat, frame, filter, lines, scroll, room,
                originX, originY, true, opening, chatWidth, columns);
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
     * channel prefixes that tell the channels apart. The feed lays its
     * own lines out, at the game's chat width, because a run here is
     * broken by whatever the feed itself shows between two of a
     * sender's messages — every channel interleaved, unlike a window;
     * only a feed whose history cannot be read falls back to the shared
     * list. Each window marks itself undrawn so nothing hit-tests
     * against a window that is not on screen.
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
        List<ChatLine> own = ChatWindowLines.forFeed(minecraft, chat, filter);
        List<ChatLine> lines = own != null ? own
                : ClientChatChannelViews.visibleLines(drawn, filter);
        frame.lines = lines;
        frame.view = null;
        frame.resolveDividerRow(lines, null);
        frame.resolveRows();
        // The frame is captured and blurred only while the feed has a
        // line still on screen; the rest of the time gameplay pays
        // nothing for the feed's blur.
        if (LostTalesConfig.enableChatBackgroundBlur
                && LostTalesConfig.enableGuiBackgroundBlur
                && !lines.isEmpty() && lines.get(0) != null
                && minecraft.ingameGUI.getUpdateCounter()
                        - lines.get(0).getUpdatedCounter()
                                < FEED_FADE_TICKS) {
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
                (float)frame.room,
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
            // Front to back, and no further than the window the point is
            // in: a window covered by another owns nothing under it, so
            // a line of it that happens to lie behind the front window
            // answers neither a hover nor a click. Windows that do not
            // overlap are unaffected — each still owns its own lines.
            List<ChatWindowFrame> frames = ChatWindowFrame.drawnFrames();
            for (int index = frames.size() - 1; index >= 0; index--) {
                ChatWindowFrame frame = frames.get(index);
                ChatLineBands bands = frame.bands;
                List<ChatLine> lines = frame.lines;
                if (lines != null && bands.describes(lines, lines.size())) {
                    int band = bands.find(mouseX, mouseY);
                    if (band >= 0) {
                        return new Band(frame, lines, bands.viewIndexOf(band),
                                bands.localX(band, mouseX), bands.topOf(band),
                                bands.bottomOf(band), bands.scale());
                    }
                }
                if (frame.contains(mouseX, mouseY)) {
                    return null;
                }
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
     * math does not match what is on screen. The position is
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
            int index = -1;
            for (Object value : lineRoot) {
                if (!(value instanceof IChatComponent)) {
                    continue;
                }
                index++;
                IChatComponent part = (IChatComponent)value;
                if (ChatPrefixMarker.isHidden(part, true)) {
                    continue;
                }
                int start = cursor;
                cursor += LostTalesChatVisualStyle.partWidth(
                        minecraft.fontRenderer, part, true);
                if (band.localX < cursor) {
                    return new Hit(part, lineRoot, index, band, start,
                            cursor - start);
                }
            }
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * A component under the pointer together with the wrapped line that
     * holds it and its place on that line, counted over every component
     * the line's iterator yields. The iterator hands out copies, so the
     * component is never the same object twice; the line is, and the
     * place tells the component apart from every other on it. The band
     * the row was found in and the run's own extent on it come along,
     * so whatever asks a finer question of the hit — where inside the
     * run the pointer stands — measures nothing again.
     */
    static final class Hit {
        final IChatComponent component;
        final IChatComponent line;
        final int index;
        /** The drawn row the run is on, the pointer mapped into its text space. */
        final Band band;
        /** Where the run starts on the row, in the row's unscaled text space. */
        final int partLeft;
        /** The width the run takes there. */
        final int partWidth;

        private Hit(IChatComponent component, IChatComponent line,
                    int index, Band band, int partLeft, int partWidth) {
            this.component = component;
            this.line = line;
            this.index = index;
            this.band = band;
            this.partLeft = partLeft;
            this.partWidth = partWidth;
        }

        /** The pointer's x in the row's text space. */
        float localX() {
            return this.band.localX;
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

    /**
     * Lines that fit the user's configured chat pixel height at this
     * chat's stride: vanilla counts that height in its own 9px lines.
     */
    static int visibleLineCount(GuiNewChat chat) {
        return Math.max(1, chat.func_146232_i() * 9 / LINE_HEIGHT);
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
        return beginClip(minecraft, Double.NaN, Double.NaN, topY, bottomY,
                inward);
    }

    /**
     * As {@link #beginVerticalClip}, cutting on any of the four sides.
     * Any edge may be {@code NaN}, meaning nothing is cut there.
     *
     * <p>The rectangle replaces whatever scissor is in force rather than
     * narrowing it, so a caller clipping inside another clip passes both
     * — the tab row hands its own bottom edge down to each tab, which
     * then adds its own two sides.</p>
     */
    static boolean beginClip(Minecraft minecraft, double leftX,
                             double rightX, double topY, double bottomY,
                             boolean inward) {
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
            // Scissor space counts rightward from the display's left, so
            // the horizontal edges need no flip; the rounding is the
            // same rule, inward giving up the pixel a fraction falls in.
            int left = Double.isNaN(leftX) ? 0 : (int)(inward
                    ? Math.ceil(leftX * pixelsPerGuiPixel
                            - CLIP_EDGE_EPSILON)
                    : Math.floor(leftX * pixelsPerGuiPixel
                            + CLIP_EDGE_EPSILON));
            int right = Double.isNaN(rightX) ? minecraft.displayWidth
                    : (int)(inward
                            ? Math.floor(rightX * pixelsPerGuiPixel
                                    + CLIP_EDGE_EPSILON)
                            : Math.ceil(rightX * pixelsPerGuiPixel
                                    - CLIP_EDGE_EPSILON));
            left = Math.max(0, left);
            right = Math.min(minecraft.displayWidth, right);
            if (bottom <= top || right <= left) {
                return false;
            }
            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(left, top, right - left, bottom - top);
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
            double scrollLines, float room, float restingX,
            float restingY, boolean open,
            LostTalesGuiAnimationSample opening, int chatWidth,
            ChatTimestampColumn columns) {
        // The offset is in rows and fractions of one: whole rows pick
        // where the stack starts, the fraction slides it by that much of
        // the row it is inside, and one more row is drawn so the gap the
        // slide opens is filled.
        int scrollPosition = (int)Math.floor(Math.max(0.0D, scrollLines));
        float scrollSlide = (float)(Math.max(0.0D, scrollLines)
                - scrollPosition);
        float eligibleHeight = 0.0F;
        int totalLineCount = lines.size();
        // The stack is measured in rows, not in lines: the unread
        // divider takes a row of its own between the last read message
        // and the first unread one, and everything older stands a row
        // higher for it. A line's row is worked out from where the
        // divider is rather than accumulated as the loop passes it, so
        // the stack lands in the same place whichever end of the
        // history the draw starts from. Rows are not all one height —
        // the blank row between two runs is ChatStackRows.SPACER_HEIGHT
        // — so every
        // distance up the stack is read from the frame's row geometry,
        // which is what the scroll ceiling and the scrollbar read too:
        // the three are one geometry.
        int dividerIndex = open ? frame.dividerLineIndex : -1;
        // A day's rule standing over the first unread message carries
        // the divider instead of a row being added for it.
        int dividerDateIndex = open ? frame.dividerDateLineIndex : -1;
        int dividerRows = dividerIndex >= 0 ? 1 : 0;
        int totalRowCount = totalLineCount + dividerRows;
        ChatStackRows rows = frame.rows;
        if (!rows.describes(lines, totalLineCount, dividerIndex)) {
            rows.reset(lines, dividerIndex);
        }
        int scrollRow = Math.min(scrollPosition, totalRowCount);
        // Pixels of stack under the baseline, and the slide's share of
        // the row the offset is inside.
        float stackBase = rows.top(scrollRow);
        float slidePixels = scrollSlide * rows.height(scrollRow);
        float totalHeight = rows.total();
        float opacity = LostTalesChatVisualStyle.chatOpacity(minecraft);
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
        // The closed feed stands its lines against the edge the client
        // chose; an open window's always start at its left.
        ChatFeedAlignment alignment = open ? ChatFeedAlignment.LEFT
                : ChatFeedAlignment.current();
        // Switching tabs is a hard cut for the lines: only the tabs
        // themselves ease. The stack rises with a new message only while
        // the window is still growing; full, it would only look like it
        // is trying to.
        float roomUnscaled = room / scale;
        boolean growing = totalHeight <= roomUnscaled + 0.01F;
        float originX = restingX;
        float originY = restingY;
        // Everything the message stack is moved by, and nothing else is:
        // the entrance of a new message, and the scroll's part of a
        // row. Rounded to whole display pixels, because the heads and
        // the emoji sprites are pixel art sampled one texel to one
        // pixel, and at a fraction of a pixel their texels crawl; a
        // display pixel is finer than a GUI pixel at every scale above
        // one, so the motion stays smooth.
        float stackOffset = snapToDisplayPixels(minecraft,
                (growing ? entryDisplacement(filter, scrollPosition) : 0.0F)
                        + slidePixels * scale);
        float offset = stackOffset / scale;

        // The topmost row the loop reaches, so the cut is known before
        // it runs: every row that starts below the room's top edge once
        // the stack is offset — the head-room band above the room and
        // the slide included, so the row the slide reveals is drawn and
        // clipped where the room ends.
        int lastRow = Math.min(totalRowCount - 1, rows.lastRowBelow(
                stackBase + roomUnscaled
                        + ChatWindowPlacement.HISTORY_TOP_MARGIN / scale
                        + Math.max(0.0F, offset)));
        // Height of the rows the loop is about to draw.
        float plannedHeight = lastRow < scrollRow ? 0.0F
                : rows.top(lastRow + 1) - stackBase;
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
        // The message area: from the timestamp column's separator, when
        // there is one, to the panel's right. The panel's backdrop and
        // every line's tint and shade stand in it and nowhere left of
        // it, so the column keeps a surface of its own and no two
        // backgrounds are ever laid over each other.
        float messageLeft = panelLeft
                + (columns.enabled ? columns.separatorX() : 0.0F);
        // The panel's opacity, shared by every stretch of it a line
        // recolours, so the stretch and the panel beside it are one.
        int panelAlpha = backdropAlpha(opacity, opening) / 2;
        // The timestamp column's opacity: the inset surface's two thirds,
        // thinning with the game's chat opacity as the panel does.
        int columnAlpha = Math.round(LostTalesChatVisualStyle.INSET_ALPHA
                * opacity * opening.getOpacity());
        // A window whose history can fill its room keeps its row on the
        // window's own top edge, so it does not move as the stack
        // scrolls under it — the trailing scroll room included; so does
        // one with a height of its own, whose empty rows are part of the
        // window. Only a window following the game setting and still
        // filling up carries its row down onto its last line.
        boolean full = fixedHeight || totalRowCount <= 0
                || totalHeight >= roomUnscaled - 0.01F;
        // Laid on a whole display pixel, like every edge the window is
        // drawn from: the tab row hangs from it, and a row standing
        // between two pixels would lose one to its own inward cut.
        frame.setStackTop(full ? restingY - room
                : ChatWindowFrame.snapToDisplayPixels(
                        restingY + stackOffset - plannedHeight * scale));

        // A scrolled view starts below the baseline: every row whose top
        // the scroll slid into the trailing strip, clipped where the
        // strip's reveal ends. A blank row in the strip leaves room for
        // part of the row under it.
        int firstRow = rows.firstRowShown(scrollRow, offset,
                (clipBottom - restingY) / scale);
        int firstLine = Math.max(0, lineOfRow(firstRow, dividerIndex));
        // The floating controls — the hovered message's toolbar and the
        // jump-to-present button — are placed before anything is drawn,
        // so the panel, the lines and the shades can leave them holes.
        ToolbarPlace toolbar = open ? placeToolbar(minecraft, lines,
                firstLine, lastRow, dividerIndex, rows, stackBase, opacity,
                opening) : null;
        int[] toolbarKinds = toolbar == null ? new int[0]
                : offeredToolbar(toolbar.chatLineId);
        // Clear of the scrollbar's track at the panel's right edge.
        float toolbarLeft = panelRight - 1.0F - SCROLLBAR_WIDTH
                - TOOLBAR_GAP - toolbarWidth(toolbarKinds.length);
        boolean jumpShown = open && advanceJumpButton(frame, scrollLines);
        // Centred across the panel, on a whole pixel.
        int jumpWidth = jumpButtonWidth(minecraft.fontRenderer);
        float jumpLeft = Math.round((panelLeft + panelRight - jumpWidth)
                / 2.0F);
        float jumpTop = jumpButtonTop(frame, bottomEdge, originY, scale);
        Holes holes = new Holes();
        for (int index = 0; index < toolbarKinds.length; index++) {
            holes.add(toolbarLeft + index * TOOLBAR_STRIDE,
                    toolbar.top + offset, TOOLBAR_BUTTON_SIZE,
                    TOOLBAR_BUTTON_SIZE, CONTROL_DEPTH, 1.0F);
        }
        if (jumpShown) {
            holes.add(jumpLeft, jumpTop, jumpWidth, JUMP_BUTTON_HEIGHT,
                    CONTROL_DEPTH, 1.0F);
        }
        if (open) {
            // The reaction chips are framed buttons too, in the stack.
            placeChipHoles(holes, minecraft, font, lines, firstLine,
                    lastRow, dividerIndex, rows, stackBase, opacity,
                    opening, alignment, unscaledWidth, offset);
        }

        GL11.glPushMatrix();
        boolean clipped = false;
        boolean masked = false;
        try {
            GL11.glTranslatef(originX, originY, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            masked = beginHoles(panelLeft, topEdge, panelRight, bottomEdge,
                    holes);
            if (open) {
                // One backdrop for the whole message area between the
                // rules, laid before the stack: a window has one panel,
                // and the messages are drawn on it. A band per line would
                // give every line an edge where a seam could open, and a
                // stack sliding under a scroll would open them. It thins
                // out along the window's whole width, so it fades the
                // same with the timestamp column or without it.
                drawChatBackdrop(panelLeft, messageLeft, topEdge,
                        panelRight, bottomEdge, panelAlpha,
                        LostTalesChatVisualStyle.backdropRgb());
                if (columns.enabled) {
                    // The timestamp column's own surface: the chat's
                    // inset surface, plum black at two thirds, the typing
                    // well's and a resting tab's, so the timestamps read
                    // as a margin rather than as part of the messages.
                    // The panel stops at the separator, so the two lie
                    // side by side, never one over the other. It thins
                    // with the game's chat opacity as the panel does.
                    fillRect(panelLeft, topEdge, messageLeft, bottomEdge,
                            LostTalesChatVisualStyle.argb(
                                    LostTalesChatVisualStyle.SURFACE_RGB,
                                    columnAlpha));
                }
                // Rows the history does not reach: hatched, so the
                // region reads as holding no messages rather than as a
                // gap. The hatch hangs from the panel's top and stops
                // one gap short of the stack's top — the space two
                // groups stand apart by, so the hatch reads as a stretch
                // of its own above the history rather than as part of
                // its first row — following the stack exactly, entry
                // motion included. Only a history too short for the room
                // has such rows; anything that can scroll fills it, so
                // the head-room band above a scrolled stack stays plain
                // panel. An empty view's invitation stands on the row
                // the newest message would take, and that row is a
                // message's row: the hatch keeps the same gap above it.
                float hatchedHeight = plannedHeight
                        + (totalLineCount <= 0 ? LINE_HEIGHT : 0.0F);
                if (totalHeight < roomUnscaled - 0.01F) {
                    ChatIconSheet.EMPTY_HATCH.drawTiledFadingFromMiddle(
                            columns.enabled
                                    ? panelLeft + columns.separatorX()
                                            + ChatTimestampColumn
                                                    .SEPARATOR_WIDTH
                                    : panelLeft,
                            topEdge, panelRight,
                            hatchBottom(offset, hatchedHeight),
                            Math.round(EMPTY_HATCH_ALPHA * opacity
                                    * opening.getOpacity()));
                }
            }
            // Only the message stack is offset; the panel and the shades
            // belong to the window's own edges and stay on them.
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(0.0F, offset, 0.0F);
                // Inward: a line never touches a rule row; whatever the
                // cut gives up shows the panel drawn unclipped above.
                clipped = open && beginVerticalClip(minecraft, clipTop,
                        clipBottom, true);
                String dividerLabel = unreadDividerLabel(frame, lines,
                        dividerIndex, dividerDateIndex);
                float smallScale = LostTalesChatVisualStyle.stackSmallScale();
                // The message whose lowest row of words has taken its
                // delivery mark: the stack walks upward, so that row is the
                // first of the message it reaches.
                int markedLineId = 0;
                // The line in the topmost slot owns the head-room above
                // it: its band reaches up to the rule instead of being
                // cut flush on its glyphs. A window with a fixed height
                // and empty rows has no line at its top, and neither has
                // one whose topmost row is the divider's, so nothing
                // there is extended.
                int topmostIndex = ChatStackRows.isDividerRow(lastRow,
                        dividerIndex) ? -1 : lineOfRow(lastRow, dividerIndex);
                for (int lineIndex = firstLine;
                     lineIndex < lines.size(); lineIndex++) {
                    int rowIndex = rowOfLine(lineIndex, dividerIndex);
                    if (rowIndex > lastRow) {
                        break;
                    }
                    ChatLine line = lines.get(lineIndex);
                    if (line == null) {
                        continue;
                    }
                    if (ChatWindowLines.isSpacer(line)
                            && !olderNeighbourShown(minecraft, lines,
                                    lineIndex, dividerIndex, lastRow, open)) {
                        // A blank row marks the gap between two runs; with
                        // the run above it gone or cut off there is no gap,
                        // and the row would read as an empty line over the
                        // topmost message.
                        continue;
                    }
                    int rowHeight = rows.height(rowIndex);
                    float headroom = open && lineIndex == topmostIndex
                            && (!fixedHeight
                                    || totalHeight >= roomUnscaled - 0.01F)
                            ? ChatWindowPlacement.HISTORY_TOP_MARGIN : 0.0F;
                    int age = minecraft.ingameGUI.getUpdateCounter()
                            - line.getUpdatedCounter();
                    if (age >= FEED_FADE_TICKS && !open) {
                        continue;
                    }
                    double fade = 1.0D - age / (double)FEED_FADE_TICKS;
                    fade = Math.max(0.0D,
                            Math.min(1.0D, fade * 10.0D));
                    fade *= fade;
                    int alpha = lineAlpha(open ? 255 : (int)(255.0D * fade),
                            line, opacity, opening);
                    eligibleHeight += rowHeight;
                    // The row's bottom edge, measured up the stack from
                    // the row the scroll rests on.
                    int y = -(rows.top(rowIndex) - Math.round(stackBase));
                    float entry = alignment.slide(entrySlide(line));
                    // A reply's quote and a message's reaction chips are
                    // the chat's small text: drawn shrunk from where the
                    // row's first run starts, and hit where they are.
                    float smallPivot = smallRowPivot(line.func_151461_a(),
                            open, smallScale);
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
                                + stackOffset + (y - rowHeight) * scale
                                - headroom);
                        float bandBottom = Math.min(clipBottom,
                                originY + stackOffset + y * scale);
                        if (bandBottom > bandTop) {
                            bands.add(lineIndex, bandLeft,
                                    bandLeft + unscaledWidth * scale, bandTop,
                                    bandBottom, Math.max(0.0F, smallPivot),
                                    smallPivot >= 0.0F ? smallScale : 1.0F);
                        }
                    }
                    if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
                        continue;
                    }
                    String dayLabel = ChatWindowLines.dateDividerLabel(line);
                    if (dayLabel != null) {
                        // A day's first message stands under a dated rule
                        // of its own, drawn like the unread divider in
                        // the chat's aside tone; the row is nobody's line.
                        // While that message is also the first unread
                        // one, the row is the unread divider — crimson,
                        // with the divider's words — and the date comes
                        // back once the divider goes.
                        boolean unreadHere = lineIndex == dividerDateIndex;
                        drawDividerRow(font, columns, panelLeft, panelRight,
                                y - rowHeight,
                                unreadHere ? dividerLabel : dayLabel,
                                unreadHere ? UNREAD_DIVIDER_RGB
                                        : LostTalesChatVisualStyle.asideRgb(),
                                alpha);
                        continue;
                    }
                    boolean dividerHere = lineIndex == dividerIndex;
                    int backdropRgb = LostTalesChatVisualStyle.backdropRgb();
                    boolean pinged = LostTalesChatPresentation.isPingedLine(
                            line.getChatLineID());
                    int mentionRgb = LostTalesChatVisualStyle.mentionLineRgb();
                    // A line a jump just landed on is lit over whatever
                    // else it wears, and fades out of it.
                    float flash = LostTalesChatPresentation.flashStrength(
                            line.getChatLineID());
                    if (open) {
                        // A search lights its matches the same way, the
                        // one stood on whole and the others a share.
                        flash = Math.max(flash,
                                ChatSearch.litShare(line.getChatLineID()));
                    }
                    boolean hoveredLine = open
                            && LostTalesChatPresentation.isHoveredLine(
                                    line.getChatLineID());
                    // The pointer's shade comes and goes on the same
                    // crossfade the controls answer the pointer with,
                    // rather than switching in a frame; so does the
                    // stamp it brings out below.
                    float hoverFade = open
                            ? LostTalesChatPresentation.lineHoverFade(
                                    line.getChatLineID(), hoveredLine)
                            : 0.0F;
                    if (open) {
                        // The open window has one panel behind every
                        // line, and a highlighted line is that panel in
                        // another colour: its stretch is recoloured in
                        // place — a mention's tint, a jump's flash, and
                        // under the pointer each of them a shade lighter
                        // — never laid over, from the separator on and
                        // thinning out with the panel. It stays where
                        // the panel is while the line's text slides in.
                        int bandRgb = lineBandRgb(backdropRgb,
                                LostTalesChatVisualStyle.selectedLineRgb(),
                                pinged, mentionRgb,
                                LostTalesChatVisualStyle
                                        .selectedMentionLineRgb(),
                                lineShare(line), flash,
                                LostTalesChatVisualStyle.replyHighlightRgb(),
                                LostTalesChatVisualStyle
                                        .selectedReplyHighlightRgb(),
                                hoverFade);
                        if (bandRgb != backdropRgb) {
                            recolourBackdrop(panelLeft, messageLeft,
                                    y - rowHeight - headroom / scale,
                                    panelRight, y, panelAlpha, backdropRgb,
                                    bandRgb);
                        }
                        if (columns.enabled && hoverFade > 0.0F) {
                            // The line under the pointer lights its row of
                            // the timestamp column too, so the stamp that
                            // belongs to it is found at a glance: the
                            // column's own surface recoloured in place, at
                            // the column's two thirds, toward the colour
                            // the line's panel lights to. The column is
                            // never what the pointer selects a line on.
                            int litRgb = lineBandRgb(backdropRgb,
                                    LostTalesChatVisualStyle.selectedLineRgb(),
                                    pinged, mentionRgb,
                                    LostTalesChatVisualStyle
                                            .selectedMentionLineRgb(),
                                    lineShare(line), flash,
                                    LostTalesChatVisualStyle.replyHighlightRgb(),
                                    LostTalesChatVisualStyle
                                            .selectedReplyHighlightRgb(),
                                    1.0F);
                            recolour(panelLeft, panelLeft,
                                    y - rowHeight - headroom / scale,
                                    messageLeft, y, columnAlpha,
                                    LostTalesChatVisualStyle.SURFACE_RGB,
                                    LostTalesChatVisualStyle.blend(
                                            LostTalesChatVisualStyle
                                                    .SURFACE_RGB,
                                            litRgb, hoverFade),
                                    FLAT_WEIGHTS);
                        }
                    }
                    // The closed feed has no panel — each of its lines
                    // fades on its own — so there each brings its own
                    // band, sliding with its text so a new message
                    // enters as one piece.
                    GL11.glPushMatrix();
                    GL11.glTranslatef(entry, 0.0F, 0.0F);
                    if (!open) {
                        // A feed line softens the world behind its own
                        // band: the blur rides the line, fades with its
                        // age, and thins away from where the lines stand
                        // like its backdrop. Without a fresh capture
                        // nothing is drawn.
                        LostTalesGuiRegionBlur.getInstance()
                                .drawFadedRegionInTransform(
                                        panelLeft, y - rowHeight,
                                        panelRight, y,
                                        alignment.bandWeights(),
                                        originX + entry * scale,
                                        originY + stackOffset, scale,
                                        alpha / 255.0F);
                    }
                    if (!open) {
                        // The colour the open window's panel would wear
                        // on the line — the backdrop, a mention's tint, a
                        // jump's flash crossing over either and back — at
                        // the panel's half opacity, fading with the line.
                        int replyRgb =
                                LostTalesChatVisualStyle.replyHighlightRgb();
                        int color = lineBandRgb(backdropRgb, backdropRgb,
                                pinged, mentionRgb, mentionRgb,
                                lineShare(line), flash, replyRgb, replyRgb,
                                0.0F);
                        drawChatBackdrop(panelLeft,
                                y - rowHeight - headroom / scale,
                                panelRight, y, alpha / 2, color,
                                alignment.bandWeights());
                    }
                    if (pinged && alignment.hasMentionBar()) {
                        // A mention also wears a solid bar on the edge
                        // the lines stand against — just past the
                        // timestamp column's separator, when there is
                        // one — the way Discord's does: the tint says
                        // the line, the bar says it at a glance from
                        // across the window. A feed of centred lines has
                        // no such edge and wears the tint alone.
                        float barLeft = alignment == ChatFeedAlignment.RIGHT
                                ? panelRight - MENTION_BAR_WIDTH
                                : panelLeft + (columns.enabled
                                        ? columns.separatorX()
                                                + ChatTimestampColumn
                                                        .SEPARATOR_WIDTH
                                        : 0.0F);
                        fillRect(barLeft, y - rowHeight - headroom / scale,
                                barLeft + MENTION_BAR_WIDTH, y,
                                (alpha << 24) | mentionRgb);
                    }
                    GL11.glPopMatrix();
                    if (dividerHere) {
                        // The unread divider's own row, directly above
                        // the first unread message: it rides the stack
                        // but not the line's entry slide, like the
                        // timestamps. The row holds the divider's line
                        // with the gap between runs on either side, so the
                        // rule stands as far from each group as two
                        // groups stand apart; the rows say where that
                        // line is.
                        int dividerBottom = -(rows.dividerLineBottom()
                                - Math.round(stackBase));
                        drawDividerRow(font, columns, panelLeft,
                                panelRight, dividerBottom - LINE_HEIGHT,
                                dividerLabel, UNREAD_DIVIDER_RGB, alpha);
                    }
                    // The line's timestamp lives in the column at the
                    // window's edge: it rides the stack's vertical
                    // motion and the line's fade, but not the entry
                    // slide — the column does not move sideways. Drawn
                    // after the line's band, so on a highlighted line
                    // the digits stand on the tint instead of being
                    // darkened under it. A message whose stamp the
                    // column leaves blank — the rest of a speaker's
                    // minute — shows it while the pointer rests on the
                    // message, the way Discord shows a grouped line's
                    // time on hover; only a message's first line carries
                    // a stamp, so a wrapped message is stamped once.
                    if (open && columns.enabled) {
                        boolean stamped = opensItsMinute(lines, lineIndex);
                        int stampAlpha = stamped ? alpha
                                : Math.round(alpha * hoverFade);
                        if ((stamped || hoverFade > 0.0F)
                                && timestampText(line.func_151461_a())
                                        .length() > 0) {
                            // The stamp stands at the middle of the whole
                            // message - every wrapped row of it, a reply's
                            // quote row included - not on the row that
                            // happens to carry it, so a name on one line
                            // and its words on the next are stamped
                            // between them, the way a reply's quote, name
                            // and words already were.
                            int stampX = Math.round(panelLeft)
                                    + columns.timestampX();
                            int stampY = y - TEXT_OFFSET + messageCentreShift(
                                    lines, lineIndex, rows, dividerIndex);
                            drawTimestampRuns(font, line.func_151461_a(),
                                    stampX, stampY, stampAlpha);
                            if (stamped) {
                                // Where the stamp stands on screen, for the
                                // tip that reads out its whole date; one the
                                // pointer only brings out goes as the
                                // pointer reaches for it, so it is left out.
                                recordStamp(frame, font, line, stampX,
                                        stampY, originX,
                                        originY + stackOffset, scale,
                                        clipTop, clipBottom);
                            }
                        }
                    }
                    GL11.glPushMatrix();
                    GL11.glTranslatef(entry, 0.0F, 0.0F);
                    GL11.glEnable(GL11.GL_BLEND);
                    IChatComponent component = line.func_151461_a();
                    float rowShift = feedRowShift(font, component, alignment,
                            unscaledWidth, smallPivot, smallScale);
                    // A reaction row's chips are centred in the row, which
                    // is as tall as they need; any other row's text stands
                    // on the row's own line.
                    boolean reactionRow =
                            ChatReactionMarker.isReactionRow(component);
                    GL11.glPushMatrix();
                    GL11.glTranslatef(rowShift, reactionRow
                            ? reactionTextTop(y, rowHeight,
                                    smallPivot >= 0.0F ? smallScale : 1.0F)
                            : y - (float)TEXT_OFFSET, 0.0F);
                    if (smallPivot >= 0.0F) {
                        // The row keeps where its first run starts and
                        // shrinks from there, its capitals centred on a
                        // message's and each of its pixels a small one.
                        GL11.glTranslatef(smallPivot * (1.0F - smallScale),
                                reactionRow ? 0.0F
                                        : LostTalesChatVisualStyle
                                                .stackSmallTopOffset(),
                                0.0F);
                        GL11.glScalef(smallScale, smallScale, 1.0F);
                    }
                    ChatHeadMarker.Data marker = findMarker(component);
                    LostTalesChatVisualStyle.drawFormatted(font,
                            component, marker, 0, 0, alpha, open);
                    drawHead(minecraft, font, component,
                            HEAD_TOP_OFFSET, alpha, open);
                    if (open && line.getChatLineID() != markedLineId
                            && !ChatWindowLines.isSpacer(line)
                            && !ChatReactionMarker.isReactionRow(component)
                            && !ChatReplyMarker.isQuoteRow(component)) {
                        // A line of the player's own the Discord bridge has
                        // not posted yet, or could not post, says so after
                        // the message's lowest row of words.
                        markedLineId = line.getChatLineID();
                        drawDeliveryMark(frame, font, component,
                                markedLineId, alpha,
                                panelRight - entry - rowShift,
                                originX + (entry + rowShift) * scale,
                                originY + stackOffset
                                        + (y - TEXT_OFFSET) * scale,
                                scale, clipTop, clipBottom);
                    }
                    GL11.glPopMatrix();
                    GL11.glPopMatrix();
                    GL11.glDisable(GL11.GL_ALPHA_TEST);
                }

            } finally {
                endVerticalClip(clipped);
                clipped = false;
                GL11.glPopMatrix();
            }
            if (!full) {
                // Rows the draw passed over: the stack ends lower than
                // the room allows, so the tab row follows it down. A
                // history short enough to leave room is drawn whole, so
                // the divider's row is among them.
                frame.setStackTop(ChatWindowFrame.snapToDisplayPixels(
                        restingY + stackOffset
                                - (eligibleHeight + (dividerIndex >= 0
                                        ? rows.height(dividerIndex + 1) : 0))
                                        * scale));
            }

            if (open && totalLineCount <= 0) {
                // The invitation, where the newest line would be, in the
                // chat's aside tone and in italics, trimmed to the message
                // area so a narrow window never lets it run out under its
                // edge.
                int inviteAlpha = Math.round(255.0F * opacity
                        * opening.getOpacity());
                GL11.glEnable(GL11.GL_BLEND);
                LostTalesChatVisualStyle.drawColored(font,
                        "§o" + font.trimStringToWidth(
                                StatCollector.translateToLocal(
                                        "gui.losttales.chat.empty"),
                                Math.max(20, Math.round(panelRight) - 4)),
                        0, -TEXT_OFFSET,
                        LostTalesChatVisualStyle.asideRgb(),
                        inviteAlpha);
            }
            if (open) {
                if (columns.enabled) {
                    // The separator stands over the lines, so a message
                    // sliding past never crosses it — but under the
                    // shades below, with the rest of the history: the
                    // rules are what the window ends on, and everything
                    // the history draws goes behind them.
                    drawVerticalRule(panelLeft + columns.separatorX(),
                            panelLeft + columns.separatorX()
                                    + ChatTimestampColumn.SEPARATOR_WIDTH,
                            topEdge, bottomEdge,
                            Math.round(255.0F * opening.getOpacity()));
                }
                int fadeAlpha = Math.round(EDGE_FADE_ALPHA * opacity
                        * opening.getOpacity());
                // The shades hang from the rules themselves, so a line
                // passing under one fades out before it is cut. They lie
                // over everything the history drew — its backdrop, the
                // hatch, the timestamp column and its separator, the
                // messages and the unread divider — and leave the
                // floating controls' holes as all of it does: those stand
                // on the window rather than in it, like the tab row and
                // the input bar the screen draws after this.
                // In front of the chips, so the shades fade them by the
                // rules as they fade the words beside them.
                GL11.glPushMatrix();
                GL11.glTranslatef(0.0F, 0.0F, SHADE_DEPTH);
                drawEdgeFade(panelLeft, panelRight, topEdge, bottomEdge,
                        TOP_EDGE_FADE_HEIGHT, fadeAlpha);
                drawEdgeFade(panelLeft, panelRight, bottomEdge, topEdge,
                        BOTTOM_EDGE_FADE_HEIGHT, fadeAlpha);
                GL11.glPopMatrix();
            }
            endHoles(masked);
            masked = false;
            frame.scrollbarRight = 0.0F;
            if (open) {
                // The thumb is sized in the stack's own pixels — the
                // room against the whole stack — and placed by how far
                // up it the offset stands, so a thumb dragged along the
                // track lands on the row under it whatever the rows
                // there measure.
                drawScrollbar(frame, panelRight, topEdge, bottomEdge,
                        totalHeight, roomUnscaled, stackBase + slidePixels,
                        opacity * opening.getOpacity(), originX, originY,
                        scale);
            }
            // The floating controls, each in the hole everything under it
            // left: the hovered message's toolbar, slid with the stack as
            // the row it belongs to is, and the jump-to-present button a
            // view scrolled away from the newest line grows at the panel's
            // foot, flying in from below the bottom rule. Both live inside
            // the history, cut on its rules by the clip that cuts a line,
            // so the button emerges through the rule instead of fading in
            // over it; both are recorded on the frame exactly as drawn, so
            // the click and the pixels cannot disagree.
            frame.toolbarLeft = 0.0F;
            frame.toolbarTop = 0.0F;
            frame.toolbarRight = 0.0F;
            frame.toolbarBottom = 0.0F;
            frame.toolbarKinds = new int[0];
            frame.toolbarChatLineId = 0;
            frame.jumpPillLeft = 0.0F;
            frame.jumpPillTop = 0.0F;
            frame.jumpPillRight = 0.0F;
            frame.jumpPillBottom = 0.0F;
            if (toolbar != null || jumpShown) {
                int controlAlpha = Math.round(255.0F * opacity
                        * opening.getOpacity());
                boolean controlsClipped = beginVerticalClip(minecraft,
                        clipTop, clipBottom, true);
                try {
                    if (toolbar != null) {
                        GL11.glPushMatrix();
                        try {
                            GL11.glTranslatef(0.0F, offset, 0.0F);
                            drawMessageToolbar(frame, toolbar.chatLineId,
                                    toolbarKinds, toolbarLeft, toolbar.top,
                                    controlAlpha, originX,
                                    originY + stackOffset, scale);
                        } finally {
                            GL11.glPopMatrix();
                        }
                    }
                    if (jumpShown) {
                        drawJumpButton(minecraft, frame, jumpLeft, jumpTop,
                                jumpWidth, bottomEdge, controlAlpha, originX,
                                originY, scale);
                    }
                } finally {
                    endVerticalClip(controlsClipped);
                }
            }
        } finally {
            endHoles(masked);
            GL11.glPopMatrix();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }
    }

    /**
     * Whether the line above a blank row — the older neighbour, one
     * further along the newest-first list — is on screen: still inside
     * the window's rows, and in the closed feed not yet faded. A blank
     * row stands for the gap between two runs, so with nothing above it
     * there is nothing to mark.
     */
    static boolean olderNeighbourShown(Minecraft minecraft,
                                       List<ChatLine> lines, int lineIndex,
                                       int dividerIndex, int lastRow,
                                       boolean open) {
        int older = lineIndex + 1;
        if (older >= lines.size() || lines.get(older) == null) {
            return false;
        }
        if (rowOfLine(older, dividerIndex) > lastRow) {
            return false;
        }
        if (open) {
            return true;
        }
        int age = minecraft.ingameGUI.getUpdateCounter()
                - lines.get(older).getUpdatedCounter();
        return age < FEED_FADE_TICKS;
    }

    /**
     * Which row of the stack a line stands on. The unread divider owns
     * the row directly above the message it divides at, so every line
     * older than that message — one further along vanilla's newest-first
     * list — stands one row higher than its index.
     */
    static int rowOfLine(int lineIndex, int dividerIndex) {
        return dividerIndex >= 0 && lineIndex > dividerIndex
                ? lineIndex + 1 : lineIndex;
    }

    /**
     * The line standing on a row, as the inverse of {@link #rowOfLine}.
     * The divider's own row carries no line; it answers with the line
     * below it, so a draw starting there begins one row early rather
     * than skipping the divider.
     */
    static int lineOfRow(int row, int dividerIndex) {
        return dividerIndex >= 0 && row > dividerIndex ? row - 1 : row;
    }

    /**
     * Where the hovered message's toolbar starts, for the message's
     * topmost row ending at {@code rowBottom}: its buttons are taller than
     * a row, so the toolbar is centred on the row's capitals, half a pixel
     * above their middle, and its react button's emoji stands exactly
     * where an emoji in the row stands.
     */
    static int toolbarTop(int rowBottom, int rowHeight) {
        return rowBottom - rowHeight + ROW_TEXT_TOP
                + Math.floorDiv(GLYPH_CAP_HEIGHT - TOOLBAR_BUTTON_SIZE, 2);
    }

    /**
     * Where the hatch over rows the history does not reach ends, in the
     * stack's own units above the resting baseline: one gap
     * ({@link ChatStackRows#SPACER_HEIGHT}) short of the stack's top,
     * which stands {@code hatchedHeight} above the baseline and moves
     * with the stack's {@code offset}; never below the baseline.
     */
    static float hatchBottom(float offset, float hatchedHeight) {
        return Math.min(0.0F,
                offset - hatchedHeight - ChatStackRows.SPACER_HEIGHT);
    }

    /**
     * How far the closed feed moves a row sideways for its alignment, in
     * the stack's units and on the display's own grid, so that the row's
     * ink, not its advance, meets the edge it stands against. A row drawn
     * as small text shrinks from where its first run starts, so its ink
     * ends where the shrink leaves it. Nothing for a row at the left.
     */
    private static float feedRowShift(FontRenderer font, IChatComponent row,
                                      ChatFeedAlignment alignment,
                                      float areaWidth, float smallPivot,
                                      float smallScale) {
        if (alignment == ChatFeedAlignment.LEFT || row == null
                || font == null) {
            return 0.0F;
        }
        float inkLeft = LostTalesChatVisualStyle.contentStart(row, false);
        float inkRight = LostTalesChatVisualStyle.inkEnd(font, row, false);
        if (smallPivot >= 0.0F) {
            inkRight = smallPivot + (inkRight - smallPivot) * smallScale;
        }
        return snapToStackPixel(alignment.rowShift(areaWidth, inkLeft,
                inkRight));
    }

    /**
     * Words drawn between two clip edges, thinning out into either edge
     * over {@code depth} pixels as far as {@code leftStrength} and
     * {@code rightStrength} say (0..1, how far the words have gone past
     * that edge): the stretch at a fading edge is drawn in one-pixel
     * slices at falling opacity, so the words themselves fade rather
     * than a wash being laid over them, and the fade is seamless over
     * whatever surface they stand on, as a browser's cut tab name is.
     * The clip edges are in screen space; {@code x} and {@code fraction}
     * are in the caller's matrix, and {@code clipBottom} may be NaN.
     */
    static void drawFadingText(Minecraft minecraft, FontRenderer font,
                               String text, int x, float fraction, int y,
                               int rgb, int alpha, double clipLeft,
                               double clipRight, double clipBottom,
                               float depth, float leftStrength,
                               float rightStrength) {
        if (clipRight <= clipLeft
                || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        double leftZone = leftStrength > 0.0F ? Math.min(depth,
                (clipRight - clipLeft) / 2.0D) : 0.0D;
        double rightZone = rightStrength > 0.0F ? Math.min(depth,
                (clipRight - clipLeft) / 2.0D) : 0.0D;
        // Every slice is one display pixel wide, its edges laid on the
        // display grid: the clip rounds inward, and two neighbours
        // meeting on a fraction of a pixel would each give that pixel
        // up and leave a gap in the words.
        int factor = ChatWindowFrame.displayScaleFactor();
        int leftSlices = (int)Math.round(leftZone * factor);
        int rightSlices = (int)Math.round(rightZone * factor);
        double leftEnd = ChatWindowFrame.snapToDisplayPixels(
                clipLeft + leftSlices / (double)factor);
        double rightStart = ChatWindowFrame.snapToDisplayPixels(
                clipRight - rightSlices / (double)factor);
        drawTextSlice(minecraft, font, text, x, fraction, y, rgb, alpha,
                leftEnd, rightStart, clipBottom);
        for (int slice = 0; slice < leftSlices; slice++) {
            // A slice's opacity is read at its middle: none at the very
            // edge for words fully gone past it, the whole at the zone's
            // inner end.
            float share = 1.0F - leftStrength
                    * (1.0F - (slice + 0.5F) / leftSlices);
            drawTextSlice(minecraft, font, text, x, fraction, y, rgb,
                    Math.round(alpha * share),
                    ChatWindowFrame.snapToDisplayPixels(
                            leftEnd - (leftSlices - slice) / (double)factor),
                    ChatWindowFrame.snapToDisplayPixels(
                            leftEnd - (leftSlices - slice - 1) / (double)factor),
                    clipBottom);
        }
        for (int slice = 0; slice < rightSlices; slice++) {
            float share = 1.0F - rightStrength
                    * (1.0F - (slice + 0.5F) / rightSlices);
            drawTextSlice(minecraft, font, text, x, fraction, y, rgb,
                    Math.round(alpha * share),
                    ChatWindowFrame.snapToDisplayPixels(
                            rightStart + (rightSlices - slice - 1) / (double)factor),
                    ChatWindowFrame.snapToDisplayPixels(
                            rightStart + (rightSlices - slice) / (double)factor),
                    clipBottom);
        }
    }

    /** The words once, cut to one stretch of screen. */
    private static void drawTextSlice(Minecraft minecraft, FontRenderer font,
                                      String text, int x, float fraction,
                                      int y, int rgb, int alpha,
                                      double clipLeft, double clipRight,
                                      double clipBottom) {
        if (clipRight <= clipLeft
                || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        boolean clipped = beginClip(minecraft, clipLeft, clipRight,
                Double.NaN, clipBottom, true);
        try {
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(fraction, 0.0F, 0.0F);
                LostTalesChatVisualStyle.drawColored(font, text, x, y, rgb,
                        alpha);
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            endVerticalClip(clipped);
        }
    }

    /**
     * How strongly words fade into an edge they are cut at: as far as
     * they have gone past the edge, up to the fade's depth. The fade
     * comes and goes with the marquee instead of appearing, and an edge
     * nothing is cut at has none.
     */
    static float sideFadeStrength(double hiddenPixels, float depth) {
        if (depth <= 0.0F || hiddenPixels <= 0.0D) {
            return 0.0F;
        }
        return (float)Math.min(1.0D, hiddenPixels / depth);
    }

    /**
     * How deep a side fade reaches into a clip {@code width} pixels wide:
     * one line, as the history's top shade does, but never past a third of
     * the clip, so a short name keeps its middle clear.
     */
    static float sideFadeDepth(double width) {
        return (float)Math.max(0.0D,
                Math.min(TOP_EDGE_FADE_HEIGHT, width / 3.0D));
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
    static void drawEdgeFade(float left, float right, float edge,
                             float limit, float height, int alpha) {
        drawEdgeFade(left, right, left, right, edge, limit, height, alpha,
                LostTalesChatVisualStyle.backdropRgb(), true);
    }

    /**
     * As above in {@code rgb}, drawing only the stretch from
     * {@code from} to {@code to} of the band from {@code left} to
     * {@code right}: what the tab strip draws in pieces, each tab's
     * stretch in the tab's own colour and the selected tab's left out.
     * {@code ramped} gives the band the history's left-to-right ramp,
     * one ramp across every piece; without it the shade is even across
     * the whole width, as the strip's is, so its far end shows the
     * shade as its near end does.
     */
    static void drawEdgeFade(float left, float right, float from, float to,
                             float edge, float limit, float height,
                             int alpha, int rgb, boolean ramped) {
        float drawLeft = Math.max(left, from);
        float drawRight = Math.min(right, to);
        if (right <= left || drawRight <= drawLeft) {
            return;
        }
        // The ramp is linear across the whole span, so the drawn stretch
        // takes its weight at either end from the span's own line.
        float[] columnX = {drawLeft, drawRight};
        float[] columnWeight = ramped
                ? new float[] {
                        1.0F - (drawLeft - left) / (right - left),
                        1.0F - (drawRight - left) / (right - left)}
                : new float[] {1.0F, 1.0F};
        drawEdgeFade(columnX, columnWeight, edge, limit, height, alpha, rgb);
    }

    /** Columns the bell of a tab's own shade is drawn in. */
    private static final int BELL_FADE_COLUMNS = 8;

    /**
     * A tab's own shade above the rule, in the tab's colour: the edge
     * fade with a bell across the tab's width, full at its middle and
     * nothing at either side, eased the way the vertical ramp is, so
     * the colour gathers under the tab's middle and is gone where the
     * tab meets its neighbours.
     */
    static void drawBellFade(float left, float right, float edge,
                             float limit, float height, int alpha, int rgb) {
        if (right <= left) {
            return;
        }
        float[] columnX = new float[BELL_FADE_COLUMNS + 1];
        float[] columnWeight = new float[columnX.length];
        for (int column = 0; column <= BELL_FADE_COLUMNS; column++) {
            float t = column / (float)BELL_FADE_COLUMNS;
            columnX[column] = left + (right - left) * t;
            columnWeight[column] = 1.0F - LostTalesChatMotion.smoothStep(
                    Math.abs(t - 0.5F) * 2.0F);
        }
        drawEdgeFade(columnX, columnWeight, edge, limit, height, alpha, rgb);
    }

    /**
     * The edge fade over the columns given — each with its own share of
     * the opacity, the shade blending straight between neighbours —
     * from {@code edge} toward {@code limit} for at most {@code height}.
     */
    private static void drawEdgeFade(float[] columnX, float[] columnWeight,
                                     float edge, float limit, float height,
                                     int alpha, int rgb) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (safeAlpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        boolean downward = limit > edge;
        int backdropRgb = rgb;
        float far = downward
                ? Math.min(limit, edge + height)
                : Math.max(limit, edge - height);
        if (far == edge) {
            return;
        }
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(true);
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
                tessellator.setColorRGBA_I(backdropRgb,
                        Math.round(safeAlpha * h1 * v1));
                tessellator.addVertex(x1, y1, 0.0D);
                tessellator.setColorRGBA_I(backdropRgb,
                        Math.round(safeAlpha * h1 * v0));
                tessellator.addVertex(x1, y0, 0.0D);
                tessellator.setColorRGBA_I(backdropRgb,
                        Math.round(safeAlpha * h0 * v0));
                tessellator.addVertex(x0, y0, 0.0D);
                tessellator.setColorRGBA_I(backdropRgb,
                        Math.round(safeAlpha * h0 * v1));
                tessellator.addVertex(x0, y1, 0.0D);
            }
        }
        LostTalesSkyrimUiStyle.endQuads(tessellator, true);
    }

    /**
     * What the unread divider says: how many messages stand below it,
     * and the day its run began when that was not today. Messages from
     * an earlier day are dated; today's are simply counted. Empty
     * without a divider.
     */
    private static String unreadDividerLabel(ChatWindowFrame frame,
                                             List<ChatLine> lines,
                                             int dividerIndex,
                                             int dividerDateIndex) {
        if (dividerIndex < 0 && dividerDateIndex < 0) {
            return "";
        }
        // The divider's own row stands directly above the first unread
        // message's; a day's rule carrying it stands further up, past
        // its gap, and the count passes over filler rows.
        int firstUnread = dividerIndex >= 0 ? dividerIndex
                : dividerDateIndex - 1;
        int count = ChatWindowLines.messagesThrough(lines, firstUnread);
        String words = count == 1
                ? StatCollector.translateToLocal(
                        "gui.losttales.chat.unread_divider.one")
                : StatCollector.translateToLocalFormatted(
                        "gui.losttales.chat.unread_divider",
                        Integer.toString(count));
        long began = ClientChatChannelViews.unreadDividerTimestamp(
                frame.view);
        if (began > 0L && !ChatTimestampFormatter.isSameDay(began,
                System.currentTimeMillis())) {
            words = StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.unread_divider.dated", words,
                    ChatTimestampFormatter.formatDay(began));
        }
        return words;
    }

    /**
     * A divider's row, Discord-style: a rule in {@code rgb} on the row's
     * centre, strongest beside the date standing in a gap at the middle
     * and falling off to nothing at the sides, starting clear of the
     * timestamp column. The whole divider is the chat's small text — the
     * date, the rule it stands on and the rule's caps, each pixel of them
     * a small one — so the rule is as fine as the date's strokes. The
     * unread divider draws it in crimson, a day's first message in the
     * timestamps' colour. {@code top} is the row's top edge in the
     * caller's stack space.
     */
    private static void drawDividerRow(FontRenderer font,
                                       ChatTimestampColumn columns,
                                       float panelLeft, float panelRight,
                                       float top, String label, int rgb,
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
        // One small pixel, in the stack's units. The date's capitals are
        // centred on a message's, and the rule runs on their middle row.
        float pixel = Math.min(1.0F, LostTalesChatVisualStyle.stackSmallScale());
        float textTop = top + ROW_TEXT_TOP + (pixel < 1.0F
                ? LostTalesChatVisualStyle.stackSmallTopOffset() : 0.0F);
        float ruleTop = textTop + (GLYPH_CAP_HEIGHT / 2) * pixel;
        int textWidth = label.length() == 0 ? 0
                : font.getStringWidth(label);
        float drawnWidth = textWidth * pixel;
        if (textWidth > 0 && drawnWidth < right - left - 24.0F) {
            // Everything is anchored on the date's x, laid on a display
            // pixel, so the gap is exactly three empty small columns on
            // either side whatever fraction the window's centre falls
            // on; the font's measured width carries one trailing spacing
            // column, so the right rule starts one short of width-plus-
            // three.
            float textX = snapToStackPixel((left + right - drawnWidth)
                    / 2.0F);
            float gapLeft = textX - 3.0F * pixel;
            float gapRight = textX + drawnWidth + 2.0F * pixel;
            drawHorizontalFade(left, gapLeft, ruleTop, pixel,
                    rgb, 0, alpha);
            drawHorizontalFade(gapRight, right, ruleTop, pixel,
                    rgb, alpha, 0);
            // Each half's starting pixel — where the rule is strongest,
            // beside the date — carries a small cap: one pixel above
            // and one below it, so the rule opens toward the date the
            // way Discord's does.
            int cap = (alpha << 24) | rgb;
            fillRect(gapLeft - pixel, ruleTop - pixel, gapLeft, ruleTop,
                    cap);
            fillRect(gapLeft - pixel, ruleTop + pixel, gapLeft,
                    ruleTop + 2.0F * pixel, cap);
            fillRect(gapRight, ruleTop - pixel, gapRight + pixel, ruleTop,
                    cap);
            fillRect(gapRight, ruleTop + pixel, gapRight + pixel,
                    ruleTop + 2.0F * pixel, cap);
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(textX, textTop, 0.0F);
                GL11.glScalef(pixel, pixel, 1.0F);
                LostTalesChatVisualStyle.drawColored(font, label, 0, 0, rgb,
                        alpha);
            } finally {
                GL11.glPopMatrix();
            }
        } else {
            // No room for the date: the rule alone, strongest at the
            // centre exactly as the halves would meet.
            float centre = (left + right) / 2.0F;
            drawHorizontalFade(left, centre, ruleTop, pixel,
                    rgb, 0, alpha);
            drawHorizontalFade(centre, right, ruleTop, pixel,
                    rgb, alpha, 0);
        }
    }

    /**
     * A stack-space x laid on the nearest whole display pixel, so what
     * the stack draws from it lands on the display's grid.
     */
    private static float snapToStackPixel(float x) {
        float pixelsPerUnit = ChatWindowFrame.displayScaleFactor()
                * LostTalesChatVisualStyle.chatScale();
        return Math.round(x * pixelsPerUnit) / pixelsPerUnit;
    }

    /**
     * One row of colour {@code height} tall whose opacity runs from
     * {@code leftAlpha} to {@code rightAlpha} across its width: the
     * divider's fade, built exactly like the edge fades' shaded quads.
     */
    private static void drawHorizontalFade(float left, float right,
                                           float top, float height, int rgb,
                                           int leftAlpha, int rightAlpha) {
        if (right <= left || height <= 0.0F || Math.max(leftAlpha, rightAlpha)
                < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(true);
        // Same winding as the backdrop: the GUI pass culls back faces.
        tessellator.setColorRGBA_I(rgb, rightAlpha);
        tessellator.addVertex(right, top + height, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.setColorRGBA_I(rgb, leftAlpha);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.addVertex(left, top + height, 0.0D);
        LostTalesSkyrimUiStyle.endQuads(tessellator, true);
    }

    /** Width of the scrollbar's track and thumb. */
    private static final float SCROLLBAR_WIDTH = 2.0F;
    /** Shortest the thumb may get, however long the history is. */
    private static final float SCROLLBAR_MIN_THUMB = 8.0F;
    /** How long the bar takes to fade in and out. */
    private static final double SCROLLBAR_FADE_SECONDS = 0.12D;

    /**
     * The window's scrollbar, measured in the stack's own (unscaled)
     * pixels: {@code contentHeight} is the whole stack, {@code room} the
     * window's message room, and {@code offset} how far up the stack
     * the view stands. A thin track runs down the panel's right edge
     * with a thumb as tall a share of it as the window shows of the
     * history; nothing is drawn while the stack fits.
     *
     * <p>It fades in while the pointer rests in the window and out again
     * when it leaves, so a window being read carries no furniture it
     * does not need — Discord's rule, and the reason the chat can afford
     * a scrollbar at all at this size. The thumb's screen rectangle and
     * the track it slides in are recorded on the frame, so a drag maps
     * the pointer onto the history without measuring the window
     * again.</p>
     */
    private static void drawScrollbar(ChatWindowFrame frame,
                                      float panelRight, float topEdge,
                                      float bottomEdge, float contentHeight,
                                      float room, float offset,
                                      float opacity, float originX,
                                      float originY, float scale) {
        float wanted = frame.scrollbarProgress;
        long now = System.nanoTime();
        double elapsed = frame.scrollbarNanos == 0L ? 0.0D
                : (now - frame.scrollbarNanos) / 1.0E9D;
        frame.scrollbarNanos = now;
        frame.scrollbarProgress = LostTalesConfig.enableChatAnimations
                ? (float)LostTalesChatMotion.approach(wanted,
                        frame.scrollbarWanted ? 1.0D : 0.0D, elapsed,
                        SCROLLBAR_FADE_SECONDS)
                : (frame.scrollbarWanted ? 1.0F : 0.0F);
        int alpha = Math.round(255.0F * opacity * frame.scrollbarProgress);
        if (contentHeight <= room + 0.01F || contentHeight <= 0.0F
                || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        float right = panelRight - 1.0F;
        float left = right - SCROLLBAR_WIDTH;
        float trackHeight = bottomEdge - topEdge;
        float thumbHeight = Math.max(SCROLLBAR_MIN_THUMB,
                trackHeight * room / contentHeight);
        float reach = Math.max(1.0F, contentHeight - room);
        float travel = trackHeight - thumbHeight;
        // Scroll counts upward from the newest line, which sits at the
        // bottom: no scroll puts the thumb at the foot of the track.
        float thumbBottom = bottomEdge - travel
                * Math.max(0.0F, Math.min(1.0F, offset / reach));
        float thumbTop = thumbBottom - thumbHeight;
        fillRect(left, topEdge, right, bottomEdge,
                (Math.round(alpha * 0.35F) << 24)
                        | LostTalesChatVisualStyle.SURFACE_RGB);
        fillRect(left, thumbTop, right, thumbBottom,
                (alpha << 24)
                        | LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB);
        frame.scrollbarLeft = originX + (left - 2.0F) * scale;
        frame.scrollbarRight = originX + (right + 1.0F) * scale;
        frame.scrollbarTrackTop = originY + topEdge * scale;
        frame.scrollbarTrackBottom = originY + bottomEdge * scale;
        frame.scrollbarThumbTop = originY + thumbTop * scale;
        frame.scrollbarThumbBottom = originY + thumbBottom * scale;
    }

    /**
     * Height of the jump-to-present button: the framed buttons' one
     * height. The label's capitals and the chevron's three rows stand
     * in its middle, the odd row below each.
     */
    private static final int JUMP_BUTTON_HEIGHT = ChatFramedButton.HEIGHT;
    /** Clear pixels between the jump button's chevron and its label. */
    private static final int JUMP_ICON_GAP = 2;

    /** What the jump-to-present button says, after its chevron. */
    private static String jumpButtonLabel() {
        return StatCollector.translateToLocal("gui.losttales.chat.jump_to_present");
    }

    /**
     * Width of the jump-to-present button: the chevron, the gap, the
     * label's ink, and the wide inset at either end. The last glyph's
     * width includes a column of spacing after it, which is not ink.
     */
    private static int jumpButtonWidth(FontRenderer font) {
        int label = font == null ? 0
                : Math.max(0, font.getStringWidth(jumpButtonLabel()) - 1);
        return ChatFramedButton.WIDE_INSET + ChatIconSheet.CHEVRON_1.getWidth()
                + JUMP_ICON_GAP + label + ChatFramedButton.WIDE_INSET;
    }

    /**
     * Advances the jump-to-present button's fly-in: out while the view is
     * scrolled away from the newest line, home again once it is not.
     * Answers whether any of the button shows.
     */
    private static boolean advanceJumpButton(ChatWindowFrame frame,
                                             double scrollLines) {
        boolean wanted = frame.view != null && scrollLines > 0.5D;
        float progress = frame.jumpMotion.advance(System.nanoTime(), wanted,
                LostTalesConfig.enableChatAnimations
                        ? Math.max(1, LostTalesConfig
                                .chatAnimationDurationMillis)
                        : 0,
                LostTalesUiEasing.SMOOTH);
        return progress > 0.02F;
    }

    /**
     * The jump-to-present button's top edge in the window's units: at the
     * panel's foot a pixel above the bottom rule, lowered past it by as
     * much of the fly-in as is still to come, and laid on a whole display
     * pixel so the frame's pixel art keeps its texels whole as it flies.
     */
    private static float jumpButtonTop(ChatWindowFrame frame,
                                       float bottomEdge, float originY,
                                       float scale) {
        float slide = (1.0F - frame.jumpMotion.clamped())
                * (JUMP_BUTTON_HEIGHT + 3.0F);
        float top = bottomEdge - 1.0F - JUMP_BUTTON_HEIGHT + slide;
        return (float)((ChatWindowFrame.snapToDisplayPixels(
                originY + top * scale) - originY) / scale);
    }

    /**
     * The jump-to-present button of a scrolled-back view: a framed button
     * centred across the panel, the sheet's down chevron — the tab
     * search's, at rest — and its label in ivory after it, lit under the
     * pointer, standing in the hole the history left for it. Drawn in
     * the window's local space; the screen rectangle it lands on is
     * recorded on the frame, so the click resolves against exactly what
     * is on screen.
     */
    private static void drawJumpButton(Minecraft minecraft,
                                       ChatWindowFrame frame, float left,
                                       float top, int width, float bottomEdge,
                                       int alpha, float originX,
                                       float originY, float scale) {
        if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        long now = System.nanoTime();
        double elapsed = frame.jumpFadeNanos == 0L ? 0.0D
                : (now - frame.jumpFadeNanos) / 1.0E9D;
        frame.jumpFadeNanos = now;
        frame.jumpFade = LostTalesChatVisualStyle.hoverFade(frame.jumpFade,
                frame.jumpHovered, elapsed);
        ChatFramedButton.drawSurface(left, top, width, JUMP_BUTTON_HEIGHT,
                frame.jumpFade, Math.round(alpha
                        * LostTalesChatVisualStyle.INSET_ALPHA / 255.0F));
        // The label and the badge's count are text, which is drawn at
        // whole coordinates: the matrix carries the button's own place
        // — a whole display pixel, fractions of a GUI pixel included —
        // so they fly with the frame instead of stepping a GUI pixel at
        // a time beside it.
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(left, top, 0.0F);
            // The label's capitals in the button's middle, the icon gap
            // past the chevron.
            LostTalesChatVisualStyle.drawColored(minecraft.fontRenderer,
                    jumpButtonLabel(), ChatFramedButton.WIDE_INSET
                            + ChatIconSheet.CHEVRON_1.getWidth()
                            + JUMP_ICON_GAP,
                    (JUMP_BUTTON_HEIGHT - GLYPH_CAP_HEIGHT) / 2,
                    LostTalesChatVisualStyle.IVORY, alpha);
            // What is waiting below, so a view scrolled back says how
            // much it has not seen rather than only that there is more.
            // In the divider's own crimson, which is the colour this
            // chat says "unread" in, on the button's right shoulder.
            drawWaitingCount(minecraft, frame, width, 0.0F, alpha);
        } finally {
            GL11.glPopMatrix();
        }
        // The chevron first, the wide inset in from the frame's edge.
        ChatIconSheet.drawPairWithShadow(ChatIconSheet.CHEVRON_1,
                ChatIconSheet.CHEVRON_1_HOVER, frame.jumpFade,
                left + ChatFramedButton.WIDE_INSET,
                top + (JUMP_BUTTON_HEIGHT
                        - ChatIconSheet.CHEVRON_1.getHeight()) / 2, alpha);
        ChatFramedButton.drawInk(left, top, width, JUMP_BUTTON_HEIGHT,
                frame.jumpFade, alpha);
        frame.jumpPillLeft = originX + left * scale;
        frame.jumpPillTop = originY + top * scale;
        frame.jumpPillRight = originX + (left + width) * scale;
        // The clip cuts the flying-in button on the bottom rule; the
        // hitbox ends where the pixels do.
        frame.jumpPillBottom = Math.min(
                originY + (top + JUMP_BUTTON_HEIGHT) * scale,
                originY + bottomEdge * scale);
    }

    /**
     * The depths the window's layers stand at while its holes are cut
     * ({@link #beginHoles}), against the GUI's own depth of zero, which
     * the history is drawn at. From the back: the window's box, far
     * behind; the slab an item icon's own depth is squeezed into
     * ({@link #squeezeItemDepth}); the history; the reaction chips'
     * holes, and the chips half a step in front of them; the edge shades,
     * in front of the chips so they fade a chip by the rules as they fade
     * the words beside it; and the floating controls' holes, in front of
     * everything, so nothing the history draws reaches into them. Every
     * layer stands at least half a step from the next: two chains of
     * transforms can round one depth a hair apart, so no layer may need
     * to meet another's exactly.
     */
    static final float BASE_DEPTH = -500.0F;
    static final float ITEM_FAR_DEPTH = BASE_DEPTH + 0.5F;
    static final float ITEM_NEAR_DEPTH = -0.5F;
    static final float CHIP_HOLE_DEPTH = 1.0F;
    static final float CHIP_DEPTH = 1.5F;
    static final float SHADE_DEPTH = 2.0F;
    static final float CONTROL_DEPTH = 3.0F;

    /**
     * Where an item icon's depth range starts and ends in the depth
     * buffer while holes are cut — {@link #ITEM_NEAR_DEPTH} and
     * {@link #ITEM_FAR_DEPTH} as the window's matrices place them — and
     * NaN while none are.
     */
    private static double itemDepthNear = Double.NaN;
    private static double itemDepthFar = Double.NaN;
    /** Scratch for reading the matrices and the depth range back. */
    private static final FloatBuffer GL_FLOATS =
            BufferUtils.createFloatBuffer(16);
    private static final float[] MODELVIEW = new float[16];
    private static final float[] PROJECTION = new float[16];

    /** The holes one window's draw cuts, six numbers to a hole. */
    private static final class Holes {
        private float[] data = new float[6 * 8];
        private int count;

        /**
         * A framed button's footprint at {@code depth}, less its four
         * corner pixels, each {@code corner} across.
         */
        void add(float left, float top, float width, float height,
                 float depth, float corner) {
            if (width <= 0.0F || height <= 0.0F) {
                return;
            }
            if ((this.count + 1) * 6 > this.data.length) {
                this.data = java.util.Arrays.copyOf(this.data,
                        this.data.length * 2);
            }
            int at = this.count * 6;
            this.data[at] = left;
            this.data[at + 1] = top;
            this.data[at + 2] = left + width;
            this.data[at + 3] = top + height;
            this.data[at + 4] = depth;
            this.data[at + 5] = corner;
            this.count++;
        }
    }

    /**
     * Cuts the framed buttons' holes out of everything the window draws
     * until {@link #endHoles}. The depth test, off for the rest of the
     * chat, is turned on; the window's box is laid down far behind the
     * GUI's own depth and each hole at its layer's depth — a button's
     * footprint less its four corner pixels, which stay with what is
     * around it — without writing a colour. Everything the window then
     * draws at the GUI's depth — the backdrop, the lines' highlights, the
     * text — passes everywhere but in the holes, so each button stands on
     * nothing but what is behind the window, at its own opacity; what is
     * drawn a layer in front passes the holes of the layers behind it.
     */
    private static boolean beginHoles(float left, float top, float right,
                                      float bottom, Holes holes) {
        if (holes.count <= 0) {
            return false;
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        GL11.glColorMask(false, false, false, false);
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(false);
        tessellator.setColorRGBA_I(0, 255);
        depthQuad(tessellator, left, top, right, bottom, BASE_DEPTH);
        for (int index = 0; index < holes.count; index++) {
            int at = index * 6;
            float holeLeft = holes.data[at];
            float holeTop = holes.data[at + 1];
            float holeRight = holes.data[at + 2];
            float holeBottom = holes.data[at + 3];
            double depth = holes.data[at + 4];
            float corner = holes.data[at + 5];
            depthQuad(tessellator, holeLeft + corner, holeTop,
                    holeRight - corner, holeTop + corner, depth);
            depthQuad(tessellator, holeLeft, holeTop + corner, holeRight,
                    holeBottom - corner, depth);
            depthQuad(tessellator, holeLeft + corner, holeBottom - corner,
                    holeRight - corner, holeBottom, depth);
        }
        LostTalesSkyrimUiStyle.endQuads(tessellator, false);
        GL11.glColorMask(true, true, true, true);
        // Nothing drawn from here writes a depth but an item icon, whose
        // depth goes in behind the history, so every layer is tested
        // against the mask alone.
        GL11.glDepthMask(false);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        placeItemDepth(left, top);
        return true;
    }

    /**
     * Ends what {@link #beginHoles} began: the mask is cleared from the
     * depth buffer, so nothing drawn after the window is held out of its
     * holes, and the chat draws without the depth test again.
     */
    private static void endHoles(boolean began) {
        if (!began) {
            return;
        }
        itemDepthNear = Double.NaN;
        itemDepthFar = Double.NaN;
        GL11.glDepthMask(true);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    /**
     * Squeezes the depth of what is drawn next into the slab between the
     * window's box and the history while holes are cut, and answers
     * whether they are. An item icon drawn in it still sorts its own
     * faces, stays out of every hole as the words beside it do, and
     * leaves behind a depth that everything the window draws after it
     * passes. The caller saves and restores the depth range
     * ({@code GL_VIEWPORT_BIT}).
     */
    static boolean squeezeItemDepth() {
        if (Double.isNaN(itemDepthNear)) {
            return false;
        }
        GL11.glDepthRange(itemDepthNear, itemDepthFar);
        return true;
    }

    /**
     * Finds where the item slab's two ends land in the depth buffer under
     * the window's matrices. A slab that does not come out between the
     * history and the window's box is not used, and the icons draw at
     * their own depth.
     */
    private static void placeItemDepth(float x, float y) {
        itemDepthNear = Double.NaN;
        itemDepthFar = Double.NaN;
        GL_FLOATS.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, GL_FLOATS);
        GL_FLOATS.get(MODELVIEW);
        GL_FLOATS.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, GL_FLOATS);
        GL_FLOATS.get(PROJECTION);
        GL_FLOATS.clear();
        GL11.glGetFloat(GL11.GL_DEPTH_RANGE, GL_FLOATS);
        double rangeNear = GL_FLOATS.get(0);
        double rangeFar = GL_FLOATS.get(1);
        double history = bufferDepth(MODELVIEW, PROJECTION, rangeNear,
                rangeFar, x, y, 0.0F);
        double near = bufferDepth(MODELVIEW, PROJECTION, rangeNear,
                rangeFar, x, y, ITEM_NEAR_DEPTH);
        double far = bufferDepth(MODELVIEW, PROJECTION, rangeNear,
                rangeFar, x, y, ITEM_FAR_DEPTH);
        double box = bufferDepth(MODELVIEW, PROJECTION, rangeNear,
                rangeFar, x, y, BASE_DEPTH);
        // Nearer is smaller, as GL_LEQUAL reads it.
        if (history < near && near < far && far < box) {
            itemDepthNear = near;
            itemDepthFar = far;
        }
    }

    /**
     * Where a point lands in the depth buffer: carried through the
     * modelview and the projection, each a column at a time as OpenGL
     * keeps them, then from the clip volume's depth onto the depth range
     * in force.
     */
    static double bufferDepth(float[] modelview, float[] projection,
                              double rangeNear, double rangeFar,
                              float x, float y, float z) {
        double eyeX = modelview[0] * (double)x + modelview[4] * (double)y
                + modelview[8] * (double)z + modelview[12];
        double eyeY = modelview[1] * (double)x + modelview[5] * (double)y
                + modelview[9] * (double)z + modelview[13];
        double eyeZ = modelview[2] * (double)x + modelview[6] * (double)y
                + modelview[10] * (double)z + modelview[14];
        double eyeW = modelview[3] * (double)x + modelview[7] * (double)y
                + modelview[11] * (double)z + modelview[15];
        double clipZ = projection[2] * eyeX + projection[6] * eyeY
                + projection[10] * eyeZ + projection[14] * eyeW;
        double clipW = projection[3] * eyeX + projection[7] * eyeY
                + projection[11] * eyeZ + projection[15] * eyeW;
        return rangeNear
                + (rangeFar - rangeNear) * (clipZ / clipW + 1.0D) / 2.0D;
    }

    /** One quad of the depth mask, wound as the GUI pass's quads are. */
    private static void depthQuad(Tessellator tessellator, float left,
                                  float top, float right, float bottom,
                                  double depth) {
        if (right <= left || bottom <= top) {
            return;
        }
        tessellator.addVertex(left, bottom, depth);
        tessellator.addVertex(right, bottom, depth);
        tessellator.addVertex(right, top, depth);
        tessellator.addVertex(left, top, depth);
    }

    /**
     * The count of messages that arrived while the view was scrolled
     * back, as a small badge on the jump-to-present button. Anchored on
     * the button's top-right corner and drawn over its outline, so a
     * long count grows leftward across the button rather than off the
     * panel; nothing is drawn while nothing is waiting. Drawn in the
     * button's own space, its origin at the button's top-left corner.
     */
    private static void drawWaitingCount(Minecraft minecraft,
                                         ChatWindowFrame frame,
                                         float buttonRight, float buttonTop,
                                         int alpha) {
        int waiting = ClientChatChannelViews.waitingBelow(frame.view);
        if (waiting <= 0 || minecraft.fontRenderer == null
                || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        String text = waiting > ClientChatChannelViews.MAX_UNREAD
                ? ClientChatChannelViews.MAX_UNREAD + "+"
                : String.valueOf(waiting);
        int textWidth = minecraft.fontRenderer.getStringWidth(text);
        float badgeRight = buttonRight + 2.0F;
        float badgeLeft = badgeRight - textWidth - 3.0F;
        float badgeTop = buttonTop - 2.0F;
        float badgeBottom = badgeTop + 9.0F;
        fillRect(badgeLeft, badgeTop, badgeRight, badgeBottom,
                (alpha << 24) | UNREAD_DIVIDER_RGB);
        LostTalesChatVisualStyle.drawColored(minecraft.fontRenderer, text,
                Math.round(badgeLeft) + 2, Math.round(badgeTop) + 1,
                LostTalesChatVisualStyle.IVORY, alpha);
    }

    /**
     * Edge of one toolbar button's square: the framed buttons' one
     * height, an emoji's box with the frame's inset either side.
     */
    static final int TOOLBAR_BUTTON_SIZE = ChatFramedButton.HEIGHT;
    /** Clear pixels between two of the toolbar's buttons, and after the last. */
    static final int TOOLBAR_GAP = 2;
    /** From one toolbar button's left edge to the next one's. */
    static final int TOOLBAR_STRIDE = TOOLBAR_BUTTON_SIZE + TOOLBAR_GAP;
    /** Answer the message. */
    static final int TOOLBAR_REPLY = 1;
    /** Take a copy of the message. */
    static final int TOOLBAR_COPY = 2;
    /** React to the message. */
    static final int TOOLBAR_REACT = 3;
    /** The reply glyph's ink, and the copy glyph's. */
    private static final int REPLY_GLYPH_WIDTH = 6;
    private static final int REPLY_GLYPH_HEIGHT = 5;
    private static final int COPY_GLYPH_WIDTH = 8;
    private static final int COPY_GLYPH_HEIGHT = 7;

    /** Where the hovered message's toolbar stands, in the stack's units, and whose it is. */
    private static final class ToolbarPlace {
        final float top;
        final int chatLineId;

        ToolbarPlace(float top, int chatLineId) {
            this.top = top;
            this.chatLineId = chatLineId;
        }
    }

    /**
     * A line's opacity from {@code base} — full in an open window, its
     * age's fade in the closed feed: a message still on its way is faint
     * until the server's own copy of it arrives to take its place, and
     * every line carries the chat opacity, its own entrance and the
     * opening fade.
     */
    private static int lineAlpha(int base, ChatLine line, float opacity,
                                 LostTalesGuiAnimationSample opening) {
        int alpha = base;
        if (ClientChatPendingEchoes.isPending(line.getChatLineID())) {
            alpha = Math.round(alpha
                    * ClientChatPendingEchoes.PENDING_OPACITY);
        }
        alpha = (int)(alpha * opacity);
        alpha = (int)(alpha * entryOpacity(line));
        alpha = (int)(alpha * opening.getOpacity());
        return alpha;
    }

    /**
     * Where the hovered message's toolbar stands this frame, found before
     * anything is drawn so everything under it can leave it a hole: on
     * the message's topmost row the stack draws, found by the same tests
     * the stack skips a row by. Null while no drawn line is hovered.
     */
    private static ToolbarPlace placeToolbar(Minecraft minecraft,
                                             List<ChatLine> lines,
                                             int firstLine, int lastRow,
                                             int dividerIndex,
                                             ChatStackRows rows,
                                             float stackBase, float opacity,
                                             LostTalesGuiAnimationSample opening) {
        ToolbarPlace place = null;
        for (int lineIndex = firstLine; lineIndex < lines.size();
             lineIndex++) {
            int rowIndex = rowOfLine(lineIndex, dividerIndex);
            if (rowIndex > lastRow) {
                break;
            }
            ChatLine line = lines.get(lineIndex);
            if (line == null || !LostTalesChatPresentation.isHoveredLine(
                    line.getChatLineID())) {
                continue;
            }
            if (ChatWindowLines.isSpacer(line) && !olderNeighbourShown(
                    minecraft, lines, lineIndex, dividerIndex, lastRow,
                    true)) {
                continue;
            }
            if (lineAlpha(255, line, opacity, opening)
                    < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA
                    || ChatWindowLines.dateDividerLabel(line) != null) {
                continue;
            }
            // The stack walks upward, so the last row found is the
            // message's topmost.
            int rowBottom = -(rows.top(rowIndex) - Math.round(stackBase));
            place = new ToolbarPlace(toolbarTop(rowBottom,
                    rows.height(rowIndex)), line.getChatLineID());
        }
        return place;
    }

    /**
     * Where a reaction row's text starts, in the stack's units, for the
     * row ending at {@code rowBottom}: its chips — framed buttons, drawn
     * at {@code rowScale} of the words — centred in the row, which is
     * made tall enough for them ({@link ChatStackRows#reactionRowHeight}),
     * and each chip's count a row below the top of the emoji's box.
     */
    static float reactionTextTop(int rowBottom, int rowHeight,
                                 float rowScale) {
        return rowBottom - rowHeight
                + (rowHeight - ChatReactionMarker.HEIGHT * rowScale) / 2.0F
                + ChatReactionMarker.TEXT_DROP * rowScale;
    }

    /**
     * Adds a hole for every reaction chip the stack draws this frame,
     * where it draws it: each chip is a framed button, standing on
     * nothing but what is behind the window. Found by the tests the stack
     * skips a row by, and laid out by the walk that draws the row.
     */
    private static void placeChipHoles(Holes holes, Minecraft minecraft,
                                       FontRenderer font,
                                       List<ChatLine> lines, int firstLine,
                                       int lastRow, int dividerIndex,
                                       ChatStackRows rows, float stackBase,
                                       float opacity,
                                       LostTalesGuiAnimationSample opening,
                                       ChatFeedAlignment alignment,
                                       float areaWidth, float offset) {
        float smallScale = LostTalesChatVisualStyle.stackSmallScale();
        for (int lineIndex = firstLine; lineIndex < lines.size();
             lineIndex++) {
            int rowIndex = rowOfLine(lineIndex, dividerIndex);
            if (rowIndex > lastRow) {
                break;
            }
            ChatLine line = lines.get(lineIndex);
            if (line == null || ChatWindowLines.isSpacer(line)) {
                continue;
            }
            IChatComponent row = line.func_151461_a();
            if (!ChatReactionMarker.isReactionRow(row)
                    || lineAlpha(255, line, opacity, opening)
                            < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA
                    || ChatWindowLines.dateDividerLabel(line) != null) {
                continue;
            }
            int[] chips = LostTalesChatVisualStyle.chipBoxes(font, row, true);
            if (chips.length == 0) {
                continue;
            }
            float pivot = smallRowPivot(row, true, smallScale);
            float rowScale = pivot >= 0.0F ? smallScale : 1.0F;
            int rowBottom = -(rows.top(rowIndex) - Math.round(stackBase));
            float chipTop = reactionTextTop(rowBottom, rows.height(rowIndex),
                    rowScale) - ChatReactionMarker.TEXT_DROP * rowScale
                    + offset;
            float rowLeft = alignment.slide(entrySlide(line))
                    + feedRowShift(font, row, alignment, areaWidth, pivot,
                            smallScale)
                    + (pivot >= 0.0F ? pivot * (1.0F - smallScale) : 0.0F);
            for (int index = 0; index + 1 < chips.length; index += 2) {
                holes.add(rowLeft + chips[index] * rowScale, chipTop,
                        chips[index + 1] * rowScale,
                        ChatReactionMarker.HEIGHT * rowScale,
                        CHIP_HOLE_DEPTH, rowScale);
            }
        }
    }

    /**
     * The controls a message's toolbar offers, left to right: React for a
     * message the server named, Reply for one somebody may answer, and
     * Copy always, so a console notice shows Copy alone.
     */
    private static int[] offeredToolbar(int chatLineId) {
        List<Integer> offered = new ArrayList<Integer>(3);
        if (LostTalesChatPresentation.isReactable(chatLineId)) {
            offered.add(Integer.valueOf(TOOLBAR_REACT));
        }
        if (LostTalesChatPresentation.isRepliable(chatLineId)) {
            offered.add(Integer.valueOf(TOOLBAR_REPLY));
        }
        offered.add(Integer.valueOf(TOOLBAR_COPY));
        int[] kinds = new int[offered.size()];
        for (int index = 0; index < kinds.length; index++) {
            kinds[index] = offered.get(index).intValue();
        }
        return kinds;
    }

    /** The width of a toolbar of {@code count} buttons, the gaps between them included. */
    static int toolbarWidth(int count) {
        return count <= 0 ? 0
                : count * TOOLBAR_BUTTON_SIZE + (count - 1) * TOOLBAR_GAP;
    }

    /**
     * The hovered message's own controls, at the top right of it: react
     * to it, reply to it and copy it — what the message's menu offers,
     * where the pointer already is — each a framed button standing in the
     * hole the history left for it, lit under the pointer.
     *
     * <p>Drawn in the stack's space, so it rides the scroll with the
     * message it belongs to: {@code top} is the row's place before the
     * slide, the caller translates by the slide and {@code originY}
     * includes it. The buttons are taller than the row and reach past it;
     * while the pointer is on one, the message it belongs to stays the
     * hovered one. The screen rectangle is recorded on the frame, so the
     * click resolves against exactly what was drawn.</p>
     */
    private static void drawMessageToolbar(ChatWindowFrame frame,
                                           int chatLineId, int[] kinds,
                                           float left, float top,
                                           int alpha, float originX,
                                           float originY, float scale) {
        if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA
                || kinds.length == 0) {
            return;
        }
        long now = System.nanoTime();
        double elapsed = frame.toolbarFadeNanos == 0L ? 0.0D
                : (now - frame.toolbarFadeNanos) / 1.0E9D;
        frame.toolbarFadeNanos = now;
        frame.startToolbarFades(chatLineId);
        int surfaceAlpha = Math.round(alpha
                * LostTalesChatVisualStyle.INSET_ALPHA / 255.0F);
        int ivory = (alpha << 24) | LostTalesChatVisualStyle.IVORY;
        for (int index = 0; index < kinds.length; index++) {
            int kind = kinds[index];
            float buttonLeft = left + index * TOOLBAR_STRIDE;
            float lit = frame.toolbarFade(kind, elapsed);
            ChatFramedButton.drawSurface(buttonLeft, top,
                    TOOLBAR_BUTTON_SIZE, TOOLBAR_BUTTON_SIZE, lit,
                    surfaceAlpha);
            if (kind == TOOLBAR_REACT) {
                drawReactGlyph(buttonLeft + (TOOLBAR_BUTTON_SIZE
                                - CONTENT_BOX_HEIGHT) / 2,
                        top + (TOOLBAR_BUTTON_SIZE - CONTENT_BOX_HEIGHT) / 2,
                        alpha);
            } else if (kind == TOOLBAR_REPLY) {
                drawReplyGlyph(buttonLeft + (TOOLBAR_BUTTON_SIZE
                                - REPLY_GLYPH_WIDTH) / 2,
                        top + (TOOLBAR_BUTTON_SIZE - REPLY_GLYPH_HEIGHT) / 2,
                        ivory);
            } else {
                drawCopyGlyph(buttonLeft + (TOOLBAR_BUTTON_SIZE
                                - COPY_GLYPH_WIDTH) / 2,
                        top + (TOOLBAR_BUTTON_SIZE - COPY_GLYPH_HEIGHT) / 2,
                        ivory);
            }
            ChatFramedButton.drawInk(buttonLeft, top, TOOLBAR_BUTTON_SIZE,
                    TOOLBAR_BUTTON_SIZE, lit, alpha);
        }
        frame.toolbarLeft = originX + left * scale;
        frame.toolbarTop = originY + top * scale;
        frame.toolbarRight = originX + (left + toolbarWidth(kinds.length))
                * scale;
        frame.toolbarBottom = originY + (top + TOOLBAR_BUTTON_SIZE) * scale;
        frame.toolbarStride = TOOLBAR_STRIDE * scale;
        frame.toolbarKinds = kinds;
        frame.toolbarChatLineId = chatLineId;
    }

    /** A face: react to this. The picker's own smile, drawn one texel to one pixel. */
    private static void drawReactGlyph(float x, float y, int alpha) {
        LostTalesChatVisualStyle.beginContent();
        ChatInlineIcons.drawEmoji(Minecraft.getMinecraft(),
                ChatEmoji.SLIGHT_SMILE, x, y, ChatInlineIcons.CONTENT_SIZE,
                alpha);
    }

    /** An arrow turning back on itself, its ink from {@code (x, y)}: answer this. */
    private static void drawReplyGlyph(float x, float y, int argb) {
        // The head, three pixel steps down to a point at the left.
        fillRect(x, y + 2.0F, x + 1.0F, y + 3.0F, argb);
        fillRect(x + 1.0F, y + 1.0F, x + 2.0F, y + 4.0F, argb);
        fillRect(x + 2.0F, y, x + 3.0F, y + 5.0F, argb);
        // The shaft, and the tail turning up behind it.
        fillRect(x + 2.0F, y + 2.0F, x + 6.0F, y + 3.0F, argb);
        fillRect(x + 5.0F, y, x + 6.0F, y + 3.0F, argb);
    }

    /** Two leaves, one behind the other, their ink from {@code (x, y)}: take a copy of this. */
    private static void drawCopyGlyph(float x, float y, int argb) {
        // The leaf behind, an outline open at its covered corner.
        fillRect(x + 3.0F, y, x + 8.0F, y + 1.0F, argb);
        fillRect(x + 7.0F, y, x + 8.0F, y + 5.0F, argb);
        fillRect(x + 5.0F, y + 4.0F, x + 8.0F, y + 5.0F, argb);
        // The leaf in front, whole.
        fillRect(x, y + 2.0F, x + 6.0F, y + 3.0F, argb);
        fillRect(x, y + 2.0F, x + 1.0F, y + 7.0F, argb);
        fillRect(x + 5.0F, y + 2.0F, x + 6.0F, y + 7.0F, argb);
        fillRect(x, y + 6.0F, x + 6.0F, y + 7.0F, argb);
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
                LostTalesChatVisualStyle.backdropRgb());
    }

    /**
     * As above with the stretch from {@code holeLeft} to
     * {@code holeRight} left out — where the selected tab stands on the
     * row — the band's curve still running the whole width.
     */
    static void drawBackdropRowAround(float left, float top, float right,
                                      float bottom, int alpha,
                                      float holeLeft, float holeRight) {
        int rgb = LostTalesChatVisualStyle.backdropRgb();
        if (holeRight <= holeLeft || holeRight <= left || holeLeft >= right) {
            drawChatBackdrop(left, top, right, bottom, alpha, rgb);
            return;
        }
        drawChatBackdrop(left, left, Math.max(left, holeLeft), top, right,
                bottom, alpha, rgb, GL11.GL_ONE_MINUS_SRC_ALPHA,
                BACKDROP_FADE_WEIGHTS);
        drawChatBackdrop(left, Math.min(right, holeRight), right, top, right,
                bottom, alpha, rgb, GL11.GL_ONE_MINUS_SRC_ALPHA,
                BACKDROP_FADE_WEIGHTS);
    }

    /**
     * The opacity a window's backdrop is drawn at, before the opening
     * fade is applied to it. Both rules ask here, so the row each of
     * them stands on is the same one the messages between them lie on.
     */
    static int backdropRowAlpha(Minecraft minecraft) {
        float opacity = LostTalesChatVisualStyle.chatOpacity(minecraft);
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
        drawRuleAround(left, right, top, bottom, alpha, 0.0F, 0.0F);
    }

    /**
     * The rule with the stretch from {@code holeLeft} to
     * {@code holeRight} left out — where the selected tab stands on it —
     * hung from the tab: each piece is full where it meets the tab's
     * border and fades to nothing at the strip's end, so the rule and
     * the tab read as one. Without a hole, the whole rule as
     * {@link #drawRule} draws it.
     */
    static void drawRuleAround(float left, float right, float top,
                               float bottom, int alpha, float holeLeft,
                               float holeRight) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (safeAlpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA
                || right <= left || bottom <= top) {
            return;
        }
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(true);
        if (holeRight <= holeLeft || holeRight <= left || holeLeft >= right) {
            drawRuleSpan(tessellator, left, right, left, right, top, bottom,
                    safeAlpha);
        } else {
            drawRuleRamp(tessellator, left, Math.max(left, holeLeft), top,
                    bottom, safeAlpha);
            drawRuleRamp(tessellator, right, Math.min(right, holeRight), top,
                    bottom, safeAlpha);
        }
        LostTalesSkyrimUiStyle.endQuads(tessellator, true);
    }

    /**
     * One piece of a rule from {@code faint}, where it is nothing, to
     * {@code full}, where it is opaque; either may be the left end.
     */
    private static void drawRuleRamp(Tessellator tessellator, float faint,
                                     float full, float top, float bottom,
                                     int alpha) {
        if (faint == full) {
            return;
        }
        float leftX = Math.min(faint, full);
        float rightX = Math.max(faint, full);
        int leftAlpha = faint < full ? 0 : alpha;
        int rightAlpha = faint < full ? alpha : 0;
        // Same winding as the backdrop: the GUI pass culls back faces.
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, rightAlpha);
        tessellator.addVertex(rightX, bottom, 0.0D);
        tessellator.addVertex(rightX, top, 0.0D);
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, leftAlpha);
        tessellator.addVertex(leftX, top, 0.0D);
        tessellator.addVertex(leftX, bottom, 0.0D);
    }

    /**
     * The stretch of a rule from {@code from} to {@code to}: opaque at
     * the rule's centre and nothing at its ends, the stretch cut at the
     * centre when it crosses it so each piece is one linear ramp.
     */
    private static void drawRuleSpan(Tessellator tessellator, float left,
                                     float right, float from, float to,
                                     float top, float bottom, int alpha) {
        if (to <= from) {
            return;
        }
        float centre = (left + right) / 2.0F;
        if (from < centre && to > centre) {
            drawRuleSpan(tessellator, left, right, from, centre, top, bottom,
                    alpha);
            drawRuleSpan(tessellator, left, right, centre, to, top, bottom,
                    alpha);
            return;
        }
        int fromAlpha = ruleAlpha(left, right, from, alpha);
        int toAlpha = ruleAlpha(left, right, to, alpha);
        // Same winding as the backdrop: the GUI pass culls back faces.
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, toAlpha);
        tessellator.addVertex(to, bottom, 0.0D);
        tessellator.addVertex(to, top, 0.0D);
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, fromAlpha);
        tessellator.addVertex(from, top, 0.0D);
        tessellator.addVertex(from, bottom, 0.0D);
    }

    /** The rule's opacity at {@code x}: full at its centre, none at its ends. */
    private static int ruleAlpha(float left, float right, float x, int alpha) {
        float half = (right - left) / 2.0F;
        float centre = (left + right) / 2.0F;
        return half <= 0.0F ? alpha : Math.round(alpha
                * Math.max(0.0F, 1.0F - Math.abs(x - centre) / half));
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
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(true);
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
        LostTalesSkyrimUiStyle.endQuads(tessellator, true);
    }

    /**
     * Whether the line's timestamp is the first of its speaker's
     * minute, reading down the column: the clock the chat shows has no
     * seconds, so a burst of messages inside one minute would otherwise
     * repeat the same {@code [HH:mm]} on every row of it. The topmost
     * line of each minute carries the time and the rest of that minute
     * is left blank — per voice, as the messages themselves are
     * grouped: another sender speaking inside the same minute opens a
     * turn of their own and is stamped again, so two people talking at
     * once each carry their time. Stable while a view is scrolled — a
     * line shows the same thing wherever it happens to sit.
     *
     * <p>Answered against the line above (older, further along the
     * list), skipping the wrapped continuation lines that carry no
     * timestamp of their own. A day's rule above the line opens the turn
     * again: the same clock reading on another day is another minute.</p>
     */
    static boolean opensItsMinute(List<ChatLine> lines, int lineIndex) {
        ChatLine line = lines.get(lineIndex);
        String own = timestampText(line.func_151461_a());
        if (own.length() == 0) {
            return false;
        }
        for (int index = lineIndex + 1; index < lines.size(); index++) {
            ChatLine older = lines.get(index);
            if (older == null) {
                continue;
            }
            if (ChatWindowLines.isDateDivider(older)) {
                return true;
            }
            String above = timestampText(older.func_151461_a());
            if (above.length() > 0) {
                return !above.equals(own)
                        || !ChatGroupRuns.sameVoice(line.getChatLineID(),
                                older.getChatLineID());
            }
        }
        return true;
    }

    /**
     * How far below its own row's baseline the stamp of the message at
     * {@code lineIndex} stands, so that it is centred on the message's
     * words: from the row its body opens on — the row under a name that
     * stands on a row of its own, or the stamped row itself for a
     * grouped line and a line with no name — down to its last wrapped
     * row, its reaction row left out. The name's row and a reply's quote
     * above it are not the words, so the stamp stands beside the words
     * rather than between them and the name. The rows of one message
     * share its chat line id and stand together in the list, wrapped
     * continuations toward the newer end and a leading row toward the
     * older; a blank row or a day's rule ends the message. Zero for a
     * message on one row.
     */
    static int messageCentreShift(List<ChatLine> lines, int lineIndex,
                                  ChatStackRows rows, int dividerIndex) {
        ChatLine stamped = lines.get(lineIndex);
        if (stamped == null) {
            return 0;
        }
        int id = stamped.getChatLineID();
        // The row the words open on: the one the body's chevron stands
        // on, the stamped row's own or one below it. A line with no
        // chevron anywhere — a system line — is words from its first
        // row down.
        int bodyRow = lineIndex;
        if (!opensBody(stamped)) {
            for (int index = lineIndex - 1; index >= 0; index--) {
                ChatLine newer = lines.get(index);
                if (!sameMessage(newer, id)) {
                    break;
                }
                if (opensBody(newer)) {
                    bodyRow = index;
                    break;
                }
            }
        }
        int nameHeight = 0;
        int wordsHeight = 0;
        for (int index = lineIndex; index >= 0; index--) {
            ChatLine row = lines.get(index);
            if (index != lineIndex && !sameMessage(row, id)) {
                break;
            }
            if (ChatReactionMarker.isReactionRow(row.func_151461_a())) {
                // What readers answered with is not the message: the
                // stamp stays centred on the words.
                continue;
            }
            int height = rows.height(rowOfLine(index, dividerIndex));
            if (index > bodyRow) {
                nameHeight += height;
            } else {
                wordsHeight += height;
            }
        }
        // The stamp is laid out on the stamped row and moves down by the
        // distance from that row's middle to the middle of the words.
        // Halves round up the screen, as everything the chat centres
        // does.
        int stampedHeight = rows.height(rowOfLine(lineIndex, dividerIndex));
        return Math.floorDiv(2 * nameHeight + wordsHeight - stampedHeight, 2);
    }

    /** Whether the row is part of the message with chat line id {@code id}. */
    private static boolean sameMessage(ChatLine row, int id) {
        return row != null && row.getChatLineID() == id
                && !ChatWindowLines.isFiller(row);
    }

    /** Whether the row is the one a message's body opens on, behind its chevron. */
    private static boolean opensBody(ChatLine row) {
        for (Object value : row.func_151461_a()) {
            if (value instanceof IChatComponent
                    && ChatBodyMarker.isMarker((IChatComponent)value)) {
                return true;
            }
        }
        return false;
    }

    /** Clear pixels between a line's last words and its delivery mark. */
    private static final int DELIVERY_MARK_GAP = 3;
    /** The retrying mark's clock: as tall as the capitals, and as wide. */
    private static final int DELIVERY_CLOCK_SIZE = GLYPH_CAP_HEIGHT;

    /**
     * The mark a line of the player's own wears while the Discord bridge
     * has not posted it yet, or after it could not: a small clock in the
     * chat's aside tone while it is tried again, a crimson "!" once
     * Discord refused it or the bridge gave up, standing on the capitals a
     * gap past the row's last words and never past the panel's scrollbar.
     * Drawn in the row's text space, where the text's top is zero and the
     * panel's right edge stands {@code textSpaceRight} along; the box it
     * lands on is recorded on the frame, so the tip saying why answers
     * exactly where the mark stands.
     */
    private static void drawDeliveryMark(ChatWindowFrame frame,
                                         FontRenderer font,
                                         IChatComponent row, int chatLineId,
                                         int alpha, float textSpaceRight,
                                         float screenX, float screenTop,
                                         float scale, float clipTop,
                                         float clipBottom) {
        ChatDeliveryMark.State state =
                ClientChatDeliveryMarks.stateOf(chatLineId);
        if (state == ChatDeliveryMark.State.NONE || font == null
                || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        boolean failed = state == ChatDeliveryMark.State.FAILED;
        // The advance ends in the font's one-pixel gap; the ink is the rest.
        int width = failed ? Math.max(1, font.getStringWidth("!") - 1)
                : DELIVERY_CLOCK_SIZE;
        int x = Math.min(LostTalesChatVisualStyle.inkEnd(font, row, true)
                        + DELIVERY_MARK_GAP,
                (int)Math.floor(textSpaceRight - 2.0F - SCROLLBAR_WIDTH)
                        - width);
        if (failed) {
            LostTalesChatVisualStyle.beginContent();
            LostTalesChatVisualStyle.drawColored(font, "!", x, 0,
                    UNREAD_DIVIDER_RGB, alpha);
        } else {
            drawDeliveryClock(x, 0, alpha);
        }
        frame.recordMark(screenX + (x - 1) * scale,
                Math.max(clipTop, screenTop - scale),
                screenX + (x + width + 1) * scale,
                Math.min(clipBottom,
                        screenTop + (GLYPH_CAP_HEIGHT + 1) * scale),
                chatLineId);
    }

    /** The retrying mark: a clock at a quarter past, with the chat's shadow. */
    private static void drawDeliveryClock(int x, int y, int alpha) {
        int shadow = LostTalesChatVisualStyle.shadowAlpha(alpha);
        if (shadow > 0) {
            drawClockPixels(x + LostTalesChatVisualStyle.SHADOW_OFFSET,
                    y + LostTalesChatVisualStyle.SHADOW_OFFSET,
                    LostTalesChatVisualStyle.argb(
                            LostTalesChatVisualStyle.SHADOW, shadow));
        }
        drawClockPixels(x, y, LostTalesChatVisualStyle.argb(
                LostTalesChatVisualStyle.asideRgb(), alpha));
    }

    /** The clock's pixels, seven rows round, from {@code (x, y)}. */
    private static void drawClockPixels(float x, float y, int argb) {
        // The rim.
        fillRect(x + 2.0F, y, x + 5.0F, y + 1.0F, argb);
        fillRect(x + 1.0F, y + 1.0F, x + 2.0F, y + 2.0F, argb);
        fillRect(x + 5.0F, y + 1.0F, x + 6.0F, y + 2.0F, argb);
        fillRect(x, y + 2.0F, x + 1.0F, y + 5.0F, argb);
        fillRect(x + 6.0F, y + 2.0F, x + 7.0F, y + 5.0F, argb);
        fillRect(x + 1.0F, y + 5.0F, x + 2.0F, y + 6.0F, argb);
        fillRect(x + 5.0F, y + 5.0F, x + 6.0F, y + 6.0F, argb);
        fillRect(x + 2.0F, y + 6.0F, x + 5.0F, y + 7.0F, argb);
        // The hands, at a quarter past.
        fillRect(x + 3.0F, y + 2.0F, x + 4.0F, y + 4.0F, argb);
        fillRect(x + 4.0F, y + 3.0F, x + 5.0F, y + 4.0F, argb);
    }

    /**
     * Records a drawn stamp's box on the frame in screen GUI pixels: from
     * the stamp's left edge across its text, as tall as its small text
     * with a pixel to spare above, kept inside the room the window shows.
     */
    private static void recordStamp(ChatWindowFrame frame, FontRenderer font,
                                    ChatLine line, int x, int y,
                                    float originX, float originY, float scale,
                                    float clipTop, float clipBottom) {
        float small = LostTalesChatVisualStyle.stackSmallScale();
        float textTop = y + LostTalesChatVisualStyle.stackSmallTopOffset();
        float width = font.getStringWidth(
                timestampText(line.func_151461_a()).trim()) * small;
        frame.recordStamp(originX + x * scale,
                Math.max(clipTop, originY + (textTop - 1.0F) * scale),
                originX + (x + width) * scale,
                Math.min(clipBottom, originY
                        + (textTop + font.FONT_HEIGHT * small) * scale),
                line.getChatLineID());
    }

    /** The line's timestamp runs as one string, empty when it has none. */
    private static String timestampText(IChatComponent line) {
        StringBuilder text = null;
        for (Object value : line) {
            if (!(value instanceof IChatComponent)
                    || !ChatPrefixMarker.isTimestamp((IChatComponent)value)) {
                continue;
            }
            if (text == null) {
                text = new StringBuilder(10);
            }
            text.append(((IChatComponent)value)
                    .getUnformattedTextForChat());
        }
        return text == null ? "" : text.toString();
    }

    /**
     * Where a row drawn as the chat's small text shrinks from — the
     * text-space x its first run starts at — or -1 for a row drawn at the
     * words' own size. A reply's quote and a message's reaction chips
     * are small, as the timestamps are, wherever the screen has a size
     * smaller than the words'; at GUI scale 1 it has none.
     */
    private static float smallRowPivot(IChatComponent row, boolean open,
                                       float smallScale) {
        if (row == null || smallScale >= 1.0F
                || !(ChatReplyMarker.isQuoteRow(row)
                        || ChatReactionMarker.isReactionRow(row))) {
            return -1.0F;
        }
        return LostTalesChatVisualStyle.contentStart(row, open);
    }

    /**
     * The line's timestamp runs, drawn in the column as small text: each
     * run keeps the colour and the decorations it was composed with —
     * the aside tone, the italic time — at one display pixel less per
     * font pixel than the message beside it, its capitals centred on
     * the message's and its shadow a pixel of its own size away. Only a
     * message's first line carries them, so a message is stamped once.
     */
    private static void drawTimestampRuns(FontRenderer font,
                                          IChatComponent line, int x,
                                          int y, int alpha) {
        if (font == null || line == null
                || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        float small = LostTalesChatVisualStyle.stackSmallScale();
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y
                    + LostTalesChatVisualStyle.stackSmallTopOffset(), 0.0F);
            GL11.glScalef(small, small, 1.0F);
            int cursor = 0;
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
                    rendered = LostTalesChatVisualStyle.styleCodesOnly(
                            formatting)
                            + LostTalesChatVisualStyle.removeColorCodes(text);
                    rgb = color != null ? color.intValue()
                            : LostTalesChatVisualStyle.IVORY;
                }
                LostTalesChatVisualStyle.drawColored(font, rendered, cursor,
                        0, rgb, alpha);
                cursor += font.getStringWidth(rendered);
            }
        } finally {
            GL11.glPopMatrix();
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
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(true);
        // Same winding as the backdrop: the GUI pass culls back faces.
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY,
                bottomAlpha);
        tessellator.addVertex(x, bottom, 0.0D);
        tessellator.addVertex(x + 1.0F, bottom, 0.0D);
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, topAlpha);
        tessellator.addVertex(x + 1.0F, top, 0.0D);
        tessellator.addVertex(x, top, 0.0D);
        LostTalesSkyrimUiStyle.endQuads(tessellator, true);
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
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(true);
        // Same winding as the backdrop: the GUI pass culls back faces.
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, safeAlpha);
        tessellator.addVertex(left, top + 1.0F, 0.0D);
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, 0);
        tessellator.addVertex(right, top + 1.0F, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.setColorRGBA_I(LostTalesChatVisualStyle.IVORY, safeAlpha);
        tessellator.addVertex(left, top, 0.0D);
        LostTalesSkyrimUiStyle.endQuads(tessellator, true);
    }

    /** A flat quad at fractional edges; {@code Gui.drawRect} is whole. */
    static void fillRect(float left, float top, float right,
                                 float bottom, int argb) {
        int alpha = argb >>> 24;
        if (alpha <= 0 || right <= left || bottom <= top) {
            return;
        }
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(false);
        tessellator.setColorRGBA_I(argb & 0xFFFFFF, alpha);
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
        LostTalesSkyrimUiStyle.endQuads(tessellator, false);
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

    /**
     * The colour a line's stretch of the panel wears. At rest it is the
     * panel's own, crossed toward a mention's tint as far as the line
     * has arrived and toward a jump's flash as far as it still burns,
     * each over the last. Under the pointer the same layers cross, as
     * far as the pointer's shade has come in, toward their selected
     * colours — the selected line's for the panel, the selected
     * mention's for the tint, the flash's own lighter shade for the
     * flash — so a highlighted line lightens its own colour rather than
     * giving it up.
     */
    static int lineBandRgb(int panelRgb, int selectedRgb, boolean pinged,
                           int mentionRgb, int selectedMentionRgb,
                           float share, float flash, int flashRgb,
                           int selectedFlashRgb, float hover) {
        int resting = layeredRgb(panelRgb, pinged, mentionRgb, share, flash,
                flashRgb);
        if (hover <= 0.0F) {
            return resting;
        }
        return LostTalesChatVisualStyle.blend(resting, layeredRgb(
                selectedRgb, pinged, selectedMentionRgb, share, flash,
                selectedFlashRgb), hover);
    }

    /** A line's layers over {@code baseRgb}: a mention's tint, then a jump's flash. */
    private static int layeredRgb(int baseRgb, boolean pinged,
                                  int mentionRgb, float share, float flash,
                                  int flashRgb) {
        int rgb = baseRgb;
        if (pinged) {
            rgb = LostTalesChatVisualStyle.blend(rgb, mentionRgb, share);
        }
        if (flash > 0.0F) {
            rgb = LostTalesChatVisualStyle.blend(rgb, flashRgb,
                    flash * share);
        }
        return rgb;
    }

    /**
     * How far a line has arrived, as a share: its entry fade, faint
     * while it is still on its way to the server. What a mention's tint
     * crosses in with.
     */
    private static float lineShare(ChatLine line) {
        float share = (float)entryOpacity(line);
        if (ClientChatPendingEchoes.isPending(line.getChatLineID())) {
            share *= ClientChatPendingEchoes.PENDING_OPACITY;
        }
        return Math.max(0.0F, Math.min(1.0F, share));
    }

    /** Whether the context has the blend equations the recolour needs. */
    private static Boolean recolourSupported;

    private static boolean canRecolour() {
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
     * Gives a stretch of the panel another colour without laying a
     * second layer over it: the panel's own share there is taken back
     * out — its colour at its opacity, subtracted — and {@code toRgb} at
     * the same opacity is added in its place. The stretch reads as the
     * panel painted in the new colour, and the world behind it is
     * darkened once, as everywhere else. Both passes follow the panel's
     * thinning-out vertex for vertex, so the stretch meets the rest of
     * the panel without an edge. Only for area the panel covers and
     * nothing has been drawn over since; where the blend equations are
     * missing, the new colour is laid over the panel instead.
     */
    private static void recolourBackdrop(float curveLeft, float left,
                                         float top, float right,
                                         float bottom, int alpha,
                                         int fromRgb, int toRgb) {
        recolour(curveLeft, left, top, right, bottom, alpha, fromRgb, toRgb,
                BACKDROP_FADE_WEIGHTS);
    }

    /**
     * As above for a surface with the opacity profile {@code weights}:
     * the panel's thinning one, or {@link #FLAT_WEIGHTS} for one laid in
     * a single flat colour, as the timestamp column is.
     */
    private static void recolour(float curveLeft, float left, float top,
                                 float right, float bottom, int alpha,
                                 int fromRgb, int toRgb, float[] weights) {
        if (!canRecolour()) {
            drawChatBackdrop(curveLeft, left, top, right, bottom, alpha,
                    toRgb, GL11.GL_ONE_MINUS_SRC_ALPHA, weights);
            return;
        }
        try {
            GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
            drawChatBackdrop(curveLeft, left, top, right, bottom, alpha,
                    fromRgb, GL11.GL_ONE, weights);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            drawChatBackdrop(curveLeft, left, top, right, bottom, alpha,
                    toRgb, GL11.GL_ONE, weights);
        } finally {
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        }
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
        drawChatBackdrop(left, left, top, right, bottom, alpha,
                backdropRgb);
    }

    /**
     * As above with the band's own opacity profile, sampled evenly from
     * its left edge to its right: the closed feed's lines thin out away
     * from whichever edge they stand against ({@link ChatFeedAlignment}).
     */
    private static void drawChatBackdrop(
            float left, float top, float right, float bottom, int alpha,
            int backdropRgb, float[] weights) {
        drawChatBackdrop(left, left, right, top, right, bottom, alpha,
                backdropRgb, GL11.GL_ONE_MINUS_SRC_ALPHA, weights);
    }

    /**
     * As above for the part of a band from {@code left} rightward, its
     * curve still running from {@code curveLeft}: a window's message
     * area starts at the timestamp column's separator, and its backdrop
     * thins out along the window's whole width, exactly as it does in a
     * window without the column. The step the band starts inside begins
     * at the curve's value there.
     */
    private static void drawChatBackdrop(
            float curveLeft, float left, float top, float right,
            float bottom, int alpha, int backdropRgb) {
        drawChatBackdrop(curveLeft, left, right, top, right, bottom, alpha,
                backdropRgb, GL11.GL_ONE_MINUS_SRC_ALPHA,
                BACKDROP_FADE_WEIGHTS);
    }

    /**
     * As above with the framebuffer's own share weighted by
     * {@code destinationFactor} — {@code GL_ONE} adds the band to what
     * is there, or, under a reversed blend equation, takes it away — and
     * the opacity profile {@code weights}, sampled evenly from the
     * curve's left edge to the band's right, one more sample than the
     * steps it is drawn in. The framebuffer's alpha is left as it is.
     */
    private static void drawChatBackdrop(
            float curveLeft, float left, float top, float right,
            float bottom, int alpha, int backdropRgb,
            int destinationFactor, float[] weights) {
        drawChatBackdrop(curveLeft, left, right, top, right, bottom, alpha,
                backdropRgb, destinationFactor, weights);
    }

    /**
     * As above, drawing only up to {@code drawRight}: the band's curve
     * still runs to {@code right}, and the band stops short of it.
     */
    private static void drawChatBackdrop(
            float curveLeft, float left, float drawRight, float top,
            float right, float bottom, int alpha, int backdropRgb,
            int destinationFactor, float[] weights) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        int steps = weights == null ? 0 : weights.length - 1;
        if (drawRight <= left || bottom <= top || safeAlpha <= 0
                || steps < 1) {
            return;
        }
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(true);
        if (destinationFactor != GL11.GL_ONE_MINUS_SRC_ALPHA) {
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, destinationFactor,
                    GL11.GL_ZERO, GL11.GL_ONE);
        }
        float width = right - curveLeft;
        for (int step = 0; step < steps; step++) {
            float x0 = curveLeft + width * step / (float)steps;
            float x1 = curveLeft + width * (step + 1) / (float)steps;
            if (x1 <= left) {
                continue;
            }
            if (x0 >= drawRight) {
                break;
            }
            float w0 = weights[step];
            float w1 = weights[step + 1];
            if (x0 < left) {
                w0 += (w1 - w0) * (left - x0) / (x1 - x0);
                x0 = left;
            }
            if (x1 > drawRight) {
                // The band stops inside the step: the curve's value
                // there, as at a start inside one.
                w1 = w0 + (w1 - w0) * (drawRight - x0) / (x1 - x0);
                x1 = drawRight;
            }
            int a0 = Math.round(safeAlpha * w0);
            int a1 = Math.round(safeAlpha * w1);
            // Same winding as the backdrop's other quads: the GUI pass
            // culls back faces.
            tessellator.setColorRGBA_I(backdropRgb, a1);
            tessellator.addVertex(x1, bottom, 0.0D);
            tessellator.addVertex(x1, top, 0.0D);
            tessellator.setColorRGBA_I(backdropRgb, a0);
            tessellator.addVertex(x0, top, 0.0D);
            tessellator.addVertex(x0, bottom, 0.0D);
        }
        LostTalesSkyrimUiStyle.endQuads(tessellator, true);
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
            // The line's own head, or the head a reply's quote wears.
            ChatHeadMarker.Data marker = ChatHeadMarker.headOf(part);
            if (marker != null) {
                float opacity = alpha / 255.0F;
                ChatEmoji mark = marker.mark();
                if (mark != null) {
                    // A line from the bridge or from the server has no
                    // account behind it; its mark stands where the head
                    // would, drawn 1:1 — its slot is declared two
                    // pixels wider than a head's, so it keeps the same
                    // clear pixels either side instead of eating into
                    // them. Centred in the line band exactly as an
                    // inline emoji is.
                    float markTop = y - HEAD_TOP_OFFSET
                            + centredBoxTop(CONTENT_BOX_HEIGHT);
                    ChatEmojiRenderer.drawShadow(minecraft, mark,
                            x + HEAD_LEFT_OFFSET
                                    + LostTalesChatVisualStyle.SHADOW_OFFSET,
                            markTop + LostTalesChatVisualStyle.SHADOW_OFFSET,
                            ChatEmoji.SPRITE_SIZE,
                            LostTalesChatVisualStyle.SHADOW,
                            Math.round(alpha
                                    * LostTalesChatVisualStyle.SHADOW_OPACITY));
                    ChatEmojiRenderer.draw(minecraft, mark,
                            x + HEAD_LEFT_OFFSET, markTop,
                            ChatEmoji.SPRITE_SIZE, alpha);
                    return;
                }
                drawHeadShadow(minecraft, marker, x, y,
                        opacity * LostTalesChatVisualStyle.SHADOW_OPACITY);
                if (marker.npcIdentity) {
                    LostTalesCharacterHeadIconRenderer.drawNpcHead(
                            minecraft, marker.skinId,
                            x + HEAD_LEFT_OFFSET, y, HEAD_SIZE, 1.0F, opacity);
                } else if (marker.accountIdentity) {
                    LostTalesCharacterHeadIconRenderer.drawAccountHead(
                            minecraft, marker.senderId,
                            x + HEAD_LEFT_OFFSET, y, HEAD_SIZE, 1.0F, opacity);
                } else {
                    LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                            minecraft, marker.senderId, marker.skinId,
                            x + HEAD_LEFT_OFFSET, y, HEAD_SIZE, 1.0F, opacity);
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
     * text shadow, on whole pixels. Silhouette mode gives every skin the
     * same shadow colour instead of a darkened copy of its own pixels.
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
                        minecraft, marker.skinId, shadowX, shadowY, HEAD_SIZE,
                        1.0F, 1.0F, 1.0F, opacity);
            } else if (marker.accountIdentity) {
                LostTalesCharacterHeadIconRenderer.drawTintedAccountHeadBase(
                        minecraft, marker.senderId, shadowX, shadowY, HEAD_SIZE,
                        1.0F, 1.0F, 1.0F, opacity);
            } else {
                LostTalesCharacterHeadIconRenderer.drawTintedSnapshotHeadBase(
                        minecraft, marker.senderId, marker.skinId,
                        shadowX, shadowY, HEAD_SIZE,
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
                || !LostTalesChatPresentation.isLastMessage(
                        line.getChatLineID())) {
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
                || !LostTalesChatPresentation.isLastMessage(
                        line.getChatLineID())) {
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
