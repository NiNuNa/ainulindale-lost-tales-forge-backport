package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiLayerFade;
import lotr.common.network.LOTRPacketClientMQEvent;
import lotr.common.network.LOTRPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The map as a page (M1-M4 a): LOTR's map with everything Lost Tales adds
 * to it, drawn in its window's box as if the box were the whole screen.
 * It works there as it does on a screen of its own — the drag, the zoom,
 * the turn and lean, the markers, waypoints, fast travel, search, legend,
 * compass and prompts — and only stands in a box: its scissors are moved
 * to the box ({@link LostTalesLotrMapLayout#beginWindow}), the pointer and
 * the presses reach it in the box's own pixels, and its keys, WASD and the
 * arrows among them, only while it holds the keys. Its window has a tab
 * row and no tool strip. LOTR's special maps, the conquest grid and the
 * control zones, keep a screen of their own.
 *
 * <p>The map is made the first time the page is drawn and let go when it
 * leaves the screen, as closing the map screen let it go: it comes back
 * where the player left it ({@link LostTalesMapViewMemory}). Its window
 * closes with the screen (N3 a), and while the window fades in the map
 * fades with it as one picture (N2 a).</p>
 */
public final class LostTalesMapPage extends PageContent {
    public static final String PAGE_ID = "map";
    /** The pointer handed to the map while it has none: far outside it. */
    private static final int AWAY = -100000;

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

    @Override
    public boolean hasToolStrip() {
        return false;
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

    /**
     * The map hides the world, so it closes with the screen: T opens the
     * chat without it, and M brings it back where it was left (N3 a).
     */
    @Override
    public boolean closesWithScreen() {
        return true;
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
            gui.drawScreen(x, y, partialTicks);
        } finally {
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
     * Every key the map is handed is the map's, Escape among them; the
     * screen answers the pages' keys, M's included, before the map.
     */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.map == null) {
            return false;
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
        } catch (Throwable ignored) {
            // A changed LOTR request costs the quest event, not the map.
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
