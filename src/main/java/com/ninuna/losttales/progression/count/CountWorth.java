package com.ninuna.losttales.progression.count;

import com.ninuna.losttales.progression.skill.SkillAct;
import com.ninuna.losttales.progression.skill.SkillCount;
import com.ninuna.losttales.progression.skill.WorthTable;
import java.util.List;

/**
 * What one count of a skill file gives an act (PROG-02): {@code per} times
 * what the act measures (damage, blocks ridden, levels a repair cost), or
 * what its table makes the thing worth, food by each point it fills, for
 * each of the things.
 */
public final class CountWorth {
    private CountWorth() {}

    /** The worth of an act measured in {@code measure} (damage, blocks, a count of things). */
    public static double of(SkillCount count, Thing thing, double measure) {
        if (count == null || !(measure > 0.0D)) {
            return 0.0D;
        }
        if (count.getAct().getMeasure() == SkillAct.Measure.PER) {
            return Math.max(0.0D, count.getPer() * measure);
        }
        return Math.max(0.0D, tableWorth(count.getWorth(), thing) * measure);
    }

    /** A craft's worth: what it made, plus what each thing it used up is worth. */
    public static double ofCraft(SkillCount count, Thing made, int madeCount,
                                 List<Thing> used) {
        if (count == null) {
            return 0.0D;
        }
        double worth = tableWorth(count.getWorth(), made) * Math.max(0, madeCount);
        if (used != null && !count.getIngredients().isEmpty()) {
            for (Thing ingredient : used) {
                worth += tableWorth(count.getIngredients(), ingredient);
            }
        }
        return Math.max(0.0D, worth);
    }

    /**
     * What a table makes one thing worth: its own entry by the most exact
     * key, else what food is worth by each point it fills, else {@code *}.
     */
    public static double tableWorth(WorthTable table, Thing thing) {
        if (table == null || table.isEmpty() || thing == null) {
            return 0.0D;
        }
        for (String key : thing.getKeys()) {
            Double worth = table.entries().get(key);
            if (worth != null) {
                return worth.doubleValue();
            }
        }
        Double perFood = table.perFood();
        if (perFood != null && thing.getFoodPoints() > 0) {
            return perFood.doubleValue() * thing.getFoodPoints();
        }
        Double any = table.entries().get(WorthTable.ANY);
        return any == null ? 0.0D : any.doubleValue();
    }
}
