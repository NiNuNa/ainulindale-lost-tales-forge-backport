package com.ninuna.losttales.character.cape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stable numeric catalog for LOTR cosmetic capes.
 *
 * Numeric IDs are persisted and sent over the network. They must never be
 * reordered or reused. Texture resolution remains client-only and references
 * LOTR's public LOTRCapes constants rather than copying LOTR assets.
 */
public final class CharacterCapeCatalog {

    public static final int NONE_ID = 0;
    public static final int MAX_NETWORK_ID = 65535;

    public static final int GONDOR = 1;
    public static final int TOWER_GUARD = 2;
    public static final int RANGER = 3;
    public static final int RANGER_ITHILIEN = 4;
    public static final int LOSSARNACH = 5;
    public static final int PELARGIR = 6;
    public static final int BLACKROOT = 7;
    public static final int PINNATH_GELIN = 8;
    public static final int LAMEDON = 9;
    public static final int ROHAN = 10;
    public static final int DALE = 11;
    public static final int DUNLENDING_BERSERKER = 12;
    public static final int GALADHRIM = 13;
    public static final int GALADHRIM_TRADER = 14;
    public static final int WOOD_ELF = 15;
    public static final int HIGH_ELF = 16;
    public static final int RIVENDELL = 17;
    public static final int RIVENDELL_TRADER = 18;
    public static final int NEAR_HARAD = 19;
    public static final int SOUTHRON_CHAMPION = 20;
    public static final int GULF_HARAD = 21;
    public static final int TAURETHRIM = 22;
    public static final int GALADHRIM_SMITH = 23;
    public static final int DORWINION_CAPTAIN = 24;
    public static final int DORWINION_ELF_CAPTAIN = 25;
    public static final int GANDALF = 26;
    public static final int GANDALF_SANTA = 27;

    private static final List<CharacterCapeDefinition> DEFINITIONS;
    private static final Map<Integer, CharacterCapeDefinition> BY_NETWORK_ID;

    static {
        ArrayList<CharacterCapeDefinition> definitions =
                new ArrayList<CharacterCapeDefinition>();
        register(definitions, GONDOR, "gondor");
        register(definitions, TOWER_GUARD, "tower_guard");
        register(definitions, RANGER, "ranger");
        register(definitions, RANGER_ITHILIEN, "ranger_ithilien");
        register(definitions, LOSSARNACH, "lossarnach");
        register(definitions, PELARGIR, "pelargir");
        register(definitions, BLACKROOT, "blackroot");
        register(definitions, PINNATH_GELIN, "pinnath_gelin");
        register(definitions, LAMEDON, "lamedon");
        register(definitions, ROHAN, "rohan");
        register(definitions, DALE, "dale");
        register(definitions, DUNLENDING_BERSERKER, "dunlending_berserker");
        register(definitions, GALADHRIM, "galadhrim");
        register(definitions, GALADHRIM_TRADER, "galadhrim_trader");
        register(definitions, WOOD_ELF, "wood_elf");
        register(definitions, HIGH_ELF, "high_elf");
        register(definitions, RIVENDELL, "rivendell");
        register(definitions, RIVENDELL_TRADER, "rivendell_trader");
        register(definitions, NEAR_HARAD, "near_harad");
        register(definitions, SOUTHRON_CHAMPION, "southron_champion");
        register(definitions, GULF_HARAD, "gulf_harad");
        register(definitions, TAURETHRIM, "taurethrim");
        register(definitions, GALADHRIM_SMITH, "galadhrim_smith");
        register(definitions, DORWINION_CAPTAIN, "dorwinion_captain");
        register(definitions, DORWINION_ELF_CAPTAIN, "dorwinion_elf_captain");
        register(definitions, GANDALF, "gandalf");
        register(definitions, GANDALF_SANTA, "gandalf_santa");

        LinkedHashMap<Integer, CharacterCapeDefinition> byNetworkId =
                new LinkedHashMap<Integer, CharacterCapeDefinition>();
        for (CharacterCapeDefinition definition : definitions) {
            CharacterCapeDefinition previous = byNetworkId.put(
                    Integer.valueOf(definition.getNetworkId()), definition);
            if (previous != null) {
                throw new IllegalStateException("duplicate cape network ID");
            }
        }
        DEFINITIONS = Collections.unmodifiableList(definitions);
        BY_NETWORK_ID = Collections.unmodifiableMap(byNetworkId);
    }

    private CharacterCapeCatalog() {}

    private static void register(List<CharacterCapeDefinition> definitions,
                                 int networkId, String id) {
        definitions.add(new CharacterCapeDefinition(
                networkId,
                id,
                "gui.losttales.character.cape." + id));
    }

    public static List<CharacterCapeDefinition> getDefinitions() {
        return DEFINITIONS;
    }

    public static CharacterCapeDefinition get(int networkId) {
        return BY_NETWORK_ID.get(Integer.valueOf(networkId));
    }

    public static boolean isValidSelection(int networkId) {
        return networkId == NONE_ID || get(networkId) != null;
    }

    public static int normalizeSelection(int networkId) {
        return isValidSelection(networkId) ? networkId : NONE_ID;
    }
}
