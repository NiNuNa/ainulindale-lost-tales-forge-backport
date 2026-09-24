package com.ninuna.losttales.chat;

import java.util.Locale;

/** Why a player reports a message to staff. */
public enum ChatReportReason {
    SPAM,
    HARASSMENT,
    CHEATING,
    OTHER;

    /** The lang key of the reason's name, as a menu row and a console entry show it. */
    public String langKey() {
        return "chat.losttales.report.reason." + name().toLowerCase(Locale.ROOT);
    }

    /** The reason at that ordinal, or null for none. */
    public static ChatReportReason fromOrdinal(int ordinal) {
        ChatReportReason[] reasons = values();
        return ordinal < 0 || ordinal >= reasons.length ? null : reasons[ordinal];
    }
}
