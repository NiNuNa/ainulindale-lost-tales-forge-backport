package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.config.server.ServerConfigChangeValidator;
import com.ninuna.losttales.config.server.ServerConfigEntry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A setting too long for the Server Settings page (DATA-16): more lines
 * than a change may carry, or a value or default longer than one may
 * hold. It travels marked and without its values, so the page always
 * loads, and the settings beside it arrive whole.
 */
public final class ServerConfigTooLongTest {

    private static List<String> lines(int count) {
        List<String> lines = new ArrayList<String>(count);
        for (int index = 0; index < count; index++) {
            lines.add("word" + index);
        }
        return lines;
    }

    private static String letters(int count) {
        StringBuilder text = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            text.append('a');
        }
        return text.toString();
    }

    private static ServerConfigEntry list(List<String> values, List<String> defaults) {
        return new ServerConfigEntry("chat", "profanityWords", ServerConfigEntry.Type.STRING,
                true, values, defaults, "", "", false, null);
    }

    private static ServerConfigEntry single(String value) {
        return new ServerConfigEntry("chat", "serverAddress", ServerConfigEntry.Type.STRING,
                false, Collections.singletonList(value), Collections.singletonList(""),
                "", "", false, null);
    }

    @Test
    public void aSettingWithinTheBoundsFitsThePage() {
        int most = ServerConfigChangeValidator.MAX_LIST_ITEMS;
        assertFalse(list(lines(most), Collections.<String>emptyList()).isTooLong());
        assertFalse(single(letters(ServerConfigChangeValidator.MAX_VALUE_LENGTH)).isTooLong());
    }

    @Test
    public void moreLinesOrMoreLettersIsTooLong() {
        int most = ServerConfigChangeValidator.MAX_LIST_ITEMS;
        assertTrue(list(lines(most + 1), Collections.<String>emptyList()).isTooLong());
        assertTrue("its shipped list counts too",
                list(Collections.<String>emptyList(), lines(most + 1)).isTooLong());
        assertTrue(single(letters(ServerConfigChangeValidator.MAX_VALUE_LENGTH + 1))
                .isTooLong());
        assertTrue(list(Arrays.asList("fine",
                letters(ServerConfigChangeValidator.MAX_VALUE_LENGTH + 1)),
                Collections.<String>emptyList()).isTooLong());
    }

    /** The snapshot that used to throw as it was written now goes, and the page loads. */
    @Test
    public void theSnapshotGoesWithATooLongSettingMarkedAndEmpty() {
        ServerConfigEntry tooLong = list(lines(300), Collections.<String>emptyList());
        ServerConfigEntry beside = single("play.example.net");
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesServerConfigSyncPacket(Arrays.asList(tooLong, beside)).toBytes(buffer);
        LostTalesServerConfigSyncPacket decoded = new LostTalesServerConfigSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        ServerConfigEntry received = decoded.getEntries().get(0);
        assertTrue(received.isTooLong());
        assertTrue(received.isList());
        assertEquals("profanityWords", received.getKey());
        assertTrue(received.getValues().isEmpty());
        assertTrue(received.getDefaults().isEmpty());
        ServerConfigEntry whole = decoded.getEntries().get(1);
        assertFalse(whole.isTooLong());
        assertEquals("play.example.net", whole.getValue());
    }

    @Test
    public void aValueTooLongGoesMarkedToo() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesServerConfigSyncPacket(Collections.singletonList(single(letters(
                ServerConfigChangeValidator.MAX_VALUE_LENGTH + 1)))).toBytes(buffer);
        LostTalesServerConfigSyncPacket decoded = new LostTalesServerConfigSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.getEntries().get(0).isTooLong());
        assertFalse(decoded.getEntries().get(0).isList());
    }
}
