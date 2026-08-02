package com.ninuna.losttales.client.mapmarker;

/** Pure delayed-hover state used by the map's single focus owner. */
final class LostTalesMapHoverFocus {
    static final long DEFAULT_DELAY_NANOS = 80000000L;

    private final long delayNanos;
    private String pendingKey = "";
    private String activeKey = "";
    private long pendingSinceNanos;

    LostTalesMapHoverFocus() {
        this(DEFAULT_DELAY_NANOS);
    }

    LostTalesMapHoverFocus(long delayNanos) {
        this.delayNanos = Math.max(0L, delayNanos);
    }

    String update(String candidateKey, long nowNanos) {
        String normalized = normalize(candidateKey);
        if (normalized.length() == 0) {
            clear();
            return this.activeKey;
        }
        if (!normalized.equals(this.pendingKey)) {
            this.pendingKey = normalized;
            this.pendingSinceNanos = nowNanos;
            this.activeKey = "";
        } else if (!normalized.equals(this.activeKey)
                && elapsed(nowNanos, this.pendingSinceNanos)
                        >= this.delayNanos) {
            this.activeKey = normalized;
        }
        return this.activeKey;
    }

    void clear() {
        this.pendingKey = "";
        this.activeKey = "";
        this.pendingSinceNanos = 0L;
    }

    String getActiveKey() {
        return this.activeKey;
    }

    private static long elapsed(long now, long since) {
        long elapsed = now - since;
        return elapsed < 0L ? Long.MAX_VALUE : elapsed;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
