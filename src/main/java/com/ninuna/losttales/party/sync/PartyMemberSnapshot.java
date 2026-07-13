package com.ninuna.losttales.party.sync;

import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.model.PartyMember;

import java.util.UUID;

/** Immutable client-safe projection of one authorized party member. */
public final class PartyMemberSnapshot {

    private final UUID characterId;
    private final UUID ownerId;
    private final String characterName;
    private final long joinedAt;
    private final PartyColor color;

    public PartyMemberSnapshot(UUID characterId,
                               UUID ownerId,
                               String characterName,
                               long joinedAt,
                               PartyColor color) {
        if (characterId == null || ownerId == null || color == null) {
            throw new IllegalArgumentException("party member identity and color must not be null");
        }
        this.characterId = characterId;
        this.ownerId = ownerId;
        this.characterName = normalizeName(characterName);
        this.joinedAt = Math.max(0L, joinedAt);
        this.color = color;
    }

    public static PartyMemberSnapshot fromMember(PartyMember member) {
        if (member == null) {
            throw new IllegalArgumentException("member must not be null");
        }
        return new PartyMemberSnapshot(
                member.getCharacterId(),
                member.getOwnerId(),
                member.getCharacterName(),
                member.getJoinedAt(),
                member.getColor());
    }

    public UUID getCharacterId() {
        return this.characterId;
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public String getCharacterName() {
        return this.characterName;
    }

    public long getJoinedAt() {
        return this.joinedAt;
    }

    public PartyColor getColor() {
        return this.color;
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "Unknown";
        }
        String normalized = name.trim();
        if (normalized.length() == 0) {
            return "Unknown";
        }
        return normalized.length() <= 64
                ? normalized : normalized.substring(0, 64);
    }
}
