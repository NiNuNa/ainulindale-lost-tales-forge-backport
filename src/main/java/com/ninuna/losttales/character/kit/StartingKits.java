package com.ninuna.losttales.character.kit;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.character.state.component.VanillaInventoryStateComponent;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.progression.ProgressionFiles;
import com.ninuna.losttales.util.LostTalesLog;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * The starting kits the logical server knows (CHAR-40): the bundled race
 * kits, with a server's own in place of those of the same id and its new
 * ones after. Read as the server starts; cleared as it stops.
 *
 * <p>A character is given every kit that fits it in its first saved
 * state, which is made once, as the character is first stored: a switch
 * back to it or a rejoin plays the state it saved since, and the ghost
 * has no first state of its own.</p>
 */
public final class StartingKits {
    /** The main inventory's slots, the hotbar's nine first. */
    public static final int SLOTS = 36;

    private static List<StartingKit> kits = Collections.<StartingKit>emptyList();

    private StartingKits() {}

    /** The game's own races, LOTR's factions and every mod's items, as the running game has them. */
    public static final StartingKitFiles.Game GAME = new StartingKitFiles.Game() {
        @Override
        public boolean hasRace(String raceId) {
            // A stand-in names a race gone; no new character is made as one.
            CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
            return race != null && !race.isStandIn();
        }

        @Override
        public boolean hasFaction(String factionId) {
            return LotrCharacterAdapter.getInstance().resolve(factionId) != null;
        }

        @Override
        public int stackLimit(String itemId, int damage) {
            Item item = item(itemId);
            return item == null ? 0 : Math.max(1, new ItemStack(item, 1, damage).getMaxStackSize());
        }

        @Override
        public boolean wornThrough(String itemId, int damage) {
            Item item = item(itemId);
            return item != null && item.isDamageable() && damage >= item.getMaxDamage();
        }
    };

    /** Reads every kit file again; answers why each file left out was left out. */
    public static synchronized List<String> load() {
        return load(ProgressionFiles.CLASSPATH,
                ProgressionFiles.folder(StartingKitFiles.FOLDER), GAME);
    }

    static synchronized List<String> load(DataFiles.Source bundledSource, File folder,
                                          StartingKitFiles.Game game) {
        DataFiles.Kind<StartingKit> kind = StartingKitFiles.kind(game);
        DataFiles.Result<StartingKit> bundled = DataFiles.readBundled(bundledSource,
                StartingKitFiles.INDEX, StartingKitFiles.LIST_KEY, kind);
        DataFiles.Result<StartingKit> server = DataFiles.readFolder(folder, kind);
        List<String> problems = new ArrayList<String>();
        note(problems, "Bundled starting kit", bundled.problems);
        note(problems, "Server starting kit", server.problems);
        kits = Collections.unmodifiableList(DataFiles.merge(bundled.values, server.values, kind));
        return problems;
    }

    public static synchronized void clear() {
        kits = Collections.<StartingKit>emptyList();
    }

    /** Every kit, the bundled ones in their index's order and a server's own after. */
    public static synchronized List<StartingKit> kits() {
        return kits;
    }

    /** The kits a character of {@code raceId} starting in {@code factionId} is given, in order. */
    public static List<StartingKit> fitting(List<StartingKit> all, String raceId, String factionId) {
        List<StartingKit> fitting = new ArrayList<StartingKit>();
        for (StartingKit kit : all) {
            if (kit.fits(raceId, factionId)) {
                fitting.add(kit);
            }
        }
        return fitting;
    }

    /** The faction a character's kits are matched by: the one it starts in, Unaligned for none. */
    public static String factionOf(RoleplayCharacter character) {
        return LotrCharacterAdapter.factionIdOrUnaligned(character.getStartingFactionId());
    }

    /** Where the kits' items go, and what found no room. */
    public static final class Packed {
        public final List<KitItem> slots;
        public final List<KitItem> leftOut;

        Packed(List<KitItem> slots, List<KitItem> leftOut) {
            this.slots = Collections.unmodifiableList(slots);
            this.leftOut = Collections.unmodifiableList(leftOut);
        }
    }

    /**
     * Lays the kits' items into the main inventory in order: each fills the
     * stacks of the same item and damage before it starts new ones, and
     * what no slot holds is left out. An item the game lacks is left out
     * whole.
     */
    public static Packed pack(List<StartingKit> kitsGiven, StartingKitFiles.Game game) {
        List<KitItem> slots = new ArrayList<KitItem>();
        List<KitItem> leftOut = new ArrayList<KitItem>();
        for (StartingKit kit : kitsGiven) {
            for (KitItem item : kit.getItems()) {
                int limit = game.stackLimit(item.getItemId(), item.getDamage());
                if (limit <= 0) {
                    leftOut.add(item);
                    continue;
                }
                int remaining = item.getCount();
                for (int index = 0; index < slots.size() && remaining > 0; index++) {
                    KitItem slot = slots.get(index);
                    if (slot.stacksWith(item) && slot.getCount() < limit) {
                        int moved = Math.min(limit - slot.getCount(), remaining);
                        slots.set(index, slot.withCount(slot.getCount() + moved));
                        remaining -= moved;
                    }
                }
                while (remaining > 0 && slots.size() < SLOTS) {
                    int moved = Math.min(limit, remaining);
                    slots.add(item.withCount(moved));
                    remaining -= moved;
                }
                if (remaining > 0) {
                    leftOut.add(item.withCount(remaining));
                }
            }
        }
        return new Packed(slots, leftOut);
    }

    /**
     * A new character's first inventory: every kit that fits it, laid out
     * by {@link #pack}. Should the kits' items not make a valid inventory,
     * the character starts with an empty one and the log says why; a
     * character is never refused for its kit.
     */
    public static NBTTagCompound firstInventory(VanillaInventoryStateComponent component,
                                                RoleplayCharacter character) {
        if (character == null) {
            return component.createDefault();
        }
        List<StartingKit> given = fitting(kits(), character.getRaceId(), factionOf(character));
        if (given.isEmpty()) {
            return component.createDefault();
        }
        Packed packed = pack(given, GAME);
        if (!packed.leftOut.isEmpty()) {
            LostTalesLog.warning("Character %s found no room for %s of its starting kits",
                    character.getCharacterId(), packed.leftOut);
        }
        try {
            List<ItemStack> stacks = new ArrayList<ItemStack>();
            for (KitItem slot : packed.slots) {
                stacks.add(new ItemStack(item(slot.getItemId()), slot.getCount(), slot.getDamage()));
            }
            NBTTagCompound state = component.createDefault(stacks);
            component.validate(state);
            return state;
        } catch (CharacterStateValidationException invalid) {
            LostTalesLog.warning("Character %s starts with an empty inventory; its starting"
                    + " kits do not make a valid one: %s", character.getCharacterId(),
                    invalid.getMessage());
        } catch (RuntimeException failure) {
            LostTalesLog.warning("Character %s starts with an empty inventory; its starting"
                    + " kits could not be made: %s", character.getCharacterId(), failure.toString());
        }
        return component.createDefault();
    }

    private static Item item(String itemId) {
        Object found = Item.itemRegistry.getObject(itemId);
        return found instanceof Item ? (Item) found : null;
    }

    private static void note(List<String> problems, String what, List<String> found) {
        for (String problem : found) {
            problems.add(problem);
            LostTalesLog.warning("%s left out: %s", what, problem);
        }
    }
}
