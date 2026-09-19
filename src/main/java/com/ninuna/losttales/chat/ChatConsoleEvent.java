package com.ninuna.losttales.chat;

/**
 * One entry of the Server Console: something administrative
 * that happened on the server, said once for every staff member to
 * read. Not a chat message — nobody signs it, nobody replies to it,
 * nothing bridges it — and not a mirror of the server log either: only
 * what a moderator has a use for becomes an entry, and every entry is
 * built by the server from facts it holds, never from a line a client
 * typed. Free of Minecraft imports: the packet and the client read the
 * same class.
 */
public final class ChatConsoleEvent {

    /** What kind of thing happened; the client labels and colours by it. */
    public enum Kind {
        /** A command a player or the console ran. */
        COMMAND,
        /** A mute, an unmute, a message taken back by a moderator. */
        MODERATION,
        /** A role made, changed, deleted, given or taken. */
        ROLES,
        /** A server setting changed live. */
        CONFIG,
        /** The server itself: started or stopped, a bridge came up. */
        SERVER,
        /** Something the mod could not do and staff should know about. */
        WARNING;

        /** The kind at that ordinal, or null for none. */
        public static Kind fromOrdinal(int ordinal) {
            Kind[] kinds = values();
            return ordinal < 0 || ordinal >= kinds.length ? null : kinds[ordinal];
        }
    }

    /** How loudly the entry reads. */
    public enum Severity {
        INFO,
        NOTICE,
        WARNING;

        public static Severity fromOrdinal(int ordinal) {
            Severity[] severities = values();
            return ordinal < 0 || ordinal >= severities.length ? null
                    : severities[ordinal];
        }
    }

    public static final int MAX_ACTOR_LENGTH = 64;
    public static final int MAX_TEXT_LENGTH = 512;
    /**
     * The longest tab id a command's context may name: a whisper
     * conversation's id carries two names and a character id.
     */
    public static final int MAX_CONTEXT_LENGTH = 384;

    private final long id;
    private final long timestampMillis;
    private final Kind kind;
    private final Severity severity;
    /** Who did it: an account name, {@code Server} for the console, empty for nobody. */
    private final String actor;
    /**
     * The actor's account as the server knew it when the entry was made
     * — its id and the colour its name wears out of character — or null
     * for the Server, for nobody, and for an account that was not on
     * the server. What lets an entry's mention open the actor's card and
     * menu, in the colour their name wears, long after they have gone.
     */
    private final ChatNamedPlayer actorIdentity;
    private final String text;
    /**
     * For a command, the id of the tab the actor typed it in, as their
     * client reported it; empty when unknown — a command run from the
     * server's own console, or by a client that said nothing.
     */
    private final String context;

    public ChatConsoleEvent(long id, long timestampMillis, Kind kind,
                            Severity severity, String actor, String text) {
        this(id, timestampMillis, kind, severity, actor, text, "");
    }

    public ChatConsoleEvent(long id, long timestampMillis, Kind kind,
                            Severity severity, String actor, String text,
                            String context) {
        this(id, timestampMillis, kind, severity, actor, text, context, null);
    }

    /**
     * As above with the actor's account as the server knew it; an
     * identity naming another account than {@code actor} is dropped.
     */
    public ChatConsoleEvent(long id, long timestampMillis, Kind kind,
                            Severity severity, String actor, String text,
                            String context, ChatNamedPlayer actorIdentity) {
        if (kind == null || severity == null) {
            throw new IllegalArgumentException("a console event has a kind and a severity");
        }
        this.id = id;
        this.timestampMillis = timestampMillis;
        this.kind = kind;
        this.severity = severity;
        this.actor = clip(actor, MAX_ACTOR_LENGTH);
        this.actorIdentity = actorIdentity != null
                && actorIdentity.getPlayerId() != null
                && this.actor.length() > 0
                && actorIdentity.getAccount().equalsIgnoreCase(this.actor)
                ? actorIdentity : null;
        this.text = clip(text, MAX_TEXT_LENGTH);
        if (this.text.length() == 0) {
            throw new IllegalArgumentException("a console event says something");
        }
        String where = context == null ? "" : context.trim();
        // A context that would not be a tab id names nothing.
        this.context = kind == Kind.COMMAND && isContext(where)
                && where.length() <= MAX_CONTEXT_LENGTH ? where : "";
    }

    /**
     * Whether a string may stand as a command's context: one line of
     * printable text, nothing that could break a line or a log.
     */
    public static boolean isContext(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) < ' ') {
                return false;
            }
        }
        return true;
    }

    public long getId() { return this.id; }
    public long getTimestampMillis() { return this.timestampMillis; }
    public Kind getKind() { return this.kind; }
    public Severity getSeverity() { return this.severity; }
    public String getActor() { return this.actor; }
    /** The actor's account as the server knew it, or null; see {@link #actorIdentity}. */
    public ChatNamedPlayer getActorIdentity() { return this.actorIdentity; }
    public String getText() { return this.text; }
    /** The tab a command was typed in, or empty; see {@link #context}. */
    public String getContext() { return this.context; }

    private static String clip(String value, int maximum) {
        String text = value == null ? "" : value.trim();
        return text.length() > maximum ? text.substring(0, maximum) : text;
    }

    @Override
    public String toString() {
        return this.kind + "/" + this.severity + " " + this.actor + ": " + this.text;
    }
}
