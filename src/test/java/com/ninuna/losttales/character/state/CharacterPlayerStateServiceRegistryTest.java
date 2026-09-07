package com.ninuna.losttales.character.state;

import com.ninuna.losttales.character.state.component.VanillaLocationStateComponent;
import com.ninuna.losttales.character.state.component.VanillaVitalsStateComponent;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Guards the component registry the state service builds in its
 * constructor: the set of components, the ids a saved snapshot is keyed
 * by, the bootstrap versions the migration reads, and the apply order.
 * The service is a singleton whose lists are private, so the registry is
 * read back by reflection from the same package.
 */
public final class CharacterPlayerStateServiceRegistryTest {

    private static final String COMPONENT_PACKAGE =
            "com.ninuna.losttales.character.state.component";
    private static final String COMPONENT_PACKAGE_PATH =
            "com/ninuna/losttales/character/state/component";

    /**
     * A snapshot is a compound keyed by these ids, so renaming one
     * orphans the state of every character already saved.
     */
    private static final Set<String> SAVED_COMPONENT_IDS =
            new HashSet<String>(Arrays.asList(
                    "vanilla_inventory",
                    "vanilla_ender_chest",
                    "vanilla_location",
                    "vanilla_spawns",
                    "vanilla_potions",
                    "vanilla_statistics",
                    "vanilla_vitals",
                    "losttales_accessory",
                    "losttales_quests",
                    "lotr_fast_travel_regions",
                    "lotr_waypoint_uses",
                    "lotr_custom_waypoints",
                    "lotr_progression",
                    "lotr_character_details",
                    "lotr_quests"));

    /**
     * A component class that is written but never registered captures and
     * applies nothing, and nothing else reports it missing. The compiled
     * component package is the source of truth here.
     */
    @Test
    public void everyComponentClassInThePackageIsRegistered() throws Exception {
        Set<Class<?>> compiled = componentClassesInPackage();
        assertFalse("no component classes were found on the classpath",
                compiled.isEmpty());

        LinkedHashSet<Class<?>> registered = new LinkedHashSet<Class<?>>();
        for (CharacterStateComponent component : registeredComponents()) {
            assertTrue("component registered twice: " + component.getClass(),
                    registered.add(component.getClass()));
        }
        assertEquals(compiled, registered);
    }

    /** Registering two components under one id is refused at construction. */
    @Test
    public void componentIdsAreUniqueAndNonBlank() throws Exception {
        List<CharacterStateComponent> components = registeredComponents();
        Map<String, CharacterStateComponent> byId = componentsById();
        HashSet<String> seen = new HashSet<String>();
        for (CharacterStateComponent component : components) {
            String id = component.getId();
            assertNotNull("component id is null: " + component.getClass(), id);
            assertTrue("component id is blank: " + component.getClass(),
                    id.trim().length() > 0);
            assertTrue("duplicate component id " + id, seen.add(id));
            assertSame("id lookup misses " + id, component, byId.get(id));
        }
        assertEquals(components.size(), byId.size());
    }

    /** The ids are save surface, so the set is spelled out rather than derived. */
    @Test
    public void componentIdsMatchTheSavedNames() throws Exception {
        HashSet<String> ids = new HashSet<String>();
        for (CharacterStateComponent component : registeredComponents()) {
            ids.add(component.getId());
        }
        assertEquals(SAVED_COMPONENT_IDS, ids);
    }

    /**
     * The migration fills in every component introduced after the version
     * an account was bootstrapped at. A version outside the known range
     * would make a component either unreachable or permanently missing.
     */
    @Test
    public void everyComponentNamesAKnownBootstrapVersion() throws Exception {
        List<CharacterStateComponent> components = registeredComponents();
        Map<String, Integer> introducedAt = introducedAt();
        assertEquals(components.size(), introducedAt.size());
        for (CharacterStateComponent component : components) {
            Integer version = introducedAt.get(component.getId());
            assertNotNull("no bootstrap version for " + component.getId(), version);
            assertTrue(component.getId() + " names bootstrap version " + version,
                    version.intValue() >= 1 && version.intValue()
                            <= CharacterPlayerStateAccount.CURRENT_BOOTSTRAP_VERSION);
        }
    }

