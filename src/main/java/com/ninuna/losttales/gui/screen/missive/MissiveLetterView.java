package com.ninuna.losttales.gui.screen.missive;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.item.LostTalesItemMissiveLetter;
import com.ninuna.losttales.quest.LostTalesQuestTimeText;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.MissiveWords;
import com.ninuna.losttales.quest.missive.LostTalesMissiveObjectiveData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

/**
 * A missive's letter as both missive pages show it — the board's picked
 * notice and a letter in hand — on a sheet edged in the parchment's own
 * tan: its title in parchment, who issued it, its words, the work it
 * asks under a heading, the reward in meadow green and the time limit in
 * salmon. The sheet scrolls where the letter is longer than it, gliding
 * as the journal's columns do, with a scrollbar only then.
 */
final class MissiveLetterView {
    /** Clear pixels between the sheet's edge and the words. */
    static final int PADDING = 6;
    /** One line of the letter. */
    static final int LINE_HEIGHT = 10;
    /** A heading's line, a little taller than a line of words. */
    static final int HEADING_HEIGHT = 14;
    /** The clear space between the letter's parts. */
    static final int GAP = 5;
    /** How far a line of an objective stands in from its dash. */
    private static final String DASH = "- ";

    /** What a line of the letter is, for how it is drawn. */
    private enum Kind { TITLE, ASIDE, TEXT, HEADING, REWARD, TIME, RULE, GAP }

    private static final class Line {
        final Kind kind;
        final String text;
        final int indent;

        Line(Kind kind, String text, int indent) {
            this.kind = kind;
            this.text = text;
            this.indent = indent;
        }

        int height() {
            return this.kind == Kind.HEADING ? HEADING_HEIGHT
                    : this.kind == Kind.GAP || this.kind == Kind.RULE ? GAP
                    : LINE_HEIGHT;
        }
    }

    private List<Line> lines = Collections.emptyList();
    private LostTalesMissiveData builtFor;
    private String builtEmpty;
    private int builtWidth = -1;
    private int contentHeight;
    private int scroll;
    private double shownScroll;
    private long glideNanos;

    /** Back at the top: the view turned to another letter. */
    void toTop() {
        this.scroll = 0;
        this.shownScroll = 0.0D;
    }

