package com.ninuna.losttales.client.input;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

/** Static layered-sprite metadata for {@code keyboard_keys.png}. */
@SideOnly(Side.CLIENT)
public final class LostTalesInputIconAtlas {
    public static final ResourceLocation TEXTURE = new ResourceLocation(
            LostTalesMetaData.MOD_ID, "textures/gui/keyboard_keys.png");

    public static final int TEXTURE_WIDTH = 363;
    public static final int TEXTURE_HEIGHT = 113;
    public static final int SPRITE_HEIGHT = 13;
    public static final int KEYBOARD_FRAME_COUNT = 3;
    private static final int FRAME_SPACING = 1;
    private static final int GLYPH_HEIGHT = 5;
    private static final int GLYPH_OFFSET_X = 3;
    /** Idle aperture starts here; each later pose sits one pixel lower. */
    private static final int GLYPH_OFFSET_Y = 2;

    private static final Map<Integer, Sprite> KEYBOARD_SPRITES;
    private static final Map<Integer, Sprite> MOUSE_BUTTON_SPRITES;
    private static final Sprite MOUSE_WHEEL_SPRITE =
            new Sprite(0, 100, 16, SPRITE_HEIGHT);

    static {
        Map<Integer, Sprite> keyboardSprites =
                new HashMap<Integer, Sprite>();

        // The glyph strip is A-Z, Up, Right, Down, Left, 0-9, then the two
        // punctuation marks. All forty-two symbols are composed over the same
        // three reusable 13-pixel frames.
        int[] singleGlyphCodes = {
                Keyboard.KEY_A, Keyboard.KEY_B, Keyboard.KEY_C,
                Keyboard.KEY_D, Keyboard.KEY_E, Keyboard.KEY_F,
                Keyboard.KEY_G, Keyboard.KEY_H, Keyboard.KEY_I,
                Keyboard.KEY_J, Keyboard.KEY_K, Keyboard.KEY_L,
                Keyboard.KEY_M, Keyboard.KEY_N, Keyboard.KEY_O,
                Keyboard.KEY_P, Keyboard.KEY_Q, Keyboard.KEY_R,
                Keyboard.KEY_S, Keyboard.KEY_T, Keyboard.KEY_U,
                Keyboard.KEY_V, Keyboard.KEY_W, Keyboard.KEY_X,
                Keyboard.KEY_Y, Keyboard.KEY_Z,
                Keyboard.KEY_UP, Keyboard.KEY_RIGHT,
                Keyboard.KEY_DOWN, Keyboard.KEY_LEFT,
                Keyboard.KEY_0, Keyboard.KEY_1, Keyboard.KEY_2,
                Keyboard.KEY_3, Keyboard.KEY_4, Keyboard.KEY_5,
                Keyboard.KEY_6, Keyboard.KEY_7, Keyboard.KEY_8,
                Keyboard.KEY_9,
                Keyboard.KEY_PERIOD, Keyboard.KEY_COMMA
        };
        for (int index = 0; index < singleGlyphCodes.length; index++) {
            keyboardSprites.put(Integer.valueOf(singleGlyphCodes[index]),
                    layeredSprite(0, 0, 13,
                            index * 8, 14, 7));
        }

        // Two-character keys: F1 to F9, in their own frame family because a
        // single-glyph frame has no room for two characters.
        int[] shortFunctionKeys = {
                Keyboard.KEY_F1, Keyboard.KEY_F2, Keyboard.KEY_F3,
                Keyboard.KEY_F4, Keyboard.KEY_F5, Keyboard.KEY_F6,
                Keyboard.KEY_F7, Keyboard.KEY_F8, Keyboard.KEY_F9
        };
        for (int index = 0; index < shortFunctionKeys.length; index++) {
            keyboardSprites.put(Integer.valueOf(shortFunctionKeys[index]),
                    layeredSprite(0, 20, 17,
                            index * 12, 34, 11));
        }

        // Three-character keys: ALT, ESC, TAB, DEL, then F10 to F12.
        Sprite alt = layeredSprite(0, 40, 21, 0, 54, 15);
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_LMENU), alt);
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_RMENU), alt);
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_ESCAPE),
                layeredSprite(0, 40, 21, 16, 54, 15));
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_TAB),
                layeredSprite(0, 40, 21, 32, 54, 15));
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_DELETE),
                layeredSprite(0, 40, 21, 48, 54, 15));
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_F10),
                layeredSprite(0, 40, 21, 64, 54, 15));
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_F11),
                layeredSprite(0, 40, 21, 80, 54, 15));
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_F12),
                layeredSprite(0, 40, 21, 96, 54, 15));

        // Four-letter keys: CAPS and CTRL.
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_CAPITAL),
                layeredSprite(0, 60, 25, 0, 74, 19));
        Sprite control = layeredSprite(0, 60, 25, 20, 74, 19);
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_LCONTROL), control);
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_RCONTROL), control);

        // Five-letter keys: SHIFT and SPACE.
        Sprite shift = layeredSprite(0, 80, 29, 0, 94, 23);
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_LSHIFT), shift);
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_RSHIFT), shift);
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_SPACE),
                layeredSprite(0, 80, 29, 24, 94, 23));

        KEYBOARD_SPRITES = Collections.unmodifiableMap(keyboardSprites);

        // The current atlas still has only the static mouse-wheel artwork.
        MOUSE_BUTTON_SPRITES = Collections.unmodifiableMap(
                new HashMap<Integer, Sprite>());
    }

    private LostTalesInputIconAtlas() {}

    private static Sprite layeredSprite(
            int frameU, int frameV, int frameWidth,
            int glyphU, int glyphV, int glyphWidth) {
        return new Sprite(frameU, frameV, frameWidth, SPRITE_HEIGHT,
                frameWidth + FRAME_SPACING, KEYBOARD_FRAME_COUNT,
                glyphU, glyphV, glyphWidth, GLYPH_HEIGHT,
                GLYPH_OFFSET_X, GLYPH_OFFSET_Y);
    }

    public static Sprite findSprite(
            LostTalesInputBinding.Type type, int keyCode) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case KEYBOARD:
                return KEYBOARD_SPRITES.get(Integer.valueOf(keyCode));
            case MOUSE_BUTTON:
                return MOUSE_BUTTON_SPRITES.get(Integer.valueOf(
                        LostTalesInputBinding.getMouseButtonIndex(keyCode)));
            case MOUSE_WHEEL:
                return MOUSE_WHEEL_SPRITE;
            default:
                return null;
        }
    }

    public static final class Sprite {
        private final int frameU;
        private final int frameV;
        private final int width;
        private final int height;
        private final int frameStrideX;
        private final int frameCount;
        private final int glyphU;
        private final int glyphV;
        private final int glyphWidth;
        private final int glyphHeight;
        private final int glyphOffsetX;
        private final int glyphOffsetY;

        private Sprite(int u, int v, int width, int height) {
            this(u, v, width, height, 0, 1,
                    0, 0, 0, 0, 0, 0);
        }

        private Sprite(int frameU, int frameV, int width, int height,
                       int frameStrideX, int frameCount,
                       int glyphU, int glyphV,
                       int glyphWidth, int glyphHeight,
                       int glyphOffsetX, int glyphOffsetY) {
            this.frameU = frameU;
            this.frameV = frameV;
            this.width = width;
            this.height = height;
            this.frameStrideX = Math.max(0, frameStrideX);
            this.frameCount = Math.max(1, frameCount);
            this.glyphU = glyphU;
            this.glyphV = glyphV;
            this.glyphWidth = Math.max(0, glyphWidth);
            this.glyphHeight = Math.max(0, glyphHeight);
            this.glyphOffsetX = glyphOffsetX;
            this.glyphOffsetY = glyphOffsetY;
        }

        public int getU() {
            return this.frameU;
        }

        public int getU(int frame) {
            int bounded = Math.max(0, Math.min(this.frameCount - 1, frame));
            return this.frameU + bounded * this.frameStrideX;
        }

        public int getV() {
            return this.frameV;
        }

        public int getV(int frame) {
            return this.frameV;
        }

        public int getWidth() {
            return this.width;
        }

        public int getHeight() {
            return this.height;
        }

        public boolean isAnimated() {
            return this.frameCount > 1;
        }

        public int getFrameCount() {
            return this.frameCount;
        }

        public boolean hasGlyph() {
            return this.glyphWidth > 0 && this.glyphHeight > 0;
        }

        public int getGlyphU() {
            return this.glyphU;
        }

        public int getGlyphV() {
            return this.glyphV;
        }

        public int getGlyphWidth() {
            return this.glyphWidth;
        }

        public int getGlyphHeight() {
            return this.glyphHeight;
        }

        public int getGlyphOffsetX() {
            return this.glyphOffsetX;
        }

        public int getGlyphOffsetY() {
            return this.glyphOffsetY;
        }

        public int getGlyphOffsetY(int frame) {
            int bounded = Math.max(0, Math.min(this.frameCount - 1, frame));
            return this.glyphOffsetY + bounded;
        }
    }
}
