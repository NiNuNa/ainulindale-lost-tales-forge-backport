package com.ninuna.losttales.client.motion;

import com.ninuna.losttales.client.window.PageKeys;
import com.ninuna.losttales.client.window.BarItem;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.PageAnswer;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageSearch;
import com.ninuna.losttales.client.window.OtherPage;
import com.ninuna.losttales.client.window.ScreenPart;
import com.ninuna.losttales.client.window.Settings;
import com.ninuna.losttales.client.window.TabIcons;
import com.ninuna.losttales.client.window.ToolStrip;
import com.ninuna.losttales.client.window.WheelStep;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WindowPage;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The Motion Lab, a page a window holds: every motion the
 * mod plays in a list under the heading of its family, and beside it the
 * one picked — what it is for, a sample playing it over and over, and a
 * stepper for every number, curve and choice its file writes. The
 * window holds the rest: the list's button at its tool strip's left, the
 * search in its well, which finds a motion by its id or by what it is
 * for, and Replay, Copy, Reset and Save on its input bar.
 *
 * <p>A step plays at once, everywhere the motion is used, and lives only
 * in the Lab until Save writes it into {@code config/losttales/client/motion}
 * over the mod's own file; closing the Lab's tab or the window screen
 * lets what was not saved go. In the list a motion tuned and not saved
 * wears the draft pen, and one the Lab's saved version plays reads in
 * the accent.</p>
 */
public final class MotionLabPage extends PageContent {
    /** The code name the page is registered and remembered under. */
    public static final String PAGE_ID = "motion_lab";

    /** The item the tab wears until the Lab has artwork of its own. */
    public static final ItemStack ICON = new ItemStack(Items.clock);

    /** Where the Lab's words live in the lang file. */
    private static final String LANG = "gui.losttales.motionlab.";

    /** The list's button at the strip's left end: an arrow on its way, until the Lab has a glyph of its own. */
    private static final ToolStrip.Panel LIST_PANEL = new ToolStrip.Panel(
            LostTalesUiSheet.FORWARD, LostTalesUiSheet.FORWARD_HOVER,
            LANG + "list.show", LANG + "list.hide");

    /** The bar's items: their ids, which the page is told when one is pressed. */
    private static final String REPLAY = "replay";
    private static final String COPY = "copy";
    private static final String RESET = "reset";
    private static final String SAVE = "save";
    /** The key that plays the sample from its start, as the tip names it. */
    private static final int REPLAY_KEY = Keyboard.KEY_SPACE;

    /** The glyphs of a stepper: back at its left, on at its right. */
    private static final LostTalesUiSheet LESS_GLYPH = LostTalesUiSheet.TOGGLE_5;
    private static final LostTalesUiSheet LESS_GLYPH_LIT =
            LostTalesUiSheet.TOGGLE_5_HOVER;
    private static final LostTalesUiSheet MORE_GLYPH = LostTalesUiSheet.TOGGLE_1;
    private static final LostTalesUiSheet MORE_GLYPH_LIT =
            LostTalesUiSheet.TOGGLE_1_HOVER;

    /** The part of the page a hover names: a list line, or a stepper's row, on a chevron, on the value or elsewhere on it. */
    private enum Part { LINE, LESS, VALUE, MORE, ROW }

    /** What the pointer is on: asked once a frame, read by every draw, asked again where a press lands. */
    private static final class Hover {
        final Part part;
        /** The motion of a list line. */
        final String id;
        /** The index of a stepper's row. */
        final int row;

        Hover(Part part, String id, int row) {
            this.part = part;
            this.id = id;
            this.row = row;
        }

        boolean isStepper(int row) {
            return this.part != Part.LINE && this.row == row;
        }

        boolean isLine(String id) {
            return this.part == Part.LINE && id.equals(this.id);
        }
    }

    /** The motions in force, as the list reads them. */
    private static final MotionLabList.Source MOTIONS =
            new MotionLabList.Source() {
                @Override
                public List<String> ids() {
                    return Motions.ids();
                }

                @Override
                public String family(String id) {
                    return Motions.family(id);
                }

                @Override
                public String about(String id) {
                    return aboutOf(id);
                }
            };

    /** Makes the screen's part that lets unsaved tuning go as the screen or the Lab's tab closes. */
    private static final ScreenPart.Maker CLOSER = new ScreenPart.Maker() {
        @Override
        public ScreenPart make(WindowScreen screen) {
            return new Closer(screen);
        }
    };

