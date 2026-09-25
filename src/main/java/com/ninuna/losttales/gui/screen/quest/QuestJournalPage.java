package com.ninuna.losttales.gui.screen.quest;

import com.ninuna.losttales.client.quest.ClientQuestCatalog;
import com.ninuna.losttales.client.quest.ClientQuestEntry;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.client.chat.ChatPageContent;
import com.ninuna.losttales.client.chat.ChatPageSearch;
import com.ninuna.losttales.client.chat.LostTalesChatGui;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import java.util.HashMap;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesQuestActionPacket;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import com.ninuna.losttales.quest.LostTalesQuestTimeText;
import com.ninuna.losttales.quest.LostTalesQuestRewardText;
/**
 * The quest journal, a page a chat window holds: the quests on the left,
 * the one being read on the right, its actions under it. The window's
 * strip holds the rest: the list's button at its left, the filters behind
 * its cog, and the search in its well. It reads the shared presentation
 * assembled from Lost Tales and LOTR's synchronized quest state, and
 * draws itself in the box its window gives it.
 */
public final class QuestJournalPage extends ChatPageContent {
    /** The code name the page is registered and remembered under. */
    public static final String PAGE_ID = "journal";

    private static final int LIST_ROW_HEIGHT = QuestJournalLayout.ROW_STRIDE;
    private static final int CATEGORY_ROW_HEIGHT = QuestJournalLayout.CATEGORY_STRIDE;
    private static final int DETAIL_LINE_HEIGHT = 10;
    private static final long DOUBLE_CLICK_TRACK_MS = 350L;
    /** Where a quest row's glyph and title stand inside its row. */
    private static final int ROW_GLYPH_X = 3;
    private static final int ROW_TITLE_X = 15;
    /** The square a category's chevron answers on. */
    private static final int CHEVRON_BOX = 9;
    /**
     * The quest list's button at the strip's left end: the input bar's
     * quest mark, lit and resting alike until its lit cell is drawn.
     */
    private static final Panel LIST_PANEL = new Panel(LostTalesUiSheet.QUEST,
            LostTalesUiSheet.QUEST, "gui.losttales.quest.list.show",
            "gui.losttales.quest.list.hide");

    /** What the pointer is on: asked once a frame, read by every draw. */
    private enum Hovered { NOTHING, CATEGORY, QUEST, ACTION }

    /** The filters, in the order the tab's menu lists them. */
    private static final QuestFilter[] FILTERS = QuestFilter.values();

    /** The actions a quest being read offers, in the order they stand. */
    private enum QuestAction { TRACK, SHARE, ABANDON, CLEAR }

    private static final QuestAction[] ACTIONS = QuestAction.values();

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
    /**
     * A motion each for the controls the pointer can press, so they dip,
     * rise and settle the way every other button in the mod does. A
     * labelled button carries words, and a category's plus and minus are
     * four-fold symmetric, so both only lift
     * ({@code LostTalesUiGlyphTurnTest} says which glyphs may turn).
     */
    private final Map<String, LostTalesUiButtonMotion> labelMotions =
            new HashMap<String, LostTalesUiButtonMotion>();
    private final Map<String, LostTalesUiButtonMotion> categoryMotions =
            new HashMap<String, LostTalesUiButtonMotion>();
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
    /** Where each half is drawn right now, easing toward where it was sent. */
    private double listShown;
    private double detailShown;
    /** Where the read row's surface is drawn, easing toward the row itself. */
    private double selectionShown = Double.NaN;
    private long lastFrameNanos;
    private int selectedQuestIndex;
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
            drawPage(mouseX, mouseY);
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

    private void drawPage(int mouseX, int mouseY) {
        boolean wide = this.width >= QuestJournalLayout.MIN_SPLIT_WIDTH;
        if (this.wasWide == null || wide != this.wasWide.booleanValue()) {
            this.listOut = wide;
        }
        this.wasWide = Boolean.valueOf(wide);
        advanceMotion();
        clampSelectionAndScroll();
        QuestJournalLayout layout = layout();
        this.hovered = hoverAt(layout, mouseX, mouseY);

        drawBackdrop(layout);

        List<ClientQuestEntry> quests = getVisibleQuests();
        drawDivider(layout);
        drawQuestList(quests, layout);
        drawQuestDetails(quests, layout);
        drawActions(layout);
    }

