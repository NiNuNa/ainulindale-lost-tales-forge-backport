package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiItemIcon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

/**
 * The tab of a page: the journal, the map, a waystone. One per page,
 * made by {@link WindowPages}, so a page stands in one window at most.
 */
public final class PageTab extends WindowTab {
    /** What a page tab's id opens with, before the page's code name. */
    static final String ID_PREFIX = "page:";

    private final WindowPages.Page page;

    PageTab(WindowPages.Page page) {
        this.page = page;
    }

    /** The page the tab shows. */
    public WindowPages.Page page() {
        return this.page;
    }

    /** What the page holds, made the first time it is asked for. */
    public PageContent content() {
        return this.page.content();
    }

    @Override
    public String id() {
        return ID_PREFIX + this.page.id;
    }

    @Override
    public String title() {
        return this.page.title();
    }

    @Override
    public int tone() {
        return content().tone();
    }

    @Override
    public void drawIcon(Minecraft minecraft, float x, float y, int alpha,
                         TabMark mark) {
        LostTalesUiItemIcon.drawFitted(minecraft, this.page.icon(), x, y,
                TabIcons.SIZE, alpha);
    }

    @Override
    public boolean isAvailable() {
        return content().isAvailable();
    }

    /** Whether the page is on screen: the window screen open, and the page in front of its window. */
    public boolean isShown() {
        Window window = WindowLayout.windowOf(this);
        return window != null && WindowScreen.current() != null
                && equals(WindowFrame.activeTab(window,
                        WindowFrame.visibleTabs(window)));
    }

    /** A page that stands for a thing in the world ends with the session. */
    @Override
    public boolean isKeptInLayout() {
        return !this.page.opensFromWorld();
    }

    /**
     * The page's own rows, under their heading with the chosen one marked
     * in honey, as a chosen status is; one that cannot be taken greyed.
     */
    @Override
    public List<MenuWindow.Entry> menuRows() {
        PageContent page = content();
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        for (PageContent.Choice choice : page.choices()) {
            rows.add(new MenuWindow.Entry(choice.id, choice.label, false,
                    choice.chosen ? LostTalesColors.rgb(LostTalesColors.HONEY)
                            : -1, null).unavailable(choice.unavailable));
        }
        if (rows.isEmpty() || page.choicesHeading().length() == 0) {
            return rows;
        }
        List<MenuWindow.Entry> headed = new ArrayList<MenuWindow.Entry>();
        WindowMenus.addSection(headed, StatCollector.translateToLocal(
                page.choicesHeading()), rows);
        return headed;
    }

    @Override
    public boolean hasMenuRows() {
        return !content().choices().isEmpty();
    }

    /** A choice taken; the menu stays for the next. */
    @Override
    public boolean takeMenuRow(String id) {
        content().choose(id);
        return true;
    }

    @Override
    public ToolStrip.Panel panel() {
        return content().panel();
    }

    @Override
    public boolean isPanelOut(Window window) {
        return content().isPanelOut();
    }

    @Override
    public void togglePanel(Window window) {
        content().togglePanel();
    }

    @Override
    public String searchPrompt() {
        return content().searchPrompt();
    }

    @Override
    public int searchFound() {
        return content().found();
    }

    /** A page that names nothing it searches has nothing to search. */
    @Override
    public String searchUnavailable() {
        return content().searchPrompt().length() > 0 ? ""
                : StatCollector.translateToLocalFormatted(
                        "gui.losttales.window.search.nothing", title());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PageTab
                && ((PageTab)other).page.id.equals(this.page.id);
    }

    @Override
    public int hashCode() {
        return this.page.id.hashCode() * 31 + 7;
    }
}
