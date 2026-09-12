package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.ChatMessageIds;
import cpw.mods.fml.common.FMLLog;
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
 * Versioned NBT codec for the Discord bridge's message links: each game
 * message the bridge carried, oldest first, with every Discord copy it
 * has — the copy's Discord id, the channel it is in, the binding it was
 * posted through and the reply header it opened with.
 *
 * <p>No webhook URL is ever written, since a webhook URL is a
 * credential: the binding id stands in for it, and a copy whose channel
 * Discord never named is written under its binding
 * ({@code binding:<id>}) rather than under its webhook. A copy that can
 * be written neither way is left out, and on read a destination that is
 * neither a channel nor a binding is refused, so a URL cannot pass for
 * one. An entry the codec cannot vouch for is quarantined whole with
 * its reason; data newer than this codec is left alone and the store
 * goes read-only. Nothing read is dropped or cut. A write keeps every
 * bound a read checks: a message's copies past
 * {@link #MAX_COPIES_PER_ENTRY} are not saved, the oldest kept.</p>
 */
final class DiscordMessageLinkNbtCodec {

    static final int CURRENT_ROOT_DATA_VERSION = 1;
    static final int CURRENT_ENTRY_DATA_VERSION = 1;
    static final int CURRENT_QUARANTINE_DATA_VERSION = 1;
    /** Safety bound on messages read back; entries past it are quarantined. */
    static final int MAX_ENTRIES = DiscordMessageLinks.MAX_LINKS;
    /** The most copies one message may name: it has one per binding of its channel. */
    static final int MAX_COPIES_PER_ENTRY = 64;
    /** The longest reply header kept; Discord takes no longer message. */
    static final int MAX_HEADER_LENGTH = 2000;
    /** The longest binding id kept. */
    static final int MAX_BINDING_ID_LENGTH = 128;
    /** The longest Discord id read; a snowflake has at most twenty digits. */
    private static final int MAX_DISCORD_ID_LENGTH = 24;

    /** A destination naming a Discord channel by id. */
    static final String CHANNEL_PREFIX = "channel:";
    /** A saved destination naming the binding a post went through. */
    static final String BINDING_PREFIX = "binding:";

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_MESSAGES = "Messages";
    private static final String TAG_QUARANTINE = "Quarantine";
    private static final String TAG_QUARANTINE_ENTRIES = "Entries";
    private static final String TAG_REASON = "Reason";
    private static final String TAG_ENTRY_INDEX = "EntryIndex";
    private static final String TAG_ORIGINAL_DATA = "OriginalData";

    private static final String TAG_MESSAGE_ID = "MessageId";
    private static final String TAG_COPIES = "Copies";
    private static final String TAG_DISCORD_ID = "DiscordId";
    private static final String TAG_DESTINATION = "Destination";
    private static final String TAG_BINDING = "Binding";
    private static final String TAG_HEADER = "Header";

    private DiscordMessageLinkNbtCodec() {}

    /** Writes the links in the order given, oldest first, and the quarantine as it was read. */
    static void write(NBTTagCompound output,
                      Collection<DiscordMessageLinks.SavedLink> links,
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
        output.setTag(TAG_QUARANTINE, writeQuarantine(quarantinedEntries));
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
     * written only when it reads back as one, and a copy placed under a
     * binding that does not is not written at all.
     */
    private static NBTTagCompound writeCopy(DiscordMessageLinks.SavedCopy copy) {
        String binding = isBindingId(copy.bindingId) ? copy.bindingId : "";
        boolean placed = isChannelDestination(copy.destination)
                || (binding.length() > 0
                        && copy.destination.equals(BINDING_PREFIX + binding));
        if (!isDiscordId(copy.discordId) || !placed
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

    static ReadResult read(NBTTagCompound source) {
        NBTTagCompound safeSource = source == null ? new NBTTagCompound() : source;
        int version = safeSource.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? safeSource.getInteger(TAG_DATA_VERSION) : 0;
        if (version > CURRENT_ROOT_DATA_VERSION || version < 0) {
            warn("Discord message link data uses unsupported version %d; data will remain read-only",
                    Integer.valueOf(version));
            return ReadResult.unsupported(safeSource, version);
        }
        if (safeSource.hasKey(TAG_MESSAGES)
                && !holdsCompoundsOnly(safeSource, TAG_MESSAGES)) {
            return ReadResult.unsupported(safeSource, -1);
        }
        boolean repaired = version != CURRENT_ROOT_DATA_VERSION
                || !safeSource.hasKey(TAG_MESSAGES, Constants.NBT.TAG_LIST);
        QuarantineReadResult quarantine = readQuarantine(safeSource);
        if (!quarantine.supported) {
            return ReadResult.unsupported(safeSource, quarantine.unsupportedVersion);
        }
        repaired |= quarantine.repaired;
        List<NBTTagCompound> quarantinedEntries =
                new ArrayList<NBTTagCompound>(quarantine.entries);
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
                quarantinedEntries.add(createQuarantineEntry(reason, index, raw));
                repaired = true;
                continue;
            }
            seenMessages.add(Long.valueOf(result.link.messageId));
            for (DiscordMessageLinks.SavedCopy copy : result.link.copies) {
                seenDiscordIds.add(copy.discordId);
            }
            links.add(result.link);
        }
        return ReadResult.success(links, repaired, quarantinedEntries);
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
                    || !isDiscordId(copy.getString(TAG_DISCORD_ID))) {
                return EntryReadResult.failure("invalid_discord_id");
            }
            String discordId = copy.getString(TAG_DISCORD_ID);
            if (!copy.hasKey(TAG_DESTINATION, Constants.NBT.TAG_STRING)) {
                return EntryReadResult.failure("invalid_destination");
            }
            String destination = copy.getString(TAG_DESTINATION);
            if (copy.hasKey(TAG_BINDING)
                    && !copy.hasKey(TAG_BINDING, Constants.NBT.TAG_STRING)) {
                return EntryReadResult.failure("invalid_binding");
            }
            String binding = copy.getString(TAG_BINDING);
            if (binding.length() > 0 && !isBindingId(binding)) {
                return EntryReadResult.failure("invalid_binding");
            }
            if (isBindingDestination(destination)) {
                // A copy kept under its binding names that very binding.
                if (!destination.equals(BINDING_PREFIX + binding)) {
                    return EntryReadResult.failure("invalid_binding");
                }
            } else if (!isChannelDestination(destination)) {
                return EntryReadResult.failure("invalid_destination");
            }
            if (copy.hasKey(TAG_HEADER)
                    && !copy.hasKey(TAG_HEADER, Constants.NBT.TAG_STRING)) {
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

    /** Whether a value is a Discord id: a snowflake, digits only. */
    static boolean isDiscordId(String value) {
        if (value == null || value.length() == 0
                || value.length() > MAX_DISCORD_ID_LENGTH) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    /** Whether a destination names a Discord channel by id. */
    static boolean isChannelDestination(String value) {
        return value != null && value.startsWith(CHANNEL_PREFIX)
                && isDiscordId(value.substring(CHANNEL_PREFIX.length()));
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

    /** Whether a destination names the binding a post whose channel was never learnt went through. */
    static boolean isBindingDestination(String value) {
        return value != null && value.startsWith(BINDING_PREFIX)
                && isBindingId(value.substring(BINDING_PREFIX.length()));
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
        if (root.hasKey(TAG_QUARANTINE_ENTRIES)
                && !holdsCompoundsOnly(root, TAG_QUARANTINE_ENTRIES)) {
            return new QuarantineReadResult(false, -1,
                    Collections.<NBTTagCompound>emptyList(), false);
        }
        List<NBTTagCompound> entries = new ArrayList<NBTTagCompound>();
        if (root.hasKey(TAG_QUARANTINE_ENTRIES, Constants.NBT.TAG_LIST)) {
            NBTTagList list = root.getTagList(TAG_QUARANTINE_ENTRIES,
                    Constants.NBT.TAG_COMPOUND);
            for (int index = 0; index < list.tagCount(); index++) {
                entries.add((NBTTagCompound) list.getCompoundTagAt(index).copy());
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

    static final class ReadResult {
        private final List<DiscordMessageLinks.SavedLink> entries;
        private final boolean repaired;
        private final List<NBTTagCompound> quarantineEntries;
        private final boolean readOnly;
        private final int unsupportedVersion;
        private final NBTTagCompound originalData;

        private ReadResult(List<DiscordMessageLinks.SavedLink> entries,
                           boolean repaired,
                           List<NBTTagCompound> quarantineEntries,
                           boolean readOnly, int unsupportedVersion,
                           NBTTagCompound originalData) {
            this.entries = Collections.unmodifiableList(
                    new ArrayList<DiscordMessageLinks.SavedLink>(entries));
            this.repaired = repaired;
            this.quarantineEntries = Collections.unmodifiableList(
                    new ArrayList<NBTTagCompound>(quarantineEntries));
            this.readOnly = readOnly;
            this.unsupportedVersion = unsupportedVersion;
            this.originalData = originalData;
        }

        private static ReadResult success(List<DiscordMessageLinks.SavedLink> entries,
                                          boolean repaired,
                                          List<NBTTagCompound> quarantine) {
            return new ReadResult(entries, repaired, quarantine, false, -1, null);
        }

        private static ReadResult unsupported(NBTTagCompound original, int version) {
            return new ReadResult(
                    Collections.<DiscordMessageLinks.SavedLink>emptyList(), false,
                    Collections.<NBTTagCompound>emptyList(), true, version,
                    (NBTTagCompound) original.copy());
        }

        /** The links read, oldest first. */
        List<DiscordMessageLinks.SavedLink> getEntries() {
            return this.entries;
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