    private final Minecraft mc = Minecraft.getMinecraft();
    private final MotionLabSample sample = new MotionLabSample();
    private final MotionLabEditor editor;
    /** A motion each for the steppers' chevrons, by row and side. */
    private final Map<String, LostTalesUiButtonMotion> chevrons =
            new HashMap<String, LostTalesUiButtonMotion>();
    private FontRenderer font;
    private int width = -1;
    private int height = -1;
    private boolean listOut = true;
    /**
     * Whether the page was wide enough for both halves when last drawn;
     * null before it was. Crossing into a narrow page folds the list to
     * show the motion picked, and crossing back brings it out.
     */
    private Boolean wasWide;
    /** The motion picked; null before any is. */
    private String picked;
    /** The motion the rows were read from, so a motion changed elsewhere is read again. */
    private Motion source;
    /** A motion the quick switcher found, picked as the page next draws; null for none. */
    private String pendingShow;
    /** The words in the window's well; empty while its search is closed. */
    private String query = "";
    /** Where the list and the rows were scrolled to, and where they stand on screen, gliding there. */
    private int listScroll;
    private int rowsScroll;
    private double shownListScroll;
    private double shownRowsScroll;
    private long glideNanos;
    private double frameSeconds;
    private Hover hovered;

    public MotionLabPage() {
        this.editor = new MotionLabEditor(new MotionLabEditor.Words() {
            @Override
            public String get(String key) {
                return word(key);
            }
        }, new MotionLabEditor.Listener() {
            @Override
            public void changed() {
                preview();
            }
        });
    }

    /** Registers the part of the window screen that lets unsaved tuning go. */
    public static void install() {
        WindowScreen.addPart(CLOSER);
    }

    /** Plays every motion as its files say again: what was tuned and not saved goes. */
    static void dropPreviews() {
        Motions.clearPreviews();
    }

    private static String word(String key) {
        return StatCollector.translateToLocal(LANG + key);
    }

    private static String word(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(LANG + key, args);
    }

    /**
     * What a motion is for, in the game's language: its lang line, else
     * the words a motion file gives it.
     */
    static String aboutOf(String id) {
        String key = LANG + "about." + id;
        return StatCollector.canTranslate(key)
                ? StatCollector.translateToLocal(key) : Motions.get(id).about();
    }

    /** A family's heading: its lang words, or its code name where it has none. */
    private static String familyName(String family) {
        String key = LANG + "family." + family;
        return StatCollector.canTranslate(key)
                ? StatCollector.translateToLocal(key) : family;
    }

    /* ---- Picking and editing ---- */

    private List<MotionLabList.Line> lines() {
        return MotionLabList.of(Motions.FAMILIES, MOTIONS, this.query);
    }

    /** Picks a motion; the rows start from its top. */
    private void pick(String id) {
        if (id == null || id.equals(this.picked)) {
            return;
        }
        this.picked = id;
        this.rowsScroll = 0;
        this.shownRowsScroll = 0.0D;
        clearAnswer();
        read();
    }

    /** Reads the picked motion's rows from the motion in force, and plays its sample from the start. */
    private void read() {
        Motion motion = Motions.get(this.picked);
        this.source = motion;
        if (lastAnswer().kind() == PageAnswer.Kind.FAULT) {
            clearAnswer();
        }
        this.editor.edit(this.picked, MotionCodec.encode(motion));
        this.sample.restart(motion, System.nanoTime());
    }

    /**
     * Plays the edited motion in place of its file's, and its sample from
     * the start. What the edit cannot be read for stands over the bar
     * until it can.
     */
    private void preview() {
        MotionCodec.Result result = this.editor.read();
        Motion motion = result.motions().get(this.picked);
        if (result.problems().isEmpty()) {
            clearAnswer();
        } else {
            sayFault(result.problems().get(0));
        }
        if (motion != null) {
            Motions.preview(motion);
            this.source = motion;
        }
        this.sample.restart(Motions.get(this.picked), System.nanoTime());
    }

    /**
     * Brings the pick up to date before a frame: a motion the quick
     * switcher found, the first motion where none is picked or the picked
     * one is gone, and the rows read again where the motion in force is no
     * longer the one they were read from (a save, a reset, F3+T, or the
     * previews let go).
     */
    private void settle(List<MotionLabList.Line> lines) {
        String shown = this.pendingShow;
        this.pendingShow = null;
        List<String> ids = Motions.ids();
        if (shown != null && ids.contains(shown)) {
            pick(shown);
            if (this.width >= 0 && this.width < MotionLabLayout.MIN_SPLIT_WIDTH) {
                this.listOut = false;
            }
        }
        if (this.picked == null || !ids.contains(this.picked)) {
            String first = MotionLabList.step(lines, null, 0);
            if (first == null && !ids.isEmpty()) {
                first = MotionLabList.step(MotionLabList.of(Motions.FAMILIES,
                        MOTIONS, ""), null, 0);
            }
            this.picked = null;
            pick(first);
        }
        if (this.picked != null && Motions.get(this.picked) != this.source) {
            read();
        }
    }

