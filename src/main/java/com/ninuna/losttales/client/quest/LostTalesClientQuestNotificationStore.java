package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveSelection;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import com.ninuna.losttales.quest.LostTalesQuestTimeText;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;
/**
 * Small client-side notification queue for quest HUD messages.
 *
 * Short quest banners are derived from authoritative sync snapshots, keeping
 * state-change feedback out of chat while avoiding a second event protocol.
 */
public final class LostTalesClientQuestNotificationStore {
    private static final long DEFAULT_DURATION_MS = 4200L;
    private static final int MAX_NOTIFICATIONS = 4;

    private static final List<Notification> NOTIFICATIONS = new ArrayList<Notification>();

    private LostTalesClientQuestNotificationStore() {}

    public static synchronized void clear() {
        NOTIFICATIONS.clear();
    }

    public static synchronized void add(String message, Type type) {
        if (message == null || message.trim().length() == 0) {
            return;
        }
        NOTIFICATIONS.add(new Notification(message.trim(), type == null ? Type.INFO : type, System.currentTimeMillis(), DEFAULT_DURATION_MS));
        trimOldest();
    }

    public static synchronized void addInfo(String message) {
        add(message, Type.INFO);
    }

    public static synchronized void addProgress(String message) {
        add(message, Type.PROGRESS);
    }

    public static synchronized void addComplete(String message) {
        add(message, Type.COMPLETE);
    }

    public static synchronized void addFailed(String message) {
        add(message, Type.FAILED);
    }

    /**
     * Compares the next sync with the quest state held now, and shows what
     * changed as banners with their sounds: a quest started, taken again,
     * advanced, an objective moved or done, a quest finished, failed or
     * given up. The sounds are this player's alone, and play only while
     * their quest sounds are on. The first sync after joining a world is
     * silent, so old quest state is not played as news.
     */
    public static synchronized void notifyForIncomingSync(Collection<LostTalesQuestProgress> newActiveQuests, Collection<LostTalesQuestHistoryEntry> newQuestHistory) {
        if (!LostTalesClientQuestProgressStore.hasReceivedSync()) {
            return;
        }

        Map<String, LostTalesQuestProgress> oldActive = toProgressMap(LostTalesClientQuestProgressStore.getActiveQuests());
        Map<String, LostTalesQuestProgress> newActive = toProgressMap(newActiveQuests);
        Map<String, LostTalesQuestHistoryEntry> oldHistory = toHistoryMap(
                LostTalesClientQuestProgressStore.getQuestHistory());
        Map<String, LostTalesQuestHistoryEntry> nextHistory = toHistoryMap(
                newQuestHistory);

        for (LostTalesQuestHistoryEntry entry : nextHistory.values()) {
            LostTalesQuestHistoryEntry previous = oldHistory.get(
                    entry.getQuestId());
            if (entry.isCompleted() && (previous == null
                    || previous.getWorldTime() != entry.getWorldTime())) {
                addComplete(banner("completed", entry.getQuestId()));
                playQuestSound(SOUND_COMPLETED, 0.45F, 1.0F);
            }
        }

        for (LostTalesQuestHistoryEntry entry : nextHistory.values()) {
            LostTalesQuestHistoryEntry previous = oldHistory.get(
                    entry.getQuestId());
            if (previous != null
                    && previous.getOutcome() == entry.getOutcome()
                    && previous.getWorldTime() == entry.getWorldTime()) {
                continue;
            }
            if (entry.isCompleted()) {
                continue;
            }
            String reason = entry.getDetail().length() == 0 ? ""
                    : " - " + StatCollector.translateToLocal(entry.getDetail());
            if (entry.isFailed()) {
                addFailed(banner("failed", entry.getQuestId()) + reason);
                playQuestSound(SOUND_FAILED, 0.3F, 0.8F);
            } else {
                add(banner("abandoned", entry.getQuestId()) + reason,
                        Type.ABANDONED);
            }
        }

        for (Map.Entry<String, LostTalesQuestProgress> entry : newActive.entrySet()) {
            String questId = entry.getKey();
            LostTalesQuestProgress next = entry.getValue();
            LostTalesQuestProgress previous = oldActive.get(questId);

            if (previous == null) {
                // A quest taken again after it ended starts as anew.
                addInfo(banner("started", questId));
                playQuestSound(SOUND_PROGRESS, 0.35F, 1.0F);
                if (next.getDeadlineWorldTime() > next.getAcceptedWorldTime()) {
                    addFailed(StatCollector.translateToLocalFormatted(
                            "gui.losttales.quest.banner.time_limit",
                            LostTalesQuestTimeText.shortForm(
                                    next.getDeadlineWorldTime()
                                            - next.getAcceptedWorldTime())));
                }
                continue;
            }

            if (stageChanged(previous, next)) {
                addInfo(banner("advanced", questId));
                playQuestSound(SOUND_PROGRESS, 0.35F, 1.05F);
            }

            notifyObjectiveChanges(questId, previous, next);
        }
    }

