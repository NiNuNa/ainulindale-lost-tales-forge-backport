package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.storage.NbtQuarantine;
import com.ninuna.losttales.util.LostTalesLog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * Versioned NBT codec for the Discord bridge's memory in the world save:
 * the message links, each game message the bridge carried, oldest first,
 * with every Discord copy it has — the copy's Discord id, the channel it
 * is in, the binding it was posted through and the reply header it
 * opened with — and the topics, each kept channel's words Discord last
 * took and when it took the last two writes.
 *
 * <p>No webhook URL is ever written, since a webhook URL is a
 * credential: the binding id stands in for it. Every copy is written
 * under the Discord channel it is in ({@code channel:<id>}), which every
 * link names; a copy known by anything else is left out, and on read a
 * destination that is not a channel is refused, so a URL cannot pass for
 * one. An entry the codec cannot vouch for is quarantined whole with
 * its reason, a message under {@code EntryIndex} and a topic under
 * {@code TopicIndex}; data newer than this codec is left alone and the
 * store goes read-only. Nothing read is dropped or cut. A write keeps
 * every bound a read checks: a message's copies past
 * {@link #MAX_COPIES_PER_ENTRY} are not saved, the oldest kept, and
 * topics past {@link #MAX_TOPICS} are not saved.</p>
 */
final class DiscordMessageLinkNbtCodec {

    static final int CURRENT_ROOT_DATA_VERSION = 2;
    static final int CURRENT_ENTRY_DATA_VERSION = 1;
    static final int CURRENT_TOPIC_DATA_VERSION = 1;
    /** Safety bound on messages read back; entries past it are quarantined. */
    static final int MAX_ENTRIES = DiscordMessageLinks.MAX_LINKS;
    /** The most copies one message may name: it has one per binding of its channel. */
    static final int MAX_COPIES_PER_ENTRY = 64;
    /** The longest reply header kept; Discord takes no longer message. */
    static final int MAX_HEADER_LENGTH = 2000;
    /** The longest binding id kept. */
    static final int MAX_BINDING_ID_LENGTH = 128;
    /** Safety bound on topics read back; topics past it are quarantined. */
    static final int MAX_TOPICS = DiscordTopicMemory.MAX_CHANNELS;
    /** The longest topic kept: Discord takes no longer one. */
    static final int MAX_TOPIC_LENGTH = 1024;

    /** A destination naming a Discord channel by id. */
    static final String CHANNEL_PREFIX = "channel:";

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_MESSAGES = "Messages";
    private static final String TAG_ENTRY_INDEX = "EntryIndex";
    private static final String TAG_TOPICS = "Topics";
    private static final String TAG_TOPIC_INDEX = "TopicIndex";

    private static final String TAG_MESSAGE_ID = "MessageId";
    private static final String TAG_COPIES = "Copies";
    private static final String TAG_DISCORD_ID = "DiscordId";
    private static final String TAG_DESTINATION = "Destination";
    private static final String TAG_BINDING = "Binding";
    private static final String TAG_HEADER = "Header";

    private static final String TAG_CHANNEL_ID = "ChannelId";
    private static final String TAG_WORDS = "Words";
    private static final String TAG_LAST_WRITE = "LastWrite";
    private static final String TAG_WRITE_BEFORE = "WriteBefore";

    private DiscordMessageLinkNbtCodec() {}

    /**
     * Writes the links in the order given, oldest first, the topics in the
     * order given, and the quarantine as it was read.
     */
    static void write(NBTTagCompound output,
                      Collection<DiscordMessageLinks.SavedLink> links,
                      Collection<DiscordTopicMemory.SavedTopic> topics,
                      Collection<NBTTagCompound> quarantinedEntries) {
        output.setInteger(TAG_DATA_VERSION, CURRENT_ROOT_DATA_VERSION);
        NBTTagList list = new NBTTagList();
        if (links != null) {
            for (DiscordMessageLinks.SavedLink link : links) {
                NBTTagCompound entry = link == null ? null : writeEntry(link);
                if (entry != null) {
                    list.appendTag(entry);
                }
            }
        }
        output.setTag(TAG_MESSAGES, list);
        NBTTagList topicList = new NBTTagList();
        if (topics != null) {
            Set<String> written = new HashSet<String>();
            for (DiscordTopicMemory.SavedTopic topic : topics) {
                if (topicList.tagCount() >= MAX_TOPICS) {
                    break;
                }
                NBTTagCompound entry = topic == null ? null : writeTopic(topic);
                if (entry != null && written.add(topic.channelId)) {
                    topicList.appendTag(entry);
                }
            }
        }
        output.setTag(TAG_TOPICS, topicList);
        NbtQuarantine.write(output, quarantinedEntries);
    }

    /** One topic, or null for one a read would refuse. */
    private static NBTTagCompound writeTopic(DiscordTopicMemory.SavedTopic topic) {
        if (!isTopic(topic)) {
            return null;
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, CURRENT_TOPIC_DATA_VERSION);
        tag.setString(TAG_CHANNEL_ID, topic.channelId);
        tag.setString(TAG_WORDS, topic.words);
        tag.setLong(TAG_LAST_WRITE, topic.lastWrite);
        tag.setLong(TAG_WRITE_BEFORE, topic.writeBefore);
        return tag;
    }

    /**
     * Whether a topic may be kept: a Discord channel by id, words Discord
     * would take, a last write in time, and no write or one no later than
     * it before.
     */
    static boolean isTopic(DiscordTopicMemory.SavedTopic topic) {
        return topic != null
                && DiscordChannelBindings.isSnowflake(topic.channelId)
                && topic.words.length() > 0
                && topic.words.length() <= MAX_TOPIC_LENGTH
                && topic.lastWrite > 0L
                && (topic.writeBefore == DiscordTopicMemory.SavedTopic.NO_WRITE
                        || (topic.writeBefore > 0L
                                && topic.writeBefore <= topic.lastWrite));
    }

    /**
     * One message and its copies, or null for a message with nothing the
     * save may keep: no server id, or no copy a read would take back.
     * Copies past {@link #MAX_COPIES_PER_ENTRY} are left out, the oldest
     * kept, so the entry is never one a read would quarantine.
     */
    private static NBTTagCompound writeEntry(DiscordMessageLinks.SavedLink link) {
        if (!ChatMessageIds.isServerId(link.messageId)) {
            return null;
        }
        NBTTagList copies = new NBTTagList();
        for (DiscordMessageLinks.SavedCopy copy : link.copies) {
            if (copies.tagCount() >= MAX_COPIES_PER_ENTRY) {
                break;
            }
            NBTTagCompound written = copy == null ? null : writeCopy(copy);
            if (written != null) {
                copies.appendTag(written);
            }
        }
        if (copies.tagCount() == 0) {
            return null;
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, CURRENT_ENTRY_DATA_VERSION);
        tag.setLong(TAG_MESSAGE_ID, link.messageId);
        tag.setTag(TAG_COPIES, copies);
        return tag;
    }

    /**
     * One copy, or null for one a read would refuse. Its binding is
     * written only when it reads back as one.
     */
    private static NBTTagCompound writeCopy(DiscordMessageLinks.SavedCopy copy) {
        String binding = isBindingId(copy.bindingId) ? copy.bindingId : "";
        if (!DiscordChannelBindings.isSnowflake(copy.discordId)
                || !isChannelDestination(copy.destination)
                || copy.header.length() > MAX_HEADER_LENGTH) {
            return null;
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(TAG_DISCORD_ID, copy.discordId);
        tag.setString(TAG_DESTINATION, copy.destination);
        tag.setString(TAG_BINDING, binding);
        tag.setString(TAG_HEADER, copy.header);
        return tag;
    }

    /**
     * Reads a save as {@link #write} writes it. One without its version
     * or either of its lists, or at another version, is kept as it is
     * and the store goes read-only.
     */
    static ReadResult read(NBTTagCompound source) {
        NBTTagCompound safeSource = source == null ? new NBTTagCompound() : source;
        if (!safeSource.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)) {
            LostTalesLog.warning("Discord message link data carries no version; data will remain read-only");
            return ReadResult.unsupported(safeSource, -1);
        }
        int version = safeSource.getInteger(TAG_DATA_VERSION);
        if (version != CURRENT_ROOT_DATA_VERSION) {
            LostTalesLog.warning("Discord message link data uses unsupported version %d; data will remain read-only",
                    Integer.valueOf(version));
            return ReadResult.unsupported(safeSource, version);
        }
        if (!safeSource.hasKey(TAG_MESSAGES, Constants.NBT.TAG_LIST)
                || !holdsCompoundsOnly(safeSource, TAG_MESSAGES)) {
            LostTalesLog.warning("Discord message link data lacks its list of messages; data will remain read-only");
            return ReadResult.unsupported(safeSource, -1);
        }
        if (!safeSource.hasKey(TAG_TOPICS, Constants.NBT.TAG_LIST)
                || !holdsCompoundsOnly(safeSource, TAG_TOPICS)) {
            LostTalesLog.warning("Discord message link data lacks its list of topics; data will remain read-only");
            return ReadResult.unsupported(safeSource, -1);
        }
        boolean repaired = false;
        NbtQuarantine.Read quarantine = NbtQuarantine.read(safeSource);
        if (!quarantine.isSupported()) {
            return ReadResult.unsupported(safeSource, quarantine.getUnsupportedVersion());
        }
        List<NBTTagCompound> quarantinedEntries =
                new ArrayList<NBTTagCompound>(quarantine.getEntries());
        List<DiscordMessageLinks.SavedLink> links =
                new ArrayList<DiscordMessageLinks.SavedLink>();
        Set<Long> seenMessages = new HashSet<Long>();
        Set<String> seenDiscordIds = new HashSet<String>();
        NBTTagList list = safeSource.getTagList(TAG_MESSAGES,
                Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound raw = list.getCompoundTagAt(index);
            EntryReadResult result = readEntry(raw);
            if (result.unsupportedVersion != Integer.MIN_VALUE) {
                return ReadResult.unsupported(safeSource, result.unsupportedVersion);
            }
            String reason = result.failureReason;
            if (result.link != null) {
                if (seenMessages.contains(Long.valueOf(result.link.messageId))) {
                    reason = "duplicate_message";
                } else if (namesAny(result.link, seenDiscordIds)) {
                    // One Discord id can name one message only.
                    reason = "duplicate_discord_id";
                } else if (links.size() >= MAX_ENTRIES) {
                    reason = "over_capacity";
                }
            }
            if (reason != null) {
                quarantinedEntries.add(NbtQuarantine.entry(reason, TAG_ENTRY_INDEX, index, raw));
                repaired = true;
                continue;
            }
            seenMessages.add(Long.valueOf(result.link.messageId));
            for (DiscordMessageLinks.SavedCopy copy : result.link.copies) {
                seenDiscordIds.add(copy.discordId);
            }
            links.add(result.link);
        }
        List<DiscordTopicMemory.SavedTopic> topics =
                new ArrayList<DiscordTopicMemory.SavedTopic>();
        Set<String> seenChannels = new HashSet<String>();
        NBTTagList topicList = safeSource.getTagList(TAG_TOPICS,
                Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < topicList.tagCount(); index++) {
            NBTTagCompound raw = topicList.getCompoundTagAt(index);
            int topicVersion = raw.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                    ? raw.getInteger(TAG_DATA_VERSION) : 0;
            if (topicVersion > CURRENT_TOPIC_DATA_VERSION) {
                return ReadResult.unsupported(safeSource, topicVersion);
            }
            String reason = topicVersion < 1 ? "invalid_topic_version"
                    : topicFailure(raw);
            DiscordTopicMemory.SavedTopic topic = reason != null ? null
                    : new DiscordTopicMemory.SavedTopic(
                            raw.getString(TAG_CHANNEL_ID), raw.getString(TAG_WORDS),
                            raw.getLong(TAG_LAST_WRITE), raw.getLong(TAG_WRITE_BEFORE));
            if (topic != null) {
                if (seenChannels.contains(topic.channelId)) {
                    reason = "duplicate_topic_channel";
                } else if (topics.size() >= MAX_TOPICS) {
                    reason = "topics_over_capacity";
                }
            }
            if (reason != null) {
                quarantinedEntries.add(NbtQuarantine.entry(reason, TAG_TOPIC_INDEX, index, raw));
                repaired = true;
                continue;
            }
            seenChannels.add(topic.channelId);
            topics.add(topic);
        }
        return ReadResult.success(links, topics, repaired, quarantinedEntries);
    }

    /**
     * Why a topic cannot be read as {@link #writeTopic} writes it, every
     * field present, of its type and within its bounds; null when it can.
     */
    private static String topicFailure(NBTTagCompound raw) {
        if (!raw.hasKey(TAG_CHANNEL_ID, Constants.NBT.TAG_STRING)
                || !DiscordChannelBindings.isSnowflake(raw.getString(TAG_CHANNEL_ID))) {
            return "invalid_topic_channel";
        }
        if (!raw.hasKey(TAG_WORDS, Constants.NBT.TAG_STRING)
                || raw.getString(TAG_WORDS).length() == 0
                || raw.getString(TAG_WORDS).length() > MAX_TOPIC_LENGTH) {
            return "invalid_topic_words";
        }
        if (!raw.hasKey(TAG_LAST_WRITE, Constants.NBT.TAG_LONG)
                || !raw.hasKey(TAG_WRITE_BEFORE, Constants.NBT.TAG_LONG)) {
            return "invalid_topic_writes";
        }
        DiscordTopicMemory.SavedTopic topic = new DiscordTopicMemory.SavedTopic(
                raw.getString(TAG_CHANNEL_ID), raw.getString(TAG_WORDS),
                raw.getLong(TAG_LAST_WRITE), raw.getLong(TAG_WRITE_BEFORE));
        return isTopic(topic) ? null : "invalid_topic_writes";
    }

    private static boolean namesAny(DiscordMessageLinks.SavedLink link,
                                    Set<String> discordIds) {
        for (DiscordMessageLinks.SavedCopy copy : link.copies) {
            if (discordIds.contains(copy.discordId)) {
                return true;
            }
        }
        return false;
    }

    private static EntryReadResult readEntry(NBTTagCompound raw) {
        if (raw == null) {
            return EntryReadResult.failure("missing_entry");
        }
        int version = raw.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? raw.getInteger(TAG_DATA_VERSION) : 0;
        if (version > CURRENT_ENTRY_DATA_VERSION) {
            return EntryReadResult.unsupported(version);
        }
        if (version < 1) {
            return EntryReadResult.failure("invalid_version");
        }
        if (!raw.hasKey(TAG_MESSAGE_ID, Constants.NBT.TAG_LONG)
                || !ChatMessageIds.isServerId(raw.getLong(TAG_MESSAGE_ID))) {
            return EntryReadResult.failure("invalid_message_id");
        }
        if (!raw.hasKey(TAG_COPIES, Constants.NBT.TAG_LIST)
                || !holdsCompoundsOnly(raw, TAG_COPIES)) {
            return EntryReadResult.failure("invalid_copies");
        }
        NBTTagList copies = raw.getTagList(TAG_COPIES, Constants.NBT.TAG_COMPOUND);
        if (copies.tagCount() == 0) {
            return EntryReadResult.failure("missing_copies");
        }
        if (copies.tagCount() > MAX_COPIES_PER_ENTRY) {
            return EntryReadResult.failure("too_many_copies");
        }
        List<DiscordMessageLinks.SavedCopy> read =
                new ArrayList<DiscordMessageLinks.SavedCopy>(copies.tagCount());
        Set<String> destinations = new HashSet<String>();
        Set<String> discordIds = new HashSet<String>();
        for (int index = 0; index < copies.tagCount(); index++) {
            NBTTagCompound copy = copies.getCompoundTagAt(index);
            if (!copy.hasKey(TAG_DISCORD_ID, Constants.NBT.TAG_STRING)
                    || !DiscordChannelBindings.isSnowflake(copy.getString(TAG_DISCORD_ID))) {
                return EntryReadResult.failure("invalid_discord_id");
            }
            String discordId = copy.getString(TAG_DISCORD_ID);
            if (!copy.hasKey(TAG_DESTINATION, Constants.NBT.TAG_STRING)) {
                return EntryReadResult.failure("invalid_destination");
            }
            String destination = copy.getString(TAG_DESTINATION);
            // The writer writes every field, a Discord line's empty binding
            // and a headerless post's empty header too.
            if (!copy.hasKey(TAG_BINDING, Constants.NBT.TAG_STRING)) {
                return EntryReadResult.failure("invalid_binding");
            }
            String binding = copy.getString(TAG_BINDING);
            if (binding.length() > 0 && !isBindingId(binding)) {
                return EntryReadResult.failure("invalid_binding");
            }
            if (!isChannelDestination(destination)) {
                return EntryReadResult.failure("invalid_destination");
            }
            if (!copy.hasKey(TAG_HEADER, Constants.NBT.TAG_STRING)) {
                return EntryReadResult.failure("invalid_header");
            }
            String header = copy.getString(TAG_HEADER);
            if (header.length() > MAX_HEADER_LENGTH) {
                return EntryReadResult.failure("invalid_header");
            }
            if (!destinations.add(destination)) {
                return EntryReadResult.failure("duplicate_destination");
            }
            if (!discordIds.add(discordId)) {
                return EntryReadResult.failure("duplicate_discord_id");
            }
            read.add(new DiscordMessageLinks.SavedCopy(discordId, destination,
                    binding, header));
        }
        return EntryReadResult.success(new DiscordMessageLinks.SavedLink(
                raw.getLong(TAG_MESSAGE_ID), read));
    }

    /** Whether a destination names a Discord channel by id. */
    static boolean isChannelDestination(String value) {
        return value != null && value.startsWith(CHANNEL_PREFIX)
                && DiscordChannelBindings.isSnowflake(value.substring(CHANNEL_PREFIX.length()));
    }

    /**
     * Whether a value may be kept as a binding id: letters, digits and
     * the separators a binding's key and ordinal are made of. No slash
     * is among them, so no URL can pass for one.
     */
    static boolean isBindingId(String value) {
        if (value == null || value.length() == 0
                || value.length() > MAX_BINDING_ID_LENGTH) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isLetterOrDigit(character) && character != '_'
                    && character != '-' && character != '.' && character != ':'
                    && character != '#') {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the list under {@code key} holds compounds and nothing
     * else. A list of anything else reads back as empty, which would
     * lose what it holds on the next write.
     */
    private static boolean holdsCompoundsOnly(NBTTagCompound tag, String key) {
        NBTBase raw = tag.getTag(key);
        if (!(raw instanceof NBTTagList)) {
            return false;
        }
        int count = ((NBTTagList) raw).tagCount();
        return count == 0
                || tag.getTagList(key, Constants.NBT.TAG_COMPOUND).tagCount() == count;
    }

    private static final class EntryReadResult {
        final DiscordMessageLinks.SavedLink link;
        final String failureReason;
        final int unsupportedVersion;

        private EntryReadResult(DiscordMessageLinks.SavedLink link,
                                String failureReason, int unsupportedVersion) {
            this.link = link;
            this.failureReason = failureReason;
            this.unsupportedVersion = unsupportedVersion;
        }

        static EntryReadResult success(DiscordMessageLinks.SavedLink link) {
            return new EntryReadResult(link, null, Integer.MIN_VALUE);
        }

        static EntryReadResult failure(String reason) {
            return new EntryReadResult(null, reason, Integer.MIN_VALUE);
        }

        static EntryReadResult unsupported(int version) {
            return new EntryReadResult(null, null, version);
        }
    }

    static final class ReadResult {
        private final List<DiscordMessageLinks.SavedLink> entries;
        private final List<DiscordTopicMemory.SavedTopic> topics;
        private final boolean repaired;
        private final List<NBTTagCompound> quarantineEntries;
        private final boolean readOnly;
        private final int unsupportedVersion;
        private final NBTTagCompound originalData;

        private ReadResult(List<DiscordMessageLinks.SavedLink> entries,
                           List<DiscordTopicMemory.SavedTopic> topics,
                           boolean repaired,
                           List<NBTTagCompound> quarantineEntries,
                           boolean readOnly, int unsupportedVersion,
                           NBTTagCompound originalData) {
            this.entries = Collections.unmodifiableList(
                    new ArrayList<DiscordMessageLinks.SavedLink>(entries));
            this.topics = Collections.unmodifiableList(
                    new ArrayList<DiscordTopicMemory.SavedTopic>(topics));
            this.repaired = repaired;
            this.quarantineEntries = Collections.unmodifiableList(
                    new ArrayList<NBTTagCompound>(quarantineEntries));
            this.readOnly = readOnly;
            this.unsupportedVersion = unsupportedVersion;
            this.originalData = originalData;
        }

        private static ReadResult success(List<DiscordMessageLinks.SavedLink> entries,
                                          List<DiscordTopicMemory.SavedTopic> topics,
                                          boolean repaired,
                                          List<NBTTagCompound> quarantine) {
            return new ReadResult(entries, topics, repaired, quarantine, false, -1, null);
        }

        private static ReadResult unsupported(NBTTagCompound original, int version) {
            return new ReadResult(
                    Collections.<DiscordMessageLinks.SavedLink>emptyList(),
                    Collections.<DiscordTopicMemory.SavedTopic>emptyList(), false,
                    Collections.<NBTTagCompound>emptyList(), true, version,
                    (NBTTagCompound) original.copy());
        }

        /** The links read, oldest first. */
        List<DiscordMessageLinks.SavedLink> getEntries() {
            return this.entries;
        }

        /** The topics read, in the order they were written. */
        List<DiscordTopicMemory.SavedTopic> getTopics() {
            return this.topics;
        }

        boolean wasRepaired() {
            return this.repaired;
        }

        List<NBTTagCompound> getQuarantineEntriesCopy() {
            List<NBTTagCompound> copy = new ArrayList<NBTTagCompound>();
            for (NBTTagCompound entry : this.quarantineEntries) {
                copy.add((NBTTagCompound) entry.copy());
            }
            return copy;
        }

        boolean isReadOnly() {
            return this.readOnly;
        }

        int getUnsupportedVersion() {
            return this.unsupportedVersion;
        }

        NBTTagCompound getOriginalDataCopy() {
            return this.originalData == null ? null
                    : (NBTTagCompound) this.originalData.copy();
        }
    }
}
