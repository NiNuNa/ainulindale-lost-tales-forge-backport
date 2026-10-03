package com.ninuna.losttales.gui.hud.placement;

import com.ninuna.losttales.client.chat.ChatFeedPlacement;
import com.ninuna.losttales.client.chat.ChatLayout;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import com.ninuna.losttales.gui.hud.LostTalesNotificationHud;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderer;
import com.ninuna.losttales.gui.hud.fellowship.FellowshipHudLayout;
import com.ninuna.losttales.gui.hud.fellowship.LostTalesFellowshipHudRenderer;
import com.ninuna.losttales.gui.hud.loot.LostTalesQuickLootHudRenderer;
import com.ninuna.losttales.gui.hud.quest.LostTalesQuestHudRenderer;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

/**
 * The HUD's panels the HUD Placement page moves: the compass, the
 * fellowship panel, quick loot, the quest tracker, the notices' one slot
 * and the chat feed. Each answers its box on a screen of a given size and
 * takes a new place there. The first five keep their places as shares of
 * the screen in the client file; the chat feed keeps its own in the window
 * layout, in fractions of a pixel, and stands at its default place over
 * the hotbar until it is moved.
 */
public enum HudPanel {
    COMPASS("compass"),
    FELLOWSHIP("fellowship"),
    QUICK_LOOT("quickloot"),
    QUEST_TRACKER("quest"),
    NOTIFICATIONS("notifications"),
    CHAT_FEED("");

    /** The element's name in the client file and in {@code /losttales hud}; none for the feed. */
    private final String element;

    HudPanel(String element) {
        this.element = element;
    }

    /** Its name, as the page's boxes and its bar read it. */
    public String title() {
        return StatCollector.translateToLocal("gui.losttales.hud.panel."
                + name().toLowerCase(Locale.ROOT));
    }

    /** Its box on a screen {@code screenWidth} by {@code screenHeight}: left, top, width, height. */
    public double[] box(Minecraft minecraft, int screenWidth, int screenHeight) {
        if (this == CHAT_FEED) {
            WindowPlacement.Box box = ChatFeedPlacement.bounds(minecraft,
                    screenWidth, screenHeight);
            return new double[] {box.x, box.y, box.width, box.height};
        }
        HudPlacementLayout.Bounds bounds = HudPlacementLayout.calculate(
                screenWidth, screenHeight, width(), height(), offsetX(),
                offsetY(), horizontalMode(), verticalMode(), 0,
                pixelOffsetY(minecraft));
        return new double[] {bounds.x, bounds.y, bounds.width, bounds.height};
    }

    /**
     * Moves its top left to {@code x}, {@code y} on that screen, without
     * writing it down: a drag's every frame. The fixed panels stand on
     * whole pixels, the feed anywhere.
     */
    public void moveTo(Minecraft minecraft, double x, double y,
                       int screenWidth, int screenHeight) {
        double[] box = box(minecraft, screenWidth, screenHeight);
        if (this == CHAT_FEED) {
            ChatLayout.setFeedPosition(
                    ChatFeedPlacement.percentX(x, minecraft, screenWidth),
                    ChatFeedPlacement.percentY(y + box[3], minecraft,
                            screenHeight), false);
            return;
        }
        LostTalesConfig.updateHudOffset(this.element,
                HudPlacementLayout.percentForPosition((int)Math.round(x),
                        screenWidth, (int)Math.round(box[2]),
                        horizontalMode(), 0),
                HudPlacementLayout.percentForPosition((int)Math.round(y),
                        screenHeight, (int)Math.round(box[3]),
                        verticalMode(), pixelOffsetY(minecraft)));
    }

    /** Writes its place down, once a drag or a nudge is over. */
    public void persist() {
        if (this == CHAT_FEED) {
            ChatLayout.saveFeedPosition();
        } else {
            LostTalesConfig.save();
        }
    }

    /** Whether it stands at its default place: the Default layout's, the feed over the hotbar. */
    public boolean atDefaultPlace() {
        if (this == CHAT_FEED) {
            return !ChatLayout.isFeedPlaced();
        }
        double[] place = LostTalesConfig.defaultHudOffset(this.element);
        return place != null && offsetX() == place[0] && offsetY() == place[1];
    }

