package com.ninuna.losttales.config;

import com.ninuna.losttales.config.client.DefinedCameraOptions;
import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import com.ninuna.losttales.util.EnglishWords;
import cpw.mods.fml.relauncher.FMLInjectionData;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Every option's words are lines of the lang file and nowhere else: each
 * option of the mod's files and of the camera's has a name line and a tip
 * line in English, a server option of a few words a line for each word,
 * and the comment the files carry above it is its English tip followed
 * only by what Forge adds, its range and its default. A comment written
 * in the code would stand between the two and fail here.
 */
public final class LostTalesConfigWordsTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    /** What Forge writes after a comment as it reads an option: its range and default, or nothing. */
    private static final Pattern FORGE_ADDS = Pattern.compile(
            "( \\[(range: [^\\]]* ~ [^\\]]*, )?default: .*\\])?");
    /** A property line of a written file: its type, its name, and a value or a list's start. */
    private static final Pattern PROPERTY = Pattern.compile(
            "^[A-Z]:\"?([^\"=<]+?)\"?(=.*| <)$");

    /** Every option as the first load defines it, the mod's files and the camera's. */
    private static List<Configuration> definitions() {
        Configuration options = new Configuration();
        LostTalesConfig.defineOptions(options);
        List<Configuration> both = new ArrayList<Configuration>();
        both.add(options);
        both.add(DefinedCameraOptions.definitions());
        return both;
    }

    /** Each option of {@code config} as {@code category.key}, with its property. */
    private static Map<String, Property> options(Configuration config) {
        Map<String, Property> options = new HashMap<String, Property>();
        for (String name : config.getCategoryNames()) {
            for (Map.Entry<String, Property> entry
                    : config.getCategory(name).getValues().entrySet()) {
                options.put(name + "." + entry.getKey(), entry.getValue());
            }
        }
        return options;
    }

    private static String category(String option) {
        return option.substring(0, option.indexOf('.'));
    }

    private static String key(String option) {
        return option.substring(option.indexOf('.') + 1);
    }

    @Test
    public void everyOptionHasANameAndATipLine() {
        List<String> missing = new ArrayList<String>();
        int counted = 0;
        for (Configuration config : definitions()) {
            for (String option : options(config).keySet()) {
                counted++;
                String name = LostTalesConfigWords.nameKey(category(option), key(option));
                for (String langKey : new String[] {name, name + ".tooltip"}) {
                    if (!EnglishWords.INSTANCE.has(langKey)) {
                        missing.add(langKey);
                    }
                }
            }
        }
        assertTrue("the options were found", counted > 200);
        assertEquals("option lines missing from en_US.lang",
                new ArrayList<String>(), missing);
    }

    @Test
    public void everyCategoryHasANameAndATipLine() {
        List<String> missing = new ArrayList<String>();
        for (Configuration config : definitions()) {
            for (String name : config.getCategoryNames()) {
                ConfigCategory category = config.getCategory(name);
                String langKey = category.getLanguagekey();
                for (String line : new String[] {langKey, langKey + ".tooltip"}) {
                    if (!EnglishWords.INSTANCE.has(line)) {
                        missing.add(line);
                    }
                }
            }
        }
        assertEquals("category lines missing from en_US.lang",
                new ArrayList<String>(), missing);
    }

    /**
     * An option's comment is its English tip and what Forge adds, so no
     * option keeps words of its own in the code; its language key is its
     * name line's.
     */
    @Test
    public void eachCommentIsTheEnglishTipAndWhatForgeAdds() {
        List<String> wrong = new ArrayList<String>();
        for (Configuration config : definitions()) {
            for (Map.Entry<String, Property> option : options(config).entrySet()) {
                String category = category(option.getKey());
                String key = key(option.getKey());
                Property property = option.getValue();
                String tip = EnglishWords.INSTANCE.format(
                        LostTalesConfigWords.tipKey(category, key));
                if (property.comment == null || !property.comment.startsWith(tip)
                        || !FORGE_ADDS.matcher(property.comment.substring(
                                tip.length())).matches()) {
                    wrong.add(option.getKey() + ": " + property.comment);
                }
                if (!LostTalesConfigWords.nameKey(category, key)
                        .equals(property.getLanguageKey())) {
                    wrong.add(option.getKey() + " is keyed " + property.getLanguageKey());
                }
            }
        }
        assertEquals("comments other than the English tip",
                new ArrayList<String>(), wrong);
    }

    /**
     * A tip is shown as written: a chat line formats its words, so a
     * {@code %} would be read as a pattern.
     */
    @Test
    public void everyTipReadsAsWritten() {
        List<String> patterned = new ArrayList<String>();
        for (Configuration config : definitions()) {
            for (String option : options(config).keySet()) {
                String tip = EnglishWords.INSTANCE.format(LostTalesConfigWords.tipKey(
                        category(option), key(option)));
                if (tip.indexOf('%') >= 0 || tip.trim().length() == 0) {
                    patterned.add(option);
                }
            }
        }
        assertEquals(new ArrayList<String>(), patterned);
    }

    /** The Server Settings page shows a server option's words from the lang file. */
    @Test
    public void aServerOptionOfAFewWordsHasALineForEachWord() {
        Configuration options = new Configuration();
        LostTalesConfig.defineOptions(options);
        List<String> missing = new ArrayList<String>();
        int counted = 0;
        for (Map.Entry<String, Property> option : options(options).entrySet()) {
            String category = category(option.getKey());
            String[] words = option.getValue().getValidValues();
            if (LostTalesConfig.CLIENT_CATEGORIES.contains(category)
                    || words == null) {
                continue;
            }
            for (String word : words) {
                counted++;
                String line = LostTalesConfigWords.wordKey(category,
                        key(option.getKey()), word);
                if (!EnglishWords.INSTANCE.has(line)) {
                    missing.add(line);
                }
            }
        }
        assertTrue("a server option of a few words was found", counted > 0);
        assertEquals(new ArrayList<String>(), missing);
    }

    @Test
    public void aMissingLineFallsBackToItsKey() {
        String key = LostTalesConfigWords.tipKey("no_such_category", "noSuchOption");
        assertEquals(key, LostTalesConfigWords.english(key));
    }

    /**
     * Loaded against new files, as a game's first start writes them, every
     * file holds each option under its English tip.
     */
    @Test
    public void theFilesCarryTheEnglishTips() throws Exception {
        initializeForgeHome(this.folder.getRoot());
        File client = new File(this.folder.getRoot(), "client.cfg");
        File server = new File(this.folder.getRoot(), "server.cfg");
        File roles = new File(this.folder.getRoot(), "roles.cfg");
        File channels = new File(this.folder.getRoot(), "channels.cfg");
        Object[] state = saveLoadState();
        try {
            LostTalesConfig.load(client, server, roles, channels);
        } finally {
            restoreLoadState(state);
        }
        File cameraFolder = this.folder.newFolder("config");
        LostTalesThirdPersonConfig.load(cameraFolder);
        File camera = LostTalesConfigFiles.clientFile(cameraFolder,
                LostTalesConfigFiles.CAMERA_OPTIONS);

        Map<String, String> written = new HashMap<String, String>();
        for (File file : new File[] {client, server, roles, channels, camera}) {
            written.putAll(commentsIn(file));
        }
        List<String> wrong = new ArrayList<String>();
        for (Configuration config : definitions()) {
            for (Map.Entry<String, Property> option : options(config).entrySet()) {
                String comment = written.get(option.getKey());
                String tip = EnglishWords.INSTANCE.format(LostTalesConfigWords.tipKey(
                        category(option.getKey()), key(option.getKey())));
                if (comment == null || !comment.equals(option.getValue().comment)
                        || !comment.startsWith(tip)) {
                    wrong.add(option.getKey() + ": " + comment);
                }
            }
        }
        assertEquals("options written without their English tip",
                new ArrayList<String>(), wrong);
    }

    /** Each option's comment in a written config file, by {@code category.key}. */
    private static Map<String, String> commentsIn(File file) throws Exception {
        Map<String, String> comments = new HashMap<String, String>();
        String category = null;
        boolean inList = false;
        StringBuilder comment = new StringBuilder();
        for (String raw : new String(Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8).split("\r?\n")) {
            String line = raw.trim();
            if (inList) {
                inList = !">".equals(line);
            } else if (category == null) {
                if (line.endsWith(" {")) {
                    category = line.substring(0, line.length() - 2).replace("\"", "");
                    comment.setLength(0);
                }
            } else if ("}".equals(line)) {
                category = null;
            } else if (line.startsWith("# ") || "#".equals(line)) {
                if (comment.length() > 0) {
                    comment.append('\n');
                }
                comment.append(line.length() > 2 ? line.substring(2) : "");
            } else {
                Matcher property = PROPERTY.matcher(line);
                if (property.matches()) {
                    comments.put(category + "." + property.group(1), comment.toString());
                    comment.setLength(0);
                    inList = " <".equals(property.group(2));
                }
            }
        }
        return comments;
    }

    private static final String[] LOAD_STATE = {"loadedClientFile",
            "loadedServerFile", "loadedRolesFile", "loadedChannelsFile", "shipped"};

    /** What a load leaves behind: the files it read and the definitions, for the next test to start clean. */
    private static Object[] saveLoadState() throws Exception {
        Object[] state = new Object[LOAD_STATE.length];
        for (int index = 0; index < LOAD_STATE.length; index++) {
            state[index] = loadField(LOAD_STATE[index]).get(null);
        }
        return state;
    }

    private static void restoreLoadState(Object[] state) throws Exception {
        for (int index = 0; index < LOAD_STATE.length; index++) {
            loadField(LOAD_STATE[index]).set(null, state[index]);
        }
    }

    private static Field loadField(String name) throws Exception {
        Field field = LostTalesConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void initializeForgeHome(File directory) throws Exception {
        Field minecraftHome = FMLInjectionData.class
                .getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, directory);
    }
}
