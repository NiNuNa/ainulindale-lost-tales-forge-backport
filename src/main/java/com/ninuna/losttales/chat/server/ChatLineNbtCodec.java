package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.share.ChatQuestCard;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.storage.NbtTags;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

/**
 * One kept chat line as a compound of its own: every field by name, the
 * roles it was signed with and the roles it calls by id, so a change to
 * the wire's layout or to the order of the roles leaves the save alone.
 *
 * <p>A line read back is built again through the packet's own
 * constructor and then put through the wire both ways: what does not
 * come back as a line every client reads is not a line, and the entry
 * holding it is quarantined. A channel the server no longer has reads
 * as no line either. A role the server no longer has is left off.</p>
 */
final class ChatLineNbtCodec {

    private static final String TAG_CHANNEL = "Channel";
    private static final String TAG_SENDER = "Sender";
    private static final String TAG_IDENTITY = "Identity";
    private static final String TAG_ACCOUNT = "Account";
    private static final String TAG_TITLE = "Title";
    private static final String TAG_TITLE_COLOR = "TitleColor";
    private static final String TAG_NAME_COLOR = "NameColor";
    private static final String TAG_MESSAGE = "Message";
    private static final String TAG_ACTION = "Action";
    private static final String TAG_TIMESTAMP = "Timestamp";
    private static final String TAG_SKIN = "Skin";
    private static final String TAG_SHARES = "Shares";
    private static final String TAG_FACTION = "Faction";
    private static final String TAG_PARTNER = "Partner";
    private static final String TAG_PARTNER_IDENTITY = "PartnerIdentity";
    private static final String TAG_ECHO = "Echo";
    private static final String TAG_ROLES = "Roles";
    private static final String TAG_ACCOUNT_LINE = "AccountLine";
    private static final String TAG_CHARACTER = "Character";
    private static final String TAG_OWN_CHARACTER = "OwnCharacter";
    private static final String TAG_PARTNER_CHARACTER = "PartnerCharacter";
    private static final String TAG_SCOPE = "Scope";
    private static final String TAG_MESSAGE_ID = "MessageId";
    private static final String TAG_REPLY = "Reply";
    private static final String TAG_BODY = "Body";
    private static final String TAG_NAMED = "Named";
    private static final String TAG_TAB = "Tab";
    private static final String TAG_CALLED_ROLES = "CalledRoles";
    private static final String TAG_NOT_IN_WORLD = "NotInWorld";
    private static final String TAG_TYPED = "Typed";
    private static final String TAG_UNSEEN = "Unseen";

    private static final String TAG_REPLY_KIND = "Kind";
    private static final String TAG_REPLY_AUTHOR = "Author";
    private static final String TAG_REPLY_EXCERPT = "Excerpt";
    private static final String TAG_REPLY_COLOR = "AuthorColor";
    private static final String TAG_REPLY_ACTION = "Action";
    private static final String TAG_REPLY_LINK = "Link";
    private static final String REPLY_UNKEPT = "unkept";
    private static final String REPLY_QUOTE = "quote";
    private static final String REPLY_FORWARD = "forward";

    private static final String TAG_PLAYER = "Player";

    private static final String TAG_TOKEN = "Token";
    private static final String TAG_KIND = "Kind";
    private static final String TAG_STACK = "Stack";
    private static final String TAG_MARKER_ID = "Id";
    private static final String TAG_MARKER_NAME = "Name";
    private static final String TAG_MARKER_NAMED_AFTER = "NamedAfter";
    private static final String TAG_MARKER_ICON = "Icon";
    private static final String TAG_MARKER_COLOR = "Color";
    private static final String TAG_MARKER_DIMENSION = "Dimension";
    private static final String TAG_MARKER_X = "X";
    private static final String TAG_MARKER_Z = "Z";
    private static final String TAG_QUEST_REFERENCE = "Reference";
    private static final String TAG_QUEST_CARD = "Card";
    private static final String TAG_QUEST_JOINABLE = "Joinable";

