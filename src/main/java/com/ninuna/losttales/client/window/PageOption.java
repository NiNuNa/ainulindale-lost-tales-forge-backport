package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.StatCollector;

/**
 * One of a page's options, as both places show it: a row of the page's
 * options (the three dots on its tab, a right-click on it or on its tool
 * strip) and a button on its window's tool strip. One option, one record,
 * so the two never disagree. Four kinds:
 *
 * <ul>
 * <li>an <em>action</em>, done at once: Mark as Read;</li>
 * <li>a <em>switch</em>, on or off: a kind of marker the map shows; off,
 * its button is struck through;</li>
 * <li>a <em>choice</em>, one of a group: the journal's filter, a fellowship
 * colour; the one chosen rests lit;</li>
 * <li>a <em>pick</em> of a few words, each a choice of its own, in a
 * sub-window the option opens: a conversation's Notification Settings;
 * its glyph says which word stands.</li>
 * </ul>
 *
 * <p>An option that cannot be taken stays in both places, greyed, and
 * says why. Options of one group stand together, a hairline between
 * groups in the menu and on the strip; a group may carry a heading in
 * the menu.</p>
 */
public final class PageOption {
    /** What a press on the option does. */
    public enum Kind { ACTION, SWITCH, CHOICE, PICK }

    public final String id;
    public final String label;
    public final Kind kind;
    /** A switch's state, or a choice chosen. */
    public final boolean on;
    /** The word a pick reads now; empty for every other kind. */
    public final String value;
    public final OptionGlyph glyph;
    /** A pick's words, each a choice taken by its own id; empty for every other kind. */
    private List<PageOption> choices = Collections.emptyList();
    /** What a choice says it does under the pointer, in its pick's sub-window; empty for nothing. */
    private String tip = "";
    /** Why the option cannot be taken now; empty while it can. */
    private String unavailable = "";
    /** The group it stands in; options of one group stand together. */
    private String group = "";
    /** The group's heading in the menu, as a lang key; empty for none. */
    private String headingKey = "";
    /** The settings a pick's sub-window shows under its words; null for none. */
    private Settings.Place settings;

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
     * A few words to pick from, in a sub-window the option opens; its
     * {@code glyph} says which word stands, {@code value}. Each of
     * {@code choices} is a word, taken by its own id.
     */
    public static PageOption pick(String id, String label, String value,
                                  OptionGlyph glyph, List<PageOption> choices) {
        PageOption option = new PageOption(id, label, Kind.PICK, false, value,
                glyph);
        option.choices = Collections.unmodifiableList(
                new ArrayList<PageOption>(choices));
        return option;
    }

    /** The same choice saying {@code words} under the pointer in its pick's sub-window. */
    public PageOption explained(String words) {
        this.tip = words == null ? "" : words;
        return this;
    }

    /** A pick's words; empty for every other kind. */
    public List<PageOption> choices() {
        return this.choices;
    }

    /**
     * The same pick showing the settings of {@code place} under its words,
     * a hairline between: a conversation's Chat Feed Settings holds the
     * feed's own under the three words that say what of it reaches the
     * feed.
     */
    public PageOption withSettings(Settings.Place place) {
        this.settings = place;
        return this;
    }

    /** The settings a pick's sub-window shows under its words; null for none. */
    Settings.Place settings() {
        return this.settings;
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
     * its name; a switch with On or Off. A pick says its name alone: its
     * glyph wears the word it reads, and its sub-window names them all.
     */
    public String tip() {
        if (!isAvailable()) {
            return this.unavailable;
        }
        if (this.kind == Kind.SWITCH) {
            return StatCollector.translateToLocalFormatted(
                    "gui.losttales.window.option.state", this.label,
                    StatCollector.translateToLocal(this.on
                            ? "gui.losttales.window.settings.on"
                            : "gui.losttales.window.settings.off"));
        }
        return this.label;
    }

    /**
     * Its row in the page's options, or in its pick's sub-window: its
     * glyph before its name, a switch or a choice marked in honey while it
     * is on, a pick's word at the row's end, a choice's tip under the
     * pointer.
     */
    MenuWindow.Entry row() {
        boolean marked = (this.kind == Kind.SWITCH || this.kind == Kind.CHOICE)
                && this.on;
        MenuWindow.Entry entry = new MenuWindow.Entry(this.id, this.label,
                false, marked ? LostTalesColors.rgb(LostTalesColors.HONEY) : -1,
                null).withPicture(this.glyph.asPicture());
        if (this.kind == Kind.PICK) {
            entry.withValue(this.value);
        }
        return entry.withTip(this.tip).unavailable(this.unavailable);
    }
}
