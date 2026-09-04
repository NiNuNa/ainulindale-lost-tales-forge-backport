package com.ninuna.losttales.compat.discord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the bridge has to send, one lane per webhook. Discord limits
 * each webhook on its own, so a limit on one must hold only that
 * webhook's posts: every lane keeps its items in order and a clock of
 * its own saying when it may next be worked, and the worker takes the
 * lanes that are due one after another. A lane holds at most
 * {@link #MAX_PER_LANE} items and refuses more, so a webhook that stays
 * limited cannot grow without bound. Worker thread only; nothing here
 * is synchronized.
 *
 * @param <T> what a lane holds: the bridge's own outbound entries
 */
final class DiscordOutboundLanes<T> {
    /** Items a webhook may have waiting; past it the newest are refused. */
    static final int MAX_PER_LANE = 128;

    private final Map<String, Lane<T>> lanes = new LinkedHashMap<String, Lane<T>>();

    /** Queues an item for a webhook; false when that lane is full. */
    boolean add(String webhook, T item) {
        Lane<T> lane = this.lanes.get(webhook);
        if (lane == null) {
            lane = new Lane<T>();
            this.lanes.put(webhook, lane);
        }
        if (lane.items.size() >= MAX_PER_LANE) {
            return false;
        }
        lane.items.add(item);
        return true;
    }

    /**
     * The webhooks with something waiting that may be worked at
     * {@code nowMillis}, in the order they were first used. A lane
     * delayed by a limit is left out until its clock has passed.
     */
    List<String> due(long nowMillis) {
        List<String> due = new ArrayList<String>();
        for (Map.Entry<String, Lane<T>> entry : this.lanes.entrySet()) {
            Lane<T> lane = entry.getValue();
            if (!lane.items.isEmpty() && nowMillis >= lane.notBeforeMillis) {
                due.add(entry.getKey());
            }
        }
        return due;
    }

    /** The item at the head of a webhook's lane, or null. */
    T peek(String webhook) {
        Lane<T> lane = this.lanes.get(webhook);
        return lane == null ? null : lane.items.peek();
    }

    /** Spends the item at the head of a webhook's lane. */
    void poll(String webhook) {
        Lane<T> lane = this.lanes.get(webhook);
        if (lane != null) {
            lane.items.poll();
        }
    }

    /** Holds a webhook's lane back until {@code untilMillis}. */
    void delay(String webhook, long untilMillis) {
        Lane<T> lane = this.lanes.get(webhook);
        if (lane != null) {
            lane.notBeforeMillis = untilMillis;
        }
    }

    /** Forgets everything waiting for a webhook: what a refused webhook gets. */
    void drop(String webhook) {
        Lane<T> lane = this.lanes.get(webhook);
        if (lane != null) {
            lane.items.clear();
        }
    }

    /** What a webhook's lane holds, oldest first; a snapshot. */
    List<T> items(String webhook) {
        Lane<T> lane = this.lanes.get(webhook);
        return lane == null ? Collections.<T>emptyList()
                : Collections.unmodifiableList(new ArrayList<T>(lane.items));
    }

    /** The webhooks that have a lane, in the order they were first used. */
    List<String> webhooks() {
        return Collections.unmodifiableList(new ArrayList<String>(this.lanes.keySet()));
    }

    /**
     * When the earliest delayed lane with something waiting may be
     * worked again, or {@link Long#MAX_VALUE} when nothing waits on a
     * clock: what the worker shortens its sleep to.
     */
    long nextDueMillis() {
        long next = Long.MAX_VALUE;
        for (Lane<T> lane : this.lanes.values()) {
            if (!lane.items.isEmpty()) {
                next = Math.min(next, lane.notBeforeMillis);
            }
        }
        return next;
    }

    /** Items waiting across every lane. */
    int size() {
        int size = 0;
        for (Lane<T> lane : this.lanes.values()) {
            size += lane.items.size();
        }
        return size;
    }

    private static final class Lane<T> {
        final ArrayDeque<T> items = new ArrayDeque<T>();
        long notBeforeMillis;
    }
}
