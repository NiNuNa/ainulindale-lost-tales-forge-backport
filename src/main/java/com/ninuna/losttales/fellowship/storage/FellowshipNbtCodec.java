package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipIcon;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.storage.NbtQuarantine;
import com.ninuna.losttales.storage.NbtTags;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Versioned NBT codec for the world's fellowships: each fellowship with its
 * members, guides, icon and switches, the fellowship each character travels
 * with, and the LOTR fellowship behind each of ours.
 *
 * <p>The root, each fellowship and each member are read only at the version
 * this build writes; any other version keeps the whole store as it is,
 * read-only. A fellowship or a member that lacks a key this build always
 * writes, or holds a value it cannot read, goes to the quarantine whole and
 * is never filled in.</p>
 */
public final class FellowshipNbtCodec {

    public static final int CURRENT_ROOT_DATA_VERSION = 2;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_FELLOWSHIPS = "Fellowships";
    private static final String TAG_MEMBERS = "Members";
    private static final String TAG_GUIDES = "Guides";
    private static final String TAG_SWITCHES = "Switches";
    private static final String TAG_ICON_ITEM = "IconItem";
    private static final String TAG_ICON_DAMAGE = "IconDamage";
    private static final String TAG_TRAVELLING = "Travelling";
    private static final String TAG_MIRRORS = "Mirrors";
    private static final String TAG_MIRROR_UUID = "MirrorUUID";
    private static final String TAG_REASON = "Reason";
    private static final String TAG_FELLOWSHIP_INDEX = "FellowshipIndex";
    private static final String TAG_MEMBER_INDEX = "MemberIndex";
    private static final String TAG_ORIGINAL_DATA = "OriginalData";

    private static final String TAG_FELLOWSHIP_UUID = "FellowshipUUID";
    private static final String TAG_LEADER_CHARACTER_UUID = "LeaderCharacterUUID";
    private static final String TAG_NAME = "Name";
    private static final String TAG_CHARACTER_UUID = "CharacterUUID";
    private static final String TAG_OWNER_UUID = "OwnerUUID";
    private static final String TAG_CHARACTER_NAME = "CharacterName";
    private static final String TAG_COLOR = "Color";
    private static final String TAG_CREATED_AT = "CreatedAt";
    private static final String TAG_JOINED_AT = "JoinedAt";
    private static final String TAG_REVISION = "Revision";

    private FellowshipNbtCodec() {}

    public static void write(NBTTagCompound output, Collection<Fellowship> fellowships,
                             Map<UUID, UUID> travelling, Map<UUID, UUID> mirrors,
                             Collection<NBTTagCompound> quarantinedEntries) {
        output.setInteger(TAG_DATA_VERSION, CURRENT_ROOT_DATA_VERSION);

        ArrayList<Fellowship> sortedFellowships = new ArrayList<Fellowship>();
        if (fellowships != null) {
            sortedFellowships.addAll(fellowships);
        }
        Collections.sort(sortedFellowships, new Comparator<Fellowship>() {
            @Override
            public int compare(Fellowship left, Fellowship right) {
                return left.getFellowshipId().toString().compareTo(right.getFellowshipId().toString());
            }
        });

        NBTTagList fellowshipList = new NBTTagList();
        for (Fellowship fellowship : sortedFellowships) {
            if (fellowship != null && fellowship.getMemberCount() > 0 && fellowship.hasValidLeader()) {
                fellowshipList.appendTag(writeFellowship(fellowship));
            }
        }
        output.setTag(TAG_FELLOWSHIPS, fellowshipList);
        output.setTag(TAG_TRAVELLING, writePairs(travelling, TAG_CHARACTER_UUID,
                TAG_FELLOWSHIP_UUID));
        output.setTag(TAG_MIRRORS, writePairs(mirrors, TAG_FELLOWSHIP_UUID,
                TAG_MIRROR_UUID));
        NbtQuarantine.write(output, quarantinedEntries);
    }

