package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatReportReason;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import cpw.mods.fml.common.FMLLog;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * Versioned NBT codec for the kept chat history: every message exactly
 * as it was sent, as the sender and as everyone else received it, with
 * who was sent it and who may still be shown it.
 *
 * <p>Each line's two copies are written as the bytes the wire carries,
 * so the one codec every client already decodes is the one the save is
 * read back through: a copy that does not decode, names another message
 * or another channel than the entry says, is quarantined with the entry
 * rather than shown to anyone. The audience is written in full — a line
 * restored without it would be a private line replayed to whoever asks
 * — and an entry whose audience cannot be read is quarantined too. Newer
 * data than this codec knows is left alone and the store goes
 * read-only; nothing is dropped or cut.</p>
 */
public final class ChatHistoryNbtCodec {

    public static final int CURRENT_ROOT_DATA_VERSION = 1;
    /**
     * The newest entry layout this codec reads. An entry is written at
     * the oldest layout that holds it: {@link #PLAIN_ENTRY_DATA_VERSION}
     * without reactions, {@link #REACTED_ENTRY_DATA_VERSION} with
     * reactions whose emoji are all the registry's, and this one when a
     * reaction's emoji is foreign ({@code ChatForeignEmoji}). An older
     * build reads every entry up to the layout it knows and keeps a
     * newer one read-only rather than misreading its reactions.
     */
    public static final int CURRENT_ENTRY_DATA_VERSION = 3;
    /** An entry with no reactions. */
    static final int PLAIN_ENTRY_DATA_VERSION = 1;
    /** An entry with reactions, every emoji one of the registry's. */
    static final int REACTED_ENTRY_DATA_VERSION = 2;
    /** An entry with at least one reaction whose emoji is foreign. */
    static final int FOREIGN_ENTRY_DATA_VERSION = 3;
    public static final int CURRENT_QUARANTINE_DATA_VERSION = 1;
    /** Safety bound on kept lines read back; entries past it are quarantined. */
    public static final int MAX_ENTRIES = ChatHistory.MAX_TOTAL;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_QUARANTINE = "Quarantine";
    private static final String TAG_QUARANTINE_ENTRIES = "Entries";
    private static final String TAG_REASON = "Reason";
    private static final String TAG_ENTRY_INDEX = "EntryIndex";
    private static final String TAG_ORIGINAL_DATA = "OriginalData";

    private static final String TAG_MESSAGE_ID = "MessageId";
    private static final String TAG_AUTHOR_UUID = "Author";
    private static final String TAG_AUTHOR_NAME = "AuthorName";
    private static final String TAG_EXCERPT = "Excerpt";
    private static final String TAG_CHANNEL = "Channel";
    private static final String TAG_TIMESTAMP = "Timestamp";
    private static final String TAG_SEEN_BY = "SeenBy";
    private static final String TAG_FOR_SENDER = "ForSender";
    private static final String TAG_FOR_OTHERS = "ForOthers";
    private static final String TAG_AUDIENCE = "Audience";
    private static final String TAG_AUDIENCE_HAS_ACCOUNTS = "HasAccounts";
    private static final String TAG_AUDIENCE_ACCOUNTS = "Accounts";
    private static final String TAG_AUDIENCE_PARTY = "Party";
    private static final String TAG_AUDIENCE_FACTION = "Faction";
    private static final String TAG_AUDIENCE_GATED = "Gated";
    private static final String TAG_REACTIONS = "Reactions";
    private static final String TAG_REACTION_EMOJI = "Emoji";
    private static final String TAG_REACTION_REACTORS = "Reactors";
    private static final String TAG_REACTOR_ID = "Id";
    private static final String TAG_REACTOR_NAME = "Name";
    private static final String TAG_REACTOR_ORIGIN = "Origin";
    private static final String TAG_UUID_MOST = "Most";
    private static final String TAG_UUID_LEAST = "Least";
    /** The Server Console's events, written beside the lines. */
    private static final String TAG_CONSOLE_EVENTS = "ConsoleEvents";
    private static final String TAG_EVENT_ID = "Id";
    private static final String TAG_EVENT_TIMESTAMP = "Timestamp";
    private static final String TAG_EVENT_KIND = "Kind";
    private static final String TAG_EVENT_SEVERITY = "Severity";
    private static final String TAG_EVENT_ACTOR = "Actor";
    /** The actor's account id and its out-of-character colour; absent for none. */
    private static final String TAG_EVENT_ACTOR_ID = "ActorId";
    private static final String TAG_EVENT_TEXT = "Text";
    private static final String TAG_EVENT_CONTEXT = "Context";
    private static final String TAG_EVENT_REPORT = "Report";
    private static final String TAG_REPORT_REASON = "Reason";
    private static final String TAG_REPORT_NOTE = "Note";
    private static final String TAG_REPORT_MESSAGE = "MessageId";
    private static final String TAG_REPORT_AUTHOR = "Author";
    private static final String TAG_REPORT_AUTHOR_COLOR = "AuthorColor";
    private static final String TAG_REPORT_EXCERPT = "Excerpt";
    private static final String TAG_REPORT_LINK = "Link";
    static final int MAX_CONSOLE_EVENTS = ChatConsoleStream.MAX_EVENTS;

