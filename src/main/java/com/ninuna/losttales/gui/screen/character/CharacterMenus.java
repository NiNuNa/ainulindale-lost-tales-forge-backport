package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.cape.CharacterCapeDefinition;
import com.ninuna.losttales.character.lore.sync.LoreCharacterSnapshot;
import com.ninuna.losttales.character.lore.sync.LoreCharacterSummary;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.character.ClientLoreCharacterCache;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.Settings;
import com.ninuna.losttales.client.window.SubWindow;
import com.ninuna.losttales.client.window.WindowMenus;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

/**
 * The Characters tab's menus, each in a sub-window of its own in the
 * tab's window: the capes of the identity picked, and the world's lore
 * characters. Every change is sent at once, as a setting is, and the
 * menu stays for the next; while the server answers, the rows wait.
 */
final class CharacterMenus {
    /** What the capes menu is about while the account plays as itself, with no record of its own. */
    private static final String ACCOUNT = "account";
    private static final String MINECRAFT_CAPE = "minecraft_cape";
    private static final String COSMETIC_CAPE = "cosmetic_cape";
    /** Marks a lore character's row; the rest of the id is its definition's. */
    private static final String LORE_PREFIX = "lore:";

    private CharacterMenus() {}

    /** Gives every screen the tab's menus. */
    static void install() {
        WindowMenus.registerShared(CharacterSubWindows.CAPES, new CapesSource());
        WindowMenus.registerShared(CharacterSubWindows.LORE, new LoreSource());
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

    /* ---- Lore characters ---- */

    private static LoreCharacterSummary loreOf(String entryId) {
        LoreCharacterSnapshot lore = ClientLoreCharacterCache.getSnapshot();
        if (lore == null || !entryId.startsWith(LORE_PREFIX)) {
            return null;
        }
        String id = entryId.substring(LORE_PREFIX.length());
        for (LoreCharacterSummary character : lore.getCharacters()) {
            if (character.getId().equals(id)) {
                return character;
            }
        }
        return null;
    }

    /**
     * The world's lore characters: yours, each a press from being released;
     * those free to claim, into the empty slot picked in the roster or
     * else the first empty one; and those another player has.
     */
    private static final class LoreSource extends WindowMenus.Source {
        @Override
        public void rebuild(MenuWindow menu) {
            menu.setTitle(null, LostTalesUiSheet.MEMBERS);
            LoreCharacterSnapshot lore = ClientLoreCharacterCache.getSnapshot();
            CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
            List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>();
            if (lore == null || roster == null) {
                entries.add(MenuWindow.Entry.passive(I18n.format(
                        "gui.losttales.lore.loading")));
                menu.setRows(entries);
                return;
            }
            CharactersPage page = CharactersPage.current();
            String busy = busy(page);
            String frozen = lore.canMutate() ? "" : I18n.format(
                    "gui.losttales.character.error.lore_character_ownership_storage_read_only");
            int slot = page == null ? -1 : page.claimSlot();
            List<MenuWindow.Entry> yours = new ArrayList<MenuWindow.Entry>();
            List<MenuWindow.Entry> free = new ArrayList<MenuWindow.Entry>();
            List<MenuWindow.Entry> taken = new ArrayList<MenuWindow.Entry>();
            for (LoreCharacterSummary character : lore.getCharacters()) {
                MenuWindow.Entry row = new MenuWindow.Entry(
                        LORE_PREFIX + character.getId(), character.getName())
                        .withValue(ClientCharacterDisplayNames.race(
                                character.getRaceId()));
                if (character.isOwnedByViewer()) {
                    yours.add(row.unavailable(firstOf(
                            character.isTransferInProgress() ? I18n.format(
                                    "gui.losttales.lore.transfer_pending") : "",
                            character.getOwnedCharacterId() != null
                                    && character.getOwnedCharacterId().equals(
                                            roster.getActiveCharacterId())
                                    ? I18n.format("gui.losttales.character.error.lore_character_active")
                                    : "", frozen, busy)));
                } else if (character.isAvailable()) {
                    free.add(row.unavailable(firstOf(
                            !character.isConfigured() ? I18n.format(
                                    "gui.losttales.lore.not_configured") : "",
                            character.isTransferInProgress() ? I18n.format(
                                    "gui.losttales.lore.transfer_pending") : "",
                            slot < 0 ? I18n.format("gui.losttales.lore.no_slot")
                                    : "", frozen, busy)));
                } else {
                    taken.add(row.unavailable(character.getOwnerName()
                            .length() == 0 ? I18n.format(
                                    "gui.losttales.lore.claimed")
                            : I18n.format("gui.losttales.lore.owned_by",
                                    character.getOwnerName())));
                }
            }
            if (slot >= 0 && !free.isEmpty()) {
                entries.add(MenuWindow.Entry.passive(I18n.format(
                        "gui.losttales.lore.target_slot", I18n.format(
                                "gui.losttales.character.slot",
                                Integer.valueOf(slot + 1)))));
            }
            WindowMenus.addSection(entries, I18n.format(
                    "gui.losttales.lore.section.yours"), yours);
            WindowMenus.addSection(entries, I18n.format(
                    "gui.losttales.lore.section.free"), free);
            WindowMenus.addSection(entries, I18n.format(
                    "gui.losttales.lore.section.taken"), taken);
            if (entries.isEmpty()) {
                entries.add(MenuWindow.Entry.passive(I18n.format(
                        "gui.losttales.lore.none")));
            }
            menu.setRows(entries);
        }

        /** Claiming is sent at once and the menu stays; releasing asks first. */
        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            final LoreCharacterSummary character = loreOf(entry.id);
            final CharactersPage page = CharactersPage.current();
            final CharacterRosterSnapshot roster =
                    ClientCharacterRosterCache.getSnapshot();
            if (character == null || page == null || roster == null
                    || page.isPending()) {
                return true;
            }
            if (!character.isOwnedByViewer()) {
                int slot = page.claimSlot();
                if (slot >= 0) {
                    page.track(ClientCharacterNetwork.claimLoreCharacter(
                            roster.getRevision(),
                            character.getOwnershipRevision(), slot,
                            character.getId()),
                            "gui.losttales.lore.processing");
                }
                return true;
            }
            WindowScreen screen = WindowScreen.current();
            if (screen != null) {
                screen.ask(WindowPages.tab(CharactersPage.PAGE_ID),
                        I18n.format("gui.losttales.lore.release_question",
                                character.getName()),
                        I18n.format("gui.losttales.lore.release_detail"),
                        I18n.format("gui.losttales.lore.release"),
                        new Runnable() {
                            @Override
                            public void run() {
                                page.track(ClientCharacterNetwork
                                        .releaseLoreCharacter(
                                                roster.getRevision(),
                                                character.getOwnershipRevision(),
                                                character.getId()),
                                        "gui.losttales.lore.processing");
                            }
                        });
            }
            return true;
        }
    }

    /** The first reason given; empty while none is. */
    private static String firstOf(String... reasons) {
        for (String reason : reasons) {
            if (reason != null && reason.length() > 0) {
                return reason;
            }
        }
        return "";
    }
}
