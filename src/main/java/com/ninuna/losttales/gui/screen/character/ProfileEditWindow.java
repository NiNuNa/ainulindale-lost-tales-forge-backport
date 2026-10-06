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
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.SubWindow;
import com.ninuna.losttales.client.window.SubWindowContent;
import com.ninuna.losttales.client.window.WheelStep;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowHover;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WordButton;
import com.ninuna.losttales.gui.screen.character.creator.CreatorContext;
import com.ninuna.losttales.gui.screen.character.creator.CreatorControl;
import com.ninuna.losttales.gui.screen.character.creator.CreatorNote;
import com.ninuna.losttales.gui.screen.character.creator.CreatorRows;
import com.ninuna.losttales.gui.screen.character.creator.CreatorSlider;
import com.ninuna.losttales.gui.screen.character.creator.CreatorTextArea;
import com.ninuna.losttales.gui.screen.character.creator.CreatorTextControl;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import java.util.ArrayList;
import java.util.Collections;
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
 * page's window, its player's to change at any time: three sections,
 * each a word button at its top — About, with Appearance, Personality and
 * History, each several lines under its name; Facts, with the age and the
 * six short facts; and Glances. The rows stand as the windows' Settings
 * do, one row a control, and scroll by whole rows where the window is too
 * short for them. Save sends all of it, and the window closes once the
 * server has kept it; Cancel, the cross and Escape leave it as it was.
 * The window opens as tall as its tallest section, so it stands still as
 * the sections change.
 */
final class ProfileEditWindow extends SubWindowContent {
    /** The window's sections. */
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
    private static final String SECTION_PREFIX = "section:";
    private static final int WIDTH = 300;
    private static final int PADDING_X = MenuWindow.PADDING_X;
    private static final int PADDING_Y = MenuWindow.PADDING_Y;
    /** The rows the window shows at least; a shorter one cannot be made. */
    private static final int MIN_ROWS = 4;
    /** The lines an About text shows at once. */
    private static final int ABOUT_LINES = 4;
    /** How often the rows keep time, as a screen's ticks would. */
    private static final long TICK_NANOS = 50L * 1000000L;
    /** Closer than this to the target and the drawn scroll arrives. */
    private static final double SCROLL_SNAP_PIXELS = 0.5D;
    /** What is drawn under the pointer while it is off the rows. */
    private static final int AWAY = Integer.MIN_VALUE / 2;

    private final UUID characterId;
    private final Map<Page, List<CreatorControl>> pages =
            new EnumMap<Page, List<CreatorControl>>(Page.class);
    private final Map<Page, LostTalesUiButtonMotion> sectionMotions =
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
    private CreatorContext context;
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
    /** The content box the rows were last laid out in; null before the first. */
    private LostTalesUiHitBox box;
    /** The band the rows show in, as last laid out. */
    private int bandTop;
    private int bandBottom;
    /** Pixels the rows are asked to be scrolled by; the drawn offset glides after it, as a menu's does. */
    private int scroll;
    private double renderedScroll;
    private long scrollNanos;

