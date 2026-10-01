package com.ninuna.losttales.network.packet.fellowship;

import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import com.ninuna.losttales.fellowship.model.Fellowship;
import java.nio.charset.Charset;

/**
 * The bounds the fellowship packet family holds its fields to. The reading
 * and writing is every family's, and lives in
 * {@link LostTalesPacketCodec}.
 */
final class FellowshipPacketCodec {

    static final int MAX_NAME_BYTES = 256;
    /** A fellowship's name: its most characters, at four bytes each at most. */
    static final int MAX_FELLOWSHIP_NAME_BYTES = Fellowship.MAX_NAME_LENGTH * 4;
    static final int MAX_ERROR_ID_BYTES = 64;
    static final Charset UTF_8 = Charset.forName("UTF-8");

    private FellowshipPacketCodec() {}

    /** This family's name for a payload that could not be read. */
    static final class DecodeException
            extends LostTalesPacketCodec.DecodeException {
        private static final long serialVersionUID = 1L;

        DecodeException(String message) {
            super(message);
        }
    }
}
