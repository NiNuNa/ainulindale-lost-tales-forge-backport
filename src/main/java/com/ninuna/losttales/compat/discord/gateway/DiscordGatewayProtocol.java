package com.ninuna.losttales.compat.discord.gateway;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Gateway's conversation, without the socket: what each payload the
 * server sends means, what to send back, and what to remember — the
 * heartbeat interval, the sequence, the session and where to resume it.
 * A connection hands every text it reads here and carries out the
 * actions it gets back; a heartbeat tick asks {@link #onHeartbeatDue()}.
 * Nothing here blocks or connects, so the whole exchange can be replayed
 * in a test.
 */
public final class DiscordGatewayProtocol {

    public static final int OP_DISPATCH = 0;
    public static final int OP_HEARTBEAT = 1;
    public static final int OP_IDENTIFY = 2;
    public static final int OP_RESUME = 6;
    public static final int OP_RECONNECT = 7;
    public static final int OP_REQUEST_GUILD_MEMBERS = 8;
    public static final int OP_INVALID_SESSION = 9;
    public static final int OP_HELLO = 10;
    public static final int OP_HEARTBEAT_ACK = 11;
    /**
     * GUILDS, GUILD_MESSAGES, GUILD_MESSAGE_REACTIONS and MESSAGE_CONTENT:
     * what reading bound channels and the reactions on them needs. Only
     * MESSAGE_CONTENT is privileged.
     */
    public static final int INTENTS = 1 | (1 << 9) | (1 << 10) | (1 << 15);
    /**
     * GUILD_MEMBERS and GUILD_PRESENCES: who is in a server and what
     * they are doing, which the member lists need. Both are privileged,
     * and asked for only while the server lists Discord members.
     */
    public static final int MEMBER_INTENTS = (1 << 1) | (1 << 8);
    /**
     * Servers of up to this many members arrive whole with their
     * GUILD_CREATE while the member intents are asked for; Discord's
     * highest. A larger one sends its online members, and the rest are
     * asked for ({@link #requestMembersPayload}).
     */
    public static final int LARGE_THRESHOLD = 250;

    /** What the connection is to do with a payload. */
    public static final class Action {
        public enum Type {
            /** Send the payload text. */
            SEND,
            /** Start (or restart) heartbeats every {@code intervalMillis}. */
            HEARTBEAT_EVERY,
            /** Close and connect again, resuming when {@code resume} is set. */
            RECONNECT,
            /** The session is ready: {@code data} is the READY payload. */
            READY,
            /** A dispatched event: {@code name} and {@code data}. */
            EVENT
        }

        public final Type type;
        public final String payload;
        public final long intervalMillis;
        public final boolean resume;
        public final String name;
        public final JsonObject data;

        private Action(Type type, String payload, long intervalMillis, boolean resume,
                       String name, JsonObject data) {
            this.type = type;
            this.payload = payload;
            this.intervalMillis = intervalMillis;
            this.resume = resume;
            this.name = name;
            this.data = data;
        }

        static Action send(String payload) {
            return new Action(Type.SEND, payload, 0L, false, "", null);
        }

        static Action heartbeatEvery(long intervalMillis) {
            return new Action(Type.HEARTBEAT_EVERY, "", intervalMillis, false, "", null);
        }

        static Action reconnect(boolean resume) {
            return new Action(Type.RECONNECT, "", 0L, resume, "", null);
        }

        static Action ready(JsonObject data) {
            return new Action(Type.READY, "", 0L, false, "READY", data);
        }

        static Action event(String name, JsonObject data) {
            return new Action(Type.EVENT, "", 0L, false, name, data);
        }
    }

    private final String token;
    private int intents;
    private long sequence = -1L;
    private String sessionId = "";
    private String resumeGatewayUrl = "";
    private long heartbeatIntervalMillis;
    private boolean heartbeatAcknowledged = true;

    public DiscordGatewayProtocol(String token, int intents) {
        this.token = token == null ? "" : token;
        this.intents = intents;
    }

    /** Whether a session can be resumed rather than identified afresh. */
    public boolean canResume() {
        return this.sessionId.length() > 0 && this.sequence >= 0L;
    }

    /** Where to connect next: the resume URL of a session, else empty. */
    public String getResumeGatewayUrl() {
        return canResume() ? this.resumeGatewayUrl : "";
    }

    public long getSequence() {
        return this.sequence;
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public long getHeartbeatIntervalMillis() {
        return this.heartbeatIntervalMillis;
    }

    /** The intents the next identify asks for. */
    public int getIntents() {
        return this.intents;
    }

    /**
     * Stops asking for {@code dropped}, from the next identify on: what a
     * refused privileged intent comes to.
     */
    public void dropIntents(int dropped) {
        this.intents &= ~dropped;
    }

    /** Forgets the session, so the next connection identifies afresh. */
    public void forgetSession() {
        this.sessionId = "";
        this.sequence = -1L;
        this.resumeGatewayUrl = "";
    }

    /** A new connection was opened; the next payload is HELLO. */
    public void onConnected() {
        this.heartbeatAcknowledged = true;
    }

    /** What to do with a text the server sent; never throws on bad JSON. */
    public List<Action> onPayload(String json) {
        JsonObject payload = parse(json);
        if (payload == null) {
            return Collections.emptyList();
        }
        int op = integer(payload, "op", -1);
        JsonElement sequenceValue = payload.get("s");
        if (sequenceValue != null && sequenceValue.isJsonPrimitive()) {
            try {
                this.sequence = sequenceValue.getAsLong();
            } catch (RuntimeException ignored) {
                // A sequence that is not a number is left as it was.
            }
        }
        List<Action> actions = new ArrayList<Action>();
        switch (op) {
            case OP_HELLO:
                JsonObject hello = object(payload, "d");
                this.heartbeatIntervalMillis = hello == null ? 41250L
                        : integer(hello, "heartbeat_interval", 41250);
                this.heartbeatAcknowledged = true;
                actions.add(Action.heartbeatEvery(this.heartbeatIntervalMillis));
                actions.add(Action.send(canResume() ? resumePayload() : identifyPayload()));
                break;
            case OP_HEARTBEAT:
                actions.add(Action.send(heartbeatPayload()));
                break;
            case OP_HEARTBEAT_ACK:
                this.heartbeatAcknowledged = true;
                break;
            case OP_RECONNECT:
                actions.add(Action.reconnect(true));
                break;
            case OP_INVALID_SESSION:
                JsonElement resumable = payload.get("d");
                boolean resume = resumable != null && resumable.isJsonPrimitive()
                        && bool(resumable);
                if (!resume) {
                    forgetSession();
                }
                actions.add(Action.reconnect(resume));
                break;
            case OP_DISPATCH:
                String name = string(payload, "t");
                JsonObject data = object(payload, "d");
                if ("READY".equals(name) && data != null) {
                    this.sessionId = string(data, "session_id");
                    this.resumeGatewayUrl = string(data, "resume_gateway_url");
                    actions.add(Action.ready(data));
                } else if (name.length() > 0 && data != null) {
                    actions.add(Action.event(name, data));
                }
                break;
            default:
                break;
        }
        return actions;
    }

    /**
     * The heartbeat is due: send it, unless the last one was never
     * acknowledged, in which case the connection is a zombie and is
     * dropped to be resumed.
     */
    public List<Action> onHeartbeatDue() {
        if (!this.heartbeatAcknowledged) {
            return Collections.singletonList(Action.reconnect(true));
        }
        this.heartbeatAcknowledged = false;
        return Collections.singletonList(Action.send(heartbeatPayload()));
    }

    public String heartbeatPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("op", Integer.valueOf(OP_HEARTBEAT));
        if (this.sequence < 0L) {
            payload.add("d", com.google.gson.JsonNull.INSTANCE);
        } else {
            payload.addProperty("d", Long.valueOf(this.sequence));
        }
        return payload.toString();
    }

    public String identifyPayload() {
        JsonObject properties = new JsonObject();
        properties.addProperty("os", System.getProperty("os.name", "unknown"));
        properties.addProperty("browser", "losttales");
        properties.addProperty("device", "losttales");
        JsonObject data = new JsonObject();
        data.addProperty("token", this.token);
        data.addProperty("intents", Integer.valueOf(this.intents));
        data.add("properties", properties);
        if ((this.intents & MEMBER_INTENTS) != 0) {
            data.addProperty("large_threshold", Integer.valueOf(LARGE_THRESHOLD));
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("op", Integer.valueOf(OP_IDENTIFY));
        payload.add("d", data);
        return payload.toString();
    }

    public String resumePayload() {
        JsonObject data = new JsonObject();
        data.addProperty("token", this.token);
        data.addProperty("session_id", this.sessionId);
        data.addProperty("seq", Long.valueOf(this.sequence));
        JsonObject payload = new JsonObject();
        payload.addProperty("op", Integer.valueOf(OP_RESUME));
        payload.add("d", data);
        return payload.toString();
    }

    /**
     * Asks for every member of a server and their statuses, which come
     * back as GUILD_MEMBERS_CHUNK events of up to a thousand members.
     * Discord answers this at most once per server in thirty seconds,
     * and a RATE_LIMITED event says when to ask again.
     */
    public static String requestMembersPayload(String guildId) {
        JsonObject data = new JsonObject();
        data.addProperty("guild_id", guildId == null ? "" : guildId);
        data.addProperty("query", "");
        data.addProperty("limit", Integer.valueOf(0));
        data.addProperty("presences", Boolean.TRUE);
        JsonObject payload = new JsonObject();
        payload.addProperty("op", Integer.valueOf(OP_REQUEST_GUILD_MEMBERS));
        payload.add("d", data);
        return payload.toString();
    }

    /** The guild ids a READY payload lists, for registering commands. */
    public static List<String> guildIds(JsonObject ready) {
        List<String> ids = new ArrayList<String>();
        JsonElement guilds = ready == null ? null : ready.get("guilds");
        if (guilds == null || !guilds.isJsonArray()) {
            return ids;
        }
        for (JsonElement guild : (JsonArray)guilds) {
            if (guild.isJsonObject()) {
                String id = string(guild.getAsJsonObject(), "id");
                if (id.length() > 0) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /** The application id a READY payload names, for registering commands. */
    public static String applicationId(JsonObject ready) {
        JsonObject application = ready == null ? null : object(ready, "application");
        return application == null ? "" : string(application, "id");
    }

    private static JsonObject parse(String json) {
        if (json == null || json.trim().length() == 0) {
            return null;
        }
        try {
            JsonElement root = new JsonParser().parse(json);
            return root != null && root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return "";
        }
        try {
            return value.getAsString();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static boolean bool(JsonElement value) {
        try {
            return value.getAsBoolean();
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
