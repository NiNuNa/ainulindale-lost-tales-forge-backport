package com.ninuna.losttales.client.progression;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.quest.QuestMarks;
import com.ninuna.losttales.client.window.BarItem;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageKeys;
import com.ninuna.losttales.client.window.TabIcons;
import com.ninuna.losttales.client.window.ToolStrip;
import com.ninuna.losttales.client.window.WheelStep;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.screen.quest.QuestJournalLayout;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiCutName;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiTheme;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.ProgressionRequestPacket;
import com.ninuna.losttales.progression.PerkWords;
import com.ninuna.losttales.progression.ProgressionRules;
import com.ninuna.losttales.progression.ProgressionSnapshot;
import com.ninuna.losttales.progression.ProgressionState;
import com.ninuna.losttales.progression.SkillStanding;
import com.ninuna.losttales.progression.deed.DeedCategory;
import com.ninuna.losttales.progression.deed.DeedDefinition;
import com.ninuna.losttales.progression.deed.DeedSummary;
import com.ninuna.losttales.progression.deed.DeedWords;
import com.ninuna.losttales.progression.skill.PerkDefinition;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * The Skills page in the Profile view, beside the Characters page
 * (PROG-13): the character level and its perk points over the skills on
 * the left, then the deeds; on the right the skill picked, its level and
 * how far to the next, what counts toward it, and its perk tree, a perk
 * taken from the bar; or every deed, by category, and how far each
 * stands. A stand-in look, the journal's, until Nils paints its own.
 */
public final class SkillsPage extends PageContent {
    /** The code name the page is registered and remembered under. */
    public static final String PAGE_ID = "skills";
    public static final ItemStack ICON = new ItemStack(Items.experience_bottle);

    private static final int LINE_HEIGHT = 10;
    private static final int BAR_HEIGHT = 6;
    private static final int BAR_THICKNESS = 2;
    private static final int INDENT = 10;
    private static final String TAKE = "take";
    private static final ToolStrip.Panel LIST_PANEL = new ToolStrip.Panel(LostTalesUiSheet.ITEM,
            LostTalesUiSheet.ITEM_HOVER, "gui.losttales.skills.list.show",
            "gui.losttales.skills.list.hide");

    private final Minecraft mc = Minecraft.getMinecraft();
    private FontRenderer font;
    private int width;
    private int height;
    private double clipX;
    private double clipY;
    private boolean listOut = true;
    private Boolean wasWide;
    /** The skill read, or {@link #DEEDS} for the deeds; empty for the first skill. */
    private String chosen = "";
    /** The perk picked in the tree, whose next rank the bar takes; empty for none. */
    private String chosenPerk = "";
    private int listScroll;
    private int detailScroll;
    private double listShown;
    private double detailShown;
    private long lastFrameNanos;
    private int hoveredRow = -1;
    private String hoveredPerk = "";

