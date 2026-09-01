package com.ninuna.losttales.chat.moderation;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent account-keyed chat mute list. Expiry is lazy: an expired
 * entry is dropped by the check that finds it, so no tick ever scans the
 * list. A store made read-only by newer-version data answers as if
 * nobody were muted — chat must keep working — and the mute and unmute
 * commands say why they refuse.
 */
public final class ChatMuteWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_chat_mutes";

    private final Map<UUID, ChatMuteEntry> mutesByAccount =
            new LinkedHashMap<UUID, ChatMuteEntry>();
    private final List<NBTTagCompound> quarantinedEntries =
            new ArrayList<NBTTagCompound>();

    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;

    public ChatMuteWorldData() {
        this(DATA_NAME);
    }

    public ChatMuteWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.mutesByAccount.clear();
        this.quarantinedEntries.clear();
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedNewerData = null;

        ChatMuteNbtCodec.ReadResult result = ChatMuteNbtCodec.read(compound);
        if (result.isReadOnly()) {
            this.readOnlyForNewerVersion = true;
            this.unsupportedDataVersion = result.getUnsupportedVersion();
            this.preservedNewerData = result.getOriginalDataCopy();
            return;
        }
        this.mutesByAccount.putAll(result.getMutes());
        this.quarantinedEntries.addAll(result.getQuarantineEntriesCopy());
        if (result.wasRepaired()) {
            markDirty();
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.readOnlyForNewerVersion && this.preservedNewerData != null) {
            copyTagContents(this.preservedNewerData, compound);
            return;
        }
        ChatMuteNbtCodec.write(compound, this.mutesByAccount.values(),
                this.quarantinedEntries);
    }

    public synchronized boolean isReadOnlyForNewerVersion() {
        return this.readOnlyForNewerVersion;
    }

    public synchronized int getUnsupportedDataVersion() {
        return this.unsupportedDataVersion;
    }

    /**
     * The mute currently silencing an account, or null. Finding an
     * expired one drops it there and then, so the list never needs a
     * sweep; a read-only store silences nobody.
     */
    public synchronized ChatMuteEntry getActiveMute(UUID accountId,
                                                    long nowMillis) {
        if (accountId == null || this.readOnlyForNewerVersion) {
            return null;
        }
        ChatMuteEntry mute = this.mutesByAccount.get(accountId);
        if (mute == null) {
            return null;
        }
        if (mute.isExpired(nowMillis)) {
            this.mutesByAccount.remove(accountId);
            markDirty();
            return null;
        }
        return mute;
    }

    /**
     * Every mute still running, expired ones dropped on the way. The
     * list is a snapshot ordered by when each mute was stored.
     */
    public synchronized List<ChatMuteEntry> getActiveMutes(long nowMillis) {
        if (this.readOnlyForNewerVersion) {
            return Collections.emptyList();
        }
        boolean dropped = false;
        Iterator<ChatMuteEntry> entries =
                this.mutesByAccount.values().iterator();
        while (entries.hasNext()) {
            if (entries.next().isExpired(nowMillis)) {
                entries.remove();
                dropped = true;
            }
        }
        if (dropped) {
            markDirty();
        }
        return Collections.unmodifiableList(
                new ArrayList<ChatMuteEntry>(this.mutesByAccount.values()));
    }

    /**
     * Stores a mute, replacing any the account already has. Refused at
     * the capacity bound: the operator is told rather than an old mute
     * being dropped for a new one.
     */
    public synchronized boolean mute(ChatMuteEntry entry) {
        ensureWritable();
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        if (!this.mutesByAccount.containsKey(entry.getAccountId())
                && this.mutesByAccount.size() >= ChatMuteNbtCodec.MAX_MUTES) {
            return false;
        }
        this.mutesByAccount.put(entry.getAccountId(), entry);
        markDirty();
        return true;
    }

    /** Lifts an account's mute; answers with what was lifted, or null. */
    public synchronized ChatMuteEntry unmute(UUID accountId) {
        ensureWritable();
        ChatMuteEntry removed = accountId == null ? null
                : this.mutesByAccount.remove(accountId);
        if (removed != null) {
            markDirty();
        }
        return removed;
    }

    /**
     * Lifts the mute of the account last known by this name, for lifting
     * a mute after its target left the server. Case-insensitive; null
     * when no stored mute carries the name.
     */
    public synchronized ChatMuteEntry unmuteByName(String accountName) {
        ensureWritable();
        String wanted = accountName == null ? "" : accountName.trim();
        if (wanted.length() == 0) {
            return null;
        }
        for (ChatMuteEntry mute : this.mutesByAccount.values()) {
            if (wanted.equalsIgnoreCase(mute.getAccountName())) {
                return unmute(mute.getAccountId());
            }
        }
        return null;
    }

    public synchronized int getMuteCount() {
        return this.mutesByAccount.size();
    }

    public synchronized int getQuarantinedEntryCount() {
        return this.quarantinedEntries.size();
    }

    private void ensureWritable() {
        if (this.readOnlyForNewerVersion) {
            throw new IllegalStateException(
                    "Chat mute data is read-only because it uses unsupported version "
                            + this.unsupportedDataVersion);
        }
    }

    private static void copyTagContents(NBTTagCompound source,
                                        NBTTagCompound destination) {
        Set<?> keySet = source.func_150296_c();
        for (Object keyObject : keySet) {
            if (!(keyObject instanceof String)) {
                continue;
            }
            String key = (String) keyObject;
            NBTBase value = source.getTag(key);
            if (value != null) {
                destination.setTag(key, value.copy());
            }
        }
    }
}
