package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterSkinDefinition;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Shared renderer for roleplaying-character portraits in client GUIs.
 *
 * <p>LOTR human, elf, dwarf, and hobbit textures store hair and beards on a
 * model-specific extended headwear cube rather than Minecraft's normal
 * 64x32 hat layer. This renderer composes that layer into a square portrait
 * so every HUD and map surface shows the same character appearance.</p>
 *
 * <p>A portrait is pixel art: one texel of the skin to one pixel on
 * screen, whichever layer it comes from and whichever model it belongs
 * to. The face is laid on whole display pixels and its cell is sampled
 * exactly, which together give every one of its texels the same number
 * of pixels; only the head's own rows of an extended cube are taken, so
 * nothing is ever squeezed into a square it does not fit. The headwear
 * is the one thing drawn larger than its texels — a quarter again,
 * about the face's centre, which is what stands it off as the model's
 * second cube stands off the first.</p>
 */
public final class LostTalesCharacterHeadIconRenderer {

    private static final ResourceLocation DEFAULT_PLAYER_SKIN =
            new ResourceLocation("textures/entity/steve.png");
    /**
     * How much larger the headwear is drawn than the face it lies on,
     * about their shared centre: a quarter again, which is what raises
     * the model's second cube off the first. The face keeps one texel
     * to one pixel and stays sharp; the headwear does not, and cannot —
     * eight texels over ten pixels is uneven however it is drawn — but
     * it is a hat, and the depth is worth more than its edges are.
     */
    private static final float OUTER_LAYER_SCALE = 1.25F;

    private static final Map<UUID, ResourceLocation> ACCOUNT_SKINS =
            new ConcurrentHashMap<UUID, ResourceLocation>();
    private static final Set<UUID> REQUESTED_ACCOUNT_SKINS =
            java.util.Collections.newSetFromMap(
                    new ConcurrentHashMap<UUID, Boolean>());
    private static final Map<String, float[]> NPC_TEXTURE_SIZES =
            new ConcurrentHashMap<String, float[]>();

    private LostTalesCharacterHeadIconRenderer() {}

    /**
     * Draws only a synchronized, configured roleplaying-character head.
     * Returns false so callers can retain their native account-skin fallback.
     */
    public static boolean drawRoleplayHead(Minecraft minecraft,
                                           UUID ownerId,
                                           float x,
                                           float y,
                                           float size,
                                           float brightness,
                                           float alpha) {
        ResolvedHead head = resolveConfiguredHead(ownerId);
        return head != null && drawResolvedHead(
                minecraft, head, x, y, size, brightness, alpha);
    }

    /** Draws a roleplaying head, falling back to the account skin if needed. */
    public static boolean drawHead(Minecraft minecraft,
                                   UUID ownerId,
                                   float x,
                                   float y,
                                   float size,
                                   float brightness,
                                   float alpha) {
        ResolvedHead head = resolveConfiguredHead(ownerId);
        if (head == null) {
            head = resolveAccountHead(minecraft, ownerId);
        }
        return drawResolvedHead(
                minecraft, head, x, y, size, brightness, alpha);
    }

    /** Draws the immutable character skin recorded on a chat message. */
    public static boolean drawSnapshotHead(Minecraft minecraft,
                                           UUID ownerId,
                                           String skinId,
                                           float x,
                                           float y,
                                           float size,
                                           float brightness,
                                           float alpha) {
        return drawResolvedHead(minecraft,
                resolveSnapshotHead(minecraft, ownerId, skinId),
                x, y, size, brightness, alpha);
    }

    /** Draws only the Minecraft account skin, never the active character skin. */
    public static boolean drawAccountHead(Minecraft minecraft,
                                          UUID ownerId,
                                          float x,
                                          float y,
                                          float size,
                                          float brightness,
                                          float alpha) {
        return drawResolvedHead(minecraft,
                resolveAccountHead(minecraft, ownerId),
                x, y, size, brightness, alpha);
    }

