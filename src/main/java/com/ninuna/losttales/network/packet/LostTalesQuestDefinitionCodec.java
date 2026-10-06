package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestIds;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.missive.MissiveWords;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A whole quest definition on the wire, as the server hands one to a
 * client that has no file for it: a missive a player took, worded by its
 * template ids, or a quest a server wrote in its own folder, in its
 * operator's words. Every string, count and map is bounded;
 * a read that finds anything out of bounds throws a
 * {@link LostTalesPacketCodec.DecodeException}, which the packet reading
 * it turns into a malformed payload.
 */
public final class LostTalesQuestDefinitionCodec {
    static final int MAX_QUEST_STAGES = 256;
    static final int MAX_STAGE_OBJECTIVES = 512;
    static final int MAX_STRING_MAP_ENTRIES = 256;
    static final int MAX_IDENTIFIER_BYTES = 256;
    static final int MAX_QUEST_ID_BYTES = LostTalesQuestIds.MAX_BYTES;
    static final int MAX_NAME_BYTES = 1024;
    static final int MAX_TEXT_BYTES = 8192;
    static final int MAX_MAP_VALUE_BYTES = 4096;

    private LostTalesQuestDefinitionCodec() {}

    /**
     * How many bytes the quest takes on the wire; -1 for one that cannot
     * be written, an id, a name or a text too long, or too many stages,
     * objectives or parameters.
     */
    public static int encodedSize(LostTalesQuestDefinition quest) {
        ByteBuf probe = Unpooled.buffer();
        try {
            write(probe, quest);
            return probe.readableBytes();
        } catch (RuntimeException unwritable) {
            return -1;
        } finally {
            probe.release();
        }
    }

    public static void write(ByteBuf buf, LostTalesQuestDefinition quest) {
        if (quest == null || quest.getId() == null
                || quest.getId().length() == 0) {
            throw new IllegalStateException("invalid quest definition");
        }
        writeString(buf, quest.getId(), MAX_QUEST_ID_BYTES);
        writeString(buf, quest.getTitle(), MAX_NAME_BYTES);
        writeString(buf, quest.getDescription(), MAX_TEXT_BYTES);
        buf.writeBoolean(quest.isRepeatable());
        buf.writeBoolean(quest.isRestartable());
        writeString(buf, quest.getStartMode(), MAX_IDENTIFIER_BYTES);
        writeStringMap(buf, quest.getPrerequisites(), "prerequisite");
        writeStringMap(buf, quest.getRewards(), "reward");
        writeStringMap(buf, quest.getInteraction(), "interaction");
        writeStringMap(buf, quest.getMarkers(), "quest marker");
        writeStringMap(buf, quest.getJournalLog(), "journal log");
        writeStringMap(buf, quest.getDialogue(), "dialogue");
        writeStringMap(buf, quest.getWorld(), "world");
        if (!MissiveWords.isWellFormed(quest.getWords())) {
            throw new IllegalStateException("invalid quest words");
        }
        writeStringMap(buf, quest.getWords(), "words");

        List<LostTalesQuestStageDefinition> stages = quest.getStages();
        LostTalesPacketCodec.writeCount(buf, stages.size(),
                MAX_QUEST_STAGES, "quest stage");
        for (LostTalesQuestStageDefinition stage : stages) {
            if (stage == null) {
                throw new IllegalStateException("invalid quest stage");
            }
            writeString(buf, stage.getId(), MAX_IDENTIFIER_BYTES);
            List<LostTalesQuestObjectiveDefinition> objectives =
                    stage.getObjectives();
            LostTalesPacketCodec.writeCount(buf, objectives.size(),
                    MAX_STAGE_OBJECTIVES, "stage objective");
            for (LostTalesQuestObjectiveDefinition objective : objectives) {
                if (objective == null || objective.getId() == null
                        || objective.getId().length() == 0
                        || objective.getType() == null
                        || objective.getType().length() == 0) {
                    throw new IllegalStateException(
                            "invalid quest objective");
                }
                writeString(buf, objective.getId(), MAX_IDENTIFIER_BYTES);
                writeString(buf, objective.getType(), MAX_IDENTIFIER_BYTES);
                writeString(buf, objective.getDescription(), MAX_TEXT_BYTES);
                buf.writeBoolean(objective.isOptional());
                writeStringMap(buf, objective.getParams(),
                        "objective parameter");
            }
        }
    }

