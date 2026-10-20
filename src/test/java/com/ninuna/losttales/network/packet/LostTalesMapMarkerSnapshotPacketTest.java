package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** A snapshot of markers in parts (WAY-12). */
public final class LostTalesMapMarkerSnapshotPacketTest {
    @Test
    public void priorityRoundTripsFromServerSnapshot() {
        LostTalesMapMarkerDefinition marker = marker("losttales:test");
        LostTalesMapMarkerSnapshotPacket decoded = roundTrip(
                new LostTalesMapMarkerSnapshotPacket(7, 0, 1,
                        Collections.singleton(marker)));

        assertFalse(decoded.isMalformed());
        assertEquals(99, decoded.getMarkers().get(0).getPriority());
    }

    @Test
    public void aPartCarriesItsSequenceIndexAndCount() {
        LostTalesMapMarkerSnapshotPacket decoded = roundTrip(
                new LostTalesMapMarkerSnapshotPacket(-12, 2, 5,
                        Collections.singleton(marker("losttales:a"))));

        assertFalse(decoded.isMalformed());
        assertEquals(-12, decoded.getSequence());
        assertEquals(2, decoded.getIndex());
        assertEquals(5, decoded.getTotal());
    }

    /** A part closes at 2,000 markers; every part names the one snapshot and how many parts it has. */
    @Test
    public void aSnapshotGoesInPartsOf2000() {
        List<LostTalesMapMarkerDefinition> markers = markers(4500);

        List<LostTalesMapMarkerSnapshotPacket> parts =
                LostTalesMapMarkerSnapshotPacket.split(41, markers);

        assertEquals(3, parts.size());
        int[] sizes = {2000, 2000, 500};
        List<LostTalesMapMarkerDefinition> whole =
                new ArrayList<LostTalesMapMarkerDefinition>();
        for (int at = 0; at < parts.size(); at++) {
            LostTalesMapMarkerSnapshotPacket decoded = roundTrip(parts.get(at));
            assertFalse(decoded.isMalformed());
            assertEquals(41, decoded.getSequence());
            assertEquals(at, decoded.getIndex());
            assertEquals(3, decoded.getTotal());
            assertEquals(sizes[at], decoded.getMarkers().size());
            whole.addAll(decoded.getMarkers());
        }
        assertEquals(4500, whole.size());
        for (int at = 0; at < whole.size(); at++) {
            assertEquals(markers.get(at).getId(), whole.get(at).getId());
        }
    }

    /** Fifty thousand markers go in 25 parts, none past its bytes. */
    @Test
    public void fiftyThousandMarkersGoInTwentyFiveParts() {
        List<LostTalesMapMarkerSnapshotPacket> parts =
                LostTalesMapMarkerSnapshotPacket.split(1, markers(50000));

        assertEquals(25, parts.size());
        for (LostTalesMapMarkerSnapshotPacket part : parts) {
            assertTrue(written(part) <= LostTalesMapMarkerSnapshotPacket.MAX_PART_BYTES);
        }
    }

    /**
     * The widest marker the store holds, measured: a part of them closes
     * before its bytes run out, well under 2,000, and still reads.
     */
    @Test
    public void widestMarkersCloseAPartByItsBytes() {
        LostTalesMapMarkerDefinition widest = widest("a");
        int one = written(new LostTalesMapMarkerSnapshotPacket(1, 0, 1,
                Collections.singleton(widest)))
                - LostTalesMapMarkerSnapshotPacket.HEADER_BYTES;
        assertEquals("measured as written", one,
                LostTalesMapMarkerSnapshotPacket.measure(widest));
        assertTrue("about 7 KiB: " + one, one > 6 * 1024 && one < 8 * 1024);

        List<LostTalesMapMarkerDefinition> markers =
                new ArrayList<LostTalesMapMarkerDefinition>();
        for (int at = 0; at < 400; at++) {
            markers.add(widest(Integer.toString(at)));
        }
        List<LostTalesMapMarkerSnapshotPacket> parts =
                LostTalesMapMarkerSnapshotPacket.split(3, markers);

        assertTrue(parts.size() >= 3);
        int count = 0;
        for (LostTalesMapMarkerSnapshotPacket part : parts) {
            int bytes = written(part);
            assertTrue(bytes <= LostTalesMapMarkerSnapshotPacket.MAX_PART_BYTES);
            assertEquals(part.measuredBytes(), bytes);
            LostTalesMapMarkerSnapshotPacket decoded = roundTrip(part);
            assertFalse(decoded.isMalformed());
            count += decoded.getMarkers().size();
        }
        assertEquals(400, count);
    }

