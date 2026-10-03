package com.ninuna.losttales.gui.hud.placement;

import com.ninuna.losttales.client.window.BarItem;
import com.ninuna.losttales.client.window.OptionGlyph;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageKeys;
import com.ninuna.losttales.client.window.PageOption;
import com.ninuna.losttales.client.window.Settings;
import com.ninuna.losttales.client.window.ToolStrip;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.HudPlacementLayout;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiRules;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * The HUD Placement page: the whole game screen in small, twice. At the
 * left the placement screen, where each of the HUD's panels stands as a
 * box with its name, to be dragged where it should stand; at the right,
 * as a conversation's member list stands, the preview: the screen as it
 * will look, the world behind, the game's own hotbar and the mod's panels
 * drawn as they draw themselves ({@link HudPreview}). The preview is the
 * page's panel, out and away with the strip's panel button.
 *
 * <p>A box is picked by a press and carried while the button is held; it
 * pulls to the screen's middle on either axis within {@link #SNAP_REACH}
 * of the screen's pixels, the guide lit honey while it holds. The arrows
 * move the box picked a pixel, ten with Shift, as a number in Settings
 * steps ten (Alt with an arrow snaps the window). What it moves is
 * the panel's own place, so the HUD and the preview follow at once; it is
 * written down as the press or the key ends. The bar says where the box
 * picked stands and puts it back at its default place.</p>
 */
public final class HudPlacementPage extends PageContent {
    public static final String PAGE_ID = "hud_placement";
    public static final ItemStack ICON = new ItemStack(Items.compass);
    private static final String LANG = "gui.losttales.page.hud_placement.";
    /** The preview's share of the page's width while it is out. */
    private static final double PREVIEW_SHARE = 0.4D;
    /** Clear room round each small screen. */
    private static final int MARGIN = 6;
    /** How near the screen's middle a box's own middle pulls to it, in the screen's pixels. */
    private static final int SNAP_REACH = 6;
    /** The placement screen's grid, in the screen's pixels: a fine step and a strong one. */
    private static final int GRID_MINOR = 10;
    private static final int GRID_MAJOR = 50;
    /** A fine grid line shows only where its step is at least this many pixels in small. */
    private static final double GRID_MINOR_SHOWN = 4.0D;
    private static final int NUDGE = 1;
    private static final int FAST_NUDGE = 10;
    private static final ToolStrip.Panel PREVIEW_PANEL = new ToolStrip.Panel(
            LostTalesUiSheet.AREA, LostTalesUiSheet.AREA_HOVER,
            LANG + "preview.show", LANG + "preview.hide");
    private static final String OPTION_PANEL_PREFIX = "panel:";
    private static final String OPTION_LAYOUT = "layout";
    private static final String OPTION_LAYOUT_PREFIX = "layout:";
    private static final String BAR_PUT_BACK = "put_back";
    /** Stand-ins for the HUD's and the panels' option buttons until Nils draws their own. */
    private static final OptionGlyph HUD_GLYPH = OptionGlyph.pattern(
            "#####", "#...#", "#...#", "#...#", "#####");
    private static final OptionGlyph COMPASS_GLYPH = OptionGlyph.pattern(
            ".###.", "#..##", "#.#.#", "##..#", ".###.");
    private static final OptionGlyph FELLOWSHIP_GLYPH = OptionGlyph.pattern(
            "#...#", "#...#", ".....", "##.##", "##.##");
    private static final OptionGlyph LOOT_GLYPH = OptionGlyph.pattern(
            "#####", "#...#", "##.##", "#...#", "#####");
    private static final OptionGlyph QUEST_GLYPH = OptionGlyph.pattern(
            "..#..", "..#..", "..#..", ".....", "..#..");
    private static final OptionGlyph LAYOUT_GLYPH = OptionGlyph.pattern(
            "##.##", "##.##", ".....", "##.##", "##.##");
    /** The layouts the Panel Layout option picks from, in the order it lists them. */
    private static final String[] LAYOUTS = {
            LostTalesConfig.HUD_PRESET_DEFAULT,
            LostTalesConfig.HUD_PRESET_LOTR_SAFE,
            LostTalesConfig.HUD_PRESET_COMPACT,
            LostTalesConfig.HUD_PRESET_MINIMAL};

