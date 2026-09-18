package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Server-to-client: who a conversation is shown to, as its member list
 * lists them — a channel's, a whisper's two people, or the player alone in
 * their own console. For each member the identity the conversation shows
 * them as — the character they speak as in character, the account out of
 * character — its name, colour, head and LOTR title, the group the list
 * stands them in — their faction, or their highest role — and whether
 * they are here now or absent; and how many more absent members the
 * answer leaves out past {@link #MAX_MEMBERS}. The answer to
 * {@link LostTalesChatMembersRequestPacket}, naming the conversation by
 * the key the request gave it; the server decides every other field.
 *
 * <p>Every answer carries its {@link #getFingerprint fingerprint}, which
 * the client sends back with its next request: where the answer would be
 * the same, the server sends only the word that it is ({@link
 * #unchanged}), so a list of hundreds is not sent again every few seconds.
 * A payload over its bounds is refused whole.</p>
 */
public final class LostTalesChatMembersPacket implements IMessage {
    /** The most members one answer lists. */
    public static final int MAX_MEMBERS = 1000;
    /** The most absent members one answer may say it leaves out. */
    public static final int MAX_UNLISTED = 1000000;
    public static final int MAX_CHANNEL_ID_BYTES = 64;
    /** The client's own name for the conversation, which the answer carries back. */
    public static final int MAX_CONVERSATION_KEY_BYTES = 256;
    public static final int MAX_ACCOUNT_BYTES = 64;
    public static final int MAX_NAME_BYTES = 256;
    public static final int MAX_SKIN_ID_BYTES = 128;
    public static final int MAX_TITLE_BYTES = 256;
    public static final int MAX_GROUP_KEY_BYTES = 128;
    public static final int MAX_GROUP_NAME_BYTES = 128;
    private static final int MAX_MEMBER_BYTES = 16 + 5 + MAX_ACCOUNT_BYTES
            + 17 + 5 + MAX_NAME_BYTES + 4 + 5 + MAX_SKIN_ID_BYTES
            + 5 + MAX_TITLE_BYTES + 4 + 5 + MAX_GROUP_KEY_BYTES
            + 5 + MAX_GROUP_NAME_BYTES + 2 + 1;
    private static final int MAX_PACKET_BYTES = 5 + MAX_CHANNEL_ID_BYTES
            + 5 + MAX_CONVERSATION_KEY_BYTES + 8 + 1 + 4 + 2
            + MAX_MEMBERS * MAX_MEMBER_BYTES;
    private static final long FINGERPRINT_START = 0xCBF29CE484222325L;
    private static final long FINGERPRINT_PRIME = 0x100000001B3L;

    /** One member of a conversation, as its list shows them. */
    public static final class Member {
        private final UUID playerId;
        private final String account;
        private final UUID characterId;
        private final String name;
        private final int nameColor;
        private final String skinId;
        private final String title;
        private final int titleColor;
        private final String groupKey;
        private final String groupName;
        private final int groupOrder;
        private final boolean online;
        private final boolean npc;

        /**
         * {@code characterId} is null, and {@code skinId} empty, for a
         * member shown as the account. {@code groupKey} empty stands the
         * member in the list's plain group; the group is not asked of a
         * member who is absent, whose group is the absent one.
         */
        public Member(UUID playerId, String account, UUID characterId,
                      String name, int nameColor, String skinId, String title,
                      int titleColor, String groupKey, String groupName,
                      int groupOrder, boolean online) {
            this(playerId, account, characterId, name, nameColor, skinId,
                    title, titleColor, groupKey, groupName, groupOrder, online,
                    false);
        }

        private Member(UUID playerId, String account, UUID characterId,
                       String name, int nameColor, String skinId, String title,
                       int titleColor, String groupKey, String groupName,
                       int groupOrder, boolean online, boolean npc) {
            this.playerId = playerId;
            this.account = account == null ? "" : account;
            this.characterId = characterId;
            this.name = name == null ? "" : name;
            this.nameColor = nameColor & 0xFFFFFF;
            this.skinId = skinId == null || (characterId == null && !npc)
                    ? "" : skinId;
            this.title = title == null ? "" : title;
            this.titleColor = titleColor & 0xFFFFFF;
            this.groupKey = groupKey == null ? "" : groupKey;
            this.groupName = groupName == null ? "" : groupName;
            this.groupOrder = Math.max(0, Math.min(0xFFFF, groupOrder));
            this.online = online;
            this.npc = npc;
        }

        /**
         * An NPC as the list of a conversation with it shows it, here and
         * with {@code portrait} for its head: the client lists it itself,
         * since the server knows nothing of NPCs, and never sends it.
         */
        public static Member npc(UUID npcId, String name, int nameColor,
                                 String portrait, String groupKey,
                                 String groupName, int groupOrder) {
            return new Member(npcId, "", null, name, nameColor, portrait, "",
                    nameColor, groupKey, groupName, groupOrder, true, true);
        }

        public UUID getPlayerId() { return this.playerId; }
        public String getAccount() { return this.account; }
        public UUID getCharacterId() { return this.characterId; }
        public String getName() { return this.name; }
        public int getNameColor() { return this.nameColor; }
        /** The character's skin snapshot, or an NPC's portrait; empty for an account. */
        public String getSkinId() { return this.skinId; }
        public String getTitle() { return this.title; }
        public int getTitleColor() { return this.titleColor; }
        public String getGroupKey() { return this.groupKey; }
        public String getGroupName() { return this.groupName; }
        public int getGroupOrder() { return this.groupOrder; }
        public boolean isOnline() { return this.online; }
        /** Whether the member is an NPC the client listed itself. */
        public boolean isNpc() { return this.npc; }

        /** Whether every field is within what the wire carries; an NPC never is. */
        boolean fits() {
            return !this.npc && this.playerId != null && this.name.length() > 0
                    && LostTalesPacketCodec.isUtf8WithinLimit(this.account,
                            MAX_ACCOUNT_BYTES)
                    && LostTalesPacketCodec.isUtf8WithinLimit(this.name,
                            MAX_NAME_BYTES)
                    && LostTalesPacketCodec.isUtf8WithinLimit(this.skinId,
                            MAX_SKIN_ID_BYTES)
                    && LostTalesPacketCodec.isUtf8WithinLimit(this.title,
                            MAX_TITLE_BYTES)
                    && LostTalesPacketCodec.isUtf8WithinLimit(this.groupKey,
                            MAX_GROUP_KEY_BYTES)
                    && LostTalesPacketCodec.isUtf8WithinLimit(this.groupName,
                            MAX_GROUP_NAME_BYTES);
        }

        /** Every field folded into one number, for the answer's fingerprint. */
        int contentHash() {
            int hash = this.playerId == null ? 0 : this.playerId.hashCode();
            hash = 31 * hash + this.account.hashCode();
            hash = 31 * hash + (this.characterId == null ? 0
                    : this.characterId.hashCode());
            hash = 31 * hash + this.name.hashCode();
            hash = 31 * hash + this.nameColor;
            hash = 31 * hash + this.skinId.hashCode();
            hash = 31 * hash + this.title.hashCode();
            hash = 31 * hash + this.titleColor;
            hash = 31 * hash + this.groupKey.hashCode();
            hash = 31 * hash + this.groupName.hashCode();
            hash = 31 * hash + this.groupOrder;
            return 31 * hash + (this.online ? 1 : 0);
        }
    }

    /**
     * The order a list stands its members in: those here first, by
     * group — a role's place, or a faction's name with Unaligned last, the
     * ungrouped after every group — then by name; the absent after them,
     * by name.
     */
    public static final Comparator<Member> ORDER = new Comparator<Member>() {
        @Override
        public int compare(Member one, Member other) {
            if (one.isOnline() != other.isOnline()) {
                return one.isOnline() ? -1 : 1;
            }
            int byGroup = compareGroups(one, other);
            if (byGroup != 0) {
                return byGroup;
            }
            return one.getName().toLowerCase(Locale.ROOT).compareTo(
                    other.getName().toLowerCase(Locale.ROOT));
        }
    };

    private static int compareGroups(Member one, Member other) {
        boolean oneGrouped = one.getGroupKey().length() > 0;
        boolean otherGrouped = other.getGroupKey().length() > 0;
        if (oneGrouped != otherGrouped) {
            return oneGrouped ? -1 : 1;
        }
        if (one.getGroupOrder() != other.getGroupOrder()) {
            return one.getGroupOrder() < other.getGroupOrder() ? -1 : 1;
        }
        boolean oneUnaligned = LotrCharacterAdapter.UNALIGNED_FACTION_ID
                .equals(one.getGroupKey());
        boolean otherUnaligned = LotrCharacterAdapter.UNALIGNED_FACTION_ID
                .equals(other.getGroupKey());
        if (oneUnaligned != otherUnaligned) {
            return oneUnaligned ? 1 : -1;
        }
        int byName = one.getGroupName().toLowerCase(Locale.ROOT).compareTo(
                other.getGroupName().toLowerCase(Locale.ROOT));
        return byName != 0 ? byName
                : one.getGroupKey().compareTo(other.getGroupKey());
    }

    private String channelId = "";
    private String conversationKey = "";
    private long fingerprint;
    private boolean unchanged;
    private int unlisted;
    private List<Member> members = Collections.emptyList();
    private boolean malformed;

    public LostTalesChatMembersPacket() {}

    /**
     * The members of the conversation the client calls
     * {@code conversationKey}, on {@code channel}, in the order given, and
     * {@code unlisted} more absent ones left out; any past
     * {@link #MAX_MEMBERS}, and any that would not fit the wire, are left
     * out too.
     */
    public LostTalesChatMembersPacket(ChatChannel channel,
                                      String conversationKey,
                                      List<Member> members, int unlisted) {
        this.channelId = channel == null ? "" : channel.getId();
        this.conversationKey = conversationKey == null ? "" : conversationKey;
        List<Member> kept = new ArrayList<Member>();
        if (members != null) {
            for (Member member : members) {
                if (member != null && member.fits()
                        && kept.size() < MAX_MEMBERS) {
                    kept.add(member);
                }
            }
        }
        this.members = Collections.unmodifiableList(kept);
        this.unlisted = Math.max(0, Math.min(MAX_UNLISTED, unlisted));
        this.fingerprint = fingerprintOf(this.members, this.unlisted);
    }

    /** The word that the answer whose fingerprint the client sent still stands. */
    public static LostTalesChatMembersPacket unchanged(ChatChannel channel,
                                                       String conversationKey,
                                                       long fingerprint) {
        LostTalesChatMembersPacket packet = new LostTalesChatMembersPacket();
        packet.channelId = channel == null ? "" : channel.getId();
        packet.conversationKey = conversationKey == null ? "" : conversationKey;
        packet.fingerprint = fingerprint;
        packet.unchanged = true;
        return packet;
    }

    /**
     * An answer's fingerprint: its members, in order, and how many it
     * leaves out, folded into one number. Never zero, which a request
     * sends for no answer held.
     */
    static long fingerprintOf(List<Member> members, int unlisted) {
        long fingerprint = FINGERPRINT_START;
        fingerprint = (fingerprint ^ members.size()) * FINGERPRINT_PRIME;
        fingerprint = (fingerprint ^ unlisted) * FINGERPRINT_PRIME;
        for (Member member : members) {
            fingerprint = (fingerprint ^ (member.contentHash() & 0xFFFFFFFFL))
                    * FINGERPRINT_PRIME;
        }
        return fingerprint == 0L ? 1L : fingerprint;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat members packet size");
            }
            String channel = LostTalesPacketCodec.readUtf8String(buffer,
                    MAX_CHANNEL_ID_BYTES);
            if (ChatChannel.fromId(channel) == null) {
                throw new LostTalesPacketCodec.DecodeException(
                        "members of no channel");
            }
            String conversation = LostTalesPacketCodec.readUtf8String(buffer,
                    MAX_CONVERSATION_KEY_BYTES);
            long heldBy = buffer.readLong();
            if (heldBy == 0L) {
                throw new LostTalesPacketCodec.DecodeException(
                        "an answer without a fingerprint");
            }
            boolean same = buffer.readBoolean();
            int left = 0;
            List<Member> read = new ArrayList<Member>();
            if (!same) {
                left = buffer.readInt();
                if (left < 0 || left > MAX_UNLISTED) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "an impossible count of absent members");
                }
                int count = buffer.readUnsignedShort();
                if (count > MAX_MEMBERS) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "too many members");
                }
                for (int index = 0; index < count; index++) {
                    read.add(readMember(buffer));
                }
            }
            LostTalesPacketCodec.requireFinished(buffer);
            this.channelId = channel;
            this.conversationKey = conversation;
            this.fingerprint = heldBy;
            this.unchanged = same;
            this.unlisted = left;
            this.members = Collections.unmodifiableList(read);
        } catch (RuntimeException failure) {
            this.malformed = true;
            this.channelId = "";
            this.conversationKey = "";
            this.fingerprint = 0L;
            this.unchanged = false;
            this.unlisted = 0;
            this.members = Collections.emptyList();
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    private static Member readMember(ByteBuf buffer) {
        UUID playerId = readUuid(buffer);
        String account = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_ACCOUNT_BYTES);
        UUID characterId = buffer.readBoolean() ? readUuid(buffer) : null;
        String name = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_NAME_BYTES);
        int nameColor = buffer.readInt();
        String skinId = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_SKIN_ID_BYTES);
        String title = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_TITLE_BYTES);
        int titleColor = buffer.readInt();
        String groupKey = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_GROUP_KEY_BYTES);
        String groupName = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_GROUP_NAME_BYTES);
        int groupOrder = buffer.readUnsignedShort();
        boolean online = buffer.readBoolean();
        if (name.length() == 0) {
            throw new LostTalesPacketCodec.DecodeException(
                    "a member without a name");
        }
        return new Member(playerId, account, characterId, name, nameColor,
                skinId, title, titleColor, groupKey, groupName, groupOrder,
                online);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        LostTalesPacketCodec.writeUtf8String(buffer, this.channelId,
                MAX_CHANNEL_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, this.conversationKey,
                MAX_CONVERSATION_KEY_BYTES);
        buffer.writeLong(this.fingerprint);
        buffer.writeBoolean(this.unchanged);
        if (this.unchanged) {
            return;
        }
        buffer.writeInt(this.unlisted);
        buffer.writeShort(this.members.size());
        for (Member member : this.members) {
            writeUuid(buffer, member.playerId);
            LostTalesPacketCodec.writeUtf8String(buffer, member.account,
                    MAX_ACCOUNT_BYTES);
            buffer.writeBoolean(member.characterId != null);
            if (member.characterId != null) {
                writeUuid(buffer, member.characterId);
            }
            LostTalesPacketCodec.writeUtf8String(buffer, member.name,
                    MAX_NAME_BYTES);
            buffer.writeInt(member.nameColor);
            LostTalesPacketCodec.writeUtf8String(buffer, member.skinId,
                    MAX_SKIN_ID_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, member.title,
                    MAX_TITLE_BYTES);
            buffer.writeInt(member.titleColor);
            LostTalesPacketCodec.writeUtf8String(buffer, member.groupKey,
                    MAX_GROUP_KEY_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, member.groupName,
                    MAX_GROUP_NAME_BYTES);
            buffer.writeShort(member.groupOrder);
            buffer.writeBoolean(member.online);
        }
    }

    private static UUID readUuid(ByteBuf buffer) {
        return new UUID(buffer.readLong(), buffer.readLong());
    }

    private static void writeUuid(ByteBuf buffer, UUID id) {
        buffer.writeLong(id.getMostSignificantBits());
        buffer.writeLong(id.getLeastSignificantBits());
    }

    /** The channel whose conversation the members are of. */
    public ChatChannel getChannel() { return ChatChannel.fromId(this.channelId); }

    /** The client's own name for the conversation, as its request gave it. */
    public String getConversationKey() { return this.conversationKey; }

    /** The answer's fingerprint, what the next request carries back; never zero. */
    public long getFingerprint() { return this.fingerprint; }

    /** Whether this is only the word that the answer the client holds still stands. */
    public boolean isUnchanged() { return this.unchanged; }

    /** How many more absent members the answer leaves out. */
    public int getUnlisted() { return this.unlisted; }

    public List<Member> getMembers() { return this.members; }

    public boolean isMalformed() { return this.malformed; }

    public static final class Handler
            implements IMessageHandler<LostTalesChatMembersPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatMembersPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleChatMembers(message);
                }
            });
            return null;
        }
    }
}
