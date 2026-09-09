package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.character.physics.CharacterCameraHook;
import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;

/**
 * The camera a screen borrows to show the player their own character in
 * the world: stood in front of it, orbiting it as the player drags,
 * framing it where the screen asks.
 *
 * <p>The creator uses it in place of a drawn model. Skyrim does the same:
 * there is no doll in a box, the camera simply steps round to face you.
 * While a screen holds the camera, the perspective is third person
 * whatever it was before, the orbit is the screen's rather than the
 * player's look direction, and the character is placed at a chosen point
 * and size on the screen so it sits beside the screen's own panels. When
 * the screen lets go, the perspective the player had comes back.</p>
 *
 * <p>It works through the same seams in {@code EntityRenderer.orientCamera}
 * as the third-person overhaul: the hooks ask here first, and the orbit is
 * applied as a correction to vanilla's own third-person transform, so it
 * needs no seam of its own and does not depend on the overhaul being
 * switched on. It does depend on the transformer having patched the
 * seams; {@link #isAvailable()} says whether it has.</p>
 *
 * <p>Only presentation is touched. The player's own rotation, position and
 * the server's view of either are never changed; the character stands and
 * looks where it did.</p>
 */
public final class ThirdPersonCameraInspection {

    private static final float VANILLA_EYE_HEIGHT = 1.62F;
    private static final double DEFAULT_FOV = 70.0D;

    /** The screen holding the camera, or null. */
    private static Object owner;
    /** The perspective the player had before the screen took the camera. */
    private static int previousPerspective;
    private static float yawOffset;
    private static float pitch;
    private static float zoom = 1.0F;
    private static double screenX;
    private static double screenY;
    private static double heightFraction = 0.5D;
    private static double focus = 0.5D;
    private static double lastFov = DEFAULT_FOV;
    /** What the last distance pass worked out, for the offset pass that follows it. */
    private static Placement placement;

    private ThirdPersonCameraInspection() {}

    /** Whether the camera seams are patched, so borrowing the camera can work at all. */
    public static boolean isAvailable() {
        return Boolean.getBoolean(ThirdPersonCameraHooks.ACTIVE_PROPERTY);
    }

    /**
     * A screen takes the camera. Safe to call again from the same screen,
     * as a screen does when the window is resized.
     */
    public static synchronized void begin(Object screen, Minecraft minecraft) {
        if (screen == null || minecraft == null || minecraft.gameSettings == null) {
            return;
        }
        if (owner != screen) {
            previousPerspective = minecraft.gameSettings.thirdPersonView;
            owner = screen;
            // The last holder's framing is not this one's: the world is
            // drawn before the new screen's first frame says where it
            // wants the character.
            resetFraming();
        }
        minecraft.gameSettings.thirdPersonView = 1;
    }

    private static void resetFraming() {
        placement = null;
        yawOffset = 0.0F;
        pitch = 0.0F;
        zoom = 1.0F;
        screenX = 0.0D;
        screenY = 0.0D;
        heightFraction = 0.5D;
        focus = 0.5D;
    }

    /**
     * Where the screen wants the character this frame.
     *
     * @param orbitYaw       degrees the camera has been dragged round from
     *                       facing the character; positive for a drag to
     *                       the right, which carries the camera that way
     * @param orbitPitch     degrees the camera looks down at the character
     * @param magnification  how much nearer than the fitted distance
     * @param subjectScreenX where the character's middle should sit across
     *                       the screen, from minus one to one
     * @param subjectScreenY where it should sit down the screen, from minus
     *                       one at the foot to one at the top
     * @param subjectHeight  the share of the screen's height the character
     *                       should fill at a magnification of one
     * @param subjectFocus   the point on the body that sits at that screen
     *                       position, from the feet at zero to the top of
     *                       the head at one
     */
    public static synchronized void frame(Object screen, float orbitYaw,
                                          float orbitPitch, float magnification,
                                          double subjectScreenX, double subjectScreenY,
                                          double subjectHeight, double subjectFocus) {
        if (screen == null || owner != screen) {
            return;
        }
        yawOffset = orbitYaw;
        pitch = orbitPitch;
        zoom = magnification;
        screenX = subjectScreenX;
        screenY = subjectScreenY;
        heightFraction = subjectHeight;
        focus = subjectFocus;
    }

    /** The screen gives the camera back, and the player's perspective with it. */
    public static synchronized void end(Object screen, Minecraft minecraft) {
        if (screen == null || owner != screen) {
            return;
        }
        owner = null;
        placement = null;
        restorePerspective(minecraft);
    }

    /**
     * Dropped with the rest of the client's camera state when a world is
     * left. A screen still holding the camera then gets no other chance
     * to give the perspective back, since its own close finds no owner,
     * so it is given back here.
     */
    public static synchronized void reset() {
        if (owner != null) {
            restorePerspective(Minecraft.getMinecraft());
        }
        owner = null;
        resetFraming();
        lastFov = DEFAULT_FOV;
    }

    /** Puts the perspective back, unless the player changed it while the camera was borrowed. */
    private static void restorePerspective(Minecraft minecraft) {
        if (minecraft != null && minecraft.gameSettings != null
                && minecraft.gameSettings.thirdPersonView == 1) {
            minecraft.gameSettings.thirdPersonView = previousPerspective;
        }
    }

    /** Whether the camera is borrowed right now, for that view entity. */
    public static synchronized boolean isActive(Minecraft minecraft,
                                                EntityLivingBase viewEntity) {
        return owner != null && minecraft != null && viewEntity != null
                && minecraft.currentScreen == owner
                && minecraft.thePlayer != null
                && minecraft.thePlayer == viewEntity
                && minecraft.renderViewEntity == viewEntity
                && minecraft.gameSettings != null
                && minecraft.gameSettings.thirdPersonView > 0
                && !minecraft.gameSettings.debugCamEnable
                && viewEntity.isEntityAlive();
    }

