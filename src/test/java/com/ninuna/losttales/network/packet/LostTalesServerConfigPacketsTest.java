package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.config.server.ServerConfigApplyResult;
import com.ninuna.losttales.config.server.ServerConfigChange;
import com.ninuna.losttales.config.server.ServerConfigEntry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The four server-config packets round-trip, and refuse what does not fit. */
public final class LostTalesServerConfigPacketsTest {

    private static ServerConfigEntry entry() {
        return new ServerConfigEntry("discord", "channelBindings",
                ServerConfigEntry.Type.STRING, true,
                Arrays.asList("ooc=BIDIRECTIONAL;channel=1;webhook=x", "all=DISABLED;webhook="),
                Collections.singletonList("ooc=DISABLED;channel=;webhook="),
                "", "", "One entry per bound game channel.", "channelBindings", false,
                Collections.<String>emptyList());
    }

    @Test
    public void theSnapshotRoundTripsEveryField() {
        ServerConfigEntry secret = new ServerConfigEntry("discord", "botToken",
                ServerConfigEntry.Type.STRING, false, Collections.singletonList(""),
                Collections.singletonList(""), "", "", "Secret.", "botToken", true, null);
        ServerConfigEntry number = new ServerConfigEntry("discord", "pollIntervalSeconds",
                ServerConfigEntry.Type.INTEGER, false, Collections.singletonList("3"),
                Collections.singletonList("3"), "2", "60", "How often.", "pollIntervalSeconds",
                false, Arrays.asList("2", "3"));
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesServerConfigSyncPacket(Arrays.asList(entry(), secret, number))
                .toBytes(buffer);
        LostTalesServerConfigSyncPacket decoded = new LostTalesServerConfigSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        List<ServerConfigEntry> entries = decoded.getEntries();
        assertEquals(3, entries.size());
        assertTrue(entries.get(0).isList());
        assertEquals(entry().getValues(), entries.get(0).getValues());
        assertEquals("One entry per bound game channel.", entries.get(0).getComment());
        assertTrue(entries.get(1).isSecret());
        assertEquals("", entries.get(1).getValue());
        assertEquals(ServerConfigEntry.Type.INTEGER, entries.get(2).getType());
        assertEquals("2", entries.get(2).getMinValue());
        assertEquals("60", entries.get(2).getMaxValue());
        assertEquals(Arrays.asList("2", "3"), entries.get(2).getValidValues());
    }

    @Test
    public void aTruncatedSnapshotIsMalformedAndEmpty() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesServerConfigSyncPacket(Collections.singletonList(entry())).toBytes(buffer);
        LostTalesServerConfigSyncPacket decoded = new LostTalesServerConfigSyncPacket();
        decoded.fromBytes(buffer.slice(0, buffer.readableBytes() - 5));
        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getEntries().isEmpty());
    }

    @Test
    public void changesRoundTripAndAnEmptyApplyIsRefused() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesServerConfigApplyPacket(Arrays.asList(
                new ServerConfigChange("discord", "pollIntervalSeconds", "5"),
                new ServerConfigChange("discord", "channelBindings", true,
                        Arrays.asList("ooc=DISABLED;channel=;webhook=")))).toBytes(buffer);
        LostTalesServerConfigApplyPacket decoded = new LostTalesServerConfigApplyPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(2, decoded.getChanges().size());
        assertEquals("5", decoded.getChanges().get(0).getValue());
        assertTrue(decoded.getChanges().get(1).isList());

        ByteBuf empty = Unpooled.buffer();
        empty.writeInt(0);
        LostTalesServerConfigApplyPacket none = new LostTalesServerConfigApplyPacket();
        none.fromBytes(empty);
        assertTrue(none.isMalformed());
    }

    @Test
    public void theResultRoundTrips() {
        ServerConfigApplyResult result = new ServerConfigApplyResult(
                Arrays.asList("discord.pollIntervalSeconds"),
                Collections.singletonList(new ServerConfigApplyResult.Refusal(
                        "chat.proximityRadius", "above the maximum 512")),
                Arrays.asList("Discord bridge"), "");
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesServerConfigResultPacket(result).toBytes(buffer);
        LostTalesServerConfigResultPacket decoded = new LostTalesServerConfigResultPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(Arrays.asList("discord.pollIntervalSeconds"),
                decoded.getResult().getApplied());
        assertEquals("above the maximum 512",
                decoded.getResult().getRefused().get(0).getReason());
        assertEquals(Arrays.asList("Discord bridge"), decoded.getResult().getRestarted());
    }

    @Test
    public void theRequestCarriesNothingAndRefusesTrailingBytes() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesServerConfigRequestPacket().toBytes(buffer);
        assertEquals(0, buffer.readableBytes());
        LostTalesServerConfigRequestPacket decoded = new LostTalesServerConfigRequestPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        buffer.writeByte(1);
        LostTalesServerConfigRequestPacket trailing = new LostTalesServerConfigRequestPacket();
        trailing.fromBytes(buffer);
        assertTrue(trailing.isMalformed());
    }
}
