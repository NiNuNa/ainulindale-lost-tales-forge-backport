package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterSkinDefinition;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.client.skin.LostTalesAccountSkins;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesUiCornerCut;
import com.ninuna.losttales.gui.style.LostTalesUiFlatLayers;
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
 *
 * <p>A head always stands on the display's own grid, wherever the caller
 * puts it ({@link LostTalesDisplayPixels#snapShift}): the headwear's
 * texels do not fill whole pixels, so how many pixels each one takes
 * depends on where the head stands, and a head carried by fractions of
 * a pixel — a chat scrolling under it — would shuffle them every frame;
 * and where one of their edges falls on a pixel's middle, the pixel
 * would show one texel or the other by rounding alone. On the grid, a
 * hair past each pixel's edge, every texel keeps its pixels, and the
 * head moves as the text beside it does.</p>
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

    /**
     * The corner every head drawn right now gives up, in the units the
     * head is drawn in: a head wearing a mark — its presence sphere, or a
     * tab's unread mark — is cut away from it so the mark sits in the
     * head rather than on it, with a clear pixel between them. Set around
     * a head's draw and cleared after it, like the silhouette state, so a
     * head is cut whatever matrix it is drawn in and whichever of its
     * layers is being drawn.
     */
    private static LostTalesUiCornerCut cut = LostTalesUiCornerCut.NONE;
    /**
     * How far the head being drawn was moved onto the display's grid; the
     * cut, given where the caller put the head, moves with it.
     */
    private static float shiftX;
    private static float shiftY;
    private static final float[] SHIFT = new float[2];

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
        LostTalesDisplayPixels.snapShift(x, y, SHIFT);
        x += SHIFT[0];
        y += SHIFT[1];
        shiftX = SHIFT[0];
        shiftY = SHIFT[1];
        try {
            ResourceLocation location = new ResourceLocation(texturePath);
            float[] dimensions = measureNpcTexture(minecraft, location);
            float imageWidth = dimensions[0];
            float imageHeight = dimensions[1];
            // LOTR humanoid skins scale with their width; 64 is the biped
            // reference width the 8x8 face region is defined against.
            float unit = imageWidth / 64.0F;
            minecraft.getTextureManager().bindTexture(location);
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
            drawTexturedQuad(
                    x, y, size, size,
                    8.0F * unit, 8.0F * unit,
                    8.0F * unit, 8.0F * unit,
                    imageWidth, imageHeight);
            LostTalesUiFlatLayers.nextLayer();
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
            shiftX = 0.0F;
            shiftY = 0.0F;
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

    /**
     * Cuts every head drawn until {@link #endCorner} by {@code corner},
     * given where the caller puts the head. Always paired in a
     * {@code finally}, so a head that fails to draw does not leave the
     * cut standing.
     */
    public static void beginCorner(LostTalesUiCornerCut corner) {
        cut = corner == null ? LostTalesUiCornerCut.NONE : corner;
    }

    /** Ends the cut {@link #beginCorner} opened. */
    public static void endCorner() {
        cut = LostTalesUiCornerCut.NONE;
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
        if (configured == null || configured.isAccountSkin()) {
            // An account skin is the player's own head.
            return null;
        }
        return new ResolvedHead(
                new ResourceLocation(configured.getTextureLocation()),
                CharacterHeadIconLayout.forConfiguredRace(
                        configured.getRaceId()));
    }

    static ResolvedHead resolveSnapshotHead(
            Minecraft minecraft, UUID ownerId, String skinId) {
        ResolvedHead configured = resolveConfiguredHead(skinId);
        return configured == null
                ? resolveAccountHead(minecraft, ownerId) : configured;
    }

    static ResolvedHead resolveAccountHead(
            Minecraft minecraft, UUID ownerId) {
        if (minecraft != null && minecraft.theWorld != null
                && minecraft.theWorld.playerEntities != null
                && ownerId != null) {
            for (Object value : minecraft.theWorld.playerEntities) {
                if (value instanceof AbstractClientPlayer) {
                    AbstractClientPlayer player = (AbstractClientPlayer) value;
                    if (ownerId.equals(player.getUniqueID())) {
                        // The 64x64 copy the body renderer draws with, so
                        // the portrait and the player never disagree.
                        ResourceLocation skin =
                                LostTalesAccountSkins.resolve(player).getTexture();
                        if (skin != null) {
                            ACCOUNT_SKINS.put(ownerId, skin);
                        }
                        return new ResolvedHead(
                                skin == null ? DEFAULT_PLAYER_SKIN : skin,
                                CharacterHeadIconLayout.forAccountTexture(
                                        skin == null ? null : skin.getResourceDomain()));
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
                CharacterHeadIconLayout.forAccountTexture(
                        cached == null ? null : cached.getResourceDomain()));
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

    static boolean drawResolvedHead(Minecraft minecraft,
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
        LostTalesDisplayPixels.snapShift(x, y, SHIFT);
        x += SHIFT[0];
        y += SHIFT[1];
        shiftX = SHIFT[0];
        shiftY = SHIFT[1];
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
            // Whatever the face wears stands over it: a translucent head
            // is one picture, not a face showing through its hat.
            LostTalesUiFlatLayers.nextLayer();

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
            shiftX = 0.0F;
            shiftY = 0.0F;
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
    static void drawTexturedQuad(float x,
                                 float y,
                                 float width,
                                 float height,
                                 float textureX,
                                 float textureY,
                                 float textureWidth,
                                 float textureHeight,
                                 float imageWidth,
                                 float imageHeight) {
        drawTexturedQuad(x, y, width, height, textureX, textureY,
                textureWidth, textureHeight, imageWidth, imageHeight, false);
    }

    /**
     * As above, optionally left-to-right.
     *
     * <p>A skin with no limbs of its own on the left paints one side and
     * the model mirrors it, so a figure drawn from such a skin mirrors it
     * too. The cell is still sampled whole — only which edge is which
     * changes — so every texel keeps its pixels.</p>
     */
    static void drawTexturedQuad(float x,
                                 float y,
                                 float width,
                                 float height,
                                 float textureX,
                                 float textureY,
                                 float textureWidth,
                                 float textureHeight,
                                 float imageWidth,
                                 float imageHeight,
                                 boolean mirrored) {
        double u0 = textureX / imageWidth;
        double u1 = (textureX + textureWidth) / imageWidth;
        if (mirrored) {
            double swap = u0;
            u0 = u1;
            u1 = swap;
        }
        double v0 = textureY / imageHeight;
        double v1 = (textureY + textureHeight) / imageHeight;
        LostTalesUiCornerCut corner = cut;
        if (corner.isNone()) {
            quad(x, y, x + width, y + height, u0, v0, u1, v1);
            return;
        }
        // The corner a head gives up to its mark, band by band: the rows
        // above the cut whole, and each band of the cut up to where it
        // cuts from. Each piece keeps the texels it covers, so every
        // layer of the head is cut in the same place whatever size it is
        // drawn at. The cut was given where the caller put the head; the
        // head was moved onto the display's grid, and the cut goes with
        // it.
        float right = x + width;
        float bottom = y + height;
        float firstBand = Math.min(bottom, corner.bandTop(0) + shiftY);
        if (firstBand > y) {
            quadPart(x, y, width, height, u0, v0, u1, v1,
                    x, y, right, firstBand);
        }
        for (int band = 0; band < corner.bands(); band++) {
            float top = Math.max(y, corner.bandTop(band) + shiftY);
            float end = band + 1 < corner.bands()
                    ? Math.min(bottom, corner.bandTop(band + 1) + shiftY)
                    : bottom;
            float kept = Math.min(right, corner.bandColumn(band) + shiftX);
            if (end > top && kept > x) {
                quadPart(x, y, width, height, u0, v0, u1, v1,
                        x, top, kept, end);
            }
        }
    }

    /** One piece of a quad, with the texels that piece covers. */
    private static void quadPart(float x, float y, float width, float height,
                                 double u0, double v0, double u1, double v1,
                                 float left, float top, float right,
                                 float bottom) {
        quad(left, top, right, bottom,
                lerp(u0, u1, (left - x) / width),
                lerp(v0, v1, (top - y) / height),
                lerp(u0, u1, (right - x) / width),
                lerp(v0, v1, (bottom - y) / height));
    }

    private static double lerp(double from, double to, float share) {
        return from + (to - from) * share;
    }

    private static void quad(float left, float top, float right, float bottom,
                             double u0, double v0, double u1, double v1) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(left, bottom, 0.0D, u0, v1);
        tessellator.addVertexWithUV(right, bottom, 0.0D, u1, v1);
        tessellator.addVertexWithUV(right, top, 0.0D, u1, v0);
        tessellator.addVertexWithUV(left, top, 0.0D, u0, v0);
        tessellator.draw();
    }

    static final class ResolvedHead {
        final ResourceLocation location;
        final CharacterHeadIconLayout layout;

        private ResolvedHead(ResourceLocation location,
                             CharacterHeadIconLayout layout) {
            this.location = location;
            this.layout = layout;
        }
    }
}
