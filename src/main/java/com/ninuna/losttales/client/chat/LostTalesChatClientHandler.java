package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatSystemLineClassifier;
import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * Installs the channel-aware input, the narrow CHAT-only render pass, and
 * the routing of the server's own lines into the channels
 * {@link com.ninuna.losttales.chat.ChatSystemLineClassifier} names for them.
 */
public final class LostTalesChatClientHandler {
    private static final Field DEFAULT_INPUT = resolveDefaultInputField();
    /** Newest messages inspected for stray lines before giving up. */
    private static final int UNTRACKED_SCAN_LIMIT = 16;
    /** Whether the unreadable opening text has been reported. */
    private static boolean unavailableLogged;

    /** The newest history entry the stray-line watcher has seen. */
    private ChatLine watchedHead;

    public LostTalesChatClientHandler() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void replaceVanillaChat(GuiOpenEvent event) {
        if (event == null || event.gui == null) {
            return;
        }
        // The game clears its message list as the main menu opens, right
        // after this event: leaving a world or a server empties the
        // chat. Everything the chat knows about those lines is dropped
        // with them, so the server's replay on the next join is shown
        // rather than mistaken for lines already held. Asked last, on
        // the screen every other handler has settled on.
        if (event.gui instanceof GuiMainMenu) {
            LostTalesChatPresentation.onVanillaHistoryCleared();
            return;
        }
        if (event.gui.getClass() != GuiChat.class) {
            return;
        }
        if (DEFAULT_INPUT == null) {
            logUnavailableOnce(null);
            return;
        }
        try {
            event.gui = new LostTalesChatGui(
                    (String)DEFAULT_INPUT.get(event.gui));
        } catch (IllegalAccessException refused) {
            // Keeping the original GUI is safer than losing command input.
            logUnavailableOnce(refused);
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
        if (!LostTalesConfig.showQuestChatFeedback
                && isQuestNote(event.message)) {
            // A quest's passing word (tracking, a marker found, rewards)
            // is this player's to switch off; a refusal always shows.
            event.setCanceled(true);
            return;
        }
        ChatChannel channel = ChatSystemLineClassifier.classify(event.message);
        if (channel != null && LostTalesChatPresentation.receiveSystemLine(
                event.message, channel,
                !ChatSystemLineClassifier.isMentionCueSilent(event.message))) {
            event.setCanceled(true);
        }
    }

    /** Whether a line is one of a quest's passing words, {@code chat.losttales.quest.note.*}. */
    static boolean isQuestNote(IChatComponent message) {
        return message instanceof ChatComponentTranslation
                && ((ChatComponentTranslation)message).getKey()
                        .startsWith(QUEST_NOTE_PREFIX);
    }

    /** The lang keys of a quest's passing words. */
    private static final String QUEST_NOTE_PREFIX = "chat.losttales.quest.note.";

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
        ClientChatIdentitySelection.update();
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
                LostTalesChatHistoryHooks.refresh(
                        minecraft.ingameGUI.getChatGUI());
                this.watchedHead = messages.isEmpty()
                        ? null : messages.get(0);
            }
            ChatTab console = ChatTab.of(ChatChannel.CLIENT_CONSOLE);
            if (!ChatWindowLayout.isOpen(console)
                    && !ChatWindowLayout.isHidden(console)) {
                ChatWindowLayout.openTab(console,
                        LostTalesChatPresentation.windowIdOfSelection());
            }
        } catch (RuntimeException ignored) {
            // Watching is best-effort; the chat itself is untouched.
        }
    }

    /**
     * The text vanilla's chat screen opens with ("/" for the command key),
     * by either of its names and verified to be the String it is. A
     * development workspace names it defaultInputFieldText and a release
     * jar field_146409_v. Without it the game's own chat screen stays.
     */
    static Field resolveDefaultInputField() {
        String[] names = { "defaultInputFieldText", "field_146409_v" };
        for (String name : names) {
            try {
                Field field = GuiChat.class.getDeclaredField(name);
                if (Modifier.isStatic(field.getModifiers())
                        || field.getType() != String.class) {
                    continue;
                }
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException missing) {
                continue;
            } catch (RuntimeException inaccessible) {
                return null;
            }
        }
        return null;
    }

    private static void logUnavailableOnce(Throwable cause) {
        if (unavailableLogged) {
            return;
        }
        unavailableLogged = true;
        FMLLog.warning("[%s] GuiChat's opening text (defaultInputFieldText "
                + "or field_146409_v) could not be read, so the chat key "
                + "opens the game's own chat screen instead of the Lost "
                + "Tales chat; another mod may have changed GuiChat (%s)",
                LostTalesMetaData.MOD_ID,
                cause == null ? "no such String field" : cause.toString());
    }
}
