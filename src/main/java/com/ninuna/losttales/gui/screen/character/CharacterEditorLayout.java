package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * Where every part of the Character Editor stands, worked out from the
 * page's box alone: the list of sections, a panel the tool strip's left
 * button folds away, beside the section picked, split by one rule. A page
 * too narrow for both shows one of them over the whole page, the list
 * while it is out, else the section, as the journal does.
 *
 * <p>Free of Minecraft: the geometry is arithmetic, and a test can ask it
 * every question the page does.</p>
 */
public final class CharacterEditorLayout {
    /** Clear room between the page's edge and what it shows: a menu's, across and down. */
    public static final int MARGIN_X = MenuWindow.PADDING_X;
    public static final int MARGIN_Y = MenuWindow.PADDING_Y;
    /** Clear pixels either side of the rule between the list and the section. */
    public static final int GUTTER = 6;
    /** The narrowest and widest the list is allowed to be. */
    public static final int LIST_MIN_WIDTH = 64;
    public static final int LIST_MAX_WIDTH = 110;
    /** The narrowest section the rows still read in. */
    public static final int SECTION_MIN_WIDTH = 150;
    /** The narrowest page the list and the section both fit on. */
    public static final int MIN_SPLIT_WIDTH =
            LIST_MIN_WIDTH + 2 * GUTTER + 1 + SECTION_MIN_WIDTH + MARGIN_X;

    private final int pageWidth;
    private final int pageHeight;
    private final int listWidth;
    private final boolean listOut;

    /** {@code listOut}: whether the list is out, its button lit. */
    public CharacterEditorLayout(int pageWidth, int pageHeight,
                                 boolean listOut) {
        this.pageWidth = Math.max(0, pageWidth);
        this.pageHeight = Math.max(0, pageHeight);
        this.listWidth = Math.max(LIST_MIN_WIDTH,
                Math.min(LIST_MAX_WIDTH, this.pageWidth / 4));
        this.listOut = listOut;
    }

    /** Whether the page is wide enough for the list and the section side by side. */
    public boolean isWide() {
        return this.pageWidth >= MIN_SPLIT_WIDTH;
    }

    /** Whether the list and the section stand side by side now. */
    public boolean isSplit() {
        return isWide() && this.listOut;
    }

    /**
     * The band the list's rows show in: from the page's left edge, a
     * menu's padding under its top and over its foot. The page's whole
     * width while the two take turns, and empty while it is folded away.
     */
    public LostTalesUiHitBox list() {
        if (!this.listOut) {
            return none();
        }
        return new LostTalesUiHitBox(0, MARGIN_Y,
                isWide() ? this.listWidth : this.pageWidth,
                Math.max(0, this.pageHeight - 2 * MARGIN_Y));
    }

    /** The rule between the two, as tall as the section; empty unless they stand side by side. */
    public LostTalesUiHitBox divider() {
        if (!isSplit()) {
            return none();
        }
        return new LostTalesUiHitBox(list().right() + GUTTER, MARGIN_Y, 1,
                Math.max(0, this.pageHeight - 2 * MARGIN_Y));
    }

    /**
     * Where the section's rows stand: right of the rule beside the list,
     * the whole page between the margins while the list is folded, and
     * empty while a narrow page shows the list.
     */
    public LostTalesUiHitBox section() {
        if (!isSplit()) {
            return this.listOut ? none()
                    : new LostTalesUiHitBox(MARGIN_X, MARGIN_Y,
                            Math.max(0, this.pageWidth - 2 * MARGIN_X),
                            Math.max(0, this.pageHeight - 2 * MARGIN_Y));
        }
        int left = (int)divider().right() + GUTTER;
        return new LostTalesUiHitBox(left, MARGIN_Y,
                Math.max(0, this.pageWidth - MARGIN_X - left),
                Math.max(0, this.pageHeight - 2 * MARGIN_Y));
    }

    private static LostTalesUiHitBox none() {
        return new LostTalesUiHitBox(0, 0, 0, 0);
    }
}
