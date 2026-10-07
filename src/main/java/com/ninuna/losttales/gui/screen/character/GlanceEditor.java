package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.client.chat.ChatEmojiIcon;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.WindowLists;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WordButton;
import com.ninuna.losttales.gui.screen.character.creator.CreatorContext;
import com.ninuna.losttales.gui.screen.character.creator.CreatorControl;
import com.ninuna.losttales.gui.screen.character.creator.CreatorRows;
import com.ninuna.losttales.gui.screen.character.creator.CreatorTextControl;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/**
 * The profile editor's Glances section, editing one glance at a time as Total RP 3 does: the
 * five places a glance can stand, as framed buttons wearing each glance's
 * emoji and a {@code +} in the first empty one; under them the chosen
 * glance's title and line as a window's field rows, every one of the
 * chat's emoji under a heading to give it, and Remove. A glance needs a
 * title before the profile saves. Its height never changes, so the window
 * stands still while glances come and go.
 */
final class GlanceEditor extends CreatorControl {
    private static final int SLOT = LostTalesUiFramedButton.HEIGHT;
    private static final int SLOT_GAP = WindowStyle.BUTTON_GAP;
    /** Between the slots, the rows, the emoji and Remove. */
    private static final int GAP = MenuWindow.PADDING_Y;
    /** An emoji's place in the grid: its box and a pixel either side. */
    private static final int GRID_STRIDE = ChatEmojiIcon.SIZE + 2;
    private static final int GRID_ROWS = 3;

    /** One glance as it is being written. */
    private static final class Draft {
        String emoji;
        String title;
        String line;

        Draft(String emoji, String title, String line) {
            this.emoji = emoji;
            this.title = title;
            this.line = line;
        }
    }

    private final List<Draft> drafts = new ArrayList<Draft>();
    private final LostTalesUiButtonMotion removeMotion =
            new LostTalesUiButtonMotion(LostTalesUiButtonMotion.Character.SNAP);
    private int selected = -1;
    private CreatorTextControl title;
    private CreatorTextControl line;
    /** The field the keys go to while the editor holds them. */
    private CreatorTextControl typing;

    GlanceEditor(CreatorContext context, List<CharacterProfile.Glance> start) {
        super(context);
        for (CharacterProfile.Glance glance : start) {
            this.drafts.add(new Draft(glance.getEmoji(), glance.getTitle(),
                    glance.getLine()));
        }
        select(this.drafts.isEmpty() ? -1 : 0);
    }

    /** The glances as written, in order. */
    List<CharacterProfile.Glance> glances() {
        writeBack();
        List<CharacterProfile.Glance> glances =
                new ArrayList<CharacterProfile.Glance>(this.drafts.size());
        for (Draft draft : this.drafts) {
            glances.add(new CharacterProfile.Glance(draft.emoji, draft.title,
                    draft.line));
        }
        return glances;
    }

    /** Whether every glance has a title, which a glance needs to be kept. */
    boolean allTitled() {
        writeBack();
        for (Draft draft : this.drafts) {
            if (draft.title.trim().length() == 0) {
                return false;
            }
        }
        return true;
    }

    private void select(int index) {
        writeBack();
        this.selected = index;
        Draft draft = index < 0 ? null : this.drafts.get(index);
        this.title = new CreatorTextControl(this.context, I18n.format(
                "gui.losttales.character.profile.glance.title"),
                draft == null ? "" : draft.title,
                CharacterProfile.MAX_GLANCE_TITLE_LENGTH, true);
        this.line = new CreatorTextControl(this.context, I18n.format(
                "gui.losttales.character.profile.glance.line"),
                draft == null ? "" : draft.line,
                CharacterProfile.MAX_GLANCE_LINE_LENGTH, true);
        this.typing = this.title;
        if (isFocused() && draft != null) {
            this.title.setFocused(true);
        }
    }

    /** What the fields hold, back into the chosen glance. */
    private void writeBack() {
        if (this.selected >= 0 && this.selected < this.drafts.size()
                && this.title != null) {
            Draft draft = this.drafts.get(this.selected);
            draft.title = this.title.getText();
            draft.line = this.line.getText();
        }
    }

    /* ---- Where things stand ---- */

    private int slotsTop() {
        return this.y;
    }

