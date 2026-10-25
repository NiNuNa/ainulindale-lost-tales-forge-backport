package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerEditableSettings;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNamedAfter;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneStateReason;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * A waystone's settings as the server holds them, for the waystone's page:
 * sent as the player uses the waystone, which opens the page on it, and as
 * the answer to every request the page sends, saying why ({@link #getReason}).
 * A name, category or description left empty travels empty, with what the
 * waystone is called after and whether a player placed it, so the page
 * shows the words it stands for in its reader's language. Only a player
 * who may see the waystone is ever sent it (WAY-10).
 */
public final class LostTalesWaystoneStatePacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 16384;
    private static final int MAX_MARKER_ID_BYTES = 1024;
    /** Past the longest name a character or a lore figure can have. */
    public static final int MAX_OWNER_NAME_BYTES = 256;
    /** What a waystone is called after, at three bytes for each UTF-16 unit. */
    private static final int MAX_NAMED_AFTER_BYTES =
            3 * LostTalesMapMarkerNamedAfter.MAX_LENGTH;

    private int dimensionId;
    private int x;
    private int y;
    private int z;
    private String markerId = "";
    private String namedAfter = "";
    private boolean playerPlaced;
    private long revision;
    private LostTalesMapMarkerEditableSettings settings;
    private int sharedCharacterCount;
    private int sharedFellowshipCount;
    /** The owning character's name; empty for a waystone nobody owns or one whose owner is gone. */
    private String ownerName = "";
    private boolean canEdit;
    private boolean managesMarkers;
    private LostTalesWaystoneStateReason reason;
    private boolean malformed;

    public LostTalesWaystoneStatePacket() {}

    /**
     * The record's state, called after {@code namedAfter} as the server
     * words the record's reference for every reader: its placing character
     * by its name as it stands (WAY-01).
     */
    public LostTalesWaystoneStatePacket(
            int dimensionId, int x, int y, int z,
            LostTalesMapMarkerRecord record, String namedAfter,
            String ownerName, boolean canEdit, boolean managesMarkers,
            LostTalesWaystoneStateReason reason) {
        if (record == null) {
            throw new IllegalArgumentException(
                    "waystone state requires a marker record");
        }
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.markerId = record.getId();
        this.namedAfter = namedAfter == null ? "" : namedAfter;
        this.playerPlaced = record.getSource()
                == LostTalesMapMarkerSource.PLAYER_CREATED;
        this.revision = record.getRevision();
        this.settings =
                LostTalesMapMarkerEditableSettings.fromRecord(record);
        this.sharedCharacterCount = record.getSharedCharacterIds().size();
        this.sharedFellowshipCount =
                record.getSharedFellowshipIds().size();
        this.ownerName = ownerName == null ? "" : ownerName;
        this.canEdit = canEdit;
        this.managesMarkers = managesMarkers;
        this.reason = reason;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null
                    || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid waystone state packet size");
            }
            this.dimensionId = buffer.readInt();
            this.x = buffer.readInt();
            this.y = buffer.readInt();
            this.z = buffer.readInt();
            this.markerId = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_MARKER_ID_BYTES);
            this.namedAfter = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_NAMED_AFTER_BYTES);
            this.playerPlaced = buffer.readBoolean();
            this.revision = buffer.readLong();
            this.settings =
                    LostTalesWaystoneSettingsRequestPacket.readSettings(buffer);
            this.sharedCharacterCount = buffer.readInt();
            this.sharedFellowshipCount = buffer.readInt();
            this.ownerName = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_OWNER_NAME_BYTES);
            this.canEdit = buffer.readBoolean();
            this.managesMarkers = buffer.readBoolean();
            this.reason = LostTalesWaystoneStateReason.fromNetworkId(
                    buffer.readUnsignedByte());
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        buffer.writeInt(this.dimensionId);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.markerId, MAX_MARKER_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.namedAfter, MAX_NAMED_AFTER_BYTES);
        buffer.writeBoolean(this.playerPlaced);
        buffer.writeLong(this.revision);
        LostTalesWaystoneSettingsRequestPacket.writeSettings(
                buffer, this.settings);
        buffer.writeInt(this.sharedCharacterCount);
        buffer.writeInt(this.sharedFellowshipCount);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.ownerName, MAX_OWNER_NAME_BYTES);
        buffer.writeBoolean(this.canEdit);
        buffer.writeBoolean(this.managesMarkers);
        buffer.writeByte(this.reason.getNetworkId());
    }

    private void validate() {
        if (!LostTalesPacketCodec.isValidBlockPosition(
                this.x, this.y, this.z)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.markerId, MAX_MARKER_ID_BYTES)
                || this.markerId.length() == 0
                || !LostTalesMapMarkerNamedAfter.isValid(this.namedAfter)
                || this.reason == null
                || this.revision < 1L
                || !LostTalesWaystoneSettingsRequestPacket.isValidSettings(
                        this.settings)
                || this.settings.getName().trim().length() == 0
                        && this.namedAfter.length() == 0
                || this.sharedCharacterCount < 0
                || this.sharedCharacterCount
                        > LostTalesMapMarkerRecord.MAX_SHARED_CHARACTERS
                || this.sharedFellowshipCount < 0
                || this.sharedFellowshipCount
                        > LostTalesMapMarkerRecord
                                .MAX_SHARED_FELLOWSHIPS
                || this.ownerName == null
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.ownerName, MAX_OWNER_NAME_BYTES)) {
            throw new IllegalArgumentException(
                    "invalid waystone state");
        }
    }

    public int getDimensionId() { return this.dimensionId; }
    public int getX() { return this.x; }
    public int getY() { return this.y; }
    public int getZ() { return this.z; }
    public String getMarkerId() { return this.markerId; }
    public long getRevision() { return this.revision; }
    public LostTalesMapMarkerEditableSettings getSettings() {
        return this.settings;
    }
    public String getName() { return this.settings.getName(); }
    /** What the waystone is called after while its name is empty. */
    public String getNamedAfter() { return this.namedAfter; }
    /** Whether a player placed it: its empty category and description read as a player's waystone's. */
    public boolean isPlayerPlaced() { return this.playerPlaced; }
    public boolean hasFastTravel() {
        return this.settings.hasFastTravel();
    }
    /** The characters it is shared with. */
    public int getSharedCharacterCount() {
        return this.sharedCharacterCount;
    }
    public int getSharedFellowshipCount() {
        return this.sharedFellowshipCount;
    }
    /** The owning character's name; empty for none the server could name. */
    public String getOwnerName() { return this.ownerName; }
    public boolean canEdit() { return this.canEdit; }
    /**
     * Whether the reader holds {@code mapmarker.manage}: the page offers
     * Public and Hand Over (WAY-13, WAY-08).
     */
    public boolean managesMarkers() {
        return this.managesMarkers;
    }
    /** Why it was sent: an opening, or the answer to a request. */
    public LostTalesWaystoneStateReason getReason() {
        return this.reason;
    }
    /** Whether the player used the waystone, and the page opens on it. */
    public boolean isOpening() {
        return this.reason == LostTalesWaystoneStateReason.OPENED;
    }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesWaystoneStatePacket, IMessage> {
        @Override
        public IMessage onMessage(
                final LostTalesWaystoneStatePacket message,
                MessageContext context) {
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleWaystoneState(message);
                }
            });
            return null;
        }
    }
}
