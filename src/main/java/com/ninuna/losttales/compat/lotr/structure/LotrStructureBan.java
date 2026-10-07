package com.ninuna.losttales.compat.lotr.structure;

import java.util.UUID;

/**
 * One ban from spawning LOTR's structures: on an account, which reaches
 * every character it plays, or on one character alone, as a role is
 * given. It keeps the name it was given by and who gave it, for the list.
 */
public final class LotrStructureBan {
    /** Whom a ban reaches. */
    public enum Kind {
        ACCOUNT("account"),
        CHARACTER("character");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        /** The kind as the save writes it. */
        public String id() {
            return this.id;
        }

        /** The kind a save names, or null for one this build does not know. */
        public static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** The longest name or banner's name kept. */
    public static final int MAX_NAME_LENGTH = 64;

    private final Kind kind;
    private final UUID subjectId;
    private final String name;
    private final String bannedBy;
    private final long bannedAtMillis;

    public LotrStructureBan(Kind kind, UUID subjectId, String name,
                            String bannedBy, long bannedAtMillis) {
        if (kind == null || subjectId == null) {
            throw new IllegalArgumentException("a ban names whom it reaches");
        }
        if (bannedAtMillis < 0L) {
            throw new IllegalArgumentException("a ban's time is never before 1970");
        }
        this.kind = kind;
        this.subjectId = subjectId;
        this.name = cut(name);
        this.bannedBy = cut(bannedBy);
        this.bannedAtMillis = bannedAtMillis;
    }

    public Kind getKind() { return this.kind; }
    /** The account's id, or the character's. */
    public UUID getSubjectId() { return this.subjectId; }
    /** The name it was banned by, as the command was given it. */
    public String getName() { return this.name; }
    public String getBannedBy() { return this.bannedBy; }
    public long getBannedAtMillis() { return this.bannedAtMillis; }

    private static String cut(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() > MAX_NAME_LENGTH
                ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }
}
