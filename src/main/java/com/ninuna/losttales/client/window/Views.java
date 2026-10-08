package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.input.LostTalesInputBinding;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * Every view the screen can show, in the order the Views sub-window
 * shows them: each category's view, then the custom views, the Lost Tales
 * Menu's first. The player makes, names, keys and deletes custom views;
 * those and their names and keys are kept in the layout file, a
 * {@code view} line each.
 */
public final class Views {
    /** The most custom views there may be, the Lost Tales Menu's among them. */
    public static final int MAX_CUSTOM = 8;
    /** The Lost Tales Menu's key at first. */
    static final int MENU_KEY = Keyboard.KEY_CAPITAL;
    /** What a {@code view} line starts with. */
    static final String LINE = "view";

    private static final Map<PageCategory, View> OWN =
            new EnumMap<PageCategory, View>(PageCategory.class);
    private static final List<View> CUSTOM = new ArrayList<View>();

    static {
        for (PageCategory category : PageCategory.values()) {
            if (category.isView()) {
                OWN.put(category, new View(category.id(), category));
            }
        }
        reset();
    }

    private Views() {}

    /** Back to a new player's views: the categories' and the Lost Tales Menu's, Caps Lock its key. */
    public static synchronized void reset() {
        CUSTOM.clear();
        CUSTOM.add(menuAsShipped());
    }

    private static View menuAsShipped() {
        View menu = new View(View.MENU_ID, null);
        menu.setKey(MENU_KEY);
        return menu;
    }

    /** Every view, the categories' first, then the custom views. */
    public static synchronized List<View> all() {
        List<View> all = new ArrayList<View>(OWN.values());
        all.addAll(CUSTOM);
        return all;
    }

    /** The custom views, the Lost Tales Menu's first. */
    public static synchronized List<View> custom() {
        return Collections.unmodifiableList(new ArrayList<View>(CUSTOM));
    }

    /** A category's view: its own, or the one it shares (the whispers stand with the channels). */
    public static synchronized View of(PageCategory category) {
        PageCategory home = category == null ? null : category.home();
        View own = home == null ? null : OWN.get(home);
        return own != null ? own : menu();
    }

    /** The Lost Tales Menu's view. */
    public static synchronized View menu() {
        return CUSTOM.get(0);
    }

    /** The view of that id; null for none. */
    public static synchronized View byId(String id) {
        for (View view : all()) {
            if (view.id().equals(id)) {
                return view;
            }
        }
        return null;
    }

    /** The custom view a keyboard key opens; null for none. */
    public static synchronized View byKey(int keyCode) {
        if (keyCode <= 0) {
            return null;
        }
        for (View view : CUSTOM) {
            if (view.key() == keyCode) {
                return view;
            }
        }
        return null;
    }

    /** Whether another custom view can be made. */
    public static synchronized boolean canMake() {
        return CUSTOM.size() < MAX_CUSTOM;
    }

    /** A new custom view, last on the bar, with the lowest number free; null at the most there may be. */
    static synchronized View make() {
        if (!canMake()) {
            return null;
        }
        for (int number = 1; ; number++) {
            String id = View.MADE_PREFIX + number;
            if (byId(id) == null) {
                View made = new View(id, null);
                CUSTOM.add(made);
                return made;
            }
        }
    }

    /** Takes a view the player made off the bar; its windows are the layout's to let go. */
    static synchronized boolean delete(View view) {
        return view != null && view.isDeletable() && CUSTOM.remove(view);
    }

    /** Gives a custom view a name; an empty one gives it back its own. */
    static synchronized void rename(View view, String name) {
        if (view != null && view.isCustom()) {
            view.setName(cleanName(name));
        }
    }

