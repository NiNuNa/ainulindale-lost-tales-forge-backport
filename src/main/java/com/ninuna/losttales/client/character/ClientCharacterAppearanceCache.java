package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterAppearanceKind;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/** Client-only cache of public active-character rendering information. */
public final class ClientCharacterAppearanceCache {

    private static final Map<UUID, CharacterAppearance> APPEARANCES =
            new HashMap<UUID, CharacterAppearance>();
    private static final Map<UUID, CharacterAppearance> PREVIEW_APPEARANCES =
            new HashMap<UUID, CharacterAppearance>();

    private ClientCharacterAppearanceCache() {}

    public static synchronized void replaceAll(Collection<CharacterAppearance> appearances) {
        APPEARANCES.clear();
        apply(appearances);
    }

    public static synchronized void apply(Collection<CharacterAppearance> appearances) {
        if (appearances == null) {
            return;
        }
        for (CharacterAppearance appearance : appearances) {
            if (appearance == null) {
                continue;
            }
            if (appearance.isPresent()) {
                APPEARANCES.put(appearance.getPlayerId(), appearance);
            } else {
                APPEARANCES.remove(appearance.getPlayerId());
            }
        }
    }

    public static synchronized CharacterAppearance get(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        CharacterAppearance preview = PREVIEW_APPEARANCES.get(playerId);
        return preview == null ? getAuthoritative(playerId) : preview;
    }

    /**
     * Returns synchronized state only. GUI preview overrides must never alter
     * collision, camera, eye height, targeting, or other gameplay physics.
     */
    public static synchronized CharacterAppearance getAuthoritative(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        CharacterAppearance appearance = APPEARANCES.get(playerId);
        if (appearance != null) {
            return appearance;
        }

        CharacterRosterSnapshot snapshot = ClientCharacterRosterCache.getSnapshot();
        if (snapshot != null && playerId.equals(snapshot.getOwnerId())) {
            CharacterSummary active = snapshot.getActiveCharacter();
            if (active != null) {
                return new CharacterAppearance(
                        CharacterAppearanceKind.CHARACTER,
                        playerId,
                        active.getCharacterId(),
                        "",
                        active.getName(),
                        active.getRaceId(),
                        active.getGenderId(),
                        active.getSkinId(),
                        active.isMinecraftCapeVisible(),
                        active.getCosmeticCapeId(),
                        active.getStartingFactionId(),
                        active.getRoleplayLevel(),
                        active.getAge(),
                        active.getDescription(),
                        active.getBodyTypeId(),
                        active.getChestTypeId());
            }
        }
        return null;
    }

    public static synchronized void setPreview(CharacterAppearance appearance) {
        if (appearance != null && appearance.isPresent()) {
            PREVIEW_APPEARANCES.put(appearance.getPlayerId(), appearance);
        }
    }

    public static synchronized void clearPreview(UUID playerId) {
        if (playerId != null) {
            PREVIEW_APPEARANCES.remove(playerId);
        }
    }

    /**
     * The appearance an online account plays, or null when this client
     * knows none: the local player's own by their id, which falls back
     * to their roster, and everyone else's from the appearance the
     * server syncs for every online player.
     */
    public static CharacterAppearance appearanceFor(String accountName) {
        if (accountName == null || accountName.length() == 0) {
            return null;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.thePlayer != null
                && accountName.equalsIgnoreCase(
                        minecraft.thePlayer.getCommandSenderName())) {
            return getAuthoritative(minecraft.thePlayer.getUniqueID());
        }
        for (CharacterAppearance appearance : snapshot().values()) {
            if (appearance != null
                    && accountName.equalsIgnoreCase(appearance.getAccountName())) {
                return appearance;
            }
        }
        return null;
    }

    /**
     * The name of the character an online account is playing, or null
     * when this client knows none.
     */
    public static String characterNameFor(String accountName) {
        CharacterAppearance appearance = appearanceFor(accountName);
        return appearance == null || !appearance.hasCharacter() ? null
                : trimmedOrNull(appearance.getCharacterName());
    }

    private static String trimmedOrNull(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    public static synchronized Map<UUID, CharacterAppearance> snapshot() {
        return Collections.unmodifiableMap(
                new HashMap<UUID, CharacterAppearance>(APPEARANCES));
    }

    public static synchronized void clear() {
        APPEARANCES.clear();
        PREVIEW_APPEARANCES.clear();
    }
}
