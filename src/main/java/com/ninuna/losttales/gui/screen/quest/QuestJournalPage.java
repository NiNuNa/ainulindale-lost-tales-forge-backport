package com.ninuna.losttales.gui.screen.quest;

import com.ninuna.losttales.client.window.PageKeys;
import com.ninuna.losttales.client.window.OptionGlyph;
import com.ninuna.losttales.client.window.PageOption;
import com.ninuna.losttales.client.quest.ClientQuestCatalog;
import com.ninuna.losttales.client.quest.ClientQuestEntry;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.QuestMarks;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.client.window.BarItem;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageSearch;
import com.ninuna.losttales.client.window.Settings;
import com.ninuna.losttales.client.window.TabIcons;
import com.ninuna.losttales.client.window.ToolStrip;
import com.ninuna.losttales.client.window.WheelStep;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.HashMap;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesQuestActionPacket;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestWords;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveSelection;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import com.ninuna.losttales.quest.LostTalesQuestTimeText;
import com.ninuna.losttales.quest.LostTalesQuestRewardText;
/**
 * The quest journal, a page a window holds: the quests on the left, the
 * one being read on the right. The window holds the rest: the list's
 * button at its tool strip's left, the filters behind its three dots, the search
 * in its well, and the quest's actions on its input bar, where
 * Abandon and Clear ask first. It reads the shared presentation assembled
 * from Lost Tales and LOTR's synchronized quest state, and draws itself
 * in the box its window gives it.
 */
public final class QuestJournalPage extends PageContent {
    /** The code name the page is registered and remembered under. */
    public static final String PAGE_ID = "journal";

    private static final int DETAIL_LINE_HEIGHT = 10;
    private static final long DOUBLE_CLICK_TRACK_MS = 350L;
    /** What a category's heading writes before its name, folded and open, as a menu's foldable heading does. */
    private static final String FOLDED_SIGN = "+ ";
    private static final String OPEN_SIGN = "- ";
    /** The mark at a tracked quest's row's right end. */
    private static final String TRACKED_MARK = "◆";
    /**
     * The quest list's button at the strip's left end: the input bar's
     * quest mark, lit and resting alike until its lit cell is drawn.
     */
    private static final ToolStrip.Panel LIST_PANEL = new ToolStrip.Panel(LostTalesUiSheet.QUEST,
            LostTalesUiSheet.QUEST, "gui.losttales.quest.list.show",
            "gui.losttales.quest.list.hide");

    /** What the pointer is on: asked once a frame, read by every draw. */
    private enum Hovered { NOTHING, CATEGORY, QUEST }

    /** The filters, in the order the page's options list them. */
    private static final QuestFilter[] FILTERS = QuestFilter.values();

    /** The bar's items: their ids, which the page is told when one is pressed. */
    private static final String TRACK = "track";
    private static final String SHARE = "share";
    private static final String ABANDON = "abandon";
    private static final String CLEAR = "clear";
    /** The key that tracks the quest being read, as the tip names it. */
    private static final int TRACK_KEY = Keyboard.KEY_SPACE;
    /** The lang keys of the server's word on tracking a quest, which answers Track. */
    private static final List<String> TRACKING_LINES = Arrays.asList(
            "chat.losttales.quest.note.tracking",
            "chat.losttales.quest.note.untracked",
            "chat.losttales.quest.note.untracked_all");

    private final Minecraft mc = Minecraft.getMinecraft();
    private FontRenderer fontRendererObj;
    /** The page's box as it is drawn: its size, and where its whole pixels stand on the screen. */
    private int width;
    private int height;
    private double clipX;
    private double clipY;
    private final Set<String> collapsedCategories = new HashSet<String>();
    private Hovered hovered = Hovered.NOTHING;
    private int hoveredIndex = -1;
    private String hoveredCategory = "";
    /** The frame the visible list was built for; see {@link #getVisibleQuests}. */
    private List<ClientQuestEntry> visibleQuests;
    private long visibleFrame = -1L;
    private QuestFilter visibleFilter;
    private String visibleQuery = "";
    private long frameCounter;
    /** The words in the window's well; empty while its search is closed. */
    private String query = "";
    /** Whether the quest list is out: beside the details, or over them in a narrow journal. */
    private boolean listOut = true;
    /**
     * Whether the page was wide enough for both halves when last drawn;
     * null before it was. Crossing into a narrow page folds the list to
     * show the quest being read, and crossing back brings it out.
     */
    private Boolean wasWide;
    /**
     * How far each category is unfolded, one transition each: a row's
     * height is its full height times this, so a category opens and
     * closes by growing rather than appearing.
     */
    private final Map<String, MotionTransition> categoryOpen =
            new HashMap<String, MotionTransition>();
    /**
     * Where each half is drawn right now, easing toward where it was
     * sent; the pointer reads the list's too, so it answers for what is
     * on screen.
     */
    private double listShown;
    private double detailShown;
    private long lastFrameNanos;
    private int selectedQuestIndex;
    /** A quest the quick switcher found, picked as the journal next draws; null for none. */
    private String pendingShow;
    private int listScroll;
    private int detailScroll;
    private QuestFilter filter = QuestFilter.ALL;
    private int lastClickedQuestIndex = -1;
    private long lastQuestClickMs;

