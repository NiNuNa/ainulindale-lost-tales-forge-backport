package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.quest.LostTalesQuestIds;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveObjectiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveRewardData;
import com.ninuna.losttales.quest.missive.MissiveWords;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A missive's letter on the wire, as much of it as a page shows: its
 * quest id and kind, the template ids and target it is worded by
 * ({@link MissiveWords}), objectives, reward and time limit. No sentence
 * travels; each client words the letter in its own language. Every field
 * is bounded; a letter larger than the bounds is not sent ({@link #fits})
 * and its page shows it as one that cannot be read. What the server keeps
 * of a letter to make its quest never travels.
 */
public final class LostTalesMissiveCodec {
    public static final int MAX_QUEST_ID_BYTES = LostTalesQuestIds.MAX_BYTES;
    public static final int MAX_TYPE_BYTES = 64;
    public static final int MAX_TEMPLATE_ID_BYTES =
            MissiveWords.MAX_TEMPLATE_ID_BYTES;
    public static final int MAX_TARGET_BYTES = MissiveWords.MAX_TARGET_BYTES;
    public static final int MAX_OBJECTIVES = 4;
    public static final int MAX_OBJECTIVE_ID_BYTES = 256;
    /** The most parameters an objective carries, and the most reward entries. */
    public static final int MAX_ENTRIES = 6;
    public static final int MAX_KEY_BYTES = 64;
    public static final int MAX_VALUE_BYTES = 256;

    /** A string's length prefix at its longest. */
    private static final int LENGTH_BYTES = 2;
    private static final int ENTRY_BYTES = LENGTH_BYTES + MAX_KEY_BYTES
            + LENGTH_BYTES + MAX_VALUE_BYTES;
    private static final int OBJECTIVE_BYTES = LENGTH_BYTES
            + MAX_OBJECTIVE_ID_BYTES + LENGTH_BYTES + MAX_TYPE_BYTES
            + 1 + 1 + MAX_ENTRIES * ENTRY_BYTES;

    /** A letter at its largest. */
    public static final int MAX_MISSIVE_BYTES = LENGTH_BYTES
            + MAX_QUEST_ID_BYTES + LENGTH_BYTES + MAX_TYPE_BYTES
            + 4 * (LENGTH_BYTES + MAX_TEMPLATE_ID_BYTES)
            + LENGTH_BYTES + MAX_TARGET_BYTES + 8
            + 1 + MAX_OBJECTIVES * OBJECTIVE_BYTES
            + 1 + MAX_ENTRIES * ENTRY_BYTES;

    private LostTalesMissiveCodec() {}

    /** Whether the letter can be sent whole: readable, and within every bound. */
    public static boolean fits(LostTalesMissiveData missive) {
        if (missive == null || !missive.isValid()
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        missive.getQuestId(), MAX_QUEST_ID_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        missive.getQuestType(), MAX_TYPE_BYTES)
                || missive.getObjectives().size() > MAX_OBJECTIVES
                || !fits(missive.getRewardData().getRewards())) {
            return false;
        }
        for (LostTalesMissiveObjectiveData objective : missive.getObjectives()) {
            if (!LostTalesPacketCodec.isUtf8WithinLimit(objective.getId(),
                    MAX_OBJECTIVE_ID_BYTES)
                    || !LostTalesPacketCodec.isUtf8WithinLimit(
                            objective.getType(), MAX_TYPE_BYTES)
                    || !fits(objective.getParams())) {
                return false;
            }
        }
        return true;
    }

    private static boolean fits(Map<String, String> entries) {
        if (entries.size() > MAX_ENTRIES) {
            return false;
        }
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (!LostTalesPacketCodec.isUtf8WithinLimit(entry.getKey(),
                    MAX_KEY_BYTES)
                    || !LostTalesPacketCodec.isUtf8WithinLimit(
                            entry.getValue(), MAX_VALUE_BYTES)) {
                return false;
            }
        }
        return true;
    }

    /** Writes a letter that {@link #fits}; any other is refused. */
    public static void write(ByteBuf buffer, LostTalesMissiveData missive) {
        if (!fits(missive)) {
            throw new IllegalArgumentException("missive does not fit the wire");
        }
        LostTalesPacketCodec.writeUtf8String(buffer, missive.getQuestId(),
                MAX_QUEST_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, missive.getQuestType(),
                MAX_TYPE_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, missive.getTitleId(),
                MAX_TEMPLATE_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, missive.getIssuerId(),
                MAX_TEMPLATE_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer,
                missive.getDescriptionId(), MAX_TEMPLATE_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, missive.getFlavorId(),
                MAX_TEMPLATE_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, missive.getTarget(),
                MAX_TARGET_BYTES);
        buffer.writeLong(missive.getTimeLimitTicks());
        buffer.writeByte(missive.getObjectives().size());
        for (LostTalesMissiveObjectiveData objective : missive.getObjectives()) {
            LostTalesPacketCodec.writeUtf8String(buffer, objective.getId(),
                    MAX_OBJECTIVE_ID_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, objective.getType(),
                    MAX_TYPE_BYTES);
            buffer.writeBoolean(objective.isOptional());
            writeEntries(buffer, objective.getParams());
        }
        writeEntries(buffer, missive.getRewardData().getRewards());
    }

    /**
     * Reads a letter, which must be whole and readable; anything else
     * throws {@link LostTalesPacketCodec.DecodeException}.
     */
    public static LostTalesMissiveData read(ByteBuf buffer) {
        String questId = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_QUEST_ID_BYTES);
        String questType = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_TYPE_BYTES);
        String titleId = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_TEMPLATE_ID_BYTES);
        String issuerId = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_TEMPLATE_ID_BYTES);
        String descriptionId = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_TEMPLATE_ID_BYTES);
        String flavorId = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_TEMPLATE_ID_BYTES);
        String target = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_TARGET_BYTES);
        long timeLimitTicks = readLong(buffer);
        if (timeLimitTicks < 0L) {
            throw new LostTalesPacketCodec.DecodeException(
                    "negative missive time limit");
        }
        int objectiveCount = readSmallCount(buffer, MAX_OBJECTIVES,
                "objective");
        List<LostTalesMissiveObjectiveData> objectives =
                new ArrayList<LostTalesMissiveObjectiveData>(objectiveCount);
        for (int index = 0; index < objectiveCount; index++) {
            String id = LostTalesPacketCodec.readUtf8String(buffer,
                    MAX_OBJECTIVE_ID_BYTES);
            String type = LostTalesPacketCodec.readUtf8String(buffer,
                    MAX_TYPE_BYTES);
            boolean optional = readBoolean(buffer);
            LostTalesMissiveObjectiveData objective =
                    new LostTalesMissiveObjectiveData(id, type,
                            optional, readEntries(buffer));
            if (!objective.isValid() || !objective.getId().equals(id)
                    || !objective.getType().equals(type)) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid missive objective");
            }
            objectives.add(objective);
        }
        Map<String, String> rewards = readEntries(buffer);
        LostTalesMissiveData missive = new LostTalesMissiveData(questId,
                questType, titleId, descriptionId, issuerId, flavorId,
                target, true, true, 0L, timeLimitTicks,
                Collections.<String, String>emptyMap(), objectives,
                new LostTalesMissiveRewardData(rewards));
        if (!missive.isValid()
                || !missive.getQuestId().equals(questId)
                || !missive.getQuestType().equals(questType)
                || !missive.getTitleId().equals(titleId)
                || !missive.getIssuerId().equals(issuerId)
                || !missive.getDescriptionId().equals(descriptionId)
                || !missive.getFlavorId().equals(flavorId)
                || !missive.getTarget().equals(target)) {
            throw new LostTalesPacketCodec.DecodeException(
                    "invalid missive letter");
        }
        return missive;
    }

    private static void writeEntries(ByteBuf buffer,
                                     Map<String, String> entries) {
        buffer.writeByte(entries.size());
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            LostTalesPacketCodec.writeUtf8String(buffer, entry.getKey(),
                    MAX_KEY_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, entry.getValue(),
                    MAX_VALUE_BYTES);
        }
    }

    /** Named entries, each key once and never blank. */
    private static Map<String, String> readEntries(ByteBuf buffer) {
        int count = readSmallCount(buffer, MAX_ENTRIES, "entry");
        Map<String, String> entries = new LinkedHashMap<String, String>();
        for (int index = 0; index < count; index++) {
            String key = LostTalesPacketCodec.readUtf8String(buffer,
                    MAX_KEY_BYTES);
            String value = LostTalesPacketCodec.readUtf8String(buffer,
                    MAX_VALUE_BYTES);
            if (key.trim().length() == 0 || !key.equals(key.trim())
                    || entries.containsKey(key)) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid missive entry");
            }
            entries.put(key, value);
        }
        return entries;
    }

    static int readSmallCount(ByteBuf buffer, int maximum, String field) {
        if (buffer == null || !buffer.isReadable()) {
            throw new LostTalesPacketCodec.DecodeException("truncated packet");
        }
        int count = buffer.readUnsignedByte();
        if (count > maximum) {
            throw new LostTalesPacketCodec.DecodeException(
                    "invalid " + field + " count");
        }
        return count;
    }

    static long readLong(ByteBuf buffer) {
        if (buffer == null || buffer.readableBytes() < 8) {
            throw new LostTalesPacketCodec.DecodeException("truncated packet");
        }
        return buffer.readLong();
    }

    static boolean readBoolean(ByteBuf buffer) {
        if (buffer == null || !buffer.isReadable()) {
            throw new LostTalesPacketCodec.DecodeException("truncated packet");
        }
        int value = buffer.readUnsignedByte();
        if (value > 1) {
            throw new LostTalesPacketCodec.DecodeException("invalid boolean");
        }
        return value == 1;
    }
}
