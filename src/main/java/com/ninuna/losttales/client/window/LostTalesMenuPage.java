package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * The Lost Tales Menu, the mod's main menu: a page holding every page a
 * player can open, under its category, the categories standing round a
 * centre as a wheel's points — Profile above, the Map below, the Quest
 * Journal left, the channels right, Fellowships, Settings, the consoles
 * and the whispers on the diagonals — each joined to the centre by a
 * spoke, and the name of the page under the pointer standing under the
 * centre. Each category's rows look, light and scroll as a menu's do.
 *
 * <p>Its key opens it in a window of its own filling the screen,
 * unlocked; the {@code +} of a window opens it there as a new page, as a
 * browser's new tab. A page picked opens a new copy of it in the menu's
 * place ({@link WindowScreen#openFromMenu}). Up and Down walk the pages
 * in the wheel's order, Left and Right go round the categories, and
 * Return opens the page walked to. A right-click on a category's name opens what
 * reaches all its pages. A window too small for the wheel, or words in
 * the tool strip's well, shows the pages as one list.</p>
 */
public final class LostTalesMenuPage extends PageContent {
    /** The code name the page is registered under. */
    public static final String PAGE_ID = "menu";
    /** The item its tab wears: a book of tales. */
    public static final ItemStack ICON = new ItemStack(Items.book);

    /** The wheel's points, clockwise from the top, and the category standing at each. */
    private static final PageCategory[] POINTS = {
            PageCategory.PROFILE, PageCategory.SETTINGS,
            PageCategory.CHANNELS, PageCategory.WHISPERS,
            PageCategory.MAP, PageCategory.CONSOLES,
            PageCategory.QUEST_JOURNAL, PageCategory.FELLOWSHIPS};
    /** Each point's cell in a three by three grid over the page: its column and its row. */
    private static final int[][] CELLS = {
            {1, 0}, {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2}, {0, 1}, {0, 0}};
    /** The narrowest a cell may be for the wheel to stand. */
    private static final int MIN_CELL_WIDTH = 72;
    /** The rows a cell must hold for the wheel to stand: a category's name and two of its pages. */
    private static final int MIN_CELL_ROWS = 3;
    /** Clear room between a category and the edges of its cell. */
    private static final int CELL_GAP = 4;
    /** Rows a category asks room for at most; past them it scrolls. */
    private static final int MAX_SECTION_ROWS = 64;
    /** A spoke leaves this far from the centre and stops this far short of its category. */
    private static final int SPOKE_FROM = 6;
    private static final int SPOKE_SHORT = 3;
    /** How strong a resting spoke stands: a third. */
    private static final int SPOKE_ALPHA = 0x55;
    /** Clear rows between the centre's shadow and the name under it. */
    private static final int NAME_GAP = 2;
    /** The diamond's width; the name under it is centred on it. */
    private static final int DIAMOND_WIDTH = 5;

    private final PageRows[] sections = new PageRows[POINTS.length];
    private final PageRows list;
    /** The rows the list shows, and the category each of its names heads. */
    private final List<MenuWindow.Entry> listRows = new ArrayList<MenuWindow.Entry>();
    private final List<PageCategory> listHeadings = new ArrayList<PageCategory>();
    /** What the tool strip's well holds; empty for the wheel. */
    private String query = "";
    /** The page the arrows walked to, by its row's id; null for none. Return opens it. */
    private String keyPage;
    /** Whether the page walked to is to be scrolled into view as the page next draws. */
    private boolean revealKey;
    /** Where the pointer stood as the page last drew, and as the arrows last walked. */
    private double pointerX = Double.NaN;
    private double pointerY = Double.NaN;
    private double keyPointerX = Double.NaN;
    private double keyPointerY = Double.NaN;
    /** The name under the centre, how strong it stands, and when it last stepped. */
    private String centreName = "";
    private double centreStrength;
    private long centreNanos;

    public LostTalesMenuPage() {
        for (int index = 0; index < POINTS.length; index++) {
            this.sections[index] = new PageRows(taker());
            this.sections[index].setVisibleRows(MAX_SECTION_ROWS);
        }
        this.list = new PageRows(taker());
    }

    /** A page's row picked: a new copy of it takes the menu's place. */
    private PageRows.Taker taker() {
        return new PageRows.Taker() {
            @Override
            public void take(MenuWindow.Entry entry, String part, boolean back,
                             LostTalesUiHitBox row) {
                WindowScreen screen = WindowScreen.current();
                if (!back && entry.icon != null && screen != null) {
                    screen.openFromMenu(tab(), entry.icon);
                }
            }
        };
    }

    /* ---- The rows ---- */

    /** Every page that can be opened, as rows, narrowed to {@code filter}. */
    private static List<MenuWindow.Entry> pages(String filter) {
        return TabMenus.everyPage(WindowScreen.current(), filter);
    }

    /** A category's name over its pages, or over a note that it has none now; the page walked to lit. */
    private List<MenuWindow.Entry> sectionRows(PageCategory category,
                                               List<MenuWindow.Entry> pages) {
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        rows.add(MenuWindow.Entry.header(category.title()));
        List<MenuWindow.Entry> own = TabMenus.rowsOf(pages, category);
        if (own.isEmpty()) {
            rows.add(MenuWindow.Entry.passive(StatCollector.translateToLocal(
                    "gui.losttales.menu.none")));
        } else {
            rows.addAll(chooseKeyPage(own));
        }
        return rows;
    }

    /** The rows, the one the arrows walked to chosen, so it stays lit. */
    private List<MenuWindow.Entry> chooseKeyPage(List<MenuWindow.Entry> rows) {
        for (MenuWindow.Entry row : rows) {
            row.chosen(row.icon != null && row.id.equals(this.keyPage));
        }
        return rows;
    }

    /** The list's rows: every category with pages, in the wheel's order, its name over them. */
    private void layList(String filter) {
        List<MenuWindow.Entry> pages = pages(filter);
        this.listRows.clear();
        this.listHeadings.clear();
        for (PageCategory category : POINTS) {
            List<MenuWindow.Entry> own = TabMenus.rowsOf(pages, category);
            if (own.isEmpty()) {
                continue;
            }
            this.listRows.add(MenuWindow.Entry.header(category.title()));
            this.listHeadings.add(category);
            this.listRows.addAll(chooseKeyPage(own));
        }
        if (this.listRows.isEmpty()) {
            this.listRows.add(MenuWindow.Entry.passive(
                    StatCollector.translateToLocal("gui.losttales.menu.nothing_found")));
        }
        this.list.setRows(this.listRows);
    }

    /* ---- Where everything stands ---- */

    /**
     * Where each category stands on the wheel in {@code box}: in its cell of
     * a three by three grid, against the cell's outer edges, as wide and
     * tall as its rows ask up to the cell. Null where the window is too
     * small for the wheel, or words stand in the well: the list stands then.
     */
    private LostTalesUiHitBox[] wheel(LostTalesUiHitBox box) {
        if (this.query.length() > 0) {
            return null;
        }
        int cellWidth = (int)Math.floor(box.width / 3.0D);
        int cellHeight = (int)Math.floor(box.height / 3.0D);
        if (cellWidth < MIN_CELL_WIDTH || cellHeight - 2 * CELL_GAP
                < MIN_CELL_ROWS * MenuWindow.rowHeight()) {
            return null;
        }
        List<MenuWindow.Entry> pages = pages("");
        LostTalesUiHitBox[] boxes = new LostTalesUiHitBox[POINTS.length];
        int left = (int)Math.floor(box.left);
        int top = (int)Math.floor(box.top);
        for (int index = 0; index < POINTS.length; index++) {
            PageRows section = this.sections[index];
            section.setRows(sectionRows(POINTS[index], pages));
            int width = Math.min(section.naturalWidth(), cellWidth - 2 * CELL_GAP);
            int height = Math.min(section.naturalHeight(width),
                    cellHeight - 2 * CELL_GAP);
            int column = CELLS[index][0];
            int row = CELLS[index][1];
            int cellLeft = left + column * cellWidth;
            int cellTop = top + row * cellHeight;
            int x = column == 0 ? cellLeft + CELL_GAP
                    : column == 2 ? cellLeft + cellWidth - CELL_GAP - width
                    : cellLeft + LostTalesUiInk.centredStart(cellWidth, width);
            int y = row == 0 ? cellTop + CELL_GAP
                    : row == 2 ? cellTop + cellHeight - CELL_GAP - height
                    : cellTop + LostTalesUiInk.centredStart(cellHeight, height);
            boxes[index] = new LostTalesUiHitBox(x, y, width, height);
        }
        return boxes;
    }

    /** The wheel's point whose category stands under the point; -1 for none. */
    private static int pointAt(LostTalesUiHitBox[] wheel, double x, double y) {
        if (wheel == null) {
            return -1;
        }
        for (int index = 0; index < wheel.length; index++) {
            if (wheel[index].contains(x, y)) {
                return index;
            }
        }
        return -1;
    }

    /* ---- Drawing ---- */

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        this.pointerX = pointerX;
        this.pointerY = pointerY;
        if (this.keyPage != null && pointerMoved()) {
            // The pointer moving takes the light back from the arrows.
            this.keyPage = null;
        }
        LostTalesUiHitBox[] wheel = wheel(box);
        if (wheel == null) {
            layList(this.query);
            if (this.revealKey) {
                this.list.revealChosen();
            }
            this.revealKey = false;
            LostTalesUiHitBox column = PageRows.column(box);
            this.list.draw(minecraft, column, clipX + (column.left - box.left),
                    clipY + (column.top - box.top), pointerX, pointerY, alpha);
            return;
        }
        if (this.revealKey) {
            for (PageRows section : this.sections) {
                section.revealChosen();
            }
        }
        this.revealKey = false;
        int centreX = (int)Math.floor(box.left + box.width / 2.0D);
        int centreY = (int)Math.floor(box.top + box.height / 2.0D);
        int pointed = pointAt(wheel, pointerX, pointerY);
        List<List<MenuWindow.Entry>> columns = this.keyPage == null ? null
                : walkable();
        int[] walkedAt = columns == null ? null : placeOf(columns, this.keyPage);
        MenuWindow.Entry walked = walkedAt == null ? null
                : columns.get(walkedAt[0]).get(walkedAt[1]);
        int keyPoint = walkedAt == null ? -1 : walkedAt[0];
        drawSpokes(wheel, centreX, centreY, pointed >= 0 ? pointed : keyPoint,
                alpha);
        drawCentre(centreX, centreY, pointed >= 0 || keyPoint >= 0, alpha);
        drawCentreName(minecraft.fontRenderer, walked != null ? walked.label
                        : pointedPage(wheel, pointed),
                centreX, centreY, (int)Math.floor(box.width / 3.0D)
                        - 2 * CELL_GAP, alpha);
        for (int index = 0; index < wheel.length; index++) {
            LostTalesUiHitBox at = wheel[index];
            this.sections[index].draw(minecraft, at,
                    clipX + (at.left - box.left), clipY + (at.top - box.top),
                    index == pointed ? pointerX : Double.NaN,
                    index == pointed ? pointerY : Double.NaN, alpha);
        }
    }

    /**
     * A spoke from the centre to each category, a pixel staircase in the
     * aside tone, faint; the one to the category under the pointer in
     * honey.
     */
    private static void drawSpokes(LostTalesUiHitBox[] wheel, int centreX,
                                   int centreY, int pointed, int alpha) {
        for (int index = 0; index < wheel.length; index++) {
            LostTalesUiHitBox at = wheel[index];
            int endX = (int)Math.max(at.left, Math.min(at.right() - 1, centreX));
            int endY = (int)Math.max(at.top, Math.min(at.bottom() - 1, centreY));
            int argb = index == pointed
                    ? LostTalesUiInk.argb(LostTalesColors.rgb(LostTalesColors.HONEY), alpha)
                    : LostTalesUiInk.argb(LostTalesColors.rgb(LostTalesColors.ROSE_GRAY),
                            alpha * SPOKE_ALPHA / 0xFF);
            drawSpoke(centreX, centreY, endX, endY, argb);
        }
    }

    /** One spoke: every whole pixel of the line between the two points, but near either end. */
    private static void drawSpoke(int fromX, int fromY, int toX, int toY,
                                  int argb) {
        if ((argb >>> 24) < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        int dx = Math.abs(toX - fromX);
        int dy = -Math.abs(toY - fromY);
        int stepX = fromX < toX ? 1 : -1;
        int stepY = fromY < toY ? 1 : -1;
        int error = dx + dy;
        int x = fromX;
        int y = fromY;
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(false);
        tessellator.setColorRGBA_I(argb & 0xFFFFFF, argb >>> 24);
        while (true) {
            if (distance(x, y, fromX, fromY) > SPOKE_FROM
                    && distance(x, y, toX, toY) > SPOKE_SHORT) {
                tessellator.addVertex(x, y + 1, 0.0D);
                tessellator.addVertex(x + 1, y + 1, 0.0D);
                tessellator.addVertex(x + 1, y, 0.0D);
                tessellator.addVertex(x, y, 0.0D);
            }
            if (x == toX && y == toY) {
                break;
            }
            int twice = 2 * error;
            if (twice >= dy) {
                error += dy;
                x += stepX;
            }
            if (twice <= dx) {
                error += dx;
                y += stepY;
            }
        }
        LostTalesSkyrimUiStyle.endQuads(tessellator, false);
    }

    private static int distance(int x, int y, int otherX, int otherY) {
        return Math.max(Math.abs(x - otherX), Math.abs(y - otherY));
    }

    /** The name of the page under the pointer; empty for none. */
    private String pointedPage(LostTalesUiHitBox[] wheel, int pointed) {
        if (pointed < 0) {
            return "";
        }
        MenuWindow.Entry under = this.sections[pointed].rowAt(wheel[pointed],
                this.pointerX, this.pointerY);
        return under == null || under.icon == null ? "" : under.label;
    }

    /**
     * The name under the centre, centred on the diamond in ivory with the
     * one shadow, at most {@code room} wide: it fades in as a page is
     * pointed at, out as none is, and changes in place between pages.
     */
    private void drawCentreName(FontRenderer font, String named, int centreX,
                                int centreY, int room, int alpha) {
        long now = System.nanoTime();
        double elapsed = this.centreNanos == 0L ? 0.0D
                : (now - this.centreNanos) / 1.0E9D;
        this.centreNanos = now;
        if (named.length() > 0) {
            this.centreName = named;
        }
        this.centreStrength = Motions.follow(MotionIds.WINDOW_HOVER_FADE,
                this.centreStrength, named.length() > 0 ? 1.0D : 0.0D,
                elapsed);
        int shown = (int)Math.round(alpha * this.centreStrength);
        if (this.centreName.length() == 0
                || shown < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        String text = LostTalesSkyrimUiStyle.trimToWidth(font,
                this.centreName, room);
        int ink = font.getStringWidth(text) - 1;
        int left = centreX - DIAMOND_WIDTH / 2;
        LostTalesUiInk.drawText(font, text,
                left + LostTalesUiInk.centredStart(DIAMOND_WIDTH, ink),
                centreY + 3 + LostTalesUiInk.SHADOW_OFFSET + 1 + NAME_GAP,
                LostTalesUiInk.IVORY, shown);
    }

    /** The centre: a diamond with the one shadow, lit honey while a category is pointed at. */
    private static void drawCentre(int centreX, int centreY, boolean lit,
                                   int alpha) {
        LostTalesUiInk.beginContent();
        LostTalesSkyrimUiStyle.drawDiamond(centreX + LostTalesUiInk.SHADOW_OFFSET,
                centreY + LostTalesUiInk.SHADOW_OFFSET,
                LostTalesUiInk.argb(LostTalesUiInk.SHADOW,
                        LostTalesUiInk.shadowAlpha(alpha)));
        LostTalesSkyrimUiStyle.drawDiamond(centreX, centreY,
                LostTalesUiInk.argb(LostTalesColors.rgb(lit
                        ? LostTalesColors.HONEY : LostTalesColors.ROSE_GRAY), alpha));
        LostTalesUiInk.beginContent();
    }

    /* ---- The pointer ---- */

    @Override
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        LostTalesUiHitBox[] wheel = wheel(box);
        if (wheel == null) {
            return this.list.acts(PageRows.column(box), x, y);
        }
        int point = pointAt(wheel, x, y);
        return point >= 0 && this.sections[point].acts(wheel[point], x, y);
    }

    @Override
    public String tipAt(LostTalesUiHitBox box, double x, double y) {
        LostTalesUiHitBox[] wheel = wheel(box);
        if (wheel == null) {
            return this.list.tipAt(PageRows.column(box), x, y);
        }
        int point = pointAt(wheel, x, y);
        return point < 0 ? "" : this.sections[point].tipAt(wheel[point], x, y);
    }

    /**
     * A press on a page's row opens it; a right-click on a category's name
     * opens what reaches all its pages, hung from the name.
     */
    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box,
                                double x, double y, int button) {
        LostTalesUiHitBox[] wheel = wheel(box);
        PageRows rows;
        LostTalesUiHitBox at;
        PageCategory heading = null;
        if (wheel == null) {
            rows = this.list;
            at = PageRows.column(box);
            MenuWindow.Entry under = rows.rowAt(at, x, y);
            int headingIndex = under == null || !under.header ? -1
                    : headingIndex(under);
            heading = headingIndex < 0 ? null : this.listHeadings.get(headingIndex);
        } else {
            int point = pointAt(wheel, x, y);
            if (point < 0) {
                return false;
            }
            rows = this.sections[point];
            at = wheel[point];
            MenuWindow.Entry under = rows.rowAt(at, x, y);
            heading = under != null && under.header ? POINTS[point] : null;
        }
        if (heading != null) {
            return button == 1 && openCategoryMenu(heading, at, y);
        }
        return rows.press(at, x, y, button);
    }

    /** Which of the list's category names a row is; -1 for none of them. */
    private int headingIndex(MenuWindow.Entry heading) {
        int index = 0;
        for (MenuWindow.Entry row : this.listRows) {
            if (row == heading) {
                return index;
            }
            if (row.header) {
                index++;
            }
        }
        return -1;
    }

    /** A category's menu, hung from its name's row. */
    private boolean openCategoryMenu(PageCategory category,
                                     LostTalesUiHitBox rows, double y) {
        WindowScreen screen = WindowScreen.current();
        Window window = WindowLayout.windowOf(tab());
        if (screen == null || window == null) {
            return false;
        }
        int rowTop = (int)Math.floor(y);
        SubWindowAnchor anchor = SubWindowAnchor.inward(
                (int)Math.floor(rows.left), rowTop,
                (int)Math.ceil(rows.right()), rowTop + MenuWindow.rowHeight(),
                WindowFrame.find(window.getId()), screen.width, screen.height);
        screen.menus().show(SubWindowKind.CATEGORY, category,
                WindowMenus.hangingFrom(anchor), true);
        return true;
    }

    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        if (lines == 0) {
            return false;
        }
        LostTalesUiHitBox[] wheel = wheel(box);
        if (wheel == null) {
            if (!PageRows.column(box).contains(x, y)) {
                return false;
            }
            this.list.scroll(lines);
            return true;
        }
        int point = pointAt(wheel, x, y);
        if (point < 0) {
            return false;
        }
        this.sections[point].scroll(lines);
        return true;
    }

    /* ---- The keys ---- */

    /**
     * Up and Down walk the pages in the wheel's order, from one category
     * into the next; Left and Right go round the categories, to the page
     * in the same place or the last one there; Return opens the page
     * walked to. The first arrow walks to the first page there is.
     */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            return openKeyPage();
        }
        boolean vertical = keyCode == Keyboard.KEY_UP
                || keyCode == Keyboard.KEY_DOWN;
        boolean across = keyCode == Keyboard.KEY_LEFT
                || keyCode == Keyboard.KEY_RIGHT;
        if (!vertical && !across) {
            return false;
        }
        int step = keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_LEFT
                ? -1 : 1;
        List<List<MenuWindow.Entry>> columns = walkable();
        int[] at = placeOf(columns, this.keyPage);
        MenuWindow.Entry next = at == null ? firstOf(columns)
                : across ? across(columns, at, step)
                : along(columns, at, step);
        if (next != null) {
            this.keyPage = next.id;
            this.keyPointerX = this.pointerX;
            this.keyPointerY = this.pointerY;
            this.revealKey = true;
        }
        return true;
    }

    /** Opens the page the arrows walked to in the menu's place; false with none. */
    private boolean openKeyPage() {
        MenuWindow.Entry walked = rowOf(walkable(), this.keyPage);
        WindowScreen screen = WindowScreen.current();
        if (walked == null || screen == null) {
            return false;
        }
        screen.openFromMenu(tab(), walked.icon);
        return true;
    }

    /** Whether the pointer has moved off where it stood as the arrows last walked. */
    private boolean pointerMoved() {
        if (Double.isNaN(this.keyPointerX)) {
            return !Double.isNaN(this.pointerX);
        }
        return !(Math.abs(this.pointerX - this.keyPointerX) < 1.0D
                && Math.abs(this.pointerY - this.keyPointerY) < 1.0D);
    }

    /** The pages that can be opened under each of the wheel's points, in its order. */
    private List<List<MenuWindow.Entry>> walkable() {
        List<MenuWindow.Entry> pages = pages(this.query);
        List<List<MenuWindow.Entry>> columns =
                new ArrayList<List<MenuWindow.Entry>>();
        for (PageCategory category : POINTS) {
            List<MenuWindow.Entry> own = new ArrayList<MenuWindow.Entry>();
            for (MenuWindow.Entry row : TabMenus.rowsOf(pages, category)) {
                if (row.icon != null && row.isTakeable()) {
                    own.add(row);
                }
            }
            columns.add(own);
        }
        return columns;
    }


    /** Where the row with {@code id} stands: its point and its place there; null for none. */
    private static int[] placeOf(List<List<MenuWindow.Entry>> columns,
                                 String id) {
        if (id == null) {
            return null;
        }
        for (int point = 0; point < columns.size(); point++) {
            List<MenuWindow.Entry> rows = columns.get(point);
            for (int place = 0; place < rows.size(); place++) {
                if (rows.get(place).id.equals(id)) {
                    return new int[] {point, place};
                }
            }
        }
        return null;
    }

    private static MenuWindow.Entry rowOf(List<List<MenuWindow.Entry>> columns,
                                          String id) {
        int[] at = placeOf(columns, id);
        return at == null ? null : columns.get(at[0]).get(at[1]);
    }

    private static MenuWindow.Entry firstOf(List<List<MenuWindow.Entry>> columns) {
        for (List<MenuWindow.Entry> rows : columns) {
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
        }
        return null;
    }

    /** The page a step up or down in the wheel's order, across the categories, staying put at either end. */
    private static MenuWindow.Entry along(List<List<MenuWindow.Entry>> columns,
                                          int[] at, int step) {
        List<MenuWindow.Entry> all = new ArrayList<MenuWindow.Entry>();
        int index = 0;
        for (int point = 0; point < columns.size(); point++) {
            if (point < at[0]) {
                index += columns.get(point).size();
            }
            all.addAll(columns.get(point));
        }
        index += at[1];
        return all.get(Math.max(0, Math.min(all.size() - 1, index + step)));
    }

    /**
     * The page in the same place in the next category round the wheel
     * that has pages, or its last where it has fewer; itself where no
     * other category has any.
     */
    private static MenuWindow.Entry across(List<List<MenuWindow.Entry>> columns,
                                           int[] at, int step) {
        int count = columns.size();
        for (int turn = 1; turn < count; turn++) {
            List<MenuWindow.Entry> rows = columns.get(
                    ((at[0] + step * turn) % count + count) % count);
            if (!rows.isEmpty()) {
                return rows.get(Math.min(at[1], rows.size() - 1));
            }
        }
        return columns.get(at[0]).get(at[1]);
    }

    /* ---- The window's strip ---- */

    /** Parchment, as a book of tales. */
    @Override
    public int tone() {
        return LostTalesColors.rgb(LostTalesColors.PARCHMENT);
    }

    @Override
    public List<PageKeys.Area> keyAreas() {
        return PageKeys.pageArea("gui.losttales.page.menu",
                PageKeys.pageKey(PAGE_ID, "open", PageKeys.CLICK),
                PageKeys.pageKey(PAGE_ID, "category", PageKeys.RIGHT_CLICK),
                PageKeys.pageKey(PAGE_ID, "wheel", PageKeys.WHEEL),
                PageKeys.pageKey(PAGE_ID, "walk", Keyboard.KEY_UP,
                        PageKeys.OR, Keyboard.KEY_DOWN),
                PageKeys.pageKey(PAGE_ID, "round", Keyboard.KEY_LEFT,
                        PageKeys.OR, Keyboard.KEY_RIGHT),
                PageKeys.pageKey(PAGE_ID, "take", Keyboard.KEY_RETURN));
    }

    @Override
    public String searchPrompt() {
        return StatCollector.translateToLocal("gui.losttales.menu.search");
    }

    /** Words in the well show the pages that hold them as one list. */
    @Override
    public void search(String words) {
        String next = words == null ? "" : words.trim();
        if (!next.equals(this.query)) {
            this.query = next;
            this.list.toTop();
        }
    }

    @Override
    public int found() {
        if (this.query.length() == 0) {
            return -1;
        }
        int found = 0;
        for (MenuWindow.Entry row : pages(this.query)) {
            if (row.icon != null) {
                found++;
            }
        }
        return found;
    }
}
