package com.ninuna.losttales.faction;

import com.ninuna.losttales.achievement.ELostTalesAchievement;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.util.LostTalesLangFile;
import com.ninuna.losttales.util.LostTalesPair;
import com.ninuna.losttales.util.LostTalesUtil;
import com.ninuna.losttales.world.map.waypoint.ELostTalesWaypoint;
import java.util.EnumSet;
import java.util.Map;
import lotr.common.LOTRAchievement;
import lotr.common.LOTRDimension;
import lotr.common.fac.LOTRControlZone;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
import lotr.common.fac.LOTRMapRegion;
import lotr.common.world.map.LOTRWaypoint;

public enum ELostTalesFaction {
    LOSSOTH(
            //  Faction:
            LostTalesUtil.addFaction("LOSSOTH", LostTalesColors.rgb(LostTalesColors.IVORY), DimensionRegion.NORTH.getRegion(), EnumSet.of(LOTRFaction.FactionType.TYPE_MAN)),
            //  Approves of War Crimes?, Isolationist?:
            false, false,
            //  Faction Map Region:
            new LOTRMapRegion((860), 350, 220)
    ),

    MORIA_GOBLINS(
            //  Faction:
            LostTalesUtil.addFaction("MORIA_GOBLINS", LostTalesColors.rgb(LostTalesColors.PLUM_BLACK), LOTRDimension.DimensionRegion.WEST, EnumSet.of(LOTRFaction.FactionType.TYPE_ORC)),
            //  Approves of War Crimes?, Isolationist?:
            true, false,
            //  Faction Map Region:
            new LOTRMapRegion(1155, 870, 30)
    ),

    OROCARNI(
            //  Faction:
            LostTalesUtil.addFaction("OROCARNI", LostTalesColors.rgb(LostTalesColors.CRIMSON), LOTRDimension.DimensionRegion.EAST, EnumSet.of(LOTRFaction.FactionType.TYPE_DWARF)),
            //  Approves of War Crimes?, Isolationist?:
            true, false,
            //  Faction Map Region:
            new LOTRMapRegion(2415, 915, 350)
    ),

    THARBAD(
            //  Faction:
            LostTalesUtil.addFaction("THARBAD", LostTalesColors.rgb(LostTalesColors.RUST), LOTRDimension.DimensionRegion.WEST, EnumSet.of(LOTRFaction.FactionType.TYPE_MAN)),
            //  Approves of War Crimes?, Isolationist?:
            false, false,
            //  Faction Map Region: round LOTR's Tharbad waypoint.
            new LOTRMapRegion(979, 878, 50)
    ),

    MOON_ELVES(
            //  Faction:
            LostTalesUtil.addFaction("MOON_ELVES", LostTalesColors.rgb(LostTalesColors.INDIGO), DimensionRegion.NORTH.getRegion(), EnumSet.of(LOTRFaction.FactionType.TYPE_ELF, LOTRFaction.FactionType.TYPE_FREE)),
            //  Approves of War Crimes?, Isolationist?:
            false, false,
            //  Faction Map Region:
            new LOTRMapRegion(1577, 533, 35)
    ),

    SUN_ELVES(
            //  Faction:
            LostTalesUtil.addFaction("SUN_ELVES", LostTalesColors.rgb(LostTalesColors.WINE), LOTRDimension.DimensionRegion.EAST, EnumSet.of(LOTRFaction.FactionType.TYPE_ELF, LOTRFaction.FactionType.TYPE_FREE)),
            //  Approves of War Crimes?, Isolationist?:
            false, false,
            //  Faction Map Region:
            new LOTRMapRegion(2550, 1475, 225)
            ),

    ODANE(
            //  Faction:
            LostTalesUtil.addFaction("ODANE", LostTalesColors.rgb(LostTalesColors.CORAL), LOTRDimension.DimensionRegion.EAST, EnumSet.of(LOTRFaction.FactionType.TYPE_MAN)),
            //  Approves of War Crimes?, Isolationist?:
            false, false,
            //  Faction Map Region:
            new LOTRMapRegion(3128, 790, 150)
    ),

    BLUE_GOBLINS(
            //  Faction:
            LostTalesUtil.addFaction("BLUE_GOBLINS", LostTalesColors.rgb(LostTalesColors.HARBOR_BLUE), LOTRDimension.DimensionRegion.WEST, EnumSet.of(LOTRFaction.FactionType.TYPE_ORC)),
            //  Approves of War Crimes?, Isolationist?:
            true, false,
            //  Faction Map Region:
            new LOTRMapRegion(630, 530, 80)
    );

