package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiItemIcon;
import net.minecraft.client.Minecraft;

/**
 * The tab of a page: the quest journal, the party. One per page, made by
 * {@link WindowPages}, so a page stands in one window at most.
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

    @Override
    public boolean hasToolStrip() {
        return content().hasToolStrip();
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
