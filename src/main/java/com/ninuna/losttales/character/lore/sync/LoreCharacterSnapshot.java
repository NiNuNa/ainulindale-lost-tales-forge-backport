package com.ninuna.losttales.character.lore.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Complete private view of lore definitions and current world ownership. */
public final class LoreCharacterSnapshot {

    private final List<LoreCharacterSummary> characters;
    private final Map<String, LoreCharacterSummary> byId;
    private final boolean ownershipReadOnly;
    private final boolean transferReadOnly;

    public LoreCharacterSnapshot(
            List<LoreCharacterSummary> characters,
            boolean ownershipReadOnly,
            boolean transferReadOnly) {
        ArrayList<LoreCharacterSummary> accepted =
                new ArrayList<LoreCharacterSummary>();
        LinkedHashMap<String, LoreCharacterSummary> indexed =
                new LinkedHashMap<String, LoreCharacterSummary>();
        if (characters != null) {
            for (LoreCharacterSummary character : characters) {
                if (character != null && character.getId().length() > 0
                        && !indexed.containsKey(character.getId())) {
                    accepted.add(character);
                    indexed.put(character.getId(), character);
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
        this.byId = Collections.unmodifiableMap(indexed);
        this.ownershipReadOnly = ownershipReadOnly;
        this.transferReadOnly = transferReadOnly;
    }

    public List<LoreCharacterSummary> getCharacters() { return this.characters; }
    public LoreCharacterSummary get(String id) {
        return id == null ? null : this.byId.get(id);
    }
    public boolean isOwnershipReadOnly() { return this.ownershipReadOnly; }
    public boolean isTransferReadOnly() { return this.transferReadOnly; }
    public boolean canMutate() {
        return !this.ownershipReadOnly && !this.transferReadOnly;
    }
}
