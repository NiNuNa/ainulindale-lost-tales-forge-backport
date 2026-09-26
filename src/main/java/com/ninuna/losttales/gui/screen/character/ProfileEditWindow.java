package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterProfileCache;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.window.SubWindow;
import com.ninuna.losttales.client.window.SubWindowContent;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowHover;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WordButton;
import com.ninuna.losttales.gui.screen.character.creator.CreatorContext;
import com.ninuna.losttales.gui.screen.character.creator.CreatorControl;
import com.ninuna.losttales.gui.screen.character.creator.CreatorNote;
import com.ninuna.losttales.gui.screen.character.creator.CreatorSlider;
import com.ninuna.losttales.gui.screen.character.creator.CreatorTextArea;
import com.ninuna.losttales.gui.screen.character.creator.CreatorTextControl;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/**
 * What a character says about itself, in a sub-window of the Characters
 * tab's window, its player's to change at any time (P6 a): three tabs of
 * its own — About, with Appearance, Personality and History, each a
 * box of several lines; Facts, with the age and the six short facts; and
 * Glances. Save sends all of it, and the window closes once the server
 * has kept it; Cancel, the cross and Escape leave it as it was. The
 * window is as tall as its tallest tab, so it stands still as the tabs
 * change.
 */
final class ProfileEditWindow extends SubWindowContent {
    /** The window's own tabs. */
    private enum Page {
        ABOUT("gui.losttales.character.profile.about"),
        FACTS("gui.losttales.character.profile.facts"),
        GLANCES("gui.losttales.character.profile.glances");

        final String labelKey;

        Page(String labelKey) {
            this.labelKey = labelKey;
        }
    }

    private static final String SAVE = "save";
    private static final String CANCEL = "cancel";
    private static final String CONTROL = "control";
    private static final String TAB_PREFIX = "tab:";
    private static final int WIDTH = 300;
    private static final int PADDING = 6;
    private static final int CONTROL_GAP = 6;
    private static final int STATUS_HEIGHT = 12;
    /** The lines an About text's box shows at once. */
    private static final int ABOUT_LINES = 4;
    /** How often the rows keep time, as a screen's ticks would. */
    private static final long TICK_NANOS = 50L * 1000000L;

    private final UUID characterId;
    private final Map<Page, List<CreatorControl>> pages =
            new EnumMap<Page, List<CreatorControl>>(Page.class);
    private final Map<Page, LostTalesUiButtonMotion> tabMotions =
            new EnumMap<Page, LostTalesUiButtonMotion>(Page.class);
    private final Map<CharacterProfile.Section, CreatorTextArea> sections =
            new EnumMap<CharacterProfile.Section, CreatorTextArea>(
                    CharacterProfile.Section.class);
    private final Map<CharacterProfile.Fact, CreatorTextControl> facts =
            new EnumMap<CharacterProfile.Fact, CreatorTextControl>(
                    CharacterProfile.Fact.class);
    private final LostTalesUiButtonMotion saveMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    private final LostTalesUiButtonMotion cancelMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    private GlanceEditor glances;
    private Page page = Page.ABOUT;
    private CreatorControl focused;
    private CreatorControl held;
    private CreatorControl hovered;
    private int age = CharacterValidator.MIN_AGE;
    /** Whether the rows hold the profile the server sent, rather than an empty one waiting for it. */
    private boolean loaded;
    /** Whether anything was changed by hand, after which an arriving profile no longer replaces the rows. */
    private boolean touched;
    private int pendingRequestId;
    private String status = "";
    private boolean statusError;
    private long tickedNanos;

