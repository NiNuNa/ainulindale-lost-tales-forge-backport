package com.ninuna.losttales.faction;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.achievement.ELostTalesAchievement;
import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.block.LostTalesBlockPlushie;
import com.ninuna.losttales.item.block.LostTalesItemBlockBase;
import com.ninuna.losttales.item.block.LostTalesItemBlockPlushie;
import com.ninuna.losttales.util.LostTalesPair;
import com.ninuna.losttales.util.LostTalesUtil;
import com.ninuna.losttales.world.map.waypoint.ELostTalesWaypoint;
import cpw.mods.fml.common.registry.GameRegistry;
import lotr.common.LOTRAchievement;
import lotr.common.LOTRDimension;
import lotr.common.fac.LOTRControlZone;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
import lotr.common.fac.LOTRMapRegion;
import lotr.common.world.map.LOTRWaypoint;

import java.util.EnumSet;

public enum ELostTalesFaction {
    ARNOR(
            //  Faction:
            LostTalesUtil.addFaction("ARNOR", 0xC6E5FF, LOTRDimension.DimensionRegion.WEST, EnumSet.of(LOTRFaction.FactionType.TYPE_MAN)),
            //  Active?, Approves of War Crimes?, Isolationist?:
            false, false, false,
            //  Faction Map Region:
            new LOTRMapRegion(0, 0, 250)
            ),

    LOSSOTH(
            //  Faction:
            LostTalesUtil.addFaction("LOSSOTH", 0xe8e8e8, DimensionRegion.NORTH.getRegion(), EnumSet.of(LOTRFaction.FactionType.TYPE_MAN)),
            //  Active?, Approves of War Crimes?, Isolationist?:
            true, false, false,
            //  Faction Map Region:
            new LOTRMapRegion((860), 350, 220)
    ),

    MORIA_GOBLINS(
            //  Faction:
            LostTalesUtil.addFaction("MORIA_GOBLINS", 0x201d1d, LOTRDimension.DimensionRegion.WEST, EnumSet.of(LOTRFaction.FactionType.TYPE_ORC)),
            //  Active?, Approves of War Crimes?, Isolationist?:
            true, true, false,
            //  Faction Map Region:
            new LOTRMapRegion(1155, 870, 30)
    ),

    OROCARNI(
            //  Faction:
            LostTalesUtil.addFaction("OROCARNI", 0xb03838, LOTRDimension.DimensionRegion.EAST, EnumSet.of(LOTRFaction.FactionType.TYPE_DWARF)),
            //  Active?, Approves of War Crimes?, Isolationist?:
            true, true, false,
            //  Faction Map Region:
            new LOTRMapRegion(2415, 915, 350)
    ),

    LOTHLORIEN(
            //  Faction:
            LostTalesUtil.addFaction("LOTHLORIEN", 0x8c2727, LOTRDimension.DimensionRegion.WEST, EnumSet.of(LOTRFaction.FactionType.TYPE_ELF)),
            //  Active?, Approves of War Crimes?, Isolationist?:
            false, false, false,
            //  Faction Map Region:
            new LOTRMapRegion(2415, 915, 350)
    ),

    NEUTRAL(
            //  Faction:
            LostTalesUtil.addFaction("NEUTRAL", 0x8c2727, LOTRDimension.DimensionRegion.WEST, EnumSet.of(LOTRFaction.FactionType.TYPE_FREE)),
            //  Active?, Approves of War Crimes?, Isolationist?:
            false, false, false,
            //  Faction Map Region:
            new LOTRMapRegion(2415, 915, 350)
    ),

    THARBAD(
            //  Faction:
            LostTalesUtil.addFaction("THARBAD", 0x8c2727, LOTRDimension.DimensionRegion.WEST, EnumSet.of(LOTRFaction.FactionType.TYPE_MAN)),
            //  Active?, Approves of War Crimes?, Isolationist?:
            true, false, false,
            //  Faction Map Region:
            new LOTRMapRegion(2415, 915, 350)
    ),

    MOON_ELVES(
            //  Faction:
            LostTalesUtil.addFaction("MOON_ELVES", 0x1f305c, DimensionRegion.NORTH.getRegion(), EnumSet.of(LOTRFaction.FactionType.TYPE_ELF, LOTRFaction.FactionType.TYPE_FREE)),
            //  Active?, Approves of War Crimes?, Isolationist?:
            true, false, false,
            //  Faction Map Region:
            new LOTRMapRegion(1577, 533, 35)
    ),

    SUN_ELVES(
            //  Faction:
            LostTalesUtil.addFaction("SUN_ELVES", 0x731440, LOTRDimension.DimensionRegion.EAST, EnumSet.of(LOTRFaction.FactionType.TYPE_ELF, LOTRFaction.FactionType.TYPE_FREE)),
            //  Active?, Approves of War Crimes?, Isolationist?:
            true, false, false,
            //  Faction Map Region:
            new LOTRMapRegion(2550, 1475, 225)
            ),