    public static ReadResult read(NBTTagCompound source) {
        NBTTagCompound safeSource = source == null ? new NBTTagCompound() : source;
        int version = versionOf(safeSource);
        if (version != CURRENT_ROOT_DATA_VERSION) {
            LostTalesLog.warning("Fellowship data root uses unsupported version %d; data will remain read-only",
                    Integer.valueOf(version));
            return ReadResult.unsupported(safeSource, version);
        }

        NbtQuarantine.Read quarantineResult = NbtQuarantine.readCurrentVersionOnly(safeSource);
        if (!quarantineResult.isSupported()) {
            LostTalesLog.warning("Fellowship quarantine data is malformed or uses unsupported "
                            + "version %d; data will remain read-only",
                    Integer.valueOf(quarantineResult.getUnsupportedVersion()));
            return ReadResult.unsupported(safeSource, quarantineResult.getUnsupportedVersion());
        }
        boolean repaired = quarantineResult.isRepaired();
        ArrayList<NBTTagCompound> quarantine =
                new ArrayList<NBTTagCompound>(quarantineResult.getEntries());

        if (safeSource.hasKey(TAG_FELLOWSHIPS)
                && !safeSource.hasKey(TAG_FELLOWSHIPS, Constants.NBT.TAG_LIST)) {
            LostTalesLog.warning("Fellowship data root has a malformed fellowship list; preserving data read-only");
            return ReadResult.unsupported(safeSource, -1);
        }
        if (!safeSource.hasKey(TAG_FELLOWSHIPS, Constants.NBT.TAG_LIST)) {
            repaired = true;
        }

        LinkedHashMap<UUID, Fellowship> fellowships = new LinkedHashMap<UUID, Fellowship>();
        NBTTagList fellowshipList = safeSource.getTagList(TAG_FELLOWSHIPS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < fellowshipList.tagCount(); i++) {
            NBTTagCompound rawFellowship = fellowshipList.getCompoundTagAt(i);
            FellowshipReadResult fellowshipResult = readFellowship(rawFellowship, i);
            if (fellowshipResult.unsupportedVersion >= 0) {
                return ReadResult.unsupported(safeSource, fellowshipResult.unsupportedVersion);
            }
            repaired |= fellowshipResult.repaired;
            quarantine.addAll(fellowshipResult.quarantineEntries);
            Fellowship fellowship = fellowshipResult.fellowship;
            if (fellowship == null) {
                quarantine.add(createQuarantineEntry(
                        fellowshipResult.failureReason, i, -1, rawFellowship));
                repaired = true;
                continue;
            }
            if (fellowships.containsKey(fellowship.getFellowshipId())) {
                quarantine.add(createQuarantineEntry(
                        "duplicate_fellowship_uuid", i, -1, rawFellowship));
                repaired = true;
                LostTalesLog.warning("Quarantining duplicate fellowship UUID %s at index %d",
                        fellowship.getFellowshipId(), Integer.valueOf(i));
                continue;
            }
            fellowships.put(fellowship.getFellowshipId(), fellowship);
        }
        Map<UUID, UUID> travelling = readPairs(safeSource, TAG_TRAVELLING,
                TAG_CHARACTER_UUID, TAG_FELLOWSHIP_UUID);
        Map<UUID, UUID> mirrors = readPairs(safeSource, TAG_MIRRORS,
                TAG_FELLOWSHIP_UUID, TAG_MIRROR_UUID);
        return ReadResult.success(fellowships, travelling, mirrors, repaired,
                quarantine);
    }

    /** The data version a record carries; zero for a record that names none. */
    private static int versionOf(NBTTagCompound source) {
        return source.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? source.getInteger(TAG_DATA_VERSION) : 0;
    }

