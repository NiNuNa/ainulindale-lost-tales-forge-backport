package com.ninuna.losttales.compat.lotr;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** What a mirror's shape holds, for the tests of the fellowships that make one. */
public final class MirrorShapes {
    private MirrorShapes() {}

    public static UUID owner(LotrFellowshipMirror.Shape shape) {
        return shape.owner;
    }

    public static boolean ownerPlays(LotrFellowshipMirror.Shape shape) {
        return shape.ownerPlays;
    }

    public static List<UUID> members(LotrFellowshipMirror.Shape shape) {
        return shape.members;
    }

    public static Set<UUID> admins(LotrFellowshipMirror.Shape shape) {
        return shape.admins;
    }
}
