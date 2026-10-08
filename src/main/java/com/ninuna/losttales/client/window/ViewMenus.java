package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.input.LostTalesKeyPress;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * A view's menu, under a right-click on its button on the Views
 * sub-window: Rename View and Set Key for a custom view, then Reset View,
 * and Delete View for a view the player made. Rename View opens the
 * name's field beside the menu, Set Key a sub-window that takes the next
 * key pressed; Reset View and Delete View ask first.
 */
final class ViewMenus {
    static final SubWindowKind MENU = SubWindowKind.register("view",
            "gui.losttales.window.sub.view");
    static final SubWindowKind NAME = SubWindowKind.register("view_name",
            "gui.losttales.window.sub.view_name");
    static final SubWindowKind KEY = SubWindowKind.register("view_key",
            "gui.losttales.window.sub.view_key");
    private static final String ROW_RENAME = "view:rename";
    private static final String ROW_KEY = "view:key";
    private static final String ROW_RESET = "view:reset";
    private static final String ROW_DELETE = "view:delete";
    private static final String ROW_NAME_KEEP = "view:name_keep";
    private static final String ROW_NAME_OWN = "view:name_own";
    private static final String ROW_KEY_NONE = "view:key_none";

    private final WindowScreen screen;
    private final WindowMenus menus;
    /** Why the last key pressed for a view could not be its key, by the view; shown until another is pressed. */
    private final Map<View, String> refusals = new HashMap<View, String>();

    ViewMenus(WindowScreen screen, WindowMenus menus) {
        this.screen = screen;
        this.menus = menus;
        menus.register(MENU, new MenuSource());
        menus.register(NAME, new NameSource());
        menus.register(KEY, new KeySource());
    }

    /** The view's menu, hung from its button: a switch, as a right-click on the button is. */
    void show(View view, SubWindowAnchor anchor) {
        if (view != null) {
            this.menus.show(MENU, view, WindowMenus.hangingFrom(anchor), true);
        }
    }

    private static String word(String key) {
        return StatCollector.translateToLocal(key);
    }

    /** The name of the key a view has, or that it has none. */
    private static String keyWords(View view) {
        return view.key() > 0 ? Views.keyName(view.key())
                : word("gui.losttales.window.views.key.none");
    }

