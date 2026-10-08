package com.ninuna.losttales.client.window;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

/**
 * One thing a window holds: a conversation or a page.
 * A window keeps its tabs in a row and shows the one in front; the tab
 * says how it looks in that row, its words, its colour, its icon and
 * what waits in the icon's corner, and each kind of tab lives with the
 * system it belongs to. Tabs are values: two tabs with the same id are
 * the same tab.
 *
 * <p>A page may stand open more than once, each copy a tab of its own
 * with its own view: its {@link #instance}, 1 for the first. What the
 * page is (a channel's lines, a setting) the copies share.</p>
 */
public abstract class WindowPage {
    /** Reads a tab back from the id the layout file keeps it by. */
    public interface Reader {
        /** The tab {@code id} names; null for an id this reader does not know. */
        WindowPage read(String id);
    }

    /** Between a page's id and its copy's number in a tab's id: {@code page:map#2}. */
    public static final String INSTANCE_MARK = "#";
    /** The most copies of one page; more than anyone opens. */
    public static final int MAX_INSTANCE = 99;

    private static final List<Reader> READERS = new CopyOnWriteArrayList<Reader>();

    /** Adds a kind of tab the layout file can name. */
    public static void addReader(Reader reader) {
        if (reader != null && !READERS.contains(reader)) {
            READERS.add(reader);
        }
    }

    /**
     * The tab an id names, asked of every kind in turn, its copy's number
     * read off its end; null for an unknown one.
     */
    public static WindowPage fromId(String id) {
        if (id == null) {
            return null;
        }
        int instance = instanceIn(id);
        for (Reader reader : READERS) {
            WindowPage tab = reader.read(pageIdIn(id));
            if (tab != null) {
                return instance == 1 ? tab : tab.withInstance(instance);
            }
        }
        return null;
    }

    /** Which copy a tab's id names: the number after its mark, else 1. */
    public static int instanceIn(String id) {
        int mark = id == null ? -1 : id.lastIndexOf(INSTANCE_MARK);
        int number = mark > 0
                ? instanceNumber(id.substring(mark + INSTANCE_MARK.length())) : -1;
        return number > 1 ? number : 1;
    }

    /** A tab's id without its copy's number: the page's own id. */
    public static String pageIdIn(String id) {
        return instanceIn(id) == 1 ? id
                : id.substring(0, id.lastIndexOf(INSTANCE_MARK));
    }

    /** A copy's number as written in a tab's id; -1 for anything else. */
    private static int instanceNumber(String digits) {
        if (digits.length() == 0 || digits.length() > 2) {
            return -1;
        }
        for (int index = 0; index < digits.length(); index++) {
            if (!Character.isDigit(digits.charAt(index))) {
                return -1;
            }
        }
        int number = Integer.parseInt(digits);
        return number >= 1 && number <= MAX_INSTANCE ? number : -1;
    }

    /** A page's id with its copy's number: the first copy's is the page's own. */
    protected static String instanceId(String pageId, int instance) {
        return instance <= 1 ? pageId : pageId + INSTANCE_MARK + instance;
    }

    /** The id the layout file keeps the tab by: {@code global}, {@code page:journal}, {@code page:map#2}. */
    public abstract String id();

    /** Which copy of its page the tab is: 1 for the first, which is the page itself. */
    public int instance() {
        return 1;
    }

    /**
     * Copy {@code instance} of the same page; null for a page that opens
     * once, which has only its first.
     */
    public WindowPage withInstance(int instance) {
        return instance == 1 ? this : null;
    }

    /** Whether the page may stand open more than once. */
    public boolean opensMoreThanOnce() {
        return false;
    }

    /** The page's first copy: the page itself, whichever copy this is. */
    public final WindowPage firstInstance() {
        WindowPage first = withInstance(1);
        return first == null ? this : first;
    }

    /**
     * The copy is opening anew, where no window holds it: whatever an
     * earlier copy of its number kept is forgotten, as the page decides.
     */
    public void forgetCopy() {}

    /** The copy was opened as a duplicate of {@code source}: it carries on what the page says a duplicate keeps. */
    public void duplicatedFrom(WindowPage source) {}

    /** Whether {@code other} is a copy of the same page, this one included. */
    public final boolean isCopyOf(WindowPage other) {
        return other != null && firstInstance().equals(other.firstInstance());
    }

    /** The words on the tab. */
    public abstract String title();

    /** Whether a search finds the page by what is typed: its title holds it. */
    public boolean answers(String filter) {
        return WindowMenus.matchesFilter(title(), filter);
    }

