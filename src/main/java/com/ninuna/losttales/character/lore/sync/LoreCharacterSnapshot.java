package com.ninuna.losttales.character.lore.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Complete private view of lore definitions and current world ownership. */
public final class LoreCharacterSnapshot {

    private final List<LoreCharacterSummary> characters;
    private final boolean ownershipReadOnly;
    private final boolean transferReadOnly;

    public LoreCharacterSnapshot(
            List<LoreCharacterSummary> characters,
            boolean ownershipReadOnly,
            boolean transferReadOnly) {
        ArrayList<LoreCharacterSummary> accepted =
                new ArrayList<LoreCharacterSummary>();
        Set<String> ids = new HashSet<String>();
        if (characters != null) {
            for (LoreCharacterSummary character : characters) {
                if (character != null && character.getId().length() > 0
                        && ids.add(character.getId())) {
                    accepted.add(character);
                }
            }
        }
        Collections.sort(accepted, new Comparator<LoreCharacterSummary>() {
            @Override public int compare(LoreCharacterSummary a,
                                         LoreCharacterSummary b) {
                int name = a.getName().compareToIgnoreCase(b.getName());
                return name != 0 ? name : a.getId().compareTo(b.getId());
            }
        });
        this.characters = Collections.unmodifiableList(accepted);
        this.ownershipReadOnly = ownershipReadOnly;
        this.transferReadOnly = transferReadOnly;
    }

    public List<LoreCharacterSummary> getCharacters() { return this.characters; }
    public boolean isOwnershipReadOnly() { return this.ownershipReadOnly; }
    public boolean isTransferReadOnly() { return this.transferReadOnly; }
    public boolean canMutate() {
        return !this.ownershipReadOnly && !this.transferReadOnly;
    }
}