    private final boolean isFactionIsolationist;
    private final boolean factionApprovesWarCrimes;
    private final LOTRMapRegion factionMapRegion;
    private final LOTRFaction faction;
    private LostTalesPair[] factionRelations;
    private LostTalesPair[] factionControlZones;

    ELostTalesFaction(LOTRFaction faction, boolean factionApprovesWarCrimes, boolean isFactionIsolationist, LOTRMapRegion factionMapRegion) {
        this.faction = faction;
        this.factionApprovesWarCrimes = factionApprovesWarCrimes;
        this.isFactionIsolationist = isFactionIsolationist;
        this.factionMapRegion = factionMapRegion;
    }

    public static void initAndRegisterFactions() {
        for (ELostTalesFaction f : ELostTalesFaction.values()) {
            //Faction Info & Details
            f.getFaction().factionMapInfo = f.getFactionMapRegion();
            f.getFaction().approvesWarCrimes = f.doesFactionApproveWarCrimes();
            f.getFaction().isolationist = f.isFactionIsolationist();
        }
        ELostTalesFaction.setUpFactionRelations();
        ELostTalesFaction.setUpFactionControlZones();
        ELostTalesFaction.setUpFactionRanks();
    }

    public static void setUpFactionRelations() {
        BLUE_GOBLINS.setFactionRelations( new LostTalesPair[]{
                new LostTalesPair(LOTRFaction.ANGMAR, LOTRFactionRelations.Relation.ALLY),
                new LostTalesPair(LOTRFaction.MORDOR, LOTRFactionRelations.Relation.FRIEND),
                new LostTalesPair(LOTRFaction.BLUE_MOUNTAINS, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.HIGH_ELF, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.RANGER_NORTH, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.HOBBIT, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.BREE, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.GONDOR, LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(LOTRFaction.DUNLAND, LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(LOTRFaction.WOOD_ELF, LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(LOTRFaction.DALE, LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(LOTRFaction.DURINS_FOLK, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.LOTHLORIEN, LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(LOTRFaction.FANGORN, LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(LOTRFaction.ROHAN, LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(LOTRFaction.DORWINION, LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(LOTRFaction.GUNDABAD, LOTRFactionRelations.Relation.FRIEND),
                new LostTalesPair(ELostTalesFaction.MORIA_GOBLINS.getFaction(), LOTRFactionRelations.Relation.FRIEND),
                new LostTalesPair(ELostTalesFaction.MOON_ELVES.getFaction(), LOTRFactionRelations.Relation.ENEMY)
        });

        // The mortal enemies are the same list Mordor carries in
        // LOTRFaction.initAllProperties(): every good-aligned faction.
        MORIA_GOBLINS.setFactionRelations( new LostTalesPair[]{
                new LostTalesPair(LOTRFaction.GUNDABAD, LOTRFactionRelations.Relation.FRIEND),
                new LostTalesPair(LOTRFaction.HOBBIT, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.BREE, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.RANGER_NORTH, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.BLUE_MOUNTAINS, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.HIGH_ELF, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.WOOD_ELF, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.DALE, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.DURINS_FOLK, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.LOTHLORIEN, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.FANGORN, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.ROHAN, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.GONDOR, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.DORWINION, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.TAURETHRIM, LOTRFactionRelations.Relation.MORTAL_ENEMY)
        });

        LOSSOTH.setFactionRelations( new LostTalesPair[]{
                new LostTalesPair(LOTRFaction.RANGER_NORTH, LOTRFactionRelations.Relation.ALLY),
                new LostTalesPair(LOTRFaction.BREE, LOTRFactionRelations.Relation.FRIEND),
                new LostTalesPair(ELostTalesFaction.THARBAD.getFaction(), LOTRFactionRelations.Relation.FRIEND),
                new LostTalesPair(ELostTalesFaction.BLUE_GOBLINS.getFaction(), LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.ANGMAR, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.GUNDABAD, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.MORDOR, LOTRFactionRelations.Relation.ENEMY)
        });

        THARBAD.setFactionRelations( new LostTalesPair[]{
                new LostTalesPair(LOTRFaction.HOBBIT, LOTRFactionRelations.Relation.ALLY),
                new LostTalesPair(LOTRFaction.BREE, LOTRFactionRelations.Relation.ALLY),
                new LostTalesPair(LOTRFaction.RANGER_NORTH, LOTRFactionRelations.Relation.ALLY),
                new LostTalesPair(LOTRFaction.BLUE_MOUNTAINS, LOTRFactionRelations.Relation.FRIEND),
                new LostTalesPair(LOTRFaction.HIGH_ELF, LOTRFactionRelations.Relation.FRIEND),
                new LostTalesPair(ELostTalesFaction.MORIA_GOBLINS.getFaction(), LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(ELostTalesFaction.BLUE_GOBLINS.getFaction(), LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.GUNDABAD, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.ANGMAR, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.DUNLAND, LOTRFactionRelations.Relation.ENEMY)
        });

        MOON_ELVES.setFactionRelations( new LostTalesPair[]{
                new LostTalesPair(ELostTalesFaction.SUN_ELVES.getFaction(), LOTRFactionRelations.Relation.MORTAL_ENEMY)
        });

        // Mordor's mortal-enemy list again, except Durin's Folk, who are only an enemy.
        OROCARNI.setFactionRelations( new LostTalesPair[]{
                new LostTalesPair(LOTRFaction.MORDOR, LOTRFactionRelations.Relation.ALLY),
                new LostTalesPair(LOTRFaction.RHUDEL, LOTRFactionRelations.Relation.ALLY),
                new LostTalesPair(LOTRFaction.DURINS_FOLK, LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(LOTRFaction.HOBBIT, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.BREE, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.RANGER_NORTH, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.BLUE_MOUNTAINS, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.HIGH_ELF, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.WOOD_ELF, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.DALE, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.LOTHLORIEN, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.FANGORN, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.ROHAN, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.GONDOR, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.DORWINION, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.TAURETHRIM, LOTRFactionRelations.Relation.MORTAL_ENEMY)
        });

        ODANE.setFactionRelations( new LostTalesPair[]{
                new LostTalesPair(ELostTalesFaction.OROCARNI.getFaction(), LOTRFactionRelations.Relation.FRIEND),
                new LostTalesPair(LOTRFaction.MORDOR, LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(LOTRFaction.GUNDABAD, LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(LOTRFaction.ANGMAR, LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(LOTRFaction.DOL_GULDUR, LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(LOTRFaction.ISENGARD, LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(ELostTalesFaction.MORIA_GOBLINS.getFaction(), LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(ELostTalesFaction.BLUE_GOBLINS.getFaction(), LOTRFactionRelations.Relation.ENEMY),
                new LostTalesPair(LOTRFaction.HIGH_ELF, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.WOOD_ELF, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(LOTRFaction.LOTHLORIEN, LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(ELostTalesFaction.MOON_ELVES.getFaction(), LOTRFactionRelations.Relation.MORTAL_ENEMY),
                new LostTalesPair(ELostTalesFaction.SUN_ELVES.getFaction(), LOTRFactionRelations.Relation.MORTAL_ENEMY)
        });

        for (ELostTalesFaction f : ELostTalesFaction.values()) {
            if (f.getFactionRelations() != null) {
                for (LostTalesPair factionRelations : f.getFactionRelations()) {
                    LOTRFactionRelations.setDefaultRelations(f.getFaction(), factionRelations.getKeyAsFaction(), factionRelations.getValueAsRelation());
                }
            }
        }
    }

    public static void setUpFactionControlZones() {
        MOON_ELVES.setFactionControlZones( new LostTalesPair[]{
                new LostTalesPair(ELostTalesWaypoint.MOON_ELVES.getWaypoint(), 24),
                new LostTalesPair(ELostTalesWaypoint.MOON_ELVES_2.getWaypoint(), 22)
        });

        SUN_ELVES.setFactionControlZones( new LostTalesPair[]{
                new LostTalesPair(ELostTalesWaypoint.SUN_ELVES.getWaypoint(), 285)
        });

        OROCARNI.setFactionControlZones( new LostTalesPair[]{
                new LostTalesPair(ELostTalesWaypoint.OROCARNI.getWaypoint(), 175)
        });

        MORIA_GOBLINS.setFactionControlZones( new LostTalesPair[]{
                new LostTalesPair(LOTRWaypoint.MOUNT_CELEBDIL, 80)
        });

        BLUE_GOBLINS.setFactionControlZones( new LostTalesPair[]{
                new LostTalesPair(LOTRWaypoint.MOUNT_RERIR, 125)
        });

        LOSSOTH.setFactionControlZones( new LostTalesPair[]{
                new LostTalesPair(LOTRWaypoint.CAPE_OF_FOROCHEL, 175)
        });

        // The Rangers of the North hold a zone of the same size here.
        THARBAD.setFactionControlZones( new LostTalesPair[]{
                new LostTalesPair(LOTRWaypoint.THARBAD, 50)
        });

        ODANE.setFactionControlZones( new LostTalesPair[]{
                new LostTalesPair(ELostTalesWaypoint.ODANE.getWaypoint(), 70),
                new LostTalesPair(ELostTalesWaypoint.ODANE_MOUNTAINS.getWaypoint(), 45),
                new LostTalesPair(ELostTalesWaypoint.ODANE_PORT.getWaypoint(), 45),
                new LostTalesPair(ELostTalesWaypoint.ODANE_EASTWATCH.getWaypoint(), 45)
        });

        for (ELostTalesFaction f : ELostTalesFaction.values()) {
            if (f.getFactionControlZones() != null) {
                for (LostTalesPair factionControlZones : f.getFactionControlZones()) {
                    LostTalesUtil.addControlZone(f.getFaction(), new LOTRControlZone(factionControlZones.getKeyAsWaypoint(), factionControlZones.getValueAsInteger()));
                }
            }
        }
    }

    public static void setUpFactionRanks() {
        for (ELostTalesFaction f : ELostTalesFaction.values()) {
            boolean categoryExists = false;
            for (LOTRAchievement.Category category : LOTRAchievement.Category.values()) {
                if (category.codeName().equals(
                        f.getFaction().codeName())) {
                    categoryExists = true;
                }
            }

            if (categoryExists) {
                LostTalesUtil.setFactionAchievementCategory(f.getFaction(), LOTRAchievement.Category.valueOf(f.getFaction().codeName()));
            } else {
                LostTalesUtil.setFactionAchievementCategory(f.getFaction(), ELostTalesAchievement.Category.valueOf(f.getFaction().codeName()).getCategory());
            }
            LostTalesUtil.addFactionRank(f.getFaction(), 10.0f, "a", f.hasFeminineRank("a")).makeAchievement().makeTitle();
            LostTalesUtil.addFactionRank(f.getFaction(), 50.0f, "b", f.hasFeminineRank("b")).makeAchievement().makeTitle();
            LostTalesUtil.addFactionRank(f.getFaction(), 100.0f, "c", f.hasFeminineRank("c")).makeAchievement().makeTitle().setPledgeRank();
            LostTalesUtil.addFactionRank(f.getFaction(), 200.0f, "d", f.hasFeminineRank("d")).makeAchievement().makeTitle();
            LostTalesUtil.addFactionRank(f.getFaction(), 500.0f, "e", f.hasFeminineRank("e")).makeAchievement().makeTitle();
            LostTalesUtil.addFactionRank(f.getFaction(), 1000.0f, "f", f.hasFeminineRank("f")).makeAchievement().makeTitle();
            LostTalesUtil.addFactionRank(f.getFaction(), 1500.0f, "g", f.hasFeminineRank("g")).makeAchievement().makeTitle();
            LostTalesUtil.addFactionRank(f.getFaction(), 3000.0f, "h", f.hasFeminineRank("h")).makeAchievement().makeTitle();
        }
    }

    /**
     * A rank has a feminine form wherever the mod's English lang file has
     * its {@code _fm} line (FACTION-03). The English file is the one every
     * language's keys follow, so the answer is the same on every side and
     * in every language.
     */
    private boolean hasFeminineRank(String rankName) {
        return hasFeminineRank(LostTalesLangFile.english(), getFaction().codeName(), rankName);
    }

    /** True when {@code lines} hold the feminine title of the faction's rank. */
    static boolean hasFeminineRank(Map<String, String> lines, String factionCode,
                                   String rankName) {
        return lines.containsKey(rankKey(factionCode, rankName) + "_fm");
    }

    /** LOTR's lang key of a rank's title: {@code lotr.faction.<code>.rank.<name>}. */
    static String rankKey(String factionCode, String rankName) {
        return "lotr.faction." + factionCode + ".rank." + rankName;
    }

    public LOTRFaction getFaction() {
        return faction;
    }

    public LOTRMapRegion getFactionMapRegion() {
        return factionMapRegion;
    }

    public boolean doesFactionApproveWarCrimes() {
        return factionApprovesWarCrimes;
    }

    public boolean isFactionIsolationist() {
        return isFactionIsolationist;
    }

    public LostTalesPair[] getFactionControlZones() {
        return factionControlZones;
    }

    public void setFactionControlZones(LostTalesPair[] factionControlZones) {
        this.factionControlZones = factionControlZones;
    }

    public LostTalesPair[] getFactionRelations() {
        return factionRelations;
    }

    public void setFactionRelations(LostTalesPair[] factionRelations) {
        this.factionRelations = factionRelations;
    }

    public enum DimensionRegion {
        NORTH(LostTalesUtil.addDimensionRegion("NORTH", "north"));

        private final LOTRDimension.DimensionRegion region;

        DimensionRegion(LOTRDimension.DimensionRegion region) {
            this.region = region;
            this.setDimension(this.region);
        }

        public LOTRDimension.DimensionRegion getRegion() {
            return region;
        }

        private void setDimension(LOTRDimension.DimensionRegion region) {
            region.setDimension(LOTRDimension.MIDDLE_EARTH);
            LOTRDimension.MIDDLE_EARTH.dimensionRegions.add(region);
        }
    }
}
