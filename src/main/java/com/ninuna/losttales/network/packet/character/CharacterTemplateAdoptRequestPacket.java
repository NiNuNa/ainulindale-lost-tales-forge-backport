package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.server.CharacterNetworkRequestHandler;
import com.ninuna.losttales.character.server.CharacterServerPacketDispatcher;
import com.ninuna.losttales.character.server.CharacterTemplateAdoption;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * A client offering the account's template for this world's default
 * character, the one time the world asks for it.
 *
 * <p>The server decides everything. It re-finds the roster and the
 * default character from its own store, checks that this world has not
 * already had its reading, and puts every field through the same
 * validation a character somebody is making goes through. A request that
 * names a race, sex or skin this server does not offer is refused, not
 * trimmed.</p>
 *
 * <p>The payload is sent even when the account has no template, with
 * {@code offered} false: the reading is what is being spent, and spending
 * it is what makes a world's default character that world's from then
 * on.</p>
 */
public final class CharacterTemplateAdoptRequestPacket implements IMessage {

    private int requestId;
    private long expectedRosterRevision;
    private boolean offered;
    private String name = "";
    private String raceId = "";
    private String genderId = "";
    private String skinId = "";
    private String bodyTypeId = "";
    private String chestTypeId = "";
    private String description = "";
    private int age;
    private boolean showMinecraftCape = true;
    private int cosmeticCapeId;
    private boolean malformed;

    public CharacterTemplateAdoptRequestPacket() {}

    public CharacterTemplateAdoptRequestPacket(
            int requestId, CharacterTemplateAdoption adoption) {
        if (adoption == null) {
            throw new IllegalArgumentException("adoption must not be null");
        }
        this.requestId = requestId;
        this.expectedRosterRevision = adoption.getExpectedRosterRevision();
        this.offered = adoption.isOffered();
        this.name = adoption.getName();
        this.raceId = adoption.getRaceId();
        this.genderId = adoption.getGenderId();
        this.skinId = adoption.getSkinId();
        this.bodyTypeId = adoption.getBodyTypeId();
        this.chestTypeId = adoption.getChestTypeId();
        this.description = adoption.getDescription();
        this.age = adoption.getAge();
        this.showMinecraftCape = adoption.isMinecraftCapeVisible();
        this.cosmeticCapeId = adoption.getCosmeticCapeId();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            this.expectedRosterRevision = buffer.readLong();
            this.offered = buffer.readBoolean();
            this.name = CharacterPacketCodec.readString(
                    buffer, CharacterPacketCodec.MAX_NAME_BYTES);
            this.raceId = CharacterPacketCodec.readString(
                    buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            this.genderId = CharacterPacketCodec.readString(
                    buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            this.skinId = CharacterPacketCodec.readString(
                    buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            this.bodyTypeId = CharacterPacketCodec.readString(
                    buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            this.chestTypeId = CharacterPacketCodec.readString(
                    buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            this.description = CharacterPacketCodec.readString(
                    buffer, CharacterPacketCodec.MAX_DESCRIPTION_BYTES);
            this.age = buffer.readInt();
            this.showMinecraftCape = buffer.readBoolean();
            this.cosmeticCapeId = buffer.readInt();
            CharacterPacketCodec.requireFinished(buffer);
            if (this.expectedRosterRevision < 0L) {
                throw new CharacterPacketCodec.DecodeException(
                        "missing roster revision");
            }
            if (this.cosmeticCapeId < CharacterCapeCatalog.NONE_ID
                    || this.cosmeticCapeId > CharacterCapeCatalog.MAX_NETWORK_ID) {
                throw new CharacterPacketCodec.DecodeException(
                        "cape id out of range");
            }
        } catch (RuntimeException exception) {
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.requestId);
        buffer.writeLong(this.expectedRosterRevision);
        buffer.writeBoolean(this.offered);
        CharacterPacketCodec.writeString(
                buffer, this.name, CharacterPacketCodec.MAX_NAME_BYTES);
        CharacterPacketCodec.writeString(
                buffer, this.raceId, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        CharacterPacketCodec.writeString(
                buffer, this.genderId, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        CharacterPacketCodec.writeString(
                buffer, this.skinId, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        CharacterPacketCodec.writeString(
                buffer, this.bodyTypeId, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        CharacterPacketCodec.writeString(
                buffer, this.chestTypeId, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        CharacterPacketCodec.writeString(
                buffer, this.description, CharacterPacketCodec.MAX_DESCRIPTION_BYTES);
        buffer.writeInt(this.age);
        // The cape the template chose comes last; the wire layout only grows.
        buffer.writeBoolean(this.showMinecraftCape);
        buffer.writeInt(this.cosmeticCapeId);
    }

    /** Whether the payload could not be read and must be discarded. */
    public boolean isMalformed() {
        return this.malformed;
    }

    /** What the payload asks for, as the server will re-check it. */
    public CharacterTemplateAdoption toAdoption() {
        return new CharacterTemplateAdoption(this.expectedRosterRevision,
                this.offered, this.name, this.raceId, this.genderId,
                this.skinId, this.bodyTypeId, this.chestTypeId,
                this.description, this.age, this.showMinecraftCape,
                this.cosmeticCapeId);
    }

    public static final class Handler implements
            IMessageHandler<CharacterTemplateAdoptRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(
                final CharacterTemplateAdoptRequestPacket message,
                MessageContext context) {
            final EntityPlayerMP player =
                    CharacterServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            final int requestId = message.requestId;
            final CharacterTemplateAdoption adoption = message.toAdoption();
            CharacterServerPacketDispatcher.submit(
                    player,
                    requestId,
                    CharacterOperationType.CREATE,
                    message.malformed,
                    "CharacterTemplateAdoptRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            CharacterNetworkRequestHandler
                                    .handleTemplateAdoptRequest(
                                            livePlayer, requestId, adoption);
                        }
                    }
            );
            return null;
        }
    }
}
