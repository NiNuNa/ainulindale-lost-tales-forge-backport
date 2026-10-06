package com.ninuna.losttales.config.server;

import java.util.List;
import java.util.Locale;

/**
 * Whether a change fits the entry it names: the right shape (single or
 * list), a value of the entry's type inside its bounds, one of the valid
 * values where the entry lists them, and within the size the transport
 * allows. Answers why a change is refused, as a lang key under
 * {@link #REASON} and its arguments, or null when it fits.
 */
public final class ServerConfigChangeValidator {

    public static final int MAX_LIST_ITEMS = 256;
    public static final int MAX_VALUE_LENGTH = 4096;

    /** What the lang key of every reason a change is refused begins with. */
    public static final String REASON = ServerConfigApplyResult.WORDS + "refusal.";

    private ServerConfigChangeValidator() {}

    public static ServerConfigApplyResult.Refusal refusal(ServerConfigEntry entry,
                                                          ServerConfigChange change) {
        String name = change.qualifiedName();
        if (entry == null) {
            return refused(name, "no_such_key");
        }
        if (change.isList() != entry.isList()) {
            return refused(name, entry.isList() ? "expects_list" : "expects_single");
        }
        if (change.getValues().size() > MAX_LIST_ITEMS) {
            return refused(name, "too_many_items", String.valueOf(MAX_LIST_ITEMS));
        }
        if (!entry.isList() && change.getValues().size() != 1) {
            return refused(name, "expects_one");
        }
        for (String value : change.getValues()) {
            ServerConfigApplyResult.Refusal refusal = refusal(name, entry, value);
            if (refusal != null) {
                return refusal;
            }
        }
        return null;
    }

    private static ServerConfigApplyResult.Refusal refusal(String name,
                                                           ServerConfigEntry entry,
                                                           String value) {
        if (value == null) {
            return refused(name, "missing_value");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            return refused(name, "too_long", String.valueOf(MAX_VALUE_LENGTH));
        }
        switch (entry.getType()) {
            case INTEGER:
                return integerRefusal(name, entry, value);
            case DOUBLE:
                return doubleRefusal(name, entry, value);
            case BOOLEAN:
                String lower = value.trim().toLowerCase(Locale.ROOT);
                return "true".equals(lower) || "false".equals(lower)
                        ? null : refused(name, "expects_boolean");
            default:
                return validValueRefusal(name, entry.getValidValues(), value);
        }
    }

    private static ServerConfigApplyResult.Refusal integerRefusal(String name,
                                                                  ServerConfigEntry entry,
                                                                  String value) {
        long parsed;
        try {
            parsed = Long.parseLong(value.trim());
        } catch (NumberFormatException malformed) {
            return refused(name, "expects_whole_number");
        }
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            return refused(name, "whole_number_range");
        }
        Long minimum = parseLong(entry.getMinValue());
        Long maximum = parseLong(entry.getMaxValue());
        if (minimum != null && parsed < minimum.longValue()) {
            return refused(name, "below_minimum", String.valueOf(minimum));
        }
        if (maximum != null && parsed > maximum.longValue()) {
            return refused(name, "above_maximum", String.valueOf(maximum));
        }
        return null;
    }

    private static ServerConfigApplyResult.Refusal doubleRefusal(String name,
                                                                 ServerConfigEntry entry,
                                                                 String value) {
        double parsed;
        try {
            parsed = Double.parseDouble(value.trim());
        } catch (NumberFormatException malformed) {
            return refused(name, "expects_number");
        }
        if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
            return refused(name, "expects_finite_number");
        }
        Double minimum = parseDouble(entry.getMinValue());
        Double maximum = parseDouble(entry.getMaxValue());
        if (minimum != null && parsed < minimum.doubleValue()) {
            return refused(name, "below_minimum", String.valueOf(minimum));
        }
        if (maximum != null && parsed > maximum.doubleValue()) {
            return refused(name, "above_maximum", String.valueOf(maximum));
        }
        return null;
    }

    private static ServerConfigApplyResult.Refusal validValueRefusal(String name,
                                                                     List<String> validValues,
                                                                     String value) {
        if (validValues == null || validValues.isEmpty()) {
            return null;
        }
        for (String candidate : validValues) {
            if (candidate.equals(value)) {
                return null;
            }
        }
        return refused(name, "expects_one_of", validValues.toString());
    }

    /** A refusal of the change named {@code name}, its reason the line under {@link #REASON}. */
    static ServerConfigApplyResult.Refusal refused(String name, String reason,
                                                   String... arguments) {
        return new ServerConfigApplyResult.Refusal(name, REASON + reason, arguments);
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
