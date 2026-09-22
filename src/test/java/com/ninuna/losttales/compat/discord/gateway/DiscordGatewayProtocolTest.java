package com.ninuna.losttales.compat.discord.gateway;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The Gateway exchange, replayed: hello, identify, heartbeat, ready, resume, events. */
public final class DiscordGatewayProtocolTest {

    private static final String TOKEN = "t.o.k.e.n";

    private static JsonObject json(String text) {
        return new JsonParser().parse(text).getAsJsonObject();
    }

    @Test
    public void helloStartsHeartbeatsAndIdentifiesAFreshSession() {
        DiscordGatewayProtocol protocol = new DiscordGatewayProtocol(TOKEN,
                DiscordGatewayProtocol.INTENTS);
        protocol.onConnected();
        List<DiscordGatewayProtocol.Action> actions = protocol.onPayload(
                "{\"op\":10,\"d\":{\"heartbeat_interval\":41250}}");
        assertEquals(2, actions.size());
        assertEquals(DiscordGatewayProtocol.Action.Type.HEARTBEAT_EVERY, actions.get(0).type);
        assertEquals(41250L, actions.get(0).intervalMillis);
        assertEquals(DiscordGatewayProtocol.Action.Type.SEND, actions.get(1).type);
        JsonObject identify = json(actions.get(1).payload);
        assertEquals(2, identify.get("op").getAsInt());
        assertEquals(TOKEN, identify.getAsJsonObject("d").get("token").getAsString());
        assertEquals(DiscordGatewayProtocol.INTENTS,
                identify.getAsJsonObject("d").get("intents").getAsInt());
        assertEquals(34305, DiscordGatewayProtocol.INTENTS);
    }

    /**
     * With the member intents a server of up to 250 members arrives
     * whole; refused, they are dropped from the next identify.
     */
    @Test
    public void theMemberIntentsAskForWholeServersAndCanBeDropped() {
        DiscordGatewayProtocol protocol = new DiscordGatewayProtocol(TOKEN,
                DiscordGatewayProtocol.INTENTS | DiscordGatewayProtocol.MEMBER_INTENTS);
        JsonObject identify = json(protocol.identifyPayload()).getAsJsonObject("d");
        assertEquals(34305 | 2 | 256, identify.get("intents").getAsInt());
        assertEquals(250, identify.get("large_threshold").getAsInt());
        protocol.dropIntents(DiscordGatewayProtocol.MEMBER_INTENTS);
        identify = json(protocol.identifyPayload()).getAsJsonObject("d");
        assertEquals(34305, identify.get("intents").getAsInt());
        assertFalse(identify.has("large_threshold"));
    }

    /** The ask for a server's members names it and wants everyone, statuses included. */
    @Test
    public void membersAreAskedForWithTheirStatuses() {
        JsonObject ask = json(DiscordGatewayProtocol.requestMembersPayload("100"));
        assertEquals(8, ask.get("op").getAsInt());
        JsonObject data = ask.getAsJsonObject("d");
        assertEquals("100", data.get("guild_id").getAsString());
        assertEquals("", data.get("query").getAsString());
        assertEquals(0, data.get("limit").getAsInt());
        assertTrue(data.get("presences").getAsBoolean());
    }

