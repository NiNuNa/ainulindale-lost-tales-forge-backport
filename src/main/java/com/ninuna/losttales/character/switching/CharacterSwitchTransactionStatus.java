package com.ninuna.losttales.character.switching;

/** Durable phases for one server-authoritative character switch. */
public enum CharacterSwitchTransactionStatus {
    PREPARED("prepared"),
    COMMITTED("committed"),
    ABORTED("aborted"),
    RECOVERY_REQUIRED("recovery_required");

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
