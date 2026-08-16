package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.character.ClientCharacterDisplayNames;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

/** Resolves and draws the player card for the chat geometry on this frame. */
final class LostTalesChatHoverCard {
    private static final int PADDING = 6;
    private static final int HEAD_SIZE = 16;
    private static final int MIN_WIDTH = 118;
    private static final int MAX_WIDTH = 210;

    private LostTalesChatHoverCard() {}

    static void draw(Minecraft minecraft, int mouseX, int mouseY,
                     int screenWidth, int screenHeight) {
        Target target = find(minecraft, mouseX, mouseY);
        if (target == null || minecraft.fontRenderer == null) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        String name = target.identityName;
        String title = cleanBracketed(target.title);
        String account = target.accountName;
        String race = raceFor(target);

        String accountLine = StatCollector.translateToLocal(
                "gui.losttales.character.minecraft_account") + ": "
                + account;
        String raceLine = race.length() == 0 ? ""
                : StatCollector.translateToLocal(
                        "gui.losttales.character.race") + ": " + race;
        int contentWidth = Math.max(font.getStringWidth(name),
                font.getStringWidth(accountLine));
        contentWidth = Math.max(contentWidth,
                font.getStringWidth(title));
        contentWidth = Math.max(contentWidth,
                font.getStringWidth(raceLine));
        int width = Math.max(MIN_WIDTH,
                Math.min(MAX_WIDTH, PADDING + HEAD_SIZE + 6
                        + contentWidth + PADDING));
        width = Math.min(width, Math.max(40, screenWidth - 8));
        int textWidth = width - PADDING - HEAD_SIZE - 6 - PADDING;
        name = LostTalesSkyrimUiStyle.trimToWidth(font, name, textWidth);
        title = LostTalesSkyrimUiStyle.trimToWidth(
                font, title, textWidth);
        accountLine = LostTalesSkyrimUiStyle.trimToWidth(
                font, accountLine, textWidth);
        raceLine = LostTalesSkyrimUiStyle.trimToWidth(
                font, raceLine, textWidth);
        int lines = 2 + (title.length() == 0 ? 0 : 1)
                + (raceLine.length() == 0 ? 0 : 1);
        int height = Math.max(HEAD_SIZE + PADDING * 2,
                PADDING * 2 + lines * font.FONT_HEIGHT);
        int x = cardX(mouseX, width, screenWidth);
        int y = cardY(mouseY, height, screenHeight);

        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, 0.0F, 300.0F);
            LostTalesSkyrimUiStyle.drawPanel(x, y, width, height);
            drawHead(minecraft, target.marker,
                    x + PADDING, y + PADDING);
            int textX = x + PADDING + HEAD_SIZE + 6;
            int textY = y + PADDING;
            drawColored(font, name, textX, textY,
                    target.marker.nameColor);
            textY += font.FONT_HEIGHT;
            if (title.length() > 0) {
                LostTalesChatVisualStyle.drawPlain(font, title,
                        textX, textY, 255);
                textY += font.FONT_HEIGHT;
            }
            drawColored(font, accountLine, textX, textY,
                    LostTalesSkyrimUiStyle.TEXT_MUTED);
            textY += font.FONT_HEIGHT;
            if (raceLine.length() > 0) {
                drawColored(font, raceLine, textX, textY,
                        LostTalesSkyrimUiStyle.TEXT_MUTED);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }
    }

    private static Target find(Minecraft minecraft, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.ingameGUI == null
                || minecraft.fontRenderer == null) {
            return null;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        if (chat == null || !chat.getChatOpen()) {
            return null;
        }
        try {
            List<ChatLine> lines =
                    LostTalesChatOverlayRenderer.getDrawnLines(chat);
            int scroll = LostTalesChatOverlayRenderer.getScrollPosition(chat);
            if (lines == null || lines.isEmpty()) {
                return null;
            }
            float chatScale = chat.func_146244_h();
            float originX = LostTalesChatOverlayRenderer.getLastOffsetX()
                    + 2.0F;
            float originY = LostTalesChatOverlayRenderer.getLastOffsetY()
                    + 20.0F
                    + LostTalesChatOverlayRenderer
                    .getEntryDisplacement(scroll);
            int visible = chat.func_146232_i();
            for (int visibleIndex = 0;
                 visibleIndex < visible
                         && visibleIndex + scroll < lines.size();
                 visibleIndex++) {
                int lineIndex = visibleIndex + scroll;
                ChatLine line = lines.get(lineIndex);
                if (line == null) {
                    continue;
                }
                float lineTranslateY = -visibleIndex * 9.0F - 8.0F;
                int cursor = 0;
                for (Object value : line.func_151461_a()) {
                    if (!(value instanceof IChatComponent)) {
                        continue;
                    }
                    IChatComponent part = (IChatComponent)value;
                    String formatted = part.getChatStyle()
                            .getFormattingCode()
                            + part.getUnformattedTextForChat();
                    int partWidth = minecraft.fontRenderer
                            .getStringWidth(formatted);
                    boolean head = ChatHeadMarker.decode(part) != null;
                    boolean identity = isReplyIdentity(part);
                    if (head || identity) {
                        float localX = head ? cursor + 0.5F : cursor;
                        float localY = head ? -0.5F : 0.0F;
                        float localWidth = head ? 9.0F : partWidth;
                        float localHeight = head ? 9.0F
                                : minecraft.fontRenderer.FONT_HEIGHT;
                        float left = originX + chatScale * localX;
                        float top = originY + chatScale
                                * (lineTranslateY + localY);
                        float right = left + chatScale * localWidth;
                        float bottom = top + chatScale * localHeight;
                        if (contains(mouseX, mouseY,
                                left, top, right, bottom)) {
                            return targetForGroup(lines, lineIndex);
                        }
                    }
                    cursor += partWidth;
                }
            }
        } catch (IllegalAccessException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static Target targetForGroup(List<ChatLine> lines,
                                         int lineIndex) {
        ChatLine selected = lines.get(lineIndex);
        if (selected == null) {
            return null;
        }
        ChatHeadMarker.Data marker = null;
        String identity = "";
        String title = "";
        String account = "";
        boolean afterHead = false;
        // GuiNewChat inserts wrapped lines at index zero. Walk this group in
        // reverse list order to reconstruct the original component sequence.
        for (int candidateIndex = lines.size() - 1;
             candidateIndex >= 0; candidateIndex--) {
            ChatLine candidate = lines.get(candidateIndex);
            if (candidate == null
                    || candidate.getUpdatedCounter()
                    != selected.getUpdatedCounter()
                    || candidate.getChatLineID()
                    != selected.getChatLineID()) {
                continue;
            }
            for (Object value : candidate.func_151461_a()) {
                if (!(value instanceof IChatComponent)) {
                    continue;
                }
                IChatComponent part = (IChatComponent)value;
                ChatHeadMarker.Data decoded = ChatHeadMarker.decode(part);
                if (decoded != null) {
                    marker = decoded;
                    afterHead = true;
                    continue;
                }
                if (isReplyIdentity(part)) {
                    identity = LostTalesChatVisualStyle.removeColorCodes(
                            part.getUnformattedTextForChat()).trim();
                    account = replyAccount(part);
                } else if (afterHead && title.length() == 0
                        && identity.length() == 0
                        && part.getUnformattedTextForChat()
                        .startsWith("[")) {
                    title = LostTalesChatVisualStyle.removeColorCodes(
                            part.getUnformattedTextForChat()).trim();
                }
            }
        }
        return marker == null || identity.length() == 0
                || account.length() == 0 ? null
                : new Target(marker, identity, title, account);
    }

    private static boolean isReplyIdentity(IChatComponent part) {
        ClickEvent click = part == null || part.getChatStyle() == null
                ? null : part.getChatStyle().getChatClickEvent();
        return click != null
                && click.getAction() == ClickEvent.Action.SUGGEST_COMMAND
                && click.getValue() != null
                && click.getValue().startsWith("/msg ");
    }

    private static String replyAccount(IChatComponent part) {
        String value = part.getChatStyle().getChatClickEvent().getValue();
        String account = value.substring("/msg ".length()).trim();
        int space = account.indexOf(' ');
        return space < 0 ? account : account.substring(0, space);
    }

    private static String raceFor(Target target) {
        CharacterAppearance appearance =
                ClientCharacterAppearanceCache.getAuthoritative(
                        target.marker.senderId);
        if (appearance == null || !appearance.isPresent()) {
            return "";
        }
        if (!target.marker.accountIdentity
                && !target.identityName.equals(
                appearance.getCharacterName())) {
            // A historic chat line must not borrow the sender's newer
            // character identity after a switch.
            return "";
        }
        return ClientCharacterDisplayNames.race(appearance.getRaceId());
    }

    private static void drawHead(Minecraft minecraft,
                                 ChatHeadMarker.Data marker,
                                 float x, float y) {
        if (marker.accountIdentity) {
            LostTalesCharacterHeadIconRenderer.drawAccountHead(
                    minecraft, marker.senderId, x, y,
                    HEAD_SIZE, 1.0F, 1.0F);
        } else {
            LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                    minecraft, marker.senderId, marker.skinId,
                    x, y, HEAD_SIZE, 1.0F, 1.0F);
        }
    }

    private static void drawColored(FontRenderer font, String text,
                                    int x, int y, int color) {
        String visible = LostTalesChatVisualStyle.removeColorCodes(text);
        font.drawString(visible, x + 1, y + 1,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SHADOW, 255));
        font.drawString(visible, x, y,
                LostTalesChatVisualStyle.argb(color, 255));
    }

    static boolean contains(float x, float y, float left, float top,
                            float right, float bottom) {
        return x >= Math.min(left, right) && x < Math.max(left, right)
                && y >= Math.min(top, bottom) && y < Math.max(top, bottom);
    }

    static int cardX(int mouseX, int width, int screenWidth) {
        int candidate = mouseX + 12;
        if (candidate + width > screenWidth - 4) {
            candidate = mouseX - width - 12;
        }
        return Math.max(4, Math.min(candidate,
                Math.max(4, screenWidth - width - 4)));
    }

    static int cardY(int mouseY, int height, int screenHeight) {
        int candidate = mouseY + 8;
        if (candidate + height > screenHeight - 4) {
            candidate = mouseY - height - 8;
        }
        return Math.max(4, Math.min(candidate,
                Math.max(4, screenHeight - height - 4)));
    }

    private static String cleanBracketed(String value) {
        String result = value == null ? "" : value.trim();
        if (result.startsWith("[") && result.endsWith("]")
                && result.length() > 1) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }

    private static final class Target {
        final ChatHeadMarker.Data marker;
        final String identityName;
        final String title;
        final String accountName;

        Target(ChatHeadMarker.Data marker, String identityName,
               String title, String accountName) {
            this.marker = marker;
            this.identityName = identityName;
            this.title = title;
            this.accountName = accountName;
        }
    }
}
