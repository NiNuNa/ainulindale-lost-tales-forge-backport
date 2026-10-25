package com.ninuna.losttales.armour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A named set as its file writes it (CONTENT-07): the items each of its
 * four slots takes, and the bonus all four give. A relic set (CONTENT-11)
 * takes only pieces carrying its relic mark, and names each piece and
 * gives it a lore line. A bundled set's words are lang lines named by its
 * id; a server's own file may write them itself.
 */
public final class NamedSet {
    private final String id;
    private final boolean relic;
    private final Map<ArmourSlot, List<String>> slots;
    private final List<SetBonus> bonuses;
    private final String name;
    private final Map<ArmourSlot, String> pieceNames;
    private final Map<ArmourSlot, String> pieceLore;

    public NamedSet(String id, boolean relic, Map<ArmourSlot, List<String>> slots,
                    List<SetBonus> bonuses, String name, Map<ArmourSlot, String> pieceNames,
                    Map<ArmourSlot, String> pieceLore) {
        this.id = id == null ? "" : id;
        this.relic = relic;
        Map<ArmourSlot, List<String>> slotCopy =
                new EnumMap<ArmourSlot, List<String>>(ArmourSlot.class);
        for (ArmourSlot slot : ArmourSlot.values()) {
            List<String> items = slots == null ? null : slots.get(slot);
            slotCopy.put(slot, Collections.unmodifiableList(items == null
                    ? new ArrayList<String>() : new ArrayList<String>(items)));
        }
        this.slots = Collections.unmodifiableMap(slotCopy);
        this.bonuses = Collections.unmodifiableList(bonuses == null
                ? new ArrayList<SetBonus>() : new ArrayList<SetBonus>(bonuses));
        this.name = name == null ? "" : name;
        this.pieceNames = words(pieceNames);
        this.pieceLore = words(pieceLore);
    }

    private static Map<ArmourSlot, String> words(Map<ArmourSlot, String> written) {
        Map<ArmourSlot, String> copy = new EnumMap<ArmourSlot, String>(ArmourSlot.class);
        if (written != null) {
            copy.putAll(written);
        }
        return Collections.unmodifiableMap(copy);
    }

    public String getId() {
        return this.id;
    }

    public boolean isRelic() {
        return this.relic;
    }

    /** The items a slot takes, by registry name; empty for none. */
    public List<String> items(ArmourSlot slot) {
        return this.slots.get(slot);
    }

    public Map<ArmourSlot, List<String>> getSlots() {
        return this.slots;
    }

    public List<SetBonus> getBonuses() {
        return this.bonuses;
    }

    /** The file's own name for the set, standing in where no lang line names it. */
    public String getName() {
        return this.name;
    }

    /** A relic piece's own name, as a server's file writes it; empty for none. */
    public String pieceName(ArmourSlot slot) {
        String written = this.pieceNames.get(slot);
        return written == null ? "" : written;
    }

    /** A relic piece's lore line, as a server's file writes it; empty for none. */
    public String pieceLore(ArmourSlot slot) {
        String written = this.pieceLore.get(slot);
        return written == null ? "" : written;
    }

    public Map<ArmourSlot, String> getPieceNames() {
        return this.pieceNames;
    }

    public Map<ArmourSlot, String> getPieceLore() {
        return this.pieceLore;
    }
}