    public static synchronized List<Notification> getVisibleNotifications() {
        long now = System.currentTimeMillis();
        List<Notification> visible = new ArrayList<Notification>();
        for (int i = NOTIFICATIONS.size() - 1; i >= 0 && visible.size() < MAX_NOTIFICATIONS; i--) {
            Notification notification = NOTIFICATIONS.get(i);
            if (!notification.isExpired(now)) {
                visible.add(notification);
            }
        }
        Collections.reverse(visible);
        return Collections.unmodifiableList(visible);
    }

    private static void notifyObjectiveChanges(String questId, LostTalesQuestProgress previous, LostTalesQuestProgress next) {
        LostTalesQuestDefinition quest = LostTalesClientQuestDefinitionStore.getQuest(questId);
        if (quest == null) {
            return;
        }

        Map<String, LostTalesQuestObjectiveDefinition> objectives =
                new LinkedHashMap<String, LostTalesQuestObjectiveDefinition>();
        for (LostTalesQuestObjectiveDefinition objective
                : LostTalesQuestObjectiveSelection
                .getProgressibleObjectives(quest, previous)) {
            objectives.put(objective.getId(), objective);
        }
        for (LostTalesQuestObjectiveDefinition objective
                : LostTalesQuestObjectiveSelection
                .getProgressibleObjectives(quest, next)) {
            objectives.put(objective.getId(), objective);
        }

        for (LostTalesQuestObjectiveDefinition objective
                : objectives.values()) {
            int before = previous.getObjectiveProgress(objective.getId());
            int after = next.getObjectiveProgress(objective.getId());
            if (after <= before) {
                continue;
            }

            int target = getObjectiveTargetCount(objective);
            String description = objective.getDescription() == null || objective.getDescription().length() == 0 ? objective.getId() : objective.getDescription();
            if (before < target && after >= target) {
                addComplete(StatCollector.translateToLocalFormatted(
                        "gui.losttales.quest.banner.objective_done", description));
            } else {
                addProgress(description + " (" + Math.min(after, target) + "/" + target + ")");
            }
            playQuestSound(SOUND_PROGRESS, 0.35F, 1.25F);
        }
    }

    private static Map<String, LostTalesQuestProgress> toProgressMap(Collection<LostTalesQuestProgress> progresses) {
        Map<String, LostTalesQuestProgress> map = new LinkedHashMap<String, LostTalesQuestProgress>();
        if (progresses != null) {
            for (LostTalesQuestProgress progress : progresses) {
                if (progress != null && progress.getQuestId() != null && progress.getQuestId().length() > 0) {
                    map.put(progress.getQuestId(), progress.copy());
                }
            }
        }
        return map;
    }

