package com.ninuna.losttales.party.storage;

import com.ninuna.losttales.party.model.PartyInvitation;
import net.minecraft.nbt.NBTBase;
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
import java.util.Set;
import java.util.UUID;

/** Persistent server-owned collection of pending party invitations. */
public final class PartyInvitationWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_party_invitations";

    private final Map<UUID, PartyInvitation> invitations =
            new LinkedHashMap<UUID, PartyInvitation>();
    private final Map<UUID, LinkedHashSet<UUID>> invitationIdsByParty =
            new LinkedHashMap<UUID, LinkedHashSet<UUID>>();
    private final Map<UUID, LinkedHashSet<UUID>> invitationIdsByTargetCharacter =
            new LinkedHashMap<UUID, LinkedHashSet<UUID>>();
    private final List<NBTTagCompound> quarantinedEntries =
            new ArrayList<NBTTagCompound>();

    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;

    public PartyInvitationWorldData() {
        this(DATA_NAME);
    }

    public PartyInvitationWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.invitations.clear();
        this.invitationIdsByParty.clear();
        this.invitationIdsByTargetCharacter.clear();
        this.quarantinedEntries.clear();
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedNewerData = null;

        PartyInvitationNbtCodec.ReadResult result =
                PartyInvitationNbtCodec.read(compound);
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
            copyTagContents(this.preservedNewerData, compound);
            return;
        }
        PartyInvitationNbtCodec.write(
                compound,
                this.invitations.values(),
                this.quarantinedEntries);
    }

    public synchronized boolean isReadOnlyForNewerVersion() {
        return this.readOnlyForNewerVersion;
    }

    public synchronized int getUnsupportedDataVersion() {
        return this.unsupportedDataVersion;
    }

    public synchronized PartyInvitation getInvitation(UUID invitationId) {
        return invitationId == null ? null : this.invitations.get(invitationId);
    }

    public synchronized boolean containsInvitation(UUID invitationId) {
        return invitationId != null && this.invitations.containsKey(invitationId);
    }

    public synchronized boolean hasInvitationForPartyAndTarget(
            UUID partyId, UUID targetCharacterId) {
        if (partyId == null || targetCharacterId == null) {
            return false;
        }
        LinkedHashSet<UUID> invitationIds =
                this.invitationIdsByParty.get(partyId);
        if (invitationIds == null) {
            return false;
        }
        for (UUID invitationId : invitationIds) {
            PartyInvitation invitation = this.invitations.get(invitationId);
            if (invitation != null
                    && targetCharacterId.equals(
                    invitation.getTargetCharacterId())) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<PartyInvitation> getInvitationsForParty(
            UUID partyId) {
        return getByIds(this.invitationIdsByParty.get(partyId));
    }

    public synchronized List<PartyInvitation> getInvitationsForTargetCharacter(
            UUID characterId) {
        return getByIds(this.invitationIdsByTargetCharacter.get(characterId));
    }

    public synchronized Collection<PartyInvitation> getInvitations() {
        ArrayList<PartyInvitation> copy =
                new ArrayList<PartyInvitation>(this.invitations.values());
        Collections.sort(copy, INVITATION_ORDER);
        return Collections.unmodifiableList(copy);
    }

    public synchronized int getInvitationCount() {
        return this.invitations.size();
    }

    public synchronized void saveInvitation(PartyInvitation invitation) {
        ensureWritable();
        validateInvitation(invitation);

        PartyInvitation existing =
                this.invitations.get(invitation.getInvitationId());
        if (existing != null) {
            removeFromIndexes(existing);
        }
        if (hasInvitationForPartyAndTarget(
                invitation.getPartyId(),
                invitation.getTargetCharacterId())) {
            if (existing != null) {
                addToIndexes(existing);
            }
            throw new IllegalStateException(
                    "Target character already has an invitation from this party");
        }

        this.invitations.put(invitation.getInvitationId(), invitation);
        addToIndexes(invitation);
        markDirty();
    }

    public synchronized PartyInvitation removeInvitation(UUID invitationId) {
        ensureWritable();
        PartyInvitation removed = invitationId == null
                ? null : this.invitations.remove(invitationId);
        if (removed != null) {
            removeFromIndexes(removed);
            markDirty();
        }
        return removed;
    }

    public synchronized int removeInvitationsForParty(UUID partyId) {
        ensureWritable();
        return removeByIds(copyIds(this.invitationIdsByParty.get(partyId)));
    }

    public synchronized int removeInvitationsForTargetCharacter(
            UUID characterId) {
        ensureWritable();
        return removeByIds(copyIds(
                this.invitationIdsByTargetCharacter.get(characterId)));
    }

    public synchronized int removeInvitationsInvolvingCharacter(
            UUID characterId) {
        ensureWritable();
        if (characterId == null) {
            return 0;
        }
        LinkedHashSet<UUID> toRemove = new LinkedHashSet<UUID>();
        LinkedHashSet<UUID> targeted =
                this.invitationIdsByTargetCharacter.get(characterId);
        if (targeted != null) {
            toRemove.addAll(targeted);
        }
        for (PartyInvitation invitation : this.invitations.values()) {
            if (characterId.equals(invitation.getInvitingCharacterId())) {
                toRemove.add(invitation.getInvitationId());
            }
        }
        return removeByIds(toRemove);
    }

    public synchronized int removeExpired(long now) {
        ensureWritable();
        LinkedHashSet<UUID> toRemove = new LinkedHashSet<UUID>();
        for (PartyInvitation invitation : this.invitations.values()) {
            if (invitation.isExpired(now)) {
                toRemove.add(invitation.getInvitationId());
            }
        }
        return removeByIds(toRemove);
    }

    public synchronized void quarantine(String reason,
                                        PartyInvitation invitation) {
        ensureWritable();
        this.quarantinedEntries.add(
                PartyInvitationNbtCodec.createQuarantineEntry(
                        reason,
                        invitation == null ? null
                                : invitation.getInvitationId(),
                        invitation == null ? null : invitation.getPartyId(),
                        invitation == null ? null
                                : invitation.getTargetCharacterId()));
        markDirty();
    }

    public synchronized int getQuarantinedEntryCount() {
        return this.quarantinedEntries.size();
    }

    public synchronized List<NBTTagCompound> getQuarantinedEntriesCopy() {
        ArrayList<NBTTagCompound> copies = new ArrayList<NBTTagCompound>();
        for (NBTTagCompound entry : this.quarantinedEntries) {
            if (entry != null) {
                copies.add((NBTTagCompound) entry.copy());
            }
        }
        return Collections.unmodifiableList(copies);
    }

    private boolean rebuildIndexesAndRepairDuplicates() {
        this.invitationIdsByParty.clear();
        this.invitationIdsByTargetCharacter.clear();

        ArrayList<PartyInvitation> ordered =
                new ArrayList<PartyInvitation>(this.invitations.values());
        Collections.sort(ordered, INVITATION_ORDER);
        LinkedHashSet<UUID> duplicateIds = new LinkedHashSet<UUID>();
        LinkedHashSet<PartyTargetKey> seenPairs =
                new LinkedHashSet<PartyTargetKey>();
        for (PartyInvitation invitation : ordered) {
            PartyTargetKey key = new PartyTargetKey(
                    invitation.getPartyId(),
                    invitation.getTargetCharacterId());
            if (!seenPairs.add(key)) {
                duplicateIds.add(invitation.getInvitationId());
                this.quarantinedEntries.add(
                        PartyInvitationNbtCodec.createQuarantineEntry(
                                "duplicate_party_target_invitation",
                                invitation.getInvitationId(),
                                invitation.getPartyId(),
                                invitation.getTargetCharacterId()));
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
            PartyInvitation removed = this.invitations.remove(invitationId);
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

    private List<PartyInvitation> getByIds(Collection<UUID> invitationIds) {
        ArrayList<PartyInvitation> result = new ArrayList<PartyInvitation>();
        if (invitationIds != null) {
            for (UUID invitationId : invitationIds) {
                PartyInvitation invitation = this.invitations.get(invitationId);
                if (invitation != null) {
                    result.add(invitation);
                }
            }
        }
        Collections.sort(result, INVITATION_ORDER);
        return Collections.unmodifiableList(result);
    }

    private void addToIndexes(PartyInvitation invitation) {
        addIndex(this.invitationIdsByParty,
                invitation.getPartyId(), invitation.getInvitationId());
        addIndex(this.invitationIdsByTargetCharacter,
                invitation.getTargetCharacterId(),
                invitation.getInvitationId());
    }

    private void removeFromIndexes(PartyInvitation invitation) {
        removeIndex(this.invitationIdsByParty,
                invitation.getPartyId(), invitation.getInvitationId());
        removeIndex(this.invitationIdsByTargetCharacter,
                invitation.getTargetCharacterId(),
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

    private static void validateInvitation(PartyInvitation invitation) {
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
                    "Party invitation data is read-only because it uses unsupported version "
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

    private static final Comparator<PartyInvitation> INVITATION_ORDER =
            new Comparator<PartyInvitation>() {
                @Override
                public int compare(PartyInvitation left,
                                   PartyInvitation right) {
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

    private static final class PartyTargetKey {
        private final UUID partyId;
        private final UUID targetCharacterId;

        private PartyTargetKey(UUID partyId, UUID targetCharacterId) {
            this.partyId = partyId;
            this.targetCharacterId = targetCharacterId;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof PartyTargetKey)) {
                return false;
            }
            PartyTargetKey other = (PartyTargetKey) object;
            return this.partyId.equals(other.partyId)
                    && this.targetCharacterId.equals(other.targetCharacterId);
        }

        @Override
        public int hashCode() {
            return 31 * this.partyId.hashCode()
                    + this.targetCharacterId.hashCode();
        }
    }
}
