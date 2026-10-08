package com.ninuna.losttales.gui.style;

import com.ninuna.losttales.config.LostTalesConfig;

/**
 * The windows' three colours, each a palette entry the player picks in
 * Window Settings:
 * <ul>
 * <li>the primary, every window's and sub-window's surface, the member
 * list's, a popup's (plum black as the mod ships);</li>
 * <li>the secondary, the tool strip, the bars, a sub-window's strip, a
 * tab in front, hairlines, a scrollbar's track and every lit row (plum
 * grey);</li>
 * <li>the accent, what lights up and leads the eye: lit glyphs, the fade
 * where a list goes on, the line two windows line up on, what the Server
 * and the Client say (honey).</li>
 * </ul>
 * Asked for here every time rather than kept by each drawer, so a pick
 * reaches every window at once. Colours that mean something (a mention, a
 * faction, a quest's state) keep their own.
 */
public final class LostTalesUiTheme {
    private static String primaryName;
    private static String secondaryName;
    private static String accentName;
    private static int primary;
    private static int secondary;
    private static int accent;
    /** Steps on whenever a colour changes, for what is built from them. */
    private static int revision;

    private LostTalesUiTheme() {}

    /** The surface of every window, sub-window and popup, alpha-free. */
    public static int primaryRgb() {
        refresh();
        return primary;
    }

    /** The strips, bars and lit rows of every window, alpha-free. */
    public static int secondaryRgb() {
        refresh();
        return secondary;
    }

    /** What lights up and leads the eye, alpha-free. */
    public static int accentRgb() {
        refresh();
        return accent;
    }

    /** The accent's palette name, as the sheet's ink is worked out from it. */
    public static String accentName() {
        refresh();
        return accentName;
    }

    /** A number that changes whenever one of the three colours does. */
    public static int revision() {
        refresh();
        return revision;
    }

    /**
     * Reads the three names again where the config holds others than last
     * time. The config replaces a name whenever it changes, so comparing
     * the strings themselves is enough.
     */
    private static synchronized void refresh() {
        String primaryNow = LostTalesConfig.windowPrimaryColor;
        String secondaryNow = LostTalesConfig.windowSecondaryColor;
        String accentNow = LostTalesConfig.windowAccentColor;
        if (primaryNow == primaryName && secondaryNow == secondaryName
                && accentNow == accentName) {
            return;
        }
        primaryName = primaryNow;
        secondaryName = secondaryNow;
        accentName = accentNow;
        primary = LostTalesColors.rgb(LostTalesColors.paletteColor(primaryNow,
                LostTalesColors.PLUM_BLACK));
        secondary = LostTalesColors.rgb(LostTalesColors.paletteColor(
                secondaryNow, LostTalesColors.PLUM_GRAY));
        accent = LostTalesColors.rgb(LostTalesColors.paletteColor(accentNow,
                LostTalesColors.HONEY));
        revision++;
    }
}