    /** Letters past the first plane count four bytes, as written. */
    @Test
    public void lettersPastTheFirstPlaneAreMeasuredAsWritten() {
        LostTalesMapMarkerDefinition marker = new LostTalesMapMarkerDefinition(
                "losttales:plane", "🐉 Dragoné", "fort", "white",
                "", "🗡", true, 100, 1.0D, 64.0D, 1.0D,
                128.0D, 8.0D, false, true, true,
                LostTalesMapMarkerSource.CUSTOM_PRESET, false, "", 0);
        assertEquals(written(new LostTalesMapMarkerSnapshotPacket(1, 0, 1,
                        Collections.singleton(marker)))
                        - LostTalesMapMarkerSnapshotPacket.HEADER_BYTES,
                LostTalesMapMarkerSnapshotPacket.measure(marker));
    }

    /** A waystone called after a lore figure of 64 three-byte letters still travels. */
    @Test
    public void aLongNameInManyByteLettersIsCalledAfter() {
        StringBuilder name = new StringBuilder();
        for (int at = 0; at < 64; at++) {
            name.append('მ');
        }
        String namedAfter = "player:" + name;
        LostTalesMapMarkerDefinition marker = new LostTalesMapMarkerDefinition(
                "losttales:player/lore", "", "shack", "white", "", "", true,
                100, 1.0D, 64.0D, 1.0D, 128.0D, 8.0D, true, true, false,
                LostTalesMapMarkerSource.PLAYER_CREATED, true,
                "losttales:player_placed", 0, namedAfter);
        assertEquals(namedAfter, marker.getNamedAfter());

        LostTalesMapMarkerSnapshotPacket decoded = roundTrip(
                new LostTalesMapMarkerSnapshotPacket(1, 0, 1,
                        Collections.singleton(marker)));

        assertFalse(decoded.isMalformed());
        assertEquals(namedAfter, decoded.getMarkers().get(0).getNamedAfter());
    }

    @Test
    public void noMarkersIsOneEmptyPart() {
        List<LostTalesMapMarkerSnapshotPacket> parts =
                LostTalesMapMarkerSnapshotPacket.split(9,
                        Collections.<LostTalesMapMarkerDefinition>emptyList());

        assertEquals(1, parts.size());
        LostTalesMapMarkerSnapshotPacket decoded = roundTrip(parts.get(0));
        assertFalse(decoded.isMalformed());
        assertEquals(0, decoded.getIndex());
        assertEquals(1, decoded.getTotal());
        assertTrue(decoded.getMarkers().isEmpty());
    }

    @Test
    public void aPartThatDoesNotFitItsSnapshotIsNeverMade() {
        int[][] shapes = {{1, 1}, {-1, 2}, {0, 0},
                {0, LostTalesMapMarkerSnapshotPacket.MAX_PARTS + 1}};
        for (int[] shape : shapes) {
            try {
                new LostTalesMapMarkerSnapshotPacket(1, shape[0], shape[1],
                        Collections.singleton(marker("losttales:a")));
                fail("part " + shape[0] + " of " + shape[1]);
            } catch (IllegalArgumentException expected) {
                // refused
            }
        }
    }

