package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.sync.CharacterGear;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

/**
 * The gear the local player wears while a stage shows another character
 * (CHAR-42): that character's armour and held item, or none, drawn in
 * place of the player's own. It is presentation alone. The player's
 * renderer dresses them for the length of one draw ({@link #dress}) and
 * gives every slot back in a finally block ({@link #undress}), so the
 * inventory is the player's own again before anything else reads it:
 * the hand in first person, the hotbar, a click, the server.
 *
 * <p>The gear belongs to the screen that set it and is worn only while
 * that screen is open, so it can never outlast its stage.</p>
 */
public final class CharacterGearPreview {

    private static Object owner;
    private static CharacterGear worn;

    private CharacterGearPreview() {}

    /** The screen's stage shows {@code gear} on the player; null for the player's own. */
    public static synchronized void set(Object screen, CharacterGear gear) {
        if (screen == null) {
            return;
        }
        owner = gear == null ? null : screen;
        worn = gear;
    }

    /** The screen's stage lets go: the player wears their own again. */
    public static synchronized void clear(Object screen) {
        if (owner == screen) {
            owner = null;
            worn = null;
        }
    }

    /** Dropped with every other client cache when the world is left. */
    public static synchronized void clear() {
        owner = null;
        worn = null;
    }

    /**
     * Before {@code player} is drawn: the gear in place of their own,
     * when it is the local player and the screen that set it is open.
     * Returns what to give back, or null when nothing changed.
     */
    public static Dressing dress(EntityPlayer player) {
        CharacterGear gear;
        synchronized (CharacterGearPreview.class) {
            gear = worn;
            if (gear == null || player == null) {
                return null;
            }
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null || player != minecraft.thePlayer
                    || owner == null || minecraft.currentScreen != owner) {
                return null;
            }
        }
        return dress(player.inventory, gear);
    }

    /** Puts the gear in the inventory's armour slots and its selected hotbar slot. */
    static Dressing dress(InventoryPlayer inventory, CharacterGear gear) {
        if (inventory == null || gear == null
                || inventory.armorInventory == null
                || inventory.armorInventory.length != CharacterGear.ARMOUR_SLOTS
                || inventory.mainInventory == null) {
            return null;
        }
        int held = inventory.currentItem;
        boolean hand = held >= 0 && held < InventoryPlayer.getHotbarSize()
                && held < inventory.mainInventory.length;
        Dressing dressing = new Dressing(inventory, hand ? held : -1);
        for (int slot = 0; slot < CharacterGear.ARMOUR_SLOTS; slot++) {
            dressing.own[slot] = inventory.armorInventory[slot];
            dressing.put[slot] = gear.get(slot);
            inventory.armorInventory[slot] = dressing.put[slot];
        }
        if (hand) {
            dressing.own[CharacterGear.HELD] = inventory.mainInventory[held];
            dressing.put[CharacterGear.HELD] = gear.get(CharacterGear.HELD);
            inventory.mainInventory[held] = dressing.put[CharacterGear.HELD];
        }
        return dressing;
    }

    /**
     * After the draw: every slot back as it was. A slot something else
     * filled meanwhile keeps what it holds now.
     */
    public static void undress(Dressing dressing) {
        if (dressing == null) {
            return;
        }
        InventoryPlayer inventory = dressing.inventory;
        for (int slot = 0; slot < CharacterGear.ARMOUR_SLOTS; slot++) {
            if (inventory.armorInventory[slot] == dressing.put[slot]) {
                inventory.armorInventory[slot] = dressing.own[slot];
            }
        }
        if (dressing.heldSlot >= 0
                && inventory.mainInventory[dressing.heldSlot]
                        == dressing.put[CharacterGear.HELD]) {
            inventory.mainInventory[dressing.heldSlot] = dressing.own[CharacterGear.HELD];
        }
    }

    /** What one draw put in the inventory, and what it took out. */
    public static final class Dressing {
        final InventoryPlayer inventory;
        final int heldSlot;
        final ItemStack[] own = new ItemStack[CharacterGear.SLOTS];
        final ItemStack[] put = new ItemStack[CharacterGear.SLOTS];

        Dressing(InventoryPlayer inventory, int heldSlot) {
            this.inventory = inventory;
            this.heldSlot = heldSlot;
        }
    }
}
