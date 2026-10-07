package com.ninuna.losttales.compat.lotr.structure;

import com.ninuna.losttales.storage.NbtQuarantine;
import com.ninuna.losttales.storage.NbtTags;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The structure bans as the world saves them. Only this build's version
 * is read; any other is kept as it is and the store goes read-only. A ban
 * that cannot be read, a second ban on the same account or character and
 * any past {@link #MAX_BANS} are set aside in the quarantine, never
 * dropped.
 */
public final class LotrStructureBanNbtCodec {
    public static final int CURRENT_DATA_VERSION = 1;
    /** The most bans a world keeps. */
    public static final int MAX_BANS = 4096;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_BANS = "Bans";
    private static final String TAG_BAN_INDEX = "BanIndex";
    private static final String TAG_KIND = "Kind";
    private static final String TAG_SUBJECT = "SubjectUUID";
    private static final String TAG_NAME = "Name";
    private static final String TAG_BANNED_BY = "BannedBy";
    private static final String TAG_BANNED_AT = "BannedAt";

    private LotrStructureBanNbtCodec() {}

    /** The key a ban is held under: one ban per account or character. */
    static String keyOf(LotrStructureBan.Kind kind, UUID subjectId) {
        return kind.id() + ':' + subjectId;
    }

    public static void write(NBTTagCompound output, Collection<LotrStructureBan> bans,
                             Collection<NBTTagCompound> quarantined) {
        output.setInteger(TAG_DATA_VERSION, CURRENT_DATA_VERSION);
        NBTTagList list = new NBTTagList();
        if (bans != null) {
            for (LotrStructureBan ban : bans) {
                if (ban != null) {
                    list.appendTag(writeBan(ban));
                }
            }
        }
        output.setTag(TAG_BANS, list);
        NbtQuarantine.write(output, quarantined);
    }

    public static ReadResult read(NBTTagCompound source) {
        NBTTagCompound root = source == null ? new NBTTagCompound() : source;
        int version = root.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? root.getInteger(TAG_DATA_VERSION) : 0;
        if (version != CURRENT_DATA_VERSION
                || !root.hasKey(TAG_BANS, Constants.NBT.TAG_LIST)) {
            LostTalesLog.warning("Structure ban data uses unsupported version %d; data will remain read-only",
                    Integer.valueOf(version));
            return ReadResult.unsupported(root, version);
        }
        NbtQuarantine.Read quarantine = NbtQuarantine.readCurrentVersionOnly(root);
        if (!quarantine.isSupported()) {
            return ReadResult.unsupported(root, quarantine.getUnsupportedVersion());
        }
        boolean repaired = quarantine.isRepaired();
        List<NBTTagCompound> quarantined =
                new ArrayList<NBTTagCompound>(quarantine.getEntries());
        Map<String, LotrStructureBan> bans = new LinkedHashMap<String, LotrStructureBan>();
        NBTTagList list = root.getTagList(TAG_BANS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound raw = list.getCompoundTagAt(index);
            LotrStructureBan ban = readBan(raw);
            String reason = null;
            if (ban == null) {
                reason = "malformed_ban";
            } else if (bans.containsKey(keyOf(ban.getKind(), ban.getSubjectId()))) {
                reason = "duplicate_ban";
            } else if (bans.size() >= MAX_BANS) {
                reason = "over_capacity";
            }
            if (reason != null) {
                quarantined.add(NbtQuarantine.entry(reason, TAG_BAN_INDEX, index, raw));
                repaired = true;
                continue;
            }
            bans.put(keyOf(ban.getKind(), ban.getSubjectId()), ban);
        }
        return ReadResult.success(bans, quarantined, repaired);
    }

    private static NBTTagCompound writeBan(LotrStructureBan ban) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(TAG_KIND, ban.getKind().id());
        NbtTags.writeUuid(tag, TAG_SUBJECT, ban.getSubjectId());
        tag.setString(TAG_NAME, ban.getName());
        tag.setString(TAG_BANNED_BY, ban.getBannedBy());
        tag.setLong(TAG_BANNED_AT, ban.getBannedAtMillis());
        return tag;
    }

    /** The ban a record holds, or null for one that is not whole. */
    private static LotrStructureBan readBan(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(TAG_KIND, Constants.NBT.TAG_STRING)
                || !tag.hasKey(TAG_NAME, Constants.NBT.TAG_STRING)
                || !tag.hasKey(TAG_BANNED_BY, Constants.NBT.TAG_STRING)
                || !tag.hasKey(TAG_BANNED_AT, Constants.NBT.TAG_LONG)) {
            return null;
        }
        LotrStructureBan.Kind kind = LotrStructureBan.Kind.byId(tag.getString(TAG_KIND));
        UUID subject = NbtTags.readUuid(tag, TAG_SUBJECT);
        String name = tag.getString(TAG_NAME);
        String bannedBy = tag.getString(TAG_BANNED_BY);
        long bannedAt = tag.getLong(TAG_BANNED_AT);
        if (kind == null || subject == null || bannedAt < 0L
                || name.length() > LotrStructureBan.MAX_NAME_LENGTH
                || bannedBy.length() > LotrStructureBan.MAX_NAME_LENGTH) {
            return null;
        }
        return new LotrStructureBan(kind, subject, name, bannedBy, bannedAt);
    }

    /** What the save read as: the bans and the quarantine, or the whole of it kept read-only. */
    public static final class ReadResult {
        private final Map<String, LotrStructureBan> bans;
        private final List<NBTTagCompound> quarantined;
        private final boolean repaired;
        private final boolean readOnly;
        private final int unsupportedVersion;
        private final NBTTagCompound original;

        private ReadResult(Map<String, LotrStructureBan> bans,
                           List<NBTTagCompound> quarantined, boolean repaired,
                           boolean readOnly, int unsupportedVersion,
                           NBTTagCompound original) {
            this.bans = Collections.unmodifiableMap(
                    new LinkedHashMap<String, LotrStructureBan>(bans));
            this.quarantined = Collections.unmodifiableList(
                    new ArrayList<NBTTagCompound>(quarantined));
            this.repaired = repaired;
            this.readOnly = readOnly;
            this.unsupportedVersion = unsupportedVersion;
            this.original = original;
        }

        static ReadResult success(Map<String, LotrStructureBan> bans,
                                  List<NBTTagCompound> quarantined, boolean repaired) {
            return new ReadResult(bans, quarantined, repaired, false, -1, null);
        }

        static ReadResult unsupported(NBTTagCompound original, int version) {
            return new ReadResult(Collections.<String, LotrStructureBan>emptyMap(),
                    Collections.<NBTTagCompound>emptyList(), false, true, version,
                    (NBTTagCompound) original.copy());
        }

        public Map<String, LotrStructureBan> getBans() { return this.bans; }
        public List<NBTTagCompound> getQuarantined() { return this.quarantined; }
        public boolean wasRepaired() { return this.repaired; }
        public boolean isReadOnly() { return this.readOnly; }
        public int getUnsupportedVersion() { return this.unsupportedVersion; }

        public NBTTagCompound getOriginalCopy() {
            return this.original == null ? null : (NBTTagCompound) this.original.copy();
        }
    }
}
