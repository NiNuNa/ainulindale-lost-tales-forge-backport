package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesColors;
import net.minecraft.util.StatCollector;

/**
 * One of a page's options, as both places show it: a row of the page's
 * options (the three dots on its tab, a right-click on it) and a button
 * on its window's tool strip. One option, one record, so the two never
 * disagree. Four kinds:
 *
 * <ul>
 * <li>an <em>action</em>, done at once: Mark as Read;</li>
 * <li>a <em>switch</em>, on or off: a kind of marker the map shows; off,
 * its button is struck through;</li>
 * <li>a <em>choice</em>, one of a group: the journal's filter, a party
 * colour; the one chosen rests lit;</li>
 * <li>a <em>cycle</em> of a few words, a click on and a right-click back:
 * a conversation's Notifications; its glyph says where it stands.</li>
 * </ul>
 *
 * <p>An option that cannot be taken stays in both places, greyed, and
 * says why. Options of one group stand together, a hairline between
 * groups in the menu, a gap on the strip; a group may carry a heading in
 * the menu.</p>
 */
public final class PageOption {
    /** What a press on the option does. */
    public enum Kind { ACTION, SWITCH, CHOICE, CYCLE }

    public final String id;
    public final String label;
    public final Kind kind;
    /** A switch's state, a choice chosen, or a cycle that rests lit. */
    public final boolean on;
    /** What a cycle reads now; empty for every other kind. */
    public final String value;
    public final OptionGlyph glyph;
    /** Why the option cannot be taken now; empty while it can. */
    private String unavailable = "";
    /** The group it stands in; options of one group stand together. */
    private String group = "";
    /** The group's heading in the menu, as a lang key; empty for none. */
    private String headingKey = "";

    private PageOption(String id, String label, Kind kind, boolean on,
                       String value, OptionGlyph glyph) {
        this.id = id;
        this.label = label == null ? "" : label;
        this.kind = kind;
        this.on = on;
        this.value = value == null ? "" : value;
        this.glyph = glyph;
    }

    /** Done at once: Mark as Read, Jump to First Unread. */
    public static PageOption action(String id, String label, OptionGlyph glyph) {
        return new PageOption(id, label, Kind.ACTION, false, "", glyph);
    }

    /** On or off; off, its button is struck through. */
    public static PageOption toggle(String id, String label, boolean on,
                                    OptionGlyph glyph) {
        return new PageOption(id, label, Kind.SWITCH, on, "",
                on ? glyph : glyph.struck());
    }

    /** One of a group; the one chosen rests lit. */
    public static PageOption choice(String id, String label, boolean chosen,
                                    OptionGlyph glyph) {
        return new PageOption(id, label, Kind.CHOICE, chosen, "", glyph);
    }

    /**
     * A few words, stepped on by a click and back by a right-click;
     * {@code glyph} says where it stands, resting lit where {@code lit}.
     */
    public static PageOption cycle(String id, String label, String value,
                                   boolean lit, OptionGlyph glyph) {
        return new PageOption(id, label, Kind.CYCLE, lit, value, glyph);
    }

    /** The same option, greyed, saying {@code reason}; an empty reason leaves it as it is. */
    public PageOption unavailable(String reason) {
        this.unavailable = reason == null ? "" : reason;
        return this;
    }

    /** The same option in {@code group}, which the menu heads with {@code headingKey} (empty for none). */
    public PageOption inGroup(String group, String headingKey) {
        this.group = group == null ? "" : group;
        this.headingKey = headingKey == null ? "" : headingKey;
        return this;
    }

    public String unavailable() {
        return this.unavailable;
    }

    public boolean isAvailable() {
        return this.unavailable.length() == 0;
    }

    public String group() {
        return this.group;
    }

    String headingKey() {
        return this.headingKey;
    }

    /**
     * What its button says under the pointer: why it cannot be taken, or
     * its name; a switch with On or Off, a cycle with the word it reads.
     */
    public String tip() {
        if (!isAvailable()) {
            return this.unavailable;
        }
        switch (this.kind) {
            case SWITCH:
                return StatCollector.translateToLocalFormatted(
                        "gui.losttales.window.option.state", this.label,
                        StatCollector.translateToLocal(this.on
                                ? "gui.losttales.window.settings.on"
                                : "gui.losttales.window.settings.off"));
            case CYCLE:
                return StatCollector.translateToLocalFormatted(
                        "gui.losttales.window.option.state", this.label,
                        this.value);
            default:
                return this.label;
        }
    }

    /**
     * Its row in the page's options: its glyph before its name, a switch
     * or a choice marked in honey while it is on, a cycle's word at the
     * row's end.
     */
    MenuWindow.Entry row() {
        boolean marked = (this.kind == Kind.SWITCH || this.kind == Kind.CHOICE)
                && this.on;
        MenuWindow.Entry entry = new MenuWindow.Entry(this.id, this.label,
                false, marked ? LostTalesColors.rgb(LostTalesColors.HONEY) : -1,
                null).withPicture(this.glyph.asPicture());
        if (this.kind == Kind.CYCLE) {
            entry.withValue(this.value);
        }
        return entry.unavailable(this.unavailable);
    }
}
