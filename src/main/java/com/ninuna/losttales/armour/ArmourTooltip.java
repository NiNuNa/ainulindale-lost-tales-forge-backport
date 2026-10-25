package com.ninuna.losttales.armour;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines an armour piece's tooltip gives its weight and its named sets
 * (CONTENT-06, CONTENT-07, CONTENT-11): how many pieces of its weight are
 * worn and what the weight set gives, what a heavy piece slows, what each
 * slot holds, then each named set it counts for, {@code [3/4] Gondorian
 * Set}, with its bonus. A bonus in force reads in the game's attribute
 * blue, one not yet in force in dark grey. The words come from a
 * {@link Words}, so the lines are tested without a game.
 */
public final class ArmourTooltip {
    private static final String COUNT = "§e";
    private static final String NAME = "§r§7";
    private static final String IN_FORCE = "§9  ";
    private static final String NOT_YET = "§8  ";
    private static final String SLOT = "§8>§o ";
    private static final String SLOT_COUNTED = "§e>§o ";

    private ArmourTooltip() {}

    /** The words of the lines, in the reader's language. */
    public interface Words {
        /** {@code Light Armor Set}, {@code Heavy Armor Set}. */
        String weightSet(ArmourWeight weight);

        /** A named set's name, {@code Gondorian Set}. */
        String setName(NamedSet set);

        /** What one effect of a set's bonus does, its number written in. */
        String bonus(SetBonus bonus);

        /** What the weight set gives: four light pieces' speed or four heavy pieces' footing. */
        String weightBonus(ArmourWeight weight, double amount);

        /** What a heavy piece slows, {@code Slows you by 2.5%}. */
        String slow(double share);

        /** A worn slot's piece by name; {@code Empty} for none. */
        String slotItem(ArmourSlot slot);
    }

    /**
     * The lines for a piece of {@code weight} made for {@code slot}, with
     * its item and its relic mark, while {@code worn} is worn. Empty for a
     * piece of neither weight that counts for no named set.
     */
    public static List<String> lines(Words words, WornArmour worn, ArmourCatalogue catalogue,
                                     ArmourSettings settings, ArmourWeight weight,
                                     ArmourSlot slot, String itemId, String relicId) {
        List<String> lines = new ArrayList<String>();
        List<NamedSet> sets = catalogue.setsOf(slot, itemId, relicId);
        if (weight.counts()) {
            int count = worn.count(weight);
            lines.add(COUNT + "[" + count + "/4] " + NAME + words.weightSet(weight));
            double amount = weight == ArmourWeight.LIGHT ? settings.getLightSpeed()
                    : settings.getHeavyKnockback();
            if (amount > 0.0D) {
                lines.add((count >= 4 ? IN_FORCE : NOT_YET) + words.weightBonus(weight, amount));
            }
            if (weight == ArmourWeight.HEAVY && slot != null && settings.slowOf(slot) > 0.0D) {
                lines.add(NOT_YET + words.slow(settings.slowOf(slot)));
            }
            for (ArmourSlot each : ArmourSlot.values()) {
                lines.add((worn.weight(each) == weight ? SLOT_COUNTED : SLOT)
                        + words.slotItem(each));
            }
        }
        for (NamedSet set : sets) {
            int count = worn.count(set);
            lines.add(COUNT + "[" + count + "/4] " + NAME + words.setName(set));
            for (SetBonus bonus : set.getBonuses()) {
                lines.add((count >= 4 ? IN_FORCE : NOT_YET) + words.bonus(bonus));
            }
        }
        return lines;
    }

    /** Whether a piece has anything for these lines to tell. */
    public static boolean tells(ArmourCatalogue catalogue, ArmourWeight weight, ArmourSlot slot,
                                String itemId, String relicId) {
        return weight.counts() || !catalogue.setsOf(slot, itemId, relicId).isEmpty();
    }
}
