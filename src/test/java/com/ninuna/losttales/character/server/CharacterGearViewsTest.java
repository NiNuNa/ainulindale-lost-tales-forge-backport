package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.character.state.component.VanillaInventoryStateComponent;
import com.ninuna.losttales.character.sync.CharacterGear;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Which characters the server tells a player the gear of, for the stage
 * (CHAR-42), and how a saved inventory is read for it: only the player's
 * own, never the one played, and nothing where the inventory does not
 * read.
 */
public final class CharacterGearViewsTest {

    private static final UUID OWNER = UUID.randomUUID();

    @Test
    public void onlyACharacterOfTheRosterAsideFromTheOnePlayedIsTold() {
        CharacterRoster roster = new CharacterRoster(OWNER);
        RoleplayCharacter aldric = character("Aldric", 0);
        RoleplayCharacter beren = character("Beren", 1);
        assertTrue(roster.addCharacter(aldric));
        assertTrue(roster.addCharacter(beren));
        roster.setActiveCharacterId(aldric.getCharacterId());

        assertTrue(CharacterGearViews.isOwnAndAside(roster, beren.getCharacterId()));
        assertFalse("the one played wears its own",
                CharacterGearViews.isOwnAndAside(roster, aldric.getCharacterId()));
        assertFalse("another player's character",
                CharacterGearViews.isOwnAndAside(roster, UUID.randomUUID()));
        assertFalse(CharacterGearViews.isOwnAndAside(null, beren.getCharacterId()));
        assertFalse(CharacterGearViews.isOwnAndAside(roster, null));
    }

    @Test
    public void aGhostsCharactersAreAllAside() {
        CharacterRoster roster = new CharacterRoster(OWNER);
        RoleplayCharacter beren = character("Beren", 0);
        assertTrue(roster.addCharacter(beren));
        assertTrue(CharacterGearViews.isOwnAndAside(roster, beren.getCharacterId()));
    }

    @Test
    public void anEmptySavedInventoryWearsNothing() {
        assertSame(CharacterGear.NONE,
                CharacterGearViews.gearOf(UUID.randomUUID(), emptyInventory()));
    }

    @Test
    public void anInventoryThatDoesNotReadWearsNothing() {
        assertSame(CharacterGear.NONE, CharacterGearViews.gearOf(UUID.randomUUID(), null));
        assertSame(CharacterGear.NONE,
                CharacterGearViews.gearOf(UUID.randomUUID(), new NBTTagCompound()));
        NBTTagCompound wrongHand = emptyInventory();
        wrongHand.setInteger("CurrentItem", 9);
        assertSame(CharacterGear.NONE, CharacterGearViews.gearOf(UUID.randomUUID(), wrongHand));
    }

    @Test
    public void readingASavedInventoryLeavesItAsItWas() throws CharacterStateValidationException {
        NBTTagCompound inventory = emptyInventory();
        NBTTagCompound before = (NBTTagCompound) inventory.copy();
        ItemStack[] worn = VanillaInventoryStateComponent.wornOf(inventory);
        assertEquals(CharacterGear.SLOTS, worn.length);
        for (ItemStack stack : worn) {
            assertNull(stack);
        }
        assertEquals(before, inventory);
    }

    @Test
    public void aSavedInventoryIsCheckedBeforeItIsRead() {
        NBTTagCompound inventory = emptyInventory();
        inventory.setInteger("Version", 2);
        try {
            VanillaInventoryStateComponent.wornOf(inventory);
            fail("a newer inventory is not read");
        } catch (CharacterStateValidationException expected) {
            assertEquals("Unsupported inventory component version 2",
                    expected.getMessage());
        }
    }

    private static NBTTagCompound emptyInventory() {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("Version", 1);
        state.setTag("Items", new NBTTagList());
        state.setInteger("CurrentItem", 3);
        return state;
    }

    private static RoleplayCharacter character(String name, int slot) {
        return RoleplayCharacter.builder(UUID.randomUUID(), OWNER)
                .slot(slot).name(name).race(CharacterRaceRegistry.HUMAN)
                .bodyType(CharacterBodyTypeRegistry.MASCULINE)
                .skin("losttales:human_bree_masculine_0").age(30)
                .startingFaction("lotr:bree").createdAt(1L)
                .build();
    }
}
