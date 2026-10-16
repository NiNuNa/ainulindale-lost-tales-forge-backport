package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.input.LostTalesInputBinding;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
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
 * shows them: each category's view, then the custom views the player
 * made. A custom view is numbered 1 to {@link #MAX_CUSTOM}; its key is
 * the binding of its number in Controls (<em>Custom View 1</em> and on),
 * set there or in the view's menu. The custom views and their names are
 * kept in the layout file, a {@code view} line each.
 */
public final class Views {
    /** The most custom views there may be: one per key in Controls. */
    public static final int MAX_CUSTOM = 8;
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
    }

    private Views() {}

    /** Back to a new player's views: the categories' alone. */
    public static synchronized void reset() {
        CUSTOM.clear();
    }

    /** Every view, the categories' first, then the custom views. */
    public static synchronized List<View> all() {
        List<View> all = new ArrayList<View>(OWN.values());
        all.addAll(CUSTOM);
        return all;
    }

    /** The custom views, in the order they were made. */
    public static synchronized List<View> custom() {
        return Collections.unmodifiableList(new ArrayList<View>(CUSTOM));
    }

    /**
     * A category's view: its own, or the one it shares (the whispers
     * stand with the channels). A New Page with nowhere else to stand
     * stands in the Chat view.
     */
    public static synchronized View of(PageCategory category) {
        PageCategory home = category == null ? null : category.home();
        View own = home == null ? null : OWN.get(home);
        return own != null ? own : OWN.get(PageCategory.CHANNELS);
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

    /** The custom view of that number; null while there is none. */
    public static synchronized View numbered(int number) {
        for (View view : CUSTOM) {
            if (view.number() == number) {
                return view;
            }
        }
        return null;
    }

    /**
     * The number of the custom view whose key in Controls is that keyboard
     * key, made or not; 0 for none.
     */
    public static int numberForKey(int keyCode) {
        if (keyCode <= 0) {
            return 0;
        }
        for (int number = 1; number <= MAX_CUSTOM; number++) {
            KeyBinding binding = LostTalesKeyBindings.getViewKeyBinding(number);
            if (binding != null && binding.getKeyCode() == keyCode) {
                return number;
            }
        }
        return 0;
    }

    /** Whether another custom view can be made. */
    public static synchronized boolean canMake() {
        return CUSTOM.size() < MAX_CUSTOM;
    }

    /** A new custom view, last on the bar, with the lowest number free; null at the most there may be. */
    static synchronized View make() {
        for (int number = 1; number <= MAX_CUSTOM; number++) {
            if (numbered(number) == null) {
                return make(number);
            }
        }
        return null;
    }

    /**
     * The custom view of {@code number}, made last on the bar, as its key
     * makes it; null for a number out of range or one standing already.
     */
    public static synchronized View make(int number) {
        if (number < 1 || number > MAX_CUSTOM || numbered(number) != null) {
            return null;
        }
        View made = new View(View.CUSTOM_PREFIX + number, null);
        CUSTOM.add(made);
        return made;
    }

    /**
     * Takes a custom view off the bar, and its key away with it, so it
     * never comes back by a key pressed later; its windows are the
     * layout's to let go.
     */
    static synchronized boolean delete(View view) {
        if (view == null || !view.isCustom() || !CUSTOM.remove(view)) {
            return false;
        }
        bind(view.number(), 0);
        return true;
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
     * alone), and one another binding holds, another view's among them,
     * are refused. 0 takes the key away and is always allowed.
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
        int number = numberForKey(keyCode);
        if (number > 0 && number != view.number()) {
            View other = numbered(number);
            return StatCollector.translateToLocalFormatted(
                    "gui.losttales.window.views.key.taken", name,
                    other != null ? other.title() : StatCollector
                            .translateToLocal(LostTalesKeyBindings
                                    .getViewKeyBinding(number)
                                    .getKeyDescription()));
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
        if (view == null || !view.isCustom()
                || keyRefusal(view, keyCode) != null) {
            return false;
        }
        bind(view.number(), keyCode);
        return true;
    }

    /** Sets the binding of custom view {@code number} in Controls, and writes the game's options. */
    private static void bind(int number, int keyCode) {
        KeyBinding binding = LostTalesKeyBindings.getViewKeyBinding(number);
        if (binding == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.gameSettings != null) {
            minecraft.gameSettings.setOptionKeyBinding(binding, keyCode);
        } else {
            binding.setKeyCode(keyCode);
        }
        KeyBinding.resetKeyBindingArrayAndHash();
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

    /**
     * The name of the game's binding on that key, in the player's
     * language, the custom views' own left aside; null for none.
     */
    private static String bindingHolding(int keyCode) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameSettings == null) {
            return null;
        }
        for (KeyBinding binding : minecraft.gameSettings.keyBindings) {
            if (binding != null && binding.getKeyCode() == keyCode
                    && !LostTalesKeyBindings.isViewKeyBinding(binding)) {
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
     * A {@code view} line: a custom view's id and the name the player gave
     * it. A line that cannot be read is left out.
     */
    static View parse(String[] parts) {
        if (parts.length < 2 || !LINE.equals(parts[0])
                || numberOf(parts[1]) == 0) {
            return null;
        }
        View view = new View(parts[1], null);
        for (int index = 2; index < parts.length; index++) {
            if (parts[index].startsWith("name=")) {
                view.setName(cleanName(decode(parts[index].substring(5))));
            }
        }
        return view;
    }

    /** The number a custom view's id names, 1 to {@link #MAX_CUSTOM}; 0 for no custom view's id. */
    private static int numberOf(String id) {
        if (!id.startsWith(View.CUSTOM_PREFIX)) {
            return 0;
        }
        String digits = id.substring(View.CUSTOM_PREFIX.length());
        if (digits.length() == 0 || digits.length() > 2
                || digits.charAt(0) == '0') {
            return 0;
        }
        for (int index = 0; index < digits.length(); index++) {
            if (digits.charAt(index) < '0' || digits.charAt(index) > '9') {
                return 0;
            }
        }
        int number = Integer.parseInt(digits);
        return number <= MAX_CUSTOM ? number : 0;
    }

    /** The custom views the file named, in its order; a view named twice is left out. */
    static synchronized void load(List<View> read) {
        CUSTOM.clear();
        if (read == null) {
            return;
        }
        for (View view : read) {
            if (numbered(view.number()) == null) {
                CUSTOM.add(view);
            }
        }
    }

    /** Every custom view's line, in the bar's order. */
    static synchronized void describe(List<String> lines) {
        for (View view : CUSTOM) {
            StringBuilder line = new StringBuilder(LINE).append(' ')
                    .append(view.id());
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
