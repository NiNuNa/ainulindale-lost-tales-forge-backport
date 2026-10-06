package com.ninuna.losttales.client.window;

import org.lwjgl.input.Keyboard;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/**
 * The Client Settings page: every client option that has no window or
 * page of its own ({@link Settings.Place#CLIENT}) — the camera, the
 * screens, the look of bodies, the shortcuts —
 * in Settings' sections and rows, under the search in the window's tool
 * strip, Defaults last. Ctrl+, opens it on the window screen, and so
 * does the character menu. A colour's row opens its palette and a
 * number's value or a line the field it is typed into, each hung from
 * its row.
 */
public final class ClientSettingsPage extends PageContent {
    public static final String PAGE_ID = "client_settings";
    public static final ItemStack ICON = new ItemStack(Items.comparator);

    private final PageRows list;
    /** What the tool strip's search holds; empty for everything. */
    private String query = "";

    public ClientSettingsPage() {
        this.list = new PageRows(new PageRows.Taker() {
            @Override
            public void take(MenuWindow.Entry entry, String part,
                             boolean back, LostTalesUiHitBox row) {
                WindowScreen screen = WindowScreen.current();
                if (screen == null) {
                    return;
                }
                Settings.Setting opened = screen.settings().take(
                        Settings.Place.CLIENT, entry, part, back);
                if (opened != null && row != null) {
                    open(screen, opened, row);
                }
            }
        });
    }

    /** A colour's palette, a few-word setting's words, or the field a number or a line is typed into, hung from its row. */
    private static void open(WindowScreen screen, Settings.Setting setting,
                             LostTalesUiHitBox row) {
        OtherPage tab = WindowPages.tab(PAGE_ID);
        Window window = tab == null ? null : WindowLayout.windowOf(tab);
        if (window == null) {
            return;
        }
        SubWindowAnchor anchor = SubWindowAnchor.inward(
                (int)Math.floor(row.left), (int)Math.floor(row.top),
                (int)Math.ceil(row.right()), (int)Math.ceil(row.bottom()),
                WindowFrame.find(window.getId()), screen.width,
                screen.height);
        WindowMenus.FirstPlace place = WindowMenus.hangingFrom(anchor);
        if (setting instanceof Settings.Colour) {
            screen.settings().openPalette(setting, place);
        } else {
            screen.settings().openValue(setting, place);
            // The field takes the keys the page held for the press.
            screen.leavePage();
            screen.syncTypingFocus();
        }
    }

    /** The rows the search keeps; none without a window screen to read them from. */
    private List<MenuWindow.Entry> rows() {
        WindowScreen screen = WindowScreen.current();
        return screen == null ? null
                : screen.settings().rows(Settings.Place.CLIENT, this.query);
    }

    /* ---- Drawing ---- */

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        List<MenuWindow.Entry> rows = rows();
        if (rows == null) {
            return;
        }
        this.list.setRows(rows);
        LostTalesUiHitBox column = PageRows.column(box);
        this.list.draw(minecraft, column, clipX + (column.left - box.left),
                clipY + (column.top - box.top), pointerX, pointerY, alpha);
    }


    /* ---- The pointer ---- */

    @Override
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        return this.list.acts(PageRows.column(box), x, y);
    }

    @Override
    public String tipAt(LostTalesUiHitBox box, double x, double y) {
        return this.list.tipAt(PageRows.column(box), x, y);
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box,
                                double x, double y, int button) {
        return this.list.press(PageRows.column(box), x, y, button);
    }

    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        if (lines == 0 || !PageRows.column(box).contains(x, y)) {
            return false;
        }
        this.list.scroll(lines);
        return true;
    }

    /* ---- The window's strip ---- */

    /** The Console's grey. */
    @Override
    public int tone() {
        return LostTalesColors.rgb(LostTalesColors.CONSOLE_TONE);
    }

    /** The keys the Client Settings page answers to, for its help. */
    @Override
    public List<PageKeys.Area> keyAreas() {
        return PageKeys.pageArea("gui.losttales.page.client_settings",
                PageKeys.pageKey(PAGE_ID, "step", PageKeys.CLICK),
                PageKeys.pageKey(PAGE_ID, "back", PageKeys.RIGHT_CLICK),
                PageKeys.pageKey(PAGE_ID, "ten",
                        Keyboard.KEY_LSHIFT, PageKeys.PLUS, PageKeys.CLICK),
                PageKeys.pageKey(PAGE_ID, "type", PageKeys.CLICK),
                PageKeys.pageKey(PAGE_ID, "wheel", PageKeys.WHEEL));
    }

    @Override
    public String searchPrompt() {
        return StatCollector.translateToLocal(
                "gui.losttales.window.settings.search");
    }

    @Override
    public void search(String words) {
        String next = words == null ? "" : words.trim();
        if (!next.equals(this.query)) {
            this.query = next;
            this.list.toTop();
        }
    }

    @Override
    public int found() {
        List<MenuWindow.Entry> rows = rows();
        return this.query.length() == 0 || rows == null ? -1
                : PageRows.found(rows);
    }
}