    /** What the list's last row reads: every deed. */
    private static final String DEEDS = "#deeds";

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX, double clipY,
                     double pointerX, double pointerY, float partialTicks, int alpha) {
        this.width = (int) Math.floor(box.width);
        this.height = (int) Math.floor(box.height);
        this.clipX = clipX;
        this.clipY = clipY;
        this.font = minecraft.fontRenderer;
        boolean wide = this.width >= QuestJournalLayout.MIN_SPLIT_WIDTH;
        if (this.wasWide == null || wide != this.wasWide.booleanValue()) {
            this.listOut = wide;
        }
        this.wasWide = Boolean.valueOf(wide);
        advanceMotion();
        int mouseX = Double.isNaN(pointerX) ? Integer.MIN_VALUE / 2
                : (int) Math.floor(pointerX - box.left);
        int mouseY = Double.isNaN(pointerY) ? Integer.MIN_VALUE / 2
                : (int) Math.floor(pointerY - box.top);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef((float) box.left, (float) box.top, 0.0F);
            QuestJournalLayout layout = layout();
            List<Row> rows = rows();
            List<Line> lines = lines((int) layout.detailRows().width);
            hover(layout, rows, lines, mouseX, mouseY);
            LostTalesUiHitBox divider = layout.divider();
            if (divider.width > 0.0D) {
                Gui.drawRect((int) divider.left, (int) divider.top, (int) divider.right(),
                        (int) divider.bottom(), LostTalesColors.BORDER_DIM);
            }
            drawList(minecraft, layout, rows, alpha);
            drawDetail(layout, lines, alpha);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private void advanceMotion() {
        long now = System.nanoTime();
        double elapsed = this.lastFrameNanos == 0L ? 0.0D
                : (now - this.lastFrameNanos) / 1000000000.0D;
        this.lastFrameNanos = now;
        this.listShown = Motions.followTravel(MotionIds.SCREEN_JOURNAL_SCROLL, this.listShown,
                this.listScroll, elapsed);
        this.detailShown = Motions.followTravel(MotionIds.SCREEN_JOURNAL_SCROLL,
                this.detailShown, this.detailScroll, elapsed);
    }

    private QuestJournalLayout layout() {
        return new QuestJournalLayout(this.width, this.height, this.listOut);
    }

    /* ---- The list: the character, its skills, the deeds ---- */

    private List<Row> rows() {
        List<Row> rows = new ArrayList<Row>();
        rows.add(new Row(null, false));
        for (SkillDefinition skill : ClientProgression.skills()) {
            rows.add(new Row(skill, false));
        }
        rows.add(new Row(null, true));
        return rows;
    }

    private String chosenId() {
        if (this.chosen.length() > 0
                && (DEEDS.equals(this.chosen) || ClientProgression.skill(this.chosen) != null)) {
            return this.chosen;
        }
        List<SkillDefinition> skills = ClientProgression.skills();
        return skills.isEmpty() ? DEEDS : skills.get(0).getId();
    }

    private void drawList(Minecraft minecraft, QuestJournalLayout layout, List<Row> rows,
                          int alpha) {
        LostTalesUiHitBox list = layout.list();
        if (list.width <= 0.0D || list.height <= 0.0D) {
            return;
        }
        int rowHeight = MenuWindow.rowHeight();
        int maxScroll = Math.max(0, rows.size() * rowHeight - (int) list.height);
        this.listScroll = clamp(this.listScroll, 0, maxScroll);
        int surfaceAlpha = WindowLists.pageSurfaceAlpha(minecraft, alpha);
        String chosen = chosenId();
        int top = listTop(list);
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            int y = top + index * rowHeight;
            int litTop = Math.max((int) list.top, y);
            int litBottom = Math.min((int) list.bottom(), y + rowHeight);
            if (litBottom > litTop && row.selectable()
                    && (row.id().equals(chosen) || index == this.hoveredRow)) {
                WindowLists.drawLitRow(0.0D, this.width, list.left, litTop, list.right(),
                        litBottom, surfaceAlpha);
            }
        }
        LostTalesUiHitBox inner = layout.listRows();
        enableScissor((int) list.left, (int) list.top, (int) list.width, (int) list.height);
        try {
            for (int index = 0; index < rows.size(); index++) {
                int y = top + index * rowHeight;
                if (y + rowHeight > list.top && y < list.bottom()) {
                    drawRow(rows.get(index), index, inner, y, rowHeight, chosen, alpha);
                }
            }
        } finally {
            disableScissor();
        }
        WindowLists.drawScroll(list.left, 0.0D, list.right(), this.height, list.top,
                list.bottom(), this.listShown, maxScroll, alpha);
    }

    /**
     * One row of the list: the heading with the points to spend at its
     * end, or a skill's (or the deeds') name and its level. A name too
     * long for its row fades into the row's end and slides along under
     * the pointer (LOOK-37).
     */
    private void drawRow(Row row, int index, LostTalesUiHitBox inner, int y, int rowHeight,
                         String chosen, int alpha) {
        int left = (int) inner.left;
        int right = (int) inner.right();
        if (!row.selectable()) {
            ProgressionSnapshot snapshot = ClientProgression.snapshot();
            ProgressionState state = ClientProgression.state();
            int points = snapshot == null ? 0 : snapshot.getAvailablePoints();
            String spare = translate(points == 1 ? "gui.losttales.skills.point"
                    : "gui.losttales.skills.points", Integer.valueOf(points));
            int spareX = right - inkWidth(spare);
            WindowLists.drawHeading(this.font, translate("gui.losttales.skills.character",
                            Integer.valueOf(state.getCharacterLevel())),
                    left, left, spareX - TabIcons.GAP, right, y, rowHeight, true, alpha,
                    null, null, false);
            LostTalesUiInk.drawText(this.font, spare, spareX,
                    y + LostTalesUiInk.centredStart(rowHeight, LostTalesUiInk.CAP_HEIGHT),
                    LostTalesColors.rgb(points > 0 ? LostTalesColors.GOLD : LostTalesColors.TEXT_DIM),
                    alpha);
            return;
        }
        int textTop = y + LostTalesUiInk.centredStart(rowHeight, LostTalesUiInk.CAP_HEIGHT);
        boolean picked = row.id().equals(chosen);
        String name;
        String value;
        if (row.deeds) {
            name = translate("gui.losttales.skills.deeds");
            value = ClientProgression.state().deeds().size() + "/"
                    + ClientProgression.deeds().size();
        } else {
            name = PerkWords.skillName(row.skill);
            value = String.valueOf(ClientProgression.state().skill(row.skill.getId()).getLevel());
        }
        int valueX = right - inkWidth(value);
        LostTalesUiInk.drawText(this.font, value, valueX, textTop,
                LostTalesColors.rgb(LostTalesColors.GOLD), alpha);
        LostTalesUiCutName.drawText(this.font, name, left, textTop,
                valueX - TabIcons.GAP - left,
                LostTalesColors.rgb(picked ? LostTalesColors.TEXT_BRIGHT : LostTalesColors.TEXT),
                alpha, this, Integer.valueOf(index), index == this.hoveredRow);
    }

    private int listTop(LostTalesUiHitBox list) {
        return (int) list.top - (int) Math.round(this.listShown);
    }

    /* ---- The detail: a skill and its tree, or the deeds ---- */

    private List<Line> lines(int width) {
        List<Line> lines = new ArrayList<Line>();
        if (this.font == null || width <= 0) {
            return lines;
        }
        String chosen = chosenId();
        if (DEEDS.equals(chosen)) {
            deedLines(lines, width);
        } else {
            SkillDefinition skill = ClientProgression.skill(chosen);
            if (skill != null) {
                skillLines(lines, skill, width);
            }
        }
        return lines;
    }

    private void skillLines(List<Line> lines, SkillDefinition skill, int width) {
        ProgressionState state = ClientProgression.state();
        ProgressionSnapshot snapshot = ClientProgression.snapshot();
        SkillStanding standing = state.skill(skill.getId());
        double needed = snapshot == null ? 0.0D : snapshot.skillNeeded(skill.getId());
        lines.add(Line.heading(PerkWords.skillName(skill), true));
        String progress = needed > 0.0D
                ? translate("gui.losttales.skills.level", Integer.valueOf(standing.getLevel()),
                        Long.valueOf(Math.round(Math.floor(standing.getInto()))),
                        Long.valueOf(Math.round(needed)))
                : translate("gui.losttales.skills.level.top", Integer.valueOf(standing.getLevel()));
        lines.add(Line.text(progress, LostTalesColors.rgb(LostTalesColors.TEXT_BRIGHT), 0));
        lines.add(Line.bar(needed > 0.0D ? (float) Math.min(1.0D, standing.getInto() / needed)
                : 1.0F));
        wrap(lines, PerkWords.skillDescription(skill), width, WindowStyle.asideRgb(), 0, "");
        lines.add(Line.separator());
        lines.add(Line.heading(translate("gui.losttales.skills.perks"), false));
        int points = snapshot == null ? 0 : snapshot.getAvailablePoints();
        for (PerkDefinition perk : skill.getPerks()) {
            int rank = state.perkRank(skill.getId(), perk.getId());
            ProgressionRules.Refusal refusal = ProgressionRules.canTake(state, skill, perk, points);
            Line head = Line.perk(perk.getId(), perkTitle(skill, perk, rank), rank, refusal,
                    perk.isEnabled());
            lines.add(head);
            wrap(lines, PerkWords.perkDescription(skill, perk), width - INDENT,
                    WindowStyle.asideRgb(), INDENT, perk.getId());
            String needs = needsLine(skill, perk, rank);
            if (needs.length() > 0) {
                wrap(lines, needs, width - INDENT, LostTalesColors.rgb(LostTalesColors.TEXT_DIM),
                        INDENT, perk.getId());
            }
        }
    }

    /** The perk's name, its ranks taken of how many where it has several: {@code Steady Draw  1/3}. */
    private static String perkTitle(SkillDefinition skill, PerkDefinition perk, int rank) {
        String name = PerkWords.perkName(skill, perk);
        return perk.getRanks() > 1 ? name + "  " + rank + "/" + perk.getRanks() : name;
    }

    /** What the next rank needs: the level, the perk before it; empty once every rank is taken. */
    private static String needsLine(SkillDefinition skill, PerkDefinition perk, int rank) {
        if (!perk.isEnabled()) {
            return translate("gui.losttales.skills.switched_off");
        }
        if (rank >= perk.getRanks()) {
            return "";
        }
        Integer level = Integer.valueOf(perk.levelFor(rank + 1));
        PerkDefinition before = perk.getNeedsPerk().length() == 0 ? null
                : skill.perk(perk.getNeedsPerk());
        return before == null ? translate("gui.losttales.skills.needs.level", level)
                : translate("gui.losttales.skills.needs.perk", level,
                        PerkWords.perkName(skill, before, perk.getNeedsRank()));
    }

    private void deedLines(List<Line> lines, int width) {
        ProgressionState state = ClientProgression.state();
        lines.add(Line.heading(translate("gui.losttales.skills.deeds"), true));
        for (DeedCategory category : DeedCategory.values()) {
            boolean headed = false;
            for (DeedSummary deed : ClientProgression.deeds()) {
                if (!category.word().equals(deed.getCategory())) {
                    continue;
                }
                if (!headed) {
                    lines.add(Line.heading(translate(DeedWords.categoryKey(category)), false));
                    headed = true;
                }
                boolean done = state.hasDeed(deed.getId());
                String counted = done ? "" : deedProgress(deed, state.deedCounter(deed.getId()));
                lines.add(Line.deed(deedName(deed), counted, done));
                wrap(lines, deedDescription(deed), width - INDENT, WindowStyle.asideRgb(),
                        INDENT, "");
                if (deed.hasTitle()) {
                    wrap(lines, translate("gui.losttales.skills.deed.title",
                                    translate(DeedWords.titleKey(deed.getId()))),
                            width - INDENT, LostTalesColors.rgb(LostTalesColors.TEXT_DIM), INDENT, "");
                }
            }
        }
    }

    /** How far a deed stands: places reached, things counted; empty for a level, read from the skills. */
    private static String deedProgress(DeedSummary deed, long counter) {
        if (deed.getKind() == DeedDefinition.Kind.SKILL_LEVEL
                || deed.getKind() == DeedDefinition.Kind.CHARACTER_LEVEL) {
            return "";
        }
        long done = deed.getKind() == DeedDefinition.Kind.PLACES ? Long.bitCount(counter) : counter;
        return done + "/" + deed.getTarget();
    }

    private static String deedName(DeedSummary deed) {
        return deed.getName().length() > 0 ? deed.getName()
                : translate(DeedWords.nameKey(deed.getId()));
    }

    private static String deedDescription(DeedSummary deed) {
        return deed.getDescription().length() > 0 ? deed.getDescription()
                : translate(DeedWords.descriptionKey(deed.getId()));
    }

    private void wrap(List<Line> lines, String text, int width, int rgb, int indent,
                      String perkId) {
        for (Object line : this.font.listFormattedStringToWidth(text, Math.max(1, width))) {
            Line written = Line.text(String.valueOf(line), rgb, indent);
            written.perkId = perkId;
            lines.add(written);
        }
    }

    private void drawDetail(QuestJournalLayout layout, List<Line> lines, int alpha) {
        LostTalesUiHitBox area = layout.detail();
        LostTalesUiHitBox rows = layout.detailRows();
        if (area.width <= 0.0D || area.height <= 0.0D) {
            return;
        }
        int total = 0;
        for (Line line : lines) {
            total += line.height();
        }
        int maxScroll = Math.max(0, total - (int) area.height);
        this.detailScroll = clamp(this.detailScroll, 0, maxScroll);
        enableScissor((int) area.left, (int) area.top, (int) area.width, (int) area.height);
        try {
            double y = area.top - this.detailShown;
            for (Line line : lines) {
                if (y + line.height() >= area.top && y <= area.bottom()) {
                    drawLine(line, rows, (int) Math.round(y), alpha);
                }
                y += line.height();
            }
        } finally {
            disableScissor();
        }
        WindowLists.drawScroll(area.left, 0.0D, area.right(), this.height, area.top,
                area.bottom(), this.detailShown, maxScroll, alpha);
    }

    private void drawLine(Line line, LostTalesUiHitBox rows, int y, int alpha) {
        int left = (int) rows.left;
        int right = (int) rows.right();
        if (line.kind == Line.HEADING) {
            WindowLists.drawHeading(this.font, line.text, left, left, right, y, line.height(),
                    line.title, alpha);
            return;
        }
        if (line.kind == Line.SEPARATOR) {
            WindowLists.drawSeparator(left, right, y, alpha);
            return;
        }
        if (line.kind == Line.BAR) {
            int barTop = y + LostTalesUiInk.centredStart(BAR_HEIGHT, BAR_THICKNESS);
            int filled = left + Math.round((right - left) * line.share);
            LostTalesUiInk.fillRect(left, barTop, right, barTop + BAR_THICKNESS,
                    LostTalesUiInk.argb(LostTalesUiTheme.secondaryRgb(), alpha));
            if (filled > left) {
                LostTalesUiInk.fillRect(left, barTop, filled, barTop + BAR_THICKNESS,
                        LostTalesUiInk.argb(LostTalesUiTheme.accentRgb(), alpha));
            }
            return;
        }
        int textTop = y + LostTalesUiInk.centredStart(line.height(), LostTalesUiInk.CAP_HEIGHT);
        if (line.kind == Line.PERK || line.kind == Line.DEED) {
            boolean lit = line.kind == Line.PERK && (line.perkId.equals(this.chosenPerk)
                    || line.perkId.equals(this.hoveredPerk));
            LostTalesUiInk.drawText(this.font, line.mark, left, textTop, line.markRgb, alpha);
            int nameLeft = left + INDENT;
            int nameRight = right;
            if (line.value.length() > 0) {
                int valueX = right - inkWidth(line.value);
                LostTalesUiInk.drawText(this.font, line.value, valueX, textTop,
                        LostTalesColors.rgb(LostTalesColors.GOLD), alpha);
                nameRight = valueX - TabIcons.GAP;
            }
            // A perk's name too long for its line fades into the line's end
            // and slides along under the pointer (LOOK-37); a deed's only
            // fades, since nothing points at it.
            boolean perk = line.kind == Line.PERK;
            LostTalesUiCutName.drawText(this.font, line.text, nameLeft, textTop,
                    nameRight - nameLeft,
                    lit ? LostTalesColors.rgb(LostTalesColors.TEXT_BRIGHT) : line.rgb, alpha,
                    perk ? this : null, line.perkId,
                    perk && line.perkId.equals(this.hoveredPerk));
            return;
        }
        LostTalesUiInk.drawText(this.font, line.text, left + line.indent, textTop, line.rgb,
                alpha);
    }

    /* ---- The pointer ---- */

    private void hover(QuestJournalLayout layout, List<Row> rows, List<Line> lines, int x,
                       int y) {
        this.hoveredRow = rowAt(layout, rows, x, y);
        Line line = lineAt(layout, lines, x, y);
        this.hoveredPerk = line == null ? "" : line.perkId;
    }

    private int rowAt(QuestJournalLayout layout, List<Row> rows, int x, int y) {
        LostTalesUiHitBox list = layout.list();
        if (!list.contains(x, y)) {
            return -1;
        }
        int index = (y - listTop(list)) / MenuWindow.rowHeight();
        return index >= 0 && index < rows.size() && rows.get(index).selectable() ? index : -1;
    }

    private Line lineAt(QuestJournalLayout layout, List<Line> lines, int x, int y) {
        LostTalesUiHitBox area = layout.detail();
        if (!area.contains(x, y)) {
            return null;
        }
        double top = area.top - this.detailShown;
        for (Line line : lines) {
            if (y >= top && y < top + line.height()) {
                return line.perkId.length() > 0 ? line : null;
            }
            top += line.height();
        }
        return null;
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box, double x, double y,
                                int button) {
        if (button != 0 || this.font == null) {
            return false;
        }
        int pageX = (int) Math.floor(x - box.left);
        int pageY = (int) Math.floor(y - box.top);
        QuestJournalLayout layout = layout();
        List<Row> rows = rows();
        int row = rowAt(layout, rows, pageX, pageY);
        if (row >= 0) {
            choose(rows.get(row).id());
            if (!layout.isWide()) {
                this.listOut = false;
            }
            return true;
        }
        Line line = lineAt(layout, lines((int) layout.detailRows().width), pageX, pageY);
        if (line != null) {
            this.chosenPerk = line.perkId;
            return true;
        }
        return false;
    }

    @Override
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        int pageX = (int) Math.floor(x - box.left);
        int pageY = (int) Math.floor(y - box.top);
        QuestJournalLayout layout = layout();
        return this.font != null && (rowAt(layout, rows(), pageX, pageY) >= 0
                || lineAt(layout, lines((int) layout.detailRows().width), pageX, pageY) != null);
    }

    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y, int lines) {
        if (this.font == null || lines == 0) {
            return false;
        }
        int pageX = (int) Math.floor(x - box.left);
        int pageY = (int) Math.floor(y - box.top);
        if (layout().list().contains(pageX, pageY)) {
            this.listScroll += WheelStep.pixels(WheelStep.menuRows(lines), MenuWindow.rowHeight());
        } else {
            this.detailScroll += WheelStep.pixels(lines, LINE_HEIGHT);
        }
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            List<Row> rows = rows();
            int at = 0;
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).selectable() && rows.get(index).id().equals(chosenId())) {
                    at = index;
                }
            }
            int next = at + (keyCode == Keyboard.KEY_UP ? -1 : 1);
            if (next >= 0 && next < rows.size() && rows.get(next).selectable()) {
                choose(rows.get(next).id());
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN && this.chosenPerk.length() > 0) {
            takeChosenPerk();
            return true;
        }
        return false;
    }

    private void choose(String id) {
        if (!id.equals(this.chosen)) {
            this.chosen = id;
            this.chosenPerk = "";
            this.detailScroll = 0;
        }
    }

    /* ---- The window's strip and bar ---- */

    @Override
    public int tone() {
        return LostTalesColors.rgb(LostTalesColors.SEA_GREEN);
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

    @Override
    public List<PageKeys.Area> keyAreas() {
        return PageKeys.pageArea("gui.losttales.page.skills",
                PageKeys.pageKey(PAGE_ID, "pick", Keyboard.KEY_UP, PageKeys.OR, Keyboard.KEY_DOWN),
                PageKeys.pageKey(PAGE_ID, "take", Keyboard.KEY_RETURN),
                PageKeys.pageKey(PAGE_ID, "wheel", PageKeys.WHEEL));
    }

    /** The server's word on a perk answers the page's Take while it is shown. */
    @Override
    public boolean answersLine(String key) {
        return key != null && key.startsWith("chat.losttales.skill.perk.");
    }

    @Override
    public void answerLine(String key, String words) {
        if ("chat.losttales.skill.perk.taken".equals(key)) {
            sayDone(words);
        } else {
            sayRefused(words);
        }
    }

    /** Take the next rank of the perk picked; greyed and saying why while it cannot be taken. */
    @Override
    public List<BarItem> barItems() {
        List<BarItem> items = new ArrayList<BarItem>(1);
        SkillDefinition skill = DEEDS.equals(chosenId()) ? null : ClientProgression.skill(chosenId());
        PerkDefinition perk = skill == null ? null : skill.perk(this.chosenPerk);
        String label = perk == null ? translate("gui.losttales.skills.take")
                : translate("gui.losttales.skills.take.perk", PerkWords.perkName(skill, perk,
                        ClientProgression.state().perkRank(skill.getId(), perk.getId()) + 1));
        BarItem take = BarItem.button(TAKE, label, ICON).tip(label);
        if (perk == null) {
            take = take.unavailable(translate("gui.losttales.skills.why.none"));
        } else {
            ProgressionSnapshot snapshot = ClientProgression.snapshot();
            ProgressionRules.Refusal refusal = ProgressionRules.canTake(ClientProgression.state(),
                    skill, perk, snapshot == null ? 0 : snapshot.getAvailablePoints());
            if (refusal != ProgressionRules.Refusal.NONE) {
                take = take.unavailable(translate("gui.losttales.skills.why."
                        + refusal.name().toLowerCase(Locale.ROOT)));
            }
        }
        items.add(take);
        return items;
    }

    @Override
    public void barPressed(String id, int offer) {
        if (TAKE.equals(id)) {
            takeChosenPerk();
        }
    }

    private void takeChosenPerk() {
        String skill = chosenId();
        if (DEEDS.equals(skill) || this.chosenPerk.length() == 0) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                ProgressionRequestPacket.takePerk(skill, this.chosenPerk));
        sayWorking(translate("gui.losttales.skills.taking"));
    }

    /* ---- Helpers ---- */

    private int inkWidth(String text) {
        return Math.max(0, this.font.getStringWidth(text) - 1);
    }

    private static int clamp(int value, int least, int most) {
        return value < least ? least : value > most ? most : value;
    }

    private static String translate(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }

    private void enableScissor(int x, int y, int width, int height) {
        ScaledResolution scaled = new ScaledResolution(this.mc, this.mc.displayWidth,
                this.mc.displayHeight);
        int scale = scaled.getScaleFactor();
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) Math.round((this.clipX + x) * scale),
                this.mc.displayHeight - (int) Math.round((this.clipY + y + height) * scale),
                Math.max(0, width * scale), Math.max(0, height * scale));
    }

    private void disableScissor() {
        GL11.glPopAttrib();
    }

    /** A row of the list: the character's heading, a skill, or the deeds. */
    private static final class Row {
        final SkillDefinition skill;
        final boolean deeds;

        Row(SkillDefinition skill, boolean deeds) {
            this.skill = skill;
            this.deeds = deeds;
        }

        boolean selectable() {
            return this.skill != null || this.deeds;
        }

        String id() {
            return this.deeds ? DEEDS : this.skill == null ? "" : this.skill.getId();
        }
    }

    /** A line of the detail, as the journal's are: a heading, words, a bar, a perk or a deed. */
    private static final class Line {
        static final int TEXT = 0;
        static final int HEADING = 1;
        static final int SEPARATOR = 2;
        static final int BAR = 3;
        static final int PERK = 4;
        static final int DEED = 5;

        final int kind;
        String text = "";
        int rgb;
        int indent;
        boolean title;
        float share;
        String mark = "";
        int markRgb;
        String value = "";
        /** The perk a line belongs to, which a press on it picks; empty for none. */
        String perkId = "";

        Line(int kind) {
            this.kind = kind;
        }

        static Line heading(String text, boolean title) {
            Line line = new Line(HEADING);
            line.text = text;
            line.title = title;
            return line;
        }

        static Line text(String text, int rgb, int indent) {
            Line line = new Line(TEXT);
            line.text = text;
            line.rgb = rgb;
            line.indent = indent;
            return line;
        }

        static Line separator() {
            return new Line(SEPARATOR);
        }

        static Line bar(float share) {
            Line line = new Line(BAR);
            line.share = Math.max(0.0F, Math.min(1.0F, share));
            return line;
        }

        /** A perk's head line: its mark (taken, open to take, or ahead), its name and rank. */
        static Line perk(String perkId, String title, int rank, ProgressionRules.Refusal refusal,
                         boolean enabled) {
            Line line = new Line(PERK);
            line.perkId = perkId;
            line.text = title;
            boolean all = refusal == ProgressionRules.Refusal.ALL_TAKEN;
            boolean open = refusal == ProgressionRules.Refusal.NONE;
            line.mark = all || rank > 0 && !open ? QuestMarks.DONE
                    : open ? QuestMarks.OPEN : QuestMarks.AHEAD;
            line.markRgb = LostTalesColors.rgb(!enabled ? LostTalesColors.TEXT_DIM
                    : all || rank > 0 ? LostTalesColors.GREEN
                    : open ? LostTalesColors.GOLD : LostTalesColors.TEXT_DIM);
            line.rgb = LostTalesColors.rgb(enabled && (rank > 0 || open)
                    ? LostTalesColors.TEXT : LostTalesColors.TEXT_DIM);
            return line;
        }

        static Line deed(String name, String progress, boolean done) {
            Line line = new Line(DEED);
            line.text = name;
            line.value = progress;
            line.mark = done ? QuestMarks.DONE : QuestMarks.AHEAD;
            line.markRgb = LostTalesColors.rgb(done ? LostTalesColors.GREEN
                    : LostTalesColors.TEXT_DIM);
            line.rgb = LostTalesColors.rgb(done ? LostTalesColors.TEXT : LostTalesColors.TEXT_DIM);
            return line;
        }

        int height() {
            switch (this.kind) {
                case HEADING:
                case PERK:
                case DEED:
                    return MenuWindow.rowHeight();
                case SEPARATOR:
                    return WindowLists.SEPARATOR_HEIGHT;
                case BAR:
                    return BAR_HEIGHT;
                default:
                    return LINE_HEIGHT;
            }
        }
    }
}
