package com.ninuna.losttales.config.client;

import com.ninuna.losttales.client.window.NumberStepper;
import com.ninuna.losttales.config.server.ServerConfigApplyResult;
import com.ninuna.losttales.config.server.ServerConfigChange;
import com.ninuna.losttales.config.server.ServerConfigChangeValidator;
import com.ninuna.losttales.config.server.ServerConfigEntry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The server's settings as the Server Settings page edits them: the
 * snapshot the server last sent, and the values changed on top of it,
 * which wait until they are saved. A value put back as the server holds
 * it waits no longer, a number compared as a number. A secret is never
 * read, only replaced: an empty value leaves it as it is. Free of
 * Minecraft.
 */
final class ServerSettingsDraft {
    /** Places a number's step and values are shown to at most. */
    private static final int MAX_PLACES = 4;
    /** The share of a number's range one step takes, as the power of ten below it. */
    private static final double STEPS_ACROSS = 100.0D;

    private List<ServerConfigEntry> saved = Collections.emptyList();
    /** The values that wait, by the setting's name in lower case. */
    private final Map<String, List<String>> waiting =
            new HashMap<String, List<String>>();
    private boolean loaded;

    /** Whether a snapshot has been taken. */
    boolean isLoaded() {
        return this.loaded;
    }

    /** The settings as the server holds them, in the snapshot's order. */
    List<ServerConfigEntry> entries() {
        return this.saved;
    }

    /**
     * Takes the settings as the server holds them now. What waits stays
     * on top where its setting is still there and still differs.
     */
    void load(List<ServerConfigEntry> snapshot) {
        this.saved = snapshot == null ? Collections.<ServerConfigEntry>emptyList()
                : Collections.unmodifiableList(
                        new ArrayList<ServerConfigEntry>(snapshot));
        this.loaded = true;
        Iterator<Map.Entry<String, List<String>>> waits =
                this.waiting.entrySet().iterator();
        while (waits.hasNext()) {
            Map.Entry<String, List<String>> wait = waits.next();
            ServerConfigEntry entry = find(wait.getKey());
            if (entry == null || same(entry, wait.getValue())) {
                waits.remove();
            }
        }
    }

    /** Forgets the snapshot and everything that waits. */
    void clear() {
        this.saved = Collections.emptyList();
        this.waiting.clear();
        this.loaded = false;
    }

    /** The setting named {@code category.key}; null for none. */
    ServerConfigEntry find(String qualifiedName) {
        for (ServerConfigEntry entry : this.saved) {
            if (entry.qualifiedName().equalsIgnoreCase(qualifiedName)) {
                return entry;
            }
        }
        return null;
    }

    /** What a setting reads on the page: what waits, else what the server holds. */
    List<String> values(ServerConfigEntry entry) {
        List<String> waits = this.waiting.get(nameOf(entry));
        return waits != null ? waits : entry.getValues();
    }

    /** A single value as the page reads it; empty for none. */
    String value(ServerConfigEntry entry) {
        List<String> values = values(entry);
        return values.isEmpty() ? "" : values.get(0);
    }

    /** Whether a change to the setting waits. */
    boolean isChanged(ServerConfigEntry entry) {
        return this.waiting.containsKey(nameOf(entry));
    }

    /** How many settings have a change waiting. */
    int count() {
        return this.waiting.size();
    }

    /**
     * Sets a single value, trimmed as the server writes it. An empty
     * value leaves a secret as it is.
     */
    void set(ServerConfigEntry entry, String value) {
        if (entry == null || entry.isList()) {
            return;
        }
        String text = value == null ? "" : value.trim();
        if (entry.isSecret()) {
            if (text.length() > 0) {
                this.waiting.put(nameOf(entry), Collections.singletonList(text));
            }
            return;
        }
        put(entry, Collections.singletonList(text));
    }

    /** Sets the item at {@code index} of a list; an empty value takes it out. */
    void setItem(ServerConfigEntry entry, int index, String value) {
        if (entry == null || !entry.isList()) {
            return;
        }
        List<String> items = new ArrayList<String>(values(entry));
        if (index < 0 || index >= items.size()) {
            return;
        }
        String text = value == null ? "" : value.trim();
        if (text.length() == 0) {
            items.remove(index);
        } else {
            items.set(index, text);
        }
        put(entry, items);
    }

    /** Whether a list takes another item: it holds fewer than a change may carry. */
    boolean canAdd(ServerConfigEntry entry) {
        return entry != null && entry.isList() && values(entry).size()
                < ServerConfigChangeValidator.MAX_LIST_ITEMS;
    }

    /** Adds an item at a list's end; an empty value adds nothing. */
    void addItem(ServerConfigEntry entry, String value) {
        String text = value == null ? "" : value.trim();
        if (!canAdd(entry) || text.length() == 0) {
            return;
        }
        List<String> items = new ArrayList<String>(values(entry));
        items.add(text);
        put(entry, items);
    }

    private void put(ServerConfigEntry entry, List<String> values) {
        if (same(entry, values)) {
            this.waiting.remove(nameOf(entry));
        } else {
            this.waiting.put(nameOf(entry), Collections.unmodifiableList(
                    new ArrayList<String>(values)));
        }
    }

    /** Every change that waits, one per setting, in the snapshot's order. */
    List<ServerConfigChange> changes() {
        List<ServerConfigChange> changes = new ArrayList<ServerConfigChange>();
        for (ServerConfigEntry entry : this.saved) {
            List<String> waits = this.waiting.get(nameOf(entry));
            if (waits != null) {
                changes.add(new ServerConfigChange(entry.getCategory(),
                        entry.getKey(), entry.isList(), waits));
            }
        }
        return changes;
    }

    /** Puts every setting back as the server holds it. */
    void discard() {
        this.waiting.clear();
    }

