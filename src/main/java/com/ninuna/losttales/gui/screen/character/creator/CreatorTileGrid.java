package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Keyboard;

/**
 * The skins, as a grid of faces: every appearance the race and sex allow,
 * each tile the head that skin would give the character, the chosen one
 * bracketed and named above the grid. A click chooses; the arrow keys
 * walk the grid while it holds the focus.
 *
 * <p>The tiles are heads because a head is what tells skins apart at
 * this size, and because the head renderer already draws one from a skin
 * id alone. The full figure on the stage shows the rest.</p>
 */
public final class CreatorTileGrid extends CreatorControl {

    private static final int TILE = 26;
    private static final int TILE_GAP = 4;
    private static final int HEAD_INSET = 3;
    private static final int TITLE_HEIGHT = 12;

    private final String label;
    private final CreatorChoice choice;

    public CreatorTileGrid(CreatorContext context, String label,
                           CreatorChoice choice) {
        super(context);
        this.label = label;
        this.choice = choice;
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    private int columns() {
        return Math.max(1, (this.width + TILE_GAP) / (TILE + TILE_GAP));
    }

    private int rows() {
        int count = this.choice.count();
        return count == 0 ? 0 : (count + columns() - 1) / columns();
    }

    @Override
    public int height() {
        int rows = rows();
        return TITLE_HEIGHT + (rows == 0
                ? LABEL_HEIGHT : rows * (TILE + TILE_GAP) - TILE_GAP) + 2;
    }

    private int gridTop() {
        return this.y + TITLE_HEIGHT;
    }

    private int tileX(int index) {
        return this.x + (index % columns()) * (TILE + TILE_GAP);
    }

    private int tileY(int index) {
        return gridTop() + (index / columns()) * (TILE + TILE_GAP);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        FontRenderer font = this.context.getFont();
        int count = this.choice.count();
        int selected = this.choice.index();
        String title = this.label;
        if (count > 0 && selected >= 0 && selected < count) {
            title = this.label + ": " + this.choice.label(selected);
        }
        drawLabel(LostTalesSkyrimUiStyle.trimToWidth(font, title, this.width));
        if (count == 0) {
            font.drawStringWithShadow(LostTalesSkyrimUiStyle.trimToWidth(font,
                    this.choice.emptyLabel(), this.width), this.x, gridTop(),
                    LostTalesSkyrimUiStyle.TEXT_MUTED);
            return;
        }
        int hovered = tileAt(mouseX, mouseY);
        for (int index = 0; index < count; index++) {
            int tileX = tileX(index);
            int tileY = tileY(index);
            boolean isSelected = index == selected;
            Gui.drawRect(tileX, tileY, tileX + TILE, tileY + TILE,
                    isSelected ? LostTalesSkyrimUiStyle.PANEL_SELECTED
                            : index == hovered
                            ? LostTalesSkyrimUiStyle.PANEL_HOVER
                            : LostTalesSkyrimUiStyle.withAlpha(
                                    LostTalesSkyrimUiStyle.PLUM_BLACK, 0x70));
            CreatorWidgets.drawFrame(tileX, tileY, TILE, TILE,
                    isSelected ? LostTalesSkyrimUiStyle.BORDER
                            : LostTalesSkyrimUiStyle.BORDER_DIM);
            LostTalesSkyrimUiStyle.beginContent();
            LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                    this.context.getMinecraft(), this.context.getAccountId(),
                    this.choice.id(index), tileX + HEAD_INSET,
                    tileY + HEAD_INSET, TILE - HEAD_INSET * 2, 1.0F, 1.0F);
            if (isSelected) {
                LostTalesSkyrimUiStyle.drawSelectionBrackets(tileX - 1, tileY - 1,
                        TILE + 2, TILE + 2, 4, isFocused()
                                ? LostTalesSkyrimUiStyle.GOLD
                                : LostTalesSkyrimUiStyle.TEXT_BRIGHT);
                LostTalesSkyrimUiStyle.beginContent();
            }
        }
    }

    private int tileAt(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY) || mouseY < gridTop()) {
            return -1;
        }
        int column = (mouseX - this.x) / (TILE + TILE_GAP);
        int row = (mouseY - gridTop()) / (TILE + TILE_GAP);
        if (column >= columns() || (mouseX - this.x) % (TILE + TILE_GAP) >= TILE
                || (mouseY - gridTop()) % (TILE + TILE_GAP) >= TILE) {
            return -1;
        }
        int index = row * columns() + column;
        return index >= 0 && index < this.choice.count() ? index : -1;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        int index = tileAt(mouseX, mouseY);
        if (index >= 0) {
            this.choice.choose(index);
        }
        return true;
    }

    @Override
    public boolean isPointerOverAction(int mouseX, int mouseY) {
        return tileAt(mouseX, mouseY) >= 0;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        int count = this.choice.count();
        if (count == 0) {
            return false;
        }
        int current = Math.max(0, this.choice.index());
        int next;
        switch (keyCode) {
            case Keyboard.KEY_LEFT:
                next = current - 1;
                break;
            case Keyboard.KEY_RIGHT:
                next = current + 1;
                break;
            case Keyboard.KEY_UP:
                next = current - columns();
                break;
            case Keyboard.KEY_DOWN:
                next = current + columns();
                break;
            default:
                return false;
        }
        if (next >= 0 && next < count) {
            this.choice.choose(next);
        }
        return true;
    }
}
