package com.ninuna.losttales.quest;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNamedAfter;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibilityPolicy;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData;
import com.ninuna.losttales.mapmarker.LostTalesWaystonePlacers;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.util.LostTalesDimensionHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Where the rumours a character heard point (QUEST-20). A rumour points to
 * the marker its file names, as its log keeps it. A rumour that names none
 * points to the first place its quest names, while
 * {@code rumoursPointToQuestPlaces} is on: a marker of its {@code markers}
 * other than its giver's, else an objective's {@code marker}, else the
 * place its {@code interaction} block stands. Each marker is looked up
 * among the world's markers as they stand now; a marker gone, or one the
 * player may not see, points nowhere, and the next place the quest names
 * is tried. Nothing is discovered here.
 */
final class QuestRumourPlaces {
    private QuestRumourPlaces() {}

    /** The places the player's rumours point to, in the order they were heard. */
    static List<QuestRumourPlace> of(EntityPlayerMP player,
                                     LostTalesQuestPlayerData data) {
        List<QuestRumourPlace> places = new ArrayList<QuestRumourPlace>();
        if (player == null || data == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return places;
        }
        Map<String, String> markers = data.getRumourMarkers();
        if (markers.isEmpty()) {
            return places;
        }
        Markers world = new Markers(player);
        int here = player.worldObj.provider.dimensionId;
        for (Map.Entry<String, String> rumour : markers.entrySet()) {
            String questId = rumour.getKey();
            QuestRumourPlace place = null;
            if (rumour.getValue().length() > 0) {
                place = world.place(questId, rumour.getValue());
            } else if (LostTalesConfig.rumoursPointToQuestPlaces) {
                LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(questId);
                for (String markerId : questMarkers(quest)) {
                    place = world.place(questId, markerId);
                    if (place != null) {
                        break;
                    }
                }
                if (place == null) {
                    place = blockPlace(quest, here);
                }
            }
            if (place != null && place.isValid()) {
                places.add(place);
            }
        }
        return places;
    }

    /**
     * The markers a rumour with none of its own may point to, in the order
     * its quest names them: each of its {@code markers} but its giver's,
     * then each objective's {@code marker}, stage by stage. A marker its
     * giver's names is never one of them.
     */
    static List<String> questMarkers(LostTalesQuestDefinition quest) {
        List<String> ids = new ArrayList<String>();
        if (quest == null) {
            return ids;
        }
        for (Map.Entry<String, String> entry : quest.getMarkers().entrySet()) {
            if (!LostTalesQuestMarkerHelper.isQuestGiverMarkerKey(entry.getKey())) {
                LostTalesQuestMarkerHelper.addMarkerIds(ids, entry.getValue());
            }
        }
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                LostTalesQuestMarkerHelper.addMarkerIds(ids,
                        LostTalesQuestParams.value(objective.getParams(), "marker"));
            }
        }
        ids.removeAll(LostTalesQuestMarkerHelper.collectQuestGiverMarkerIds(quest));
        return ids;
    }

    /**
     * Where the block that gives the quest stands, as its {@code interaction}
     * writes it: its {@code x} and {@code z}, in the dimension it names, else
     * {@code here}, where bare coordinates count. Null for a quest no block
     * gives, or one whose block has no place written.
     */
    static QuestRumourPlace blockPlace(LostTalesQuestDefinition quest, int here) {
        if (quest == null) {
            return null;
        }
        Map<String, String> interaction = quest.getInteraction();
        if (LostTalesQuestParams.value(interaction, "block").length() == 0) {
            return null;
        }
        Double x = LostTalesQuestParams.parseNumber(
                LostTalesQuestParams.value(interaction, "x"));
        Double z = LostTalesQuestParams.parseNumber(
                LostTalesQuestParams.value(interaction, "z"));
        if (x == null || z == null) {
            return null;
        }
        int dimension = LostTalesDimensionHelper.parseDimensionId(
                LostTalesQuestParams.value(interaction, "dimension"), here);
        // The block's middle, as a touch on it is measured.
        return new QuestRumourPlace(quest.getId(), "", "", "", dimension,
                Math.floor(x.doubleValue()) + 0.5D, Math.floor(z.doubleValue()) + 0.5D);
    }

    /** The world's markers as one player may see them, looked up only when a rumour asks. */
    private static final class Markers {
        private final EntityPlayerMP player;
        private LostTalesMapMarkerWorldData world;
        private LostTalesMapMarkerVisibilityPolicy.Viewer viewer;
        private LostTalesWaystonePlacers placers;

        Markers(EntityPlayerMP player) {
            this.player = player;
        }

        /** The place the marker stands, for the quest's rumour; null for one gone or out of sight. */
        QuestRumourPlace place(String questId, String markerId) {
            if (this.world == null) {
                this.world = LostTalesMapMarkerStorage.get(this.player.worldObj);
                this.viewer = LostTalesMapMarkerVisibilityPolicy.Viewer.of(this.player);
                this.placers = LostTalesWaystonePlacers.of(this.player.worldObj);
            }
            LostTalesMapMarkerRecord record = this.world.getRecord(markerId);
            if (record == null
                    || !LostTalesMapMarkerVisibilityPolicy.canView(record, this.viewer)) {
                return null;
            }
            QuestRumourPlace place = new QuestRumourPlace(questId, record.getId(),
                    record.getName(), LostTalesMapMarkerNamedAfter.shown(
                            record.getNamedAfter(), this.placers),
                    record.getDimensionId(), record.getX(), record.getZ());
            return place.isValid() && place.isMarker() ? place : null;
        }
    }
}
