package com.ninuna.losttales.chat;

import java.util.Locale;

/**
 * One way an account comes to hold a role, as the config states it: an
 * operator permission level, or a LOTR faction rank reached by the
 * identity being played. Assigned members are kept apart, on the
 * catalogue, since they are a list of accounts rather than a rule. The
 * server evaluates sources; a client never sees them.
 */
public final class ChatRoleSource {

    public enum Kind { OP_LEVEL, FACTION_RANK }

    private final Kind kind;
    private final int level;
    private final String faction;
    private final String rank;

    private ChatRoleSource(Kind kind, int level, String faction, String rank) {
        this.kind = kind;
        this.level = level;
        this.faction = faction == null ? "" : faction;
        this.rank = rank == null ? "" : rank;
    }

    /** Held by anyone the server lets use commands of this level. */
    public static ChatRoleSource opLevel(int level) {
        return new ChatRoleSource(Kind.OP_LEVEL, Math.max(0, Math.min(4, level)), "", "");
    }

    /**
     * Held while the identity being played has reached {@code rank}
     * (a LOTR rank code name such as {@code gondor.knight}) with the
     * faction named by its LOTR code name.
     */
    public static ChatRoleSource factionRank(String faction, String rank) {
        return new ChatRoleSource(Kind.FACTION_RANK, 0,
                faction.trim().toUpperCase(Locale.ROOT), rank.trim().toLowerCase(Locale.ROOT));
    }

    public Kind getKind() {
        return this.kind;
    }

    public int getLevel() {
        return this.level;
    }

    public String getFaction() {
        return this.faction;
    }

    public String getRank() {
        return this.rank;
    }

    /** The config form: {@code op:<level>} or {@code faction:<FACTION>@<rank>}. */
    public String toConfigOption() {
        return this.kind == Kind.OP_LEVEL ? "op:" + this.level
                : "faction:" + this.faction + "@" + this.rank;
    }

    @Override
    public String toString() {
        return toConfigOption();
    }
}
