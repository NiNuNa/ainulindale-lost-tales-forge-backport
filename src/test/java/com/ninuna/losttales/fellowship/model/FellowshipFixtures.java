package com.ninuna.losttales.fellowship.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Fellowships for tests: named, every switch on, no guides and no icon. */
public final class FellowshipFixtures {
    private FellowshipFixtures() {}

    public static Fellowship of(UUID fellowshipId, UUID leaderIdentityId,
                                List<FellowshipMember> members) {
        return new Fellowship(fellowshipId, leaderIdentityId, members,
                Collections.<UUID>emptyList(), "Grey Company", null,
                EnumSet.allOf(FellowshipSwitch.class), 1L, 0L,
                Fellowship.CURRENT_DATA_VERSION);
    }
}
