package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.ChatChannelSuggester;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatReportReason;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

/**
 * Messages reported to staff (Nils, 2026-09-24). A player may report a
 * line by another player or a Discord member that they were shown, once
 * per message, and five times in ten minutes at most. A report becomes a
 * Server Console entry, which counts as a mention for everyone reading
 * it; the reporter is thanked in their Client Console, and the reported
 * player is never told.
 *
 * <p>What was reported and by whom lives for the server's run only: the
 * console keeps the entries, and these limits need no more.</p>
 */
public final class ChatReports {
    static final int MAX_PER_WINDOW = 5;
    static final long WINDOW_MILLIS = 10L * 60L * 1000L;
    /** The most messages one player is remembered to have reported. */
    static final int MAX_REMEMBERED = 256;

    /** The messages each player has reported, oldest first. */
    private static final Map<UUID, LinkedHashSet<Long>> REPORTED =
            new HashMap<UUID, LinkedHashSet<Long>>();
    /** When each player's reports of the last ten minutes were filed, oldest first. */
    private static final Map<UUID, ArrayDeque<Long>> RECENT =
            new HashMap<UUID, ArrayDeque<Long>>();

    private ChatReports() {}

    /** Files a report of {@code messageId}, or tells the reporter why not. */
    public static void file(EntityPlayerMP reporter, long messageId,
                            ChatReportReason reason, String note) {
        if (reporter == null || reason == null) {
            return;
        }
        UUID account = reporter.getUniqueID();
        ChatHistory.Reportable message = ChatHistory.reportable(messageId, account);
        if (message == null) {
            tell(reporter, "chat.losttales.report.refused");
            return;
        }
        String refusal = admit(account, messageId, System.currentTimeMillis());
        if (refusal != null) {
            tell(reporter, refusal);
            return;
        }
        String link = ChatChannelSuggester.messageLink(message.channel,
                message.scope, messageId);
        ChatConsoleEvent.Report report;
        try {
            report = new ChatConsoleEvent.Report(reason, note, messageId,
                    message.author, message.authorColor, message.excerpt,
                    link == null ? "#" + message.channel.getId() : link);
        } catch (IllegalArgumentException unfit) {
            tell(reporter, "chat.losttales.report.refused");
            return;
        }
        LostTalesChatService.console(ChatConsoleEvent.Kind.REPORT,
                ChatConsoleEvent.Severity.NOTICE,
                reporter.getCommandSenderName(), "reported a message", "",
                report);
        FMLLog.info("[%s] %s reported message %d (%s)", LostTalesMetaData.MOD_ID,
                reporter.getCommandSenderName(), Long.valueOf(messageId),
                reason.name());
        tell(reporter, "chat.losttales.report.thanks");
    }

    /**
     * Whether a player may report {@code messageId} at {@code now}: null
     * when they may, and the report is counted; else the lang key of why
     * not. A message is reported once per player, and five reports stand
     * in any ten minutes.
     */
    static synchronized String admit(UUID reporter, long messageId, long now) {
        LinkedHashSet<Long> reported = REPORTED.get(reporter);
        if (reported != null && reported.contains(Long.valueOf(messageId))) {
            return "chat.losttales.report.already";
        }
        ArrayDeque<Long> recent = RECENT.get(reporter);
        if (recent == null) {
            recent = new ArrayDeque<Long>();
            RECENT.put(reporter, recent);
        }
        while (!recent.isEmpty() && now - recent.peekFirst().longValue()
                >= WINDOW_MILLIS) {
            recent.removeFirst();
        }
        if (recent.size() >= MAX_PER_WINDOW) {
            return "chat.losttales.report.too_many";
        }
        recent.addLast(Long.valueOf(now));
        if (reported == null) {
            reported = new LinkedHashSet<Long>();
            REPORTED.put(reporter, reported);
        }
        reported.add(Long.valueOf(messageId));
        if (reported.size() > MAX_REMEMBERED) {
            Iterator<Long> oldest = reported.iterator();
            oldest.next();
            oldest.remove();
        }
        return null;
    }

    private static void tell(EntityPlayerMP player, String key) {
        player.addChatMessage(new ChatComponentTranslation(key));
    }

    /** Forgets every report; on server start and stop. */
    public static synchronized void clear() {
        REPORTED.clear();
        RECENT.clear();
    }
}