    /** The most accounts one entry may name before it is quarantined. */
    static final int MAX_ACCOUNTS_PER_ENTRY = 4096;

    private ChatHistoryNbtCodec() {}

    /**
     * Writes the entries oldest first, by message id, the console's
     * events oldest first beside them with their reactions, and the
     * quarantine as it was read.
     */
    public static void write(NBTTagCompound output,
                             Collection<ChatHistory.Entry> entries,
                             Collection<ChatConsoleEvent> consoleEvents,
                             Map<Long, ChatReactions> consoleReactions,
                             Collection<NBTTagCompound> quarantinedEntries) {
        output.setInteger(TAG_DATA_VERSION, CURRENT_ROOT_DATA_VERSION);
        List<ChatHistory.Entry> ordered = new ArrayList<ChatHistory.Entry>();
        if (entries != null) {
            for (ChatHistory.Entry entry : entries) {
                if (entry != null) {
                    ordered.add(entry);
                }
            }
        }
        Collections.sort(ordered, ENTRY_ORDER);
        NBTTagList list = new NBTTagList();
        List<NBTTagCompound> quarantine = new ArrayList<NBTTagCompound>();
        if (quarantinedEntries != null) {
            quarantine.addAll(quarantinedEntries);
        }
        int unwritable = 0;
        for (int index = 0; index < ordered.size(); index++) {
            ChatHistory.Entry entry = ordered.get(index);
            try {
                list.appendTag(writeEntry(entry));
            } catch (RuntimeException refused) {
                // A line the wire codec no longer accepts — a channel or
                // a role the server has since lost — is kept by name in
                // the quarantine rather than taking the save down with it.
                quarantine.add(createQuarantineEntry("unwritable_line", index,
                        describe(entry)));
                unwritable++;
            }
        }
        if (unwritable > 0) {
            warn("%d kept chat lines could not be written to the save and were quarantined",
                    Integer.valueOf(unwritable));
        }
        output.setTag(TAG_ENTRIES, list);
        output.setTag(TAG_CONSOLE_EVENTS, writeConsoleEvents(consoleEvents,
                consoleReactions));
        output.setTag(TAG_QUARANTINE, writeQuarantine(quarantine));
    }

