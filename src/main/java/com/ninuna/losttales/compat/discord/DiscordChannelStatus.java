package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.io.IOException;

/**
 * One worker's writer of a channel's topic. The server thread states the
 * wanted topic; the worker writes it when its {@link DiscordTopicPacing}
 * gives it a turn: only when the words differ from those Discord last
 * took, at most once every interval, and never more often than Discord's
 * two topic edits per channel in ten minutes, so a burst of joins and
 * leaves folds into one write, the newest words winning. The pacing
 * belongs to the bridge for the whole server run and outlives this
 * writer, so a restart of the bridge neither writes the same words again
 * nor spends an edit Discord has not given back.
 *
 * <p>A last write as the bridge stops (the offline topic) skips the
 * interval but not Discord's allowance or a pause it asked for. A
 * refused write (no Manage Channels, a bad token, a JVM that cannot
 * PATCH) switches this writer off until the bridge is reloaded, with one
 * log line; any other failure backs off on its own clock and never
 * delays the chat relay. One of these stands for each Discord channel
 * whose topic the bridge keeps, so a channel refused or limited on its
 * own never holds the others back.</p>
 */
final class DiscordChannelStatus {
    private static final long MIN_BACKOFF_MILLIS = 5000L;
    private static final long MAX_BACKOFF_MILLIS = 60000L;

    /** The Discord channel this topic belongs to, for its log lines. */
    private final String channelId;
    /** When and what the topic is written, kept by the bridge for the server run. */
    private final DiscordTopicPacing pacing;
    /** Until when a failure holds the next write back. */
    private long failedUntilMillis;
    private long backoffMillis = MIN_BACKOFF_MILLIS;
    private boolean disabled;
    private boolean healthy = true;
    /**
     * Whether the words wanted are this writer's last, the offline topic
     * as the bridge stops or lets the channel go: only those skip the
     * interval as the worker stops.
     */
    private volatile boolean last;

    DiscordChannelStatus(String channelId, DiscordTopicPacing pacing) {
        this.channelId = channelId == null ? "" : channelId;
        this.pacing = pacing == null ? new DiscordTopicPacing() : pacing;
    }

    /** States the topic the channel should show; any thread. */
    void request(String topic) {
        this.pacing.want(topic);
    }

    /**
     * States the last topic this writer is to write, as the bridge stops
     * or lets the channel go: written as the worker stops, without
     * waiting out the interval. Any thread.
     */
    void requestLast(String topic) {
        this.last = true;
        this.pacing.want(topic);
    }

    /**
     * When this writer next has something to do, by the pacing and its
     * own backoff: {@link Long#MAX_VALUE} when the topic shows what is
     * wanted or the writer is off. Worker thread.
     */
    synchronized long nextDueMillis(long intervalMillis) {
        if (this.disabled || !this.pacing.hasChange()) {
            return Long.MAX_VALUE;
        }
        return Math.max(this.failedUntilMillis,
                this.pacing.nextTurn(intervalMillis, false));
    }

    /**
     * Writes the wanted topic when its turn has come; worker thread only.
     * The interval starts at a successful write; a failure starts a
     * backoff of its own, doubling up to a minute. As the worker stops
     * ({@code finalAttempt}) only a last topic skips the interval
     * ({@link #requestLast}); a change still waiting for its turn is
     * left to the next worker, which keeps the same pacing.
     */
    void flush(String botToken, long intervalMillis, boolean finalAttempt) {
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (this.disabled || (now < this.failedUntilMillis && !finalAttempt)) {
                return;
            }
        }
        String topic = this.pacing.take(now, intervalMillis, finalAttempt && this.last);
        if (topic == null) {
            return;
        }
        DiscordHttp.Reply reply = null;
        try {
            reply = DiscordHttp.patchChannel(botToken, this.channelId,
                    DiscordJson.channelTopicBody(topic));
        } catch (DiscordHttp.PatchUnsupportedException unsupported) {
            disable("this Java runtime cannot send the request: "
                    + DiscordHttp.describe(unsupported));
            return;
        } catch (IOException exception) {
            failed(now, DiscordHttp.describe(exception));
            return;
        } catch (RuntimeException exception) {
            failed(now, DiscordHttp.describe(exception));
            return;
        } finally {
            if (reply == null) {
                // No answer: the words stay wanted for the next turn.
                this.pacing.released();
            }
        }
        long answered = System.currentTimeMillis();
        if (reply.status == 429) {
            // Discord's own pause, up to its whole window for topic edits:
            // a pause cut shorter would only be refused again.
            long retryAfter = Math.max(MIN_BACKOFF_MILLIS, DiscordJson.retryAfterMillis(
                    reply.body, DiscordTopicPacing.WINDOW_MILLIS));
            this.pacing.limited(answered, retryAfter);
            FMLLog.info("[%s] Discord limited the topic write of channel %s; "
                    + "retrying in %d s", LostTalesMetaData.MOD_ID,
                    this.channelId, Long.valueOf(retryAfter / 1000L));
            return;
        }
        if (reply.status == 401 || reply.status == 403) {
            this.pacing.released();
            disable("Discord refused it (HTTP " + reply.status + "): the bot "
                    + "needs the Manage Channels permission in the channel "
                    + "and a valid token");
            return;
        }
        if (!reply.isSuccess()) {
            this.pacing.released();
            failed(now, "Discord replied HTTP " + reply.status);
            return;
        }
        this.pacing.posted(topic, answered);
        synchronized (this) {
            this.backoffMillis = MIN_BACKOFF_MILLIS;
            if (!this.healthy) {
                this.healthy = true;
                FMLLog.info("[%s] Discord topic updates of channel %s recovered",
                        LostTalesMetaData.MOD_ID, this.channelId);
            }
        }
    }

    private synchronized void failed(long now, String reason) {
        this.failedUntilMillis = now + this.backoffMillis;
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
                + "the bridge is reloaded: %s", LostTalesMetaData.MOD_ID,
                this.channelId, reason);
    }
}
