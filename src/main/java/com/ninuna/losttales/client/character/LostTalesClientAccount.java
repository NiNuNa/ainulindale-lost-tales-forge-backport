package com.ninuna.losttales.client.character;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/**
 * Which account this client is playing as.
 *
 * <p>Two answers, which agree on an online server and in single player.
 * {@link #id()} is the account the game files data under right now: in a
 * world, the id the server gave the player, which an offline-mode server
 * or a proxy derives from the name; before any world is joined, the
 * session's own id. {@link #templateId()} is always the signed-in
 * session's account, the one the template file on this installation is
 * named after, so the main menu and every world read the same file
 * whatever id a server hands out.</p>
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

    /** The account the game files data under right now, or null when nothing says. */
    public static UUID id() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return null;
        }
        // In world the player is the answer: it is the account the server
        // accepted, which is what its data is filed under.
        if (minecraft.thePlayer != null
                && minecraft.thePlayer.getUniqueID() != null) {
            return minecraft.thePlayer.getUniqueID();
        }
        return sessionId(minecraft);
    }

    /** The account the template file is named after, or null when the session does not say. */
    public static UUID templateId() {
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

    /**
     * The name the session is signed in under, or null. Vanilla's skin
     * lookup is keyed by name as well as id, so a portrait drawn before
     * any world is joined needs both.
     */
    public static String name() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return null;
        }
        if (minecraft.thePlayer != null) {
            String playerName = minecraft.thePlayer.getCommandSenderName();
            if (playerName != null && playerName.trim().length() > 0) {
                return playerName.trim();
            }
        }
        try {
            if (minecraft.getSession() == null) {
                return null;
            }
            String sessionName = minecraft.getSession().getUsername();
            return sessionName == null || sessionName.trim().length() == 0
                    ? null : sessionName.trim();
        } catch (RuntimeException unavailable) {
            return null;
        }
    }
}
