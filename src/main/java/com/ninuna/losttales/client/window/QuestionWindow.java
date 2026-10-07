package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

/**
 * A question before an action that cannot be undone — abandoning a
 * quest, leaving a fellowship — asked in a sub-window inside the window that
 * wants the answer: what will happen, then Cancel and the action itself,
 * in red, at the foot. The cross, Escape and Cancel all say no. Closing
 * the screen says no too: a question never comes back with it.
 */
public final class QuestionWindow extends SubWindowContent {
    private static final String CONFIRM = "confirm";
    private static final String CANCEL = "cancel";
    private static final int WIDTH = 200;
    /** The menus' clear room round the words and the buttons, across and down. */
    private static final int PADDING_X = MenuWindow.PADDING_X;
    private static final int PADDING_Y = MenuWindow.PADDING_Y;
    /** The words' room: a menu note's, its lines and the clear above and under them. */
    private static final int NOTE_LINE = MenuWindow.NOTE_LINE;
    private static final int NOTE_PADDING = MenuWindow.NOTE_PADDING;

    private String title = "";
    private String detail = "";
    private String confirmLabel = "";
    private Runnable action;
    private final LostTalesUiButtonMotion confirmMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    private final LostTalesUiButtonMotion cancelMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.LIFT);
    /** Set once answered, so a second press takes nothing. */
    private boolean answered;

    /**
     * Puts a question: its title on the strip, what will happen, the
     * action's word and the action. A window already asking takes the new
     * question in its place.
     */
    public void ask(String title, String detail, String confirmLabel,
                    Runnable action) {
        this.title = title == null ? "" : title;
        this.detail = detail == null ? "" : detail;
        this.confirmLabel = confirmLabel == null ? "" : confirmLabel;
        this.action = action;
        this.answered = false;
    }

    @Override
    public String stripTitle() {
        return this.title;
    }

    @Override
    public LostTalesUiSheet stripIcon() {
        return LostTalesUiSheet.QUESTION;
    }

    @Override
    public int naturalWidth() {
        return WIDTH;
    }

    /** The words as a menu's note, then the buttons, the menus' padding round both. */
    @Override
    public int naturalHeight(int width) {
        return PADDING_Y + noteHeight(lines(width).size())
                + LostTalesUiFramedButton.HEIGHT + PADDING_Y;
    }

    private static int noteHeight(int lines) {
        return NOTE_PADDING + lines * NOTE_LINE + NOTE_PADDING;
    }

    @Override
    public int minWidth() {
        return 120;
    }

    @Override
    public int minHeight() {
        return PADDING_Y * 2 + noteHeight(1) + LostTalesUiFramedButton.HEIGHT;
    }

    private List<String> lines(int width) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        @SuppressWarnings("unchecked")
        List<String> lines = font.listFormattedStringToWidth(this.detail,
                Math.max(1, width - 2 * PADDING_X));
        return lines;
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     int alpha, int surfaceAlpha) {
        FontRenderer font = minecraft.fontRenderer;
        int y = (int)box.top + PADDING_Y + NOTE_PADDING;
        for (String line : lines((int)box.width)) {
            LostTalesUiInk.drawText(font, line, (int)box.left + PADDING_X,
                    y + LostTalesUiInk.centredStart(NOTE_LINE,
                            LostTalesUiInk.CAP_HEIGHT),
                    LostTalesUiInk.IVORY, alpha);
            y += NOTE_LINE;
        }
        String part = partAt(font, box, pointerX, pointerY);
        WordButton.draw(font, buttonBox(font, box, CANCEL), cancelLabel(),
                false, true, CANCEL.equals(part), this.cancelMotion, alpha,
                surfaceAlpha);
        WordButton.draw(font, buttonBox(font, box, CONFIRM),
                this.confirmLabel, true, true, CONFIRM.equals(part),
                this.confirmMotion, alpha, surfaceAlpha);
    }

    private static String cancelLabel() {
        return StatCollector.translateToLocal("gui.cancel");
    }

    /** The two buttons at the foot's right, the action last, a framed button's gap apart. */
    private LostTalesUiHitBox buttonBox(FontRenderer font, LostTalesUiHitBox box,
                                        String part) {
        int confirmWidth = WordButton.width(font, this.confirmLabel);
        int cancelWidth = WordButton.width(font, cancelLabel());
        double top = box.top + box.height - PADDING_Y
                - LostTalesUiFramedButton.HEIGHT;
        double confirmLeft = box.left + box.width - WindowStyle.EDGE_GAP
                - confirmWidth;
        if (CONFIRM.equals(part)) {
            return new LostTalesUiHitBox(confirmLeft, top, confirmWidth,
                    LostTalesUiFramedButton.HEIGHT);
        }
        return new LostTalesUiHitBox(confirmLeft - WindowBar.BUTTON_GAP
                - cancelWidth, top, cancelWidth,
                LostTalesUiFramedButton.HEIGHT);
    }


    private String partAt(FontRenderer font, LostTalesUiHitBox box, double x,
                          double y) {
        if (buttonBox(font, box, CONFIRM).contains(x, y)) {
            return CONFIRM;
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
        hover.acts = true;
        return hover;
    }

    /** Cancel closes the question; the action runs, then closes it. */
    @Override
    public boolean pressed(WindowHover hover, double x, double y,
                           int button) {
        if (button != 0 || hover.part == null || this.answered) {
            return true;
        }
        this.answered = true;
        WindowScreen screen = WindowScreen.current();
        if (screen != null) {
            screen.subWindows().close(hover.subWindow);
        }
        if (CONFIRM.equals(hover.part) && this.action != null) {
            this.action.run();
        }
        return true;
    }
}