    private static final String TAG_CARD_SOURCE = "Source";
    private static final String TAG_CARD_TITLE = "Title";
    private static final String TAG_CARD_CATEGORY = "Category";
    private static final String TAG_CARD_OBJECTIVES = "Objectives";
    private static final String TAG_CARD_REWARDS = "Rewards";
    private static final String TAG_OBJECTIVE_ID = "Id";
    private static final String TAG_OBJECTIVE_TYPE = "Type";
    private static final String TAG_OBJECTIVE_TARGETS = "Targets";
    private static final String TAG_OBJECTIVE_COUNT = "Count";
    private static final String TAG_OBJECTIVE_PROGRESS = "Progress";
    private static final String TAG_OBJECTIVE_OPTIONAL = "Optional";
    private static final String TAG_OBJECTIVE_TEXT = "Text";
    private static final String TAG_KEY = "Key";
    private static final String TAG_VALUE = "Value";

    private ChatLineNbtCodec() {}

    static NBTTagCompound write(LostTalesChatMessagePacket line) {
        NBTTagCompound tag = new NBTTagCompound();
        ChatChannel channel = line.getChannel();
        tag.setString(TAG_CHANNEL, channel == null ? "" : channel.getId());
        NbtTags.writeUuid(tag, TAG_SENDER, line.getSenderId());
        tag.setString(TAG_IDENTITY, line.getIdentityName());
        tag.setString(TAG_ACCOUNT, line.getAccountName());
        tag.setString(TAG_TITLE, line.getTitle());
        tag.setInteger(TAG_TITLE_COLOR, line.getTitleColor());
        tag.setInteger(TAG_NAME_COLOR, line.getNameColor());
        tag.setString(TAG_MESSAGE, line.getMessage());
        tag.setBoolean(TAG_ACTION, line.isAction());
        tag.setLong(TAG_TIMESTAMP, line.getTimestampMillis());
        tag.setString(TAG_SKIN, line.getSkinId());
        NBTTagList shares = new NBTTagList();
        for (ChatShowcase showcase : line.getShowcases()) {
            shares.appendTag(writeShowcase(showcase));
        }
        tag.setTag(TAG_SHARES, shares);
        tag.setString(TAG_FACTION, line.getFactionId());
        tag.setString(TAG_PARTNER, line.getPartner());
        tag.setString(TAG_PARTNER_IDENTITY, line.getPartnerIdentity());
        tag.setLong(TAG_ECHO, line.getEchoNonce());
        tag.setTag(TAG_ROLES, writeStrings(roleIdsOf(line.getRoles())));
        tag.setBoolean(TAG_ACCOUNT_LINE, line.isAccountLine());
        NbtTags.writeUuid(tag, TAG_CHARACTER, line.getIdentityCharacterId());
        NbtTags.writeUuid(tag, TAG_OWN_CHARACTER, line.getOwnCharacterId());
        NbtTags.writeUuid(tag, TAG_PARTNER_CHARACTER, line.getPartnerCharacterId());
        tag.setString(TAG_SCOPE, line.getScopeValue());
        tag.setLong(TAG_MESSAGE_ID, line.getMessageId());
        if (line.getReply().exists()) {
            tag.setTag(TAG_REPLY, writeReply(line.getReply()));
        }
        tag.setString(TAG_BODY, line.getBodyJson());
        NBTTagList named = new NBTTagList();
        for (ChatNamedPlayer player : line.getNamedPlayers()) {
            NBTTagCompound entry = new NBTTagCompound();
            NbtTags.writeUuid(entry, TAG_PLAYER, player.getPlayerId());
            entry.setString(TAG_ACCOUNT, player.getAccount());
            NbtTags.writeUuid(entry, TAG_CHARACTER, player.getCharacterId());
            entry.setString(TAG_IDENTITY, player.getIdentityName());
            entry.setString(TAG_SKIN, player.getSkinId());
            named.appendTag(entry);
        }
        tag.setTag(TAG_NAMED, named);
        tag.setString(TAG_TAB, line.getTabId());
        tag.setTag(TAG_CALLED_ROLES, writeStrings(line.getMentionedRoles()));
        tag.setBoolean(TAG_NOT_IN_WORLD, line.isNotInWorld());
        tag.setString(TAG_TYPED, line.getTyped());
        tag.setBoolean(TAG_UNSEEN, line.isUnseen());
        return tag;
    }

