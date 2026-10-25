package com.ninuna.losttales.accessory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * The accessories one side of the game plays with (ACC-01): the server's
 * as its files read ({@link Accessories}), and the client's as the server
 * it joined sent them. Each holds a fixed {@link AccessoryCatalogue},
 * swapped whole, so a lookup on any thread sees one list or the other.
 * An integrated server and its client keep their own, so a client leaving
 * never changes what the server still saving its players reads.
 */
public final class AccessoryCompatibilityRegistry {

    private static final AccessoryCompatibilityRegistry SERVER =
            new AccessoryCompatibilityRegistry();
    private static final AccessoryCompatibilityRegistry CLIENT =
            new AccessoryCompatibilityRegistry();

    private volatile AccessoryCatalogue catalogue = AccessoryCatalogue.EMPTY;

    private AccessoryCompatibilityRegistry() {}

    /** What the logical server's slots take and its accessories do. */
    public static AccessoryCompatibilityRegistry server() {
        return SERVER;
    }

    /** What the client's slot takes, as the server it joined says. */
    public static AccessoryCompatibilityRegistry client() {
        return CLIENT;
    }

    /** The side's own: the client's for a client world, else the server's. */
    public static AccessoryCompatibilityRegistry of(World world) {
        return world != null && world.isRemote ? CLIENT : SERVER;
    }

    public void set(AccessoryCatalogue catalogue) {
        this.catalogue = catalogue == null ? AccessoryCatalogue.EMPTY : catalogue;
    }

    public AccessoryCatalogue getCatalogue() {
        return this.catalogue;
    }

    /** The accessory {@code slotType} takes {@code stack} as; null where it takes none. */
    public AccessoryDefinition find(AccessorySlotType slotType, ItemStack stack) {
        if (slotType == null || stack == null || stack.getItem() == null) {
            return null;
        }
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        return name == null ? null : this.catalogue.find(slotType, name.toString());
    }

    public boolean isCompatible(AccessorySlotType slotType, ItemStack stack) {
        return find(slotType, stack) != null;
    }

    /** The accessory of that id; null for none. */
    public AccessoryDefinition getDefinition(String id) {
        return id == null || id.length() == 0 ? null : this.catalogue.byId(id);
    }
}