    /**
     * Draws the sheet in {@code box} at {@code alpha}, with
     * {@code missive}'s letter on it, or {@code empty}'s words where there
     * is no letter to show.
     */
    void draw(Minecraft minecraft, LostTalesUiHitBox box,
              LostTalesMissiveData missive, String empty, int alpha) {
        if (box.width <= PADDING * 2 + 2 || box.height <= PADDING * 2 + 2) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        LostTalesUiHitBox inner = inner(box);
        build(font, missive, empty, (int)inner.width);
        long now = System.nanoTime();
        double seconds = this.glideNanos == 0L ? 0.0D
                : Math.max(0.0D, (now - this.glideNanos) / 1.0E9D);
        this.glideNanos = now;
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll(inner)));
        this.shownScroll = Motions.followTravel(MotionIds.WINDOW_SCROLL,
                this.shownScroll, this.scroll, seconds);
        drawEdge(box, alpha);
        boolean clipped = LostTalesUiClip.beginLocal(minecraft,
                (float)inner.left, (float)inner.top, (float)inner.right(),
                (float)inner.bottom());
        GL11.glPushMatrix();
        try {
            // Whole rows pick where the letter starts; the fraction slides it.
            int whole = (int)Math.floor(this.shownScroll);
            GL11.glTranslatef(0.0F, (float)(whole - this.shownScroll), 0.0F);
            int y = (int)inner.top - whole;
            for (Line line : this.lines) {
                int height = line.height();
                if (y + height >= inner.top - 1 && y <= inner.bottom() + 1) {
                    drawLine(font, line, (int)inner.left, (int)inner.right(),
                            y, alpha);
                }
                y += height;
            }
        } finally {
            GL11.glPopMatrix();
            LostTalesUiClip.end(clipped);
        }
        drawScrollbar(box, inner, alpha);
    }

    /** The wheel over the sheet scrolls it; answers whether there was anything to scroll. */
    boolean scroll(LostTalesUiHitBox box, int lines) {
        LostTalesUiHitBox inner = inner(box);
        if (lines == 0 || maxScroll(inner) <= 0) {
            return false;
        }
        this.scroll = Math.max(0, Math.min(maxScroll(inner),
                this.scroll + lines * LINE_HEIGHT));
        return true;
    }

    /** A page key: the sheet's height less a line, up or down. */
    void page(LostTalesUiHitBox box, int direction) {
        LostTalesUiHitBox inner = inner(box);
        int page = Math.max(LINE_HEIGHT, (int)inner.height - LINE_HEIGHT);
        this.scroll = Math.max(0, Math.min(maxScroll(inner),
                this.scroll + (direction < 0 ? -page : page)));
    }

    private static LostTalesUiHitBox inner(LostTalesUiHitBox box) {
        return new LostTalesUiHitBox(Math.floor(box.left) + PADDING,
                Math.floor(box.top) + PADDING,
                Math.max(0.0D, Math.floor(box.width) - PADDING * 2
                        - WindowLists.SCROLLBAR_ROOM),
                Math.max(0.0D, Math.floor(box.height) - PADDING * 2));
    }

    private int maxScroll(LostTalesUiHitBox inner) {
        return Math.max(0, this.contentHeight - (int)inner.height);
    }

    /* ---- The lines ---- */

    /**
     * Lays the letter out in lines as wide as the sheet, again only when
     * the letter or the width changed. Another letter reads from its top;
     * the same letter sent again keeps its place.
     */
    private void build(FontRenderer font, LostTalesMissiveData missive,
                       String empty, int width) {
        if (missive == this.builtFor && width == this.builtWidth
                && (missive != null || String.valueOf(empty).equals(this.builtEmpty))) {
            return;
        }
        boolean sameLetter = missive != null && this.builtFor != null
                && missive.getQuestId().equals(this.builtFor.getQuestId());
        if (!sameLetter && missive != this.builtFor) {
            toTop();
        }
        this.builtFor = missive;
        this.builtEmpty = String.valueOf(empty);
        this.builtWidth = width;
        List<Line> built = new ArrayList<Line>();
        if (missive == null) {
            wrap(font, built, Kind.ASIDE, empty, width, 0);
        } else {
            addLetter(font, built, missive, width);
        }
        this.lines = built;
        int height = 0;
        for (Line line : built) {
            height += line.height();
        }
        this.contentHeight = height;
    }

    private static void addLetter(FontRenderer font, List<Line> lines,
                                  LostTalesMissiveData missive, int width) {
        wrap(font, lines, Kind.TITLE, MissiveWords.title(missive), width, 0);
        String issuer = MissiveWords.issuer(missive);
        if (issuer.length() > 0) {
            wrap(font, lines, Kind.ASIDE, StatCollector.translateToLocalFormatted(
                    "gui.losttales.missive_letter.issuer", issuer),
                    width, 0);
        }
        lines.add(new Line(Kind.RULE, "", 0));
        String body = MissiveWords.journalLine(missive);
        if (body.length() > 0) {
            wrap(font, lines, Kind.TEXT, body, width, 0);
            lines.add(new Line(Kind.GAP, "", 0));
        }
        if (!missive.getObjectives().isEmpty()) {
            lines.add(new Line(Kind.HEADING, StatCollector.translateToLocal(
                    "gui.losttales.missive_letter.work"), 0));
            int indent = font.getStringWidth(DASH);
            for (LostTalesMissiveObjectiveData objective : missive.getObjectives()) {
                List<String> wrapped = split(font,
                        MissiveWords.objective(objective),
                        Math.max(1, width - indent));
                for (int index = 0; index < wrapped.size(); index++) {
                    lines.add(new Line(Kind.TEXT, index == 0
                            ? DASH + wrapped.get(index) : wrapped.get(index),
                            index == 0 ? 0 : indent));
                }
            }
            lines.add(new Line(Kind.GAP, "", 0));
        }
        String reward = LostTalesItemMissiveLetter.buildRewardSummary(missive);
        if (reward.length() > 0) {
            wrap(font, lines, Kind.REWARD, StatCollector.translateToLocalFormatted(
                    "gui.losttales.missive_letter.reward", reward), width, 0);
        }
        if (missive.hasTimeLimit()) {
            wrap(font, lines, Kind.TIME, StatCollector.translateToLocalFormatted(
                    "gui.losttales.missive_letter.time_limit",
                    LostTalesQuestTimeText.shortForm(missive.getTimeLimitTicks())),
                    width, 0);
        }
    }

    private static void wrap(FontRenderer font, List<Line> lines, Kind kind,
                             String text, int width, int indent) {
        for (String line : split(font, text, Math.max(1, width - indent))) {
            lines.add(new Line(kind, line, indent));
        }
    }

    private static List<String> split(FontRenderer font, String text,
                                      int width) {
        List<String> lines = new ArrayList<String>();
        if (text == null || text.length() == 0) {
            return lines;
        }
        for (Object line : font.listFormattedStringToWidth(text, width)) {
            lines.add(String.valueOf(line));
        }
        return lines;
    }

    /* ---- Drawing ---- */

    private static void drawLine(FontRenderer font, Line line, int left,
                                 int right, int y, int alpha) {
        int textTop = y + LostTalesUiInk.centredStart(line.height(),
                LostTalesUiInk.CAP_HEIGHT);
        switch (line.kind) {
            case RULE:
                LostTalesUiInk.fillRect(left, y + GAP / 2, right,
                        y + GAP / 2 + 1, faded(LostTalesColors.BORDER_DIM, alpha));
                return;
            case GAP:
                return;
            case HEADING:
                drawHeading(font, line.text, left, right, textTop, alpha);
                return;
            default:
                LostTalesUiInk.drawText(font, line.text, left + line.indent,
                        textTop, rgbOf(line.kind), alpha);
        }
    }

    /** A heading: its words in capitals, and a rule filling what they leave, as the Motion Lab's. */
    private static void drawHeading(FontRenderer font, String words, int left,
                                    int right, int textTop, int alpha) {
        String name = LostTalesSkyrimUiStyle.uppercase(words);
        LostTalesUiInk.drawText(font, LostTalesSkyrimUiStyle.trimToWidth(font,
                        name, Math.max(0, right - left)), left, textTop,
                LostTalesColors.rgb(LostTalesColors.TEXT), alpha);
        int ruleLeft = left + font.getStringWidth(name) + 5;
        if (ruleLeft < right) {
            LostTalesUiInk.fillRect(ruleLeft, textTop + 3, right, textTop + 4,
                    faded(LostTalesColors.BORDER_DIM, alpha));
        }
    }

    private static int rgbOf(Kind kind) {
        switch (kind) {
            case TITLE:
                return LostTalesColors.rgb(LostTalesColors.PARCHMENT);
            case ASIDE:
                return WindowStyle.asideRgb();
            case REWARD:
                return LostTalesColors.rgb(LostTalesColors.MEADOW_GREEN);
            case TIME:
                return LostTalesColors.rgb(LostTalesColors.SALMON);
            default:
                return LostTalesColors.rgb(LostTalesColors.TEXT);
        }
    }

    /** The sheet's edge: a one-pixel ring in the parchment's tan, so the letter reads as a sheet on the page. */
    private static void drawEdge(LostTalesUiHitBox box, int alpha) {
        float left = (float)Math.floor(box.left);
        float top = (float)Math.floor(box.top);
        float right = left + (float)Math.floor(box.width);
        float bottom = top + (float)Math.floor(box.height);
        int edge = faded(LostTalesColors.withAlpha(LostTalesColors.TAN, 0xC0),
                alpha);
        LostTalesUiInk.fillRect(left, top, right, top + 1, edge);
        LostTalesUiInk.fillRect(left, bottom - 1, right, bottom, edge);
        LostTalesUiInk.fillRect(left, top + 1, left + 1, bottom - 1, edge);
        LostTalesUiInk.fillRect(right - 1, top + 1, right, bottom - 1, edge);
    }

    /** Where more of the letter waits, as every list in the windows says it, inside the sheet's right edge. */
    private void drawScrollbar(LostTalesUiHitBox box, LostTalesUiHitBox inner,
                               int alpha) {
        // The fades hang from just inside the sheet's own edge.
        double top = Math.floor(box.top);
        WindowLists.drawScroll(Math.floor(box.left) + 1, top + 1,
                Math.floor(box.left) + Math.floor(box.width) - 1,
                top + Math.floor(box.height) - 1, inner.top, inner.bottom(),
                this.shownScroll, maxScroll(inner), alpha);
    }

    /** A colour of the palette at its own opacity times the page's. */
    static int faded(int argb, int alpha) {
        return LostTalesUiInk.argb(LostTalesColors.rgb(argb), Math.round(
                (argb >>> 24) * Math.max(0, Math.min(255, alpha)) / 255.0F));
    }
}