    /**
     * The line the compound holds, or null for one that is not a line
     * every client would read: a field missing or out of its bounds, a
     * channel the server no longer has, or a line the wire refuses.
     */
    static LostTalesChatMessagePacket read(NBTTagCompound tag) {
        if (tag == null) {
            return null;
        }
        try {
            ChatChannel channel = ChatChannel.fromId(string(tag, TAG_CHANNEL));
            UUID sender = NbtTags.readUuid(tag, TAG_SENDER);
            if (channel == null || sender == null) {
                return null;
            }
            List<ChatShowcase> showcases = new ArrayList<ChatShowcase>();
            NBTTagList shares = list(tag, TAG_SHARES);
            for (int index = 0; index < shares.tagCount(); index++) {
                showcases.add(readShowcase(shares.getCompoundTagAt(index)));
            }
            List<ChatNamedPlayer> named = new ArrayList<ChatNamedPlayer>();
            NBTTagList namedList = list(tag, TAG_NAMED);
            if (namedList.tagCount() > ChatNamedPlayer.MAX_PER_LINE) {
                return null;
            }
            for (int index = 0; index < namedList.tagCount(); index++) {
                NBTTagCompound entry = namedList.getCompoundTagAt(index);
                ChatNamedPlayer player = new ChatNamedPlayer(
                        NbtTags.readUuid(entry, TAG_PLAYER),
                        string(entry, TAG_ACCOUNT),
                        NbtTags.readUuid(entry, TAG_CHARACTER),
                        string(entry, TAG_IDENTITY), string(entry, TAG_SKIN));
                if (!player.isValid()) {
                    return null;
                }
                named.add(player);
            }
            LostTalesChatMessagePacket line = new LostTalesChatMessagePacket(
                    channel, sender, string(tag, TAG_IDENTITY),
                    string(tag, TAG_ACCOUNT), string(tag, TAG_TITLE),
                    integer(tag, TAG_TITLE_COLOR), integer(tag, TAG_NAME_COLOR),
                    string(tag, TAG_MESSAGE), longValue(tag, TAG_TIMESTAMP),
                    string(tag, TAG_SKIN), showcases, string(tag, TAG_FACTION),
                    string(tag, TAG_PARTNER), roleMaskOf(strings(tag, TAG_ROLES)),
                    flag(tag, TAG_ACCOUNT_LINE), longValue(tag, TAG_MESSAGE_ID),
                    readReply(tag), string(tag, TAG_PARTNER_IDENTITY),
                    longValue(tag, TAG_ECHO), NbtTags.readUuid(tag, TAG_CHARACTER))
                    .withConversation(NbtTags.readUuid(tag, TAG_OWN_CHARACTER),
                            NbtTags.readUuid(tag, TAG_PARTNER_CHARACTER))
                    .withScope(string(tag, TAG_SCOPE))
                    .withAction(flag(tag, TAG_ACTION))
                    .withServerBody(string(tag, TAG_BODY), named)
                    .withTabId(string(tag, TAG_TAB))
                    .withMentionedRoles(knownRoles(strings(tag, TAG_CALLED_ROLES)))
                    .withNotInWorld(flag(tag, TAG_NOT_IN_WORLD))
                    .withTyped(string(tag, TAG_TYPED))
                    .withUnseen(flag(tag, TAG_UNSEEN));
            return readsOnTheWire(line) ? line : null;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * Whether the line goes onto the wire and comes back as itself: the
     * one test of a line every client already makes, bounds included.
     */
    private static boolean readsOnTheWire(LostTalesChatMessagePacket line) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            line.toBytes(buffer);
            if (buffer.readableBytes() > LostTalesChatMessagePacket.MAX_PACKET_BYTES) {
                return false;
            }
            LostTalesChatMessagePacket back = new LostTalesChatMessagePacket();
            back.fromBytes(buffer);
            return !back.isMalformed() && back.getMessageId() == line.getMessageId();
        } catch (RuntimeException refused) {
            return false;
        } finally {
            buffer.release();
        }
    }

