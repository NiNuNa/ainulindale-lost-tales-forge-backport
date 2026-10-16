package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.StatCollector;

/**
 * One view of the window screen: the windows the screen shows, one view
 * at a time. A category's view (Chat, Consoles, Map, Quest Journal,
 * Fellowships, Profile, Settings) opens with the window
 * {@link ViewDefaults} lays out for it and has the category's key. A
 * custom view is the player's: numbered 1 to {@link Views#MAX_CUSTOM},
 * opening with a New Page, with a name of its own and the key of its
 * number's binding in Controls. Any page may stand in any view.
 */
public final class View {
    /** What a custom view's id starts with, before its number. */
    static final String CUSTOM_PREFIX = "v";
    /** The longest name a player may give a view. */
    public static final int MAX_NAME_LENGTH = 24;

    private final String id;
    private final PageCategory category;
    /** The name the player gave it; empty for its own. */
    private String name = "";

    View(String id, PageCategory category) {
        this.id = id;
        this.category = category;
    }

    /** Its code name in the layout file: the category's, or {@code v1} and on. */
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

    /** A custom view's number, which names its key in Controls; 0 for a category's view. */
    public int number() {
        return isCustom() ? Integer.parseInt(this.id.substring(
                CUSTOM_PREFIX.length())) : 0;
    }

    /** Its name: the category's, the one the player gave it, else its own. */
    public String title() {
        if (this.category != null) {
            return this.category.title();
        }
        return this.name.length() > 0 ? this.name : ownTitle();
    }

    /** The name a custom view wears while the player gives it none: Custom View and its number. */
    public String ownTitle() {
        return StatCollector.translateToLocalFormatted(
                "gui.losttales.window.views.made", Integer.valueOf(number()));
    }

    /** The name the player gave it; empty while it keeps its own. */
    String name() {
        return this.name;
    }

    void setName(String name) {
        this.name = name == null ? "" : name;
    }

    /**
     * The key that opens it, its binding's: a custom view's by its number,
     * a category's view's by its category (none for the settings, whose
     * key is Ctrl+,); 0 for none.
     */
    public int key() {
        KeyBinding binding = isCustom()
                ? LostTalesKeyBindings.getViewKeyBinding(number())
                : bindingOf(this.category);
        return binding == null ? 0 : binding.getKeyCode();
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
