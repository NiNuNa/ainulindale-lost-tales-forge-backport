package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.gui.animation.LostTalesUiEasing;
import com.ninuna.losttales.client.gui.animation.LostTalesUiTransition;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * Snap assist, as a desktop offers it: once a window has been sent to a
 * part of the screen that is one zone of a layout, the layout's other
 * zones stand empty as panes of frosted glass, each holding a card for
 * every other window — its tabs' icons and the name of the tab in front.
 * A click on a card sends that window to the zone, and the panes left go
 * on offering the windows left; Escape, a press anywhere else, or the
 * last zone taken ends it. A zone another window already fills is not
 * offered, nor is a window that is locked, stuck to another, or already
 * filling one of the layout's zones.
 */
final class ChatSnapAssist {
    /** Clear pixels between two cards of a pane. */
    static final int CARD_GAP = 3;
    /** Clear pixels between a card's icons, and between them and its name. */
    static final int ICON_GAP = 2;
    /** Clear pixels the cards keep from the pane's sides. */
    static final int PANE_PADDING = 8;

    private final LostTalesUiTransition shown = new LostTalesUiTransition();
    private boolean open;
    /** The window whose landing opened the offer. */
    private String snappedId;
    /** The layout it landed in, and the zones of it still on offer. */
    private ChatWindow.ScreenFill[] layout;
    private final List<ChatWindow.ScreenFill> zones =
            new ArrayList<ChatWindow.ScreenFill>();
    /** The panes as laid out this frame. */
    private List<Pane> panes = Collections.emptyList();
    /** How far each window's card has lit, by window id. */
    private final Map<String, Float> lit = new HashMap<String, Float>();
    private long litNanos;

    /** One window's card: where it stands and the zone it would fill. */
    static final class Card {
        final String windowId;
        final ChatWindow.ScreenFill zone;
        final LostTalesUiHitBox box;

        Card(String windowId, ChatWindow.ScreenFill zone,
             LostTalesUiHitBox box) {
            this.windowId = windowId;
            this.zone = zone;
            this.box = box;
        }
    }

    /** One empty zone: its pane of glass and its cards. */
    static final class Pane {
        final ChatSnapPreview.Pane glass;
        final List<Card> cards;

        Pane(ChatSnapPreview.Pane glass, List<Card> cards) {
            this.glass = glass;
            this.cards = cards;
        }
    }

    ChatSnapAssist() {
        // Away from the start, so the first offer fades in.
        this.shown.settle(false);
    }

    /**
     * Offers the rest of {@code layout} to the other windows once the
     * window {@code windowId} has been sent to {@code fill}: nothing for
     * a part that is no zone of a layout, and nothing when no zone is
     * left or no window can take one.
     */
    void offer(String windowId, ChatWindow.ScreenFill[] layout,
               ChatWindow.ScreenFill fill) {
        this.open = false;
        this.zones.clear();
        if (windowId == null || layout == null || fill == null) {
            return;
        }
        this.snappedId = windowId;
        this.layout = layout;
        for (ChatWindow.ScreenFill zone : layout) {
            if (zone != fill && !filledByAnother(zone)) {
                this.zones.add(zone);
            }
        }
        this.open = !this.zones.isEmpty() && !candidates().isEmpty();
    }

    boolean isOpen() {
        return this.open;
    }

    /** Ends the offer; the panes fade where they stand. */
    void close() {
        this.open = false;
    }

    private boolean filledByAnother(ChatWindow.ScreenFill zone) {
        for (ChatWindow window : ChatWindowLayout.windows()) {
            if (!window.getId().equals(this.snappedId)
                    && window.getFill() == zone) {
                return true;
            }
        }
        return false;
    }

    private boolean inLayout(ChatWindow.ScreenFill fill) {
        if (this.layout == null) {
            return false;
        }
        for (ChatWindow.ScreenFill zone : this.layout) {
            if (zone == fill) {
                return true;
            }
        }
        return false;
    }

    /** The windows a card is offered for, the one in front first. */
    List<ChatWindow> candidates() {
        List<ChatWindow> result = new ArrayList<ChatWindow>();
        for (ChatWindow window : otherWindows(this.snappedId)) {
            if (!inLayout(window.getFill())) {
                result.add(window);
            }
        }
        return result;
    }

