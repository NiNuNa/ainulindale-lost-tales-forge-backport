package com.ninuna.losttales.network.packet.party;

import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import com.ninuna.losttales.party.model.Party;
import java.nio.charset.Charset;

/**
 * The bounds the party packet family holds its fields to. The reading
 * and writing is every family's, and lives in
 * {@link LostTalesPacketCodec}.
 */
final class PartyPacketCodec {

    static final int MAX_NAME_BYTES = 256;
    /** A party's name: its most characters, at four bytes each at most. */
    static final int MAX_PARTY_NAME_BYTES = Party.MAX_NAME_LENGTH * 4;
    static final int MAX_ERROR_ID_BYTES = 64;
    static final Charset UTF_8 = Charset.forName("UTF-8");

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