    ProfileEditWindow(UUID characterId) {
        this.characterId = characterId;
        for (Page each : Page.values()) {
            this.sectionMotions.put(each, new LostTalesUiButtonMotion(
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
        this.scroll = 0;
        this.renderedScroll = 0.0D;
    }

    private CharacterSummary character() {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        return snapshot == null ? null : snapshot.getCharacter(this.characterId);
    }

    private void build(CharacterProfile profile) {
        Minecraft minecraft = Minecraft.getMinecraft();
        this.context = new CreatorContext(minecraft, minecraft.fontRenderer,
                minecraft.thePlayer == null ? null
                        : minecraft.thePlayer.getUniqueID(),
                CreatorContext.Presentation.ROWS);
        List<CreatorControl> about = new ArrayList<CreatorControl>();
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            CreatorTextArea area = new CreatorTextArea(this.context,
                    I18n.format("gui.losttales.character.profile."
                            + section.getId()), profile.section(section),
                    CharacterProfile.MAX_SECTION_LENGTH, ABOUT_LINES);
            this.sections.put(section, area);
            about.add(area);
        }
        this.pages.put(Page.ABOUT, about);

        List<CreatorControl> facts = new ArrayList<CreatorControl>();
        facts.add(new CreatorSlider(this.context,
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
        facts.add(new CreatorNote(this.context,
                I18n.format("gui.losttales.character.creator.age.hint")));
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            CreatorTextControl field = new CreatorTextControl(this.context,
                    I18n.format("gui.losttales.character.profile.fact."
                            + fact.getId()), profile.fact(fact),
                    CharacterProfile.MAX_FACT_LENGTH, true);
            this.facts.put(fact, field);
            facts.add(field);
        }
        this.pages.put(Page.FACTS, facts);

        this.glances = new GlanceEditor(this.context, profile.glances());
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

    /** Stacks a section's rows from {@code top}, one under the other; answers how tall they stand. */
    private int layOut(Page shown, int left, int top, int width) {
        int y = top;
        for (CreatorControl control : this.pages.get(shown)) {
            control.place(left, y, width);
            y += control.height();
        }
        return y - top;
    }

    /** The rows' height the tallest section takes. */
    private int pageHeight(int width) {
        int tallest = 0;
        for (Page each : Page.values()) {
            tallest = Math.max(tallest, layOut(each, 0, 0, width));
        }
        return tallest;
    }

    private static int inner(LostTalesUiHitBox box) {
        return (int)box.width - 2 * PADDING_X;
    }

    /** Where the rows' band starts: under the section buttons, a padding clear. */
    private static int bandTop(LostTalesUiHitBox box) {
        return (int)box.top + PADDING_Y + LostTalesUiFramedButton.HEIGHT
                + PADDING_Y;
    }

    /** Where the status line stands: a note's height over Cancel and Save. */
    private static int statusTop(LostTalesUiHitBox box) {
        return (int)(box.top + box.height) - PADDING_Y
                - LostTalesUiFramedButton.HEIGHT - CreatorRows.noteHeight(1);
    }

    /**
     * Lays the shown section out in {@code box}: the band its rows show
     * in, the scroll kept within them, and each row placed from the drawn
     * offset. Answers the furthest the rows scroll.
     */
    private int layOut(LostTalesUiHitBox box) {
        this.box = box;
        this.bandTop = bandTop(box);
        this.bandBottom = Math.max(this.bandTop, statusTop(box));
        int maxScroll = Math.max(0, layOut(this.page, 0, 0, inner(box))
                - (this.bandBottom - this.bandTop));
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));
        this.renderedScroll = Math.max(0.0D, Math.min(maxScroll,
                this.renderedScroll));
        place();
        return maxScroll;
    }

    /** Places the shown section's rows in the band, from the drawn offset. */
    private void place() {
        if (this.box != null) {
            layOut(this.page, (int)this.box.left + PADDING_X, this.bandTop
                    - (int)Math.round(this.renderedScroll), inner(this.box));
        }
    }

    /** The drawn scroll glides after the asked one with the windows' shared scroll motion, as a menu's does. */
    private void glideScroll() {
        long now = System.nanoTime();
        double elapsed = this.scrollNanos == 0L ? 0.0D
                : (now - this.scrollNanos) / 1.0E9D;
        this.scrollNanos = now;
        if (Math.abs(this.scroll - this.renderedScroll) <= SCROLL_SNAP_PIXELS) {
            this.renderedScroll = this.scroll;
            return;
        }
        this.renderedScroll = Motions.followTravel(MotionIds.WINDOW_SCROLL,
                this.renderedScroll, this.scroll, elapsed);
    }

    /** Asks the band to scroll so a row that took the keys stands whole in it. */
    private void reveal(CreatorControl control) {
        if (control == null || this.box == null) {
            return;
        }
        place();
        int band = this.bandBottom - this.bandTop;
        int top = control.getY() - (this.bandTop
                - (int)Math.round(this.renderedScroll));
        int bottom = top + control.height();
        if (top < this.scroll) {
            this.scroll = top;
        } else if (bottom > this.scroll + band) {
            this.scroll = Math.min(top, bottom - band);
        }
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

    /** The section buttons, the tallest section's rows, the status line and the foot's buttons. */
    @Override
    public int naturalHeight(int width) {
        return chromeHeight() + pageHeight(width - 2 * PADDING_X);
    }

    /** Everything but the rows: the section buttons, the status line and the foot's buttons, each a padding clear. */
    private static int chromeHeight() {
        return PADDING_Y + LostTalesUiFramedButton.HEIGHT + PADDING_Y
                + CreatorRows.noteHeight(1) + LostTalesUiFramedButton.HEIGHT
                + PADDING_Y;
    }

    @Override
    public int minWidth() {
        return 240;
    }

    @Override
    public int minHeight() {
        return chromeHeight() + MIN_ROWS * CreatorRows.height();
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     int alpha, int surfaceAlpha) {
        keepTime();
        followProfile();
        followRequest();
        FontRenderer font = minecraft.fontRenderer;
        int left = (int)box.left + PADDING_X;
        int maxScroll = layOut(box);
        glideScroll();
        place();
        this.context.frame(alpha, surfaceAlpha, (int)box.left,
                (int)(box.left + box.width));
        String part = partAt(font, box, pointerX, pointerY);
        for (Page each : Page.values()) {
            drawSection(font, box, each, (SECTION_PREFIX + each.name()).equals(part),
                    alpha, surfaceAlpha);
        }
        boolean onRows = inBand(pointerX, pointerY);
        int mouseX = onRows ? (int)Math.floor(pointerX) : AWAY;
        int mouseY = onRows ? (int)Math.floor(pointerY) : AWAY;
        // The rows are cut to their band; across, the cut takes in the
        // frame's ring, which a lit row recolours beside it.
        int ring = LostTalesUiWindowFrame.WIDTH;
        boolean clipped = SubWindowContent.beginClip(minecraft, clipX - ring,
                clipY + (this.bandTop - box.top), box.width + 2 * ring,
                this.bandBottom - this.bandTop);
        try {
            for (CreatorControl control : this.pages.get(this.page)) {
                if (control.getY() < this.bandBottom
                        && control.getY() + control.height() > this.bandTop) {
                    control.draw(mouseX, mouseY);
                }
            }
        } finally {
            SubWindowContent.endClip(clipped);
        }
        WindowLists.drawScroll(box.left, this.bandTop, box.left + box.width,
                this.bandBottom, this.bandTop, this.bandBottom,
                this.renderedScroll, maxScroll, alpha);
        String said = statusText();
        if (said.length() > 0) {
            CreatorRows.drawNote(this.context, Collections.singletonList(
                    font.trimStringToWidth(said, inner(box))), left,
                    statusTop(box), this.statusError
                            ? LostTalesColors.rgb(LostTalesColors.RED)
                            : WindowStyle.asideRgb());
        }
        WordButton.draw(font, buttonBox(font, box, CANCEL), cancelLabel(),
                false, true, CANCEL.equals(part), this.cancelMotion, alpha,
                surfaceAlpha);
        WordButton.draw(font, buttonBox(font, box, SAVE), saveLabel(), false,
                canSave(), SAVE.equals(part), this.saveMotion, alpha,
                surfaceAlpha);
    }

    /**
     * One of the window's section buttons: the one shown stands lit with its name in
     * honey, the others are buttons.
     */
    private void drawSection(FontRenderer font, LostTalesUiHitBox box, Page each,
                         boolean hovered, int alpha, int surfaceAlpha) {
        LostTalesUiHitBox at = sectionBox(font, box, each);
        String label = I18n.format(each.labelKey);
        if (each != this.page) {
            WordButton.draw(font, at, label, false, true, hovered,
                    this.sectionMotions.get(each), alpha, surfaceAlpha);
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

    /** The section buttons side by side at the window's top left, a framed button's gap apart. */
    private static LostTalesUiHitBox sectionBox(FontRenderer font,
                                            LostTalesUiHitBox box, Page each) {
        double left = box.left + PADDING_X;
        for (Page before : Page.values()) {
            int width = WordButton.width(font, I18n.format(before.labelKey));
            if (before == each) {
                return new LostTalesUiHitBox(left, box.top + PADDING_Y, width,
                        LostTalesUiFramedButton.HEIGHT);
            }
            left += width + WindowBar.BUTTON_GAP;
        }
        return new LostTalesUiHitBox(left, box.top + PADDING_Y, 0, 0);
    }

    /** Cancel and Save at the foot's right, Save last, a framed button's gap apart. */
    private static LostTalesUiHitBox buttonBox(FontRenderer font,
                                               LostTalesUiHitBox box,
                                               String part) {
        int saveWidth = WordButton.width(font, saveLabel());
        int cancelWidth = WordButton.width(font, cancelLabel());
        double top = box.top + box.height - PADDING_Y
                - LostTalesUiFramedButton.HEIGHT;
        double saveLeft = box.left + box.width - PADDING_X - saveWidth;
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
            if (sectionBox(font, box, each).contains(x, y)) {
                return SECTION_PREFIX + each.name();
            }
        }
        return controlAt(x, y) != null ? CONTROL : null;
    }

    /** Whether a point is in the band the rows show in, across the whole box. */
    private boolean inBand(double x, double y) {
        return this.box != null && !Double.isNaN(x) && !Double.isNaN(y)
                && LostTalesUiHitBox.contains(x, y, this.box.left,
                        this.bandTop, this.box.width,
                        this.bandBottom - this.bandTop);
    }

    /** The row under a point, as the rows were last laid out; only where the band shows them. */
    private CreatorControl controlAt(double x, double y) {
        if (!inBand(x, y)) {
            return null;
        }
        place();
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
        layOut(box);
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
        if (hover.part != null && hover.part.startsWith(SECTION_PREFIX)
                && button == 0) {
            show(Page.valueOf(hover.part.substring(SECTION_PREFIX.length())));
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

    /** Turns the window to one of its sections, from its top; its first row takes the keys. */
    private void show(Page shown) {
        if (shown == this.page) {
            return;
        }
        focus(null);
        this.page = shown;
        this.hovered = null;
        this.scroll = 0;
        this.renderedScroll = 0.0D;
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

    /**
     * The wheel over a row turns it: the age steps, a text scrolls its
     * lines. Elsewhere, and past a text's ends, it scrolls the rows by
     * whole rows, as a menu's.
     */
    @Override
    public void scrollBy(int lines) {
        if (this.hovered != null && this.hovered.mouseWheel(lines < 0 ? 1 : -1)) {
            clearError();
            return;
        }
        this.scroll = Math.max(0, this.scroll + WheelStep.pixels(
                WheelStep.menuRows(lines), CreatorRows.height()));
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

    /** The next row of the section that takes the keys, round from either end. */
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

    /** The section's first row takes the keys as the window comes in front. */
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
     * A row takes its keys first; then Tab walks the section's rows, the
     * band scrolling to the one that takes them, Ctrl+Tab the window's
     * sections, and Return saves.
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
            reveal(this.focused);
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
