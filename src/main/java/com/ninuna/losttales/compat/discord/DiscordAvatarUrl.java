package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.config.LostTalesConfig;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * The picture a webhook post carries: the server's configured template
 * with {@code {name}} and {@code {uuid}} filled in from the sender's
 * Minecraft account, so each line shows its sender's head. Discord
 * fetches the image itself; the template must be an {@code https} URL
 * or it is not used at all.
 */
public final class DiscordAvatarUrl {
    private DiscordAvatarUrl() {}

    /**
     * The avatar URL for an online account under the server's configured
     * template; empty for no player or a template that gives none.
     */
    public static String forPlayer(EntityPlayerMP player) {
        if (player == null) {
            return "";
        }
        String accountName = player.getGameProfile() == null
                ? player.getCommandSenderName()
                : player.getGameProfile().getName();
        return of(LostTalesConfig.discordAvatarUrlTemplate, accountName,
                player.getUniqueID());
    }

    /** The avatar URL for the sender, or empty when the template gives none. */
    public static String of(String template, String accountName, UUID accountId) {
        if (template == null || accountName == null) {
            return "";
        }
        String url = template.trim();
        if (!url.startsWith("https://")) {
            return "";
        }
        url = url.replace("{name}", encode(accountName.trim()));
        url = url.replace("{uuid}", accountId == null ? ""
                : accountId.toString().replace("-", ""));
        return url.contains("{") ? "" : url;
    }

    private static String encode(String value) {
        try {
            // A path segment, not a query: a space is %20 there.
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException impossible) {
            return value;
        }
    }
}
