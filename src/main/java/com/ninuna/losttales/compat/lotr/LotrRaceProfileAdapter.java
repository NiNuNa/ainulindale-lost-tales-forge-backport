package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayProfile;
import com.ninuna.losttales.character.registry.CharacterRaceGameplayRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import cpw.mods.fml.common.FMLLog;
import lotr.common.entity.npc.LOTREntityBreeMan;
import lotr.common.entity.npc.LOTREntityDwarf;
import lotr.common.entity.npc.LOTREntityHalfTroll;
import lotr.common.entity.npc.LOTREntityHighElf;
import lotr.common.entity.npc.LOTREntityHobbit;
import lotr.common.entity.npc.LOTREntityMordorOrc;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTREntityUrukHai;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads playable-race combat attributes from real LOTR NPC classes. Physical
 * player dimensions remain authoritative in {@link CharacterRaceRegistry};
 * NPC collision boxes are mob implementation details and do not necessarily
 * match their rendered model size.
 */
public final class LotrRaceProfileAdapter {

    private interface RepresentativeFactory {
        LOTREntityNPC create(World world);
    }

    private static final LotrRaceProfileAdapter INSTANCE =
            new LotrRaceProfileAdapter();

    private Map<String, CharacterRaceGameplayProfile> profiles =
            Collections.emptyMap();
    private boolean initialized;
    private boolean available;

    public static LotrRaceProfileAdapter getInstance() {
        return INSTANCE;
    }

    private LotrRaceProfileAdapter() {}

    public synchronized CharacterRaceGameplayProfile resolve(
            World world, String raceId) {
        String canonicalRaceId = CharacterRaceRegistry.canonicalizeIdentifier(raceId);
        if (canonicalRaceId.length() == 0) {
            return CharacterRaceGameplayRegistry.DEFAULT;
        }
        CharacterRaceGameplayProfile fallback =
                CharacterRaceGameplayRegistry.getFallback(canonicalRaceId);
        if (world == null) {
            return fallback;
        }
        ensureInitialized(world);
        CharacterRaceGameplayProfile profile = this.profiles.get(canonicalRaceId);
        return profile == null ? fallback : profile;
    }

    public synchronized boolean isAvailable(World world) {
        if (world != null) {
            ensureInitialized(world);
        }
        return this.available;
    }

    public synchronized void clear() {
        this.initialized = false;
        this.available = false;
        this.profiles = Collections.emptyMap();
    }

    private void ensureInitialized(World world) {
        if (this.initialized) {
            return;
        }
        this.initialized = true;

        LinkedHashMap<String, RepresentativeFactory> factories =
                createFactories();
        LinkedHashMap<String, LOTREntityNPC> representatives =
                new LinkedHashMap<String, LOTREntityNPC>();
        try {
            for (Map.Entry<String, RepresentativeFactory> entry
                    : factories.entrySet()) {
                LOTREntityNPC representative = entry.getValue().create(world);
                if (representative == null) {
                    throw new IllegalStateException(
                            "null representative for " + entry.getKey());
                }
                representatives.put(entry.getKey(), representative);
            }

            LOTREntityNPC human = representatives.get(CharacterRaceRegistry.HUMAN);
            double humanMovementSpeed = readBaseAttribute(
                    human, SharedMonsterAttributes.movementSpeed, 0.2D);
            if (!isPositiveFinite(humanMovementSpeed)) {
                throw new IllegalStateException(
                        "invalid LOTR human movement speed " + humanMovementSpeed);
            }

            LinkedHashMap<String, CharacterRaceGameplayProfile> resolved =
                    new LinkedHashMap<String, CharacterRaceGameplayProfile>();
            for (Map.Entry<String, LOTREntityNPC> entry
                    : representatives.entrySet()) {
                CharacterRaceGameplayProfile profile = createProfile(
                        entry.getKey(), entry.getValue(), humanMovementSpeed);
                resolved.put(entry.getKey(), profile);
                FMLLog.info(
                        "[%s] LOTR race profile %s <- %s: %.2fx%.2f, eye %.2f, health %.2f, speed x%.3f, damage %.2f",
                        LostTalesMetaData.MOD_ID,
                        profile.getRaceId(),
                        profile.getRepresentativeEntityClassName(),
                        profile.getWidth(),
                        profile.getHeight(),
                        profile.getEyeHeight(),
                        profile.getMaxHealth(),
                        profile.getMovementSpeedMultiplier(),
                        profile.getAttackDamage());
            }

            this.profiles = Collections.unmodifiableMap(resolved);
            this.available = true;
        } catch (Throwable throwable) {
            this.profiles = Collections.emptyMap();
            this.available = false;
            FMLLog.severe(
                    "[%s] LOTR-driven race profiles could not be initialized. Vanilla-safe values will be used: %s",
                    LostTalesMetaData.MOD_ID, throwable.toString());
        } finally {
            for (LOTREntityNPC representative : representatives.values()) {
                try {
                    representative.setDead();
                } catch (Throwable ignored) {
                    // The representative was never spawned into the world.
                }
            }
        }
    }