    /**
     * The windows that may be put in a zone beside the window
     * {@code windowId}, the one in front first: not that window, nor one
     * that is locked, stuck to another or showing no tab.
     */
    static List<ChatWindow> otherWindows(String windowId) {
        List<ChatWindow> result = new ArrayList<ChatWindow>();
        List<ChatWindow> windows = ChatWindowLayout.windows();
        for (int index = windows.size() - 1; index >= 0; index--) {
            ChatWindow window = windows.get(index);
            if (window.getId().equals(windowId) || window.isLocked()
                    || ChatWindowFrame.visibleTabs(window).isEmpty()
                    || ChatWindowLayout.linkedGroup(window).size() > 1) {
                continue;
            }
            result.add(window);
        }
        return result;
    }

    /**
     * Lays the panes and their cards out for this frame: a pane in each
     * zone on offer, a window's frame in from the zone's edges as a
     * window filling it would stand, and the cards stacked in its middle
     * as many as it holds.
     */
    void layOut(Minecraft minecraft, FontRenderer font, int screenWidth,
                int screenHeight) {
        float share = this.shown.advance(System.nanoTime(), this.open,
                LostTalesConfig.enableChatAnimations
                        ? Math.max(1, LostTalesConfig.chatAnimationDurationMillis)
                        : 0, LostTalesUiEasing.SMOOTH);
        if (!this.open && share <= 0.0F) {
            this.panes = Collections.emptyList();
            this.zones.clear();
            return;
        }
        if (!this.open) {
            // Fading: the panes stay where they were laid.
            return;
        }
        List<ChatWindow> windows = candidates();
        List<Pane> laid = new ArrayList<Pane>(this.zones.size());
        for (ChatWindow.ScreenFill zone : this.zones) {
            ChatWindowPlacement.Box box = ChatWindowPlacement.fillBounds(zone,
                    minecraft, screenWidth, screenHeight);
            ChatSnapPreview.Pane glass = new ChatSnapPreview.Pane(
                    Math.floor(box.x), Math.floor(box.y),
                    Math.floor(box.right()), Math.floor(box.bottom()));
            laid.add(new Pane(glass, cardsIn(glass, zone, windows, font)));
        }
        this.panes = laid;
    }

    private static List<Card> cardsIn(ChatSnapPreview.Pane glass,
                                      ChatWindow.ScreenFill zone,
                                      List<ChatWindow> windows,
                                      FontRenderer font) {
        int room = (int)Math.floor(glass.right - glass.left) - 2 * PANE_PADDING;
        int height = LostTalesUiFramedButton.HEIGHT;
        int fits = Math.max(0, ((int)Math.floor(glass.bottom - glass.top)
                - 2 * PANE_PADDING + CARD_GAP) / (height + CARD_GAP));
        int count = Math.min(windows.size(), fits);
        if (room < LostTalesUiFramedButton.MIN_SIZE || count <= 0) {
            return Collections.emptyList();
        }
        int total = count * height + (count - 1) * CARD_GAP;
        int top = (int)Math.floor((glass.top + glass.bottom - total) / 2.0D);
        List<Card> cards = new ArrayList<Card>(count);
        for (int index = 0; index < count; index++) {
            ChatWindow window = windows.get(index);
            int width = Math.min(room, cardWidth(window, font));
            int left = (int)Math.floor((glass.left + glass.right - width)
                    / 2.0D);
            cards.add(new Card(window.getId(), zone, new LostTalesUiHitBox(
                    left, top + index * (height + CARD_GAP), width, height)));
        }
        return cards;
    }

    /** A card's whole width: its icons, the front tab's name, the inset round them. */
    private static int cardWidth(ChatWindow window, FontRenderer font) {
        List<ChatTab> tabs = ChatWindowFrame.visibleTabs(window);
        ChatTab front = ChatWindowFrame.activeTab(window, tabs);
        int icons = tabs.size() * LostTalesUiInk.ICON_SIZE
                + Math.max(0, tabs.size() - 1) * ICON_GAP;
        int name = front == null || font == null ? 0
                : font.getStringWidth(ClientChatChannelState.displayName(front));
        return 2 * LostTalesUiFramedButton.INSET + icons
                + (name > 0 ? ICON_GAP + 1 + name : 0);
    }

    /** The card under the point, or null. */
    Card cardAt(double x, double y) {
        if (!this.open) {
            return null;
        }
        for (Pane pane : this.panes) {
            for (Card card : pane.cards) {
                if (card.box.contains(x, y)) {
                    return card;
                }
            }
        }
        return null;
    }

