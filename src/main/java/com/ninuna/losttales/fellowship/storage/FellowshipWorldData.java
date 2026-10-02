package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.storage.NbtTags;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The fellowships, kept with the world: each fellowship, which fellowships
 * each character is in, the one each character travels with, and the LOTR
 * fellowship that stands behind each of ours.
 */
public final class FellowshipWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_fellowships";

    private final Map<UUID, Fellowship> fellowships = new LinkedHashMap<UUID, Fellowship>();
    /** The fellowships of each identity, in the order it joined them. */
    private final Map<UUID, Set<UUID>> fellowshipIdsByIdentityId =
            new LinkedHashMap<UUID, Set<UUID>>();
    /** The fellowship each identity travels with, where it chose one. */
    private final Map<UUID, UUID> travellingByIdentityId = new LinkedHashMap<UUID, UUID>();
    /** The LOTR fellowship behind each of ours. */
    private final Map<UUID, UUID> mirrorIdByFellowshipId = new LinkedHashMap<UUID, UUID>();
    private final List<NBTTagCompound> quarantinedEntries = new ArrayList<NBTTagCompound>();
    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;
    private transient boolean characterReferencesValidated;

    public FellowshipWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.fellowships.clear();
        this.fellowshipIdsByIdentityId.clear();
        this.travellingByIdentityId.clear();
        this.mirrorIdByFellowshipId.clear();
        this.quarantinedEntries.clear();
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedNewerData = null;
        this.characterReferencesValidated = false;

        FellowshipNbtCodec.ReadResult result = FellowshipNbtCodec.read(compound);
        if (result.isReadOnly()) {
            this.readOnlyForNewerVersion = true;
            this.unsupportedDataVersion = result.getUnsupportedVersion();
            this.preservedNewerData = result.getOriginalDataCopy();
            return;
        }
        this.fellowships.putAll(result.getFellowships());
        this.quarantinedEntries.addAll(result.getQuarantineEntriesCopy());
        rebuildMembershipIndex();
        boolean repaired = result.wasRepaired();
        for (Map.Entry<UUID, UUID> entry : result.getTravelling().entrySet()) {
            if (isMember(entry.getKey(), entry.getValue())) {
                this.travellingByIdentityId.put(entry.getKey(), entry.getValue());
            } else {
                repaired = true;
            }
        }
        // A mirror whose fellowship is gone is kept until LOTR's is ended.
        this.mirrorIdByFellowshipId.putAll(result.getMirrors());
        if (repaired) {
            markDirty();
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.readOnlyForNewerVersion && this.preservedNewerData != null) {
            NbtTags.copyContents(this.preservedNewerData, compound);
            return;
        }
        FellowshipNbtCodec.write(compound, this.fellowships.values(),
                this.travellingByIdentityId, this.mirrorIdByFellowshipId,
                this.quarantinedEntries);
    }

    public synchronized boolean isReadOnlyForNewerVersion() {
        return this.readOnlyForNewerVersion;
    }

    public synchronized Fellowship getFellowship(UUID fellowshipId) {
        return fellowshipId == null ? null : this.fellowships.get(fellowshipId);
    }

    /** The fellowships an identity is in, in the order it joined them. */
    public synchronized List<Fellowship> getFellowshipsForIdentity(UUID identityId) {
        Set<UUID> ids = identityId == null ? null
                : this.fellowshipIdsByIdentityId.get(identityId);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Fellowship> found = new ArrayList<Fellowship>(ids.size());
        for (UUID id : ids) {
            Fellowship fellowship = this.fellowships.get(id);
            if (fellowship != null) {
                found.add(fellowship);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /**
     * The fellowship an identity travels with: the one it chose, else the
     * first it joined; null while it is in none. The HUD, the compass, the
     * go-here marker and shared quest credit follow it.
     */
    public synchronized Fellowship getTravellingFellowship(UUID identityId) {
        UUID chosen = identityId == null ? null
                : this.travellingByIdentityId.get(identityId);
        if (chosen != null && isMember(identityId, chosen)) {
            return this.fellowships.get(chosen);
        }
        List<Fellowship> joined = getFellowshipsForIdentity(identityId);
        return joined.isEmpty() ? null : joined.get(0);
    }

    /** Lets an identity travel with one of its fellowships; false when it is no member of it. */
    public synchronized boolean setTravelling(UUID identityId, UUID fellowshipId) {
        ensureWritable();
        if (!isMember(identityId, fellowshipId)) {
            return false;
        }
        UUID before = this.travellingByIdentityId.put(identityId, fellowshipId);
        if (!fellowshipId.equals(before)) {
            markDirty();
        }
        return true;
    }

    /** Whether the identity is a member of the fellowship. */
    public synchronized boolean isMember(UUID identityId, UUID fellowshipId) {
        Fellowship fellowship = getFellowship(fellowshipId);
        return fellowship != null && fellowship.containsMember(identityId);
    }

    public synchronized boolean containsFellowship(UUID fellowshipId) {
        return fellowshipId != null && this.fellowships.containsKey(fellowshipId);
    }

    public synchronized Collection<Fellowship> getFellowships() {
        return Collections.unmodifiableCollection(
                new ArrayList<Fellowship>(this.fellowships.values()));
    }

    public synchronized int getFellowshipCount() {
        return this.fellowships.size();
    }

    /** The LOTR fellowship behind one of ours; null while it has none yet. */
    public synchronized UUID getMirrorId(UUID fellowshipId) {
        return fellowshipId == null ? null : this.mirrorIdByFellowshipId.get(fellowshipId);
    }

    /**
     * The LOTR fellowships whose fellowship of ours is gone, by the id ours
     * had: each still to be ended in LOTR, then forgotten.
     */
    public synchronized Map<UUID, UUID> getEndedMirrors() {
        Map<UUID, UUID> ended = new LinkedHashMap<UUID, UUID>();
        for (Map.Entry<UUID, UUID> entry : this.mirrorIdByFellowshipId.entrySet()) {
            if (!this.fellowships.containsKey(entry.getKey())) {
                ended.put(entry.getKey(), entry.getValue());
            }
        }
        return ended;
    }

    /** Forgets the LOTR fellowship behind one of ours once LOTR's is ended. */
    public synchronized void forgetMirror(UUID fellowshipId) {
        ensureWritable();
        if (fellowshipId != null && this.mirrorIdByFellowshipId.remove(fellowshipId) != null) {
            markDirty();
        }
    }

    /** Remembers the LOTR fellowship behind one of ours. */
    public synchronized void setMirrorId(UUID fellowshipId, UUID mirrorId) {
        ensureWritable();
        if (fellowshipId == null || mirrorId == null
                || !this.fellowships.containsKey(fellowshipId)) {
            return;
        }
        if (!mirrorId.equals(this.mirrorIdByFellowshipId.put(fellowshipId, mirrorId))) {
            markDirty();
        }
    }

    public synchronized void saveFellowship(Fellowship fellowship) {
        ensureWritable();
        validateFellowship(fellowship);
        UUID fellowshipId = fellowship.getFellowshipId();
        this.fellowships.put(fellowshipId, fellowship);
        // Members who left lose the fellowship from their list; new ones
        // gain it at the end of theirs.
        Iterator<Map.Entry<UUID, Set<UUID>>> entries =
                this.fellowshipIdsByIdentityId.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Set<UUID>> entry = entries.next();
            if (!fellowship.containsMember(entry.getKey())
                    && entry.getValue().remove(fellowshipId)) {
                forgetTravelling(entry.getKey(), fellowshipId);
                if (entry.getValue().isEmpty()) {
                    entries.remove();
                }
            }
        }
        for (FellowshipMember member : fellowship.getMembers()) {
            indexOf(member.getIdentityId()).add(fellowshipId);
        }
        markDirty();
    }

    public synchronized Fellowship removeFellowship(UUID fellowshipId) {
        ensureWritable();
        Fellowship removed = fellowshipId == null ? null : this.fellowships.remove(fellowshipId);
        if (removed != null) {
            Iterator<Map.Entry<UUID, Set<UUID>>> entries =
                    this.fellowshipIdsByIdentityId.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<UUID, Set<UUID>> entry = entries.next();
                if (entry.getValue().remove(fellowshipId)) {
                    forgetTravelling(entry.getKey(), fellowshipId);
                    if (entry.getValue().isEmpty()) {
                        entries.remove();
                    }
                }
            }
            // Its mirror stays named until LOTR's fellowship is ended.
            markDirty();
        }
        return removed;
    }

    public synchronized void quarantine(String reason, UUID fellowshipId, UUID identityId) {
        ensureWritable();
        this.quarantinedEntries.add(
                FellowshipNbtCodec.createQuarantineEntry(reason, fellowshipId, identityId));
        markDirty();
    }

    public synchronized int getQuarantinedEntryCount() {
        return this.quarantinedEntries.size();
    }

    public synchronized boolean areCharacterReferencesValidated() {
        return this.characterReferencesValidated;
    }

    public synchronized void markCharacterReferencesValidated() {
        this.characterReferencesValidated = true;
    }

    private void forgetTravelling(UUID identityId, UUID fellowshipId) {
        if (fellowshipId.equals(this.travellingByIdentityId.get(identityId))) {
            this.travellingByIdentityId.remove(identityId);
        }
    }

    private Set<UUID> indexOf(UUID identityId) {
        Set<UUID> ids = this.fellowshipIdsByIdentityId.get(identityId);
        if (ids == null) {
            ids = new LinkedHashSet<UUID>();
            this.fellowshipIdsByIdentityId.put(identityId, ids);
        }
        return ids;
    }

    /** Files every member under its fellowships, in the order each joined them. */
    private void rebuildMembershipIndex() {
        List<Object[]> joins = new ArrayList<Object[]>();
        for (Fellowship fellowship : this.fellowships.values()) {
            for (FellowshipMember member : fellowship.getMembers()) {
                joins.add(new Object[] {member, fellowship.getFellowshipId()});
            }
        }
        Collections.sort(joins, new java.util.Comparator<Object[]>() {
            @Override
            public int compare(Object[] left, Object[] right) {
                long a = ((FellowshipMember)left[0]).getJoinedAt();
                long b = ((FellowshipMember)right[0]).getJoinedAt();
                return a < b ? -1 : a > b ? 1 : 0;
            }
        });
        for (Object[] join : joins) {
            indexOf(((FellowshipMember)join[0]).getIdentityId()).add((UUID)join[1]);
        }
    }

    private void validateFellowship(Fellowship fellowship) {
        if (fellowship == null) {
            throw new IllegalArgumentException("fellowship must not be null");
        }
        if (fellowship.getMemberCount() <= 0 || fellowship.getMemberCount() > Fellowship.MAX_MEMBERS) {
            throw new IllegalArgumentException("fellowship member count is invalid");
        }
        if (!fellowship.hasValidLeader()) {
            throw new IllegalArgumentException("fellowship leader must be a member");
        }
    }

    private void ensureWritable() {
        if (this.readOnlyForNewerVersion) {
            throw new IllegalStateException(
                    "Fellowship data is read-only because it uses unsupported version "
                            + this.unsupportedDataVersion);
        }
    }
}
