package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * Where every part of the Characters tab stands, worked out from the
 * page's box alone.
 *
 * <p>The page is the roster, a panel the tool strip's left button folds
 * away, beside the profile of the character picked in it, split by one
 * rule. A page too narrow for both shows one of them over the whole body:
 * the roster while it is out, else the profile. The profile is one
 * column; in a room wide enough the figure stands at its left and the
 * words scroll beside it, else the figure stands at the column's top and
 * scrolls with the words.</p>
 *
 * <p>Free of Minecraft: the geometry is arithmetic, and a test can ask it
 * every question the page does.</p>
 */
public final class CharactersLayout {
    /** Clear pixels between the page's edge and anything drawn. */
    public static final int MARGIN = 8;
    /** Clear pixels either side of the rule between the roster and the profile. */
    public static final int GUTTER = 10;
    /** The narrowest and widest the roster is allowed to be. */
    public static final int ROSTER_MIN_WIDTH = 120;
    public static final int ROSTER_MAX_WIDTH = 170;
    /** The narrowest profile the words still read in. */
    public static final int PROFILE_MIN_WIDTH = 150;
    /** The figure's box beside the words, and its height above them in a narrow room. */
    public static final int FIGURE_WIDTH = 110;
    public static final int FIGURE_HEIGHT = 120;
    /** The narrowest page the roster and the profile both fit on. */
    public static final int MIN_SPLIT_WIDTH =
            2 * MARGIN + ROSTER_MIN_WIDTH + 2 * GUTTER + 1 + PROFILE_MIN_WIDTH;

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
        return new LostTalesUiHitBox(MARGIN, MARGIN,
                Math.max(0, this.pageWidth - 2 * MARGIN),
                Math.max(0, this.pageHeight - 2 * MARGIN));
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
        return new LostTalesUiHitBox(MARGIN, MARGIN, this.rosterWidth,
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
        return new LostTalesUiHitBox(left, MARGIN,
                Math.max(0, this.pageWidth - MARGIN - left),
                Math.max(0, this.pageHeight - 2 * MARGIN));
    }

    /** Whether the figure stands at the profile's left, the words beside it. */
    public boolean figureBeside() {
        return profile().width >= FIGURE_WIDTH + GUTTER + PROFILE_MIN_WIDTH;
    }

    /**
     * The figure's box: at the profile's left the profile's height, or at
     * its top the profile's width, {@code scroll} pixels up with the words.
     */
    public LostTalesUiHitBox figure(int scroll) {
        LostTalesUiHitBox profile = profile();
        if (profile.width <= 0) {
            return none();
        }
        if (figureBeside()) {
            return new LostTalesUiHitBox(profile.left, profile.top,
                    FIGURE_WIDTH, profile.height);
        }
        return new LostTalesUiHitBox(profile.left, profile.top - scroll,
                profile.width, FIGURE_HEIGHT);
    }

    /**
     * The words' column: beside the figure, or the whole profile with the
     * figure the first thing in it; the words start at its top plus
     * {@link #wordsOffset}.
     */
    public LostTalesUiHitBox words() {
        LostTalesUiHitBox profile = profile();
        if (profile.width <= 0 || !figureBeside()) {
            return profile;
        }
        int left = (int)profile.left + FIGURE_WIDTH + GUTTER;
        return new LostTalesUiHitBox(left, profile.top,
                Math.max(0, (int)profile.right() - left), profile.height);
    }

    /** How far down the words' column the words start: under the figure where it stands on top. */
    public int wordsOffset() {
        return figureBeside() ? 0 : FIGURE_HEIGHT + GUTTER;
    }
}
