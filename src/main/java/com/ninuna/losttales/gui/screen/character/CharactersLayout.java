package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * Where every part of the Characters page stands, worked out from the
 * page's box alone.
 *
 * <p>The page is the roster, a panel the tool strip's left button folds
 * away, beside the profile of the character picked in it, split by one
 * rule. A page too narrow for both shows one of them over the whole body:
 * the roster while it is out, else the profile, one column of words. The
 * character itself stands on the stage beside the window.</p>
 *
 * <p>Free of Minecraft: the geometry is arithmetic, and a test can ask it
 * every question the page does.</p>
 */
public final class CharactersLayout {
    /** Clear room between the page's edge and what it shows: a menu's, across and down. */
    public static final int MARGIN_X = MenuWindow.PADDING_X;
    public static final int MARGIN_Y = MenuWindow.PADDING_Y;
    /** Clear pixels either side of the rule between the roster and the profile. */
    public static final int GUTTER = 10;
    /** The narrowest and widest the roster is allowed to be. */
    public static final int ROSTER_MIN_WIDTH = 120;
    public static final int ROSTER_MAX_WIDTH = 170;
    /** The narrowest profile the words still read in. */
    public static final int PROFILE_MIN_WIDTH = 150;
    /** The narrowest page the roster and the profile both fit on. */
    public static final int MIN_SPLIT_WIDTH =
            2 * MARGIN_X + ROSTER_MIN_WIDTH + 2 * GUTTER + 1 + PROFILE_MIN_WIDTH;

    private final int pageWidth;
    private final int pageHeight;
    private final int rosterWidth;
    private final boolean rosterOut;

    /** {@code rosterOut}: whether the roster is out, its button lit. */
    public CharactersLayout(int pageWidth, int pageHeight, boolean rosterOut) {
        this.pageWidth = Math.max(0, pageWidth);
        this.pageHeight = Math.max(0, pageHeight);
        this.rosterWidth = Math.max(ROSTER_MIN_WIDTH,
                Math.min(ROSTER_MAX_WIDTH, this.pageWidth / 3));
        this.rosterOut = rosterOut;
    }

    /** Whether the page is wide enough for the roster and the profile side by side. */
    public boolean isWide() {
        return this.pageWidth >= MIN_SPLIT_WIDTH;
    }

    /** Whether the roster and the profile stand side by side now. */
    public boolean isSplit() {
        return isWide() && this.rosterOut;
    }

    private LostTalesUiHitBox body() {
        return new LostTalesUiHitBox(MARGIN_X, MARGIN_Y,
                Math.max(0, this.pageWidth - 2 * MARGIN_X),
                Math.max(0, this.pageHeight - 2 * MARGIN_Y));
    }

    private static LostTalesUiHitBox none() {
        return new LostTalesUiHitBox(0, 0, 0, 0);
    }

    /**
     * The roster: the left of the body beside the profile, the whole body
     * while the two take turns, and empty while it is folded away.
     */
    public LostTalesUiHitBox roster() {
        if (!this.rosterOut) {
            return none();
        }
        if (!isWide()) {
            return body();
        }
        LostTalesUiHitBox body = body();
        return new LostTalesUiHitBox(MARGIN_X, MARGIN_Y, this.rosterWidth,
                body.height);
    }

    /** The rule between the roster and the profile; empty unless they stand side by side. */
    public LostTalesUiHitBox divider() {
        if (!isSplit()) {
            return none();
        }
        LostTalesUiHitBox roster = roster();
        return new LostTalesUiHitBox(roster.right() + GUTTER, roster.top, 1,
                roster.height);
    }

    /**
     * The profile: right of the rule beside the roster, the whole body
     * while the roster is folded, and empty while a narrow page shows the
     * roster.
     */
    public LostTalesUiHitBox profile() {
        if (!isSplit()) {
            return this.rosterOut ? none() : body();
        }
        int left = (int)divider().right() + GUTTER;
        return new LostTalesUiHitBox(left, MARGIN_Y,
                Math.max(0, this.pageWidth - MARGIN_X - left),
                Math.max(0, this.pageHeight - 2 * MARGIN_Y));
    }
}
