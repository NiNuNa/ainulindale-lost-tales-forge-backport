package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.server.ChatMemberDirectory;
import com.ninuna.losttales.chat.server.ChatMemberWatches;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Client-to-server: who is in a conversation, for the member list of a
 * window showing it. It names the channel, the client's own key for the
 * conversation, which the answer carries back, and the fingerprint of the
 * answer the client holds for it ({@link
 * LostTalesChatMembersPacket#getFingerprint}, zero for none). A whisper
 * also names the other party — their account, the identity the
 * conversation is with, and that identity's character where the client
 * knows it — and the character of the player's own it is held as; an
 * empty account asks for the player alone, as a conversation with an NPC
 * does, whose NPC the client adds itself.
 *
 * <p>Which of a channel's conversations is meant — the faction, the
 * party — is the server's to say from the identity the player speaks as;
 * whether they may read the channel at all is asked again, and a named
 * party is looked up in the server's own records, never taken on trust.
 * The answer is {@link LostTalesChatMembersPacket}: empty for a channel
 * the player may not read, and only a word that nothing changed where the
 * fingerprint still matches.</p>
 */
public final class LostTalesChatMembersRequestPacket implements IMessage {
    private static final int MAX_CHANNEL_ID_BYTES = 64;
    private static final int MAX_ACCOUNT_BYTES = 64;
    private static final int MAX_IDENTITY_BYTES = 128;
    private static final int MAX_PACKET_BYTES = 5 + MAX_CHANNEL_ID_BYTES
            + 5 + LostTalesChatMembersPacket.MAX_CONVERSATION_KEY_BYTES
            + 5 + MAX_ACCOUNT_BYTES + 5 + MAX_IDENTITY_BYTES + 17 + 17 + 8;

    private String channelId = "";
    private String conversationKey = "";
    private String partnerAccount = "";
    private String partnerIdentity = "";
    private UUID partnerCharacterId;
    private UUID heldCharacterId;
    private long heldFingerprint;
    private boolean malformed;

    public LostTalesChatMembersRequestPacket() {}

    /**
     * Asks for {@code channel}'s conversation, which the client calls
     * {@code conversationKey}. For a whisper, {@code partnerAccount} names
     * the other party — empty for none — {@code partnerIdentity} the
     * identity the conversation is with, {@code partnerCharacterId} its
     * character where known, and {@code heldCharacterId} the player's own
     * character the conversation is held as, null for the account.
     */
    public LostTalesChatMembersRequestPacket(ChatChannel channel,
                                             String conversationKey,
                                             String partnerAccount,
                                             String partnerIdentity,
                                             UUID partnerCharacterId,
                                             UUID heldCharacterId,
                                             long heldFingerprint) {
        this.channelId = channel == null ? "" : channel.getId();
        this.conversationKey = conversationKey == null ? "" : conversationKey;
        this.partnerAccount = partnerAccount == null ? "" : partnerAccount.trim();
        this.partnerIdentity = partnerIdentity == null ? "" : partnerIdentity.trim();
        this.partnerCharacterId = partnerCharacterId;
        this.heldCharacterId = heldCharacterId;
        this.heldFingerprint = heldFingerprint;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat members request size");
            }
            this.channelId = LostTalesPacketCodec.readUtf8String(buffer,
                    MAX_CHANNEL_ID_BYTES);
            this.conversationKey = LostTalesPacketCodec.readUtf8String(buffer,
                    LostTalesChatMembersPacket.MAX_CONVERSATION_KEY_BYTES);
            this.partnerAccount = LostTalesPacketCodec.readUtf8String(buffer,
                    MAX_ACCOUNT_BYTES);
            this.partnerIdentity = LostTalesPacketCodec.readUtf8String(buffer,
                    MAX_IDENTITY_BYTES);
            this.partnerCharacterId = readOptionalUuid(buffer);
            this.heldCharacterId = readOptionalUuid(buffer);
            this.heldFingerprint = buffer.readLong();
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.channelId = "";
            this.conversationKey = "";
            this.partnerAccount = "";
            this.partnerIdentity = "";
            this.partnerCharacterId = null;
            this.heldCharacterId = null;
            this.heldFingerprint = 0L;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        LostTalesPacketCodec.writeUtf8String(buffer, this.channelId,
                MAX_CHANNEL_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, this.conversationKey,
                LostTalesChatMembersPacket.MAX_CONVERSATION_KEY_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, this.partnerAccount,
                MAX_ACCOUNT_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, this.partnerIdentity,
                MAX_IDENTITY_BYTES);
        writeOptionalUuid(buffer, this.partnerCharacterId);
        writeOptionalUuid(buffer, this.heldCharacterId);
        buffer.writeLong(this.heldFingerprint);
    }

    private static UUID readOptionalUuid(ByteBuf buffer) {
        return buffer.readBoolean()
                ? new UUID(buffer.readLong(), buffer.readLong()) : null;
    }

    private static void writeOptionalUuid(ByteBuf buffer, UUID id) {
        buffer.writeBoolean(id != null);
        if (id != null) {
            buffer.writeLong(id.getMostSignificantBits());
            buffer.writeLong(id.getLeastSignificantBits());
        }
    }

    /**
     * A request names a channel this build has, within its bounds; a
     * party is named only for a whisper.
     */
    private void validate() {
        if (ChatChannel.fromId(this.channelId) == null
                || !LostTalesPacketCodec.isUtf8WithinLimit(this.conversationKey,
                        LostTalesChatMembersPacket.MAX_CONVERSATION_KEY_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(this.partnerAccount,
                        MAX_ACCOUNT_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(this.partnerIdentity,
                        MAX_IDENTITY_BYTES)
                || (getChannel() != ChatChannel.WHISPER
                        && (this.partnerAccount.length() > 0
                                || this.partnerIdentity.length() > 0
                                || this.partnerCharacterId != null
                                || this.heldCharacterId != null))) {
            throw new IllegalArgumentException("invalid chat members request");
        }
    }

    public ChatChannel getChannel() { return ChatChannel.fromId(this.channelId); }

    /** The client's own name for the conversation. */
    public String getConversationKey() { return this.conversationKey; }

    /** The whisper's other party's account; empty for none. */
    public String getPartnerAccount() { return this.partnerAccount; }

    /** The identity the whisper is with: a character's name, or the account's. */
    public String getPartnerIdentity() { return this.partnerIdentity; }

    /** That identity's character, where the client knows it; null otherwise. */
    public UUID getPartnerCharacterId() { return this.partnerCharacterId; }

    /** The player's own character the whisper is held as; null for the account. */
    public UUID getHeldCharacterId() { return this.heldCharacterId; }

    /** The fingerprint of the answer the client holds; zero for none. */
    public long getHeldFingerprint() { return this.heldFingerprint; }

    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatMembersRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatMembersRequestPacket message,
                                  MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(player,
                    LostTalesRequestRateLimiter.RequestType.CHAT_MEMBERS,
                    message.isMalformed(), "LostTalesChatMembersRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP serverPlayer) {
                            LostTalesChatMembersPacket full =
                                    ChatMemberDirectory.listFor(serverPlayer,
                                            message);
                            LostTalesNetworkHandler.CHANNEL.sendTo(
                                    full.getFingerprint()
                                            == message.getHeldFingerprint()
                                            ? LostTalesChatMembersPacket.unchanged(
                                                    message.getChannel(),
                                                    message.getConversationKey(),
                                                    full.getFingerprint())
                                            : full,
                                    serverPlayer);
                            // Watched from here, so a change reaches the
                            // list before its next ask.
                            ChatMemberWatches.watch(serverPlayer.getUniqueID(),
                                    message, full.getFingerprint(),
                                    System.currentTimeMillis());
                        }
                    });
            return null;
        }
    }
}