    /** Draws only the Minecraft account skin under a full colour tint. */
    public static boolean drawTintedAccountHead(
            Minecraft minecraft, UUID ownerId,
            float x, float y, float size,
            float red, float green, float blue, float alpha) {
        return drawResolvedHead(minecraft,
                resolveAccountHead(minecraft, ownerId),
                x, y, size, red, green, blue, alpha);
    }

    /** Account-skin shadow without enlarging the outer layer. */
    public static boolean drawTintedAccountHeadBase(
            Minecraft minecraft, UUID ownerId,
            float x, float y, float size,
            float red, float green, float blue, float alpha) {
        return drawResolvedHead(minecraft,
                resolveAccountHead(minecraft, ownerId),
                x, y, size, red, green, blue, alpha, false);
    }

    /** Immutable character-skin shadow without enlarging model features. */
    public static boolean drawTintedSnapshotHeadBase(
            Minecraft minecraft, UUID ownerId, String skinId,
            float x, float y, float size,
            float red, float green, float blue, float alpha) {
        return drawResolvedHead(minecraft,
                resolveSnapshotHead(minecraft, ownerId, skinId),
                x, y, size, red, green, blue, alpha, false);
    }

    /**
     * Draws an NPC portrait from an explicit skin texture path (as recorded
     * on a chat line by the NPC speech hook). LOTR humanoid NPCs use
     * biped-layout skins, so the face sits at the standard 8x8 region
     * scaled by the texture's actual dimensions; taller-than-wide LOTR
     * skins additionally carry their hair on the extended headwear region.
     */
    public static boolean drawNpcHead(Minecraft minecraft,
                                      String texturePath,
                                      float x,
                                      float y,
                                      float size,
                                      float brightness,
                                      float alpha) {
        return drawNpcHead(minecraft, texturePath, x, y, size,
                brightness, brightness, brightness, alpha, true);
    }

    /** NPC-portrait shadow without the raised headwear layer. */
    public static boolean drawTintedNpcHeadBase(
            Minecraft minecraft, String texturePath,
            float x, float y, float size,
            float red, float green, float blue, float alpha) {
        return drawNpcHead(minecraft, texturePath, x, y, size,
                red, green, blue, alpha, false);
    }

