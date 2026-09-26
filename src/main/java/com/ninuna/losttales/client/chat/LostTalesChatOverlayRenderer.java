package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.chat.ChatFeedPlacement;
import com.ninuna.losttales.client.window.PageTab;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowDrawing;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowOpening;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WindowTab;
import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiCornerCut;
import com.ninuna.losttales.gui.style.LostTalesUiCornerMark;
import com.ninuna.losttales.gui.style.LostTalesUiFlatLayers;
import com.ninuna.losttales.gui.style.LostTalesUiItemIcon;
import com.ninuna.losttales.gui.style.LostTalesUiRules;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatDeliveryMark;
import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
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
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.motion.MotionIds;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.renderer.OpenGlHelper;
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
 * {@link WindowPlacement} gives it, with the opening motion every
 * other Lost Tales screen uses, and records the screen band of every
 * line it draws in its
 * {@link WindowFrame}; all mouse-to-line mapping (hover card,
 * clipboard, component hits, the vanilla hit-test hook) resolves against
 * those recorded bands, so it always matches what is on screen.
 */
public final class LostTalesChatOverlayRenderer {
    /** Width of the bar a mention wears on the window's left edge. */
    static final float MENTION_BAR_WIDTH = 2.0F;
    /** Clear pixels between the window's frame and a mention's bar. */
    static final float MENTION_BAR_INSET = 1.0F;
    /** The unread divider's rule and date: the palette's red. */
    private static final int UNREAD_DIVIDER_RGB =
            LostTalesColors.rgb(LostTalesColors.CRIMSON);
    /**
     * Vertical distance between chat lines: a window's line, since
     * vanilla's 9 px stride cannot hold the 10 px emoji. Bands are
     * contiguous — each line's backdrop fills the full stride.
     */
    public static final int LINE_HEIGHT = WindowStyle.LINE_HEIGHT;
    /**
     * The font's lowercase letters: the row they start on below the
     * capitals' top, and how many rows they stand. Where most of a name's
     * weight is, since a name is mostly lowercase.
     */
    static final int GLYPH_X_TOP = 2;
    static final int GLYPH_X_HEIGHT = 5;
    /** Height of the content box every inline emoji, item and marker fills. */
    static final int CONTENT_BOX_HEIGHT = (int)ChatInlineIcons.CONTENT_SIZE;
    /** Height of a head's face, drawn one texel to one pixel. */
    static final int HEAD_SIZE = 8;
    /**
     * How far above a row's bottom edge its text's top edge stands.
     * Everything drawn against the text — heads, emoji, items,
     * timestamps — is placed from there; a reaction row centres its
     * chips in the row instead ({@link #reactionTextTop}).
     */
    static final int TEXT_OFFSET = LINE_HEIGHT - WindowStyle.ROW_TEXT_TOP;
    /**
     * Where a divider's rule stands below its row's top edge at the
     * words' own size: on the middle row of the capitals a message's
     * text would have in the row, so the date written on the rule is
     * centred on it exactly. In an even row that is half a pixel above
     * the row's middle, as the capitals are, with the odd clear row below
     * the rule. Drawn as small text, the rule runs on the middle row of
     * the small capitals, which are centred on these.
     */
    static final int DIVIDER_RULE_OFFSET = WindowStyle.ROW_TEXT_TOP + LostTalesUiInk.CAP_HEIGHT / 2;
    /**
     * Where a head sits against the text it stands beside: centred on the
     * capitals by the row's rule ({@link #centredBoxTop}), on whole
     * pixels — a head is pixel art at one texel to one pixel. Eight rows
     * against seven, so the face starts a row above the capitals and its
     * middle stands half a pixel above theirs.
     */
    static final float HEAD_TOP_OFFSET = WindowStyle.centredBoxTop(HEAD_SIZE);

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
    static final float[] FLAT_WEIGHTS = {1.0F, 1.0F};

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
     * How long a line stays on screen in the closed feed, in the update
     * counter's own ticks: vanilla's own ten seconds, held to full
     * opacity for the first nine and falling to nothing over the last.
     * A line older than this is not drawn at all, which is also what
     * decides how long a run may go on there — see
     * {@link ChatGroupRuns}.
     */
    static final int FEED_FADE_TICKS = 200;
    /** Between the speech bubble and the words of the feed's typing row. */
    private static final int FEED_TYPING_BUBBLE_GAP = 3;
    /** Between two conversations on the feed's typing row. */
    private static final int FEED_TYPING_SEGMENT_GAP = 8;
    /**
     * The opacity of the hatch laid over message rows the history does
     * not reach yet, at the middle of the hatched region: a third, since
     * it lies over the panel's own surface. The chat sheet's own hatch
     * cell, drawn in the colours it was authored in, falling off to
     * nothing at the region's top and bottom edges.
     */
    private static final int EMPTY_HATCH_ALPHA = Math.round(255.0F / 3.0F);
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
            List<Window> windows = WindowLayout.windows();
            // Both per-window caches are let go together: the frame that
            // records where a window drew, and the lines it laid out for
            // itself.
            ChatFrame.prune(windows);
            ChatWindowLines.prune(windows);
            if (!open) {
                drawFeed(minecraft, chat, drawn, screenWidth, screenHeight,
                        partialTicks);
                return true;
            }
            ChatFrame.feed().drawn = false;
            if (minecraft.currentScreen instanceof WindowScreen) {
                // The screen draws its windows after the HUD, each one
                // whole; vanilla's chat pass is still cancelled here.
                return true;
            }
            // Another chat screen is open: the windows are drawn here,
            // back to front, the window in use over the others.
            LostTalesGuiAnimationSample opening =
                    WindowOpening.sample();
            List<Window> stacked = WindowLayout.stacked();
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
    static void drawWindowForScreen(Minecraft minecraft, Window window,
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
            ChatFrame.of(window).drawn = false;
        } catch (RuntimeException ignored) {
            ChatFrame.of(window).drawn = false;
        }
    }

    /**
     * Opening the screen brings the history in with the same sampler as
     * every other Lost Tales screen; the tabs follow because they stand
     * on the bands.
     */
    private static void drawOpenWindow(Minecraft minecraft, GuiNewChat chat,
                                       List<ChatLine> drawn,
                                       Window window, int screenWidth,
                                       int screenHeight,
                                       LostTalesGuiAnimationSample opening) {
        ChatFrame frame = ChatFrame.of(window);
        // Only the delivery marks and avatars this draw puts on screen
        // answer the pointer.
        frame.clearMarks();
        frame.clearAvatars();
        WindowTab front = ChatFrame.activeTab(window,
                ChatFrame.visibleTabs(window));
        if (front instanceof PageTab) {
            // A page is no conversation: its window holds no lines, no
            // member list and no search, and the screen draws the page.
            WindowDrawing.layOutPage(minecraft, window, frame, (PageTab)front,
                    screenWidth, screenHeight, opening);
            return;
        }
        frame.page = null;
        ChatTab view = ChatTab.from(front);
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
        int chatWidth = WindowPlacement.drawnChatWidth(window, minecraft,
                screenWidth);
        // The timestamp area and the member list glide in and out before
        // anything reads their width: the lines are laid out against it.
        // The list takes the width its edge was dragged to, within a third
        // of the window and the words' least room beside it, and narrows
        // with the window down to its heads rather than leaving.
        float windowChatWidth = chatWidth / chat.func_146244_h() + 6.0F;
        ChatMemberList.measure(frame.members,
                ChatLayout.getMembersWidth(window), windowChatWidth,
                ChatTimestampColumn.of(frame, minecraft.fontRenderer)
                        .messageX());
        frame.advancePanels(window, view != null);
        // The unread divider opens a run of its own under it while it
        // stands, so the window lays its lines out knowing where it is.
        Integer unread = view == null ? null
                : ClientChatChannelViews.unreadDividerLine(view);
        List<ChatLine> own = ChatWindowLines.forWindow(minecraft, chat,
                window, filter, chatWidth,
                unread == null ? 0 : unread.intValue());
        List<ChatLine> lines = own != null ? own
                : ClientChatChannelViews.visibleLines(drawn, filter);
        frame.lines = lines;
        frame.view = view;
        if (front == null) {
            // Nothing the player can see lives here right now.
            frame.drawn = false;
            frame.bands.reset(lines, 0, 1.0F);
            return;
        }
        // The scroll range is taken from the rows the window will draw,
        // the unread divider's own row included, so the oldest message
        // can always be scrolled to. The rows are laid out here, before
        // anything measures the stack against the box: the stack's blank
        // rows are shorter than a line.
        frame.resolveDividerRow(lines, unread);
        frame.resolveRows();
        WindowPlacement.Box box = WindowPlacement.windowBounds(
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
        double scrollTarget = view == null ? 0.0D
                : ClientChatChannelViews.getScroll(view, frame.contentRows(),
                        roomLines);
        double scroll = view == null ? 0.0D
                : ClientChatChannelViews.renderedScroll(view, scrollTarget);
        frame.renderedScrollLines = scroll;
        // The history is on its way to where it was scrolled: the toolbar
        // waits for it to rest.
        frame.historyMoving = Math.abs(scroll - scrollTarget) > 0.001D;
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
        // The window's own rectangle of the blurred frame, the ring its
        // frame stands on included, under the backdrop; drawn only while
        // the chat screen captured one this frame, so every other path
        // keeps the plain backdrop. The history band thins out to the
        // right exactly as its backdrop does; the tab row's band and the
        // bar's stay whole.
        int ring = WindowPlacement.FRAME_WIDTH;
        LostTalesGuiRegionBlur blur = LostTalesGuiRegionBlur.getInstance();
        double blurLeft = frame.drawnLeft() - ring;
        double blurRight = frame.drawnLeft() + (frame.boxRight - frame.boxLeft)
                + ring;
        double blurTop = frame.boxTop + frame.motionY - ring;
        double blurBottom = frame.boxBottom + frame.motionY + ring;
        double historyTop = frame.drawnBaseline() - room;
        double historyBottom = frame.drawnBaseline()
                + WindowPlacement.lineHeight(minecraft);
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
        // message text's own left edge; the timestamp area lies between
        // it and the window edge.
        ChatTimestampColumn columns =
                ChatTimestampColumn.of(frame, minecraft.fontRenderer);
        float originX = (float)LostTalesDisplayPixels.snap(
                frame.drawnLeft() + columns.messageX() * scale);
        float originY = (float)frame.drawnBaseline();
        drawWindow(minecraft, chat, frame, filter, lines, scroll, room,
                originX, originY, true, opening, chatWidth, columns);
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
        List<Window> windows = WindowLayout.windows();
        for (int index = 0; index < windows.size(); index++) {
            ChatFrame.of(windows.get(index)).drawn = false;
        }
        ChatFrame frame = ChatFrame.feed();
        List<ChatTab> feedTabs = ChatLayout.feedTabs();
        ChatLineFilter filter = ChatLineFilter.of(feedTabs);
        // Someone typing into a conversation the feed carries raises
        // the lines a row, and the typing row comes up under them.
        float typingShare = ChatFeedTyping.advance(
                ChatFeedTyping.segments(feedTabs), System.nanoTime());
        List<ChatLine> own = ChatWindowLines.forFeed(minecraft, chat, filter);
        List<ChatLine> lines = own != null ? own
                : ClientChatChannelViews.visibleLines(drawn, filter);
        frame.lines = lines;
        frame.view = null;
        frame.resolveDividerRow(lines, null);
        frame.resolveRows();
        // The frame is captured and blurred only while the feed has a
        // line or its typing row on screen; the rest of the time
        // gameplay pays nothing for the feed's blur.
        if (LostTalesConfig.enableChatBackgroundBlur
                && LostTalesConfig.enableGuiBackgroundBlur
                && (typingShare > 0.0F || !lines.isEmpty()
                        && lines.get(0) != null
                        && minecraft.ingameGUI.getUpdateCounter()
                                - lines.get(0).getUpdatedCounter()
                                        < FEED_FADE_TICKS)) {
            LostTalesGuiRegionBlur.getInstance().capture(minecraft,
                    partialTicks, (float)LostTalesConfig.guiBlurStrength);
        }
        WindowPlacement.Box box = ChatFeedPlacement.bounds(
                minecraft, screenWidth, screenHeight);
        float scale = chat.func_146244_h();
        frame.begin(box, scale, 0.0F, 0.0F);
        frame.drawn = true;
        // The feed shows neither the timestamp column nor the avatars;
        // its lines begin the edge gap from its edge and wear the small
        // head beside the name.
        ChatTimestampColumn columns = ChatTimestampColumn.feed();
        float originX = (float)LostTalesDisplayPixels.snap(
                frame.drawnLeft() + columns.messageX() * scale);
        float baseline = (float)frame.drawnBaseline();
        // The lines stand raised by the typing row's share of a row, on
        // whole display pixels as the stack always moves.
        float lift = snapToDisplayPixels(minecraft,
                typingShare * LINE_HEIGHT * scale);
        int chatWidth = WindowPlacement.chatWidth(minecraft);
        drawWindow(minecraft, chat, frame, filter, lines, 0.0D,
                (float)frame.room, originX, baseline - lift, false,
                LostTalesGuiAnimationSample.SETTLED, chatWidth, columns);
        if (typingShare > 0.0F) {
            drawFeedTyping(minecraft, ChatFeedTyping.shown(), originX,
                    baseline, lift, scale, chatWidth, columns);
        }
    }

    /**
     * The closed feed's typing row, under its newest line: each
     * conversation someone is typing into, named as the feed names a
     * line's channel, then the speech bubble where a line's head would
     * stand and the typing line's words and dots. A conversation that
     * does not fit whole is left out, and the first is cut to fit. It
     * wears a band and a blur of its own, as every feed line does, and
     * rises with the lines from the feed's bottom edge, cut there, so it
     * never lies over the line above it.
     */
    private static void drawFeedTyping(Minecraft minecraft,
                                       List<ChatFeedTyping.Segment> segments,
                                       float originX, float baseline,
                                       float lift, float scale,
                                       int chatWidth,
                                       ChatTimestampColumn columns) {
        FontRenderer font = minecraft.fontRenderer;
        int alpha = Math.round(255.0F
                * WindowStyle.opacity(minecraft));
        if (segments.isEmpty() || font == null
                || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        ChatFeedAlignment alignment = ChatFeedAlignment.current();
        float panelLeft = -columns.messageX();
        float panelRight = panelLeft
                + MathHelper.ceiling_float_int(chatWidth / scale) + 6.0F;
        // The row stands between the feed's edge gaps, as its lines do.
        int room = (int)ChatTimestampColumn.feedRowRoom(panelRight - panelLeft);
        LostTalesUiSheet bubble = LostTalesUiSheet.SPEECH_BUBBLE;
        // Each conversation's prefix and words, as far as the row holds.
        List<String> prefixes = new ArrayList<String>();
        List<String> words = new ArrayList<String>();
        int used = 0;
        for (ChatFeedTyping.Segment segment : segments) {
            String prefix = (segment.tab.isWhisper()
                    ? ChatChannel.WHISPER.getDisplayName()
                    : ClientChatChannelState.displayName(segment.tab)) + ": ";
            String said = ChatTypingLine.words(segment.names);
            int width = font.getStringWidth(prefix) + bubble.getWidth()
                    + FEED_TYPING_BUBBLE_GAP + ChatTypingLine.width(font, said);
            int gap = prefixes.isEmpty() ? 0 : FEED_TYPING_SEGMENT_GAP;
            if (!prefixes.isEmpty() && used + gap + width > room) {
                break;
            }
            prefixes.add(prefix);
            words.add(said);
            used += gap + width;
        }
        // The row's bottom edge, a row under the lines' raised baseline.
        float rowBottom = baseline - lift + LINE_HEIGHT * scale;
        boolean clipped = LostTalesUiClip.beginRows(minecraft,
                baseline - LINE_HEIGHT * scale, baseline, true);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(originX, rowBottom, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            LostTalesGuiRegionBlur.getInstance().drawFadedRegionInTransform(
                    panelLeft, -LINE_HEIGHT, panelRight, 0.0F,
                    alignment.bandWeights(), originX, rowBottom, scale,
                    alpha / 255.0F);
            drawChatBackdrop(panelLeft, -LINE_HEIGHT, panelRight, 0.0F,
                    alpha / 2, WindowStyle.backdropRgb(),
                    alignment.bandWeights());
            GL11.glEnable(GL11.GL_BLEND);
            // The row stands against the feed's edge by its ink, as a
            // line does; its last dot has one column of spacing after it.
            int start = Math.round(floorToStackPixel(alignment.rowShift(room,
                    0.0F, Math.min(used, room) - 1.0F)));
            int x = start;
            int y = -TEXT_OFFSET;
            long now = System.nanoTime();
            for (int index = 0; index < prefixes.size(); index++) {
                if (index > 0) {
                    x += FEED_TYPING_SEGMENT_GAP;
                }
                ChatTab tab = segments.get(index).tab;
                LostTalesUiInk.drawText(font,
                        prefixes.get(index), x, y,
                        ClientChatChannelState.displayColor(tab), alpha);
                x += font.getStringWidth(prefixes.get(index));
                bubble.drawWithShadow(x, y + WindowStyle.centredBoxTop(bubble.getHeight()),
                        alpha);
                x += bubble.getWidth() + FEED_TYPING_BUBBLE_GAP;
                // Only a first conversation longer than the row is cut.
                x += ChatTypingLine.draw(font, words.get(index), x, y,
                        used > room ? room - (x - start) : Integer.MAX_VALUE,
                        alpha, now);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            LostTalesUiClip.end(clipped);
        }
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
            List<ChatFrame> frames = ChatFrame.drawn();
            for (int index = frames.size() - 1; index >= 0; index--) {
                ChatFrame frame = frames.get(index);
                ChatLineBands bands = frame.bands;
                List<ChatLine> lines = frame.lines;
                if (lines != null && bands.describes(lines, lines.size())) {
                    int band = bands.find(mouseX, mouseY);
                    if (band >= 0) {
                        return new Band(frame, lines, bands.viewIndexOf(band),
                                bands.localX(band, mouseX), bands.topOf(band),
                                bands.bottomOf(band), bands.scale(),
                                mouseX < bands.leftOf(band));
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
        Hit avatar = avatarHitAt(minecraft, mouseX, mouseY);
        if (avatar != null) {
            return avatar;
        }
        Band band = bandAt(minecraft, mouseX, mouseY);
        if (band == null) {
            return null;
        }
        if (band.inArea) {
            // The timestamp area lights its row, but no run of the row
            // stands in it.
            return null;
        }
        try {
            List<ChatLine> lines = band.lines;
            if (lines == null || band.viewIndex >= lines.size()
                    || lines.get(band.viewIndex) == null) {
                return null;
            }
            ChatLine hitLine = lines.get(band.viewIndex);
            IChatComponent lineRoot = hitLine.func_151461_a();
            // The words of a message under the pointer stand where its
            // motion has carried them, and answer there.
            float localX = band.localX;
            if (ChatLayoutMarker.isBodyRow(lineRoot)
                    && !ChatReactionMarker.isReactionRow(lineRoot)) {
                ChatRowMotion motion = ChatLineHover.rowMotion(
                        hitLine.getChatLineID(), System.nanoTime());
                if (motion != null) {
                    localX -= motion.endX();
                }
            }
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
                if (localX < cursor) {
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
     * The speaker's head under the pointer when the pointer stands on an
     * avatar in an open window: the head's own run on the speaker's row,
     * which is the speaker's name to everything that asks — the card, the
     * underline under the name, the hand, and a click opening the
     * conversation. Windows are asked front to back, and no further than
     * the window the point is in; null anywhere else.
     */
    private static Hit avatarHitAt(Minecraft minecraft, float mouseX,
                                   float mouseY) {
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        if (chat == null || !chat.getChatOpen()) {
            return null;
        }
        List<ChatFrame> frames = ChatFrame.drawn();
        for (int index = frames.size() - 1; index >= 0; index--) {
            ChatFrame frame = frames.get(index);
            int chatLineId = frame.avatarLineAt(mouseX, mouseY);
            List<ChatLine> lines = frame.lines;
            if (chatLineId != 0 && lines != null) {
                for (int at = 0; at < lines.size(); at++) {
                    ChatLine line = lines.get(at);
                    if (line == null || line.getChatLineID() != chatLineId) {
                        continue;
                    }
                    IChatComponent row = line.func_151461_a();
                    int head = ChatAvatar.headIndex(row);
                    if (head < 0) {
                        continue;
                    }
                    IChatComponent part = partAt(row, head);
                    return part == null ? null : new Hit(part, row, head,
                            new Band(frame, lines, at, 0.0F, mouseY, mouseY,
                                    frame.bands.scale(), true), 0, 0);
                }
            }
            if (frame.contains(mouseX, mouseY)) {
                return null;
            }
        }
        return null;
    }

    /** The run at a place on a row, counted as a row's iterator yields them. */
    private static IChatComponent partAt(IChatComponent row, int place) {
        int index = -1;
        for (Object value : row) {
            if (value instanceof IChatComponent && ++index == place) {
                return (IChatComponent)value;
            }
        }
        return null;
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
        final ChatFrame frame;
        /** The view's line list the band indexes into. */
        final List<ChatLine> lines;
        /** Index into {@link #lines}. */
        final int viewIndex;
        /** Pointer x in the line's own unscaled text space. */
        final float localX;
        final float top;
        final float bottom;
        final float scale;
        /**
         * Whether the pointer stands in the window's timestamp area,
         * left of where the row's text starts: the row is lit there, but
         * none of its runs is under the pointer.
         */
        final boolean inArea;

        private Band(ChatFrame frame, List<ChatLine> lines,
                     int viewIndex, float localX, float top, float bottom,
                     float scale, boolean inArea) {
            this.frame = frame;
            this.lines = lines;
            this.viewIndex = viewIndex;
            this.localX = localX;
            this.top = top;
            this.bottom = bottom;
            this.scale = scale;
            this.inArea = inArea;
        }
    }


    private static void drawWindow(
            Minecraft minecraft, GuiNewChat chat, ChatFrame frame,
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
        if (!rows.describes(lines, totalLineCount, dividerIndex, open)) {
            rows.reset(lines, dividerIndex, open);
            frame.glide.relaid(lines, rows, dividerIndex, System.nanoTime());
        }
        // A row carried to a new layout glides there while the view
        // reads the newest line. Scrolled away, the scroll holds the line
        // being read in place, and the rows are set down at once.
        ChatRowGlide glide = frame.glide;
        if (scrollLines > 0.0D) {
            glide.halt();
        }
        glide.advance(System.nanoTime(), stackPixelsPerUnit());
        int scrollRow = Math.min(scrollPosition, totalRowCount);
        // Pixels of stack under the baseline, and the slide's share of
        // the row the offset is inside.
        float stackBase = rows.top(scrollRow);
        float slidePixels = scrollSlide * rows.height(scrollRow);
        float totalHeight = rows.total();
        float opacity = WindowStyle.opacity(minecraft);
        float scale = chat.func_146244_h();
        ChatLineBands bands = frame.bands;
        bands.reset(lines, totalLineCount, scale);
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
        // the history is shorter than the room; full, it would only look
        // like it is trying to.
        float roomUnscaled = room / scale;
        boolean roomToSpare = totalHeight <= roomUnscaled + 0.01F;
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
                (roomToSpare ? entryDisplacement(filter, scrollPosition) : 0.0F)
                        + slidePixels * scale);
        float offset = stackOffset / scale;

        // The topmost row the loop reaches, so the cut is known before
        // it runs: every row that starts below the room's top edge once
        // the stack is offset — the head-room band above the room and
        // the slide included, so the row the slide reveals is drawn and
        // clipped where the room ends.
        float roomTop = stackBase + roomUnscaled
                + WindowPlacement.HISTORY_TOP_MARGIN / scale
                + Math.max(0.0F, offset);
        int placedRow = Math.min(totalRowCount - 1,
                rows.lastRowBelow(roomTop));
        // A row gliding up to a place above the room is drawn until it
        // has left it.
        int lastRow = Math.min(totalRowCount - 1,
                rows.lastRowBelow(roomTop + glide.deepestDrop()));
        // Height of the rows placed in the room.
        float plannedHeight = placedRow < scrollRow ? 0.0F
                : rows.top(placedRow + 1) - stackBase;
        // How far the topmost of them is drawn above its place: the
        // hatch over the rows the history does not reach follows it.
        float topLift = placedRow < 0 ? 0.0F
                : glide.lift(ChatStackRows.isDividerRow(placedRow,
                        dividerIndex) ? dividerIndex
                        : lineOfRow(placedRow, dividerIndex));
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
                - WindowPlacement.HISTORY_TOP_MARGIN;
        float clipBottom = restingY
                + WindowPlacement.lineHeight(minecraft);
        float topEdge = -roomUnscaled
                - WindowPlacement.HISTORY_TOP_MARGIN / scale;
        float bottomEdge = LINE_HEIGHT;
        // The timestamp area's left edge: the text origin this method
        // draws from lies messageX inside it. The avatars, the times and
        // the separator ride it, so they slide with the words as the
        // area goes.
        float areaLeft = -columns.messageX();
        // The panel reaches from the window's left edge — past the
        // timestamp column when there is one — to its right; the member
        // list stands in from the window's right edge as far as it has
        // come out, and the panel ends where it begins. An open window's
        // edges are laid on the display's grid on the screen, where the
        // window stands, and only then brought into the history's units:
        // the text origin slides with the area and is laid on the grid
        // afresh every frame, and edges measured from it would pick up
        // its rounding and shake while the area moves.
        float panelLeft = areaLeft;
        float windowRight = areaLeft + unscaledWidth + 6.0F;
        float panelRight = windowRight;
        if (open) {
            double screenLeft = frame.drawnLeft();
            double screenRight = LostTalesDisplayPixels.snap(
                    screenLeft + (unscaledWidth + 6.0F) * scale);
            panelLeft = (float)((screenLeft - originX) / scale);
            windowRight = (float)((screenRight - originX) / scale);
            panelRight = (float)((LostTalesDisplayPixels.snap(
                    screenRight - ChatMemberList.drawnWidth(frame) * scale)
                    - originX) / scale);
        }
        // The message area: from the timestamp column's separator, when
        // there is one, to the panel's right. The panel's backdrop and
        // every line's tint and shade stand in it and nowhere left of
        // it, so the column keeps a surface of its own and no two
        // backgrounds are ever laid over each other. While the area is
        // driven out, what is left of it narrows toward the window's
        // edge.
        float messageLeft = columns.shows()
                ? Math.max(panelLeft, areaLeft + columns.separatorX())
                : panelLeft;
        // Where the words' own stretch begins: past the separator.
        float wordsLeft = columns.shows()
                ? messageLeft + ChatTimestampColumn.SEPARATOR_WIDTH
                : panelLeft;
        // Where the area's contents are cut: the window's own left edge,
        // past which they slide as the area is driven out.
        double areaClipLeft = open ? frame.drawnLeft()
                : originX + panelLeft * scale;
        // The panel's opacity, shared by every stretch of it a line
        // recolours, so the stretch and the panel beside it are one.
        int panelAlpha = backdropAlpha(opacity, opening) / 2;
        // The timestamp column's opacity: the inset surface's two thirds,
        // thinning with the game's chat opacity as the panel does.
        int columnAlpha = Math.round(WindowStyle.INSET_ALPHA
                * opacity * opening.getOpacity());
        // The window's frame runs a frame wide outside the history's
        // sides, here in the history's own units.
        float ringLeft = panelLeft - WindowPlacement.FRAME_WIDTH / scale;
        float ringRight = windowRight
                + WindowPlacement.FRAME_WIDTH / scale;
        // Whether the history reaches the room's top, where the topmost
        // line owns the head-room under the row.
        boolean reachesTop = totalHeight >= roomUnscaled - 0.01F;
        // An open window keeps its row on its own top edge whatever the
        // tab in front holds. Its box is its own height, the game's chat
        // height, or the part of the screen it fills, and never what its
        // tabs hold, so switching tabs neither resizes the window nor
        // moves its row: the rows a shorter history does not reach stay
        // as hatched panel under the row. Nor does the row move as the
        // stack scrolls under it, the trailing scroll room included. Only
        // the closed feed, which has no row, ends on the lines it has.
        boolean full = open || totalRowCount <= 0 || reachesTop;
        // Laid on a whole display pixel, like every edge the window is
        // drawn from: the tab row hangs from it, and a row standing
        // between two pixels would lose one to its own inward cut.
        frame.setStackTop(full ? restingY - room
                : LostTalesDisplayPixels.snap(
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
                firstLine, lastRow, dividerIndex, rows, glide, stackBase,
                opacity, opening) : null;
        float toolbarShare = frame.toolbarShare(toolbar == null ? 0
                : toolbar.chatLineId, frame.historyMoving, System.nanoTime());
        if (toolbarShare <= 0.0F) {
            toolbar = null;
        }
        float toolbarScale = LostTalesChatVisualStyle.stackSmallScale();
        // Clear of the scrollbar's track at the panel's right edge.
        float toolbarLeft = panelRight - 1.0F - SCROLLBAR_WIDTH
                - TOOLBAR_GAP - toolbarWidth() * toolbarScale;
        boolean jumpShown = open && advanceJumpButton(frame, scrollLines);
        // Centred across the panel, on a whole pixel, the odd one left.
        int jumpWidth = jumpButtonWidth(minecraft.fontRenderer);
        float jumpLeft = (float)Math.floor((panelLeft + panelRight
                - jumpWidth) / 2.0F);
        float jumpTop = jumpButtonTop(frame, bottomEdge, originY, scale);
        Holes holes = new Holes();
        if (toolbar != null) {
            holes.add(toolbarLeft, toolbar.top + offset,
                    toolbarWidth() * toolbarScale,
                    TOOLBAR_HEIGHT * toolbarScale, CONTROL_DEPTH,
                    toolbarScale);
        }
        if (jumpShown) {
            holes.add(jumpLeft, jumpTop, jumpWidth, JUMP_BUTTON_HEIGHT,
                    CONTROL_DEPTH, 1.0F);
        }
        if (open) {
            // The reaction chips are framed buttons too, in the stack.
            placeChipHoles(holes, minecraft, font, lines, firstLine,
                    lastRow, dividerIndex, rows, glide, stackBase, opacity,
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
                        WindowStyle.backdropRgb());
                if (columns.shows()) {
                    // The timestamp area's own surface: the chat's inset
                    // surface, plum black at two thirds, the typing
                    // well's and a resting tab's, so the avatars and the
                    // times read as a margin rather than as part of the
                    // messages.
                    // The panel stops at the separator, so the two lie
                    // side by side, never one over the other. It thins
                    // with the game's chat opacity as the panel does.
                    LostTalesUiInk.fillRect(panelLeft, topEdge, messageLeft, bottomEdge,
                            LostTalesUiInk.argb(
                                    LostTalesUiInk.SURFACE_RGB,
                                    columnAlpha));
                }
                // The window's frame beside the history, in the colour it
                // touches (Nils): the timestamp area's, or the panel's
                // own left end; on the right the member list's while it
                // is out, else the panel's own colour at its full
                // strength rather than where it has thinned out to
                // nothing (Nils). A highlighted line recolours its
                // stretches with its row.
                int areaArgb = LostTalesUiInk.argb(
                        LostTalesUiInk.SURFACE_RGB, columnAlpha);
                int panelArgb = LostTalesUiInk.argb(
                        WindowStyle.backdropRgb(), panelAlpha);
                LostTalesUiInk.fillRect(ringLeft, topEdge, panelLeft, bottomEdge,
                        columns.shows() ? areaArgb : panelArgb);
                LostTalesUiInk.fillRect(windowRight, topEdge, ringRight, bottomEdge,
                        panelRight < windowRight ? areaArgb : panelArgb);
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
                float hatchedHeight = plannedHeight + topLift
                        + (totalLineCount <= 0 ? LINE_HEIGHT : 0.0F);
                if (totalHeight < roomUnscaled - 0.01F) {
                    LostTalesUiSheet.EMPTY_HATCH.drawTiledFadingFromMiddle(
                            wordsLeft, topEdge, panelRight,
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
                clipped = open && LostTalesUiClip.beginRows(minecraft, clipTop,
                        clipBottom, true);
                String dividerLabel = unreadDividerLabel(frame, lines,
                        dividerIndex, dividerDateIndex);
                RowSizes sizes = RowSizes.of(open);
                // The message whose lowest row of words has taken its
                // delivery mark: the stack walks upward, so that row is the
                // first of the message it reaches.
                int markedLineId = 0;
                // The line in the topmost slot owns the head-room above
                // it: its band reaches up to the rule instead of being
                // cut flush on its glyphs. A window with empty rows has no
                // line at its top, and neither has one whose topmost row
                // is the divider's, so nothing there is extended.
                int topmostIndex = ChatStackRows.isDividerRow(placedRow,
                        dividerIndex) ? -1 : lineOfRow(placedRow, dividerIndex);
                // The lift the stack's matrix carries: each row's own,
                // put on as the walk reaches it.
                float appliedLift = 0.0F;
                // How far the topmost line's band reaches up past its row.
                float topHeadroom = open && reachesTop
                        ? WindowPlacement.HISTORY_TOP_MARGIN / scale : 0.0F;
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
                            && reachesTop
                            ? WindowPlacement.HISTORY_TOP_MARGIN : 0.0F;
                    int age = minecraft.ingameGUI.getUpdateCounter()
                            - line.getUpdatedCounter();
                    if (age >= FEED_FADE_TICKS && !open) {
                        continue;
                    }
                    double fade = 1.0D - age / (double)FEED_FADE_TICKS;
                    fade = Math.max(0.0D,
                            Math.min(1.0D, fade * 10.0D));
                    fade *= fade;
                    int alpha = Math.round(lineAlpha(
                            open ? 255 : (int)(255.0D * fade), line, opacity,
                            opening) * glide.shown(lineIndex));
                    eligibleHeight += rowHeight;
                    // The row's bottom edge, measured up the stack from
                    // the row the scroll rests on.
                    int y = -(rows.top(rowIndex) - Math.round(stackBase));
                    // A row gliding to a new layout is drawn on its way
                    // there, everything of it moved together.
                    float lift = glide.lift(lineIndex);
                    if (lift != appliedLift) {
                        GL11.glTranslatef(0.0F, appliedLift - lift, 0.0F);
                        appliedLift = lift;
                    }
                    float entry = alignment.slide(entrySlide(line));
                    // A reply's quote and a message's reaction chips are
                    // the chat's small text and the row a message names
                    // its speaker on its large text: each drawn from
                    // where the row's first run starts, and hit where it
                    // is drawn.
                    float rowScale = sizes.scaleOf(line.func_151461_a());
                    float rowPivot = rowPivot(line.func_151461_a(), open,
                            rowScale);
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
                                + stackOffset + (y - lift - rowHeight) * scale
                                - headroom);
                        float bandBottom = Math.min(clipBottom,
                                originY + stackOffset + (y - lift) * scale);
                        // It ends where the panel does: the text origin
                        // stands past the window's columns, so the chat
                        // width measured from there would reach past the
                        // window's edge and over whatever stands beside it.
                        float bandRight = Math.min(
                                bandLeft + unscaledWidth * scale,
                                originX + panelRight * scale);
                        // It answers the pointer from the window's edge:
                        // the timestamp area lights the row as its words
                        // do.
                        if (bandBottom > bandTop) {
                            bands.add(lineIndex, bandLeft, bandRight, bandTop,
                                    bandBottom, Math.max(0.0F, rowPivot),
                                    rowScale, originX + panelLeft * scale);
                        }
                    }
                    if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
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
                        drawDividerRow(font, wordsLeft, panelRight,
                                y - rowHeight,
                                unreadHere ? dividerLabel : dayLabel,
                                unreadHere ? UNREAD_DIVIDER_RGB
                                        : LostTalesChatVisualStyle.asideRgb(),
                                alpha);
                        continue;
                    }
                    boolean dividerHere = lineIndex == dividerIndex;
                    int backdropRgb = WindowStyle.backdropRgb();
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
                    // The colour the row stands on — the open window's
                    // band or the feed line's own — which the row's
                    // backdrops take care never to be.
                    int surfaceRgb = backdropRgb;
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
                        surfaceRgb = bandRgb;
                        if (columns.shows()) {
                            // The line's row of the timestamp area wears
                            // the line's highlight too — a mention's tint,
                            // a jump's flash, a search's light, and under
                            // the pointer each a shade lighter — so a
                            // highlighted message is lit across its whole
                            // width, avatar and time included: the area's
                            // own surface recoloured in place, at its own
                            // two thirds, crossing in with the line. The
                            // pointer lights a line from the area as well.
                            int areaRgb = lineBandRgb(
                                    LostTalesUiInk.SURFACE_RGB,
                                    LostTalesChatVisualStyle.selectedLineRgb(),
                                    pinged, mentionRgb,
                                    LostTalesChatVisualStyle
                                            .selectedMentionLineRgb(),
                                    lineShare(line), flash,
                                    LostTalesChatVisualStyle.replyHighlightRgb(),
                                    LostTalesChatVisualStyle
                                            .selectedReplyHighlightRgb(),
                                    hoverFade);
                            if (areaRgb != LostTalesUiInk.SURFACE_RGB) {
                                // The window's frame beside the area takes
                                // the row's highlight with it.
                                recolour(ringLeft, ringLeft,
                                        y - rowHeight - headroom / scale,
                                        messageLeft, y, columnAlpha,
                                        LostTalesUiInk.SURFACE_RGB,
                                        areaRgb, FLAT_WEIGHTS);
                            }
                        } else if (bandRgb != backdropRgb) {
                            // With no timestamp area, the window's frame
                            // beside the line takes the line's band as the
                            // panel's own left end wears it.
                            recolour(ringLeft, ringLeft,
                                    y - rowHeight - headroom / scale,
                                    panelLeft, y, panelAlpha, backdropRgb,
                                    bandRgb, FLAT_WEIGHTS);
                        }
                        if (bandRgb != backdropRgb
                                && panelRight >= windowRight) {
                            // On the right the frame wears the panel at
                            // its full strength, and the line's band there
                            // the same way.
                            recolour(windowRight, windowRight,
                                    y - rowHeight - headroom / scale,
                                    ringRight, y, panelAlpha, backdropRgb,
                                    bandRgb, FLAT_WEIGHTS);
                        }
                        if (pinged) {
                            // A mention also wears a bar by the window's
                            // edge, left of the avatars, the way Discord's
                            // does: the tint says the line, the bar says it
                            // at a glance from across the window. A pixel
                            // clear of the frame, strongest at the
                            // message's middle and fading to nothing at its
                            // ends, as a tab's accent line does. It stands
                            // with the panel, still while the line's text
                            // slides in.
                            float barLeft = panelLeft + MENTION_BAR_INSET;
                            drawMentionBar(barLeft, barLeft + MENTION_BAR_WIDTH,
                                    y - rowHeight - headroom / scale, y,
                                    mentionSpan(lines, lineIndex, dividerIndex,
                                            rows, stackBase, topmostIndex,
                                            topHeadroom),
                                    alpha, mentionRgb);
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
                                        originY + stackOffset - lift * scale,
                                        scale, alpha / 255.0F);
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
                        surfaceRgb = color;
                    }
                    if (!open && pinged && alignment.hasMentionBar()) {
                        // In the feed the bar stands on the edge the
                        // lines stand against and slides in with its
                        // line; a feed of centred lines has no such edge
                        // and wears the tint alone. With no frame there,
                        // it keeps to the edge.
                        float barLeft = alignment == ChatFeedAlignment.RIGHT
                                ? panelRight - MENTION_BAR_WIDTH
                                : panelLeft;
                        drawMentionBar(barLeft, barLeft + MENTION_BAR_WIDTH,
                                y - rowHeight - headroom / scale, y,
                                mentionSpan(lines, lineIndex, dividerIndex,
                                        rows, stackBase, topmostIndex,
                                        topHeadroom),
                                alpha, mentionRgb);
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
                        drawDividerRow(font, wordsLeft,
                                panelRight, dividerBottom - LINE_HEIGHT,
                                dividerLabel, UNREAD_DIVIDER_RGB, alpha);
                    }
                    // A message with no name row — the rest of a
                    // speaker's group, a line that is words from its
                    // first row — shows its time in the timestamp area
                    // while the pointer rests on it, the way Discord
                    // shows a grouped line's time on hover; a message
                    // that names its speaker wears its time behind the
                    // name. The time rides the stack's vertical motion
                    // and the line's fade, but not the entry slide — the
                    // area does not move sideways — and it is drawn after
                    // the line's band, so on a highlighted line it stands
                    // on the tint instead of being darkened under it. It
                    // is centred on the message's words, every wrapped
                    // row of them.
                    if (open && columns.shows() && hoverFade > 0.0F
                            && showsTimeInColumn(lines, lineIndex)) {
                        Long said = ClientChatChannelViews.timeOf(
                                line.getChatLineID());
                        if (said != null) {
                            boolean areaCut = LostTalesUiClip.begin(minecraft,
                                    areaClipLeft, Double.NaN, clipTop,
                                    clipBottom, true);
                            try {
                                drawColumnTime(font, columns, areaLeft,
                                        ChatTimestampFormatter.formatDrawnTime(
                                                said.longValue()),
                                        stampTextTop(y, rowHeight,
                                                messageCentreShift(lines,
                                                        lineIndex, rows,
                                                        dividerIndex),
                                                sizes.small),
                                        Math.round(alpha * hoverFade));
                            } finally {
                                LostTalesUiClip.end(areaCut);
                            }
                        }
                    }
                    GL11.glPushMatrix();
                    GL11.glTranslatef(entry, 0.0F, 0.0F);
                    GL11.glEnable(GL11.GL_BLEND);
                    IChatComponent component = line.func_151461_a();
                    float rowShift = feedRowShift(font, component, alignment,
                            ChatTimestampColumn.feedRowRoom(
                                    windowRight - panelLeft),
                            rowPivot, rowScale);
                    if (open && ChatReplyMarker.isQuoteRow(component)) {
                        drawQuoteSpine(y, rowScale, alpha);
                    }
                    // A reaction row's chips are centred in the row, which
                    // is as tall as they need; any other row's text stands
                    // on the row's own line.
                    boolean reactionRow =
                            ChatReactionMarker.isReactionRow(component);
                    GL11.glPushMatrix();
                    GL11.glTranslatef(rowShift, reactionRow
                            ? reactionTextTop(y, rowHeight, rowScale)
                            : y - (float)TEXT_OFFSET, 0.0F);
                    if (rowPivot >= 0.0F) {
                        // The row keeps where its first run starts and
                        // grows or shrinks from there, each of its pixels
                        // a whole one, and it stands on the line the
                        // words stand on: a speaker's row takes the room
                        // it gains above them, a quote gives its own
                        // back there, and each is as tall as its text.
                        GL11.glTranslatef(rowPivot * (1.0F - rowScale),
                                reactionRow ? 0.0F
                                        : LostTalesChatVisualStyle
                                                .stackRowTopOffset(rowScale),
                                0.0F);
                        GL11.glScalef(rowScale, rowScale, 1.0F);
                    }
                    ChatHeadMarker.Data marker = findMarker(component);
                    // A message under the pointer moves its words and
                    // their chevron; its name row, its quote and its
                    // reactions stay where they are.
                    ChatRowMotion rowMotion = open && !reactionRow
                            && ChatLayoutMarker.isBodyRow(component)
                            ? ChatLineHover.rowMotion(line.getChatLineID(),
                                    System.nanoTime())
                            : null;
                    // The row's backdrops go down over its band before its
                    // words, riding the words as they slide in and fading
                    // with them.
                    ChatRunBackdrops.draw(font, component, line.getChatLineID(),
                            surfaceRgb, alpha, open, rowMotion);
                    LostTalesChatVisualStyle.drawFormatted(font,
                            component, marker, 0, 0, alpha, open, rowMotion,
                            line.getChatLineID());
                    drawHead(minecraft, font, component,
                            HEAD_TOP_OFFSET, alpha, open);
                    if (open && ChatLayoutMarker.isHeaderRow(component)) {
                        drawHeaderStamp(font, component, rowScale,
                                sizes.small, alpha);
                    }
                    if (open && reactionRow
                            && LostTalesChatPresentation.isReactable(
                                    line.getChatLineID())) {
                        drawReactionAddButton(font, component, alpha);
                    }
                    if (open && line.getChatLineID() != markedLineId
                            && !ChatWindowLines.isSpacer(line)
                            && !ChatReactionMarker.isReactionRow(component)
                            && !ChatLayoutMarker.isHeaderRow(component)
                            && !ChatReplyMarker.isQuoteRow(component)) {
                        // A line of the player's own the Discord bridge has
                        // not posted yet, or could not post, says so after
                        // the message's lowest row of words.
                        markedLineId = line.getChatLineID();
                        // The mark rides the end of the words it follows.
                        float wordsShift = rowMotion == null ? 0.0F
                                : rowMotion.endX();
                        GL11.glPushMatrix();
                        GL11.glTranslatef(wordsShift, 0.0F, 0.0F);
                        drawDeliveryMark(frame, font, component,
                                markedLineId, alpha,
                                panelRight - entry - rowShift - wordsShift,
                                originX + (entry + rowShift + wordsShift)
                                        * scale,
                                originY + stackOffset
                                        + (y - lift - TEXT_OFFSET) * scale,
                                scale, clipTop, clipBottom);
                        GL11.glPopMatrix();
                    }
                    GL11.glPopMatrix();
                    GL11.glPopMatrix();
                    GL11.glDisable(GL11.GL_ALPHA_TEST);
                }
                if (appliedLift != 0.0F) {
                    GL11.glTranslatef(0.0F, appliedLift, 0.0F);
                }
                if (open && columns.shows()) {
                    // The avatars, over every row they stand beside, so
                    // no row's band is laid over one, and under the
                    // shades the window ends on; cut at the window's
                    // edge, past which they slide as the area goes.
                    boolean areaCut = LostTalesUiClip.begin(minecraft, areaClipLeft,
                            Double.NaN, clipTop, clipBottom, true);
                    try {
                        drawAvatars(minecraft, frame, lines, firstLine,
                                lastRow, dividerIndex, rows, glide, stackBase,
                                opacity, opening, columns, areaLeft,
                                originX, originY + stackOffset, scale,
                                clipTop, clipBottom, areaClipLeft);
                    } finally {
                        LostTalesUiClip.end(areaCut);
                    }
                }
            } finally {
                LostTalesUiClip.end(clipped);
                clipped = false;
                GL11.glPopMatrix();
            }
            if (!full) {
                // The closed feed's rows the draw passed over: its stack
                // ends lower than the room allows, and the feed ends on
                // it. A history short enough to leave room is drawn
                // whole, so the divider's row is among them.
                frame.setStackTop(LostTalesDisplayPixels.snap(
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
                LostTalesUiInk.drawText(font,
                        "§o" + font.trimStringToWidth(
                                StatCollector.translateToLocal(
                                        "gui.losttales.chat.empty"),
                                Math.max(20, Math.round(panelRight) - 4)),
                        0, -TEXT_OFFSET,
                        LostTalesChatVisualStyle.asideRgb(),
                        inviteAlpha);
            }
            if (open) {
                if (columns.shows() && columns.separatorX() >= 0.0F) {
                    // The separator stands over the lines, so a message
                    // sliding past never crosses it — but under the
                    // shades below, with the rest of the history: the
                    // rules are what the window ends on, and everything
                    // the history draws goes behind them.
                    LostTalesUiRules.drawVerticalRule(areaLeft + columns.separatorX(),
                            areaLeft + columns.separatorX()
                                    + ChatTimestampColumn.SEPARATOR_WIDTH,
                            topEdge, bottomEdge,
                            Math.round(255.0F * opening.getOpacity()));
                }
                if (panelRight < windowRight) {
                    // The member list beside the history, on the window's
                    // own surfaces, its rows cut to the history's rules.
                    ChatMemberList.draw(minecraft, font, frame, panelRight,
                            topEdge, bottomEdge, windowRight, clipTop,
                            clipBottom, originX, originY, scale, columnAlpha,
                            Math.round(255.0F * opacity
                                    * opening.getOpacity()));
                } else {
                    frame.members.clearDrawn();
                }
                int fadeAlpha = Math.round(LostTalesUiRules.EDGE_FADE_ALPHA * opacity
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
                        WindowStyle.TOP_EDGE_FADE_HEIGHT, fadeAlpha);
                drawEdgeFade(panelLeft, panelRight, bottomEdge, topEdge,
                        WindowStyle.BOTTOM_EDGE_FADE_HEIGHT, fadeAlpha);
                if (panelRight < windowRight) {
                    // The member list's rows fade by the rules as the
                    // words do, evenly across its width.
                    LostTalesUiRules.drawEdgeFade(panelRight, windowRight, panelRight,
                            windowRight, topEdge, bottomEdge,
                            WindowStyle.TOP_EDGE_FADE_HEIGHT, fadeAlpha,
                            WindowStyle.backdropRgb(), false);
                    LostTalesUiRules.drawEdgeFade(panelRight, windowRight, panelRight,
                            windowRight, bottomEdge, topEdge,
                            WindowStyle.BOTTOM_EDGE_FADE_HEIGHT, fadeAlpha,
                            WindowStyle.backdropRgb(), false);
                }
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
            frame.toolbarWhy = new String[0];
            frame.toolbarChatLineId = 0;
            frame.jumpPillLeft = 0.0F;
            frame.jumpPillTop = 0.0F;
            frame.jumpPillRight = 0.0F;
            frame.jumpPillBottom = 0.0F;
            if (toolbar != null || jumpShown) {
                int controlAlpha = Math.round(255.0F * opacity
                        * opening.getOpacity());
                boolean controlsClipped = LostTalesUiClip.beginRows(minecraft,
                        clipTop, clipBottom, true);
                try {
                    if (toolbar != null) {
                        GL11.glPushMatrix();
                        try {
                            GL11.glTranslatef(0.0F, offset, 0.0F);
                            drawMessageToolbar(frame, toolbar.chatLineId,
                                    toolbarLeft, toolbar.top, toolbarScale,
                                    Math.round(controlAlpha * toolbarShare),
                                    originX, originY + stackOffset, scale);
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
                    LostTalesUiClip.end(controlsClipped);
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
     * topmost row ending at {@code rowBottom}, drawn at the chat's small
     * size: centred on the row's capitals, the odd display pixel up, and
     * on whole display pixels, measured with the words drawn at
     * {@code wordsPixels} and the toolbar at {@code smallPixels} display
     * pixels to one of their own, {@code unitPixels} to one of the
     * stack's.
     */
    static float toolbarTop(int rowBottom, int rowHeight, int wordsPixels,
                            int smallPixels, float unitPixels) {
        int spare = LostTalesUiInk.CAP_HEIGHT * wordsPixels - TOOLBAR_HEIGHT * smallPixels;
        return rowBottom - rowHeight + WindowStyle.ROW_TEXT_TOP
                + Math.floorDiv(spare, 2) / Math.max(0.001F, unitPixels);
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
     * at another size than the words grows or shrinks from where its
     * first run starts, so its ink ends where that leaves it. Nothing for
     * a row at the left.
     */
    private static float feedRowShift(FontRenderer font, IChatComponent row,
                                      ChatFeedAlignment alignment,
                                      float areaWidth, float rowPivot,
                                      float rowScale) {
        if (alignment == ChatFeedAlignment.LEFT || row == null
                || font == null) {
            return 0.0F;
        }
        float inkLeft = LostTalesChatVisualStyle.contentStart(row, false);
        float inkRight = LostTalesChatVisualStyle.inkEnd(font, row, false);
        if (rowPivot >= 0.0F) {
            inkLeft = rowPivot + (inkLeft - rowPivot) * rowScale;
            inkRight = rowPivot + (inkRight - rowPivot) * rowScale;
        }
        return floorToStackPixel(alignment.rowShift(areaWidth, inkLeft,
                inkRight));
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
        LostTalesUiRules.drawEdgeFade(left, right, left, right, edge, limit, height, alpha,
                WindowStyle.backdropRgb(), true);
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
     * What the unread divider says: how many messages stand below it,
     * and the day its run began when that was not today. Messages from
     * an earlier day are dated; today's are simply counted. Empty
     * without a divider.
     */
    private static String unreadDividerLabel(ChatFrame frame,
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
     * caller's stack space; {@code wordsLeft} is where the words'
     * stretch begins, past the timestamp column.
     */
    private static void drawDividerRow(FontRenderer font, float wordsLeft,
                                       float panelRight, float top,
                                       String label, int rgb, int alpha) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        float left = wordsLeft + 3.0F;
        float right = panelRight - 3.0F;
        if (right <= left) {
            return;
        }
        // One small pixel, in the stack's units. The date's capitals are
        // centred on a message's, and the rule runs on their middle row.
        float pixel = Math.min(1.0F, LostTalesChatVisualStyle.stackSmallScale());
        float textTop = top + WindowStyle.ROW_TEXT_TOP + (pixel < 1.0F
                ? LostTalesChatVisualStyle.stackSmallTopOffset() : 0.0F);
        float ruleTop = textTop + (LostTalesUiInk.CAP_HEIGHT / 2) * pixel;
        int textWidth = label.length() == 0 ? 0
                : font.getStringWidth(label);
        float drawnWidth = textWidth * pixel;
        if (textWidth > 0 && drawnWidth < right - left - 24.0F) {
            // Everything is anchored on the date's x, laid on a display
            // pixel, so the gap is exactly three empty small columns on
            // either side whatever fraction the window's centre falls
            // on. The date is centred by its ink — the font's measured
            // width carries one trailing spacing column, which is not ink
            // — the odd display pixel left, and the right rule starts
            // three small pixels past the ink.
            float inkWidth = (textWidth - 1) * pixel;
            float textX = floorToStackPixel((left + right - inkWidth)
                    / 2.0F);
            float gapLeft = textX - 3.0F * pixel;
            float gapRight = textX + inkWidth + 3.0F * pixel;
            drawHorizontalFade(left, gapLeft, ruleTop, pixel,
                    rgb, 0, alpha);
            drawHorizontalFade(gapRight, right, ruleTop, pixel,
                    rgb, alpha, 0);
            // Each half's starting pixel — where the rule is strongest,
            // beside the date — carries a small cap: one pixel above
            // and one below it, so the rule opens toward the date the
            // way Discord's does.
            int cap = (alpha << 24) | rgb;
            LostTalesUiInk.fillRect(gapLeft - pixel, ruleTop - pixel, gapLeft, ruleTop,
                    cap);
            LostTalesUiInk.fillRect(gapLeft - pixel, ruleTop + pixel, gapLeft,
                    ruleTop + 2.0F * pixel, cap);
            LostTalesUiInk.fillRect(gapRight, ruleTop - pixel, gapRight + pixel, ruleTop,
                    cap);
            LostTalesUiInk.fillRect(gapRight, ruleTop + pixel, gapRight + pixel,
                    ruleTop + 2.0F * pixel, cap);
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(textX, textTop, 0.0F);
                GL11.glScalef(pixel, pixel, 1.0F);
                LostTalesUiInk.drawText(font, label, 0, 0, rgb,
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
        float pixelsPerUnit = LostTalesDisplayPixels.scaleFactor()
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
                < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
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
    private static void drawScrollbar(ChatFrame frame,
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
        frame.scrollbarProgress = (float)Motions.follow(
                MotionIds.CHAT_SCROLLBAR_FADE, wanted,
                frame.scrollbarWanted ? 1.0D : 0.0D, elapsed);
        int alpha = Math.round(255.0F * opacity * frame.scrollbarProgress);
        if (contentHeight <= room + 0.01F || contentHeight <= 0.0F
                || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
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
        LostTalesUiInk.fillRect(left, topEdge, right, bottomEdge,
                (Math.round(alpha * 0.35F) << 24)
                        | LostTalesUiInk.SURFACE_RGB);
        LostTalesUiInk.fillRect(left, thumbTop, right, thumbBottom,
                (alpha << 24)
                        | LostTalesUiInk.SURFACE_HIGHLIGHT_RGB);
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
    private static final int JUMP_BUTTON_HEIGHT = LostTalesUiFramedButton.HEIGHT;
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
        return LostTalesUiFramedButton.WIDE_INSET + LostTalesUiSheet.CHEVRON_1.getWidth()
                + JUMP_ICON_GAP + label + LostTalesUiFramedButton.WIDE_INSET;
    }

    /**
     * Advances the jump-to-present button's fly-in: out while the view is
     * scrolled away from the newest line, home again once it is not.
     * Answers whether any of the button shows.
     */
    private static boolean advanceJumpButton(ChatFrame frame,
                                             double scrollLines) {
        boolean wanted = frame.view != null && scrollLines > 0.5D;
        float progress = frame.jumpMotion.advance(System.nanoTime(), wanted);
        return progress > 0.02F;
    }

    /**
     * The jump-to-present button's top edge in the window's units: at the
     * panel's foot a pixel above the bottom rule, lowered past it by as
     * much of the fly-in as is still to come, and laid on a whole display
     * pixel so the frame's pixel art keeps its texels whole as it flies.
     */
    private static float jumpButtonTop(ChatFrame frame,
                                       float bottomEdge, float originY,
                                       float scale) {
        float slide = (1.0F - frame.jumpMotion.clamped())
                * (JUMP_BUTTON_HEIGHT + 3.0F);
        float top = bottomEdge - 1.0F - JUMP_BUTTON_HEIGHT + slide;
        return (float)((LostTalesDisplayPixels.snap(
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
                                       ChatFrame frame, float left,
                                       float top, int width, float bottomEdge,
                                       int alpha, float originX,
                                       float originY, float scale) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        frame.jumpButtonMotion.advance(System.nanoTime(), frame.jumpHovered);
        float jumpLit = frame.jumpButtonMotion.lit();
        LostTalesUiFramedButton.drawSurface(left, top, width, JUMP_BUTTON_HEIGHT,
                jumpLit, Math.round(alpha
                        * WindowStyle.INSET_ALPHA / 255.0F));
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
            LostTalesUiInk.drawText(minecraft.fontRenderer,
                    jumpButtonLabel(), LostTalesUiFramedButton.WIDE_INSET
                            + LostTalesUiSheet.CHEVRON_1.getWidth()
                            + JUMP_ICON_GAP,
                    (JUMP_BUTTON_HEIGHT - LostTalesUiInk.CAP_HEIGHT) / 2,
                    LostTalesUiInk.IVORY, alpha);
            // What is waiting below, so a view scrolled back says how
            // much it has not seen rather than only that there is more.
            // In the divider's own crimson, which is the colour this
            // chat says "unread" in, on the button's right shoulder.
            drawWaitingCount(minecraft, frame, width, 0.0F, alpha);
        } finally {
            GL11.glPopMatrix();
        }
        // The chevron first, the wide inset in from the frame's edge, and
        // posed by the button's beat while the frame keeps its place.
        LostTalesUiButton.drawGlyph(LostTalesUiSheet.CHEVRON_1,
                LostTalesUiSheet.CHEVRON_1_HOVER, frame.jumpButtonMotion,
                left + LostTalesUiFramedButton.WIDE_INSET,
                top + (JUMP_BUTTON_HEIGHT
                        - LostTalesUiSheet.CHEVRON_1.getHeight()) / 2, alpha);
        LostTalesUiFramedButton.drawInk(left, top, width, JUMP_BUTTON_HEIGHT,
                jumpLit, alpha);
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
     * ({@link LostTalesUiItemIcon#squeezeDepthInto}); the history; the reaction chips'
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
        LostTalesUiItemIcon.releaseDepth();
        GL11.glDepthMask(true);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    /**
     * Finds where the item slab's two ends land in the depth buffer under
     * the window's matrices. A slab that does not come out between the
     * history and the window's box is not used, and the icons draw at
     * their own depth.
     */
    private static void placeItemDepth(float x, float y) {
        LostTalesUiItemIcon.releaseDepth();
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
            LostTalesUiItemIcon.squeezeDepthInto(near, far);
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
                                         ChatFrame frame,
                                         float buttonRight, float buttonTop,
                                         int alpha) {
        int waiting = ClientChatChannelViews.waitingBelow(frame.view);
        if (waiting <= 0 || minecraft.fontRenderer == null
                || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
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
        LostTalesUiInk.fillRect(badgeLeft, badgeTop, badgeRight, badgeBottom,
                (alpha << 24) | UNREAD_DIVIDER_RGB);
        LostTalesUiInk.drawText(minecraft.fontRenderer, text,
                Math.round(badgeLeft) + 2, Math.round(badgeTop) + 1,
                LostTalesUiInk.IVORY, alpha);
    }

    /**
     * One control of the toolbar, square, in the toolbar's own pixels: an
     * emoji's box with the framed buttons' clear pixels round it. The
     * controls stand side by side inside one frame.
     */
    static final int TOOLBAR_CELL = LostTalesUiInk.ICON_SIZE
            + 2 * LostTalesUiFramedButton.PADDING;
    /** The toolbar's height in its own pixels: a control and the frame's edge above and below it. */
    static final int TOOLBAR_HEIGHT = TOOLBAR_CELL + 2 * LostTalesUiFramedButton.EDGE;
    /** Clear pixels between the toolbar and the scrollbar's track. */
    static final int TOOLBAR_GAP = 2;
    /** Answer the message. */
    static final int TOOLBAR_REPLY = 1;
    /** Take a copy of the message. */
    public static final int TOOLBAR_COPY = 2;
    /** React to the message. */
    static final int TOOLBAR_REACT = 3;
    /** Take a link to the message. */
    static final int TOOLBAR_LINK = 4;
    /** Open the message's menu, the one a right click on it opens. */
    static final int TOOLBAR_MORE = 5;
    /** Carry the message on to another conversation. */
    static final int TOOLBAR_FORWARD = 6;
    /**
     * The toolbar's controls, left to right, on every message alike, the
     * menu's last as on a messenger's bar: one that cannot be taken on a
     * message stands muted in its place and says why under the pointer.
     */
    public static final int[] TOOLBAR_KINDS = {TOOLBAR_REACT, TOOLBAR_REPLY,
            TOOLBAR_FORWARD, TOOLBAR_COPY, TOOLBAR_LINK, TOOLBAR_MORE};
    /**
     * The link glyph's pixels: two rings of a chain holding each other,
     * standing in until the link's own artwork is drawn.
     */
    private static final String[] LINK_GLYPH = {
            ".##..##.",
            "#..##..#",
            "#..##..#",
            ".##..##."};

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
                                             ChatRowGlide glide,
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
                    < LostTalesUiInk.MIN_VISIBLE_ALPHA
                    || ChatWindowLines.dateDividerLabel(line) != null) {
                continue;
            }
            // The stack walks upward, so the last row found is the
            // message's topmost.
            int rowBottom = -(rows.top(rowIndex) - Math.round(stackBase));
            int factor = LostTalesDisplayPixels.scaleFactor();
            float chat = LostTalesChatVisualStyle.chatScale();
            place = new ToolbarPlace(toolbarTop(rowBottom,
                    rows.height(rowIndex),
                    LostTalesChatVisualStyle.rowPixels(chat, factor, 0),
                    LostTalesChatVisualStyle.rowPixels(chat, factor, -1),
                    factor * chat) - glide.lift(lineIndex),
                    line.getChatLineID());
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
                                       ChatStackRows rows,
                                       ChatRowGlide glide, float stackBase,
                                       float opacity,
                                       LostTalesGuiAnimationSample opening,
                                       ChatFeedAlignment alignment,
                                       float areaWidth, float offset) {
        RowSizes sizes = RowSizes.of(true);
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
                            < LostTalesUiInk.MIN_VISIBLE_ALPHA
                    || ChatWindowLines.dateDividerLabel(line) != null) {
                continue;
            }
            int[] chips = LostTalesChatVisualStyle.chipBoxes(font, row, true);
            if (chips.length == 0) {
                continue;
            }
            float rowScale = sizes.scaleOf(row);
            float pivot = rowPivot(row, true, rowScale);
            int rowBottom = -(rows.top(rowIndex) - Math.round(stackBase));
            float chipTop = reactionTextTop(rowBottom, rows.height(rowIndex),
                    rowScale) - ChatReactionMarker.TEXT_DROP * rowScale
                    + offset - glide.lift(lineIndex);
            float rowLeft = alignment.slide(entrySlide(line))
                    + feedRowShift(font, row, alignment, areaWidth, pivot,
                            rowScale)
                    + (pivot >= 0.0F ? pivot * (1.0F - rowScale) : 0.0F);
            for (int index = 0; index + 1 < chips.length; index += 2) {
                holes.add(rowLeft + chips[index] * rowScale, chipTop,
                        chips[index + 1] * rowScale,
                        ChatReactionMarker.HEIGHT * rowScale,
                        CHIP_HOLE_DEPTH, rowScale);
            }
            // The button the row ends on stands beside the chips, and its
            // hole with it.
            int[] add = LostTalesChatPresentation.isReactable(
                    line.getChatLineID())
                    ? LostTalesChatVisualStyle.addButtonBox(font, row, true)
                    : null;
            if (add != null) {
                holes.add(rowLeft + add[0] * rowScale, chipTop,
                        add[1] * rowScale,
                        ChatReactionMarker.HEIGHT * rowScale,
                        CHIP_HOLE_DEPTH, rowScale);
            }
        }
    }

    /**
     * Why each of the toolbar's controls cannot be taken on the message
     * drawn on {@code chatLineId}, in {@link #TOOLBAR_KINDS}' order;
     * empty for one that can. Copy and the menu always can.
     */
    private static String[] toolbarReasons(int chatLineId) {
        String[] why = new String[TOOLBAR_KINDS.length];
        for (int index = 0; index < why.length; index++) {
            int kind = TOOLBAR_KINDS[index];
            why[index] = kind == TOOLBAR_REACT
                    ? LostTalesChatPresentation.whyNotReactable(chatLineId)
                    : kind == TOOLBAR_REPLY
                            ? LostTalesChatPresentation.whyNotRepliable(chatLineId)
                            : kind == TOOLBAR_FORWARD
                            ? LostTalesChatPresentation.whyNotForwardable(chatLineId)
                            : kind == TOOLBAR_LINK
                                    ? ChatMenus.whyNotLinkable(chatLineId)
                                    : "";
        }
        return why;
    }

    /** The toolbar's width in its own pixels: its controls and the frame's edge either side. */
    static int toolbarWidth() {
        return TOOLBAR_KINDS.length * TOOLBAR_CELL
                + 2 * LostTalesUiFramedButton.EDGE;
    }

    /**
     * The hovered message's own controls, at the top right of it: react
     * to it, reply to it, copy it and copy a link to it — what the
     * message's menu offers, where the pointer already is — and open that
     * menu for the rest, side by side
     * inside one frame standing in the hole the history left for it, as
     * Discord's message bar does. Each control's own stretch of the bar's
     * surface lights under the pointer, beside the others rather than
     * over the bar, and its glyph moves inside it; the frame lights with
     * whichever control is lit. A control that cannot be taken on this
     * message stands muted and never lights. The whole bar is drawn at
     * the chat's small size, {@code small} of the stack's units to one
     * of its own.
     *
     * <p>Drawn in the stack's space, so it rides the scroll with the
     * message it belongs to: {@code top} is the row's place before the
     * slide, the caller translates by the slide and {@code originY}
     * includes it. While the pointer is on the bar, the message it
     * belongs to stays the hovered one. The screen rectangle is recorded
     * on the frame, so the click resolves against exactly what was
     * drawn.</p>
     */
    private static void drawMessageToolbar(ChatFrame frame,
                                           int chatLineId, float left,
                                           float top, float small,
                                           int alpha, float originX,
                                           float originY, float scale) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        long now = System.nanoTime();
        frame.startToolbarFades(chatLineId);
        String[] why = toolbarReasons(chatLineId);
        int width = toolbarWidth();
        int edge = LostTalesUiFramedButton.EDGE;
        int surfaceAlpha = Math.round(alpha
                * WindowStyle.INSET_ALPHA / 255.0F);
        float[] lit = new float[TOOLBAR_KINDS.length];
        LostTalesUiButtonMotion[] motions =
                new LostTalesUiButtonMotion[TOOLBAR_KINDS.length];
        float frameLit = 0.0F;
        for (int index = 0; index < TOOLBAR_KINDS.length; index++) {
            motions[index] = frame.toolbarMotion(TOOLBAR_KINDS[index], now);
            lit[index] = why[index].length() > 0 ? 0.0F : motions[index].lit();
            frameLit = Math.max(frameLit, lit[index]);
        }
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(left, top, 0.0F);
            GL11.glScalef(small, small, 1.0F);
            // The frame's edge in the frame's own light, then each
            // control's square in its own, side by side.
            int edgeArgb = LostTalesUiInk.argb(
                    LostTalesUiFramedButton.surfaceRgb(frameLit), surfaceAlpha);
            LostTalesUiInk.fillRect(1.0F, 0.0F, width - 1.0F, edge, edgeArgb);
            LostTalesUiInk.fillRect(0.0F, edge, edge, TOOLBAR_HEIGHT - edge, edgeArgb);
            LostTalesUiInk.fillRect(width - edge, edge, width, TOOLBAR_HEIGHT - edge,
                    edgeArgb);
            LostTalesUiInk.fillRect(1.0F, TOOLBAR_HEIGHT - edge, width - 1.0F,
                    TOOLBAR_HEIGHT, edgeArgb);
            for (int index = 0; index < TOOLBAR_KINDS.length; index++) {
                float cellLeft = edge + index * TOOLBAR_CELL;
                LostTalesUiInk.fillRect(cellLeft, edge, cellLeft + TOOLBAR_CELL,
                        edge + TOOLBAR_CELL, LostTalesUiInk.argb(
                                LostTalesUiFramedButton.surfaceRgb(lit[index]),
                                surfaceAlpha));
            }
            for (int index = 0; index < TOOLBAR_KINDS.length; index++) {
                float cellLeft = edge + index * TOOLBAR_CELL;
                boolean open = why[index].length() == 0;
                if (!open) {
                    // A control that cannot be taken stands still, muted.
                    drawToolbarGlyph(TOOLBAR_KINDS[index], cellLeft, edge,
                            0.0F, false, Math.round(alpha
                                    * WindowStyle.UNAVAILABLE_OPACITY));
                    continue;
                }
                LostTalesUiButton.beginPose(motions[index], cellLeft, edge,
                        TOOLBAR_CELL, TOOLBAR_CELL);
                try {
                    drawToolbarGlyph(TOOLBAR_KINDS[index], cellLeft, edge,
                            lit[index], true, alpha);
                } finally {
                    LostTalesUiButton.endPose();
                }
            }
            LostTalesUiFramedButton.drawInk(0.0F, 0.0F, width,
                    TOOLBAR_HEIGHT, frameLit, alpha);
        } finally {
            GL11.glPopMatrix();
        }
        float unit = small * scale;
        frame.toolbarLeft = originX + left * scale;
        frame.toolbarTop = originY + top * scale;
        frame.toolbarRight = frame.toolbarLeft + width * unit;
        frame.toolbarBottom = frame.toolbarTop + TOOLBAR_HEIGHT * unit;
        frame.toolbarCellsLeft = frame.toolbarLeft + edge * unit;
        frame.toolbarCellWidth = TOOLBAR_CELL * unit;
        frame.toolbarKinds = TOOLBAR_KINDS;
        frame.toolbarWhy = why;
        frame.toolbarChatLineId = chatLineId;
    }

    /**
     * One control's glyph centred in its square at {@code (left, top)},
     * crossing to its lit artwork as far as {@code lit}: the input bar's
     * own emoji button for React, the sheet's reply arrow, copy page and
     * menu dots, and the link glyph in ivory; muted where the control
     * cannot be taken.
     */
    private static void drawToolbarGlyph(int kind, float left, float top,
                                         float lit, boolean open, int alpha) {
        if (kind == TOOLBAR_LINK) {
            drawPixelGlyph(LINK_GLYPH,
                    left + LostTalesUiInk.centredStart(TOOLBAR_CELL,
                            LINK_GLYPH[0].length()),
                    top + LostTalesUiInk.centredStart(TOOLBAR_CELL,
                            LINK_GLYPH.length),
                    open ? LostTalesUiInk.IVORY
                            : LostTalesChatVisualStyle.asideRgb(),
                    alpha);
            return;
        }
        LostTalesUiSheet resting;
        LostTalesUiSheet hovered;
        switch (kind) {
            case TOOLBAR_REACT:
                resting = LostTalesUiSheet.EMOJI;
                hovered = LostTalesUiSheet.EMOJI_HOVER;
                break;
            case TOOLBAR_REPLY:
                resting = LostTalesUiSheet.REPLY;
                hovered = LostTalesUiSheet.REPLY_HOVER;
                break;
            case TOOLBAR_FORWARD:
                resting = LostTalesUiSheet.FORWARD;
                hovered = LostTalesUiSheet.FORWARD_HOVER;
                break;
            case TOOLBAR_COPY:
                resting = LostTalesUiSheet.COPY;
                hovered = LostTalesUiSheet.COPY_HOVER;
                break;
            default:
                resting = LostTalesUiSheet.MORE;
                hovered = LostTalesUiSheet.MORE_HOVER;
                break;
        }
        LostTalesUiSheet.drawPairWithShadow(resting, hovered, lit,
                left + LostTalesUiInk.centredStart(TOOLBAR_CELL,
                        resting.getWidth()),
                top + LostTalesUiInk.centredStart(TOOLBAR_CELL,
                        resting.getHeight()),
                alpha);
    }

    /**
     * A glyph drawn from its own pixels — {@link #LINK_GLYPH} — its ink
     * from {@code (x, y)} in {@code rgb}, over the chat's one shadow: the
     * same pixels a pixel down and right in the shadow tone, at the
     * shadow's share of {@code alpha}.
     */
    private static void drawPixelGlyph(String[] rows, float x, float y,
                                       int rgb, int alpha) {
        int shadow = LostTalesUiInk.shadowAlpha(alpha);
        if (shadow > 0) {
            drawGlyphPixels(rows, x + LostTalesUiInk.SHADOW_OFFSET,
                    y + LostTalesUiInk.SHADOW_OFFSET,
                    LostTalesUiInk.argb(
                            LostTalesUiInk.SHADOW, shadow));
        }
        drawGlyphPixels(rows, x, y, LostTalesUiInk.argb(rgb, alpha));
    }

    /**
     * A glyph's pixels in one colour, each run of a row as one quad, so
     * no pixel is laid down twice and a translucent pass — the shadow, a
     * fading toolbar — stays one even tone.
     */
    private static void drawGlyphPixels(String[] rows, float x, float y,
                                        int argb) {
        for (int row = 0; row < rows.length; row++) {
            String pixels = rows[row];
            int column = 0;
            while (column < pixels.length()) {
                if (pixels.charAt(column) != '#') {
                    column++;
                    continue;
                }
                int start = column;
                while (column < pixels.length()
                        && pixels.charAt(column) == '#') {
                    column++;
                }
                LostTalesUiInk.fillRect(x + start, y + row, x + column, y + row + 1.0F,
                        argb);
            }
        }
    }

    /**
     * The history's own backdrop for one row: the same plum black at the
     * same opacity, fading out to the right the same way. The empty
     * screen's strip stands on it, so its hairlines read as the chat's
     * edges instead of as lines across the world.
     */
    static void drawBackdropRow(float left, float top, float right,
                                float bottom, int alpha) {
        drawChatBackdrop(left, top, right, bottom, alpha,
                WindowStyle.backdropRgb());
    }

    /**
     * The opacity the chat's backdrop is drawn at, before any opening
     * fade is applied to it: the empty screen's strip asks here, so it
     * stands on the same backdrop a window's messages lie on.
     */
    static int backdropRowAlpha(Minecraft minecraft) {
        float opacity = WindowStyle.opacity(minecraft);
        return Math.max(0, Math.min(255, Math.round(255.0F * opacity))) / 2;
    }

    /**
     * Where the rows of the message on line {@code lineIndex} start and
     * end, measured as a row's {@code y} is: {@code {top, bottom}}, the
     * top of its oldest row and the bottom of its newest. The rows of one
     * message share its line id and stand together in the list, the
     * oldest toward the end. The topmost line's band reaches up by
     * {@code topHeadroom}, and so does the span of its message.
     */
    static float[] mentionSpan(List<ChatLine> lines, int lineIndex,
                               int dividerIndex, ChatStackRows rows,
                               float stackBase, int topmostIndex,
                               float topHeadroom) {
        int id = lines.get(lineIndex).getChatLineID();
        int newest = lineIndex;
        while (newest > 0 && sameMessage(lines.get(newest - 1), id)) {
            newest--;
        }
        int oldest = lineIndex;
        while (oldest + 1 < lines.size()
                && sameMessage(lines.get(oldest + 1), id)) {
            oldest++;
        }
        float base = Math.round(stackBase);
        float bottom = -(rows.top(rowOfLine(newest, dividerIndex)) - base);
        float top = -(rows.top(rowOfLine(oldest, dividerIndex) + 1) - base);
        if (oldest == topmostIndex) {
            top -= topHeadroom;
        }
        return new float[] {top, bottom};
    }

    /**
     * One row's stretch of a mention's bar, from {@code top} to
     * {@code bottom}. The bar fades over its whole message
     * ({@code span}, as {@link #mentionSpan} gives it): {@code alpha} at
     * the message's middle and nothing at its two ends, so the rows drawn
     * one by one make one gradient.
     */
    static void drawMentionBar(float left, float right, float top,
                               float bottom, float[] span, int alpha,
                               int rgb) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA
                || right <= left || bottom <= top) {
            return;
        }
        float centre = (span[0] + span[1]) / 2.0F;
        float reach = Math.max(1.0F, (span[1] - span[0]) / 2.0F);
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(true);
        // Split at the middle, so the strongest point is a vertex.
        if (top < centre && centre < bottom) {
            mentionBarQuad(tessellator, left, right, top, centre, centre,
                    reach, alpha, rgb);
            mentionBarQuad(tessellator, left, right, centre, bottom, centre,
                    reach, alpha, rgb);
        } else {
            mentionBarQuad(tessellator, left, right, top, bottom, centre,
                    reach, alpha, rgb);
        }
        LostTalesSkyrimUiStyle.endQuads(tessellator, true);
    }

    private static void mentionBarQuad(Tessellator tessellator, float left,
                                       float right, float top, float bottom,
                                       float centre, float reach, int alpha,
                                       int rgb) {
        int topAlpha = mentionBarAlpha(top, centre, reach, alpha);
        int bottomAlpha = mentionBarAlpha(bottom, centre, reach, alpha);
        // Same winding as the backdrop: the GUI pass culls back faces.
        tessellator.setColorRGBA_I(rgb, bottomAlpha);
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.setColorRGBA_I(rgb, topAlpha);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
    }

    /** The bar's alpha at {@code y}: full at the middle, none a reach away. */
    static int mentionBarAlpha(float y, float centre, float reach,
                               int alpha) {
        float share = 1.0F - Math.abs(y - centre) / reach;
        return Math.round(alpha * Math.max(0.0F, Math.min(1.0F, share)));
    }

    /**
     * Whether the row shows its message's time in the timestamp area
     * while the pointer rests on the message: the first row of a message
     * with no name row — the rest of a speaker's group, or a line that
     * is words from its first row down. A message that names its speaker
     * wears its time behind the name instead, and its avatar stands in
     * the area. The rows of one message share its chat line id and stand
     * together in the list, the first of them toward the older end.
     */
    static boolean showsTimeInColumn(List<ChatLine> lines, int lineIndex) {
        ChatLine line = lines.get(lineIndex);
        if (line == null || ChatWindowLines.isFiller(line)) {
            return false;
        }
        IChatComponent row = line.func_151461_a();
        if (row == null || ChatLayoutMarker.isHeaderRow(row)
                || ChatReplyMarker.isQuoteRow(row)
                || ChatReactionMarker.isReactionRow(row)) {
            return false;
        }
        return lineIndex + 1 >= lines.size()
                || !sameMessage(lines.get(lineIndex + 1),
                        line.getChatLineID());
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
    private static final int DELIVERY_CLOCK_SIZE = LostTalesUiInk.CAP_HEIGHT;

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
    private static void drawDeliveryMark(ChatFrame frame,
                                         FontRenderer font,
                                         IChatComponent row, int chatLineId,
                                         int alpha, float textSpaceRight,
                                         float screenX, float screenTop,
                                         float scale, float clipTop,
                                         float clipBottom) {
        ChatDeliveryMark.State state =
                ClientChatDeliveryMarks.stateOf(chatLineId);
        if (state == ChatDeliveryMark.State.NONE || font == null
                || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
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
            LostTalesUiInk.beginContent();
            LostTalesUiInk.drawText(font, "!", x, 0,
                    UNREAD_DIVIDER_RGB, alpha);
        } else {
            drawDeliveryClock(x, 0, alpha);
        }
        frame.recordMark(screenX + (x - 1) * scale,
                Math.max(clipTop, screenTop - scale),
                screenX + (x + width + 1) * scale,
                Math.min(clipBottom,
                        screenTop + (LostTalesUiInk.CAP_HEIGHT + 1) * scale),
                chatLineId);
    }

    /** The retrying mark: a clock at a quarter past, with the chat's shadow. */
    private static void drawDeliveryClock(int x, int y, int alpha) {
        int shadow = LostTalesUiInk.shadowAlpha(alpha);
        if (shadow > 0) {
            drawClockPixels(x + LostTalesUiInk.SHADOW_OFFSET,
                    y + LostTalesUiInk.SHADOW_OFFSET,
                    LostTalesUiInk.argb(
                            LostTalesUiInk.SHADOW, shadow));
        }
        drawClockPixels(x, y, LostTalesUiInk.argb(
                LostTalesChatVisualStyle.asideRgb(), alpha));
    }

    /** The clock's pixels, seven rows round, from {@code (x, y)}. */
    private static void drawClockPixels(float x, float y, int argb) {
        // The rim.
        LostTalesUiInk.fillRect(x + 2.0F, y, x + 5.0F, y + 1.0F, argb);
        LostTalesUiInk.fillRect(x + 1.0F, y + 1.0F, x + 2.0F, y + 2.0F, argb);
        LostTalesUiInk.fillRect(x + 5.0F, y + 1.0F, x + 6.0F, y + 2.0F, argb);
        LostTalesUiInk.fillRect(x, y + 2.0F, x + 1.0F, y + 5.0F, argb);
        LostTalesUiInk.fillRect(x + 6.0F, y + 2.0F, x + 7.0F, y + 5.0F, argb);
        LostTalesUiInk.fillRect(x + 1.0F, y + 5.0F, x + 2.0F, y + 6.0F, argb);
        LostTalesUiInk.fillRect(x + 5.0F, y + 5.0F, x + 6.0F, y + 6.0F, argb);
        LostTalesUiInk.fillRect(x + 2.0F, y + 6.0F, x + 5.0F, y + 7.0F, argb);
        // The hands, at a quarter past.
        LostTalesUiInk.fillRect(x + 3.0F, y + 2.0F, x + 4.0F, y + 4.0F, argb);
        LostTalesUiInk.fillRect(x + 4.0F, y + 3.0F, x + 5.0F, y + 4.0F, argb);
    }

    /**
     * The sizes one stack draws its rows at, against the words of a
     * message in the open window: the row naming a speaker, a reply's
     * quote, a message's own words and the chat's small text, each read
     * once for the whole stack rather than per row. The window and the
     * closed feed are asked separately, so the feed can show the voices
     * plain and shrink what they say while the window does the opposite.
     * A size the display cannot draw apart from the words is the words'.
     */
    static final class RowSizes {
        final float speaker;
        final float quote;
        final float message;
        /** The chat's small text: the chips and the times. */
        final float small;

        private RowSizes(float speaker, float quote, float message,
                         float small) {
            this.speaker = speaker;
            this.quote = quote;
            this.message = message;
            this.small = small;
        }

        static RowSizes of(boolean chatOpen) {
            return new RowSizes(
                    LostTalesChatVisualStyle.speakerRowScale(chatOpen),
                    LostTalesChatVisualStyle.quoteRowScale(chatOpen),
                    LostTalesChatVisualStyle.messageRowScale(chatOpen),
                    LostTalesChatVisualStyle.stackSmallScale());
        }

        /** The size this row is drawn at; the words' own for any other. */
        float scaleOf(IChatComponent row) {
            if (row == null) {
                return 1.0F;
            }
            if (ChatLayoutMarker.isHeaderRow(row)) {
                return this.speaker;
            }
            if (ChatReactionMarker.isReactionRow(row)) {
                return this.small;
            }
            if (ChatReplyMarker.isQuoteRow(row)) {
                return this.quote;
            }
            return ChatLayoutMarker.isBodyRow(row) ? this.message : 1.0F;
        }
    }

    /**
     * Where a row drawn at another size than the words grows or shrinks
     * from, or -1 for a row drawn at the words' own size, which stands
     * where it is laid out.
     *
     * <p>A row carrying a message's own words moves from its left edge,
     * indent and all, so a wrapped body still reads as one block beside
     * its chevron however big the words are. Every other row moves from
     * the x its first run starts at, keeping the place it was laid out
     * at under the message: a reply's quote and a message's chips stay
     * where the message is.</p>
     */
    private static float rowPivot(IChatComponent row, boolean open,
                                  float rowScale) {
        if (rowScale == 1.0F) {
            return -1.0F;
        }
        if (ChatLayoutMarker.isBodyRow(row)
                && !ChatReactionMarker.isReactionRow(row)) {
            return 0.0F;
        }
        return LostTalesChatVisualStyle.contentStart(row, open);
    }

    /**
     * Where a message's time in the timestamp area stands, measured from
     * the bottom edge of the row that carries it: its capitals centred on
     * the capitals of the message's words ({@link #messageCentreShift}),
     * on the display's grid, the odd display pixel up. The rows of one
     * message are not all one height, so the time is placed against the
     * words themselves rather than against the row it happens to be
     * written on; the words' capitals stand half a pixel above the middle
     * of their rows.
     */
    static float stampTextTop(int rowBottom, int rowHeight, int centreShift,
                              float smallScale) {
        float capitalsMiddle = rowBottom - rowHeight / 2.0F + centreShift
                - 0.5F;
        return floorToStackPixel(capitalsMiddle
                - LostTalesUiInk.CAP_HEIGHT * smallScale / 2.0F);
    }

    /**
     * A stack-space coordinate laid on the whole display pixel at or
     * before it: above it for a y, left of it for an x.
     */
    static float floorToStackPixel(float at) {
        return (float)LostTalesDisplayPixels.floor(at, stackPixelsPerUnit());
    }

    /** Display pixels per unit of the message stack: the GUI scale times the chat's. */
    private static float stackPixelsPerUnit() {
        return LostTalesDisplayPixels.scaleFactor()
                * LostTalesChatVisualStyle.chatScale();
    }

    /**
     * A message's time in the timestamp area: the chat's small text in
     * its aside tone, the time in italics — ivory and upright with chat
     * colours off, as every run is — with its shadow a pixel of its own
     * size away, the two centred in the area together on the display's
     * grid, the odd pixel on the separator's side.
     */
    private static void drawColumnTime(FontRenderer font,
                                       ChatTimestampColumn columns,
                                       float areaLeft, String time,
                                       float textTop, int alpha) {
        if (font == null
                || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        boolean colours = LostTalesChatVisualStyle.chatColoursEnabled();
        String rendered = colours ? time
                : LostTalesChatVisualStyle.stripCodes(time);
        float small = LostTalesChatVisualStyle.stackSmallScale();
        // Centred by its ink: the advance ends in the font's one-pixel
        // gap, which is not ink. Where the two cannot meet in the middle
        // the time stands the odd display pixel left, as its shadow
        // counts toward its shape.
        float ink = (font.getStringWidth(rendered) - 1) * small;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(floorToStackPixel(areaLeft
                    + columns.timeX(ink)), textTop, 0.0F);
            GL11.glScalef(small, small, 1.0F);
            LostTalesUiInk.drawText(font, rendered, 0, 0,
                    colours ? LostTalesChatVisualStyle.asideRgb()
                            : LostTalesUiInk.IVORY, alpha);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * The time a name's row wears behind the name
     * ({@link ChatStampMarker}), drawn where its run stands in the row's
     * own text space: the chat's small text in its aside tone, the time
     * in italics, standing where {@link #stampDrop} puts it, with its
     * shadow a pixel of its own size away. The row is drawn at
     * {@code rowScale} of the words and the stamp at {@code smallScale}
     * of them.
     */
    private static void drawHeaderStamp(FontRenderer font,
                                        IChatComponent row, float rowScale,
                                        float smallScale, int alpha) {
        if (font == null || row == null || rowScale <= 0.0F
                || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        int x = 0;
        for (Object value : row) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            if (ChatPrefixMarker.isHidden(part, true)) {
                continue;
            }
            String stamp = ChatStampMarker.textOf(part);
            if (stamp == null) {
                x += LostTalesChatVisualStyle.partWidth(font, part, true);
                continue;
            }
            boolean colours = LostTalesChatVisualStyle.chatColoursEnabled();
            String rendered = colours ? stamp
                    : LostTalesChatVisualStyle.stripCodes(stamp);
            // Counted in display pixels, so the stamp lands on the
            // display's grid inside the row.
            float perUnit = stackPixelsPerUnit();
            int rowPixels = Math.max(1, Math.round(rowScale * perUnit));
            int smallPixels = Math.max(1, Math.round(smallScale * perUnit));
            int drop = stampDrop(rowPixels, smallPixels);
            float relative = smallScale / rowScale;
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(x, drop / (float)rowPixels, 0.0F);
                GL11.glScalef(relative, relative, 1.0F);
                LostTalesUiInk.drawText(font, rendered, 0, 0,
                        colours ? LostTalesChatVisualStyle.asideRgb()
                                : LostTalesUiInk.IVORY, alpha);
            } finally {
                GL11.glPopMatrix();
            }
            return;
        }
    }

    /**
     * The button a hovered message's reaction row ends on, the way
     * Discord's does, drawn where its run stands in the row's own text
     * space and lit under the pointer; the row keeps its room while the
     * message is not hovered, so nothing moves as it comes and goes.
     */
    private static void drawReactionAddButton(FontRenderer font,
                                              IChatComponent row,
                                              int alpha) {
        int x = 0;
        int index = -1;
        for (Object value : row) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            index++;
            IChatComponent part = (IChatComponent)value;
            if (ChatPrefixMarker.isHidden(part, true)) {
                continue;
            }
            if (ChatReactionMarker.isAddButton(part)) {
                LostTalesChatVisualStyle.drawReactionAddButton(x, alpha,
                        LostTalesChatPresentation.addButtonHoverFade(
                                ChatReactionMarker.addButtonMessageId(part),
                                LostTalesChatPresentation.isHoveredRun(row,
                                        index)));
                return;
            }
            x += LostTalesChatVisualStyle.partWidth(font, part, true);
        }
    }

    /** Where the line a quote hangs from stands, right of the name's column. */
    static final int QUOTE_SPINE_X = 2;
    /** Clear pixels between the line's end and the quote's head. */
    static final int QUOTE_SPINE_GAP = 2;

    /**
     * The line a reply's quote hangs from in an open window, the way
     * Discord leads a reply into its message: from the quote's capitals,
     * where it bends with a pixel's rounding, down to the row under the
     * quote, above the name's first letter. The quote stands pushed right
     * of the name's column to make room for it
     * ({@link ChatReplyMarker#OPEN_INDENT}). One of the quote's own
     * pixels thick, as fine as its strokes, in the chat's aside tone, and
     * structural like a rule, so it casts no shadow. {@code rowBottom} is
     * the quote row's bottom edge in the stack's units and
     * {@code rowScale} the size the quote is drawn at.
     */
    private static void drawQuoteSpine(int rowBottom, float rowScale,
                                       int alpha) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        float pixel = rowScale;
        float textTop = rowBottom - (float)TEXT_OFFSET + (rowScale == 1.0F
                ? 0.0F : LostTalesChatVisualStyle.stackRowTopOffset(rowScale));
        float bend = floorToStackPixel(textTop
                + (LostTalesUiInk.CAP_HEIGHT / 2) * pixel);
        float left = snapToStackPixel(QUOTE_SPINE_X);
        float right = snapToStackPixel(ChatReplyMarker.OPEN_INDENT
                - QUOTE_SPINE_GAP);
        int argb = LostTalesUiInk.argb(
                LostTalesChatVisualStyle.asideRgb(), alpha);
        // The bend: the two strokes meet corner to corner, a pixel of
        // rounding where they turn.
        LostTalesUiInk.fillRect(left + pixel, bend, right, bend + pixel, argb);
        LostTalesUiInk.fillRect(left, bend + pixel, left + pixel, rowBottom, argb);
    }

    /**
     * How far below the name's top edge, in display pixels, the stamp
     * behind it starts, for a name drawn at {@code rowPixels} display
     * pixels per font pixel and the stamp at {@code smallPixels}: the
     * stamp's capitals centred on the name's lowercase letters, where a
     * name's weight is, the odd pixel up. Centred on the name's capitals
     * instead, a stamp reads as hung from the name's top.
     */
    static int stampDrop(int rowPixels, int smallPixels) {
        return Math.max(0, Math.floorDiv(
                (2 * GLYPH_X_TOP + GLYPH_X_HEIGHT) * rowPixels
                        - LostTalesUiInk.CAP_HEIGHT * smallPixels, 2));
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
        return LostTalesUiInk.blend(resting, layeredRgb(
                selectedRgb, pinged, selectedMentionRgb, share, flash,
                selectedFlashRgb), hover);
    }

    /** A line's layers over {@code baseRgb}: a mention's tint, then a jump's flash. */
    private static int layeredRgb(int baseRgb, boolean pinged,
                                  int mentionRgb, float share, float flash,
                                  int flashRgb) {
        int rgb = baseRgb;
        if (pinged) {
            rgb = LostTalesUiInk.blend(rgb, mentionRgb, share);
        }
        if (flash > 0.0F) {
            rgb = LostTalesUiInk.blend(rgb, flashRgb,
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
    private static boolean canRecolour() {
        return WindowStyle.canRecolour();
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
     * A stretch of a surface laid in one flat colour — the timestamp
     * area, the member list — given another colour in place, as a line's
     * stretch of the panel is ({@link #recolourBackdrop}).
     */
    static void recolourSurface(float left, float top, float right,
                                float bottom, int alpha, int fromRgb,
                                int toRgb) {
        WindowStyle.recolourFlat(left, top, right, bottom, alpha, fromRgb,
                toRgb);
    }

    /**
     * As above for a surface with the opacity profile {@code weights}:
     * the panel's thinning one, or {@link #FLAT_WEIGHTS} for one laid in
     * a single flat colour, as the timestamp column is.
     */
    private static void recolour(float curveLeft, float left, float top,
                                 float right, float bottom, int alpha,
                                 int fromRgb, int toRgb, float[] weights) {
        recolour(curveLeft, left, right, top, right, bottom, alpha, fromRgb,
                toRgb, weights);
    }

    /**
     * As above, stopping at {@code drawRight} while the surface's curve
     * runs on to {@code right}.
     */
    private static void recolour(float curveLeft, float left, float drawRight,
                                 float top, float right, float bottom,
                                 int alpha, int fromRgb, int toRgb,
                                 float[] weights) {
        if (!canRecolour()) {
            drawChatBackdrop(curveLeft, left, drawRight, top, right, bottom,
                    alpha, toRgb, GL11.GL_ONE_MINUS_SRC_ALPHA, weights);
            return;
        }
        try {
            GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
            drawChatBackdrop(curveLeft, left, drawRight, top, right, bottom,
                    alpha, fromRgb, GL11.GL_ONE, weights);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            drawChatBackdrop(curveLeft, left, drawRight, top, right, bottom,
                    alpha, toRgb, GL11.GL_ONE, weights);
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
                if (marker.avatar) {
                    // An open window draws the speaker's head in its
                    // timestamp area, not in the row.
                    return;
                }
                ChatEmoji mark = marker.mark();
                if (mark != null) {
                    // A line from the bridge or from the server has no
                    // account behind it; its mark stands where the head
                    // would, drawn 1:1 — its slot is declared two
                    // pixels wider than a head's, so it keeps the same
                    // clear pixels either side instead of eating into
                    // them. Centred in the line band exactly as an
                    // inline emoji is, wearing the sphere of a voice
                    // with a status in its own corner.
                    float markX = x + HEAD_LEFT_OFFSET;
                    float markY = y - HEAD_TOP_OFFSET
                            + WindowStyle.centredBoxTop(CONTENT_BOX_HEIGHT);
                    drawHeadMark(minecraft, mark, markX, markY,
                            ChatEmoji.SPRITE_SIZE, alpha, statusOf(marker),
                            markX, markY, ChatEmoji.SPRITE_SIZE);
                    return;
                }
                drawFace(minecraft, marker, x + HEAD_LEFT_OFFSET, y,
                        HEAD_SIZE, alpha);
                return;
            }
            // getFormattedText() recursively includes a component's siblings.
            // Measuring only this node keeps the marker aligned after the
            // structured message has been split into wrapped ChatLines.
            x += LostTalesChatVisualStyle.partWidth(font, part, chatOpen);
        }
    }

    /** The status a head wears, or null for a voice that is never online. */
    private static ChatPresence statusOf(ChatHeadMarker.Data head) {
        return ChatPresenceMark.wears(head) ? ChatPresenceMark.presenceOf(head)
                : null;
    }

    /**
     * The mark standing for a head — the Discord mark, the console mark,
     * the Narrator's — drawn {@code size} pixels square at {@code x},
     * {@code y}, with the chat's one shadow under it: one texel to one
     * pixel beside a name, as large as the avatar in the timestamp area.
     * A voice with a status wears its sphere as a face does, in the
     * corner of the head's own square ({@code headX}, {@code headY},
     * {@code headSize}), cut into the mark; {@code presence} is null for
     * one without.
     */
    private static void drawHeadMark(final Minecraft minecraft,
                                     final ChatEmoji mark, final float x,
                                     final float y, final float size,
                                     final int alpha,
                                     final ChatPresence presence,
                                     final float headX, final float headY,
                                     final float headSize) {
        final LostTalesUiCornerCut cut = presence == null
                ? LostTalesUiCornerCut.NONE
                : ChatPresenceMark.cutFor(headX, headY, headSize);
        LostTalesUiFlatLayers.draw(alpha, Math.min(x, headX),
                Math.min(y, headY),
                Math.max(x + size, headX + headSize
                        + LostTalesUiCornerMark.OVERHANG_X)
                        + LostTalesUiInk.SHADOW_OFFSET,
                Math.max(y + size, headY + headSize
                        + LostTalesUiCornerMark.OVERHANG_Y)
                        + LostTalesUiInk.SHADOW_OFFSET,
                new LostTalesUiFlatLayers.Layers() {
                    @Override
                    public void draw() {
                        ChatChannelIcons.drawEmojiLayers(minecraft, mark, x, y,
                                size, alpha, false, cut);
                        if (presence != null) {
                            LostTalesUiFlatLayers.nextLayer();
                            ChatPresenceMark.draw(headX, headY, headSize,
                                    presence, alpha);
                        }
                    }
                });
    }

    /**
     * A head drawn as an open window's avatar is, {@link ChatAvatar#SIZE}
     * square at {@code x}, {@code y} in a space of {@code pixelsPerUnit}
     * display pixels a unit: the face and its sphere, or the mark standing
     * for it filling the square on the display's grid, with its sphere
     * where its voice has a status. What an avatar and a member list's row
     * are both drawn with.
     */
    static void drawAvatarHead(Minecraft minecraft, ChatHeadMarker.Data head,
                               float x, float y, float pixelsPerUnit,
                               int alpha) {
        ChatEmoji mark = head.mark();
        if (mark == null) {
            drawFace(minecraft, head, x, y, ChatAvatar.SIZE, alpha);
            return;
        }
        float markSize = ChatAvatar.markSize(pixelsPerUnit);
        float inset = (ChatAvatar.SIZE - markSize) / 2.0F;
        drawHeadMark(minecraft, mark, x + inset, y + inset, markSize, alpha,
                statusOf(head), x, y, ChatAvatar.SIZE);
    }

    /**
     * A face {@code size} pixels square at {@code x}, {@code y}: its flat
     * shadow, the face, and — on a player's own head — the presence
     * sphere in the corner the face gives up for it; an NPC has no
     * account to have one. What the row's small head and an open window's
     * avatar are both drawn by. A head drawn translucent is one picture
     * ({@link LostTalesUiFlatLayers}): the hat does not show the face
     * through it, nor the face its shadow.
     */
    static void drawFace(final Minecraft minecraft,
                         final ChatHeadMarker.Data marker, final float x,
                         final float y, final float size, final int alpha) {
        float reach = size * 0.125F;
        LostTalesUiFlatLayers.draw(alpha, x - reach, y - reach,
                x + size + reach + LostTalesUiCornerMark.OVERHANG_X + 1,
                y + size + reach + LostTalesUiCornerMark.OVERHANG_Y + 1,
                new LostTalesUiFlatLayers.Layers() {
                    @Override
                    public void draw() {
                        drawFaceLayers(minecraft, marker, x, y, size, alpha);
                    }
                });
    }

    private static void drawFaceLayers(Minecraft minecraft,
                                       ChatHeadMarker.Data marker, float x,
                                       float y, float size, int alpha) {
        float opacity = alpha / 255.0F;
        boolean wearsPresence = ChatPresenceMark.wears(marker);
        if (wearsPresence) {
            ChatPresenceMark.beginShadowCut(x, y, size);
        }
        try {
            drawHeadShadow(minecraft, marker, x, y, size,
                    opacity * LostTalesUiInk.SHADOW_OPACITY);
        } finally {
            if (wearsPresence) {
                ChatPresenceMark.endHeadCut();
            }
        }
        LostTalesUiFlatLayers.nextLayer();
        if (wearsPresence) {
            ChatPresenceMark.beginHeadCut(x, y, size);
        }
        try {
            if (marker.npcIdentity) {
                LostTalesCharacterHeadIconRenderer.drawNpcHead(minecraft,
                        marker.skinId, x, y, size, 1.0F, opacity);
            } else if (marker.accountIdentity) {
                LostTalesCharacterHeadIconRenderer.drawAccountHead(minecraft,
                        marker.senderId, x, y, size, 1.0F, opacity);
            } else {
                LostTalesCharacterHeadIconRenderer.drawSnapshotHead(minecraft,
                        marker.senderId, marker.skinId, x, y, size, 1.0F,
                        opacity);
            }
        } finally {
            if (wearsPresence) {
                ChatPresenceMark.endHeadCut();
            }
        }
        if (wearsPresence) {
            LostTalesUiFlatLayers.nextLayer();
            ChatPresenceMark.draw(x, y, size,
                    ChatPresenceMark.presenceOf(marker), alpha);
        }
    }

    /**
     * Flat shadow of the head's base face, one pixel down-right like the
     * text shadow, on whole pixels. Silhouette mode gives every skin the
     * same shadow colour instead of a darkened copy of its own pixels.
     */
    private static void drawHeadShadow(
            Minecraft minecraft, ChatHeadMarker.Data marker,
            float x, float y, float size, float opacity) {
        if (opacity * 255.0F < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        float shadowX = x + LostTalesUiInk.SHADOW_OFFSET;
        float shadowY = y + LostTalesUiInk.SHADOW_OFFSET;
        LostTalesSilhouetteRenderState.begin(LostTalesUiInk.SHADOW);
        try {
            if (marker.npcIdentity) {
                LostTalesCharacterHeadIconRenderer.drawTintedNpcHeadBase(
                        minecraft, marker.skinId, shadowX, shadowY, size,
                        1.0F, 1.0F, 1.0F, opacity);
            } else if (marker.accountIdentity) {
                LostTalesCharacterHeadIconRenderer.drawTintedAccountHeadBase(
                        minecraft, marker.senderId, shadowX, shadowY, size,
                        1.0F, 1.0F, 1.0F, opacity);
            } else {
                LostTalesCharacterHeadIconRenderer.drawTintedSnapshotHeadBase(
                        minecraft, marker.senderId, marker.skinId,
                        shadowX, shadowY, size,
                        1.0F, 1.0F, 1.0F, opacity);
            }
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
    }

    /**
     * Every avatar of an open window's history: the speaker's head of
     * each message that names its speaker, in the timestamp area beside
     * the speaker's row and the first row of their words, centred across
     * the two ({@link ChatAvatar#top}). A speaker's row one past the
     * topmost row drawn is looked at too, since its avatar reaches down
     * into the rows shown; the clip cuts what the room does not hold.
     * Each avatar rides the stack and its message's fade, but not the
     * entry slide — the area does not move sideways — and the box it is
     * drawn in is recorded, since that is where it answers the pointer
     * as the speaker's name does.
     */
    private static void drawAvatars(Minecraft minecraft,
                                    ChatFrame frame,
                                    List<ChatLine> lines, int firstLine,
                                    int lastRow, int dividerIndex,
                                    ChatStackRows rows, ChatRowGlide glide,
                                    float stackBase, float opacity,
                                    LostTalesGuiAnimationSample opening,
                                    ChatTimestampColumn columns,
                                    float panelLeft, float originX,
                                    float originY, float scale,
                                    float clipTop, float clipBottom,
                                    double clipLeft) {
        int reach = Math.min(rows.count() - 1, lastRow + 1);
        float left = panelLeft + columns.avatarX();
        for (int lineIndex = firstLine; lineIndex < lines.size();
             lineIndex++) {
            int rowIndex = rowOfLine(lineIndex, dividerIndex);
            if (rowIndex > reach) {
                break;
            }
            ChatLine line = lines.get(lineIndex);
            ChatHeadMarker.Data avatar = line == null ? null
                    : ChatAvatar.of(line.func_151461_a());
            if (avatar == null || rowIndex <= 0) {
                continue;
            }
            int alpha = Math.round(lineAlpha(255, line, opacity, opening)
                    * glide.shown(lineIndex));
            if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
                continue;
            }
            int rowBottom = -(rows.top(rowIndex) - Math.round(stackBase));
            int rowHeight = rows.height(rowIndex);
            float top = ChatAvatar.top(rowBottom - rowHeight, rowHeight,
                    rows.height(rowIndex - 1)) - glide.lift(lineIndex);
            drawAvatarHead(minecraft, avatar, left, top, stackPixelsPerUnit(),
                    alpha);
            // Where it answers the pointer: the avatar itself, its sphere
            // included; the rest of the area lights the row it stands by.
            float boxLeft = (float)Math.max(clipLeft, originX + left * scale);
            float boxRight = originX + (left + ChatAvatar.ICON_WIDTH) * scale;
            if (boxRight <= boxLeft) {
                continue;
            }
            frame.recordAvatar(boxLeft,
                    Math.max(clipTop, originY + top * scale),
                    boxRight,
                    Math.min(clipBottom, originY + (top + ChatAvatar.SIZE
                            + LostTalesUiCornerMark.OVERHANG_Y) * scale),
                    line.getChatLineID());
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
        long duration = Motions.travelNanos(MotionIds.CHAT_LINE_APPEAR);
        if (duration <= 0L || scrollPosition != 0) {
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
        float progress = Math.min(1.0F,
                (System.nanoTime() - started) / (float)duration);
        return LostTalesChatMotion.message(progress).stackOffsetY;
    }

    /** Horizontal entry offset for lines of the newest message only. */
    private static float entrySlide(ChatLine line) {
        long duration = Motions.travelNanos(MotionIds.CHAT_LINE_APPEAR);
        if (duration <= 0L || line == null
                || !LostTalesChatPresentation.isLastMessage(
                        line.getChatLineID())) {
            return 0.0F;
        }
        long started = LostTalesChatPresentation.getLastMessageNanos();
        if (started <= 0L) {
            return 0.0F;
        }
        return LostTalesChatMotion.message(
                (System.nanoTime() - started) / (float)duration)
                .slideOffsetX;
    }

    /**
     * The newest message's fade in: part of its entrance, and all that is
     * left of it under reduced motion.
     */
    private static float entryOpacity(ChatLine line) {
        long duration = Motions.nanos(MotionIds.CHAT_LINE_APPEAR);
        if (duration <= 0L || line == null
                || !LostTalesChatPresentation.isLastMessage(
                        line.getChatLineID())) {
            return 1.0F;
        }
        long started = LostTalesChatPresentation.getLastMessageNanos();
        if (started <= 0L) {
            return 1.0F;
        }
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
