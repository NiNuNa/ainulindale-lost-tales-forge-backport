package com.ninuna.losttales.armour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * The relic mark (CONTENT-11): an ordinary armour piece carrying the id of
 * the relic set it belongs to, under {@code losttales_relic} in its item
 * data. The mark is all a relic piece is: it looks and weighs as its make
 * does, and its name and lore line come from the relic set's file. A
 * relic is handed out by a quest's {@code relic} reward, a starting kit's
 * {@code relic} entry or {@code /losttales relic}, as often as it is
 * handed out.
 */
public final class Relics {
    /** The item data key the mark is kept under. */
    public static final String TAG = "losttales_relic";

    private Relics() {}

    /** The relic set a piece is marked with; empty for an unmarked piece. */
    public static String relicOf(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return "";
        }
        String relic = stack.getTagCompound().getString(TAG);
        return ArmourFiles.isId(relic) ? relic : "";
    }

    /** Marks a piece with a relic set, in place. */
    public static void mark(ItemStack stack, String relicId) {
        if (stack == null || !ArmourFiles.isId(relicId)) {
            return;
        }
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound()
                : new NBTTagCompound();
        tag.setString(TAG, relicId);
        stack.setTagCompound(tag);
    }

    /** Whether a relic set of this id takes the item in any of its slots. */
    public static boolean takes(ArmourCatalogue catalogue, String relicId, String itemId) {
        NamedSet set = catalogue == null ? null : catalogue.set(relicId);
        if (set == null || !set.isRelic()) {
            return false;
        }
        for (ArmourSlot slot : ArmourSlot.values()) {
            if (set.items(slot).contains(itemId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The pieces a relic reward written {@code set} or {@code set/slot}
     * hands out, each the first item its slot takes that {@code game} has:
     * all four for a set, one for a slot. Empty for a relic the catalogue
     * does not hold, a slot that is none, or no item the game has.
     */
    public static List<Piece> pieces(ArmourCatalogue catalogue, String written, Game game) {
        String text = written == null ? "" : written.trim();
        int slash = text.indexOf('/');
        String relicId = slash < 0 ? text : text.substring(0, slash);
        NamedSet set = catalogue == null ? null : catalogue.set(relicId);
        if (set == null || !set.isRelic()) {
            return Collections.<Piece>emptyList();
        }
        List<ArmourSlot> slots = new ArrayList<ArmourSlot>();
        if (slash < 0) {
            Collections.addAll(slots, ArmourSlot.values());
        } else {
            ArmourSlot slot = ArmourSlot.fromWord(text.substring(slash + 1));
            if (slot == null) {
                return Collections.<Piece>emptyList();
            }
            slots.add(slot);
        }
        List<Piece> pieces = new ArrayList<Piece>();
        for (ArmourSlot slot : slots) {
            for (String item : set.items(slot)) {
                if (game.hasItem(item)) {
                    pieces.add(new Piece(set, slot, item));
                    break;
                }
            }
        }
        return pieces;
    }

    /** Whether a relic reward is written as a relic set's id, with a slot after a slash or none. */
    public static boolean isWritten(String written) {
        String text = written == null ? "" : written.trim();
        int slash = text.indexOf('/');
        if (slash < 0) {
            return ArmourFiles.isId(text);
        }
        return ArmourFiles.isId(text.substring(0, slash))
                && ArmourSlot.fromWord(text.substring(slash + 1)) != null;
    }

    /** A marked piece of {@code itemId}; null for an item the game does not have. */
    public static ItemStack make(String itemId, String relicId) {
        Object item = Item.itemRegistry.getObject(itemId);
        if (!(item instanceof Item)) {
            return null;
        }
        ItemStack stack = new ItemStack((Item) item, 1, 0);
        mark(stack, relicId);
        return stack;
    }

    /** What the game has, for a relic's items. */
    public interface Game {
        boolean hasItem(String itemId);
    }

    /** Every mod's items, as the running game has them. */
    public static final Game GAME = new Game() {
        @Override
        public boolean hasItem(String itemId) {
            return Item.itemRegistry.getObject(itemId) instanceof Item;
        }
    };

    /** One relic piece a reward hands out: its set, its slot and its item. */
    public static final class Piece {
        private final NamedSet set;
        private final ArmourSlot slot;
        private final String itemId;

        Piece(NamedSet set, ArmourSlot slot, String itemId) {
            this.set = set;
            this.slot = slot;
            this.itemId = itemId;
        }

        public NamedSet getSet() {
            return this.set;
        }

        public ArmourSlot getSlot() {
            return this.slot;
        }

        public String getItemId() {
            return this.itemId;
        }
    }
}
