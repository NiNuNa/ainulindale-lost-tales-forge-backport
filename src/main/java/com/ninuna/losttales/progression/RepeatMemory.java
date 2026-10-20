package com.ninuna.losttales.progression;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What one character repeated lately (PROG-08) and how much each skill
 * gained this hour, kept while the server runs and never saved. The
 * oldest things are forgotten first past {@link #MAX_THINGS}.
 */
public final class RepeatMemory {
    public static final int MAX_THINGS = 512;
    private static final long HOUR_MILLIS = 60L * 60L * 1000L;

    private final Map<String, Entry> repeats = new LinkedHashMap<String, Entry>(16, 0.75F, true);
    private final Map<String, Hour> hours = new LinkedHashMap<String, Hour>();

    /**
     * The share an act on {@code thing} counts for now, and that it was
     * done once more.
     */
    public double countRepeat(String thing, RepeatRule rule, long nowMillis) {
        Entry entry = this.repeats.get(thing);
        double remembered = entry == null ? 0.0D
                : rule.recovered(entry.count, nowMillis - entry.atMillis);
        double share = rule.share(remembered);
        if (entry == null) {
            entry = new Entry();
            this.repeats.put(thing, entry);
            trim();
        }
        entry.count = remembered + 1.0D;
        entry.atMillis = nowMillis;
        return share;
    }

    /**
     * The share an act on {@code thing} counts for now, without counting
     * it as a repeat: a hit shrinks as kills of its kind do, and is no
     * repeat of its own.
     */
    public double peekShare(String thing, RepeatRule rule, long nowMillis) {
        Entry entry = this.repeats.get(thing);
        return rule.share(entry == null ? 0.0D
                : rule.recovered(entry.count, nowMillis - entry.atMillis));
    }

    /**
     * How much of {@code amount} a skill may still gain this hour under
     * {@code cap} (0 for no cap), and that it gained it.
     */
    public double allowWithinHour(String skillId, double amount, int cap, long nowMillis) {
        if (cap <= 0) {
            return amount;
        }
        Hour hour = this.hours.get(skillId);
        if (hour == null || nowMillis - hour.startMillis >= HOUR_MILLIS
                || nowMillis < hour.startMillis) {
            hour = new Hour();
            hour.startMillis = nowMillis;
            this.hours.put(skillId, hour);
        }
        double allowed = Math.max(0.0D, Math.min(amount, cap - hour.gained));
        hour.gained += allowed;
        return allowed;
    }

    private void trim() {
        Iterator<String> oldest = this.repeats.keySet().iterator();
        while (this.repeats.size() > MAX_THINGS && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    private static final class Entry {
        double count;
        long atMillis;
    }

    private static final class Hour {
        long startMillis;
        double gained;
    }
}
