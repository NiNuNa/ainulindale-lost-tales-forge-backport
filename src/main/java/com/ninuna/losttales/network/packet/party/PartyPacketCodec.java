package com.ninuna.losttales.network.packet.party;

import com.ninuna.losttales.network.packet.LostTalesPacketCodec;

/**
 * The bounds the party packet family holds its fields to. The reading
 * and writing is every family's, and lives in
 * {@link LostTalesPacketCodec}.
 */
final class PartyPacketCodec {

    static final int MAX_NAME_BYTES = 256;
    static final int MAX_ERROR_ID_BYTES = 64;

    private PartyPacketCodec() {}

    /** This family's name for a payload that could not be read. */
    static final class DecodeException
            extends LostTalesPacketCodec.DecodeException {
        private static final long serialVersionUID = 1L;

        DecodeException(String message) {
            super(message);
        }
    }
}
