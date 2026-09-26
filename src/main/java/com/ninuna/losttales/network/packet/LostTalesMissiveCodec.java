package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveObjectiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveRewardData;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A missive's letter on the wire, as much of it as a page shows: its
 * quest id and kind, title, issuer, words, objectives, reward and time
 * limit. Every field is bounded; a letter larger than the bounds is not
 * sent ({@link #fits}) and its page shows it as one that cannot be read.
 * What the server keeps of a letter to make its quest never travels.
 */
public final class LostTalesMissiveCodec {
    public static final int MAX_QUEST_ID_BYTES = 512;
    public static final int MAX_TYPE_BYTES = 64;
    public static final int MAX_TITLE_BYTES = 512;
    public static final int MAX_ISSUER_BYTES = 512;
    public static final int MAX_TEXT_BYTES = 2048;
    public static final int MAX_OBJECTIVES = 4;
    public static final int MAX_OBJECTIVE_ID_BYTES = 256;
    public static final int MAX_OBJECTIVE_TEXT_BYTES = 512;
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
            + LENGTH_BYTES + MAX_OBJECTIVE_TEXT_BYTES + 1 + 1
            + MAX_ENTRIES * ENTRY_BYTES;

    /** A letter at its largest. */
    public static final int MAX_MISSIVE_BYTES = LENGTH_BYTES
            + MAX_QUEST_ID_BYTES + LENGTH_BYTES + MAX_TYPE_BYTES
            + LENGTH_BYTES + MAX_TITLE_BYTES + LENGTH_BYTES
            + MAX_ISSUER_BYTES + 2 * (LENGTH_BYTES + MAX_TEXT_BYTES) + 8
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
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        missive.getTitle(), MAX_TITLE_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        missive.getIssuer(), MAX_ISSUER_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        missive.getDescription(), MAX_TEXT_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        missive.getFlavorText(), MAX_TEXT_BYTES)
                || missive.getObjectives().size() > MAX_OBJECTIVES
                || !fits(missive.getRewardData().getRewards())) {
            return false;
        }
        for (LostTalesMissiveObjectiveData objective : missive.getObjectives()) {
            if (!LostTalesPacketCodec.isUtf8WithinLimit(objective.getId(),
                    MAX_OBJECTIVE_ID_BYTES)
                    || !LostTalesPacketCodec.isUtf8WithinLimit(
                            objective.getType(), MAX_TYPE_BYTES)
                    || !LostTalesPacketCodec.isUtf8WithinLimit(
                            objective.getDescription(),
                            MAX_OBJECTIVE_TEXT_BYTES)
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
        LostTalesPacketCodec.writeUtf8String(buffer, missive.getTitle(),
                MAX_TITLE_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, missive.getIssuer(),
                MAX_ISSUER_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, missive.getDescription(),
                MAX_TEXT_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, missive.getFlavorText(),
                MAX_TEXT_BYTES);
        buffer.writeLong(missive.getTimeLimitTicks());
        buffer.writeByte(missive.getObjectives().size());
        for (LostTalesMissiveObjectiveData objective : missive.getObjectives()) {
            LostTalesPacketCodec.writeUtf8String(buffer, objective.getId(),
                    MAX_OBJECTIVE_ID_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, objective.getType(),
                    MAX_TYPE_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer,
                    objective.getDescription(), MAX_OBJECTIVE_TEXT_BYTES);
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
        String title = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_TITLE_BYTES);
        String issuer = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_ISSUER_BYTES);
        String description = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_TEXT_BYTES);
        String flavorText = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_TEXT_BYTES);
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
            String text = LostTalesPacketCodec.readUtf8String(buffer,
                    MAX_OBJECTIVE_TEXT_BYTES);
            boolean optional = readBoolean(buffer);
            LostTalesMissiveObjectiveData objective =
                    new LostTalesMissiveObjectiveData(id, type, text,
                            optional, readEntries(buffer));
            if (!objective.isValid()) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid missive objective");
            }
            objectives.add(objective);
        }
        Map<String, String> rewards = readEntries(buffer);
        LostTalesMissiveData missive = new LostTalesMissiveData(questId,
                questType, title, description, issuer, flavorText, true,
                true, 0L, timeLimitTicks,
                Collections.<String, String>emptyMap(), objectives,
                new LostTalesMissiveRewardData(rewards));
        if (!missive.isValid()
                || !missive.getQuestId().equals(questId)
                || !missive.getQuestType().equals(questType)) {
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
