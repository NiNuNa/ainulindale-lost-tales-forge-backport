package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiCaret;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * Naming something, in a sub-window of its own: a view, a fellowship, a
 * mark on the map. The field holds the name, how many letters are left
 * stands at its end, and Cancel and Keep stand at the foot. Enter keeps;
 * the cross, Escape and Cancel keep nothing, and so does closing the
 * screen. Keep is greyed while the name may not be kept, and a press on
 * it says why.
 */
public final class NamingWindow extends SubWindowContent {
    public static final SubWindowKind KIND = SubWindowKind.register("naming",
            "gui.losttales.window.sub.naming");

    /** Whether a name may be kept: empty when it may, else why not, as a sentence. */
    public interface Rule {
        String refusal(String name);
    }

    /** What keeping the name does. */
    public interface Keeper {
        void keep(String name);
    }

    /** Every name may be kept, the empty one too. */
    public static final Rule ANY = new Rule() {
        @Override
        public String refusal(String name) {
            return "";
        }
    };

    private static final String KEEP = "keep";
    private static final String CANCEL = "cancel";
    private static final int WIDTH = 200;
    private static final int PADDING_X = MenuWindow.PADDING_X;
    private static final int PADDING_Y = MenuWindow.PADDING_Y;
    /** How often a rule is asked again while the name stands still: what it reads may change. */
    private static final long RULE_NANOS = 500000000L;

    private String title = "";
    private String prompt = "";
    private int maxLength;
    private Rule rule = ANY;
    private Keeper keeper;
    private Object about;
    private GuiTextField field;
    private final LostTalesUiButtonMotion keepMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    private final LostTalesUiButtonMotion cancelMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    /** The name the refusal was asked for, and when. */
    private String askedFor;
    private long askedNanos;
    private String refusal = "";
    /** Set once kept, so a second press keeps nothing. */
    private boolean answered;

    /**
     * Asks for a name: its title on the strip, the name it starts with, how
     * long it may be, whether it may be kept and what keeping it does. A
     * window already asking takes the new question in its place.
     */
    public NamingWindow ask(String title, String name, int maxLength, Rule rule,
                            Keeper keeper) {
        this.title = title == null ? "" : title;
        this.maxLength = Math.max(1, maxLength);
        this.rule = rule == null ? ANY : rule;
        this.keeper = keeper;
        this.prompt = "";
        this.about = null;
        this.field = WindowFields.make(LostTalesUiCaret.HEIGHT, this.maxLength, false);
        String start = name == null ? "" : name;
        this.field.setText(start.length() > this.maxLength
                ? start.substring(0, this.maxLength) : start);
        this.field.setCursorPositionEnd();
        this.field.setFocused(true);
        this.askedFor = null;
        this.answered = false;
        return this;
    }

    /** What the empty field reads. */
    public NamingWindow prompt(String prompt) {
        this.prompt = prompt == null ? "" : prompt;
        return this;
    }

    /** What is being named: a second ask about it puts the window away. */
    public NamingWindow about(Object about) {
        this.about = about;
        return this;
    }

    /** What is being named; null for nothing said. */
    public Object about() {
        return this.about;
    }

    /** The name as it stands, trimmed. */
    public String name() {
        return this.field == null ? "" : this.field.getText().trim();
    }

    /** How many more letters the field takes. */
    public int lettersLeft() {
        return this.field == null ? 0 : Math.max(0, this.maxLength - this.field.getText().length());
    }

    /** Why the name may not be kept now; empty when it may. */
    public String refusal() {
        String name = name();
        long now = System.nanoTime();
        if (!name.equals(this.askedFor) || now - this.askedNanos > RULE_NANOS) {
            String said = this.rule.refusal(name);
            this.refusal = said == null ? "" : said;
            this.askedFor = name;
            this.askedNanos = now;
        }
        return this.refusal;
    }

    @Override
    public String stripTitle() {
        return this.title;
    }

    @Override
    public LostTalesUiSheet stripIcon() {
        return LostTalesUiSheet.DRAFT;
    }

    @Override
    public int naturalWidth() {
        return WIDTH;
    }

    @Override
    public int naturalHeight(int width) {
        return PADDING_Y + WindowLists.fieldHeight() + PADDING_Y
                + LostTalesUiFramedButton.HEIGHT + PADDING_Y;
    }

    @Override
    public int minWidth() {
        return 120;
    }

    @Override
    public int minHeight() {
        return naturalHeight(WIDTH);
    }

