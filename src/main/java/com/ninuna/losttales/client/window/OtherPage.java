package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiItemIcon;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

/**
 * The tab of a page: the journal, the map, a waystone. A page may stand
 * open more than once, each copy with a tab and a content of its own; a
 * page standing for a thing in the world opens once.
 */
public final class OtherPage extends WindowPage {
    /** What a page tab's id opens with, before the page's code name. */
    static final String ID_PREFIX = "page:";

    private final WindowPages.Page page;
    private final int instance;

    OtherPage(WindowPages.Page page, int instance) {
        this.page = page;
        this.instance = instance;
    }

    /** The page the tab shows. */
    public WindowPages.Page page() {
        return this.page;
    }

    /** What this copy of the page holds, made the first time it is asked for. */
    public PageContent content() {
        return this.page.content(this.instance);
    }

    @Override
    public String id() {
        return instanceId(ID_PREFIX + this.page.id, this.instance);
    }

    @Override
    public int instance() {
        return this.instance;
    }

    @Override
    public WindowPage withInstance(int instance) {
        return instance == this.instance ? this
                : instance == 1 || opensMoreThanOnce() ? this.page.tab(instance)
                : null;
    }

    /** A new copy past the first starts afresh. */
    @Override
    public void forgetCopy() {
        this.page.forgetCopy(this.instance);
    }

    /** Every page opens more than once but one standing for a thing in the world, or one registered to open once. */
    @Override
    public boolean opensMoreThanOnce() {
        return this.page.opensMoreThanOnce();
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
    public PageCategory category() {
        return this.page.category();
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

    @Override
    public List<PageOption> options() {
        return content().options();
    }

    /**
     * The page's help: the guide the language file holds for it under
     * {@code gui.losttales.help.page.<its code name>}, and its keys.
     */
    @Override
    public PageHelp help() {
        return new PageHelp(PageHelp.paragraphs(
                "gui.losttales.help.page." + this.page.id),
                content().keyAreas());
    }

    @Override
    public Settings.Place settingsPlace() {
        return content().settingsPlace();
    }

    @Override
    public boolean takeOption(String id) {
        return content().takeOption(id);
    }

    @Override
    public ToolStrip.Panel panel() {
        return content().panel();
    }

    @Override
    public boolean isPanelOut() {
        return content().isPanelOut();
    }

    @Override
    public void togglePanel() {
        content().togglePanel();
    }

    @Override
    public void resetView() {
        content().resetPanel();
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
        return other instanceof OtherPage
                && ((OtherPage)other).page.id.equals(this.page.id)
                && ((OtherPage)other).instance == this.instance;
    }

    @Override
    public int hashCode() {
        return (this.page.id.hashCode() * 31 + 7) * 31 + this.instance;
    }
}
