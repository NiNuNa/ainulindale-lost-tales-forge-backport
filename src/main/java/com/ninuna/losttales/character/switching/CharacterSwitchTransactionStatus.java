package com.ninuna.losttales.character.switching;

/**
 * How far a switch got. The last two hold the journal: nothing settles it
 * by itself, and switching stays off while it stands.
 */
public enum CharacterSwitchTransactionStatus {
    PREPARED("prepared"),
    COMMITTED("committed"),
    ABORTED("aborted"),
    /** An attempt that failed part-way, held for an operator. */
    RECOVERY_REQUIRED("recovery_required"),
    /**
     * Discarded by an operator: the character the switch was leaving comes
     * back as the journal's source generation holds it (CHAR-37).
     */
    RESTORE_SOURCE("restore_source");

    private final String id;

    CharacterSwitchTransactionStatus(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public static CharacterSwitchTransactionStatus fromId(String id) {
        if (id != null) {
            for (CharacterSwitchTransactionStatus value : values()) {
                if (value.id.equals(id)) {
                    return value;
                }
            }
        }
        return RECOVERY_REQUIRED;
    }
}
