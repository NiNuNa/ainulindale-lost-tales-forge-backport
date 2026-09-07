package com.ninuna.losttales.character.state.component;

import com.ninuna.losttales.character.state.CharacterStateComponent;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * What the state components refuse. Each one stands between a snapshot that
 * has been sitting in world data and the live player a switch is about to
 * put it on, so the version check, every bound, and the shape the component
 * itself writes are covered here.
 *
 * <p>Every case asserts the exact refusal message. Most of these fixtures
 * would be refused by some later rule as well, so a test that only asked
 * "was it refused" would keep passing after the bound it means to pin had
 * been widened.</p>

 * <p>The duplicate-slot rule in the inventory and the ender chest is not
 * covered: an entry built here carries no decodable item, so the first one
 * is refused by the item decode before a second naming the same slot is
 * read. Reaching that rule needs the item registry, and so a running
 * game.</p>
 */
public final class VanillaStateComponentValidationTest {

    private final VanillaInventoryStateComponent inventory =
            new VanillaInventoryStateComponent();
    private final VanillaEnderChestStateComponent enderChest =
            new VanillaEnderChestStateComponent();
    private final VanillaLocationStateComponent location =
            new VanillaLocationStateComponent();
    private final VanillaPotionStateComponent potions =
            new VanillaPotionStateComponent();
    private final VanillaStatisticsStateComponent statistics =
            new VanillaStatisticsStateComponent();
    private final LostTalesQuestStateComponent quests =
            new LostTalesQuestStateComponent();

    /**
     * Nothing at all, a state with no version, and a version stored as text
     * are each refused. Every component writes its own message, so each is
     * asserted against its own component.
     */
    @Test
    public void everyComponentRefusesAStateWithNoUsableVersion() {
        CharacterStateComponent[] components = new CharacterStateComponent[] {
                this.inventory, this.enderChest, this.location,
                this.potions, this.statistics, this.quests,
        };
        String[] messages = new String[] {
                "Inventory component version is missing",
                "Ender-chest component version is missing",
                "Location component version or kind is missing",
                "Potion component version is missing",
                "Vanilla statistics component version is missing",
                "Lost Tales quest component version is missing",
        };
        for (int index = 0; index < components.length; index++) {
            assertRefused(components[index], null, messages[index]);
            assertRefused(components[index], new NBTTagCompound(),
                    messages[index]);

            NBTTagCompound text = new NBTTagCompound();
            text.setString("Version", "1");
            assertRefused(components[index], text, messages[index]);
        }
    }

    // ---------------------------------------------------------------
    // Inventory
    // ---------------------------------------------------------------

    @Test
    public void anEmptyInventoryDefaultValidates()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.inventory.createDefault();
        this.inventory.validate(state);

