package com.ninuna.losttales.util;

import java.util.Locale;

/** Identifiers as the registries and stores compare them. */
public final class LostTalesIdentifiers {

    private LostTalesIdentifiers() {}

    /** The identifier trimmed and lower-cased in the root locale; empty for null. */
    public static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
