package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
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

    public LostTalesChatClientHandler() {
        MinecraftForge.EVENT_BUS.register(this);
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
                event.message, channel)) {
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
            if (!LostTalesChatOverlayRenderer.draw(Minecraft.getMinecraft())) {
                return;
            }
            event.setCanceled(true);
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
