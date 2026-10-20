package com.ninuna.losttales.client.fellowship;

import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
import lotr.common.LOTRPlayerData;
import lotr.common.fellowship.LOTRFellowshipClient;

import java.util.ArrayList;
import java.util.UUID;

/**
 * The LOTR fellowships the player may share a waypoint into: the ones
 * behind the fellowships of the character played. The account's LOTR list
 * may also hold one it holds for the record only, while nobody plays a
 * member, and that one has no row (FEL-02). The server refuses a share
 * into it either way; until this game holds its fellowship state, LOTR's
 * own rows stand.
 */
public final class LostTalesLotrFellowshipShareRows {
    private LostTalesLotrFellowshipShareRows() {}

    /**
     * Called by the coremod where LOTR's map finds, by name, the fellowship
     * a waypoint is shared into: the first of that name the player may
     * share into, as LOTR finds it; null for none. Where that cannot be
     * read, LOTR's own answer.
     */
    public static LOTRFellowshipClient fellowshipByName(LOTRPlayerData data, String name) {
        try {
            FellowshipStateSnapshot state = ClientFellowshipStateCache.getSnapshot();
            if (state == null || !state.isAvailable()) {
                return data.getClientFellowshipByName(name);
            }
            for (LOTRFellowshipClient fellowship
                    : new ArrayList<LOTRFellowshipClient>(data.getClientFellowships())) {
                if (fellowship != null && name != null
                        && name.equalsIgnoreCase(fellowship.getName())
                        && state.mayShareInto(fellowship.getFellowshipID())) {
                    return fellowship;
                }
            }
            return null;
        } catch (RuntimeException unreadable) {
            return data.getClientFellowshipByName(name);
        }
    }

    /** Whether the player may share a waypoint into LOTR's fellowship {@code lotrFellowshipId}. */
    public static boolean mayShareInto(UUID lotrFellowshipId) {
        FellowshipStateSnapshot state = ClientFellowshipStateCache.getSnapshot();
        return state == null || !state.isAvailable() || state.mayShareInto(lotrFellowshipId);
    }
}
