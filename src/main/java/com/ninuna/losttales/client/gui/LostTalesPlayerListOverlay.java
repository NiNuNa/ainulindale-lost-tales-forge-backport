package com.ninuna.losttales.client.gui;

import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.client.chat.ChatPresenceMark;
import com.ninuna.losttales.client.chat.ClientChatPresence;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import org.lwjgl.opengl.GL11;

/**
 * The player list the Tab key shows, drawn in place of the game's own so
 * every row shows the character an account is playing rather than the
 * account: their head, then their name. The grid is the game's: the server's player cap in columns of
 * at most twenty rows, three hundred pixels shared between the columns
 * and none wider than a hundred and fifty, a ping bar at each row's end
 * and the tab-list score where a server sets one. The names come from
 * the appearance the server syncs for every online player, and an
 * account this client knows no character for yet is named as itself.
 */
public final class LostTalesPlayerListOverlay extends Gui {
    static final int MAX_ROWS = 20;
    static final int LIST_WIDTH = 300;
    static final int MAX_COLUMN_WIDTH = 150;
    static final int ROW_HEIGHT = 9;
    static final int TOP = 10;
    /** A head as the chat draws one, then a gap before the name. */
    static final int HEAD_SIZE = 8;
    /**
     * The icon's whole width: the head and the presence sphere standing
     * past it. Every row keeps the same column, so a row whose account
     * this client cannot place still lines up with the rest.
     */
    static final int ICON_WIDTH = HEAD_SIZE + ChatPresenceMark.OVERHANG_X;
    static final int HEAD_GAP = 2;
    private static final int PING_WIDTH = 10;
    private static final int PING_HEIGHT = 8;
    /** The ping bars' row on the game's icon sheet, one bar picture per height. */
    private static final int PING_ICON_V = 176;
    private static final int PANEL_ARGB =
            LostTalesColors.withAlpha(LostTalesColors.PLUM_BLACK, 0x80);
    private static final int CELL_ARGB =
            LostTalesColors.withAlpha(LostTalesColors.IVORY, 0x20);

    private static final LostTalesPlayerListOverlay DRAWER =
            new LostTalesPlayerListOverlay();

    private LostTalesPlayerListOverlay() {}

    /**
     * Draws the list across a screen {@code width} wide. False, with
     * nothing drawn, when there is no world or player to list for, so
     * the game's own list may stand in.
     */
    public static boolean draw(Minecraft minecraft, int width) {
        if (minecraft == null || minecraft.thePlayer == null
                || minecraft.theWorld == null || minecraft.fontRenderer == null
                || minecraft.thePlayer.sendQueue == null) {
            return false;
        }
        NetHandlerPlayClient handler = minecraft.thePlayer.sendQueue;
        @SuppressWarnings("unchecked")
        List<GuiPlayerInfo> players =
                new ArrayList<GuiPlayerInfo>(handler.playerInfoList);
        int maxPlayers = handler.currentServerMaxPlayers;
        int columns = columns(maxPlayers);
        int rows = rows(maxPlayers, columns);
        int columnWidth = columnWidth(columns);
        int left = (width - columns * columnWidth) / 2;
        Scoreboard scoreboard = minecraft.theWorld.getScoreboard();
        ScoreObjective objective = scoreboard.func_96539_a(0);
        FontRenderer font = minecraft.fontRenderer;
        drawRect(left - 1, TOP - 1, left + columnWidth * columns,
                TOP + ROW_HEIGHT * rows, PANEL_ARGB);
        for (int index = 0; index < maxPlayers; index++) {
            int x = left + index % columns * columnWidth;
            int y = TOP + index / columns * ROW_HEIGHT;
            drawRect(x, y, x + columnWidth - 1, y + 8, CELL_ARGB);
            if (index >= players.size()) {
                continue;
            }
            GuiPlayerInfo player = players.get(index);
            CharacterAppearance appearance =
                    ClientCharacterAppearanceCache.appearanceFor(player.name);
            LostTalesSkyrimUiStyle.beginContent();
            boolean known = appearance != null
                    && appearance.getPlayerId() != null;
            if (known) {
                ChatPresenceMark.beginHeadCut(x, y, HEAD_SIZE);
            }
            try {
                drawHead(minecraft, appearance, x, y);
            } finally {
                if (known) {
                    ChatPresenceMark.endHeadCut();
                }
            }
            if (known) {
                // The row names the identity being played, so it wears
                // that identity's status.
                ChatPresenceMark.draw(x, y, HEAD_SIZE,
                        ClientChatPresence.presenceOf(
                                appearance.getPlayerId(),
                                appearance.isAccount()
                                        ? ChatPresenceIdentity.ACCOUNT
                                        : ChatPresenceIdentity.character(
                                                appearance.getCharacterId())),
                        255);
            }
            int nameX = x + ICON_WIDTH + HEAD_GAP;
            ScorePlayerTeam team = scoreboard.getPlayersTeam(player.name);
            String shown = ScorePlayerTeam.formatPlayerName(team,
                    shownNameOf(appearance, player.name));
            LostTalesSkyrimUiStyle.beginContent();
            font.drawStringWithShadow(shown, nameX, y, LostTalesColors.TEXT_BRIGHT);
            if (objective != null) {
                int endX = nameX + font.getStringWidth(shown) + 5;
                int maxX = x + columnWidth - PING_WIDTH - 2 - 5;
                if (maxX - endX > 5) {
                    Score score = scoreboard.func_96529_a(player.name, objective);
                    String points = String.valueOf(score.getScorePoints());
                    font.drawStringWithShadow(points,
                            maxX - font.getStringWidth(points), y,
                            LostTalesColors.GOLD);
                }
            }
            LostTalesSkyrimUiStyle.beginContent();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            minecraft.getTextureManager().bindTexture(Gui.icons);
            DRAWER.zLevel += 100.0F;
            DRAWER.drawTexturedModalRect(x + columnWidth - PING_WIDTH - 2, y, 0,
                    PING_ICON_V + pingIndex(player.responseTime) * PING_HEIGHT,
                    PING_WIDTH, PING_HEIGHT);
            DRAWER.zLevel -= 100.0F;
        }
        return true;
    }

