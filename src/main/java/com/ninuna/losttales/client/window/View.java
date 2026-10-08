package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.StatCollector;

/**
 * One view of the window screen: the windows the screen shows, one view
 * at a time. A category's view (Chat, Consoles, Map, Quest Journal,
 * Fellowships, Profile, Settings) opens with the windows
 * {@link ViewDefaults} lays out for it and has the category's key. A
 * custom view is the player's: the Lost Tales Menu's first, then any
 * they make; each opens with a New Page, and has a name and a key of its
 * own. Any page may stand in any view. Only a custom view the player
 * made can be deleted.
 */
public final class View {
    /** The Lost Tales Menu's id: the first custom view, which cannot be deleted. */
    public static final String MENU_ID = "menu";
    /** What a made view's id starts with, before its number. */
    static final String MADE_PREFIX = "v";
    /** The longest name a player may give a view. */
    public static final int MAX_NAME_LENGTH = 24;

    private final String id;
    private final PageCategory category;
    /** The name the player gave it; empty for its own. */
    private String name = "";
    /** The key that opens it, a keyboard code; 0 for none. A category's view uses its binding's. */
    private int key;

    View(String id, PageCategory category) {
        this.id = id;
        this.category = category;
    }

    /** Its code name in the layout file: the category's, {@code menu}, or {@code v1} and on. */
    public String id() {
        return this.id;
    }

    /** The category whose view it is; null for a custom view. */
    public PageCategory category() {
        return this.category;
    }

    public boolean isCustom() {
        return this.category == null;
    }

    /** Whether the player may delete it: a view they made, never the Lost Tales Menu's. */
    public boolean isDeletable() {
        return isCustom() && !MENU_ID.equals(this.id);
    }

    /** Its name: the category's, the one the player gave it, else its own. */
    public String title() {
        if (this.category != null) {
            return this.category.title();
        }
        if (this.name.length() > 0) {
            return this.name;
        }
        if (MENU_ID.equals(this.id)) {
            return StatCollector.translateToLocal(
                    "gui.losttales.window.views.menu");
        }
        return StatCollector.translateToLocalFormatted(
                "gui.losttales.window.views.made", this.id.substring(
                        MADE_PREFIX.length()));
    }

    /** The name the player gave it; empty while it keeps its own. */
    String name() {
        return this.name;
    }

    void setName(String name) {
        this.name = name == null ? "" : name;
    }

    /**
     * The key that opens it: a custom view's own, a category's view's
     * binding's (none for the settings, whose key is Ctrl+,); 0 for none.
     */
    public int key() {
        if (this.category == null) {
            return this.key;
        }
        KeyBinding binding = bindingOf(this.category);
        return binding == null ? 0 : binding.getKeyCode();
    }

    void setKey(int key) {
        this.key = Math.max(0, key);
    }

    /** The binding that opens a category's view; null for the settings. */
    private static KeyBinding bindingOf(PageCategory category) {
        Minecraft minecraft = Minecraft.getMinecraft();
        switch (category) {
            case CHANNELS:
                return minecraft == null ? null : minecraft.gameSettings.keyBindChat;
            case CONSOLES:
                return minecraft == null ? null : minecraft.gameSettings.keyBindCommand;
            case MAP:
                return LostTalesKeyBindings.getMapKeyBinding();
            case QUEST_JOURNAL:
                return LostTalesKeyBindings.getQuestJournalKeyBinding();
            case FELLOWSHIPS:
                return LostTalesKeyBindings.getFellowshipKeyBinding();
            case PROFILE:
                return LostTalesKeyBindings.getCharactersKeyBinding();
            default:
                return null;
        }
    }

    @Override
    public String toString() {
        return this.id;
    }
}
