package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiChat;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * Installs the channel-aware input, the narrow CHAT-only render pass, and
 * the routing of server-visible vanilla lines into the Global channel.
 */
public final class LostTalesChatClientHandler {
    private static final Field DEFAULT_INPUT = findDefaultInputField();
    /** Newest messages inspected for stray lines before giving up. */
    private static final int UNTRACKED_SCAN_LIMIT = 16;

    /** The newest history entry the stray-line watcher has seen. */
    private ChatLine watchedHead;

    public LostTalesChatClientHandler() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void replaceVanillaChat(GuiOpenEvent event) {
        if (event == null || event.gui == null
                || event.gui.getClass() != GuiChat.class
                || DEFAULT_INPUT == null) {
            return;
        }
        try {
            event.gui = new LostTalesChatGui(
                    (String)DEFAULT_INPUT.get(event.gui));
        } catch (IllegalAccessException ignored) {
            // Keeping the original GUI is safer than losing command input.
        }
    }

    /**
     * Files every incoming vanilla or third-party line under a channel —
     * Global for what the whole server sees, the console for the rest —
     * and prints it with the channel prefix, a timestamp and a tracked
     * line id, so the feed and the tabs treat it like any other line.
     * Runs last so every other mod has had its say on the component.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void routeSystemLine(ClientChatReceivedEvent event) {
        if (event == null || event.isCanceled() || event.message == null) {
            return;
        }
        ChatChannel channel = ChatSystemLineClassifier.classify(event.message);
        if (channel != null && LostTalesChatPresentation.receiveSystemLine(
                event.message, channel,
                !ChatSystemLineClassifier.isMentionCueSilent(event.message))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void renderChat(RenderGameOverlayEvent.Pre event) {
        if (event != null
                && event.type == RenderGameOverlayEvent.ElementType.CHAT
                && event instanceof RenderGameOverlayEvent.Chat) {
            // Windows are placed by the chat layout, not by the vanilla
            // anchor Forge passes; the event still marks the chat pass.
            if (!LostTalesChatOverlayRenderer.draw(Minecraft.getMinecraft(),
                    event.partialTicks)) {
                return;
            }
            event.setCanceled(true);
        }
    }

    /**
     * Notices lines printed straight into the chat without passing
     * through the received-chat event — a game-mode change notice, a
     * screenshot's saved-as notice, another mod's local print. Each one
     * is adopted into the console: rebuilt in place as a system line
     * with the channel prefix, the timestamp and a tracked id, and the
     * history laid out again once, so a local print reads exactly like
     * routed console output. The console reopens for them like it does
     * for any message, unless it is hidden. Once per tick over the
     * newest few history entries; everything Lost Tales routed is
     * already filed by the time the tick runs, so only genuinely stray
     * lines match. A stray printed under a deletable id of its own is
     * left as it is — its printer may still replace it by that id — and
     * only brings the console tab back.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.theWorld == null
                || minecraft.ingameGUI == null) {
            this.watchedHead = null;
            return;
        }
        try {
            List<ChatLine> messages = ChatWindowLines.messageHistory(
                    minecraft.ingameGUI.getChatGUI());
            if (messages == null) {
                return;
            }
            ChatLine head = messages.isEmpty() ? null : messages.get(0);
            ChatLine previous = this.watchedHead;
            this.watchedHead = head;
            if (head == null || head == previous) {
                return;
            }
            boolean stray = false;
            boolean adopted = false;
            for (int index = 0; index < messages.size()
                    && index < UNTRACKED_SCAN_LIMIT; index++) {
                ChatLine line = messages.get(index);
                if (line == previous) {
                    break;
                }
                if (line != null && ClientChatChannelViews.tabOf(
                        line.getChatLineID()) == null) {
                    stray = true;
                    adopted |= LostTalesChatPresentation.adoptStrayLine(
                            messages, index);
                }
            }
            if (!stray) {
                return;
            }
            if (adopted) {
                // The drawn lines are rebuilt from the adopted history,
                // and the new head is remembered so the next tick does
                // not rescan what was just adopted.
                minecraft.ingameGUI.getChatGUI().refreshChat();
                this.watchedHead = messages.isEmpty()
                        ? null : messages.get(0);
            }
            ChatTab console = ChatTab.of(ChatChannel.CONSOLE);
            if (!ChatWindowLayout.isOpen(console)
                    && !ChatWindowLayout.isHidden(console)) {
                // The active window takes the tab first, like any other
                // reopening channel.
                ChatWindow selectedWindow = ChatWindowLayout.windowOf(
                        ClientChatChannelState.getSelected());
                ChatWindowLayout.openTab(console, selectedWindow == null
                        ? null : selectedWindow.getId());
            }
        } catch (RuntimeException ignored) {
            // Watching is best-effort; the chat itself is untouched.
        }
    }

    private static Field findDefaultInputField() {
        try {
            Field field = GuiChat.class.getDeclaredField(
                    "defaultInputFieldText");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
