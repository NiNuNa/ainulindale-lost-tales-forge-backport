package com.ninuna.losttales.client.input;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

/** Static sprite metadata for {@code textures/gui/keyboard_keys.png}. */
@SideOnly(Side.CLIENT)
public final class LostTalesInputIconAtlas {
    public static final ResourceLocation TEXTURE = new ResourceLocation(LostTalesMetaData.MOD_ID, "textures/gui/keyboard_keys.png");

    public static final int TEXTURE_WIDTH = 363;
    public static final int TEXTURE_HEIGHT = 41;
    public static final int SPRITE_HEIGHT = 13;
    public static final int GRID_SPRITE_WIDTH = 13;
    public static final int GRID_SPACING = 1;

    private static final Map<Integer, Sprite> KEYBOARD_SPRITES;
    private static final Map<Integer, Sprite> MOUSE_BUTTON_SPRITES;
    private static final Sprite MOUSE_WHEEL_SPRITE = new Sprite(0, 28, 16, SPRITE_HEIGHT);

    static {
        Map<Integer, Sprite> keyboardSprites = new HashMap<Integer, Sprite>();
        int[] letterCodes = {
                Keyboard.KEY_A, Keyboard.KEY_B, Keyboard.KEY_C, Keyboard.KEY_D, Keyboard.KEY_E,
                Keyboard.KEY_F, Keyboard.KEY_G, Keyboard.KEY_H, Keyboard.KEY_I, Keyboard.KEY_J,
                Keyboard.KEY_K, Keyboard.KEY_L, Keyboard.KEY_M, Keyboard.KEY_N, Keyboard.KEY_O,
                Keyboard.KEY_P, Keyboard.KEY_Q, Keyboard.KEY_R, Keyboard.KEY_S, Keyboard.KEY_T,
                Keyboard.KEY_U, Keyboard.KEY_V, Keyboard.KEY_W, Keyboard.KEY_X, Keyboard.KEY_Y,
                Keyboard.KEY_Z
        };
        for (int i = 0; i < letterCodes.length; i++) {
            keyboardSprites.put(Integer.valueOf(letterCodes[i]), new Sprite(i * (GRID_SPRITE_WIDTH + GRID_SPACING), 0, GRID_SPRITE_WIDTH, SPRITE_HEIGHT));
        }

        Sprite alt = new Sprite(0, 14, 20, SPRITE_HEIGHT);
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_LMENU), alt);
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_RMENU), alt);

        Sprite shift = new Sprite(21, 14, 29, SPRITE_HEIGHT);
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_LSHIFT), shift);
        keyboardSprites.put(Integer.valueOf(Keyboard.KEY_RSHIFT), shift);

        KEYBOARD_SPRITES = Collections.unmodifiableMap(keyboardSprites);

        // The current atlas contains a mouse-wheel sprite but no mouse-button sprites.
        // Add future button sprites here using their zero-based LWJGL button index.
        MOUSE_BUTTON_SPRITES = Collections.unmodifiableMap(new HashMap<Integer, Sprite>());
    }

    private LostTalesInputIconAtlas() {}

    public static Sprite findSprite(LostTalesInputBinding.Type type, int keyCode) {
        if (type == null) {
            return null;
        }

        switch (type) {
            case KEYBOARD:
                return KEYBOARD_SPRITES.get(Integer.valueOf(keyCode));
            case MOUSE_BUTTON:
                return MOUSE_BUTTON_SPRITES.get(Integer.valueOf(LostTalesInputBinding.getMouseButtonIndex(keyCode)));
            case MOUSE_WHEEL:
                return MOUSE_WHEEL_SPRITE;
            default:
                return null;
        }
    }

    public static final class Sprite {
        private final int u;
        private final int v;
        private final int width;
        private final int height;

        private Sprite(int u, int v, int width, int height) {
            this.u = u;
            this.v = v;
            this.width = width;
            this.height = height;
        }

        public int getU() {
            return this.u;
        }

        public int getV() {
            return this.v;
        }

        public int getWidth() {
            return this.width;
        }

        public int getHeight() {
            return this.height;
        }
    }
}
