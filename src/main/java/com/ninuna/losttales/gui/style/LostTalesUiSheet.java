package com.ninuna.losttales.gui.style;

import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * The chat's own artwork, one sprite sheet: the four picker buttons and
 * the send button, the tab row's controls and their hover states, the
 * window's fullscreen control, the search fields' magnifiers, the tab
 * borders, the framed buttons' corners and a chat window frame's, the
 * window grip, and the hatch
 * laid over empty message rows. Each
 * constant is a cell of {@code textures/gui/chat.png} in
 * texels; the sheet is drawn 1:1 in GUI pixels, so a sprite's width and
 * height are also its size on screen. The padlock's frames are the one
 * thing not held here: they are a regular grid, and
 * {@link ChatLockAnimation} walks it. {@code LostTalesUiSheetTest} locks
 * the constants to the bundled PNG the way the emoji sheet is locked.
 */
public enum LostTalesUiSheet {
    EMOJI(0, 0, 10, 10),
    EMOJI_HOVER(11, 0, 10, 10),
    /** The dagger the item picker's button carries. */
    ITEM(22, 0, 10, 10),
    ITEM_HOVER(32, 0, 10, 10),
    /**
     * The map-marker and quest pickers' buttons: the round the emoji
     * button wears, holding a marker's ring and a quest's mark, each with
     * its lit cell to the right.
     */
    MAP_MARKER(43, 0, 10, 10),
    MAP_MARKER_HOVER(54, 0, 10, 10),
    QUEST(65, 0, 10, 10),
    QUEST_HOVER(76, 0, 10, 10),
    /**
     * The input bar's send button, resting and hovered: ten-pixel cells
     * like the emoji and item buttons', crossing from one to the other
     * as theirs do.
     */
    SEND(87, 0, 10, 10),
    SEND_HOVER(98, 0, 10, 10),
    PLUS(0, 11, 5, 5),
    PLUS_HOVER(6, 11, 5, 5),
    /**
     * The {@code +} with its upright stroke taken away: what the restore
     * control wears while the list it opens is out. One row of artwork
     * rather than a five-row cell, so it centres on the same row of the
     * strip the {@code +}'s own crossbar stands on.
     */
    MINUS(12, 13, 5, 1),
    MINUS_HOVER(18, 13, 5, 1),
    CLOSE(24, 11, 5, 5),
    CLOSE_HOVER(30, 11, 5, 5),
    COG(36, 11, 5, 5),
    COG_HOVER(42, 11, 5, 5),
    /**
     * The speech bubble, its tail included: the typing line, a reply's
     * quote and a message link wear it, and the character menu's
     * Narrator row crosses to its lit artwork.
     */
    SPEECH_BUBBLE(32, 18, 9, 6),
    SPEECH_BUBBLE_HOVER(42, 18, 9, 6),
    GRIP(0, 17, 6, 8),
    GRIP_HOVER(7, 17, 6, 8),
    /**
     * The window's fullscreen control: four corners pointing out while
     * the window keeps its own size, pointing in while it fills the
     * screen, each with its lit artwork.
     */
    FULLSCREEN(75, 26, 5, 5),
    FULLSCREEN_HOVER(81, 26, 5, 5),
    FULLSCREEN_EXIT(63, 26, 5, 5),
    FULLSCREEN_EXIT_HOVER(69, 26, 5, 5),
    /**
     * The pen a tab wears while something is written in its input and
     * not yet sent, resting and lit: the draft mark, beside the
     * fullscreen controls on the sheet.
     */
    DRAFT(87, 26, 4, 5),
    DRAFT_HOVER(92, 26, 4, 5),
    /**
     * A question mark and an exclamation mark, each in the chat's ivory
     * and in a colour of its own — the question green, the exclamation
     * crimson. Nothing draws them yet; they are here so the sheet and
     * these constants stay one description of the artwork.
     */
    QUESTION(97, 26, 3, 5),
    QUESTION_LIT(101, 26, 3, 5),
    EXCLAMATION(102, 11, 1, 5),
    EXCLAMATION_LIT(104, 11, 1, 5),
    /**
     * The mark a head wears for the presence of the identity it shows,
     * at the head's bottom-right corner in a notch cut out of the head
     * ({@link com.ninuna.losttales.client.chat.ChatPresenceMark}), each
     * status a shape as well as a colour: the green sphere for Online, a
     * honey crescent for Away, a crimson disc barred across for Do Not
     * Disturb, and a muted ring for Offline, which is what everyone else
     * sees of Invisible; the hollows are painted in, shaded plum at two
     * thirds. The ivory sphere is not a presence: it is the lit look a
     * status row's mark crosses to under the pointer in the head
     * button's menu.
     */
    PRESENCE_SELECTED(17, 26, 5, 5),
    PRESENCE_OFFLINE(23, 26, 5, 5),
    PRESENCE_ONLINE(29, 26, 5, 5),
    PRESENCE_AWAY(35, 26, 5, 5),
    PRESENCE_BUSY(41, 26, 5, 5),
    /**
     * The member list's button: two people, one standing before the
     * other, with its lit cell to the right.
     */
    MEMBERS(47, 26, 7, 5),
    MEMBERS_HOVER(55, 26, 7, 5),
    /**
     * A hovered message's toolbar controls, each with its lit artwork:
     * copy, a page with its corner turned down, and reply, an arrow
     * curling back to the left.
     */
    COPY(45, 32, 4, 5),
    COPY_HOVER(50, 32, 4, 5),
    REPLY(55, 32, 4, 5),
    REPLY_HOVER(60, 32, 4, 5),
    /**
     * An arrow curling on to the right, with its lit artwork: forwarding a
     * message to another conversation. Nothing draws it yet, as there is
     * no forwarding; it is here so the sheet and these constants stay one
     * description of the artwork.
     */
    FORWARD(65, 32, 4, 5),
    FORWARD_HOVER(70, 32, 4, 5),
    /**
     * The message toolbar's menu button, three dots in a row, with its
     * lit artwork beneath it on the sheet: it opens the message's own
     * menu, the one a right click opens.
     */
    MORE(36, 32, 8, 2),
    MORE_HOVER(36, 35, 8, 2),
    /**
     * A tab's count of unread mentions, as a messenger's mention badge
     * is: a crimson tile as tall as the capitals, built from its left
     * edge and one figure after it — one to nine in ivory, or the plus
     * past nine ({@link #drawJoinedWithShadow}).
     */
    COUNT_LEFT(52, 18, 1, 7),
    COUNT_MORE(54, 18, 4, 7),
    COUNT_1(59, 18, 4, 7),
    COUNT_2(64, 18, 4, 7),
    COUNT_3(69, 18, 4, 7),
    COUNT_4(74, 18, 4, 7),
    COUNT_5(79, 18, 4, 7),
    COUNT_6(84, 18, 4, 7),
    COUNT_7(89, 18, 4, 7),
    COUNT_8(94, 18, 4, 7),
    COUNT_9(99, 18, 4, 7),
    /**
     * The magnifier every search field opens with, and its lit artwork,
     * which the search bar and the pickers cross to while their field
     * takes the keys or the pointer is on it.
     */
    SEARCH(14, 17, 8, 8),
    SEARCH_HOVER(23, 17, 8, 8),
    /**
     * A framed button's four corners, resting and lit: six-texel cells
     * whose innermost column and row are the frame's edges, stretched
     * between the corners to any size ({@link ChatFramedButton}). Like
     * the tab pieces they bring ink alone; their backdrop texels preview
     * the surface the button paints itself.
     */
    FRAME_TOP_LEFT(0, 79, 6, 6),
    FRAME_TOP_RIGHT(7, 79, 6, 6),
    FRAME_BOTTOM_LEFT(0, 86, 6, 6),
    FRAME_BOTTOM_RIGHT(7, 86, 6, 6),
    FRAME_LIT_TOP_LEFT(14, 79, 6, 6),
    FRAME_LIT_TOP_RIGHT(21, 79, 6, 6),
    FRAME_LIT_BOTTOM_LEFT(14, 86, 6, 6),
    FRAME_LIT_BOTTOM_RIGHT(21, 86, 6, 6),
    /**
     * A chat window's frame, its corners and, stretched as a framed
     * button's are, its edges: the lit framed button's ink texel for
     * texel, previewing the window's plum black behind it where a
     * button's cells preview their plum grey.
     */
    WINDOW_FRAME_TOP_LEFT(28, 79, 6, 6),
    WINDOW_FRAME_TOP_RIGHT(35, 79, 6, 6),
    WINDOW_FRAME_BOTTOM_LEFT(28, 86, 6, 6),
    WINDOW_FRAME_BOTTOM_RIGHT(35, 86, 6, 6),
    /** The favourite heart: plain, and filled in the palette's wine. */
    HEART(48, 11, 5, 5),
    HEART_FAVORITE(54, 11, 5, 5),
    /**
     * The insert-toolbar chevron's animation, five frames from pointing
     * right (the inserts are out and fold back toward it) to pointing
     * left (they are away and open leftward), each cell exactly its own
     * artwork so a frame centres on the control however wide it is.
     */
    TOGGLE_1(60, 11, 3, 5),
    TOGGLE_2(64, 11, 2, 5),
    TOGGLE_3(67, 11, 1, 5),
    TOGGLE_4(69, 11, 2, 5),
    TOGGLE_5(72, 11, 3, 5),
    TOGGLE_1_HOVER(76, 11, 3, 5),
    TOGGLE_2_HOVER(80, 11, 2, 5),
    TOGGLE_3_HOVER(83, 11, 1, 5),
    TOGGLE_4_HOVER(85, 11, 2, 5),
    TOGGLE_5_HOVER(88, 11, 3, 5),
    /**
     * The timestamp area's button: one person, for the heads the area
     * holds, with its lit cell to the right.
     */
    AREA(92, 11, 4, 5),
    AREA_HOVER(97, 11, 4, 5),
    /**
     * A tab's two border pieces. Each carries the corner it turns at the
     * top, the line that runs down the tab's side, and the tab's own
     * interior tone behind them, so a tab is these two with a line
     * between their tips and that same tone filling the span. The
     * selected pair is one row taller than the resting one and a texel
     * wider: each piece ends in a foot on its last row, which stands on
     * the strip's rule, reaching one texel out past the tab's side.
     */
    TAB_LEFT(0, 59, 4, 19),
    TAB_RIGHT(5, 59, 4, 19),
    TAB_HOVER_LEFT(10, 59, 4, 19),
    TAB_HOVER_RIGHT(15, 59, 4, 19),
    TAB_SELECTED_LEFT(20, 58, 5, 20),
    TAB_SELECTED_RIGHT(26, 58, 5, 20),
    /**
     * The selected pair as the hand lifts it off the row: a row taller
     * at the top, its feet on the same row, its contours warmed through
     * honey and apricot to coral at the feet. The extra row is one more
     * of the plain side line both pairs run down, so a tab rising
     * stretches along that run from the one shape to the other and its
     * feet never leave the rule.
     */
    TAB_LIFTED_LEFT(32, 57, 5, 21),
    TAB_LIFTED_RIGHT(38, 57, 5, 21),
    /**
     * The hatch laid over message rows the history does not reach: a
     * 45° line every eight texels. The pattern's period divides the
     * cell in both directions, so whole cells meet seamlessly wherever
     * the region is tiled with them.
     */
    EMPTY_HATCH(0, 26, 16, 16),
    /**
     * The chevron a control opens a list below itself with, animated:
     * five frames from pointing down (the list is away and opens
     * downward) through a flat rule to pointing up (the list is out and
     * folds back up toward it). The vertical counterpart of the
     * insert-toolbar chevron above, laid out the same way — each cell
     * exactly its own artwork, so a frame centres on the control
     * however tall it is.
     *
     * <p>Three colourways of the same run: ivory, lit, and the muted
     * tones the grip rests in. The search bar's chevrons and the jump
     * button rest in ivory and light to the lit run; the tab search
     * button rests in the muted run and lights to ivory.</p>
     */
    CHEVRON_1(0, 43, 5, 3),
    CHEVRON_2(0, 47, 5, 2),
    CHEVRON_3(0, 50, 5, 1),
    CHEVRON_4(0, 52, 5, 2),
    CHEVRON_5(0, 55, 5, 3),
    CHEVRON_1_HOVER(6, 43, 5, 3),
    CHEVRON_2_HOVER(6, 47, 5, 2),
    CHEVRON_3_HOVER(6, 50, 5, 1),
    CHEVRON_4_HOVER(6, 52, 5, 2),
    CHEVRON_5_HOVER(6, 55, 5, 3),
    CHEVRON_1_MUTED(12, 43, 5, 3),
    CHEVRON_2_MUTED(12, 47, 5, 2),
    CHEVRON_3_MUTED(12, 50, 5, 1),
    CHEVRON_4_MUTED(12, 52, 5, 2),
    CHEVRON_5_MUTED(12, 55, 5, 3);

