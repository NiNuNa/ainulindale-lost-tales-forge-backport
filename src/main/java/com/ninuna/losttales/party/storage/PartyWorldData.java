package com.ninuna.losttales.party.storage;

import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyMember;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent server-authoritative party collection and character membership index. */
public final class PartyWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_parties";

    private final Map<UUID, Party> parties = new LinkedHashMap<UUID, Party>();
    private final Map<UUID, UUID> partyIdByCharacterId = new LinkedHashMap<UUID, UUID>();
    private final List<NBTTagCompound> quarantinedEntries = new ArrayList<NBTTagCompound>();
    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;
    private transient boolean characterReferencesValidated;

    public PartyWorldData() {
        this(DATA_NAME);
    }

    public PartyWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.parties.clear();
        this.partyIdByCharacterId.clear();
        this.quarantinedEntries.clear();
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedNewerData = null;
        this.characterReferencesValidated = false;

        PartyNbtCodec.ReadResult result = PartyNbtCodec.read(compound);
        if (result.isReadOnly()) {
            this.readOnlyForNewerVersion = true;
            this.unsupportedDataVersion = result.getUnsupportedVersion();
            this.preservedNewerData = result.getOriginalDataCopy();
            return;
        }
        this.parties.putAll(result.getParties());
        this.quarantinedEntries.addAll(result.getQuarantineEntriesCopy());
        boolean repaired = rebuildMembershipIndexAndRepair();
        if (result.wasRepaired() || repaired) {
            markDirty();
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.readOnlyForNewerVersion && this.preservedNewerData != null) {
            copyTagContents(this.preservedNewerData, compound);
            return;
        }
        PartyNbtCodec.write(compound, this.parties.values(), this.quarantinedEntries);
    }

    public synchronized boolean isReadOnlyForNewerVersion() {
        return this.readOnlyForNewerVersion;
    }

    public synchronized int getUnsupportedDataVersion() {
        return this.unsupportedDataVersion;
    }

    public synchronized Party getParty(UUID partyId) {
        return partyId == null ? null : this.parties.get(partyId);
    }

    public synchronized Party getPartyForCharacter(UUID characterId) {
        UUID partyId = characterId == null
                ? null : this.partyIdByCharacterId.get(characterId);
        return partyId == null ? null : this.parties.get(partyId);
    }

    public synchronized UUID getPartyIdForCharacter(UUID characterId) {
        return characterId == null ? null : this.partyIdByCharacterId.get(characterId);
    }

    public synchronized boolean containsParty(UUID partyId) {
        return partyId != null && this.parties.containsKey(partyId);
    }

    public synchronized Collection<Party> getParties() {
        return Collections.unmodifiableCollection(
                new ArrayList<Party>(this.parties.values()));
    }

    public synchronized int getPartyCount() {
        return this.parties.size();
    }

    public synchronized void saveParty(Party party) {
        ensureWritable();
        validateParty(party);

        for (PartyMember member : party.getMembers()) {
            UUID existingPartyId = this.partyIdByCharacterId.get(member.getCharacterId());
            if (existingPartyId != null && !existingPartyId.equals(party.getPartyId())) {
                throw new IllegalStateException(
                        "Character already belongs to another party: "
                                + member.getCharacterId());
            }
        }

        removeIndexEntriesForParty(party.getPartyId());
        this.parties.put(party.getPartyId(), party);
        for (PartyMember member : party.getMembers()) {
            this.partyIdByCharacterId.put(
                    member.getCharacterId(), party.getPartyId());
        }
        markDirty();
    }

    public synchronized Party removeParty(UUID partyId) {
        ensureWritable();
        Party removed = partyId == null ? null : this.parties.remove(partyId);
        if (removed != null) {
            removeIndexEntriesForParty(partyId);
            markDirty();
        }
        return removed;
    }

    public synchronized void quarantine(String reason, UUID partyId, UUID characterId) {
        ensureWritable();
        this.quarantinedEntries.add(
                PartyNbtCodec.createQuarantineEntry(reason, partyId, characterId));
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

    public synchronized boolean areCharacterReferencesValidated() {
        return this.characterReferencesValidated;
    }

    public synchronized void markCharacterReferencesValidated() {
        this.characterReferencesValidated = true;
    }

    public synchronized void markCharacterReferencesUnchecked() {
        this.characterReferencesValidated = false;
    }

    private boolean rebuildMembershipIndexAndRepair() {
        boolean repaired = false;
        ArrayList<UUID> emptyPartyIds = new ArrayList<UUID>();
        for (Party party : this.parties.values()) {
            ArrayList<UUID> duplicateMemberIds = new ArrayList<UUID>();
            for (PartyMember member : party.getMembers()) {
                UUID existingPartyId = this.partyIdByCharacterId.get(member.getCharacterId());
                if (existingPartyId != null && !existingPartyId.equals(party.getPartyId())) {
                    duplicateMemberIds.add(member.getCharacterId());
                    this.quarantinedEntries.add(PartyNbtCodec.createQuarantineEntry(
                            "character_in_multiple_parties",
                            party.getPartyId(), member.getCharacterId()));
                    repaired = true;
                    continue;
                }
                this.partyIdByCharacterId.put(
                        member.getCharacterId(), party.getPartyId());
            }
            for (UUID duplicateMemberId : duplicateMemberIds) {
                party.removeMember(duplicateMemberId);
            }
            if (party.getMemberCount() == 0) {
                emptyPartyIds.add(party.getPartyId());
            } else if (party.repairLeaderIfNecessary()) {
                repaired = true;
            }
        }
        for (UUID partyId : emptyPartyIds) {
            this.parties.remove(partyId);
            removeIndexEntriesForParty(partyId);
            repaired = true;
        }
        return repaired;
    }

    private void validateParty(Party party) {
        if (party == null) {
            throw new IllegalArgumentException("party must not be null");
        }
        if (party.getMemberCount() <= 0 || party.getMemberCount() > Party.MAX_MEMBERS) {
            throw new IllegalArgumentException("party member count is invalid");
        }
        if (!party.hasValidLeader()) {
            throw new IllegalArgumentException("party leader must be a member");
        }
        for (PartyMember member : party.getMembers()) {
            if (!party.isColorAvailable(member.getColor(), member.getCharacterId())) {
                throw new IllegalArgumentException("party member colors must be unique");
            }
        }
    }

    private void removeIndexEntriesForParty(UUID partyId) {
        Iterator<Map.Entry<UUID, UUID>> iterator =
                this.partyIdByCharacterId.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            if (partyId.equals(entry.getValue())) {
                iterator.remove();
            }
        }
    }

    private void ensureWritable() {
        if (this.readOnlyForNewerVersion) {
            throw new IllegalStateException(
                    "Party data is read-only because it uses unsupported version "
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
