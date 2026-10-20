package com.ninuna.losttales.compat.lotr;

import lotr.common.LOTRPlayerData;
import lotr.common.fellowship.LOTRFellowship;
import lotr.common.fellowship.LOTRFellowshipData;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Called by the coremod where LOTR's own fellowship requests find their
 * fellowship, before its request to make one, and where its request to
 * share a waypoint finds the fellowship it names. Every fellowship is the
 * mod's, kept on the Fellowships page and checked there; LOTR's screen
 * opens that page instead, and its requests check little, so each is
 * refused. LOTR's {@code /fellowship} is removed at server start
 * ({@link LotrCommands}).
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

    /**
     * The fellowship LOTR's request to share a waypoint, or to stop sharing
     * it, names: the first of the player's own of that name, as LOTR finds
     * it, passing over one the account holds for the record only (FEL-02).
     * Where that cannot be read, LOTR's own answer.
     */
    public static LOTRFellowship shareInto(LOTRPlayerData data, String name) {
        try {
            List<LOTRFellowship> listed = new ArrayList<LOTRFellowship>();
            for (UUID id : new ArrayList<UUID>(data.getFellowshipIDs())) {
                LOTRFellowship fellowship = LOTRFellowshipData.getActiveFellowship(id);
                if (fellowship != null) {
                    listed.add(fellowship);
                }
            }
            return firstToShareInto(listed, name, LotrFellowshipMirror.heldForTheRecordIds());
        } catch (RuntimeException unreadable) {
            return data.getFellowshipByName(name);
        }
    }

    /**
     * The first of {@code listed} named {@code name}, whatever its case, that
     * is not in {@code heldForTheRecord}; null for none.
     */
    static LOTRFellowship firstToShareInto(List<LOTRFellowship> listed, String name,
                                           Set<UUID> heldForTheRecord) {
        if (name == null) {
            return null;
        }
        for (LOTRFellowship fellowship : listed) {
            if (name.equalsIgnoreCase(fellowship.getName())
                    && !heldForTheRecord.contains(fellowship.getFellowshipID())) {
                return fellowship;
            }
        }
        return null;
    }
}
