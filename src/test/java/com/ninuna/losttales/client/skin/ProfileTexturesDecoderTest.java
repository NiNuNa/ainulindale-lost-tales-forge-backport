package com.ninuna.losttales.client.skin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The decoder reads only what Mojang's payload promises and trusts none of it. */
public final class ProfileTexturesDecoderTest {

    private static final String MOJANG_URL =
            "http://textures.minecraft.net/texture/"
                    + "3b60a1f6d562f52aaebbf1434f1de147933a3affe0e764fa49ea057536623cd3";

    @Test
    public void readsUrlHashAndSlimFlag() {
        AccountSkinProfile skin = ProfileTexturesDecoder.decodePayload(payload(
                "{\"textures\":{\"SKIN\":{\"url\":\"" + MOJANG_URL + "\","
                        + "\"metadata\":{\"model\":\"slim\"}},"
                        + "\"CAPE\":{\"url\":\"http://textures.minecraft.net/texture/cape\"}}}"));
        assertNotNull(skin);
        assertEquals(MOJANG_URL, skin.getUrl());
        assertEquals("3b60a1f6d562f52aaebbf1434f1de147933a3affe0e764fa49ea057536623cd3",
                skin.getHash());
        assertTrue(skin.isSlim());
    }

    @Test
    public void missingMetadataMeansWide() {
        AccountSkinProfile skin = ProfileTexturesDecoder.decodePayload(payload(
                "{\"textures\":{\"SKIN\":{\"url\":\"" + MOJANG_URL + "\"}}}"));
        assertNotNull(skin);
        assertFalse(skin.isSlim());
    }

    @Test
    public void rejectsForeignHostsAndGarbage() {
        assertNull(ProfileTexturesDecoder.decodePayload(payload(
                "{\"textures\":{\"SKIN\":{\"url\":\"http://example.com/texture/abc\"}}}")));
        assertNull(ProfileTexturesDecoder.decodePayload(payload(
                "{\"textures\":{\"SKIN\":{\"url\":\"ftp://textures.minecraft.net/texture/abc\"}}}")));
        assertNull(ProfileTexturesDecoder.decodePayload(payload(
                "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/../x\"}}}")));
        assertNull(ProfileTexturesDecoder.decodePayload(payload("{\"textures\":{}}")));
        assertNull(ProfileTexturesDecoder.decodePayload(payload("[1,2,3]")));
        assertNull(ProfileTexturesDecoder.decodePayload("not base64 at all!"));
        assertNull(ProfileTexturesDecoder.decodePayload(null));
        assertNull(ProfileTexturesDecoder.decodePayload(""));
    }

    @Test
    public void readsTheTexturesPropertyOfAProfile() {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "Tester");
        assertNull(ProfileTexturesDecoder.decode(profile));
        profile.getProperties().put(ProfileTexturesDecoder.TEXTURES_PROPERTY,
                new Property(ProfileTexturesDecoder.TEXTURES_PROPERTY, payload(
                        "{\"textures\":{\"SKIN\":{\"url\":\"" + MOJANG_URL + "\","
                                + "\"metadata\":{\"model\":\"slim\"}}}}")));
        AccountSkinProfile skin = ProfileTexturesDecoder.decode(profile);
        assertNotNull(skin);
        assertTrue(skin.isSlim());
        assertNull(ProfileTexturesDecoder.decode(null));
    }

    private static String payload(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
