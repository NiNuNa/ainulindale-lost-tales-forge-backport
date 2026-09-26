package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiOrigin;
import com.ninuna.losttales.client.window.BarItem;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.ToolStrip;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiLayerFade;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.List;
import lotr.client.LOTRKeyHandler;
import lotr.common.network.LOTRPacketClientMQEvent;
import lotr.common.network.LOTRPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The map as a page (M1-M4 a): LOTR's map with everything Lost Tales adds
 * to it, drawn in its window's box as if the box were the whole screen.
 * It works there as it does on a screen of its own — the drag, the zoom,
 * the turn and lean, the markers, waypoints, fast travel, legend, compass
 * and prompts — and only stands in a box: its scissors are moved to the
 * box ({@link LostTalesLotrMapLayout#beginWindow}), the pointer and the
 * presses reach it in the box's own pixels, and its keys, WASD and the
 * arrows among them, only while it holds the keys. LOTR's special maps,
 * the conquest grid and the control zones, keep a screen of their own.
 *
 * <p>Its window is every window's shape (U1 a, U3 a): the tool strip's
 * panel button folds the legend, its cog holds the legend's switches and
 * its well is Find Location; the bar holds Current Location, Create
 * Waypoint and an operator's Teleport, then the place under the pointer,
 * the date, and the zoom. The key-hint strip of the map's own screen is
 * not drawn here.</p>
 *
 * <p>The map is made the first time the page is drawn and let go when it
 * leaves the screen, as closing the map screen let it go: it comes back
 * where the player left it ({@link LostTalesMapViewMemory}). Its tab stays
 * in its window when the screen closes, as every tab does, and while the
 * window fades in the map fades with it as one picture (N2 a).</p>
 */
public final class LostTalesMapPage extends PageContent {
    public static final String PAGE_ID = "map";
    /** The pointer handed to the map while it has none: far outside it. */
    private static final int AWAY = -100000;
    /** The legend's button at the strip's left: a marker, lit and resting alike until its own glyph is drawn. */
    private static final ToolStrip.Panel LEGEND_PANEL = new ToolStrip.Panel(
            LostTalesUiSheet.MAP_MARKER, LostTalesUiSheet.MAP_MARKER_HOVER,
            "gui.losttales.map.legend.show", "gui.losttales.map.legend.hide");
    /** The bar's items: their ids, which the page is told when one is pressed. */
    private static final String LOCATION = "location";
    private static final String WAYPOINT = "waypoint";
    private static final String TELEPORT = "teleport";
    private static final String ZOOM_OUT = "zoom_out";
    private static final String ZOOM_IN = "zoom_in";

    private LostTalesLotrMapGui map;
    /** The map fading in with its window as one picture (N2 a). */
    private final LostTalesUiLayerFade fade = new LostTalesUiLayerFade();
    private boolean hasKeys;
    /** The buttons that went down on the map and are still held, one bit each. */
    private int pressedButtons;
    private long pressedMillis;

    /**
     * Whether the map can stand in a window: its full-screen layout and the
     * scissor that follows its box both patched in. Without them the map
     * keeps a screen of its own.
     */
    public static boolean standsInWindow() {
        return LostTalesLotrMapLayout.canStandInWindow()
                && WindowPages.byId(PAGE_ID) != null;
    }

    /**
     * The screen LOTR hands the open map's news to — whether the player is
     * an operator, why a waypoint cannot stand — in place of the screen it
     * would read: the map in a window while the window screen shows it,
     * else the screen itself.
     */
    public static GuiScreen screenOf(Minecraft minecraft) {
        GuiScreen screen = minecraft == null ? null : minecraft.currentScreen;
        if (!(screen instanceof WindowScreen)) {
            return screen;
        }
        WindowPages.Page page = WindowPages.byId(PAGE_ID);
        PageContent content = page == null ? null : page.content();
        LostTalesLotrMapGui shown = content instanceof LostTalesMapPage
                ? ((LostTalesMapPage)content).map : null;
        return shown != null ? shown : screen;
    }

    /** The map's tab waits unseen where the map cannot stand in a window. */
    @Override
    public boolean isAvailable() {
        return standsInWindow();
    }


    /** The map is drawn as on a screen of its own, depth testing on. */
    @Override
    public boolean wantsDepthTest() {
        return true;
    }

    /** A prompt with a field, the waypoint's or Find Location's, keeps every key. */
    @Override
    public boolean holdsKeys() {
        return this.map != null && this.map.hasFieldPrompt();
    }

    /* ---- The window's strip: the legend and Find Location ---- */

    @Override
    public ToolStrip.Panel panel() {
        return LEGEND_PANEL;
    }

    @Override
    public boolean isPanelOut() {
        return this.map != null && this.map.isMapLegendOpen();
    }

    @Override
    public void togglePanel() {
        if (this.map != null) {
            this.map.toggleMapLegend();
        }
    }

    @Override
    public String choicesHeading() {
        return "gui.losttales.map.menu.show";
    }

    /** The legend's switches: which kinds of marker the map shows. */
    @Override
    public List<Choice> choices() {
        List<LostTalesMapLegendCategory> categories =
                LostTalesMapLegendRegistry.getCategories();
        List<Choice> choices = new ArrayList<Choice>(categories.size());
        for (LostTalesMapLegendCategory category : categories) {
            choices.add(new Choice(category.getId(),
                    StatCollector.translateToLocal(
                            category.getTranslationKey()),
                    LostTalesMapLegendRegistry.isCategoryEnabled(
                            category.getId())));
        }
        return choices;
    }

    @Override
    public void choose(String id) {
        LostTalesMapLegendRegistry.toggleCategory(id);
        if (this.map != null) {
            this.map.onMapLegendFiltersChanged();
        }
    }

    @Override
    public String searchPrompt() {
        return StatCollector.translateToLocal("gui.losttales.map.search");
    }

    /** The well's words find places, and the map goes to the first. */
    @Override
    public void search(String words) {
        if (this.map != null) {
            this.map.findPlaces(words);
        }
    }

    @Override
    public int found() {
        return this.map == null ? -1 : this.map.placesFound();
    }

    /** The arrows walk the places found, the map going to each; Return gives the map the keys. */
    @Override
    public boolean searchKey(int keyCode) {
        if (this.map == null) {
            return false;
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            this.map.walkPlaces(keyCode == Keyboard.KEY_UP ? -1 : 1);
            return true;
        }
        return keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER;
    }

    /* ---- The window's bar ---- */

    /**
     * Current Location and Create Waypoint, an operator's Teleport, then
     * the place under the pointer and the date as quiet words, and the
     * zoom's two glyphs at the right end. Close Map is the tab's cross.
     */
    @Override
    public List<BarItem> barItems() {
        List<BarItem> items = new ArrayList<BarItem>(7);
        boolean inMiddleEarth = this.map != null && this.map.isInMiddleEarth();
        String away = StatCollector.translateToLocal(
                "gui.losttales.map.control.why.away");
        String location = StatCollector.translateToLocal(
                "gui.losttales.map.control.location");
        BarItem locationItem = BarItem.button(LOCATION, location,
                new ItemStack(Items.compass)).tip(WindowBar.withKey(location,
                LostTalesLotrMapGui.CURRENT_LOCATION_KEY));
        items.add(inMiddleEarth ? locationItem : locationItem.unavailable(away));
        String waypoint = StatCollector.translateToLocal(
                "gui.losttales.map.control.waypoint.short");
        BarItem waypointItem = BarItem.button(WAYPOINT, waypoint,
                new ItemStack(Items.sign)).tip(WindowBar.withKey(
                StatCollector.translateToLocal(
                        "gui.losttales.map.control.waypoint"),
                LostTalesLotrMapGui.CREATE_WAYPOINT_KEY));
        items.add(inMiddleEarth ? waypointItem : waypointItem.unavailable(away));
        if (this.map != null && this.map.isPlayerOp) {
            String teleport = StatCollector.translateToLocal(
                    "gui.losttales.map.control.teleport");
            items.add(BarItem.button(TELEPORT, teleport,
                    new ItemStack(Items.ender_pearl)).tip(WindowBar.withKey(
                    StatCollector.translateToLocal(
                            "gui.losttales.map.control.teleport.tip"),
                    LOTRKeyHandler.keyBindingMapTeleport.getKeyCode()))
                    .lit(this.map.isTeleportArmed()));
        }
        String cursor = this.map == null ? "" : this.map.cursorWords();
        if (cursor.length() > 0) {
            items.add(BarItem.words(cursor));
        }
        String[] dates = LostTalesLotrMapCalendar.describe();
        if (dates.length > 0) {
            // The date without its weekday where there is one: the bar is
            // shared with the place under the pointer.
            items.add(BarItem.words(dates[Math.min(1, dates.length - 1)]));
        }
        String zoom = StatCollector.translateToLocal(
                "gui.losttales.map.control.zoom_out");
        items.add(BarItem.glyph(ZOOM_OUT, LostTalesUiSheet.MINUS,
                LostTalesUiSheet.MINUS_HOVER, zoom + " ("
                + StatCollector.translateToLocal("gui.losttales.map.control.wheel")
                + ")"));
        String zoomIn = StatCollector.translateToLocal(
                "gui.losttales.map.control.zoom_in");
        items.add(BarItem.glyph(ZOOM_IN, LostTalesUiSheet.PLUS,
                LostTalesUiSheet.PLUS_HOVER, zoomIn + " ("
                + StatCollector.translateToLocal("gui.losttales.map.control.wheel")
                + ")"));
        return items;
    }

    @Override
    public void barPressed(String id, int offer) {
        if (this.map == null) {
            return;
        }
        if (LOCATION.equals(id)) {
            this.map.focusCurrentLocation();
        } else if (WAYPOINT.equals(id)) {
            this.map.openWaypointPrompt();
        } else if (TELEPORT.equals(id)) {
            this.map.toggleTeleport();
        } else if (ZOOM_OUT.equals(id)) {
            this.map.zoomStep(-1);
        } else if (ZOOM_IN.equals(id)) {
            this.map.zoomStep(1);
        }
    }

    /** Whether the map holds the keys: it answers WASD and the arrows only then. */
    boolean hasKeys() {
        return this.hasKeys;
    }

    /** Closes the map's tab, its window with it when the map is all it holds. */
    void close() {
        WindowScreen screen = WindowScreen.current();
        if (screen != null) {
            screen.closeTab(WindowPages.tab(PAGE_ID));
        }
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        LostTalesLotrMapGui gui = mapIn(minecraft, box);
        if (gui == null) {
            return;
        }
        // The map is handed the pointer while it is on the map, and a held
        // button only when it went down on the map: dragged in from
        // elsewhere, it pans nothing.
        boolean pointed = !Double.isNaN(pointerX) && !Double.isNaN(pointerY)
                && (this.pressedButtons != 0 || !Mouse.isButtonDown(0));
        int x = pointed ? local(pointerX, box.left) : AWAY;
        int y = pointed ? local(pointerY, box.top) : AWAY;
        // While its window fades in, the map fades with it as one
        // picture; where that cannot be done it shows at full strength.
        boolean fading = alpha < 255 && this.fade.begin(minecraft, clipX,
                clipY, box.width, box.height);
        LostTalesLotrMapLayout.beginWindow(clipX, clipY, gui.height);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_SCISSOR_BIT
                | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(box.left, box.top, 0.0D);
            // The map's strip, compass, legend and prompts stand in the box.
            LostTalesGuiOrigin.mark();
            gui.drawScreen(x, y, partialTicks);
        } finally {
            LostTalesGuiOrigin.unmark();
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            LostTalesLotrMapLayout.endWindow();
            if (fading) {
                this.fade.end(minecraft, Math.max(0, alpha) / 255.0F);
            }
        }
    }

    /**
     * The map for a box: made and laid out the first time, and afterwards
     * only told its new edges, since it lays itself out from them every
     * frame. Laying it out again would put it back over the player.
     */
    private LostTalesLotrMapGui mapIn(Minecraft minecraft,
                                      LostTalesUiHitBox box) {
        int width = (int)box.width;
        int height = (int)box.height;
        if (minecraft == null || width <= 0 || height <= 0) {
            return null;
        }
        if (this.map == null) {
            this.map = new LostTalesLotrMapGui();
            this.map.embedIn(this);
            this.map.setWorldAndResolution(minecraft, width, height);
        } else if (this.map.width != width || this.map.height != height) {
            this.map.width = width;
            this.map.height = height;
        }
        return this.map;
    }

    private static int local(double at, double origin) {
        return (int)Math.floor(at - origin);
    }

    @Override
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        return this.map != null && this.map.isPointerOverInteractable(
                local(x, box.left), local(y, box.top));
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box,
                                double x, double y, int button) {
        if (this.map == null || button < 0 || button > 15) {
            return false;
        }
        this.pressedButtons |= 1 << button;
        this.pressedMillis = Minecraft.getSystemTime();
        this.map.mouseClicked(local(x, box.left), local(y, box.top), button);
        return true;
    }

    @Override
    public void mouseDragged(Minecraft minecraft, LostTalesUiHitBox box,
                             double x, double y, int button) {
        if (this.map != null) {
            this.map.mouseClickMove(local(x, box.left), local(y, box.top),
                    button, Minecraft.getSystemTime() - this.pressedMillis);
        }
    }

    @Override
    public void mouseReleased(Minecraft minecraft, LostTalesUiHitBox box,
                              double x, double y, int button) {
        if (button >= 0 && button <= 15) {
            this.pressedButtons &= ~(1 << button);
        }
        if (this.map != null) {
            this.map.mouseMovedOrUp(local(x, box.left), local(y, box.top),
                    button);
        }
    }

    /** A notch of the wheel toward the player zooms out, away from them in. */
    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        return this.map != null && lines != 0 && this.map.wheelAt(
                local(x, box.left), local(y, box.top), lines < 0 ? 120 : -120);
    }

    /**
     * Every key the map is handed is the map's, Escape among them, but F:
     * Find Location is the window's well. The screen answers the pages'
     * keys, M's included, before the map.
     */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.map == null) {
            return false;
        }
        WindowScreen screen = WindowScreen.current();
        if (keyCode == LostTalesLotrMapGui.FIND_LOCATION_KEY
                && !this.map.hasFieldPrompt() && screen != null) {
            screen.openSearchFor(WindowPages.tab(PAGE_ID));
            return true;
        }
        this.map.keyTyped(typedChar, keyCode);
        return true;
    }

    @Override
    public void tick() {
        if (this.map != null) {
            this.map.updateScreen();
        }
    }

    @Override
    public void focusChanged(boolean held) {
        this.hasKeys = held;
    }

    /**
     * LOTR counts opening the map toward its quests and tells the server
     * so while the map is the screen; in a window it is told here.
     */
    @Override
    public void shown() {
        try {
            LOTRPacketHandler.networkWrapper.sendToServer(
                    new LOTRPacketClientMQEvent(
                            LOTRPacketClientMQEvent.ClientMQEvent.MAP));
        } catch (RuntimeException | LinkageError changed) {
            // A changed LOTR request costs the quest event, not the map.
            FMLLog.warning("[%s] LOTR refused the map's opened event (%s)",
                    LostTalesMetaData.MOD_ID, changed);
        }
    }

    @Override
    public void hidden() {
        if (this.map != null) {
            this.map.onGuiClosed();
            this.map = null;
        }
        this.fade.release();
        this.pressedButtons = 0;
    }
}
