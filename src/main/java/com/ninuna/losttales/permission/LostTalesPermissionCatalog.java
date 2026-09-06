package com.ninuna.losttales.permission;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The permissions in force: the names a server gives to sets of
 * capabilities, so a role is written in the server's own words rather
 * than in the mod's. {@code permissions.definitions} in
 * {@code server/roles.cfg} defines them, one per entry, and a role
 * {@code grant:}s them by id.
 *
 * <p>A granted id that no permission defines is read as the capability
 * of that id, so a role naming a capability directly keeps working and
 * no server has to define a permission to say something simple. An id
 * that is neither is held on to and reaches nothing: a permission
 * defined later starts working without the roles being rewritten, and a
 * misspelt one never quietly becomes a different grant.</p>
 *
 * <p>One catalogue is current per side, installed with the roles. It
 * decides nothing on its own — {@link LostTalesPermissions} asks it.</p>
 */
public final class LostTalesPermissionCatalog {

    /** The most permissions a server may define; well past any real file. */
    public static final int MAX_PERMISSIONS = 256;

    private static volatile LostTalesPermissionCatalog current = empty();

    /** The capability ids each permission reaches, by permission id. */
    private final Map<String, Set<String>> capabilityIds;
    private final Map<String, String> descriptions;

    private LostTalesPermissionCatalog(Map<String, Set<String>> capabilityIds,
                                       Map<String, String> descriptions) {
        Map<String, Set<String>> copied = new LinkedHashMap<String, Set<String>>();
        for (Map.Entry<String, Set<String>> entry : capabilityIds.entrySet()) {
            copied.put(entry.getKey(), Collections.unmodifiableSet(
                    new LinkedHashSet<String>(entry.getValue())));
        }
        this.capabilityIds = Collections.unmodifiableMap(copied);
        this.descriptions = Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(descriptions));
    }

    /** No permission defined: every grant is read as a capability id. */
    public static LostTalesPermissionCatalog empty() {
        return new LostTalesPermissionCatalog(
                Collections.<String, Set<String>>emptyMap(),
                Collections.<String, String>emptyMap());
    }

    /**
     * The permissions the entries describe. Ids are lower-cased; a
     * permission reaching no capability is kept as it was written and
     * reaches nothing, since a name with nothing behind it must never
     * read as a name with everything behind it.
     */
    public static LostTalesPermissionCatalog of(Map<String, Set<String>> capabilityIds,
                                                Map<String, String> descriptions) {
        Map<String, Set<String>> byId = new LinkedHashMap<String, Set<String>>();
        if (capabilityIds != null) {
            for (Map.Entry<String, Set<String>> entry : capabilityIds.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && byId.size() < MAX_PERMISSIONS) {
                    byId.put(entry.getKey().trim().toLowerCase(Locale.ROOT),
                            entry.getValue());
                }
            }
        }
        Map<String, String> texts = new LinkedHashMap<String, String>();
        if (descriptions != null) {
            for (Map.Entry<String, String> entry : descriptions.entrySet()) {
                if (entry.getKey() != null && byId.containsKey(
                        entry.getKey().trim().toLowerCase(Locale.ROOT))) {
                    texts.put(entry.getKey().trim().toLowerCase(Locale.ROOT),
                            entry.getValue() == null ? "" : entry.getValue());
                }
            }
        }
        return new LostTalesPermissionCatalog(byId, texts);
    }

    public static LostTalesPermissionCatalog current() {
        return current;
    }

    public static void install(LostTalesPermissionCatalog catalog) {
        current = catalog == null ? empty() : catalog;
    }

    public static void resetToEmpty() {
        current = empty();
    }

    /** Every permission id defined, in the order the file lists them. */
    public List<String> ids() {
        return Collections.unmodifiableList(
                new ArrayList<String>(this.capabilityIds.keySet()));
    }

    /** Whether a permission of that id is defined. */
    public boolean isDefined(String permissionId) {
        return permissionId != null && this.capabilityIds.containsKey(
                permissionId.trim().toLowerCase(Locale.ROOT));
    }

    /** What the file says the permission is for; empty when it says nothing. */
    public String descriptionOf(String permissionId) {
        String text = permissionId == null ? null
                : this.descriptions.get(permissionId.trim().toLowerCase(Locale.ROOT));
        return text == null ? "" : text;
    }

    /** The capability ids the permission names; empty for one that is not defined. */
    public Set<String> capabilityIdsOf(String permissionId) {
        Set<String> ids = permissionId == null ? null
                : this.capabilityIds.get(permissionId.trim().toLowerCase(Locale.ROOT));
        return ids == null ? Collections.<String>emptySet() : ids;
    }

    /**
     * Whether the granted id reaches the capability: through a permission
     * of that id, or — when none is defined — by naming the capability
     * itself. An id that is neither reaches nothing.
     */
    public boolean reaches(String grantedId, LostTalesCapability capability) {
        if (grantedId == null || capability == null) {
            return false;
        }
        String id = grantedId.trim().toLowerCase(Locale.ROOT);
        Set<String> defined = this.capabilityIds.get(id);
        if (defined != null) {
            return defined.contains(capability.getId());
        }
        return id.equals(capability.getId())
                && LostTalesCapability.byId(id) != null;
    }

    /** Whether any of the granted ids reaches the capability. */
    public boolean reachesAny(Collection<String> grantedIds,
                              LostTalesCapability capability) {
        if (grantedIds == null || capability == null) {
            return false;
        }
        for (String granted : grantedIds) {
            if (reaches(granted, capability)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the id names anything at all: a defined permission, or a
     * registered capability. What a listing and a config warning ask.
     */
    public boolean isKnown(String grantedId) {
        return isDefined(grantedId) || LostTalesCapability.byId(grantedId) != null;
    }
}
