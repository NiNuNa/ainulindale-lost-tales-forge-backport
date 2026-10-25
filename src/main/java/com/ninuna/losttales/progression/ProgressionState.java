package com.ninuna.losttales.progression;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything a character has grown: its skills, its character level, the
 * perks it took, its deeds and the title it wears, how often it reset a
 * tree at a statue, and the LOTR waypoints it reached, each of which Lore
 * counts once (PROG-01). One character's own (CHAR-28): a switch swaps the
 * whole of it. Bounded on every collection; a change past a bound is
 * refused.
 */
public final class ProgressionState {
    public static final int MAX_SKILLS = 64;
    public static final int MAX_PERKS = 1024;
    public static final int MAX_DEEDS = 2048;
    public static final int MAX_DEED_COUNTERS = 2048;
    public static final int MAX_ID_CHARACTERS = 128;
    public static final int MAX_RANK = 16;
    public static final int MAX_RESETS = 1000;
    /** LOTR holds under 300 waypoints; the bound leaves room for a server's mods. */
    public static final int MAX_WAYPOINTS = 1024;

    private final Map<String, SkillStanding> skills =
            new LinkedHashMap<String, SkillStanding>();
    private int characterLevel = 1;
    private long characterInto;
    /** Perk ranks by {@link #perkKey}. */
    private final Map<String, Integer> perks = new LinkedHashMap<String, Integer>();
    private final Set<String> deeds = new LinkedHashSet<String>();
    /** How far each unfinished counting deed stands. */
    private final Map<String, Long> deedCounters = new LinkedHashMap<String, Long>();
    private String title = "";
    private int resets;
    /** The LOTR waypoints reached, as deed files name them ({@code lotr:BREE}). */
    private final Set<String> waypoints = new LinkedHashSet<String>();

    public static String perkKey(String skillId, String perkId) {
        return skillId + "/" + perkId;
    }

    public static boolean fitsId(String id) {
        return id != null && id.length() > 0 && id.length() <= MAX_ID_CHARACTERS;
    }

    public ProgressionState copy() {
        ProgressionState copy = new ProgressionState();
        copy.skills.putAll(this.skills);
        copy.characterLevel = this.characterLevel;
        copy.characterInto = this.characterInto;
        copy.perks.putAll(this.perks);
        copy.deeds.addAll(this.deeds);
        copy.deedCounters.putAll(this.deedCounters);
        copy.title = this.title;
        copy.resets = this.resets;
        copy.waypoints.addAll(this.waypoints);
        return copy;
    }

    /** The skill's standing; level 1 with nothing gathered for a skill not yet used. */
    public SkillStanding skill(String skillId) {
        SkillStanding standing = this.skills.get(skillId);
        return standing == null ? SkillStanding.start(SkillCurve.FIRST_LEVEL) : standing;
    }

    public Map<String, SkillStanding> skills() {
        return Collections.unmodifiableMap(this.skills);
    }

    public void setSkill(String skillId, SkillStanding standing) {
        if (!fitsId(skillId) || standing == null) {
            throw new IllegalArgumentException("a skill needs an id and a standing");
        }
        if (!this.skills.containsKey(skillId) && this.skills.size() >= MAX_SKILLS) {
            throw new IllegalStateException("a character holds at most "
                    + MAX_SKILLS + " skills");
        }
        this.skills.put(skillId, standing);
    }

    public int getCharacterLevel() {
        return this.characterLevel;
    }

    public long getCharacterInto() {
        return this.characterInto;
    }

    public void setCharacter(int level, long into) {
        if (level < 1 || level > CharacterLevelRule.MAX_LEVEL || into < 0L) {
            throw new IllegalArgumentException("a character level runs from 1 to "
                    + CharacterLevelRule.MAX_LEVEL);
        }
        this.characterLevel = level;
        this.characterInto = into;
    }

    public int perkRank(String skillId, String perkId) {
        Integer rank = this.perks.get(perkKey(skillId, perkId));
        return rank == null ? 0 : rank.intValue();
    }

    /** Perk ranks by {@link #perkKey}. */
    public Map<String, Integer> perks() {
        return Collections.unmodifiableMap(this.perks);
    }

