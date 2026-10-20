package com.ninuna.losttales.progression.count;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What an act was done to, as the skill files' tables name it: its keys,
 * most exact first ({@code id@meta}, {@code id}, {@code ore:Name}), the
 * food it fills if it is food, and the one name its repeats are counted
 * under (PROG-08).
 */
public final class Thing {
    public static final Thing NONE = new Thing(Collections.<String>emptyList(), 0, "");

    private final List<String> keys;
    private final int foodPoints;
    private final String repeatKey;

    public Thing(List<String> keys, int foodPoints, String repeatKey) {
        this.keys = Collections.unmodifiableList(keys == null
                ? new ArrayList<String>() : new ArrayList<String>(keys));
        this.foodPoints = Math.max(0, foodPoints);
        this.repeatKey = repeatKey == null ? "" : repeatKey;
    }

    public static Thing named(String key) {
        return key == null ? NONE : new Thing(Collections.singletonList(key), 0, key);
    }

    public List<String> getKeys() {
        return this.keys;
    }

    /** The points of hunger it fills; 0 for anything that is no food. */
    public int getFoodPoints() {
        return this.foodPoints;
    }

    public String getRepeatKey() {
        return this.repeatKey;
    }
}
