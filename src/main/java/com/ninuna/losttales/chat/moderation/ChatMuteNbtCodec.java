package com.ninuna.losttales.chat.moderation;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Versioned NBT codec for the persistent chat mute list. */
public final class ChatMuteNbtCodec {

    public static final int CURRENT_ROOT_DATA_VERSION = 1;
    public static final int CURRENT_QUARANTINE_DATA_VERSION = 1;
    /** Safety bound on stored mutes; entries past it are quarantined. */
    public static final int MAX_MUTES = 1024;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_MUTES = "Mutes";
    private static final String TAG_QUARANTINE = "Quarantine";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_REASON_LABEL = "Reason";
    private static final String TAG_MUTE_INDEX = "MuteIndex";
    private static final String TAG_ORIGINAL_DATA = "OriginalData";
    private static final String TAG_ACCOUNT_UUID = "AccountUUID";
    private static final String TAG_ACCOUNT_NAME = "AccountName";
    private static final String TAG_MUTED_BY = "MutedBy";
    private static final String TAG_MUTE_REASON = "MuteReason";
    private static final String TAG_ISSUED_AT = "IssuedAt";
    private static final String TAG_EXPIRES_AT = "ExpiresAt";

    private ChatMuteNbtCodec() {}

    public static void write(NBTTagCompound output,
                             Collection<ChatMuteEntry> mutes,
                             Collection<NBTTagCompound> quarantinedEntries) {
        output.setInteger(TAG_DATA_VERSION, CURRENT_ROOT_DATA_VERSION);
        ArrayList<ChatMuteEntry> ordered = new ArrayList<ChatMuteEntry>();
        if (mutes != null) {
            ordered.addAll(mutes);
        }
        Collections.sort(ordered, MUTE_ORDER);
        NBTTagList list = new NBTTagList();
        for (ChatMuteEntry mute : ordered) {
            if (mute != null) {
                list.appendTag(writeMute(mute));
            }
        }
        output.setTag(TAG_MUTES, list);
        output.setTag(TAG_QUARANTINE, writeQuarantine(quarantinedEntries));
    }

    public static ReadResult read(NBTTagCompound source) {
        NBTTagCompound safeSource = source == null ? new NBTTagCompound() : source;
        int version = safeSource.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? safeSource.getInteger(TAG_DATA_VERSION) : 0;
        if (version > CURRENT_ROOT_DATA_VERSION || version < 0) {
            warn("Chat mute data uses unsupported version %d; data will remain read-only",
                    Integer.valueOf(version));
            return ReadResult.unsupported(safeSource, version);
        }
        if (safeSource.hasKey(TAG_MUTES)
                && !safeSource.hasKey(TAG_MUTES, Constants.NBT.TAG_LIST)) {
            return ReadResult.unsupported(safeSource, -1);
        }

        boolean repaired = version != CURRENT_ROOT_DATA_VERSION
                || !safeSource.hasKey(TAG_MUTES, Constants.NBT.TAG_LIST);
        QuarantineReadResult quarantine = readQuarantine(safeSource);
        if (!quarantine.supported) {
            return ReadResult.unsupported(safeSource, quarantine.unsupportedVersion);
        }
        repaired |= quarantine.repaired;
        ArrayList<NBTTagCompound> quarantinedEntries =
                new ArrayList<NBTTagCompound>(quarantine.entries);
        LinkedHashMap<UUID, ChatMuteEntry> mutes =
                new LinkedHashMap<UUID, ChatMuteEntry>();
        NBTTagList list = safeSource.getTagList(
                TAG_MUTES, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound raw = list.getCompoundTagAt(index);
            MuteReadResult muteResult = readMute(raw);
            if (muteResult.unsupportedVersion != Integer.MIN_VALUE) {
                return ReadResult.unsupported(
                        safeSource, muteResult.unsupportedVersion);
            }
            if (muteResult.mute == null) {
                quarantinedEntries.add(createQuarantineEntry(
                        muteResult.failureReason, index, raw));
                repaired = true;
                continue;
            }
            UUID accountId = muteResult.mute.getAccountId();
            ChatMuteEntry previous = mutes.get(accountId);
            if (previous != null) {
                ChatMuteEntry retained = previous.getIssuedAtMillis()
                        >= muteResult.mute.getIssuedAtMillis()
                        ? previous : muteResult.mute;
                ChatMuteEntry discarded = retained == previous
                        ? muteResult.mute : previous;
                mutes.put(accountId, retained);
                quarantinedEntries.add(createQuarantineEntry(
                        "duplicate_account_mute", index,
                        writeMute(discarded)));
                repaired = true;
            } else if (mutes.size() >= MAX_MUTES) {
                quarantinedEntries.add(createQuarantineEntry(
                        "over_capacity", index, raw));
                repaired = true;
            } else {
                mutes.put(accountId, muteResult.mute);
            }
            repaired |= muteResult.repaired;
        }
        return ReadResult.success(mutes, repaired, quarantinedEntries);
    }

