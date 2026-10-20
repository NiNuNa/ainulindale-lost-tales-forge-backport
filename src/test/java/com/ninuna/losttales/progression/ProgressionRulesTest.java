package com.ninuna.losttales.progression;

import com.ninuna.losttales.progression.skill.PerkDefinition;
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
import static org.junit.Assert.assertTrue;

/** The curve, the character level and the perks' needs (PROG-03, 04, 05, 06). */
public final class ProgressionRulesTest {
    private static final SkillCurve CURVE = new SkillCurve(100.0D, 5.0D);
    private static final CharacterLevelRule RULE = new CharacterLevelRule(75, 25);

    @Test
    public void levelTwoNeedsTheFirstNumberAndEachLevelAfterFivePercentMore() {
        assertEquals(100.0D, CURVE.needed(1), 1.0E-9D);
        assertEquals(105.0D, CURVE.needed(2), 1.0E-9D);
        assertEquals(100.0D * Math.pow(1.05D, 98), CURVE.needed(99), 1.0E-6D);
        assertEquals("the top level needs nothing more", 0.0D, CURVE.needed(100), 0.0D);
    }

    @Test
    public void reachingOneHundredTakesAQuarterOfAMillion() {
        double total = CURVE.total(100);
        assertTrue("about 248,000 for the whole road: " + total,
                total > 240000.0D && total < 256000.0D);
        assertTrue("half way in level is a small share of the road",
                CURVE.total(50) < total / 10.0D);
    }

    @Test
    public void experiencePastSeveralLevelsCarriesOverAndStopsAtTheTop() {
        SkillCurve.Standing standing = CURVE.add(1, 0.0D, 210.0D);
        assertEquals(3, standing.level);
        assertEquals(5.0D, standing.into, 1.0E-9D);
        SkillCurve.Standing top = CURVE.add(99, 0.0D, 1.0E9D);
        assertEquals(100, top.level);
        assertEquals(0.0D, top.into, 0.0D);
    }

    @Test
    public void eachSkillLevelReachedAddsItsOwnNumberToTheCharacter() {
        ProgressionState state = new ProgressionState();
        ProgressionRules.Gain gain = ProgressionRules.gain(state, "losttales:archery",
                210.0D, CURVE, RULE);
        assertEquals(Arrays.asList(Integer.valueOf(2), Integer.valueOf(3)), gain.levelsReached);
        assertEquals("2 + 3 toward character level 2", 5L, state.getCharacterInto());
        assertEquals(1, state.getCharacterLevel());
    }

    @Test
    public void theCharacterLevelNeedsALittleMoreEachTimeAndHasNoCap() {
        assertEquals(100L, RULE.needed(1));
        assertEquals(125L, RULE.needed(2));
        CharacterLevelRule.Standing standing = RULE.add(1, 0L, 100L + 125L + 10L);
        assertEquals(3, standing.level);
        assertEquals(10L, standing.into);
        CharacterLevelRule.Standing far = RULE.add(1, 0L, Long.MAX_VALUE / 4L);
        assertTrue("no cap short of the bound", far.level > 100);
    }

    @Test
    public void aRacesStartingSkillsGiveNoCharacterLevel() {
        Map<String, Integer> start = new LinkedHashMap<String, Integer>();
        start.put("losttales:archery", Integer.valueOf(10));
        start.put("losttales:lore", Integer.valueOf(5));
        ProgressionState state = ProgressionRules.startingState(start);
        assertEquals(10, state.skill("losttales:archery").getLevel());
        assertEquals(5, state.skill("losttales:lore").getLevel());
        assertEquals(1, state.getCharacterLevel());
        assertEquals(0L, state.getCharacterInto());
    }

    @Test
    public void eachCharacterLevelGivesAPointAndEachRankCostsOne() {
        ProgressionState state = new ProgressionState();
        state.setCharacter(4, 0L);
        Map<String, SkillDefinition> skills = skills();
        assertEquals(3, ProgressionRules.availablePoints(state, 1, skills));
        state.setPerkRank("losttales:archery", "steady_draw", 2);
        assertEquals(1, ProgressionRules.availablePoints(state, 1, skills));
    }

    @Test
    public void sageGivesAPointOfItsOwn() {
        ProgressionState state = new ProgressionState();
        state.setCharacter(2, 0L);
        state.setPerkRank("losttales:archery", "sage", 1);
        assertEquals("one level's point spent on Sage comes back",
                1, ProgressionRules.availablePoints(state, 1, skills()));
    }

    @Test
    public void aPerkNeedsItsLevelThePerkBeforeAndAPoint() {
        SkillDefinition archery = skills().get("losttales:archery");
        PerkDefinition steady = archery.perk("steady_draw");
        PerkDefinition quick = archery.perk("quick_nock");
        ProgressionState state = new ProgressionState();
        assertEquals(ProgressionRules.Refusal.SKILL_TOO_LOW,
                ProgressionRules.canTake(state, archery, steady, 5));
        state.setSkill("losttales:archery", SkillStanding.start(60));
        assertEquals(ProgressionRules.Refusal.NONE,
                ProgressionRules.canTake(state, archery, steady, 5));
        assertEquals(ProgressionRules.Refusal.NEEDS_PERK,
                ProgressionRules.canTake(state, archery, quick, 5));
        state.setPerkRank("losttales:archery", "steady_draw", 2);
        assertEquals(ProgressionRules.Refusal.NONE,
                ProgressionRules.canTake(state, archery, quick, 5));
        assertEquals(ProgressionRules.Refusal.NO_POINTS,
                ProgressionRules.canTake(state, archery, quick, 0));
        state.setPerkRank("losttales:archery", "steady_draw", 3);
        assertEquals(ProgressionRules.Refusal.ALL_TAKEN,
                ProgressionRules.canTake(state, archery, steady, 5));
    }

    @Test
    public void aResetGivesBackEveryRankOfOneTree() {
        ProgressionState state = new ProgressionState();
        state.setPerkRank("losttales:archery", "steady_draw", 2);
        state.setPerkRank("losttales:archery", "eagle_eye", 1);
        state.setPerkRank("losttales:blades", "edge", 1);
        assertEquals(3, state.clearPerks("losttales:archery"));
        assertEquals(1, state.spentPoints());
        assertEquals(1, state.perkRank("losttales:blades", "edge"));
    }

    private static Map<String, SkillDefinition> skills() {
        List<PerkDefinition> perks = new ArrayList<PerkDefinition>();
        perks.add(perk("steady_draw", new int[] { 10, 30, 50 }, "", 0, "draw_speed", 0.1D));
        perks.add(perk("quick_nock", new int[] { 60 }, "steady_draw", 2, "quick_nock", 0.5D));
        perks.add(perk("sage", new int[] { 1 }, "", 0, "perk_point", 1.0D));
        Map<String, SkillDefinition> skills = new LinkedHashMap<String, SkillDefinition>();
        skills.put("losttales:archery", new SkillDefinition("losttales:archery", "", 1, "",
                "", Collections.<SkillCount>emptyList(), perks));
        return skills;
    }

    private static PerkDefinition perk(String id, int[] levels, String needs, int needsRank,
                                       String effect, double amount) {
        return new PerkDefinition(id, levels, needs, needsRank, effect, amount,
                null, null, true, "", "");
    }
}