    /** A part that does not fit is malformed and carries nothing. */
    @Test
    public void aPartThatDoesNotFitIsMalformed() {
        assertMalformed(header(1, 1, 1, 0), "index past the count");
        assertMalformed(header(1, -1, 1, 0), "index below 0");
        assertMalformed(header(1, 0, 0, 0), "no parts");
        assertMalformed(header(1, 0,
                LostTalesMapMarkerSnapshotPacket.MAX_PARTS + 1, 0),
                "too many parts");
        assertMalformed(header(1, 0, 1,
                LostTalesMapMarkerSnapshotPacket.MAX_MARKERS_PER_PART + 1),
                "too many markers");
        assertMalformed(header(1, 0, 1, 1), "a marker missing");
        assertMalformed(Unpooled.buffer().writeInt(1), "no header");

        ByteBuf trailing = Unpooled.buffer();
        new LostTalesMapMarkerSnapshotPacket(1, 0, 1,
                Collections.singleton(marker("losttales:a"))).toBytes(trailing);
        trailing.writeByte(0);
        assertMalformed(trailing, "trailing byte");

        ByteBuf oversized = header(1, 0, 1, 0);
        oversized.writeZero(LostTalesMapMarkerSnapshotPacket.MAX_PART_BYTES);
        assertMalformed(oversized, "past the part's bytes");
    }

    private static void assertMalformed(ByteBuf buffer, String why) {
        LostTalesMapMarkerSnapshotPacket decoded =
                new LostTalesMapMarkerSnapshotPacket();
        decoded.fromBytes(buffer);
        assertTrue(why, decoded.isMalformed());
        assertTrue(why, decoded.getMarkers().isEmpty());
        assertEquals(why, 0, buffer.readableBytes());
    }

    private static ByteBuf header(int sequence, int index, int total,
                                  int count) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(sequence);
        buffer.writeInt(index);
        buffer.writeInt(total);
        buffer.writeInt(count);
        return buffer;
    }

    private static LostTalesMapMarkerSnapshotPacket roundTrip(
            LostTalesMapMarkerSnapshotPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesMapMarkerSnapshotPacket decoded =
                new LostTalesMapMarkerSnapshotPacket();
        decoded.fromBytes(buffer);
        return decoded;
    }

    private static int written(LostTalesMapMarkerSnapshotPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        return buffer.readableBytes();
    }

    private static List<LostTalesMapMarkerDefinition> markers(int count) {
        List<LostTalesMapMarkerDefinition> markers =
                new ArrayList<LostTalesMapMarkerDefinition>(count);
        for (int at = 0; at < count; at++) {
            markers.add(marker("losttales:player/" + at));
        }
        return markers;
    }

    /** A marker of the usual size: a short name and a line of words. */
    private static LostTalesMapMarkerDefinition marker(String id) {
        return new LostTalesMapMarkerDefinition(
                id, "Test", "fort", "white",
                "Place", "A place by the road where travellers rest.",
                true, 100,
                12.5D, 64.0D, -4.5D, 128.0D, 8.0D,
                false, true, true,
                LostTalesMapMarkerSource.CUSTOM_PRESET,
                true, "losttales:glowstone_house", 99);
    }

    /** A marker at every bound of the store, in three-byte letters. */
    private static LostTalesMapMarkerDefinition widest(String suffix) {
        String id = "losttales:" + suffix + repeat('მ',
                LostTalesMapMarkerRecord.MAX_ID_LENGTH - 10 - suffix.length());
        String words = repeat('მ', LostTalesMapMarkerRecord.MAX_NAME_LENGTH);
        String text = repeat('მ', LostTalesMapMarkerRecord.MAX_TEXT_LENGTH);
        return new LostTalesMapMarkerDefinition(
                id, words, words, words, words, text, true, 100,
                1.0D, 64.0D, 1.0D, 128.0D, 8.0D, false, true, true,
                LostTalesMapMarkerSource.CUSTOM_PRESET, true,
                "losttales:" + repeat('a',
                        LostTalesMapMarkerRecord.MAX_STRUCTURE_ID_LENGTH - 10),
                0);
    }

    private static String repeat(char letter, int count) {
        StringBuilder text = new StringBuilder(count);
        for (int at = 0; at < count; at++) {
            text.append(letter);
        }
        return text.toString();
    }
}