    private static NBTTagCompound writeReply(ChatReplyReference reply) {
        NBTTagCompound tag = new NBTTagCompound();
        if (reply.isUnkept()) {
            tag.setString(TAG_REPLY_KIND, REPLY_UNKEPT);
            return tag;
        }
        tag.setString(TAG_REPLY_KIND, reply.isForward() ? REPLY_FORWARD : REPLY_QUOTE);
        tag.setLong(TAG_MESSAGE_ID, reply.getMessageId());
        tag.setString(TAG_REPLY_AUTHOR, reply.getAuthor());
        tag.setString(TAG_REPLY_EXCERPT, reply.getExcerpt());
        tag.setInteger(TAG_REPLY_COLOR, reply.getAuthorColor());
        tag.setBoolean(TAG_REPLY_ACTION, reply.isAction());
        tag.setString(TAG_REPLY_LINK, reply.getForwardedFrom());
        tag.setBoolean(TAG_UNSEEN, reply.isUnseen());
        if (reply.hasHead()) {
            NbtTags.writeUuid(tag, TAG_SENDER, reply.getSenderId());
            tag.setBoolean(TAG_ACCOUNT_LINE, reply.isAccountLine());
            tag.setString(TAG_SKIN, reply.getSkinId());
        }
        return tag;
    }

    /** The quote the line's compound holds; NONE when it holds none. */
    private static ChatReplyReference readReply(NBTTagCompound line) {
        if (!line.hasKey(TAG_REPLY)) {
            return ChatReplyReference.NONE;
        }
        if (!line.hasKey(TAG_REPLY, Constants.NBT.TAG_COMPOUND)) {
            throw new IllegalArgumentException("a quote that is not one");
        }
        NBTTagCompound tag = line.getCompoundTag(TAG_REPLY);
        String kind = string(tag, TAG_REPLY_KIND);
        if (REPLY_UNKEPT.equals(kind)) {
            return ChatReplyReference.UNKEPT;
        }
        long messageId = longValue(tag, TAG_MESSAGE_ID);
        String author = string(tag, TAG_REPLY_AUTHOR);
        int color = integer(tag, TAG_REPLY_COLOR);
        ChatReplyReference reply;
        if (REPLY_FORWARD.equals(kind)) {
            reply = ChatReplyReference.forward(messageId, author, color,
                    string(tag, TAG_REPLY_LINK));
        } else if (REPLY_QUOTE.equals(kind)) {
            reply = ChatReplyReference.of(messageId, author,
                    string(tag, TAG_REPLY_EXCERPT), color)
                    .asAction(flag(tag, TAG_REPLY_ACTION));
        } else {
            throw new IllegalArgumentException("an unknown kind of quote");
        }
        if (!reply.exists()) {
            throw new IllegalArgumentException("a quote of nothing");
        }
        UUID head = NbtTags.readUuid(tag, TAG_SENDER);
        return (head == null ? reply : reply.withHead(head,
                flag(tag, TAG_ACCOUNT_LINE), string(tag, TAG_SKIN)))
                .asUnseen(flag(tag, TAG_UNSEEN));
    }

