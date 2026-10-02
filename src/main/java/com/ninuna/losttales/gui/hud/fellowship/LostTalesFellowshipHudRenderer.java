package com.ninuna.losttales.gui.hud.fellowship;

import com.ninuna.losttales.client.fellowship.ClientFellowshipMemberStatusCache;
import com.ninuna.losttales.client.fellowship.ClientFellowshipStateCache;
import com.ninuna.losttales.client.fellowship.ClientFellowshipTrackingCache;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberAvailability;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberStatusSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStatusSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackedMemberSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackingSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Compact fellowship HUD: the members of the fellowship the player travels
 * with who are nearest them, one row each.
 */
public final class LostTalesFellowshipHudRenderer {

    private static final ResourceLocation GUI_ICONS =
            new ResourceLocation("textures/gui/icons.png");
    private static final RenderItem ITEM_RENDERER = new RenderItem();

    private static final int HEAD_SIZE = 20;
    private static final int HEART_SIZE = 9;
    private static final int MAX_VISIBLE_HEARTS = 15;

    private LostTalesFellowshipHudRenderer() {}

    /**
     * The rows a full fellowship shows, for placing the HUD: the server's
     * member limit less the player, at most the panel's rows; the panel's
     * rows before the server has said.
     */
    public static int placementRows() {
        FellowshipStateSnapshot state = ClientFellowshipStateCache.getSnapshot();
        return state == null ? FellowshipHudLayout.MAX_ROWS
                : Math.min(FellowshipHudLayout.MAX_ROWS, state.getMemberLimit() - 1);
    }

    public static void render(Minecraft minecraft, float partialTicks) {
        if (!LostTalesConfig.showLostTalesHud
                || !LostTalesConfig.showFellowshipHud
                || minecraft == null || minecraft.thePlayer == null
                || minecraft.theWorld == null
                || minecraft.gameSettings.hideGUI) {
            return;
        }

        FellowshipStateSnapshot state = ClientFellowshipStateCache.getSnapshot();
        FellowshipSnapshot fellowship = state == null || !state.isAvailable()
                ? null : state.getTravellingFellowship();
        if (fellowship == null || fellowship.getMemberCount() <= 1
                || state.getActiveIdentityId() == null) {
            return;
        }

        List<FellowshipMemberSnapshot> others = collectNearestMembers(minecraft,
                fellowship, state.getActiveIdentityId(),
                ClientFellowshipTrackingCache.getMatching(state));
        if (others.isEmpty()) {
            return;
        }

        FellowshipStatusSnapshot statuses =
                ClientFellowshipMemberStatusCache.getMatching(state);
        boolean stale = ClientFellowshipMemberStatusCache.isStale(state);
        ScaledResolution resolution = new ScaledResolution(
                minecraft, minecraft.displayWidth, minecraft.displayHeight);
        FellowshipHudLayout.Bounds bounds = FellowshipHudLayout.calculate(
                resolution.getScaledWidth(),
                resolution.getScaledHeight(),
                LostTalesConfig.fellowshipHudOffsetX,
                LostTalesConfig.fellowshipHudOffsetY,
                others.size());

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        try {
            LostTalesSkyrimUiStyle.drawPanelSoft(
                    bounds.x, bounds.y, bounds.width, bounds.height);
            for (int index = 0;
                 index < others.size() && index < bounds.rowCount;
                 index++) {
                FellowshipMemberSnapshot member = others.get(index);
                FellowshipMemberStatusSnapshot status = statuses == null
                        ? null : statuses.getMemberStatus(
                        member.getIdentityId());
                renderMemberRow(
                        minecraft,
                        member,
                        status,
                        stale,
                        fellowship.isLeader(member.getIdentityId()),
                        bounds.x + FellowshipHudLayout.PANEL_PADDING,
                        bounds.y + FellowshipHudLayout.PANEL_PADDING
                                + index * FellowshipHudLayout.ROW_HEIGHT,
                        bounds.width - FellowshipHudLayout.PANEL_PADDING * 2);
            }
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glPopMatrix();
        }
    }