    private static CharacterRaceGameplayProfile createProfile(
            String raceId,
            LOTREntityNPC representative,
            double humanMovementSpeed) {
        CharacterRaceDefinition definition = CharacterRaceRegistry.get(raceId);
        CharacterRaceGameplayProfile fallback =
                CharacterRaceGameplayRegistry.getFallback(raceId);
        if (definition == null) {
            throw new IllegalStateException(
                    "missing physical race definition for " + raceId);
        }
        // Do not copy EntityLivingBase dimensions from the representative.
        // LOTR intentionally gives Mordor Orc and Uruk NPCs the same collision
        // box even though their rendered models differ. Those values previously
        // overwrote the player registry and placed both cameras at 1.32 blocks.
        float width = definition.getWidth();
        float height = definition.getHeight();
        float eyeHeight = definition.getStandingEyeHeight();
        float sneakingEyeHeight = definition.getSneakingEyeHeight();
        double maxHealth = readBaseAttribute(
                representative, SharedMonsterAttributes.maxHealth, fallback.getMaxHealth());
        double movementSpeed = readBaseAttribute(
                representative, SharedMonsterAttributes.movementSpeed,
                humanMovementSpeed);
        double movementMultiplier = movementSpeed / humanMovementSpeed;
        double attackDamage = readBaseAttribute(
                representative, LOTREntityNPC.npcAttackDamage,
                fallback.getAttackDamage());

        validatePhysicalValue("width", width);
        validatePhysicalValue("height", height);
        validatePhysicalValue("eye height", eyeHeight);
        validatePhysicalValue("sneaking eye height", sneakingEyeHeight);
        validatePositiveValue("max health", maxHealth);
        validatePositiveValue("movement multiplier", movementMultiplier);
        validateNonNegativeValue("attack damage", attackDamage);

        return new CharacterRaceGameplayProfile(
                raceId,
                representative.getClass().getName(),
                width,
                height,
                eyeHeight,
                sneakingEyeHeight,
                maxHealth,
                movementMultiplier,
                attackDamage,
                true
        );
    }

    private static double readBaseAttribute(
            LOTREntityNPC entity, IAttribute attribute, double fallback) {
        if (entity == null || attribute == null) {
            return fallback;
        }
        IAttributeInstance instance = entity.getEntityAttribute(attribute);
        return instance == null ? fallback : instance.getBaseValue();
    }

    private static void validatePhysicalValue(String name, float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0.0F) {
            throw new IllegalStateException("invalid " + name + ": " + value);
        }
    }

    private static LinkedHashMap<String, RepresentativeFactory> createFactories() {
        LinkedHashMap<String, RepresentativeFactory> factories =
                new LinkedHashMap<String, RepresentativeFactory>();
        factories.put(CharacterRaceRegistry.HUMAN,
                new RepresentativeFactory() {
                    @Override
                    public LOTREntityNPC create(World world) {
                        return new LOTREntityBreeMan(world);
                    }
                });
        factories.put(CharacterRaceRegistry.ELF,
                new RepresentativeFactory() {
                    @Override
                    public LOTREntityNPC create(World world) {
                        return new LOTREntityHighElf(world);
                    }
                });
        factories.put(CharacterRaceRegistry.DWARF,
                new RepresentativeFactory() {
                    @Override
                    public LOTREntityNPC create(World world) {
                        return new LOTREntityDwarf(world);
                    }
                });
        factories.put(CharacterRaceRegistry.HOBBIT,
                new RepresentativeFactory() {
                    @Override
                    public LOTREntityNPC create(World world) {
                        return new LOTREntityHobbit(world);
                    }
                });
        factories.put(CharacterRaceRegistry.ORC,
                new RepresentativeFactory() {
                    @Override
                    public LOTREntityNPC create(World world) {
                        return new LOTREntityMordorOrc(world);
                    }
                });
        factories.put(CharacterRaceRegistry.URUK,
                new RepresentativeFactory() {
                    @Override
                    public LOTREntityNPC create(World world) {
                        return new LOTREntityUrukHai(world);
                    }
                });
        factories.put(CharacterRaceRegistry.HALF_TROLL,
                new RepresentativeFactory() {
                    @Override
                    public LOTREntityNPC create(World world) {
                        return new LOTREntityHalfTroll(world);
                    }
                });
        return factories;
    }

    private static void validatePositiveValue(String name, double value) {
        if (!isPositiveFinite(value)) {
            throw new IllegalStateException("invalid " + name + ": " + value);
        }
    }

    private static void validateNonNegativeValue(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D) {
            throw new IllegalStateException("invalid " + name + ": " + value);
        }
    }

    private static boolean isPositiveFinite(double value) {
        return !Double.isNaN(value)
                && !Double.isInfinite(value)
                && value > 0.0D;
    }
}
