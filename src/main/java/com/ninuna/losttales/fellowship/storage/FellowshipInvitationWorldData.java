package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.fellowship.model.FellowshipInvitation;
import com.ninuna.losttales.storage.NbtTags;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent server-owned collection of pending fellowship invitations. */
public final class FellowshipInvitationWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_fellowship_invitations";

    private final Map<UUID, FellowshipInvitation> invitations =
            new LinkedHashMap<UUID, FellowshipInvitation>();
    private final Map<UUID, LinkedHashSet<UUID>> invitationIdsByFellowship =
            new LinkedHashMap<UUID, LinkedHashSet<UUID>>();
    private final Map<UUID, LinkedHashSet<UUID>> invitationIdsByTargetCharacter =
            new LinkedHashMap<UUID, LinkedHashSet<UUID>>();
    private final List<NBTTagCompound> quarantinedEntries =
            new ArrayList<NBTTagCompound>();

    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;

    public FellowshipInvitationWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.invitations.clear();
        this.invitationIdsByFellowship.clear();
        this.invitationIdsByTargetCharacter.clear();
        this.quarantinedEntries.clear();
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedNewerData = null;

        FellowshipInvitationNbtCodec.ReadResult result =
                FellowshipInvitationNbtCodec.read(compound);
        if (result.isReadOnly()) {
            this.readOnlyForNewerVersion = true;
            this.unsupportedDataVersion = result.getUnsupportedVersion();
            this.preservedNewerData = result.getOriginalDataCopy();
            return;
        }

        this.invitations.putAll(result.getInvitations());
        this.quarantinedEntries.addAll(
                result.getQuarantineEntriesCopy());
        boolean repaired = rebuildIndexesAndRepairDuplicates();
        if (result.wasRepaired() || repaired) {
            markDirty();
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.readOnlyForNewerVersion
                && this.preservedNewerData != null) {
            NbtTags.copyContents(this.preservedNewerData, compound);
            return;
        }
        FellowshipInvitationNbtCodec.write(
                compound,
                this.invitations.values(),
                this.quarantinedEntries);
    }

    public synchronized boolean isReadOnlyForNewerVersion() {
        return this.readOnlyForNewerVersion;
    }

    public synchronized FellowshipInvitation getInvitation(UUID invitationId) {
        return invitationId == null ? null : this.invitations.get(invitationId);
    }

    public synchronized boolean containsInvitation(UUID invitationId) {
        return invitationId != null && this.invitations.containsKey(invitationId);
    }

    public synchronized boolean hasInvitationForFellowshipAndTarget(
            UUID fellowshipId, UUID targetIdentityId) {
        if (fellowshipId == null || targetIdentityId == null) {
            return false;
        }
        LinkedHashSet<UUID> invitationIds =
                this.invitationIdsByFellowship.get(fellowshipId);
        if (invitationIds == null) {
            return false;
        }
        for (UUID invitationId : invitationIds) {
            FellowshipInvitation invitation = this.invitations.get(invitationId);
            if (invitation != null
                    && targetIdentityId.equals(
                    invitation.getTargetIdentityId())) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<FellowshipInvitation> getInvitationsForFellowship(
            UUID fellowshipId) {
        return getByIds(this.invitationIdsByFellowship.get(fellowshipId));
    }

    public synchronized List<FellowshipInvitation> getInvitationsForTargetIdentity(
            UUID identityId) {
        return getByIds(this.invitationIdsByTargetCharacter.get(identityId));
    }

    public synchronized Collection<FellowshipInvitation> getInvitations() {
        ArrayList<FellowshipInvitation> copy =
                new ArrayList<FellowshipInvitation>(this.invitations.values());
        Collections.sort(copy, INVITATION_ORDER);
        return Collections.unmodifiableList(copy);
    }

    public synchronized int getInvitationCount() {
        return this.invitations.size();
    }

    public synchronized void saveInvitation(FellowshipInvitation invitation) {
        ensureWritable();
        validateInvitation(invitation);

        FellowshipInvitation existing =
                this.invitations.get(invitation.getInvitationId());
        if (existing != null) {
            removeFromIndexes(existing);
        }
        if (hasInvitationForFellowshipAndTarget(
                invitation.getFellowshipId(),
                invitation.getTargetIdentityId())) {
            if (existing != null) {
                addToIndexes(existing);
            }
            throw new IllegalStateException(
                    "Target character already has an invitation from this fellowship");
        }

        this.invitations.put(invitation.getInvitationId(), invitation);
        addToIndexes(invitation);
        markDirty();
    }

    public synchronized FellowshipInvitation removeInvitation(UUID invitationId) {
        ensureWritable();
        FellowshipInvitation removed = invitationId == null
                ? null : this.invitations.remove(invitationId);
        if (removed != null) {
            removeFromIndexes(removed);
            markDirty();
        }
        return removed;
    }

    public synchronized int removeInvitationsForFellowship(UUID fellowshipId) {
        ensureWritable();
        return removeByIds(copyIds(this.invitationIdsByFellowship.get(fellowshipId)));
    }

    public synchronized int removeInvitationsForTargetIdentity(
            UUID identityId) {
        ensureWritable();
        return removeByIds(copyIds(
                this.invitationIdsByTargetCharacter.get(identityId)));
    }

    /** The invitations a fellowship sent to one identity: what joining it answers. */
    public synchronized int removeInvitationsForFellowshipAndTarget(
            UUID fellowshipId, UUID targetIdentityId) {
        ensureWritable();
        LinkedHashSet<UUID> toRemove = new LinkedHashSet<UUID>();
        for (FellowshipInvitation invitation : getInvitationsForFellowship(fellowshipId)) {
            if (invitation.getTargetIdentityId().equals(targetIdentityId)) {
                toRemove.add(invitation.getInvitationId());
            }
        }
        return removeByIds(toRemove);
    }

    /** The invitations one member sent for a fellowship: what leaving it takes back. */
    public synchronized int removeInvitationsSentBy(UUID fellowshipId,
                                                    UUID invitingIdentityId) {
        ensureWritable();
        LinkedHashSet<UUID> toRemove = new LinkedHashSet<UUID>();
        for (FellowshipInvitation invitation : getInvitationsForFellowship(fellowshipId)) {
            if (invitation.getInvitingIdentityId().equals(invitingIdentityId)) {
                toRemove.add(invitation.getInvitationId());
            }
        }
        return removeByIds(toRemove);
    }

    public synchronized int removeInvitationsInvolvingIdentity(
            UUID identityId) {
        ensureWritable();
        if (identityId == null) {
            return 0;
        }
        LinkedHashSet<UUID> toRemove = new LinkedHashSet<UUID>();
        LinkedHashSet<UUID> targeted =
                this.invitationIdsByTargetCharacter.get(identityId);
        if (targeted != null) {
            toRemove.addAll(targeted);
        }
        for (FellowshipInvitation invitation : this.invitations.values()) {
            if (identityId.equals(invitation.getInvitingIdentityId())) {
                toRemove.add(invitation.getInvitationId());
            }
        }
        return removeByIds(toRemove);
    }

    public synchronized int removeExpired(long now) {
        ensureWritable();
        LinkedHashSet<UUID> toRemove = new LinkedHashSet<UUID>();
        for (FellowshipInvitation invitation : this.invitations.values()) {
            if (invitation.isExpired(now)) {
                toRemove.add(invitation.getInvitationId());
            }
        }
        return removeByIds(toRemove);
    }

    public synchronized void quarantine(String reason,
                                        FellowshipInvitation invitation) {
        ensureWritable();
        this.quarantinedEntries.add(
                FellowshipInvitationNbtCodec.createQuarantineEntry(
                        reason,
                        invitation == null ? null
                                : invitation.getInvitationId(),
                        invitation == null ? null : invitation.getFellowshipId(),
                        invitation == null ? null
                                : invitation.getTargetIdentityId()));
        markDirty();
    }

    public synchronized int getQuarantinedEntryCount() {
        return this.quarantinedEntries.size();
    }

    private boolean rebuildIndexesAndRepairDuplicates() {
        this.invitationIdsByFellowship.clear();
        this.invitationIdsByTargetCharacter.clear();

        ArrayList<FellowshipInvitation> ordered =
                new ArrayList<FellowshipInvitation>(this.invitations.values());
        Collections.sort(ordered, INVITATION_ORDER);
        LinkedHashSet<UUID> duplicateIds = new LinkedHashSet<UUID>();
        LinkedHashSet<FellowshipTargetKey> seenPairs =
                new LinkedHashSet<FellowshipTargetKey>();
        for (FellowshipInvitation invitation : ordered) {
            FellowshipTargetKey key = new FellowshipTargetKey(
                    invitation.getFellowshipId(),
                    invitation.getTargetIdentityId());
            if (!seenPairs.add(key)) {
                duplicateIds.add(invitation.getInvitationId());
                this.quarantinedEntries.add(
                        FellowshipInvitationNbtCodec.createQuarantineEntry(
                                "duplicate_fellowship_target_invitation",
                                invitation.getInvitationId(),
                                invitation.getFellowshipId(),
                                invitation.getTargetIdentityId()));
                continue;
            }
            addToIndexes(invitation);
        }
        for (UUID duplicateId : duplicateIds) {
            this.invitations.remove(duplicateId);
        }
        return !duplicateIds.isEmpty();
    }

    private int removeByIds(Collection<UUID> invitationIds) {
        if (invitationIds == null || invitationIds.isEmpty()) {
            return 0;
        }
        int removedCount = 0;
        for (UUID invitationId : invitationIds) {
            FellowshipInvitation removed = this.invitations.remove(invitationId);
            if (removed != null) {
                removeFromIndexes(removed);
                removedCount++;
            }
        }
        if (removedCount > 0) {
            markDirty();
        }
        return removedCount;
    }

    private List<FellowshipInvitation> getByIds(Collection<UUID> invitationIds) {
        ArrayList<FellowshipInvitation> result = new ArrayList<FellowshipInvitation>();
        if (invitationIds != null) {
            for (UUID invitationId : invitationIds) {
                FellowshipInvitation invitation = this.invitations.get(invitationId);
                if (invitation != null) {
                    result.add(invitation);
                }
            }
        }
        Collections.sort(result, INVITATION_ORDER);
        return Collections.unmodifiableList(result);
    }

    private void addToIndexes(FellowshipInvitation invitation) {
        addIndex(this.invitationIdsByFellowship,
                invitation.getFellowshipId(), invitation.getInvitationId());
        addIndex(this.invitationIdsByTargetCharacter,
                invitation.getTargetIdentityId(),
                invitation.getInvitationId());
    }

    private void removeFromIndexes(FellowshipInvitation invitation) {
        removeIndex(this.invitationIdsByFellowship,
                invitation.getFellowshipId(), invitation.getInvitationId());
        removeIndex(this.invitationIdsByTargetCharacter,
                invitation.getTargetIdentityId(),
                invitation.getInvitationId());
    }

    private static void addIndex(
            Map<UUID, LinkedHashSet<UUID>> index,
            UUID key,
            UUID invitationId) {
        LinkedHashSet<UUID> ids = index.get(key);
        if (ids == null) {
            ids = new LinkedHashSet<UUID>();
            index.put(key, ids);
        }
        ids.add(invitationId);
    }

    private static void removeIndex(
            Map<UUID, LinkedHashSet<UUID>> index,
            UUID key,
            UUID invitationId) {
        LinkedHashSet<UUID> ids = index.get(key);
        if (ids == null) {
            return;
        }
        ids.remove(invitationId);
        if (ids.isEmpty()) {
            index.remove(key);
        }
    }

    private static Collection<UUID> copyIds(Collection<UUID> ids) {
        return ids == null ? Collections.<UUID>emptyList()
                : new ArrayList<UUID>(ids);
    }

    private static void validateInvitation(FellowshipInvitation invitation) {
        if (invitation == null) {
            throw new IllegalArgumentException("invitation must not be null");
        }
        if (invitation.getExpiresAt() <= invitation.getCreatedAt()) {
            throw new IllegalArgumentException(
                    "invitation expiration must follow creation");
        }
    }

    private void ensureWritable() {
        if (this.readOnlyForNewerVersion) {
            throw new IllegalStateException(
                    "Fellowship invitation data is read-only because it uses unsupported version "
                            + this.unsupportedDataVersion);
        }
    }

    private static final Comparator<FellowshipInvitation> INVITATION_ORDER =
            new Comparator<FellowshipInvitation>() {
                @Override
                public int compare(FellowshipInvitation left,
                                   FellowshipInvitation right) {
                    if (left.getCreatedAt() < right.getCreatedAt()) {
                        return -1;
                    }
                    if (left.getCreatedAt() > right.getCreatedAt()) {
                        return 1;
                    }
                    return left.getInvitationId().toString().compareTo(
                            right.getInvitationId().toString());
                }
            };

    private static final class FellowshipTargetKey {
        private final UUID fellowshipId;
        private final UUID targetIdentityId;

        private FellowshipTargetKey(UUID fellowshipId, UUID targetIdentityId) {
            this.fellowshipId = fellowshipId;
            this.targetIdentityId = targetIdentityId;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof FellowshipTargetKey)) {
                return false;
            }
            FellowshipTargetKey other = (FellowshipTargetKey) object;
            return this.fellowshipId.equals(other.fellowshipId)
                    && this.targetIdentityId.equals(other.targetIdentityId);
        }

        @Override
        public int hashCode() {
            return 31 * this.fellowshipId.hashCode()
                    + this.targetIdentityId.hashCode();
        }
    }
}
