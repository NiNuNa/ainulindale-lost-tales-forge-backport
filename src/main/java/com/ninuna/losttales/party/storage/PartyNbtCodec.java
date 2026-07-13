package com.ninuna.losttales.party.storage;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.model.PartyMember;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Versioned NBT codec for the world-level party store. */
public final class PartyNbtCodec {

    public static final int CURRENT_ROOT_DATA_VERSION = 1;
    public static final int CURRENT_QUARANTINE_DATA_VERSION = 1;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_PARTIES = "Parties";
    private static final String TAG_MEMBERS = "Members";
    private static final String TAG_QUARANTINE = "Quarantine";
    private static final String TAG_QUARANTINE_ENTRIES = "Entries";
    private static final String TAG_REASON = "Reason";
    private static final String TAG_PARTY_INDEX = "PartyIndex";
    private static final String TAG_MEMBER_INDEX = "MemberIndex";
    private static final String TAG_ORIGINAL_DATA = "OriginalData";

    private static final String TAG_PARTY_UUID = "PartyUUID";
    private static final String TAG_LEADER_CHARACTER_UUID = "LeaderCharacterUUID";
    private static final String TAG_CHARACTER_UUID = "CharacterUUID";
    private static final String TAG_OWNER_UUID = "OwnerUUID";
    private static final String TAG_CHARACTER_NAME = "CharacterName";
    private static final String TAG_COLOR = "Color";
    private static final String TAG_CREATED_AT = "CreatedAt";
    private static final String TAG_JOINED_AT = "JoinedAt";
    private static final String TAG_REVISION = "Revision";

    private PartyNbtCodec() {}

    public static void write(NBTTagCompound output, Collection<Party> parties,
                             Collection<NBTTagCompound> quarantinedEntries) {
        output.setInteger(TAG_DATA_VERSION, CURRENT_ROOT_DATA_VERSION);

        ArrayList<Party> sortedParties = new ArrayList<Party>();
        if (parties != null) {
            sortedParties.addAll(parties);
        }
        Collections.sort(sortedParties, new Comparator<Party>() {
            @Override
            public int compare(Party left, Party right) {
                return left.getPartyId().toString().compareTo(right.getPartyId().toString());
            }
        });

        NBTTagList partyList = new NBTTagList();
        for (Party party : sortedParties) {
            if (party != null && party.getMemberCount() > 0 && party.hasValidLeader()) {
                partyList.appendTag(writeParty(party));
            }
        }
        output.setTag(TAG_PARTIES, partyList);
        output.setTag(TAG_QUARANTINE, writeQuarantine(quarantinedEntries));
    }

    public static ReadResult read(NBTTagCompound source) {
        NBTTagCompound safeSource = source == null ? new NBTTagCompound() : source;
        int version = safeSource.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? safeSource.getInteger(TAG_DATA_VERSION) : 0;
        if (version > CURRENT_ROOT_DATA_VERSION || version < 0) {
            warn("Party data root uses unsupported version %d; data will remain read-only",
                    Integer.valueOf(version));
            return ReadResult.unsupported(safeSource, version);
        }

        boolean repaired = version != CURRENT_ROOT_DATA_VERSION;
        QuarantineReadResult quarantineResult = readQuarantine(safeSource);
        if (!quarantineResult.supported) {
            warn("Party quarantine data is malformed or uses unsupported version %d; data will remain read-only",
                    Integer.valueOf(quarantineResult.unsupportedVersion));
            return ReadResult.unsupported(safeSource, quarantineResult.unsupportedVersion);
        }
        repaired |= quarantineResult.repaired;
        ArrayList<NBTTagCompound> quarantine =
                new ArrayList<NBTTagCompound>(quarantineResult.entries);

        if (safeSource.hasKey(TAG_PARTIES)
                && !safeSource.hasKey(TAG_PARTIES, Constants.NBT.TAG_LIST)) {
            warn("Party data root has a malformed party list; preserving data read-only");
            return ReadResult.unsupported(safeSource, -1);
        }
        if (!safeSource.hasKey(TAG_PARTIES, Constants.NBT.TAG_LIST)) {
            repaired = true;
        }

        LinkedHashMap<UUID, Party> parties = new LinkedHashMap<UUID, Party>();
        NBTTagList partyList = safeSource.getTagList(TAG_PARTIES, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < partyList.tagCount(); i++) {
            NBTTagCompound rawParty = partyList.getCompoundTagAt(i);
            PartyReadResult partyResult = readParty(rawParty, i);
            if (partyResult.unsupportedVersion >= 0) {
                return ReadResult.unsupported(safeSource, partyResult.unsupportedVersion);
            }
            repaired |= partyResult.repaired;
            quarantine.addAll(partyResult.quarantineEntries);
            Party party = partyResult.party;
            if (party == null) {
                quarantine.add(createQuarantineEntry(
                        partyResult.failureReason, i, -1, rawParty));
                repaired = true;
                continue;
            }
            if (parties.containsKey(party.getPartyId())) {
                quarantine.add(createQuarantineEntry(
                        "duplicate_party_uuid", i, -1, rawParty));
                repaired = true;
                warn("Quarantining duplicate party UUID %s at index %d",
                        party.getPartyId(), Integer.valueOf(i));
                continue;
            }
            parties.put(party.getPartyId(), party);
        }
        return ReadResult.success(parties, repaired, quarantine);
    }

