package com.ninuna.losttales.gui.screen;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.client.gui.LostTalesGuiPointerTargets;
import com.ninuna.losttales.client.gui.LostTalesPointerInteractable;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimations;
import com.ninuna.losttales.client.gui.controlbar.LostTalesControlBar;
import com.ninuna.losttales.client.gui.controlbar.LostTalesControlBar.Hint;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.client.quest.ClientQuestCatalog;
import com.ninuna.losttales.client.quest.ClientQuestEntry;
import com.ninuna.losttales.client.quest.LostTalesClientQuestDefinitionStore;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.hud.compass.LostTalesCompassHudRenderHelper;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.client.chat.LostTalesChatGui;
import com.ninuna.losttales.gui.screen.quest.QuestJournalLayout;
import com.ninuna.losttales.gui.screen.quest.QuestSearchQuery;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiButton;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiTextField;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiEasing;
import com.ninuna.losttales.client.gui.animation.LostTalesUiEasing;
import com.ninuna.losttales.client.gui.animation.LostTalesUiTransition;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
/**
 * Skyrim-inspired quest journal layout for 1.7.10.
 *
 * The screen stays client-only and reads the shared presentation assembled from
 * Lost Tales and LOTR's synchronized quest state.
 */
public class LostTalesQuestJournalGui extends GuiScreen
        implements LostTalesPointerInteractable {
    private static final int LIST_ROW_HEIGHT = QuestJournalLayout.ROW_STRIDE;
    private static final int CATEGORY_ROW_HEIGHT = QuestJournalLayout.CATEGORY_STRIDE;
    private static final int DETAIL_LINE_HEIGHT = 10;
    private static final long DOUBLE_CLICK_TRACK_MS = 350L;
    /** Where a quest row's glyph and title stand inside its row. */
    private static final int ROW_GLYPH_X = 3;
    private static final int ROW_TITLE_X = 15;
    /** The square a category's chevron answers on. */
    private static final int CHEVRON_BOX = 9;
    /** The longest a search may be; a title is shorter than this. */
    private static final int MAX_SEARCH = 64;

    /** What the pointer is on: asked once a frame, read by every draw. */
    private enum Hovered { NOTHING, FILTER, CATEGORY, QUEST, ACTION, SEARCH }

    /** How long a fold, a glide or the search opening takes. */
    private static final int MOTION_MILLIS = 140;
    /** Seconds a scroll takes to reach where it was sent. */
    private static final double SCROLL_EASE_SECONDS = 0.09D;

    /** The filters, in the order their buttons stand. */
    private static final QuestFilter[] FILTERS = QuestFilter.values();

    /** The actions a quest being read offers, in the order they stand. */
    private enum QuestAction { TRACK, SHARE, ABANDON }

    private static final QuestAction[] ACTIONS = QuestAction.values();

    private final GuiScreen parent;
    private final Set<String> collapsedCategories = new HashSet<String>();
    private Hovered hovered = Hovered.NOTHING;
    private int hoveredIndex = -1;
    private String hoveredCategory = "";
    /** The search field, made once the screen knows its font. */
    private LostTalesUiTextField searchField;
    /**
     * A motion each for the controls the pointer can press, so they dip,
     * rise and settle the way every other button in the mod does. The
     * magnifier turns on its handle, which is lopsided enough to show a
     * turn; a labelled button carries words, and a category's plus and
     * minus are four-fold symmetric, so both only lift
     * ({@code LostTalesUiGlyphTurnTest} says which glyphs may turn).
     */
    private final LostTalesUiButtonMotion searchMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.TURN);
    private final Map<String, LostTalesUiButtonMotion> filterMotions =
            new HashMap<String, LostTalesUiButtonMotion>();
    private final Map<String, LostTalesUiButtonMotion> categoryMotions =
            new HashMap<String, LostTalesUiButtonMotion>();
    /** The frame the visible list was built for; see {@link #getVisibleQuests}. */
    private List<ClientQuestEntry> visibleQuests;
    private long visibleFrame = -1L;
    private QuestFilter visibleFilter;
    private String visibleQuery = "";
    private long frameCounter;
    private final LostTalesUiTransition searchOpen = new LostTalesUiTransition();
    private boolean searching;
    /**
     * How far each category is unfolded, one transition each: a row's
     * height is its full height times this, so a category opens and
     * closes by growing rather than appearing.
     */
    private final Map<String, LostTalesUiTransition> categoryOpen =
            new HashMap<String, LostTalesUiTransition>();
    /**
     * How far the search has opened as it was last drawn: the field
     * answers to a press on the box it is actually drawn in, not on the
     * one it will have once it finishes opening.
     */
    private float searchShown;
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

    public LostTalesQuestJournalGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        LostTalesClientQuestDefinitionStore.ensureLoaded(this.mc.getResourceManager());
        this.buttonList.clear();
        if (this.searchField == null && this.fontRendererObj != null) {
            this.searchField = new LostTalesUiTextField(this.fontRendererObj,
                    0, 0, 10, 10);
            this.searchField.setMaxStringLength(MAX_SEARCH);
        }
        clampSelectionAndScroll();
        ensureSelectedQuestVisible();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        advanceMotion();
        clampSelectionAndScroll();
        QuestJournalLayout layout = layout();
        this.hovered = hoverAt(layout, mouseX, mouseY);

        drawBackdrop(layout);
        drawHeader(layout);

        List<ClientQuestEntry> quests = getVisibleQuests();
        drawDivider(layout);
        drawQuestList(quests, layout);
        drawQuestDetails(quests, layout);
        drawActions(layout);
        drawFooterHelp();

        super.drawScreen(mouseX, mouseY, partialTicks);
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
        if (!motionWanted()) {
            this.listShown = this.listScroll;
            this.detailShown = this.detailScroll;
            this.selectionShown = selectionTarget();
            return;
        }
        this.listShown = LostTalesGuiEasing.approach(this.listShown,
                this.listScroll, elapsed, SCROLL_EASE_SECONDS);
        this.detailShown = LostTalesGuiEasing.approach(this.detailShown,
                this.detailScroll, elapsed, SCROLL_EASE_SECONDS);
        double target = selectionTarget();
        this.selectionShown = Double.isNaN(this.selectionShown)
                || Double.isNaN(target) ? target
                : LostTalesGuiEasing.approach(this.selectionShown, target,
                        elapsed, SCROLL_EASE_SECONDS);
    }

    /** Whether the player wants motion at all; the chat's own setting. */
    private static boolean motionWanted() {
        return LostTalesConfig.enableGuiAnimations
                && !LostTalesConfig.reducedGuiMotion;
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
        LostTalesUiTransition transition = this.categoryOpen.get(category);
        if (transition == null) {
            transition = new LostTalesUiTransition();
            transition.settle(!this.collapsedCategories.contains(category));
            this.categoryOpen.put(category, transition);
        }
        boolean open = !this.collapsedCategories.contains(category);
        if (!motionWanted()) {
            transition.settle(open);
            return open ? 1.0F : 0.0F;
        }
        return transition.advance(System.nanoTime(), open, MOTION_MILLIS,
                LostTalesUiEasing.SETTLE);
    }

    /** The screen's own geometry, worked out fresh every frame. */
    private QuestJournalLayout layout() {
        return new QuestJournalLayout(this.width,
                this.height - LostTalesControlBar.HEIGHT);
    }

    /**
     * The world behind, dimmed, and one surface for each strip. A strip
     * does not lie over the backdrop: each is its own flat colour, so
     * nothing is ever two translucent layers deep.
     */
    private void drawBackdrop(QuestJournalLayout layout) {
        if (!LostTalesGuiAnimations.isManagingBackdrop(this)) {
            drawRect(0, 0, this.width, this.height,
                    LostTalesColors.withAlpha(LostTalesColors.PLUM_BLACK, 0xC8));
        }
        drawStrip(layout.header());
        drawStrip(layout.actions());
        drawRule(layout.headerRule(), LostTalesColors.BORDER);
        drawRule(layout.actionRule(), LostTalesColors.BORDER);
    }

    private void drawStrip(LostTalesUiHitBox box) {
        drawRect((int)box.left, (int)box.top, (int)box.right(),
                (int)box.bottom(),
                LostTalesColors.withAlpha(LostTalesColors.PLUM_DARK, 0x9A));
    }

    private void drawRule(LostTalesUiHitBox box, int color) {
        if (box.width <= 0.0D || box.height <= 0.0D) {
            return;
        }
        drawRect((int)box.left, (int)box.top, (int)box.right(),
                (int)box.bottom(), color);
    }

    /**
     * The title on the left, the filters on the right. Every control is
     * a framed button drawn from the same box its hit test asks, so a
     * press lands exactly where the frame is.
     */
    private void drawHeader(QuestJournalLayout layout) {
        LostTalesUiHitBox header = layout.header();
        LostTalesSkyrimUiStyle.beginContent();
        this.fontRendererObj.drawStringWithShadow(
                translate("gui.losttales.quest.title"),
                QuestJournalLayout.MARGIN, textTop(header),
                LostTalesColors.rgb(LostTalesColors.TEXT_BRIGHT));

        float open = motionWanted()
                ? this.searchOpen.advance(System.nanoTime(), this.searching,
                        MOTION_MILLIS, LostTalesUiEasing.SETTLE)
                : this.searching ? 1.0F : 0.0F;
        this.searchShown = open;
        drawSearchControl(layout, open);
        if (open < 1.0F) {
            int[] widths = filterWidths();
            double left = filtersLeft(layout, widths);
            for (int index = 0; index < FILTERS.length; index++) {
                drawFramedLabel(QuestJournalLayout.buttonAt(header, left,
                                widths, index),
                        translate(FILTERS[index].labelKey),
                        FILTERS[index] == this.filter,
                        this.hovered == Hovered.FILTER
                                && this.hoveredIndex == index,
                        LostTalesColors.rgb(LostTalesColors.TEXT_BRIGHT));
            }
        }
    }

    /**
     * The magnifier at the header's right, and the field that grows out
     * of it over the filters as it opens. The filters are still in force
     * while a search is being typed; the search narrows what they leave.
     */
    private void drawSearchControl(QuestJournalLayout layout, float open) {
        LostTalesUiHitBox button = searchBox(layout);
        if (open > 0.0F) {
            LostTalesUiHitBox well = searchFieldBox(layout, open);
            LostTalesUiFramedButton.drawSurface((float)well.left,
                    (float)well.top, (float)well.width, (float)well.height,
                    0.0F, 0xC8);
            LostTalesUiFramedButton.drawInk((float)well.left, (float)well.top,
                    (int)well.width, (int)well.height, 0.0F, 0xFF);
            if (this.searchField != null && open > 0.6F) {
                placeSearchField(well);
                LostTalesSkyrimUiStyle.beginContent();
                this.searchField.drawTextBox();
                this.searchField.drawHint(
                        translate("gui.losttales.quest.search"));
            }
        }
        boolean hovered = this.hovered == Hovered.SEARCH;
        this.searchMotion.advance(System.nanoTime(),
                this.searching || hovered, hovered,
                hovered && Mouse.isButtonDown(0), motionWanted());
        float lit = this.searchMotion.lit();
        LostTalesUiFramedButton.drawSurface((float)button.left,
                (float)button.top, (float)button.width, (float)button.height,
                lit, 0xC8);
        LostTalesUiFramedButton.drawInk((float)button.left, (float)button.top,
                (int)button.width, (int)button.height, lit, 0xFF);
        LostTalesSkyrimUiStyle.beginContent();
        LostTalesUiButton.drawGlyph(LostTalesUiSheet.SEARCH,
                LostTalesUiSheet.SEARCH_HOVER, this.searchMotion,
                (float)Math.round(button.left + (button.width
                        - LostTalesUiSheet.SEARCH.getWidth()) / 2.0D),
                (float)Math.round(button.top + (button.height
                        - LostTalesUiSheet.SEARCH.getHeight()) / 2.0D), 0xFF);
    }

    /** The magnifier's own square, at the header's right end. */
    private LostTalesUiHitBox searchBox(QuestJournalLayout layout) {
        LostTalesUiHitBox header = layout.header();
        int size = LostTalesUiFramedButton.HEIGHT;
        return new LostTalesUiHitBox(
                header.right() - QuestJournalLayout.CONTROL_INSET - size,
                Math.floor(header.top + (header.height - size) / 2.0D),
                size, size);
    }

    /** The field's well, growing left out of the magnifier as it opens. */
    private LostTalesUiHitBox searchFieldBox(QuestJournalLayout layout,
                                             float open) {
        LostTalesUiHitBox button = searchBox(layout);
        double full = Math.max(LostTalesUiFramedButton.MIN_SIZE,
                Math.min(200.0D, layout.header().width * 0.4D));
        double width = Math.max(LostTalesUiFramedButton.MIN_SIZE,
                Math.round(full * open));
        return new LostTalesUiHitBox(
                button.left - QuestJournalLayout.CONTROL_GAP - width,
                button.top, width, button.height);
    }

    private void placeSearchField(LostTalesUiHitBox well) {
        this.searchField.xPosition = (int)well.left
                + LostTalesUiFramedButton.WIDE_INSET;
        this.searchField.yPosition = textTop(well);
        this.searchField.width = Math.max(4, (int)well.width
                - 2 * LostTalesUiFramedButton.WIDE_INSET);
    }

    /** Opens or closes the search, moving the keys with it. */
    private void toggleSearch() {
        this.searching = !this.searching;
        if (this.searchField != null) {
            this.searchField.setFocused(this.searching);
            if (!this.searching) {
                this.searchField.setText("");
            }
        }
        this.selectedQuestIndex = 0;
        this.listScroll = 0;
        this.detailScroll = 0;
        clampSelectionAndScroll();
    }

    /** What is being searched for; empty while the search is closed. */
    private String searchQuery() {
        return this.searching && this.searchField != null
                ? this.searchField.getText().trim() : "";
    }

    /** The row a line of text stands on to be centred in a strip. */
    private static int textTop(LostTalesUiHitBox strip) {
        return (int)Math.round(strip.top + (strip.height - 8) / 2.0D);
    }

    /** Each filter button's width: its label with the frame round it. */
    private int[] filterWidths() {
        int[] widths = new int[FILTERS.length];
        for (int index = 0; index < FILTERS.length; index++) {
            widths[index] = QuestJournalLayout.buttonWidthFor(
                    this.fontRendererObj.getStringWidth(
                            translate(FILTERS[index].labelKey)));
        }
        return widths;
    }

    /** Where the filter row begins: right-aligned in the header strip. */
    private double filtersLeft(QuestJournalLayout layout, int[] widths) {
        return layout.header().right() - QuestJournalLayout.CONTROL_INSET
                - QuestJournalLayout.buttonsWidth(widths);
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
                hovered && Mouse.isButtonDown(0), motionWanted());
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
        LostTalesUiButtonMotion motion = this.filterMotions.get(key);
        if (motion == null) {
            motion = new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);
            this.filterMotions.put(key, motion);
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
        int top = (int)Math.round(y + (CATEGORY_ROW_HEIGHT - 8) / 2.0D) + 1;

        LostTalesSkyrimUiStyle.beginContent();
        LostTalesUiButtonMotion motion = categoryMotion(label);
        motion.advance(System.nanoTime(), lit, motionWanted());
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
            drawRect(ruleLeft, top + 3, (int)rows.right(), top + 4,
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
            drawRect((int)rows.left - 2, (int)Math.round(y),
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
        drawRect((int)rows.left - 2, (int)Math.round(y), (int)rows.right(),
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

    /** A category's name, translated where the code chose it. */
    private static String categoryName(String category) {
        if (category == null || category.length() == 0) {
            return translate("gui.losttales.quest.category.misc");
        }
        String key = "gui.losttales.quest.category."
                + category.toLowerCase(Locale.ROOT).replace(' ', '_')
                        .replace("-", "");
        String translated = translate(key);
        return key.equals(translated) ? category : translated;
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
        drawRect((int)bar.left, (int)bar.top, (int)bar.right(),
                (int)bar.bottom(),
                LostTalesColors.withAlpha(LostTalesColors.PLUM_BLACK, 0x8C));
        LostTalesUiHitBox handle = QuestJournalLayout.scrollHandle(bar,
                contentHeight, scroll);
        drawRect((int)handle.left, (int)Math.round(handle.top),
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
            drawRect((int)rows.left + line.indent, y + 4,
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
     * across the bottom, and the control bar's hints beside them.
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
        if (action == QuestAction.ABANDON) {
            return quest.isActive()
                    && quest.getSource() == ClientQuestEntry.Source.LOST_TALES;
        }
        if (action == QuestAction.TRACK) {
            return quest.isActive();
        }
        return true;
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
            // The chat opens with the quest's own token already typed, so
            // the share is written the way a player writes one.
            this.mc.displayGuiScreen(new LostTalesChatGui(
                    ChatShareTokenParser.buildToken(ChatShareKind.QUEST,
                            quest.getTitle(), 1)));
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesQuestActionPacket(
                        LostTalesQuestActionPacket.ACTION_ABANDON,
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
        if (searchBox(layout).contains(mouseX, mouseY)) {
            return Hovered.SEARCH;
        }
        if (this.searching) {
            // The filters are behind the open field and answer to nothing.
            return questRowHover(layout, mouseX, mouseY);
        }
        int[] filters = filterWidths();
        double filtersX = filtersLeft(layout, filters);
        for (int index = 0; index < FILTERS.length; index++) {
            if (QuestJournalLayout.buttonAt(layout.header(), filtersX, filters,
                    index).contains(mouseX, mouseY)) {
                this.hoveredIndex = index;
                return Hovered.FILTER;
            }
        }
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
            String remaining = formatRemainingTime(progress.getRemainingTicks(this.mc.theWorld.getTotalWorldTime()));
            addWrappedLines(lines, translate("gui.losttales.quest.remaining", remaining), remaining.equals("expired") ? LostTalesSkyrimUiStyle.RED : LostTalesSkyrimUiStyle.GOLD, 8, width - 16);
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
                            history.getDetail()),
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
        List<String> result = new ArrayList<String>();
        if (quest == null || quest.getRewards().isEmpty()) {
            return result;
        }
        for (Map.Entry<String, String> entry : quest.getRewards().entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue();
            if ("experience".equalsIgnoreCase(key) || "xp".equalsIgnoreCase(key) || "experiencePoints".equalsIgnoreCase(key)) {
                result.add(value + " experience");
            } else if ("levels".equalsIgnoreCase(key) || "experienceLevels".equalsIgnoreCase(key) || "xpLevels".equalsIgnoreCase(key)) {
                result.add(value + " experience level" + ("1".equals(value) ? "" : "s"));
            } else if ("items".equalsIgnoreCase(key) || "stacks".equalsIgnoreCase(key) || "itemStacks".equalsIgnoreCase(key)) {
                String[] parts = value.replace(';', ',').split(",");
                for (String part : parts) {
                    String reward = formatItemReward(part);
                    if (reward.length() > 0) {
                        result.add(reward);
                    }
                }
            } else if ("item".equalsIgnoreCase(key) || "itemId".equalsIgnoreCase(key) || "stack".equalsIgnoreCase(key)) {
                String reward = formatItemReward(value);
                if (reward.length() > 0) {
                    result.add(reward);
                }
            } else if (value.length() > 0) {
                result.add(prettifyKey(key) + ": " + value);
            }
        }
        if (result.isEmpty()) {
            result.add(translate("gui.losttales.quest.reward.pending"));
        }
        return result;
    }

    private String formatItemReward(String value) {
        String spec = value == null ? "" : value.trim();
        if (spec.length() == 0) {
            return "";
        }
        int count = 1;
        int meta = 0;
        int star = spec.lastIndexOf('*');
        if (star >= 0 && star + 1 < spec.length()) {
            count = Math.max(1, parseStageNumber(spec.substring(star + 1), 1));
            spec = spec.substring(0, star);
        }
        int at = spec.lastIndexOf('@');
        if (at >= 0 && at + 1 < spec.length()) {
            meta = Math.max(0, parseStageNumber(spec.substring(at + 1), 0));
            spec = spec.substring(0, at);
        }
        if (spec.indexOf(':') < 0) {
            spec = "minecraft:" + spec;
        }
        Object object = Item.itemRegistry.getObject(spec);
        String name = spec;
        if (object instanceof Item) {
            try {
                name = new ItemStack((Item)object, 1, meta).getDisplayName();
            } catch (RuntimeException ignored) {
                name = prettifyKey(spec.substring(spec.indexOf(':') + 1));
            }
        } else if (spec.indexOf(':') >= 0) {
            name = prettifyKey(spec.substring(spec.indexOf(':') + 1));
        }
        return (count > 1 ? count + "x " : "") + name;
    }

    private String buildObjectiveLine(LostTalesQuestProgress progress, LostTalesQuestObjectiveDefinition objective, boolean currentStage, boolean questCompleted) {
        return LostTalesQuestObjectiveTextHelper.buildObjectiveLine(progress, objective, currentStage, questCompleted, false, false);
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

    private String formatRemainingTime(long ticks) {
        if (ticks <= 0L) {
            return "expired";
        }
        long days = ticks / 24000L;
        long remainder = ticks % 24000L;
        long hours = remainder / 1000L;
        long minutes = (remainder % 1000L) * 60L / 1000L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1L, minutes) + "m";
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

    private void drawFooterHelp() {
        if (this.mc == null || this.fontRendererObj == null) {
            return;
        }
        List<Hint> hints = new ArrayList<Hint>();
        hints.add(Hint.wheel(this.mc, this.fontRendererObj,
                translate("gui.losttales.quest.hint.scroll")));
        hints.add(Hint.mouseButton(this.mc, this.fontRendererObj, 0,
                translate("gui.losttales.quest.hint.select")));
        hints.add(Hint.key(this.mc, this.fontRendererObj,
                Keyboard.KEY_SPACE,
                translate("gui.losttales.quest.hint.track")));
        hints.add(Hint.key(this.mc, this.fontRendererObj,
                Keyboard.KEY_F,
                translate("gui.losttales.quest.hint.filter")));
        hints.add(Hint.binding(this.mc, this.fontRendererObj,
                LostTalesKeyBindings.getQuestJournalKeyBinding(),
                translate("gui.losttales.quest.hint.close")));
        hints.add(Hint.binding(this.mc, this.fontRendererObj,
                LostTalesKeyBindings.getCharacterMenuKeyBinding(),
                translate("gui.losttales.quest.hint.character")));
        String sync = translate(
                LostTalesClientQuestProgressStore.hasReceivedSync()
                        ? "gui.losttales.quest.sync.ready"
                        : "gui.losttales.quest.sync.waiting");
        LostTalesControlBar.render(this, this.mc, this.fontRendererObj,
                this.width, this.height, hints, 4, 0,
                Arrays.asList(sync), true);
    }

    /**
     * The quests the filter and the search leave, built once a frame.
     * Every part of the screen asks for it several times over — the
     * rows, the glide, the pointer, the detail — and searching measures
     * each quest's words, so it is worked out once and handed back.
     */
    private List<ClientQuestEntry> getVisibleQuests() {
        String query = searchQuery();
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
        QuestSearchQuery query = QuestSearchQuery.of(rawQuery);
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
                                         QuestSearchQuery query) {
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

    private String prettifyKey(String key) {
        if (key == null || key.length() == 0) {
            return "";
        }
        String[] parts = key.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ENGLISH));
            }
        }
        return builder.length() == 0 ? key : builder.toString();
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
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            QuestJournalLayout layout = layout();
            Hovered where = hoverAt(layout, mouseX, mouseY);
            if (where == Hovered.SEARCH) {
                toggleSearch();
                return;
            }
            if (this.searching && this.searchField != null
                    && searchFieldBox(layout, this.searchShown)
                            .contains(mouseX, mouseY)) {
                this.searchField.setFocused(true);
                return;
            }
            if (where == Hovered.FILTER) {
                setFilter(FILTERS[this.hoveredIndex]);
                return;
            }
            if (where == Hovered.ACTION) {
                runAction(getSelectedQuest(), ACTIONS[this.hoveredIndex]);
                return;
            }
            QuestListRow row = rowAt(buildQuestListRows(getVisibleQuests()),
                    layout, mouseX, mouseY);
            if (row != null && row.category) {
                toggleCategory(row.label);
                return;
            }
            if (row != null && row.quest != null) {
                long now = System.currentTimeMillis();
                boolean doubleClick = this.lastClickedQuestIndex == row.questIndex && now - this.lastQuestClickMs <= DOUBLE_CLICK_TRACK_MS;
                setSelectedQuestIndex(row.questIndex);
                if (doubleClick) {
                    toggleSelectedQuestTracking(row.quest);
                }
                this.lastClickedQuestIndex = row.questIndex;
                this.lastQuestClickMs = now;
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /**
     * Every list row answers to a click: a category header folds or unfolds
     * its category and a quest row selects its quest.
     */
    @Override
    public boolean isPointerOverInteractable(int mouseX, int mouseY) {
        if (hoverAt(layout(), mouseX, mouseY) != Hovered.NOTHING) {
            return true;
        }
        return LostTalesGuiPointerTargets.isOverEnabledButton(this, mouseX, mouseY);
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

    @Override
    public void handleMouseInput() {
        int eventMouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int eventMouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        eventMouseX = LostTalesGuiAnimations.inverseMouseX(
                this, eventMouseX);
        eventMouseY = LostTalesGuiAnimations.inverseMouseY(
                this, eventMouseY);
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int notches = wheel / 120;
            if (notches == 0) {
                notches = wheel > 0 ? 1 : -1;
            }
            int amount = -notches * 24;
            QuestJournalLayout layout = layout();
            if (layout.list().contains(eventMouseX, eventMouseY)) {
                scrollList(amount);
            } else {
                scrollDetails(amount);
            }
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // A field taking the keys answers first, so a letter types
        // rather than reaching a shortcut; Escape closes the search
        // before it closes the screen.
        if (this.searching && this.searchField != null
                && this.searchField.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                toggleSearch();
                return;
            }
            if (keyCode == Keyboard.KEY_UP) {
                setSelectedQuestIndex(this.selectedQuestIndex - 1);
                return;
            }
            if (keyCode == Keyboard.KEY_DOWN) {
                setSelectedQuestIndex(this.selectedQuestIndex + 1);
                return;
            }
            if (keyCode == Keyboard.KEY_RETURN) {
                this.searchField.setFocused(false);
                return;
            }
            if (this.searchField.textboxKeyTyped(typedChar, keyCode)) {
                this.selectedQuestIndex = 0;
                this.listScroll = 0;
                this.detailScroll = 0;
                clampSelectionAndScroll();
                return;
            }
        }
        if (keyCode == Keyboard.KEY_F && isCtrlKeyDown()) {
            if (!this.searching) {
                toggleSearch();
            } else if (this.searchField != null) {
                this.searchField.setFocused(true);
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || LostTalesKeyBindings.isQuestJournalKey(keyCode)) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (LostTalesKeyBindings.isCharacterMenuKey(keyCode)) {
            this.mc.displayGuiScreen(new LostTalesCharacterMenuGui(this.parent));
            return;
        }
        if (keyCode == Keyboard.KEY_SPACE || keyCode == Keyboard.KEY_RETURN) {
            toggleSelectedQuestTracking(getSelectedQuest());
            return;
        }
        if (keyCode == Keyboard.KEY_F) {
            cycleFilter();
            return;
        }
        if (keyCode == Keyboard.KEY_UP) {
            setSelectedQuestIndex(this.selectedQuestIndex - 1);
            return;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            setSelectedQuestIndex(this.selectedQuestIndex + 1);
            return;
        }
        if (keyCode == Keyboard.KEY_PRIOR) {
            scrollDetails(-80);
            return;
        }
        if (keyCode == Keyboard.KEY_NEXT) {
            scrollDetails(80);
            return;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            this.detailScroll = 0;
            return;
        }
        if (keyCode == Keyboard.KEY_END) {
            this.detailScroll = getDetailMaxScroll();
            return;
        }
        super.keyTyped(typedChar, keyCode);
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

    private void enableScissor(int x, int y, int width, int height) {
        ScaledResolution scaled = new ScaledResolution(this.mc, this.mc.displayWidth, this.mc.displayHeight);
        int scale = scaled.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, this.mc.displayHeight - (y + height) * scale, width * scale, height * scale);
    }

    private void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (this.searchField != null) {
            this.searchField.updateCursorCounter();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private enum QuestFilter {
        ALL("gui.losttales.quest.filter.all"),
        ACTIVE("gui.losttales.quest.filter.active"),
        COMPLETED("gui.losttales.quest.filter.completed"),
        HISTORY("gui.losttales.quest.filter.history");

        private final String labelKey;

        QuestFilter(String labelKey) {
            this.labelKey = labelKey;
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