    /**
     * The console's events oldest first, by id, each whole, with its
     * reactions in the layout an entry's take where it has any.
     */
    private static NBTTagList writeConsoleEvents(
            Collection<ChatConsoleEvent> events,
            Map<Long, ChatReactions> reactions) {
        List<ChatConsoleEvent> ordered = new ArrayList<ChatConsoleEvent>();
        if (events != null) {
            for (ChatConsoleEvent event : events) {
                if (event != null) {
                    ordered.add(event);
                }
            }
        }
        Collections.sort(ordered, EVENT_ORDER);
        NBTTagList list = new NBTTagList();
        for (ChatConsoleEvent event : ordered) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong(TAG_EVENT_ID, event.getId());
            tag.setLong(TAG_EVENT_TIMESTAMP, event.getTimestampMillis());
            tag.setString(TAG_EVENT_KIND, event.getKind().name());
            tag.setString(TAG_EVENT_SEVERITY, event.getSeverity().name());
            tag.setString(TAG_EVENT_ACTOR, event.getActor());
            tag.setString(TAG_EVENT_TEXT, event.getText());
            tag.setString(TAG_EVENT_CONTEXT, event.getContext());
            if (event.getActorIdentity() != null) {
                writeUuid(tag, TAG_EVENT_ACTOR_ID,
                        event.getActorIdentity().getPlayerId());
            }
            if (event.getReport() != null) {
                tag.setTag(TAG_EVENT_REPORT, writeReport(event.getReport()));
            }
            ChatReactions reacted = reactions == null ? null
                    : reactions.get(Long.valueOf(event.getId()));
            if (reacted != null && !reacted.isEmpty()) {
                tag.setTag(TAG_REACTIONS, writeReactions(reacted));
            }
            list.appendTag(tag);
        }
        return list;
    }

    /**
     * One console event as the save holds it, or null with the reason
     * when the save cannot vouch for it: a kind or severity this build
     * does not know, an id that is none, words past the event's own
     * bounds or none at all.
     */
    private static ChatConsoleEvent readConsoleEvent(NBTTagCompound raw,
                                                     String[] failureReason) {
        if (!raw.hasKey(TAG_EVENT_ID, Constants.NBT.TAG_LONG)
                || !raw.hasKey(TAG_EVENT_TIMESTAMP, Constants.NBT.TAG_LONG)
                || !raw.hasKey(TAG_EVENT_KIND, Constants.NBT.TAG_STRING)
                || !raw.hasKey(TAG_EVENT_SEVERITY, Constants.NBT.TAG_STRING)
                || !raw.hasKey(TAG_EVENT_ACTOR, Constants.NBT.TAG_STRING)
                || !raw.hasKey(TAG_EVENT_TEXT, Constants.NBT.TAG_STRING)
                || !raw.hasKey(TAG_EVENT_CONTEXT, Constants.NBT.TAG_STRING)) {
            failureReason[0] = "missing_event_field";
            return null;
        }
        long id = raw.getLong(TAG_EVENT_ID);
        ChatConsoleEvent.Kind kind = kindOf(raw.getString(TAG_EVENT_KIND));
        ChatConsoleEvent.Severity severity =
                severityOf(raw.getString(TAG_EVENT_SEVERITY));
        String actor = raw.getString(TAG_EVENT_ACTOR);
        String text = raw.getString(TAG_EVENT_TEXT);
        String context = raw.getString(TAG_EVENT_CONTEXT);
        if (id <= 0L || kind == null || severity == null
                || actor.length() > ChatConsoleEvent.MAX_ACTOR_LENGTH
                || text.trim().length() == 0
                || text.length() > ChatConsoleEvent.MAX_TEXT_LENGTH
                || context.length() > ChatConsoleEvent.MAX_CONTEXT_LENGTH
                || !ChatConsoleEvent.isContext(context)) {
            failureReason[0] = "invalid_event";
            return null;
        }
        UUID actorId = readUuid(raw, TAG_EVENT_ACTOR_ID);
        ChatNamedPlayer actorIdentity = actorId == null ? null
                : ChatNamedPlayer.account(actorId, actor);
        ChatConsoleEvent.Report report = null;
        if (raw.hasKey(TAG_EVENT_REPORT, Constants.NBT.TAG_COMPOUND)) {
            report = readReport(raw.getCompoundTag(TAG_EVENT_REPORT));
            if (report == null) {
                failureReason[0] = "invalid_report";
                return null;
            }
        }
        if ((kind == ChatConsoleEvent.Kind.REPORT) != (report != null)) {
            failureReason[0] = "invalid_report";
            return null;
        }
        return new ChatConsoleEvent(id, raw.getLong(TAG_EVENT_TIMESTAMP),
                kind, severity, actor, text, context, actorIdentity, report);
    }

    private static NBTTagCompound writeReport(ChatConsoleEvent.Report report) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(TAG_REPORT_REASON, report.getReason().name());
        tag.setString(TAG_REPORT_NOTE, report.getNote());
        tag.setLong(TAG_REPORT_MESSAGE, report.getMessageId());
        tag.setString(TAG_REPORT_AUTHOR, report.getAuthor());
        tag.setInteger(TAG_REPORT_AUTHOR_COLOR, report.getAuthorColor());
        tag.setString(TAG_REPORT_EXCERPT, report.getExcerpt());
        tag.setString(TAG_REPORT_LINK, report.getLink());
        return tag;
    }

    /** A report as the save holds it, or null when the save cannot vouch for it. */
    private static ChatConsoleEvent.Report readReport(NBTTagCompound raw) {
        ChatReportReason reason = null;
        for (ChatReportReason known : ChatReportReason.values()) {
            if (known.name().equals(raw.getString(TAG_REPORT_REASON))) {
                reason = known;
            }
        }
        String note = raw.getString(TAG_REPORT_NOTE);
        String author = raw.getString(TAG_REPORT_AUTHOR);
        String excerpt = raw.getString(TAG_REPORT_EXCERPT);
        String link = raw.getString(TAG_REPORT_LINK);
        if (reason == null || !raw.hasKey(TAG_REPORT_MESSAGE, Constants.NBT.TAG_LONG)
                || note.length() > ChatConsoleEvent.Report.MAX_NOTE_LENGTH
                || author.length() > ChatConsoleEvent.Report.MAX_AUTHOR_LENGTH
                || excerpt.length() > ChatReplyReference.MAX_EXCERPT_CHARACTERS
                || link.length() > ChatConsoleEvent.Report.MAX_LINK_LENGTH) {
            return null;
        }
        try {
            return new ChatConsoleEvent.Report(reason, note,
                    raw.getLong(TAG_REPORT_MESSAGE), author,
                    raw.getInteger(TAG_REPORT_AUTHOR_COLOR), excerpt, link);
        } catch (IllegalArgumentException unfit) {
            return null;
        }
    }

    private static ChatConsoleEvent.Kind kindOf(String name) {
        for (ChatConsoleEvent.Kind kind : ChatConsoleEvent.Kind.values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return null;
    }

    private static ChatConsoleEvent.Severity severityOf(String name) {
        for (ChatConsoleEvent.Severity severity
                : ChatConsoleEvent.Severity.values()) {
            if (severity.name().equals(name)) {
                return severity;
            }
        }
        return null;
    }

    /** What is kept of a line that could not be written: its names, not its bytes. */
    private static NBTTagCompound describe(ChatHistory.Entry entry) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong(TAG_MESSAGE_ID, entry.forOthers.getMessageId());
        tag.setString(TAG_AUTHOR_NAME, entry.author == null ? "" : entry.author);
        tag.setString(TAG_CHANNEL, entry.channelId == null ? "" : entry.channelId);
        tag.setLong(TAG_TIMESTAMP, entry.timestampMillis);
        return tag;
    }

    public static ReadResult read(NBTTagCompound source) {
        NBTTagCompound safeSource = source == null ? new NBTTagCompound() : source;
        int version = safeSource.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? safeSource.getInteger(TAG_DATA_VERSION) : 0;
        if (version > CURRENT_ROOT_DATA_VERSION || version < 0) {
            warn("Chat history data uses unsupported version %d; data will remain read-only",
                    Integer.valueOf(version));
            return ReadResult.unsupported(safeSource, version);
        }
        if (safeSource.hasKey(TAG_ENTRIES)
                && !safeSource.hasKey(TAG_ENTRIES, Constants.NBT.TAG_LIST)) {
            return ReadResult.unsupported(safeSource, -1);
        }
        boolean repaired = version != CURRENT_ROOT_DATA_VERSION
                || !safeSource.hasKey(TAG_ENTRIES, Constants.NBT.TAG_LIST);
        QuarantineReadResult quarantine = readQuarantine(safeSource);
        if (!quarantine.supported) {
            return ReadResult.unsupported(safeSource, quarantine.unsupportedVersion);
        }
        repaired |= quarantine.repaired;
        List<NBTTagCompound> quarantinedEntries =
                new ArrayList<NBTTagCompound>(quarantine.entries);
        List<ChatHistory.Entry> entries = new ArrayList<ChatHistory.Entry>();
        Set<Long> seenIds = new HashSet<Long>();
        NBTTagList list = safeSource.getTagList(TAG_ENTRIES,
                Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound raw = list.getCompoundTagAt(index);
            EntryReadResult result = readEntry(raw);
            if (result.unsupportedVersion != Integer.MIN_VALUE) {
                return ReadResult.unsupported(safeSource, result.unsupportedVersion);
            }
            if (result.entry == null) {
                quarantinedEntries.add(createQuarantineEntry(
                        result.failureReason, index, raw));
                repaired = true;
                continue;
            }
            Long id = Long.valueOf(result.entry.forOthers.getMessageId());
            if (!seenIds.add(id)) {
                quarantinedEntries.add(createQuarantineEntry(
                        "duplicate_message", index, raw));
                repaired = true;
            } else if (entries.size() >= MAX_ENTRIES) {
                quarantinedEntries.add(createQuarantineEntry(
                        "over_capacity", index, raw));
                repaired = true;
            } else {
                entries.add(result.entry);
                repaired |= result.repaired;
            }
        }
        Collections.sort(entries, ENTRY_ORDER);
        if (safeSource.hasKey(TAG_CONSOLE_EVENTS)
                && !safeSource.hasKey(TAG_CONSOLE_EVENTS, Constants.NBT.TAG_LIST)) {
            return ReadResult.unsupported(safeSource, -1);
        }
        // A save without the console's events reads as an empty console
        // and is written whole.
        repaired |= !safeSource.hasKey(TAG_CONSOLE_EVENTS, Constants.NBT.TAG_LIST);
        List<ChatConsoleEvent> events = new ArrayList<ChatConsoleEvent>();
        Map<Long, ChatReactions> eventReactions = new HashMap<Long, ChatReactions>();
        Set<Long> seenEventIds = new HashSet<Long>();
        NBTTagList eventList = safeSource.getTagList(TAG_CONSOLE_EVENTS,
                Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < eventList.tagCount(); index++) {
            NBTTagCompound raw = eventList.getCompoundTagAt(index);
            String[] failureReason = new String[1];
            ChatConsoleEvent event = readConsoleEvent(raw, failureReason);
            // An event's reactions read as the newest entry layout reads
            // them; ones that cannot be read whole take the event into
            // the quarantine with them.
            ChatReactions reactions = event == null ? null
                    : readReactions(raw, FOREIGN_ENTRY_DATA_VERSION);
            if (event == null || reactions == null) {
                quarantinedEntries.add(createQuarantineEntry(
                        event == null ? failureReason[0] : "invalid_reactions",
                        index, raw));
                repaired = true;
            } else if (!seenEventIds.add(Long.valueOf(event.getId()))) {
                quarantinedEntries.add(createQuarantineEntry(
                        "duplicate_event", index, raw));
                repaired = true;
            } else if (events.size() >= MAX_CONSOLE_EVENTS) {
                quarantinedEntries.add(createQuarantineEntry(
                        "over_capacity", index, raw));
                repaired = true;
            } else {
                events.add(event);
                if (!reactions.isEmpty()) {
                    eventReactions.put(Long.valueOf(event.getId()), reactions);
                }
            }
        }
        Collections.sort(events, EVENT_ORDER);
        return ReadResult.success(entries, events, eventReactions, repaired,
                quarantinedEntries);
    }

    static NBTTagCompound writeEntry(ChatHistory.Entry entry) {
        NBTTagCompound tag = new NBTTagCompound();
        boolean reacted = entry.reactions != null && !entry.reactions.isEmpty();
        tag.setInteger(TAG_DATA_VERSION, !reacted ? PLAIN_ENTRY_DATA_VERSION
                : entry.reactions.hasForeign() ? FOREIGN_ENTRY_DATA_VERSION
                : REACTED_ENTRY_DATA_VERSION);
        tag.setLong(TAG_MESSAGE_ID, entry.forOthers.getMessageId());
        if (entry.authorId != null) {
            writeUuid(tag, TAG_AUTHOR_UUID, entry.authorId);
        }
        tag.setString(TAG_AUTHOR_NAME, entry.author);
        tag.setString(TAG_EXCERPT, entry.excerpt == null ? "" : entry.excerpt);
        tag.setString(TAG_CHANNEL, entry.channelId);
        tag.setLong(TAG_TIMESTAMP, entry.timestampMillis);
        tag.setTag(TAG_SEEN_BY, writeUuids(entry.seenBy));
        tag.setByteArray(TAG_FOR_OTHERS, bytesOf(entry.forOthers));
        if (entry.forSender != null && entry.forSender != entry.forOthers) {
            tag.setByteArray(TAG_FOR_SENDER, bytesOf(entry.forSender));
        }
        NBTTagCompound audience = new NBTTagCompound();
        Set<UUID> accounts = entry.audience.accounts();
        audience.setBoolean(TAG_AUDIENCE_HAS_ACCOUNTS, accounts != null);
        if (accounts != null) {
            audience.setTag(TAG_AUDIENCE_ACCOUNTS, writeUuids(accounts));
        }
        if (entry.audience.partyId() != null) {
            writeUuid(audience, TAG_AUDIENCE_PARTY, entry.audience.partyId());
        }
        audience.setString(TAG_AUDIENCE_FACTION, entry.audience.factionId() == null
                ? "" : entry.audience.factionId());
        audience.setBoolean(TAG_AUDIENCE_GATED, entry.audience.isGated());
        tag.setTag(TAG_AUDIENCE, audience);
        if (reacted) {
            tag.setTag(TAG_REACTIONS, writeReactions(entry.reactions));
        }
        return tag;
    }

    /**
     * Each emoji, and under it each reactor by id with the name they
     * reacted as, and a Discord member's with the Discord channel they
     * reacted in.
     */
    private static NBTTagList writeReactions(ChatReactions reactions) {
        NBTTagList kinds = new NBTTagList();
        for (Map.Entry<String, Map<UUID, String>> kind
                : reactions.snapshot().entrySet()) {
            NBTTagCompound kindTag = new NBTTagCompound();
            kindTag.setString(TAG_REACTION_EMOJI, kind.getKey());
            NBTTagList reactors = new NBTTagList();
            for (Map.Entry<UUID, String> reactor : kind.getValue().entrySet()) {
                NBTTagCompound reactorTag = new NBTTagCompound();
                writeUuid(reactorTag, TAG_REACTOR_ID, reactor.getKey());
                reactorTag.setString(TAG_REACTOR_NAME, reactor.getValue());
                String origin = reactions.originOf(kind.getKey(),
                        reactor.getKey());
                if (origin.length() > 0) {
                    reactorTag.setString(TAG_REACTOR_ORIGIN, origin);
                }
                reactors.appendTag(reactorTag);
            }
            kindTag.setTag(TAG_REACTION_REACTORS, reactors);
            kinds.appendTag(kindTag);
        }
        return kinds;
    }

    /**
     * The reactions an entry was written with, or null when they cannot
     * be read back whole: a list of the wrong kind, an emoji that is no
     * reaction key, a foreign emoji without its layout, a reactor
     * without an id, one named twice under one emoji, a channel that is
     * no Discord id or one on a player's reaction, or more than the
     * bounds allow. Such an entry is
     * quarantined whole rather than kept with part of its reactions.
     */
    private static ChatReactions readReactions(NBTTagCompound raw,
                                               int version) {
        ChatReactions reactions = new ChatReactions();
        if (!raw.hasKey(TAG_REACTIONS)) {
            return reactions;
        }
        if (!raw.hasKey(TAG_REACTIONS, Constants.NBT.TAG_LIST)) {
            return null;
        }
        NBTTagList kinds = raw.getTagList(TAG_REACTIONS, Constants.NBT.TAG_COMPOUND);
        if (kinds.tagCount() > ChatReactions.MAX_KINDS) {
            return null;
        }
        for (int kindIndex = 0; kindIndex < kinds.tagCount(); kindIndex++) {
            NBTTagCompound kind = kinds.getCompoundTagAt(kindIndex);
            String emoji = kind.getString(TAG_REACTION_EMOJI);
            // Before foreign emoji had a layout an entry held registry
            // names alone; from then on a foreign key the registry has
            // come to carry is filed under its name by the restore.
            if (!kind.hasKey(TAG_REACTION_REACTORS, Constants.NBT.TAG_LIST)
                    || (version < FOREIGN_ENTRY_DATA_VERSION
                            && ChatEmoji.fromName(emoji) == null)) {
                return null;
            }
            NBTTagList reactors = kind.getTagList(TAG_REACTION_REACTORS,
                    Constants.NBT.TAG_COMPOUND);
            if (reactors.tagCount() == 0) {
                return null;
            }
            for (int index = 0; index < reactors.tagCount(); index++) {
                NBTTagCompound reactor = reactors.getCompoundTagAt(index);
                UUID id = reactor.hasKey(TAG_REACTOR_ID + TAG_UUID_MOST)
                        ? readUuid(reactor, TAG_REACTOR_ID) : null;
                String origin = reactor.getString(TAG_REACTOR_ORIGIN);
                if (id == null || (origin.length() > 0
                        && !(isDiscordChannelId(origin)
                                && LostTalesChatMessagePacket.isDiscordSender(id)))
                        || !reactions.restore(emoji, id,
                                reactor.getString(TAG_REACTOR_NAME), origin)) {
                    return null;
                }
            }
        }
        return reactions;
    }

    /** A Discord channel's id: one to twenty digits. */
    private static boolean isDiscordChannelId(String value) {
        if (value.length() == 0 || value.length() > 20) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char each = value.charAt(index);
            if (each < '0' || each > '9') {
                return false;
            }
        }
        return true;
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
        if (!raw.hasKey(TAG_MESSAGE_ID, Constants.NBT.TAG_LONG)
                || !ChatMessageIds.isServerId(raw.getLong(TAG_MESSAGE_ID))) {
            return EntryReadResult.failure("invalid_message_id");
        }
        long messageId = raw.getLong(TAG_MESSAGE_ID);
        String author = raw.getString(TAG_AUTHOR_NAME);
        if (author == null || author.trim().length() == 0) {
            return EntryReadResult.failure("missing_author");
        }
        String channelId = raw.getString(TAG_CHANNEL);
        if (channelId == null || channelId.length() == 0) {
            return EntryReadResult.failure("missing_channel");
        }
        if (!raw.hasKey(TAG_TIMESTAMP, Constants.NBT.TAG_LONG)
                || raw.getLong(TAG_TIMESTAMP) <= 0L) {
            return EntryReadResult.failure("invalid_timestamp");
        }
        if (!raw.hasKey(TAG_FOR_OTHERS, Constants.NBT.TAG_BYTE_ARRAY)) {
            return EntryReadResult.failure("missing_line");
        }
        LostTalesChatMessagePacket forOthers = decode(raw.getByteArray(TAG_FOR_OTHERS));
        if (forOthers == null || forOthers.getChannel() == null
                || forOthers.getMessageId() != messageId
                || !channelId.equals(forOthers.getChannel().getId())) {
            return EntryReadResult.failure("invalid_line");
        }
        LostTalesChatMessagePacket forSender = forOthers;
        if (raw.hasKey(TAG_FOR_SENDER, Constants.NBT.TAG_BYTE_ARRAY)) {
            forSender = decode(raw.getByteArray(TAG_FOR_SENDER));
            if (forSender == null || forSender.getChannel() == null
                    || forSender.getMessageId() != messageId
                    || !channelId.equals(forSender.getChannel().getId())) {
                return EntryReadResult.failure("invalid_sender_line");
            }
        }
        List<UUID> seenBy = readUuids(raw, TAG_SEEN_BY);
        if (seenBy == null) {
            return EntryReadResult.failure("invalid_recipients");
        }
        if (!raw.hasKey(TAG_AUDIENCE, Constants.NBT.TAG_COMPOUND)) {
            return EntryReadResult.failure("missing_audience");
        }
        NBTTagCompound audienceTag = raw.getCompoundTag(TAG_AUDIENCE);
        if (!audienceTag.hasKey(TAG_AUDIENCE_HAS_ACCOUNTS, Constants.NBT.TAG_BYTE)
                || !audienceTag.hasKey(TAG_AUDIENCE_GATED, Constants.NBT.TAG_BYTE)) {
            return EntryReadResult.failure("invalid_audience");
        }
        List<UUID> accounts = null;
        if (audienceTag.getBoolean(TAG_AUDIENCE_HAS_ACCOUNTS)) {
            accounts = readUuids(audienceTag, TAG_AUDIENCE_ACCOUNTS);
            if (accounts == null) {
                return EntryReadResult.failure("invalid_audience");
            }
        }
        UUID partyId = null;
        if (audienceTag.hasKey(TAG_AUDIENCE_PARTY + TAG_UUID_MOST)) {
            partyId = readUuid(audienceTag, TAG_AUDIENCE_PARTY);
            if (partyId == null) {
                return EntryReadResult.failure("invalid_audience");
            }
        }
        ChatHistory.Audience audience = ChatHistory.Audience.restore(accounts,
                partyId, audienceTag.getString(TAG_AUDIENCE_FACTION),
                audienceTag.getBoolean(TAG_AUDIENCE_GATED));
        UUID authorId = raw.hasKey(TAG_AUTHOR_UUID + TAG_UUID_MOST)
                ? readUuid(raw, TAG_AUTHOR_UUID) : null;
        ChatReactions reactions = readReactions(raw, version);
        if (reactions == null) {
            return EntryReadResult.failure("invalid_reactions");
        }
        return EntryReadResult.success(new ChatHistory.Entry(authorId,
                author.trim(), raw.getString(TAG_EXCERPT), new HashSet<UUID>(seenBy),
                channelId, forSender, forOthers, audience,
                raw.getLong(TAG_TIMESTAMP), reactions),
                reactions.renamedOnRestore());
    }

    /** The line's wire bytes; what the save keeps and every client already reads. */
    static byte[] bytesOf(LostTalesChatMessagePacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    /** The line the bytes carry, or null for bytes no client would accept. */
    static LostTalesChatMessagePacket decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0
                || bytes.length > LostTalesChatMessagePacket.MAX_PACKET_BYTES) {
            return null;
        }
        try {
            LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket();
            packet.fromBytes(Unpooled.wrappedBuffer(bytes));
            return packet.isMalformed() ? null : packet;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static NBTTagList writeUuids(Collection<UUID> ids) {
        NBTTagList list = new NBTTagList();
        if (ids != null) {
            for (UUID id : ids) {
                if (id != null) {
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setLong(TAG_UUID_MOST, id.getMostSignificantBits());
                    tag.setLong(TAG_UUID_LEAST, id.getLeastSignificantBits());
                    list.appendTag(tag);
                }
            }
        }
        return list;
    }

    /** The ids under {@code key}; null for a list that is not one, or too long. */
    private static List<UUID> readUuids(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key)) {
            return Collections.emptyList();
        }
        if (!tag.hasKey(key, Constants.NBT.TAG_LIST)) {
            return null;
        }
        NBTTagList list = tag.getTagList(key, Constants.NBT.TAG_COMPOUND);
        if (list.tagCount() > MAX_ACCOUNTS_PER_ENTRY) {
            return null;
        }
        List<UUID> ids = new ArrayList<UUID>(list.tagCount());
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            if (!entry.hasKey(TAG_UUID_MOST, Constants.NBT.TAG_LONG)
                    || !entry.hasKey(TAG_UUID_LEAST, Constants.NBT.TAG_LONG)) {
                return null;
            }
            ids.add(new UUID(entry.getLong(TAG_UUID_MOST),
                    entry.getLong(TAG_UUID_LEAST)));
        }
        return ids;
    }

    private static void writeUuid(NBTTagCompound tag, String key, UUID value) {
        tag.setLong(key + TAG_UUID_MOST, value.getMostSignificantBits());
        tag.setLong(key + TAG_UUID_LEAST, value.getLeastSignificantBits());
    }

    private static UUID readUuid(NBTTagCompound tag, String key) {
        String most = key + TAG_UUID_MOST;
        String least = key + TAG_UUID_LEAST;
        if (!tag.hasKey(most, Constants.NBT.TAG_LONG)
                || !tag.hasKey(least, Constants.NBT.TAG_LONG)) {
            return null;
        }
        return new UUID(tag.getLong(most), tag.getLong(least));
    }

    private static NBTTagCompound writeQuarantine(
            Collection<NBTTagCompound> quarantinedEntries) {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger(TAG_DATA_VERSION, CURRENT_QUARANTINE_DATA_VERSION);
        NBTTagList list = new NBTTagList();
        if (quarantinedEntries != null) {
            for (NBTTagCompound entry : quarantinedEntries) {
                if (entry != null) {
                    list.appendTag(entry.copy());
                }
            }
        }
        root.setTag(TAG_QUARANTINE_ENTRIES, list);
        return root;
    }

    private static QuarantineReadResult readQuarantine(NBTTagCompound source) {
        if (!source.hasKey(TAG_QUARANTINE)) {
            return new QuarantineReadResult(true, -1,
                    Collections.<NBTTagCompound>emptyList(), false);
        }
        if (!source.hasKey(TAG_QUARANTINE, Constants.NBT.TAG_COMPOUND)) {
            return new QuarantineReadResult(false, -1,
                    Collections.<NBTTagCompound>emptyList(), false);
        }
        NBTTagCompound root = source.getCompoundTag(TAG_QUARANTINE);
        int version = root.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? root.getInteger(TAG_DATA_VERSION) : 0;
        if (version > CURRENT_QUARANTINE_DATA_VERSION || version < 0) {
            return new QuarantineReadResult(false, version,
                    Collections.<NBTTagCompound>emptyList(), false);
        }
        List<NBTTagCompound> entries = new ArrayList<NBTTagCompound>();
        if (root.hasKey(TAG_QUARANTINE_ENTRIES, Constants.NBT.TAG_LIST)) {
            NBTTagList list = root.getTagList(TAG_QUARANTINE_ENTRIES,
                    Constants.NBT.TAG_COMPOUND);
            for (int index = 0; index < list.tagCount(); index++) {
                entries.add((NBTTagCompound)list.getCompoundTagAt(index).copy());
            }
        }
        return new QuarantineReadResult(true, -1, entries,
                version != CURRENT_QUARANTINE_DATA_VERSION
                        || !root.hasKey(TAG_QUARANTINE_ENTRIES,
                                Constants.NBT.TAG_LIST));
    }

    private static NBTTagCompound createQuarantineEntry(String reason,
                                                        int entryIndex,
                                                        NBTTagCompound original) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(TAG_REASON, reason == null ? "unknown" : reason);
        entry.setInteger(TAG_ENTRY_INDEX, entryIndex);
        if (original != null) {
            entry.setTag(TAG_ORIGINAL_DATA, original.copy());
        }
        return entry;
    }

    private static void warn(String format, Object... args) {
        try {
            FMLLog.warning("[%s] " + format, prependModId(args));
        } catch (RuntimeException ignored) {
            // FML's logger is not bootstrapped in isolated codec unit tests.
        }
    }

    private static Object[] prependModId(Object[] args) {
        Object[] values = new Object[(args == null ? 0 : args.length) + 1];
        values[0] = LostTalesMetaData.MOD_ID;
        if (args != null) {
            System.arraycopy(args, 0, values, 1, args.length);
        }
        return values;
    }

    /** Oldest first: ids are handed out in the order the server accepted the lines. */
    private static final Comparator<ChatHistory.Entry> ENTRY_ORDER =
            new Comparator<ChatHistory.Entry>() {
                @Override
                public int compare(ChatHistory.Entry left, ChatHistory.Entry right) {
                    long a = left.forOthers.getMessageId();
                    long b = right.forOthers.getMessageId();
                    return a < b ? -1 : a == b ? 0 : 1;
                }
            };

    /** Oldest first: the console's ids come from the same allocator. */
    private static final Comparator<ChatConsoleEvent> EVENT_ORDER =
            new Comparator<ChatConsoleEvent>() {
                @Override
                public int compare(ChatConsoleEvent left, ChatConsoleEvent right) {
                    long a = left.getId();
                    long b = right.getId();
                    return a < b ? -1 : a == b ? 0 : 1;
                }
            };

    private static final class EntryReadResult {
        final ChatHistory.Entry entry;
        final String failureReason;
        final int unsupportedVersion;
        /** Whether the entry was read back other than it was saved, and is to be written again. */
        final boolean repaired;

        private EntryReadResult(ChatHistory.Entry entry, String failureReason,
                                int unsupportedVersion, boolean repaired) {
            this.entry = entry;
            this.failureReason = failureReason;
            this.unsupportedVersion = unsupportedVersion;
            this.repaired = repaired;
        }

        static EntryReadResult success(ChatHistory.Entry entry,
                                       boolean repaired) {
            return new EntryReadResult(entry, null, Integer.MIN_VALUE, repaired);
        }

        static EntryReadResult failure(String reason) {
            return new EntryReadResult(null, reason, Integer.MIN_VALUE, false);
        }

        static EntryReadResult unsupported(int version) {
            return new EntryReadResult(null, null, version, false);
        }
    }

    private static final class QuarantineReadResult {
        final boolean supported;
        final int unsupportedVersion;
        final List<NBTTagCompound> entries;
        final boolean repaired;

        QuarantineReadResult(boolean supported, int unsupportedVersion,
                             List<NBTTagCompound> entries, boolean repaired) {
            this.supported = supported;
            this.unsupportedVersion = unsupportedVersion;
            this.entries = entries;
            this.repaired = repaired;
        }
    }

    public static final class ReadResult {
        private final List<ChatHistory.Entry> entries;
        private final List<ChatConsoleEvent> consoleEvents;
        private final Map<Long, ChatReactions> consoleReactions;
        private final boolean repaired;
        private final List<NBTTagCompound> quarantineEntries;
        private final boolean readOnly;
        private final int unsupportedVersion;
        private final NBTTagCompound originalData;

        private ReadResult(List<ChatHistory.Entry> entries,
                           List<ChatConsoleEvent> consoleEvents,
                           Map<Long, ChatReactions> consoleReactions,
                           boolean repaired,
                           List<NBTTagCompound> quarantineEntries,
                           boolean readOnly, int unsupportedVersion,
                           NBTTagCompound originalData) {
            this.entries = Collections.unmodifiableList(
                    new ArrayList<ChatHistory.Entry>(entries));
            this.consoleEvents = Collections.unmodifiableList(
                    new ArrayList<ChatConsoleEvent>(consoleEvents));
            this.consoleReactions = Collections.unmodifiableMap(
                    new HashMap<Long, ChatReactions>(consoleReactions));
            this.repaired = repaired;
            this.quarantineEntries = Collections.unmodifiableList(
                    new ArrayList<NBTTagCompound>(quarantineEntries));
            this.readOnly = readOnly;
            this.unsupportedVersion = unsupportedVersion;
            this.originalData = originalData;
        }

        private static ReadResult success(List<ChatHistory.Entry> entries,
                                          List<ChatConsoleEvent> consoleEvents,
                                          Map<Long, ChatReactions> consoleReactions,
                                          boolean repaired,
                                          List<NBTTagCompound> quarantine) {
            return new ReadResult(entries, consoleEvents, consoleReactions,
                    repaired, quarantine, false, -1, null);
        }

        private static ReadResult unsupported(NBTTagCompound original, int version) {
            return new ReadResult(Collections.<ChatHistory.Entry>emptyList(),
                    Collections.<ChatConsoleEvent>emptyList(),
                    Collections.<Long, ChatReactions>emptyMap(), false,
                    Collections.<NBTTagCompound>emptyList(), true, version,
                    (NBTTagCompound)original.copy());
        }

        /** The kept lines, oldest first. */
        public List<ChatHistory.Entry> getEntries() {
            return this.entries;
        }

        /** The console's kept events, oldest first. */
        /** The reactions the console's events were saved with, by the event's id. */
        public Map<Long, ChatReactions> getConsoleReactions() {
            return this.consoleReactions;
        }

        public List<ChatConsoleEvent> getConsoleEvents() {
            return this.consoleEvents;
        }

        public boolean wasRepaired() {
            return this.repaired;
        }

        public List<NBTTagCompound> getQuarantineEntriesCopy() {
            List<NBTTagCompound> copy = new ArrayList<NBTTagCompound>();
            for (NBTTagCompound entry : this.quarantineEntries) {
                copy.add((NBTTagCompound)entry.copy());
            }
            return copy;
        }

        public boolean isReadOnly() {
            return this.readOnly;
        }

        public int getUnsupportedVersion() {
            return this.unsupportedVersion;
        }

        public NBTTagCompound getOriginalDataCopy() {
            return this.originalData == null ? null
                    : (NBTTagCompound)this.originalData.copy();
        }
    }
}
