package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;

/**
 * Where the closed feed sits: one stack of every unmuted channel's
 * messages, shown only while the chat is closed. It is placed like a
 * window, by the baseline its newest message sits on, but has no frame,
 * no row and no bar, keeps the screen margin alone and never hangs off
 * the screen. It is moved in the HUD placement editor.
 */
public final class ChatFeedPlacement {
    /** The share of the screen the feed may fill at most. */
    private static final double HEIGHT_SHARE = 1.0D / 3.0D;

    private ChatFeedPlacement() {}

    /**
     * The most message lines the feed shows: as many whole ones as fit in
     * {@link #HEIGHT_SHARE} of the screen, measured with the stride the
     * renderer draws them at, so the feed grows with the screen and the
     * GUI scale without any line changing height. What is left over is
     * margin rather than a clipped line. Without a screen to measure it
     * takes the game's own chat height.
     */
    public static int lineCapacity(Minecraft minecraft) {
        int screenHeight = WindowPlacement.scaledScreenHeight(minecraft);
        if (screenHeight <= 0) {
            GuiNewChat chat = WindowPlacement.chat(minecraft);
            return chat == null ? 10
                    : WindowPlacement.gameChatLines(chat);
        }
        return Math.max(1, (int)(screenHeight * HEIGHT_SHARE
                / WindowPlacement.lineStride(minecraft)));
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

    /** The feed's box for the given screen size. */
    public static WindowPlacement.Box bounds(Minecraft minecraft,
                                             int screenWidth,
                                             int screenHeight) {
        int width = WindowPlacement.windowWidth(minecraft);
        double height = height(minecraft);
        double baseline = keepOnScreen(baselineFor(
                ChatLayout.feedOffsetY(), minecraft, screenHeight),
                height, screenHeight);
        return new WindowPlacement.Box(WindowPlacement.position(
                ChatLayout.feedOffsetX(), screenWidth, width),
                baseline - height, width, height, 0, height);
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
                WindowPlacement.windowWidth(minecraft));
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
}
