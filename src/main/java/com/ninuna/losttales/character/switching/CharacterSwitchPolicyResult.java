package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.character.validation.CharacterErrorId;

/** Structured server-side switching-policy decision. */
public final class CharacterSwitchPolicyResult {

    private final boolean allowed;
    private final CharacterErrorId errorId;
    private final long retryAtEpochMillis;

    private CharacterSwitchPolicyResult(boolean allowed, CharacterErrorId errorId,
                                        long retryAtEpochMillis) {
        this.allowed = allowed;
        this.errorId = errorId == null ? CharacterErrorId.INTERNAL_ERROR : errorId;
        this.retryAtEpochMillis = retryAtEpochMillis;
    }

    public static CharacterSwitchPolicyResult allowed() {
        return new CharacterSwitchPolicyResult(true, CharacterErrorId.NONE, -1L);
    }

    public static CharacterSwitchPolicyResult denied(CharacterErrorId errorId) {
        return denied(errorId, -1L);
    }

    public static CharacterSwitchPolicyResult denied(CharacterErrorId errorId,
                                                       long retryAtEpochMillis) {
        if (errorId == null || errorId == CharacterErrorId.NONE) {
            throw new IllegalArgumentException("Denied policy result requires an error");
        }
        return new CharacterSwitchPolicyResult(false, errorId, retryAtEpochMillis);
    }

    public boolean isAllowed() { return this.allowed; }
    public CharacterErrorId getErrorId() { return this.errorId; }
    public long getRetryAtEpochMillis() { return this.retryAtEpochMillis; }
}
