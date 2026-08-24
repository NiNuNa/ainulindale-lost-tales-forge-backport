package com.ninuna.losttales.user;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The recognized Lost Tales users: the team and, as it grows, community
 * members the mod acknowledges by name. Identity is the Minecraft account
 * UUID; the name is only what credits and tooltips print. Recognition is
 * purely cosmetic ({@link ELostTalesUserRecognition}) — a recognized user
 * is never, by virtue of being listed here, an operator or anything else
 * the server grants permissions to. On an offline-mode server the UUIDs
 * are name-derived and match nobody here, so recognition simply does not
 * apply there.
 */
public enum ELostTalesUser {
    //  LostTales - Team.
    NINUNA("NiNuNa", "42c208f1-bdde-445b-91f6-b76a3606f333",
            ELostTalesUserRecognition.DEVELOPER),
    SCOSHER("Scosher", "d0269a66-bbce-4123-bbc4-472623201eda",
            ELostTalesUserRecognition.DEVELOPER),
    BALARAUKO("Balarauko", "e1968bbb-813c-425a-998e-3f75e8aa1b68",
            ELostTalesUserRecognition.DEVELOPER),
    CAPTAIN_CHEESE("captainCheese", "d36e696d-dbbe-48ed-a878-bc8eb480a29c",
            ELostTalesUserRecognition.DEVELOPER),

    //  LostTales - Community.


    //  Empty User - No Credits.
    NULL("", "", ELostTalesUserRecognition.NONE);

    private static final Map<UUID, ELostTalesUser> BY_UUID = index();

    private final String name;
    private final String uuid;
    private final UUID uniqueId;
    private final ELostTalesUserRecognition recognition;

    ELostTalesUser(String name, String uuid,
                   ELostTalesUserRecognition recognition) {
        this.name = name;
        this.uuid = uuid;
        this.uniqueId = parse(uuid);
        this.recognition = recognition;
    }

    public String getName() {
        return name;
    }

    public String getUuid() {
        return uuid;
    }

    /** The account id, or null for {@link #NULL} and a malformed constant. */
    public UUID getUniqueId() {
        return uniqueId;
    }

    public ELostTalesUserRecognition getRecognition() {
        return recognition;
    }

    /** False only for {@link #NULL}. */
    public boolean isRecognized() {
        return this != NULL;
    }

    /** The recognized user with that account id, or {@link #NULL}. */
    public static ELostTalesUser byUniqueId(UUID uniqueId) {
        ELostTalesUser user = uniqueId == null ? null : BY_UUID.get(uniqueId);
        return user == null ? NULL : user;
    }

    private static Map<UUID, ELostTalesUser> index() {
        Map<UUID, ELostTalesUser> index = new HashMap<UUID, ELostTalesUser>();
        for (ELostTalesUser user : values()) {
            if (user.uniqueId != null && !index.containsKey(user.uniqueId)) {
                index.put(user.uniqueId, user);
            }
        }
        return Collections.unmodifiableMap(index);
    }

    /** A malformed constant must not take the enum (and the mod) down. */
    private static UUID parse(String uuid) {
        if (uuid == null || uuid.length() == 0) {
            return null;
        }
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