    public void setPerkRank(String skillId, String perkId, int rank) {
        if (!fitsId(skillId) || !fitsId(perkId) || rank < 0 || rank > MAX_RANK) {
            throw new IllegalArgumentException("a perk needs a skill, an id and a rank");
        }
        String key = perkKey(skillId, perkId);
        if (rank == 0) {
            this.perks.remove(key);
            return;
        }
        if (!this.perks.containsKey(key) && this.perks.size() >= MAX_PERKS) {
            throw new IllegalStateException("a character holds at most "
                    + MAX_PERKS + " perks");
        }
        this.perks.put(key, Integer.valueOf(rank));
    }

    /** Every rank taken in one skill's tree, which is what a reset gives back. */
    public int ranksIn(String skillId) {
        int ranks = 0;
        String prefix = skillId + "/";
        for (Map.Entry<String, Integer> perk : this.perks.entrySet()) {
            if (perk.getKey().startsWith(prefix)) {
                ranks += perk.getValue().intValue();
            }
        }
        return ranks;
    }

    /** Takes back every perk of one skill; answers how many ranks went. */
    public int clearPerks(String skillId) {
        int ranks = ranksIn(skillId);
        String prefix = skillId + "/";
        Iterator<String> keys = this.perks.keySet().iterator();
        while (keys.hasNext()) {
            if (keys.next().startsWith(prefix)) {
                keys.remove();
            }
        }
        return ranks;
    }

    /** Every rank taken in every tree; each rank cost one point. */
    public int spentPoints() {
        int spent = 0;
        for (Integer rank : this.perks.values()) {
            spent += rank.intValue();
        }
        return spent;
    }

    public boolean hasDeed(String deedId) {
        return this.deeds.contains(deedId);
    }

    public Set<String> deeds() {
        return Collections.unmodifiableSet(this.deeds);
    }

    /** Marks a deed done; answers whether it was new. */
    public boolean addDeed(String deedId) {
        if (!fitsId(deedId)) {
            throw new IllegalArgumentException("a deed needs an id");
        }
        if (this.deeds.contains(deedId)) {
            return false;
        }
        if (this.deeds.size() >= MAX_DEEDS) {
            throw new IllegalStateException("a character holds at most "
                    + MAX_DEEDS + " deeds");
        }
        this.deedCounters.remove(deedId);
        this.deeds.add(deedId);
        return true;
    }

    public long deedCounter(String deedId) {
        Long counter = this.deedCounters.get(deedId);
        return counter == null ? 0L : counter.longValue();
    }

    public Map<String, Long> deedCounters() {
        return Collections.unmodifiableMap(this.deedCounters);
    }

    public void setDeedCounter(String deedId, long count) {
        if (!fitsId(deedId) || count < 0L) {
            throw new IllegalArgumentException("a deed counter needs an id and a count");
        }
        if (count == 0L) {
            this.deedCounters.remove(deedId);
            return;
        }
        if (!this.deedCounters.containsKey(deedId)
                && this.deedCounters.size() >= MAX_DEED_COUNTERS) {
            throw new IllegalStateException("a character counts at most "
                    + MAX_DEED_COUNTERS + " deeds at once");
        }
        this.deedCounters.put(deedId, Long.valueOf(count));
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        if (title == null || title.length() > MAX_ID_CHARACTERS) {
            throw new IllegalArgumentException("a title id is at most "
                    + MAX_ID_CHARACTERS + " characters");
        }
        this.title = title;
    }

    public int getResets() {
        return this.resets;
    }

    public void setResets(int resets) {
        if (resets < 0 || resets > MAX_RESETS) {
            throw new IllegalArgumentException("resets run from 0 to " + MAX_RESETS);
        }
        this.resets = resets;
    }

    public boolean hasWaypoint(String waypointId) {
        return this.waypoints.contains(waypointId);
    }

    public Set<String> waypoints() {
        return Collections.unmodifiableSet(this.waypoints);
    }

    /** Marks a LOTR waypoint reached; answers whether it was new. */
    public boolean addWaypoint(String waypointId) {
        if (!fitsId(waypointId)) {
            throw new IllegalArgumentException("a waypoint needs an id");
        }
        if (this.waypoints.contains(waypointId)) {
            return false;
        }
        if (this.waypoints.size() >= MAX_WAYPOINTS) {
            throw new IllegalStateException("a character reaches at most "
                    + MAX_WAYPOINTS + " waypoints");
        }
        this.waypoints.add(waypointId);
        return true;
    }
}
