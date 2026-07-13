package com.ninuna.losttales.gui.hud.party;

import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinDefinition;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.party.ClientPartyMemberStatusCache;
import com.ninuna.losttales.client.party.ClientPartyStateCache;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.sync.PartyMemberAvailability;
import com.ninuna.losttales.party.sync.PartyMemberSnapshot;
import com.ninuna.losttales.party.sync.PartyMemberStatusSnapshot;
import com.ninuna.losttales.party.sync.PartySnapshot;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;
import com.ninuna.losttales.party.sync.PartyStatusSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Compact party HUD showing only the local character's other party members. */
public final class LostTalesPartyHudRenderer {

    private static final ResourceLocation DEFAULT_PLAYER_SKIN =
            new ResourceLocation("textures/entity/steve.png");
    private static final ResourceLocation GUI_ICONS =
            new ResourceLocation("textures/gui/icons.png");
    private static final RenderItem ITEM_RENDERER = new RenderItem();

    private static final int HEAD_SIZE = 20;
    private static final int HEART_SIZE = 9;
    private static final int MAX_VISIBLE_HEARTS = 15;

    private LostTalesPartyHudRenderer() {}

    public static void render(Minecraft minecraft, float partialTicks) {
        if (!LostTalesConfig.showLostTalesHud
                || !LostTalesConfig.showPartyHud
                || minecraft == null || minecraft.thePlayer == null
                || minecraft.theWorld == null
                || minecraft.gameSettings.hideGUI) {
            return;
        }

        PartyStateSnapshot state = ClientPartyStateCache.getSnapshot();
        PartySnapshot party = state == null || !state.isAvailable()
                ? null : state.getParty();
        if (party == null || party.getMemberCount() <= 1
                || state.getActiveCharacterId() == null) {
            return;
        }

        List<PartyMemberSnapshot> others =
                collectOtherMembers(party, state.getActiveCharacterId());
        if (others.isEmpty()) {
            return;
        }

        PartyStatusSnapshot statuses =
                ClientPartyMemberStatusCache.getMatching(state);
        boolean stale = ClientPartyMemberStatusCache.isStale(state);
        ScaledResolution resolution = new ScaledResolution(
                minecraft, minecraft.displayWidth, minecraft.displayHeight);
        PartyHudLayout.Bounds bounds = PartyHudLayout.calculate(
                resolution.getScaledWidth(),
                resolution.getScaledHeight(),
                LostTalesConfig.partyHudOffsetX,
                LostTalesConfig.partyHudOffsetY,
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
                PartyMemberSnapshot member = others.get(index);
                PartyMemberStatusSnapshot status = statuses == null
                        ? null : statuses.getMemberStatus(
                        member.getCharacterId());
                renderMemberRow(
                        minecraft,
                        member,
                        status,
                        stale,
                        party.isLeader(member.getCharacterId()),
                        bounds.x + PartyHudLayout.PANEL_PADDING,
                        bounds.y + PartyHudLayout.PANEL_PADDING
                                + index * PartyHudLayout.ROW_HEIGHT,
                        bounds.width - PartyHudLayout.PANEL_PADDING * 2);
            }
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glPopMatrix();
        }
    }

    private static void renderMemberRow(Minecraft minecraft,
                                        PartyMemberSnapshot member,
                                        PartyMemberStatusSnapshot status,
                                        boolean stale,
                                        boolean leader,
                                        int x,
                                        int y,
                                        int width) {
        int partyColor = color(member.getColor());
        Gui.drawRect(x, y + 1, x + 2, y + PartyHudLayout.ROW_HEIGHT - 2,
                partyColor);
        Gui.drawRect(x + 3, y + PartyHudLayout.ROW_HEIGHT - 1,
                x + width, y + PartyHudLayout.ROW_HEIGHT,
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
                    heldItemX + 17, y + 26, 0x66302D28);
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
                    == PartyMemberAvailability.DEAD ? 0.65F : 1.0F;
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
            PartyMemberStatusSnapshot status, boolean stale) {
        if (stale || status == null) {
            return I18n.format("gui.losttales.party.hud.status_unavailable");
        }
        PartyMemberAvailability availability = status.getAvailability();
        if (availability == PartyMemberAvailability.OFFLINE) {
            return I18n.format("gui.losttales.party.hud.offline");
        }
        if (availability == PartyMemberAvailability.INACTIVE_CHARACTER) {
            return I18n.format("gui.losttales.party.hud.different_character");
        }
        return I18n.format("gui.losttales.party.hud.unavailable");
    }

    private static int statusColor(PartyMemberStatusSnapshot status,
                                   boolean stale) {
        if (stale || status == null) {
            return LostTalesSkyrimUiStyle.TEXT_DIM;
        }
        if (status.getAvailability() == PartyMemberAvailability.DEAD) {
            return LostTalesSkyrimUiStyle.RED;
        }
        if (status.getAvailability() == PartyMemberAvailability.ACTIVE) {
            return LostTalesSkyrimUiStyle.TEXT;
        }
        return LostTalesSkyrimUiStyle.TEXT_MUTED;
    }

    private static void drawHearts(Minecraft minecraft,
                                   PartyMemberStatusSnapshot status,
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

    private static List<PartyMemberSnapshot> collectOtherMembers(
            PartySnapshot party, UUID activeCharacterId) {
        ArrayList<PartyMemberSnapshot> result =
                new ArrayList<PartyMemberSnapshot>();
        for (PartyMemberSnapshot member : party.getMembers()) {
            if (!member.getCharacterId().equals(activeCharacterId)) {
                result.add(member);
            }
        }
        return result;
    }

    private static void drawMemberHead(Minecraft minecraft,
                                       UUID ownerId,
                                       int x,
                                       int y,
                                       float brightness) {
        HeadTexture texture = resolveHeadTexture(minecraft, ownerId);
        try {
            minecraft.getTextureManager().bindTexture(texture.location);
            GL11.glColor4f(brightness, brightness, brightness, 1.0F);
            drawTexturedQuad(x, y, HEAD_SIZE, HEAD_SIZE,
                    8.0F, 8.0F, 8.0F, 8.0F,
                    64.0F, texture.imageHeight);
            // Both classic 64x32 player skins and LOTR's 64x64 biped
            // textures use the standard headwear UV region. Transparent
            // pixels make this a no-op for skins without an overlay.
            drawTexturedQuad(x, y, HEAD_SIZE, HEAD_SIZE,
                    40.0F, 8.0F, 8.0F, 8.0F,
                    64.0F, texture.imageHeight);
        } catch (Throwable ignored) {
            Gui.drawRect(x, y, x + HEAD_SIZE, y + HEAD_SIZE,
                    0xAA272727);
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static HeadTexture resolveHeadTexture(
            Minecraft minecraft, UUID ownerId) {
        CharacterAppearance appearance =
                ClientCharacterAppearanceCache.getAuthoritative(ownerId);
        CharacterSkinDefinition configured = appearance == null
                ? null : CharacterSkinRegistry.get(appearance.getSkinId());
        if (configured != null) {
            return new HeadTexture(
                    new ResourceLocation(configured.getTextureLocation()),
                    configuredTextureHeight(appearance));
        }

        if (minecraft != null && minecraft.theWorld != null
                && minecraft.theWorld.playerEntities != null) {
            for (Object value : minecraft.theWorld.playerEntities) {
                if (value instanceof AbstractClientPlayer) {
                    AbstractClientPlayer player = (AbstractClientPlayer) value;
                    if (ownerId.equals(player.getUniqueID())) {
                        // Legacy player skins use the classic 64x32 layout.
                        return new HeadTexture(
                                player.getLocationSkin(), 32.0F);
                    }
                } else if (value instanceof EntityPlayer) {
                    EntityPlayer player = (EntityPlayer) value;
                    if (ownerId.equals(player.getUniqueID())) {
                        break;
                    }
                }
            }
        }
        return new HeadTexture(DEFAULT_PLAYER_SKIN, 32.0F);
    }

    /** LOTR orc and Uruk body textures retain the classic 64x32 layout. */
    private static float configuredTextureHeight(
            CharacterAppearance appearance) {
        if (appearance != null
                && (CharacterRaceRegistry.ORC.equals(appearance.getRaceId())
                || CharacterRaceRegistry.URUK.equals(appearance.getRaceId()))) {
            return 32.0F;
        }
        return 64.0F;
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

    private static int color(PartyColor color) {
        if (color == PartyColor.YELLOW) {
            return 0xFFE3CF58;
        }
        if (color == PartyColor.PURPLE) {
            return 0xFFAA72CF;
        }
        if (color == PartyColor.BLUE) {
            return 0xFF6098D8;
        }
        return 0xFF62B56B;
    }

    private static final class HeadTexture {
        private final ResourceLocation location;
        private final float imageHeight;
        private HeadTexture(ResourceLocation location,
                            float imageHeight) {
            this.location = location;
            this.imageHeight = imageHeight;
        }
    }
}
