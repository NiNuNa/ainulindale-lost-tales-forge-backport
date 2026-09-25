package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * What a page holds: the quest journal, the party. A page is a tab a chat
 * window holds beside its conversations; while it is the tab in front the
 * window shows the page under its tab row and tool strip instead of a
 * conversation's lines, member list and input bar. The window owns the
 * frame, the row, the strip, the surface and the place; the page draws
 * itself in the box it is given and answers the pointer and the keys
 * there.
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
public abstract class ChatPageContent {
    /** The button of a page's panel at the strip's left end: its glyph, lit artwork and tips. */
    public static final class Panel {
        final LostTalesUiSheet glyph;
        final LostTalesUiSheet litGlyph;
        final String showKey;
        final String hideKey;

        public Panel(LostTalesUiSheet glyph, LostTalesUiSheet litGlyph,
                     String showKey, String hideKey) {
            this.glyph = glyph;
            this.litGlyph = litGlyph;
            this.showKey = showKey;
            this.hideKey = hideKey;
        }
    }

    /** One row a page puts in its tab's menu; the chosen one is marked. */
    public static final class Choice {
        final String id;
        final String label;
        final boolean chosen;

        public Choice(String id, String label, boolean chosen) {
            this.id = id;
            this.label = label == null ? "" : label;
            this.chosen = chosen;
        }
    }

    /**
     * The colour the page's tab wears, as a channel's tab wears its
     * channel's: its accent, its glow and its lit name. Ivory for a page
     * with no tone of its own.
     */
    public int tone() {
        return LostTalesChatVisualStyle.IVORY;
    }

    /** The page's panel button, or null for a page with no panel. */
    public Panel panel() {
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
}
