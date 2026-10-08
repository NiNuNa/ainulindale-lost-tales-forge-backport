package com.ninuna.losttales.client.character;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/**
 * Which account this client is playing as.
 *
 * <p>Two answers, which agree on an online server and in single player.
 * {@link #id()} is the account the game files data under in a world: the
 * id the server gave the player, which an offline-mode server or a proxy
 * derives from the name. {@link #signedInId()} is always the signed-in
 * session's account, the one this installation's per-account files are
 * named after, so every world reads the same file whatever id a server
 * hands out.</p>
 *
 * <p>A session signed in with Mojang carries its own id. One that is not
 * — an offline login, a development launch — carries none, and the
 * session names it after the account instead, exactly as a server in
 * offline mode does.</p>
 *
 * <p>Null only when there is no session at all to ask. Everything that
 * keeps something per account treats that as "this installation has no
 * account of its own" rather than falling back to a shared one.</p>
 */
public final class LostTalesClientAccount {

    private LostTalesClientAccount() {}

    /** The account the server accepted, which its data is filed under; null outside a world. */
    public static UUID id() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null || minecraft.thePlayer == null ? null
                : minecraft.thePlayer.getUniqueID();
    }

    /** The account signed in on this installation, which its per-account files are named after; null when the session does not say. */
    public static UUID signedInId() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null ? null : sessionId(minecraft);
    }

    private static UUID sessionId(Minecraft minecraft) {
        try {
            if (minecraft.getSession() == null) {
                return null;
            }
            GameProfile profile = minecraft.getSession().func_148256_e();
            return profile == null ? null : profile.getId();
        } catch (RuntimeException unavailable) {
            return null;
        }
    }
}
