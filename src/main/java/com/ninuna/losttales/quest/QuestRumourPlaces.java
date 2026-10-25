package com.ninuna.losttales.quest;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNamedAfter;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibilityPolicy;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData;
import com.ninuna.losttales.mapmarker.LostTalesWaystonePlacers;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Where the rumours a character heard point (QUEST-20): each rumour's
 * marker, as its log keeps it, looked up among the world's markers as they
 * stand now, for the player's game to name and pin. A marker gone, or one
 * the player may not see, points nowhere. Nothing is discovered here.
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
        if (!pointsAnywhere(markers)) {
            return places;
        }
        LostTalesMapMarkerWorldData world =
                LostTalesMapMarkerStorage.get(player.worldObj);
        LostTalesMapMarkerVisibilityPolicy.Viewer viewer =
                LostTalesMapMarkerVisibilityPolicy.Viewer.of(player);
        LostTalesWaystonePlacers placers =
                LostTalesWaystonePlacers.of(player.worldObj);
        for (Map.Entry<String, String> rumour : markers.entrySet()) {
            if (rumour.getValue().length() == 0) {
                continue;
            }
            LostTalesMapMarkerRecord record = world.getRecord(rumour.getValue());
            if (record == null
                    || !LostTalesMapMarkerVisibilityPolicy.canView(record, viewer)) {
                continue;
            }
            QuestRumourPlace place = new QuestRumourPlace(rumour.getKey(),
                    record.getId(), record.getName(),
                    LostTalesMapMarkerNamedAfter.shown(
                            record.getNamedAfter(), placers),
                    record.getDimensionId(), record.getX(), record.getZ());
            if (place.isValid()) {
                places.add(place);
            }
        }
        return places;
    }

    private static boolean pointsAnywhere(Map<String, String> markers) {
        for (String marker : markers.values()) {
            if (marker.length() > 0) {
                return true;
            }
        }
        return false;
    }
}
