package com.ninuna.losttales.compat.lotr;

import lotr.common.fellowship.LOTRFellowship;

/**
 * Called by the coremod where LOTR's own fellowship requests find their
 * fellowship, and before its request to make one. Every fellowship is the
 * mod's, kept on the Fellowships page and checked there; LOTR's screen
 * opens that page instead, and its requests check little, so each is
 * refused. LOTR's operator command works as LOTR made it.
 */
public final class LostTalesLotrFellowshipRequestHook {
    private LostTalesLotrFellowshipRequestHook() {}

    /** The fellowship one of LOTR's requests is about: none, so the request does nothing. */
    public static LOTRFellowship refuse(LOTRFellowship fellowship) {
        return null;
    }

    /** Whether LOTR's request to make a fellowship is refused: always. */
    public static boolean refusesCreation() {
        return true;
    }
}
