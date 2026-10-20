package com.ninuna.losttales.chat.moderation;

import com.ninuna.losttales.storage.NbtQuarantine;
import com.ninuna.losttales.storage.NbtTags;
import com.ninuna.losttales.util.LostTalesLog;
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

/**
 * Versioned NBT codec for the persistent chat mute list. A save or a
 * mute at another version than this codec writes, or a save without its
 * version or its list, is left alone and the store goes read-only; a
 * mute missing a field, a second mute of one account, or one past
 * {@link #MAX_MUTES} is quarantined.
 */
public final class ChatMuteNbtCodec {

    public static final int CURRENT_ROOT_DATA_VERSION = 1;
    /** Safety bound on stored mutes; entries past it are quarantined. */
    public static final int MAX_MUTES = 1024;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_MUTES = "Mutes";
    private static final String TAG_MUTE_INDEX = "MuteIndex";
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
        NbtQuarantine.write(output, quarantinedEntries);
    }

    public static ReadResult read(NBTTagCompound source) {
        NBTTagCompound safeSource = source == null ? new NBTTagCompound() : source;
        if (!safeSource.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)) {
            LostTalesLog.warning("Chat mute data carries no version; data will remain read-only");
            return ReadResult.unsupported(safeSource, -1);
        }
        int version = safeSource.getInteger(TAG_DATA_VERSION);
        if (version != CURRENT_ROOT_DATA_VERSION) {
            LostTalesLog.warning("Chat mute data uses unsupported version %d; data will remain read-only",
                    Integer.valueOf(version));
            return ReadResult.unsupported(safeSource, version);
        }
        if (!safeSource.hasKey(TAG_MUTES, Constants.NBT.TAG_LIST)) {
            LostTalesLog.warning("Chat mute data lacks its list of mutes; data will remain read-only");
            return ReadResult.unsupported(safeSource, -1);
        }

        boolean repaired = false;
        NbtQuarantine.Read quarantine = NbtQuarantine.read(safeSource);
        if (!quarantine.isSupported()) {
            return ReadResult.unsupported(safeSource, quarantine.getUnsupportedVersion());
        }
        ArrayList<NBTTagCompound> quarantinedEntries =
                new ArrayList<NBTTagCompound>(quarantine.getEntries());
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
                quarantinedEntries.add(NbtQuarantine.entry(
                        muteResult.failureReason, TAG_MUTE_INDEX, index, raw));
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
                quarantinedEntries.add(NbtQuarantine.entry(
                        "duplicate_account_mute", TAG_MUTE_INDEX, index,
                        writeMute(discarded)));
                repaired = true;
            } else if (mutes.size() >= MAX_MUTES) {
                quarantinedEntries.add(NbtQuarantine.entry(
                        "over_capacity", TAG_MUTE_INDEX, index, raw));
                repaired = true;
            } else {
                mutes.put(accountId, muteResult.mute);
            }
        }
        return ReadResult.success(mutes, repaired, quarantinedEntries);
    }

    private static NBTTagCompound writeMute(ChatMuteEntry mute) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, ChatMuteEntry.CURRENT_DATA_VERSION);
        NbtTags.writeUuid(tag, TAG_ACCOUNT_UUID, mute.getAccountId());
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
                ? source.getInteger(TAG_DATA_VERSION) : -1;
        if (version != ChatMuteEntry.CURRENT_DATA_VERSION) {
            return MuteReadResult.unsupported(version);
        }
        UUID accountId = NbtTags.readUuid(source, TAG_ACCOUNT_UUID);
        if (accountId == null) {
            return MuteReadResult.failed("missing_account");
        }
        // The writer writes every field: a mute without one is quarantined,
        // never read with a stand-in.
        if (!source.hasKey(TAG_ACCOUNT_NAME, Constants.NBT.TAG_STRING)
                || !source.hasKey(TAG_MUTED_BY, Constants.NBT.TAG_STRING)
                || !source.hasKey(TAG_MUTE_REASON, Constants.NBT.TAG_STRING)
                || !source.hasKey(TAG_ISSUED_AT, Constants.NBT.TAG_LONG)
                || !source.hasKey(TAG_EXPIRES_AT, Constants.NBT.TAG_LONG)) {
            return MuteReadResult.failed("missing_mute_field");
        }
        long expiresAt = source.getLong(TAG_EXPIRES_AT);
        if (expiresAt < 0L) {
            return MuteReadResult.failed("invalid_expiry");
        }
        try {
            return MuteReadResult.success(new ChatMuteEntry(
                    accountId,
                    source.getString(TAG_ACCOUNT_NAME),
                    source.getString(TAG_MUTED_BY),
                    source.getString(TAG_MUTE_REASON),
                    source.getLong(TAG_ISSUED_AT),
                    expiresAt));
        } catch (IllegalArgumentException exception) {
            return MuteReadResult.failed("invalid_mute_data");
        }
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
        private final String failureReason;
        private final int unsupportedVersion;

        private MuteReadResult(ChatMuteEntry mute, String failureReason,
                               int unsupportedVersion) {
            this.mute = mute;
            this.failureReason = failureReason;
            this.unsupportedVersion = unsupportedVersion;
        }

        private static MuteReadResult success(ChatMuteEntry mute) {
            return new MuteReadResult(mute, null, Integer.MIN_VALUE);
        }

        private static MuteReadResult failed(String reason) {
            return new MuteReadResult(null, reason, Integer.MIN_VALUE);
        }

        private static MuteReadResult unsupported(int version) {
            return new MuteReadResult(null, null, version);
        }
    }
}
