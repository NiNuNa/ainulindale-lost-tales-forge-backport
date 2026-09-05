package com.ninuna.losttales.compat.lotr.hired;

import java.nio.charset.Charset;
import java.util.UUID;

/**
 * What to do with one hired unit given who hired it and who its owner is
 * playing as. A unit whose identity is the active one is released to the
 * owner's own UUID, so LOTR treats the owner as present; any other unit of
 * that owner is parked under a UUID no player has, so LOTR treats the
 * owner as offline and the unit holds still. Nothing here touches the
 * world; the custody service applies the answer.
 */
public final class LotrHiredUnitCustodyRule {

    public enum Action { RELEASE, PARK, LEAVE }

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private LotrHiredUnitCustodyRule() {}

    /** The UUID an owner's parked units are hired under; one per account. */
    public static UUID parkedUuid(UUID ownerId) {
        return UUID.nameUUIDFromBytes(("losttales:parked:" + ownerId).getBytes(UTF_8));
    }

    /**
     * The action for a unit hired by {@code tagIdentity} while its owner is
     * playing as {@code activeIdentity} (null while no identity is active,
     * which is the middle of a switch) and the unit is currently hired
     * under {@code hiringUuid}.
     */
    public static Action decide(String tagIdentity, String activeIdentity,
                                UUID ownerId, UUID hiringUuid) {
        if (activeIdentity != null && activeIdentity.equals(tagIdentity)) {
            return ownerId.equals(hiringUuid) ? Action.LEAVE : Action.RELEASE;
        }
        return parkedUuid(ownerId).equals(hiringUuid) ? Action.LEAVE : Action.PARK;
    }

    /** The UUID the unit is hired under after the action. */
    public static UUID hiringUuidAfter(Action action, UUID ownerId, UUID current) {
        switch (action) {
            case RELEASE:
                return ownerId;
            case PARK:
                return parkedUuid(ownerId);
            default:
                return current;
        }
    }
}