    /**
     * A name as a view may wear it: no formatting codes or control
     * characters, trimmed, and {@link View#MAX_NAME_LENGTH} at most.
     */
    static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder clean = new StringBuilder(name.length());
        for (int index = 0; index < name.length(); index++) {
            char letter = name.charAt(index);
            if (letter == '§') {
                index++;
            } else if (!Character.isISOControl(letter)) {
                clean.append(letter);
            }
        }
        String trimmed = clean.toString().trim();
        return trimmed.length() > View.MAX_NAME_LENGTH
                ? trimmed.substring(0, View.MAX_NAME_LENGTH).trim() : trimmed;
    }

    /**
     * Why a keyboard key cannot open {@code view}; null where it can. A
     * key the screen or the game keeps (Escape, Enter, F1, F3, a modifier
     * alone), one another binding holds and one another view holds are
     * refused. 0 takes the key away and is always allowed.
     */
    static synchronized String keyRefusal(View view, int keyCode) {
        if (keyCode == 0) {
            return null;
        }
        String name = keyName(keyCode);
        if (isReserved(keyCode)) {
            return StatCollector.translateToLocalFormatted(
                    "gui.losttales.window.views.key.reserved", name);
        }
        View other = byKey(keyCode);
        if (other != null && other != view) {
            return StatCollector.translateToLocalFormatted(
                    "gui.losttales.window.views.key.taken", name, other.title());
        }
        String binding = bindingHolding(keyCode);
        if (binding != null) {
            return StatCollector.translateToLocalFormatted(
                    "gui.losttales.window.views.key.taken", name, binding);
        }
        return null;
    }

    /** Gives a custom view its key, where {@link #keyRefusal} allows it. */
    static synchronized boolean setKey(View view, int keyCode) {
        if (view == null || !view.isCustom() || keyRefusal(view, keyCode) != null) {
            return false;
        }
        view.setKey(keyCode);
        return true;
    }

    private static boolean isReserved(int keyCode) {
        switch (keyCode) {
            case Keyboard.KEY_ESCAPE:
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
            case Keyboard.KEY_BACK:
            case Keyboard.KEY_DELETE:
            case Keyboard.KEY_F1:
            case Keyboard.KEY_F3:
            case Keyboard.KEY_LSHIFT:
            case Keyboard.KEY_RSHIFT:
            case Keyboard.KEY_LCONTROL:
            case Keyboard.KEY_RCONTROL:
            case Keyboard.KEY_LMENU:
            case Keyboard.KEY_RMENU:
            case Keyboard.KEY_LMETA:
            case Keyboard.KEY_RMETA:
                return true;
            default:
                return false;
        }
    }

    /** The name of the game's binding on that key, in the player's language; null for none. */
    private static String bindingHolding(int keyCode) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameSettings == null) {
            return null;
        }
        for (KeyBinding binding : minecraft.gameSettings.keyBindings) {
            if (binding != null && binding.getKeyCode() == keyCode) {
                return StatCollector.translateToLocal(binding.getKeyDescription());
            }
        }
        return null;
    }

    /** A key's name as the tips write it. */
    static String keyName(int keyCode) {
        return LostTalesInputBinding.getFallbackLabel(
                LostTalesInputBinding.Type.KEYBOARD, keyCode);
    }

    /* ---- The layout file ---- */

    /**
     * A {@code view} line: a custom view's id, its key and the name the
     * player gave it. A line that cannot be read is left out.
     */
    static View parse(String[] parts) {
        if (parts.length < 2 || !LINE.equals(parts[0])) {
            return null;
        }
        String id = parts[1];
        if (!View.MENU_ID.equals(id) && !isMadeId(id)) {
            return null;
        }
        View view = new View(id, null);
        for (int index = 2; index < parts.length; index++) {
            String part = parts[index];
            if (part.startsWith("key=")) {
                try {
                    int key = Integer.parseInt(part.substring(4));
                    view.setKey(key > 0 && key < 256 && !isReserved(key) ? key : 0);
                } catch (NumberFormatException unreadable) {
                    view.setKey(0);
                }
            } else if (part.startsWith("name=")) {
                view.setName(cleanName(decode(part.substring(5))));
            }
        }
        return view;
    }

    private static boolean isMadeId(String id) {
        if (!id.startsWith(View.MADE_PREFIX) || id.length() > 4) {
            return false;
        }
        for (int index = View.MADE_PREFIX.length(); index < id.length(); index++) {
            if (!Character.isDigit(id.charAt(index))) {
                return false;
            }
        }
        return id.length() > View.MADE_PREFIX.length()
                && id.charAt(View.MADE_PREFIX.length()) != '0';
    }

    /**
     * The custom views the file named, in its order, the Lost Tales
     * Menu's first whatever the file says; a view named twice, or past the
     * most there may be, is left out.
     */
    static synchronized void load(List<View> read) {
        CUSTOM.clear();
        View menu = null;
        List<View> made = new ArrayList<View>();
        if (read != null) {
            for (View view : read) {
                if (View.MENU_ID.equals(view.id())) {
                    if (menu == null) {
                        menu = view;
                    }
                } else if (!containsId(made, view.id())) {
                    made.add(view);
                }
            }
        }
        CUSTOM.add(menu != null ? menu : menuAsShipped());
        for (View view : made) {
            if (CUSTOM.size() >= MAX_CUSTOM) {
                break;
            }
            CUSTOM.add(view);
        }
        // Two views never share a key: the first named keeps it.
        for (int index = 1; index < CUSTOM.size(); index++) {
            int key = CUSTOM.get(index).key();
            for (int before = 0; before < index; before++) {
                if (key != 0 && CUSTOM.get(before).key() == key) {
                    CUSTOM.get(index).setKey(0);
                }
            }
        }
    }

    private static boolean containsId(List<View> views, String id) {
        for (View view : views) {
            if (view.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Every custom view's line, in the bar's order. */
    static synchronized void describe(List<String> lines) {
        for (View view : CUSTOM) {
            StringBuilder line = new StringBuilder(LINE).append(' ')
                    .append(view.id()).append(" key=").append(view.key());
            if (view.name().length() > 0) {
                line.append(" name=").append(encode(view.name()));
            }
            lines.add(line.toString());
        }
    }

    private static String encode(String name) {
        try {
            return URLEncoder.encode(name, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            return "";
        }
    }

    private static String decode(String name) {
        try {
            return URLDecoder.decode(name, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            return "";
        } catch (IllegalArgumentException unreadable) {
            return "";
        }
    }
}