    /** The distance seam: works out this frame's placement and answers how far back the camera stands. */
    static synchronized double resolveDistance(EntityLivingBase viewEntity,
                                               float partialTicks) {
        placement = place(viewEntity, partialTicks);
        return placement.distance;
    }

    /** The FOV seam: nothing changes, but the value is what the framing is worked out from. */
    static synchronized float recordFov(float vanillaFov) {
        if (vanillaFov > 1.0F && vanillaFov < 179.0F) {
            lastFov = vanillaFov;
        }
        return vanillaFov;
    }

    /**
     * The offset seam. Vanilla is about to pull the camera back along the
     * player's own look direction; this takes that back and stands the
     * camera on the orbit instead.
     *
     * <p>The modelview at this point maps the world so the eye is at the
     * origin and, once vanilla's rotations after this seam are applied,
     * looks along the player's view. Each step here is one factor of the
     * correction, in the order OpenGL composes them: the sideways and
     * upward framing, the pull-back along the orbit, the orbit's own
     * rotation, the drop from the eye to the middle of the body, the
     * inverse of the player's rotation, and the undoing of vanilla's
     * pull-back. What survives is: frame, pull back, look along the orbit
     * at the body's middle.</p>
     */
    static synchronized void applyCameraOffset(EntityLivingBase viewEntity,
                                               float partialTicks,
                                               double actualDistance) {
        Placement current = placement != null
                ? placement : place(viewEntity, partialTicks);
        float entityYaw = viewEntity.prevRotationYaw
                + (viewEntity.rotationYaw - viewEntity.prevRotationYaw) * partialTicks;
        float entityPitch = viewEntity.prevRotationPitch
                + (viewEntity.rotationPitch - viewEntity.prevRotationPitch) * partialTicks;
        GL11.glTranslatef((float)-current.side, (float)-current.vertical, 0.0F);
        GL11.glTranslatef(0.0F, 0.0F, (float)-current.distance);
        GL11.glRotatef((float)current.pitch, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef((float)(current.yaw + 180.0D), 0.0F, 1.0F, 0.0F);
        GL11.glTranslatef(0.0F, (float)current.drop, 0.0F);
        GL11.glRotatef(-(entityYaw + 180.0F), 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-entityPitch, 1.0F, 0.0F, 0.0F);
        GL11.glTranslatef(0.0F, 0.0F, (float)actualDistance);
    }

    /** Where the camera stands this frame, with the world's walls respected. */
    private static Placement place(EntityLivingBase viewEntity, float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        double partial = Math.max(0.0D, Math.min(1.0D, partialTicks));
        float cameraOffset = CharacterCameraHook.resolveCameraOffset(
                viewEntity, viewEntity.yOffset - VANILLA_EYE_HEIGHT);
        double eyeX = viewEntity.prevPosX + (viewEntity.posX - viewEntity.prevPosX) * partial;
        double eyeY = viewEntity.prevPosY + (viewEntity.posY - viewEntity.prevPosY) * partial
                - cameraOffset;
        double eyeZ = viewEntity.prevPosZ + (viewEntity.posZ - viewEntity.prevPosZ) * partial;
        double eyeAboveFeet = viewEntity.yOffset - cameraOffset;
        double drop = InspectionCameraMath.pivotDropBelowEye(
                viewEntity.height, eyeAboveFeet, focus);
        double bodyYaw = viewEntity.prevRenderYawOffset
                + CameraMath.wrapDegrees(viewEntity.renderYawOffset
                        - viewEntity.prevRenderYawOffset) * partial;
        // In front of the body, then round by however far it was dragged:
        // a drag to the right carries the camera round to the right, so
        // the character appears to turn the way the pointer went.
        double yaw = CameraMath.wrapDegrees(bodyYaw + 180.0D + yawOffset);
        double aspect = minecraft == null || minecraft.displayHeight <= 0
                ? 16.0D / 9.0D
                : minecraft.displayWidth / (double)minecraft.displayHeight;
        double distance = InspectionCameraMath.distanceFor(
                viewEntity.height, heightFraction, lastFov, zoom);
        double side = InspectionCameraMath.sideOffsetFor(distance, lastFov, aspect, screenX);
        double vertical = InspectionCameraMath.verticalOffsetFor(distance, lastFov, screenY);
        double allowed = distance;
        if (viewEntity.worldObj != null) {
            allowed = CameraCollisionResolver.resolveAllowedDistance(
                    ThirdPersonCameraHooks.createWorldRaycaster(viewEntity),
                    eyeX, eyeY - drop, eyeZ, yaw, pitch, distance, side, vertical,
                    Math.max(0.0D, LostTalesThirdPersonConfig.collisionPadding));
            allowed = Math.max(0.0D, Math.min(distance, allowed));
        }
        // A camera pushed nearer by a wall keeps the character in frame by
        // stepping aside less, in the same proportion.
        double share = distance <= 0.0D ? 0.0D : allowed / distance;
        return new Placement(yaw, pitch, allowed, side * share, vertical * share, drop);
    }

    private static final class Placement {
        final double yaw;
        final double pitch;
        final double distance;
        final double side;
        final double vertical;
        final double drop;

        Placement(double yaw, double pitch, double distance, double side,
                  double vertical, double drop) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.distance = distance;
            this.side = side;
            this.vertical = vertical;
            this.drop = drop;
        }
    }
}
