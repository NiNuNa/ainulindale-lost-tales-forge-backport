package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.input.LostTalesKeyPress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;

/**
 * The one text field every window types into — the tool strip's search
 * well, a page's bar, a menu's field — made in the look a system gives it:
 * the chat's, with its one caret, its selection and its previews. The
 * chat installs its kit before any screen opens; without one the game's
 * own field is made.
 */
public final class WindowFields {
    /** How a system makes, draws and lists for its fields. */
    public interface Kit {
        /**
         * A field {@code height} tall holding at most {@code limit}
         * characters (none for the field's own limit); one that
         * {@code showsEmoji} draws a complete shortcode as its picture, as a
         * status line is drawn once set, and anything else shows what is
         * typed as it is.
         */
        GuiTextField make(int height, int limit, boolean showsEmoji);

        /** Draws the field at {@code alpha} of its window's strength. */
        void draw(GuiTextField field, int alpha);

        /** The list the field opens as it is typed in; null for a field that opens none. */
        FieldList listFor(GuiTextField field, boolean showsEmoji);
    }

    /**
     * A list a field opens over itself while what stands at its caret
     * asks for one: the emoji list for a {@code :name}.
     */
    public interface FieldList {
        /** Follows the field: out while it has something to offer at the caret. */
        void update(GuiTextField field);

        boolean isActive();

        /** Puts the list away until what stands at the caret changes. */
        void dismiss();

        /** Up and Down walk it, Tab and Enter take the chosen row; answers whether it took the press. */
        boolean serve(GuiTextField field, LostTalesKeyPress press);

        /** Writes a row into the field in place of what was typed for it. */
        void take(GuiTextField field, int row);

        /** Draws it over everything, its bottom a pixel above {@code fieldTop}, from {@code left}. */
        void draw(Minecraft minecraft, PointerRegions regions, int left,
                  int fieldTop, double pointerX, double pointerY);

        /** The row under a point; -1 on the list between rows, -2 off it. */
        int rowAt(double x, double y, int left, int fieldTop);
    }

    private static final Kit PLAIN = new Kit() {
        @Override
        public GuiTextField make(int height, int limit, boolean showsEmoji) {
            GuiTextField field = new GuiTextField(
                    Minecraft.getMinecraft().fontRenderer, 0, 0, 10, height);
            if (limit > 0) {
                field.setMaxStringLength(limit);
            }
            field.setEnableBackgroundDrawing(false);
            return field;
        }

        @Override
        public void draw(GuiTextField field, int alpha) {
            field.drawTextBox();
        }

        @Override
        public FieldList listFor(GuiTextField field, boolean showsEmoji) {
            return null;
        }
    };

    private static Kit kit = PLAIN;

    private WindowFields() {}

    /** The kit every field is made with from now on. */
    public static synchronized void install(Kit made) {
        kit = made == null ? PLAIN : made;
    }

    public static synchronized GuiTextField make(int height, int limit,
                                                 boolean showsEmoji) {
        return kit.make(height, limit, showsEmoji);
    }

    public static synchronized void draw(GuiTextField field, int alpha) {
        if (field != null) {
            kit.draw(field, alpha);
        }
    }

    public static synchronized FieldList listFor(GuiTextField field,
                                                 boolean showsEmoji) {
        return field == null ? null : kit.listFor(field, showsEmoji);
    }
}
