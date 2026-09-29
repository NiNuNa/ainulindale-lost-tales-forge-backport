package com.ninuna.losttales.party.storage;

import com.ninuna.losttales.party.model.PartyInvitation;
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

/** Versioned NBT codec for pending party invitations. */
public final class PartyInvitationNbtCodec {

    public static final int CURRENT_ROOT_DATA_VERSION = 1;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_INVITATIONS = "Invitations";
    private static final String TAG_REASON = "Reason";
    private static final String TAG_INVITATION_INDEX = "InvitationIndex";

    private static final String TAG_INVITATION_UUID = "InvitationUUID";
    private static final String TAG_PARTY_UUID = "PartyUUID";
    private static final String TAG_INVITING_CHARACTER_UUID = "InvitingCharacterUUID";
    private static final String TAG_INVITING_OWNER_UUID = "InvitingOwnerUUID";
    private static final String TAG_INVITING_CHARACTER_NAME = "InvitingCharacterName";
    private static final String TAG_TARGET_CHARACTER_UUID = "TargetCharacterUUID";
    private static final String TAG_TARGET_OWNER_UUID = "TargetOwnerUUID";
    private static final String TAG_TARGET_CHARACTER_NAME = "TargetCharacterName";
    private static final String TAG_CREATED_AT = "CreatedAt";
    private static final String TAG_EXPIRES_AT = "ExpiresAt";

    private PartyInvitationNbtCodec() {}

    public static void write(NBTTagCompound output,
                             Collection<PartyInvitation> invitations,
                             Collection<NBTTagCompound> quarantinedEntries) {
        output.setInteger(TAG_DATA_VERSION, CURRENT_ROOT_DATA_VERSION);

        ArrayList<PartyInvitation> ordered = new ArrayList<PartyInvitation>();
        if (invitations != null) {
            ordered.addAll(invitations);
        }
        Collections.sort(ordered, INVITATION_ORDER);

        NBTTagList list = new NBTTagList();
        for (PartyInvitation invitation : ordered) {
            if (invitation != null) {
                list.appendTag(writeInvitation(invitation));
            }
        }
        output.setTag(TAG_INVITATIONS, list);
        NbtQuarantine.write(output, quarantinedEntries);
    }

    public static ReadResult read(NBTTagCompound source) {
        NBTTagCompound safeSource = source == null ? new NBTTagCompound() : source;
        int version = safeSource.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? safeSource.getInteger(TAG_DATA_VERSION) : 0;
        if (version > CURRENT_ROOT_DATA_VERSION || version < 0) {
            LostTalesLog.warning("Party invitation data uses unsupported version %d; data will remain read-only",
                    Integer.valueOf(version));
            return ReadResult.unsupported(safeSource, version);
        }

        boolean repaired = version != CURRENT_ROOT_DATA_VERSION;
        NbtQuarantine.Read quarantineResult = NbtQuarantine.read(safeSource);
        if (!quarantineResult.isSupported()) {
            LostTalesLog.warning("Party invitation quarantine is malformed or unsupported; data will remain read-only");
            return ReadResult.unsupported(safeSource, quarantineResult.getUnsupportedVersion());
        }
        repaired |= quarantineResult.isRepaired();
        ArrayList<NBTTagCompound> quarantine =
                new ArrayList<NBTTagCompound>(quarantineResult.getEntries());

        if (safeSource.hasKey(TAG_INVITATIONS)
                && !safeSource.hasKey(TAG_INVITATIONS, Constants.NBT.TAG_LIST)) {
            LostTalesLog.warning("Party invitation root has a malformed invitation list; preserving data read-only");
            return ReadResult.unsupported(safeSource, -1);
        }
        if (!safeSource.hasKey(TAG_INVITATIONS, Constants.NBT.TAG_LIST)) {
            repaired = true;
        }

        LinkedHashMap<UUID, PartyInvitation> invitations =
                new LinkedHashMap<UUID, PartyInvitation>();
        NBTTagList list = safeSource.getTagList(
                TAG_INVITATIONS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound raw = list.getCompoundTagAt(i);
            InvitationReadResult result = readInvitation(raw);
            if (result.unsupportedVersion >= 0) {
                return ReadResult.unsupported(safeSource, result.unsupportedVersion);
            }
            if (result.invitation == null) {
                quarantine.add(NbtQuarantine.entry(
                        result.failureReason, TAG_INVITATION_INDEX, i, raw));
                repaired = true;
                continue;
            }
            UUID invitationId = result.invitation.getInvitationId();
            if (invitations.containsKey(invitationId)) {
                quarantine.add(NbtQuarantine.entry(
                        "duplicate_invitation_uuid", TAG_INVITATION_INDEX, i, raw));
                repaired = true;
                continue;
            }
            invitations.put(invitationId, result.invitation);
            repaired |= result.repaired;
        }
        return ReadResult.success(invitations, repaired, quarantine);
    }

