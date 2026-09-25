package com.ninuna.losttales.client.window;

import java.util.Locale;
import net.minecraft.util.StatCollector;

/**
 * The kinds of sub-window the chat opens: one of each at most, but a
 * card for each person. The id is what the layout file remembers a kind's
 * place by.
 */
public enum SubWindowKind {
    EMOJI("emoji"),
    REACTIONS("reactions"),
    ITEMS("items"),
    MARKERS("markers"),
    QUESTS("quests"),
    CARD("card"),
    /** A message's actions. */
    MESSAGE("message"),
    /** A person's: message them, ignore them. */
    PERSON("person"),
    /** A tab's settings, behind the channel's cog on the tool strip. */
    TAB("tab"),
    /** A window's own menu, behind the three dots at the end of its row. */
    WINDOW("window"),
    /** The palette a colour row of the window menu opens. */
    PALETTE("palette"),
    /** The closed channels and the conversations to open, behind the {@code +}. */
    OPEN("open"),
    /** The tab search. */
    TAB_SEARCH("tab_search"),
    /** The identities and the statuses, behind the head button. */
    CHARACTERS("characters"),
    /** The status line's field. */
    STATUS_LINE("status_line"),
    /** A message's report: a note and a reason. */
    REPORT("report"),
    /** Every chat setting, the channels' switches, the ignored and the shortcuts. */
    SETTINGS("settings");

    final String id;

    SubWindowKind(String id) {
        this.id = id;
    }

    /** The name on the window's strip. */
    public String title() {
        return StatCollector.translateToLocal(
                "gui.losttales.chat.small_window." + this.id);
    }

    /** The kind the layout file names, or null for a word it does not know. */
    static SubWindowKind fromId(String id) {
        String wanted = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (SubWindowKind kind : values()) {
            if (kind.id.equals(wanted)) {
                return kind;
            }
        }
        return null;
    }
}
