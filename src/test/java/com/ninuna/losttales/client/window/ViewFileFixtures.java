package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.mapmarker.LostTalesMapPage;
import com.ninuna.losttales.client.motion.MotionLabPage;
import com.ninuna.losttales.client.progression.ResetStatuePage;
import com.ninuna.losttales.client.progression.SkillsPage;
import com.ninuna.losttales.config.client.ServerSettingsPage;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.gui.hud.placement.HudPlacementPage;
import com.ninuna.losttales.gui.screen.character.CharactersPage;
import com.ninuna.losttales.gui.screen.fellowship.FellowshipPage;
import com.ninuna.losttales.gui.screen.missive.MissiveBoardPage;
import com.ninuna.losttales.gui.screen.missive.MissiveLetterPage;
import com.ninuna.losttales.gui.screen.quest.QuestJournalPage;
import com.ninuna.losttales.gui.screen.waystone.WaystonePage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The mod's view files for tests, read from the classpath with no game
 * running: checked against the pages and sets the mod registers, by the
 * ids it registers them under, then in force for the views.
 */
public final class ViewFileFixtures {
    /** Every page the client registers a player opens, by its id. */
    static final Set<String> PAGES = new HashSet<String>(Arrays.asList(
            NewPage.PAGE_ID, QuestJournalPage.PAGE_ID, FellowshipPage.PAGE_ID,
            LostTalesMapPage.PAGE_ID, CharactersPage.PAGE_ID,
            SkillsPage.PAGE_ID, ClientSettingsPage.PAGE_ID,
            MotionLabPage.PAGE_ID, HudPlacementPage.PAGE_ID,
            ServerSettingsPage.PAGE_ID));
    /** Every page the client registers that opens from a thing in the world. */
    static final Set<String> WORLD_PAGES = new HashSet<String>(Arrays.asList(
            WaystonePage.PAGE_ID, MissiveBoardPage.PAGE_ID,
            MissiveLetterPage.PAGE_ID, ResetStatuePage.PAGE_ID));
    /** The sets the chat gives. */
    static final Set<String> SETS = new HashSet<String>(Arrays.asList(
            ViewDefaults.CHAT, ViewDefaults.FELLOWSHIP_CHATS));

    /** What the game has, as the mod registers it. */
    static final ViewFile.Known GAME_IDS = new ViewFile.Known() {
        @Override
        public boolean isPage(String id) {
            return PAGES.contains(id) || WORLD_PAGES.contains(id);
        }

        @Override
        public boolean opensFromWorld(String id) {
            return WORLD_PAGES.contains(id);
        }

        @Override
        public boolean isSet(String name) {
            return SETS.contains(name);
        }
    };

    private ViewFileFixtures() {}

    /** The mod's own files, read from the classpath. */
    static DataFiles.Result<ViewFile> bundled() {
        return DataFiles.readBundled(ViewFiles.CLASSPATH, ViewFiles.INDEX,
                ViewFiles.LIST_KEY, ViewFiles.kind(GAME_IDS));
    }

    /** Has the views open with the mod's own files; any problem with them fails the test. */
    public static void useBundled() {
        DataFiles.Result<ViewFile> bundled = bundled();
        if (!bundled.problems.isEmpty()) {
            throw new IllegalStateException("the mod's view files: "
                    + bundled.problems);
        }
        ViewDefaults.use(bundled.values);
    }
}