    public static NBTTagCompound createQuarantineEntry(String reason,
                                                        UUID invitationId,
                                                        UUID partyId,
                                                        UUID targetIdentityId) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(TAG_REASON, reason == null ? "unknown" : reason);
        if (invitationId != null) {
            NbtTags.writeUuid(entry, TAG_INVITATION_UUID, invitationId);
        }
        if (partyId != null) {
            NbtTags.writeUuid(entry, TAG_PARTY_UUID, partyId);
        }
        if (targetIdentityId != null) {
            NbtTags.writeUuid(entry, TAG_TARGET_CHARACTER_UUID, targetIdentityId);
        }
        return entry;
    }

    private static NBTTagCompound writeInvitation(PartyInvitation invitation) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, PartyInvitation.CURRENT_DATA_VERSION);
        NbtTags.writeUuid(tag, TAG_INVITATION_UUID, invitation.getInvitationId());
        NbtTags.writeUuid(tag, TAG_PARTY_UUID, invitation.getPartyId());
        NbtTags.writeUuid(tag, TAG_INVITING_CHARACTER_UUID,
                invitation.getInvitingIdentityId());
        NbtTags.writeUuid(tag, TAG_INVITING_OWNER_UUID, invitation.getInvitingOwnerId());
        tag.setString(TAG_INVITING_CHARACTER_NAME,
                invitation.getInvitingCharacterName());
        NbtTags.writeUuid(tag, TAG_TARGET_CHARACTER_UUID,
                invitation.getTargetIdentityId());
        NbtTags.writeUuid(tag, TAG_TARGET_OWNER_UUID, invitation.getTargetOwnerId());
        tag.setString(TAG_TARGET_CHARACTER_NAME,
                invitation.getTargetCharacterName());
        tag.setLong(TAG_CREATED_AT, invitation.getCreatedAt());
        tag.setLong(TAG_EXPIRES_AT, invitation.getExpiresAt());
        return tag;
    }

    private static InvitationReadResult readInvitation(NBTTagCompound source) {
        if (source == null) {
            return InvitationReadResult.failed("missing_invitation");
        }
        int version = source.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? source.getInteger(TAG_DATA_VERSION) : 0;
        if (version > PartyInvitation.CURRENT_DATA_VERSION || version < 0) {
            return InvitationReadResult.unsupported(version);
        }

        UUID invitationId = NbtTags.readUuid(source, TAG_INVITATION_UUID);
        UUID partyId = NbtTags.readUuid(source, TAG_PARTY_UUID);
        UUID invitingIdentityId = NbtTags.readUuid(source, TAG_INVITING_CHARACTER_UUID);
        UUID invitingOwnerId = NbtTags.readUuid(source, TAG_INVITING_OWNER_UUID);
        UUID targetIdentityId = NbtTags.readUuid(source, TAG_TARGET_CHARACTER_UUID);
        UUID targetOwnerId = NbtTags.readUuid(source, TAG_TARGET_OWNER_UUID);
        if (invitationId == null || partyId == null
                || invitingIdentityId == null || invitingOwnerId == null
                || targetIdentityId == null || targetOwnerId == null) {
            return InvitationReadResult.failed("missing_required_identity");
        }
        if (invitingIdentityId.equals(targetIdentityId)) {
            return InvitationReadResult.failed("self_invitation");
        }
        if (!source.hasKey(TAG_CREATED_AT, Constants.NBT.TAG_LONG)
                || !source.hasKey(TAG_EXPIRES_AT, Constants.NBT.TAG_LONG)) {
            return InvitationReadResult.failed("missing_timestamps");
        }

        long createdAt = source.getLong(TAG_CREATED_AT);
        long expiresAt = source.getLong(TAG_EXPIRES_AT);
        String invitingName = source.hasKey(
                TAG_INVITING_CHARACTER_NAME, Constants.NBT.TAG_STRING)
                ? source.getString(TAG_INVITING_CHARACTER_NAME) : "Unknown";
        String targetName = source.hasKey(
                TAG_TARGET_CHARACTER_NAME, Constants.NBT.TAG_STRING)
                ? source.getString(TAG_TARGET_CHARACTER_NAME) : "Unknown";
        try {
            PartyInvitation invitation = new PartyInvitation(
                    invitationId,
                    partyId,
                    invitingIdentityId,
                    invitingOwnerId,
                    invitingName,
                    targetIdentityId,
                    targetOwnerId,
                    targetName,
                    createdAt,
                    expiresAt);
            boolean repaired = version != PartyInvitation.CURRENT_DATA_VERSION
                    || !source.hasKey(TAG_INVITING_CHARACTER_NAME,
                    Constants.NBT.TAG_STRING)
                    || !source.hasKey(TAG_TARGET_CHARACTER_NAME,
                    Constants.NBT.TAG_STRING);
            return InvitationReadResult.success(invitation, repaired);
        } catch (IllegalArgumentException exception) {
            return InvitationReadResult.failed("invalid_invitation_fields");
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

    public static final class ReadResult {
        private final Map<UUID, PartyInvitation> invitations;
        private final boolean repaired;
        private final List<NBTTagCompound> quarantineEntries;
        private final boolean readOnly;
        private final int unsupportedVersion;
        private final NBTTagCompound originalData;

        private ReadResult(Map<UUID, PartyInvitation> invitations,
                           boolean repaired,
                           List<NBTTagCompound> quarantineEntries,
                           boolean readOnly,
                           int unsupportedVersion,
                           NBTTagCompound originalData) {
            this.invitations = invitations;
            this.repaired = repaired;
            this.quarantineEntries = quarantineEntries;
            this.readOnly = readOnly;
            this.unsupportedVersion = unsupportedVersion;
            this.originalData = originalData;
        }

        private static ReadResult success(
                Map<UUID, PartyInvitation> invitations,
                boolean repaired,
                List<NBTTagCompound> quarantineEntries) {
            return new ReadResult(
                    Collections.unmodifiableMap(
                            new LinkedHashMap<UUID, PartyInvitation>(invitations)),
                    repaired,
                    Collections.unmodifiableList(copyEntries(quarantineEntries)),
                    false,
                    -1,
                    null);
        }

        private static ReadResult unsupported(NBTTagCompound source,
                                              int version) {
            return new ReadResult(
                    Collections.<UUID, PartyInvitation>emptyMap(),
                    false,
                    Collections.<NBTTagCompound>emptyList(),
                    true,
                    version,
                    source == null ? new NBTTagCompound()
                            : (NBTTagCompound) source.copy());
        }

        public Map<UUID, PartyInvitation> getInvitations() {
            return this.invitations;
        }

        public boolean wasRepaired() {
            return this.repaired;
        }

        public List<NBTTagCompound> getQuarantineEntriesCopy() {
            return Collections.unmodifiableList(
                    copyEntries(this.quarantineEntries));
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

    private static List<NBTTagCompound> copyEntries(
            Collection<NBTTagCompound> entries) {
        ArrayList<NBTTagCompound> copies = new ArrayList<NBTTagCompound>();
        if (entries != null) {
            for (NBTTagCompound entry : entries) {
                if (entry != null) {
                    copies.add((NBTTagCompound) entry.copy());
                }
            }
        }
        return copies;
    }

    private static final class InvitationReadResult {
        private final PartyInvitation invitation;
        private final boolean repaired;
        private final int unsupportedVersion;
        private final String failureReason;

        private InvitationReadResult(PartyInvitation invitation,
                                     boolean repaired,
                                     int unsupportedVersion,
                                     String failureReason) {
            this.invitation = invitation;
            this.repaired = repaired;
            this.unsupportedVersion = unsupportedVersion;
            this.failureReason = failureReason;
        }

        private static InvitationReadResult success(
                PartyInvitation invitation, boolean repaired) {
            return new InvitationReadResult(invitation, repaired, -1, "");
        }

        private static InvitationReadResult failed(String reason) {
            return new InvitationReadResult(null, true, -1, reason);
        }

        private static InvitationReadResult unsupported(int version) {
            return new InvitationReadResult(
                    null, false, version, "unsupported_version");
        }
    }
}
