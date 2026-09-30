package com.ninuna.losttales.compat.discord;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Discord members typing in a linked channel, so the game shows them
 * typing as it shows a player. Discord says a
 * member is typing about every ten seconds while they are, and the game
 * forgets a typer after six, so each is told again every
 * {@link #RESEND_MILLIS} until {@link #SHOWN_MILLIS} after Discord last
 * said so, or until their message arrives. At most {@link #MAX_TYPERS} at
 * once, the oldest going first. Kept by the bridge and cleared with it.
 */
final class DiscordTypers {
    static final long SHOWN_MILLIS = 10000L;
    static final long RESEND_MILLIS = 5000L;
    static final int MAX_TYPERS = 64;

    /** One member typing in one Discord channel. */
    static final class Typer {
        final String discordChannelId;
        final String authorId;
        final String name;
        long until;
        long nextSend;

        Typer(String discordChannelId, String authorId, String name) {
            this.discordChannelId = discordChannelId;
            this.authorId = authorId;
            this.name = name;
        }
    }

    private final Map<String, Typer> typers = new LinkedHashMap<String, Typer>();

    /**
     * Discord says the member is typing at {@code now}: the typer to tell
     * the game about now, or null when the game was told a moment ago.
     */
    synchronized Typer typing(String discordChannelId, String authorId, String name,
                              long now) {
        String key = discordChannelId + ">" + authorId;
        Typer typer = this.typers.remove(key);
        if (typer == null) {
            typer = new Typer(discordChannelId, authorId, name);
        }
        typer.until = now + SHOWN_MILLIS;
        this.typers.put(key, typer);
        Iterator<String> oldest = this.typers.keySet().iterator();
        while (this.typers.size() > MAX_TYPERS && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
        if (now < typer.nextSend) {
            return null;
        }
        typer.nextSend = now + RESEND_MILLIS;
        return typer;
    }

    /** The typers to tell again at {@code now}; those whose time is up are let go. */
    synchronized List<Typer> due(long now) {
        List<Typer> due = new ArrayList<Typer>();
        Iterator<Typer> each = this.typers.values().iterator();
        while (each.hasNext()) {
            Typer typer = each.next();
            if (now >= typer.until) {
                each.remove();
            } else if (now >= typer.nextSend) {
                typer.nextSend = now + RESEND_MILLIS;
                due.add(typer);
            }
        }
        return due;
    }

    /** The member's message arrived: the typer it ends, or null for none. */
    synchronized Typer stopped(String discordChannelId, String authorId) {
        return this.typers.remove(discordChannelId + ">" + authorId);
    }

    synchronized void clear() {
        this.typers.clear();
    }
}