    private static boolean drawNpcHead(Minecraft minecraft,
                                       String texturePath,
                                       float x,
                                       float y,
                                       float size,
                                       float red,
                                       float green,
                                       float blue,
                                       float alpha,
                                       boolean drawFeatures) {
        if (minecraft == null || texturePath == null
                || texturePath.length() == 0 || size <= 0.0F
                || alpha <= 0.0F) {
            return false;
        }
        try {
            ResourceLocation location = new ResourceLocation(texturePath);
            float[] dimensions = measureNpcTexture(minecraft, location);
            float imageWidth = dimensions[0];
            float imageHeight = dimensions[1];
            // LOTR humanoid skins scale with their width; 64 is the biped
            // reference width the 8x8 face region is defined against.
            float unit = imageWidth / 64.0F;
            minecraft.getTextureManager().bindTexture(location);
            GL11.glColor4f(
                    Math.min(1.0F, red), Math.min(1.0F, green),
                    Math.min(1.0F, blue), Math.min(1.0F, alpha));
            drawTexturedQuad(
                    x, y, size, size,
                    8.0F * unit, 8.0F * unit,
                    8.0F * unit, 8.0F * unit,
                    imageWidth, imageHeight);
            if (drawFeatures && imageHeight >= imageWidth) {
                // As on a player's head: the headwear stands off the
                // face by growing about their shared centre.
                float outerSize = size * OUTER_LAYER_SCALE;
                float outerOffset = (outerSize - size) * 0.5F;
                drawNpcExtendedOverlay(x - outerOffset, y - outerOffset,
                        outerSize, unit, imageWidth, imageHeight);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void drawNpcExtendedOverlay(float x, float y,
                                               float size, float unit,
                                               float imageWidth,
                                               float imageHeight) {
        drawTexturedQuad(
                x, y, size, size,
                8.0F * unit, 40.0F * unit,
                8.0F * unit, 8.0F * unit,
                imageWidth, imageHeight);
    }

    private static float[] measureNpcTexture(Minecraft minecraft,
                                             ResourceLocation location) {
        float[] cached = NPC_TEXTURE_SIZES.get(location.toString());
        if (cached != null) {
            return cached;
        }
        float[] measured = new float[] {64.0F, 32.0F};
        java.io.InputStream stream = null;
        try {
            stream = minecraft.getResourceManager()
                    .getResource(location).getInputStream();
            java.awt.image.BufferedImage image =
                    javax.imageio.ImageIO.read(stream);
            if (image != null && image.getWidth() > 0
                    && image.getHeight() > 0) {
                measured = new float[] {
                        image.getWidth(), image.getHeight()};
            }
        } catch (Throwable ignored) {
            // Unreadable textures fall back to the classic 64x32 layout.
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (java.io.IOException ignored) {
                }
            }
        }
        NPC_TEXTURE_SIZES.put(location.toString(), measured);
        return measured;
    }

    /** Starts vanilla's asynchronous skin lookup for an OOC sender if needed. */
    public static void rememberAccountSkin(
            Minecraft minecraft, final UUID ownerId,
            String accountName) {
        if (minecraft == null || ownerId == null
                || accountName == null || accountName.length() == 0
                || ACCOUNT_SKINS.containsKey(ownerId)
                || !REQUESTED_ACCOUNT_SKINS.add(ownerId)) {
            return;
        }
        try {
            minecraft.func_152342_ad().func_152790_a(
                    new GameProfile(ownerId, accountName),
                    new SkinManager.SkinAvailableCallback() {
                        @Override
                        public void func_152121_a(
                                MinecraftProfileTexture.Type type,
                                ResourceLocation location) {
                            if (type == MinecraftProfileTexture.Type.SKIN
                                    && location != null) {
                                ACCOUNT_SKINS.put(ownerId, location);
                            }
                        }
                    }, true);
        } catch (RuntimeException ignored) {
            REQUESTED_ACCOUNT_SKINS.remove(ownerId);
        }
    }

    public static void clearAccountSkinCache() {
        ACCOUNT_SKINS.clear();
        REQUESTED_ACCOUNT_SKINS.clear();
    }

    private static ResolvedHead resolveConfiguredHead(UUID ownerId) {
        CharacterAppearance appearance =
                ClientCharacterAppearanceCache.getAuthoritative(ownerId);
        return appearance == null
                ? null : resolveConfiguredHead(appearance.getSkinId());
    }

    private static ResolvedHead resolveConfiguredHead(String skinId) {
        CharacterSkinDefinition configured =
                CharacterSkinRegistry.get(skinId);
        if (configured == null) {
            return null;
        }
        return new ResolvedHead(
                new ResourceLocation(configured.getTextureLocation()),
                CharacterHeadIconLayout.forConfiguredRace(
                        configured.getRaceId()));
    }

    private static ResolvedHead resolveSnapshotHead(
            Minecraft minecraft, UUID ownerId, String skinId) {
        ResolvedHead configured = resolveConfiguredHead(skinId);
        return configured == null
                ? resolveAccountHead(minecraft, ownerId) : configured;
    }

    private static ResolvedHead resolveAccountHead(
            Minecraft minecraft, UUID ownerId) {
        if (minecraft != null && minecraft.theWorld != null
                && minecraft.theWorld.playerEntities != null
                && ownerId != null) {
            for (Object value : minecraft.theWorld.playerEntities) {
                if (value instanceof AbstractClientPlayer) {
                    AbstractClientPlayer player = (AbstractClientPlayer) value;
                    if (ownerId.equals(player.getUniqueID())) {
                        ResourceLocation skin = player.getLocationSkin();
                        if (skin != null) {
                            ACCOUNT_SKINS.put(ownerId, skin);
                        }
                        return new ResolvedHead(
                                skin == null ? DEFAULT_PLAYER_SKIN : skin,
                                CharacterHeadIconLayout.minecraftSkin());
                    }
                } else if (value instanceof EntityPlayer
                        && ownerId.equals(
                        ((EntityPlayer) value).getUniqueID())) {
                    break;
                }
            }
        }
        ResourceLocation cached = ownerId == null
                ? null : ACCOUNT_SKINS.get(ownerId);
        return new ResolvedHead(
                cached == null ? DEFAULT_PLAYER_SKIN : cached,
                CharacterHeadIconLayout.minecraftSkin());
    }

    /**
     * Draws a head under a full colour tint rather than a grey brightness.
     *
     * <p>Drop shadows are not grey: the map and the compass HUD both back
     * their icons with one shared colour, so a shadow drawn here has to be
     * able to use it too.</p>
     */
    public static boolean drawTintedHead(Minecraft minecraft,
                                         UUID ownerId,
                                         float x,
                                         float y,
                                         float size,
                                         float red,
                                         float green,
                                         float blue,
                                         float alpha) {
        ResolvedHead head = resolveConfiguredHead(ownerId);
        if (head == null) {
            head = resolveAccountHead(minecraft, ownerId);
        }
        return drawResolvedHead(
                minecraft, head, x, y, size, red, green, blue, alpha);
    }

    private static boolean drawResolvedHead(Minecraft minecraft,
                                            ResolvedHead head,
                                            float x,
                                            float y,
                                            float size,
                                            float brightness,
                                            float alpha) {
        return drawResolvedHead(minecraft, head, x, y, size,
                brightness, brightness, brightness, alpha, true);
    }

    private static boolean drawResolvedHead(Minecraft minecraft,
                                            ResolvedHead head,
                                            float x,
                                            float y,
                                            float size,
                                            float red,
                                            float green,
                                            float blue,
                                            float alpha) {
        return drawResolvedHead(minecraft, head, x, y, size,
                red, green, blue, alpha, true);
    }

    private static boolean drawResolvedHead(Minecraft minecraft,
                                            ResolvedHead head,
                                            float x,
                                            float y,
                                            float size,
                                            float red,
                                            float green,
                                            float blue,
                                            float alpha,
                                            boolean drawFeatures) {
        if (minecraft == null || head == null || size <= 0.0F
                || alpha <= 0.0F
                || (red <= 0.0F && green <= 0.0F && blue <= 0.0F)) {
            return false;
        }
        try {
            minecraft.getTextureManager().bindTexture(head.location);
            // A head is drawn wherever a caller wants one, and a GUI
            // panel drawn just before it may have left blending off —
            // vanilla's drawRect does. Without it a head at half opacity,
            // a shadow above all, would land solid.
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glColor4f(
                    Math.min(1.0F, red), Math.min(1.0F, green),
                    Math.min(1.0F, blue), Math.min(1.0F, alpha));
            CharacterHeadIconLayout layout = head.layout;
            drawTexturedQuad(
                    x, y, size, size,
                    layout.getFaceX(), layout.getFaceY(),
                    layout.getFaceSize(), layout.getFaceSize(),
                    64.0F, layout.getImageHeight());

            if (drawFeatures && (layout.getOverlayKind()
                    == CharacterHeadIconLayout.OverlayKind.MINECRAFT
                    || layout.getOverlayKind()
                    == CharacterHeadIconLayout.OverlayKind.LOTR_EXTENDED)) {
                // The headwear stands off the face by growing about
                // their shared centre — the second cube is larger than
                // the first on the model too. No shade under it: the
                // size is the depth.
                float outerSize = size * OUTER_LAYER_SCALE;
                float outerOffset = (outerSize - size) * 0.5F;
                drawOuterOverlay(layout, x - outerOffset, y - outerOffset,
                        outerSize);
            } else if (drawFeatures && layout.getOverlayKind()
                    == CharacterHeadIconLayout.OverlayKind.LOTR_ORC_FEATURES) {
                drawOrcNose(x, y, size, layout.getImageHeight());
            } else if (drawFeatures && layout.getOverlayKind()
                    == CharacterHeadIconLayout.OverlayKind
                    .LOTR_HALF_TROLL_FEATURES) {
                drawHalfTrollNose(x, y, size, layout.getImageHeight());
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void drawOuterOverlay(CharacterHeadIconLayout layout,
                                         float x,
                                         float y,
                                         float size) {
        if (layout.getOverlayKind()
                == CharacterHeadIconLayout.OverlayKind.MINECRAFT) {
            drawTexturedQuad(
                    x, y, size, size,
                    40.0F, 8.0F, 8.0F, 8.0F,
                    64.0F, layout.getImageHeight());
        } else {
            drawLotrExtendedOverlay(x, y, size, layout.getFaceSize(),
                    layout.getImageHeight());
        }
    }

    /** Front face of ModelOrc's centered 1x2x1 nose cuboid. */
    private static void drawOrcNose(float x, float y, float size,
                                    float imageHeight) {
        float unit = size / 8.0F;
        drawTexturedQuad(
                x + 3.5F * unit, y + 4.0F * unit,
                unit, 2.0F * unit,
                15.0F, 18.0F, 1.0F, 2.0F,
                64.0F, imageHeight);
    }

    /** Front face of ModelHalfTroll's 8x3x2 muzzle cuboid. */
    private static void drawHalfTrollNose(float x, float y, float size,
                                          float imageHeight) {
        float unit = size / 10.0F;
        drawTexturedQuad(
                x + unit, y + 7.0F * unit,
                8.0F * unit, 3.0F * unit,
                42.0F, 7.0F, 8.0F, 3.0F,
                64.0F, imageHeight);
    }

    /**
     * The head's own square of LOTR's extended headwear cube. Only that
     * square: the cube carries hair below the chin for some races, and
     * squeezing those extra rows into a square portrait would stretch
     * every texel in it. A portrait shows the head, so it takes the
     * head's rows and leaves the rest on the model.
     */
    private static void drawLotrExtendedOverlay(float x,
                                                float y,
                                                float size,
                                                float faceSize,
                                                float imageHeight) {
        drawTexturedQuad(
                x, y, size, size,
                8.0F, 40.0F, faceSize, faceSize,
                64.0F, imageHeight);
    }

    /**
     * One cell of a skin, drawn as a quad. The sample rectangle is the
     * cell exactly — no inset. A skin is pixel art sampled with no
     * filtering, and the quad is laid on whole display pixels, so every
     * destination pixel's centre falls strictly inside a texel and each
     * texel gets the same number of pixels. Holding the rectangle even
     * a quarter of a texel inside the cell samples less than the whole
     * cell and spreads what is left unevenly: eight texels over
     * twenty-four pixels becomes seven and a half, and the columns come
     * out three, then four, then two pixels wide.
     */
    private static void drawTexturedQuad(float x,
                                         float y,
                                         float width,
                                         float height,
                                         float textureX,
                                         float textureY,
                                         float textureWidth,
                                         float textureHeight,
                                         float imageWidth,
                                         float imageHeight) {
        double u0 = textureX / imageWidth;
        double u1 = (textureX + textureWidth) / imageWidth;
        double v0 = textureY / imageHeight;
        double v1 = (textureY + textureHeight) / imageHeight;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0D, u0, v1);
        tessellator.addVertexWithUV(
                x + width, y + height, 0.0D, u1, v1);
        tessellator.addVertexWithUV(x + width, y, 0.0D, u1, v0);
        tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
        tessellator.draw();
    }

    private static final class ResolvedHead {
        private final ResourceLocation location;
        private final CharacterHeadIconLayout layout;

        private ResolvedHead(ResourceLocation location,
                             CharacterHeadIconLayout layout) {
            this.location = location;
            this.layout = layout;
        }
    }
}
