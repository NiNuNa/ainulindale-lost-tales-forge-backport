package com.ninuna.losttales.compat.discord;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;

/**
 * Turns an opened {@link HttpURLConnection} into a {@code PATCH} request.
 * Java 8's client rejects the name in {@code setRequestMethod}, so the
 * method is written to the connection's own {@code method} field — and,
 * for an HTTPS connection, to the delegate it wraps, since that is the
 * object that actually speaks to the socket. Both fields are resolved
 * once and checked for shape; anything unexpected means no PATCH rather
 * than a request with the wrong method.
 */
final class DiscordHttpPatch {
    private static final String METHOD = "PATCH";
    private static final Field METHOD_FIELD = resolveMethodField();
    /** The delegate field of the one HTTPS connection class seen so far. */
    private static volatile Class<?> delegateOwner;
    private static volatile Field delegateField;

    private DiscordHttpPatch() {}

    static boolean isAvailable() {
        return METHOD_FIELD != null;
    }

    /** Makes the connection a PATCH before it connects. */
    static void apply(HttpURLConnection connection)
            throws DiscordHttp.PatchUnsupportedException {
        if (connection == null) {
            throw new DiscordHttp.PatchUnsupportedException(
                    "no connection to patch");
        }
        if (METHOD_FIELD == null) {
            throw new DiscordHttp.PatchUnsupportedException(
                    "this JVM's HttpURLConnection has no settable method field");
        }
        try {
            Object delegate = delegateOf(connection);
            if (delegate instanceof HttpURLConnection) {
                METHOD_FIELD.set(delegate, METHOD);
            }
            METHOD_FIELD.set(connection, METHOD);
        } catch (IllegalAccessException exception) {
            throw new DiscordHttp.PatchUnsupportedException(
                    "the request method could not be set: " + exception);
        } catch (RuntimeException exception) {
            throw new DiscordHttp.PatchUnsupportedException(
                    "the request method could not be set: " + exception);
        }
        if (!METHOD.equals(connection.getRequestMethod())) {
            throw new DiscordHttp.PatchUnsupportedException(
                    "the connection did not take the method");
        }
    }

    private static Object delegateOf(HttpURLConnection connection)
            throws IllegalAccessException {
        Class<?> owner = connection.getClass();
        Field field;
        if (owner == delegateOwner) {
            field = delegateField;
        } else {
            field = findDelegateField(owner);
            delegateField = field;
            delegateOwner = owner;
        }
        return field == null ? null : field.get(connection);
    }

    /**
     * The wrapper's instance field that holds another
     * {@link HttpURLConnection}, whatever it is named; null when the
     * class wraps nothing (plain HTTP, or a different implementation).
     */
    private static Field findDelegateField(Class<?> owner) {
        for (Class<?> type = owner; type != null
                && type != HttpURLConnection.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || !HttpURLConnection.class.isAssignableFrom(
                                field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    return field;
                } catch (RuntimeException inaccessible) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Field resolveMethodField() {
        try {
            Field field = HttpURLConnection.class.getDeclaredField("method");
            if (Modifier.isStatic(field.getModifiers())
                    || field.getType() != String.class) {
                return null;
            }
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException missing) {
            return null;
        } catch (RuntimeException inaccessible) {
            return null;
        }
    }
}
