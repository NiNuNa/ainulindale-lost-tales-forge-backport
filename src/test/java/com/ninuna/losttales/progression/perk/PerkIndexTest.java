package com.ninuna.losttales.progression.perk;

import com.ninuna.losttales.progression.ProgressionState;
import com.ninuna.losttales.progression.skill.PerkDefinition;
import com.ninuna.losttales.progression.skill.PerkEffect;
import com.ninuna.losttales.progression.skill.SkillAct;
import com.ninuna.losttales.progression.skill.SkillCount;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A character's taken perks, by effect, and the numbers they add up to (PROG-06). */
public final class PerkIndexTest {

    @Test
    public void ranksMultiplyTheAmountAndOnlyTheSkillsWeaponsCount() {
        ProgressionState state = new ProgressionState();
        state.setPerkRank("losttales:blades", "edge", 2);
        PerkIndex index = PerkIndex.of(state, skills(true));
        assertEquals(0.1D, index.total(PerkEffect.WEAPON_DAMAGE, named("sword")), 1.0E-9D);
        assertEquals("an axe is no blade", 0.0D,
                index.total(PerkEffect.WEAPON_DAMAGE, named("axe")), 0.0D);
    }

    @Test
    public void aPerksOwnListOverridesTheSkillsWeapons() {
        ProgressionState state = new ProgressionState();
        state.setPerkRank("losttales:blades", "daggerwork", 1);
        PerkIndex index = PerkIndex.of(state, skills(true));
        assertEquals(0.2D, index.total(PerkEffect.DAGGER_WORK, named("dagger")), 1.0E-9D);
        assertEquals(0.0D, index.total(PerkEffect.DAGGER_WORK, named("sword")), 0.0D);
    }

    @Test
    public void aSwitchedOffOrUnknownPerkDoesNothing() {
        ProgressionState state = new ProgressionState();
        state.setPerkRank("losttales:blades", "edge", 3);
        state.setPerkRank("losttales:blades", "gone", 1);
        state.setPerkRank("losttales:sailing", "oars", 1);
        PerkIndex index = PerkIndex.of(state, skills(false));
        assertFalse(index.has(PerkEffect.WEAPON_DAMAGE));
        assertTrue(index.taken(PerkEffect.DAGGER_WORK).isEmpty());
    }

    @Test
    public void aRankPastTheTreeCountsAsTheTopRank() {
        ProgressionState state = new ProgressionState();
        state.setPerkRank("losttales:blades", "edge", 7);
        assertEquals(0.15D, PerkIndex.of(state, skills(true))
                .total(PerkEffect.WEAPON_DAMAGE, named("sword")), 1.0E-9D);
    }

    @Test
    public void sharesLowerRaiseAndShortenSensibly() {
        assertEquals(9, PerkShares.lower(10, 0.1D));
        assertEquals("a price never falls to nothing", 1, PerkShares.lower(1, 0.5D));
        assertEquals(11, PerkShares.raise(10, 0.1D));
        assertEquals(8, PerkShares.shorter(10, 0.2D));
        assertEquals(1, PerkShares.shorter(2, 0.9D));
        assertEquals(10.5F, PerkShares.gain(10.0F, 0.05D), 1.0E-5F);
        assertEquals("a loss stays a loss", -4.0F, PerkShares.gain(-4.0F, 0.05D), 0.0F);
    }

    @Test
    public void sunderLetsItsShareOfTheArmourThrough() {
        double withoutPerk = PerkCombatEvents.passArmour(10.0D, 20, 0.0D);
        assertEquals(10.0D, withoutPerk, 1.0E-9D);
        double half = PerkCombatEvents.passArmour(10.0D, 20, 0.5D);
        // Full armour keeps 80%: 2 of 10 pass; passing half of what it keeps lets 6 through.
        assertEquals(6.0D, half * (1.0D - 0.8D), 1.0E-9D);
        assertEquals("no armour, nothing to pass", 10.0D,
                PerkCombatEvents.passArmour(10.0D, 0, 0.5D), 0.0D);
    }

    private static PerkIndex.WeaponMatch named(final String kind) {
        return new PerkIndex.WeaponMatch() {
            @Override
            public boolean matches(List<String> names) {
                return names.contains(kind);
            }
        };
    }

    private static Map<String, SkillDefinition> skills(boolean enabled) {
        List<PerkDefinition> perks = new ArrayList<PerkDefinition>();
        perks.add(new PerkDefinition("edge", new int[] { 10, 30, 50 }, "", 0, "weapon_damage",
                0.05D, null, null, enabled, "", ""));
        perks.add(new PerkDefinition("daggerwork", new int[] { 40 }, "", 0, "dagger_work",
                0.2D, null, Collections.singletonList("dagger"), enabled, "", ""));
        List<SkillCount> counts = Collections.singletonList(new SkillCount(SkillAct.HIT, 1.0D,
                Arrays.asList("sword", "dagger"), null, null));
        Map<String, SkillDefinition> skills = new LinkedHashMap<String, SkillDefinition>();
        skills.put("losttales:blades", new SkillDefinition("losttales:blades", "", 2, "", "",
                counts, perks));
        return skills;
    }
}
