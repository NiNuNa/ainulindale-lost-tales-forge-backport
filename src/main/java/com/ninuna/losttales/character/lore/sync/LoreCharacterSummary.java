package com.ninuna.losttales.character.lore.sync;

import java.util.UUID;

/** One lore character as one player sees it: who it is, and whether it is free, claimed or theirs. */
public final class LoreCharacterSummary {

    private final String id;
    private final String name;
    private final String description;
    private final String raceId;
    private final boolean configured;
    private final boolean claimed;
    private final boolean ownedByViewer;
    private final boolean transferInProgress;
    private final String ownerName;
    private final UUID ownedCharacterId;
    private final long ownershipRevision;

    public LoreCharacterSummary(
            String id, String name, String description, String raceId,
            boolean configured, boolean claimed, boolean ownedByViewer,
            boolean transferInProgress, String ownerName,
            UUID ownedCharacterId, long ownershipRevision) {
        this.id = safe(id);
        this.name = safe(name);
        this.description = safe(description);
        this.raceId = safe(raceId);
        this.configured = configured;
        this.claimed = claimed;
        this.ownedByViewer = claimed && ownedByViewer;
        this.transferInProgress = transferInProgress;
        this.ownerName = claimed ? safe(ownerName) : "";
        this.ownedCharacterId = this.ownedByViewer ? ownedCharacterId : null;
        this.ownershipRevision = Math.max(0L, ownershipRevision);
    }

    public String getId() { return this.id; }
    public String getName() { return this.name; }
    public String getDescription() { return this.description; }
    public String getRaceId() { return this.raceId; }
    public boolean isConfigured() { return this.configured; }
    public boolean isClaimed() { return this.claimed; }
    public boolean isAvailable() {
        return this.configured && !this.claimed && !this.transferInProgress;
    }
    public boolean isOwnedByViewer() { return this.ownedByViewer; }
    public boolean isTransferInProgress() { return this.transferInProgress; }
    public String getOwnerName() { return this.ownerName; }
    public UUID getOwnedCharacterId() { return this.ownedCharacterId; }
    public long getOwnershipRevision() { return this.ownershipRevision; }

    private static String safe(String value) { return value == null ? "" : value; }
}
