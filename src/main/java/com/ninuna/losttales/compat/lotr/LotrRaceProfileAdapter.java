package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.util.LostTalesIdentifiers;
import cpw.mods.fml.common.FMLLog;
import lotr.common.entity.npc.LOTREntityBreeMan;
import lotr.common.entity.npc.LOTREntityDwarf;
import lotr.common.entity.npc.LOTREntityHighElf;
import lotr.common.entity.npc.LOTREntityHobbit;
import lotr.common.entity.npc.LOTREntityMordorOrc;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTREntityUrukHai;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a race's health, speed and attack from the LOTR NPC its race file
 * names ({@code values_from}, CHAR-38), speed relative to the Bree-man's.
 * The race's own size always stands: NPC collision boxes are mob details
 * and need not match their drawn size. A race that names no NPC, and any
 * race while LOTR cannot be asked, has its file's values. The log names
 * each number of a file the NPC's replaced.
 */
public final class LotrRaceProfileAdapter {

    /** The NPC whose speed every race's is measured against. */
    private static final String SPEED_BASELINE = "lotr:bree_man";

    private interface RepresentativeFactory {
        LOTREntityNPC create(World world);
    }

    /** What one NPC is worth, as LOTR sets it up; a value it lacks is not a number. */
    private static final class Values {
        private final String className;
        private final double maxHealth;
        private final double movementMultiplier;
        private final double attackDamage;

        private Values(String className, double maxHealth, double movementMultiplier,
                       double attackDamage) {
            this.className = className;
            this.maxHealth = maxHealth;
            this.movementMultiplier = movementMultiplier;
            this.attackDamage = attackDamage;
        }
    }

    private static final LotrRaceProfileAdapter INSTANCE =
            new LotrRaceProfileAdapter();

    private Map<String, Values> representatives = Collections.emptyMap();
    private final Map<String, CharacterRaceGameplayProfile> profiles =
            new HashMap<String, CharacterRaceGameplayProfile>();
    private int profilesRevision = -1;
    private boolean initialized;

    public static LotrRaceProfileAdapter getInstance() {
        return INSTANCE;
    }

    private LotrRaceProfileAdapter() {}

    public synchronized CharacterRaceGameplayProfile resolve(
            World world, String raceId) {
        String canonicalRaceId = LostTalesIdentifiers.normalize(raceId);
        if (canonicalRaceId.length() == 0) {
            return CharacterRaceGameplayRegistry.DEFAULT;
        }
        CharacterRaceDefinition race = CharacterRaceRegistry.get(canonicalRaceId);
        CharacterRaceGameplayProfile fallback =
                CharacterRaceGameplayRegistry.getFallback(canonicalRaceId);
        if (world == null || race == null) {
            return fallback;
        }
        ensureInitialized(world);
        int revision = CharacterRaceRegistry.revision();
        if (revision != this.profilesRevision) {
            this.profiles.clear();
            this.profilesRevision = revision;
        }
        CharacterRaceGameplayProfile profile = this.profiles.get(canonicalRaceId);
        if (profile == null) {
            profile = createProfile(race,
                    this.representatives.get(race.getLotrRaceAssociation()), fallback);
            this.profiles.put(canonicalRaceId, profile);
            List<String> replaced = replacedNumbers(fallback, profile);
            if (!replaced.isEmpty()) {
                FMLLog.info("[%s] Race %s takes its values from %s: %s",
                        LostTalesMetaData.MOD_ID, canonicalRaceId,
                        race.getLotrRaceAssociation(), join(replaced));
            }
        }
        return profile;
    }

    /**
     * Each number of the race's file that the NPC's replaced, as the log
     * names it: {@code health 30 in place of 20}. A number the NPC left as
     * the file wrote it is not named.
     */
    static List<String> replacedNumbers(CharacterRaceGameplayProfile file,
                                        CharacterRaceGameplayProfile taken) {
        List<String> replaced = new ArrayList<String>();
        if (file == null || taken == null || file == taken) {
            return replaced;
        }
        addReplaced(replaced, "health", "", file.getMaxHealth(), taken.getMaxHealth());
        addReplaced(replaced, "speed", "x", file.getMovementSpeedMultiplier(),
                taken.getMovementSpeedMultiplier());
        addReplaced(replaced, "attack", "", file.getAttackDamage(), taken.getAttackDamage());
        return replaced;
    }

    private static void addReplaced(List<String> replaced, String what, String mark,
                                    double file, double taken) {
        if (Math.abs(file - taken) > 1.0E-6D) {
            replaced.add(what + " " + mark + number(taken) + " in place of "
                    + mark + number(file));
        }
    }