    ProfileEditWindow(UUID characterId) {
        this.characterId = characterId;
        for (Page each : Page.values()) {
            this.tabMotions.put(each, new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT));
        }
        restart();
    }

    /** Starts again from what the character says now: the window opened afresh. */
    void restart() {
        ClientCharacterProfileCache.refresh(this.characterId);
        CharacterSummary character = character();
        this.age = character == null ? CharacterValidator.MIN_AGE
                : Math.max(CharacterValidator.MIN_AGE, character.getAge());
        CharacterProfile profile = ClientCharacterProfileCache.get(
                this.characterId);
        build(profile == null ? CharacterProfile.EMPTY : profile);
        this.loaded = profile != null;
        this.touched = false;
        this.page = Page.ABOUT;
        this.status = "";
        this.statusError = false;
        this.pendingRequestId = 0;
    }

    private CharacterSummary character() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        return snapshot == null ? null : snapshot.getCharacter(this.characterId);
    }

    private void build(CharacterProfile profile) {
        Minecraft minecraft = Minecraft.getMinecraft();
        CreatorContext context = new CreatorContext(minecraft,
                minecraft.fontRenderer, minecraft.thePlayer == null ? null
                        : minecraft.thePlayer.getUniqueID());
        List<CreatorControl> about = new ArrayList<CreatorControl>();
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            CreatorTextArea area = new CreatorTextArea(context, I18n.format(
                    "gui.losttales.character.profile." + section.getId()),
                    profile.section(section),
                    CharacterProfile.MAX_SECTION_LENGTH, ABOUT_LINES);
            this.sections.put(section, area);
            about.add(area);
        }
        this.pages.put(Page.ABOUT, about);

        List<CreatorControl> facts = new ArrayList<CreatorControl>();
        facts.add(new CreatorSlider(context,
                I18n.format("gui.losttales.character.age"),
                new CreatorSlider.IntValue() {
                    @Override
                    public int get() {
                        return age;
                    }

                    @Override
                    public void set(int value) {
                        age = Math.max(CharacterValidator.MIN_AGE, value);
                        touched = true;
                        clearError();
                    }

                    @Override
                    public int typedMax() {
                        return CharacterValidator.MAX_AGE;
                    }
                }, I18n.format("gui.losttales.character.creator.age.oldest")));
        facts.add(new CreatorNote(context,
                I18n.format("gui.losttales.character.creator.age.hint")));
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            CreatorTextControl field = new CreatorTextControl(context,
                    I18n.format("gui.losttales.character.profile.fact."
                            + fact.getId()), profile.fact(fact),
                    CharacterProfile.MAX_FACT_LENGTH, true);
            this.facts.put(fact, field);
            facts.add(field);
        }
        this.pages.put(Page.FACTS, facts);

        this.glances = new GlanceEditor(context, profile.glances());
        List<CreatorControl> glanceRows = new ArrayList<CreatorControl>();
        glanceRows.add(this.glances);
        this.pages.put(Page.GLANCES, glanceRows);
        this.focused = null;
        this.held = null;
        this.hovered = null;
    }

    /** The profile arrives: it fills the rows, unless they were already changed by hand. */
    private void followProfile() {
        if (this.loaded) {
            return;
        }
        CharacterProfile profile = ClientCharacterProfileCache.get(
                this.characterId);
        if (profile == null) {
            return;
        }
        this.loaded = true;
        if (!this.touched) {
            build(profile);
        }
    }

    /* ---- Where things stand ---- */

    /**
     * Places a tab's rows from the box's top left; answers where they
     * end. The facts stand two to a row under the age.
     */
    private int layOut(Page shown, int left, int top, int width) {
        int y = top;
        int column = 0;
        int rowHeight = 0;
        int half = (width - CONTROL_GAP) / 2;
        for (CreatorControl control : this.pages.get(shown)) {
            if (shown == Page.FACTS && control instanceof CreatorTextControl) {
                control.place(left + column * (half + CONTROL_GAP), y, half);
                rowHeight = Math.max(rowHeight, control.height());
                if (++column == 2) {
                    y += rowHeight + CONTROL_GAP;
                    column = 0;
                    rowHeight = 0;
                }
                continue;
            }
            control.place(left, y, width);
            y += control.height() + CONTROL_GAP;
        }
        if (column > 0) {
            y += rowHeight + CONTROL_GAP;
        }
        return y;
    }

    /** The rows' height the tallest tab takes. */
    private int pageHeight(int width) {
        int tallest = 0;
        for (Page each : Page.values()) {
            tallest = Math.max(tallest, layOut(each, 0, 0, width));
        }
        return tallest;
    }

    private int pagesTop(LostTalesUiHitBox box) {
        return (int)box.top + PADDING + LostTalesUiFramedButton.HEIGHT
                + CONTROL_GAP;
    }

    /* ---- Saving ---- */

    /** The profile as the rows write it. */
    private CharacterProfile written() {
        CharacterProfile profile = CharacterProfile.EMPTY;
        for (Map.Entry<CharacterProfile.Section, CreatorTextArea> section
                : this.sections.entrySet()) {
            profile = profile.withSection(section.getKey(),
                    section.getValue().getText());
        }
        for (Map.Entry<CharacterProfile.Fact, CreatorTextControl> fact
                : this.facts.entrySet()) {
            profile = profile.withFact(fact.getKey(), fact.getValue().getText());
        }
        return profile.withGlances(this.glances.glances());
    }

    private boolean canSave() {
        CharacterSummary character = character();
        CharacterProfile kept = ClientCharacterProfileCache.get(
                this.characterId);
        return this.pendingRequestId == 0 && this.loaded && character != null
                && kept != null && this.glances.allTitled()
                && (this.age != character.getAge()
                        || !CharacterValidator.normalizeProfile(written())
                                .equals(kept));
    }

    private void save() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot == null || !canSave()) {
            return;
        }
        this.status = I18n.format("gui.losttales.character.profile_edit.saving");
        this.statusError = false;
        this.pendingRequestId = ClientCharacterNetwork.updateProfile(
                snapshot.getRevision(), this.characterId, written(), this.age);
    }

    /** The server's answer: kept closes the window, refused says why. */
    private void followRequest() {
        if (this.pendingRequestId == 0
                || ClientCharacterRosterCache.isRequestPending(
                        this.pendingRequestId)) {
            return;
        }
        int completed = this.pendingRequestId;
        this.pendingRequestId = 0;
        CharacterOperationFeedback feedback =
                ClientCharacterRosterCache.getOperation(completed);
        if (feedback == null) {
            return;
        }
        ClientCharacterRosterCache.clearOperation(completed);
        if (!feedback.isSuccessful()) {
            this.status = ClientCharacterDisplayNames.error(feedback);
            this.statusError = true;
            return;
        }
        close();
    }

    private void close() {
        WindowScreen screen = WindowScreen.current();
        SubWindow window = screen == null ? null : screen.subWindows().find(
                CharacterSubWindows.PROFILE_EDIT, this.characterId.toString());
        if (window != null) {
            screen.subWindows().close(window);
        }
    }

    private void clearError() {
        if (this.statusError) {
            this.status = "";
            this.statusError = false;
        }
    }

    /** What the status line says: the server's refusal, the save under way, the profile on its way, or what still keeps Save back. */
    private String statusText() {
        if (this.status.length() > 0) {
            return this.status;
        }
        if (!this.loaded) {
            return I18n.format("gui.losttales.character.profile_edit.loading");
        }
        if (!this.glances.allTitled()) {
            return I18n.format("gui.losttales.character.profile.glance.untitled");
        }
        return "";
    }

    /* ---- The window ---- */

    @Override
    public String stripTitle() {
        CharacterSummary character = character();
        return character == null ? null : I18n.format(
                "gui.losttales.character.profile_edit.title_of",
                character.getName());
    }

    @Override
    public LostTalesUiSheet stripIcon() {
        return null;
    }

    @Override
    public int naturalWidth() {
        return WIDTH;
    }

    @Override
    public int naturalHeight(int width) {
        return PADDING + LostTalesUiFramedButton.HEIGHT + CONTROL_GAP
                + pageHeight(width - 2 * PADDING) + STATUS_HEIGHT
                + LostTalesUiFramedButton.HEIGHT + PADDING;
    }

    @Override
    public int minWidth() {
        return 240;
    }

    @Override
    public int minHeight() {
        return naturalHeight(minWidth());
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     int alpha, int surfaceAlpha) {
        keepTime();
        followProfile();
        followRequest();
        FontRenderer font = minecraft.fontRenderer;
        int left = (int)box.left;
        int inner = (int)box.width - 2 * PADDING;
        int statusTop = pagesTop(box) + pageHeight(inner);
        layOut(this.page, left + PADDING, pagesTop(box), inner);
        int mouseX = Double.isNaN(pointerX) ? Integer.MIN_VALUE / 2
                : (int)Math.floor(pointerX);
        int mouseY = Double.isNaN(pointerY) ? Integer.MIN_VALUE / 2
                : (int)Math.floor(pointerY);
        String part = partAt(font, box, pointerX, pointerY);
        for (Page each : Page.values()) {
            drawTab(font, box, each, (TAB_PREFIX + each.name()).equals(part),
                    alpha, surfaceAlpha);
        }
        for (CreatorControl control : this.pages.get(this.page)) {
            control.draw(mouseX, mouseY);
        }
        String said = statusText();
        if (said.length() > 0) {
            LostTalesUiInk.drawText(font, font.trimStringToWidth(said, inner),
                    left + PADDING, statusTop, this.statusError
                            ? LostTalesColors.rgb(LostTalesColors.RED)
                            : WindowStyle.asideRgb(), alpha);
        }
        WordButton.draw(font, buttonBox(font, box, CANCEL), cancelLabel(),
                false, true, CANCEL.equals(part), this.cancelMotion, alpha,
                surfaceAlpha);
        WordButton.draw(font, buttonBox(font, box, SAVE), saveLabel(), false,
                canSave(), SAVE.equals(part), this.saveMotion, alpha,
                surfaceAlpha);
    }

    /**
     * One of the window's tabs: the one shown stands lit with its name in
     * honey, the others are buttons.
     */
    private void drawTab(FontRenderer font, LostTalesUiHitBox box, Page each,
                         boolean hovered, int alpha, int surfaceAlpha) {
        LostTalesUiHitBox at = tabBox(font, box, each);
        String label = I18n.format(each.labelKey);
        if (each != this.page) {
            WordButton.draw(font, at, label, false, true, hovered,
                    this.tabMotions.get(each), alpha, surfaceAlpha);
            return;
        }
        LostTalesUiFramedButton.drawSurface((float)at.left, (float)at.top,
                (float)at.width, (float)at.height, 1.0F, surfaceAlpha);
        LostTalesUiInk.drawText(font, label,
                (int)at.left + LostTalesUiFramedButton.WIDE_INSET,
                (int)at.top + (LostTalesUiFramedButton.HEIGHT
                        - WindowStyle.LINE_HEIGHT) / 2 + WindowStyle.ROW_TEXT_TOP,
                LostTalesColors.rgb(LostTalesColors.HONEY), alpha);
        LostTalesUiFramedButton.drawInk((float)at.left, (float)at.top,
                (int)at.width, (int)at.height, 1.0F, alpha);
    }

    /** The rows keep time as a screen's ticks would: the caret blinks. */
    private void keepTime() {
        long now = System.nanoTime();
        if (now - this.tickedNanos < TICK_NANOS) {
            return;
        }
        this.tickedNanos = now;
        for (CreatorControl control : this.pages.get(this.page)) {
            control.tick();
        }
    }

    private static String cancelLabel() {
        return I18n.format("gui.cancel");
    }

    private static String saveLabel() {
        return I18n.format("gui.losttales.character.profile_edit.save");
    }

    /** The tabs side by side at the window's top left, a framed button's gap apart. */
    private static LostTalesUiHitBox tabBox(FontRenderer font,
                                            LostTalesUiHitBox box, Page each) {
        double left = box.left + PADDING;
        for (Page before : Page.values()) {
            int width = WordButton.width(font, I18n.format(before.labelKey));
            if (before == each) {
                return new LostTalesUiHitBox(left, box.top + PADDING, width,
                        LostTalesUiFramedButton.HEIGHT);
            }
            left += width + WindowBar.BUTTON_GAP;
        }
        return new LostTalesUiHitBox(left, box.top + PADDING, 0, 0);
    }

    /** Cancel and Save at the foot's right, Save last, a framed button's gap apart. */
    private static LostTalesUiHitBox buttonBox(FontRenderer font,
                                               LostTalesUiHitBox box,
                                               String part) {
        int saveWidth = WordButton.width(font, saveLabel());
        int cancelWidth = WordButton.width(font, cancelLabel());
        double top = box.top + box.height - PADDING
                - LostTalesUiFramedButton.HEIGHT;
        double saveLeft = box.left + box.width - PADDING - saveWidth;
        if (SAVE.equals(part)) {
            return new LostTalesUiHitBox(saveLeft, top, saveWidth,
                    LostTalesUiFramedButton.HEIGHT);
        }
        return new LostTalesUiHitBox(saveLeft - WindowBar.BUTTON_GAP
                - cancelWidth, top, cancelWidth,
                LostTalesUiFramedButton.HEIGHT);
    }

    private String partAt(FontRenderer font, LostTalesUiHitBox box, double x,
                          double y) {
        if (buttonBox(font, box, SAVE).contains(x, y)) {
            return SAVE;
        }
        if (buttonBox(font, box, CANCEL).contains(x, y)) {
            return CANCEL;
        }
        for (Page each : Page.values()) {
            if (tabBox(font, box, each).contains(x, y)) {
                return TAB_PREFIX + each.name();
            }
        }
        return controlAt(x, y) != null ? CONTROL : null;
    }

    private CreatorControl controlAt(double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return null;
        }
        for (CreatorControl control : this.pages.get(this.page)) {
            if (control.contains((int)Math.floor(x), (int)Math.floor(y))) {
                return control;
            }
        }
        return null;
    }

    @Override
    public WindowHover hoverAt(LostTalesUiHitBox box, double x, double y) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String part = partAt(font, box, x, y);
        this.hovered = controlAt(x, y);
        if (part == null) {
            return null;
        }
        WindowHover hover = new WindowHover(WindowHover.Kind.SUB_WINDOW);
        hover.part = part;
        hover.acts = SAVE.equals(part) ? canSave() : !CONTROL.equals(part)
                || this.hovered.isPointerOverAction((int)Math.floor(x),
                        (int)Math.floor(y));
        return hover;
    }

    @Override
    public boolean pressed(WindowHover hover, double x, double y,
                           int button) {
        if (CANCEL.equals(hover.part) && button == 0) {
            close();
            return true;
        }
        if (SAVE.equals(hover.part) && button == 0) {
            save();
            return true;
        }
        if (hover.part != null && hover.part.startsWith(TAB_PREFIX)
                && button == 0) {
            show(Page.valueOf(hover.part.substring(TAB_PREFIX.length())));
            return true;
        }
        CreatorControl control = controlAt(x, y);
        focus(control);
        if (control != null && control.mouseClicked((int)Math.floor(x),
                (int)Math.floor(y), button)) {
            this.held = control;
            this.touched = true;
            clearError();
        }
        return true;
    }

    /** Turns the window to one of its tabs; its first row takes the keys. */
    private void show(Page shown) {
        if (shown == this.page) {
            return;
        }
        focus(null);
        this.page = shown;
        this.hovered = null;
        focusNext(1);
    }

    @Override
    public void dragged(double x, double y) {
        if (this.held != null) {
            this.held.mouseDragged((int)Math.floor(x), (int)Math.floor(y));
        }
    }

    @Override
    public void released() {
        if (this.held != null) {
            this.held.mouseReleased();
            this.held = null;
        }
    }

    /** The wheel over a row turns it: the age steps, a text scrolls. */
    @Override
    public void scrollBy(int lines) {
        if (this.hovered != null && this.hovered.mouseWheel(lines < 0 ? 1 : -1)) {
            clearError();
        }
    }

    private void focus(CreatorControl control) {
        if (this.focused == control) {
            return;
        }
        if (this.focused != null) {
            this.focused.setFocused(false);
        }
        this.focused = control != null && control.canFocus() ? control : null;
        if (this.focused != null) {
            this.focused.setFocused(true);
        }
    }

    /** The next row of the tab that takes the keys, round from either end. */
    private void focusNext(int step) {
        List<CreatorControl> focusable = new ArrayList<CreatorControl>();
        for (CreatorControl control : this.pages.get(this.page)) {
            if (control.canFocus()) {
                focusable.add(control);
            }
        }
        if (focusable.isEmpty()) {
            return;
        }
        int at = focusable.indexOf(this.focused);
        int next = at < 0 ? (step > 0 ? 0 : focusable.size() - 1)
                : (at + step + focusable.size()) % focusable.size();
        focus(focusable.get(next));
    }

    @Override
    public boolean holdsKeys() {
        return this.focused != null;
    }

    /** The tab's first row takes the keys as the window comes in front. */
    @Override
    public void takeKeys() {
        if (this.focused == null) {
            focusNext(1);
        }
    }

    @Override
    public void releaseKeys() {
        focus(null);
    }

    @Override
    public void closed() {
        releaseKeys();
    }

    /**
     * A row takes its keys first; then Tab walks the tab's rows,
     * Ctrl+Tab the window's tabs, and Return saves.
     */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_TAB && GuiScreen.isCtrlKeyDown()) {
            int step = GuiScreen.isShiftKeyDown() ? -1 : 1;
            Page[] all = Page.values();
            show(all[(this.page.ordinal() + step + all.length) % all.length]);
            return true;
        }
        if (this.focused != null && this.focused.keyTyped(typedChar, keyCode)) {
            this.touched = true;
            clearError();
            return true;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            focusNext(GuiScreen.isShiftKeyDown() ? -1 : 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            save();
            return true;
        }
        return false;
    }
}