    /**
     * What a frame or tab piece must clear to be drawn: the artwork
     * previews the surface behind its ink at a low alpha, and only the
     * ink itself is wanted. A property of the sheet, so everything drawn
     * from it cuts at the same place.
     */
    public static final float INK_THRESHOLD = 0.8F;
    public static final String TEXTURE_PATH = "textures/gui/chat.png";
    public static final int SHEET_WIDTH = 108;
    public static final int SHEET_HEIGHT = 92;
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("losttales", TEXTURE_PATH);

    private final int u;
    private final int v;
    private final int width;
    private final int height;

    LostTalesUiSheet(int u, int v, int width, int height) {
        this.u = u;
        this.v = v;
        this.width = width;
        this.height = height;
    }

    public int getTextureU() { return this.u; }
    public int getTextureV() { return this.v; }
    public int getWidth() { return this.width; }
    public int getHeight() { return this.height; }

    /** The sprite at its own size, with the chat's shadow under it. */
    public void drawWithShadow(float x, float y, int alpha) {
        drawWithShadow(this.u, this.v, this.width, this.height, x, y, alpha);
    }

    /**
     * The sprite as a flat silhouette in one colour and nothing else:
     * what a two-pass renderer draws twice, once in the shadow tone and
     * once in the run's colour.
     */
    public void drawSilhouette(int rgb, float x, float y, int alpha) {
        LostTalesSilhouetteRenderState.begin(rgb);
        try {
            draw(x, y, alpha);
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
    }

    /**
     * A control that has both a resting and a hovered artwork, drawn a
     * share of the way from one to the other: the hovered artwork laid
     * over the resting one as far as it has come, the resting ink whole
     * under it while the resting hollows give way to the hovered ones
     * ({@link #drawSplit}), so a lit control shows exactly its lit
     * artwork's hollows and never the two stacked. One shadow, from the
     * resting sprite, since the two are the same shape and a second would
     * darken it twice; the picture is flat, so the hollows never show it.
     */
    public static void drawPairWithShadow(final LostTalesUiSheet resting,
                                          final LostTalesUiSheet hovered,
                                          final float progress, final float x,
                                          final float y, final int alpha) {
        drawFlatWithShadow(x, y, resting.width, resting.height,
                new LostTalesUiFlatLayers.Layers() {
                    @Override
                    public void draw() {
                        drawShadow(resting.u, resting.v, resting.width,
                                resting.height, x, y, alpha);
                        LostTalesUiFlatLayers.nextLayer();
                        int over = Math.round(alpha * Math.max(0.0F,
                                Math.min(1.0F, progress)));
                        if (over < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
                            LostTalesUiSheet.draw(resting.u, resting.v,
                                    resting.width, resting.height, x, y,
                                    alpha);
                            return;
                        }
                        drawSplit(resting.u, resting.v, resting.width,
                                resting.height, x, y, alpha, alpha - over);
                        LostTalesUiSheet.draw(hovered.u, hovered.v,
                                hovered.width, hovered.height, x, y, over);
                    }
                });
    }

    /**
     * Any cell of the sheet, with the chat's shadow under it. The
     * padlock's frames are addressed this way: they are a grid rather
     * than named cells, so they carry their own coordinates.
     */
    public static void drawWithShadow(final int u, final int v,
                                      final int width, final int height,
                                      final float x, final float y,
                                      final int alpha) {
        drawFlatWithShadow(x, y, width, height,
                new LostTalesUiFlatLayers.Layers() {
                    @Override
                    public void draw() {
                        drawShadow(u, v, width, height, x, y, alpha);
                        LostTalesUiFlatLayers.nextLayer();
                        LostTalesUiSheet.draw(u, v, width, height, x, y,
                                alpha);
                    }
                });
    }

    /**
     * A sprite and its shadow as one flat picture: the sprite stands over
     * its shadow, and its painted hollows — the translucent texels behind
     * its ink, two thirds in the artwork — show what lies behind the
     * picture exactly as painted, never the sprite's own shadow.
     */
    private static void drawFlatWithShadow(float x, float y, int width,
                                           int height,
                                           LostTalesUiFlatLayers.Layers layers) {
        LostTalesUiFlatLayers.drawFlat(x, y,
                x + width + LostTalesUiInk.SHADOW_OFFSET,
                y + height + LostTalesUiInk.SHADOW_OFFSET, layers);
    }

    /**
     * Any cell of the sheet with its ink at {@code inkAlpha} and its
     * hollows — the translucent texels painted behind the ink — at
     * {@code hollowAlpha}, no more than the ink: artwork crossing to other
     * artwork laid over it lets its hollows give way to the other's while
     * its ink stays whole under the other's ink.
     */
    public static void drawSplit(int u, int v, int width, int height,
                                 float x, float y, int inkAlpha,
                                 int hollowAlpha) {
        int hollow = Math.max(0, Math.min(inkAlpha, hollowAlpha));
        if (hollow >= LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            draw(u, v, width, height, x, y, hollow);
        } else {
            hollow = 0;
        }
        int ink = inkOver(inkAlpha, hollow);
        if (ink < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        // The ink alone, over itself where the whole cell was laid: the
        // test sees texture and vertex alpha multiplied, so the threshold
        // is scaled by the share the ink is drawn at.
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        try {
            GL11.glAlphaFunc(GL11.GL_GREATER, INK_THRESHOLD * ink / 255.0F);
            draw(u, v, width, height, x, y, ink);
        } finally {
            GL11.glAlphaFunc(alphaFunc, alphaRef);
            if (!alphaTest) {
                GL11.glDisable(GL11.GL_ALPHA_TEST);
            }
        }
    }

    /**
     * The opacity to lay a sprite's ink at over the same ink already laid
     * at {@code under}, so the two together stand at {@code alpha}.
     */
    static int inkOver(int alpha, int under) {
        float whole = Math.max(0, Math.min(255, alpha)) / 255.0F;
        float laid = Math.max(0, Math.min(255, under)) / 255.0F;
        if (laid >= 1.0F) {
            return 0;
        }
        return Math.round(255.0F * Math.max(0.0F,
                1.0F - (1.0F - whole) / (1.0F - laid)));
    }

    /**
     * Two cells side by side, {@code first} from ({@code x}, {@code y})
     * and {@code second} straight after it, their tops level, over one
     * shadow for both as a single sprite casts it: how a count tile is
     * drawn, its left edge and then its figure.
     */
    public static void drawJoinedWithShadow(LostTalesUiSheet first,
                                            LostTalesUiSheet second, float x,
                                            float y, int alpha) {
        float secondX = x + first.width;
        drawShadow(first.u, first.v, first.width, first.height, x, y, alpha);
        drawShadow(second.u, second.v, second.width, second.height, secondX,
                y, alpha);
        // The cells stand over their shadow, so a translucent picture
        // does not show the one through the other.
        LostTalesUiFlatLayers.nextLayer();
        draw(first.u, first.v, first.width, first.height, x, y, alpha);
        draw(second.u, second.v, second.width, second.height, secondX, y,
                alpha);
    }

    /** The shared drop shadow: the same cell, offset, in the shadow tone. */
    private static void drawShadow(int u, int v, int width, int height,
                                   float x, float y, int alpha) {
        int shadowAlpha = LostTalesUiInk.shadowAlpha(alpha);
        if (shadowAlpha <= 0) {
            return;
        }
        LostTalesSilhouetteRenderState.begin(LostTalesUiInk.SHADOW);
        try {
            draw(u, v, width, height,
                    x + LostTalesUiInk.SHADOW_OFFSET,
                    y + LostTalesUiInk.SHADOW_OFFSET, shadowAlpha);
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
    }

    /**
     * Tiles the sprite over a region in the colours it was authored in,
     * at full strength across the region's middle and falling off
     * linearly to nothing at its top and bottom edges, so the fill has
     * no edge of its own to read as a border.
     *
     * <p>Whole cells first, the partial last column and row cut by
     * their UVs, so the pattern never stretches and the region's edge
     * never samples texels beyond the cell. A row is also cut where the
     * ramp turns, so the peak is a vertex rather than a corner
     * interpolated across a cell; the cut carries its place in the cell
     * with it, so the pattern runs on through it unbroken. One texture
     * bind and a handful of quads.</p>
     */
    public void drawTiledFadingFromMiddle(float left, float top, float right,
                                   float bottom, int alpha) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (minecraft == null || right <= left || bottom <= top
                || safeAlpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        minecraft.getTextureManager().bindTexture(TEXTURE);
        LostTalesUiInk.beginContent();
        GL11.glShadeModel(GL11.GL_SMOOTH);
        // The GUI draws under an alpha test that throws away nearly
        // transparent fragments. A ramp ending in nothing is exactly
        // that, so left on it would cut the fade off at a hard edge
        // partway down instead of letting it reach nothing; every other
        // gradient the chat draws turns it off for the same reason.
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        try {
            float middle = (top + bottom) / 2.0F;
            float half = (bottom - top) / 2.0F;
            float u0 = this.u / (float)SHEET_WIDTH;
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            float y = top;
            while (y < bottom) {
                // Where this row starts inside the cell: a row cut short
                // by the ramp's turn leaves the next one to carry the
                // pattern on from exactly where it stopped.
                float inCell = (y - top) % this.height;
                float y1 = Math.min(bottom, y + this.height - inCell);
                if (y < middle && y1 > middle) {
                    y1 = middle;
                }
                float v0 = (this.v + inCell) / (float)SHEET_HEIGHT;
                float v1 = (this.v + inCell + (y1 - y))
                        / (float)SHEET_HEIGHT;
                int topAlpha = rampAlpha(safeAlpha, y, middle, half);
                int bottomAlpha = rampAlpha(safeAlpha, y1, middle, half);
                for (float x = left; x < right; x += this.width) {
                    float x1 = Math.min(right, x + this.width);
                    float u1 = (this.u + (x1 - x)) / (float)SHEET_WIDTH;
                    tessellator.setColorRGBA_I(0xFFFFFF, bottomAlpha);
                    tessellator.addVertexWithUV(x, y1, 0.0D, u0, v1);
                    tessellator.addVertexWithUV(x1, y1, 0.0D, u1, v1);
                    tessellator.setColorRGBA_I(0xFFFFFF, topAlpha);
                    tessellator.addVertexWithUV(x1, y, 0.0D, u1, v0);
                    tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
                }
                y = y1;
            }
            tessellator.draw();
        } finally {
            GL11.glShadeModel(GL11.GL_FLAT);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /** The ramp at one height: full in the middle, nothing at the edges. */
    private static int rampAlpha(int alpha, float y, float middle,
                                 float half) {
        if (half <= 0.0F) {
            return alpha;
        }
        float ramp = 1.0F - Math.abs(y - middle) / half;
        return Math.round(alpha * Math.max(0.0F, Math.min(1.0F, ramp)));
    }

    /** The figure a count tile shows for {@code count}: one to nine, the plus past nine. */
    public static LostTalesUiSheet countFigure(int count) {
        LostTalesUiSheet[] figures = {COUNT_1, COUNT_2, COUNT_3, COUNT_4,
                COUNT_5, COUNT_6, COUNT_7, COUNT_8, COUNT_9};
        return count > figures.length ? COUNT_MORE
                : figures[Math.max(1, count) - 1];
    }

    /** The sprite at its own size, 1:1, at the given opacity. */
    public void draw(float x, float y, int alpha) {
        draw(this.u, this.v, this.width, this.height, x, y, alpha);
    }

    /**
     * A strip of the sheet stretched over a region: {@code width} by
     * {@code height} texels from {@code (u, v)} laid over the region at
     * the given opacity. Only ever one row or one column stretched along
     * its length, which the sheet's nearest-texel sampling keeps flat; a
     * framed button's edges are drawn this way.
     */
    public static void drawStretched(int u, int v, int width, int height, float x,
                              float y, float regionWidth, float regionHeight,
                              int alpha) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || regionWidth <= 0.0F || regionHeight <= 0.0F
                || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        minecraft.getTextureManager().bindTexture(TEXTURE);
        LostTalesUiInk.beginContent();
        GL11.glColor4f(1.0F, 1.0F, 1.0F,
                MathHelper.clamp_float(alpha / 255.0F, 0.0F, 1.0F));
        try {
            float u0 = u / (float)SHEET_WIDTH;
            float u1 = (u + width) / (float)SHEET_WIDTH;
            float v0 = v / (float)SHEET_HEIGHT;
            float v1 = (v + height) / (float)SHEET_HEIGHT;
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(x, y + regionHeight, 0.0D, u0, v1);
            tessellator.addVertexWithUV(x + regionWidth, y + regionHeight,
                    0.0D, u1, v1);
            tessellator.addVertexWithUV(x + regionWidth, y, 0.0D, u1, v0);
            tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
            tessellator.draw();
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /**
     * One row or one column of the sheet, {@code texels} texels from
     * {@code (u, v)}, laid from ({@code x}, {@code y}) over {@code length}
     * pixels along its run — a texel to a pixel across it, and along it
     * too unless {@code length} stretches it, as a framed button's edges
     * are stretched — its opacity running linearly from
     * {@code startAlpha} at the run's start to {@code endAlpha} at its
     * end: a column top to bottom, a row left to right. What a chat
     * window's frame is drawn with, so it fades along its edges.
     */
    public static void drawFading(int u, int v, int texels, boolean column,
                                  float x, float y, float length,
                                  int startAlpha, int endAlpha) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int start = Math.max(0, Math.min(255, startAlpha));
        int end = Math.max(0, Math.min(255, endAlpha));
        if (minecraft == null || texels <= 0 || length <= 0.0F
                || Math.max(start, end) < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        minecraft.getTextureManager().bindTexture(TEXTURE);
        LostTalesUiInk.beginContent();
        GL11.glShadeModel(GL11.GL_SMOOTH);
        // The GUI's alpha test throws away nearly transparent fragments,
        // which a fade's faint end is; off, the fade reaches nothing
        // rather than stopping short.
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        try {
            float width = column ? 1.0F : length;
            float height = column ? length : 1.0F;
            float u0 = u / (float)SHEET_WIDTH;
            float u1 = (u + (column ? 1 : texels)) / (float)SHEET_WIDTH;
            float v0 = v / (float)SHEET_HEIGHT;
            float v1 = (v + (column ? texels : 1)) / (float)SHEET_HEIGHT;
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.setColorRGBA_I(0xFFFFFF, column ? end : start);
            tessellator.addVertexWithUV(x, y + height, 0.0D, u0, v1);
            tessellator.setColorRGBA_I(0xFFFFFF, end);
            tessellator.addVertexWithUV(x + width, y + height, 0.0D, u1, v1);
            tessellator.setColorRGBA_I(0xFFFFFF, column ? start : end);
            tessellator.addVertexWithUV(x + width, y, 0.0D, u1, v0);
            tessellator.setColorRGBA_I(0xFFFFFF, start);
            tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
            tessellator.draw();
        } finally {
            GL11.glShadeModel(GL11.GL_FLAT);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /** Any cell of the sheet at its own size, 1:1, at the given opacity. */
    public static void draw(int u, int v, int width, int height,
                     float x, float y, int alpha) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null
                || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        minecraft.getTextureManager().bindTexture(TEXTURE);
        LostTalesUiInk.beginContent();
        GL11.glColor4f(1.0F, 1.0F, 1.0F,
                MathHelper.clamp_float(alpha / 255.0F, 0.0F, 1.0F));
        try {
            float u0 = u / (float)SHEET_WIDTH;
            float u1 = (u + width) / (float)SHEET_WIDTH;
            float v0 = v / (float)SHEET_HEIGHT;
            float v1 = (v + height) / (float)SHEET_HEIGHT;
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(x, y + height, 0.0D, u0, v1);
            tessellator.addVertexWithUV(x + width, y + height, 0.0D, u1, v1);
            tessellator.addVertexWithUV(x + width, y, 0.0D, u1, v0);
            tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
            tessellator.draw();
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
