package com.ninuna.losttales.client.window;

import com.ninuna.losttales.data.DataFiles;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The window each view opens with is a view file (WIN-49): the mod's own
 * give each view its window without a warning, the Profile view a stage on
 * the left third (WIN-50); a file is read strictly, any warning leaving it
 * out; and a player's own file of a view takes the mod's place.
 */
public final class ViewFileTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @After
    public void restore() {
        ViewFileFixtures.useBundled();
    }

    /* ---- The mod's own files ---- */

    @Test
    public void everyBundledFileLoadsWithoutAWarning() {
        DataFiles.Result<ViewFile> bundled = ViewFileFixtures.bundled();
        assertEquals(new ArrayList<String>(), bundled.problems);
        assertEquals(9, bundled.values.size());
    }

    /**
     * Chat with the chat's set; Consoles every console; Map the map; the
     * Quest Journal the journal then the map; Fellowships the Fellowships
     * page, the map and the fellowship chats; Profile the Characters page;
     * Skills the Skills page; Settings every settings page: each locked. A
     * custom view's New Page, unlocked.
     */
    @Test
    public void theBundledFilesGiveTheWindowsTheViewsHaveAlwaysOpenedWith() {
        ViewFileFixtures.useBundled();
        expect("channels", true, ViewFile.Entry.set(ViewDefaults.CHAT));
        expect("consoles", true, ViewFile.Entry.category());
        expect("map", true, ViewFile.Entry.page("map"));
        expect("quest_journal", true, ViewFile.Entry.page("journal"),
                ViewFile.Entry.page("map"));
        expect("fellowships", true, ViewFile.Entry.page("fellowship"),
                ViewFile.Entry.page("map"),
                ViewFile.Entry.set(ViewDefaults.FELLOWSHIP_CHATS));
        expect("profile", true, ViewFile.Entry.page("characters"));
        expect("skills", true, ViewFile.Entry.page("skills"));
        expect("settings", true, ViewFile.Entry.category());
        expect(ViewFile.CUSTOM, false, ViewFile.Entry.page(NewPage.PAGE_ID));
    }

    /** The Profile view alone is a stage, its window on the left third; every other stands in the middle. */
    @Test
    public void theProfileViewIsAStageOnTheLeftThird() {
        ViewFileFixtures.useBundled();
        for (PageCategory category : PageCategory.values()) {
            if (!category.isView()) {
                continue;
            }
            View view = Views.of(category);
            boolean profile = category == PageCategory.PROFILE;
            assertEquals(category.id(), profile, ViewDefaults.isStage(view));
            assertEquals(category.id(), profile ? Window.ScreenFill.LEFT_THIRD
                    : Window.ScreenFill.NONE, ViewDefaults.placeOf(view));
        }
        assertEquals(ViewFile.MIDDLE, ViewDefaults.fileOf(ViewFile.CUSTOM).place);
        assertFalse(ViewDefaults.fileOf(ViewFile.CUSTOM).stage);
    }

    /** Every view the screen has opens with a window of the mod's files. */
    @Test
    public void everyViewHasABundledFile() {
        ViewFileFixtures.useBundled();
        for (PageCategory category : PageCategory.values()) {
            if (category.isView()) {
                assertNotNull(category.id(), ViewDefaults.fileOf(category.id()));
            }
        }
        assertNotNull(ViewDefaults.fileOf(ViewFile.CUSTOM));
    }

    private static void expect(String view, boolean locked,
                               ViewFile.Entry... pages) {
        ViewFile file = ViewDefaults.fileOf(view);
        assertNotNull(view, file);
        assertEquals(view, locked, file.locked);
        assertEquals(view, Arrays.asList(pages), file.pages);
    }

    /* ---- Reading strictly ---- */

    @Test
    public void entriesAreReadAsWritten() {
        assertEquals(ViewFile.Entry.category(), ViewFile.entryOf("category"));
        assertEquals(ViewFile.Entry.set("chat"), ViewFile.entryOf("set:chat"));
        assertEquals(ViewFile.Entry.page("map"), ViewFile.entryOf("map"));
        assertEquals("set:fellowship_chats",
                ViewFile.Entry.set("fellowship_chats").written());
    }

    @Test
    public void aGoodFileRaisesNothing() {
        assertTrue(problems("{\"view\": \"map\", \"locked\": false, \"place\": \"middle\", \"stage\": false,"
                + " \"pages\": [\"map\", \"journal\"]}").isEmpty());
    }

    @Test
    public void aKeyNothingReadsIsAWarning() {
        assertWarns("{\"view\": \"map\", \"locked\": true, \"place\": \"middle\", \"stage\": false, \"pages\": [\"map\"],"
                + " \"front\": \"map\"}", "nothing reads");
    }

    @Test
    public void everyFieldMustBeWritten() {
        assertWarns("{\"locked\": true, \"place\": \"middle\", \"stage\": false, \"pages\": [\"map\"]}", "names no view");
        assertWarns("{\"view\": \"map\", \"pages\": [\"map\"]}",
                "whether the window is locked");
        assertWarns("{\"view\": \"map\", \"locked\": true}", "lists no pages");
        assertWarns("{\"view\": \"map\", \"locked\": true, \"place\": \"middle\", \"stage\": false, \"pages\": []}",
                "lists no page");
    }

    @Test
    public void wherePlaceAndStageAreMustBeWritten() {
        assertWarns("{\"view\": \"map\", \"locked\": true, \"stage\": false,"
                + " \"pages\": [\"map\"]}", "where the window stands");
        assertWarns("{\"view\": \"map\", \"locked\": true, \"place\": \"middle\","
                + " \"pages\": [\"map\"]}", "whether the view is a stage");
    }

    @Test
    public void aPlaceIsTheMiddleOrAPartOfTheScreen() {
        assertWarns("{\"view\": \"map\", \"locked\": true, \"place\": \"corner\","
                + " \"stage\": false, \"pages\": [\"map\"]}", "which is no place");
        assertWarns("{\"view\": \"map\", \"locked\": true, \"place\": \"none\","
                + " \"stage\": false, \"pages\": [\"map\"]}", "which is no place");
        assertTrue(problems("{\"view\": \"map\", \"locked\": true,"
                + " \"place\": \"right_half\", \"stage\": true,"
                + " \"pages\": [\"map\"]}").toString().contains("no place"));
        List<String> said = new ArrayList<String>();
        ViewFile third = ViewFile.parse(new StringReader("{\"view\": \"map\","
                + " \"locked\": true, \"place\": \"left_third\", \"stage\": true,"
                + " \"pages\": [\"map\"]}"), "test.json", said);
        assertTrue(said.isEmpty());
        assertEquals(Window.ScreenFill.LEFT_THIRD, third.fill());
        assertTrue(third.stage);
        assertTrue(problems("{\"view\": \"map\", \"locked\": true,"
                + " \"place\": \"right\", \"stage\": true,"
                + " \"pages\": [\"map\"]}").isEmpty());
    }

    @Test
    public void aFieldOfTheWrongShapeIsAWarning() {
        assertWarns("{\"view\": \"map\", \"locked\": \"yes\", \"pages\": [\"map\"]}",
                "true or false");
        assertWarns("{\"view\": \"map\", \"locked\": true, \"place\": \"middle\", \"stage\": false, \"pages\": \"map\"}",
                "other than a list");
        assertWarns("{\"view\": \"map\", \"locked\": true, \"place\": \"middle\", \"stage\": false, \"pages\": [3]}",
                "other than a text");
        assertWarns("{\"view\": 7, \"locked\": true, \"place\": \"middle\", \"stage\": false, \"pages\": [\"map\"]}",
                "other than a text");
    }

    @Test
    public void anUnknownPageSetOrViewIsAWarning() {
        assertWarns("{\"view\": \"map\", \"locked\": true, \"place\": \"middle\", \"stage\": false, \"pages\": [\"atlas\"]}",
                "which is no page");
        assertWarns("{\"view\": \"map\", \"locked\": true, \"place\": \"middle\", \"stage\": false,"
                + " \"pages\": [\"set:whispers\"]}", "which no system gives");
        assertWarns("{\"view\": \"tavern\", \"locked\": true, \"place\": \"middle\", \"stage\": false, \"pages\": [\"map\"]}",
                "which is no view");
    }

    @Test
    public void aCategoryWithNoViewOfItsOwnIsNoView() {
        assertWarns("{\"view\": \"whispers\", \"locked\": true, \"place\": \"middle\", \"stage\": false,"
                + " \"pages\": [\"set:chat\"]}", "which is no view");
        assertWarns("{\"view\": \"new_page\", \"locked\": false, \"place\": \"middle\", \"stage\": false,"
                + " \"pages\": [\"new_page\"]}", "which is no view");
    }

    @Test
    public void aPageOnlyTheGameOpensIsAWarning() {
        assertWarns("{\"view\": \"map\", \"locked\": true, \"place\": \"middle\", \"stage\": false,"
                + " \"pages\": [\"waystone\"]}", "which only the game opens");
        assertWarns("{\"view\": \"profile\", \"locked\": true, \"place\": \"middle\", \"stage\": false,"
                + " \"pages\": [\"character_editor\"]}", "which only the game opens");
    }

    @Test
    public void aCustomViewHasNoCategoryToList() {
        assertWarns("{\"view\": \"custom\", \"locked\": false, \"place\": \"middle\", \"stage\": false,"
                + " \"pages\": [\"category\"]}", "no category of its own");
    }

    @Test
    public void anEntryListedTwiceIsAWarning() {
        assertWarns("{\"view\": \"map\", \"locked\": true, \"place\": \"middle\", \"stage\": false,"
                + " \"pages\": [\"map\", \"journal\", \"map\"]}", "lists map twice");
    }

    @Test
    public void aWindowOpensWithAFewPagesAtMost() {
        StringBuilder pages = new StringBuilder();
        for (int index = 0; index <= ViewFile.MAX_PAGES; index++) {
            pages.append(index == 0 ? "" : ", ").append("\"page_")
                    .append(index).append('"');
        }
        List<String> said = problems("{\"view\": \"map\", \"locked\": true, \"place\": \"middle\", \"stage\": false,"
                + " \"pages\": [" + pages + "]}");
        assertTrue(said.toString(), said.toString().contains("at most"));
    }

    /** A file with any warning is left out; the others still load. */
    @Test
    public void aFileWithAWarningIsLeftOut() throws IOException {
        File folder = this.temporary.newFolder("views");
        write(folder, "a.json", "{\"view\": \"map\", \"locked\": true, \"place\": \"middle\", \"stage\": false,"
                + " \"pages\": [\"atlas\"]}");
        write(folder, "b.json", "{\"view\": \"profile\", \"locked\": true, \"place\": \"middle\", \"stage\": false,"
                + " \"pages\": [\"skills\"]}");
        write(folder, "c.json", "not json at all [");
        DataFiles.Result<ViewFile> read = DataFiles.readFolder(folder,
                ViewFiles.kind(ViewFileFixtures.GAME_IDS));
        assertEquals(1, read.values.size());
        assertEquals("profile", read.values.get(0).view);
        assertEquals(2, read.problems.size());
    }

    /* ---- A player's own ---- */

    @Test
    public void aPlayersFileTakesTheModsPlace() throws IOException {
        File folder = this.temporary.newFolder("views");
        write(folder, "journal.json", "{\"view\": \"quest_journal\","
                + " \"locked\": false, \"place\": \"middle\", \"stage\": false, \"pages\": [\"map\", \"journal\"]}");
        write(folder, "mine.json", "{\"view\": \"custom\", \"locked\": true, \"place\": \"middle\", \"stage\": false,"
                + " \"pages\": [\"map\"]}");
        DataFiles.Kind<ViewFile> kind = ViewFiles.kind(ViewFileFixtures.GAME_IDS);
        DataFiles.Result<ViewFile> mine = DataFiles.readFolder(folder, kind);
        assertTrue(mine.problems.isEmpty());
        ViewDefaults.use(DataFiles.merge(ViewFileFixtures.bundled().values,
                mine.values, kind));
        expect("quest_journal", false, ViewFile.Entry.page("map"),
                ViewFile.Entry.page("journal"));
        expect(ViewFile.CUSTOM, true, ViewFile.Entry.page("map"));
        // The views the player's files leave alone keep the mod's.
        expect("map", true, ViewFile.Entry.page("map"));
    }

    @Test
    public void aPlayersFileWithAWarningLeavesTheModsInPlace() throws IOException {
        File folder = this.temporary.newFolder("views");
        write(folder, "journal.json", "{\"view\": \"quest_journal\","
                + " \"locked\": false, \"place\": \"middle\", \"stage\": false, \"pages\": [\"atlas\"]}");
        DataFiles.Kind<ViewFile> kind = ViewFiles.kind(ViewFileFixtures.GAME_IDS);
        DataFiles.Result<ViewFile> mine = DataFiles.readFolder(folder, kind);
        assertEquals(1, mine.problems.size());
        ViewDefaults.use(DataFiles.merge(ViewFileFixtures.bundled().values,
                mine.values, kind));
        expect("quest_journal", true, ViewFile.Entry.page("journal"),
                ViewFile.Entry.page("map"));
    }

    @Test
    public void aViewNoFileNamesOpensWithNoWindow() {
        ViewDefaults.use(new ArrayList<ViewFile>());
        assertNull(ViewDefaults.fileOf("map"));
        assertNull(ViewDefaults.of(Views.of(PageCategory.MAP)));
        ViewFileFixtures.useBundled();
        assertNotNull(ViewDefaults.of(Views.of(PageCategory.MAP)));
        assertTrue(ViewDefaults.of(Views.of(PageCategory.MAP)).locked);
    }

    /** Until the resources are first read, the views open with the mod's own files from its jar. */
    @Test
    public void beforeTheResourcesAreReadTheModsOwnFilesHold() {
        ViewDefaults.forget();
        ViewFile journal = ViewDefaults.fileOf("quest_journal");
        assertNotNull(journal);
        assertEquals(Arrays.asList(ViewFile.Entry.page("journal"),
                ViewFile.Entry.page("map")), journal.pages);
        assertNotNull(ViewDefaults.fileOf(ViewFile.CUSTOM));
    }

    @Test
    public void theGameKnowsNoSetNoSystemGave() {
        assertFalse(ViewDefaults.isSet("no_such_set"));
        assertFalse(ViewDefaults.isSet(null));
    }

    /* ---- Helpers ---- */

    private static List<String> problems(String json) {
        List<String> said = new ArrayList<String>();
        ViewFile file = ViewFile.parse(new StringReader(json), "test.json", said);
        assertNotNull(json, file);
        said.addAll(ViewFile.warningsOf(file, ViewFileFixtures.GAME_IDS));
        return said;
    }

    private static void assertWarns(String json, String warning) {
        List<String> said = problems(json);
        boolean found = false;
        for (String each : said) {
            found |= each.contains(warning);
        }
        assertTrue("expected a warning with '" + warning + "', got " + said,
                found);
    }

    private static void write(File folder, String name, String text)
            throws IOException {
        Files.write(new File(folder, name).toPath(),
                text.getBytes(StandardCharsets.UTF_8));
    }
}