    private int slotLeft(int index) {
        return this.x + index * (SLOT + SLOT_GAP);
    }

    private int fieldsTop() {
        return slotsTop() + SLOT + GAP;
    }

    /** The emoji's heading, under the two field rows. */
    private int headingTop() {
        return fieldsTop() + this.title.height() + this.line.height();
    }

    private int gridTop() {
        return headingTop() + CreatorRows.height() + GAP;
    }

    /** The title's and the line's rows, under the slots. */
    private void placeFields() {
        this.title.place(this.x, fieldsTop(), this.width);
        this.line.place(this.x, fieldsTop() + this.title.height(), this.width);
    }

    private int gridColumns() {
        return Math.max(1, this.width / GRID_STRIDE);
    }

    private LostTalesUiHitBox removeBox(FontRenderer font) {
        return new LostTalesUiHitBox(this.x, gridTop() + GRID_ROWS * GRID_STRIDE
                + GAP, WordButton.width(font, removeLabel()),
                LostTalesUiFramedButton.HEIGHT);
    }

    private static String removeLabel() {
        return I18n.format("gui.losttales.character.profile.glance.remove");
    }

    @Override
    public int height() {
        return SLOT + GAP + this.title.height() + this.line.height()
                + CreatorRows.height() + GAP + GRID_ROWS * GRID_STRIDE + GAP
                + LostTalesUiFramedButton.HEIGHT;
    }

    /** The slot under the point: a glance's, the {@code +}'s, or -1. */
    private int slotAt(int mouseX, int mouseY) {
        if (mouseY < slotsTop() || mouseY >= slotsTop() + SLOT) {
            return -1;
        }
        int shown = Math.min(CharacterProfile.MAX_GLANCES,
                this.drafts.size() + 1);
        for (int index = 0; index < shown; index++) {
            int left = slotLeft(index);
            if (mouseX >= left && mouseX < left + SLOT) {
                return index;
            }
        }
        return -1;
    }

    /** The emoji under the point in the grid, or null. */
    private ChatEmoji emojiAt(int mouseX, int mouseY) {
        if (this.selected < 0 || mouseX < this.x || mouseY < gridTop()) {
            return null;
        }
        int column = (mouseX - this.x) / GRID_STRIDE;
        int row = (mouseY - gridTop()) / GRID_STRIDE;
        int columns = gridColumns();
        if (column >= columns || row >= GRID_ROWS) {
            return null;
        }
        int index = row * columns + column;
        ChatEmoji[] all = ChatEmoji.values();
        return index < all.length ? all[index] : null;
    }

    /* ---- Drawing ---- */

    @Override
    public void draw(int mouseX, int mouseY) {
        FontRenderer font = this.context.getFont();
        int alpha = this.context.alpha();
        int shown = Math.min(CharacterProfile.MAX_GLANCES,
                this.drafts.size() + 1);
        int hoveredSlot = slotAt(mouseX, mouseY);
        for (int index = 0; index < shown; index++) {
            drawSlot(index, index == this.selected || index == hoveredSlot);
        }
        placeFields();
        if (this.selected < 0) {
            LostTalesUiInk.drawText(font, font.trimStringToWidth(I18n.format(
                    "gui.losttales.character.profile.glance.none"),
                    this.width), this.x, CreatorRows.textTop(fieldsTop()),
                    WindowStyle.asideRgb(), alpha);
            return;
        }
        this.title.draw(mouseX, mouseY);
        this.line.draw(mouseX, mouseY);
        WindowLists.drawHeading(font, I18n.format(
                "gui.losttales.character.profile.glance.emoji"), this.x,
                this.x, this.x + this.width, headingTop(),
                CreatorRows.height(), false, alpha);
        drawGrid(mouseX, mouseY);
        LostTalesUiHitBox remove = removeBox(font);
        WordButton.draw(font, remove, removeLabel(), true, true,
                remove.contains(mouseX, mouseY), this.removeMotion, alpha,
                this.context.surfaceAlpha());
    }

