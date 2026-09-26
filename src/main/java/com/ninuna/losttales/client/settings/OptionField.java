package com.ninuna.losttales.client.settings;

import com.ninuna.losttales.client.diagnostics.LostTalesClientDiagnostics;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * One option's field on the class that reads its file, found by the
 * option's key, which is the field's name. It is looked up once, as its
 * row is made, and its shape checked: public, static, not final, and of a
 * type the row can read. A field that is missing or of another shape
 * leaves its row out and says so in the log once; the test that every
 * option has a row fails on it before it ships.
 */
final class OptionField {
    private final Field field;

    private OptionField(Field field) {
        this.field = field;
    }

    /**
     * The option {@code key}'s field on {@code owner}, of one of
     * {@code types}; null where there is none of that shape.
     */
    static OptionField find(Class<?> owner, String key, Class<?>... types) {
        Field found;
        try {
            found = owner.getField(key);
        } catch (NoSuchFieldException missing) {
            unavailable(owner, key, missing);
            return null;
        }
        int modifiers = found.getModifiers();
        boolean typed = false;
        for (Class<?> type : types) {
            typed |= found.getType() == type;
        }
        if (!Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)
                || !typed) {
            unavailable(owner, key, new IllegalStateException(
                    "the field is " + found.toGenericString()));
            return null;
        }
        return new OptionField(found);
    }

    private static void unavailable(Class<?> owner, String key,
                                    Exception why) {
        LostTalesClientDiagnostics.warnOnce("settings-field-" + key,
                "Settings leaves out " + key + ": " + owner.getSimpleName()
                        + " holds no option field of that name and shape",
                why);
    }

    boolean getBoolean() {
        try {
            return this.field.getBoolean(null);
        } catch (IllegalAccessException denied) {
            throw new IllegalStateException(denied);
        }
    }

    void setBoolean(boolean on) {
        try {
            this.field.setBoolean(null, on);
        } catch (IllegalAccessException denied) {
            throw new IllegalStateException(denied);
        }
    }

    /** A whole number, a float or a double, read as a double. */
    double getNumber() {
        try {
            return ((Number)this.field.get(null)).doubleValue();
        } catch (IllegalAccessException denied) {
            throw new IllegalStateException(denied);
        }
    }

    /** Writes a number in the field's own type: a whole number rounded. */
    void setNumber(double value) {
        try {
            Class<?> type = this.field.getType();
            if (type == int.class) {
                this.field.setInt(null, (int)Math.round(value));
            } else if (type == float.class) {
                this.field.setFloat(null, (float)value);
            } else {
                this.field.setDouble(null, value);
            }
        } catch (IllegalAccessException denied) {
            throw new IllegalStateException(denied);
        }
    }

    String getString() {
        try {
            Object value = this.field.get(null);
            return value == null ? "" : value.toString();
        } catch (IllegalAccessException denied) {
            throw new IllegalStateException(denied);
        }
    }

    void setString(String text) {
        try {
            this.field.set(null, text == null ? "" : text);
        } catch (IllegalAccessException denied) {
            throw new IllegalStateException(denied);
        }
    }
}