    /** Puts it back at its default place, and writes it down. */
    public void putBack() {
        if (this == CHAT_FEED) {
            ChatLayout.resetFeedPlace();
            return;
        }
        double[] place = LostTalesConfig.defaultHudOffset(this.element);
        if (place != null) {
            LostTalesConfig.setHudOffset(this.element, place[0], place[1]);
        }
    }

    /** Where it stands as a share of the screen, for the bar to read; the feed's at its default place reads none. */
    public double[] offsets() {
        if (this == CHAT_FEED) {
            return ChatLayout.isFeedPlaced() ? new double[] {
                    ChatLayout.feedOffsetX(), ChatLayout.feedOffsetY()} : null;
        }
        return new double[] {offsetX(), offsetY()};
    }

    private int width() {
        switch (this) {
            case COMPASS:
                return LostTalesCompassHudRenderer.getPlacementWidth();
            case FELLOWSHIP:
                return FellowshipHudLayout.PANEL_WIDTH;
            case QUICK_LOOT:
                return LostTalesQuickLootHudRenderer.getPlacementWidth();
            case QUEST_TRACKER:
                return LostTalesQuestHudRenderer.getTrackerPlacementWidth();
            default:
                return LostTalesNotificationHud.getPlacementWidth();
        }
    }

    private int height() {
        switch (this) {
            case COMPASS:
                return LostTalesCompassHudRenderer.getPlacementHeight();
            case FELLOWSHIP:
                return FellowshipHudLayout.height(
                        LostTalesFellowshipHudRenderer.placementRows());
            case QUICK_LOOT:
                return LostTalesQuickLootHudRenderer.getPlacementHeight();
            case QUEST_TRACKER:
                return LostTalesQuestHudRenderer.getTrackerPlacementHeight();
            default:
                return LostTalesNotificationHud.getPlacementHeight();
        }
    }

    private double offsetX() {
        switch (this) {
            case COMPASS:
                return LostTalesConfig.compassHudOffsetX;
            case FELLOWSHIP:
                return LostTalesConfig.fellowshipHudOffsetX;
            case QUICK_LOOT:
                return LostTalesConfig.quickLootHudOffsetX;
            case QUEST_TRACKER:
                return LostTalesConfig.questHudOffsetX;
            default:
                return LostTalesConfig.notificationHudOffsetX;
        }
    }

    private double offsetY() {
        switch (this) {
            case COMPASS:
                return LostTalesConfig.compassHudOffsetY;
            case FELLOWSHIP:
                return LostTalesConfig.fellowshipHudOffsetY;
            case QUICK_LOOT:
                return LostTalesConfig.quickLootHudOffsetY;
            case QUEST_TRACKER:
                return LostTalesConfig.questHudOffsetY;
            default:
                return LostTalesConfig.notificationHudOffsetY;
        }
    }

    private HudPlacementLayout.CoordinateMode horizontalMode() {
        return this == FELLOWSHIP || this == QUICK_LOOT || this == QUEST_TRACKER
                ? HudPlacementLayout.CoordinateMode.SCREEN_PERCENT
                : HudPlacementLayout.CoordinateMode.AVAILABLE_SPACE_PERCENT;
    }

    private HudPlacementLayout.CoordinateMode verticalMode() {
        return this == NOTIFICATIONS
                ? HudPlacementLayout.CoordinateMode.AVAILABLE_SPACE_PERCENT
                : HudPlacementLayout.CoordinateMode.SCREEN_PERCENT;
    }

    /** The compass is placed by its strip, its distance labels standing above it. */
    private int pixelOffsetY(Minecraft minecraft) {
        return this == COMPASS && minecraft != null
                && minecraft.fontRenderer != null
                ? minecraft.fontRenderer.FONT_HEIGHT
                        + LostTalesCompassHudRenderer
                                .MAP_MARKER_DISTANCE_LABEL_OFFSET_Y
                : 0;
    }
}