    private boolean previewOut = true;
    /** The box picked: the one the arrows move and the bar reads; null for none. */
    private HudPanel picked;
    /** Whether the box picked is in the hand. */
    private boolean carrying;
    /** Where in the carried box the press took hold of it, in the screen's pixels. */
    private double holdX;
    private double holdY;
    /** Whether the carried box holds the screen's middle, on each axis. */
    private boolean heldMiddleX;
    private boolean heldMiddleY;
    /** Whether an arrow moved the box picked since its place was last written. */
    private boolean nudged;

    /* ---- Where everything stands ---- */

    /** The game's screen, in its GUI pixels, as the page draws it in small. */
    private static int[] screenSize(Minecraft minecraft) {
        int width = Math.max(1, WindowPlacement.scaledScreenWidth(minecraft));
        int height = Math.max(1, WindowPlacement.scaledScreenHeight(minecraft));
        return new int[] {width, height};
    }

    /** The placement screen's side of the page: all of it while the preview is away. */
    private LostTalesUiHitBox placementSide(LostTalesUiHitBox box) {
        if (!this.previewOut) {
            return box;
        }
        double preview = Math.floor(box.width * PREVIEW_SHARE);
        return new LostTalesUiHitBox(box.left, box.top,
                Math.max(0.0D, box.width - preview - 1.0D), box.height);
    }

    /** The preview's side of the page, right of the rule; null while it is away. */
    private LostTalesUiHitBox previewSide(LostTalesUiHitBox box) {
        if (!this.previewOut) {
            return null;
        }
        double preview = Math.floor(box.width * PREVIEW_SHARE);
        return new LostTalesUiHitBox(box.left + box.width - preview, box.top,
                preview, box.height);
    }

    /**
     * The game's screen shrunk to stand whole in {@code side}, its margin
     * kept, centred on whole pixels: left, top, width, height and the scale.
     */
    static double[] fitted(LostTalesUiHitBox side, int screenWidth,
                           int screenHeight) {
        double roomWidth = Math.max(1.0D, side.width - 2 * MARGIN);
        double roomHeight = Math.max(1.0D, side.height - 2 * MARGIN);
        double scale = Math.min(roomWidth / screenWidth,
                roomHeight / screenHeight);
        double width = Math.floor(screenWidth * scale);
        double height = Math.floor(screenHeight * scale);
        double left = Math.floor(side.left + (side.width - width) / 2.0D);
        double top = Math.floor(side.top + (side.height - height) / 2.0D);
        return new double[] {left, top, width, height, scale};
    }