    /* ---- Drawing ---- */

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        this.font = minecraft.fontRenderer;
        this.width = (int)Math.floor(box.width);
        this.height = (int)Math.floor(box.height);
        long now = System.nanoTime();
        this.frameSeconds = this.glideNanos == 0L ? 0.0D
                : Math.max(0.0D, (now - this.glideNanos) / 1.0E9D);
        this.glideNanos = now;
        boolean wide = this.width >= MotionLabLayout.MIN_SPLIT_WIDTH;
        if (this.wasWide == null || wide != this.wasWide.booleanValue()) {
            this.listOut = wide;
        }
        this.wasWide = Boolean.valueOf(wide);
        List<MotionLabList.Line> lines = lines();
        settle(lines);
        List<String> about = aboutLines();
        MotionLabLayout layout = layout(about.size());
        clampScrolls(layout, lines);
        double x = Double.isNaN(pointerX) ? Double.NaN : pointerX - box.left;
        double y = Double.isNaN(pointerY) ? Double.NaN : pointerY - box.top;
        this.hovered = hoverAt(layout, lines, x, y);
        int surfaceAlpha = WindowLists.pageSurfaceAlpha(minecraft, alpha);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef((float)box.left, (float)box.top, 0.0F);
            drawList(layout, lines, alpha, surfaceAlpha);
            LostTalesUiHitBox divider = layout.divider();
            if (divider.width > 0) {
                LostTalesUiInk.fillRect((float)divider.left,
                        (float)divider.top, (float)divider.right(),
                        (float)divider.bottom(),
                        faded(LostTalesColors.BORDER_DIM, alpha));
            }
            drawContent(layout, about, alpha, surfaceAlpha, now);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** The picked motion's words about itself, wrapped to the column; none before a draw. */
    private List<String> aboutLines() {
        if (this.font == null || this.picked == null) {
            return Collections.emptyList();
        }
        int width = (int)layout(0).column().width;
        if (width <= 0) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        for (Object line : this.font.listFormattedStringToWidth(
                aboutOf(this.picked), width)) {
            lines.add(String.valueOf(line));
        }
        return lines;
    }

    private MotionLabLayout layout(int aboutLines) {
        return new MotionLabLayout(this.width, this.height,
                MenuWindow.rowHeight(), this.listOut, aboutLines);
    }

    /** The layout as the page stands; before its first draw, one with no room. */
    private MotionLabLayout layout() {
        return layout(aboutLines().size());
    }

    /** The furthest the list scrolls: its last line whole at the band's foot. */
    private static int listMost(MotionLabLayout layout,
                                List<MotionLabList.Line> lines) {
        return Math.max(0, lines.size() * layout.rowHeight()
                - (int)layout.list().height);
    }

    /** The furthest the rows scroll: the last whole at the band's foot. */
    private int rowsMost(MotionLabLayout layout) {
        return Math.max(0, this.editor.rows().size() * layout.rowHeight()
                - (int)layout.rows().height);
    }

    /** Keeps both scrolls within what there is, and glides the drawn ones after them. */
    private void clampScrolls(MotionLabLayout layout,
                              List<MotionLabList.Line> lines) {
        this.listScroll = Math.max(0, Math.min(this.listScroll,
                listMost(layout, lines)));
        this.rowsScroll = Math.max(0, Math.min(this.rowsScroll,
                rowsMost(layout)));
        this.shownListScroll = Motions.followTravel(MotionIds.WINDOW_SCROLL,
                this.shownListScroll, this.listScroll, this.frameSeconds);
        this.shownRowsScroll = Motions.followTravel(MotionIds.WINDOW_SCROLL,
                this.shownRowsScroll, this.rowsScroll, this.frameSeconds);
    }

    /** The row whose drawn top is {@code top}, cut to its band, lit as a menu's row is. */
    private void drawLit(LostTalesUiHitBox band, double top, int rowHeight,
                         int surfaceAlpha) {
        double litTop = Math.max(band.top, top);
        double litBottom = Math.min(band.bottom(), top + rowHeight);
        if (litBottom > litTop) {
            WindowLists.drawLitRow(0.0D, this.width, band.left, litTop,
                    band.right(), litBottom, surfaceAlpha);
        }
    }

    /** The index of the row under {@code y} in a band scrolled by {@code scroll}. */
    private static int rowAt(LostTalesUiHitBox band, double y, double scroll,
                             int rowHeight) {
        return (int)Math.floor((y - band.top + scroll) / rowHeight);
    }

    /** The whole pixels a scroll stands at; its fraction is drawn through the matrix. */
    private static int whole(double scroll) {
        return (int)Math.floor(scroll);
    }

    /** A colour of the palette at its own opacity times the page's. */
    private static int faded(int argb, int alpha) {
        return LostTalesUiInk.argb(LostTalesColors.rgb(argb), Math.round(
                (argb >>> 24) * Math.max(0, Math.min(255, alpha)) / 255.0F));
    }

    /* ---- The list ---- */

