package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipMark;
import com.ninuna.losttales.fellowship.model.FellowshipMember;

/** Result of one atomic server-side fellowship operation. */
public final class FellowshipOperationResult {

    private final boolean successful;
    private final boolean changed;
    private final boolean fellowshipDisbanded;
    private final FellowshipErrorId errorId;
    private final Fellowship fellowship;
    private final FellowshipMember affectedMember;
    private final FellowshipMark mark;

    private FellowshipOperationResult(boolean successful, boolean changed,
                                 boolean fellowshipDisbanded, FellowshipErrorId errorId,
                                 Fellowship fellowship, FellowshipMember affectedMember,
                                 FellowshipMark mark) {
        this.successful = successful;
        this.changed = changed;
        this.fellowshipDisbanded = fellowshipDisbanded;
        this.errorId = errorId == null ? FellowshipErrorId.INTERNAL_ERROR : errorId;
        this.fellowship = fellowship;
        this.affectedMember = affectedMember;
        this.mark = mark;
    }

    public static FellowshipOperationResult success(boolean changed, Fellowship fellowship,
                                               FellowshipMember affectedMember) {
        return new FellowshipOperationResult(true, changed, false,
                FellowshipErrorId.NONE, fellowship, affectedMember, null);
    }

    /** A mark placed, moved or removed: the mark as it stands now, or as it stood last. */
    public static FellowshipOperationResult markChanged(Fellowship fellowship,
                                                        FellowshipMember actor,
                                                        FellowshipMark mark) {
        return new FellowshipOperationResult(true, true, false,
                FellowshipErrorId.NONE, fellowship, actor, mark);
    }

    /** The fellowship ended: {@code fellowship} as it stood last, so its members are told. */
    public static FellowshipOperationResult disbanded(Fellowship fellowship,
                                                      FellowshipMember affectedMember) {
        return new FellowshipOperationResult(true, true, true,
                FellowshipErrorId.NONE, fellowship, affectedMember, null);
    }

    public static FellowshipOperationResult failure(FellowshipErrorId errorId, Fellowship fellowship) {
        if (errorId == null || errorId == FellowshipErrorId.NONE) {
            errorId = FellowshipErrorId.INTERNAL_ERROR;
        }
        return new FellowshipOperationResult(false, false, false,
                errorId, fellowship, null, null);
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public boolean wasChanged() {
        return this.changed;
    }

    public boolean wasFellowshipDisbanded() {
        return this.fellowshipDisbanded;
    }

    public FellowshipErrorId getErrorId() {
        return this.errorId;
    }

    public Fellowship getFellowship() {
        return this.fellowship;
    }

    public FellowshipMember getAffectedMember() {
        return this.affectedMember;
    }

    /** The mark a mark's operation changed; null for every other operation. */
    public FellowshipMark getMark() {
        return this.mark;
    }
}