    public static NBTTagCompound createQuarantineEntry(String reason,
                                                        UUID fellowshipId,
                                                        UUID identityId) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(TAG_REASON, reason == null ? "unknown" : reason);
        if (fellowshipId != null) {
            NbtTags.writeUuid(entry, TAG_FELLOWSHIP_UUID, fellowshipId);
        }
        if (identityId != null) {
            NbtTags.writeUuid(entry, TAG_CHARACTER_UUID, identityId);
        }
        return entry;
    }

    private static NBTTagList writePairs(Map<UUID, UUID> pairs, String keyTag,
                                         String valueTag) {
        NBTTagList list = new NBTTagList();
        if (pairs == null) {
            return list;
        }
        for (Map.Entry<UUID, UUID> pair : pairs.entrySet()) {
            if (pair.getKey() == null || pair.getValue() == null) {
                continue;
            }
            NBTTagCompound entry = new NBTTagCompound();
            NbtTags.writeUuid(entry, keyTag, pair.getKey());
            NbtTags.writeUuid(entry, valueTag, pair.getValue());
            list.appendTag(entry);
        }
        return list;
    }

    private static Map<UUID, UUID> readPairs(NBTTagCompound source, String listTag,
                                             String keyTag, String valueTag) {
        Map<UUID, UUID> pairs = new LinkedHashMap<UUID, UUID>();
        NBTTagList list = source.getTagList(listTag, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            UUID key = NbtTags.readUuid(entry, keyTag);
            UUID value = NbtTags.readUuid(entry, valueTag);
            if (key != null && value != null) {
                pairs.put(key, value);
            }
        }
        return pairs;
    }

    private static NBTTagCompound writeFellowship(Fellowship fellowship) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, Fellowship.CURRENT_DATA_VERSION);
        NbtTags.writeUuid(tag, TAG_FELLOWSHIP_UUID, fellowship.getFellowshipId());
        NbtTags.writeUuid(tag, TAG_LEADER_CHARACTER_UUID, fellowship.getLeaderIdentityId());
        tag.setString(TAG_NAME, fellowship.getName());
        tag.setLong(TAG_CREATED_AT, fellowship.getCreatedAt());
        tag.setLong(TAG_REVISION, fellowship.getRevision());
        FellowshipIcon icon = fellowship.getIcon();
        if (icon != null) {
            tag.setString(TAG_ICON_ITEM, icon.getItemName());
            tag.setInteger(TAG_ICON_DAMAGE, icon.getDamage());
        }
        NBTTagList switches = new NBTTagList();
        for (FellowshipSwitch on : fellowship.getSwitchesOn()) {
            switches.appendTag(new NBTTagString(on.getId()));
        }
        tag.setTag(TAG_SWITCHES, switches);
        NBTTagList guides = new NBTTagList();
        for (UUID guide : fellowship.getGuides()) {
            NBTTagCompound guideTag = new NBTTagCompound();
            NbtTags.writeUuid(guideTag, TAG_CHARACTER_UUID, guide);
            guides.appendTag(guideTag);
        }
        tag.setTag(TAG_GUIDES, guides);

        NBTTagList members = new NBTTagList();
        for (FellowshipMember member : fellowship.getMembers()) {
            NBTTagCompound memberTag = new NBTTagCompound();
            memberTag.setInteger(TAG_DATA_VERSION, FellowshipMember.CURRENT_DATA_VERSION);
            NbtTags.writeUuid(memberTag, TAG_CHARACTER_UUID, member.getIdentityId());
            NbtTags.writeUuid(memberTag, TAG_OWNER_UUID, member.getOwnerId());
            memberTag.setString(TAG_CHARACTER_NAME, member.getCharacterName());
            memberTag.setLong(TAG_JOINED_AT, member.getJoinedAt());
            memberTag.setString(TAG_COLOR, member.getColor().getId());
            members.appendTag(memberTag);
        }
        tag.setTag(TAG_MEMBERS, members);
        return tag;
    }

    private static FellowshipReadResult readFellowship(NBTTagCompound source, int fellowshipIndex) {
        if (source == null) {
            return FellowshipReadResult.failed(true, "missing_fellowship");
        }
        int version = versionOf(source);
        if (version != Fellowship.CURRENT_DATA_VERSION) {
            LostTalesLog.warning("Fellowship at index %d uses unsupported version %d",
                    Integer.valueOf(fellowshipIndex), Integer.valueOf(version));
            return FellowshipReadResult.unsupported(version);
        }
        boolean repaired = false;

        UUID fellowshipId = NbtTags.readUuid(source, TAG_FELLOWSHIP_UUID);
        if (fellowshipId == null) {
            return FellowshipReadResult.failed(true, "missing_or_invalid_fellowship_uuid");
        }
        UUID leaderId = NbtTags.readUuid(source, TAG_LEADER_CHARACTER_UUID);
        if (leaderId == null) {
            return FellowshipReadResult.failed(true, "missing_or_invalid_leader_uuid");
        }
        if (!source.hasKey(TAG_CREATED_AT, Constants.NBT.TAG_LONG)
                || source.getLong(TAG_CREATED_AT) < 0L) {
            return FellowshipReadResult.failed(true, "missing_or_invalid_created_at");
        }
        long createdAt = source.getLong(TAG_CREATED_AT);
        if (!source.hasKey(TAG_REVISION, Constants.NBT.TAG_LONG)
                || source.getLong(TAG_REVISION) < 0L) {
            return FellowshipReadResult.failed(true, "missing_or_invalid_revision");
        }
        long revision = source.getLong(TAG_REVISION);
        if (!source.hasKey(TAG_MEMBERS, Constants.NBT.TAG_LIST)
                || !source.hasKey(TAG_GUIDES, Constants.NBT.TAG_LIST)
                || !source.hasKey(TAG_SWITCHES, Constants.NBT.TAG_LIST)) {
            return FellowshipReadResult.failed(true, "missing_or_malformed_list");
        }
        ArrayList<NBTTagCompound> quarantine = new ArrayList<NBTTagCompound>();
        String name = source.hasKey(TAG_NAME, Constants.NBT.TAG_STRING)
                ? source.getString(TAG_NAME) : "";
        if (!Fellowship.isWellFormedName(name)) {
            return FellowshipReadResult.failed(true, "invalid_fellowship_name");
        }
        FellowshipIcon icon = null;
        if (source.hasKey(TAG_ICON_ITEM, Constants.NBT.TAG_STRING)) {
            String item = source.getString(TAG_ICON_ITEM);
            int damage = source.getInteger(TAG_ICON_DAMAGE);
            if (FellowshipIcon.isWellFormed(item, damage)) {
                icon = new FellowshipIcon(item, damage);
            } else {
                quarantine.add(createQuarantineEntry(
                        "invalid_fellowship_icon", fellowshipIndex, -1, source));
                repaired = true;
            }
        }
        Set<FellowshipSwitch> switches = EnumSet.noneOf(FellowshipSwitch.class);
        NBTTagList switchList = source.getTagList(TAG_SWITCHES, Constants.NBT.TAG_STRING);
        for (int index = 0; index < switchList.tagCount(); index++) {
            FellowshipSwitch on = FellowshipSwitch.fromId(switchList.getStringTagAt(index));
            if (on == null) {
                return FellowshipReadResult.failed(true, "unknown_fellowship_switch");
            }
            switches.add(on);
        }

        NBTTagList memberList = source.getTagList(TAG_MEMBERS, Constants.NBT.TAG_COMPOUND);
        ArrayList<FellowshipMember> members = new ArrayList<FellowshipMember>();
        Set<UUID> identityIds = new HashSet<UUID>();
        for (int i = 0; i < memberList.tagCount(); i++) {
            NBTTagCompound rawMember = memberList.getCompoundTagAt(i);
            MemberReadResult memberResult = readMember(rawMember);
            if (memberResult.unsupportedVersion >= 0) {
                return FellowshipReadResult.unsupported(memberResult.unsupportedVersion);
            }
            repaired |= memberResult.repaired;
            FellowshipMember member = memberResult.member;
            if (member == null) {
                quarantine.add(createQuarantineEntry(
                        memberResult.failureReason, fellowshipIndex, i, rawMember));
                repaired = true;
                continue;
            }
            if (!identityIds.add(member.getIdentityId())) {
                quarantine.add(createQuarantineEntry(
                        "duplicate_member_character_uuid", fellowshipIndex, i, rawMember));
                repaired = true;
                continue;
            }
            if (members.size() >= Fellowship.MAX_MEMBERS) {
                quarantine.add(createQuarantineEntry(
                        "fellowship_member_limit_exceeded", fellowshipIndex, i, rawMember));
                repaired = true;
                continue;
            }
            members.add(member);
        }

        if (members.isEmpty()) {
            return FellowshipReadResult.failed(true, "fellowship_has_no_valid_members");
        }

        // A leader whose own entry went to the quarantine above is followed
        // by the member who joined first, as when a leader's character goes.
        if (!identityIds.contains(leaderId)) {
            leaderId = selectFirstMember(members).getIdentityId();
            repaired = true;
        }
        List<UUID> guides = new ArrayList<UUID>();
        NBTTagList guideList = source.getTagList(TAG_GUIDES, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < guideList.tagCount(); index++) {
            UUID guide = NbtTags.readUuid(guideList.getCompoundTagAt(index),
                    TAG_CHARACTER_UUID);
            if (guide != null && identityIds.contains(guide)) {
                guides.add(guide);
            } else {
                repaired = true;
            }
        }

        try {
            Fellowship fellowship = new Fellowship(fellowshipId, leaderId, members,
                    guides, name, icon, switches, createdAt, revision,
                    Fellowship.CURRENT_DATA_VERSION);
            return FellowshipReadResult.success(fellowship, repaired, quarantine);
        } catch (RuntimeException exception) {
            LostTalesLog.warning("Skipping invalid fellowship %s at index %d: %s",
                    fellowshipId, Integer.valueOf(fellowshipIndex), exception.toString());
            return FellowshipReadResult.failed(true, "invalid_fellowship_structure");
        }
    }

    private static MemberReadResult readMember(NBTTagCompound source) {
        if (source == null) {
            return MemberReadResult.failed(true, "missing_member");
        }
        int version = versionOf(source);
        if (version != FellowshipMember.CURRENT_DATA_VERSION) {
            return MemberReadResult.unsupported(version);
        }
        UUID identityId = NbtTags.readUuid(source, TAG_CHARACTER_UUID);
        UUID ownerId = NbtTags.readUuid(source, TAG_OWNER_UUID);
        if (identityId == null) {
            return MemberReadResult.failed(true, "missing_or_invalid_character_uuid");
        }
        if (ownerId == null) {
            return MemberReadResult.failed(true, "missing_or_invalid_owner_uuid");
        }

        String name = source.hasKey(TAG_CHARACTER_NAME, Constants.NBT.TAG_STRING)
                ? source.getString(TAG_CHARACTER_NAME) : "";
        if (name.trim().length() == 0) {
            return MemberReadResult.failed(true, "missing_or_blank_character_name");
        }
        if (!source.hasKey(TAG_JOINED_AT, Constants.NBT.TAG_LONG)
                || source.getLong(TAG_JOINED_AT) < 0L) {
            return MemberReadResult.failed(true, "missing_or_invalid_joined_at");
        }
        long joinedAt = source.getLong(TAG_JOINED_AT);
        FellowshipColor color = source.hasKey(TAG_COLOR, Constants.NBT.TAG_STRING)
                ? FellowshipColor.fromId(source.getString(TAG_COLOR)) : null;
        if (color == null) {
            return MemberReadResult.failed(true, "missing_or_unknown_color");
        }
        return MemberReadResult.success(
                new FellowshipMember(identityId, ownerId, name, joinedAt, color), false);
    }

    private static FellowshipMember selectFirstMember(List<FellowshipMember> members) {
        ArrayList<FellowshipMember> ordered = new ArrayList<FellowshipMember>(members);
        Collections.sort(ordered, new Comparator<FellowshipMember>() {
            @Override
            public int compare(FellowshipMember left, FellowshipMember right) {
                if (left.getJoinedAt() < right.getJoinedAt()) {
                    return -1;
                }
                if (left.getJoinedAt() > right.getJoinedAt()) {
                    return 1;
                }
                return left.getIdentityId().toString().compareTo(right.getIdentityId().toString());
            }
        });
        return ordered.get(0);
    }

    private static NBTTagCompound createQuarantineEntry(String reason,
                                                        int fellowshipIndex,
                                                        int memberIndex,
                                                        NBTTagCompound originalData) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(TAG_REASON, reason == null ? "unknown" : reason);
        if (fellowshipIndex >= 0) {
            entry.setInteger(TAG_FELLOWSHIP_INDEX, fellowshipIndex);
        }
        if (memberIndex >= 0) {
            entry.setInteger(TAG_MEMBER_INDEX, memberIndex);
        }
        entry.setTag(TAG_ORIGINAL_DATA, originalData == null
                ? new NBTTagCompound() : originalData.copy());
        return entry;
    }

    public static final class ReadResult {
        private final Map<UUID, Fellowship> fellowships;
        private final Map<UUID, UUID> travelling;
        private final Map<UUID, UUID> mirrors;
        private final boolean repaired;
        private final List<NBTTagCompound> quarantineEntries;
        private final boolean readOnly;
        private final int unsupportedVersion;
        private final NBTTagCompound originalData;

        private ReadResult(Map<UUID, Fellowship> fellowships, Map<UUID, UUID> travelling,
                           Map<UUID, UUID> mirrors, boolean repaired,
                           List<NBTTagCompound> quarantineEntries,
                           boolean readOnly, int unsupportedVersion,
                           NBTTagCompound originalData) {
            this.fellowships = fellowships;
            this.travelling = travelling;
            this.mirrors = mirrors;
            this.repaired = repaired;
            this.quarantineEntries = quarantineEntries;
            this.readOnly = readOnly;
            this.unsupportedVersion = unsupportedVersion;
            this.originalData = originalData;
        }

        private static ReadResult success(Map<UUID, Fellowship> fellowships,
                                          Map<UUID, UUID> travelling,
                                          Map<UUID, UUID> mirrors, boolean repaired,
                                          List<NBTTagCompound> quarantineEntries) {
            return new ReadResult(
                    Collections.unmodifiableMap(new LinkedHashMap<UUID, Fellowship>(fellowships)),
                    Collections.unmodifiableMap(new LinkedHashMap<UUID, UUID>(travelling)),
                    Collections.unmodifiableMap(new LinkedHashMap<UUID, UUID>(mirrors)),
                    repaired,
                    Collections.unmodifiableList(copyEntries(quarantineEntries)),
                    false, -1, null);
        }

        private static ReadResult unsupported(NBTTagCompound source, int version) {
            return new ReadResult(Collections.<UUID, Fellowship>emptyMap(),
                    Collections.<UUID, UUID>emptyMap(), Collections.<UUID, UUID>emptyMap(),
                    false, Collections.<NBTTagCompound>emptyList(), true, version,
                    source == null ? new NBTTagCompound() : (NBTTagCompound) source.copy());
        }

        public Map<UUID, Fellowship> getFellowships() {
            return this.fellowships;
        }

        /** The fellowship each character chose to travel with. */
        public Map<UUID, UUID> getTravelling() {
            return this.travelling;
        }

        /** The LOTR fellowship behind each of ours. */
        public Map<UUID, UUID> getMirrors() {
            return this.mirrors;
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

    private static final class FellowshipReadResult {
        private final Fellowship fellowship;
        private final boolean repaired;
        private final int unsupportedVersion;
        private final String failureReason;
        private final List<NBTTagCompound> quarantineEntries;

        private FellowshipReadResult(Fellowship fellowship, boolean repaired, int unsupportedVersion,
                                String failureReason,
                                List<NBTTagCompound> quarantineEntries) {
            this.fellowship = fellowship;
            this.repaired = repaired;
            this.unsupportedVersion = unsupportedVersion;
            this.failureReason = failureReason;
            this.quarantineEntries = quarantineEntries;
        }

        private static FellowshipReadResult success(Fellowship fellowship, boolean repaired,
                                               List<NBTTagCompound> entries) {
            return new FellowshipReadResult(fellowship, repaired, -1, "",
                    entries == null ? Collections.<NBTTagCompound>emptyList() : entries);
        }

        private static FellowshipReadResult failed(boolean repaired, String reason) {
            return new FellowshipReadResult(null, repaired, -1, reason,
                    Collections.<NBTTagCompound>emptyList());
        }

        private static FellowshipReadResult unsupported(int version) {
            return new FellowshipReadResult(null, false, version, "unsupported_version",
                    Collections.<NBTTagCompound>emptyList());
        }
    }

    private static final class MemberReadResult {
        private final FellowshipMember member;
        private final boolean repaired;
        private final int unsupportedVersion;
        private final String failureReason;

        private MemberReadResult(FellowshipMember member, boolean repaired,
                                 int unsupportedVersion, String failureReason) {
            this.member = member;
            this.repaired = repaired;
            this.unsupportedVersion = unsupportedVersion;
            this.failureReason = failureReason;
        }

        private static MemberReadResult success(FellowshipMember member, boolean repaired) {
            return new MemberReadResult(member, repaired, -1, "");
        }

        private static MemberReadResult failed(boolean repaired, String reason) {
            return new MemberReadResult(null, repaired, -1, reason);
        }

        private static MemberReadResult unsupported(int version) {
            return new MemberReadResult(null, false, version, "unsupported_version");
        }
    }
}
