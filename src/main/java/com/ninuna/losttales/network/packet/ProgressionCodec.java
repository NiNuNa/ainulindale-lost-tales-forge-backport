package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.progression.ProgressionState;
import com.ninuna.losttales.progression.skill.PerkDefinition;
import com.ninuna.losttales.progression.skill.SkillAct;
import com.ninuna.losttales.progression.skill.SkillCount;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import com.ninuna.losttales.progression.skill.SkillFileValidator;
import com.ninuna.losttales.progression.deed.DeedDefinition;
import com.ninuna.losttales.progression.deed.DeedSummary;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The progression's bounded reads and writes on the wire: a skill as the
 * Skills page and the client's perk effects need it (its words, its
 * order, the weapons it is used with, its tree; never what counts toward
 * it, which only the server reads) and the numbers of a perk.
 */
public final class ProgressionCodec {
    public static final int MAX_ID_BYTES = ProgressionState.MAX_ID_CHARACTERS * 4;
    public static final int MAX_NAME_BYTES = SkillFileValidator.MAX_NAME_LENGTH * 4;
    public static final int MAX_DESCRIPTION_BYTES = SkillFileValidator.MAX_DESCRIPTION_LENGTH * 4;
    public static final int MAX_PARAMS = 8;

    private ProgressionCodec() {}

