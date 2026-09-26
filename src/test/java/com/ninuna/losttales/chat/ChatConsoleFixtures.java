package com.ninuna.losttales.chat;

/**
 * Builds Server Console entries for tests through the entry's full
 * constructor: no command context, actor identity or report unless the
 * helper's name says so.
 */
public final class ChatConsoleFixtures {

    private ChatConsoleFixtures() {}

    /** An entry with no command context, no actor identity and no report. */
    public static ChatConsoleEvent entry(long id, long timestampMillis,
                                         ChatConsoleEvent.Kind kind,
                                         ChatConsoleEvent.Severity severity,
                                         String actor, String text) {
        return new ChatConsoleEvent(id, timestampMillis, kind, severity,
                actor, text, "", null, null);
    }

    /** A command {@code actor} ran from the tab {@code context}. */
    public static ChatConsoleEvent command(long id, long timestampMillis,
                                           String actor, String text,
                                           String context) {
        return command(id, timestampMillis, actor, text, context, null);
    }

    /** As {@link #command(long, long, String, String, String)}, with the actor's account. */
    public static ChatConsoleEvent command(long id, long timestampMillis,
                                           String actor, String text,
                                           String context,
                                           ChatNamedPlayer actorIdentity) {
        return new ChatConsoleEvent(id, timestampMillis,
                ChatConsoleEvent.Kind.COMMAND, ChatConsoleEvent.Severity.INFO,
                actor, text, context, actorIdentity, null);
    }
}
