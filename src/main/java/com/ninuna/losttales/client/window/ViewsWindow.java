package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.gui.tooltip.LostTalesTooltipSmoothing;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Mouse;

/**
 * The Views sub-window: a row of square buttons at the foot of every
 * screen, as Steam's overlay keeps its bar. Each category's view first, a
 * hairline, then the custom views, the Lost Tales Menu's first, and the
 * {@code +} that makes another. A press swaps the screen to that view,
 * and the one shown stands lit; a right-click opens the view's menu
 * (rename, key, reset, delete). It cannot be closed, stands in front of
 * every window and sub-window, and never fades.
 */
final class ViewsWindow extends SubWindowContent {
    /** The kind the screen opens it as, and the layout file keeps its place by. */
    static final SubWindowKind KIND = SubWindowKind.register("views",
            "gui.losttales.window.sub.views");
    /** A button's side: an icon with a framed button's wide inset round it. */
    static final int BUTTON = LostTalesUiInk.ICON_SIZE
            + 2 * LostTalesUiFramedButton.WIDE_INSET;
    private static final int GAP = WindowStyle.BUTTON_GAP;
    private static final int EDGE = WindowStyle.EDGE_GAP;
    private static final int DIVIDER = WindowStyle.DIVIDER_WIDTH;
    /** The index the {@code +} answers to among the buttons, after every view. */
    private static final int MAKE = -2;

    /** Each view's button's motion, by the view's id; the {@code +}'s under its own key. */
    private final Map<String, LostTalesUiButtonMotion> motions =
            new HashMap<String, LostTalesUiButtonMotion>();
    /** Where the content stood when last drawn, in its own whole pixels. */
    private LostTalesUiHitBox drawnBox;
    /** The button under the pointer when last drawn: a view's index, {@link #MAKE}, or -1. */
    private int pointed = -1;

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public boolean standsInFront() {
        return true;
    }

    @Override
    public LostTalesUiSheet stripIcon() {
        return null;
    }

    @Override
    public int naturalWidth() {
        int own = Views.all().size() - Views.custom().size();
        int custom = Views.custom().size();
        return EDGE + own * BUTTON + (own - 1) * GAP + EDGE + DIVIDER + EDGE
                + custom * BUTTON + custom * GAP + BUTTON + EDGE;
    }

    @Override
    public int naturalHeight(int width) {
        return EDGE + BUTTON + EDGE;
    }

    @Override
    public int minWidth() {
        return naturalWidth();
    }

    @Override
    public int minHeight() {
        return naturalHeight(naturalWidth());
    }

    /** Where the hairline stands, after the categories' views. */
    private static int dividerX(double left) {
        int own = Views.all().size() - Views.custom().size();
        return (int)left + EDGE + own * BUTTON + (own - 1) * GAP + EDGE;
    }

    /**
     * Where button {@code index} of {@code views} stands in a content box
     * whose top left is {@code left}, {@code top}; {@link #MAKE} for the
     * {@code +}, after the last view.
     */
    private static LostTalesUiHitBox buttonBox(double left, double top,
                                               int index, List<View> views) {
        int own = views.size() - Views.custom().size();
        int at = index == MAKE ? views.size() : index;
        double x = left + EDGE + at * (BUTTON + GAP);
        if (at >= own) {
            x += EDGE + DIVIDER + EDGE - GAP;
        }
        return new LostTalesUiHitBox(x, top + EDGE, BUTTON, BUTTON);
    }

    private int buttonAt(double x, double y, List<View> views) {
        if (this.drawnBox == null) {
            return -1;
        }
        for (int index = 0; index < views.size(); index++) {
            if (buttonBox(this.drawnBox.left, this.drawnBox.top, index, views)
                    .contains(x, y)) {
                return index;
            }
        }
        return buttonBox(this.drawnBox.left, this.drawnBox.top, MAKE, views)
                .contains(x, y) ? MAKE : -1;
    }

    private LostTalesUiButtonMotion motion(String key) {
        LostTalesUiButtonMotion motion = this.motions.get(key);
        if (motion == null) {
            motion = new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
            this.motions.put(key, motion);
        }
        return motion;
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     int alpha, int surfaceAlpha) {
        List<View> views = Views.all();
        this.drawnBox = box;
        this.pointed = buttonAt(pointerX, pointerY, views);
        long now = System.nanoTime();
        boolean pressed = Mouse.isButtonDown(0);
        for (int index = 0; index < views.size(); index++) {
            View view = views.get(index);
            boolean under = index == this.pointed;
            LostTalesUiHitBox at = buttonBox(box.left, box.top, index, views);
            LostTalesUiButtonMotion motion = motion(view.id());
            motion.advance(now, WindowView.isOn(view) || under, under,
                    under && pressed);
            drawSurface(at, motion.lit(), surfaceAlpha);
            WindowPage icon = iconOf(view);
            if (icon != null) {
                float iconX = (float)at.left + LostTalesUiFramedButton.WIDE_INSET;
                float iconY = (float)at.top + LostTalesUiFramedButton.WIDE_INSET;
                LostTalesUiButton.beginPose(motion, iconX, iconY,
                        LostTalesUiInk.ICON_SIZE, LostTalesUiInk.ICON_SIZE);
                try {
                    LostTalesUiInk.beginContent();
                    icon.drawIcon(minecraft, iconX, iconY, alpha, TabMark.NONE);
                } finally {
                    LostTalesUiButton.endPose();
                }
            }
            LostTalesUiFramedButton.drawInk((float)at.left, (float)at.top,
                    (int)at.width, (int)at.height, motion.lit(), alpha);
        }
        drawMakeButton(box, views, now, pressed, alpha, surfaceAlpha);
        int divider = Math.round(WindowStyle.DIVIDER_ALPHA * alpha / 255.0F);
        WindowStyle.drawDivider(dividerX(box.left), (int)box.top + EDGE,
                BUTTON, divider);
    }

