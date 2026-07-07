package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    /** Returns marker id -> display label for every marker referenced by any tracked quest. */
    public static Map<String, String> collectActiveQuestMarkerLabels() {
        Map<String, String> labels = new LinkedHashMap<String, String>();
        for (LostTalesQuestProgress progress : LostTalesClientQuestProgressStore.getPinnedQuests()) {
            LostTalesQuestDefinition quest = LostTalesClientQuestDefinitionStore.getQuest(progress.getQuestId());
            if (quest == null) {
                continue;
            }

            String label = createQuestLabel(quest);
            addQuestDefinitionMarkers(labels, quest, label);
            addCurrentStageObjectiveMarkers(labels, quest, progress, label);
        }
        return labels;
    }

    /** Returns temporary coordinate markers produced by current-stage goto objectives for tracked quests. */
    public static Set<ActiveCoordinateMarker> collectActiveCoordinateMarkers() {
        Set<ActiveCoordinateMarker> markers = new LinkedHashSet<ActiveCoordinateMarker>();
        for (LostTalesQuestProgress progress : LostTalesClientQuestProgressStore.getPinnedQuests()) {
            LostTalesQuestDefinition quest = LostTalesClientQuestDefinitionStore.getQuest(progress.getQuestId());
            LostTalesQuestStageDefinition stage = getCurrentStage(quest, progress);
            if (quest == null || stage == null) {
                continue;
            }

            String label = createQuestLabel(quest);
            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                if (objective == null || !isGotoObjective(objective)) {
                    continue;
                }
                ActiveCoordinateMarker marker = coordinateMarkerFromObjective(quest, objective, label);
                if (marker != null) {
                    markers.add(marker);
                }
            }
        }
        return markers;
    }

    public static boolean isActiveQuestMarker(String markerId) {
        return markerId != null && collectActiveQuestMarkerLabels().containsKey(markerId);
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

    private static void addCurrentStageObjectiveMarkers(Map<String, String> labels, LostTalesQuestDefinition quest, LostTalesQuestProgress progress, String label) {
        LostTalesQuestStageDefinition stage = getCurrentStage(quest, progress);
        if (stage == null) {
            return;
        }
        for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
            if (objective == null) {
                continue;
            }
            String markerId = firstParam(objective, "marker", "mapMarker", "map_marker", "targetMarker", "target_marker");
            if (markerId != null && markerId.length() > 0) {
                String[] split = markerId.split(",");
                for (String part : split) {
                    putMarkerLabel(labels, LostTalesQuestMarkerHelper.normalizeMarkerId(part), label);
                }
            }
        }
    }

    private static void putMarkerLabel(Map<String, String> labels, String markerId, String label) {
        markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        if (markerId.length() > 0 && !labels.containsKey(markerId)) {
            labels.put(markerId, label);
        }
    }

    private static LostTalesQuestStageDefinition getCurrentStage(LostTalesQuestDefinition quest, LostTalesQuestProgress progress) {
        if (quest == null || progress == null || quest.getStages().isEmpty()) {
            return null;
        }
        String stageId = progress.getStageId();
        if (stageId != null && stageId.length() > 0) {
            for (LostTalesQuestStageDefinition stage : quest.getStages()) {
                if (stageId.equals(stage.getId())) {
                    return stage;
                }
            }
        }
        int index = progress.getStageIndex();
        if (index >= 0 && index < quest.getStages().size()) {
            return quest.getStages().get(index);
        }
        return quest.getFirstStage();
    }

    private static ActiveCoordinateMarker coordinateMarkerFromObjective(LostTalesQuestDefinition quest, LostTalesQuestObjectiveDefinition objective, String label) {
        String xValue = firstParam(objective, "x", "posX", "targetX");
        String yValue = firstParam(objective, "y", "posY", "targetY");
        String zValue = firstParam(objective, "z", "posZ", "targetZ");
        if (xValue == null || zValue == null) {
            return null;
        }

        Double x = parseDouble(xValue);
        Double z = parseDouble(zValue);
        Double y = parseDouble(yValue == null || yValue.length() == 0 ? "64" : yValue);
        if (x == null || y == null || z == null) {
            return null;
        }

        int dimensionId = parseDimensionId(firstParam(objective, "dimension", "dim", "world"));
        String id = "objective:" + (quest == null ? "unknown" : quest.getId()) + ":" + objective.getId();
        return new ActiveCoordinateMarker(id, label, dimensionId, x.doubleValue(), y.doubleValue(), z.doubleValue());
    }

    private static boolean isGotoObjective(LostTalesQuestObjectiveDefinition objective) {
        String type = objective.getType();
        if (type == null) {
            return false;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return "goto".equals(normalized) || "go_to".equals(normalized) || "travel".equals(normalized) || "location".equals(normalized);
    }

    private static String firstParam(LostTalesQuestObjectiveDefinition objective, String... keys) {
        if (objective == null || keys == null) {
            return "";
        }
        Map<String, String> params = objective.getParams();
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            String value = params.get(key);
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }

    private static Double parseDouble(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int parseDimensionId(String value) {
        if (value == null || value.trim().length() == 0) {
            return 0;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("minecraft:overworld".equals(normalized) || "overworld".equals(normalized) || "world".equals(normalized)) {
            return 0;
        }
        if ("minecraft:the_nether".equals(normalized) || "minecraft:nether".equals(normalized) || "the_nether".equals(normalized) || "nether".equals(normalized)) {
            return -1;
        }
        if ("minecraft:the_end".equals(normalized) || "minecraft:end".equals(normalized) || "the_end".equals(normalized) || "end".equals(normalized)) {
            return 1;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return 0;
        }
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

        public String getId() {
            return id;
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
