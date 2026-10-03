package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.gui.LostTalesHudFade;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.GuiIngameForge;

/**
 * Where the closed feed sits: one stack of every unmuted channel's
 * messages, shown only while the chat is closed. It is placed like a
 * window, by the baseline its newest message sits on, but has no frame,
 * no row and no bar, keeps the screen margin alone and never hangs off
 * the screen. It is as wide and holds as many lines as its settings say
 * (Chat Feed Settings).
 *
 * <p>Until the player places it on the HUD Placement page it stands at
 * its default place: centred, its last row {@link #HUD_GAP} pixels above
 * the rows the game stacks over the hotbar — hearts, armour, food, air, a
 * mount's health, the experience bar — following them as they come and
 * go, gliding there.</p>
 */
public final class ChatFeedPlacement {
    /** The share of the screen the feed may fill at most, whatever its lines. */
    private static final double HEIGHT_SHARE = 1.0D / 2.0D;
    /** Clear pixels between the feed at its default place and the rows over the hotbar. */
    static final int HUD_GAP = 3;
    /**
     * How far above the screen's foot Forge starts the rows over the
     * hotbar, the hearts' and the food's, and how far each row reaches up.
     */
    private static final int FIRST_ROW_RISE = 39;
    private static final int ROW_STEP = 10;
    /** The experience bar's top, over the hotbar, while no row stands. */
    private static final int EXPERIENCE_RISE = 29;
    /** The hotbar's top, with neither rows nor experience over it. */
    private static final int HOTBAR_RISE = 22;
    /** How far the rows over the hotbar reach, as last seen whole: the hearts' row until then. */
    private static int hudRise = FIRST_ROW_RISE;
    /** The same as the feed glides to it; NaN before the first frame. */
    private static double shownRise = Double.NaN;
    private static long riseNanos;

    private ChatFeedPlacement() {}

    /**
     * Reads how far the game's rows over the hotbar reach this frame,
     * from the heights Forge counts up as it draws them, which other mods'
     * rows add to as well; asked as the chat draws, after them. Only while
     * the HUD shows whole: one stepping aside for the windows draws none
     * of them, and the feed stays where they last stood.
     */
    public static void noteHud(Minecraft minecraft) {
        if (minecraft == null || minecraft.playerController == null
                || !LostTalesHudFade.isWhole()) {
            return;
        }
        hudRise = hudRise(Math.max(GuiIngameForge.left_height,
                GuiIngameForge.right_height),
                minecraft.playerController.shouldDrawHUD(),
                minecraft.playerController.gameIsSurvivalOrAdventure());
    }

    /**
     * How far above the screen's foot the rows over the hotbar reach,
     * from Forge's count of them ({@code stacked}, the higher side's):
     * the top row's top, each row standing a step over the count before
     * it; with no row, the experience bar's top or the hotbar's.
     */
    static int hudRise(int stacked, boolean statusRows, boolean experience) {
        if (statusRows && stacked > FIRST_ROW_RISE) {
            return stacked - ROW_STEP;
        }
        return experience ? EXPERIENCE_RISE : HOTBAR_RISE;
    }

    /** Forgets the rows' height as the player leaves the world: the next measures afresh. */
    public static void reset() {
        hudRise = FIRST_ROW_RISE;
        shownRise = Double.NaN;
    }

    /**
     * The feed's baseline at its default place: its last row
     * {@link #HUD_GAP} pixels over the rows over the hotbar, gliding as
     * they come and go.
     */
    static double defaultBaseline(int screenHeight) {
        long now = System.nanoTime();
        if (Double.isNaN(shownRise)) {
            shownRise = hudRise;
        } else {
            double elapsed = Math.min(0.25D,
                    Math.max(0.0D, (now - riseNanos) / 1.0E9D));
            shownRise = Motions.followTravel(MotionIds.HUD_FEED_RISE,
                    shownRise, hudRise, elapsed);
            if (Math.abs(shownRise - hudRise) < 0.01D) {
                shownRise = hudRise;
            }
        }
        riseNanos = now;
        return screenHeight - shownRise - HUD_GAP;
    }

