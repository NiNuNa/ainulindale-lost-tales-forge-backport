package com.ninuna.losttales.gui.hud.party;

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
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Compact party HUD showing only the local character's other party members. */
public final class LostTalesPartyHudRenderer {

    private static final ResourceLocation DEFAULT_PLAYER_SKIN =
            new ResourceLocation("textures/entity/steve.png");
    private static final int HEAD_SIZE = 20;

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
        int color = color(member.getColor());
        Gui.drawRect(x, y + 1, x + 2, y + PartyHudLayout.ROW_HEIGHT - 2,
                color);
        if (y > 0) {
            Gui.drawRect(x + 3, y + PartyHudLayout.ROW_HEIGHT - 1,
                    x + width, y + PartyHudLayout.ROW_HEIGHT,
                    LostTalesSkyrimUiStyle.BORDER_DIM);
        }

        boolean muted = stale || status == null
                || status.getAvailability() != PartyMemberAvailability.ACTIVE;
        drawMemberHead(
                minecraft,
                member.getOwnerId(),
                !stale && status != null && status.isOnlineActive(),
                x + 5,
                y + 4,
                muted ? 0.55F : 1.0F);

        FontRenderer font = minecraft.fontRenderer;
        int textX = x + 30;
        int nameRight = x + width - (leader ? 13 : 4);
        String name = LostTalesSkyrimUiStyle.trimToWidth(
                font, member.getCharacterName(),
                Math.max(8, nameRight - textX));
        font.drawStringWithShadow(name, textX, y + 3,
                muted ? LostTalesSkyrimUiStyle.TEXT_MUTED
                        : LostTalesSkyrimUiStyle.TEXT_BRIGHT);
        if (leader) {
            LostTalesSkyrimUiStyle.drawDiamond(
                    x + width - 7, y + 7,
                    LostTalesSkyrimUiStyle.GOLD);
        }

        String statusText = describeStatus(
                minecraft, status, stale);
        font.drawStringWithShadow(
                LostTalesSkyrimUiStyle.trimToWidth(
                        font, statusText,
                        Math.max(8, x + width - 4 - textX)),
                textX, y + 14,
                statusColor(status, stale));

        if (!stale && status != null
                && status.getAvailability().hasLiveEntityData()) {
            int barWidth = Math.max(8, x + width - 4 - textX);
            int fill = Math.round(barWidth
                    * status.getHealth()
                    / Math.max(0.001F, status.getMaximumHealth()));
            fill = Math.max(0, Math.min(barWidth, fill));
            Gui.drawRect(textX, y + 24,
                    textX + barWidth, y + 26, 0x773D3A33);
            if (fill > 0) {
                Gui.drawRect(textX, y + 24,
                        textX + fill, y + 26, color);
            }
        }
    }

    private static String describeStatus(Minecraft minecraft,
                                         PartyMemberStatusSnapshot status,
                                         boolean stale) {
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
        if (availability == PartyMemberAvailability.UNAVAILABLE) {
            return I18n.format("gui.losttales.party.hud.unavailable");
        }
        if (availability == PartyMemberAvailability.DEAD) {
            return I18n.format("gui.losttales.party.hud.dead");
        }

        String health = I18n.format(
                "gui.losttales.party.hud.health",
                formatHealth(status.getHealth()),
                formatHealth(status.getMaximumHealth()));
        if (minecraft.thePlayer != null
                && minecraft.thePlayer.dimension != status.getDimensionId()) {
            return I18n.format(
                    "gui.losttales.party.hud.other_dimension", health);
        }
        if (status.getMaximumHealth() > 0.0F
                && status.getHealth() / status.getMaximumHealth() <= 0.25F) {
            return I18n.format(
                    "gui.losttales.party.hud.low_health", health);
        }
        return health;
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
                                       boolean allowConfiguredCharacterTexture,
                                       int x,
                                       int y,
                                       float brightness) {
        ResourceLocation texture = resolveHeadTexture(
                minecraft, ownerId, allowConfiguredCharacterTexture);
        try {
            minecraft.getTextureManager().bindTexture(texture);
            GL11.glColor4f(brightness, brightness, brightness, 1.0F);
            drawTexturedQuad(x, y, HEAD_SIZE, HEAD_SIZE,
                    8.0F, 8.0F, 8.0F, 8.0F, 64.0F, 32.0F);
            drawTexturedQuad(x, y, HEAD_SIZE, HEAD_SIZE,
                    40.0F, 8.0F, 8.0F, 8.0F, 64.0F, 32.0F);
        } catch (Throwable ignored) {
            Gui.drawRect(x, y, x + HEAD_SIZE, y + HEAD_SIZE,
                    0xAA272727);
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static ResourceLocation resolveHeadTexture(
            Minecraft minecraft,
            UUID ownerId,
            boolean allowConfiguredCharacterTexture) {
        if (allowConfiguredCharacterTexture) {
            CharacterAppearance appearance =
                    ClientCharacterAppearanceCache.getAuthoritative(ownerId);
            CharacterSkinDefinition configured = appearance == null
                    ? null : CharacterSkinRegistry.get(appearance.getSkinId());
            if (configured != null) {
                return new ResourceLocation(configured.getTextureLocation());
            }
        }

        if (minecraft != null && minecraft.theWorld != null
                && minecraft.theWorld.playerEntities != null) {
            for (Object value : minecraft.theWorld.playerEntities) {
                if (value instanceof AbstractClientPlayer) {
                    AbstractClientPlayer player = (AbstractClientPlayer) value;
                    if (ownerId.equals(player.getUniqueID())) {
                        return player.getLocationSkin();
                    }
                } else if (value instanceof EntityPlayer) {
                    EntityPlayer player = (EntityPlayer) value;
                    if (ownerId.equals(player.getUniqueID())) {
                        break;
                    }
                }
            }
        }
        return DEFAULT_PLAYER_SKIN;
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

    private static String formatHealth(float value) {
        int rounded = Math.round(value);
        if (Math.abs(value - rounded) < 0.05F) {
            return Integer.toString(rounded);
        }
        return String.format(java.util.Locale.ENGLISH, "%.1f", value);
    }
}