    private static void renderMemberRow(Minecraft minecraft,
                                        FellowshipMemberSnapshot member,
                                        FellowshipMemberStatusSnapshot status,
                                        boolean stale,
                                        boolean leader,
                                        int x,
                                        int y,
                                        int width) {
        int fellowshipColor = color(member.getColor());
        Gui.drawRect(x, y + 1, x + 2, y + FellowshipHudLayout.ROW_HEIGHT - 2,
                fellowshipColor);
        Gui.drawRect(x + 3, y + FellowshipHudLayout.ROW_HEIGHT - 1,
                x + width, y + FellowshipHudLayout.ROW_HEIGHT,
                LostTalesSkyrimUiStyle.BORDER_DIM);

        boolean hasLiveStatus = !stale && status != null
                && status.getAvailability().hasLiveEntityData();
        boolean muted = !hasLiveStatus;
        ItemStack helmet = hasLiveStatus ? status.getHelmet() : null;
        ItemStack heldItem = hasLiveStatus ? status.getHeldItem() : null;

        drawMemberHead(
                minecraft,
                member.getOwnerId(),
                x + 5,
                y + 4,
                muted ? 0.55F : 1.0F);
        if (helmet != null) {
            renderStack(minecraft, helmet,
                    x + 5 + HEAD_SIZE - 8,
                    y + 4 + HEAD_SIZE - 8,
                    0.55F, false);
        }

        int heldItemX = x + width - 18;
        if (heldItem != null) {
            Gui.drawRect(heldItemX - 1, y + 8,
                    heldItemX + 17, y + 26,
                    LostTalesColors.withAlpha(LostTalesColors.PLUM_BLACK, 0x66));
            renderStack(minecraft, heldItem, heldItemX, y + 9, 1.0F, true);
        }

        FontRenderer font = minecraft.fontRenderer;
        int textX = x + 30;
        int leaderX = heldItem == null
                ? x + width - 7 : heldItemX - 7;
        int nameRight = leader ? leaderX - 5
                : (heldItem == null ? x + width - 4 : heldItemX - 3);
        String name = LostTalesSkyrimUiStyle.trimToWidth(
                font, member.getCharacterName(),
                Math.max(8, nameRight - textX));
        font.drawStringWithShadow(name, textX, y + 3,
                muted ? LostTalesSkyrimUiStyle.TEXT_MUTED
                        : LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        if (leader) {
            LostTalesSkyrimUiStyle.drawDiamond(
                    leaderX, y + 7,
                    LostTalesSkyrimUiStyle.GOLD);
        }

        int contentRight = heldItem == null
                ? x + width - 4 : heldItemX - 3;
        if (hasLiveStatus) {
            float brightness = status.getAvailability()
                    == FellowshipMemberAvailability.DEAD ? 0.65F : 1.0F;
            if (minecraft.thePlayer != null
                    && minecraft.thePlayer.dimension
                    != status.getDimensionId()) {
                brightness *= 0.65F;
            }
            drawHearts(minecraft, status,
                    textX, y + 15,
                    Math.max(HEART_SIZE, contentRight - textX),
                    brightness);
        } else {
            String statusText = describeUnavailableStatus(status, stale);
            font.drawStringWithShadow(
                    LostTalesSkyrimUiStyle.trimToWidth(
                            font, statusText,
                            Math.max(8, contentRight - textX)),
                    textX, y + 15,
                    statusColor(status, stale));
        }
    }

    private static String describeUnavailableStatus(
            FellowshipMemberStatusSnapshot status, boolean stale) {
        if (stale || status == null) {
            return I18n.format("gui.losttales.fellowship.hud.status_unavailable");
        }
        FellowshipMemberAvailability availability = status.getAvailability();
        if (availability == FellowshipMemberAvailability.OFFLINE) {
            return I18n.format("gui.losttales.fellowship.hud.offline");
        }
        if (availability == FellowshipMemberAvailability.INACTIVE_CHARACTER) {
            return I18n.format("gui.losttales.fellowship.hud.different_character");
        }
        return I18n.format("gui.losttales.fellowship.hud.unavailable");
    }

    private static int statusColor(FellowshipMemberStatusSnapshot status,
                                   boolean stale) {
        if (stale || status == null) {
            return LostTalesSkyrimUiStyle.TEXT_DIM;
        }
        if (status.getAvailability() == FellowshipMemberAvailability.DEAD) {
            return LostTalesSkyrimUiStyle.RED;
        }
        if (status.getAvailability() == FellowshipMemberAvailability.ACTIVE) {
            return LostTalesSkyrimUiStyle.TEXT;
        }
        return LostTalesSkyrimUiStyle.TEXT_MUTED;
    }

    private static void drawHearts(Minecraft minecraft,
                                   FellowshipMemberStatusSnapshot status,
                                   int x,
                                   int y,
                                   int availableWidth,
                                   float brightness) {
        int actualHearts = Math.max(1,
                (int) Math.ceil(status.getMaximumHealth() / 2.0F));
        int visibleHearts = Math.min(MAX_VISIBLE_HEARTS, actualHearts);
        int spacing = visibleHearts <= 1 ? 0
                : Math.max(1, Math.min(8,
                (availableWidth - HEART_SIZE) / (visibleHearts - 1)));

        int filledHalfHearts;
        if (actualHearts <= MAX_VISIBLE_HEARTS) {
            filledHalfHearts = Math.max(0,
                    Math.min(visibleHearts * 2,
                            (int) Math.ceil(status.getHealth())));
        } else {
            float fraction = status.getHealth()
                    / Math.max(0.001F, status.getMaximumHealth());
            filledHalfHearts = Math.max(0,
                    Math.min(visibleHearts * 2,
                            Math.round(fraction * visibleHearts * 2.0F)));
        }

        minecraft.getTextureManager().bindTexture(GUI_ICONS);
        GL11.glColor4f(brightness, brightness, brightness, 1.0F);
        for (int index = 0; index < visibleHearts; index++) {
            int heartX = x + index * spacing;
            drawTexturedQuad(heartX, y, HEART_SIZE, HEART_SIZE,
                    16.0F, 0.0F, HEART_SIZE, HEART_SIZE,
                    256.0F, 256.0F);
            int halfThreshold = index * 2;
            if (filledHalfHearts >= halfThreshold + 2) {
                drawTexturedQuad(heartX, y, HEART_SIZE, HEART_SIZE,
                        52.0F, 0.0F, HEART_SIZE, HEART_SIZE,
                        256.0F, 256.0F);
            } else if (filledHalfHearts == halfThreshold + 1) {
                drawTexturedQuad(heartX, y, HEART_SIZE, HEART_SIZE,
                        61.0F, 0.0F, HEART_SIZE, HEART_SIZE,
                        256.0F, 256.0F);
            }
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** The other members, nearest the player first, as many as the panel shows. */
    private static List<FellowshipMemberSnapshot> collectNearestMembers(
            Minecraft minecraft, FellowshipSnapshot fellowship, UUID activeIdentityId,
            FellowshipTrackingSnapshot tracking) {
        ArrayList<FellowshipMemberSnapshot> others =
                new ArrayList<FellowshipMemberSnapshot>();
        for (FellowshipMemberSnapshot member : fellowship.getMembers()) {
            if (!member.getIdentityId().equals(activeIdentityId)) {
                others.add(member);
            }
        }
        double[] distances = new double[others.size()];
        Arrays.fill(distances, Double.NaN);
        if (tracking != null) {
            for (FellowshipTrackedMemberSnapshot tracked : tracking.getTrackedMembers()) {
                if (tracked.getDimensionId() != minecraft.thePlayer.dimension) {
                    continue;
                }
                for (int index = 0; index < others.size(); index++) {
                    if (others.get(index).getIdentityId().equals(tracked.getIdentityId())) {
                        double dx = tracked.getX() - minecraft.thePlayer.posX;
                        double dz = tracked.getZ() - minecraft.thePlayer.posZ;
                        distances[index] = dx * dx + dz * dz;
                    }
                }
            }
        }
        ArrayList<FellowshipMemberSnapshot> nearest =
                new ArrayList<FellowshipMemberSnapshot>();
        for (Integer index : FellowshipHudLayout.nearestFirst(distances)) {
            if (nearest.size() >= FellowshipHudLayout.MAX_ROWS) {
                break;
            }
            nearest.add(others.get(index.intValue()));
        }
        return nearest;
    }

    private static void drawMemberHead(Minecraft minecraft,
                                       UUID ownerId,
                                       int x,
                                       int y,
                                       float brightness) {
        if (!LostTalesCharacterHeadIconRenderer.drawHead(
                minecraft, ownerId, x, y, HEAD_SIZE,
                brightness, 1.0F)) {
            Gui.drawRect(x, y, x + HEAD_SIZE, y + HEAD_SIZE,
                    LostTalesColors.withAlpha(LostTalesColors.PLUM_BLACK, 0xAA));
        }
    }

    private static void renderStack(Minecraft minecraft,
                                    ItemStack stack,
                                    int x,
                                    int y,
                                    float scale,
                                    boolean drawOverlay) {
        if (minecraft == null || stack == null || scale <= 0.0F) {
            return;
        }

        float previousZLevel = ITEM_RENDERER.zLevel;
        float previousLightmapX = OpenGlHelper.lastBrightnessX;
        float previousLightmapY = OpenGlHelper.lastBrightnessY;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);

            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setLightmapTextureCoords(
                    OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);

            RenderHelper.enableGUIStandardItemLighting();
            ITEM_RENDERER.zLevel = 200.0F;
            ITEM_RENDERER.renderItemAndEffectIntoGUI(
                    minecraft.fontRenderer,
                    minecraft.getTextureManager(),
                    stack, 0, 0);
            if (drawOverlay) {
                ITEM_RENDERER.renderItemOverlayIntoGUI(
                        minecraft.fontRenderer,
                        minecraft.getTextureManager(),
                        stack, 0, 0);
            }
        } finally {
            ITEM_RENDERER.zLevel = previousZLevel;
            RenderHelper.disableStandardItemLighting();
            OpenGlHelper.setLightmapTextureCoords(
                    OpenGlHelper.lightmapTexUnit,
                    previousLightmapX, previousLightmapY);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
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
        tessellator.addVertexWithUV(x + width, y + height, 0.0D, u1, v1);
        tessellator.addVertexWithUV(x + width, y, 0.0D, u1, v0);
        tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
        tessellator.draw();
    }

    /** The member's own colour, opaque; the colour itself is the fellowship's. */
    private static int color(FellowshipColor color) {
        FellowshipColor drawn = color == null ? FellowshipColor.GREEN : color;
        return 0xFF000000 | drawn.getRgb();
    }

}
