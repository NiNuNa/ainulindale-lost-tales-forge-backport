package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.validation.CharacterErrorId;

/** Result returned by authoritative character-management operations. */
public final class CharacterOperationResult {

    private final boolean successful;
    private final boolean changed;
    private final CharacterErrorId errorId;
    private final CharacterRoster roster;
    private final RoleplayCharacter character;

    private CharacterOperationResult(boolean successful, boolean changed,
                                     CharacterErrorId errorId,
                                     CharacterRoster roster,
                                     RoleplayCharacter character) {
        this.successful = successful;
        this.changed = changed;
        this.errorId = errorId == null ? CharacterErrorId.INTERNAL_ERROR : errorId;
        this.roster = roster;
        this.character = character;
    }

    public static CharacterOperationResult success(boolean changed,
                                                   CharacterRoster roster,
                                                   RoleplayCharacter character) {
        return new CharacterOperationResult(true, changed, CharacterErrorId.NONE,
                roster, character);
    }

    public static CharacterOperationResult failure(CharacterErrorId errorId,
                                                   CharacterRoster roster) {
        if (errorId == null || errorId == CharacterErrorId.NONE) {
            throw new IllegalArgumentException("failure requires a non-success error id");
        }
        return new CharacterOperationResult(false, false, errorId, roster, null);
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public boolean wasChanged() {
        return this.changed;
    }

    public CharacterErrorId getErrorId() {
        return this.errorId;
    }

    public CharacterRoster getRoster() {
        return this.roster;
    }

    public RoleplayCharacter getCharacter() {
        return this.character;
    }

    public long getRosterRevision() {
        return this.roster == null ? -1L : this.roster.getRevision();
    }
}