    /**
     * What became of the changes sent: each the server applied is what
     * it holds now, and waits no longer unless it changed again since it
     * was sent; each refused still waits.
     */
    void settle(List<ServerConfigChange> sent, ServerConfigApplyResult result) {
        if (sent == null || result == null) {
            return;
        }
        Set<String> applied = new HashSet<String>();
        for (String name : result.getApplied()) {
            applied.add(name.toLowerCase(Locale.ROOT));
        }
        List<ServerConfigEntry> updated = new ArrayList<ServerConfigEntry>(this.saved);
        for (ServerConfigChange change : sent) {
            String name = change.qualifiedName().toLowerCase(Locale.ROOT);
            if (!applied.contains(name)) {
                continue;
            }
            for (int index = 0; index < updated.size(); index++) {
                ServerConfigEntry entry = updated.get(index);
                if (nameOf(entry).equals(name)) {
                    updated.set(index, holding(entry, change.getValues()));
                }
            }
            if (change.getValues().equals(this.waiting.get(name))) {
                this.waiting.remove(name);
            }
        }
        this.saved = Collections.unmodifiableList(updated);
    }

    /** The entry as the server holds it once {@code values} were written; a secret is then set. */
    private static ServerConfigEntry holding(ServerConfigEntry entry,
                                             List<String> values) {
        return new ServerConfigEntry(entry.getCategory(), entry.getKey(),
                entry.getType(), entry.isList(), entry.isSecret()
                        ? Collections.singletonList("") : values,
                entry.getDefaults(), entry.getMinValue(), entry.getMaxValue(),
                entry.isSecret(), entry.getValidValues());
    }

    private static String nameOf(ServerConfigEntry entry) {
        return entry.qualifiedName().toLowerCase(Locale.ROOT);
    }

    /** Whether {@code values} are what the server holds; never for a secret, which is never read. */
    private static boolean same(ServerConfigEntry entry, List<String> values) {
        List<String> held = entry.getValues();
        if (entry.isSecret() || held.size() != values.size()) {
            return false;
        }
        for (int index = 0; index < held.size(); index++) {
            if (!sameValue(entry.getType(), held.get(index), values.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameValue(ServerConfigEntry.Type type, String held,
                                     String value) {
        if (type == ServerConfigEntry.Type.INTEGER
                || type == ServerConfigEntry.Type.DOUBLE) {
            Double heldNumber = number(held);
            Double number = number(value);
            if (heldNumber != null && number != null) {
                return heldNumber.doubleValue() == number.doubleValue();
            }
        }
        if (type == ServerConfigEntry.Type.BOOLEAN) {
            return held.trim().equalsIgnoreCase(value.trim());
        }
        return held.equals(value);
    }

    /* ---- Numbers ---- */

    /**
     * A number setting's bounds, {min, max}, where the server names both;
     * null where it does not, or names the widest a value of its kind
     * holds, as Forge writes a number with no bounds.
     */
    static double[] bounds(ServerConfigEntry entry) {
        Double min = number(entry.getMinValue());
        Double max = number(entry.getMaxValue());
        if (min == null || max == null || unbounded(entry, min.doubleValue())
                || unbounded(entry, max.doubleValue())) {
            return null;
        }
        return new double[] {min.doubleValue(), max.doubleValue()};
    }

    private static boolean unbounded(ServerConfigEntry entry, double bound) {
        return entry.getType() == ServerConfigEntry.Type.INTEGER
                ? bound <= Integer.MIN_VALUE || bound >= Integer.MAX_VALUE
                : Math.abs(bound) >= Double.MAX_VALUE;
    }

    /**
     * A number setting's arithmetic: its bounds, a step of the power of
     * ten at or below a hundredth of its range, and as many places as its
     * value, its default, its bounds or its step are written to. A whole
     * number has no places and steps one at the least; a number with no
     * bounds steps by its last place.
     */
    static NumberStepper stepper(ServerConfigEntry entry) {
        boolean whole = entry.getType() == ServerConfigEntry.Type.INTEGER;
        double[] bounds = bounds(entry);
        int places = 0;
        if (!whole) {
            places = Math.max(places(entry.getValue()), places(entry.getDefault()));
            if (bounds != null) {
                places = Math.max(places, Math.max(places(entry.getMinValue()),
                        places(entry.getMaxValue())));
            }
            places = Math.min(MAX_PLACES, places);
        }
        double step = Math.pow(10.0D, -places);
        if (bounds != null && bounds[1] > bounds[0]) {
            double share = Math.pow(10.0D, Math.floor(Math.log10(
                    (bounds[1] - bounds[0]) / STEPS_ACROSS)));
            if (share > step) {
                step = share;
            } else if (!whole) {
                places = Math.min(MAX_PLACES, Math.max(places,
                        (int)Math.round(-Math.log10(share))));
                step = Math.pow(10.0D, -places);
            }
        }
        return bounds == null
                ? new NumberStepper(-Double.MAX_VALUE, Double.MAX_VALUE, step, places)
                : new NumberStepper(bounds[0], bounds[1], step, places);
    }

    /** The places after the point a number is written to, trailing noughts aside; 0 for none or no number. */
    private static int places(String text) {
        if (number(text) == null) {
            return 0;
        }
        try {
            return Math.max(0, new BigDecimal(text.trim()).stripTrailingZeros()
                    .scale());
        } catch (NumberFormatException unreadable) {
            return 0;
        }
    }

    /** A finite number, or null for words that are none. */
    private static Double number(String text) {
        if (text == null || text.trim().length() == 0) {
            return null;
        }
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isNaN(value) || Double.isInfinite(value) ? null
                    : Double.valueOf(value);
        } catch (NumberFormatException unreadable) {
            return null;
        }
    }
}
