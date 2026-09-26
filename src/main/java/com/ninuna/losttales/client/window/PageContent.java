package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * What a page holds: the quest journal, the party, the map. A page is a
 * tab a window holds beside its other tabs; while it is the tab in front
 * the window shows the page between its tool strip and its input bar
 * instead of a conversation's lines and member list. The window owns the
 * frame, the row, the strip, the bar, the surface and the place; the page
 * draws itself in the box it is given, says what stands on the bar
 * ({@link #barItems}), and answers the pointer and the keys.
 *
 * <p>The strip is the same as over a conversation, with the page's own
 * parts in it: the button of the page's panel at its left end, where a
 * conversation's timestamp area button stands; the cog, which opens the
 * tab's menu with the page's {@link #choices} in it; and the search well,
 * whose words the page is handed ({@link #search}) and whose count it
 * gives ({@link #found}). A page has no member list, so its strip has no
 * member list button.</p>
 *
 * <p>Every box handed in is in whole GUI pixels, the window having moved
 * the matrix by whatever fraction of a pixel it stands on; the pointer is
 * in the same space, or NaN while it is not on the page.</p>
 */
public abstract class PageContent {
    /**
     * One row a page puts in its tab's menu; the chosen one is marked, and
     * one that cannot be taken now is greyed and says why.
     */
    public static final class Choice {
        public final String id;
        public final String label;
        public final boolean chosen;
        /** Why the row cannot be taken now; empty while it can. */
        public final String unavailable;

        public Choice(String id, String label, boolean chosen) {
            this(id, label, chosen, "");
        }

        public Choice(String id, String label, boolean chosen,
                      String unavailable) {
            this.id = id;
            this.label = label == null ? "" : label;
            this.chosen = chosen;
            this.unavailable = unavailable == null ? "" : unavailable;
        }
    }

    /**
     * The colour the page's tab wears, as a channel's tab wears its
     * channel's: its accent, its glow and its lit name. Ivory for a page
     * with no tone of its own.
     */
    public int tone() {
        return LostTalesUiInk.IVORY;
    }

    /** The page's panel button, or null for a page with no panel. */
    public ToolStrip.Panel panel() {
        return null;
    }

    /** Whether the panel is out; its button rests lit while it is. */
    public boolean isPanelOut() {
        return false;
    }

    /** The panel's button was pressed. */
    public void togglePanel() {}

    /** The heading over the page's rows in its tab's menu, as a lang key; empty for none. */
    public String choicesHeading() {
        return "";
    }

    /** The rows the page puts in its tab's menu; empty for none. */
    public List<Choice> choices() {
        return Collections.emptyList();
    }

    /** One of its {@link #choices} was taken; the menu stays open. */
    public void choose(String id) {}

    /** What the well says while nothing is typed in it: {@code Search active quests}. */
    public String searchPrompt() {
        return "";
    }

    /** The words in the well, handed over every frame; empty while the search is closed. */
    public void search(String words) {}

    /** How many entries the words found, shown in the well; -1 while nothing is typed. */
    public int found() {
        return -1;
    }

    /**
     * A key typed while the well holds the keys, before the field sees
     * it: the arrows walk what was found, and Return reads it. Answers
     * whether the page took it; after Return the keys go to the page.
     */
    public boolean searchKey(int keyCode) {
        return false;
    }

    /**
     * What stands on the page's input bar, left to right, made afresh each
     * frame: the page's actions, its field, its quiet words and glyphs.
     * An action that cannot be taken now is there, greyed, saying why.
     * Empty for a bare bar.
     */
    public List<BarItem> barItems() {
        return Collections.emptyList();
    }

    /**
     * An item of the bar was pressed: a button or a glyph, the field's well
     * ({@code offer} -1), or a row of the field's list ({@code offer} its
     * index). Greyed items are never pressed.
     */
    public void barPressed(String id, int offer) {}

    /**
     * Draws the page in {@code box} at {@code alpha} (0-255), on the
     * window's own surface. {@code clipX}/{@code clipY} is where the box's
     * whole pixels really stand on the screen, for a scissor.
     */
    public abstract void draw(Minecraft minecraft, LostTalesUiHitBox box,
                              double clipX, double clipY, double pointerX,
                              double pointerY, float partialTicks, int alpha);

    /** Whether a press at the point would do something: the hand shows there. */
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        return false;
    }

    /** The words beside the pointer for what it rests on; empty for none. */
    public String tipAt(LostTalesUiHitBox box, double x, double y) {
        return "";
    }

    /** A press on the page; answers whether the page took it. */
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box,
                                double x, double y, int button) {
        return false;
    }

    /** A wheel turn over the page, in lines, positive toward later rows; answers whether it scrolled. */
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        return false;
    }

    /**
     * A key while the page is the one in front: the keys its own controls
     * answer to. Answers whether the page took it.
     */
    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    /** Once a game tick, while the page is open: a list that follows the world. */
    public void tick() {}

    /** Whether the page can be shown now; one that cannot waits in its window unseen. */
    public boolean isAvailable() {
        return true;
    }

    /* ---- The quick switcher ---- */

    /** The heading over what the quick switcher finds of the page, as a lang key; empty for a page it finds nothing in. */
    public String findHeading() {
        return "";
    }

    /**
     * What the quick switcher finds of the page by {@code words}, which
     * are never empty: a quest, a character, each row's id the page's own.
     */
    public List<MenuWindow.Entry> find(String words) {
        return Collections.emptyList();
    }

    /** One of the rows the page {@link #find found} was chosen: the page, come forward, shows it. */
    public void show(String id) {}

    /**
     * Whether the page is drawn with depth testing on, as a screen of its
     * own would be; the windows round it never are.
     */
    public boolean wantsDepthTest() {
        return false;
    }

    /** The pointer moving with a button held that went down on the page. */
    public void mouseDragged(Minecraft minecraft, LostTalesUiHitBox box,
                             double x, double y, int button) {}

    /** A button that went down on the page coming up, wherever the pointer is. */
    public void mouseReleased(Minecraft minecraft, LostTalesUiHitBox box,
                              double x, double y, int button) {}

    /** The page took the keys, or let them go. */
    public void focusChanged(boolean hasKeys) {}

    /**
     * Whether the page keeps every key now, the pages' keys among them: a
     * field of its own is typed in, or a question waits for its answer.
     * Otherwise a page's key over it opens that page, or closes this one.
     */
    public boolean holdsKeys() {
        return false;
    }

    /** The page is on screen: its window's tab in front on an open screen. */
    public void shown() {}

    /** The page left the screen: behind another tab, its window or the screen closed. */
    public void hidden() {}
}
