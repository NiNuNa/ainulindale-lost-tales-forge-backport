package com.ninuna.losttales.armour;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One armour weight file (CONTENT-06): the weight of each armour
 * material it names, by the material's name as the game registers it
 * ({@code LOTR_GONDOR}, {@code IRON}), and of each single item it names
 * by its registry name, which goes before its material's.
 */
public final class WeightFile {
    private final String id;
    private final Map<String, ArmourWeight> materials;
    private final Map<String, ArmourWeight> items;

    public WeightFile(String id, Map<String, ArmourWeight> materials,
                      Map<String, ArmourWeight> items) {
        this.id = id == null ? "" : id;
        this.materials = Collections.unmodifiableMap(materials == null
                ? new LinkedHashMap<String, ArmourWeight>()
                : new LinkedHashMap<String, ArmourWeight>(materials));
        this.items = Collections.unmodifiableMap(items == null
                ? new LinkedHashMap<String, ArmourWeight>()
                : new LinkedHashMap<String, ArmourWeight>(items));
    }

    public String getId() {
        return this.id;
    }

    public Map<String, ArmourWeight> getMaterials() {
        return this.materials;
    }

    public Map<String, ArmourWeight> getItems() {
        return this.items;
    }
}