    public static void writeSkill(ByteBuf buf, SkillDefinition skill) {
        LostTalesPacketCodec.writeUtf8String(buf, skill.getId(), MAX_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(buf, skill.getIcon(), MAX_ID_BYTES);
        buf.writeInt(skill.getOrder());
        LostTalesPacketCodec.writeUtf8String(buf, skill.getName(), MAX_NAME_BYTES);
        LostTalesPacketCodec.writeUtf8String(buf, skill.getDescription(), MAX_DESCRIPTION_BYTES);
        writeIds(buf, skill.weapons(), SkillFileValidator.MAX_WEAPONS);
        LostTalesPacketCodec.writeCount(buf, skill.getPerks().size(),
                SkillFileValidator.MAX_PERKS, "perk");
        for (PerkDefinition perk : skill.getPerks()) {
            writePerk(buf, perk);
        }
    }

    public static SkillDefinition readSkill(ByteBuf buf) {
        String id = readId(buf);
        String icon = LostTalesPacketCodec.readUtf8String(buf, MAX_ID_BYTES);
        int order = buf.readInt();
        String name = LostTalesPacketCodec.readUtf8String(buf, MAX_NAME_BYTES);
        String description = LostTalesPacketCodec.readUtf8String(buf, MAX_DESCRIPTION_BYTES);
        List<String> weapons = readIds(buf, SkillFileValidator.MAX_WEAPONS);
        int perkCount = LostTalesPacketCodec.readCount(buf, SkillFileValidator.MAX_PERKS, "perk");
        List<PerkDefinition> perks = new ArrayList<PerkDefinition>();
        for (int index = 0; index < perkCount; index++) {
            perks.add(readPerk(buf));
        }
        // The weapons travel as a hit count of their own; nothing is worth anything on a client.
        List<SkillCount> counts = weapons.isEmpty() ? Collections.<SkillCount>emptyList()
                : Collections.singletonList(new SkillCount(SkillAct.HIT, 0.0D, weapons, null, null));
        return new SkillDefinition(id, icon, order, name, description, counts, perks);
    }

    private static void writePerk(ByteBuf buf, PerkDefinition perk) {
        LostTalesPacketCodec.writeUtf8String(buf, perk.getId(), MAX_ID_BYTES);
        int[] levels = perk.getLevels();
        LostTalesPacketCodec.writeCount(buf, levels.length, SkillFileValidator.MAX_RANKS, "rank");
        for (int level : levels) {
            buf.writeByte(level);
        }
        LostTalesPacketCodec.writeUtf8String(buf, perk.getNeedsPerk(), MAX_ID_BYTES);
        buf.writeByte(perk.getNeedsRank());
        LostTalesPacketCodec.writeUtf8String(buf, perk.getEffect(), MAX_ID_BYTES);
        buf.writeDouble(perk.getAmount());
        LostTalesPacketCodec.writeCount(buf, perk.getParams().size(), MAX_PARAMS, "param");
        for (Map.Entry<String, Double> param : perk.getParams().entrySet()) {
            LostTalesPacketCodec.writeUtf8String(buf, param.getKey(), MAX_ID_BYTES);
            buf.writeDouble(param.getValue().doubleValue());
        }
        writeIds(buf, perk.getWith(), SkillFileValidator.MAX_WITH);
        buf.writeBoolean(perk.isEnabled());
        LostTalesPacketCodec.writeUtf8String(buf, perk.getName(), MAX_NAME_BYTES);
        LostTalesPacketCodec.writeUtf8String(buf, perk.getDescription(), MAX_DESCRIPTION_BYTES);
    }

    private static PerkDefinition readPerk(ByteBuf buf) {
        String id = readId(buf);
        int ranks = LostTalesPacketCodec.readCount(buf, SkillFileValidator.MAX_RANKS, "rank");
        if (ranks < 1) {
            throw new LostTalesPacketCodec.DecodeException("a perk without ranks");
        }
        int[] levels = new int[ranks];
        for (int index = 0; index < ranks; index++) {
            levels[index] = buf.readUnsignedByte();
            if (levels[index] < 1 || levels[index] > 100) {
                throw new LostTalesPacketCodec.DecodeException("a perk level out of range");
            }
        }
        String needsPerk = LostTalesPacketCodec.readUtf8String(buf, MAX_ID_BYTES);
        int needsRank = buf.readUnsignedByte();
        String effect = LostTalesPacketCodec.readUtf8String(buf, MAX_ID_BYTES);
        double amount = finite(buf.readDouble());
        int paramCount = LostTalesPacketCodec.readCount(buf, MAX_PARAMS, "param");
        Map<String, Double> params = new LinkedHashMap<String, Double>();
        for (int index = 0; index < paramCount; index++) {
            String key = readId(buf);
            if (params.put(key, Double.valueOf(finite(buf.readDouble()))) != null) {
                throw new LostTalesPacketCodec.DecodeException("a param written twice");
            }
        }
        List<String> with = readIds(buf, SkillFileValidator.MAX_WITH);
        boolean enabled = buf.readBoolean();
        String name = LostTalesPacketCodec.readUtf8String(buf, MAX_NAME_BYTES);
        String description = LostTalesPacketCodec.readUtf8String(buf, MAX_DESCRIPTION_BYTES);
        return new PerkDefinition(id, levels, needsPerk, needsRank, effect, amount,
                params, with, enabled, name, description);
    }

    private static void writeIds(ByteBuf buf, List<String> ids, int max) {
        LostTalesPacketCodec.writeCount(buf, ids.size(), max, "name");
        for (String id : ids) {
            writeId(buf, id);
        }
    }

    private static List<String> readIds(ByteBuf buf, int max) {
        int count = LostTalesPacketCodec.readCount(buf, max, "name");
        List<String> ids = new ArrayList<String>();
        for (int index = 0; index < count; index++) {
            ids.add(readId(buf));
        }
        return ids;
    }

    public static void writeDeed(ByteBuf buf, DeedSummary deed) {
        writeId(buf, deed.getId());
        LostTalesPacketCodec.writeUtf8String(buf, deed.getCategory(), MAX_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(buf, deed.getIcon(), MAX_ID_BYTES);
        buf.writeByte(deed.getKind().ordinal());
        buf.writeLong(deed.getTarget());
        buf.writeBoolean(deed.hasTitle());
        LostTalesPacketCodec.writeUtf8String(buf, deed.getName(), MAX_NAME_BYTES);
        LostTalesPacketCodec.writeUtf8String(buf, deed.getDescription(), MAX_DESCRIPTION_BYTES);
    }

    public static DeedSummary readDeed(ByteBuf buf) {
        String id = readId(buf);
        String category = LostTalesPacketCodec.readUtf8String(buf, MAX_ID_BYTES);
        String icon = LostTalesPacketCodec.readUtf8String(buf, MAX_ID_BYTES);
        int kind = buf.readUnsignedByte();
        if (kind >= DeedDefinition.Kind.values().length) {
            throw new LostTalesPacketCodec.DecodeException("a deed of no kind");
        }
        long target = buf.readLong();
        if (target < 0L) {
            throw new LostTalesPacketCodec.DecodeException("a deed's target below 0");
        }
        boolean title = buf.readBoolean();
        String name = LostTalesPacketCodec.readUtf8String(buf, MAX_NAME_BYTES);
        String description = LostTalesPacketCodec.readUtf8String(buf, MAX_DESCRIPTION_BYTES);
        return new DeedSummary(id, category, icon, DeedDefinition.Kind.values()[kind], target,
                title, name, description);
    }

    /** A non-empty id within its bound. */
    public static String readId(ByteBuf buf) {
        String id = LostTalesPacketCodec.readUtf8String(buf, MAX_ID_BYTES);
        if (!ProgressionState.fitsId(id)) {
            throw new LostTalesPacketCodec.DecodeException("an empty or long id");
        }
        return id;
    }

    public static void writeId(ByteBuf buf, String id) {
        LostTalesPacketCodec.writeUtf8String(buf, id, MAX_ID_BYTES);
    }

    public static double finite(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new LostTalesPacketCodec.DecodeException("a number that is not finite");
        }
        return value;
    }

    /** How many bytes a skill takes on the wire; -1 for one that cannot be written. */
    public static int encodedSize(SkillDefinition skill) {
        ByteBuf buf = Unpooled.buffer();
        try {
            writeSkill(buf, skill);
            return buf.readableBytes();
        } catch (RuntimeException unwritable) {
            return -1;
        } finally {
            buf.release();
        }
    }

    static int readCount(ByteBuf buf, int maximum, String field) {
        return LostTalesPacketCodec.readCount(buf, maximum, field);
    }

    static void writeCount(ByteBuf buf, int count, int maximum, String field) {
        LostTalesPacketCodec.writeCount(buf, count, maximum, field);
    }
}
