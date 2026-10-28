package com.ninuna.losttales.progression.count;

import com.ninuna.losttales.armour.ArmourWeight;
import com.ninuna.losttales.compat.lotr.LotrWeaponKinds;
import com.ninuna.losttales.progression.GrantSource;
import com.ninuna.losttales.progression.ProgressionCatalog;
import com.ninuna.losttales.progression.ProgressionService;
import com.ninuna.losttales.progression.skill.SkillAct;
import com.ninuna.losttales.progression.skill.SkillCount;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import com.ninuna.losttales.progression.skill.WeaponKind;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/**
 * Hands an act to every skill whose file counts it (PROG-01), at what
 * that file makes it worth (PROG-02). Each skill grows apart; nothing is
 * shared (PROG-09). A finished quest and a first find go to the skill as
 * what they are, which the hourly cap may let past (PROG-08).
 */
public final class SkillCounter {
    private SkillCounter() {}

    /** The name repeats of an act on a thing are counted under (PROG-08). */
    public static String key(SkillAct act, String thing) {
        return thing == null || thing.length() == 0 ? "" : act.word() + "|" + thing;
    }

    /**
     * An act with no weapon: {@code measure} is what a {@code per} count
     * multiplies, or how many things a table count is worth each. Repeats
     * are counted under {@code repeatKey} ({@link #key}), none when empty.
     */
    public static void act(EntityPlayerMP player, SkillAct act, Thing thing, double measure,
                           String repeatKey, boolean repeats) {
        weaponAct(player, act, thing, measure, repeatKey, repeats, null, null);
    }

    /** A hit or a kill, counted by the skills whose weapons name the one it was done with. */
    public static void weaponAct(EntityPlayerMP player, SkillAct act, Thing thing,
                                 double measure, String repeatKey, boolean repeats,
                                 WeaponKind kind, ItemStack weapon) {
        if (player == null || !(measure > 0.0D)) {
            return;
        }
        String key = repeatKey == null ? "" : repeatKey;
        for (SkillDefinition skill : ProgressionCatalog.skills()) {
            for (SkillCount count : skill.getCounts()) {
                if (count.getAct() != act || act.takesWeapons()
                        && !LotrWeaponKinds.names(count.getWeapons(), kind, weapon)) {
                    continue;
                }
                double worth = CountWorth.of(count, thing, measure);
                if (worth > 0.0D) {
                    ProgressionService.count(player, skill.getId(), key, repeats, worth,
                            GrantSource.of(act));
                }
            }
        }
    }

    /**
     * What armour kept from a foe's blow, by weight: each skill with a
     * weight counts its weight's share (PROG-17), repeats under
     * {@code repeatKey} shrinking it as a hit's do.
     */
    public static void armourAct(EntityPlayerMP player, Thing thing,
                                 Map<ArmourWeight, Double> kept, String repeatKey) {
        if (player == null || kept == null || kept.isEmpty()) {
            return;
        }
        String key = repeatKey == null ? "" : repeatKey;
        for (SkillDefinition skill : ProgressionCatalog.skills()) {
            Double share = skill.getWeight() == null ? null : kept.get(skill.getWeight());
            if (share == null || !(share.doubleValue() > 0.0D)) {
                continue;
            }
            for (SkillCount count : skill.getCounts()) {
                if (count.getAct() != SkillAct.ARMOUR) {
                    continue;
                }
                double worth = CountWorth.of(count, thing, share.doubleValue());
                if (worth > 0.0D) {
                    ProgressionService.count(player, skill.getId(), key, true, worth);
                }
            }
        }
    }

    /** A craft: what it made, {@code madeCount} of them, and the things it used up. */
    public static void craft(EntityPlayerMP player, Thing made, int madeCount, List<Thing> used) {
        if (player == null) {
            return;
        }
        String key = key(SkillAct.CRAFT, made.getRepeatKey());
        for (SkillDefinition skill : ProgressionCatalog.skills()) {
            for (SkillCount count : skill.getCounts()) {
                if (count.getAct() != SkillAct.CRAFT) {
                    continue;
                }
                double worth = CountWorth.ofCraft(count, made, madeCount, used);
                if (worth > 0.0D) {
                    ProgressionService.count(player, skill.getId(), key, true, worth);
                }
            }
        }
    }

    /** Whether any skill counts this act, so an event with nothing to give is passed by. */
    public static boolean anyCounts(SkillAct act) {
        for (SkillDefinition skill : ProgressionCatalog.skills()) {
            for (SkillCount count : skill.getCounts()) {
                if (count.getAct() == act) {
                    return true;
                }
            }
        }
        return false;
    }
}
