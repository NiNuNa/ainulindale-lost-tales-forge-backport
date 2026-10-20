package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.Settings;
import com.ninuna.losttales.client.window.SubWindow;
import com.ninuna.losttales.client.window.WindowMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

/**
 * The Characters page's capes menu, in a sub-window of its own in the
 * page's window: the capes of the character picked. Every change is sent at
 * once, as a setting is, and the menu stays for the next; while the
 * server answers, the rows wait. The menu works for the copy of the page
 * it was opened from.
 */
final class CharacterMenus {
    private static final String MINECRAFT_CAPE = "minecraft_cape";
    private static final String COSMETIC_CAPE = "cosmetic_cape";

    private CharacterMenus() {}

    /** Gives every screen the page's menu. */
    static void install() {
        WindowMenus.registerShared(CharacterSubWindows.CAPES, new CapesSource());
    }

    /** What the capes menu is about: a character, from one copy of the page. */
    static Object capesAbout(CharactersPage page, CharacterSummary character) {
        return new CapesAbout(page, character.getCharacterId());
    }

    /** The copy of the page a capes menu was opened from, and the character. */
    private static final class CapesAbout {
        final CharactersPage page;
        final UUID characterId;

        CapesAbout(CharactersPage page, UUID characterId) {
            this.page = page;
            this.characterId = characterId;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof CapesAbout)) {
                return false;
            }
            CapesAbout that = (CapesAbout)other;
            return this.page == that.page
                    && this.characterId.equals(that.characterId);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this.page) * 31
                    + this.characterId.hashCode();
        }
    }

    private static String busy(CharactersPage page) {
        return page != null && page.isPending()
                ? I18n.format("gui.losttales.character.working") : "";
    }

    /* ---- Capes ---- */

    /** One character's capes as the roster keeps them, and the copy of the page they were opened from. */
    private static final class Capes {
        final CharactersPage page;
        final UUID characterId;
        final String name;
        final boolean minecraftShown;
        final String cosmeticId;
        /** The capes the character may choose, none first (CHAR-39). */
        final List<String> choices;

        Capes(CharactersPage page, UUID characterId, String name,
              boolean minecraftShown, String cosmeticId, List<String> choices) {
            this.page = page;
            this.characterId = characterId;
            this.name = name;
            this.minecraftShown = minecraftShown;
            this.cosmeticId = cosmeticId;
            this.choices = choices;
        }
    }

    /** The capes a menu is about; null once that character has gone from the roster. */
    private static Capes capesOf(MenuWindow menu) {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot == null || !(menu.about() instanceof CapesAbout)) {
            return null;
        }
        CapesAbout about = (CapesAbout)menu.about();
        CharacterSummary character = snapshot.getCharacter(about.characterId);
        return character == null ? null : new Capes(about.page,
                character.getCharacterId(), character.getName(),
                character.isMinecraftCapeVisible(),
                character.getCosmeticCapeId(),
                CharacterCapeCatalog.choicesFor(character.getRaceId(),
                        character.getFactionId(), character.getCosmeticCapeId()));
    }

    /**
     * The capes of one character: whether its Minecraft cape shows, a
     * switch, and its LOTR cape, stepped forward on a click and back on a
     * right-click. Each change is saved at once.
     */
    private static final class CapesSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            return capesOf(menu) != null;
        }

        @Override
        public void rebuild(MenuWindow menu) {
            Capes capes = capesOf(menu);
            if (capes == null) {
                menu.setRows(new ArrayList<MenuWindow.Entry>());
                return;
            }
            menu.setTitle(I18n.format("gui.losttales.character.cape.title",
                    capes.name), null);
            String busy = busy(capes.page);
            List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>(3);
            rows.add(new MenuWindow.Entry(MINECRAFT_CAPE, I18n.format(
                    "gui.losttales.character.creator.cape.minecraft"))
                    .withValue(Settings.onOff(capes.minecraftShown))
                    .unavailable(busy));
            rows.add(new MenuWindow.Entry(COSMETIC_CAPE, I18n.format(
                    "gui.losttales.character.creator.cape.cosmetic"))
                    .withValue(ClientCharacterDisplayNames.cape(
                            capes.cosmeticId))
                    .unavailable(busy));
            if (!CharacterCapeCatalog.NONE_ID.equals(capes.cosmeticId)) {
                rows.add(MenuWindow.Entry.separator());
                rows.add(MenuWindow.Entry.passive(I18n.format(
                        "gui.losttales.character.cape.cosmetic_precedence")));
            }
            menu.setRows(rows);
        }

        @Override
        public boolean takesBack() {
            return true;
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            Capes capes = capesOf(menu);
            CharactersPage page = capes == null ? null : capes.page;
            CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
            if (capes == null || page == null || snapshot == null
                    || page.isPending()) {
                return true;
            }
            boolean shown = capes.minecraftShown;
            String cosmetic = capes.cosmeticId;
            if (MINECRAFT_CAPE.equals(entry.id)) {
                shown = !shown;
            } else if (COSMETIC_CAPE.equals(entry.id)) {
                List<String> ids = capes.choices;
                cosmetic = ids.get(Settings.nextIndex(ids.indexOf(cosmetic),
                        ids.size(), back));
            } else {
                return true;
            }
            page.track(ClientCharacterNetwork.updateCapeSettings(
                    snapshot.getRevision(), capes.characterId, shown,
                    cosmetic), "gui.losttales.character.cape.saving");
            return true;
        }
    }
}
