package com.ninuna.losttales.accessory.effect;

/**
 * One wearer's accessory effects as the clients were told them, and what
 * the wearer wears now (ACC-08). A change goes out at once when the last
 * change went out a second or more before, else once that second is up;
 * one undone within the second is never sent. The sequence only grows, so
 * a client keeps the newest it heard.
 */
final class AccessoryEffectState {

    /** The fewest ticks between two changes sent for one wearer. */
    static final long TICKS_BETWEEN_CHANGES = 20L;

    private String worn = "";
    private String sent = "";
    private long sequence;
    private long lastChangeAt;

    /** Notes the accessory with effects the wearer wears now; empty for none. */
    void wear(String definitionId) {
        this.worn = definitionId == null ? "" : definitionId;
    }

    /** What the wearer wears now; empty for nothing with effects. */
    String worn() {
        return this.worn;
    }

    /** What the clients were last told; empty for nothing. */
    String sent() {
        return this.sent;
    }

    /** The sequence of what the clients were last told; 0 before anything was. */
    long sequence() {
        return this.sequence;
    }

    /**
     * Whether the clients are to be told what is worn now, at server tick
     * {@code now}: it differs from what they were told, and the last change
     * they were told of is a second old. When so, it counts as told.
     */
    boolean takeChange(long now) {
        if (this.worn.equals(this.sent)) {
            return false;
        }
        if (this.sequence > 0L && now - this.lastChangeAt < TICKS_BETWEEN_CHANGES) {
            return false;
        }
        tell(now);
        return true;
    }

    /**
     * Whether the clients are to be told at once that nothing is worn: a
     * death or a logout, which no wearer repeats at will. When so, it
     * counts as told.
     */
    boolean takeRemoval(long now) {
        this.worn = "";
        if (this.sent.length() == 0) {
            return false;
        }
        tell(now);
        return true;
    }

    private void tell(long now) {
        this.sent = this.worn;
        this.sequence++;
        if (this.sequence <= 0L) {
            throw new IllegalStateException("accessory effect sequence exhausted");
        }
        this.lastChangeAt = now;
    }
}
