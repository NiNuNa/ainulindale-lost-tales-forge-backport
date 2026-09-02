package com.ninuna.losttales.client.skin;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.config.LostTalesConfig;
import com.mojang.authlib.GameProfile;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Account skins in Minecraft's 64x64 layout, one texture per skin hash.
 *
 * Vanilla keeps its own {@code skins/<hash>} texture cropped to 64x32; it is
 * left alone so skulls, LOTR's map heads and everything else that samples it
 * keep working. Every texture here lives under {@code losttales:skins/} and
 * stays registered for the rest of the client run, since the same hash names
 * the same image. The per-player answers are dropped on disconnect.
 *
 * The local player's profile carries no {@code textures} property when the
 * entity is built; vanilla's skin manager fetches it on a worker thread and
 * writes it onto the same profile object a moment later. An answer given
 * before that property exists is therefore provisional and is replaced as
 * soon as the property appears. Client render thread only.
 */
public final class LostTalesAccountSkins {

    /** The texture and arm width one player renders with. */
    public static final class AccountSkin {
        private final ResourceLocation texture;
        private final String bodyTypeId;
        private final boolean fromAccount;
        private final boolean awaitingProfile;

        AccountSkin(ResourceLocation texture, String bodyTypeId, boolean fromAccount,
                    boolean awaitingProfile) {
            this.texture = texture;
            this.bodyTypeId = bodyTypeId;
            this.fromAccount = fromAccount;
            this.awaitingProfile = awaitingProfile;
        }

        public ResourceLocation getTexture() {
            return this.texture;
        }

        public String getBodyTypeId() {
            return this.bodyTypeId;
        }

        /** False when the player has no account skin and wears the default. */
        public boolean isFromAccount() {
            return this.fromAccount;
        }

        /** True while the default stands in for a profile not yet fetched. */
        public boolean isAwaitingProfile() {
            return this.awaitingProfile;
        }
    }

    private static final ResourceLocation VANILLA_DEFAULT_SKIN =
            new ResourceLocation("textures/entity/steve.png");
    private static final ResourceLocation DEFAULT_TEXTURE =
            new ResourceLocation(LostTalesMetaData.MOD_ID, "skins/default");
    private static final ResourceLocation OVERRIDE_TEXTURE =
            new ResourceLocation(LostTalesMetaData.MOD_ID, "skins/dev_override");
    private static final String TEXTURE_PREFIX = "skins/";

    private static final Map<UUID, AccountSkin> RESOLVED = new HashMap<UUID, AccountSkin>();
    private static BufferedImage defaultImage;
    private static boolean defaultRegistered;
    private static boolean overrideLogged;

    private LostTalesAccountSkins() {}

    public static AccountSkin resolve(AbstractClientPlayer player) {
        UUID playerId = player == null ? null : player.getUniqueID();
        if (playerId == null) {
            return defaultSkin(false);
        }
        AccountSkin skin = RESOLVED.get(playerId);
        if (skin == null
                || (skin.awaitingProfile && hasTextures(player.getGameProfile()))) {
            skin = create(player);
            RESOLVED.put(playerId, skin);
        }
        return skin;
    }

    private static boolean hasTextures(GameProfile profile) {
        return profile != null && profile.getProperties() != null
                && profile.getProperties().containsKey(
                        ProfileTexturesDecoder.TEXTURES_PROPERTY);
    }

