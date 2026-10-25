package com.ninuna.losttales.client.input;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

/**
 * The cells of {@code keyboard_keys.png} a key hint is drawn from: the
 * blank key it writes its key's name on in the reader's language (MAP-17),
 * the arrows' glyphs and the mouse wheel.
 *
 * <p>The blank key is a stand-in until its own cells are painted: the
 * narrowest painted key's frame in its three poses (resting, half down,
 * pressed), cut into bands and stretched to the name it carries. Its
 * ends are kept whole and its middle column stretched across; its face
 * is opened to {@link #FACE_ROWS} rows, the font's capitals, and filled
 * with the frame's own face texel; its front gives up the rows the face
 * took. A letter, a digit or a word stands on the face as text. The
 * arrows keep their painted glyphs, which carry no words.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesInputIconAtlas {
    public static final ResourceLocation TEXTURE = new ResourceLocation(
            LostTalesMetaData.MOD_ID, "textures/gui/keyboard_keys.png");

    public static final int TEXTURE_WIDTH = 363;
    public static final int TEXTURE_HEIGHT = 113;
    /** Every key's height, and the mouse wheel's. */
    public static final int SPRITE_HEIGHT = 13;
    /** The key's poses: resting, half down and pressed. */
    public static final int KEYBOARD_FRAME_COUNT = 3;

    /** The painted key the blank one is cut from: its three poses side by side. */
    static final int KEY_CELL_U = 0;
    static final int KEY_CELL_V = 0;
    static final int KEY_CELL_WIDTH = 13;
    /** From one pose's cell to the next: its width and a clear column. */
    static final int KEY_CELL_STRIDE = KEY_CELL_WIDTH + 1;
    /** The columns kept whole at either end: the rounded edge, its rim and shade. */
    static final int KEY_END_COLUMNS = 5;
    /** The column stretched between the ends. */
    static final int KEY_MIDDLE_COLUMN = 6;
    /** The texel of the frame's face colour, in the resting pose's cell, stretched over the face. */
    static final int FACE_TEXEL_U = 5;
    static final int FACE_TEXEL_V = 0;
    /** The rows above the face: the top outline and the rim. */
    static final int RIM_ROWS = 2;
    /** A painted face's rows in the cell, under the rim. */
    static final int PAINTED_FACE_ROWS = 5;
    /** The face's rows on the blank key: the font's capitals. */
    public static final int FACE_ROWS = LostTalesUiInk.CAP_HEIGHT;
    /** The front's rows on the resting blank key, under the face and its lip. */
    static final int FRONT_ROWS = SPRITE_HEIGHT - RIM_ROWS - FACE_ROWS - 1;
    /** From the key's side to its face: the outline, the rim and the shade. */
    public static final int FACE_INSET = 3;
    /** Clear columns at least between a name and the face's sides. */
    static final int LABEL_CLEAR = 1;
    /** The narrowest key: as wide as a painted letter key. */
    public static final int MIN_KEY_WIDTH = KEY_CELL_WIDTH;

    /** The arrows' glyphs: up, right, down, left, in the glyph strip after A to Z. */
    private static final int ARROW_GLYPH_FIRST = 26;
    private static final int GLYPH_U_STRIDE = 8;
    static final int GLYPH_V = 14;
    static final int GLYPH_WIDTH = 7;
    static final int GLYPH_HEIGHT = 5;

    private static final Cell MOUSE_WHEEL = new Cell(0, 100, 16, SPRITE_HEIGHT);

    private LostTalesInputIconAtlas() {}

    /** The mouse wheel's picture. */
    public static Cell mouseWheel() {
        return MOUSE_WHEEL;
    }

    /** The painted glyph an arrow key shows, which carries no words; null for any other key. */
    public static Cell arrowGlyph(int keyCode) {
        int index;
        switch (keyCode) {
            case Keyboard.KEY_UP:
                index = 0;
                break;
            case Keyboard.KEY_RIGHT:
                index = 1;
                break;
            case Keyboard.KEY_DOWN:
                index = 2;
                break;
            case Keyboard.KEY_LEFT:
                index = 3;
                break;
            default:
                return null;
        }
        return new Cell((ARROW_GLYPH_FIRST + index) * GLYPH_U_STRIDE, GLYPH_V,
                GLYPH_WIDTH, GLYPH_HEIGHT);
    }

    /**
     * A blank key's width for a name {@code labelWidth} wide, its shadow
     * column with it: the name and a clear column each side on the face,
     * never narrower than a letter key.
     */
    public static int keyWidth(int labelWidth) {
        return Math.max(MIN_KEY_WIDTH,
                Math.max(0, labelWidth) + 2 * (FACE_INSET + LABEL_CLEAR));
    }

    /** A key's width with a glyph on it, which fills its face as a painted one does. */
    public static int glyphKeyWidth(Cell glyph) {
        return Math.max(MIN_KEY_WIDTH,
                (glyph == null ? 0 : glyph.width) + 2 * FACE_INSET);
    }

    /** Where a name {@code labelWidth} wide starts on a key {@code keyWidth} wide: centred on its face. */
    public static int labelLeft(int keyWidth, int labelWidth) {
        return FACE_INSET + LostTalesUiInk.centredStart(
                keyWidth - 2 * FACE_INSET, labelWidth);
    }

    /** The face's top row in {@code pose}: under the rim, a row lower for each step down. */
    public static int faceTop(int pose) {
        return boundedPose(pose) + RIM_ROWS;
    }

    /** The left of {@code pose}'s cell in the texture. */
    static int cellU(int pose) {
        return KEY_CELL_U + boundedPose(pose) * KEY_CELL_STRIDE;
    }

    /**
     * The bands the blank key is drawn in, top to bottom, for
     * {@code pose}: the rim, the face's sides stretched from one painted
     * face row, the lip under the face, and what is left of the front.
     * Each band is cut into the ends and the stretched middle.
     */
    static Band[] bands(int pose) {
        int step = boundedPose(pose);
        int faceTop = step + RIM_ROWS;
        int lipRow = faceTop + FACE_ROWS;
        int frontRows = FRONT_ROWS - step;
        return new Band[] {
                new Band(step, RIM_ROWS, step, RIM_ROWS),
                new Band(step + RIM_ROWS + PAINTED_FACE_ROWS / 2, 1,
                        faceTop, FACE_ROWS),
                new Band(step + RIM_ROWS + PAINTED_FACE_ROWS, 1, lipRow, 1),
                new Band(SPRITE_HEIGHT - frontRows, frontRows,
                        SPRITE_HEIGHT - frontRows, frontRows)
        };
    }

    private static int boundedPose(int pose) {
        return Math.max(0, Math.min(KEYBOARD_FRAME_COUNT - 1, pose));
    }

    /** A rectangle of the texture, drawn one texel to one pixel. */
    public static final class Cell {
        final int u;
        final int v;
        final int width;
        final int height;

        Cell(int u, int v, int width, int height) {
            this.u = u;
            this.v = v;
            this.width = width;
            this.height = height;
        }

        public int getWidth() {
            return this.width;
        }

        public int getHeight() {
            return this.height;
        }
    }

    /**
     * Rows of a pose's cell drawn to rows of the key: {@code sourceRows}
     * from {@code sourceRow}, stretched over {@code rows} from {@code top}.
     */
    static final class Band {
        final int sourceRow;
        final int sourceRows;
        final int top;
        final int rows;

        Band(int sourceRow, int sourceRows, int top, int rows) {
            this.sourceRow = sourceRow;
            this.sourceRows = sourceRows;
            this.top = top;
            this.rows = rows;
        }
    }
}
