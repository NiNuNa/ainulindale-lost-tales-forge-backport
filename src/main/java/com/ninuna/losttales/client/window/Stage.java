package com.ninuna.losttales.client.window;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.camera.InspectionCameraMath;
import com.ninuna.losttales.client.camera.StageLayout;
import com.ninuna.losttales.client.camera.StageLight;
import com.ninuna.losttales.client.camera.StagePose;
import com.ninuna.losttales.client.camera.StageShot;
import com.ninuna.losttales.client.camera.ThirdPersonCameraInspection;
import com.ninuna.losttales.character.sync.CharacterGear;
import com.ninuna.losttales.client.character.CharacterGearPreview;
import com.ninuna.losttales.client.character.CharacterGuiPreviewLayout;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterGearCache;
import com.ninuna.losttales.client.render.player.LostTalesCharacterFigureRenderer;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiCutName;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The stage (WIN-50, CHAR-42): while the view shown is one its view file
 * calls a stage, the Profile view, the camera turns round to face the
 * player's character in the widest part of the screen its windows leave
 * free. The page in front of that view ({@link StageSubject}) dresses the
 * character in the look it shows and the armour and held item with it,
 * gives the camera its shot and the nameplate its words. A drag on the
 * bare world turns the camera, a drag with the right button slides it,
 * the wheel brings it nearer, a double-click puts it back, and a page's
 * light key lights the character. Where the camera's seams are not
 * patched, the character is drawn on the free part instead, posed from
 * its look, with no armour and nothing in hand.
 *
 * <p>Only presentation is touched: the look and the gear worn are a
 * preview the world never sees, the inventory is the player's own again
 * after each draw ({@link CharacterGearPreview}), and the player's own
 * rotation and place stay as they were.</p>
 */
public final class Stage extends ScreenPart {
    /** The longest gap between the two presses of a double-click: the desktop's half second. */
    private static final long DOUBLE_CLICK_NANOS = 500L * 1000000L;
    /** A biped is two blocks tall, which is what the stage fits. */
    private static final float FIGURE_HEIGHT_BLOCKS = 2.0F;
    /** Where the drawn figure's head is, in blocks above its feet. */
    private static final float HEAD_HEIGHT_BLOCKS = 1.6F;

    private static final ScreenPart.Maker MAKER = new ScreenPart.Maker() {
        @Override
        public ScreenPart make(WindowScreen screen) {
            return new Stage(screen);
        }
    };

    private final Minecraft mc = Minecraft.getMinecraft();
    private final StagePose pose = new StagePose();
    /** Whether the stage stands: a stage view shown, in a world. */
    private boolean active;
    /** Whether the camera is borrowed; false where its seams are not patched. */
    private boolean camera;
    private StageLayout layout;
    /** The look worn as a preview, as last handed to the cache; null while none is. */
    private CharacterAppearance shownPreview;
    /**
     * Whether the camera is being turned or slid. A screen hears the mouse
     * once a game tick, a stutter on a camera, so while it is held the
     * pointer is read from the window every frame, in window pixels.
     */
    private boolean turning;
    private boolean sliding;
    private int heldX;
    private int heldY;
    private long lastPressNanos;

    private Stage(WindowScreen screen) {
        super(screen);
    }

    /** Gives every window screen a stage, before the chat's part, so a wheel on the bare world reaches the stage first. */
    public static void install() {
        WindowScreen.addPart(MAKER);
    }

    /** The stage of the window screen open now; null while none stands. */
    private static Stage shown() {
        WindowScreen screen = WindowScreen.current();
        Stage stage = screen == null ? null : screen.part(Stage.class);
        return stage != null && stage.active ? stage : null;
    }

    /** Whether a stage stands now: the HUD steps aside for it whatever the windows' setting says. */
    public static boolean isShown() {
        return shown() != null;
    }

    /**
     * What the stage answers to, as a stage view's pages list it in their
     * help: a drag on the world turns the camera, a right-drag slides the
     * character, the wheel brings it nearer, a double-click puts it back.
     */
    public static PageKeys.Area keyArea() {
        return PageKeys.area("gui.losttales.help.area.camera",
                PageKeys.shortcut("gui.losttales.help.keys.camera.turn",
                        PageKeys.DRAG),
                PageKeys.shortcut("gui.losttales.help.keys.camera.slide",
                        PageKeys.RIGHT_DRAG),
                PageKeys.shortcut("gui.losttales.help.keys.camera.zoom",
                        PageKeys.WHEEL),
                PageKeys.shortcut("gui.losttales.help.keys.camera.reset",
                        PageKeys.DOUBLE_CLICK));
    }