    public static LostTalesQuestDefinition read(ByteBuf buf) {
        String id = LostTalesPacketCodec.readUtf8String(buf,
                MAX_QUEST_ID_BYTES);
        String title = LostTalesPacketCodec.readUtf8String(buf,
                MAX_NAME_BYTES);
        String description = LostTalesPacketCodec.readUtf8String(buf,
                MAX_TEXT_BYTES);
        boolean repeatable = buf.readBoolean();
        boolean restartable = buf.readBoolean();
        String startMode = LostTalesPacketCodec.readUtf8String(buf,
                MAX_IDENTIFIER_BYTES);
        Map<String, String> prerequisites = readStringMap(buf,
                "prerequisite");
        Map<String, String> rewards = readStringMap(buf, "reward");
        Map<String, String> interaction = readStringMap(buf, "interaction");
        Map<String, String> markers = readStringMap(buf, "quest marker");
        Map<String, String> journalLog = readStringMap(buf, "journal log");
        Map<String, String> dialogue = readStringMap(buf, "dialogue");
        Map<String, String> world = readStringMap(buf, "world");
        Map<String, String> words = readStringMap(buf, "words");
        if (!MissiveWords.isWellFormed(words)) {
            throw new LostTalesPacketCodec.DecodeException(
                    "invalid quest words");
        }

        List<LostTalesQuestStageDefinition> stages =
                new ArrayList<LostTalesQuestStageDefinition>();
        int stageCount = LostTalesPacketCodec.readCount(
                buf, MAX_QUEST_STAGES, "quest stage");
        for (int i = 0; i < stageCount; i++) {
            String stageId = LostTalesPacketCodec.readUtf8String(buf,
                    MAX_IDENTIFIER_BYTES);
            List<LostTalesQuestObjectiveDefinition> objectives =
                    new ArrayList<LostTalesQuestObjectiveDefinition>();
            int objectiveCount = LostTalesPacketCodec.readCount(
                    buf, MAX_STAGE_OBJECTIVES, "stage objective");
            for (int j = 0; j < objectiveCount; j++) {
                String objectiveId = LostTalesPacketCodec.readUtf8String(buf,
                        MAX_IDENTIFIER_BYTES);
                String objectiveType = LostTalesPacketCodec.readUtf8String(
                        buf, MAX_IDENTIFIER_BYTES);
                String objectiveDescription =
                        LostTalesPacketCodec.readUtf8String(buf,
                                MAX_TEXT_BYTES);
                boolean optional = buf.readBoolean();
                Map<String, String> params = readStringMap(
                        buf, "objective parameter");
                if (objectiveId.length() == 0
                        || objectiveType.length() == 0) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid quest objective");
                }
                objectives.add(new LostTalesQuestObjectiveDefinition(
                        objectiveId, objectiveType, objectiveDescription,
                        optional, params));
            }
            stages.add(new LostTalesQuestStageDefinition(stageId, objectives));
        }

        if (id.length() == 0) {
            throw new LostTalesPacketCodec.DecodeException(
                    "quest ID is empty");
        }
        return new LostTalesQuestDefinition(id, title, description,
                repeatable, restartable, startMode, prerequisites, rewards,
                interaction, markers, journalLog, dialogue, world, stages,
                false, words);
    }

    private static void writeString(ByteBuf buf, String value, int maxBytes) {
        LostTalesPacketCodec.writeUtf8String(buf, value == null ? "" : value,
                maxBytes);
    }

    private static void writeStringMap(ByteBuf buf, Map<String, String> values,
                                       String fieldName) {
        if (values == null || values.isEmpty()) {
            buf.writeInt(0);
            return;
        }
        LostTalesPacketCodec.writeCount(buf, values.size(),
                MAX_STRING_MAP_ENTRIES, fieldName);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() == 0) {
                throw new IllegalStateException(
                        "invalid " + fieldName + " key");
            }
            writeString(buf, entry.getKey(), MAX_IDENTIFIER_BYTES);
            writeString(buf, entry.getValue(), MAX_MAP_VALUE_BYTES);
        }
    }

    private static Map<String, String> readStringMap(ByteBuf buf,
                                                     String fieldName) {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        int count = LostTalesPacketCodec.readCount(
                buf, MAX_STRING_MAP_ENTRIES, fieldName);
        for (int i = 0; i < count; i++) {
            String key = LostTalesPacketCodec.readUtf8String(buf,
                    MAX_IDENTIFIER_BYTES);
            String value = LostTalesPacketCodec.readUtf8String(
                    buf, MAX_MAP_VALUE_BYTES);
            if (key.length() == 0) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid " + fieldName + " key");
            }
            map.put(key, value);
        }
        return map;
    }
}
