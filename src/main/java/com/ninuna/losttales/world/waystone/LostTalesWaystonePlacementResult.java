package com.ninuna.losttales.world.waystone;

/** Result distinguishes retryable unloaded-chunk deferral from durable blockage. */
public final class LostTalesWaystonePlacementResult {
    public enum Status { SUCCESS, DEFERRED, BLOCKED }

    private final Status status;
    private final String reason;

    private LostTalesWaystonePlacementResult(
            Status status, String reason) {
        this.status = status;
        this.reason = reason == null ? "" : reason;
    }

    public static LostTalesWaystonePlacementResult success() {
        return new LostTalesWaystonePlacementResult(Status.SUCCESS, "");
    }

    public static LostTalesWaystonePlacementResult deferred(String reason) {
        return new LostTalesWaystonePlacementResult(
                Status.DEFERRED, reason);
    }

    public static LostTalesWaystonePlacementResult blocked(String reason) {
        return new LostTalesWaystonePlacementResult(
                Status.BLOCKED, reason);
    }

    public Status getStatus() { return this.status; }
    public String getReason() { return this.reason; }
}
