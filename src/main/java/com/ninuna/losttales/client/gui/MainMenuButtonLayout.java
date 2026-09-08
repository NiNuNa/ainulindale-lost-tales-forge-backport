package com.ninuna.losttales.client.gui;

import net.minecraft.client.gui.GuiButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Shared button spacing, with a larger gap before the menu's utility row. */
final class MainMenuButtonLayout {
    static final int HEIGHT = 20;
    static final int GAP = 4;
    static final int FOOTER_GAP = GAP + 12;
    static final int TOP_OFFSET = 48;
    static final int BOTTOM_MARGIN = 12;
    /**
     * The height of the standard menu's column: Singleplayer, Multiplayer,
     * the Mods and Realms row, the wider gap, and the Options and Quit
     * row. Every menu is centred on where that column's middle falls, so
     * a menu with fewer rows sits where the standard one would, not
     * hanging from its top.
     */
    static final int BASELINE_COLUMN_HEIGHT = HEIGHT * 4 + GAP * 2 + FOOTER_GAP;

    /** Vanilla's ids for the controls a menu without a character withholds. */
    static final int SINGLEPLAYER_ID = 1;
    static final int MULTIPLAYER_ID = 2;
    static final int REALMS_ID = 14;
    /** Forge's mod list, which shares Realms' row. */
    static final int MODS_ID = 6;

    private MainMenuButtonLayout() {}

    /**
     * Takes Multiplayer and Realms out of the menu before the rows are
     * laid out, so their rows close up: Singleplayer's row is kept to
     * anchor the column, and Realms' row closes around Mods alone.
     * Nothing to do for a button the menu does not have.
     */
    static void withholdPlayButtons(List<?> buttons) {
        GuiButton multiplayer = find(buttons, MULTIPLAYER_ID);
        if (multiplayer != null) {
            multiplayer.visible = false;
        }
        GuiButton realms = find(buttons, REALMS_ID);
        if (realms != null) {
            realms.visible = false;
        }
    }

    /**
     * Withholds Singleplayer once the column has been positioned and
     * answers its frame, as {@code x, y, width, height}, for the one
     * control that stands in for the play buttons; Mods, left alone in
     * its row, is widened to the column. Null, with nothing changed,
     * when the menu has no Singleplayer button.
     */
    static int[] replacePlayButtons(List<?> buttons) {
        GuiButton singleplayer = find(buttons, SINGLEPLAYER_ID);
        if (singleplayer == null || !singleplayer.visible) {
            return null;
        }
        singleplayer.visible = false;
        GuiButton mods = find(buttons, MODS_ID);
        if (mods != null && mods.visible && isAloneInRow(buttons, mods)) {
            mods.xPosition = singleplayer.xPosition;
            mods.width = singleplayer.width;
        }
        return new int[] {singleplayer.xPosition, singleplayer.yPosition,
                singleplayer.width, singleplayer.height};
    }

    private static boolean isAloneInRow(List<?> buttons, GuiButton button) {
        for (Object value : buttons) {
            if (value instanceof GuiButton && value != button
                    && ((GuiButton) value).visible
                    && ((GuiButton) value).yPosition == button.yPosition
                    && ((GuiButton) value).id != 5) {
                return false;
            }
        }
        return true;
    }

    private static GuiButton find(List<?> buttons, int id) {
        for (Object value : buttons) {
            if (value instanceof GuiButton && ((GuiButton) value).id == id) {
                return (GuiButton) value;
            }
        }
        return null;
    }

    /**
     * Centers the main column and anchors the standard menu's first row a
     * quarter down the screen; a shorter or taller column is centred on
     * the same middle the standard one has there.
     */
    static void position(List<?> buttons, int screenWidth, int screenHeight) {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (Object value : buttons) {
            if (!(value instanceof LostTalesButton) || !((GuiButton) value).visible
                    || value instanceof LostTalesCharacterMenuButton || ((GuiButton) value).id == 5) {
                continue;
            }
            GuiButton button = (GuiButton) value;
            left = Math.min(left, button.xPosition);
            top = Math.min(top, button.yPosition);
            right = Math.max(right, button.xPosition + button.width);
            bottom = Math.max(bottom, button.yPosition + button.height);
        }
        if (left == Integer.MAX_VALUE) {
            return;
        }
        // A shared integer translation preserves pixel alignment and every gap.
        int offsetX = (screenWidth - (right - left)) / 2 - left;
        int preferredTop = screenHeight / 4 + TOP_OFFSET
                + baselineShift(bottom - top);
        int availableTop = screenHeight - BOTTOM_MARGIN - (bottom - top);
        int offsetY = Math.max(0, Math.min(preferredTop, availableTop)) - top;
        for (Object value : buttons) {
            if (value instanceof LostTalesButton && ((GuiButton) value).visible) {
                GuiButton button = (GuiButton) value;
                button.xPosition += offsetX;
                button.yPosition += offsetY;
            }
        }
    }

    /**
     * How far down a column that tall starts so that its middle is the
     * standard column's middle. Zero for the standard column itself.
     */
    static int baselineShift(int columnHeight) {
        return (BASELINE_COLUMN_HEIGHT - columnHeight) / 2;
    }

    /** Keeps row order and separates Options/Quit/Language from the play controls. */
    static void arrange(List<?> buttons) {
        Map<Integer, List<GuiButton>> rows = new TreeMap<Integer, List<GuiButton>>();
        int columnLeft = Integer.MAX_VALUE;
        for (Object value : buttons) {
            if (!(value instanceof LostTalesButton)
                    || value instanceof LostTalesCharacterMenuButton) {
                continue;
            }
            GuiButton button = (GuiButton) value;
            if (!button.visible) {
                continue;
            }
            List<GuiButton> row = rows.get(button.yPosition);
            if (row == null) {
                row = new ArrayList<GuiButton>();
                rows.put(button.yPosition, row);
            }
            row.add(button);
            if (button.id != 5) {
                columnLeft = Math.min(columnLeft, button.xPosition);
            }
        }
        if (columnLeft == Integer.MAX_VALUE) {
            return;
        }
        int top = rows.keySet().iterator().next();
        boolean firstRow = true;
        for (List<GuiButton> row : rows.values()) {
            if (!firstRow && isUtilityRow(row)) {
                top += FOOTER_GAP - GAP;
            }
            Collections.sort(row, new Comparator<GuiButton>() {
                @Override
                public int compare(GuiButton left, GuiButton right) {
                    return Integer.compare(left.xPosition, right.xPosition);
                }
            });
            // Forge's modern menu puts Mods before Realms in the shared row.
            int modsIndex = indexOf(row, 6);
            int realmsIndex = indexOf(row, 14);
            if (modsIndex >= 0 && realmsIndex >= 0 && modsIndex > realmsIndex) {
                Collections.swap(row, modsIndex, realmsIndex);
            }
            int x = columnLeft;
            for (GuiButton button : row) {
                button.yPosition = top;
                button.height = HEIGHT;
                if (button.id == 5) {
                    button.xPosition = columnLeft - GAP - button.width;
                } else {
                    button.xPosition = x;
                    x += button.width + GAP;
                }
            }
            top += HEIGHT + GAP;
            firstRow = false;
        }
    }

    private static int indexOf(List<GuiButton> row, int id) {
        for (int index = 0; index < row.size(); index++) {
            if (row.get(index).id == id) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isUtilityRow(List<GuiButton> row) {
        for (GuiButton button : row) {
            if (button.id == 0 || button.id == 4 || button.id == 5) {
                return true;
            }
        }
        return false;
    }
}
