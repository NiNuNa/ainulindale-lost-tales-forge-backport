package com.ninuna.losttales.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import java.util.Locale;

/**
 * How far a line said nearby carries (CHAT-37): a whisper, talk or a
 * shout, each as far as the server's settings say. Only Proximity Chat
 * and a server channel heard nearby have ranges; every other line is
 * talk, which reaches whoever the channel reaches.
 *
 * <p>A reader within the nearest third of the line's range reads the
 * words one size step larger, one in the far third one step smaller
 * ({@link #stepFor}); the server measures that once per reader, as the
 * line is said.</p>
 */
public enum ChatSpeechRange {
    WHISPER("whisper"),
    TALK("talk"),
    SHOUT("shout");

    /** The size step for a reader in the nearest third of the range. */
    public static final int NEAR_STEP = 1;
    /** The size step for a reader in the middle third: the usual size. */
    public static final int USUAL_STEP = 0;
    /** The size step for a reader in the far third. */
    public static final int FAR_STEP = -1;

    /** The range's code name: config, the layout file and lang keys. */
    public final String id;

    ChatSpeechRange(String id) {
        this.id = id;
    }

    /** The range a byte on the wire names, or null for none. */
    public static ChatSpeechRange fromCode(int code) {
        ChatSpeechRange[] all = values();
        return code >= 0 && code < all.length ? all[code] : null;
    }

    /** The byte this range travels as. */
    public int code() {
        return ordinal();
    }

    /** The range a code name names, or {@code fallback} for any other word. */
    public static ChatSpeechRange of(String id, ChatSpeechRange fallback) {
        String wanted = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (ChatSpeechRange range : values()) {
            if (range.id.equals(wanted)) {
                return range;
            }
        }
        return fallback;
    }

    /** Every range's code name, nearest first: the words of its options. */
    public static String[] ids() {
        ChatSpeechRange[] all = values();
        String[] ids = new String[all.length];
        for (int index = 0; index < all.length; index++) {
            ids[index] = all[index].id;
        }
        return ids;
    }

    /** The next range out, the farthest stepping round to the nearest: a click on the button. */
    public ChatSpeechRange next() {
        ChatSpeechRange[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** The next range in, the nearest stepping round to the farthest: a right-click. */
    public ChatSpeechRange previous() {
        ChatSpeechRange[] all = values();
        return all[(ordinal() + all.length - 1) % all.length];
    }

    /** Whether a line of {@code channel} is heard nearby and so takes a range. */
    public static boolean appliesTo(ChatChannel channel) {
        return channel != null
                && channel.getRecipientRule() == ChatRecipientRule.PROXIMITY;
    }

    /** The range a line of {@code channel} takes: the one asked for nearby, talk anywhere else. */
    public static ChatSpeechRange forChannel(ChatChannel channel,
                                             ChatSpeechRange asked) {
        return appliesTo(channel) && asked != null ? asked : TALK;
    }

    /** How far this range carries on the server, in blocks, as its settings say. */
    public int blocks() {
        return blocks(LostTalesConfig.chatProximityWhisperRange,
                LostTalesConfig.chatProximityTalkRange,
                LostTalesConfig.chatProximityShoutRange);
    }

    /** As above, from the three settings given. */
    int blocks(int whisper, int talk, int shout) {
        int blocks = this == WHISPER ? whisper : this == SHOUT ? shout : talk;
        return Math.max(1, blocks);
    }

    /**
     * The size step a reader {@code distance} blocks from the speaker
     * reads a line of {@code rangeBlocks} at: larger within its nearest
     * third, the usual size in its middle third, smaller beyond.
     */
    public static int stepFor(double distance, int rangeBlocks) {
        double range = Math.max(1.0D, rangeBlocks);
        double away = Math.max(0.0D, distance);
        if (away <= range / 3.0D) {
            return NEAR_STEP;
        }
        return away <= range * 2.0D / 3.0D ? USUAL_STEP : FAR_STEP;
    }

    /** Whether {@code step} is one a line may carry. */
    public static boolean isStep(int step) {
        return step >= FAR_STEP && step <= NEAR_STEP;
    }
}
