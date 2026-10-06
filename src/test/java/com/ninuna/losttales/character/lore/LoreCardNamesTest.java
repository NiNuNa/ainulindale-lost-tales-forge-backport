package com.ninuna.losttales.character.lore;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A titled lore character's name as the lore window's card shows it: in
 * English the card's line is the character's own name, word for word, so
 * only a translation reads it differently.
 */
public final class LoreCardNamesTest {
    private static final String PREFIX = "gui.losttales.character.lore.";
    private static final String NAME = ".name";

    @Test
    public void everyCardNameIsTheCharactersOwnNameInEnglish() throws Exception {
        Map<String, String> names = loreNames();
        int lines = 0;
        for (Map.Entry<String, String> line : LostTalesLangFile.english().entrySet()) {
            String key = line.getKey();
            if (!key.startsWith(PREFIX) || !key.endsWith(NAME)) {
                continue;
            }
            String path = key.substring(PREFIX.length(), key.length() - NAME.length());
            assertNotNull("no lore character " + path, names.get(path));
            assertEquals(names.get(path), line.getValue());
            lines++;
        }
        assertTrue(lines > 0);
    }

    @Test
    public void everyNameWithATitleHasACardLine() throws Exception {
        Map<String, String> english = LostTalesLangFile.english();
        for (Map.Entry<String, String> named : loreNames().entrySet()) {
            if (named.getValue().contains(", ")) {
                assertTrue("no card line for " + named.getKey(),
                        english.containsKey(PREFIX + named.getKey() + NAME));
            }
        }
    }

    /** Every bundled lore character's name, by the path of its id. */
    private static Map<String, String> loreNames() throws Exception {
        Map<String, String> names = new HashMap<String, String>();
        JsonArray files = read("/assets/losttales/lore_characters/index.json")
                .getAsJsonArray("files");
        for (JsonElement file : files) {
            JsonObject character = read("/assets/losttales/" + file.getAsString());
            String id = character.get("id").getAsString();
            names.put(id.substring(id.indexOf(':') + 1),
                    character.get("name").getAsString());
        }
        return names;
    }

    private static JsonObject read(String resource) throws Exception {
        InputStream in = LoreCardNamesTest.class.getResourceAsStream(resource);
        assertNotNull(resource, in);
        try {
            return new JsonParser().parse(new InputStreamReader(in, "UTF-8"))
                    .getAsJsonObject();
        } finally {
            in.close();
        }
    }
}
