package com.ninuna.losttales.fellowship.server;

/**
 * Whether a member's place goes to the others of a fellowship: its
 * position on the compass, in the HUD's order and on our map, and the
 * world it is in. Only while the fellowship shows members on the map, and
 * never for a member who is concealed or hides on LOTR's map.
 */
public final class FellowshipPlaces {

    private FellowshipPlaces() {}

    /**
     * {@code ruleOn}: the fellowship's *Show Members on the Map*;
     * {@code hidden}: the member hides on LOTR's map.
     */
    public static boolean shared(boolean ruleOn, boolean concealed, boolean hidden) {
        return ruleOn && !concealed && !hidden;
    }
}
