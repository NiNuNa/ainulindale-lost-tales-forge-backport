package com.ninuna.losttales.client.gui;

import net.minecraft.client.gui.GuiButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** One gap for the main menu's rows, columns and side buttons. */
final class MainMenuButtonLayout {
    static final int HEIGHT = 21;
    static final int GAP = 4;

    private MainMenuButtonLayout() {}

    /** Keeps the menu's column origin and row order, with uniform spacing. */
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
        for (List<GuiButton> row : rows.values()) {
            Collections.sort(row, new Comparator<GuiButton>() {
                @Override
                public int compare(GuiButton left, GuiButton right) {
                    return Integer.compare(left.xPosition, right.xPosition);
                }
            });
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
        }
    }
}