    /**
     * Vitals is registered last and is applied last: it clamps the stored
     * health against the player's maximum, which the race attributes set
     * between the two apply phases.
     */
    @Test
    public void vitalsIsAppliedLast() throws Exception {
        List<CharacterStateComponent> components = registeredComponents();
        CharacterStateComponent vitals =
                components.get(components.size() - 1);
        assertEquals(VanillaVitalsStateComponent.class, vitals.getClass());
        assertEquals(CharacterStateApplyPhase.AFTER_ATTRIBUTES,
                vitals.getApplyPhase());

        List<CharacterStateComponent> applied = applyOrder(components);
        assertSame(vitals, applied.get(applied.size() - 1));
    }

    /**
     * Location is coordinator-only, so neither apply phase moves the
     * player; the transition service places them from the same component.
     * Every other component is applied exactly once.
     */
    @Test
    public void coordinatorOnlyComponentsAreNotApplied() throws Exception {
        List<CharacterStateComponent> components = registeredComponents();
        List<CharacterStateComponent> applied = applyOrder(components);
        int coordinatorOnly = 0;
        for (CharacterStateComponent component : components) {
            assertNotNull("component has no apply phase: " + component.getId(),
                    component.getApplyPhase());
            if (component.getApplyPhase()
                    == CharacterStateApplyPhase.COORDINATOR_ONLY) {
                coordinatorOnly++;
                assertFalse(component.getId() + " must not be applied",
                        applied.contains(component));
            }
        }
        assertEquals(components.size() - coordinatorOnly, applied.size());

        CharacterStateComponent location =
                componentsById().get(VanillaLocationStateComponent.ID);
        assertNotNull("the location component is not registered", location);
        assertEquals(CharacterStateApplyPhase.COORDINATOR_ONLY,
                location.getApplyPhase());
    }

    /** A snapshot that does not carry the whole component set is refused. */
    @Test(expected = CharacterStateValidationException.class)
    public void anIncompleteSnapshotIsRefused() throws Exception {
        CharacterPlayerStateSnapshot empty = new CharacterPlayerStateSnapshot(
                UUID.randomUUID(),
                1L,
                1L,
                CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION,
                new LinkedHashMap<String, NBTTagCompound>());
        CharacterPlayerStateService.getInstance().validateSnapshot(empty);
    }

    /** The components the two apply phases visit, in the order they visit them. */
    private static List<CharacterStateComponent> applyOrder(
            List<CharacterStateComponent> components) {
        ArrayList<CharacterStateComponent> applied =
                new ArrayList<CharacterStateComponent>();
        CharacterStateApplyPhase[] phases = new CharacterStateApplyPhase[] {
                CharacterStateApplyPhase.BEFORE_ATTRIBUTES,
                CharacterStateApplyPhase.AFTER_ATTRIBUTES };
        for (CharacterStateApplyPhase phase : phases) {
            for (CharacterStateComponent component : components) {
                if (component.getApplyPhase() == phase) {
                    applied.add(component);
                }
            }
        }
        return applied;
    }

    private static Set<Class<?>> componentClassesInPackage() throws Exception {
        CodeSource source =
                CharacterStateComponent.class.getProtectionDomain().getCodeSource();
        assertNotNull("the mod classes have no code source", source);
        URL location = source.getLocation();
        assertNotNull("the mod classes have no code source location", location);
        File directory = new File(new File(location.toURI()),
                COMPONENT_PACKAGE_PATH);
        assertTrue("compiled component package not found at " + directory,
                directory.isDirectory());
        File[] files = directory.listFiles();
        assertNotNull("compiled component package is unreadable", files);

        LinkedHashSet<Class<?>> found = new LinkedHashSet<Class<?>>();
        ClassLoader loader = CharacterStateComponent.class.getClassLoader();
        for (File file : files) {
            String name = file.getName();
            if (!file.isFile() || !name.endsWith(".class")) {
                continue;
            }
            Class<?> type = Class.forName(COMPONENT_PACKAGE + "."
                    + name.substring(0, name.length() - ".class".length()),
                    false, loader);
            if (CharacterStateComponent.class.isAssignableFrom(type)
                    && !type.isInterface()
                    && !Modifier.isAbstract(type.getModifiers())) {
                found.add(type);
            }
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static List<CharacterStateComponent> registeredComponents()
            throws Exception {
        return (List<CharacterStateComponent>) read("components");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, CharacterStateComponent> componentsById()
            throws Exception {
        return (Map<String, CharacterStateComponent>) read("componentsById");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> introducedAt() throws Exception {
        return (Map<String, Integer>) read("introducedAt");
    }

    private static Object read(String fieldName) throws Exception {
        Field field = CharacterPlayerStateService.class
                .getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(CharacterPlayerStateService.getInstance());
        assertNotNull(fieldName + " is null", value);
        return value;
    }
}
