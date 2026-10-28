package com.ninuna.losttales.gui.hud.quest;

import com.ninuna.losttales.config.LostTalesConfig;

/**
 * What the quests show on the HUD and in the world, each by its own
 * switch (QUEST-46): the tracker's switch the tracker, the banners'
 * switch the banners, the world labels' switch the world labels. The
 * HUD's own switch stands over all three.
 */
public final class QuestHudSwitches {
    /** Whether the tracker shows. */
    public final boolean tracker;
    /** Whether the banners show. */
    public final boolean banners;
    /** Whether the labels over tracked places show in the world. */
    public final boolean worldLabels;

    private QuestHudSwitches(boolean tracker, boolean banners,
                             boolean worldLabels) {
        this.tracker = tracker;
        this.banners = banners;
        this.worldLabels = worldLabels;
    }

    /** What shows for the HUD's switch and the three quest switches. */
    static QuestHudSwitches of(boolean hud, boolean tracker, boolean banners,
                               boolean worldLabels) {
        return new QuestHudSwitches(hud && tracker, hud && banners,
                hud && worldLabels);
    }

    /** What shows by the client's settings now. */
    public static QuestHudSwitches current() {
        return of(LostTalesConfig.showLostTalesHud,
                LostTalesConfig.showQuestTracker,
                LostTalesConfig.showQuestBanners,
                LostTalesConfig.showWorldLabels);
    }
}
