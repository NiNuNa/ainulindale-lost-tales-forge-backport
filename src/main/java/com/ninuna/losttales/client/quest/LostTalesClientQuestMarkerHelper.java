package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveSelection;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveType;
import com.ninuna.losttales.quest.LostTalesQuestParams;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
/**
 * Client-side helper for deciding which quest/map markers should be emphasized.
 *
 * The modern NeoForge branch marks map markers as active quest markers before
 * rendering. In 1.7.10 we derive that state from the server-synced tracked quest
 * and the bundled client quest definitions.
 */
@SideOnly(Side.CLIENT)
public final class LostTalesClientQuestMarkerHelper {
    private LostTalesClientQuestMarkerHelper() {}

    /** Returns canonical marker identity -> label for tracked quests. */
    public static Map<String, String> collectActiveQuestMarkerLabels() {
        Map<String, String> labels = new LinkedHashMap<String, String>();
        for (LostTalesQuestProgress progress : LostTalesClientQuestProgressStore.getPinnedQuests()) {
            LostTalesQuestDefinition quest = LostTalesClientQuestDefinitionStore.getQuest(progress.getQuestId());
            if (quest == null) {
                continue;
            }

            String label = createQuestLabel(quest);
            addQuestDefinitionMarkers(labels, quest, label);
            addProgressibleObjectiveMarkers(labels, quest, progress, label);
        }
        return labels;
    }

    /** Returns temporary coordinate markers produced by current-stage goto objectives for tracked quests. */
    public static Set<ActiveCoordinateMarker> collectActiveCoordinateMarkers() {
        Set<ActiveCoordinateMarker> markers = new LinkedHashSet<ActiveCoordinateMarker>();
        for (LostTalesQuestProgress progress : LostTalesClientQuestProgressStore.getPinnedQuests()) {
            LostTalesQuestDefinition quest = LostTalesClientQuestDefinitionStore.getQuest(progress.getQuestId());
            if (quest == null) {
                continue;
            }

            String label = createQuestLabel(quest);
            for (LostTalesQuestObjectiveDefinition objective
                    : LostTalesQuestObjectiveSelection
                    .getProgressibleObjectives(quest, progress)) {
                if (objective == null || !isGotoObjective(objective)
                        || LostTalesQuestObjectiveSelection
                        .isComplete(progress, objective)) {
                    continue;
                }
                ActiveCoordinateMarker marker = coordinateMarkerFromObjective(quest, objective, label);
                if (marker != null) {
                    markers.add(marker);
                }
            }
        }
        for (ClientQuestEntry quest
                : ClientQuestCatalog.getEntries(Minecraft.getMinecraft())) {
            if (quest.getSource() != ClientQuestEntry.Source.LOTR
                    || !quest.isActive() || !quest.isTracked()) {
                continue;
            }
            int index = 0;
            for (ClientQuestEntry.Target target : quest.getTargets()) {
                markers.add(new ActiveCoordinateMarker(
                        quest.getReference() + ":" + index,
                        quest.getTitle(), target.getDimensionId(),
                        target.getX(), target.getY(), target.getZ()));
                index++;
            }
        }
        return markers;
    }

    public static String getActiveQuestMarkerLabel(
            Map<String, String> labels, String markerId) {
        String key = LostTalesQuestMarkerHelper.markerCanonicalKey(markerId);
        return labels == null || key.length() == 0
                ? null : labels.get(key);
    }

    private static void addQuestDefinitionMarkers(Map<String, String> labels, LostTalesQuestDefinition quest, String label) {
        if (quest == null) {
            return;
        }
        for (String markerId : LostTalesQuestMarkerHelper.collectStaticQuestMarkerIds(quest)) {
            putMarkerLabel(labels, markerId, label);
        }
        for (String markerId : LostTalesQuestMarkerHelper.collectDynamicQuestGiverMarkerIds(quest)) {
            putMarkerLabel(labels, markerId, label);
        }
    }

    private static void addProgressibleObjectiveMarkers(
            Map<String, String> labels, LostTalesQuestDefinition quest,
            LostTalesQuestProgress progress, String label) {
        for (LostTalesQuestObjectiveDefinition objective
                : LostTalesQuestObjectiveSelection
                .getProgressibleObjectives(quest, progress)) {
            if (objective == null || LostTalesQuestObjectiveSelection
                    .isComplete(progress, objective)) {
                continue;
            }
            String markerId = LostTalesQuestParams.value(objective.getParams(), "marker");
            if (markerId != null && markerId.length() > 0) {
                String[] split = markerId.split(",");
                for (String part : split) {
                    putMarkerLabel(labels, LostTalesQuestMarkerHelper.normalizeMarkerId(part), label);
                }
            }
        }
    }

    private static void putMarkerLabel(Map<String, String> labels, String markerId, String label) {
        String key = LostTalesQuestMarkerHelper.markerCanonicalKey(markerId);
        if (key.length() > 0 && !labels.containsKey(key)) {
            labels.put(key, label);
        }
    }

    private static ActiveCoordinateMarker coordinateMarkerFromObjective(LostTalesQuestDefinition quest, LostTalesQuestObjectiveDefinition objective, String label) {
        LostTalesQuestParams.Location location =
                LostTalesQuestParams.location(objective.getParams(),
                        currentDimension());
        if (location == null) {
            return null;
        }
        String id = "objective:" + (quest == null ? "unknown" : quest.getId()) + ":" + objective.getId();
        return new ActiveCoordinateMarker(id, label, location.getDimensionId(),
                location.getX(), location.getY(), location.getZ());
    }

    private static boolean isGotoObjective(LostTalesQuestObjectiveDefinition objective) {
        return LostTalesQuestObjectiveType.GOTO.is(objective);
    }

    /** The dimension this player stands in; the overworld before a world is joined. */
    private static int currentDimension() {
        net.minecraft.client.Minecraft minecraft =
                net.minecraft.client.Minecraft.getMinecraft();
        return minecraft == null || minecraft.thePlayer == null
                ? 0 : minecraft.thePlayer.dimension;
    }

    private static String createQuestLabel(LostTalesQuestDefinition quest) {
        if (quest == null) {
            return "Quest Objective";
        }
        String title = quest.getTitle();
        return title == null || title.length() == 0 ? quest.getId() : title;
    }

    public static final class ActiveCoordinateMarker {
        private final String id;
        private final String label;
        private final int dimensionId;
        private final double x;
        private final double y;
        private final double z;

        private ActiveCoordinateMarker(String id, String label, int dimensionId, double x, double y, double z) {
            this.id = id;
            this.label = label;
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String getLabel() {
            return label;
        }

        public int getDimensionId() {
            return dimensionId;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof ActiveCoordinateMarker)) return false;
            ActiveCoordinateMarker other = (ActiveCoordinateMarker) object;
            return id != null && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id == null ? 0 : id.hashCode();
        }
    }
}
