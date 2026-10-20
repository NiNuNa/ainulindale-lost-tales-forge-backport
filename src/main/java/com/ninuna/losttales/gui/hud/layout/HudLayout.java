package com.ninuna.losttales.gui.hud.layout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One HUD layout as its file writes it (MAP-11): its id, the name and
 * description a player's own file writes (empty in a bundled file, whose
 * words are lang lines under its id), and where it puts each panel,
 * {across, down}, each a share of the room the panel's box can move in
 * (MAP-10).
 */
public final class HudLayout {
    private final String id;
    private final String name;
    private final String description;
    private final Map<String, int[]> places;

    public HudLayout(String id, String name, String description,
                     Map<String, int[]> places) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        Map<String, int[]> copied = new LinkedHashMap<String, int[]>();
        if (places != null) {
            for (Map.Entry<String, int[]> place : places.entrySet()) {
                copied.put(place.getKey(), place.getValue().clone());
            }
        }
        this.places = Collections.unmodifiableMap(copied);
    }

    public String getId() {
        return this.id;
    }

    /** The name its file writes; empty for one named by its lang line. */
    public String getName() {
        return this.name;
    }

    /** What its file says it is for; empty for one explained by its lang line. */
    public String getDescription() {
        return this.description;
    }

    /** Each panel it places, by the client file's names, {across, down}. */
    public Map<String, int[]> getPlaces() {
        return this.places;
    }

    /** Where it puts {@code panel}, {across, down}; null for a panel it does not place. */
    public int[] place(String panel) {
        int[] place = this.places.get(panel);
        return place == null ? null : place.clone();
    }
}
