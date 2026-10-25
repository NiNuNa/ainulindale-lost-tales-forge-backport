package com.ninuna.losttales.accessory;

import java.util.ArrayList;
import java.util.List;

/**
 * What a worn accessory can do (ACC-01): a fixed list the code holds,
 * named by id in the accessory files.
 */
public enum AccessoryEffect {
    /**
     * Hides the wearer from players, mobs and the map, and their nearby
     * lines in the chat (ACC-03); the wearer sees a faint shade of
     * themselves (ACC-02).
     */
    CONCEALMENT("losttales:concealment"),
    /** The wearer sees the wraith world. */
    WRAITH_WORLD("losttales:wraith_world");

    private final String id;

    AccessoryEffect(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    /** The effect of that id; null for none. */
    public static AccessoryEffect byId(String id) {
        for (AccessoryEffect effect : values()) {
            if (effect.id.equals(id)) {
                return effect;
            }
        }
        return null;
    }

    /** Every effect's id, in order. */
    public static List<String> ids() {
        List<String> ids = new ArrayList<String>();
        for (AccessoryEffect effect : values()) {
            ids.add(effect.id);
        }
        return ids;
    }
}
