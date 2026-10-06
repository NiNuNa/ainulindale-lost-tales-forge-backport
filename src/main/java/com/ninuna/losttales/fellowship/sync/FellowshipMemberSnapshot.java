package com.ninuna.losttales.fellowship.sync;

import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipMember;

import java.util.UUID;

/**
 * One member of a fellowship as its members' clients see it: who, whose,
 * when they joined, the colour they wear, and where they stand now, with
 * the name of the other character they play while that is elsewhere.
 */
public final class FellowshipMemberSnapshot {

    /** The longest name a member snapshot keeps, in characters. */
    public static final int MAX_NAME_LENGTH = 64;

    private final UUID identityId;
    private final UUID ownerId;
    private final String characterName;
    private final long joinedAt;
    private final FellowshipColor color;
    private final FellowshipMemberPresence presence;
    /** The character the member's account plays now, while it is another; empty otherwise. */
    private final String elsewhereName;

    public FellowshipMemberSnapshot(UUID identityId, UUID ownerId, String characterName,
                                    long joinedAt, FellowshipColor color,
                                    FellowshipMemberPresence presence,
                                    String elsewhereName) {
        if (identityId == null || ownerId == null || color == null || presence == null) {
            throw new IllegalArgumentException("fellowship member identity, color and presence must not be null");
        }
        this.identityId = identityId;
        this.ownerId = ownerId;
        // Empty where the member has no name; the client words it.
        this.characterName = normalizeName(characterName);
        this.joinedAt = Math.max(0L, joinedAt);
        this.color = color;
        this.presence = presence;
        this.elsewhereName = presence == FellowshipMemberPresence.ELSEWHERE
                ? normalizeName(elsewhereName) : "";
    }

    /** A member with where they stand, as the server sees it. */
    public static FellowshipMemberSnapshot fromMember(FellowshipMember member,
                                                      FellowshipMemberPresence presence,
                                                      String elsewhereName) {
        if (member == null) {
            throw new IllegalArgumentException("member must not be null");
        }
        return new FellowshipMemberSnapshot(member.getIdentityId(), member.getOwnerId(),
                member.getCharacterName(), member.getJoinedAt(), member.getColor(),
                presence, elsewhereName);
    }

    public UUID getIdentityId() {
        return this.identityId;
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

    public FellowshipColor getColor() {
        return this.color;
    }

    public FellowshipMemberPresence getPresence() {
        return this.presence;
    }

    /** The character the member's account plays now, while it is another; empty otherwise. */
    public String getElsewhereName() {
        return this.elsewhereName;
    }

    private static String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        return normalized.length() <= MAX_NAME_LENGTH
                ? normalized : normalized.substring(0, MAX_NAME_LENGTH);
    }
}