    /**
     * Takes the box it is drawn in this frame, and the first time the
     * quests' definitions.
     */
    private void takeBox(LostTalesUiHitBox box, double clipX, double clipY) {
        this.width = (int)Math.floor(box.width);
        this.height = (int)Math.floor(box.height);
        this.clipX = clipX;
        this.clipY = clipY;
        if (this.fontRendererObj == null) {
            this.fontRendererObj = this.mc.fontRenderer;
            LostTalesClientQuestDefinitionStore.ensureLoaded(
                    this.mc.getResourceManager());
            clampSelectionAndScroll();
            ensureSelectedQuestVisible();
        }
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        takeBox(box, clipX, clipY);
        int mouseX = pageX(box, pointerX);
        int mouseY = pageY(box, pointerY);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef((float)box.left, (float)box.top, 0.0F);
            drawPage(minecraft, mouseX, mouseY, alpha);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** A screen x in the page's own space; far off for a pointer away. */
    private static int pageX(LostTalesUiHitBox box, double x) {
        return Double.isNaN(x) ? Integer.MIN_VALUE / 2
                : (int)Math.floor(x - box.left);
    }

    private static int pageY(LostTalesUiHitBox box, double y) {
        return Double.isNaN(y) ? Integer.MIN_VALUE / 2
                : (int)Math.floor(y - box.top);
    }

    private void drawPage(Minecraft minecraft, int mouseX, int mouseY,
                          int alpha) {
        boolean wide = this.width >= QuestJournalLayout.MIN_SPLIT_WIDTH;
        if (this.wasWide == null || wide != this.wasWide.booleanValue()) {
            this.listOut = wide;
        }
        this.wasWide = Boolean.valueOf(wide);
        advanceMotion();
        clampSelectionAndScroll();
        showPending();
        QuestJournalLayout layout = layout();
        this.hovered = hoverAt(layout, mouseX, mouseY);

        List<ClientQuestEntry> quests = getVisibleQuests();
        drawDivider(layout);
        drawQuestList(minecraft, quests, layout, alpha);
        drawQuestDetails(quests, layout, alpha);
    }

    /**
     * Moves both halves' scroll one frame on. Each is read from where it
     * is drawn and sent toward where it belongs, so a second press part
     * way through carries on from what is on screen rather than jumping.
     */
    private void advanceMotion() {
        this.frameCounter++;
        long now = System.nanoTime();
        double elapsed = this.lastFrameNanos == 0L ? 0.0D
                : (now - this.lastFrameNanos) / 1000000000.0D;
        this.lastFrameNanos = now;
        String glide = MotionIds.SCREEN_JOURNAL_SCROLL;
        this.listShown = Motions.followTravel(glide, this.listShown,
                this.listScroll, elapsed);
        this.detailShown = Motions.followTravel(glide, this.detailShown,
                this.detailScroll, elapsed);
    }

    /** Every row of the list is a menu's row high. */
    private static int rowHeight() {
        return MenuWindow.rowHeight();
    }

    /**
     * How far a category is unfolded, eased. A fold is not a row
     * appearing and disappearing: its quests' rows grow and shrink, so
     * the list never jumps under the pointer.
     */
    private float categoryOpenShare(String category) {
        MotionTransition transition = this.categoryOpen.get(category);
        if (transition == null) {
            transition = new MotionTransition(MotionIds.SCREEN_JOURNAL_FOLD,
                    true);
            transition.settle(!this.collapsedCategories.contains(category));
            this.categoryOpen.put(category, transition);
        }
        return transition.advance(System.nanoTime(),
                !this.collapsedCategories.contains(category));
    }

    /** The page's own geometry, worked out fresh every frame. */
    private QuestJournalLayout layout() {
        return new QuestJournalLayout(this.width, this.height,
                this.listOut);
    }

    private void drawRule(LostTalesUiHitBox box, int color) {
        if (box.width <= 0.0D || box.height <= 0.0D) {
            return;
        }
        Gui.drawRect((int)box.left, (int)box.top, (int)box.right(),
                (int)box.bottom(), color);
    }

    /* ---- The window's strip ---- */

    /** The journal's tab wears tan, a leather binding's tone no channel wears. */
    @Override
    public int tone() {
        return LostTalesColors.rgb(LostTalesColors.TAN);
    }

    /**
     * The server's word on tracking a quest answers the journal's Track
     * while the journal is shown: it stands over the bar, not in the chat.
     */
    @Override
    public boolean answersLine(String key) {
        return TRACKING_LINES.contains(key);
    }

    @Override
    public void answerLine(String key, String words) {
        sayDone(words);
    }

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

    /** The tracker, the banners, the conversation and the quests' marks: Quest Settings. */
    @Override
    public Settings.Place settingsPlace() {
        return Settings.Place.QUESTS;
    }

    /** The filters, the one in force chosen; each a button on the strip. */
    @Override
    public List<PageOption> options() {
        List<PageOption> options = new ArrayList<PageOption>(FILTERS.length);
        for (QuestFilter each : FILTERS) {
            options.add(PageOption.choice(each.name(),
                    translate(each.labelKey), each == this.filter, each.glyph)
                    .inGroup("filters", "gui.losttales.quest.menu.show"));
        }
        return options;
    }

    @Override
    public boolean takeOption(String id) {
        for (QuestFilter each : FILTERS) {
            if (each.name().equals(id)) {
                setFilter(each);
            }
        }
        return true;
    }

    /** The keys the journal answers to, for its help. */
    @Override
    public List<PageKeys.Area> keyAreas() {
        return PageKeys.pageArea("gui.losttales.page.journal",
                PageKeys.pageKey(PAGE_ID, "track",
                        Keyboard.KEY_SPACE, PageKeys.OR, Keyboard.KEY_RETURN,
                        PageKeys.OR, PageKeys.DOUBLE_CLICK),
                PageKeys.pageKey(PAGE_ID, "filter", Keyboard.KEY_F),
                PageKeys.pageKey(PAGE_ID, "pick",
                        Keyboard.KEY_UP, PageKeys.OR, Keyboard.KEY_DOWN),
                PageKeys.pageKey(PAGE_ID, "scroll",
                        Keyboard.KEY_PRIOR, PageKeys.OR, Keyboard.KEY_NEXT),
                PageKeys.pageKey(PAGE_ID, "ends",
                        Keyboard.KEY_HOME, PageKeys.OR, Keyboard.KEY_END),
                PageKeys.pageKey(PAGE_ID, "fold", PageKeys.CLICK),
                PageKeys.pageKey(PAGE_ID, "wheel", PageKeys.WHEEL));
    }

    /** The well names the filter in force: {@code Search active quests}. */
    @Override
    public String searchPrompt() {
        return translate(this.filter.promptKey);
    }

    /**
     * New words read the list from its top, and bring the list out: the
     * search narrows it, so it shows.
     */
    @Override
    public void search(String words) {
        String typed = words == null ? "" : words.trim();
        if (typed.equals(this.query)) {
            return;
        }
        this.query = typed;
        this.selectedQuestIndex = 0;
        this.listScroll = 0;
        this.detailScroll = 0;
        if (typed.length() > 0) {
            this.listOut = true;
        }
        if (this.fontRendererObj != null) {
            clampSelectionAndScroll();
        }
    }

    @Override
    public int found() {
        return this.query.length() == 0 ? -1 : getVisibleQuests().size();
    }

    /**
     * The arrows walk the quests found; Return reads the one chosen, and
     * a narrow journal folds its list to show it.
     */
    @Override
    public boolean searchKey(int keyCode) {
        if (keyCode == Keyboard.KEY_UP) {
            setSelectedQuestIndex(this.selectedQuestIndex - 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            setSelectedQuestIndex(this.selectedQuestIndex + 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (!layout().isWide()) {
                this.listOut = false;
            }
            return true;
        }
        return false;
    }

    /* ---- The quick switcher ---- */

    @Override
    public String findHeading() {
        return "gui.losttales.quest.find";
    }

    /** Every quest whose title or category holds the words, the state it is in beside it. */
    @Override
    public List<MenuWindow.Entry> find(String words) {
        PageSearch search = PageSearch.of(words);
        List<MenuWindow.Entry> found = new ArrayList<MenuWindow.Entry>();
        for (ClientQuestEntry quest : ClientQuestCatalog.getEntries(this.mc)) {
            if (search.matches(quest.getTitle(), quest.getCategory())) {
                found.add(new MenuWindow.Entry(quest.getReference(),
                        quest.getTitle()).withValue(statusWord(quest)));
            }
        }
        return found;
    }

    /** The quest found is picked in the list as the journal next draws. */
    @Override
    public void show(String reference) {
        this.pendingShow = reference;
    }

    /**
     * Picks the quest the switcher found, the filter opened to every quest
     * where the one in force hides it; a narrow journal shows its details.
     */
    private void showPending() {
        String reference = this.pendingShow;
        this.pendingShow = null;
        if (reference == null) {
            return;
        }
        if (indexOfQuest(reference) < 0) {
            setFilter(QuestFilter.ALL);
        }
        int index = indexOfQuest(reference);
        if (index < 0) {
            return;
        }
        if (!layout().isWide()) {
            this.listOut = false;
        }
        setSelectedQuestIndex(index);
    }

    private int indexOfQuest(String reference) {
        List<ClientQuestEntry> quests = getVisibleQuests();
        for (int index = 0; index < quests.size(); index++) {
            if (reference.equals(quests.get(index).getReference())) {
                return index;
            }
        }
        return -1;
    }

    /** How a quest's state reads on its own line. */
    private static String statusWord(ClientQuestEntry quest) {
        if (quest.isFailed()) {
            return translate("gui.losttales.quest.status.failed");
        }
        if (quest.isAbandoned()) {
            return translate("gui.losttales.quest.status.abandoned");
        }
        if (quest.isCompleted()) {
            return translate("gui.losttales.quest.status.completed");
        }
        return translate("gui.losttales.quest.status.active");
    }

    /** A translated line. */
    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static String translate(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }

    /** One rule between the halves; the chat divides with one, so does this. */
    private void drawDivider(QuestJournalLayout layout) {
        drawRule(layout.divider(), LostTalesColors.BORDER_DIM);
    }

    /**
     * The quest list, drawn as a menu's rows are: each category a
     * foldable heading, then its quests. The chosen quest and the row
     * under the pointer are lit alike, cut to the band the rows show in,
     * before anything lands on them; the scroll edge goes over the band
     * after the rows.
     */
    private void drawQuestList(Minecraft minecraft,
                               List<ClientQuestEntry> quests,
                               QuestJournalLayout layout, int alpha) {
        LostTalesUiHitBox list = layout.list();
        if (list.width <= 0.0D || list.height <= 0.0D) {
            return;
        }
        List<QuestListRow> rows = buildQuestListRows(quests);
        int maxScroll = Math.max(0, getRowsHeight(rows) - (int)list.height);
        this.listScroll = clamp(this.listScroll, 0, maxScroll);
        int bandTop = (int)list.top;
        int bandBottom = (int)list.bottom();
        int surfaceAlpha = WindowLists.pageSurfaceAlpha(minecraft, alpha);
        int y = listTop(list);
        for (QuestListRow row : rows) {
            int litTop = Math.max(bandTop, y);
            int litBottom = Math.min(bandBottom, y + row.height);
            if (litBottom > litTop && isLit(row)) {
                WindowLists.drawLitRow(0.0D, this.width, list.left, litTop,
                        list.right(), litBottom, surfaceAlpha);
            }
            y += row.height;
        }

        enableScissor((int)list.left, bandTop, (int)list.width,
                bandBottom - bandTop);
        try {
            y = listTop(list);
            for (QuestListRow row : rows) {
                if (y + row.height > bandTop && y < bandBottom) {
                    if (row.category) {
                        drawCategoryRow(row.label, layout, y, alpha);
                    } else if (row.quest != null) {
                        drawQuestRow(row, layout, y, alpha);
                    }
                }
                y += row.height;
            }
        } finally {
            disableScissor();
        }
        WindowLists.drawScroll(list.left, 0.0D, list.right(), this.height,
                list.top, list.bottom(), this.listShown, maxScroll, alpha);

        if (rows.isEmpty()) {
            drawEmptyList(layout, alpha);
        }
    }

    /**
     * Where the list's first row stands this frame: the band's top less
     * the drawn scroll, on a whole pixel. The rows, their light and the
     * pointer all count from here.
     */
    private int listTop(LostTalesUiHitBox list) {
        return (int)list.top - (int)Math.round(this.listShown);
    }

    /** Whether a row is lit: the chosen quest's, or the one under the pointer. */
    private boolean isLit(QuestListRow row) {
        if (row.category) {
            return this.hovered == Hovered.CATEGORY
                    && row.label.equals(this.hoveredCategory);
        }
        return row.questIndex == this.selectedQuestIndex
                || (this.hovered == Hovered.QUEST
                        && this.hoveredIndex == row.questIndex);
    }

    /**
     * What the list says when it holds nothing: one row's words, then
     * why, a line at a time down to the band's foot.
     */
    private void drawEmptyList(QuestJournalLayout layout, int alpha) {
        LostTalesUiHitBox rows = layout.listRows();
        FontRenderer font = this.fontRendererObj;
        boolean loaded = !LostTalesClientQuestDefinitionStore.getQuests().isEmpty();
        int left = (int)rows.left;
        int width = (int)rows.width;
        LostTalesUiInk.drawText(font, LostTalesSkyrimUiStyle.trimToWidth(font,
                        translate("gui.losttales.quest.empty.title"), width),
                left, (int)rows.top + LostTalesUiInk.centredStart(rowHeight(),
                        LostTalesUiInk.CAP_HEIGHT),
                WindowStyle.asideRgb(), alpha);
        List<String> lines = font.listFormattedStringToWidth(translate(loaded
                        ? "gui.losttales.quest.empty.body"
                        : "gui.losttales.quest.empty.definitions"),
                Math.max(1, width));
        int y = (int)rows.top + rowHeight();
        for (String line : lines) {
            if (y + DETAIL_LINE_HEIGHT > rows.bottom()) {
                break;
            }
            LostTalesUiInk.drawText(font, line, left, y
                            + LostTalesUiInk.centredStart(DETAIL_LINE_HEIGHT,
                                    LostTalesUiInk.CAP_HEIGHT),
                    WindowStyle.asideRgb(), alpha);
            y += DETAIL_LINE_HEIGHT;
        }
    }

    /**
     * A category: a menu's foldable heading, its sign before its name
     * as written, lit while the pointer is on it.
     */
    private void drawCategoryRow(String label, QuestJournalLayout layout,
                                 int y, int alpha) {
        LostTalesUiHitBox rows = layout.listRows();
        boolean lit = this.hovered == Hovered.CATEGORY
                && label.equals(this.hoveredCategory);
        String sign = this.collapsedCategories.contains(label)
                ? FOLDED_SIGN : OPEN_SIGN;
        WindowLists.drawHeading(this.fontRendererObj,
                sign + categoryName(label), (int)rows.left, (int)rows.left,
                (int)rows.right(), y, rowHeight(), lit, alpha);
    }

    /**
     * One quest: its state glyph centred in an icon's box, its title in
     * the column after it, and a mark at the row's right end where it is
     * tracked, so every row keeps the same columns. A row growing or
     * shrinking as its category folds is cut to its own height.
     */
    private void drawQuestRow(QuestListRow row, QuestJournalLayout layout,
                              int y, int alpha) {
        LostTalesUiHitBox list = layout.list();
        int top = Math.max((int)list.top, y);
        int bottom = Math.min((int)list.bottom(), y + row.height);
        if (bottom <= top) {
            return;
        }
        boolean cut = row.height < rowHeight();
        if (cut) {
            enableScissor((int)list.left, top, (int)list.width, bottom - top);
        }
        try {
            ClientQuestEntry quest = row.quest;
            FontRenderer font = this.fontRendererObj;
            LostTalesUiHitBox rows = layout.listRows();
            int left = (int)rows.left;
            int right = (int)rows.right();
            int textTop = y + LostTalesUiInk.centredStart(row.height,
                    LostTalesUiInk.CAP_HEIGHT);
            String glyph = stateGlyph(quest);
            LostTalesUiInk.drawText(font, glyph, left
                            + LostTalesUiInk.centredStart(TabIcons.SIZE,
                                    inkWidth(glyph)), textTop,
                    LostTalesColors.rgb(glyphRgb(quest)), alpha);
            int titleX = left + TabIcons.SLOT + TabIcons.GAP;
            int titleRight = right;
            if (quest.isTracked()) {
                int markX = right - inkWidth(TRACKED_MARK);
                LostTalesUiInk.drawText(font, TRACKED_MARK, markX, textTop,
                        LostTalesColors.rgb(LostTalesColors.GOLD), alpha);
                titleRight = markX - TabIcons.GAP;
            }
            LostTalesUiInk.drawText(font, LostTalesSkyrimUiStyle.trimToWidth(
                            font, quest.getTitle(), titleRight - titleX),
                    titleX, textTop, LostTalesColors.rgb(questRgb(quest,
                            row.questIndex == this.selectedQuestIndex)),
                    alpha);
        } finally {
            if (cut) {
                disableScissor();
            }
        }
    }

    /** How wide words are inked: their advance less the spacing column after the last glyph. */
    private int inkWidth(String text) {
        return Math.max(0, this.fontRendererObj.getStringWidth(text) - 1);
    }

    /** The mark a quest's state is read by, in the list and in the detail. */
    private static String stateGlyph(ClientQuestEntry quest) {
        if (quest.isCompleted()) {
            return QuestMarks.DONE;
        }
        if (quest.isFailed() || quest.isAbandoned()) {
            return QuestMarks.ENDED;
        }
        return QuestMarks.OPEN;
    }

    private static int glyphRgb(ClientQuestEntry quest) {
        if (quest.isCompleted()) {
            return LostTalesColors.GREEN;
        }
        if (quest.isFailed()) {
            return LostTalesColors.RED;
        }
        if (quest.isAbandoned()) {
            return LostTalesColors.GOLD;
        }
        return LostTalesColors.TEXT;
    }

    private static int questRgb(ClientQuestEntry quest, boolean selected) {
        if (quest.isFailed()) {
            return LostTalesColors.RED;
        }
        if (quest.isAbandoned()) {
            return LostTalesColors.GOLD;
        }
        if (quest.isCompleted()) {
            return LostTalesColors.TEXT_DIM;
        }
        return selected ? LostTalesColors.TEXT_BRIGHT : LostTalesColors.TEXT;
    }

    private static String categoryName(String category) {
        return ClientQuestCatalog.categoryName(category);
    }

    private static int clamp(int value, int least, int most) {
        return value < least ? least : value > most ? most : value;
    }

    /**
     * What the chosen quest says: its lines laid out in the detail
     * column, clipped to it, with the lists' scroll edge where there is
     * more than fits. A separator is one rule, as everywhere else.
     */
    private void drawQuestDetails(List<ClientQuestEntry> quests,
                                  QuestJournalLayout layout, int alpha) {
        LostTalesUiHitBox area = layout.detail();
        LostTalesUiHitBox rows = layout.detailRows();
        if (area.width <= 0.0D || area.height <= 0.0D) {
            return;
        }
        if (quests.isEmpty()) {
            LostTalesSkyrimUiStyle.beginContent();
            drawWrappedText(translate("gui.losttales.quest.empty.detail"),
                    (int)rows.left, (int)rows.top + MenuWindow.PADDING_Y
                            + MenuWindow.NOTE_PADDING, (int)rows.width,
                    WindowStyle.asideRgb(), (int)rows.height
                            - 2 * (MenuWindow.PADDING_Y + MenuWindow.NOTE_PADDING),
                    alpha);
            this.detailScroll = 0;
            return;
        }

        List<DetailLine> lines = buildDetailLines(
                quests.get(this.selectedQuestIndex), (int)rows.width);
        int maxScroll = Math.max(0, detailHeight(lines) - (int)area.height);
        this.detailScroll = clamp(this.detailScroll, 0, maxScroll);

        enableScissor((int)area.left, (int)area.top, (int)area.width,
                (int)area.height);
        LostTalesSkyrimUiStyle.beginContent();
        double y = area.top - this.detailShown;
        for (DetailLine line : lines) {
            if (y + line.height() >= area.top && y <= area.bottom()) {
                drawDetailLine(line, rows, (int)Math.round(y), alpha);
            }
            y += line.height();
        }
        disableScissor();
        WindowLists.drawScroll(area.left, 0.0D, area.right(), this.height,
                area.top, area.bottom(), this.detailShown, maxScroll, alpha);
    }

    /**
     * One line of the quest's words, as a menu's are drawn: a heading as
     * every list's, a separator as a menu's, the words with the one
     * shadow, centred in their line.
     */
    private void drawDetailLine(DetailLine line, LostTalesUiHitBox rows, int y,
                                int alpha) {
        int left = (int)rows.left;
        int right = (int)rows.right();
        if (line.heading) {
            WindowLists.drawHeading(this.fontRendererObj, line.text, left, left,
                    right, y, line.height(), line.title, alpha);
            return;
        }
        if (line.separator) {
            WindowLists.drawSeparator(left + line.indent, right, y, alpha);
            return;
        }
        int textTop = y + LostTalesUiInk.centredStart(DETAIL_LINE_HEIGHT,
                LostTalesUiInk.CAP_HEIGHT);
        if (line.objective) {
            LostTalesUiInk.drawText(this.fontRendererObj,
                    line.complete ? QuestMarks.DONE : line.active
                            ? QuestMarks.OPEN : QuestMarks.AHEAD,
                    left + line.indent, textTop,
                    LostTalesColors.rgb(line.complete ? LostTalesColors.GREEN
                            : line.active ? LostTalesColors.GOLD
                            : LostTalesColors.TEXT_DIM), alpha);
            LostTalesUiInk.drawText(this.fontRendererObj, line.text,
                    left + line.indent + 10, textTop, line.color, alpha);
            return;
        }
        int x = line.centered
                ? (int)Math.round(rows.left + (rows.width
                        - this.fontRendererObj.getStringWidth(line.text)) / 2.0D)
                : left + line.indent;
        LostTalesUiInk.drawText(this.fontRendererObj, line.text, x, textTop,
                line.color, alpha);
    }

    /** How tall the quest's words stand, every line its own height. */
    private static int detailHeight(List<DetailLine> lines) {
        int height = 0;
        for (DetailLine line : lines) {
            height += line.height();
        }
        return height;
    }

    /* ---- The window's bar ---- */

    /**
     * The quest being read's actions: Track (or Stop tracking) and Share
     * from the left, Abandon last in red, or Clear for a finished LOTR
     * quest, which only such a quest may leave History by. Each is there
     * whatever is read, greyed and saying why when it cannot be taken; a
     * world quest takes none of them, being the whole server's.
     */
    @Override
    public List<BarItem> barItems() {
        ClientQuestEntry quest = getSelectedQuest();
        List<BarItem> items = new ArrayList<BarItem>(3);
        String track = translate(quest != null && quest.isTracked()
                ? "gui.losttales.quest.action.untrack"
                : "gui.losttales.quest.action.track");
        items.add(runningOnly(BarItem.button(TRACK, track,
                        new ItemStack(Items.compass))
                .tip(WindowBar.withKey(track, TRACK_KEY)), quest,
                "gui.losttales.quest.action.why.track",
                "gui.losttales.quest.action.why.world_track"));
        String share = translate("gui.losttales.quest.action.share");
        items.add(runningOnly(BarItem.button(SHARE, share,
                        LostTalesUiSheet.SEND, LostTalesUiSheet.SEND_HOVER)
                .tip(share), quest, "gui.losttales.quest.action.why.share",
                "gui.losttales.quest.action.why.world_share"));
        if (clears(quest)) {
            String clear = translate("gui.losttales.quest.action.clear");
            items.add(BarItem.button(CLEAR, clear, LostTalesUiSheet.CLOSE,
                    LostTalesUiSheet.CLOSE_HOVER).tip(clear).ending());
        } else {
            String abandon = translate("gui.losttales.quest.action.abandon");
            items.add(runningOnly(BarItem.button(ABANDON, abandon,
                            LostTalesUiSheet.CLOSE, LostTalesUiSheet.CLOSE_HOVER)
                    .tip(abandon).ending(), quest,
                    "gui.losttales.quest.action.why.abandon",
                    "gui.losttales.quest.action.why.world_abandon"));
        }
        return items;
    }

    /**
     * Greys an action for a quest not running, for a world quest, or with
     * none read, saying why.
     */
    private static BarItem runningOnly(BarItem item, ClientQuestEntry quest,
                                       String whyKey, String worldWhyKey) {
        if (quest == null) {
            return item.unavailable(translate(
                    "gui.losttales.quest.action.why.none"));
        }
        if (quest.getSource() == ClientQuestEntry.Source.WORLD) {
            return item.unavailable(translate(worldWhyKey));
        }
        return quest.isActive() ? item : item.unavailable(translate(whyKey));
    }

    /**
     * Whether the quest leaves History by Clear: a finished Middle-earth
     * quest, as LOTR's own quest book clears one. A Lost Tales quest's
     * History decides whether it may be taken again, so it stays.
     */
    private static boolean clears(ClientQuestEntry quest) {
        return quest != null && quest.getSource() == ClientQuestEntry.Source.LOTR
                && (quest.isCompleted() || quest.isFailed());
    }

    @Override
    public void barPressed(String id, int offer) {
        final ClientQuestEntry quest = getSelectedQuest();
        if (quest == null) {
            return;
        }
        if (TRACK.equals(id)) {
            toggleSelectedQuestTracking(quest);
        } else if (SHARE.equals(id)) {
            // The quest's own token goes into the field being typed in,
            // so the share is written the way a player writes one.
            WindowScreen screen = WindowScreen.current();
            if (screen != null) {
                screen.insertIntoInput(ChatShareTokenParser.buildToken(
                        ChatShareKind.QUEST, quest.getTitle(), 1));
            }
        } else if (ABANDON.equals(id) || CLEAR.equals(id)) {
            ask(quest, CLEAR.equals(id));
        }
    }

    /**
     * Asks before a quest is abandoned or cleared, since either loses the
     * quest's progress. The answer acts on the quest asked about,
     * whatever is read by then.
     */
    private void ask(final ClientQuestEntry quest, final boolean clear) {
        WindowScreen screen = WindowScreen.current();
        if (screen == null) {
            return;
        }
        String kind = clear ? "clear" : "abandon";
        screen.ask(tab(),
                translate("gui.losttales.quest.ask." + kind + ".title"),
                translate("gui.losttales.quest.ask." + kind + ".detail",
                        quest.getTitle()),
                translate("gui.losttales.quest.action." + kind),
                new Runnable() {
                    @Override
                    public void run() {
                        LostTalesNetworkHandler.CHANNEL.sendToServer(
                                new LostTalesQuestActionPacket(clear
                                        ? LostTalesQuestActionPacket.ACTION_CLEAR
                                        : LostTalesQuestActionPacket.ACTION_ABANDON,
                                        quest.getReference()));
                    }
                });
    }

    /**
     * What the pointer is on, asked once before anything is drawn and
     * read by every highlight and every press, so no two of them can
     * read an edge differently.
     */
    private Hovered hoverAt(QuestJournalLayout layout, int mouseX,
                            int mouseY) {
        this.hoveredIndex = -1;
        this.hoveredCategory = "";
        return questRowHover(layout, mouseX, mouseY);
    }

    /** The list row under the point, as one of the hover answers. */
    private Hovered questRowHover(QuestJournalLayout layout, int mouseX,
                                  int mouseY) {
        QuestListRow row = rowAt(buildQuestListRows(getVisibleQuests()),
                layout, mouseX, mouseY);
        if (row == null) {
            return Hovered.NOTHING;
        }
        if (row.category) {
            this.hoveredCategory = row.label;
            return Hovered.CATEGORY;
        }
        this.hoveredIndex = row.questIndex;
        return Hovered.QUEST;
    }

    private List<DetailLine> buildDetailLines(ClientQuestEntry entry,
            int width) {
        LostTalesQuestDefinition quest = entry == null
                ? null : entry.getLostTalesDefinition();
        if (quest == null) {
            return buildExternalDetailLines(entry, width);
        }
        List<DetailLine> lines = new ArrayList<DetailLine>();
        LostTalesQuestProgress progress = entry.getLostTalesProgress();
        boolean completed = entry.isCompleted();
        boolean failed = entry.isFailed();
        boolean abandoned = entry.isAbandoned();
        boolean pinned = entry.isTracked();

        addTitleHeader(lines, quest.getTitle(), width);
        addQuestStatusLines(lines, quest, progress, completed, failed,
                abandoned, pinned, width);
        addHistoryLines(lines, entry.getHistoryEntry(), width);
        addBlankLine(lines);

        String loreText = getCurrentJournalText(quest, progress, completed);
        addWrappedLines(lines, loreText, completed ? WindowStyle.asideRgb() : LostTalesUiInk.IVORY, 8, width - 16);
        addBlankLine(lines);

        addStageSummary(lines, quest, progress, completed,
                entry.getHistoryEntry(), width);
        addRewardSummary(lines, quest, width, completed || failed);

        return lines;
    }

    private List<DetailLine> buildExternalDetailLines(
            ClientQuestEntry quest, int width) {
        List<DetailLine> lines = new ArrayList<DetailLine>();
        if (quest == null) {
            return lines;
        }
        addTitleHeader(lines, quest.getTitle(), width);
        String status = statusWord(quest);
        String tracking = translate(quest.isTracked()
                ? "gui.losttales.quest.status.tracked"
                : "gui.losttales.quest.status.untracked");
        String stage = quest.getStageCount() > 1
                ? " \u00b7 " + translate("gui.losttales.quest.stage",
                        String.valueOf(quest.getStageNumber()),
                        String.valueOf(quest.getStageCount())) : "";
        addWrappedLines(lines, status + " \u00b7 " + tracking
                + stage, quest.isFailed() ? LostTalesColors.rgb(LostTalesColors.RED)
                        : quest.isAbandoned() ? LostTalesColors.rgb(LostTalesColors.GOLD)
                        : WindowStyle.asideRgb(),
                8, width - 16);
        if (quest.getSubtitle().length() > 0) {
            addWrappedLines(lines, quest.getSubtitle(),
                    LostTalesColors.rgb(LostTalesColors.GOLD), 8, width - 16);
        }
        addBlankLine(lines);
        addWrappedLines(lines, quest.getJournalText(),
                quest.isCompleted() ? WindowStyle.asideRgb()
                        : LostTalesUiInk.IVORY,
                8, width - 16);
        addSectionTitle(lines, translate("gui.losttales.quest.section.objectives"));
        for (ClientQuestEntry.Objective objective : quest.getObjectives()) {
            int color = objective.isComplete()
                    ? LostTalesColors.rgb(LostTalesColors.GREEN)
                    : LostTalesUiInk.IVORY;
            addObjectiveWrappedLines(lines, objective.getText(), color,
                    12, width - 16, objective.isComplete(),
                    quest.isActive() && !objective.isComplete());
        }
        if (!quest.getRewards().isEmpty()) {
            addSectionTitle(lines, translate("gui.losttales.quest.section.rewards"));
            for (String reward : quest.getRewards()) {
                addWrappedLines(lines, reward,
                        quest.isCompleted() ? WindowStyle.asideRgb()
                                : WindowStyle.asideRgb(),
                        16, width - 16);
            }
            addBlankLine(lines);
        }
        return lines;
    }

    /** The quest's name at the top, a heading in ivory over its hairline. */
    private void addTitleHeader(List<DetailLine> lines, String title, int width) {
        DetailLine line = new DetailLine(title, LostTalesUiInk.IVORY, 0, false);
        line.heading = true;
        line.title = true;
        lines.add(line);
    }

    /**
     * The journal line for where the quest stands: the current stage's, or
     * the latest earlier stage's; the last stage's for a finished quest,
     * the first's for one no longer running. The description stands in
     * where no stage has a line.
     */
    private String getCurrentJournalText(LostTalesQuestDefinition quest, LostTalesQuestProgress progress, boolean completed) {
        int stage = completed ? quest.getStages().size() - 1
                : Math.max(0, LostTalesQuestObjectiveSelection
                        .getCurrentStageIndex(quest, progress));
        String line = LostTalesQuestWords.journalLine(quest, stage);
        if (line.length() == 0) {
            line = LostTalesQuestWords.description(quest);
        }
        return line == null || line.length() == 0 ? translate("gui.losttales.quest.log.none") : line;
    }

    private void addStageSummary(List<DetailLine> lines,
            LostTalesQuestDefinition quest, LostTalesQuestProgress progress,
            boolean completed, LostTalesQuestHistoryEntry history,
            int width) {
        addSectionTitle(lines, translate("gui.losttales.quest.section.objectives"));
        if (quest.getStages().isEmpty()) {
            addWrappedLines(lines, translate("gui.losttales.quest.objectives.none"), WindowStyle.asideRgb(), 16, width - 16);
            return;
        }

        int currentStageIndex = getCurrentStageIndex(progress, completed, quest);
        boolean addedAny = false;
        for (int i = 0; i < quest.getStages().size(); i++) {
            LostTalesQuestStageDefinition stage = quest.getStages().get(i);
            boolean stageComplete = completed || i < currentStageIndex;
            boolean current = progress != null && i == currentStageIndex && !completed;
            if (!completed && !stageComplete && !current) {
                continue;
            }

            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                boolean lingeringOptional = !completed && i < currentStageIndex
                        && objective.isOptional();
                boolean objectiveActive = current || lingeringOptional;
                boolean recordedOptionalComplete = completed
                        && objective.isOptional() && history != null
                        && history.isOptionalObjectiveCompleted(
                        objective.getId());
                boolean implicitlyComplete = (stageComplete || completed)
                        && !objective.isOptional();
                boolean objectiveComplete = isObjectiveComplete(progress,
                        objective, objectiveActive,
                        implicitlyComplete || recordedOptionalComplete);
                int objectiveColor = completed
                        ? WindowStyle.asideRgb()
                        : objectiveComplete ? LostTalesColors.rgb(LostTalesColors.GREEN)
                        : objectiveActive ? LostTalesUiInk.IVORY
                        : WindowStyle.asideRgb();
                String line = buildObjectiveLine(progress, objective,
                        objectiveActive,
                        implicitlyComplete || recordedOptionalComplete);
                addObjectiveWrappedLines(lines, line, objectiveColor, 12,
                        width - 16, objectiveComplete,
                        objectiveActive && !objectiveComplete);
                addedAny = true;
            }
        }

        if (!addedAny) {
            addWrappedLines(lines, completed ? translate("gui.losttales.quest.status.completed") : translate("gui.losttales.quest.objective.none"), completed ? WindowStyle.asideRgb() : WindowStyle.asideRgb(), 16, width - 16);
        }
        if (completed) {
            addWrappedLines(lines, translate("gui.losttales.quest.status.completed"), WindowStyle.asideRgb(), 16, width - 16);
        }
        addBlankLine(lines);
    }

    private void addRewardSummary(List<DetailLine> lines, LostTalesQuestDefinition quest, int width, boolean completed) {
        if (quest.getRewards().isEmpty()) {
            return;
        }
        addSectionTitle(lines, translate("gui.losttales.quest.section.rewards"));
        for (String rewardLine : buildRewardLines(quest)) {
            addWrappedLines(lines, rewardLine, completed ? WindowStyle.asideRgb() : WindowStyle.asideRgb(), 16, width - 16);
        }
        addBlankLine(lines);
    }

    private void addQuestStatusLines(List<DetailLine> lines, LostTalesQuestDefinition quest, LostTalesQuestProgress progress, boolean completed, boolean failed, boolean abandoned, boolean pinned, int width) {
        String status = translate(failed ? "gui.losttales.quest.status.failed"
                : abandoned ? "gui.losttales.quest.status.abandoned"
                : completed ? "gui.losttales.quest.status.completed"
                : "gui.losttales.quest.status.active");
        String tracking = translate(pinned
                ? "gui.losttales.quest.status.tracked"
                : "gui.losttales.quest.status.untracked");
        String stageText = progress == null ? "" : " \u00b7 "
                + translate("gui.losttales.quest.stage",
                        String.valueOf(LostTalesQuestObjectiveSelection
                                .getCurrentStageIndex(quest, progress) + 1),
                        String.valueOf(Math.max(1, quest.getStages().size())));
        int color = failed ? LostTalesColors.rgb(LostTalesColors.RED)
                : abandoned ? LostTalesColors.rgb(LostTalesColors.GOLD)
                : WindowStyle.asideRgb();
        addWrappedLines(lines, status + " \u00b7 " + tracking + stageText,
                color, 8, width - 16);
        if (progress != null && progress.hasTimeLimit() && this.mc != null && this.mc.theWorld != null) {
            long left = progress.getRemainingTicks(this.mc.theWorld.getTotalWorldTime());
            addWrappedLines(lines, translate("gui.losttales.quest.remaining",
                    left > 0L ? LostTalesQuestTimeText.shortForm(left)
                            : translate("gui.losttales.quest.expired")),
                    left > 0L ? LostTalesColors.rgb(LostTalesColors.GOLD) : LostTalesColors.rgb(LostTalesColors.RED),
                    8, width - 16);
        }
    }

    private void addHistoryLines(List<DetailLine> lines,
            LostTalesQuestHistoryEntry history, int width) {
        if (history == null) {
            return;
        }
        if (history.getDetail().length() > 0) {
            addWrappedLines(lines,
                    translate(history.isCompleted()
                            ? "gui.losttales.quest.outcome"
                            : "gui.losttales.quest.reason",
                            // A reason the game wrote is a lang key; one a
                            // quest's file wrote reads as it was written.
                            translate(history.getDetail())),
                    history.isFailed() ? LostTalesColors.rgb(LostTalesColors.RED)
                            : history.isCompleted()
                            ? LostTalesColors.rgb(LostTalesColors.GREEN)
                            : LostTalesColors.rgb(LostTalesColors.GOLD),
                    8, width - 16);
        }
        addWrappedLines(lines, translate("gui.losttales.quest.recorded",
                formatWorldDate(history.getWorldTime())),
                WindowStyle.asideRgb(), 8, width - 16);
    }

    private String formatWorldDate(long worldTime) {
        long safeTime = Math.max(0L, worldTime);
        long day = safeTime / 24000L + 1L;
        long timeOfDay = safeTime % 24000L;
        long totalMinutes = (timeOfDay * 60L / 1000L + 360L) % 1440L;
        long hour = totalMinutes / 60L;
        long minute = totalMinutes % 60L;
        return translate("gui.losttales.quest.worlddate",
                String.valueOf(day),
                (hour < 10L ? "0" : "") + hour,
                (minute < 10L ? "0" : "") + minute);
    }

    private List<String> buildRewardLines(LostTalesQuestDefinition quest) {
        List<String> result = quest == null ? new ArrayList<String>()
                : LostTalesQuestRewardText.phrases(quest.getRewards());
        if (result.isEmpty()) {
            result.add(translate("gui.losttales.quest.reward.pending"));
        }
        return result;
    }

    private String buildObjectiveLine(LostTalesQuestProgress progress, LostTalesQuestObjectiveDefinition objective, boolean currentStage, boolean questCompleted) {
        return LostTalesQuestObjectiveTextHelper.buildObjectiveLine(progress, objective, currentStage, questCompleted);
    }

    private boolean isObjectiveComplete(LostTalesQuestProgress progress, LostTalesQuestObjectiveDefinition objective, boolean currentStage, boolean stageComplete) {
        int target = getObjectiveTargetCount(objective);
        int current = stageComplete ? target : currentStage && progress != null ? progress.getObjectiveProgress(objective.getId()) : 0;
        return current >= target;
    }

    private int getObjectiveTargetCount(LostTalesQuestObjectiveDefinition objective) {
        return LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(objective);
    }

    private int getCurrentStageIndex(LostTalesQuestProgress progress, boolean completed, LostTalesQuestDefinition quest) {
        if (completed) {
            return Math.max(0, quest.getStages().size() - 1);
        }
        if (progress == null) {
            return 0;
        }
        return LostTalesQuestObjectiveSelection
                .getCurrentStageIndex(quest, progress);
    }

    /** A section's name as every list's heading: sand words over a full hairline. */
    private void addSectionTitle(List<DetailLine> lines, String title) {
        addBlankLine(lines);
        DetailLine line = new DetailLine(title, LostTalesUiInk.IVORY, 0, false);
        line.heading = true;
        lines.add(line);
    }

    private void addBlankLine(List<DetailLine> lines) {
        lines.add(new DetailLine("", LostTalesUiInk.IVORY, 0, false));
    }

    private void addObjectiveLine(List<DetailLine> lines, String text, int color, int indent, boolean complete, boolean active) {
        DetailLine line = new DetailLine(text, color, indent, false);
        line.objective = true;
        line.complete = complete;
        line.active = active;
        lines.add(line);
    }

    private void addObjectiveWrappedLines(List<DetailLine> lines, String text, int color, int indent, int width, boolean complete, boolean active) {
        if (text == null || text.length() == 0) {
            return;
        }
        int wrapWidth = Math.max(30, width - indent - 24);
        List<String> wrapped = this.fontRendererObj.listFormattedStringToWidth(text, wrapWidth);
        if (wrapped.isEmpty()) {
            addObjectiveLine(lines, text, color, indent, complete, active);
            return;
        }
        for (int i = 0; i < wrapped.size(); i++) {
            if (i == 0) {
                addObjectiveLine(lines, wrapped.get(i), color, indent, complete, active);
            } else {
                lines.add(new DetailLine(wrapped.get(i), color, indent + 22, false));
            }
        }
    }

    private void addWrappedLines(List<DetailLine> lines, String text, int color, int indent, int width) {
        if (text == null || text.length() == 0) {
            return;
        }
        int wrapWidth = Math.max(20, width - Math.max(0, indent));
        List<String> wrapped = this.fontRendererObj.listFormattedStringToWidth(text, wrapWidth);
        for (String line : wrapped) {
            lines.add(new DetailLine(line, color, indent, false));
        }
    }

    /** Words wrapped as a menu's note, from {@code y}, cut with dots where they run past {@code maxHeight}. */
    private int drawWrappedText(String text, int x, int y, int width, int color,
                                int maxHeight, int alpha) {
        if (text == null || text.length() == 0 || maxHeight <= 0) {
            return y;
        }
        List<String> lines = this.fontRendererObj.listFormattedStringToWidth(text, width);
        int lineY = y;
        int bottom = y + maxHeight;
        int rise = LostTalesUiInk.centredStart(DETAIL_LINE_HEIGHT,
                LostTalesUiInk.CAP_HEIGHT);
        for (String line : lines) {
            if (lineY + DETAIL_LINE_HEIGHT > bottom) {
                LostTalesUiInk.drawText(this.fontRendererObj, "...", x,
                        lineY + rise, color, alpha);
                return bottom;
            }
            LostTalesUiInk.drawText(this.fontRendererObj, line, x,
                    lineY + rise, color, alpha);
            lineY += DETAIL_LINE_HEIGHT;
        }
        return lineY;
    }

    /**
     * The quests the filter and the search leave, built once a frame.
     * Every part of the screen asks for it several times over — the
     * rows, the glide, the pointer, the detail — and searching measures
     * each quest's words, so it is worked out once and handed back.
     */
    private List<ClientQuestEntry> getVisibleQuests() {
        String query = this.query;
        if (this.visibleQuests != null
                && this.visibleFrame == this.frameCounter
                && this.visibleFilter == this.filter
                && this.visibleQuery.equals(query)) {
            return this.visibleQuests;
        }
        this.visibleQuests = buildVisibleQuests(query);
        this.visibleFrame = this.frameCounter;
        this.visibleFilter = this.filter;
        this.visibleQuery = query;
        return this.visibleQuests;
    }

    private List<ClientQuestEntry> buildVisibleQuests(String rawQuery) {
        List<ClientQuestEntry> visible = new ArrayList<ClientQuestEntry>();
        PageSearch query = PageSearch.of(rawQuery);
        for (ClientQuestEntry quest : ClientQuestCatalog.getEntries(this.mc)) {
            if (!query.isEmpty() && !matchesSearch(quest, query)) {
                continue;
            }
            if (this.filter == QuestFilter.ACTIVE && !quest.isActive()) {
                continue;
            }
            if (this.filter == QuestFilter.COMPLETED && !quest.isCompleted()) {
                continue;
            }
            if (this.filter == QuestFilter.HISTORY
                    && !quest.isFailed() && !quest.isAbandoned()) {
                continue;
            }
            visible.add(quest);
        }
        return visible;
    }

    /** Everything a quest says, put to the search. */
    private static boolean matchesSearch(ClientQuestEntry quest,
                                         PageSearch query) {
        List<String> parts = new ArrayList<String>();
        parts.add(quest.getTitle());
        parts.add(quest.getCategory());
        parts.add(quest.getSubtitle());
        for (ClientQuestEntry.Objective objective : quest.getObjectives()) {
            parts.add(objective.getText());
        }
        return query.matches(parts.toArray(new String[parts.size()]));
    }

    private List<QuestListRow> buildQuestListRows(List<ClientQuestEntry> quests) {
        List<QuestListRow> rows = new ArrayList<QuestListRow>();
        int rowHeight = rowHeight();
        String lastCategory = null;
        for (int i = 0; i < quests.size(); i++) {
            ClientQuestEntry quest = quests.get(i);
            String category = quest.getCategory().length() == 0
                    ? translate("gui.losttales.quest.category.misc") : quest.getCategory();
            if (!category.equals(lastCategory)) {
                rows.add(QuestListRow.category(category, rowHeight));
                lastCategory = category;
            }
            int height = Math.round(rowHeight
                    * categoryOpenShare(category));
            if (height > 0) {
                rows.add(QuestListRow.quest(quest, i, height));
            }
        }
        return rows;
    }

    private int getRowsHeight(List<QuestListRow> rows) {
        int height = 0;
        for (QuestListRow row : rows) {
            height += row.height;
        }
        return height;
    }

    private int getQuestCategoryStartY(List<QuestListRow> rows, int questIndex) {
        int y = 0;
        for (QuestListRow row : rows) {
            if (!row.category && row.questIndex == questIndex) {
                return y;
            }
            y += row.height;
        }
        return 0;
    }


    private ClientQuestEntry getSelectedQuest() {
        List<ClientQuestEntry> quests = getVisibleQuests();
        if (quests.isEmpty() || this.selectedQuestIndex < 0 || this.selectedQuestIndex >= quests.size()) {
            return null;
        }
        return quests.get(this.selectedQuestIndex);
    }

    private void setSelectedQuestIndex(int index) {
        if (index != this.selectedQuestIndex) {
            this.detailScroll = 0;
        }
        this.selectedQuestIndex = index;
        clampSelectionAndScroll();
        ensureSelectedQuestVisible();
    }

    private void clampSelectionAndScroll() {
        List<ClientQuestEntry> quests = getVisibleQuests();
        if (quests.isEmpty()) {
            this.selectedQuestIndex = 0;
            this.listScroll = 0;
            this.detailScroll = 0;
            return;
        }

        if (this.selectedQuestIndex < 0) {
            this.selectedQuestIndex = 0;
        }
        if (this.selectedQuestIndex >= quests.size()) {
            this.selectedQuestIndex = quests.size() - 1;
        }

        List<QuestListRow> rows = buildQuestListRows(quests);
        int visibleHeight = listHeight();
        int maxScroll = Math.max(0, getRowsHeight(rows) - visibleHeight);
        this.listScroll = clamp(this.listScroll, 0, maxScroll);

        int maxDetailScroll = getDetailMaxScroll();
        if (this.detailScroll > maxDetailScroll) {
            this.detailScroll = maxDetailScroll;
        }
        if (this.detailScroll < 0) {
            this.detailScroll = 0;
        }
    }

    private void ensureSelectedQuestVisible() {
        List<ClientQuestEntry> quests = getVisibleQuests();
        if (quests.isEmpty()) {
            return;
        }
        List<QuestListRow> rows = buildQuestListRows(quests);
        int visibleHeight = listHeight();
        int selectedY = getQuestCategoryStartY(rows, this.selectedQuestIndex);
        int selectedBottom = selectedY + rowHeight();
        if (selectedY < this.listScroll + 32) {
            this.listScroll = Math.max(0, selectedY - 32);
        } else if (selectedBottom > this.listScroll + visibleHeight - 20) {
            this.listScroll = selectedBottom - visibleHeight + 20;
        }
        int maxScroll = Math.max(0, getRowsHeight(rows) - visibleHeight);
        if (this.listScroll > maxScroll) {
            this.listScroll = maxScroll;
        }
        if (this.listScroll < 0) {
            this.listScroll = 0;
        }
    }

    private int getDetailMaxScroll() {
        ClientQuestEntry quest = getSelectedQuest();
        if (quest == null || this.fontRendererObj == null) {
            return 0;
        }
        QuestJournalLayout layout = layout();
        List<DetailLine> lines = buildDetailLines(quest,
                (int)layout.detailRows().width);
        return Math.max(0, detailHeight(lines) - (int)layout.detail().height);
    }

    /** How tall the list's viewport is; at least a row, for the maths. */
    private int listHeight() {
        return Math.max(rowHeight(), (int)layout().list().height);
    }

    /**
     * The list row drawn under the point, or null outside the list's
     * band. A row spans the list's full width and is counted from where
     * the rows are drawn ({@link #listTop}), so the light, the click and
     * the pointer all answer for the same pixels.
     */
    private QuestListRow rowAt(List<QuestListRow> rows,
                               QuestJournalLayout layout, int mouseX,
                               int mouseY) {
        LostTalesUiHitBox list = layout.list();
        if (rows == null || !list.contains(mouseX, mouseY)) {
            return null;
        }
        int y = listTop(list);
        for (QuestListRow row : rows) {
            if (mouseY >= y && mouseY < y + row.height) {
                return row;
            }
            y += row.height;
        }
        return null;
    }

    private void scrollDetails(int amount) {
        this.detailScroll += amount;
        int max = getDetailMaxScroll();
        if (this.detailScroll < 0) {
            this.detailScroll = 0;
        }
        if (this.detailScroll > max) {
            this.detailScroll = max;
        }
    }

    private void scrollList(int amount) {
        int content = getRowsHeight(buildQuestListRows(getVisibleQuests()));
        this.listScroll = clamp(this.listScroll + amount, 0,
                Math.max(0, content - listHeight()));
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box,
                                double x, double y, int mouseButton) {
        int mouseX = pageX(box, x);
        int mouseY = pageY(box, y);
        if (mouseButton == 0) {
            QuestJournalLayout layout = layout();
            QuestListRow row = rowAt(buildQuestListRows(getVisibleQuests()),
                    layout, mouseX, mouseY);
            if (row != null && row.category) {
                toggleCategory(row.label);
                return true;
            }
            if (row != null && row.quest != null) {
                long now = System.currentTimeMillis();
                boolean doubleClick = this.lastClickedQuestIndex == row.questIndex && now - this.lastQuestClickMs <= DOUBLE_CLICK_TRACK_MS;
                setSelectedQuestIndex(row.questIndex);
                if (!layout.isWide()) {
                    // A narrow journal folds its list to show the quest picked.
                    this.listOut = false;
                }
                if (doubleClick) {
                    toggleSelectedQuestTracking(row.quest);
                }
                this.lastClickedQuestIndex = row.questIndex;
                this.lastQuestClickMs = now;
                return true;
            }
        }
        return false;
    }

    /**
     * Every control and every list row answers to a click: a category
     * header folds or unfolds its category and a quest row selects its
     * quest.
     */
    @Override
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        return this.fontRendererObj != null && hoverAt(layout(),
                pageX(box, x), pageY(box, y)) != Hovered.NOTHING;
    }

    private void toggleCategory(String category) {
        if (category == null || category.length() == 0) {
            return;
        }
        if (this.collapsedCategories.contains(category)) {
            this.collapsedCategories.remove(category);
        } else {
            this.collapsedCategories.add(category);
        }
        clampSelectionAndScroll();
    }

    private void toggleSelectedQuestTracking(ClientQuestEntry quest) {
        if (quest == null || !quest.isActive()
                || quest.getSource() == ClientQuestEntry.Source.WORLD) {
            return;
        }
        if (quest.isTracked()) {
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    new LostTalesQuestActionPacket(
                            LostTalesQuestActionPacket.ACTION_UNPIN,
                            quest.getReference()));
        } else {
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    new LostTalesQuestActionPacket(
                            LostTalesQuestActionPacket.ACTION_PIN,
                            quest.getReference()));
        }
    }

    /**
     * The wheel scrolls the half it is turned over: the list by whole
     * rows, as a menu's, the details by whole lines.
     */
    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        if (this.fontRendererObj == null || lines == 0) {
            return false;
        }
        if (layout().list().contains(pageX(box, x), pageY(box, y))) {
            scrollList(WheelStep.pixels(WheelStep.menuRows(lines),
                    rowHeight()));
        } else {
            scrollDetails(WheelStep.pixels(lines, DETAIL_LINE_HEIGHT));
        }
        return true;
    }

    /**
     * The journal's keys while it is the page in front; Escape and the
     * chat's own shortcuts, {@code Ctrl+F} for the well among them, pass
     * to the chat.
     */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.fontRendererObj == null) {
            return false;
        }
        if (keyCode == Keyboard.KEY_SPACE || keyCode == Keyboard.KEY_RETURN) {
            toggleSelectedQuestTracking(getSelectedQuest());
            return true;
        }
        if (keyCode == Keyboard.KEY_F) {
            cycleFilter();
            return true;
        }
        if (keyCode == Keyboard.KEY_UP) {
            setSelectedQuestIndex(this.selectedQuestIndex - 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            setSelectedQuestIndex(this.selectedQuestIndex + 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_PRIOR) {
            scrollDetails(-80);
            return true;
        }
        if (keyCode == Keyboard.KEY_NEXT) {
            scrollDetails(80);
            return true;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            this.detailScroll = 0;
            return true;
        }
        if (keyCode == Keyboard.KEY_END) {
            this.detailScroll = getDetailMaxScroll();
            return true;
        }
        return false;
    }

    private void cycleFilter() {
        setFilter(this.filter.next());
    }

    /** Chooses a filter and reads the list from its top again. */
    private void setFilter(QuestFilter chosen) {
        if (chosen == null || chosen == this.filter) {
            return;
        }
        this.filter = chosen;
        this.selectedQuestIndex = 0;
        this.listScroll = 0;
        this.detailScroll = 0;
        clampSelectionAndScroll();
    }

    /**
     * Cuts what is drawn next to a box of the page, where the page really
     * stands on the screen, until {@link #disableScissor} puts back the
     * cut that stood before.
     */
    private void enableScissor(int x, int y, int width, int height) {
        ScaledResolution scaled = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);
        int scale = scaled.getScaleFactor();
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int)Math.round((this.clipX + x) * scale),
                this.mc.displayHeight - (int)Math.round((this.clipY + y + height) * scale),
                Math.max(0, width * scale), Math.max(0, height * scale));
    }

    private void disableScissor() {
        GL11.glPopAttrib();
    }

    private enum QuestFilter {
        // The glyphs are patterns until their artwork is painted.
        ALL("gui.losttales.quest.filter.all",
                "gui.losttales.quest.search.all", OptionGlyph.pattern(
                        "#####",
                        ".....",
                        "#####",
                        ".....",
                        "#####")),
        ACTIVE("gui.losttales.quest.filter.active",
                "gui.losttales.quest.search.active", OptionGlyph.sprite(
                        LostTalesUiSheet.EXCLAMATION,
                        LostTalesUiSheet.EXCLAMATION_LIT)),
        COMPLETED("gui.losttales.quest.filter.completed",
                "gui.losttales.quest.search.completed", OptionGlyph.pattern(
                        "....#",
                        "...#.",
                        "#.#..",
                        ".#...")),
        HISTORY("gui.losttales.quest.filter.history",
                "gui.losttales.quest.search.history", OptionGlyph.pattern(
                        "#####",
                        ".#.#.",
                        "..#..",
                        ".#.#.",
                        "#####"));

        private final String labelKey;
        /** What the well says while this filter is in force. */
        private final String promptKey;
        /** Its button on the strip and its row's picture. */
        private final OptionGlyph glyph;

        QuestFilter(String labelKey, String promptKey, OptionGlyph glyph) {
            this.labelKey = labelKey;
            this.promptKey = promptKey;
            this.glyph = glyph;
        }

        private QuestFilter next() {
            QuestFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private static final class QuestListRow {
        private final boolean category;
        private final String label;
        private final ClientQuestEntry quest;
        private final int questIndex;
        private final int height;

        private QuestListRow(boolean category, String label,
                ClientQuestEntry quest, int questIndex, int height) {
            this.category = category;
            this.label = label;
            this.quest = quest;
            this.questIndex = questIndex;
            this.height = height;
        }

        private static QuestListRow category(String label, int height) {
            return new QuestListRow(true, label == null ? translate("gui.losttales.quest.category.misc") : label, null, -1, height);
        }

        private static QuestListRow quest(ClientQuestEntry quest, int index,
                int height) {
            return new QuestListRow(false, null, quest, index, height);
        }
    }

    private static final class DetailLine {
        private final String text;
        private final int color;
        private final int indent;
        private final boolean centered;
        /** A section's heading, a menu row high; the quest's name, {@link #title}, in ivory. */
        private boolean heading;
        private boolean title;
        private boolean separator;
        private boolean objective;
        private boolean complete;
        private boolean active;

        private DetailLine(String text, int color, int indent, boolean centered) {
            this.text = text == null ? "" : text;
            this.color = color;
            this.indent = Math.max(0, indent);
            this.centered = centered;
        }

        /** A heading a menu row, a separator its hairline's room, words a note's line. */
        private int height() {
            return this.heading ? MenuWindow.rowHeight()
                    : this.separator ? WindowLists.SEPARATOR_HEIGHT
                    : DETAIL_LINE_HEIGHT;
        }
    }
}
