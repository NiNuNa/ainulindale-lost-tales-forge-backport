package com.ninuna.losttales.character.registry;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.util.LostTalesIdentifiers;
import com.ninuna.losttales.util.LostTalesLog;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The races the game plays with (CHAR-38): the bundled six, read from their
 * files as the mod loads, until a list takes their place. On the logical
 * server that is the server's own list, read as it starts
 * ({@code character.server.CharacterRaceCatalog}); on a client, the list the server sent as
 * it joined. Both go back to the bundled six as the server stops and as the
 * client leaves.
 */
public final class CharacterRaceRegistry {

    public static final String HUMAN = "losttales:human";
    public static final String ELF = "losttales:elf";
    public static final String DWARF = "losttales:dwarf";
    public static final String HOBBIT = "losttales:hobbit";
    public static final String ORC = "losttales:orc";
    public static final String URUK = "losttales:uruk";

    /** The most races a list holds, the bundled six among them. */
    public static final int MAX_RACES = 32;
    /** The most stand-ins a list holds, for races gone that characters still name. */
    public static final int MAX_STAND_INS = 32;

    /** Where the bundled files are read from: the mod's own classpath. */
    public static final DataFiles.Source CLASSPATH = new DataFiles.Source() {
        @Override
        public Reader open(String path) {
            InputStream stream = CharacterRaceRegistry.class.getClassLoader()
                    .getResourceAsStream("assets/" + LostTalesMetaData.MOD_ID + "/" + path);
            return stream == null ? null
                    : new InputStreamReader(stream, StandardCharsets.UTF_8);
        }
    };

    private static final List<CharacterRaceDefinition> BUNDLED = readBundled();
    private static volatile Map<String, CharacterRaceDefinition> races = index(BUNDLED);
    private static volatile int revision;

    private CharacterRaceRegistry() {}

    /** A race by id, a stand-in included; null for one the list does not hold. */
    public static CharacterRaceDefinition get(String id) {
        return races.get(LostTalesIdentifiers.normalize(id));
    }

    /** Every race of the list in its order, the stand-ins last. */
    public static Collection<CharacterRaceDefinition> getAll() {
        return races.values();
    }

    /** The races a new character may be made as: every one but the stand-ins. */
    public static List<CharacterRaceDefinition> getOffered() {
        List<CharacterRaceDefinition> offered = new ArrayList<CharacterRaceDefinition>();
        for (CharacterRaceDefinition race : races.values()) {
            if (!race.isStandIn()) {
                offered.add(race);
            }
        }
        return offered;
    }

    /** The bundled six, as their files write them. */
    public static List<CharacterRaceDefinition> getBundled() {
        return BUNDLED;
    }

    /** Puts a list in the bundled six's place: the server's own, or what a server sent. */
    public static synchronized void set(List<CharacterRaceDefinition> list) {
        races = index(list == null || list.isEmpty() ? BUNDLED : list);
        revision++;
    }

    /** The bundled six again, as the server stops and a client leaves. */
    public static synchronized void resetToBundled() {
        set(BUNDLED);
    }

    /** Counts every change of the list, so what is worked out from it knows to be worked out again. */
    public static int revision() {
        return revision;
    }

    private static List<CharacterRaceDefinition> readBundled() {
        DataFiles.Result<CharacterRaceDefinition> result = DataFiles.readBundled(CLASSPATH,
                CharacterRaceFiles.INDEX, CharacterRaceFiles.LIST_KEY,
                CharacterRaceFiles.kind(null));
        for (String problem : result.problems) {
            LostTalesLog.warning("Bundled race left out: %s", problem);
        }
        return Collections.unmodifiableList(
                new ArrayList<CharacterRaceDefinition>(result.values));
    }

    private static Map<String, CharacterRaceDefinition> index(
            List<CharacterRaceDefinition> list) {
        LinkedHashMap<String, CharacterRaceDefinition> byId =
                new LinkedHashMap<String, CharacterRaceDefinition>();
        for (CharacterRaceDefinition race : list) {
            if (race != null && !byId.containsKey(race.getId())) {
                byId.put(race.getId(), race);
            }
        }
        return Collections.unmodifiableMap(byId);
    }
}