    /** The colour the tab wears: its accent, its glow and its lit words. */
    public abstract int tone();

    /** Whether the tab shows an icon before its words. */
    public boolean hasIcon() {
        return true;
    }

    /**
     * Draws the icon with its top-left at the point, {@link TabIcons#SIZE}
     * square, wearing {@code mark} in its corner.
     */
    public abstract void drawIcon(Minecraft minecraft, float x, float y,
                                  int alpha, TabMark mark);

    /** What waits in the icon's corner. */
    public TabMark mark() {
        return TabMark.NONE;
    }

    /** Whether the tab holds words not sent yet: the row shows its draft mark. */
    public boolean hasDraft() {
        return false;
    }

    /** Whether the tab's news is kept out of the way: the row writes its name in italics. */
    public boolean isMuted() {
        return false;
    }

    /** Whether nothing typed in the tab can be sent: its bar's tab button names it in italics. */
    public boolean isReadOnly() {
        return false;
    }

    /**
     * Whether the layout file keeps the tab where it stands. A
     * conversation that ends with the session is left out of it.
     */
    public boolean isKeptInLayout() {
        return true;
    }

    /** Whether the tab can be shown now; one that cannot waits in its window unseen. */
    public boolean isAvailable() {
        return true;
    }

    /**
     * What kind of page it is: where the {@code +} lists it, which key's
     * view shows it, and which windows it opens in.
     */
    public abstract PageCategory category();

    /* ---- What the window's tool strip offers while the tab is in front ---- */

    /**
     * The tab's own options: the rows of its options menu (the three dots
     * on its tab, a right-click on it) and the buttons of its window's
     * tool strip alike ({@link PageOption}).
     */
    public List<PageOption> options() {
        return Collections.emptyList();
    }

    /**
     * The settings of the tab's kind (Chat, Quest, Map, Motion Settings),
     * which the tool strip's cog opens and the tab's options offer after
     * its own rows; every conversation shares Chat Settings. Null for a
     * tab with none.
     */
    public Settings.Place settingsPlace() {
        return null;
    }

    /**
     * One of its options taken, on its row or its button, or one of a
     * pick's words in the pick's sub-window. Answers whether the menu
     * stays open.
     */
    public boolean takeOption(String id) {
        return false;
    }

    /**
     * What the tab's help says, behind the question mark at the end of
     * the tool strip and under F1: its guide and its own keys.
     */
    public PageHelp help() {
        return new PageHelp(null, null);
    }

    /** The panel button at the strip's left end; null for none. */
    public ToolStrip.Panel panel() {
        return null;
    }

    /**
     * Whether the page's panel is out. Each page keeps its own, wherever
     * it stands: the page beside it in a split keeps another.
     */
    public boolean isPanelOut() {
        return false;
    }

    /** Drives the page's panel out, or back in. */
    public void togglePanel() {}

    /** Whether the strip offers the member list's button. */
    public boolean hasMemberList() {
        return false;
    }

    /** Whether the page's member list is out; each page keeps its own. */
    public boolean isMemberListOut() {
        return false;
    }

    /** Puts the page's member list away, or brings it out. */
    public void toggleMemberList() {}

    /**
     * The mark the strip's inbox button wears beside it, counting what
     * waits for the player; null for a page whose strip has no inbox
     * button.
     */
    public TabMark inboxMark() {
        return null;
    }

    /** Opens the inbox the strip's button stands for. */
    public void openInbox() {}

    /**
     * Puts back what the page lays out as it first was — its panel, its
     * member list and that list's width — as its window's layout is reset.
     */
    public void resetView() {}

    /** What the well says while nothing is typed in it. */
    public String searchPrompt() {
        return "";
    }

    /**
     * Why the well has nothing to search while the tab is in front, for
     * its tip; empty while it searches. A well with nothing to do stays,
     * greyed.
     */
    public String searchUnavailable() {
        return "";
    }

    /**
     * Whether a standing search is walked, match by match, with the
     * chevrons; else the well only counts what it found.
     */
    public boolean walksSearch() {
        return false;
    }

    /** How many things a standing search found. */
    public int searchFound() {
        return 0;
    }

    /** The count a standing search shows in the well. */
    public String searchCount() {
        return String.valueOf(Math.max(0, searchFound()));
    }

    /**
     * What the options button says under the pointer, and what the menu
     * it opens is called: {@code Global Chat Options}, {@code Map Options}.
     */
    public final String optionsTitle() {
        return StatCollector.translateToLocalFormatted(
                "gui.losttales.window.page.options", title());
    }

    @Override
    public String toString() {
        return id();
    }
}
