package com.ninuna.losttales.client.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import java.net.URI;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the {@code textures} property Mojang attaches to a game profile.
 *
 * The property is Base64 JSON. This build's authlib exposes only the skin
 * URL from it, so the {@code metadata.model} flag that marks a slim skin has
 * to be read here. Nothing in the payload is trusted: the URL must point at
 * Mojang's texture hosts, the hash must be a plain name, and anything
 * malformed decodes to null.
 */
public final class ProfileTexturesDecoder {

    static final String TEXTURES_PROPERTY = "textures";
    static final int MAX_PAYLOAD_CHARACTERS = 16 * 1024;

    private static final Set<String> ALLOWED_HOSTS = new HashSet<String>(Arrays.asList(
            "textures.minecraft.net", "skins.minecraft.net"));
    private static final Pattern HASH = Pattern.compile("[a-z0-9]{1,128}");

    private ProfileTexturesDecoder() {}

    /** The profile's skin, or null when the profile carries none it can trust. */
    public static AccountSkinProfile decode(GameProfile profile) {
        if (profile == null || profile.getProperties() == null) {
            return null;
        }
        Collection<Property> properties = profile.getProperties().get(TEXTURES_PROPERTY);
        if (properties == null) {
            return null;
        }
        for (Property property : properties) {
            AccountSkinProfile skin = property == null
                    ? null : decodePayload(property.getValue());
            if (skin != null) {
                return skin;
            }
        }
        return null;
    }

    /** Decodes one Base64 payload; null for anything that is not a usable skin entry. */
    static AccountSkinProfile decodePayload(String base64) {
        if (base64 == null || base64.length() == 0
                || base64.length() > MAX_PAYLOAD_CHARACTERS) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64.trim());
            String json = new String(bytes, "UTF-8");
            JsonElement root = new JsonParser().parse(json);
            if (root == null || !root.isJsonObject()) {
                return null;
            }
            JsonElement textures = root.getAsJsonObject().get("textures");
            if (textures == null || !textures.isJsonObject()) {
                return null;
            }
            JsonElement skin = textures.getAsJsonObject().get("SKIN");
            if (skin == null || !skin.isJsonObject()) {
                return null;
            }
            JsonObject skinObject = skin.getAsJsonObject();
            JsonElement url = skinObject.get("url");
            if (url == null || !url.isJsonPrimitive()
                    || !url.getAsJsonPrimitive().isString()) {
                return null;
            }
            String hash = hashOf(url.getAsString());
            if (hash == null) {
                return null;
            }
            return new AccountSkinProfile(url.getAsString(), hash, isSlim(skinObject));
        } catch (Exception failure) {
            return null;
        }
    }

    private static boolean isSlim(JsonObject skin) {
        JsonElement metadata = skin.get("metadata");
        if (metadata == null || !metadata.isJsonObject()) {
            return false;
        }
        JsonElement model = metadata.getAsJsonObject().get("model");
        return model != null && model.isJsonPrimitive()
                && model.getAsJsonPrimitive().isString()
                && "slim".equals(model.getAsString());
    }

    /** The last path segment of a Mojang texture URL, or null for any other URL. */
    static String hashOf(String url) {
        if (url == null || url.length() == 0 || url.length() > 512) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception failure) {
            return null;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String path = uri.getPath();
        if (scheme == null || host == null || path == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || !ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT))
                || path.contains("..")) {
            return null;
        }
        int slash = path.lastIndexOf('/');
        String name = slash < 0 ? path : path.substring(slash + 1);
        String hash = name.toLowerCase(Locale.ROOT);
        return HASH.matcher(hash).matches() ? hash : null;
    }
}
