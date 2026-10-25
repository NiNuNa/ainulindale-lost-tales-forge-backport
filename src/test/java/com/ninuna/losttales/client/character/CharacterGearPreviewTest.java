package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.sync.CharacterGear;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * The stage dresses the local player in another character's gear for one
 * draw (CHAR-42): the armour slots and the selected hotbar slot hold it
 * while the player is drawn, and the inventory is the player's own again
 * afterwards, stack for stack.
 */
public final class CharacterGearPreviewTest {

    @After
    public void forget() {
        CharacterGearPreview.clear();
    }

    @Test
    public void theGearStandsInTheArmourAndTheHandForTheDraw() {
        InventoryPlayer inventory = ownInventory(2);
        CharacterGear gear = gear();
        CharacterGearPreview.Dressing dressing = CharacterGearPreview.dress(inventory, gear);
        for (int slot = 0; slot < CharacterGear.ARMOUR_SLOTS; slot++) {
            assertSame(gear.get(slot), inventory.armorInventory[slot]);
        }
        assertSame(gear.get(CharacterGear.HELD), inventory.mainInventory[2]);
        CharacterGearPreview.undress(dressing);
    }

    @Test
    public void afterTheDrawTheInventoryIsThePlayersOwn() {
        InventoryPlayer inventory = ownInventory(2);
        ItemStack[] armour = inventory.armorInventory.clone();
        ItemStack[] main = inventory.mainInventory.clone();
        CharacterGearPreview.undress(CharacterGearPreview.dress(inventory, gear()));
        assertSameStacks(armour, inventory.armorInventory);
        assertSameStacks(main, inventory.mainInventory);
    }

    @Test
    public void nothingEmptiesTheSlotsForTheDrawAlone() {
        InventoryPlayer inventory = ownInventory(0);
        ItemStack[] armour = inventory.armorInventory.clone();
        ItemStack held = inventory.mainInventory[0];
        CharacterGearPreview.Dressing dressing =
                CharacterGearPreview.dress(inventory, CharacterGear.NONE);
        for (int slot = 0; slot < CharacterGear.ARMOUR_SLOTS; slot++) {
            assertNull(inventory.armorInventory[slot]);
        }
        assertNull(inventory.mainInventory[0]);
        CharacterGearPreview.undress(dressing);
        assertSameStacks(armour, inventory.armorInventory);
        assertSame(held, inventory.mainInventory[0]);
    }

    @Test
    public void onlyTheSelectedHotbarSlotIsTouched() {
        InventoryPlayer inventory = ownInventory(4);
        ItemStack[] main = inventory.mainInventory.clone();
        CharacterGearPreview.Dressing dressing = CharacterGearPreview.dress(inventory, gear());
        for (int slot = 0; slot < main.length; slot++) {
            if (slot != 4) {
                assertSame(main[slot], inventory.mainInventory[slot]);
            }
        }
        CharacterGearPreview.undress(dressing);
    }

    @Test
    public void aSlotFilledDuringTheDrawKeepsWhatItHolds() {
        InventoryPlayer inventory = ownInventory(1);
        CharacterGearPreview.Dressing dressing = CharacterGearPreview.dress(inventory, gear());
        ItemStack arrived = new ItemStack(new ItemSnowball());
        inventory.armorInventory[3] = arrived;
        CharacterGearPreview.undress(dressing);
        assertSame(arrived, inventory.armorInventory[3]);
    }

    @Test
    public void aHandOutsideTheHotbarIsLeftAlone() {
        InventoryPlayer inventory = ownInventory(0);
        inventory.currentItem = 12;
        ItemStack held = inventory.mainInventory[12];
        CharacterGearPreview.undress(CharacterGearPreview.dress(inventory, gear()));
        assertSame(held, inventory.mainInventory[12]);
    }

    @Test
    public void nothingToDressChangesNothing() {
        InventoryPlayer inventory = ownInventory(0);
        assertNull(CharacterGearPreview.dress(inventory, null));
        assertNull(CharacterGearPreview.dress((InventoryPlayer)null, gear()));
        CharacterGearPreview.undress(null);
    }

    @Test
    public void noGearIsWornWhileNoStageSetsAny() {
        assertNull(CharacterGearPreview.dress((net.minecraft.entity.player.EntityPlayer)null));
        Object screen = new Object();
        CharacterGearPreview.set(screen, gear());
        CharacterGearPreview.clear(new Object());
        CharacterGearPreview.clear(screen);
        assertNull(CharacterGearPreview.dress((net.minecraft.entity.player.EntityPlayer)null));
    }

    /** An inventory full of the player's own things, the hand on {@code held}. */
    private static InventoryPlayer ownInventory(int held) {
        InventoryPlayer inventory = new InventoryPlayer(null);
        for (int slot = 0; slot < inventory.mainInventory.length; slot++) {
            inventory.mainInventory[slot] = new ItemStack(new ItemSnowball());
        }
        for (int slot = 0; slot < inventory.armorInventory.length; slot++) {
            inventory.armorInventory[slot] = new ItemStack(new ItemSnowball());
        }
        inventory.currentItem = held;
        return inventory;
    }

    private static CharacterGear gear() {
        return CharacterGear.of(new ItemStack[] {
                new ItemStack(new ItemBow()), null,
                new ItemStack(new ItemBow()), new ItemStack(new ItemBow()),
                new ItemStack(new ItemBow())});
    }

    private static void assertSameStacks(ItemStack[] expected, ItemStack[] actual) {
        for (int slot = 0; slot < expected.length; slot++) {
            assertSame("slot " + slot, expected[slot], actual[slot]);
        }
    }
}