    /* ---- Drawing ---- */

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        int[] screen = screenSize(minecraft);
        double[] placement = fitted(placementSide(box), screen[0], screen[1]);
        drawPlacement(minecraft, placement, screen, pointerX, pointerY, alpha);
        LostTalesUiHitBox preview = previewSide(box);
        if (preview == null) {
            return;
        }
        float rule = (float)(preview.left - 1.0D);
        LostTalesUiRules.drawStandingRule(rule, rule + 1.0F,
                (float)box.top, (float)(box.top + box.height), alpha);
        double[] shown = fitted(preview, screen[0], screen[1]);
        HudPreview.draw(minecraft, shown, screen, partialTicks, alpha,
                clipX + (shown[0] - box.left), clipY + (shown[1] - box.top));
    }

    /**
     * The placement screen: its surface, its grid and its middle, each
     * panel's box with its name, the box picked over the others, and the
     * middle lit while a carried box holds it.
     */
    private void drawPlacement(Minecraft minecraft, double[] placed,
                               int[] screen, double pointerX, double pointerY,
                               int alpha) {
        float left = (float)placed[0];
        float top = (float)placed[1];
        float right = left + (float)placed[2];
        float bottom = top + (float)placed[3];
        double scale = placed[4];
        LostTalesUiInk.fillRect(left, top, right, bottom,
                LostTalesUiInk.argb(LostTalesColors.rgb(LostTalesColors.PLUM_BLACK),
                        Math.round(alpha * 0.6F)));
        drawGrid(left, top, right, bottom, scale, screen, alpha);
        HudPanel pointed = panelAt(minecraft, placed, screen, pointerX, pointerY);
        for (HudPanel panel : HudPanel.values()) {
            if (panel != this.picked) {
                drawBox(minecraft, panel, placed, screen, panel == pointed,
                        alpha);
            }
        }
        if (this.picked != null) {
            drawBox(minecraft, this.picked, placed, screen,
                    this.picked == pointed, alpha);
        }
        if (this.carrying) {
            int honey = LostTalesColors.rgb(LostTalesColors.HONEY);
            float middleX = (float)Math.floor(left + screen[0] / 2 * scale);
            float middleY = (float)Math.floor(top + screen[1] / 2 * scale);
            if (this.heldMiddleX) {
                LostTalesUiInk.fillRect(middleX, top, middleX + 1.0F, bottom,
                        LostTalesUiInk.argb(honey, alpha));
            }
            if (this.heldMiddleY) {
                LostTalesUiInk.fillRect(left, middleY, right, middleY + 1.0F,
                        LostTalesUiInk.argb(honey, alpha));
            }
        }
    }

    /**
     * The grid, stepped out from the screen's middle so both halves
     * mirror: the strong lines always, the fine ones only where they stand
     * apart enough to read; the middle's two lines over them.
     */
    private static void drawGrid(float left, float top, float right,
                                 float bottom, double scale, int[] screen,
                                 int alpha) {
        int middleX = screen[0] / 2;
        int middleY = screen[1] / 2;
        boolean fine = GRID_MINOR * scale >= GRID_MINOR_SHOWN;
        int step = fine ? GRID_MINOR : GRID_MAJOR;
        int strong = LostTalesUiInk.argb(LostTalesColors.rgb(
                LostTalesColors.SLATE_BLUE), Math.round(alpha * 0x30 / 255.0F));
        int faint = LostTalesUiInk.argb(LostTalesColors.rgb(
                LostTalesColors.INDIGO), Math.round(alpha * 0x18 / 255.0F));
        for (int dx = step; middleX - dx >= 0 || middleX + dx <= screen[0];
                dx += step) {
            int argb = dx % GRID_MAJOR == 0 ? strong : faint;
            for (int at : new int[] {middleX - dx, middleX + dx}) {
                if (at >= 0 && at <= screen[0]) {
                    float x = (float)Math.floor(left + at * scale);
                    LostTalesUiInk.fillRect(x, top, x + 1.0F, bottom, argb);
                }
            }
        }
        for (int dy = step; middleY - dy >= 0 || middleY + dy <= screen[1];
                dy += step) {
            int argb = dy % GRID_MAJOR == 0 ? strong : faint;
            for (int at : new int[] {middleY - dy, middleY + dy}) {
                if (at >= 0 && at <= screen[1]) {
                    float y = (float)Math.floor(top + at * scale);
                    LostTalesUiInk.fillRect(left, y, right, y + 1.0F, argb);
                }
            }
        }
        int middle = LostTalesUiInk.argb(LostTalesColors.rgb(
                LostTalesColors.ROSE_GRAY), Math.round(alpha * 0x66 / 255.0F));
        float x = (float)Math.floor(left + middleX * scale);
        float y = (float)Math.floor(top + middleY * scale);
        LostTalesUiInk.fillRect(x, top, x + 1.0F, bottom, middle);
        LostTalesUiInk.fillRect(left, y, right, y + 1.0F, middle);
    }

    /**
     * One panel's box on the placement screen: its surface, its edge and
     * its name, centred and cut to the box. The box picked wears honey, the
     * one pointed at ivory, the rest sand.
     */
    private void drawBox(Minecraft minecraft, HudPanel panel, double[] placed,
                         int[] screen, boolean pointed, int alpha) {
        double[] box = boxOnPage(minecraft, panel, placed, screen);
        float left = (float)Math.floor(box[0]);
        float top = (float)Math.floor(box[1]);
        float right = Math.max(left + 2.0F, (float)Math.floor(box[0] + box[2]));
        float bottom = Math.max(top + 2.0F, (float)Math.floor(box[1] + box[3]));
        boolean picked = panel == this.picked;
        int fill = picked ? LostTalesColors.PLUM_DARK
                : pointed ? LostTalesColors.DUSK_VIOLET : LostTalesColors.PLUM_BLACK;
        int fillAlpha = picked ? 0x99 : pointed ? 0x66 : 0x40;
        LostTalesUiInk.fillRect(left, top, right, bottom, LostTalesUiInk.argb(
                LostTalesColors.rgb(fill), Math.round(alpha * fillAlpha / 255.0F)));
        int edge = LostTalesColors.rgb(picked ? LostTalesColors.HONEY
                : pointed ? LostTalesColors.IVORY : LostTalesColors.SAND);
        int edgeArgb = LostTalesUiInk.argb(edge, picked ? alpha
                : Math.round(alpha * (pointed ? 0xDD : 0x99) / 255.0F));
        LostTalesUiInk.fillRect(left, top, right, top + 1.0F, edgeArgb);
        LostTalesUiInk.fillRect(left, bottom - 1.0F, right, bottom, edgeArgb);
        LostTalesUiInk.fillRect(left, top + 1.0F, left + 1.0F, bottom - 1.0F,
                edgeArgb);
        LostTalesUiInk.fillRect(right - 1.0F, top + 1.0F, right, bottom - 1.0F,
                edgeArgb);
        FontRenderer font = minecraft.fontRenderer;
        int room = (int)(right - left) - 4;
        if (font == null || room < 6 || bottom - top < LostTalesUiInk.CAP_HEIGHT + 4) {
            return;
        }
        String name = font.trimStringToWidth(panel.title(), room);
        int x = (int)left + LostTalesUiInk.centredStart((int)(right - left),
                font.getStringWidth(name));
        int y = (int)top + LostTalesUiInk.centredStart((int)(bottom - top),
                LostTalesUiInk.CAP_HEIGHT);
        LostTalesUiInk.drawText(font, name, x, y, picked ? edge
                : LostTalesUiInk.IVORY, alpha);
    }

    /** A panel's box on the page: its box on the screen, shrunk onto the placement screen. */
    private static double[] boxOnPage(Minecraft minecraft, HudPanel panel,
                                      double[] placed, int[] screen) {
        double[] box = panel.box(minecraft, screen[0], screen[1]);
        double scale = placed[4];
        return new double[] {placed[0] + box[0] * scale,
                placed[1] + box[1] * scale, box[2] * scale, box[3] * scale};
    }

    /** The panel whose box is under the point, the one picked first, then the last drawn. */
    private HudPanel panelAt(Minecraft minecraft, double[] placed, int[] screen,
                             double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return null;
        }
        if (this.picked != null && holds(boxOnPage(minecraft, this.picked,
                placed, screen), x, y)) {
            return this.picked;
        }
        HudPanel[] panels = HudPanel.values();
        for (int index = panels.length - 1; index >= 0; index--) {
            if (holds(boxOnPage(minecraft, panels[index], placed, screen), x, y)) {
                return panels[index];
            }
        }
        return null;
    }

    private static boolean holds(double[] box, double x, double y) {
        return x >= box[0] && x < box[0] + Math.max(2.0D, box[2])
                && y >= box[1] && y < box[1] + Math.max(2.0D, box[3]);
    }

    /* ---- The pointer ---- */

    @Override
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int[] screen = screenSize(minecraft);
        return panelAt(minecraft, fitted(placementSide(box), screen[0],
                screen[1]), screen, x, y) != null;
    }

    @Override
    public String tipAt(LostTalesUiHitBox box, double x, double y) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int[] screen = screenSize(minecraft);
        HudPanel panel = panelAt(minecraft, fitted(placementSide(box),
                screen[0], screen[1]), screen, x, y);
        return panel == null ? "" : StatCollector.translateToLocalFormatted(
                LANG + "move", panel.title());
    }

    /** A press on a box picks it and takes it in the hand; a press beside every box lets the pick go. */
    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box,
                                double x, double y, int button) {
        if (button != 0) {
            return false;
        }
        int[] screen = screenSize(minecraft);
        double[] placed = fitted(placementSide(box), screen[0], screen[1]);
        HudPanel pressed = panelAt(minecraft, placed, screen, x, y);
        writeNudge();
        this.picked = pressed;
        this.carrying = pressed != null;
        this.heldMiddleX = false;
        this.heldMiddleY = false;
        if (pressed != null) {
            double[] at = pressed.box(minecraft, screen[0], screen[1]);
            this.holdX = (x - placed[0]) / placed[4] - at[0];
            this.holdY = (y - placed[1]) / placed[4] - at[1];
        }
        return pressed != null || placementSide(box).contains(x, y);
    }

    /**
     * The carried box follows the pointer, in the screen's own pixels,
     * pulling to the screen's middle on either axis.
     */
    @Override
    public void mouseDragged(Minecraft minecraft, LostTalesUiHitBox box,
                             double x, double y, int button) {
        if (!this.carrying || this.picked == null || button != 0) {
            return;
        }
        int[] screen = screenSize(minecraft);
        double[] placed = fitted(placementSide(box), screen[0], screen[1]);
        double[] at = this.picked.box(minecraft, screen[0], screen[1]);
        HudPlacementLayout.PreciseDragResult moved =
                HudPlacementLayout.constrainDrag(
                        (x - placed[0]) / placed[4] - this.holdX,
                        (y - placed[1]) / placed[4] - this.holdY,
                        (int)Math.round(at[2]), (int)Math.round(at[3]),
                        screen[0], screen[1], SNAP_REACH);
        this.heldMiddleX = moved.snappedX;
        this.heldMiddleY = moved.snappedY;
        this.picked.moveTo(minecraft, moved.x, moved.y, screen[0], screen[1]);
    }

    /** The box put down: its place is written down. */
    @Override
    public void mouseReleased(Minecraft minecraft, LostTalesUiHitBox box,
                              double x, double y, int button) {
        if (button == 0 && this.carrying) {
            this.carrying = false;
            this.heldMiddleX = false;
            this.heldMiddleY = false;
            if (this.picked != null) {
                this.picked.persist();
            }
        }
    }

    /** The arrows move the box picked a pixel, ten with Shift. */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.picked == null || this.carrying) {
            return false;
        }
        int step = GuiScreen.isShiftKeyDown() ? FAST_NUDGE : NUDGE;
        int dx = keyCode == Keyboard.KEY_LEFT ? -step
                : keyCode == Keyboard.KEY_RIGHT ? step : 0;
        int dy = keyCode == Keyboard.KEY_UP ? -step
                : keyCode == Keyboard.KEY_DOWN ? step : 0;
        if (dx == 0 && dy == 0) {
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        int[] screen = screenSize(minecraft);
        double[] at = this.picked.box(minecraft, screen[0], screen[1]);
        HudPlacementLayout.PreciseDragResult moved =
                HudPlacementLayout.constrainDrag(at[0] + dx, at[1] + dy,
                        (int)Math.round(at[2]), (int)Math.round(at[3]),
                        screen[0], screen[1], 0);
        this.picked.moveTo(minecraft, moved.x, moved.y, screen[0], screen[1]);
        this.nudged = true;
        return true;
    }

    /** What the arrows moved is written down once the keys go elsewhere. */
    @Override
    public void focusChanged(boolean hasKeys) {
        if (!hasKeys) {
            writeNudge();
        }
    }

    @Override
    public void hidden() {
        writeNudge();
        this.carrying = false;
    }

    private void writeNudge() {
        if (this.nudged && this.picked != null) {
            this.picked.persist();
        }
        this.nudged = false;
    }

    /* ---- The bar ---- */

    /**
     * The box picked and where it stands, as shares of the screen, and
     * Default Place, which puts it back; greyed while nothing is picked or
     * it stands there already.
     */
    @Override
    public List<BarItem> barItems() {
        List<BarItem> items = new ArrayList<BarItem>(2);
        BarItem putBack = BarItem.button(BAR_PUT_BACK,
                StatCollector.translateToLocal(LANG + "put_back"),
                LostTalesUiSheet.FORWARD, LostTalesUiSheet.FORWARD_HOVER)
                .tip(StatCollector.translateToLocal(LANG + "put_back.tip"));
        if (this.picked == null) {
            items.add(putBack.unavailable(StatCollector.translateToLocal(
                    LANG + "nothing_picked")));
            items.add(BarItem.words(StatCollector.translateToLocal(
                    LANG + "pick")));
            return items;
        }
        items.add(this.picked.atDefaultPlace()
                ? putBack.unavailable(StatCollector.translateToLocalFormatted(
                        LANG + "at_default", this.picked.title()))
                : putBack);
        double[] offsets = this.picked.offsets();
        items.add(BarItem.words(offsets == null
                ? StatCollector.translateToLocalFormatted(LANG + "where_default",
                        this.picked.title())
                : StatCollector.translateToLocalFormatted(LANG + "where",
                        this.picked.title(), percent(offsets[0]),
                        percent(offsets[1]))));
        return items;
    }

    private static String percent(double share) {
        return String.format(Locale.ROOT, "%.0f%%", share);
    }

    @Override
    public void barPressed(String id, int offer) {
        if (BAR_PUT_BACK.equals(id) && this.picked != null) {
            this.picked.putBack();
        }
    }

    /* ---- The window's strip ---- */

    /** The settings pages' grey. */
    @Override
    public int tone() {
        return LostTalesColors.rgb(LostTalesColors.CONSOLE_TONE);
    }

    @Override
    public ToolStrip.Panel panel() {
        return PREVIEW_PANEL;
    }

    @Override
    public boolean isPanelOut() {
        return this.previewOut;
    }

    @Override
    public void togglePanel() {
        this.previewOut = !this.previewOut;
    }

    @Override
    public void resetPanel() {
        this.previewOut = true;
    }

    /**
     * The page's options: the HUD as a whole, as the HUD key switches it,
     * and a switch for each panel that has one; then Panel Layout, the
     * layouts that put every panel in its place at once.
     */
    @Override
    public List<PageOption> options() {
        List<PageOption> options = new ArrayList<PageOption>(6);
        options.add(PageOption.toggle(OPTION_PANEL_PREFIX + "hud",
                StatCollector.translateToLocal(LANG + "hud"),
                LostTalesConfig.showLostTalesHud, HUD_GLYPH)
                .inGroup("hud", ""));
        options.add(panelSwitch("compass", HudPanel.COMPASS,
                LostTalesConfig.showCompassHud, COMPASS_GLYPH));
        options.add(panelSwitch("fellowship", HudPanel.FELLOWSHIP,
                LostTalesConfig.showFellowshipHud, FELLOWSHIP_GLYPH));
        options.add(panelSwitch("quick_loot", HudPanel.QUICK_LOOT,
                LostTalesConfig.showQuickLootHud, LOOT_GLYPH));
        options.add(panelSwitch("quest", HudPanel.QUEST_TRACKER,
                LostTalesConfig.showQuestHud, QUEST_GLYPH));
        String chosen = LostTalesConfig.normalizeHudPreset(
                LostTalesConfig.hudPlacementPreset);
        List<PageOption> words = new ArrayList<PageOption>(LAYOUTS.length);
        for (String layout : LAYOUTS) {
            words.add(PageOption.choice(OPTION_LAYOUT_PREFIX + layout,
                    layoutWord(layout), layout.equals(chosen), LAYOUT_GLYPH)
                    .explained(StatCollector.translateToLocal(
                            LANG + "layout." + layout)));
        }
        options.add(PageOption.pick(OPTION_LAYOUT,
                StatCollector.translateToLocal(LANG + "layout"),
                layoutWord(chosen), LAYOUT_GLYPH, words)
                .inGroup("layout", ""));
        return options;
    }

    private static PageOption panelSwitch(String id, HudPanel panel,
                                          boolean on, OptionGlyph glyph) {
        return PageOption.toggle(OPTION_PANEL_PREFIX + id, panel.title(), on,
                glyph).inGroup("panels", "");
    }

    private static String layoutWord(String layout) {
        return StatCollector.translateToLocal(
                "gui.losttales.settings.hud.preset." + layout);
    }

    /** A panel switched on or off, or a layout picked; both stay, for another try. */
    @Override
    public boolean takeOption(String id) {
        if (id.startsWith(OPTION_LAYOUT_PREFIX)) {
            LostTalesConfig.applyHudPreset(id.substring(
                    OPTION_LAYOUT_PREFIX.length()));
            return true;
        }
        String panel = id.startsWith(OPTION_PANEL_PREFIX)
                ? id.substring(OPTION_PANEL_PREFIX.length()) : "";
        if ("hud".equals(panel)) {
            // The panels switched with the HUD follow it.
            LostTalesConfig.setShowLostTalesHud(!LostTalesConfig.showLostTalesHud);
        } else if ("compass".equals(panel)) {
            LostTalesConfig.showCompassHud = !LostTalesConfig.showCompassHud;
        } else if ("fellowship".equals(panel)) {
            LostTalesConfig.showFellowshipHud = !LostTalesConfig.showFellowshipHud;
        } else if ("quick_loot".equals(panel)) {
            LostTalesConfig.showQuickLootHud = !LostTalesConfig.showQuickLootHud;
        } else if ("quest".equals(panel)) {
            LostTalesConfig.showQuestHud = !LostTalesConfig.showQuestHud;
        } else {
            return true;
        }
        LostTalesConfig.save();
        return true;
    }

    /** Its cog opens HUD Settings: the panels' places and the compass's own. */
    @Override
    public Settings.Place settingsPlace() {
        return Settings.Place.HUD;
    }

    /** The keys the page answers to, for its help. */
    @Override
    public List<PageKeys.Area> keyAreas() {
        return PageKeys.pageArea("gui.losttales.page.hud_placement",
                PageKeys.pageKey(PAGE_ID, "move", PageKeys.DRAG),
                PageKeys.pageKey(PAGE_ID, "pick", PageKeys.CLICK),
                PageKeys.pageKey(PAGE_ID, "nudge", Keyboard.KEY_LEFT,
                        PageKeys.OR, Keyboard.KEY_RIGHT, PageKeys.OR,
                        Keyboard.KEY_UP, PageKeys.OR, Keyboard.KEY_DOWN),
                PageKeys.pageKey(PAGE_ID, "nudge_ten",
                        Keyboard.KEY_LSHIFT, PageKeys.PLUS, Keyboard.KEY_LEFT));
    }

    /** The arrows are the page's while a box is picked. */
    @Override
    public boolean holdsKeys() {
        return this.carrying;
    }
}
