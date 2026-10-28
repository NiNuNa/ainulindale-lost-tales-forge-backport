package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatUnseenVoice;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.UUID;

/**
 * Server-to-client: someone is, or has stopped, typing into a channel
 * the receiving player reads. Carries the name the message would show
 * (the character's in role-play channels, the account's otherwise, the
 * Narrator's while they narrate, which the receiving game names in its
 * own words) and, for a whisper, the account name of the partner whose
 * tab it belongs in. It names the typist by their account and the
 * character they type as, so an ignore and the dots over a head go by the
 * same ids a line does (CHAT-61); the Narrator and an unseen voice name
 * nobody (CHAT-11, CHAT-84). Presence is short-lived on the client: a
 * state that is not refreshed expires on its own, so a lost stop never
 * leaves a ghost.
 */
public final class LostTalesChatTypingSyncPacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 560;
    private static final int MAX_CHANNEL_BYTES = 16;
    private static final int MAX_NAME_BYTES = 128;

    private String channelId = "";
    /** The whisper tab this belongs in; empty for every other channel. */
    private String partner = "";
    private String identityName = "";
    private String scopeValue = "";
    private String recipientIdentity = "";
    private boolean typing;
    /** Whether the typist speaks as the Narrator. */
    private boolean narrator;
    /** The typist's account; null for nobody named, the Narrator and an unseen voice. */
    private UUID accountId;
    /** The character the typist types as; null for the account and for nobody named. */
    private UUID characterId;
    /** Whether the typist is an unseen voice, which names nobody (CHAT-84). */
    private boolean unseen;
    private boolean malformed;

    public LostTalesChatTypingSyncPacket() {}

    public LostTalesChatTypingSyncPacket(ChatChannel channel, String partner,
                                         String identityName, boolean narrator,
                                         boolean typing, String scopeValue,
                                         String recipientIdentity) {
        this.scopeValue = scopeValue == null ? "" : scopeValue;
        this.recipientIdentity = recipientIdentity == null ? "" : recipientIdentity;
        this.channelId = channel == null ? "" : channel.getId();
        this.partner = partner == null ? "" : partner.trim();
        this.identityName = identityName == null ? "" : identityName.trim();
        this.narrator = narrator;
        this.typing = typing;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat typing sync packet size");
            }
            this.channelId = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_CHANNEL_BYTES);
            this.partner = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_NAME_BYTES).trim();
            this.identityName = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_NAME_BYTES).trim();
            this.narrator = buffer.readBoolean();
            this.typing = buffer.readBoolean();
            this.scopeValue = LostTalesPacketCodec.readUtf8String(buffer, 128);
            this.recipientIdentity = LostTalesPacketCodec.readUtf8String(buffer, 36);
            this.accountId = LostTalesPacketCodec.readNullableUuid(buffer);
            this.characterId = LostTalesPacketCodec.readNullableUuid(buffer);
            this.unseen = buffer.readBoolean();
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.accountId = null;
            this.characterId = null;
            this.unseen = false;
            this.channelId = "";
            this.partner = "";
            this.identityName = "";
            this.narrator = false;
            this.typing = false;
            this.scopeValue = "";
            this.recipientIdentity = "";
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        LostTalesPacketCodec.writeUtf8String(buffer, this.channelId,
                MAX_CHANNEL_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, this.partner,
                MAX_NAME_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, this.identityName,
                MAX_NAME_BYTES);
        buffer.writeBoolean(this.narrator);
        buffer.writeBoolean(this.typing);
        LostTalesPacketCodec.writeUtf8String(buffer, this.scopeValue, 128);
        LostTalesPacketCodec.writeUtf8String(buffer, this.recipientIdentity, 36);
        LostTalesPacketCodec.writeNullableUuid(buffer, this.accountId);
        LostTalesPacketCodec.writeNullableUuid(buffer, this.characterId);
        buffer.writeBoolean(this.unseen);
    }

    private void validate() {
        ChatChannel channel = ChatChannel.fromId(this.channelId);
        if (channel == null || this.identityName.length() == 0
                || !LostTalesPacketCodec.isUtf8WithinLimit(this.scopeValue, 128)
                || !LostTalesPacketCodec.isUtf8WithinLimit(this.recipientIdentity, 36)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.channelId, MAX_CHANNEL_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.partner, MAX_NAME_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.identityName, MAX_NAME_BYTES)
                || (channel == ChatChannel.WHISPER
                        && this.partner.length() == 0)
                || (channel != ChatChannel.WHISPER
                        && this.partner.length() > 0)
                // A character is someone's: it comes with its account.
                || (this.characterId != null && this.accountId == null)
                // The Narrator and an unseen voice name nobody.
                || ((this.narrator || this.unseen)
                        && (this.accountId != null || this.characterId != null))
                || (this.unseen && (this.narrator || channel == ChatChannel.WHISPER
                        || !ChatUnseenVoice.NAME.equals(this.identityName)))) {
            throw new IllegalArgumentException("invalid chat typing sync");
        }
    }

    /**
     * The same notice naming its typist by {@code accountId} and the
     * character {@code characterId} they type as, null for the account.
     */
    public LostTalesChatTypingSyncPacket from(UUID accountId, UUID characterId) {
        this.accountId = accountId;
        this.characterId = accountId == null ? null : characterId;
        validate();
        return this;
    }

    /**
     * The same notice for a typist who is an unseen voice: signed by the
     * unseen voice and naming nobody (CHAT-84).
     */
    public static LostTalesChatTypingSyncPacket unseenVoice(ChatChannel channel,
                                                            boolean typing,
                                                            String scopeValue) {
        LostTalesChatTypingSyncPacket packet = new LostTalesChatTypingSyncPacket(
                channel, "", ChatUnseenVoice.NAME, false, typing, scopeValue, "");
        packet.unseen = true;
        packet.validate();
        return packet;
    }

    public ChatChannel getChannel() {
        return ChatChannel.fromId(this.channelId);
    }
    /** The whisper partner's account name; empty otherwise. */
    public String getPartner() { return this.partner; }
    /** The name the typist's messages show in this channel. */
    public String getIdentityName() { return this.identityName; }
    /** Whether the typist speaks as the Narrator, whose name each game says in its own words. */
    public boolean isNarrator() { return this.narrator; }
    public String getScopeValue() { return this.scopeValue; }
    public String getRecipientIdentity() { return this.recipientIdentity; }
    /** The typist's account; null where the notice names nobody. */
    public UUID getAccountId() { return this.accountId; }
    /** The character the typist types as; null for the account and for nobody. */
    public UUID getCharacterId() { return this.characterId; }
    /** Whether the typist is an unseen voice, named in each game's own words. */
    public boolean isUnseen() { return this.unseen; }
    public boolean isTyping() { return this.typing; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatTypingSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatTypingSyncPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleChatTyping(message);
                }
            });
            return null;
        }
    }
}
