package com.ninuna.losttales.accessory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The accessories one side plays with (ACC-01), fixed once made: what each
 * slot takes, by item, and what each accessory does. An item names one
 * accessory per slot; a second naming it is left out, so a lookup never
 * depends on the order the files were read in.
 */
public final class AccessoryCatalogue {
    /** The most accessories a catalogue holds; a packet carries them all. */
    public static final int MAX_ACCESSORIES = 128;
    public static final AccessoryCatalogue EMPTY =
            new AccessoryCatalogue(new ArrayList<AccessoryDefinition>());

    private final List<AccessoryDefinition> definitions;
    private final Map<String, AccessoryDefinition> byId =
            new HashMap<String, AccessoryDefinition>();
    private final Map<String, AccessoryDefinition> byItem =
            new HashMap<String, AccessoryDefinition>();

    private AccessoryCatalogue(List<AccessoryDefinition> definitions) {
        this.definitions = Collections.unmodifiableList(
                new ArrayList<AccessoryDefinition>(definitions));
        for (AccessoryDefinition definition : this.definitions) {
            this.byId.put(definition.getId(), definition);
            this.byItem.put(key(definition.getSlotType(), definition.getItemId()), definition);
        }
    }

    /**
     * The catalogue of {@code definitions} in order: each with a slot, an
     * id and an item no earlier one has in that slot, at most
     * {@link #MAX_ACCESSORIES}. Why each other was left out goes to
     * {@code problems}.
     */
    public static AccessoryCatalogue of(List<AccessoryDefinition> definitions,
                                        List<String> problems) {
        List<AccessoryDefinition> kept = new ArrayList<AccessoryDefinition>();
        Map<String, String> ids = new HashMap<String, String>();
        Map<String, String> items = new HashMap<String, String>();
        if (definitions != null) {
            for (AccessoryDefinition definition : definitions) {
                String problem = null;
                String item = key(definition.getSlotType(), definition.getItemId());
                if (definition.getSlotType() == null) {
                    problem = "goes in no slot";
                } else if (ids.containsKey(definition.getId())) {
                    problem = "the id is an earlier accessory's";
                } else if (items.containsKey(item)) {
                    problem = "is the item " + definition.getItemId() + ", which the accessory "
                            + items.get(item) + " is already";
                } else if (kept.size() >= MAX_ACCESSORIES) {
                    problem = "the catalogue holds its " + MAX_ACCESSORIES + " accessories already";
                }
                if (problem != null) {
                    if (problems != null) {
                        problems.add(definition.getId() + ": " + problem);
                    }
                    continue;
                }
                ids.put(definition.getId(), definition.getId());
                items.put(item, definition.getId());
                kept.add(definition);
            }
        }
        return new AccessoryCatalogue(kept);
    }

    /** Every accessory, in the order its files were read. */
    public List<AccessoryDefinition> getDefinitions() {
        return this.definitions;
    }

    /** The accessory of that id; null for none. */
    public AccessoryDefinition byId(String id) {
        return id == null ? null : this.byId.get(id);
    }

    /** The accessory {@code slot} takes the item of that registry name as; null where it takes none. */
    public AccessoryDefinition find(AccessorySlotType slot, String itemId) {
        return slot == null || itemId == null ? null : this.byItem.get(key(slot, itemId));
    }

    private static String key(AccessorySlotType slot, String itemId) {
        return (slot == null ? "" : slot.getName()) + "|" + itemId;
    }
}
