package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.io.IOException;

/**
 * The channel topic as the server wants it and as Discord last took it,
 * and the pacing between the two. The server thread only ever states
 * the wanted topic; the bridge's worker writes it when it differs from
 * the applied one and the interval since the last write has passed, so
 * a burst of joins and leaves folds into one write and Discord's own
 * limit on topic edits (two per channel per ten minutes) is never the
 * thing that stalls it. A final write — the offline topic at shutdown —
 * skips the interval but still respects a rate limit Discord has
 * already announced. A refused write (no Manage Channels, a bad token,
 * a JVM that cannot PATCH) switches the updater off for the session
 * with one log line; any other failure backs off on its own clock and
 * never delays the chat relay. One of these stands for each Discord
 * channel whose topic the bridge keeps, so a channel refused or limited
 * on its own never holds the others back.
 */
final class DiscordChannelStatus {
    private static final long MIN_BACKOFF_MILLIS = 5000L;
    private static final long MAX_BACKOFF_MILLIS = 60000L;

    /** The Discord channel this topic belongs to, for its log lines. */
    private final String channelId;
    private String desired = "";
    private String applied = "";
    private long notBeforeMillis;
    private long rateLimitedUntilMillis;
    private long backoffMillis = MIN_BACKOFF_MILLIS;
    private boolean disabled;
    private boolean healthy = true;

    DiscordChannelStatus(String channelId) {
        this.channelId = channelId == null ? "" : channelId;
    }

    /** The Discord channel this topic belongs to. */
    String channelId() {
        return this.channelId;
    }

    /** States the topic the channel should show; any thread. */
    synchronized void request(String topic) {
        this.desired = topic == null ? "" : topic;
    }

    /** Forgets what was applied, so the next session writes afresh. */
    synchronized void reset() {
        this.desired = "";
        this.applied = "";
        this.notBeforeMillis = 0L;
        this.rateLimitedUntilMillis = 0L;
        this.backoffMillis = MIN_BACKOFF_MILLIS;
        this.disabled = false;
        this.healthy = true;
    }

    synchronized boolean isDisabled() {
        return this.disabled;
    }

    /** Whether a write is wanted and allowed now; the worker asks before each loop. */
    synchronized boolean isDue(long nowMillis, boolean finalAttempt) {
        if (this.disabled || this.desired.length() == 0
                || this.desired.equals(this.applied)) {
            return false;
        }
        if (nowMillis < this.rateLimitedUntilMillis) {
            return false;
        }
        return finalAttempt || nowMillis >= this.notBeforeMillis;
    }

    /**
     * Writes the wanted topic if {@link #isDue}; worker thread only. The
     * interval starts at a successful write; a failure starts a backoff
     * of its own, doubling up to a minute.
     */
    void flush(String botToken, long intervalMillis, boolean finalAttempt) {
        long now = System.currentTimeMillis();
        String topic;
        synchronized (this) {
            if (!isDue(now, finalAttempt)) {
                return;
            }
            topic = this.desired;
        }
        DiscordHttp.Reply reply;
        try {
            reply = DiscordHttp.patchChannel(botToken, this.channelId,
                    DiscordJson.channelTopicBody(topic));
        } catch (DiscordHttp.PatchUnsupportedException unsupported) {
            disable("this Java runtime cannot send the request: "
                    + unsupported.getMessage());
            return;
        } catch (IOException exception) {
            failed(now, exception.toString());
            return;
        } catch (RuntimeException exception) {
            failed(now, exception.toString());
            return;
        }
        if (reply.status == 429) {
            long retryAfter = Math.max(MIN_BACKOFF_MILLIS,
                    DiscordJson.retryAfterMillis(reply.body));
            synchronized (this) {
                this.rateLimitedUntilMillis = now + retryAfter;
                this.notBeforeMillis = this.rateLimitedUntilMillis;
            }
            FMLLog.info("[%s] Discord limited the topic write of channel %s; "
                    + "retrying in %d s", LostTalesMetaData.MOD_ID,
                    this.channelId, Long.valueOf(retryAfter / 1000L));
            return;
        }
        if (reply.status == 401 || reply.status == 403) {
            disable("Discord refused it (HTTP " + reply.status + "): the bot "
                    + "needs the Manage Channels permission in the channel "
                    + "and a valid token");
            return;
        }
        if (!reply.isSuccess()) {
            failed(now, "Discord replied HTTP " + reply.status);
            return;
        }
        synchronized (this) {
            this.applied = topic;
            this.notBeforeMillis = now + Math.max(0L, intervalMillis);
            this.backoffMillis = MIN_BACKOFF_MILLIS;
            if (!this.healthy) {
                this.healthy = true;
                FMLLog.info("[%s] Discord topic updates of channel %s recovered",
                        LostTalesMetaData.MOD_ID, this.channelId);
            }
        }
    }

    private synchronized void failed(long now, String reason) {
        this.notBeforeMillis = now + this.backoffMillis;
        this.backoffMillis = Math.min(MAX_BACKOFF_MILLIS,
                this.backoffMillis * 2L);
        if (this.healthy) {
            this.healthy = false;
            FMLLog.warning("[%s] Discord topic update of channel %s failing, "
                    + "retrying with backoff: %s", LostTalesMetaData.MOD_ID,
                    this.channelId, reason);
        }
    }

    private synchronized void disable(String reason) {
        if (this.disabled) {
            return;
        }
        this.disabled = true;
        FMLLog.severe("[%s] Discord topic updates of channel %s are off until "
                + "the server restarts: %s", LostTalesMetaData.MOD_ID,
                this.channelId, reason);
    }
}