    /**
     * A card pressed: its window fills the card's zone, and the offer
     * goes on with the zones and windows left, or ends.
     */
    void take(Card card, ChatTabActions actions) {
        ChatWindow window = card == null ? null
                : ChatWindowLayout.window(card.windowId);
        if (window == null || window.isLocked()) {
            close();
            return;
        }
        actions.setWindowFill(window, card.zone);
        this.zones.remove(card.zone);
        if (this.zones.isEmpty() || candidates().isEmpty()) {
            close();
        }
    }

    /**
     * Draws the panes at {@code opacity}, the card under the pointer lit,
     * and keeps their rectangles in {@code regions} while the offer
     * stands.
     */
    void draw(Minecraft minecraft, FontRenderer font,
              ChatPointerRegions regions, Card hovered, float opacity) {
        float share = opacity * this.shown.clamped();
        if (this.panes.isEmpty() || share <= 0.0F) {
            return;
        }
        long now = System.nanoTime();
        double elapsed = this.litNanos == 0L ? 0.0D
                : (now - this.litNanos) / 1.0E9D;
        this.litNanos = now;
        int alpha = Math.round(255.0F * Math.min(1.0F, share));
        for (Pane pane : this.panes) {
            List<LostTalesUiHitBox> holes =
                    new ArrayList<LostTalesUiHitBox>(pane.cards.size());
            for (Card card : pane.cards) {
                holes.add(card.box);
            }
            ChatSnapPreview.draw(minecraft, pane.glass, share, holes);
            for (Card card : pane.cards) {
                float fade = LostTalesChatVisualStyle.hoverFade(
                        litOf(card.windowId), hovered != null
                                && hovered.windowId.equals(card.windowId)
                                && hovered.zone == card.zone, elapsed);
                this.lit.put(card.windowId, Float.valueOf(fade));
                drawCard(minecraft, font, card, fade, share, alpha);
            }
            if (this.open && regions != null) {
                regions.addScreen((int)Math.floor(pane.glass.left),
                        (int)Math.floor(pane.glass.top),
                        (int)Math.ceil(pane.glass.right),
                        (int)Math.ceil(pane.glass.bottom));
            }
        }
    }

    private float litOf(String windowId) {
        Float fade = this.lit.get(windowId);
        return fade == null ? 0.0F : fade.floatValue();
    }

    /** One card: a framed button holding the window's tab icons and its front tab's name. */
    private static void drawCard(Minecraft minecraft, FontRenderer font,
                                 Card card, float lit, float share,
                                 int alpha) {
        ChatWindow window = ChatWindowLayout.window(card.windowId);
        if (window == null) {
            return;
        }
        float left = (float)card.box.left;
        float top = (float)card.box.top;
        int width = (int)card.box.width;
        int height = (int)card.box.height;
        LostTalesUiFramedButton.drawSurface(left, top, width, height, lit,
                Math.round(LostTalesChatVisualStyle.INSET_ALPHA * share
                        * LostTalesChatVisualStyle.chatOpacity(minecraft)));
        List<ChatTab> tabs = ChatWindowFrame.visibleTabs(window);
        ChatTab front = ChatWindowFrame.activeTab(window, tabs);
        float right = left + width - LostTalesUiFramedButton.INSET;
        float x = left + LostTalesUiFramedButton.INSET;
        float iconTop = top + LostTalesUiFramedButton.INSET;
        LostTalesChatVisualStyle.beginContent();
        for (ChatTab tab : tabs) {
            if (x + LostTalesUiInk.ICON_SIZE > right) {
                break;
            }
            ChatChannelIcons.draw(minecraft, tab, x, iconTop, alpha);
            x += LostTalesUiInk.ICON_SIZE + ICON_GAP;
        }
        if (front != null && font != null && x < right) {
            String name = font.trimStringToWidth(
                    ClientChatChannelState.displayName(front),
                    (int)Math.floor(right - x - 1));
            int rgb = LostTalesChatVisualStyle.blend(
                    ClientChatChannelState.displayColor(front),
                    LostTalesChatVisualStyle.IVORY, lit);
            // The capitals centred on the icons' box, the odd pixel up.
            LostTalesChatVisualStyle.drawColored(font, name, (int)x + 1,
                    (int)top + LostTalesUiFramedButton.INSET
                            + LostTalesUiInk.centredStart(
                                    LostTalesUiInk.ICON_SIZE,
                                    LostTalesChatOverlayRenderer
                                            .GLYPH_CAP_HEIGHT),
                    rgb, alpha);
        }
        LostTalesUiFramedButton.drawInk(left, top, width, height, lit, alpha);
    }
}
