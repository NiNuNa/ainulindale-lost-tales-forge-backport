package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.CharacterGuiPreviewLayout;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.render.player.LostTalesCharacterFigureRenderer;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiLayerFade;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * The figure in the Characters tab (C3 a): the character played stands
 * live, in its armour, with what it holds and its cape; any other is
 * posed from its look, as the creator's stage draws it. Dragging turns
 * it and tilts it a little, the wheel over it zooms. It is clipped to its
 * box, sorts its own faces with the depth it clears after itself, and
 * fades in with its window as one picture.
 */
final class CharacterFigureStage {
    /** How far a drag turns and tilts the figure, per pixel. */
    private static final float TURN_PER_PIXEL = 1.4F;
    private static final float TILT_PER_PIXEL = 0.45F;
    private static final float TILT_LIMIT = 35.0F;
    /** The zoom's bounds and its step a wheel notch. */
    private static final float ZOOM_MIN = 0.6F;
    private static final float ZOOM_MAX = 1.6F;
    private static final float ZOOM_STEP = 0.1F;
    /** The figure turned a little, so it reads as a body, not a card. */
    private static final float RESTING_YAW = 25.0F;
    /** Room above the head and below the feet in the box, in pixels. */
    private static final int HEAD_ROOM = 14;
    private static final int FOOT_ROOM = 8;
    /** Blocks a figure's box is fitted to: a tall race and its hair. */
    private static final float FITTED_BLOCKS = 2.2F;

    private final LostTalesUiLayerFade fade = new LostTalesUiLayerFade();
    private float yaw = RESTING_YAW;
    private float pitch;
    private float zoom = 1.0F;
    /** The zoom on screen, gliding to the one the wheel gave. */
    private float shownZoom = 1.0F;
    private long zoomNanos;
    private boolean turning;
    private double lastX;
    private double lastY;

    /** A new character picked stands as it rests. */
    void reset() {
        this.yaw = RESTING_YAW;
        this.pitch = 0.0F;
        this.zoom = 1.0F;
        this.shownZoom = 1.0F;
        this.turning = false;
    }

    /**
     * Draws the figure in {@code box}, in the page's own space: the live
     * {@code entity} when there is one, else the look, whose race sizes
     * either. {@code pageClipX} and {@code pageClipY} are where the page's
     * box stands on the screen, for the fade's copy.
     */
    void draw(Minecraft minecraft, LostTalesUiHitBox box, double pageClipX,
              double pageClipY, EntityLivingBase entity, UUID ownerId,
              CharacterAppearance look, int alpha) {
        if (box.width <= 0 || box.height <= 0 || alpha <= 0
                || (entity == null && look == null)) {
            return;
        }
        glideZoom();
        String raceId = look == null ? "" : look.getRaceId();
        int fitted = Math.max(1, Math.round((float)(box.height - HEAD_ROOM
                - FOOT_ROOM) / FITTED_BLOCKS));
        float scale = Math.max(1.0F, CharacterGuiPreviewLayout.scale(raceId,
                fitted) * this.shownZoom);
        float centreX = (float)(box.left + box.width / 2.0D);
        float feetY = CharacterGuiPreviewLayout.baselineY(raceId,
                (int)Math.round(box.bottom()) - FOOT_ROOM);
        boolean fading = alpha < 255 && this.fade.begin(minecraft,
                pageClipX + box.left, pageClipY + box.top, box.width,
                box.height);
        boolean clipped = LostTalesUiClip.beginLocal(minecraft,
                (float)box.left, (float)box.top, (float)box.right(),
                (float)box.bottom());
        try {
            // Whatever the page drew leaves no depth; the figure starts
            // from none and takes its own back out once drawn.
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            if (entity != null) {
                drawLive(entity, centreX, feetY, scale);
            } else {
                LostTalesCharacterFigureRenderer.drawPosedFigure(minecraft,
                        ownerId, look, centreX, feetY, scale, this.yaw,
                        this.pitch, 0.0F, 0.0F, 1.0F, 1.0F);
            }
        } finally {
            LostTalesUiClip.end(clipped);
            if (fading) {
                this.fade.end(minecraft, alpha / 255.0F);
            }
        }
    }

