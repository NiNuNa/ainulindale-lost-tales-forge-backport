package com.ninuna.losttales.client.render.player;

import com.mojang.authlib.GameProfile;
import com.ninuna.losttales.character.registry.CharacterBodyModelDefinition;
import com.ninuna.losttales.character.registry.CharacterBodyModelRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.skin.LostTalesAccountSkins;
import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A character stood facing the viewer, drawn as the body model itself.
 *
 * <p>Not a picture of one: the same {@link LostTalesPlayerModel} the world
 * draws, built for the same body, skin layout, arm width and chest, and
 * turned to face out of the screen. A hobbit is short and an elf has ears
 * because the model has them, not because a flat drawing remembered to —
 * and whatever a later race brings comes with it.</p>
 *
 * <p>It is rendered without an entity. Nothing in
 * {@code ModelBiped.setRotationAngles} reads one, and what this mod adds
 * on top only ever asks {@code instanceof}, so a null stands for "nobody
 * in particular, stood still, looking straight ahead" — the pose a menu
 * wants.</p>
 *
 * <p>The transform is vanilla's own, in vanilla's order: what
 * {@code GuiInventory} sets up to stand a living thing in a screen, then
 * what {@code RendererLivingEntity} does around the model to stand it on
 * its feet. The only parts left out are the yaw and pitch it takes from
 * an entity, which are zero for a figure looking at you.</p>
 *
 * <p>The light is the other vanilla one:
 * {@code RenderHelper.enableGUIStandardItemLighting}, which aims the two
 * lamps at something a screen shows square-on, rather than the inventory's
 * pair aimed for a doll the mouse turns. Which one is used is the whole
 * difference between a figure whose flat front sits at the light model's
 * ambient and one lit like everything else a menu draws.</p>
 *
 * <p>It is drawn at a whole number of pixels per texel — see
 * {@link #scaleFor} — because at this size anything else is visible as
 * unequal pixels across one face. That is also why the race's renderer
 * scale is not applied: it is a fraction, and a fraction of a pixel per
 * texel is exactly what it costs. Races still differ, because their
 * bodies do — a hobbit's limbs are squeezed and its head dropped, a
 * dwarf's torso and legs are wider — and they stand on one baseline, as
 * two people of different heights would.</p>
 */
public final class LostTalesCharacterFigureRenderer {

    /** A biped is thirty-two model units, and a unit is a sixteenth. */
    private static final float MODEL_UNIT = 0.0625F;
    private static final float MODEL_HEIGHT_UNITS = 32.0F;
    /** What stands a model on its feet, as RendererLivingEntity does it. */
    private static final float FEET_LIFT = -24.0F * MODEL_UNIT - 0.0078125F;
    /** Far enough forward of the screen to have room for its own depth. */
    private static final float DEPTH = 50.0F;
    /** Turns the model to face out, as a yaw of zero does in the world. */
    private static final float FACING_VIEWER_YAW = 180.0F;

    /** One model per body, layout, arm width and chest; a handful at most. */
    private static final Map<String, ModelBiped> MODELS =
            new HashMap<String, ModelBiped>();

    private LostTalesCharacterFigureRenderer() {}

    /**
     * The largest scale whose figure still fits {@code availableHeight},
     * in whole pixels per texel.
     *
     * <p>A texel drawn over one pixel and its neighbour over two is what
     * reads as a ragged face at this size, so the scale is always a whole
     * number of pixels per texel — sixteen of them to a block.</p>
     */
    public static int scaleFor(int availableHeight) {
        int blocks = Math.round(MODEL_HEIGHT_UNITS * MODEL_UNIT);
        int pixelsPerTexel = availableHeight
                / (blocks * (int)Math.round(1.0F / MODEL_UNIT));
        return Math.max(1, pixelsPerTexel) * (int)Math.round(1.0F / MODEL_UNIT);
    }

    /**
     * How tall a figure stands at that scale, in pixels: a biped is two
     * blocks. What a caller centres by, so every race stands on the one
     * baseline whatever its body does above it.
     */
    public static int height(int scale) {
        return Math.round(MODEL_HEIGHT_UNITS * MODEL_UNIT * scale);
    }

    /**
     * Draws the character facing the viewer.
     *
     * @param centerX where the middle of the figure stands
     * @param feetY   where it stands, which is the bottom of it
     * @param scale   pixels per block; a biped is two blocks tall before
     *                the race's own scale
     * @return whether anything was drawn
     */
    public static boolean drawFigure(Minecraft minecraft, UUID ownerId,
                                     CharacterAppearance appearance,
                                     float centerX, float feetY, int scale,
                                     float brightness, float alpha) {
        return drawPosedFigure(minecraft, ownerId, appearance, centerX, feetY,
                scale, 0.0F, 0.0F, 0.0F, 0.0F, brightness, alpha);
    }

    /**
     * Draws the character turned, tilted and looking where it is told.
     *
     * <p>The creator's stage: the player turns the figure about its feet
     * and tilts it about its middle, and its head follows the pointer.
     * The scale may be fractional here — a figure this large is zoomed,
     * not pixel art — which is the one thing the menu button's overload
     * does not allow.</p>
     *
     * @param yaw       degrees the figure is turned; zero faces the viewer
     *                  and positive turns its front toward the viewer's
     *                  right
     * @param pitch     degrees the whole figure leans about its middle;
     *                  positive brings the head toward the viewer
     * @param headYaw   degrees the head is turned from the body, in the
     *                  model's own sense: what a living entity hands it as
     *                  its net head yaw, where negative looks toward the
     *                  viewer's right while the figure faces out
     * @param headPitch degrees the head looks down
     */
    public static boolean drawPosedFigure(Minecraft minecraft, UUID ownerId,
                                          CharacterAppearance appearance,
                                          float centerX, float feetY,
                                          float scale, float yaw, float pitch,
                                          float headYaw, float headPitch,
                                          float brightness, float alpha) {
        if (minecraft == null || appearance == null || scale <= 0.0F
                || alpha <= 0.0F) {
            return false;
        }
        try {
            ResolvedPlayerAppearance resolved =
                    PlayerAppearanceResolver.resolve(ownerId, appearance);
            if (resolved == null) {
                return false;
            }
            LostTalesAccountSkins.AccountSkin account =
                    resolved.usesAccountSkin()
                            ? LostTalesAccountSkins.resolveProfile(
                                    profileOf(minecraft))
                            : null;
            ResourceLocation texture = account == null
                    ? resolved.getTexture() : account.getTexture();
            if (texture == null) {
                return false;
            }
            // An account skin says which arm width it was painted for; a
            // catalogue skin was chosen with one.
            String bodyTypeId = account == null
                    ? resolved.getBodyTypeId() : account.getBodyTypeId();
            ModelBiped model = modelFor(resolved, bodyTypeId);
            if (model == null) {
                return false;
            }
            draw(minecraft, model, texture, centerX, feetY, scale, yaw,
                    pitch, headYaw, headPitch, brightness, alpha);
            return true;
        } catch (RuntimeException unavailable) {
            // A menu is never worth a crash; the button keeps its panel.
            return false;
        }
    }

    private static void draw(Minecraft minecraft, ModelBiped model,
                             ResourceLocation texture, float centerX,
                             float feetY, float scale, float yaw, float pitch,
                             float headYaw, float headPitch, float brightness,
                             float alpha) {
        // The world's light map is still bound on its own texture unit,
        // and away from a world it is dark: left on, it multiplies the
        // figure down to a silhouette. GuiInventory turns it off after
        // drawing, which is the same thing one frame late.
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        boolean lightmap = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        // The lamps are aimed in whatever frame they are switched on in, so
        // they are switched on here, square to the screen, where vanilla
        // aims them for anything a menu shows face-on. GuiInventory instead
        // aims them a hundred and thirty-five degrees round, for a figure
        // the mouse turns: aimed that way, a face-on figure catches one
        // lamp at barely a quarter and the other not at all, and every flat
        // front — head, torso, arms, legs — sits near the light model's
        // ambient while only sloped surfaces like the chest come out lit.
        RenderHelper.enableGUIStandardItemLighting();
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GL11.glPushMatrix();
        try {
            // What GuiInventory sets up to stand a living thing in a screen.
            GL11.glTranslatef(centerX, feetY, DEPTH);
            GL11.glScalef(-scale, scale, scale);
            GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);

            // In this frame the figure stands from its feet at zero up two
            // blocks along +y, and +z is toward the viewer. A lean turns it
            // about its middle so it neither sinks into the floor nor
            // lifts off it; the turn is about its own feet, and comes
            // after the lean in the matrix so the lean stays the screen's
            // horizontal whichever way the figure faces.
            if (pitch != 0.0F) {
                GL11.glTranslatef(0.0F, MODEL_HEIGHT_UNITS * MODEL_UNIT / 2.0F, 0.0F);
                GL11.glRotatef(pitch, 1.0F, 0.0F, 0.0F);
                GL11.glTranslatef(0.0F, -MODEL_HEIGHT_UNITS * MODEL_UNIT / 2.0F, 0.0F);
            }
            // And what RendererLivingEntity does around the model itself,
            // with the yaw of somebody looking straight at you, plus the
            // turn the player gave it. A positive turn about +y here moves
            // the front toward +x, and +x is the viewer's right: the
            // mirrored scale and the half-turn above flip x once each.
            GL11.glRotatef(FACING_VIEWER_YAW + yaw, 0.0F, 1.0F, 0.0F);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glScalef(-1.0F, -1.0F, 1.0F);
            GL11.glTranslatef(0.0F, FEET_LIFT, 0.0F);

            // A model is a child until a renderer tells it otherwise, and
            // the only thing that ever does is RendererLivingEntity, from
            // the entity it is drawing. Rendering without one leaves it
            // set, and a child model is not merely smaller: vanilla draws
            // its head at three quarters and everything else at a half,
            // and this mod's own render() hands the whole job back to
            // vanilla when it is set — so no dwarf breadth, no hobbit
            // limbs, no chest, and the hair drawn down at the body.
            model.isChild = false;
            if (model instanceof LostTalesPlayerModel) {
                ((LostTalesPlayerModel)model).setOverlaysVisible(
                        LostTalesConfig.showSkinOverlays);
            }
            // The model's own faces have to sort against each other, which
            // is the one thing a screen otherwise never asks of depth.
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            float shade = Math.min(1.0F, brightness);
            GL11.glColor4f(shade, shade, shade, Math.min(1.0F, alpha));
            minecraft.getTextureManager().bindTexture(texture);
            // The head's turn and tilt are the model's own business: the
            // same two angles a living entity hands it every frame.
            model.render(null, 0.0F, 0.0F, 0.0F, headYaw, headPitch, MODEL_UNIT);
        } finally {
            GL11.glPopMatrix();
            // A screen is painted in the order it is drawn, and everything
            // it paints sits nearer than the figure did. Left behind, the
            // figure's depth rejects whatever is drawn over it afterwards —
            // a tooltip above all, which is drawn last and reads as being
            // behind the model. So the depth it wrote is taken back out.
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            if (!depthTest) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_COLOR_MATERIAL);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            if (lightmap) {
                OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            }
        }
    }

    /** The signed-in account's profile, which names its skin. */
    private static GameProfile profileOf(Minecraft minecraft) {
        if (minecraft.thePlayer != null) {
            return minecraft.thePlayer.getGameProfile();
        }
        return minecraft.getSession() == null
                ? null : minecraft.getSession().func_148256_e();
    }

    /**
     * The model for that body, kept: building one lays out every box, and
     * a menu draws the same figure every frame. There are as many as
     * there are bodies times arm widths times chests, which is a handful.
     */
    private static synchronized ModelBiped modelFor(
            ResolvedPlayerAppearance resolved, String bodyTypeId) {
        String key = resolved.getModelId() + '|' + resolved.getLayout()
                + '|' + bodyTypeId + '|' + resolved.getChestTypeId();
        ModelBiped model = MODELS.get(key);
        if (model == null) {
            CharacterBodyModelDefinition definition =
                    CharacterBodyModelRegistry.get(resolved.getModelId());
            model = LostTalesPlayerBodyModelFactory.createMainModel(
                    definition, resolved.getLayout(), bodyTypeId,
                    resolved.getChestTypeId());
            if (model == null) {
                return null;
            }
            MODELS.put(key, model);
        }
        return model;
    }

    /** Dropped with every other client cache when the world is left. */
    public static synchronized void clear() {
        MODELS.clear();
    }
}
