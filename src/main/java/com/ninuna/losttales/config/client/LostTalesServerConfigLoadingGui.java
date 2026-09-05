package com.ninuna.losttales.config.client;

import com.ninuna.losttales.client.chat.ClientChatChannelState;
import com.ninuna.losttales.config.server.LostTalesServerConfigService;
import com.ninuna.losttales.config.server.ServerConfigApplyResult;
import com.ninuna.losttales.config.server.ServerConfigChange;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesServerConfigApplyPacket;
import com.ninuna.losttales.network.packet.LostTalesServerConfigRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * The screen between an operator and the server's settings: it asks the
 * server for its config and opens the editor when the snapshot arrives,
 * or sends the editor's changes and shows what became of them. A server
 * that does not answer — because the asker is no operator, or is not
 * connected to one — says so after a moment instead of waiting forever.
 */
public final class LostTalesServerConfigLoadingGui extends GuiScreen {

    private static final int BUTTON_BACK = 1;
    private static final int ANSWER_TIMEOUT_TICKS = 200;

    private final GuiScreen parent;
    private final List<ServerConfigChange> changes;
    private int expectedSequence;
    private int ticksWaited;
    private boolean answered;
    private final List<String> lines = new ArrayList<String>();

    /**
     * Whether this client may open server settings right now: in a
     * world, only once the server has said the player is an operator;
     * in the main menu always, since the settings are then the local
     * file's — the server this game hosts.
     */
    public static boolean canOpenServerSettings(Minecraft minecraft) {
        if (minecraft == null) {
            return false;
        }
        return minecraft.theWorld == null || ClientChatChannelState.hasAdminAccess();
    }

    /**
     * The screen that edits the server settings this client can reach:
     * over the network while in a world, straight off the local file in
     * the main menu.
     */
    public static GuiScreen open(Minecraft minecraft, GuiScreen parent) {
        if (minecraft != null && minecraft.theWorld == null) {
            return new LostTalesServerConfigGui(parent,
                    LostTalesServerConfigService.snapshot(), true);
        }
        return new LostTalesServerConfigLoadingGui(parent);
    }

    /** Shows what a local apply came to; nothing is asked of a server. */
    public static LostTalesServerConfigLoadingGui showing(GuiScreen parent,
                                                          ServerConfigApplyResult result) {
        LostTalesServerConfigLoadingGui screen = new LostTalesServerConfigLoadingGui(parent);
        screen.answered = true;
        screen.describe(result);
        return screen;
    }

    /** Asks for the snapshot and opens the editor over {@code parent}. */
    public LostTalesServerConfigLoadingGui(GuiScreen parent) {
        this(parent, null);
    }

    /** Sends {@code changes} and shows the result; {@code parent} is returned to. */
    public LostTalesServerConfigLoadingGui(GuiScreen parent, List<ServerConfigChange> changes) {
        this.parent = parent;
        this.changes = changes;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(BUTTON_BACK, this.width / 2 - 50,
                this.height - 40, 100, 20, I18n.format("gui.back")));
        if (this.expectedSequence == 0 && !this.answered) {
            if (this.mc.theWorld == null) {
                // Not on a server: nothing to ask, and nothing will answer.
                this.answered = true;
                this.lines.add(I18n.format("gui.losttales.server_settings.no_answer"));
            } else if (this.changes == null) {
                this.expectedSequence = ClientServerConfigCache.getSnapshotSequence() + 1;
                LostTalesNetworkHandler.CHANNEL.sendToServer(
                        new LostTalesServerConfigRequestPacket());
            } else {
                this.expectedSequence = ClientServerConfigCache.getResultSequence() + 1;
                LostTalesNetworkHandler.CHANNEL.sendToServer(
                        new LostTalesServerConfigApplyPacket(this.changes));
            }
        }
    }

    @Override
    public void updateScreen() {
        if (this.answered) {
            return;
        }
        if (this.changes == null) {
            if (ClientServerConfigCache.getSnapshotSequence() >= this.expectedSequence) {
                this.answered = true;
                this.mc.displayGuiScreen(new LostTalesServerConfigGui(this.parent,
                        ClientServerConfigCache.getSnapshot()));
                return;
            }
        } else if (ClientServerConfigCache.getResultSequence() >= this.expectedSequence) {
            this.answered = true;
            describe(ClientServerConfigCache.getResult());
            return;
        }
        if (++this.ticksWaited > ANSWER_TIMEOUT_TICKS) {
            this.answered = true;
            this.lines.add(I18n.format("gui.losttales.server_settings.no_answer"));
        }
    }

    private void describe(ServerConfigApplyResult result) {
        this.lines.clear();
        if (result == null) {
            this.lines.add(I18n.format("gui.losttales.server_settings.no_answer"));
            return;
        }
        if (result.getMessage().length() > 0) {
            this.lines.add(result.getMessage());
        }
        if (result.getApplied().isEmpty() && result.getRefused().isEmpty()) {
            this.lines.add(I18n.format("gui.losttales.server_settings.nothing_changed"));
        }
        if (!result.getApplied().isEmpty()) {
            this.lines.add(I18n.format("gui.losttales.server_settings.applied",
                    join(result.getApplied())));
        }
        for (ServerConfigApplyResult.Refusal refusal : result.getRefused()) {
            this.lines.add(I18n.format("gui.losttales.server_settings.refused",
                    refusal.getName(), refusal.getReason()));
        }
        if (!result.getRestarted().isEmpty()) {
            this.lines.add(I18n.format("gui.losttales.server_settings.restarted",
                    join(result.getRestarted())));
        }
    }

    private static String join(List<String> values) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(value);
        }
        return joined.toString();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button != null && button.id == BUTTON_BACK) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        LostTalesSkyrimUiStyle.drawScreenShade(this.width, this.height);
        LostTalesSkyrimUiStyle.drawCenteredHeader(this.fontRendererObj,
                I18n.format("gui.losttales.server_settings.title"),
                I18n.format("gui.losttales.server_settings.subtitle"), this.width, 12);
        int y = this.height / 2 - 20;
        if (this.lines.isEmpty()) {
            drawCenteredString(this.fontRendererObj, I18n.format(this.changes == null
                    ? "gui.losttales.server_settings.loading"
                    : "gui.losttales.server_settings.applying"),
                    this.width / 2, y, LostTalesSkyrimUiStyle.TEXT_MUTED);
        } else {
            int width = Math.min(this.width - 40, 420);
            for (String line : this.lines) {
                List<?> wrapped = this.fontRendererObj.listFormattedStringToWidth(line, width);
                for (Object part : wrapped) {
                    drawCenteredString(this.fontRendererObj, String.valueOf(part),
                            this.width / 2, y, LostTalesSkyrimUiStyle.TEXT_BRIGHT);
                    y += 11;
                }
                y += 4;
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