    /**
     * The played character itself, as the inventory stands a living thing
     * in a screen, lit as a portrait rather than by the world's hour.
     */
    private void drawLive(EntityLivingBase entity, float x, float y,
                          float scale) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT
                | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        float renderYawOffset = entity.renderYawOffset;
        float rotationYaw = entity.rotationYaw;
        float rotationPitch = entity.rotationPitch;
        float prevRotationYawHead = entity.prevRotationYawHead;
        float rotationYawHead = entity.rotationYawHead;
        float playerViewY = RenderManager.instance.playerViewY;
        boolean debugBoundingBox = RenderManager.debugBoundingBox;
        // The world's light map would darken it with the hour.
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        boolean lightmap = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        try {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_COLOR_MATERIAL);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glTranslatef(x, y, 50.0F);
            GL11.glScalef(-scale, scale, scale);
            GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);
            // RenderPlayer takes yOffset off the height it draws at; the
            // inventory puts it back first.
            GL11.glTranslatef(0.0F, entity.yOffset, 0.0F);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glRotatef(135.0F, 0.0F, 1.0F, 0.0F);
            RenderHelper.enableStandardItemLighting();
            GL11.glRotatef(-135.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-this.pitch, 1.0F, 0.0F, 0.0F);
            entity.renderYawOffset = this.yaw;
            entity.rotationYaw = this.yaw;
            entity.rotationYawHead = this.yaw;
            entity.prevRotationYawHead = this.yaw;
            entity.rotationPitch = this.pitch * 0.25F;
            RenderManager.instance.playerViewY = 180.0F;
            RenderManager.debugBoundingBox = false;
            RenderManager.instance.renderEntityWithPosYaw(entity, 0.0D, 0.0D,
                    0.0D, 0.0F, 1.0F);
        } finally {
            entity.renderYawOffset = renderYawOffset;
            entity.rotationYaw = rotationYaw;
            entity.rotationPitch = rotationPitch;
            entity.prevRotationYawHead = prevRotationYawHead;
            entity.rotationYawHead = rotationYawHead;
            RenderManager.instance.playerViewY = playerViewY;
            RenderManager.debugBoundingBox = debugBoundingBox;
            GL11.glPopMatrix();
            // What it wrote into the depth would reject what is drawn over it.
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            RenderHelper.disableStandardItemLighting();
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            if (lightmap) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            } else {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glPopAttrib();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /** The zoom on screen follows the one asked for, as a list glides to where it was scrolled. */
    private void glideZoom() {
        long now = System.nanoTime();
        double elapsed = this.zoomNanos == 0L ? 0.0D
                : (now - this.zoomNanos) / 1.0E9D;
        this.zoomNanos = now;
        this.shownZoom = (float)Motions.followTravel(
                MotionIds.SCREEN_CHARACTERS_GLIDE, this.shownZoom, this.zoom,
                elapsed);
    }

    /* ---- The hand ---- */

    /** A press on the figure takes hold of it. */
    void grab(double x, double y) {
        this.turning = true;
        this.lastX = x;
        this.lastY = y;
    }

    /** Held, the figure turns with the hand and tilts a little. */
    void drag(double x, double y) {
        if (!this.turning) {
            return;
        }
        this.yaw = turnedBy(this.yaw, (float)(x - this.lastX));
        this.pitch = Math.max(-TILT_LIMIT, Math.min(TILT_LIMIT,
                this.pitch + (float)(y - this.lastY) * TILT_PER_PIXEL));
        this.lastX = x;
        this.lastY = y;
    }

    void release() {
        this.turning = false;
    }

    /** A wheel turn zooms it, a tenth a notch, within its bounds. */
    void zoomBy(int notches) {
        this.zoom = zoomed(this.zoom, notches);
    }

    /** A yaw turned by a drag of {@code pixels}, kept within a turn. */
    static float turnedBy(float yaw, float pixels) {
        float turned = (yaw + pixels * TURN_PER_PIXEL) % 360.0F;
        return turned < 0.0F ? turned + 360.0F : turned;
    }

    /** A zoom after {@code notches} of the wheel, towards the viewer for positive ones. */
    static float zoomed(float zoom, int notches) {
        return Math.max(ZOOM_MIN, Math.min(ZOOM_MAX,
                zoom + notches * ZOOM_STEP));
    }
}