    /** Forgets every per-player answer; the developer override reloads on next use. */
    public static void clear() {
        RESOLVED.clear();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.getTextureManager() != null
                && minecraft.getTextureManager().getTexture(OVERRIDE_TEXTURE) != null) {
            minecraft.getTextureManager().deleteTexture(OVERRIDE_TEXTURE);
        }
    }

    private static AccountSkin create(AbstractClientPlayer player) {
        Minecraft minecraft = Minecraft.getMinecraft();
        AccountSkin override = resolveOverride(minecraft, player);
        if (override != null) {
            return override;
        }

        GameProfile gameProfile = player.getGameProfile();
        AccountSkinProfile profile = ProfileTexturesDecoder.decode(gameProfile);
        BufferedImage placeholder = defaultImage(minecraft);
        if (profile == null || placeholder == null) {
            return defaultSkin(!hasTextures(gameProfile));
        }

        ResourceLocation location = new ResourceLocation(
                LostTalesMetaData.MOD_ID, TEXTURE_PREFIX + profile.getHash());
        TextureManager textures = minecraft.getTextureManager();
        if (textures.getTexture(location) == null) {
            textures.loadTexture(location, new AccountSkinTexture(
                    placeholder,
                    new RemoteSkinSource(profile.getUrl(),
                            vanillaCachedCopy(minecraft, profile.getHash())),
                    "account skin " + profile.getHash()));
        }
        return new AccountSkin(location,
                profile.isSlim() ? CharacterBodyTypeRegistry.SLIM
                        : CharacterBodyTypeRegistry.WIDE,
                true, false);
    }

    /**
     * The developer override: a local PNG drawn on the local player, with the
     * configured arm width. Lets modern skins be tested with an offline
     * account that has no profile textures.
     */
    private static AccountSkin resolveOverride(Minecraft minecraft,
                                               AbstractClientPlayer player) {
        String path = LostTalesConfig.devSkinOverridePath;
        if (path == null || path.trim().length() == 0
                || minecraft == null || minecraft.thePlayer != player) {
            return null;
        }
        String configured = CharacterBodyTypeRegistry.normalizeIdentifier(
                LostTalesConfig.devSkinOverrideBodyType);
        if (configured.indexOf(':') < 0) {
            configured = LostTalesMetaData.MOD_ID + ":" + configured;
        }
        String bodyTypeId = CharacterBodyTypeRegistry.normalizeOrWide(configured);
        TextureManager textures = minecraft.getTextureManager();
        if (textures.getTexture(OVERRIDE_TEXTURE) == null) {
            BufferedImage image = readOverride(new File(path.trim()));
            if (image == null) {
                return null;
            }
            textures.loadTexture(OVERRIDE_TEXTURE, new AccountSkinTexture(
                    image, null, "developer skin override"));
            if (!overrideLogged) {
                overrideLogged = true;
                FMLLog.info("[%s] Drawing the local player with the developer skin "
                        + "override %s (%s)", LostTalesMetaData.MOD_ID, path, bodyTypeId);
            }
        }
        return new AccountSkin(OVERRIDE_TEXTURE, bodyTypeId, true, false);
    }

    private static BufferedImage readOverride(File file) {
        try {
            BufferedImage image = file.isFile() ? ImageIO.read(file) : null;
            BufferedImage normalized = SkinImageNormalizer.normalize(image);
            if (normalized == null) {
                FMLLog.warning("[%s] Developer skin override %s is not a 64x32 or 64x64 PNG",
                        LostTalesMetaData.MOD_ID, file);
            }
            return normalized;
        } catch (IOException failure) {
            FMLLog.warning("[%s] Developer skin override %s could not be read: %s",
                    LostTalesMetaData.MOD_ID, file, failure.toString());
            return null;
        }
    }

    /** The default skin; provisional while the profile is still being fetched. */
    private static AccountSkin defaultSkin(boolean awaitingProfile) {
        Minecraft minecraft = Minecraft.getMinecraft();
        BufferedImage image = defaultImage(minecraft);
        if (image == null) {
            // The vanilla default is 64x32; the model samples it wrongly but
            // the player is still drawn rather than missing.
            return new AccountSkin(VANILLA_DEFAULT_SKIN,
                    CharacterBodyTypeRegistry.WIDE, false, awaitingProfile);
        }
        if (!defaultRegistered) {
            defaultRegistered = true;
            minecraft.getTextureManager().loadTexture(DEFAULT_TEXTURE,
                    new AccountSkinTexture(image, null, "default skin"));
        }
        return new AccountSkin(DEFAULT_TEXTURE, CharacterBodyTypeRegistry.WIDE, false,
                awaitingProfile);
    }

    /** Vanilla's Steve brought into the 64x64 layout, read once. */
    private static BufferedImage defaultImage(Minecraft minecraft) {
        if (defaultImage != null) {
            return defaultImage;
        }
        if (minecraft == null || minecraft.getResourceManager() == null) {
            return null;
        }
        InputStream stream = null;
        try {
            IResource resource = minecraft.getResourceManager().getResource(VANILLA_DEFAULT_SKIN);
            stream = resource.getInputStream();
            defaultImage = SkinImageNormalizer.normalize(ImageIO.read(stream));
        } catch (IOException failure) {
            FMLLog.warning("[%s] Could not read the default player skin: %s",
                    LostTalesMetaData.MOD_ID, failure.toString());
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // Nothing left to release.
                }
            }
        }
        return defaultImage;
    }

    /** Where vanilla's own downloader stores this hash; read only, never written. */
    private static File vanillaCachedCopy(Minecraft minecraft, String hash) {
        return VanillaSkinCacheAccess.cachedCopy(minecraft, hash);
    }
}
