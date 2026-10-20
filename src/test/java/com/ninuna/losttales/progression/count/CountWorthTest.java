package com.ninuna.losttales.progression.count;

import com.ninuna.losttales.progression.skill.SkillAct;
import com.ninuna.losttales.progression.skill.SkillCount;
import com.ninuna.losttales.progression.skill.WorthTable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** An act is worth what it took, by the count's numbers and tables (PROG-02). */
public final class CountWorthTest {

    @Test
    public void aHitIsWorthItsDamageAndAKillTheFoesHealth() {
        SkillCount hit = new SkillCount(SkillAct.HIT, 1.0D, Arrays.asList("bow"), null, null);
        SkillCount kill = new SkillCount(SkillAct.KILL, 0.5D, Arrays.asList("bow"), null, null);
        assertEquals(6.0D, CountWorth.of(hit, Thing.named("lotr.Troll"), 6.0D), 0.0D);
        assertEquals("a troll of 80 teaches more than a rabbit of 3", 40.0D,
                CountWorth.of(kill, Thing.named("lotr.Troll"), 80.0D), 0.0D);
        assertEquals(1.5D, CountWorth.of(kill, Thing.named("Rabbit"), 3.0D), 0.0D);
    }

    @Test
    public void aTableTakesTheMostExactKeyFirst() {
        SkillCount mine = new SkillCount(SkillAct.MINE, 0.0D, null, table(
                "lotr:tile.oreGem@3", 12.0D, "lotr:tile.oreGem", 8.0D, "ore:oreIron", 3.0D), null);
        Thing ruby = new Thing(Arrays.asList("lotr:tile.oreGem@3", "lotr:tile.oreGem"), 0, "");
        Thing topaz = new Thing(Arrays.asList("lotr:tile.oreGem@0", "lotr:tile.oreGem"), 0, "");
        Thing iron = new Thing(Arrays.asList("minecraft:iron_ore@0", "minecraft:iron_ore",
                "ore:oreIron"), 0, "");
        assertEquals(12.0D, CountWorth.of(mine, ruby, 1.0D), 0.0D);
        assertEquals(8.0D, CountWorth.of(mine, topaz, 1.0D), 0.0D);
        assertEquals(3.0D, CountWorth.of(mine, iron, 1.0D), 0.0D);
        assertEquals("what the table does not name is worth nothing", 0.0D,
                CountWorth.of(mine, Thing.named("minecraft:dirt"), 1.0D), 0.0D);
    }

    @Test
    public void foodIsWorthEachPointItFillsAndStarTheRest() {
        SkillCount smelt = new SkillCount(SkillAct.SMELT, 0.0D, null,
                table("food", 1.0D, "*", 0.1D), null);
        Thing steak = new Thing(Arrays.asList("minecraft:cooked_beef"), 8, "");
        assertEquals("three steaks of 8", 24.0D, CountWorth.of(smelt, steak, 3.0D), 0.0D);
        assertEquals(0.1D, CountWorth.of(smelt, Thing.named("minecraft:glass"), 1.0D), 1.0E-9D);
    }

    @Test
    public void aCraftIsWorthWhatItUsedSoAStickIsWorthNothing() {
        SkillCount forge = new SkillCount(SkillAct.CRAFT, 0.0D, null, null,
                table("minecraft:iron_ingot", 2.0D));
        List<Thing> sword = Arrays.asList(Thing.named("minecraft:iron_ingot"),
                Thing.named("minecraft:iron_ingot"), Thing.named("minecraft:stick"));
        assertEquals(4.0D, CountWorth.ofCraft(forge, Thing.named("minecraft:iron_sword"), 1,
                sword), 0.0D);
        List<Thing> sticks = Collections.singletonList(Thing.named("minecraft:planks"));
        assertEquals(0.0D, CountWorth.ofCraft(forge, Thing.named("minecraft:stick"), 4,
                sticks), 0.0D);
    }

    private static WorthTable table(Object... entries) {
        Map<String, Double> map = new LinkedHashMap<String, Double>();
        for (int index = 0; index < entries.length; index += 2) {
            map.put((String) entries[index], (Double) entries[index + 1]);
        }
        return new WorthTable(map);
    }
}