    BLUE_GOBLINS(
            //  Faction:
            LostTalesUtil.addFaction("BLUE_GOBLINS", 0x0d2436, LOTRDimension.DimensionRegion.WEST, EnumSet.of(LOTRFaction.FactionType.TYPE_ORC)),
            //  Active?, Approves of War Crimes?, Isolationist?:
            true, true, false,
            //  Faction Map Region:
            new LOTRMapRegion(630, 530, 80)
    );

    private final boolean isFactionActive;
    private final boolean isFactionIsolationist;
    private final boolean factionApprovesWarCrimes;
    private final LOTRMapRegion factionMapRegion;
    private final LOTRFaction faction;
    private LostTalesPair[] factionRelations;
    private LostTalesPair[] factionControlZones;

    ELostTalesFaction(LOTRFaction faction, boolean isFactionActive, boolean factionApprovesWarCrimes, boolean isFactionIsolationist, LOTRMapRegion factionMapRegion) {
        this.faction = faction;
        this.isFactionActive = isFactionActive;
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
        ELostTalesFaction.removeInactiveFactions();
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
                new LostTalesPair(ELostTalesFaction.MORIA_GOBLINS.getFaction(), LOTRFactionRelations.Relation.FRIEND),
                new LostTalesPair(ELostTalesFaction.MOON_ELVES.getFaction(), LOTRFactionRelations.Relation.ENEMY)
        });

        MOON_ELVES.setFactionRelations( new LostTalesPair[]{
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
                new LostTalesPair(ELostTalesWaypoint.SUN_ELVES.getWaypoint(), 175)
        });

        MORIA_GOBLINS.setFactionControlZones( new LostTalesPair[]{
                new LostTalesPair(LOTRWaypoint.MOUNT_CELEBDIL, 80)
        });

        BLUE_GOBLINS.setFactionControlZones( new LostTalesPair[]{
                new LostTalesPair(LOTRWaypoint.MOUNT_RERIR, 125)
        });

        LOSSOTH.setFactionControlZones( new LostTalesPair[]{
                new LostTalesPair(ELostTalesWaypoint.SUN_ELVES.getWaypoint(), 175)
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
            if (f.isFactionActive) {
                boolean categoryExists = false;
                for (LOTRAchievement.Category category : LOTRAchievement.Category.values()) {
                    if (category.codeName() == f.getFaction().codeName()) {
                        categoryExists = true;
                    }
                }

                if (categoryExists) {
                    LostTalesUtil.setFactionAchievementCategory(f.getFaction(), LOTRAchievement.Category.valueOf(f.getFaction().codeName()));
                } else {
                    LostTalesUtil.setFactionAchievementCategory(f.getFaction(), ELostTalesAchievement.Category.valueOf(f.getFaction().codeName()).getCategory());
                }
                LostTalesUtil.addFactionRank(f.getFaction(), 10.0f, "a", false).makeAchievement().makeTitle();
                LostTalesUtil.addFactionRank(f.getFaction(), 50.0f, "b", false).makeAchievement().makeTitle();
                LostTalesUtil.addFactionRank(f.getFaction(), 100.0f, "c", false).makeAchievement().makeTitle().setPledgeRank();
                LostTalesUtil.addFactionRank(f.getFaction(), 200.0f, "d", false).makeAchievement().makeTitle();
                LostTalesUtil.addFactionRank(f.getFaction(), 500.0f, "e", false).makeAchievement().makeTitle();
                LostTalesUtil.addFactionRank(f.getFaction(), 1000.0f, "f", false).makeAchievement().makeTitle();
                LostTalesUtil.addFactionRank(f.getFaction(), 1500.0f, "g", false).makeAchievement().makeTitle();
                LostTalesUtil.addFactionRank(f.getFaction(), 3000.0f, "h", false).makeAchievement().makeTitle();
            }
        }
    }

    public static void removeInactiveFactions() {
        for (ELostTalesFaction f : ELostTalesFaction.values()) {
            if (!f.isFactionActive()) {
                f.getFaction().allowPlayer = false;
                f.getFaction().hasFixedAlignment = true;
                f.getFaction().fixedAlignment = 0;

                if(f.getFaction().factionDimension != null) f.getFaction().factionDimension.factionList.remove(f.getFaction());
                if(f.getFaction().factionRegion != null) f.getFaction().factionRegion.factionList.remove(f.getFaction());
                f.getFaction().factionDimension = null;
                f.getFaction().factionRegion = null;
                f.getFaction().factionMapInfo = null;

                LostTalesUtil.clearControlZones(f.getFaction());
            }
        }
    }

    public LOTRFaction getFaction() {
        return faction;
    }

    public LOTRMapRegion getFactionMapRegion() {
        return factionMapRegion;
    }

    public boolean isFactionActive() {
        return isFactionActive;
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