package com.ninuna.losttales.progression;

import com.ninuna.losttales.config.LostTalesConfig;

/** The server's progression settings as the rules take them, read fresh each time. */
public final class ProgressionSettings {
    private ProgressionSettings() {}

    public static SkillCurve curve() {
        return new SkillCurve(LostTalesConfig.skillCurveFirstLevel,
                LostTalesConfig.skillCurveGrowthPercent);
    }

    public static CharacterLevelRule characterRule() {
        return new CharacterLevelRule(LostTalesConfig.characterLevelBase,
                LostTalesConfig.characterLevelStep);
    }

    public static int pointsPerLevel() {
        return LostTalesConfig.perkPointsPerLevel;
    }

    public static RepeatRule repeatRule() {
        return new RepeatRule(LostTalesConfig.repeatsCountedInFull,
                LostTalesConfig.repeatShrinkPercent, LostTalesConfig.repeatFloorPercent,
                LostTalesConfig.repeatRecoverySeconds);
    }
}
