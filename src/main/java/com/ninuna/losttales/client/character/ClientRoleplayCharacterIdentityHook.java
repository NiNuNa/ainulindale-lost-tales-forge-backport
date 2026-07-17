package com.ninuna.losttales.client.character;

import com.mojang.authlib.GameProfile;
import com.ninuna.losttales.character.sync.CharacterAppearance;

/** Client-only labels for UI paths that bypass normal entity nameplates. */
public final class ClientRoleplayCharacterIdentityHook {
    private ClientRoleplayCharacterIdentityHook() {}

    public static String resolveMapPlayerName(GameProfile profile) {
        if (profile == null) {
            return "";
        }
        CharacterAppearance appearance =
                ClientCharacterAppearanceCache.getAuthoritative(
                        profile.getId());
        if (appearance != null && appearance.isPresent()) {
            String name = appearance.getCharacterName();
            if (name != null && name.trim().length() > 0) {
                return name;
            }
        }
        String accountName = profile.getName();
        return accountName == null ? "" : accountName;
    }
}