    /** A page's light key: the character on the stage is lit, or no longer. */
    public static void toggleLight() {
        Stage stage = shown();
        if (stage != null && stage.camera) {
            StageLight.toggle(stage.screen);
        }
    }

    /* ---- Standing up and down ---- */

    @Override
    public void beginFrame() {
        boolean wanted = this.mc.theWorld != null && this.mc.thePlayer != null
                && ViewDefaults.isStage(WindowView.current());
        if (wanted && !this.active) {
            start();
        } else if (!wanted && this.active) {
            stop();
        }
    }

    @Override
    public void closed() {
        if (this.active) {
            stop();
        }
    }

    private void start() {
        this.active = true;
        this.pose.reset();
        this.camera = ThirdPersonCameraInspection.isAvailable();
        if (this.camera) {
            ThirdPersonCameraInspection.begin(this.screen, this.mc);
            StageLight.bind(this.screen);
        }
    }

    private void stop() {
        this.active = false;
        this.turning = false;
        this.sliding = false;
        StageLight.unbind(this.screen);
        ThirdPersonCameraInspection.end(this.screen, this.mc);
        CharacterGearPreview.clear(this.screen);
        if (this.mc.thePlayer != null) {
            ClientCharacterAppearanceCache.clearPreview(
                    this.mc.thePlayer.getUniqueID());
        }
        this.shownPreview = null;
    }

    /* ---- A frame ---- */

    @Override
    public void beforeWindows(double pointerX, double pointerY) {
        if (!this.active || this.mc.thePlayer == null) {
            return;
        }
        followHand();
        this.pose.advance(System.nanoTime());
        this.layout = StageLayout.of(this.screen.width, this.screen.height,
                coveredSpans());
        StageSubject subject = subject();
        StageShot shot = subject == null ? null : subject.stageShot();
        this.pose.setShot(shot == null ? StageShot.FULL_FRONT : shot);
        UUID player = this.mc.thePlayer.getUniqueID();
        CharacterAppearance look = subject == null ? null
                : subject.stageLook(player);
        if (this.camera) {
            frameCamera();
            wear(player, look);
            dress(look == null ? StageSubject.Gear.OWN : subject.stageGear());
        } else {
            drawFigure(player, look, pointerX, pointerY);
        }
        if (subject != null) {
            drawNameplate(subject.stageName(), subject.stageFacts());
        }
    }

    /** The stretches across the screen the drawn windows cover. */
    private static List<int[]> coveredSpans() {
        List<int[]> spans = new ArrayList<int[]>();
        for (WindowFrame frame : WindowFrame.frames()) {
            if (frame.drawn) {
                LostTalesUiHitBox box = frame.drawnBox();
                spans.add(new int[] {(int)Math.floor(box.left),
                        (int)Math.ceil(box.right())});
            }
        }
        return spans;
    }

    /** The page in front of the window in front of the stage's view, while it shows itself on the stage. */
    private static StageSubject subject() {
        View view = WindowView.current();
        List<Window> stacked = WindowLayout.stacked();
        for (int index = stacked.size() - 1; index >= 0; index--) {
            Window window = stacked.get(index);
            WindowFrame frame = WindowFrame.find(window.getId());
            if (frame == null || !frame.drawn
                    || WindowLayout.viewOf(window) != view) {
                continue;
            }
            WindowPage front = WindowFrame.activeTab(window,
                    WindowFrame.visibleTabs(window));
            PageContent content = WindowPages.contentOf(front);
            return content instanceof StageSubject ? (StageSubject)content
                    : null;
        }
        return null;
    }

    /**
     * Tells the borrowed camera where the character sits this frame: its
     * middle at the stage's, filling the stage's share of the screen,
     * nearer or farther by the zoom, slid by the pan.
     */
    private void frameCamera() {
        double centreX = this.layout.getFigureCenterX() + this.pose.getShownPanX();
        double centreY = this.layout.getFigureMiddleY() + this.pose.getShownPanY();
        ThirdPersonCameraInspection.frame(this.screen, this.pose.getShownYaw(),
                this.pose.getShownPitch(), this.pose.getShownZoom(),
                InspectionCameraMath.screenX(centreX, this.screen.width),
                InspectionCameraMath.screenY(centreY, this.screen.height),
                this.layout.getFigureFitHeight()
                        / (double)Math.max(1, this.screen.height),
                this.pose.getShownFocus());
    }