    private static void drawSurface(LostTalesUiHitBox at, float lit,
                                    int surfaceAlpha) {
        LostTalesUiFramedButton.drawSurface((float)at.left, (float)at.top,
                (float)at.width, (float)at.height, lit, surfaceAlpha);
    }

    /**
     * The {@code +}: a framed button with the plus in its middle, greyed
     * once there are as many custom views as there may be.
     */
    private void drawMakeButton(LostTalesUiHitBox box, List<View> views,
                                long now, boolean pressed, int alpha,
                                int surfaceAlpha) {
        LostTalesUiHitBox at = buttonBox(box.left, box.top, MAKE, views);
        boolean can = Views.canMake();
        boolean under = can && this.pointed == MAKE;
        LostTalesUiButtonMotion motion = motion("+");
        motion.advance(now, under, under, under && pressed);
        drawSurface(at, motion.lit(), surfaceAlpha);
        int glyph = LostTalesUiSheet.PLUS.getWidth();
        float x = (float)at.left + LostTalesUiInk.centredStart((int)at.width,
                glyph);
        float y = (float)at.top + LostTalesUiInk.centredStart((int)at.height,
                LostTalesUiSheet.PLUS.getHeight());
        int ink = can ? alpha
                : Math.round(alpha * WindowStyle.UNAVAILABLE_OPACITY);
        LostTalesUiButton.beginPose(motion, x, y, glyph,
                LostTalesUiSheet.PLUS.getHeight());
        try {
            LostTalesUiInk.beginContent();
            LostTalesUiSheet.drawPairWithShadow(LostTalesUiSheet.PLUS,
                    LostTalesUiSheet.PLUS_LIT, can ? motion.lit() : 0.0F, x, y,
                    ink);
        } finally {
            LostTalesUiButton.endPose();
        }
        LostTalesUiFramedButton.drawInk((float)at.left, (float)at.top,
                (int)at.width, (int)at.height, motion.lit(), alpha);
    }

    /**
     * The icon a view's button wears: a category's view its first page's
     * among every page that can be opened; a custom view the page in front
     * of its window brought forward last, else the New Page's. Null where
     * none can be shown.
     */
    private static WindowPage iconOf(View view) {
        if (view.isCustom()) {
            WindowPage front = null;
            List<Window> stacked = WindowLayout.stacked();
            for (int index = stacked.size() - 1; index >= 0 && front == null;
                    index--) {
                if (WindowLayout.viewOf(stacked.get(index)) == view) {
                    front = stacked.get(index).getActiveTab();
                }
            }
            return front != null ? front : WindowPages.tab(NewPage.PAGE_ID);
        }
        for (MenuWindow.Entry entry : TabMenus.everyPage(
                WindowScreen.current(), "")) {
            if (entry.icon != null
                    && Views.of(entry.icon.category()) == view) {
                return entry.icon;
            }
        }
        return null;
    }

    @Override
    public boolean pressed(WindowHover hover, double x, double y, int button) {
        List<View> views = Views.all();
        int index = buttonAt(x, y, views);
        WindowScreen screen = WindowScreen.current();
        if (index == -1 || screen == null) {
            return false;
        }
        LostTalesUiHitBox at = buttonBox(this.drawnBox.left, this.drawnBox.top,
                index, views);
        if (index == MAKE) {
            if (button == 0) {
                screen.makeView();
            }
            return true;
        }
        View view = views.get(index);
        if (button == 0) {
            screen.swapTo(view);
        } else if (button == 1) {
            screen.showViewMenu(view, new SubWindowAnchor(
                    (int)Math.floor(at.left), (int)Math.floor(at.top),
                    (int)Math.ceil(at.right()), (int)Math.ceil(at.bottom()),
                    false, false, null));
        }
        return true;
    }

    @Override
    public String tipKey() {
        return this.pointed == -1 ? "" : tip();
    }

    /** The view's name with its key, {@code Map (M)}; the {@code +}'s, {@code New View}. */
    private String tip() {
        if (this.pointed == MAKE) {
            return Views.canMake() ? StatCollector.translateToLocal(
                    "gui.losttales.window.views.new") : "";
        }
        List<View> views = Views.all();
        if (this.pointed < 0 || this.pointed >= views.size()) {
            return "";
        }
        View view = views.get(this.pointed);
        if (view.category() == PageCategory.SETTINGS) {
            return StatCollector.translateToLocalFormatted(
                    "gui.losttales.window.views.settings_key", view.title());
        }
        return view.key() <= 0 ? view.title()
                : WindowBar.withKey(view.title(), view.key());
    }

    @Override
    public void drawTip(Minecraft minecraft, int tipX, int tipY,
                        int screenWidth, float share) {
        String label = tip();
        if (label.length() == 0) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        int width = WindowStyle.popupLineWidth(font, label);
        int x = Math.max(2, Math.min(screenWidth - width - 2,
                tipX - width / 2));
        int y = tipY - 3 - WindowStyle.POPUP_LINE_HEIGHT;
        LostTalesTooltipSmoothing.begin(tipX, tipY);
        try {
            WindowStyle.drawPopupLine(font, label, x, y, share);
        } finally {
            LostTalesTooltipSmoothing.end();
        }
    }

    /** Where it first stands: centred at the foot of {@code screen}, the room it is held in. */
    LostTalesUiHitBox firstContentBox(LostTalesUiHitBox screen) {
        int width = naturalWidth();
        int height = naturalHeight(width);
        return new LostTalesUiHitBox(
                Math.floor(screen.left + (screen.width - width) / 2.0D),
                screen.top + screen.height - height,
                width, height);
    }
}
