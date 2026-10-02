package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.compat.lotr.LotrFellowshipMirror;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.storage.FellowshipStorage;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.command.ICommand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.event.CommandEvent;

/**
 * LOTR's {@code /fmsg} (and {@code /fchat}) says its words in a fellowship's
 * conversation, as a line typed there: {@code /fmsg "Grey Company" words}
 * names the fellowship, a plain {@code /fmsg words} goes to the one it is
 * bound to, {@code /fmsg bind "Grey Company"} binds it and
 * {@code /fmsg unbind} lets it go. The fellowship is one of the character
 * played, found by its name whatever its case. LOTR's own yellow line is
 * never sent. The binding is kept where LOTR keeps it, on the account, as
 * the LOTR fellowship behind ours. The words spend the message budget a
 * typed line spends ({@code CHAT_MESSAGE}), and the Server Log shows
 * the command without them ({@code ChatCommandText}).
 */
public final class FellowshipMessageCommand {
    private static final String BIND = "bind";
    private static final String UNBIND = "unbind";

    /** After the speech gate and the Server Log have had their say. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCommand(CommandEvent event) {
        if (event == null || event.isCanceled() || event.command == null
                || !(event.sender instanceof EntityPlayerMP)
                || !isFellowshipMessage(event.command)) {
            return;
        }
        event.setCanceled(true);
        answer((EntityPlayerMP)event.sender, ChatSpeechGate.joined(event.parameters));
    }

    static boolean isFellowshipMessage(ICommand command) {
        String name = command.getCommandName();
        return name != null && "fmsg".equals(name.trim().toLowerCase(Locale.ROOT));
    }

    private static void answer(EntityPlayerMP player, String line) {
        Request request = Request.parse(line);
        if (request == null) {
            tell(player, "chat.losttales.fmsg.usage");
            return;
        }
        if (request.unbind) {
            LotrFellowshipMirror.bind(player, null);
            tell(player, "chat.losttales.fmsg.unbound");
            return;
        }
        Fellowship fellowship = request.name == null ? bound(player)
                : named(player, request.name);
        if (fellowship == null) {
            if (request.name == null) {
                tell(player, "chat.losttales.fmsg.none");
            } else {
                tell(player, "chat.losttales.fmsg.not_found", request.name);
            }
            return;
        }
        if (request.bind) {
            UUID mirror = mirrorOf(player, fellowship);
            if (mirror == null) {
                tell(player, "chat.losttales.fmsg.not_bound", fellowship.getName());
                return;
            }
            LotrFellowshipMirror.bind(player, mirror);
            tell(player, "chat.losttales.fmsg.bound", fellowship.getName());
            return;
        }
        LostTalesChatSendPacket said;
        try {
            said = new LostTalesChatSendPacket(ChatChannel.FELLOWSHIP, request.words,
                    Collections.<ChatShareReference>emptyList(),
                    fellowship.getFellowshipId().toString(),
                    LostTalesChatSendPacket.IDENTITY_DEFAULT, null, ChatMessageIds.NONE,
                    "", 0L, null, false);
        } catch (IllegalArgumentException refused) {
            tell(player, "chat.losttales.fmsg.usage");
            return;
        }
        // Words said this way spend the budget a typed line spends, and a
        // spent budget drops them the same way: logged, and nobody told.
        if (!LostTalesRequestRateLimiter.allow(player,
                LostTalesRequestRateLimiter.RequestType.CHAT_MESSAGE)) {
            LostTalesRequestRateLimiter.logRateLimited(player,
                    LostTalesRequestRateLimiter.RequestType.CHAT_MESSAGE, "/fmsg");
            return;
        }
        LostTalesChatService.send(player, said);
    }

    /** The fellowship of the character played that the account's plain /fmsg is bound to; null for none. */
    private static Fellowship bound(EntityPlayerMP player) {
        UUID mirror = LotrFellowshipMirror.boundTo(player);
        if (mirror == null) {
            return null;
        }
        for (Fellowship fellowship : ChatIdentitySelection.fellowships(player)) {
            if (mirror.equals(mirrorOf(player, fellowship))) {
                return fellowship;
            }
        }
        return null;
    }

    private static Fellowship named(EntityPlayerMP player, String name) {
        for (Fellowship fellowship : ChatIdentitySelection.fellowships(player)) {
            if (fellowship.getName().equalsIgnoreCase(name)) {
                return fellowship;
            }
        }
        return null;
    }

    private static UUID mirrorOf(EntityPlayerMP player, Fellowship fellowship) {
        try {
            return FellowshipStorage.get(player.worldObj).getMirrorId(fellowship.getFellowshipId());
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static void tell(EntityPlayerMP player, String key, Object... arguments) {
        player.addChatMessage(new ChatComponentTranslation(key, arguments));
    }

    /** What an /fmsg asks: to bind, to unbind, or to say words, to a fellowship named or not. */
    static final class Request {
        final boolean bind;
        final boolean unbind;
        /** The fellowship named in quotes; null for the bound one. */
        final String name;
        final String words;

        private Request(boolean bind, boolean unbind, String name, String words) {
            this.bind = bind;
            this.unbind = unbind;
            this.name = name;
            this.words = words;
        }

        /** The request a line of /fmsg makes; null for one that makes none. */
        static Request parse(String line) {
            String text = line == null ? "" : line.trim();
            if (text.equalsIgnoreCase(UNBIND)) {
                return new Request(false, true, null, "");
            }
            boolean bind = text.toLowerCase(Locale.ROOT).startsWith(BIND + " ");
            String rest = bind ? text.substring(BIND.length()).trim() : text;
            String name = null;
            if (rest.startsWith("\"")) {
                int close = rest.indexOf('"', 1);
                if (close <= 1) {
                    return null;
                }
                name = rest.substring(1, close).trim();
                rest = rest.substring(close + 1).trim();
            }
            if (bind) {
                return name != null && name.length() > 0 && rest.length() == 0
                        ? new Request(true, false, name, "") : null;
            }
            return rest.length() == 0 || (name != null && name.length() == 0) ? null
                    : new Request(false, false, name, rest);
        }
    }
}
