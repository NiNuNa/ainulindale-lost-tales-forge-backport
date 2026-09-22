package com.ninuna.losttales.compat.discord;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The codes that pair a Discord channel with a game channel. An operator
 * asks for one in the game ({@code /losttales discord link ooc}); an admin
 * of the Discord server types it into the channel to link
 * ({@code /link code:ABCD-EFGH}), and the bot makes the channel's webhook
 * itself. A code names the game channel and the direction the link will
 * carry, works once, and runs out after {@link #LIFETIME_MILLIS}; at most
 * {@link #MAX_PENDING} wait at a time, the oldest going first. Codes are
 * drawn from a secure random source over an alphabet without the letters
 * that read alike (no 0 or O, no 1 or I), eight letters long: far more
 * than anyone could guess in the minutes one lives. Server state, cleared
 * with the rest of the server's when it starts and stops.
 */
public final class DiscordLinkCodes {
    public static final long LIFETIME_MILLIS = 10L * 60L * 1000L;
    static final int MAX_PENDING = 32;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** The codes waiting, by their letters without the dash, oldest first. */
    private static final Map<String, Pending> PENDING = new LinkedHashMap<String, Pending>();

    private DiscordLinkCodes() {}

    /** What a code will link, and who asked for it. */
    public static final class Pending {
        /** The game channel's binding key: {@code ooc}, {@code faction:lotr:gondor}. */
        public final String gameKey;
        public final DiscordBridgeDirection direction;
        /** The account that asked; null for the server's own console. */
        public final UUID issuer;
        /** The name of whoever asked, as the Server Console names them. */
        public final String issuerName;
        final long expiresMillis;

        Pending(String gameKey, DiscordBridgeDirection direction, UUID issuer,
                String issuerName, long expiresMillis) {
            this.gameKey = gameKey;
            this.direction = direction;
            this.issuer = issuer;
            this.issuerName = issuerName == null ? "" : issuerName;
            this.expiresMillis = expiresMillis;
        }
    }

    /** A new code for {@code gameKey}, as it is typed: {@code ABCD-EFGH}. */
    public static synchronized String issue(String gameKey, DiscordBridgeDirection direction,
                                            UUID issuer, String issuerName,
                                            long nowMillis) {
        prune(nowMillis);
        String letters;
        do {
            StringBuilder code = new StringBuilder(LENGTH);
            for (int index = 0; index < LENGTH; index++) {
                code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            letters = code.toString();
        } while (PENDING.containsKey(letters));
        PENDING.put(letters, new Pending(gameKey, direction, issuer, issuerName,
                nowMillis + LIFETIME_MILLIS));
        Iterator<String> oldest = PENDING.keySet().iterator();
        while (PENDING.size() > MAX_PENDING && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
        return letters.substring(0, LENGTH / 2) + "-" + letters.substring(LENGTH / 2);
    }

    /** What a typed code would link, still waiting; null for none. */
    public static synchronized Pending peek(String typed, long nowMillis) {
        prune(nowMillis);
        return PENDING.get(normalized(typed));
    }

    /** What a typed code links, spent as it is taken; null for none. */
    public static synchronized Pending take(String typed, long nowMillis) {
        prune(nowMillis);
        return PENDING.remove(normalized(typed));
    }

    public static synchronized void clear() {
        PENDING.clear();
    }

    /** The letters of a code as typed: case, spaces and the dash do not matter. */
    static String normalized(String typed) {
        if (typed == null || typed.length() > 4 * LENGTH) {
            return "";
        }
        StringBuilder letters = new StringBuilder(LENGTH);
        String upper = typed.toUpperCase(Locale.ROOT);
        for (int index = 0; index < upper.length(); index++) {
            char letter = upper.charAt(index);
            if (ALPHABET.indexOf(letter) >= 0) {
                letters.append(letter);
            } else if (letter != '-' && letter != ' ') {
                return "";
            }
        }
        return letters.length() == LENGTH ? letters.toString() : "";
    }

    private static void prune(long nowMillis) {
        Iterator<Pending> waiting = PENDING.values().iterator();
        while (waiting.hasNext()) {
            if (waiting.next().expiresMillis <= nowMillis) {
                waiting.remove();
            }
        }
    }
}
