package com.ninuna.losttales.character.state.compat;

import com.ninuna.losttales.character.state.CharacterStateValidationException;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.stats.StatisticsFile;
import net.minecraft.util.TupleIntJsonSerializable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * Narrow compatibility bridge for replacing the complete 1.7.10 statistics map.
 *
 * <p>The vanilla API supports individual statistic updates but does not expose a
 * public bulk replacement operation. Runtime-obfuscated field names are handled
 * by verifying the field shape rather than relying exclusively on an SRG name.</p>
 */
public final class VanillaStatisticsAccess {

    private static final Field STATISTICS_MAP_FIELD = resolveStatisticsMapField();

    private VanillaStatisticsAccess() {}

    public static Map<StatBase, TupleIntJsonSerializable> getStatisticsMap(
            StatisticsFile statistics) throws CharacterStateValidationException {
        if (statistics == null) {
            throw new CharacterStateValidationException(
                    "Vanilla statistics manager is unavailable");
        }
        if (STATISTICS_MAP_FIELD == null) {
            throw new CharacterStateValidationException(
                    "Vanilla statistics map is incompatible with this server build");
        }
        try {
            Object value = STATISTICS_MAP_FIELD.get(statistics);
            if (!(value instanceof Map)) {
                throw new CharacterStateValidationException(
                        "Vanilla statistics map has an unexpected runtime type");
            }
            Map<?, ?> raw = (Map<?, ?>) value;
            for (Object rawEntry : raw.entrySet()) {
                if (!(rawEntry instanceof Map.Entry)) {
                    throw new CharacterStateValidationException(
                            "Vanilla statistics map contains an invalid entry");
                }
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) rawEntry;
                if (!(entry.getKey() instanceof StatBase)
                        || !(entry.getValue() instanceof TupleIntJsonSerializable)) {
                    throw new CharacterStateValidationException(
                            "Vanilla statistics map contains incompatible values");
                }
            }
            @SuppressWarnings("unchecked")
            Map<StatBase, TupleIntJsonSerializable> map =
                    (Map<StatBase, TupleIntJsonSerializable>) raw;
            return map;
        } catch (IllegalAccessException exception) {
            throw new CharacterStateValidationException(
                    "Vanilla statistics map could not be accessed", exception);
        }
    }

    public static boolean isAvailable() {
        return STATISTICS_MAP_FIELD != null;
    }

    private static Field resolveStatisticsMapField() {
        Field named = findNamedField("field_150875_a");
        if (named != null) {
            return named;
        }

        Field candidate = null;
        Field[] fields = StatFileWriter.class.getDeclaredFields();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())
                    || !Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            if (candidate != null) {
                // Ambiguous layout: fail closed instead of guessing.
                return null;
            }
            candidate = field;
        }
        return makeAccessible(candidate);
    }

    private static Field findNamedField(String name) {
        try {
            Field field = StatFileWriter.class.getDeclaredField(name);
            if (Modifier.isStatic(field.getModifiers())
                    || !Map.class.isAssignableFrom(field.getType())) {
                return null;
            }
            return makeAccessible(field);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static Field makeAccessible(Field field) {
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field;
        } catch (SecurityException ignored) {
            return null;
        }
    }
}
