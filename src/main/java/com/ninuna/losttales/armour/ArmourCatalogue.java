package com.ninuna.losttales.armour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every armour weight and named set one side plays with (CONTENT-06,
 * CONTENT-07): the weight files merged in order, a later file's entry in
 * place of an earlier one's for the same material or item, and the named
 * sets, found by the items each slot takes. Fixed once made.
 */
public final class ArmourCatalogue {
    /** The most materials, and the most items, a catalogue weighs. */
    public static final int MAX_WEIGHTS = 4096;
    /** The most named sets a catalogue holds: every bundled file and every file of a server's own. */
    public static final int MAX_SETS = 512;

    public static final ArmourCatalogue EMPTY = new ArmourCatalogue(
            new LinkedHashMap<String, ArmourWeight>(), new LinkedHashMap<String, ArmourWeight>(),
            new ArrayList<NamedSet>());

    private final Map<String, ArmourWeight> materials;
    private final Map<String, ArmourWeight> items;
    private final List<NamedSet> sets;
    private final Map<String, NamedSet> byId;
    private final Map<ArmourSlot, Map<String, List<NamedSet>>> bySlotItem;

    private ArmourCatalogue(Map<String, ArmourWeight> materials, Map<String, ArmourWeight> items,
                            List<NamedSet> sets) {
        this.materials = Collections.unmodifiableMap(materials);
        this.items = Collections.unmodifiableMap(items);
        this.sets = Collections.unmodifiableList(sets);
        Map<String, NamedSet> ids = new LinkedHashMap<String, NamedSet>();
        Map<ArmourSlot, Map<String, List<NamedSet>>> index =
                new EnumMap<ArmourSlot, Map<String, List<NamedSet>>>(ArmourSlot.class);
        for (ArmourSlot slot : ArmourSlot.values()) {
            index.put(slot, new HashMap<String, List<NamedSet>>());
        }
        for (NamedSet set : sets) {
            ids.put(set.getId(), set);
            for (ArmourSlot slot : ArmourSlot.values()) {
                for (String item : set.items(slot)) {
                    List<NamedSet> taking = index.get(slot).get(item);
                    if (taking == null) {
                        taking = new ArrayList<NamedSet>();
                        index.get(slot).put(item, taking);
                    }
                    if (!taking.contains(set)) {
                        taking.add(set);
                    }
                }
            }
        }
        this.byId = Collections.unmodifiableMap(ids);
        this.bySlotItem = index;
    }

    /**
     * The catalogue of these files: each weight file's entries over the
     * earlier ones', and the named sets in order. Sets past
     * {@link #MAX_SETS}, and weights past {@link #MAX_WEIGHTS}, are left out.
     */
    public static ArmourCatalogue of(List<WeightFile> weightFiles, List<NamedSet> namedSets) {
        Map<String, ArmourWeight> materials = new LinkedHashMap<String, ArmourWeight>();
        Map<String, ArmourWeight> items = new LinkedHashMap<String, ArmourWeight>();
        if (weightFiles != null) {
            for (WeightFile file : weightFiles) {
                putAll(materials, file.getMaterials());
                putAll(items, file.getItems());
            }
        }
        List<NamedSet> sets = new ArrayList<NamedSet>();
        if (namedSets != null) {
            for (NamedSet set : namedSets) {
                if (sets.size() < MAX_SETS) {
                    sets.add(set);
                }
            }
        }
        return new ArmourCatalogue(materials, items, sets);
    }

    private static void putAll(Map<String, ArmourWeight> into, Map<String, ArmourWeight> from) {
        for (Map.Entry<String, ArmourWeight> entry : from.entrySet()) {
            if (into.size() < MAX_WEIGHTS || into.containsKey(entry.getKey())) {
                into.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * What a piece weighs: its item's weight where a file names the item,
     * else its material's, else neither. Either may be null or empty.
     */
    public ArmourWeight weightOf(String itemId, String material) {
        ArmourWeight weight = itemId == null ? null : this.items.get(itemId);
        if (weight == null && material != null) {
            weight = this.materials.get(material);
        }
        return weight == null ? ArmourWeight.NEITHER : weight;
    }

    /**
     * Every named set a piece in {@code slot} counts for: each ordinary set
     * whose slot takes its item, and the relic set it is marked with
     * where that set's slot takes its item too.
     */
    public List<NamedSet> setsOf(ArmourSlot slot, String itemId, String relicId) {
        if (slot == null || itemId == null) {
            return Collections.<NamedSet>emptyList();
        }
        List<NamedSet> taking = this.bySlotItem.get(slot).get(itemId);
        if (taking == null) {
            return Collections.<NamedSet>emptyList();
        }
        List<NamedSet> counted = new ArrayList<NamedSet>();
        for (NamedSet set : taking) {
            if (!set.isRelic() || set.getId().equals(relicId)) {
                counted.add(set);
            }
        }
        return counted;
    }

    /** The named set of this id; null for none. */
    public NamedSet set(String id) {
        return this.byId.get(id);
    }

    public List<NamedSet> getSets() {
        return this.sets;
    }

    public Map<String, ArmourWeight> getMaterials() {
        return this.materials;
    }

    public Map<String, ArmourWeight> getItems() {
        return this.items;
    }
}
