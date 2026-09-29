package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.cape.CharacterCapeDefinition;
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
 * The Characters tab's capes menu, in a sub-window of its own in the
 * tab's window: the capes of the identity picked. Every change is sent at
 * once, as a setting is, and the menu stays for the next; while the
 * server answers, the rows wait.
 */
final class CharacterMenus {
    /** What the capes menu is about while the account plays as itself, with no record of its own. */
    private static final String ACCOUNT = "account";
    private static final String MINECRAFT_CAPE = "minecraft_cape";
    private static final String COSMETIC_CAPE = "cosmetic_cape";

    private CharacterMenus() {}

    /** Gives every screen the tab's menu. */
    static void install() {
        WindowMenus.registerShared(CharacterSubWindows.CAPES, new CapesSource());
    }

    /** What the capes menu is about for a character: its id, or the account for none. */
    static String capesAbout(CharacterSummary character) {
        return character == null ? ACCOUNT
                : character.getCharacterId().toString();
    }

    private static String busy(CharactersPage page) {
        return page != null && page.isPending()
                ? I18n.format("gui.losttales.character.working") : "";
    }

    /* ---- Capes ---- */

    /** One identity's capes as the roster keeps them. */
    private static final class Capes {
        final UUID characterId;
        final String name;
        final boolean minecraftShown;
        final int cosmeticId;

        Capes(UUID characterId, String name, boolean minecraftShown,
              int cosmeticId) {
            this.characterId = characterId;
            this.name = name;
            this.minecraftShown = minecraftShown;
            this.cosmeticId = cosmeticId;
        }
    }

    /** The capes a menu is about; null once that identity has gone from the roster. */
    private static Capes capesOf(MenuWindow menu) {
        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot == null || !(menu.about() instanceof String)) {
            return null;
        }
        String about = (String)menu.about();
        if (ACCOUNT.equals(about)) {
            Minecraft minecraft = Minecraft.getMinecraft();
            return new Capes(null, minecraft.thePlayer == null ? ""
                    : minecraft.thePlayer.getCommandSenderName(),
                    snapshot.isAccountMinecraftCapeVisible(),
                    snapshot.getAccountCosmeticCapeId());
        }
        CharacterSummary character;
        try {
            character = snapshot.getCharacter(UUID.fromString(about));
        } catch (IllegalArgumentException unreadable) {
            return null;
        }
        return character == null ? null : new Capes(
                character.getCharacterId(), character.getName(),
                character.isMinecraftCapeVisible(),
                character.getCosmeticCapeId());
    }

    /** Every cosmetic cape to choose, none first. */
    private static List<Integer> cosmeticIds() {
        List<Integer> ids = new ArrayList<Integer>();
        ids.add(Integer.valueOf(CharacterCapeCatalog.NONE_ID));
        for (CharacterCapeDefinition definition
                : CharacterCapeCatalog.getDefinitions()) {
            ids.add(Integer.valueOf(definition.getNetworkId()));
        }
        return ids;
    }

    /**
     * The capes of one identity: whether its Minecraft cape shows, a
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
            String busy = busy(CharactersPage.current());
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
            if (capes.cosmeticId != CharacterCapeCatalog.NONE_ID) {
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
            CharactersPage page = CharactersPage.current();
            CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
            if (capes == null || page == null || snapshot == null
                    || page.isPending()) {
                return true;
            }
            boolean shown = capes.minecraftShown;
            int cosmetic = capes.cosmeticId;
            if (MINECRAFT_CAPE.equals(entry.id)) {
                shown = !shown;
            } else if (COSMETIC_CAPE.equals(entry.id)) {
                List<Integer> ids = cosmeticIds();
                cosmetic = ids.get(Settings.nextIndex(ids.indexOf(
                        Integer.valueOf(cosmetic)), ids.size(), back))
                        .intValue();
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
