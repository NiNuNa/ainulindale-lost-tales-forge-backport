package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;

/**
 * One thing on a page's input bar, as the chat's bar reads left to right
 * (U2 a): a button with an icon and a word, the field where the page
 * takes words, quiet words, and small glyph buttons at the right end. A
 * button that ends something (Abandon, Leave, Delete) stands last on the
 * right, in red. A page makes its items afresh every frame.
 *
 * <p>An action that cannot be taken now is still there, greyed, and its
 * tip says why (rule 25). Every button's tip names what it does and its
 * key.</p>
 */
public final class BarItem {
    public enum Kind {
        /** A framed button: an icon and a word. */
        BUTTON,
        /** A small glyph at the bar's right end, as the chat's pickers stand. */
        GLYPH,
        /** The page's own field, in a well, with a list of what it offers above it. */
        FIELD,
        /** Quiet words, as the chat's counter stands before its glyphs. */
        WORDS
    }

    public final Kind kind;
    /** What the page is told when the item is pressed. */
    public final String id;
    /** A button's word, a field's hint, the words themselves. */
    public final String label;
    ItemStack icon;
    LostTalesUiSheet glyph;
    LostTalesUiSheet glyphLit;
    GuiTextField field;
    String tip = "";
    String unavailable = "";
    boolean right;
    boolean ending;
    boolean lit;
    List<String> offers = Collections.emptyList();
    int offered = -1;

    private BarItem(Kind kind, String id, String label) {
        this.kind = kind;
        this.id = id == null ? "" : id;
        this.label = label == null ? "" : label;
    }

    /** A framed button with a word, and an item's picture before it. */
    public static BarItem button(String id, String label, ItemStack icon) {
        BarItem item = new BarItem(Kind.BUTTON, id, label);
        item.icon = icon;
        return item;
    }

    /** A framed button with a word, and a glyph of the UI sheet before it. */
    public static BarItem button(String id, String label,
                                 LostTalesUiSheet glyph,
                                 LostTalesUiSheet glyphLit) {
        BarItem item = new BarItem(Kind.BUTTON, id, label);
        item.glyph = glyph;
        item.glyphLit = glyphLit;
        return item;
    }

    /** A small glyph button; it always stands at the right end. */
    public static BarItem glyph(String id, LostTalesUiSheet glyph,
                                LostTalesUiSheet glyphLit, String tip) {
        BarItem item = new BarItem(Kind.GLYPH, id, "");
        item.glyph = glyph;
        item.glyphLit = glyphLit;
        item.tip = tip == null ? "" : tip;
        item.right = true;
        return item;
    }

    /** Quiet words; they always stand at the right end, before the glyphs. */
    public static BarItem words(String text) {
        BarItem item = new BarItem(Kind.WORDS, "", text);
        item.right = true;
        return item;
    }

    /**
     * The page's field, which it owns and types into; the bar lays it in
     * its well and draws it. It takes the room the other items leave.
     */
    public static BarItem field(String id, GuiTextField field, String hint) {
        BarItem item = new BarItem(Kind.FIELD, id, hint);
        item.field = field;
        return item;
    }

    /** What the pointer's tip says: what the item does, and its key. */
    public BarItem tip(String words) {
        this.tip = words == null ? "" : words;
        return this;
    }

    /** Greyed: it cannot be taken now, and {@code why} says so. */
    public BarItem unavailable(String why) {
        this.unavailable = why == null || why.length() == 0 ? " " : why;
        return this;
    }

    /** It ends something: red, and last on the right. */
    public BarItem ending() {
        this.ending = true;
        this.right = true;
        return this;
    }

    /** Lit while what it switches on is on: a map's teleport waiting for a place. */
    public BarItem lit(boolean on) {
        this.lit = on;
        return this;
    }

    /**
     * What a field offers in the list above it, and which of them is
     * chosen (-1 for none); an empty list shows none.
     */
    public BarItem offers(List<String> names, int chosen) {
        this.offers = names == null ? Collections.<String>emptyList() : names;
        this.offered = chosen;
        return this;
    }

    public boolean isAvailable() {
        return this.unavailable.length() == 0;
    }

    /** What the tip says: why it is greyed, else what it does. */
    public String tipText() {
        return isAvailable() ? this.tip : this.unavailable.trim();
    }
}