    public static NBTTagCompound createQuarantineEntry(String reason,
                                                        UUID partyId,
                                                        UUID characterId) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(TAG_REASON, reason == null ? "unknown" : reason);
        if (partyId != null) {
            writeUuid(entry, TAG_PARTY_UUID, partyId);
        }
        if (characterId != null) {
            writeUuid(entry, TAG_CHARACTER_UUID, characterId);
        }
        return entry;
    }

    private static NBTTagCompound writeParty(Party party) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, Party.CURRENT_DATA_VERSION);
        writeUuid(tag, TAG_PARTY_UUID, party.getPartyId());
        writeUuid(tag, TAG_LEADER_CHARACTER_UUID, party.getLeaderCharacterId());
        tag.setLong(TAG_CREATED_AT, party.getCreatedAt());
        tag.setLong(TAG_REVISION, party.getRevision());

        NBTTagList members = new NBTTagList();
        for (PartyMember member : party.getMembers()) {
            NBTTagCompound memberTag = new NBTTagCompound();
            memberTag.setInteger(TAG_DATA_VERSION, PartyMember.CURRENT_DATA_VERSION);
            writeUuid(memberTag, TAG_CHARACTER_UUID, member.getCharacterId());
            writeUuid(memberTag, TAG_OWNER_UUID, member.getOwnerId());
            memberTag.setString(TAG_CHARACTER_NAME, member.getCharacterName());
            memberTag.setLong(TAG_JOINED_AT, member.getJoinedAt());
            memberTag.setString(TAG_COLOR, member.getColor().getId());
            members.appendTag(memberTag);
        }
        tag.setTag(TAG_MEMBERS, members);
        return tag;
    }

    private static NBTTagCompound writeQuarantine(Collection<NBTTagCompound> entries) {
        NBTTagCompound quarantine = new NBTTagCompound();
        quarantine.setInteger(TAG_DATA_VERSION, CURRENT_QUARANTINE_DATA_VERSION);
        NBTTagList list = new NBTTagList();
        if (entries != null) {
            for (NBTTagCompound entry : entries) {
                if (entry != null) {
                    list.appendTag(entry.copy());
                }
            }
        }
        quarantine.setTag(TAG_QUARANTINE_ENTRIES, list);
        return quarantine;
    }

    private static QuarantineReadResult readQuarantine(NBTTagCompound root) {
        if (!root.hasKey(TAG_QUARANTINE)) {
            return QuarantineReadResult.success(
                    Collections.<NBTTagCompound>emptyList(), true);
        }
        if (!root.hasKey(TAG_QUARANTINE, Constants.NBT.TAG_COMPOUND)) {
            return QuarantineReadResult.unsupported(-1);
        }
        NBTTagCompound quarantine = root.getCompoundTag(TAG_QUARANTINE);
        int version = quarantine.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? quarantine.getInteger(TAG_DATA_VERSION) : 0;
        if (version > CURRENT_QUARANTINE_DATA_VERSION || version < 0) {
            return QuarantineReadResult.unsupported(version);
        }
        if (quarantine.hasKey(TAG_QUARANTINE_ENTRIES)
                && !quarantine.hasKey(TAG_QUARANTINE_ENTRIES, Constants.NBT.TAG_LIST)) {
            return QuarantineReadResult.unsupported(-1);
        }
        ArrayList<NBTTagCompound> entries = new ArrayList<NBTTagCompound>();
        NBTTagList list = quarantine.getTagList(
                TAG_QUARANTINE_ENTRIES, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            entries.add((NBTTagCompound) list.getCompoundTagAt(i).copy());
        }
        return QuarantineReadResult.success(entries,
                version != CURRENT_QUARANTINE_DATA_VERSION);
    }

    private static PartyReadResult readParty(NBTTagCompound source, int partyIndex) {
        if (source == null) {
            return PartyReadResult.failed(true, "missing_party");
        }
        int version = source.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? source.getInteger(TAG_DATA_VERSION) : 0;
        if (version > Party.CURRENT_DATA_VERSION || version < 0) {
            warn("Party at index %d uses unsupported version %d",
                    Integer.valueOf(partyIndex), Integer.valueOf(version));
            return PartyReadResult.unsupported(version);
        }
        boolean repaired = version != Party.CURRENT_DATA_VERSION;

        UUID partyId = readUuid(source, TAG_PARTY_UUID);
        if (partyId == null) {
            return PartyReadResult.failed(true, "missing_or_invalid_party_uuid");
        }
        long createdAt = source.hasKey(TAG_CREATED_AT, Constants.NBT.TAG_LONG)
                ? source.getLong(TAG_CREATED_AT) : 0L;
        if (createdAt < 0L) {
            createdAt = 0L;
            repaired = true;
        }
        long revision = source.hasKey(TAG_REVISION, Constants.NBT.TAG_LONG)
                ? source.getLong(TAG_REVISION) : 0L;
        if (revision < 0L) {
            revision = 0L;
            repaired = true;
        }

        if (source.hasKey(TAG_MEMBERS)
                && !source.hasKey(TAG_MEMBERS, Constants.NBT.TAG_LIST)) {
            return PartyReadResult.failed(true, "malformed_member_list");
        }
        NBTTagList memberList = source.getTagList(TAG_MEMBERS, Constants.NBT.TAG_COMPOUND);
        ArrayList<PartyMember> members = new ArrayList<PartyMember>();
        ArrayList<NBTTagCompound> quarantine = new ArrayList<NBTTagCompound>();
        Set<UUID> characterIds = new java.util.HashSet<UUID>();
        Set<PartyColor> usedColors = EnumSet.noneOf(PartyColor.class);

        for (int i = 0; i < memberList.tagCount(); i++) {
            NBTTagCompound rawMember = memberList.getCompoundTagAt(i);
            MemberReadResult memberResult = readMember(
                    rawMember, createdAt, i, usedColors);
            if (memberResult.unsupportedVersion >= 0) {
                return PartyReadResult.unsupported(memberResult.unsupportedVersion);
            }
            repaired |= memberResult.repaired;
            PartyMember member = memberResult.member;
            if (member == null) {
                quarantine.add(createQuarantineEntry(
                        memberResult.failureReason, partyIndex, i, rawMember));
                repaired = true;
                continue;
            }
            if (!characterIds.add(member.getCharacterId())) {
                quarantine.add(createQuarantineEntry(
                        "duplicate_member_character_uuid", partyIndex, i, rawMember));
                repaired = true;
                continue;
            }
            if (members.size() >= Party.MAX_MEMBERS) {
                quarantine.add(createQuarantineEntry(
                        "party_member_limit_exceeded", partyIndex, i, rawMember));
                repaired = true;
                continue;
            }
            usedColors.add(member.getColor());
            members.add(member);
        }

        if (members.isEmpty()) {
            return PartyReadResult.failed(true, "party_has_no_valid_members");
        }

        UUID leaderId = readUuid(source, TAG_LEADER_CHARACTER_UUID);
        if (leaderId == null || !containsCharacter(members, leaderId)) {
            leaderId = selectFirstMember(members).getCharacterId();
            repaired = true;
        }

        try {
            Party party = new Party(partyId, leaderId, members,
                    createdAt, revision, Party.CURRENT_DATA_VERSION);
            return PartyReadResult.success(party, repaired, quarantine);
        } catch (RuntimeException exception) {
            warn("Skipping invalid party %s at index %d: %s",
                    partyId, Integer.valueOf(partyIndex), exception.toString());
            return PartyReadResult.failed(true, "invalid_party_structure");
        }
    }

    private static MemberReadResult readMember(NBTTagCompound source,
                                                long partyCreatedAt,
                                                int memberIndex,
                                                Set<PartyColor> usedColors) {
        if (source == null) {
            return MemberReadResult.failed(true, "missing_member");
        }
        int version = source.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? source.getInteger(TAG_DATA_VERSION) : 0;
        if (version > PartyMember.CURRENT_DATA_VERSION || version < 0) {
            return MemberReadResult.unsupported(version);
        }
        boolean repaired = version != PartyMember.CURRENT_DATA_VERSION;
        UUID characterId = readUuid(source, TAG_CHARACTER_UUID);
        UUID ownerId = readUuid(source, TAG_OWNER_UUID);
        if (characterId == null) {
            return MemberReadResult.failed(true, "missing_or_invalid_character_uuid");
        }
        if (ownerId == null) {
            return MemberReadResult.failed(true, "missing_or_invalid_owner_uuid");
        }

        String name = source.hasKey(TAG_CHARACTER_NAME, Constants.NBT.TAG_STRING)
                ? source.getString(TAG_CHARACTER_NAME) : "Unknown";
        if (name == null || name.trim().length() == 0) {
            name = "Unknown";
            repaired = true;
        }
        long joinedAt = source.hasKey(TAG_JOINED_AT, Constants.NBT.TAG_LONG)
                ? source.getLong(TAG_JOINED_AT) : partyCreatedAt + memberIndex;
        if (joinedAt < 0L) {
            joinedAt = Math.max(0L, partyCreatedAt + memberIndex);
            repaired = true;
        }

        PartyColor color = source.hasKey(TAG_COLOR, Constants.NBT.TAG_STRING)
                ? PartyColor.fromId(source.getString(TAG_COLOR)) : null;
        if (color == null || usedColors.contains(color)) {
            color = firstAvailableColor(usedColors);
            repaired = true;
        }
        if (color == null) {
            return MemberReadResult.failed(true, "no_available_party_color");
        }
        return MemberReadResult.success(
                new PartyMember(characterId, ownerId, name, joinedAt, color), repaired);
    }

    private static PartyMember selectFirstMember(List<PartyMember> members) {
        ArrayList<PartyMember> ordered = new ArrayList<PartyMember>(members);
        Collections.sort(ordered, new Comparator<PartyMember>() {
            @Override
            public int compare(PartyMember left, PartyMember right) {
                if (left.getJoinedAt() < right.getJoinedAt()) {
                    return -1;
                }
                if (left.getJoinedAt() > right.getJoinedAt()) {
                    return 1;
                }
                return left.getCharacterId().toString().compareTo(right.getCharacterId().toString());
            }
        });
        return ordered.get(0);
    }

    private static boolean containsCharacter(List<PartyMember> members,
                                             UUID characterId) {
        if (characterId == null || members == null) {
            return false;
        }
        for (PartyMember member : members) {
            if (member != null && characterId.equals(member.getCharacterId())) {
                return true;
            }
        }
        return false;
    }

    private static PartyColor firstAvailableColor(Set<PartyColor> usedColors) {
        for (PartyColor color : PartyColor.values()) {
            if (usedColors == null || !usedColors.contains(color)) {
                return color;
            }
        }
        return null;
    }

    private static NBTTagCompound createQuarantineEntry(String reason,
                                                        int partyIndex,
                                                        int memberIndex,
                                                        NBTTagCompound originalData) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(TAG_REASON, reason == null ? "unknown" : reason);
        if (partyIndex >= 0) {
            entry.setInteger(TAG_PARTY_INDEX, partyIndex);
        }
        if (memberIndex >= 0) {
            entry.setInteger(TAG_MEMBER_INDEX, memberIndex);
        }
        entry.setTag(TAG_ORIGINAL_DATA, originalData == null
                ? new NBTTagCompound() : originalData.copy());
        return entry;
    }

    private static void writeUuid(NBTTagCompound tag, String key, UUID uuid) {
        tag.setLong(key + "Most", uuid.getMostSignificantBits());
        tag.setLong(key + "Least", uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(NBTTagCompound tag, String key) {
        String mostKey = key + "Most";
        String leastKey = key + "Least";
        if (!tag.hasKey(mostKey, Constants.NBT.TAG_LONG)
                || !tag.hasKey(leastKey, Constants.NBT.TAG_LONG)) {
            return null;
        }
        return new UUID(tag.getLong(mostKey), tag.getLong(leastKey));
    }

    private static void warn(String message, Object... arguments) {
        Object[] allArguments = new Object[arguments.length + 1];
        allArguments[0] = LostTalesMetaData.MOD_ID;
        System.arraycopy(arguments, 0, allArguments, 1, arguments.length);
        FMLLog.warning("[%s] " + message, allArguments);
    }

    public static final class ReadResult {
        private final Map<UUID, Party> parties;
        private final boolean repaired;
        private final List<NBTTagCompound> quarantineEntries;
        private final boolean readOnly;
        private final int unsupportedVersion;
        private final NBTTagCompound originalData;

        private ReadResult(Map<UUID, Party> parties, boolean repaired,
                           List<NBTTagCompound> quarantineEntries,
                           boolean readOnly, int unsupportedVersion,
                           NBTTagCompound originalData) {
            this.parties = parties;
            this.repaired = repaired;
            this.quarantineEntries = quarantineEntries;
            this.readOnly = readOnly;
            this.unsupportedVersion = unsupportedVersion;
            this.originalData = originalData;
        }

        private static ReadResult success(Map<UUID, Party> parties, boolean repaired,
                                          List<NBTTagCompound> quarantineEntries) {
            return new ReadResult(
                    Collections.unmodifiableMap(new LinkedHashMap<UUID, Party>(parties)),
                    repaired,
                    Collections.unmodifiableList(copyEntries(quarantineEntries)),
                    false, -1, null);
        }

        private static ReadResult unsupported(NBTTagCompound source, int version) {
            return new ReadResult(Collections.<UUID, Party>emptyMap(), false,
                    Collections.<NBTTagCompound>emptyList(), true, version,
                    source == null ? new NBTTagCompound() : (NBTTagCompound) source.copy());
        }

        public Map<UUID, Party> getParties() {
            return this.parties;
        }

        public boolean wasRepaired() {
            return this.repaired;
        }

        public List<NBTTagCompound> getQuarantineEntriesCopy() {
            return Collections.unmodifiableList(copyEntries(this.quarantineEntries));
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

    private static final class PartyReadResult {
        private final Party party;
        private final boolean repaired;
        private final int unsupportedVersion;
        private final String failureReason;
        private final List<NBTTagCompound> quarantineEntries;

        private PartyReadResult(Party party, boolean repaired, int unsupportedVersion,
                                String failureReason,
                                List<NBTTagCompound> quarantineEntries) {
            this.party = party;
            this.repaired = repaired;
            this.unsupportedVersion = unsupportedVersion;
            this.failureReason = failureReason;
            this.quarantineEntries = quarantineEntries;
        }

        private static PartyReadResult success(Party party, boolean repaired,
                                               List<NBTTagCompound> entries) {
            return new PartyReadResult(party, repaired, -1, "",
                    entries == null ? Collections.<NBTTagCompound>emptyList() : entries);
        }

        private static PartyReadResult failed(boolean repaired, String reason) {
            return new PartyReadResult(null, repaired, -1, reason,
                    Collections.<NBTTagCompound>emptyList());
        }

        private static PartyReadResult unsupported(int version) {
            return new PartyReadResult(null, false, version, "unsupported_version",
                    Collections.<NBTTagCompound>emptyList());
        }
    }

    private static final class MemberReadResult {
        private final PartyMember member;
        private final boolean repaired;
        private final int unsupportedVersion;
        private final String failureReason;

        private MemberReadResult(PartyMember member, boolean repaired,
                                 int unsupportedVersion, String failureReason) {
            this.member = member;
            this.repaired = repaired;
            this.unsupportedVersion = unsupportedVersion;
            this.failureReason = failureReason;
        }

        private static MemberReadResult success(PartyMember member, boolean repaired) {
            return new MemberReadResult(member, repaired, -1, "");
        }

        private static MemberReadResult failed(boolean repaired, String reason) {
            return new MemberReadResult(null, repaired, -1, reason);
        }

        private static MemberReadResult unsupported(int version) {
            return new MemberReadResult(null, false, version, "unsupported_version");
        }
    }

    private static final class QuarantineReadResult {
        private final boolean supported;
        private final int unsupportedVersion;
        private final boolean repaired;
        private final List<NBTTagCompound> entries;

        private QuarantineReadResult(boolean supported, int unsupportedVersion,
                                     boolean repaired, List<NBTTagCompound> entries) {
            this.supported = supported;
            this.unsupportedVersion = unsupportedVersion;
            this.repaired = repaired;
            this.entries = entries;
        }

        private static QuarantineReadResult success(List<NBTTagCompound> entries,
                                                    boolean repaired) {
            return new QuarantineReadResult(true, -1, repaired, entries);
        }

        private static QuarantineReadResult unsupported(int version) {
            return new QuarantineReadResult(false, version, false,
                    Collections.<NBTTagCompound>emptyList());
        }
    }
}
