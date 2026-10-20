package com.ninuna.losttales.character.cape;

import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The capes are data files (CHAR-39): the 27 bundled ones are LOTR's,
 * open to everyone; a cape that names races or factions is for them
 * alone; a file names only a picture every player has.
 */
public final class CharacterCapeCatalogTest {

    @After
    public void bundledAgain() {
        CharacterCapeCatalog.resetToBundled();
    }

    @Test
    public void theTwentySevenLotrCapesLoadOpenToEveryone() {
        DataFiles.Result<CharacterCapeDefinition> read = DataFiles.readBundled(
                CharacterCapeCatalog.CLASSPATH, CharacterCapeCatalog.INDEX,
                CharacterCapeCatalog.LIST_KEY, CharacterCapeCatalog.KIND);

        assertEquals(new ArrayList<String>(), read.problems);
        assertEquals(27, read.values.size());
        assertEquals("losttales:gondor", read.values.get(0).getId());
        assertEquals("lotr:GANDALF_SANTA", read.values.get(26).getPicture());
        Map<String, String> english = LostTalesLangFile.english();
        for (CharacterCapeDefinition cape : read.values) {
            assertTrue(cape.getId(), cape.getRaces().isEmpty() && cape.getFactions().isEmpty());
            assertEquals(cape.getId(), "", cape.getName());
            assertTrue(cape.getId(), english.containsKey(cape.getTranslationKey()));
        }
    }

    @Test
    public void aPictureEveryPlayerHasIsLotrsByNameOrTheModsOwn() {
        assertTrue(CharacterCapeCatalog.isPictureEveryoneHas("lotr:ROHAN"));
        assertFalse(CharacterCapeCatalog.isPictureEveryoneHas("lotr:BALROG"));
        assertFalse("not in the jar",
                CharacterCapeCatalog.isPictureEveryoneHas("losttales:textures/capes/none.png"));
        assertFalse(CharacterCapeCatalog.isPictureEveryoneHas("https://example.com/cape.png"));
        assertFalse(CharacterCapeCatalog.isPictureEveryoneHas("losttales:textures/capes/../x.png"));
    }

    @Test
    public void aFileOutOfShapeIsSaid() {
        List<String> said = new ArrayList<String>();
        CharacterCapeDefinition cape = CharacterCapeCatalog.parse(new StringReader(
                "{\"id\": \"myserver:bear\", \"picture\": \"lotr:BALROG\", \"colour\": 3,"
                        + " \"races\": [\"Not A Race\"]}"), "bear.json", said);
        List<String> all = new ArrayList<String>(said);
        all.addAll(CharacterCapeCatalog.warningsOf(cape));

        assertTrue(all.toString(), all.toString().contains("'colour', which nothing reads"));
        assertTrue(all.toString(), all.toString().contains("shows the picture 'lotr:BALROG'"));
        assertTrue(all.toString(), all.toString().contains("names the race 'Not A Race'"));
    }

    /** A cape naming races or factions is theirs; each list it names has to hold the wearer's. */
    @Test
    public void aLimitedCapeIsForTheRacesAndFactionsItNames() {
        CharacterCapeDefinition bear = new CharacterCapeDefinition("myserver:bear", "Bear",
                "lotr:ROHAN", set("myserver:beorning"), set("lotr:dale", "lotr:rohan"));

        assertTrue(bear.mayWear("myserver:beorning", "lotr:dale"));
        assertFalse(bear.mayWear("losttales:human", "lotr:dale"));
        assertFalse(bear.mayWear("myserver:beorning", "lotr:gondor"));
        assertTrue(new CharacterCapeDefinition("myserver:open", "Open", "lotr:DALE", null, null)
                .mayWear("losttales:orc", "lotr:mordor"));
    }

    /** The choices are none first, then every cape the character may wear, its own kept. */
    @Test
    public void theChoicesKeepTheCapeACharacterWears() {
        CharacterCapeCatalog.set(Arrays.asList(
                new CharacterCapeDefinition("myserver:open", "Open", "lotr:DALE", null, null),
                new CharacterCapeDefinition("myserver:bear", "Bear", "lotr:ROHAN",
                        set("myserver:beorning"), null)));

        assertEquals(Arrays.asList("", "myserver:open"),
                CharacterCapeCatalog.choicesFor("losttales:human", "lotr:dale", ""));
        assertEquals(Arrays.asList("", "myserver:open", "myserver:bear"),
                CharacterCapeCatalog.choicesFor("myserver:beorning", "lotr:dale", ""));
        assertEquals("a cape limited since it was chosen stays",
                Arrays.asList("", "myserver:open", "myserver:bear"),
                CharacterCapeCatalog.choicesFor("losttales:human", "lotr:dale", "myserver:bear"));
        assertEquals("a cape gone from the files stays too",
                Arrays.asList("", "myserver:open", "losttales:gondor"),
                CharacterCapeCatalog.choicesFor("losttales:human", "lotr:dale", "losttales:gondor"));
    }

    private static LinkedHashSet<String> set(String... values) {
        return new LinkedHashSet<String>(Arrays.asList(values));
    }
}