    /** The row's name: the character the account plays, else the account. */
    static String shownNameOf(CharacterAppearance appearance, String account) {
        String character = appearance == null || !appearance.hasCharacter()
                ? "" : appearance.getCharacterName().trim();
        return character.length() == 0 ? account : character;
    }

    /**
     * The row's head: the character's skin where one is played, the
     * account's otherwise, and nothing for a player this client knows
     * no appearance of yet.
     */
    private static void drawHead(Minecraft minecraft, CharacterAppearance appearance,
                                 int x, int y) {
        if (appearance == null || appearance.getPlayerId() == null) {
            return;
        }
        if (appearance.hasCharacter() && appearance.getSkinId().length() > 0) {
            LostTalesCharacterHeadIconRenderer.drawSnapshotHead(minecraft,
                    appearance.getPlayerId(), appearance.getSkinId(), x, y,
                    HEAD_SIZE, 1.0F, 1.0F);
        } else {
            LostTalesCharacterHeadIconRenderer.drawAccountHead(minecraft,
                    appearance.getPlayerId(), x, y, HEAD_SIZE, 1.0F, 1.0F);
        }
    }

    /** Columns enough that no column holds more than {@link #MAX_ROWS}. */
    static int columns(int maxPlayers) {
        int columns = 1;
        while (rows(maxPlayers, columns) > MAX_ROWS) {
            columns++;
        }
        return columns;
    }

    /** Rows per column for the player cap spread over {@code columns}. */
    static int rows(int maxPlayers, int columns) {
        return columns <= 1 ? maxPlayers : (maxPlayers + columns - 1) / columns;
    }

    /** The columns share the list's width, none wider than the cap. */
    static int columnWidth(int columns) {
        return Math.min(MAX_COLUMN_WIDTH, LIST_WIDTH / Math.max(1, columns));
    }

    /**
     * Which of the game's ping pictures a round trip earns: five bars
     * under 150 ms down to one under a second, none past that, and the
     * broken one for no answer.
     */
    static int pingIndex(int ping) {
        if (ping < 0) {
            return 5;
        }
        if (ping < 150) {
            return 0;
        }
        if (ping < 300) {
            return 1;
        }
        if (ping < 600) {
            return 2;
        }
        if (ping < 1000) {
            return 3;
        }
        return 4;
    }
}
