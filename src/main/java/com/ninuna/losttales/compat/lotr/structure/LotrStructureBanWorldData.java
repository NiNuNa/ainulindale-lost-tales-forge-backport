package com.ninuna.losttales.compat.lotr.structure;

import com.ninuna.losttales.storage.NbtTags;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The world's structure bans, on accounts and on characters. A store made
 * read-only by data of another version bans nobody, so building keeps
 * working, and the command says why it cannot change anything.
 */
public final class LotrStructureBanWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_structure_bans";

    private final Map<String, LotrStructureBan> bans =
            new LinkedHashMap<String, LotrStructureBan>();
    private final List<NBTTagCompound> quarantined = new ArrayList<NBTTagCompound>();
    private boolean readOnly;
    private int unsupportedVersion = -1;
    private NBTTagCompound preserved;

    public LotrStructureBanWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.bans.clear();
        this.quarantined.clear();
        this.readOnly = false;
        this.unsupportedVersion = -1;
        this.preserved = null;
        LotrStructureBanNbtCodec.ReadResult result = LotrStructureBanNbtCodec.read(compound);
        if (result.isReadOnly()) {
            this.readOnly = true;
            this.unsupportedVersion = result.getUnsupportedVersion();
            this.preserved = result.getOriginalCopy();
            return;
        }
        this.bans.putAll(result.getBans());
        this.quarantined.addAll(result.getQuarantined());
        if (result.wasRepaired()) {
            markDirty();
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.readOnly && this.preserved != null) {
            NbtTags.copyContents(this.preserved, compound);
            return;
        }
        LotrStructureBanNbtCodec.write(compound, this.bans.values(), this.quarantined);
    }

    public synchronized boolean isReadOnly() {
        return this.readOnly;
    }

    public synchronized int getUnsupportedVersion() {
        return this.unsupportedVersion;
    }

    /**
     * Whether the account, or the character it plays (null for none), is
     * banned from spawning structures.
     */
    public synchronized boolean isBanned(UUID account, UUID character) {
        if (this.readOnly) {
            return false;
        }
        return account != null && this.bans.containsKey(LotrStructureBanNbtCodec.keyOf(
                        LotrStructureBan.Kind.ACCOUNT, account))
                || character != null && this.bans.containsKey(LotrStructureBanNbtCodec.keyOf(
                        LotrStructureBan.Kind.CHARACTER, character));
    }

    /** The ban on that account or character, or null. */
    public synchronized LotrStructureBan find(LotrStructureBan.Kind kind, UUID subjectId) {
        return kind == null || subjectId == null ? null
                : this.bans.get(LotrStructureBanNbtCodec.keyOf(kind, subjectId));
    }

    /** Every ban, in the order they were given. */
    public synchronized List<LotrStructureBan> all() {
        return Collections.unmodifiableList(new ArrayList<LotrStructureBan>(this.bans.values()));
    }

    /**
     * Keeps a ban; false, with nothing changed, when that account or
     * character is banned already or the world holds as many bans as it
     * keeps.
     */
    public synchronized boolean ban(LotrStructureBan ban) {
        ensureWritable();
        String key = LotrStructureBanNbtCodec.keyOf(ban.getKind(), ban.getSubjectId());
        if (this.bans.containsKey(key)
                || this.bans.size() >= LotrStructureBanNbtCodec.MAX_BANS) {
            return false;
        }
        this.bans.put(key, ban);
        markDirty();
        return true;
    }

    /** Lifts the ban on that account or character; answers what was lifted, or null. */
    public synchronized LotrStructureBan allow(LotrStructureBan.Kind kind, UUID subjectId) {
        ensureWritable();
        LotrStructureBan lifted = kind == null || subjectId == null ? null
                : this.bans.remove(LotrStructureBanNbtCodec.keyOf(kind, subjectId));
        if (lifted != null) {
            markDirty();
        }
        return lifted;
    }

    private void ensureWritable() {
        if (this.readOnly) {
            throw new IllegalStateException(
                    "Structure ban data is read-only because it uses unsupported version "
                            + this.unsupportedVersion);
        }
    }
}
