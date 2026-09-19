package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.chat.ChatStatusLine;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-to-client: which identities of one or more accounts show a
 * presence, and which, each with its status line — empty for none. Each
 * account stated replaces whatever the client held of it: an identity it
 * leaves out reads as Offline, with no line, and an account stated with
 * none is offline everywhere — it left, or hides in every identity it
 * uses. One account when someone's presence changes, to everyone; every
 * account shown to a player who has just joined, {@link #MAX_ACCOUNTS}
 * to a payload, which keeps a full one inside what a payload may carry.
 * Only Online, Away and Do Not Disturb travel: Invisible is never told to
 * anyone, and Offline is what is not said. A payload naming an account or
 * an identity twice, another status, or more than the bounds allow is
 * refused whole.
 */
public final class LostTalesChatPresenceSyncPacket implements IMessage {
    public static final int MAX_ACCOUNTS = 16;
    /** Identities one account may show at once: the account, the played character and the chat character, with room. */
    public static final int MAX_SHOWN = 8;
    private static final int MAX_PACKET_BYTES = 2 + MAX_ACCOUNTS
            * (17 + MAX_SHOWN * (18 + 2 + ChatStatusLine.MAX_BYTES));

    private Map<UUID, Map<ChatPresenceIdentity, ChatPresence>> accounts =
            Collections.emptyMap();
    private Map<UUID, Map<ChatPresenceIdentity, String>> lines =
            Collections.emptyMap();
    private boolean malformed;

    public LostTalesChatPresenceSyncPacket() {}

    /**
     * What each account's identities show, and the lines of those that
     * have one; a line of an identity not shown is not told.
     */
    public LostTalesChatPresenceSyncPacket(
            Map<UUID, Map<ChatPresenceIdentity, ChatPresence>> accounts,
            Map<UUID, Map<ChatPresenceIdentity, String>> lines) {
        Map<UUID, Map<ChatPresenceIdentity, ChatPresence>> kept =
                new LinkedHashMap<UUID, Map<ChatPresenceIdentity, ChatPresence>>();
        Map<UUID, Map<ChatPresenceIdentity, String>> keptLines =
                new LinkedHashMap<UUID, Map<ChatPresenceIdentity, String>>();
        if (accounts != null) {
            for (Map.Entry<UUID, Map<ChatPresenceIdentity, ChatPresence>> account
                    : accounts.entrySet()) {
                if (account.getKey() == null || kept.size() >= MAX_ACCOUNTS) {
                    continue;
                }
                Map<ChatPresenceIdentity, ChatPresence> shown =
                        shownOnly(account.getValue());
                kept.put(account.getKey(), shown);
                keptLines.put(account.getKey(), linesOf(shown,
                        lines == null ? null : lines.get(account.getKey())));
            }
        }
        this.accounts = Collections.unmodifiableMap(kept);
        this.lines = Collections.unmodifiableMap(keptLines);
    }

    /** The lines of the identities shown, cleaned, the empty ones gone. */
    private static Map<ChatPresenceIdentity, String> linesOf(
            Map<ChatPresenceIdentity, ChatPresence> shown,
            Map<ChatPresenceIdentity, String> lines) {
        Map<ChatPresenceIdentity, String> kept =
                new LinkedHashMap<ChatPresenceIdentity, String>();
        if (lines != null) {
            for (ChatPresenceIdentity identity : shown.keySet()) {
                String line = ChatStatusLine.clean(lines.get(identity));
                if (line.length() > 0) {
                    kept.put(identity, line);
                }
            }
        }
        return Collections.unmodifiableMap(kept);
    }

    /** One account's identities, bounded, with nothing but what travels. */
    private static Map<ChatPresenceIdentity, ChatPresence> shownOnly(
            Map<ChatPresenceIdentity, ChatPresence> shown) {
        Map<ChatPresenceIdentity, ChatPresence> kept =
                new LinkedHashMap<ChatPresenceIdentity, ChatPresence>();
        if (shown != null) {
            for (Map.Entry<ChatPresenceIdentity, ChatPresence> entry
                    : shown.entrySet()) {
                if (entry.getKey() != null && travels(entry.getValue())
                        && kept.size() < MAX_SHOWN) {
                    kept.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return Collections.unmodifiableMap(kept);
    }

    /** Whether a status is ever told: never Invisible, and Offline is what is left unsaid. */
    static boolean travels(ChatPresence presence) {
        return presence == ChatPresence.ONLINE || presence == ChatPresence.AWAY
                || presence == ChatPresence.DO_NOT_DISTURB;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat presence sync size");
            }
            int count = buffer.readUnsignedShort();
            if (count > MAX_ACCOUNTS) {
                throw new LostTalesPacketCodec.DecodeException("too many presences");
            }
            Map<UUID, Map<ChatPresenceIdentity, ChatPresence>> read =
                    new LinkedHashMap<UUID, Map<ChatPresenceIdentity, ChatPresence>>();
            Map<UUID, Map<ChatPresenceIdentity, String>> readLines =
                    new LinkedHashMap<UUID, Map<ChatPresenceIdentity, String>>();
            for (int index = 0; index < count; index++) {
                UUID account = new UUID(buffer.readLong(), buffer.readLong());
                int shownCount = buffer.readUnsignedByte();
                if (read.containsKey(account) || shownCount > MAX_SHOWN) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid presence account");
                }
                Map<ChatPresenceIdentity, ChatPresence> shown =
                        new LinkedHashMap<ChatPresenceIdentity, ChatPresence>();
                Map<ChatPresenceIdentity, String> accountLines =
                        new LinkedHashMap<ChatPresenceIdentity, String>();
                for (int at = 0; at < shownCount; at++) {
                    ChatPresenceIdentity identity =
                            LostTalesChatPresencePacket.readIdentity(buffer);
                    ChatPresence presence = ChatPresence.fromCode(
                            buffer.readUnsignedByte());
                    if (!travels(presence) || shown.containsKey(identity)) {
                        throw new LostTalesPacketCodec.DecodeException(
                                "invalid presence entry");
                    }
                    shown.put(identity, presence);
                    String line = LostTalesPacketCodec.readUtf8String(buffer,
                            ChatStatusLine.MAX_BYTES);
                    if (line.length() > 0) {
                        accountLines.put(identity, line);
                    }
                }
                read.put(account, Collections.unmodifiableMap(shown));
                readLines.put(account,
                        Collections.unmodifiableMap(accountLines));
            }
            LostTalesPacketCodec.requireFinished(buffer);
            this.accounts = Collections.unmodifiableMap(read);
            this.lines = Collections.unmodifiableMap(readLines);
        } catch (RuntimeException failure) {
            this.malformed = true;
            this.accounts = Collections.emptyMap();
            this.lines = Collections.emptyMap();
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeShort(this.accounts.size());
        for (Map.Entry<UUID, Map<ChatPresenceIdentity, ChatPresence>> account
                : this.accounts.entrySet()) {
            buffer.writeLong(account.getKey().getMostSignificantBits());
            buffer.writeLong(account.getKey().getLeastSignificantBits());
            buffer.writeByte(account.getValue().size());
            Map<ChatPresenceIdentity, String> accountLines =
                    this.lines.get(account.getKey());
            for (Map.Entry<ChatPresenceIdentity, ChatPresence> entry
                    : account.getValue().entrySet()) {
                LostTalesChatPresencePacket.writeIdentity(buffer, entry.getKey());
                buffer.writeByte(entry.getValue().code());
                String line = accountLines == null ? null
                        : accountLines.get(entry.getKey());
                LostTalesPacketCodec.writeUtf8String(buffer,
                        line == null ? "" : line, ChatStatusLine.MAX_BYTES);
            }
        }
    }

    /** What each account stated shows, by account, in the order stated. */
    public Map<UUID, Map<ChatPresenceIdentity, ChatPresence>> getAccounts() {
        return this.accounts;
    }

    /**
     * The status lines of the identities shown, by account; an identity
     * with none is left out, and every account stated has its map.
     */
    public Map<UUID, Map<ChatPresenceIdentity, String>> getLines() {
        return this.lines;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler
            implements IMessageHandler<LostTalesChatPresenceSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatPresenceSyncPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleChatPresence(message);
                }
            });
            return null;
        }
    }
}
