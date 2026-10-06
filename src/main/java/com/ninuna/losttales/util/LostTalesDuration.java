package com.ninuna.losttales.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * A length of time in words: counts of days, hours, minutes or seconds,
 * each written with its unit's lang line ({@code 3h}) and joined by the
 * join line ({@code 3h 20m}). The caller decides which parts a duration
 * has; every quest timer, mute, cooldown and retention period writes them
 * through here, so a language words its units once.
 */
public final class LostTalesDuration {
    /** What the duration lines' keys begin with. */
    public static final String PREFIX = "losttales.duration.";
    /** The line two parts are joined by: {@code %s %s}. */
    public static final String JOIN_KEY = PREFIX + "join";

    /** A unit a part counts in, and the key of its line. */
    public enum Unit {
        DAYS("days"),
        HOURS("hours"),
        MINUTES("minutes"),
        SECONDS("seconds");

        private final String key;

        Unit(String id) {
            this.key = PREFIX + id;
        }

        /** The lang key that writes a count of this unit: {@code %sh}. */
        public String getKey() {
            return this.key;
        }
    }

    private final List<Unit> units;
    private final List<Long> counts;

    private LostTalesDuration(List<Unit> units, List<Long> counts) {
        this.units = units;
        this.counts = counts;
    }

    /** A duration of one part. */
    public static LostTalesDuration of(long count, Unit unit) {
        return new LostTalesDuration(Collections.<Unit>emptyList(),
                Collections.<Long>emptyList()).and(count, unit);
    }

    /** This duration with one more part after the last. */
    public LostTalesDuration and(long count, Unit unit) {
        if (unit == null) {
            throw new IllegalArgumentException("a duration's part needs a unit");
        }
        List<Unit> units = new ArrayList<Unit>(this.units);
        List<Long> counts = new ArrayList<Long>(this.counts);
        units.add(unit);
        counts.add(Long.valueOf(count));
        return new LostTalesDuration(units, counts);
    }

    /** The duration in {@code words}: each part's line, joined in order. */
    public String write(LostTalesWords words) {
        String written = words.format(this.units.get(0).getKey(), this.counts.get(0));
        for (int index = 1; index < this.units.size(); index++) {
            written = words.format(JOIN_KEY, written, words.format(
                    this.units.get(index).getKey(), this.counts.get(index)));
        }
        return written;
    }

    /** The duration in the language of the side that asks. */
    public String write() {
        return write(LostTalesWords.LANG);
    }

    /**
     * The duration as a chat component the reader's game translates, for
     * what the server sends a player: each part a translation of its unit.
     */
    public IChatComponent component() {
        IChatComponent written = new ChatComponentTranslation(
                this.units.get(0).getKey(), this.counts.get(0));
        for (int index = 1; index < this.units.size(); index++) {
            written = new ChatComponentTranslation(JOIN_KEY, written,
                    new ChatComponentTranslation(this.units.get(index).getKey(),
                            this.counts.get(index)));
        }
        return written;
    }
}
