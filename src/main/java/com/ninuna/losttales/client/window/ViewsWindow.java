package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.gui.tooltip.LostTalesTooltipSmoothing;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Mouse;

/**
 * The Views sub-window: a row of square buttons at the foot of every
 * screen, as Steam's overlay keeps its bar. The Lost Tales Menu's view
 * first, a hairline, then each category's view ({@link PageCategory#views});
 * a press swaps the screen to that view, and the one shown stands lit. It
 * cannot be closed. While a window lies over it, it stands aside, and
 * comes back under the pointer.
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

    /** The views, in the order their buttons stand: the Lost Tales Menu's first. */
    private final List<PageCategory> views = PageCategory.views();
    private final List<LostTalesUiButtonMotion> motions =
            new ArrayList<LostTalesUiButtonMotion>();
    /** Where the content stood when last drawn, in its own whole pixels. */
    private LostTalesUiHitBox drawnBox;
    /** The button under the pointer when last drawn; -1 for none. */
    private int pointed = -1;
    /** Whether it stands aside for now: a window lies over it, and the pointer is away. */
    private boolean tucked;

    ViewsWindow() {
        for (int index = 0; index < this.views.size(); index++) {
            this.motions.add(new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT));
        }
    }

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public boolean isTucked() {
        return this.tucked;
    }

    void setTucked(boolean tucked) {
        this.tucked = tucked;
    }

    @Override
    public LostTalesUiSheet stripIcon() {
        return null;
    }

    @Override
    public int naturalWidth() {
        int categories = this.views.size() - 1;
        return EDGE + BUTTON + EDGE + DIVIDER + EDGE
                + categories * BUTTON + (categories - 1) * GAP + EDGE;
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

    /** Where button {@code index} stands in a content box whose top left is {@code left}, {@code top}. */
    private LostTalesUiHitBox buttonBox(double left, double top, int index) {
        double x = left + EDGE;
        if (index > 0) {
            x += BUTTON + EDGE + DIVIDER + EDGE + (index - 1) * (BUTTON + GAP);
        }
        return new LostTalesUiHitBox(x, top + EDGE, BUTTON, BUTTON);
    }

    private int buttonAt(double x, double y) {
        if (this.drawnBox == null) {
            return -1;
        }
        for (int index = 0; index < this.views.size(); index++) {
            if (buttonBox(this.drawnBox.left, this.drawnBox.top, index)
                    .contains(x, y)) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     int alpha, int surfaceAlpha) {
        this.drawnBox = box;
        this.pointed = buttonAt(pointerX, pointerY);
        long now = System.nanoTime();
        boolean pressed = Mouse.isButtonDown(0);
        Map<PageCategory, WindowPage> icons = viewIcons();
        for (int index = 0; index < this.views.size(); index++) {
            PageCategory view = this.views.get(index);
            LostTalesUiHitBox at = buttonBox(box.left, box.top, index);
            boolean under = index == this.pointed;
            LostTalesUiButtonMotion motion = this.motions.get(index);
            motion.advance(now, WindowView.isOn(view) || under, under,
                    under && pressed);
            float lit = motion.lit();
            LostTalesUiFramedButton.drawSurface((float)at.left, (float)at.top,
                    (float)at.width, (float)at.height, lit, surfaceAlpha);
            WindowPage icon = icons.get(view);
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
                    (int)at.width, (int)at.height, lit, alpha);
        }
        int divider = Math.round(WindowStyle.DIVIDER_ALPHA * alpha / 255.0F);
        WindowStyle.drawDivider((int)box.left + EDGE + BUTTON + EDGE,
                (int)box.top + EDGE, BUTTON, divider);
    }

    /**
     * The icon each view's button wears: the New Page's for the Lost Tales
     * Menu, and each category's first page's among every page that can be
     * opened; a view none of whose pages can be opened now has none.
     */
    private Map<PageCategory, WindowPage> viewIcons() {
        Map<PageCategory, WindowPage> icons =
                new EnumMap<PageCategory, WindowPage>(PageCategory.class);
        OtherPage newPage = WindowPages.tab(NewPage.PAGE_ID);
        if (newPage != null) {
            icons.put(PageCategory.MENU, newPage);
        }
        for (MenuWindow.Entry entry : TabMenus.everyPage(
                WindowScreen.current(), "")) {
            if (entry.icon != null && entry.icon.category().home() != null
                    && !icons.containsKey(entry.icon.category().home())) {
                icons.put(entry.icon.category().home(), entry.icon);
            }
        }
        return icons;
    }

    @Override
    public boolean pressed(WindowHover hover, double x, double y, int button) {
        int index = buttonAt(x, y);
        WindowScreen screen = WindowScreen.current();
        if (index >= 0 && button == 0 && screen != null) {
            screen.swapTo(this.views.get(index));
        }
        return index >= 0;
    }

    @Override
    public String tipKey() {
        return this.pointed < 0 ? "" : tip();
    }

    /** The view's name with its key: {@code Lost Tales Menu (Caps Lock)}, {@code Map (M)}. */
    private String tip() {
        PageCategory view = this.views.get(this.pointed);
        String name = view == PageCategory.MENU
                ? StatCollector.translateToLocal("key.losttales.menu")
                : view.title();
        if (view == PageCategory.SETTINGS) {
            return StatCollector.translateToLocalFormatted(
                    "gui.losttales.window.views.settings_key", name);
        }
        KeyBinding key = keyOf(view);
        return key == null ? name : WindowBar.withKey(name, key.getKeyCode());
    }

    /** The key that opens a view; null for the settings, whose key is Ctrl+,. */
    private static KeyBinding keyOf(PageCategory view) {
        Minecraft minecraft = Minecraft.getMinecraft();
        switch (view) {
            case MENU:
                return LostTalesKeyBindings.getMenuKeyBinding();
            case CHANNELS:
                return minecraft.gameSettings.keyBindChat;
            case CONSOLES:
                return minecraft.gameSettings.keyBindCommand;
            case MAP:
                return LostTalesKeyBindings.getMapKeyBinding();
            case QUEST_JOURNAL:
                return LostTalesKeyBindings.getQuestJournalKeyBinding();
            case FELLOWSHIPS:
                return LostTalesKeyBindings.getFellowshipKeyBinding();
            case PROFILE:
                return LostTalesKeyBindings.getCharactersKeyBinding();
            default:
                return null;
        }
    }

    @Override
    public void drawTip(Minecraft minecraft, int tipX, int tipY,
                        int screenWidth, float share) {
        if (this.pointed < 0) {
            return;
        }
        String label = tip();
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