    /** A whole number without decimals, any other with up to three. */
    static String number(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long)value);
        }
        String shown = String.format(Locale.ROOT, "%.3f", Double.valueOf(value));
        while (shown.endsWith("0")) {
            shown = shown.substring(0, shown.length() - 1);
        }
        return shown;
    }

    private static String join(List<String> parts) {
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(part);
        }
        return joined.toString();
    }

    public synchronized void clear() {
        this.initialized = false;
        this.representatives = Collections.emptyMap();
        this.profiles.clear();
        this.profilesRevision = -1;
    }

    private void ensureInitialized(World world) {
        if (this.initialized) {
            return;
        }
        this.initialized = true;

        LinkedHashMap<String, LOTREntityNPC> npcs =
                new LinkedHashMap<String, LOTREntityNPC>();
        try {
            for (Map.Entry<String, RepresentativeFactory> entry
                    : createFactories().entrySet()) {
                LOTREntityNPC representative = entry.getValue().create(world);
                if (representative == null) {
                    throw new IllegalStateException(
                            "null representative for " + entry.getKey());
                }
                npcs.put(entry.getKey(), representative);
            }

            double baselineSpeed = readBaseAttribute(npcs.get(SPEED_BASELINE),
                    SharedMonsterAttributes.movementSpeed);
            if (!isPositiveFinite(baselineSpeed)) {
                throw new IllegalStateException(
                        "invalid LOTR Bree-man movement speed " + baselineSpeed);
            }

            LinkedHashMap<String, Values> resolved = new LinkedHashMap<String, Values>();
            for (Map.Entry<String, LOTREntityNPC> entry : npcs.entrySet()) {
                LOTREntityNPC npc = entry.getValue();
                Values values = new Values(npc.getClass().getName(),
                        readBaseAttribute(npc, SharedMonsterAttributes.maxHealth),
                        readBaseAttribute(npc, SharedMonsterAttributes.movementSpeed)
                                / baselineSpeed,
                        readBaseAttribute(npc, LOTREntityNPC.npcAttackDamage));
                resolved.put(entry.getKey(), values);
                FMLLog.info(
                        "[%s] LOTR race values %s <- %s: health %.2f, speed x%.3f, damage %.2f",
                        LostTalesMetaData.MOD_ID, entry.getKey(), values.className,
                        values.maxHealth, values.movementMultiplier, values.attackDamage);
            }
            this.representatives = Collections.unmodifiableMap(resolved);
        } catch (Throwable throwable) {
            this.representatives = Collections.emptyMap();
            FMLLog.severe(
                    "[%s] LOTR-driven race values could not be read; each race's file values stand: %s",
                    LostTalesMetaData.MOD_ID, throwable.toString());
        } finally {
            for (LOTREntityNPC representative : npcs.values()) {
                try {
                    representative.setDead();
                } catch (Throwable ignored) {
                    // The representative was never spawned into the world.
                }
            }
        }
    }

    /**
     * The race's size with the NPC's health, speed and attack; each value
     * the NPC lacks, or one out of shape, is the file's.
     */
    private static CharacterRaceGameplayProfile createProfile(
            CharacterRaceDefinition race, Values values,
            CharacterRaceGameplayProfile fallback) {
        if (values == null) {
            return fallback;
        }
        return new CharacterRaceGameplayProfile(
                race.getId(),
                values.className,
                race.getWidth(),
                race.getHeight(),
                race.getStandingEyeHeight(),
                race.getSneakingEyeHeight(),
                isPositiveFinite(values.maxHealth)
                        ? values.maxHealth : fallback.getMaxHealth(),
                isPositiveFinite(values.movementMultiplier)
                        ? values.movementMultiplier : fallback.getMovementSpeedMultiplier(),
                isNonNegativeFinite(values.attackDamage)
                        ? values.attackDamage : fallback.getAttackDamage());
    }

    private static double readBaseAttribute(LOTREntityNPC entity, IAttribute attribute) {
        if (entity == null || attribute == null) {
            return Double.NaN;
        }
        IAttributeInstance instance = entity.getEntityAttribute(attribute);
        return instance == null ? Double.NaN : instance.getBaseValue();
    }

    /** The NPCs a race file may take its values from, by the name it writes. */
    private static LinkedHashMap<String, RepresentativeFactory> createFactories() {
        LinkedHashMap<String, RepresentativeFactory> factories =
                new LinkedHashMap<String, RepresentativeFactory>();
        factories.put("lotr:bree_man", new RepresentativeFactory() {
            @Override
            public LOTREntityNPC create(World world) {
                return new LOTREntityBreeMan(world);
            }
        });
        factories.put("lotr:high_elf", new RepresentativeFactory() {
            @Override
            public LOTREntityNPC create(World world) {
                return new LOTREntityHighElf(world);
            }
        });
        factories.put("lotr:dwarf", new RepresentativeFactory() {
            @Override
            public LOTREntityNPC create(World world) {
                return new LOTREntityDwarf(world);
            }
        });
        factories.put("lotr:hobbit", new RepresentativeFactory() {
            @Override
            public LOTREntityNPC create(World world) {
                return new LOTREntityHobbit(world);
            }
        });
        factories.put("lotr:mordor_orc", new RepresentativeFactory() {
            @Override
            public LOTREntityNPC create(World world) {
                return new LOTREntityMordorOrc(world);
            }
        });
        factories.put("lotr:uruk_hai", new RepresentativeFactory() {
            @Override
            public LOTREntityNPC create(World world) {
                return new LOTREntityUrukHai(world);
            }
        });
        return factories;
    }

    private static boolean isPositiveFinite(double value) {
        return !Double.isNaN(value)
                && !Double.isInfinite(value)
                && value > 0.0D;
    }

    private static boolean isNonNegativeFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0.0D;
    }
}
