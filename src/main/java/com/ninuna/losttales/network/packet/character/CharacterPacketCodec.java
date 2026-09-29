package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The bounds the character packet family holds its fields to, and the
 * profile its packets carry within them.
 *
 * <p>The limits are this family's own; the reading and writing of every
 * field is every family's, and lives in {@link LostTalesPacketCodec}, so a
 * decode primitive is hardened in one place.</p>
 */
final class CharacterPacketCodec {

    static final int MAX_NAME_BYTES = 128;
    /** An About text, the longest a character's words take in UTF-8. */
    static final int MAX_SECTION_BYTES = CharacterProfile.MAX_SECTION_LENGTH * 4;
    static final int MAX_FACT_BYTES = CharacterProfile.MAX_FACT_LENGTH * 4;
    static final int MAX_GLANCE_EMOJI_BYTES = 64;
    static final int MAX_GLANCE_TITLE_BYTES =
            CharacterProfile.MAX_GLANCE_TITLE_LENGTH * 4;
    static final int MAX_GLANCE_LINE_BYTES =
            CharacterProfile.MAX_GLANCE_LINE_LENGTH * 4;
    static final int MAX_IDENTIFIER_BYTES = 128;
    static final int MAX_ERROR_ID_BYTES = 64;
    /** A full roster: the nine slots and the account character besides them. */
    static final int MAX_CHARACTERS = CharacterRoster.MAX_SLOTS + 1;

    private CharacterPacketCodec() {}

    /**
     * A profile on the wire: the About texts in order, the facts in
     * order, a count of glances and each one's emoji, title and line. The
     * wire only bounds it; whoever takes it holds the words to the
     * validator.
     */
    static void writeProfile(ByteBuf buffer, CharacterProfile profile) {
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            LostTalesPacketCodec.writeUtf8String(buffer, profile.section(section), MAX_SECTION_BYTES);
        }
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            LostTalesPacketCodec.writeUtf8String(buffer, profile.fact(fact), MAX_FACT_BYTES);
        }
        buffer.writeByte(profile.glances().size());
        for (CharacterProfile.Glance glance : profile.glances()) {
            LostTalesPacketCodec.writeUtf8String(buffer, glance.getEmoji(), MAX_GLANCE_EMOJI_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, glance.getTitle(), MAX_GLANCE_TITLE_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, glance.getLine(), MAX_GLANCE_LINE_BYTES);
        }
    }

    static CharacterProfile readProfile(ByteBuf buffer) {
        CharacterProfile profile = CharacterProfile.EMPTY;
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            profile = profile.withSection(section,
                    LostTalesPacketCodec.readUtf8String(buffer, MAX_SECTION_BYTES));
        }
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            profile = profile.withFact(fact, LostTalesPacketCodec.readUtf8String(buffer, MAX_FACT_BYTES));
        }
        int count = buffer.readUnsignedByte();
        if (count > CharacterProfile.MAX_GLANCES) {
            throw new DecodeException("too many glances");
        }
        List<CharacterProfile.Glance> glances =
                new ArrayList<CharacterProfile.Glance>(count);
        for (int index = 0; index < count; index++) {
            glances.add(new CharacterProfile.Glance(
                    LostTalesPacketCodec.readUtf8String(buffer, MAX_GLANCE_EMOJI_BYTES),
                    LostTalesPacketCodec.readUtf8String(buffer, MAX_GLANCE_TITLE_BYTES),
                    LostTalesPacketCodec.readUtf8String(buffer, MAX_GLANCE_LINE_BYTES)));
        }
        return profile.withGlances(glances);
    }

    /** Whether a profile goes on the wire whole: every text within its bytes, five glances at most. */
    static boolean fits(CharacterProfile profile) {
        if (profile == null
                || profile.glances().size() > CharacterProfile.MAX_GLANCES) {
            return false;
        }
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            if (bytes(profile.section(section)) > MAX_SECTION_BYTES) {
                return false;
            }
        }
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            if (bytes(profile.fact(fact)) > MAX_FACT_BYTES) {
                return false;
            }
        }
        for (CharacterProfile.Glance glance : profile.glances()) {
            if (bytes(glance.getEmoji()) > MAX_GLANCE_EMOJI_BYTES
                    || bytes(glance.getTitle()) > MAX_GLANCE_TITLE_BYTES
                    || bytes(glance.getLine()) > MAX_GLANCE_LINE_BYTES) {
                return false;
            }
        }
        return true;
    }

    private static int bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    /** This family's name for a payload that could not be read. */
    static final class DecodeException
            extends LostTalesPacketCodec.DecodeException {
        private static final long serialVersionUID = 1L;

        DecodeException(String message) {
            super(message);
        }
    }
}