    @Test
    public void readyRemembersTheSessionAndEventsAreDispatched() {
        DiscordGatewayProtocol protocol = new DiscordGatewayProtocol(TOKEN, 0);
        List<DiscordGatewayProtocol.Action> ready = protocol.onPayload(
                "{\"op\":0,\"s\":1,\"t\":\"READY\",\"d\":{\"session_id\":\"abc\","
                        + "\"resume_gateway_url\":\"wss://resume.example\","
                        + "\"application\":{\"id\":\"42\"},"
                        + "\"guilds\":[{\"id\":\"g1\"},{\"id\":\"g2\"}]}}");
        assertEquals(1, ready.size());
        assertEquals(DiscordGatewayProtocol.Action.Type.READY, ready.get(0).type);
        assertEquals("abc", protocol.getSessionId());
        assertEquals(1L, protocol.getSequence());
        assertTrue(protocol.canResume());
        assertEquals("wss://resume.example", protocol.getResumeGatewayUrl());
        assertEquals("42", DiscordGatewayProtocol.applicationId(ready.get(0).data));
        assertEquals(Arrays.asList("g1", "g2"),
                DiscordGatewayProtocol.guildIds(ready.get(0).data));

        List<DiscordGatewayProtocol.Action> event = protocol.onPayload(
                "{\"op\":0,\"s\":2,\"t\":\"MESSAGE_CREATE\",\"d\":{\"id\":\"m1\"}}");
        assertEquals(DiscordGatewayProtocol.Action.Type.EVENT, event.get(0).type);
        assertEquals("MESSAGE_CREATE", event.get(0).name);
        assertEquals("m1", event.get(0).data.get("id").getAsString());
        assertEquals(2L, protocol.getSequence());

        // The next hello resumes instead of identifying.
        protocol.onConnected();
        List<DiscordGatewayProtocol.Action> hello = protocol.onPayload(
                "{\"op\":10,\"d\":{\"heartbeat_interval\":1000}}");
        JsonObject resume = json(hello.get(1).payload);
        assertEquals(6, resume.get("op").getAsInt());
        assertEquals("abc", resume.getAsJsonObject("d").get("session_id").getAsString());
        assertEquals(2, resume.getAsJsonObject("d").get("seq").getAsInt());
    }

    @Test
    public void heartbeatsCarryTheSequenceAndAMissedAckReconnects() {
        DiscordGatewayProtocol protocol = new DiscordGatewayProtocol(TOKEN, 0);
        protocol.onConnected();
        assertEquals("{\"op\":1,\"d\":null}", protocol.heartbeatPayload());
        protocol.onPayload("{\"op\":0,\"s\":7,\"t\":\"RESUMED\",\"d\":{}}");
        List<DiscordGatewayProtocol.Action> first = protocol.onHeartbeatDue();
        assertEquals(DiscordGatewayProtocol.Action.Type.SEND, first.get(0).type);
        assertEquals("{\"op\":1,\"d\":7}", first.get(0).payload);
        // No ACK arrived: the next beat gives up on the connection.
        List<DiscordGatewayProtocol.Action> second = protocol.onHeartbeatDue();
        assertEquals(DiscordGatewayProtocol.Action.Type.RECONNECT, second.get(0).type);
        assertTrue(second.get(0).resume);
        // An ACK restores the beat.
        protocol.onPayload("{\"op\":11}");
        assertEquals(DiscordGatewayProtocol.Action.Type.SEND, protocol.onHeartbeatDue().get(0).type);
        // A heartbeat request is answered at once.
        assertEquals(DiscordGatewayProtocol.Action.Type.SEND,
                protocol.onPayload("{\"op\":1}").get(0).type);
    }

    @Test
    public void reconnectAndInvalidSessionDecideWhetherToResume() {
        DiscordGatewayProtocol protocol = new DiscordGatewayProtocol(TOKEN, 0);
        protocol.onPayload("{\"op\":0,\"s\":3,\"t\":\"READY\",\"d\":{\"session_id\":\"s\"}}");
        List<DiscordGatewayProtocol.Action> reconnect = protocol.onPayload("{\"op\":7}");
        assertEquals(DiscordGatewayProtocol.Action.Type.RECONNECT, reconnect.get(0).type);
        assertTrue(reconnect.get(0).resume);
        assertTrue(protocol.canResume());
        List<DiscordGatewayProtocol.Action> resumable = protocol.onPayload(
                "{\"op\":9,\"d\":true}");
        assertTrue(resumable.get(0).resume);
        assertTrue(protocol.canResume());
        List<DiscordGatewayProtocol.Action> gone = protocol.onPayload("{\"op\":9,\"d\":false}");
        assertFalse(gone.get(0).resume);
        assertFalse(protocol.canResume());
        assertEquals("", protocol.getResumeGatewayUrl());
        assertTrue(protocol.onPayload("not json").isEmpty());
        assertTrue(protocol.onPayload("{\"op\":99}").isEmpty());
    }
}
