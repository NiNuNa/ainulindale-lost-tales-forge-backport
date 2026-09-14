package com.ninuna.losttales.chat;

/**
 * What the sender of a line bound for Discord is told about its post:
 * nothing while it goes out as it should, a clock while it is still
 * waiting a few seconds after it was said, and a crimson mark once it
 * will not reach Discord, each with the reason. The server decides and
 * tells the sender alone. Both sides read the codes, which are wire
 * surface: new ones are only ever appended.
 */
public final class ChatDeliveryMark {
    private ChatDeliveryMark() {}

    /** How far a line's post has got, as the sender's line shows it. */
    public enum State {
        /** Nothing to show: the post went out, or nothing is known. */
        NONE(0, ""),
        /** Still waiting to be posted. */
        RETRYING(1, "gui.losttales.chat.delivery.retrying"),
        /** Not posted, and it will not be. */
        FAILED(2, "gui.losttales.chat.delivery.failed");

        private final int code;
        private final String langKey;

        State(int code, String langKey) {
            this.code = code;
            this.langKey = langKey;
        }

        /** The byte the state crosses the wire as. */
        public int code() {
            return this.code;
        }

        /** What the mark's tooltip opens with; empty for {@link #NONE}. */
        public String langKey() {
            return this.langKey;
        }

        /** The state a wire code names; null for a code that names none. */
        public static State fromCode(int code) {
            for (State state : values()) {
                if (state.code == code) {
                    return state;
                }
            }
            return null;
        }
    }

    /** Why a line's post is waiting, or why it will not be posted. */
    public enum Reason {
        /** No reason: what a lifted mark carries. */
        NONE(0, ""),
        /** Behind earlier posts to the same webhook. */
        WAITING(1, "gui.losttales.chat.delivery.reason.waiting"),
        /** Discord asked the server to slow down. */
        LIMITED(2, "gui.losttales.chat.delivery.reason.limited"),
        /** Sends are failing and are being tried again. */
        FAILING(3, "gui.losttales.chat.delivery.reason.failing"),
        /** Discord refused the message itself. */
        REFUSED(4, "gui.losttales.chat.delivery.reason.refused"),
        /** Discord could not be reached in the tries a post gets. */
        GAVE_UP(5, "gui.losttales.chat.delivery.reason.gave_up"),
        /** The channel's webhook is off until the bridge is reloaded. */
        WEBHOOK_OFF(6, "gui.losttales.chat.delivery.reason.webhook_off"),
        /** More messages were waiting than the bridge holds. */
        QUEUE_FULL(7, "gui.losttales.chat.delivery.reason.queue_full"),
        /** The bridge stopped before the post went out. */
        STOPPED(8, "gui.losttales.chat.delivery.reason.stopped"),
        /**
         * A code this build does not know, from a newer server. Read,
         * never sent.
         */
        UNKNOWN(-1, "gui.losttales.chat.delivery.reason.unknown");

        private final int code;
        private final String langKey;

        Reason(int code, String langKey) {
            this.code = code;
            this.langKey = langKey;
        }

        /** The byte the reason crosses the wire as; {@link #UNKNOWN} has none. */
        public int code() {
            return this.code;
        }

        /** The tooltip line that says why; empty for {@link #NONE}. */
        public String langKey() {
            return this.langKey;
        }

        /**
         * The reason a wire code names; {@link #UNKNOWN} for a code this
         * build does not know.
         */
        public static Reason fromCode(int code) {
            for (Reason reason : values()) {
                if (reason.code == code) {
                    return reason;
                }
            }
            return UNKNOWN;
        }
    }
}