    private void drawSlot(int index, boolean lit) {
        int alpha = this.context.alpha();
        float left = slotLeft(index);
        float top = slotsTop();
        LostTalesUiFramedButton.drawSurface(left, top, SLOT, SLOT,
                lit ? 1.0F : 0.0F, this.context.surfaceAlpha());
        LostTalesUiInk.beginContent();
        if (index < this.drafts.size()) {
            ChatEmoji emoji = ChatEmoji.fromName(this.drafts.get(index).emoji);
            int inset = LostTalesUiInk.centredStart(SLOT, ChatEmojiIcon.SIZE);
            ChatEmojiIcon.draw(this.context.getMinecraft(), emoji,
                    (int)left + inset, (int)top + inset, alpha);
        } else {
            LostTalesUiSheet plus = lit ? LostTalesUiSheet.PLUS_ADD
                    : LostTalesUiSheet.PLUS;
            plus.drawWithShadow(left + LostTalesUiInk.centredStart(SLOT,
                    plus.getWidth()), top + LostTalesUiInk.centredStart(SLOT,
                    plus.getHeight()), alpha);
        }
        LostTalesUiFramedButton.drawInk(left, top, SLOT, SLOT,
                lit ? 1.0F : 0.0F, alpha);
    }

    /**
     * Every emoji of the chat, the chosen one's place and the one under
     * the pointer lit as a menu's rows are, before the emoji lands on it.
     */
    private void drawGrid(int mouseX, int mouseY) {
        ChatEmoji[] all = ChatEmoji.values();
        int columns = gridColumns();
        String chosen = this.drafts.get(this.selected).emoji;
        ChatEmoji hovered = emojiAt(mouseX, mouseY);
        for (int index = 0; index < all.length
                && index < columns * GRID_ROWS; index++) {
            int left = this.x + (index % columns) * GRID_STRIDE;
            int top = gridTop() + (index / columns) * GRID_STRIDE;
            if (all[index].getName().equals(chosen) || all[index] == hovered) {
                CreatorRows.light(this.context, left, top, left + GRID_STRIDE,
                        top + GRID_STRIDE);
            }
            LostTalesUiInk.beginContent();
            ChatEmojiIcon.draw(this.context.getMinecraft(), all[index],
                    left + 1, top + 1, this.context.alpha());
        }
    }

    /* ---- Input ---- */

    @Override
    public boolean isPointerOverAction(int mouseX, int mouseY) {
        FontRenderer font = this.context.getFont();
        return slotAt(mouseX, mouseY) >= 0 || emojiAt(mouseX, mouseY) != null
                || (this.selected >= 0
                        && removeBox(font).contains(mouseX, mouseY));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!contains(mouseX, mouseY) || button != 0) {
            return false;
        }
        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0) {
            if (slot == this.drafts.size()) {
                // The + makes a glance, wearing the first emoji until
                // another is chosen, and puts its title under the keys.
                this.drafts.add(new Draft(ChatEmoji.values()[0].getName(),
                        "", ""));
            }
            select(slot);
            return true;
        }
        ChatEmoji emoji = emojiAt(mouseX, mouseY);
        if (emoji != null) {
            this.drafts.get(this.selected).emoji = emoji.getName();
            return true;
        }
        if (this.selected >= 0
                && removeBox(this.context.getFont()).contains(mouseX, mouseY)) {
            int removed = this.selected;
            this.drafts.remove(removed);
            this.selected = -1;
            select(this.drafts.isEmpty() ? -1
                    : Math.min(this.drafts.size() - 1, removed));
            return true;
        }
        if (this.selected >= 0) {
            placeFields();
            for (CreatorTextControl field : new CreatorTextControl[] {
                    this.title, this.line}) {
                if (field.contains(mouseX, mouseY)) {
                    this.typing.setFocused(false);
                    this.typing = field;
                    field.setFocused(true);
                    field.mouseClicked(mouseX, mouseY, button);
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        this.title.setFocused(false);
        this.line.setFocused(false);
        if (focused && this.selected >= 0) {
            this.typing.setFocused(true);
        }
    }

    @Override
    public void tick() {
        this.title.tick();
        this.line.tick();
    }

    /** The keys go to the field in use; Tab steps from the title to the line and back. */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.selected < 0) {
            return false;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            boolean back = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                    || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            CreatorTextControl next = back ? this.title : this.line;
            if (next == this.typing) {
                return false;
            }
            this.typing.setFocused(false);
            this.typing = next;
            next.setFocused(true);
            return true;
        }
        return this.typing.keyTyped(typedChar, keyCode);
    }
}