    private static String word(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static String keepLabel() {
        return word("gui.losttales.window.naming.keep");
    }

    private static String cancelLabel() {
        return word("gui.cancel");
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     int alpha, int surfaceAlpha) {
        FontRenderer font = minecraft.fontRenderer;
        int left = (int)box.left + PADDING_X;
        int right = (int)(box.left + box.width) - PADDING_X;
        int top = (int)box.top + PADDING_Y;
        String count = StatCollector.translateToLocalFormatted(
                "gui.losttales.window.naming.left", Integer.valueOf(lettersLeft()));
        int countWidth = Math.max(0, font.getStringWidth(count) - 1);
        int fieldRight = right - countWidth - WindowStyle.BUTTON_GAP;
        WindowLists.drawField(font, this.field, LostTalesUiSheet.DRAFT, this.prompt,
                left, top, fieldRight, fieldRight, alpha);
        LostTalesUiInk.drawText(font, count, right - countWidth,
                top + LostTalesUiInk.centredStart(WindowLists.fieldHeight() - 1,
                        LostTalesUiInk.CAP_HEIGHT),
                WindowStyle.asideRgb(), alpha);
        String part = partAt(font, box, pointerX, pointerY);
        WordButton.draw(font, buttonBox(font, box, CANCEL), cancelLabel(), false,
                true, CANCEL.equals(part), this.cancelMotion, alpha, surfaceAlpha);
        WordButton.draw(font, buttonBox(font, box, KEEP), keepLabel(), false,
                refusal().length() == 0, KEEP.equals(part), this.keepMotion, alpha,
                surfaceAlpha);
    }

    private LostTalesUiHitBox buttonBox(FontRenderer font, LostTalesUiHitBox box,
                                        String part) {
        return WordButton.footBox(font, box, keepLabel(), cancelLabel(), PADDING_Y,
                KEEP.equals(part));
    }

    private String partAt(FontRenderer font, LostTalesUiHitBox box, double x, double y) {
        if (buttonBox(font, box, KEEP).contains(x, y)) {
            return KEEP;
        }
        return buttonBox(font, box, CANCEL).contains(x, y) ? CANCEL : null;
    }

    @Override
    public WindowHover hoverAt(LostTalesUiHitBox box, double x, double y) {
        String part = partAt(Minecraft.getMinecraft().fontRenderer, box, x, y);
        if (part == null) {
            return null;
        }
        WindowHover hover = new WindowHover(WindowHover.Kind.SUB_WINDOW);
        hover.part = part;
        if (KEEP.equals(part)) {
            hover.greyedWhy = refusal();
        }
        hover.acts = hover.greyedWhy.length() == 0;
        return hover;
    }

    /** Cancel puts the window away; Keep keeps the name, then puts it away. */
    @Override
    public boolean pressed(WindowHover hover, double x, double y, int button) {
        if (button != 0) {
            return true;
        }
        if (hover.part == null) {
            pressField(x);
            return true;
        }
        if (CANCEL.equals(hover.part)) {
            close(hover.subWindow);
        } else {
            keep(hover.subWindow);
        }
        return true;
    }

    /** A press on the field puts the caret where it landed. */
    private void pressField(double x) {
        if (this.field != null) {
            this.field.setFocused(true);
            this.field.mouseClicked((int)Math.floor(x), this.field.yPosition, 0);
        }
    }

    @Override
    public boolean holdsKeys() {
        return this.field != null && this.field.isFocused();
    }

    @Override
    public void takeKeys() {
        if (this.field != null) {
            this.field.setFocused(true);
        }
    }

    @Override
    public void releaseKeys() {
        if (this.field != null) {
            this.field.setFocused(false);
        }
    }

    @Override
    public void closed() {
        releaseKeys();
    }

    /** Enter keeps the name, or says why it cannot; every other key types. */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            WindowScreen screen = WindowScreen.current();
            SubWindow window = screen == null ? null : screen.subWindows().focused();
            if (window != null && window.content == this) {
                keep(window);
            }
            return true;
        }
        if (this.field != null) {
            this.field.textboxKeyTyped(typedChar, keyCode);
        }
        return true;
    }

    private void keep(SubWindow window) {
        if (this.answered) {
            return;
        }
        String why = refusal();
        WindowScreen screen = WindowScreen.current();
        if (why.length() > 0) {
            if (screen != null && window != null) {
                screen.showNotice(window.parentId, why);
            }
            return;
        }
        this.answered = true;
        String name = name();
        close(window);
        if (this.keeper != null) {
            this.keeper.keep(name);
        }
    }

    private static void close(SubWindow window) {
        WindowScreen screen = WindowScreen.current();
        if (screen != null && window != null) {
            screen.subWindows().close(window);
        }
    }
}