    private static NBTTagCompound writeMute(ChatMuteEntry mute) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, ChatMuteEntry.CURRENT_DATA_VERSION);
        writeUuid(tag, TAG_ACCOUNT_UUID, mute.getAccountId());
        tag.setString(TAG_ACCOUNT_NAME, mute.getAccountName());
        tag.setString(TAG_MUTED_BY, mute.getMutedByName());
        tag.setString(TAG_MUTE_REASON, mute.getReason());
        tag.setLong(TAG_ISSUED_AT, mute.getIssuedAtMillis());
        tag.setLong(TAG_EXPIRES_AT, mute.getExpiresAtMillis());
        return tag;
    }

    private static MuteReadResult readMute(NBTTagCompound source) {
        if (source == null) {
            return MuteReadResult.failed("missing_mute");
        }
        int version = source.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? source.getInteger(TAG_DATA_VERSION) : 0;
        if (version > ChatMuteEntry.CURRENT_DATA_VERSION || version < 0) {
            return MuteReadResult.unsupported(version);
        }
        UUID accountId = readUuid(source, TAG_ACCOUNT_UUID);
        if (accountId == null) {
            return MuteReadResult.failed("missing_account");
        }
        long expiresAt = source.hasKey(TAG_EXPIRES_AT, Constants.NBT.TAG_LONG)
                ? source.getLong(TAG_EXPIRES_AT) : ChatMuteEntry.EXPIRES_NEVER;
        if (expiresAt < 0L) {
            return MuteReadResult.failed("invalid_expiry");
        }
        long issuedAt = source.hasKey(TAG_ISSUED_AT, Constants.NBT.TAG_LONG)
                ? source.getLong(TAG_ISSUED_AT) : 0L;
        try {
            return MuteReadResult.success(new ChatMuteEntry(
                    accountId,
                    source.getString(TAG_ACCOUNT_NAME),
                    source.getString(TAG_MUTED_BY),
                    source.getString(TAG_MUTE_REASON),
                    issuedAt,
                    expiresAt),
                    version != ChatMuteEntry.CURRENT_DATA_VERSION
                            || !source.hasKey(TAG_ISSUED_AT,
                            Constants.NBT.TAG_LONG));
        } catch (IllegalArgumentException exception) {
            return MuteReadResult.failed("invalid_mute_data");
        }
    }

    private static NBTTagCompound writeQuarantine(
            Collection<NBTTagCompound> entries) {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger(TAG_DATA_VERSION, CURRENT_QUARANTINE_DATA_VERSION);
        NBTTagList list = new NBTTagList();
        if (entries != null) {
            for (NBTTagCompound entry : entries) {
                if (entry != null) {
                    list.appendTag(entry.copy());
                }
            }
        }
        root.setTag(TAG_ENTRIES, list);
        return root;
    }

    private static QuarantineReadResult readQuarantine(NBTTagCompound source) {
        if (!source.hasKey(TAG_QUARANTINE)) {
            return QuarantineReadResult.success(
                    Collections.<NBTTagCompound>emptyList(), true);
        }
        if (!source.hasKey(TAG_QUARANTINE, Constants.NBT.TAG_COMPOUND)) {
            return QuarantineReadResult.unsupported(-1);
        }
        NBTTagCompound root = source.getCompoundTag(TAG_QUARANTINE);
        int version = root.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? root.getInteger(TAG_DATA_VERSION) : 0;
        if (version > CURRENT_QUARANTINE_DATA_VERSION || version < 0) {
            return QuarantineReadResult.unsupported(version);
        }
        if (root.hasKey(TAG_ENTRIES)
                && !root.hasKey(TAG_ENTRIES, Constants.NBT.TAG_LIST)) {
            return QuarantineReadResult.unsupported(-1);
        }
        ArrayList<NBTTagCompound> entries = new ArrayList<NBTTagCompound>();
        NBTTagList list = root.getTagList(
                TAG_ENTRIES, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            entries.add((NBTTagCompound) list.getCompoundTagAt(index).copy());
        }
        return QuarantineReadResult.success(entries,
                version != CURRENT_QUARANTINE_DATA_VERSION
                        || !root.hasKey(TAG_ENTRIES,
                        Constants.NBT.TAG_LIST));
    }

    private static NBTTagCompound createQuarantineEntry(String reason,
                                                        int muteIndex,
                                                        NBTTagCompound original) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(TAG_REASON_LABEL, reason == null ? "unknown" : reason);
        entry.setInteger(TAG_MUTE_INDEX, muteIndex);
        if (original != null) {
            entry.setTag(TAG_ORIGINAL_DATA, original.copy());
        }
        return entry;
    }

    private static void writeUuid(NBTTagCompound tag, String key, UUID value) {
        tag.setLong(key + "Most", value.getMostSignificantBits());
        tag.setLong(key + "Least", value.getLeastSignificantBits());
    }

    private static UUID readUuid(NBTTagCompound tag, String key) {
        String most = key + "Most";
        String least = key + "Least";
        if (!tag.hasKey(most, Constants.NBT.TAG_LONG)
                || !tag.hasKey(least, Constants.NBT.TAG_LONG)) {
            return null;
        }
        return new UUID(tag.getLong(most), tag.getLong(least));
    }

    private static void warn(String format, Object... args) {
        try {
            FMLLog.warning("[%s] " + format, prependModId(args));
        } catch (RuntimeException ignored) {
            // FML's logger is not bootstrapped in isolated codec unit tests.
        }
    }

    private static Object[] prependModId(Object[] args) {
        Object[] values = new Object[(args == null ? 0 : args.length) + 1];
        values[0] = LostTalesMetaData.MOD_ID;
        if (args != null) {
            System.arraycopy(args, 0, values, 1, args.length);
        }
        return values;
    }

    private static final Comparator<ChatMuteEntry> MUTE_ORDER =
            new Comparator<ChatMuteEntry>() {
                @Override
                public int compare(ChatMuteEntry left, ChatMuteEntry right) {
                    return left.getAccountId().toString().compareTo(
                            right.getAccountId().toString());
                }
            };

    public static final class ReadResult {
        private final Map<UUID, ChatMuteEntry> mutes;
        private final boolean repaired;
        private final List<NBTTagCompound> quarantineEntries;
        private final boolean readOnly;
        private final int unsupportedVersion;
        private final NBTTagCompound originalData;

        private ReadResult(Map<UUID, ChatMuteEntry> mutes,
                           boolean repaired,
                           List<NBTTagCompound> quarantineEntries,
                           boolean readOnly,
                           int unsupportedVersion,
                           NBTTagCompound originalData) {
            this.mutes = Collections.unmodifiableMap(
                    new LinkedHashMap<UUID, ChatMuteEntry>(mutes));
            this.repaired = repaired;
            this.quarantineEntries = Collections.unmodifiableList(
                    new ArrayList<NBTTagCompound>(quarantineEntries));
            this.readOnly = readOnly;
            this.unsupportedVersion = unsupportedVersion;
            this.originalData = originalData;
        }

        private static ReadResult success(Map<UUID, ChatMuteEntry> mutes,
                                          boolean repaired,
                                          List<NBTTagCompound> quarantineEntries) {
            return new ReadResult(mutes, repaired, quarantineEntries,
                    false, -1, null);
        }

        private static ReadResult unsupported(NBTTagCompound original,
                                              int version) {
            return new ReadResult(
                    Collections.<UUID, ChatMuteEntry>emptyMap(),
                    false,
                    Collections.<NBTTagCompound>emptyList(),
                    true,
                    version,
                    original == null ? new NBTTagCompound()
                            : (NBTTagCompound) original.copy());
        }

        public Map<UUID, ChatMuteEntry> getMutes() {
            return this.mutes;
        }

        public boolean wasRepaired() {
            return this.repaired;
        }

        public List<NBTTagCompound> getQuarantineEntriesCopy() {
            ArrayList<NBTTagCompound> copies = new ArrayList<NBTTagCompound>();
            for (NBTTagCompound entry : this.quarantineEntries) {
                copies.add((NBTTagCompound) entry.copy());
            }
            return Collections.unmodifiableList(copies);
        }

        public boolean isReadOnly() {
            return this.readOnly;
        }

        public int getUnsupportedVersion() {
            return this.unsupportedVersion;
        }

        public NBTTagCompound getOriginalDataCopy() {
            return this.originalData == null ? null
                    : (NBTTagCompound) this.originalData.copy();
        }
    }

    private static final class MuteReadResult {
        private final ChatMuteEntry mute;
        private final boolean repaired;
        private final String failureReason;
        private final int unsupportedVersion;

        private MuteReadResult(ChatMuteEntry mute, boolean repaired,
                               String failureReason, int unsupportedVersion) {
            this.mute = mute;
            this.repaired = repaired;
            this.failureReason = failureReason;
            this.unsupportedVersion = unsupportedVersion;
        }

        private static MuteReadResult success(ChatMuteEntry mute,
                                              boolean repaired) {
            return new MuteReadResult(mute, repaired, null, Integer.MIN_VALUE);
        }

        private static MuteReadResult failed(String reason) {
            return new MuteReadResult(null, false, reason, Integer.MIN_VALUE);
        }

        private static MuteReadResult unsupported(int version) {
            return new MuteReadResult(null, false, null, version);
        }
    }

    private static final class QuarantineReadResult {
        private final List<NBTTagCompound> entries;
        private final boolean repaired;
        private final boolean supported;
        private final int unsupportedVersion;

        private QuarantineReadResult(List<NBTTagCompound> entries,
                                     boolean repaired,
                                     boolean supported,
                                     int unsupportedVersion) {
            this.entries = entries;
            this.repaired = repaired;
            this.supported = supported;
            this.unsupportedVersion = unsupportedVersion;
        }

        private static QuarantineReadResult success(
                List<NBTTagCompound> entries, boolean repaired) {
            return new QuarantineReadResult(entries, repaired, true, -1);
        }

        private static QuarantineReadResult unsupported(int version) {
            return new QuarantineReadResult(
                    Collections.<NBTTagCompound>emptyList(),
                    false, false, version);
        }
    }
}