    /**
     * The list as a menu's rows: each line one row high, its words a
     * menu's padding in from the band's edges, the picked motion and the
     * line under the pointer lit, and where more of it waits at the edges
     * and the right.
     */
    private void drawList(MotionLabLayout layout,
                          List<MotionLabList.Line> lines, int alpha,
                          int surfaceAlpha) {
        LostTalesUiHitBox list = layout.list();
        if (list.width <= 0 || list.height <= 0) {
            return;
        }
        int rowHeight = layout.rowHeight();
        int left = (int)list.left + MenuWindow.PADDING_X;
        int right = (int)list.right() - MenuWindow.PADDING_X;
        if (lines.isEmpty()) {
            drawNote(left, (int)list.top, right - left, rowHeight,
                    word(this.query.length() > 0 ? "search.none" : "none"),
                    alpha);
            return;
        }
        // The picked line and the one under the pointer, cut to the band,
        // before anything lands on them: each lit alike.
        for (int index = 0; index < lines.size(); index++) {
            MotionLabList.Line line = lines.get(index);
            if (line.isMotion() && (line.id.equals(this.picked)
                    || this.hovered != null && this.hovered.isLine(line.id))) {
                drawLit(list, list.top + index * rowHeight
                        - this.shownListScroll, rowHeight, surfaceAlpha);
            }
        }
        boolean clipped = LostTalesUiClip.beginLocal(this.mc,
                (float)list.left, (float)list.top, (float)list.right(),
                (float)list.bottom());
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, (float)(whole(this.shownListScroll)
                    - this.shownListScroll), 0.0F);
            int y = (int)list.top - whole(this.shownListScroll);
            for (MotionLabList.Line line : lines) {
                if (y + rowHeight >= list.top - 1 && y <= list.bottom() + 1) {
                    if (line.isMotion()) {
                        drawMotionLine(line.id, left, right, y, rowHeight,
                                alpha);
                    } else {
                        WindowLists.drawHeading(this.font,
                                familyName(line.family), left, left, right, y,
                                rowHeight, false, alpha);
                    }
                }
                y += rowHeight;
            }
        } finally {
            GL11.glPopMatrix();
            LostTalesUiClip.end(clipped);
        }
        WindowLists.drawScroll(list.left, 0.0D, list.right(), this.height,
                list.top, list.bottom(), this.shownListScroll,
                listMost(layout, lines), alpha);
    }

    /**
     * A motion's line: the name after its family, in honey while the
     * Lab's saved version plays, and the draft pen ending at the right
     * while it is tuned and not saved.
     */
    private void drawMotionLine(String id, int left, int right, int y,
                                int rowHeight, int alpha) {
        int textTop = y + LostTalesUiInk.centredStart(rowHeight,
                LostTalesUiInk.CAP_HEIGHT);
        boolean unsaved = Motions.isPreviewed(id);
        LostTalesUiSheet mark = LostTalesUiSheet.DRAFT;
        int markRoom = unsaved ? mark.getWidth() + TabIcons.GAP : 0;
        int rgb = Motions.isSaved(id) ? LostTalesUiTheme.accentRgb()
                : LostTalesUiInk.IVORY;
        LostTalesUiInk.drawText(this.font, LostTalesSkyrimUiStyle.trimToWidth(
                        this.font, MotionLabList.shortName(id),
                        Math.max(0, right - left - markRoom)), left, textTop,
                rgb, alpha);
        if (unsaved) {
            LostTalesUiInk.beginContent();
            mark.drawWithShadow(right - mark.getWidth(), textTop
                    + LostTalesUiInk.centredStart(LostTalesUiInk.CAP_HEIGHT,
                            mark.getHeight()), alpha);
        }
    }

    /** A note in the aside tone from {@code left}, its first line's words where a row's at {@code top} stand. */
    private void drawNote(int left, int top, int width, int rowHeight,
                          String text, int alpha) {
        int y = top + LostTalesUiInk.centredStart(rowHeight,
                LostTalesUiInk.CAP_HEIGHT);
        for (Object line : this.font.listFormattedStringToWidth(text,
                Math.max(1, width))) {
            LostTalesUiInk.drawText(this.font, String.valueOf(line), left, y,
                    WindowStyle.asideRgb(), alpha);
            y += MotionLabLayout.ABOUT_LINE;
        }
    }

    /* ---- The motion ---- */

    private void drawContent(MotionLabLayout layout, List<String> about,
                             int alpha, int surfaceAlpha, long now) {
        LostTalesUiHitBox content = layout.content();
        if (content.width <= 0 || content.height <= 0) {
            return;
        }
        LostTalesUiHitBox column = layout.column();
        if (this.picked == null) {
            drawNote((int)column.left, (int)column.top, (int)column.width,
                    layout.rowHeight(), word("pick"), alpha);
            return;
        }
        Motion motion = Motions.get(this.picked);
        LostTalesUiHitBox name = layout.name();
        LostTalesUiInk.drawText(this.font, LostTalesSkyrimUiStyle.trimToWidth(
                        this.font, this.picked, (int)name.width),
                (int)name.left, (int)name.top + LostTalesUiInk.centredStart(
                        (int)name.height, LostTalesUiInk.CAP_HEIGHT),
                LostTalesUiInk.IVORY, alpha);
        drawAbout(layout, about, alpha);
        drawSample(layout, motion, alpha, now);
        drawRows(layout, alpha, surfaceAlpha, now);
    }

    /** What the motion is for, as many lines as the column gives it, the last cut short where more is left. */
    private void drawAbout(MotionLabLayout layout, List<String> about,
                           int alpha) {
        LostTalesUiHitBox box = layout.about();
        int shown = layout.aboutLines();
        for (int index = 0; index < shown && index < about.size(); index++) {
            String line = about.get(index);
            if (index == shown - 1 && about.size() > shown) {
                line = LostTalesSkyrimUiStyle.trimToWidth(this.font,
                        line + "...", (int)box.width);
            }
            LostTalesUiInk.drawText(this.font, line, (int)box.left,
                    (int)box.top + index * MotionLabLayout.ABOUT_LINE,
                    WindowStyle.asideRgb(), alpha);
        }
    }

    /**
     * The sample in its box: a one-pixel rule round it, and the blocks
     * clipped inside it, in the page's own space, so the cut follows the
     * window wherever it stands and stays inside the window's own.
     */
    private void drawSample(MotionLabLayout layout, Motion motion, int alpha,
                            long now) {
        LostTalesUiHitBox box = layout.sample();
        if (box.width <= 2 || box.height <= 2) {
            return;
        }
        float left = (float)box.left;
        float top = (float)box.top;
        float right = (float)box.right();
        float bottom = (float)box.bottom();
        int rule = faded(LostTalesColors.BORDER_DIM, alpha);
        LostTalesUiInk.fillRect(left, top, right, top + 1, rule);
        LostTalesUiInk.fillRect(left, bottom - 1, right, bottom, rule);
        LostTalesUiInk.fillRect(left, top + 1, left + 1, bottom - 1, rule);
        LostTalesUiInk.fillRect(right - 1, top + 1, right, bottom - 1, rule);
        boolean clipped = LostTalesUiClip.beginLocal(this.mc, left + 1,
                top + 1, right - 1, bottom - 1);
        try {
            this.sample.draw(motion, left + 1, top + 1, right - left - 2,
                    bottom - top - 2, alpha, now);
        } finally {
            LostTalesUiClip.end(clipped);
        }
    }

    /**
     * The rows under the sample as a menu's rows: the stepper's row under
     * the pointer lit, the rows clipped to their band and scrolled as
     * one, and where more of them waits at the edges and the right.
     */
    private void drawRows(MotionLabLayout layout, int alpha, int surfaceAlpha,
                          long now) {
        LostTalesUiHitBox box = layout.rows();
        List<MotionLabEditor.Row> rows = this.editor.rows();
        if (box.width <= 0 || box.height <= 0 || rows.isEmpty()) {
            return;
        }
        int rowHeight = layout.rowHeight();
        if (this.hovered != null && this.hovered.part != Part.LINE) {
            drawLit(box, box.top + this.hovered.row * rowHeight
                    - this.shownRowsScroll, rowHeight, surfaceAlpha);
        }
        boolean clipped = LostTalesUiClip.beginLocal(this.mc,
                (float)box.left, (float)box.top, (float)box.right(),
                (float)box.bottom());
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, (float)(whole(this.shownRowsScroll)
                    - this.shownRowsScroll), 0.0F);
            int y = (int)box.top - whole(this.shownRowsScroll);
            for (int index = 0; index < rows.size(); index++) {
                if (y + rowHeight >= box.top - 1 && y <= box.bottom() + 1) {
                    drawRow(layout, rows.get(index), index, y, alpha, now);
                }
                y += rowHeight;
            }
        } finally {
            GL11.glPopMatrix();
            LostTalesUiClip.end(clipped);
        }
        WindowLists.drawScroll(box.left, box.top, box.right(), this.height,
                box.top, box.bottom(), this.shownRowsScroll, rowsMost(layout),
                alpha);
    }

    /**
     * One row as a menu's: a header over its hairline, a note in the
     * aside tone, or a label in ivory and a stepper ending at the row's
     * right, its value in the aside tone and in ivory under the pointer.
     */
    private void drawRow(MotionLabLayout layout, MotionLabEditor.Row row,
                         int index, int y, int alpha, long now) {
        int rowHeight = layout.rowHeight();
        int left = layout.rowLeft();
        int right = layout.rowRight();
        int textTop = y + LostTalesUiInk.centredStart(rowHeight,
                LostTalesUiInk.CAP_HEIGHT);
        if (!row.editable()) {
            if (row.header()) {
                WindowLists.drawHeading(this.font, row.label, left, left,
                        right, y, rowHeight, false, alpha);
            } else {
                LostTalesUiInk.drawText(this.font,
                        LostTalesSkyrimUiStyle.trimToWidth(this.font,
                                row.label, right - left), left, textTop,
                        WindowStyle.asideRgb(), alpha);
            }
            return;
        }
        LostTalesUiInk.drawText(this.font, LostTalesSkyrimUiStyle.trimToWidth(
                        this.font, row.label, layout.labelWidth()), left,
                textTop, LostTalesUiInk.IVORY, alpha);
        boolean onRow = this.hovered != null && this.hovered.isStepper(index);
        LostTalesUiHitBox value = layout.value(y);
        int chevronTop = textTop + Math.floorDiv(LostTalesUiInk.CAP_HEIGHT
                - LESS_GLYPH.getHeight(), 2);
        drawChevron(LESS_GLYPH, LESS_GLYPH_LIT, index + ":less",
                (int)layout.less(y).left, chevronTop,
                onRow && this.hovered.part == Part.LESS, alpha, now);
        drawChevron(MORE_GLYPH, MORE_GLYPH_LIT, index + ":more",
                (int)layout.more(y).left, chevronTop,
                onRow && this.hovered.part == Part.MORE, alpha, now);
        String text = LostTalesSkyrimUiStyle.trimToWidth(this.font,
                row.value(), (int)value.width);
        LostTalesUiInk.drawText(this.font, text, (int)value.left
                        + LostTalesUiInk.centredStart((int)value.width,
                                this.font.getStringWidth(text)),
                textTop, onRow && this.hovered.part == Part.VALUE
                        ? LostTalesUiInk.IVORY : WindowStyle.asideRgb(),
                alpha);
    }

    /**
     * A stepper's chevron, centred across its cell from {@code cellLeft}
     * and standing at {@code top}, rising and lighting under the pointer
     * as every glyph button does.
     */
    private void drawChevron(LostTalesUiSheet glyph, LostTalesUiSheet lit,
                             String key, int cellLeft, int top,
                             boolean under, int alpha, long now) {
        LostTalesUiButtonMotion motion = this.chevrons.get(key);
        if (motion == null) {
            motion = new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
            this.chevrons.put(key, motion);
        }
        motion.advance(now, under, under, under && (Mouse.isButtonDown(0)
                || Mouse.isButtonDown(1)));
        LostTalesUiInk.beginContent();
        LostTalesUiButton.drawGlyph(glyph, lit, motion,
                cellLeft + LostTalesUiInk.centredStart(
                        MenuWindow.STEPPER_CELL, glyph.getWidth()),
                top, alpha);
    }

    /* ---- The pointer ---- */

    /**
     * What is under a point in the page's own space: a motion's line in
     * the list, or a stepper's row and the part of it; null for nothing a
     * press does anything on. Read from the same bands, the same row
     * height and the same drawn scrolls the draw uses.
     */
    private Hover hoverAt(MotionLabLayout layout,
                          List<MotionLabList.Line> lines, double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y) || this.width < 0) {
            return null;
        }
        int rowHeight = layout.rowHeight();
        LostTalesUiHitBox list = layout.list();
        if (list.contains(x, y)) {
            int index = rowAt(list, y, this.shownListScroll, rowHeight);
            if (index >= 0 && index < lines.size()
                    && lines.get(index).isMotion()) {
                return new Hover(Part.LINE, lines.get(index).id, -1);
            }
            return null;
        }
        LostTalesUiHitBox box = layout.rows();
        if (!box.contains(x, y)) {
            return null;
        }
        List<MotionLabEditor.Row> rows = this.editor.rows();
        int index = rowAt(box, y, this.shownRowsScroll, rowHeight);
        if (index < 0 || index >= rows.size()
                || !rows.get(index).editable()) {
            return null;
        }
        double rowTop = box.top + index * rowHeight - this.shownRowsScroll;
        if (layout.less(rowTop).contains(x, y)) {
            return new Hover(Part.LESS, null, index);
        }
        if (layout.value(rowTop).contains(x, y)) {
            return new Hover(Part.VALUE, null, index);
        }
        if (layout.more(rowTop).contains(x, y)) {
            return new Hover(Part.MORE, null, index);
        }
        return new Hover(Part.ROW, null, index);
    }

    private Hover hoverAt(LostTalesUiHitBox box, double x, double y) {
        if (this.width < 0) {
            return null;
        }
        return hoverAt(layout(), lines(), x - box.left, y - box.top);
    }

    /**
     * A press: a line picks its motion, and a narrow page folds the list
     * to show it; a stepper's row steps, on from the right chevron and
     * anywhere else on the row and back from the left chevron, the other
     * way with the right button, ten steps with Shift.
     */
    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box,
                                double x, double y, int button) {
        if (button != 0 && button != 1) {
            return false;
        }
        Hover hit = hoverAt(box, x, y);
        if (hit == null) {
            return false;
        }
        if (hit.part == Part.LINE) {
            if (button != 0) {
                return false;
            }
            pick(hit.id);
            if (!layout().isWide()) {
                this.listOut = false;
            }
            return true;
        }
        int direction = hit.part == Part.LESS ? -1 : 1;
        stepRow(hit.row, button == 1 ? -direction : direction);
        return true;
    }

    private void stepRow(int index, int direction) {
        List<MotionLabEditor.Row> rows = this.editor.rows();
        if (index >= 0 && index < rows.size() && rows.get(index).editable()) {
            rows.get(index).step(direction, GuiScreen.isShiftKeyDown());
        }
    }

    @Override
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        return hoverAt(box, x, y) != null;
    }

    /** A line says how its motion stands; a stepper says how it steps. */
    @Override
    public String tipAt(LostTalesUiHitBox box, double x, double y) {
        Hover hit = hoverAt(box, x, y);
        if (hit == null) {
            return "";
        }
        if (hit.part == Part.LINE) {
            return Motions.isPreviewed(hit.id) ? word("mark.unsaved")
                    : Motions.isSaved(hit.id) ? word("mark.saved") : "";
        }
        return word("step.tip");
    }

    /** The wheel scrolls the list or the rows by whole rows, whichever it is turned over. */
    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        if (lines == 0 || this.width < 0) {
            return false;
        }
        MotionLabLayout layout = layout();
        double pageX = x - box.left;
        double pageY = y - box.top;
        int pixels = WheelStep.pixels(WheelStep.menuRows(lines),
                layout.rowHeight());
        if (layout.list().contains(pageX, pageY)) {
            this.listScroll += pixels;
            return true;
        }
        if (layout.content().contains(pageX, pageY)) {
            this.rowsScroll += pixels;
            return true;
        }
        return false;
    }

    /* ---- The keys ---- */

    /** The keys the Motion Lab answers to, for its help. */
    @Override
    public List<PageKeys.Area> keyAreas() {
        return PageKeys.pageArea("gui.losttales.page.motion_lab",
                PageKeys.pageKey(PAGE_ID, "pick",
                        Keyboard.KEY_UP, PageKeys.OR, Keyboard.KEY_DOWN),
                PageKeys.pageKey(PAGE_ID, "replay", Keyboard.KEY_SPACE),
                PageKeys.pageKey(PAGE_ID, "scroll",
                        Keyboard.KEY_PRIOR, PageKeys.OR, Keyboard.KEY_NEXT),
                PageKeys.pageKey(PAGE_ID, "step",
                        PageKeys.CLICK, PageKeys.OR, PageKeys.RIGHT_CLICK),
                PageKeys.pageKey(PAGE_ID, "ten",
                        Keyboard.KEY_LSHIFT, PageKeys.PLUS, PageKeys.CLICK),
                PageKeys.pageKey(PAGE_ID, "wheel", PageKeys.WHEEL));
    }

    /** The arrows walk the motions, Space plays the sample again, and the page keys scroll the rows. */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.width < 0) {
            return false;
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            walk(keyCode == Keyboard.KEY_UP ? -1 : 1);
            return true;
        }
        if (keyCode == REPLAY_KEY) {
            replay();
            return true;
        }
        if (keyCode == Keyboard.KEY_PRIOR || keyCode == Keyboard.KEY_NEXT) {
            MotionLabLayout layout = layout();
            int rowHeight = layout.rowHeight();
            int page = Math.max(1, (int)layout.rows().height / rowHeight - 1)
                    * rowHeight;
            this.rowsScroll += keyCode == Keyboard.KEY_PRIOR ? -page : page;
            return true;
        }
        return false;
    }

    /** Picks the motion before or after the picked one among those listed. */
    private void walk(int step) {
        pick(MotionLabList.step(lines(), this.picked, step));
    }

    private void replay() {
        if (this.picked != null) {
            this.sample.restart(Motions.get(this.picked), System.nanoTime());
        }
    }

    @Override
    public int tone() {
        return LostTalesColors.rgb(LostTalesColors.MOTION_LAB_TONE);
    }

    /** Animations, their speed and reduced motion: Motion Settings. */
    @Override
    public Settings.Place settingsPlace() {
        return Settings.Place.MOTION;
    }

    /* ---- The window's strip ---- */

    @Override
    public ToolStrip.Panel panel() {
        return LIST_PANEL;
    }

    @Override
    public boolean isPanelOut() {
        return this.listOut;
    }

    @Override
    public void togglePanel() {
        this.listOut = !this.listOut;
    }

    @Override
    public void resetPanel() {
        this.listOut = true;
    }

    @Override
    public String searchPrompt() {
        return word("search");
    }

    /**
     * New words read the list from its top and bring it out; the first
     * motion found is picked where the one picked is not among them.
     */
    @Override
    public void search(String words) {
        String typed = words == null ? "" : words.trim();
        if (typed.equals(this.query)) {
            return;
        }
        this.query = typed;
        this.listScroll = 0;
        if (typed.length() > 0) {
            this.listOut = true;
            List<MotionLabList.Line> lines = lines();
            if (!MotionLabList.holds(lines, this.picked)) {
                pick(MotionLabList.step(lines, null, 0));
            }
        }
    }

    @Override
    public int found() {
        return this.query.length() == 0 ? -1
                : MotionLabList.motions(lines()).size();
    }

    /** The arrows walk the motions found; Return gives the page the keys, and a narrow page shows the motion. */
    @Override
    public boolean searchKey(int keyCode) {
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            walk(keyCode == Keyboard.KEY_UP ? -1 : 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (this.width >= 0 && !layout().isWide()) {
                this.listOut = false;
            }
            return true;
        }
        return false;
    }

    /* ---- The quick switcher ---- */

    @Override
    public String findHeading() {
        return LANG + "find";
    }

    /** Every motion whose id or words about itself hold the words, its family beside it. */
    @Override
    public List<MenuWindow.Entry> find(String words) {
        PageSearch search = PageSearch.of(words);
        List<MenuWindow.Entry> found = new ArrayList<MenuWindow.Entry>();
        for (String id : Motions.ids()) {
            if (search.matches(id, Motions.get(id).about())) {
                found.add(new MenuWindow.Entry(id, id).withValue(
                        familyName(Motions.family(id))));
            }
        }
        return found;
    }

    /** The motion found is picked as the page next draws. */
    @Override
    public void show(String id) {
        this.pendingShow = id;
    }

    /* ---- The window's bar ---- */

    /**
     * Replay, Copy, Reset and Save, each there whatever is picked, greyed
     * with the reason where it cannot be taken. Save writes every motion
     * tuned and not saved, and is lit while there is one.
     */
    @Override
    public List<BarItem> barItems() {
        List<BarItem> items = new ArrayList<BarItem>(4);
        String none = this.picked == null ? word("why.pick") : "";
        items.add(orWhy(BarItem.button(REPLAY, word("replay"),
                new ItemStack(Items.repeater)).tip(WindowBar.withKey(
                        word("replay.tip"), REPLAY_KEY)), none));
        items.add(orWhy(BarItem.button(COPY, word("copy"),
                LostTalesUiSheet.COPY, LostTalesUiSheet.COPY_HOVER)
                .tip(word("copy.tip")), none));
        boolean tuned = this.picked != null
                && (Motions.isPreviewed(this.picked)
                        || Motions.isSaved(this.picked));
        items.add(orWhy(BarItem.button(RESET, word("reset"),
                LostTalesUiSheet.RESET, LostTalesUiSheet.RESET_DISCARD)
                .tip(word("reset.tip")), none.length() > 0 ? none
                : tuned ? "" : word("why.nothing_to_reset")));
        int unsaved = Motions.previewed().size();
        items.add(orWhy(BarItem.button(SAVE, word("save"),
                new ItemStack(Items.paper)).lit(unsaved > 0)
                .tip(unsaved == 1 ? word("save.tip.one")
                        : word("save.tip.many", Integer.valueOf(unsaved))),
                unsaved > 0 ? "" : word("why.nothing_to_save")));
        return items;
    }

    private static BarItem orWhy(BarItem item, String why) {
        return why == null || why.length() == 0 ? item : item.unavailable(why);
    }

    @Override
    public void barPressed(String id, int offer) {
        if (SAVE.equals(id)) {
            saveAll();
            return;
        }
        if (this.picked == null) {
            return;
        }
        if (REPLAY.equals(id)) {
            replay();
        } else if (COPY.equals(id)) {
            GuiScreen.setClipboardString(MotionCodec.write(
                    this.editor.read().motions()));
            sayDone(word("copied"));
        } else if (RESET.equals(id)) {
            Motions.clearPreview(this.picked);
            boolean reset = Motions.forget(this.picked,
                    this.mc.getResourceManager());
            read();
            if (reset) {
                sayDone(word("was_reset"));
            } else {
                sayRefused(word("not_saved"));
            }
        }
    }

    /** Writes every motion tuned and not saved into the Lab's files; each then plays from there. */
    private void saveAll() {
        List<String> ids = Motions.previewed();
        if (ids.isEmpty()) {
            return;
        }
        boolean failed = false;
        for (String id : ids) {
            if (Motions.save(Motions.get(id), this.mc.getResourceManager())) {
                Motions.clearPreview(id);
            } else {
                failed = true;
            }
        }
        if (failed) {
            sayRefused(word("not_saved"));
        } else {
            sayDone(word("saved"));
        }
    }

    /* ---- Life ---- */


    /** The Lab's tab closed while it was shown: what was tuned and not saved goes with it. */
    @Override
    public void hidden() {
        if (!WindowLayout.isOpen(tab())) {
            dropPreviews();
        }
    }

    /**
     * The part of the window screen that lets unsaved tuning go: as the
     * screen closes, whichever tab is in front, and as the Lab's last copy
     * or the window holding its last copies closes, shown or not. It takes
     * nothing from the screen.
     */
    private static final class Closer extends ScreenPart {
        Closer(WindowScreen screen) {
            super(screen);
        }

        @Override
        public void closed() {
            dropPreviews();
        }

        @Override
        public boolean closeTab(WindowPage tab) {
            OtherPage lab = WindowPages.tab(PAGE_ID);
            if (tab != null && tab.isCopyOf(lab)
                    && WindowLayout.copiesOf(lab).size() <= 1) {
                dropPreviews();
            }
            return false;
        }

        @Override
        public boolean closeWindow(Window window) {
            List<WindowPage> copies = WindowLayout.copiesOf(
                    WindowPages.tab(PAGE_ID));
            if (window == null || copies.isEmpty()) {
                return false;
            }
            for (WindowPage copy : copies) {
                if (!window.contains(copy)) {
                    return false;
                }
            }
            dropPreviews();
            return false;
        }
    }
}