    /**
     * Dresses the player in the look as a preview, or in their own. Set
     * only when the look changed: the renderer builds the body anew for
     * every look it is handed.
     */
    private void wear(UUID player, CharacterAppearance look) {
        if (look == null) {
            if (this.shownPreview != null) {
                ClientCharacterAppearanceCache.clearPreview(player);
                this.shownPreview = null;
            }
        } else if (this.shownPreview == null || !this.shownPreview.sameAs(look)) {
            ClientCharacterAppearanceCache.setPreview(look);
            this.shownPreview = look;
        }
    }

    /**
     * Dresses the player for drawing in the armour and held item the page
     * shows: their own, none, or a character's of theirs from its saved
     * life, bare until the server has said what that is.
     */
    private void dress(StageSubject.Gear gear) {
        if (gear == null || gear.isOwn()) {
            CharacterGearPreview.clear(this.screen);
            return;
        }
        CharacterGear worn = gear.getCharacterId() == null ? null
                : ClientCharacterGearCache.want(gear.getCharacterId());
        CharacterGearPreview.set(this.screen,
                worn == null ? CharacterGear.NONE : worn);
    }

    /**
     * Without the camera the character stands drawn on the free part,
     * posed from the look or its own, turned by the same drag, its head
     * following the pointer.
     */
    private void drawFigure(UUID player, CharacterAppearance look,
                            double pointerX, double pointerY) {
        CharacterAppearance appearance = look != null ? look
                : ClientCharacterAppearanceCache.getAuthoritative(player);
        if (appearance == null) {
            return;
        }
        float scale = this.layout.getFigureFitHeight() / FIGURE_HEIGHT_BLOCKS
                * this.pose.getShownZoom()
                * CharacterGuiPreviewLayout.scaleOf(appearance.getRaceId());
        float centreX = this.layout.getFigureCenterX() + this.pose.getShownPanX();
        float feetY = this.layout.getFigureMiddleY() + this.pose.getShownPanY()
                + this.pose.getShownFocus() * FIGURE_HEIGHT_BLOCKS * scale;
        drawFloor(centreX, feetY, scale);
        float headY = feetY - HEAD_HEIGHT_BLOCKS * scale;
        float headYaw = Double.isNaN(pointerX) ? 0.0F
                : -this.pose.headYawToward((float)pointerX - centreX);
        float headPitch = Double.isNaN(pointerY) ? 0.0F
                : this.pose.headPitchToward((float)pointerY - headY);
        // The figure sorts its own faces from a clear depth and leaves
        // none behind for what is drawn over it.
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        try {
            LostTalesCharacterFigureRenderer.drawPosedFigure(this.mc, player,
                    appearance, centreX, feetY, scale,
                    this.pose.getShownYaw(), this.pose.getShownPitch(),
                    headYaw, headPitch, 1.0F, 1.0F);
        } finally {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /** A soft pool of light where the drawn figure stands, so it is not afloat. */
    private static void drawFloor(float centreX, float feetY, float scale) {
        float halfWidth = Math.max(24.0F, scale * 1.4F);
        float left = centreX - halfWidth;
        float right = centreX + halfWidth;
        int color = LostTalesColors.SAND;
        float red = LostTalesColors.redF(color);
        float green = LostTalesColors.greenF(color);
        float blue = LostTalesColors.blueF(color);
        float peak = 0.30F;
        Tessellator tessellator = LostTalesUiInk.beginQuads(true);
        // Two quads across, meeting under the feet, so the band fades in
        // from either end; then the same again below, fading downward.
        floorQuad(tessellator, left, centreX, feetY - 1.0F, feetY + 1.5F,
                red, green, blue, 0.0F, peak, 0.0F, peak);
        floorQuad(tessellator, centreX, right, feetY - 1.0F, feetY + 1.5F,
                red, green, blue, peak, 0.0F, peak, 0.0F);
        floorQuad(tessellator, left, centreX, feetY + 1.5F, feetY + 9.0F,
                red, green, blue, 0.0F, peak * 0.5F, 0.0F, 0.0F);
        floorQuad(tessellator, centreX, right, feetY + 1.5F, feetY + 9.0F,
                red, green, blue, peak * 0.5F, 0.0F, 0.0F, 0.0F);
        LostTalesUiInk.endQuads(tessellator, true);
    }

    /** One quad with its own alpha at each corner: top-left, top-right, bottom-left, bottom-right. */
    private static void floorQuad(Tessellator tessellator, float left,
                                  float right, float top, float bottom,
                                  float red, float green, float blue,
                                  float topLeft, float topRight,
                                  float bottomLeft, float bottomRight) {
        tessellator.setColorRGBA_F(red, green, blue, topRight);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.setColorRGBA_F(red, green, blue, topLeft);
        tessellator.addVertex(left, top, 0.0D);
        tessellator.setColorRGBA_F(red, green, blue, bottomLeft);
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.setColorRGBA_F(red, green, blue, bottomRight);
        tessellator.addVertex(right, bottom, 0.0D);
    }

    /** Who stands on the stage, along its foot: the name on the left, the facts on the right. */
    private void drawNameplate(String name, String facts) {
        if (name == null || name.length() == 0) {
            return;
        }
        FontRenderer font = this.mc.fontRenderer;
        String shownFacts = facts == null ? "" : facts;
        int y = this.layout.getBottom() - 11;
        int left = this.layout.getLeft() + 6;
        int right = this.layout.getRight() - 6;
        String label = I18n.format("gui.losttales.character.name")
                .toUpperCase(Locale.ENGLISH);
        int labelWidth = font.getStringWidth(label);
        int factsWidth = font.getStringWidth(shownFacts);
        int nameRoom = Math.max(30, right - left - labelWidth - factsWidth - 24);
        int shownName = (int)LostTalesUiCutName.shownWidth(
                font.getStringWidth(name), nameRoom);
        int factsRoom = Math.max(20, right - left - labelWidth - 6 - shownName - 12);
        Gui.drawRect(left, y - 4, right, y - 3, LostTalesColors.BORDER_DIM);
        LostTalesUiInk.beginContent();
        font.drawStringWithShadow(label, left, y, LostTalesColors.TEXT_MUTED);
        // The name and the facts fade into their ends where they are too
        // long for the stage's foot (LOOK-37).
        LostTalesUiCutName.drawShadowed(font, name, left + labelWidth + 6, y,
                nameRoom, LostTalesColors.GOLD);
        if (shownFacts.length() > 0) {
            LostTalesUiCutName.drawShadowed(font, shownFacts,
                    right - (int)LostTalesUiCutName.shownWidth(factsWidth,
                            factsRoom), y, factsRoom,
                    LostTalesColors.TEXT_BRIGHT);
        }
    }

    /* ---- The hand ---- */

    /**
     * A press on the bare world: the left button turns the camera round
     * the character, the right one slides it, both read every frame; two
     * left presses in a moment put the camera back on the page's shot.
     */
    @Override
    public boolean pressControl(WindowHover press, double x, double y,
                                int mouseX, int mouseY, int button) {
        if (!this.active || !press.is(WindowHover.Kind.NONE)
                || (button != 0 && button != 1)) {
            return false;
        }
        if (button == 0) {
            long now = System.nanoTime();
            if (now - this.lastPressNanos <= DOUBLE_CLICK_NANOS) {
                this.lastPressNanos = 0L;
                this.pose.reset();
                return true;
            }
            this.lastPressNanos = now;
        }
        this.turning = button == 0;
        this.sliding = button == 1;
        this.heldX = Mouse.getX();
        this.heldY = Mouse.getY();
        return true;
    }

    /** The wheel over the bare world brings the character nearer or takes it away, a notch a turn. */
    @Override
    public boolean wheel(WindowHover under, double x, double y, int lines) {
        if (!this.active || !under.is(WindowHover.Kind.NONE) || lines == 0) {
            return false;
        }
        this.pose.wheel(lines > 0 ? 1 : -1);
        return true;
    }

    /**
     * Follows the held button from the window every frame. Window pixels
     * come to interface pixels by the GUI scale, and the window's y runs
     * upward where the screen's runs down.
     */
    private void followHand() {
        if (!this.turning && !this.sliding) {
            return;
        }
        if (!Mouse.isButtonDown(this.turning ? 0 : 1)) {
            this.turning = false;
            this.sliding = false;
            return;
        }
        int x = Mouse.getX();
        int y = Mouse.getY();
        float toInterface = this.mc.displayWidth <= 0 ? 1.0F
                : this.screen.width / (float)this.mc.displayWidth;
        float deltaX = (x - this.heldX) * toInterface;
        float deltaY = -(y - this.heldY) * toInterface;
        if (this.turning) {
            this.pose.drag(deltaX, deltaY);
        } else {
            this.pose.pan(deltaX, deltaY);
        }
        this.heldX = x;
        this.heldY = y;
    }
}