    private static NBTTagCompound writeShowcase(ChatShowcase showcase) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_TOKEN, showcase.getTokenIndex());
        tag.setInteger(TAG_KIND, showcase.getKind().getCode());
        if (showcase.getKind() == ChatShareKind.ITEM) {
            tag.setByteArray(TAG_STACK, showcase.getStackData());
        } else if (showcase.getKind() == ChatShareKind.MARKER) {
            tag.setString(TAG_MARKER_ID, showcase.getMarkerId());
            tag.setString(TAG_MARKER_NAME, showcase.getMarkerName());
            tag.setString(TAG_MARKER_NAMED_AFTER, showcase.getMarkerNamedAfter());
            tag.setString(TAG_MARKER_ICON, showcase.getMarkerIcon());
            tag.setString(TAG_MARKER_COLOR, showcase.getMarkerColor());
            tag.setInteger(TAG_MARKER_DIMENSION, showcase.getMarkerDimension());
            tag.setDouble(TAG_MARKER_X, showcase.getMarkerX());
            tag.setDouble(TAG_MARKER_Z, showcase.getMarkerZ());
        } else {
            tag.setString(TAG_QUEST_REFERENCE, showcase.getQuestReference());
            tag.setTag(TAG_QUEST_CARD, writeCard(showcase.getQuestCard()));
            tag.setBoolean(TAG_QUEST_JOINABLE, showcase.isQuestJoinable());
        }
        return tag;
    }

    private static ChatShowcase readShowcase(NBTTagCompound tag) {
        int token = integer(tag, TAG_TOKEN);
        ChatShareKind kind = ChatShareKind.fromCode(integer(tag, TAG_KIND));
        if (kind == ChatShareKind.ITEM) {
            if (!tag.hasKey(TAG_STACK, Constants.NBT.TAG_BYTE_ARRAY)) {
                throw new IllegalArgumentException("an item without its stack");
            }
            return ChatShowcase.item(token, tag.getByteArray(TAG_STACK));
        }
        if (kind == ChatShareKind.MARKER) {
            return ChatShowcase.marker(token, string(tag, TAG_MARKER_ID),
                    string(tag, TAG_MARKER_NAME), string(tag, TAG_MARKER_NAMED_AFTER),
                    string(tag, TAG_MARKER_ICON), string(tag, TAG_MARKER_COLOR),
                    integer(tag, TAG_MARKER_DIMENSION),
                    decimal(tag, TAG_MARKER_X), decimal(tag, TAG_MARKER_Z));
        }
        if (kind == ChatShareKind.QUEST) {
            if (!tag.hasKey(TAG_QUEST_CARD, Constants.NBT.TAG_COMPOUND)) {
                throw new IllegalArgumentException("a quest without its card");
            }
            return ChatShowcase.quest(token, string(tag, TAG_QUEST_REFERENCE),
                    readCard(tag.getCompoundTag(TAG_QUEST_CARD)),
                    flag(tag, TAG_QUEST_JOINABLE));
        }
        throw new IllegalArgumentException("an unknown kind of share");
    }

    private static NBTTagCompound writeCard(ChatQuestCard card) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_CARD_SOURCE, card.getSource().getCode());
        tag.setString(TAG_CARD_TITLE, card.getTitle());
        tag.setString(TAG_CARD_CATEGORY, card.getCategory());
        NBTTagList objectives = new NBTTagList();
        for (ChatQuestCard.Objective objective : card.getObjectives()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString(TAG_OBJECTIVE_ID, objective.getId());
            entry.setString(TAG_OBJECTIVE_TYPE, objective.getType());
            entry.setTag(TAG_OBJECTIVE_TARGETS, writePairs(objective.getTargets()));
            entry.setInteger(TAG_OBJECTIVE_COUNT, objective.getCount());
            entry.setInteger(TAG_OBJECTIVE_PROGRESS, objective.getProgress());
            entry.setBoolean(TAG_OBJECTIVE_OPTIONAL, objective.isOptional());
            entry.setString(TAG_OBJECTIVE_TEXT, objective.getText());
            objectives.appendTag(entry);
        }
        tag.setTag(TAG_CARD_OBJECTIVES, objectives);
        tag.setTag(TAG_CARD_REWARDS, writePairs(card.getRewards()));
        return tag;
    }

    private static ChatQuestCard readCard(NBTTagCompound tag) {
        ChatQuestCard.Source source = ChatQuestCard.Source.fromCode(
                integer(tag, TAG_CARD_SOURCE));
        NBTTagList list = list(tag, TAG_CARD_OBJECTIVES);
        if (list.tagCount() > ChatQuestCard.MAX_OBJECTIVES) {
            throw new IllegalArgumentException("too many objectives");
        }
        List<ChatQuestCard.Objective> objectives =
                new ArrayList<ChatQuestCard.Objective>(list.tagCount());
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            objectives.add(new ChatQuestCard.Objective(
                    string(entry, TAG_OBJECTIVE_ID), string(entry, TAG_OBJECTIVE_TYPE),
                    readPairs(entry, TAG_OBJECTIVE_TARGETS,
                            ChatQuestCard.TARGET_PARAMS.size()),
                    integer(entry, TAG_OBJECTIVE_COUNT),
                    integer(entry, TAG_OBJECTIVE_PROGRESS),
                    flag(entry, TAG_OBJECTIVE_OPTIONAL),
                    string(entry, TAG_OBJECTIVE_TEXT)));
        }
        return new ChatQuestCard(source, string(tag, TAG_CARD_TITLE),
                string(tag, TAG_CARD_CATEGORY), objectives,
                readPairs(tag, TAG_CARD_REWARDS, ChatQuestCard.MAX_REWARDS));
    }

    private static NBTTagList writePairs(Map<String, String> pairs) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, String> pair : pairs.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString(TAG_KEY, pair.getKey());
            entry.setString(TAG_VALUE, pair.getValue());
            list.appendTag(entry);
        }
        return list;
    }

    private static Map<String, String> readPairs(NBTTagCompound tag, String key, int most) {
        NBTTagList list = list(tag, key);
        if (list.tagCount() > most) {
            throw new IllegalArgumentException("too many pairs");
        }
        Map<String, String> pairs = new LinkedHashMap<String, String>();
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            String name = string(entry, TAG_KEY);
            if (pairs.put(name, string(entry, TAG_VALUE)) != null) {
                throw new IllegalArgumentException("a pair named twice");
            }
        }
        return pairs;
    }

    /** The ids of the server's roles set in {@code mask}. */
    private static List<String> roleIdsOf(int mask) {
        List<String> ids = new ArrayList<String>(2);
        for (ChatAccountRole role : ChatRoleCatalog.server().roles()) {
            if ((mask & role.bit()) != 0) {
                ids.add(role.getId());
            }
        }
        return ids;
    }

    /** The mask of the roles the server still has among {@code ids}. */
    private static int roleMaskOf(List<String> ids) {
        int mask = 0;
        for (String id : knownRoles(ids)) {
            mask |= ChatRoleCatalog.server().byId(id).bit();
        }
        return mask;
    }

    /** The ids among {@code ids} that name a role the server still has. */
    private static List<String> knownRoles(List<String> ids) {
        List<String> known = new ArrayList<String>(ids.size());
        for (String id : ids) {
            if (ChatRoleCatalog.server().byId(id) != null && !known.contains(id)) {
                known.add(id);
            }
        }
        return known;
    }

    private static NBTTagList writeStrings(List<String> values) {
        NBTTagList list = new NBTTagList();
        for (String value : values) {
            list.appendTag(new NBTTagString(value));
        }
        return list;
    }

    /** At most a line's worth of role ids, each one written as an id is. */
    private static List<String> strings(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_LIST)) {
            throw new IllegalArgumentException("missing " + key);
        }
        NBTTagList list = tag.getTagList(key, Constants.NBT.TAG_STRING);
        if (list.tagCount() > ChatRoleCatalog.MAX_ROLES) {
            throw new IllegalArgumentException("too many in " + key);
        }
        List<String> values = new ArrayList<String>(list.tagCount());
        for (int index = 0; index < list.tagCount(); index++) {
            String value = list.getStringTagAt(index);
            if (!ChatAccountRole.isWellFormedId(value)) {
                throw new IllegalArgumentException("not a role in " + key);
            }
            values.add(value);
        }
        return values;
    }

    private static NBTTagList list(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_LIST)) {
            throw new IllegalArgumentException("missing " + key);
        }
        return tag.getTagList(key, Constants.NBT.TAG_COMPOUND);
    }

    private static String string(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_STRING)) {
            throw new IllegalArgumentException("missing " + key);
        }
        return tag.getString(key);
    }

    private static int integer(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_INT)) {
            throw new IllegalArgumentException("missing " + key);
        }
        return tag.getInteger(key);
    }

    private static long longValue(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_LONG)) {
            throw new IllegalArgumentException("missing " + key);
        }
        return tag.getLong(key);
    }

    private static double decimal(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_DOUBLE)) {
            throw new IllegalArgumentException("missing " + key);
        }
        return tag.getDouble(key);
    }

    private static boolean flag(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_BYTE)) {
            throw new IllegalArgumentException("missing " + key);
        }
        return tag.getBoolean(key);
    }
}