    /**
     * The most message lines the feed shows: its setting's, and never more
     * whole ones than {@link #HEIGHT_SHARE} of the screen holds, measured
     * with the stride the renderer draws them at. Without a screen to
     * measure it takes the setting alone.
     */
    public static int lineCapacity(Minecraft minecraft) {
        int wanted = Math.max(1, LostTalesConfig.chatFeedLines);
        int screenHeight = WindowPlacement.scaledScreenHeight(minecraft);
        if (screenHeight <= 0) {
            return wanted;
        }
        return Math.max(1, Math.min(wanted, (int)(screenHeight * HEIGHT_SHARE
                / WindowPlacement.lineStride(minecraft))));
    }

    /**
     * The feed's box height: the stack it holds now, in lines and parts of
     * one, at least one and at most {@link #lineCapacity}.
     */
    public static double height(Minecraft minecraft) {
        ChatFrame frame = ChatFrame.feed();
        double lines = 1.0D;
        if (frame.lines != null) {
            lines = Math.max(1.0D, Math.min(lineCapacity(minecraft),
                    frame.contentLines()));
        }
        return WindowPlacement.roomForLines(lines, minecraft);
    }

    /** The feed's box width: its setting's, within the screen's margins. */
    public static int width(Minecraft minecraft, int screenWidth) {
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        return Math.max(1, Math.min(LostTalesConfig.chatFeedWidth,
                screenWidth - 2 * margin));
    }

    /** The chat width the feed's lines are laid out and drawn at: what its box leaves them. */
    public static int chatWidth(Minecraft minecraft) {
        return Math.max(1, WindowPlacement.chatWidthForBox(
                width(minecraft, WindowPlacement.scaledScreenWidth(minecraft)),
                minecraft));
    }

    /** The feed's box for the given screen size: where it was placed, else its default place. */
    public static WindowPlacement.Box bounds(Minecraft minecraft,
                                             int screenWidth,
                                             int screenHeight) {
        int width = width(minecraft, screenWidth);
        double height = height(minecraft);
        double baseline = ChatLayout.isFeedPlaced()
                ? baselineFor(ChatLayout.feedOffsetY(), minecraft, screenHeight)
                : defaultBaseline(screenHeight);
        double left = ChatLayout.isFeedPlaced()
                ? WindowPlacement.position(ChatLayout.feedOffsetX(),
                        screenWidth, width)
                : Math.floor((screenWidth - width) / 2.0D);
        return new WindowPlacement.Box(left,
                keepOnScreen(baseline, height, screenHeight) - height,
                width, height, 0, height);
    }

    /** The feed's baseline for a percent; its smallest box is one line. */
    public static double baselineFor(double percent, Minecraft minecraft,
                                     int screenHeight) {
        int minHeight = WindowPlacement.lineHeight(minecraft);
        return WindowPlacement.position(percent, screenHeight, minHeight)
                + minHeight;
    }

    /** The feed's percent for a left edge. */
    public static double percentX(double x, Minecraft minecraft,
                                  int screenWidth) {
        return WindowPlacement.percent(x, screenWidth,
                width(minecraft, screenWidth));
    }

    /** The feed's percent for a baseline. */
    public static double percentY(double baseline, Minecraft minecraft,
                                  int screenHeight) {
        int minHeight = WindowPlacement.lineHeight(minecraft);
        return WindowPlacement.percent(baseline - minHeight, screenHeight,
                minHeight);
    }

    /**
     * Keeps the feed's box whole on the screen: pushed down where it would
     * cross the top margin, up where it would leave the screen below.
     */
    static double keepOnScreen(double baseline, double height,
                               int screenHeight) {
        int margin = HudPlacementLayout.SCREEN_MARGIN;
        double minBaseline = margin + height;
        double maxBaseline = Math.max(minBaseline, screenHeight - margin);
        return Math.max(minBaseline, Math.min(maxBaseline, baseline));
    }

    /** How long a line stays in the feed, in the game's update ticks: its setting's seconds. */
    public static int fadeTicks() {
        return Math.max(2, LostTalesConfig.chatFeedSeconds) * 20;
    }

    /** The longest a line may stay in the feed, the setting's bound, in update ticks. */
    public static final int MOST_TICKS = 60 * 20;
}
