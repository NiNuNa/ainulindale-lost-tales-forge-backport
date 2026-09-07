package com.ninuna.losttales.client.character;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/**
 * Which account this client is signed in as, before it has joined
 * anywhere. The main menu has no player to ask, so the answer comes from
 * the session Minecraft started with.
 *
 * <p>Null when it cannot be said — a session with no profile id, which
 * an offline development login gives. Everything that keeps something per
 * account treats that as "this installation has no account of its own"
 * rather than falling back to a shared one.</p>
 */
public final class LostTalesClientAccount {

    private LostTalesClientAccount() {}

    /** The signed-in account's id, or null when the session does not say. */
    public static UUID id() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return null;
        }
        // In world the player is the better answer: it is the account the
        // server accepted, which is what its data is filed under.
        if (minecraft.thePlayer != null
                && minecraft.thePlayer.getUniqueID() != null) {
            return minecraft.thePlayer.getUniqueID();
        }
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