    /**
     * Moves everything that eases one frame on: both halves' scroll and
     * the surface under the row being read. A value is read from where
     * it is and sent toward where it belongs, so a second press part way
     * through carries on from what is on screen rather than jumping.
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
        double target = selectionTarget();
        this.selectionShown = Double.isNaN(this.selectionShown)
                || Double.isNaN(target) ? target
                : Motions.followTravel(glide, this.selectionShown, target,
                        elapsed);
    }

    /**
     * Where the read row stands in the list's own run of rows, or NaN
     * when nothing is read or its category is folded away.
     */
    private double selectionTarget() {
        List<QuestListRow> rows = buildQuestListRows(getVisibleQuests());
        double y = 0.0D;
        for (QuestListRow row : rows) {
            if (!row.category && row.questIndex == this.selectedQuestIndex) {
                return row.height <= 0.0D ? Double.NaN : y;
            }
            y += row.height;
        }
        return Double.NaN;
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

    /**
     * The action strip's surface on the window's own, its own flat
     * colour, so nothing is ever two translucent layers deep.
     */
    private void drawBackdrop(QuestJournalLayout layout) {
        drawStrip(layout.actions());
        drawRule(layout.actionRule(), LostTalesColors.BORDER);
    }

    private void drawStrip(LostTalesUiHitBox box) {
        Gui.drawRect((int)box.left, (int)box.top, (int)box.right(),
                (int)box.bottom(),
                LostTalesColors.withAlpha(LostTalesColors.PLUM_DARK, 0x9A));
    }

    private void drawRule(LostTalesUiHitBox box, int color) {
        if (box.width <= 0.0D || box.height <= 0.0D) {
            return;
        }
        Gui.drawRect((int)box.left, (int)box.top, (int)box.right(),
                (int)box.bottom(), color);
    }

    /** The row a line of text stands on to be centred in a strip. */
    private static int textTop(LostTalesUiHitBox strip) {
        return (int)Math.round(strip.top + (strip.height - 8) / 2.0D);
    }

    /* ---- The window's strip ---- */

    /** The journal's tab wears tan, a leather binding's tone no channel wears. */
    @Override
    public int tone() {
        return LostTalesColors.rgb(LostTalesColors.TAN);
    }

    @Override
    public Panel panel() {
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
    public String choicesHeading() {
        return "gui.losttales.quest.menu.show";
    }

    /** The filters, the one in force marked. */
    @Override
    public List<Choice> choices() {
        List<Choice> choices = new ArrayList<Choice>(FILTERS.length);
        for (QuestFilter each : FILTERS) {
            choices.add(new Choice(each.name(), translate(each.labelKey),
                    each == this.filter));
        }
        return choices;
    }

    @Override
    public void choose(String id) {
        for (QuestFilter each : FILTERS) {
            if (each.name().equals(id)) {
                setFilter(each);
            }
        }
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

    /**
     * A framed button holding one label, centred: the surface in the
     * hole the strip leaves for it, the frame at its lit share, and the
     * label inside the frame's clear pixels.
     */
    private void drawFramedLabel(LostTalesUiHitBox box, String label,
                                 boolean selected, boolean hovered,
                                 int rgb) {
        if (box.width < LostTalesUiFramedButton.MIN_SIZE) {
            return;
        }
        LostTalesUiButtonMotion motion = labelMotion(label);
        motion.advance(System.nanoTime(), selected || hovered, hovered,
                hovered && Mouse.isButtonDown(0));
        float lit = motion.lit();
        // The frame stands still; only what it holds moves, so a row of
        // buttons keeps its shape while one of them answers.
        LostTalesUiFramedButton.drawSurface((float)box.left, (float)box.top,
                (float)box.width, (float)box.height, lit, 0xC8);
        LostTalesUiFramedButton.drawInk((float)box.left, (float)box.top,
                (int)box.width, (int)box.height, lit, 0xFF);
        LostTalesSkyrimUiStyle.beginContent();
        int width = this.fontRendererObj.getStringWidth(label);
        int textX = (int)Math.round(box.left + (box.width - width) / 2.0D);
        int textY = textTop(box);
        LostTalesUiButton.beginPose(motion, textX, textY, width, 8);
        try {
            this.fontRendererObj.drawStringWithShadow(label, textX, textY,
                    LostTalesUiInk.blend(
                            LostTalesColors.rgb(LostTalesColors.TEXT_MUTED),
                            rgb, lit));
        } finally {
            LostTalesUiButton.endPose();
        }
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

    /**
     * The motion of a labelled button, made on first sight and kept by
     * the words it carries, so a button that changes its label — Track
     * becoming Stop tracking — keeps moving rather than starting over.
     */
    private LostTalesUiButtonMotion labelMotion(String key) {
        LostTalesUiButtonMotion motion = this.labelMotions.get(key);
        if (motion == null) {
            motion = new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
            this.labelMotions.put(key, motion);
        }
        return motion;
    }

    private LostTalesUiButtonMotion categoryMotion(String category) {
        LostTalesUiButtonMotion motion = this.categoryMotions.get(category);
        if (motion == null) {
            motion = new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
            this.categoryMotions.put(category, motion);
        }
        return motion;
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
     * The quest list: a category's chevron and name with a rule running
     * out to the column's edge, then its quests. The row under the
     * pointer and the row being read each take one flat surface, never
     * two laid over each other.
     */
    private void drawQuestList(List<ClientQuestEntry> quests,
                               QuestJournalLayout layout) {
        LostTalesUiHitBox list = layout.list();
        if (list.width <= 0.0D || list.height <= 0.0D) {
            return;
        }
        List<QuestListRow> rows = buildQuestListRows(quests);
        int content = getRowsHeight(rows);
        this.listScroll = clamp(this.listScroll, 0,
                Math.max(0, content - (int)list.height));

        enableScissor((int)list.left, (int)list.top, (int)list.width,
                (int)list.height);
        drawSelectionSurface(layout, list);
        double y = list.top - this.listShown;
        for (QuestListRow row : rows) {
            if (y + row.height >= list.top && y <= list.bottom()) {
                if (row.category) {
                    drawCategoryRow(row.label, layout, y);
                } else if (row.quest != null) {
                    drawQuestRow(row.quest, row.questIndex, layout,
                            row.height, y);
                }
            }
            y += row.height;
        }
        disableScissor();
        drawScrollbar(list, content, this.listShown);

        if (rows.isEmpty()) {
            drawEmptyList(layout);
        }
    }

    /** What the list says when it holds nothing. */
    private void drawEmptyList(QuestJournalLayout layout) {
        LostTalesUiHitBox rows = layout.listRows();
        boolean loaded = !LostTalesClientQuestDefinitionStore.getQuests().isEmpty();
        LostTalesSkyrimUiStyle.beginContent();
        this.fontRendererObj.drawStringWithShadow(
                translate("gui.losttales.quest.empty.title"),
                (int)rows.left, (int)rows.top,
                LostTalesColors.rgb(LostTalesColors.TEXT_MUTED));
        drawWrappedText(translate(loaded
                        ? "gui.losttales.quest.empty.body"
                        : "gui.losttales.quest.empty.definitions"),
                (int)rows.left, (int)rows.top + 14, (int)rows.width,
                LostTalesColors.rgb(LostTalesColors.TEXT_DIM),
                (int)rows.height - 14);
    }

    /**
     * A category: its chevron, its name, and a rule filling what the
     * name leaves of the column, so the eye reads the group before the
     * quests in it.
     */
    private void drawCategoryRow(String label, QuestJournalLayout layout,
                                 double y) {
        LostTalesUiHitBox rows = layout.listRows();
        boolean collapsed = this.collapsedCategories.contains(label);
        boolean lit = this.hovered == Hovered.CATEGORY
                && label.equals(this.hoveredCategory);
        String name = LostTalesSkyrimUiStyle.uppercase(categoryName(label));
        int top = (int)Math.round(y) + LostTalesUiInk.centredStart(
                CATEGORY_ROW_HEIGHT, 7);

        LostTalesSkyrimUiStyle.beginContent();
        LostTalesUiButtonMotion motion = categoryMotion(label);
        motion.advance(System.nanoTime(), lit);
        LostTalesUiSheet resting = collapsed
                ? LostTalesUiSheet.PLUS : LostTalesUiSheet.MINUS;
        LostTalesUiSheet marked = collapsed
                ? LostTalesUiSheet.PLUS_HOVER : LostTalesUiSheet.MINUS_HOVER;
        LostTalesUiButton.drawGlyph(resting, marked, motion,
                (float)rows.left + 2,
                (float)Math.round(y + (CATEGORY_ROW_HEIGHT
                        - resting.getHeight()) / 2.0D), 0xFF);
        int nameX = (int)rows.left + CHEVRON_BOX + 2;
        this.fontRendererObj.drawStringWithShadow(name, nameX, top,
                LostTalesColors.rgb(lit ? LostTalesColors.TEXT_BRIGHT
                        : LostTalesColors.TEXT));
        int ruleLeft = nameX + this.fontRendererObj.getStringWidth(name) + 5;
        if (ruleLeft < rows.right()) {
            Gui.drawRect(ruleLeft, top + 3, (int)rows.right(), top + 4,
                    LostTalesColors.BORDER_DIM);
        }
    }

    /**
     * One quest: its state glyph, its title, and a mark where it is
     * tracked. The row being read, and the row under the pointer, each
     * take one surface of their own.
     */
    private void drawQuestRow(ClientQuestEntry quest, int questIndex,
                              QuestJournalLayout layout, double height,
                              double y) {
        LostTalesUiHitBox rows = layout.listRows();
        boolean selected = questIndex == this.selectedQuestIndex;
        boolean lit = !selected && this.hovered == Hovered.QUEST
                && this.hoveredIndex == questIndex;
        if (lit) {
            Gui.drawRect((int)rows.left - 2, (int)Math.round(y),
                    (int)rows.right(), (int)Math.round(y + height),
                    LostTalesColors.withAlpha(LostTalesColors.PLUM_DARK, 0x72));
        }
        LostTalesSkyrimUiStyle.beginContent();
        int top = (int)Math.round(y + (height - 8) / 2.0D);
        int titleRgb = LostTalesColors.rgb(questRgb(quest, selected));
        this.fontRendererObj.drawStringWithShadow(stateGlyph(quest),
                (int)rows.left + ROW_GLYPH_X, top,
                LostTalesColors.rgb(glyphRgb(quest)));
        int titleX = (int)rows.left + ROW_TITLE_X;
        int room = (int)rows.right() - titleX - (quest.isTracked() ? 10 : 0);
        this.fontRendererObj.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj,
                        quest.getTitle(), Math.max(20, room)),
                titleX, top, titleRgb);
        if (quest.isTracked()) {
            this.fontRendererObj.drawStringWithShadow("\u25c6",
                    (int)rows.right() - 7, top,
                    LostTalesColors.rgb(LostTalesColors.GOLD));
        }
    }

    /**
     * The surface under the row being read, laid before the rows so no
     * row is drawn over another's highlight. It stands where the glide
     * has reached, which is why it is one surface rather than a flag on
     * a row.
     */
    private void drawSelectionSurface(QuestJournalLayout layout,
                                      LostTalesUiHitBox list) {
        if (Double.isNaN(this.selectionShown)) {
            return;
        }
        LostTalesUiHitBox rows = layout.listRows();
        double y = list.top - this.listShown + this.selectionShown;
        Gui.drawRect((int)rows.left - 2, (int)Math.round(y), (int)rows.right(),
                (int)Math.round(y) + LIST_ROW_HEIGHT,
                LostTalesColors.withAlpha(LostTalesColors.PLUM_GRAY, 0xB4));
        LostTalesSkyrimUiStyle.beginContent();
    }

    /** The mark a quest's state is read by, in the list and in the detail. */
    private static String stateGlyph(ClientQuestEntry quest) {
        if (quest.isCompleted()) {
            return "\u2714";
        }
        if (quest.isFailed() || quest.isAbandoned()) {
            return "\u2715";
        }
        return "\u25c7";
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

    /**
     * A scrollbar inside an area's right edge, drawn only where there is
     * something to scroll: one quiet column and a brighter handle.
     */
    private void drawScrollbar(LostTalesUiHitBox area, int contentHeight,
                               double scroll) {
        // The bar follows where the view is drawn, not where it is sent,
        // so the handle and the rows move as one.
        LostTalesUiHitBox bar = QuestJournalLayout.scrollbar(area, contentHeight);
        if (bar.width <= 0.0D) {
            return;
        }
        Gui.drawRect((int)bar.left, (int)bar.top, (int)bar.right(),
                (int)bar.bottom(),
                LostTalesColors.withAlpha(LostTalesColors.PLUM_BLACK, 0x8C));
        LostTalesUiHitBox handle = QuestJournalLayout.scrollHandle(bar,
                contentHeight, scroll);
        Gui.drawRect((int)handle.left, (int)Math.round(handle.top),
                (int)handle.right(),
                (int)Math.round(handle.top + handle.height),
                LostTalesColors.withAlpha(LostTalesColors.MAUVE, 0xC8));
    }

    private static int clamp(int value, int least, int most) {
        return value < least ? least : value > most ? most : value;
    }

    /**
     * What the chosen quest says: its lines laid out in the detail
     * column, clipped to it, with a scrollbar where there is more than
     * fits. A separator is one rule, as everywhere else.
     */
    private void drawQuestDetails(List<ClientQuestEntry> quests,
                                  QuestJournalLayout layout) {
        LostTalesUiHitBox area = layout.detail();
        LostTalesUiHitBox rows = layout.detailRows();
        if (area.width <= 0.0D || area.height <= 0.0D) {
            return;
        }
        if (quests.isEmpty()) {
            LostTalesSkyrimUiStyle.beginContent();
            drawWrappedText(translate("gui.losttales.quest.empty.detail"),
                    (int)rows.left, (int)rows.top + 8, (int)rows.width,
                    LostTalesColors.rgb(LostTalesColors.TEXT_MUTED),
                    (int)rows.height - 16);
            this.detailScroll = 0;
            return;
        }

        List<DetailLine> lines = buildDetailLines(
                quests.get(this.selectedQuestIndex), (int)rows.width);
        int content = lines.size() * DETAIL_LINE_HEIGHT;
        this.detailScroll = clamp(this.detailScroll, 0,
                Math.max(0, content - (int)area.height));

        enableScissor((int)area.left, (int)area.top, (int)area.width,
                (int)area.height);
        LostTalesSkyrimUiStyle.beginContent();
        double y = area.top - this.detailShown;
        for (DetailLine line : lines) {
            if (y + DETAIL_LINE_HEIGHT >= area.top && y <= area.bottom()) {
                drawDetailLine(line, rows, (int)Math.round(y));
            }
            y += DETAIL_LINE_HEIGHT;
        }
        disableScissor();
        drawScrollbar(area, content, this.detailShown);
    }

    private void drawDetailLine(DetailLine line, LostTalesUiHitBox rows, int y) {
        if (line.separator) {
            Gui.drawRect((int)rows.left + line.indent, y + 4,
                    (int)rows.right(), y + 5, line.color);
            LostTalesSkyrimUiStyle.beginContent();
            return;
        }
        if (line.objective) {
            this.fontRendererObj.drawStringWithShadow(
                    line.complete ? "\u2714" : line.active ? "\u25c7" : "\u25cb",
                    (int)rows.left + line.indent, y,
                    LostTalesColors.rgb(line.complete
                            ? LostTalesColors.GREEN
                            : line.active ? LostTalesColors.GOLD
                            : LostTalesColors.TEXT_DIM));
            this.fontRendererObj.drawStringWithShadow(line.text,
                    (int)rows.left + line.indent + 10, y, line.color);
            return;
        }
        int x = line.centered
                ? (int)Math.round(rows.left + (rows.width
                        - this.fontRendererObj.getStringWidth(line.text)) / 2.0D)
                : (int)rows.left + line.indent;
        this.fontRendererObj.drawStringWithShadow(line.text, x, y, line.color);
    }

    /**
     * The actions for the quest being read: framed buttons on the strip
     * across the bottom.
     */
    private void drawActions(QuestJournalLayout layout) {
        ClientQuestEntry quest = getSelectedQuest();
        if (quest == null) {
            return;
        }
        LostTalesUiHitBox strip = layout.actions();
        int[] widths = actionWidths(quest);
        double left = QuestJournalLayout.MARGIN;
        for (int index = 0; index < ACTIONS.length; index++) {
            if (!actionOffered(quest, ACTIONS[index])) {
                continue;
            }
            drawFramedLabel(QuestJournalLayout.buttonAt(strip, left, widths,
                            index),
                    actionLabel(quest, ACTIONS[index]), false,
                    this.hovered == Hovered.ACTION && this.hoveredIndex == index,
                    LostTalesColors.rgb(ACTIONS[index] == QuestAction.ABANDON
                            || ACTIONS[index] == QuestAction.CLEAR
                            ? LostTalesColors.RED
                            : LostTalesColors.TEXT_BRIGHT));
        }
    }

    /** Each action button's width; one it does not offer takes none. */
    private int[] actionWidths(ClientQuestEntry quest) {
        int[] widths = new int[ACTIONS.length];
        for (int index = 0; index < ACTIONS.length; index++) {
            widths[index] = actionOffered(quest, ACTIONS[index])
                    ? QuestJournalLayout.buttonWidthFor(
                            this.fontRendererObj.getStringWidth(
                                    actionLabel(quest, ACTIONS[index])))
                    : 0;
        }
        return widths;
    }

    /** Whether the quest being read offers the action at all. */
    private static boolean actionOffered(ClientQuestEntry quest,
                                         QuestAction action) {
        if (quest == null) {
            return false;
        }
        if (action == QuestAction.CLEAR) {
            // Only a Middle-earth quest leaves History: a Lost Tales one
            // decides whether the quest may be taken again.
            return quest.getSource() == ClientQuestEntry.Source.LOTR
                    && (quest.isCompleted() || quest.isFailed());
        }
        // Tracking and sharing are for a quest still running: a finished
        // one has nothing left to follow, and the server shares no other.
        return quest.isActive();
    }

    private static String actionLabel(ClientQuestEntry quest,
                                      QuestAction action) {
        if (action == QuestAction.TRACK) {
            return translate(quest.isTracked()
                    ? "gui.losttales.quest.action.untrack"
                    : "gui.losttales.quest.action.track");
        }
        if (action == QuestAction.SHARE) {
            return translate("gui.losttales.quest.action.share");
        }
        if (action == QuestAction.CLEAR) {
            return translate("gui.losttales.quest.action.clear");
        }
        return translate("gui.losttales.quest.action.abandon");
    }

    /** Runs the action on the quest being read. */
    private void runAction(ClientQuestEntry quest, QuestAction action) {
        if (!actionOffered(quest, action)) {
            return;
        }
        if (action == QuestAction.TRACK) {
            toggleSelectedQuestTracking(quest);
            return;
        }
        if (action == QuestAction.SHARE) {
            // The quest's own token goes into the field being typed in,
            // so the share is written the way a player writes one.
            if (this.mc.currentScreen instanceof LostTalesChatGui) {
                ((LostTalesChatGui)this.mc.currentScreen).insertIntoInput(
                        ChatShareTokenParser.buildToken(ChatShareKind.QUEST,
                                quest.getTitle(), 1));
            }
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesQuestActionPacket(action == QuestAction.CLEAR
                        ? LostTalesQuestActionPacket.ACTION_CLEAR
                        : LostTalesQuestActionPacket.ACTION_ABANDON,
                        quest.getReference()));
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
        ClientQuestEntry selected = getSelectedQuest();
        if (selected != null) {
            int[] actions = actionWidths(selected);
            for (int index = 0; index < ACTIONS.length; index++) {
                if (actions[index] > 0 && QuestJournalLayout.buttonAt(
                        layout.actions(), QuestJournalLayout.MARGIN, actions,
                        index).contains(mouseX, mouseY)) {
                    this.hoveredIndex = index;
                    return Hovered.ACTION;
                }
            }
        }
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
        addWrappedLines(lines, loreText, completed ? LostTalesSkyrimUiStyle.TEXT_MUTED : LostTalesSkyrimUiStyle.TEXT, 8, width - 16);
        addBlankLine(lines);
        addSeparator(lines, 0, LostTalesSkyrimUiStyle.BORDER_DIM);
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
                + stage, quest.isFailed() ? LostTalesSkyrimUiStyle.RED
                        : quest.isAbandoned() ? LostTalesSkyrimUiStyle.GOLD
                        : LostTalesSkyrimUiStyle.TEXT_MUTED,
                8, width - 16);
        if (quest.getSubtitle().length() > 0) {
            addWrappedLines(lines, quest.getSubtitle(),
                    LostTalesSkyrimUiStyle.GOLD, 8, width - 16);
        }
        addBlankLine(lines);
        addWrappedLines(lines, quest.getJournalText(),
                quest.isCompleted() ? LostTalesSkyrimUiStyle.TEXT_MUTED
                        : LostTalesSkyrimUiStyle.TEXT,
                8, width - 16);
        addBlankLine(lines);
        addSeparator(lines, 0, LostTalesSkyrimUiStyle.BORDER_DIM);
        addSectionTitle(lines, translate("gui.losttales.quest.section.objectives"));
        for (ClientQuestEntry.Objective objective : quest.getObjectives()) {
            int color = objective.isComplete()
                    ? LostTalesSkyrimUiStyle.GREEN
                    : LostTalesSkyrimUiStyle.TEXT;
            addObjectiveWrappedLines(lines, objective.getText(), color,
                    12, width - 16, objective.isComplete(),
                    quest.isActive() && !objective.isComplete());
        }
        if (!quest.getRewards().isEmpty()) {
            addSectionTitle(lines, translate("gui.losttales.quest.section.rewards"));
            for (String reward : quest.getRewards()) {
                addWrappedLines(lines, reward,
                        quest.isCompleted() ? LostTalesSkyrimUiStyle.TEXT_DIM
                                : LostTalesSkyrimUiStyle.TEXT_MUTED,
                        16, width - 16);
            }
            addBlankLine(lines);
        }
        return lines;
    }

    private void addTitleHeader(List<DetailLine> lines, String title, int width) {
        String safeTitle = LostTalesSkyrimUiStyle.uppercase(title);
        String trimmedTitle = LostTalesSkyrimUiStyle.trimToWidth(this.fontRendererObj, safeTitle, Math.max(40, width - 70));
        addSeparator(lines, 0, LostTalesSkyrimUiStyle.BORDER_DIM);
        lines.add(new DetailLine(trimmedTitle, LostTalesSkyrimUiStyle.TEXT_BRIGHT, 0, true));
        addSeparator(lines, 0, LostTalesSkyrimUiStyle.BORDER_DIM);
    }

    private String getCurrentJournalText(LostTalesQuestDefinition quest, LostTalesQuestProgress progress, boolean completed) {
        String best = null;
        if (!quest.getJournalLog().isEmpty()) {
            int currentValue = completed ? Integer.MAX_VALUE : getProgressStageNumber(progress);
            int bestValue = Integer.MIN_VALUE;
            for (Map.Entry<String, String> entry : quest.getJournalLog().entrySet()) {
                int value = parseStageNumber(entry.getKey(), Integer.MIN_VALUE);
                if (value <= currentValue && value >= bestValue) {
                    best = entry.getValue();
                    bestValue = value;
                }
            }
            if (best == null && !quest.getJournalLog().isEmpty()) {
                best = quest.getJournalLog().values().iterator().next();
            }
        }
        if (best == null || best.length() == 0) {
            best = quest.getDescription();
        }
        return best == null || best.length() == 0 ? translate("gui.losttales.quest.log.none") : best;
    }

    private void addStageSummary(List<DetailLine> lines,
            LostTalesQuestDefinition quest, LostTalesQuestProgress progress,
            boolean completed, LostTalesQuestHistoryEntry history,
            int width) {
        addSectionTitle(lines, translate("gui.losttales.quest.section.objectives"));
        if (quest.getStages().isEmpty()) {
            addWrappedLines(lines, translate("gui.losttales.quest.objectives.none"), LostTalesSkyrimUiStyle.TEXT_MUTED, 16, width - 16);
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
                        ? LostTalesSkyrimUiStyle.TEXT_DIM
                        : objectiveComplete ? LostTalesSkyrimUiStyle.GREEN
                        : objectiveActive ? LostTalesSkyrimUiStyle.TEXT
                        : LostTalesSkyrimUiStyle.TEXT_MUTED;
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
            addWrappedLines(lines, completed ? translate("gui.losttales.quest.status.completed") : translate("gui.losttales.quest.objective.none"), completed ? LostTalesSkyrimUiStyle.TEXT_DIM : LostTalesSkyrimUiStyle.TEXT_MUTED, 16, width - 16);
        }
        if (completed) {
            addWrappedLines(lines, translate("gui.losttales.quest.status.completed"), LostTalesSkyrimUiStyle.TEXT_DIM, 16, width - 16);
        }
        addBlankLine(lines);
    }

    private void addRewardSummary(List<DetailLine> lines, LostTalesQuestDefinition quest, int width, boolean completed) {
        if (quest.getRewards().isEmpty()) {
            return;
        }
        addSectionTitle(lines, translate("gui.losttales.quest.section.rewards"));
        for (String rewardLine : buildRewardLines(quest)) {
            addWrappedLines(lines, rewardLine, completed ? LostTalesSkyrimUiStyle.TEXT_DIM : LostTalesSkyrimUiStyle.TEXT_MUTED, 16, width - 16);
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
        int color = failed ? LostTalesSkyrimUiStyle.RED
                : abandoned ? LostTalesSkyrimUiStyle.GOLD
                : LostTalesSkyrimUiStyle.TEXT_MUTED;
        addWrappedLines(lines, status + " \u00b7 " + tracking + stageText,
                color, 8, width - 16);
        if (progress != null && progress.hasTimeLimit() && this.mc != null && this.mc.theWorld != null) {
            long left = progress.getRemainingTicks(this.mc.theWorld.getTotalWorldTime());
            addWrappedLines(lines, translate("gui.losttales.quest.remaining",
                    left > 0L ? LostTalesQuestTimeText.shortForm(left)
                            : translate("gui.losttales.quest.expired")),
                    left > 0L ? LostTalesSkyrimUiStyle.GOLD : LostTalesSkyrimUiStyle.RED,
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
                    history.isFailed() ? LostTalesSkyrimUiStyle.RED
                            : history.isCompleted()
                            ? LostTalesSkyrimUiStyle.GREEN
                            : LostTalesSkyrimUiStyle.GOLD,
                    8, width - 16);
        }
        addWrappedLines(lines, translate("gui.losttales.quest.recorded",
                formatWorldDate(history.getWorldTime())),
                LostTalesSkyrimUiStyle.TEXT_MUTED, 8, width - 16);
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

    private int getProgressStageNumber(LostTalesQuestProgress progress) {
        if (progress == null) {
            return Integer.MIN_VALUE;
        }
        int fromId = parseStageNumber(progress.getStageId(), Integer.MIN_VALUE);
        if (fromId != Integer.MIN_VALUE) {
            return fromId;
        }
        return progress.getStageIndex();
    }

    private int parseStageNumber(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void addSectionTitle(List<DetailLine> lines, String title) {
        addBlankLine(lines);
        lines.add(new DetailLine(LostTalesSkyrimUiStyle.uppercase(title), LostTalesSkyrimUiStyle.GOLD, 8, false));
    }

    private void addBlankLine(List<DetailLine> lines) {
        lines.add(new DetailLine("", LostTalesSkyrimUiStyle.TEXT, 0, false));
    }

    private void addSeparator(List<DetailLine> lines, int indent, int color) {
        DetailLine line = new DetailLine("", color, indent, false);
        line.separator = true;
        lines.add(line);
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

    private int drawWrappedText(String text, int x, int y, int width, int color, int maxHeight) {
        if (text == null || text.length() == 0 || maxHeight <= 0) {
            return y;
        }
        List<String> lines = this.fontRendererObj.listFormattedStringToWidth(text, width);
        int lineY = y;
        int bottom = y + maxHeight;
        for (String line : lines) {
            if (lineY + 9 > bottom) {
                this.fontRendererObj.drawStringWithShadow("...", x, lineY, color);
                return bottom;
            }
            this.fontRendererObj.drawStringWithShadow(line, x, lineY, color);
            lineY += 10;
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
        ChatPageSearch query = ChatPageSearch.of(rawQuery);
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
                                         ChatPageSearch query) {
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
        String lastCategory = null;
        for (int i = 0; i < quests.size(); i++) {
            ClientQuestEntry quest = quests.get(i);
            String category = quest.getCategory().length() == 0
                    ? translate("gui.losttales.quest.category.misc") : quest.getCategory();
            if (!category.equals(lastCategory)) {
                rows.add(QuestListRow.category(category));
                lastCategory = category;
            }
            int height = Math.round(LIST_ROW_HEIGHT
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

    private int getQuestRowY(List<QuestListRow> rows, int questIndex, int startY) {
        int y = startY;
        for (QuestListRow row : rows) {
            if (!row.category && row.questIndex == questIndex) {
                return y;
            }
            y += row.height;
        }
        return Integer.MIN_VALUE;
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
        int selectedBottom = selectedY + LIST_ROW_HEIGHT;
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
        return Math.max(0, lines.size() * DETAIL_LINE_HEIGHT
                - (int)layout.detail().height);
    }

    /** How tall the list's viewport is; at least a row, for the maths. */
    private int listHeight() {
        return Math.max(LIST_ROW_HEIGHT, (int)layout().list().height);
    }

    /**
     * The list row under the point, or null outside the list viewport. A row
     * spans the list's full width, so the hover highlight, the click and the
     * pointer all answer for the same pixels.
     */
    private QuestListRow rowAt(List<QuestListRow> rows,
                               QuestJournalLayout layout, int mouseX,
                               int mouseY) {
        if (rows == null || !layout.list().contains(mouseX, mouseY)) {
            return null;
        }
        int relativeY = (int)(mouseY - layout.list().top) + this.listScroll;
        int y = 0;
        for (QuestListRow row : rows) {
            if (relativeY >= y && relativeY < y + row.height) {
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
            Hovered where = hoverAt(layout, mouseX, mouseY);
            if (where == Hovered.ACTION) {
                runAction(getSelectedQuest(), ACTIONS[this.hoveredIndex]);
                return true;
            }
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
        if (quest == null || !quest.isActive()) {
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

    /** The wheel scrolls the half it is turned over, a line's twelve pixels a line. */
    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        if (this.fontRendererObj == null || lines == 0) {
            return false;
        }
        int amount = lines * LIST_ROW_HEIGHT;
        if (layout().list().contains(pageX(box, x), pageY(box, y))) {
            scrollList(amount);
        } else {
            scrollDetails(amount);
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

    /** Cuts what is drawn next to a box of the page, where the page really stands on the screen. */
    private void enableScissor(int x, int y, int width, int height) {
        ScaledResolution scaled = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);
        int scale = scaled.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int)Math.round((this.clipX + x) * scale),
                this.mc.displayHeight - (int)Math.round((this.clipY + y + height) * scale),
                Math.max(0, width * scale), Math.max(0, height * scale));
    }

    private void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private enum QuestFilter {
        ALL("gui.losttales.quest.filter.all",
                "gui.losttales.quest.search.all"),
        ACTIVE("gui.losttales.quest.filter.active",
                "gui.losttales.quest.search.active"),
        COMPLETED("gui.losttales.quest.filter.completed",
                "gui.losttales.quest.search.completed"),
        HISTORY("gui.losttales.quest.filter.history",
                "gui.losttales.quest.search.history");

        private final String labelKey;
        /** What the well says while this filter is in force. */
        private final String promptKey;

        QuestFilter(String labelKey, String promptKey) {
            this.labelKey = labelKey;
            this.promptKey = promptKey;
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

        private static QuestListRow category(String label) {
            return new QuestListRow(true, label == null ? translate("gui.losttales.quest.category.misc") : label, null, -1, CATEGORY_ROW_HEIGHT);
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
    }
}
