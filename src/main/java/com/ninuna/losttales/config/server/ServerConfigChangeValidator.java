package com.ninuna.losttales.config.server;

import java.util.List;
import java.util.Locale;

/**
 * Whether a change fits the entry it names: the right shape (single or
 * list), a value of the entry's type inside its bounds, one of the valid
 * values where the entry lists them, and within the size the transport
 * allows. Answers the reason a change is refused, or null when it fits.
 */
public final class ServerConfigChangeValidator {

    public static final int MAX_LIST_ITEMS = 256;
    public static final int MAX_VALUE_LENGTH = 4096;

    private ServerConfigChangeValidator() {}

    public static String refusal(ServerConfigEntry entry, ServerConfigChange change) {
        if (entry == null) {
            return "no such key";
        }
        if (change.isList() != entry.isList()) {
            return entry.isList() ? "expects a list" : "expects a single value";
        }
        if (change.getValues().size() > MAX_LIST_ITEMS) {
            return "more than " + MAX_LIST_ITEMS + " items";
        }
        if (!entry.isList() && change.getValues().size() != 1) {
            return "expects exactly one value";
        }
        for (String value : change.getValues()) {
            String reason = refusal(entry, value);
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }

    private static String refusal(ServerConfigEntry entry, String value) {
        if (value == null) {
            return "missing value";
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            return "longer than " + MAX_VALUE_LENGTH + " characters";
        }
        switch (entry.getType()) {
            case INTEGER:
                return integerRefusal(entry, value);
            case DOUBLE:
                return doubleRefusal(entry, value);
            case BOOLEAN:
                String lower = value.trim().toLowerCase(Locale.ROOT);
                return "true".equals(lower) || "false".equals(lower)
                        ? null : "expects true or false";
            default:
                return validValueRefusal(entry.getValidValues(), value);
        }
    }

    private static String integerRefusal(ServerConfigEntry entry, String value) {
        long parsed;
        try {
            parsed = Long.parseLong(value.trim());
        } catch (NumberFormatException malformed) {
            return "expects a whole number";
        }
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            return "outside the whole-number range";
        }
        Long minimum = parseLong(entry.getMinValue());
        Long maximum = parseLong(entry.getMaxValue());
        if (minimum != null && parsed < minimum.longValue()) {
            return "below the minimum " + minimum;
        }
        if (maximum != null && parsed > maximum.longValue()) {
            return "above the maximum " + maximum;
        }
        return null;
    }

    private static String doubleRefusal(ServerConfigEntry entry, String value) {
        double parsed;
        try {
            parsed = Double.parseDouble(value.trim());
        } catch (NumberFormatException malformed) {
            return "expects a number";
        }
        if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
            return "expects a finite number";
        }
        Double minimum = parseDouble(entry.getMinValue());
        Double maximum = parseDouble(entry.getMaxValue());
        if (minimum != null && parsed < minimum.doubleValue()) {
            return "below the minimum " + minimum;
        }
        if (maximum != null && parsed > maximum.doubleValue()) {
            return "above the maximum " + maximum;
        }
        return null;
    }

    private static String validValueRefusal(List<String> validValues, String value) {
        if (validValues == null || validValues.isEmpty()) {
            return null;
        }
        for (String candidate : validValues) {
            if (candidate.equals(value)) {
                return null;
            }
        }
        return "expects one of " + validValues;
    }

    private static Long parseLong(String value) {
        if (value == null || value.length() == 0) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    private static Double parseDouble(String value) {
        if (value == null || value.length() == 0) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException malformed) {
            return null;
        }
    }
}
