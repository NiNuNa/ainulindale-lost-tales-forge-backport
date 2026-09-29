package com.ninuna.losttales.client.window;

import java.util.ArrayList;
import java.util.List;

/**
 * The order the tab keys walk, as a browser's: every tab a window shows,
 * window by window in the layout's order and each window's in its row's
 * order, pages and conversations alike.
 */
final class TabWalk {
    private TabWalk() {}

    /** Every tab the windows show, in the order Ctrl+Tab walks them. */
    static List<WindowTab> everyTab(List<Window> windows) {
        List<WindowTab> order = new ArrayList<WindowTab>();
        for (Window window : windows) {
            order.addAll(WindowFrame.visibleTabs(window));
        }
        return order;
    }

    /**
     * The tab {@code step} places along {@code order} from {@code from},
     * round from one end to the other; from a tab not in it, the first
     * going forward and the last going back. Null for an empty order.
     */
    static WindowTab step(List<WindowTab> order, WindowTab from, int step) {
        if (order.isEmpty()) {
            return null;
        }
        int at = order.indexOf(from);
        if (at < 0) {
            return order.get(step >= 0 ? 0 : order.size() - 1);
        }
        int size = order.size();
        return order.get(((at + step) % size + size) % size);
    }

    /**
     * The tab at {@code ordinal}, counted from one along a row, as a
     * browser's Ctrl+1 to Ctrl+8 reach its tabs; nine is always the last.
     * Null for a number past the row or under one.
     */
    static WindowTab ordinal(List<WindowTab> row, int ordinal) {
        if (ordinal < 1 || row.isEmpty()) {
            return null;
        }
        int index = ordinal >= 9 ? row.size() - 1 : ordinal - 1;
        return index < row.size() ? row.get(index) : null;
    }
}