    private static Map<String, LostTalesQuestHistoryEntry> toHistoryMap(
            Collection<LostTalesQuestHistoryEntry> entries) {
        Map<String, LostTalesQuestHistoryEntry> map =
                new LinkedHashMap<String, LostTalesQuestHistoryEntry>();
        if (entries != null) {
            for (LostTalesQuestHistoryEntry entry : entries) {
                if (entry != null && entry.getQuestId().length() > 0) {
                    map.put(entry.getQuestId(), entry);
                }
            }
        }
        return map;
    }

    private static boolean stageChanged(LostTalesQuestProgress previous, LostTalesQuestProgress next) {
        if (previous.getStageIndex() != next.getStageIndex()) {
            return true;
        }
        String previousId = previous.getStageId() == null ? "" : previous.getStageId();
        String nextId = next.getStageId() == null ? "" : next.getStageId();
        return !previousId.equals(nextId);
    }

    private static String questTitle(String questId) {
        LostTalesQuestDefinition quest = LostTalesClientQuestDefinitionStore.getQuest(questId);
        return quest == null ? questId : quest.getTitle();
    }

    private static int getObjectiveTargetCount(LostTalesQuestObjectiveDefinition objective) {
        return LostTalesQuestObjectiveTextHelper
                .getObjectiveTargetCount(objective);
    }

    /** A banner's words for one quest: {@code gui.losttales.quest.banner.<event>} with its title. */
    private static String banner(String event, String questId) {
        return StatCollector.translateToLocalFormatted(
                "gui.losttales.quest.banner." + event, questTitle(questId));
    }

    private static final String SOUND_PROGRESS = "random.orb";
    private static final String SOUND_COMPLETED = "random.levelup";
    private static final String SOUND_FAILED = "random.break";

    /** A quest sound for this player alone, while their quest sounds are on. */
    private static void playQuestSound(String sound, float volume, float pitch) {
        if (!LostTalesConfig.playQuestSounds) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.thePlayer != null) {
            minecraft.thePlayer.playSound(sound, volume, pitch);
        }
    }

    private static void trimOldest() {
        long now = System.currentTimeMillis();
        for (int i = NOTIFICATIONS.size() - 1; i >= 0; i--) {
            if (NOTIFICATIONS.get(i).isExpired(now)) {
                NOTIFICATIONS.remove(i);
            }
        }
        while (NOTIFICATIONS.size() > MAX_NOTIFICATIONS) {
            NOTIFICATIONS.remove(0);
        }
    }

    public enum Type {
        INFO(0xDDBB77, "Quest Updated"),
        PROGRESS(0xAADDFF, "Objective Progress"),
        COMPLETE(0x77DD77, "Quest Complete"),
        FAILED(0xDD7777, "Quest Failed"),
        ABANDONED(0xCCAA77, "Quest Abandoned");

        private final int color;
        private final String displayTitle;

        Type(int color, String displayTitle) {
            this.color = color;
            this.displayTitle = displayTitle;
        }

        public int getColor() {
            return this.color;
        }

        public String getDisplayTitle() {
            return this.displayTitle;
        }
    }

    public static final class Notification {
        private final String message;
        private final Type type;
        private final long createdAt;
        private final long durationMs;

        private Notification(String message, Type type, long createdAt, long durationMs) {
            this.message = message;
            this.type = type;
            this.createdAt = createdAt;
            this.durationMs = durationMs;
        }

        public String getMessage() {
            return this.message;
        }

        public Type getType() {
            return this.type;
        }

        public float getAlpha(long now) {
            long age = now - this.createdAt;
            if (age < 0L || age >= this.durationMs) {
                return 0.0F;
            }
            long fadeIn = 180L;
            long fadeOut = 700L;
            if (age < fadeIn) {
                return Math.max(0.15F, (float) age / (float) fadeIn);
            }
            long remaining = this.durationMs - age;
            if (remaining < fadeOut) {
                return Math.max(0.0F, (float) remaining / (float) fadeOut);
            }
            return 1.0F;
        }

        private boolean isExpired(long now) {
            return now - this.createdAt >= this.durationMs;
        }
    }
}
