package com.ninuna.losttales.armour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a character wears, as the armour rules read it: each slot's piece
 * and its weight (CONTENT-06), whether the four make a weight set
 * (PROG-18), how far each named set is complete (CONTENT-07, CONTENT-11),
 * and what the complete ones' bonuses add up to. Plain data, so the rules
 * are tested without a game.
 */
public final class WornArmour {
    public static final WornArmour NONE = of(new EnumMap<ArmourSlot, Piece>(ArmourSlot.class),
            ArmourCatalogue.EMPTY);

    private final Map<ArmourSlot, Piece> pieces;
    private final Map<ArmourSlot, ArmourWeight> weights;
    private final Map<NamedSet, Integer> setCounts;
    private final String key;

    private WornArmour(Map<ArmourSlot, Piece> pieces, Map<ArmourSlot, ArmourWeight> weights,
                       Map<NamedSet, Integer> setCounts, String key) {
        this.pieces = pieces;
        this.weights = weights;
        this.setCounts = setCounts;
        this.key = key;
    }

    /** One worn piece: its item, its armour material, its relic mark and its armour points. */
    public static final class Piece {
        final String itemId;
        final String material;
        final String relicId;
        final int points;

        public Piece(String itemId, String material, String relicId, int points) {
            this.itemId = itemId == null ? "" : itemId;
            this.material = material == null ? "" : material;
            this.relicId = relicId == null ? "" : relicId;
            this.points = Math.max(0, points);
        }

        public String getItemId() {
            return this.itemId;
        }

        public String getRelicId() {
            return this.relicId;
        }

        public int getPoints() {
            return this.points;
        }
    }

    /** What these pieces make under a catalogue; a slot not in the map is empty. */
    public static WornArmour of(Map<ArmourSlot, Piece> worn, ArmourCatalogue catalogue) {
        Map<ArmourSlot, Piece> pieces = new EnumMap<ArmourSlot, Piece>(ArmourSlot.class);
        Map<ArmourSlot, ArmourWeight> weights =
                new EnumMap<ArmourSlot, ArmourWeight>(ArmourSlot.class);
        Map<NamedSet, Integer> counts = new LinkedHashMap<NamedSet, Integer>();
        StringBuilder key = new StringBuilder()
                .append(System.identityHashCode(catalogue)).append('|');
        for (ArmourSlot slot : ArmourSlot.values()) {
            Piece piece = worn == null ? null : worn.get(slot);
            if (piece == null) {
                weights.put(slot, ArmourWeight.NEITHER);
                key.append('|');
                continue;
            }
            pieces.put(slot, piece);
            weights.put(slot, catalogue.weightOf(piece.itemId, piece.material));
            for (NamedSet set : catalogue.setsOf(slot, piece.itemId, piece.relicId)) {
                Integer before = counts.get(set);
                counts.put(set, Integer.valueOf(before == null ? 1 : before.intValue() + 1));
            }
            key.append(piece.itemId).append('#').append(piece.relicId).append('#')
                    .append(piece.points).append('|');
        }
        return new WornArmour(pieces, weights, counts, key.toString());
    }

    /** The piece in a slot; null for an empty one. */
    public Piece piece(ArmourSlot slot) {
        return this.pieces.get(slot);
    }

    /** What the piece in a slot weighs; neither for an empty one. */
    public ArmourWeight weight(ArmourSlot slot) {
        return this.weights.get(slot);
    }

    /** How many pieces of a weight are worn. */
    public int count(ArmourWeight weight) {
        int count = 0;
        for (ArmourWeight worn : this.weights.values()) {
            if (worn == weight) {
                count++;
            }
        }
        return count;
    }

    /** The armour points the pieces of a weight give. */
    public int points(ArmourWeight weight) {
        int points = 0;
        for (Map.Entry<ArmourSlot, Piece> entry : this.pieces.entrySet()) {
            if (this.weights.get(entry.getKey()) == weight) {
                points += entry.getValue().points;
            }
        }
        return points;
    }

    /** The armour points every piece gives, of any weight. */
    public int totalPoints() {
        int points = 0;
        for (Piece piece : this.pieces.values()) {
            points += piece.points;
        }
        return points;
    }

    /** Light or heavy where all four pieces are of that weight; null otherwise. */
    public ArmourWeight weightSet() {
        for (ArmourWeight weight : new ArmourWeight[] { ArmourWeight.LIGHT, ArmourWeight.HEAVY }) {
            if (count(weight) == ArmourSlot.values().length) {
                return weight;
            }
        }
        return null;
    }

    /** How many worn pieces count for a named set. */
    public int count(NamedSet set) {
        Integer count = this.setCounts.get(set);
        return count == null ? 0 : count.intValue();
    }

    /** Every named set at least one worn piece counts for, in the catalogue's order. */
    public List<NamedSet> sets() {
        return Collections.unmodifiableList(new ArrayList<NamedSet>(this.setCounts.keySet()));
    }

    /** The named sets whose four pieces are worn. */
    public List<NamedSet> completeSets() {
        List<NamedSet> complete = new ArrayList<NamedSet>();
        for (Map.Entry<NamedSet, Integer> entry : this.setCounts.entrySet()) {
            if (entry.getValue().intValue() >= ArmourSlot.values().length) {
                complete.add(entry.getKey());
            }
        }
        return complete;
    }

    /** Whether the four pieces of any named set are worn (Matching Set, PROG-17). */
    public boolean hasCompleteSet() {
        return !completeSets().isEmpty();
    }

    /**
     * What the complete sets' bonuses add of an effect that adds up:
     * speed and the knockback chance.
     */
    public double bonus(SetEffect effect) {
        double total = 0.0D;
        for (NamedSet set : completeSets()) {
            for (SetBonus bonus : set.getBonuses()) {
                if (bonus.getEffect() == effect) {
                    total += bonus.getAmount();
                }
            }
        }
        return total;
    }

    /**
     * What is left of a blow, a fall or a poison after every complete
     * set's share of an effect, each taken off what the one before left
     * (PROG-19): 1 for no such bonus.
     */
    public double left(SetEffect effect) {
        double left = 1.0D;
        for (NamedSet set : completeSets()) {
            for (SetBonus bonus : set.getBonuses()) {
                if (bonus.getEffect() == effect) {
                    left *= 1.0D - Math.min(1.0D, bonus.getAmount());
                }
            }
        }
        return left;
    }

    /** The share more experience the complete sets give a skill. */
    public double experience(String skillId) {
        double total = 0.0D;
        for (NamedSet set : completeSets()) {
            for (SetBonus bonus : set.getBonuses()) {
                if (bonus.getEffect() == SetEffect.EXPERIENCE
                        && bonus.getSkill().equals(skillId)) {
                    total += bonus.getAmount();
                }
            }
        }
        return total;
    }

    /** How much the heavy pieces slow, before Conditioning (CONTENT-06). */
    public double slowdown(ArmourSettings settings) {
        double slow = 0.0D;
        for (ArmourSlot slot : ArmourSlot.values()) {
            if (this.weights.get(slot) == ArmourWeight.HEAVY) {
                slow += settings.slowOf(slot);
            }
        }
        return Math.min(1.0D, slow);
    }

    /**
     * The worn pieces in one line, equal for the same pieces under the
     * same catalogue, so what is made of them can be kept until they change.
     */
    public String key() {
        return this.key;
    }
}
