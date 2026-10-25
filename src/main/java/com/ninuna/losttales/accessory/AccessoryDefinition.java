package com.ninuna.losttales.accessory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * One accessory as its file writes it (ACC-01): its id, the slot it goes
 * in, the item it is, by the item's registry name, and its effects.
 */
public final class AccessoryDefinition {

    private final String id;
    private final AccessorySlotType slotType;
    private final String itemId;
    private final Set<AccessoryEffect> effects;

    public AccessoryDefinition(String id, AccessorySlotType slotType, String itemId,
                               Set<AccessoryEffect> effects) {
        this.id = id == null ? "" : id;
        this.slotType = slotType;
        this.itemId = itemId == null ? "" : itemId;
        this.effects = effects == null || effects.isEmpty()
                ? Collections.<AccessoryEffect>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(effects));
    }

    public String getId() {
        return this.id;
    }

    /** The slot it goes in; null where its file named none the code has. */
    public AccessorySlotType getSlotType() {
        return this.slotType;
    }

    /** The item's registry name, as the game has it: {@code lotr:item.goldRing}. */
    public String getItemId() {
        return this.itemId;
    }

    public Set<AccessoryEffect> getEffects() {
        return this.effects;
    }

    public boolean has(AccessoryEffect effect) {
        return this.effects.contains(effect);
    }
}
