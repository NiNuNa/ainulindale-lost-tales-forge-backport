package com.ninuna.losttales.client.chat;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

/** Installs the channel-aware input and the narrow CHAT-only render pass. */
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void renderChat(RenderGameOverlayEvent.Pre event) {
        if (event != null
                && event.type == RenderGameOverlayEvent.ElementType.CHAT
                && event instanceof RenderGameOverlayEvent.Chat) {
            RenderGameOverlayEvent.Chat chatEvent =
                    (RenderGameOverlayEvent.Chat)event;
            if (!LostTalesChatOverlayRenderer.draw(
                    Minecraft.getMinecraft(), chatEvent.posX,
                    chatEvent.posY)) {
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
