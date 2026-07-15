package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterSkinDefinition;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.Tessellator;
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
 */
public final class LostTalesCharacterHeadIconRenderer {

    private static final ResourceLocation DEFAULT_PLAYER_SKIN =
            new ResourceLocation("textures/entity/steve.png");

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

    private static ResolvedHead resolveConfiguredHead(UUID ownerId) {
        CharacterAppearance appearance =
                ClientCharacterAppearanceCache.getAuthoritative(ownerId);
        CharacterSkinDefinition configured = appearance == null
                ? null : CharacterSkinRegistry.get(appearance.getSkinId());
        if (configured == null) {
            return null;
        }
        return new ResolvedHead(
                new ResourceLocation(configured.getTextureLocation()),
                CharacterHeadIconLayout.forConfiguredRace(
                        configured.getRaceId()));
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
                        return new ResolvedHead(
                                player.getLocationSkin(),
                                CharacterHeadIconLayout.minecraftSkin());
                    }
                } else if (value instanceof EntityPlayer
                        && ownerId.equals(
                        ((EntityPlayer) value).getUniqueID())) {
                    break;
                }
            }
        }
        return new ResolvedHead(
                DEFAULT_PLAYER_SKIN,
                CharacterHeadIconLayout.minecraftSkin());
    }

    private static boolean drawResolvedHead(Minecraft minecraft,
                                            ResolvedHead head,
                                            float x,
                                            float y,
                                            float size,
                                            float brightness,
                                            float alpha) {
        if (minecraft == null || head == null || size <= 0.0F
                || brightness <= 0.0F || alpha <= 0.0F) {
            return false;
        }
        try {
            minecraft.getTextureManager().bindTexture(head.location);
            float color = Math.min(1.0F, brightness);
            GL11.glColor4f(color, color, color, Math.min(1.0F, alpha));
            CharacterHeadIconLayout layout = head.layout;
            drawTexturedQuad(
                    x, y, size, size,
                    layout.getFaceX(), layout.getFaceY(),
                    layout.getFaceSize(), layout.getFaceSize(),
                    64.0F, layout.getImageHeight());

            if (layout.getOverlayKind()
                    == CharacterHeadIconLayout.OverlayKind.MINECRAFT) {
                drawTexturedQuad(
                        x, y, size, size,
                        40.0F, 8.0F, 8.0F, 8.0F,
                        64.0F, layout.getImageHeight());
            } else if (layout.getOverlayKind()
                    == CharacterHeadIconLayout.OverlayKind.LOTR_EXTENDED) {
                drawLotrExtendedOverlay(
                        x, y, size,
                        layout.getExtendedOverlayHeight(),
                        layout.getImageHeight());
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /**
     * The upper eight rows cover the face and contain most hair. Remaining
     * rows hang below the 3D head as a beard; they are composed over the lower
     * half so the square icon retains both the face and beard silhouette.
     */
    private static void drawLotrExtendedOverlay(float x,
                                                float y,
                                                float size,
                                                float overlayHeight,
                                                float imageHeight) {
        drawTexturedQuad(
                x, y, size, size,
                8.0F, 40.0F, 8.0F, 8.0F,
                64.0F, imageHeight);
        float beardHeight = Math.max(0.0F, overlayHeight - 8.0F);
        if (beardHeight > 0.0F) {
            drawTexturedQuad(
                    x, y + size * 0.5F, size, size * 0.5F,
                    8.0F, 48.0F, 8.0F, beardHeight,
                    64.0F, imageHeight);
        }
    }

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
