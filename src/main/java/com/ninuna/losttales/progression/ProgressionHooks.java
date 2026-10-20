package com.ninuna.losttales.progression;

import com.ninuna.losttales.progression.count.CountEvents;
import com.ninuna.losttales.progression.count.SkillCounter;
import com.ninuna.losttales.progression.count.Thing;
import com.ninuna.losttales.progression.skill.SkillAct;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.progression.deed.DeedService;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Where the other systems tell the progression what happened: a quest
 * finished (PROG-01, Lore by the quest's size; PROG-11, its deeds), a map
 * marker or waystone found for the first time.
 */
public final class ProgressionHooks {
    private ProgressionHooks() {}

    /** A quest the player finished, worth its size: every objective it holds. */
    public static void questCompleted(EntityPlayer player, LostTalesQuestDefinition quest) {
        if (!(player instanceof EntityPlayerMP) || quest == null) {
            return;
        }
        int objectives = 0;
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            objectives += stage.getObjectives().size();
        }
        EntityPlayerMP finisher = (EntityPlayerMP) player;
        SkillCounter.act(finisher, SkillAct.QUEST, Thing.named(quest.getId()),
                Math.max(1, objectives) * ProgressionService.storyteller(finisher),
                SkillCounter.key(SkillAct.QUEST, quest.getId()), true);
        DeedService.questFinished(finisher, quest);
    }

    /** A map marker found for the first time; a waystone counts as a waypoint. */
    public static void markerDiscovered(EntityPlayerMP player, String markerId, boolean waystone) {
        CountEvents.discovered(player, waystone ? "waypoint" : "marker", markerId);
    }
}