        assertEquals(1, state.getInteger("Version"));
        assertEquals(0, state.getInteger("CurrentItem"));
        assertEquals(0, state.getTagList(
                "Items", Constants.NBT.TAG_COMPOUND).tagCount());
    }

    @Test
    public void anInventoryFromANewerBuildIsRefused() {
        NBTTagCompound state = this.inventory.createDefault();
        state.setInteger("Version", 2);

        assertRefused(this.inventory, state,
                "Unsupported inventory component version 2");
    }

    @Test
    public void anInventoryWithoutAnItemListIsRefused() {
        NBTTagCompound state = this.inventory.createDefault();
        state.removeTag("Items");

        assertRefused(this.inventory, state, "Inventory item list is missing");
    }

    /** Forty is every slot a player has: thirty-six carried and four worn. */
    @Test
    public void anInventoryPastFortySerializedSlotsIsRefused() {
        NBTTagCompound state = this.inventory.createDefault();
        state.setTag("Items", emptyCompounds(41));

        assertRefused(this.inventory, state,
                "Inventory contains too many serialized slots: 41");
    }

    @Test
    public void anInventoryEntryWithNoSlotIsRefused() {
        assertRefused(this.inventory, inventoryWith(new NBTTagCompound()),
                "Inventory entry 0 has no slot");
    }

    /** Vanilla writes the slot as a byte, so an int is not a slot. */
    @Test
    public void anInventoryEntryWithoutAByteSlotIsRefused() {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("Slot", 0);

        assertRefused(this.inventory, inventoryWith(entry),
                "Inventory entry 0 has no slot");
    }

    /** Slots between the carried range and the worn one belong to nothing. */
    @Test
    public void anInventorySlotBetweenTheBagAndTheArmourIsRefused() {
        assertRefused(this.inventory, inventoryWith(slotEntry(36)),
                "Inventory entry uses unsupported slot 36");
        assertRefused(this.inventory, inventoryWith(slotEntry(50)),
                "Inventory entry uses unsupported slot 50");
    }

    @Test
    public void anInventorySlotPastTheArmourIsRefused() {
        assertRefused(this.inventory, inventoryWith(slotEntry(104)),
                "Inventory entry uses unsupported slot 104");
    }

    /** A slot that carries no decodable item is refused, not quietly emptied. */
    @Test
    public void anInventoryEntryWithNoDecodableItemIsRefused() {
        assertRefused(this.inventory, inventoryWith(slotEntry(0)),
                "Inventory entry 0 is invalid or overstacked");
    }

    /** The held slot is one of the nine on the hotbar. */
    @Test
    public void aSelectedSlotOutsideTheHotbarIsRefused() {
        NBTTagCompound past = this.inventory.createDefault();
        past.setInteger("CurrentItem", 9);
        assertRefused(this.inventory, past,
                "Selected hotbar slot is invalid: 9");

        NBTTagCompound negative = this.inventory.createDefault();
        negative.setInteger("CurrentItem", -1);
        assertRefused(this.inventory, negative,
                "Selected hotbar slot is invalid: -1");
    }

    @Test
    public void theLastHotbarSlotIsAccepted()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.inventory.createDefault();
        state.setInteger("CurrentItem", 8);

        this.inventory.validate(state);
    }

    // ---------------------------------------------------------------
    // Ender chest
    // ---------------------------------------------------------------

    @Test
    public void anEmptyEnderChestDefaultValidates()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.enderChest.createDefault();
        this.enderChest.validate(state);

        assertEquals(1, state.getInteger("Version"));
        assertEquals(0, state.getTagList(
                "Items", Constants.NBT.TAG_COMPOUND).tagCount());
    }

    @Test
    public void anEnderChestFromANewerBuildIsRefused() {
        NBTTagCompound state = this.enderChest.createDefault();
        state.setInteger("Version", 2);

        assertRefused(this.enderChest, state,
                "Unsupported ender-chest component version 2");
    }

    @Test
    public void anEnderChestWithoutAnItemListIsRefused() {
        NBTTagCompound state = this.enderChest.createDefault();
        state.removeTag("Items");

        assertRefused(this.enderChest, state,
                "Ender-chest item list is missing");
    }

    /** The ender chest is twenty-seven slots and no more. */
    @Test
    public void anEnderChestPastItsTwentySevenSlotsIsRefused() {
        NBTTagCompound state = this.enderChest.createDefault();
        state.setTag("Items", emptyCompounds(28));

        assertRefused(this.enderChest, state,
                "Ender chest contains too many serialized slots");
    }

    @Test
    public void anEnderChestEntryOneSlotPastTheChestIsRefused() {
        assertRefused(this.enderChest, enderChestWith(slotEntry(27)),
                "Ender-chest entry uses unsupported slot 27");
    }

    @Test
    public void anEnderChestEntryWithoutAByteSlotIsRefused() {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("Slot", 3);

        assertRefused(this.enderChest, enderChestWith(entry),
                "Ender-chest entry 0 has no slot");
    }

    // ---------------------------------------------------------------
    // Location
    // ---------------------------------------------------------------

    @Test
    public void theDefaultLocationIsAWorldSpawnOfTwoFields()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.location.createDefault();
        this.location.validate(state);

        assertEquals(VanillaLocationStateComponent.KIND_WORLD_SPAWN,
                this.location.getKind(state));
        assertEquals(2, state.func_150296_c().size());
    }

    @Test
    public void aWorldSpawnCarryingAnyOtherFieldIsRefused() {
        NBTTagCompound state = this.location.createDefault();
        state.setInteger("Dimension", 0);

        assertRefused(this.location, state,
                "World-spawn location contains unsupported fields");
    }

    @Test
    public void aLocationFromANewerBuildIsRefused() {
        NBTTagCompound state = this.location.createDefault();
        state.setInteger("Version", 2);

        assertRefused(this.location, state,
                "Location component version or kind is missing");
    }

    /**
     * A location says which of its three kinds it is; the coordinator moves
     * the player on it, so one that does not say is refused before anything
     * is moved.
     */
    @Test
    public void aLocationWithoutAKindIsRefused() {
        NBTTagCompound state = this.location.createDefault();
        state.removeTag("Kind");

        assertRefused(this.location, state,
                "Location component version or kind is missing");
    }

    @Test
    public void anUnknownLocationKindIsRefused() {
        NBTTagCompound state = this.location.createDefault();
        state.setString("Kind", "bed");

        assertRefused(this.location, state,
                "Unsupported character location kind bed");
    }

    @Test
    public void aCapturedPositionValidates()
            throws CharacterStateValidationException {
        NBTTagCompound state =
                position(0, 100.5D, 64.0D, -200.5D, 90.0F, -12.0F);
        this.location.validate(state);

        assertEquals(8, state.func_150296_c().size());
        assertEquals(64.0D, this.location.getY(state), 0.0D);
    }

    @Test
    public void anIncompletePositionIsRefused() {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("Version", 1);
        state.setString("Kind", VanillaLocationStateComponent.KIND_POSITION);
        state.setInteger("Dimension", 0);
        state.setDouble("X", 0.0D);

        assertRefused(this.location, state,
                "Stored character position is incomplete");
    }

    @Test
    public void aPositionMissingOneAngleIsRefused() {
        NBTTagCompound state = position(0, 1.0D, 64.0D, 1.0D, 0.0F, 0.0F);
        state.removeTag("Pitch");

        assertRefused(this.location, state,
                "Stored character position is incomplete");
    }

    @Test
    public void aPositionCarryingAnExtraFieldIsRefused() {
        NBTTagCompound state = position(0, 1.0D, 64.0D, 1.0D, 0.0F, 0.0F);
        state.setString("WaypointId", "lotr:bree");

        assertRefused(this.location, state,
                "Stored character position is incomplete");
    }

    @Test
    public void aPositionWithAFloatCoordinateIsRefused() {
        NBTTagCompound state = position(0, 1.0D, 64.0D, 1.0D, 0.0F, 0.0F);
        state.setFloat("X", 1.0F);

        assertRefused(this.location, state,
                "Stored character position is incomplete");
    }

    /** A coordinate that is not a number would move the player nowhere real. */
    @Test
    public void aPositionThatIsNotANumberIsRefused() {
        assertRefused(this.location,
                position(0, Double.NaN, 64.0D, 1.0D, 0.0F, 0.0F),
                "Stored character X is not finite");
        assertRefused(this.location,
                position(0, 1.0D, 64.0D, Double.POSITIVE_INFINITY, 0.0F, 0.0F),
                "Stored character Z is not finite");
        assertRefused(this.location,
                position(0, 1.0D, 64.0D, 1.0D, Float.POSITIVE_INFINITY, 0.0F),
                "Stored character yaw is not finite");
    }

    @Test
    public void aPositionOutsideTheSafeHeightIsRefused() {
        assertRefused(this.location,
                position(0, 1.0D, 4096.5D, 1.0D, 0.0F, 0.0F),
                "Stored character Y coordinate is outside the safe range");
        assertRefused(this.location,
                position(0, 1.0D, -4096.5D, 1.0D, 0.0F, 0.0F),
                "Stored character Y coordinate is outside the safe range");
    }

    @Test
    public void theEdgesOfTheSafeHeightAreAccepted()
            throws CharacterStateValidationException {
        this.location.validate(position(0, 1.0D, 4096.0D, 1.0D, 0.0F, 0.0F));
        this.location.validate(position(0, 1.0D, -4096.0D, 1.0D, 0.0F, 0.0F));
    }

    @Test
    public void aPositionPastTheWorldBoundaryIsRefused() {
        assertRefused(this.location,
                position(0, 30000000.5D, 64.0D, 1.0D, 0.0F, 0.0F),
                "Stored character position is outside the world boundary");
        assertRefused(this.location,
                position(0, 1.0D, 64.0D, -30000000.5D, 0.0F, 0.0F),
                "Stored character position is outside the world boundary");
    }

    @Test
    public void theWorldBoundaryItselfIsAccepted()
            throws CharacterStateValidationException {
        this.location.validate(
                position(0, 30000000.0D, 64.0D, -30000000.0D, 0.0F, 0.0F));
    }

    @Test
    public void aStartingWaypointValidates()
            throws CharacterStateValidationException {
        NBTTagCompound state = startingWaypoint("lotr:bree");
        this.location.validate(state);

        assertEquals("lotr:bree", this.location.getWaypointId(state));
    }

    @Test
    public void aStartingWaypointIsStoredNormalized()
            throws CharacterStateValidationException {
        NBTTagCompound state =
                this.location.createStartingWaypoint("  LOTR:Bree  ");
        this.location.validate(state);

        assertEquals("lotr:bree", this.location.getWaypointId(state));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aStartingWaypointWithoutTheLotrPrefixIsRejectedAtCreation() {
        this.location.createStartingWaypoint("bree");
    }

    /** A starting waypoint carries an identifier and nothing else. */
    @Test
    public void aStartingWaypointWithoutAnIdentifierIsRefused() {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("Version", 1);
        state.setString("Kind",
                VanillaLocationStateComponent.KIND_STARTING_WAYPOINT);

        assertRefused(this.location, state,
                "Starting-waypoint location is incomplete");
    }

    @Test
    public void aStartingWaypointCarryingAnExtraFieldIsRefused() {
        NBTTagCompound state = startingWaypoint("lotr:bree");
        state.setInteger("Dimension", 0);

        assertRefused(this.location, state,
                "Starting-waypoint location is incomplete");
    }

    /** Stored identifiers are already normalized, not merely normalizable. */
    @Test
    public void anIdentifierThatIsNotAlreadyNormalizedIsRefused() {
        assertRefused(this.location, startingWaypoint(""),
                "Starting-waypoint location has an invalid identifier");
        assertRefused(this.location, startingWaypoint("LOTR:Bree"),
                "Starting-waypoint location has an invalid identifier");
        assertRefused(this.location, startingWaypoint("bree"),
                "Starting-waypoint location has an invalid identifier");
        assertRefused(this.location,
                startingWaypoint(LotrCharacterAdapter.ID_PREFIX),
                "Starting-waypoint location has an invalid identifier");
    }

    @Test
    public void aStartingWaypointPastSixtyFourCharactersIsRefused() {
        assertRefused(this.location,
                startingWaypoint(LotrCharacterAdapter.ID_PREFIX
                        + repeat('a', 60)),
                "Starting-waypoint location has an invalid identifier");
    }

    // ---------------------------------------------------------------
    // Potions
    // ---------------------------------------------------------------

    @Test
    public void anUnpoisonedDefaultValidates()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.potions.createDefault();
        this.potions.validate(state);

        assertEquals(1, state.getInteger("Version"));
        assertEquals(0, state.getTagList(
                "Effects", Constants.NBT.TAG_COMPOUND).tagCount());
    }

    @Test
    public void potionsFromANewerBuildAreRefused() {
        NBTTagCompound state = this.potions.createDefault();
        state.setInteger("Version", 2);

        assertRefused(this.potions, state,
                "Unsupported potion component version 2");
    }

    @Test
    public void potionsWithoutAnEffectListAreRefused() {
        NBTTagCompound state = this.potions.createDefault();
        state.removeTag("Effects");

        assertRefused(this.potions, state, "Potion effect list is missing");
    }

    /**
     * More effects than there are potions is a list that grew rather than a
     * player who drank. The count is checked before any entry is decoded.
     */
    @Test
    public void moreThanSixtyFourEffectsAreRefused() {
        NBTTagCompound state = this.potions.createDefault();
        state.setTag("Effects", emptyCompounds(65));

        assertRefused(this.potions, state,
                "Too many active potion effects: 65");
    }

    @Test
    public void aSingleWellFormedEffectValidates()
            throws CharacterStateValidationException {
        this.potions.validate(potionsWith(effect(1, 100, 0)));
    }

    /** Potion 0 is not a registered type, so the effect cannot be decoded. */
    @Test
    public void anEffectOfAnUnregisteredPotionIsRefused() {
        assertRefused(this.potions, potionsWith(effect(0, 100, 0)),
                "Potion effect 0 is invalid");
    }

    @Test
    public void anEffectThatHasAlreadyExpiredIsRefused() {
        assertRefused(this.potions, potionsWith(effect(1, 0, 0)),
                "Potion effect 0 is invalid");
    }

    @Test
    public void anEffectWithANegativeAmplifierIsRefused() {
        assertRefused(this.potions, potionsWith(effect(1, 100, -1)),
                "Potion effect 0 is invalid");
    }

    @Test
    public void twoEffectsOfOnePotionAreRefused() {
        assertRefused(this.potions,
                potionsWith(effect(1, 100, 0), effect(1, 200, 1)),
                "Duplicate potion effect 1");
    }

    // ---------------------------------------------------------------
    // Statistics
    // ---------------------------------------------------------------

    @Test
    public void anUnplayedStatisticsDefaultValidates()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.statistics.createDefault();
        this.statistics.validate(state);

        assertEquals(1, state.getInteger("Version"));
    }

    @Test
    public void statisticsFromANewerBuildAreRefused() {
        NBTTagCompound state = this.statistics.createDefault();
        state.setInteger("Version", 2);

        assertRefused(this.statistics, state,
                "Unsupported vanilla statistics component version 2");
    }

    @Test
    public void statisticsWithoutAnEntryListAreRefused() {
        NBTTagCompound state = this.statistics.createDefault();
        state.removeTag("Entries");

        assertRefused(this.statistics, state,
                "Vanilla statistics entry list is missing");
    }

    /** The entry list is read as a raw tag, so a string list is caught here. */
    @Test
    public void aStatisticsEntryListOfStringsIsRefused() {
        NBTTagCompound state = this.statistics.createDefault();
        NBTTagList entries = new NBTTagList();
        entries.appendTag(new NBTTagString("stat.deaths"));
        state.setTag("Entries", entries);

        assertRefused(this.statistics, state,
                "Vanilla statistics entry list has an invalid element type");
    }

    @Test
    public void moreThan32768StatisticsAreRefused() {
        NBTTagCompound state = this.statistics.createDefault();
        state.setTag("Entries", emptyCompounds(32769));

        assertRefused(this.statistics, state,
                "Vanilla statistics contain too many entries: 32769");
    }

    @Test
    public void aStatisticMissingEitherHalfIsRefused() {
        NBTTagCompound noValue = new NBTTagCompound();
        noValue.setString("StatId", "stat.deaths");
        assertRefused(this.statistics, statisticsWith(noValue),
                "Vanilla statistic entry 0 is incomplete");

        NBTTagCompound noIdentifier = new NBTTagCompound();
        noIdentifier.setInteger("Value", 1);
        assertRefused(this.statistics, statisticsWith(noIdentifier),
                "Vanilla statistic entry 0 is incomplete");
    }

    @Test
    public void anEmptyStatisticIdentifierIsRefused() {
        assertRefused(this.statistics, statisticsWith(statistic("", 1)),
                "Vanilla statistic identifier is invalid or duplicated: ");
    }

    @Test
    public void aStatisticIdentifierPastAThousandCharactersIsRefused() {
        try {
            this.statistics.validate(
                    statisticsWith(statistic(repeat('s', 1025), 1)));
            fail("An oversized statistic identifier must be refused");
        } catch (CharacterStateValidationException expected) {
            assertTrue(expected.getMessage().startsWith(
                    "Vanilla statistic identifier is invalid or duplicated:"));
        }
    }

    @Test
    public void oneStatisticStoredTwiceIsRefused() {
        assertRefused(this.statistics,
                statisticsWith(statistic("stat.deaths", 1),
                        statistic("stat.deaths", 2)),
                "Vanilla statistic identifier is invalid or duplicated: "
                        + "stat.deaths");
    }

    @Test
    public void aStatisticThisServerDoesNotKnowIsRefused() {
        assertRefused(this.statistics,
                statisticsWith(statistic("losttales.not_a_stat", 1)),
                "Vanilla statistic is not registered on this server: "
                        + "losttales.not_a_stat");
    }

    @Test
    public void aNegativeStatisticIsRefused() {
        assertRefused(this.statistics,
                statisticsWith(statistic("stat.deaths", -1)),
                "Vanilla statistic has a negative value: stat.deaths");
    }

    @Test
    public void aRegisteredStatisticValidates()
            throws CharacterStateValidationException {
        this.statistics.validate(statisticsWith(statistic("stat.deaths", 7)));
    }

    /** Progress is stored as a list of chunks, never as one plain string. */
    @Test
    public void statisticProgressStoredAsAStringIsRefused() {
        NBTTagCompound entry = statistic("stat.deaths", 1);
        entry.setString("Progress", "{}");

        assertRefused(this.statistics, statisticsWith(entry),
                "Vanilla statistic progress has an invalid NBT type");
    }

    @Test
    public void statisticProgressWithNoChunksIsRefused() {
        NBTTagCompound entry = statistic("stat.deaths", 1);
        entry.setTag("Progress", new NBTTagList());

        assertRefused(this.statistics, statisticsWith(entry),
                "Vanilla statistic progress chunks are invalid");
    }

    @Test
    public void aProgressChunkPastTwelveThousandCharactersIsRefused() {
        NBTTagCompound entry = statistic("stat.deaths", 1);
        entry.setTag("Progress", chunks(repeat('a', 12001)));

        assertRefused(this.statistics, statisticsWith(entry),
                "Vanilla statistic progress chunk is invalid");
    }

    @Test
    public void aProgressChunkWithNoCharactersIsRefused() {
        NBTTagCompound entry = statistic("stat.deaths", 1);
        entry.setTag("Progress", chunks(""));

        assertRefused(this.statistics, statisticsWith(entry),
                "Vanilla statistic progress chunk is invalid");
    }

    /** A plain counter carries no progress object, so any payload is refused. */
    @Test
    public void progressOnAPlainCounterIsRefused() {
        NBTTagCompound entry = statistic("stat.deaths", 1);
        entry.setTag("Progress", chunks("{}"));

        assertRefused(this.statistics, statisticsWith(entry),
                "Vanilla statistic does not support progress: stat.deaths");
    }

    // ---------------------------------------------------------------
    // Lost Tales quests
    // ---------------------------------------------------------------

    @Test
    public void aFreshQuestJournalValidates()
            throws CharacterStateValidationException {
        NBTTagCompound state = this.quests.createDefault();
        this.quests.validate(state);

        assertEquals(1, state.getInteger("Version"));
        assertEquals(2, state.func_150296_c().size());
    }

    @Test
    public void aQuestJournalFromANewerBuildIsRefused() {
        NBTTagCompound state = this.quests.createDefault();
        state.setInteger("Version", 2);

        assertRefused(this.quests, state,
                "Unsupported Lost Tales quest component version 2");
    }

    @Test
    public void aQuestJournalCarryingAnExtraFieldIsRefused() {
        NBTTagCompound state = this.quests.createDefault();
        state.setString("Extra", "x");

        assertRefused(this.quests, state, "Lost Tales quest component is "
                + "incomplete or contains unsupported fields");
    }

    @Test
    public void aQuestJournalWhoseDataIsNotACompoundIsRefused() {
        NBTTagCompound state = this.quests.createDefault();
        state.setString("QuestData", "x");

        assertRefused(this.quests, state, "Lost Tales quest component is "
                + "incomplete or contains unsupported fields");
    }

    @Test
    public void aQuestPayloadHoldingAFloatIsRefused() {
        NBTTagCompound state = this.quests.createDefault();
        state.getCompoundTag("QuestData").setFloat("Bad", 1.0F);

        assertRefused(this.quests, state,
                "Lost Tales quest state contains unsupported NBT type 5");
    }

    @Test
    public void aQuestPayloadHoldingANonFiniteNumberIsRefused() {
        NBTTagCompound state = this.quests.createDefault();
        state.getCompoundTag("QuestData").setDouble("Bad", Double.NaN);

        assertRefused(this.quests, state,
                "Lost Tales quest state contains a non-finite number");
    }

    @Test
    public void aQuestPayloadListOfStringsIsRefused() {
        NBTTagCompound state = this.quests.createDefault();
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagString("nope"));
        state.getCompoundTag("QuestData").setTag("Bad", list);

        assertRefused(this.quests, state,
                "Lost Tales quest state contains an invalid or oversized list");
    }

    @Test
    public void aQuestPayloadWithAnEmptyKeyIsRefused() {
        NBTTagCompound state = this.quests.createDefault();
        state.getCompoundTag("QuestData").setString("", "x");

        assertRefused(this.quests, state,
                "Lost Tales quest state contains an invalid key length");
    }

    @Test
    public void aQuestPayloadStringPastItsLimitIsRefused() {
        NBTTagCompound state = this.quests.createDefault();
        state.getCompoundTag("QuestData").setString("Bad", repeat('a', 32768));

        assertRefused(this.quests, state,
                "Lost Tales quest state contains an oversized string");
    }

    /** Nesting is bounded before the payload is decoded at all. */
    @Test
    public void aQuestPayloadNestedPastItsDepthIsRefused() {
        NBTTagCompound state = this.quests.createDefault();
        state.getCompoundTag("QuestData").setTag("Deep", nested(30));

        assertRefused(this.quests, state,
                "Lost Tales quest state exceeds the expanded structure limit");
    }

    /** A payload is refused unless it is exactly what the store writes back. */
    @Test
    public void aQuestPayloadWithAnUnknownFieldIsRefusedAsNonCanonical() {
        NBTTagCompound state = this.quests.createDefault();
        state.getCompoundTag("QuestData").setString("Bogus", "x");

        assertRefused(this.quests, state,
                "Lost Tales quest state is malformed or unsupported");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static void assertRefused(CharacterStateComponent component,
                                      NBTTagCompound state, String message) {
        try {
            component.validate(state);
            fail("Expected a refusal: " + message);
        } catch (CharacterStateValidationException expected) {
            assertEquals(message, expected.getMessage());
        }
    }

    private static NBTTagList emptyCompounds(int count) {
        NBTTagList list = new NBTTagList();
        for (int index = 0; index < count; index++) {
            list.appendTag(new NBTTagCompound());
        }
        return list;
    }

    private static NBTTagList listOf(NBTTagCompound[] entries) {
        NBTTagList list = new NBTTagList();
        for (int index = 0; index < entries.length; index++) {
            list.appendTag(entries[index]);
        }
        return list;
    }

    private NBTTagCompound inventoryWith(NBTTagCompound... entries) {
        NBTTagCompound state = this.inventory.createDefault();
        state.setTag("Items", listOf(entries));
        return state;
    }

    private NBTTagCompound enderChestWith(NBTTagCompound... entries) {
        NBTTagCompound state = this.enderChest.createDefault();
        state.setTag("Items", listOf(entries));
        return state;
    }

    private NBTTagCompound potionsWith(NBTTagCompound... effects) {
        NBTTagCompound state = this.potions.createDefault();
        state.setTag("Effects", listOf(effects));
        return state;
    }

    private NBTTagCompound statisticsWith(NBTTagCompound... entries) {
        NBTTagCompound state = this.statistics.createDefault();
        state.setTag("Entries", listOf(entries));
        return state;
    }

    /** An entry naming a slot and no item. */
    private static NBTTagCompound slotEntry(int slot) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setByte("Slot", (byte) slot);
        return entry;
    }

    /** The four fields a potion effect writes when it is captured. */
    private static NBTTagCompound effect(int id, int duration, int amplifier) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setByte("Id", (byte) id);
        entry.setByte("Amplifier", (byte) amplifier);
        entry.setInteger("Duration", duration);
        entry.setBoolean("Ambient", false);
        return entry;
    }

    private static NBTTagCompound statistic(String statId, int value) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("StatId", statId);
        entry.setInteger("Value", value);
        return entry;
    }

    private static NBTTagList chunks(String text) {
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagString(text));
        return list;
    }

    private static NBTTagCompound position(int dimension, double x, double y,
                                           double z, float yaw, float pitch) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("Version", 1);
        state.setString("Kind", VanillaLocationStateComponent.KIND_POSITION);
        state.setInteger("Dimension", dimension);
        state.setDouble("X", x);
        state.setDouble("Y", y);
        state.setDouble("Z", z);
        state.setFloat("Yaw", yaw);
        state.setFloat("Pitch", pitch);
        return state;
    }

    private static NBTTagCompound startingWaypoint(String waypointId) {
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("Version", 1);
        state.setString("Kind",
                VanillaLocationStateComponent.KIND_STARTING_WAYPOINT);
        state.setString("WaypointId", waypointId);
        return state;
    }

    private static NBTTagCompound nested(int depth) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound current = root;
        for (int index = 0; index < depth; index++) {
            NBTTagCompound child = new NBTTagCompound();
            current.setTag("Nested", child);
            current = child;
        }
        return root;
    }

    private static String repeat(char character, int length) {
        StringBuilder text = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            text.append(character);
        }
        return text.toString();
    }
}