    private final class MenuSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            return menu.about() instanceof View
                    && Views.all().contains(menu.about());
        }

        @Override
        public void rebuild(MenuWindow menu) {
            View view = (View)menu.about();
            menu.setTitle(view.title(), null);
            List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
            if (view.isCustom()) {
                rows.add(new MenuWindow.Entry(ROW_RENAME,
                        word("gui.losttales.window.views.rename")));
                rows.add(new MenuWindow.Entry(ROW_KEY,
                        word("gui.losttales.window.views.set_key"))
                        .withValue(keyWords(view)));
                rows.add(MenuWindow.Entry.separator());
            }
            rows.add(new MenuWindow.Entry(ROW_RESET,
                    word("gui.losttales.window.views.reset"))
                    .withSprite(LostTalesUiSheet.RESET,
                            LostTalesUiSheet.RESET_DISCARD, false));
            if (view.isDeletable()) {
                rows.add(new MenuWindow.Entry(ROW_DELETE,
                        word("gui.losttales.window.views.delete"))
                        .withSprite(LostTalesUiSheet.TRASH,
                                LostTalesUiSheet.TRASH_LIT, false));
            }
            menu.setRows(rows);
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            View view = (View)menu.about();
            if (ROW_RENAME.equals(entry.id)) {
                ViewMenus.this.menus.show(NAME, view,
                        WindowMenus.besideWindow(window), false);
                return true;
            }
            if (ROW_KEY.equals(entry.id)) {
                ViewMenus.this.refusals.remove(view);
                ViewMenus.this.menus.show(KEY, view,
                        WindowMenus.besideWindow(window), false);
                return true;
            }
            if (ROW_RESET.equals(entry.id)) {
                ViewMenus.this.screen.askResetView(view);
            } else if (ROW_DELETE.equals(entry.id)) {
                ViewMenus.this.screen.askDeleteView(view);
            }
            return false;
        }
    }

    /**
     * The name's field, holding the name the view wears: Enter or Keep Name
     * keeps what it holds, Default Name gives the view back its own.
     */
    private final class NameSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            return menu.about() instanceof View
                    && Views.all().contains(menu.about());
        }

        @Override
        public void prepare(MenuWindow menu) {
            View view = (View)menu.about();
            menu.openField(word("gui.losttales.window.views.name_prompt"),
                    null, null, View.MAX_NAME_LENGTH, true);
            menu.setFilter(view.title());
        }

        @Override
        public void rebuild(MenuWindow menu) {
            View view = (View)menu.about();
            menu.setTitle(null, null);
            List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>(2);
            rows.add(new MenuWindow.Entry(ROW_NAME_KEEP,
                    word("gui.losttales.window.views.name_keep")));
            if (view.name().length() > 0) {
                rows.add(new MenuWindow.Entry(ROW_NAME_OWN,
                        word("gui.losttales.window.views.name_own")));
            }
            menu.setRows(rows);
        }

        @Override
        public MenuWindow.Entry firstFound(MenuWindow menu) {
            return menu.entries().isEmpty() ? null : menu.entries().get(0);
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            View view = (View)menu.about();
            Views.rename(view, ROW_NAME_KEEP.equals(entry.id)
                    ? menu.filter() : "");
            ViewMenus.this.screen.viewsChanged();
            ViewMenus.this.menus.rebuildIfOpen(MENU);
            return false;
        }
    }

    /**
     * Waits for the key the view is to open with: the next key pressed
     * becomes it, where no other binding or view holds it; Backspace or
     * No Key takes the key away, Escape leaves it as it is.
     */
    private final class KeySource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            return menu.about() instanceof View
                    && Views.all().contains(menu.about());
        }

        @Override
        public void rebuild(MenuWindow menu) {
            View view = (View)menu.about();
            menu.setTitle(null, null);
            List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>(4);
            rows.add(MenuWindow.Entry.note(StatCollector.translateToLocalFormatted(
                    "gui.losttales.window.views.key.press", view.title())));
            String refused = ViewMenus.this.refusals.get(view);
            if (refused != null) {
                rows.add(MenuWindow.Entry.note(refused));
            }
            rows.add(MenuWindow.Entry.passive(
                    word("gui.losttales.window.views.key")).withValue(
                    keyWords(view)));
            MenuWindow.Entry none = new MenuWindow.Entry(ROW_KEY_NONE,
                    word("gui.losttales.window.views.key.clear"))
                    .withKeys(PageKeys.keysOf(Keyboard.KEY_BACK));
            rows.add(view.key() > 0 ? none : none.unavailable(
                    word("gui.losttales.window.views.key.none_set")));
            menu.setRows(rows);
        }

        @Override
        public boolean capturesKey(MenuWindow menu, SubWindow window,
                                   LostTalesKeyPress press) {
            View view = (View)menu.about();
            if (press.is(Keyboard.KEY_ESCAPE)) {
                return false;
            }
            int key = press.key == Keyboard.KEY_BACK
                    || press.key == Keyboard.KEY_DELETE ? 0 : press.key;
            String refusal = Views.keyRefusal(view, key);
            if (refusal != null) {
                ViewMenus.this.refusals.put(view, refusal);
                return true;
            }
            ViewMenus.this.refusals.remove(view);
            Views.setKey(view, key);
            ViewMenus.this.screen.viewsChanged();
            ViewMenus.this.menus.rebuildIfOpen(MENU);
            ViewMenus.this.screen.subWindows().close(window);
            return true;
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            if (ROW_KEY_NONE.equals(entry.id)) {
                Views.setKey((View)menu.about(), 0);
                ViewMenus.this.screen.viewsChanged();
                ViewMenus.this.menus.rebuildIfOpen(MENU);
            }
            return false;
        }
    }
}
