package com.ninuna.losttales.chat.moderation;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.config.LostTalesConfig;
import cpw.mods.fml.common.FMLLog;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * The server's opt-in record of what was said: one JSON line per
 * accepted message, edit, and deletion, appended to a file per UTC day
 * under {@code logs/losttales-chat/}. It exists for moderation — the
 * investigation the live chat window cannot reach back to — and it
 * records private whispers, which is why it is off by default and why
 * its files are deleted past the configured retention, on startup and
 * as the day rolls. Operational data, not world data: the files live
 * with the server's logs and never travel with a save.
 *
 * <p>A write failure disables the log for the session with one severe
 * line — chat itself must never suffer for its record.</p>
 */
public final class ChatAuditLog {

    static final String DIRECTORY = "logs/losttales-chat";
    static final String FILE_PREFIX = "chat-";
    static final String FILE_SUFFIX = ".jsonl";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private static File directory;
    private static Writer writer;
    private static String writerFileName;
    private static boolean failedThisSession;

    private ChatAuditLog() {}

    /** Cleared with the rest of the server's chat state. */
    public static synchronized void onServerStarting() {
        closeWriter();
        directory = null;
        writerFileName = null;
        failedThisSession = false;
        if (!LostTalesConfig.chatAuditLogEnabled) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        directory = server.getFile(DIRECTORY);
        pruneExpiredFiles(System.currentTimeMillis());
    }

    public static synchronized void onServerStopping() {
        closeWriter();
        directory = null;
        writerFileName = null;
        failedThisSession = false;
    }

    public static void logMessage(long messageId, String channelId,
                                  UUID account, String accountName,
                                  UUID characterId, String identityName,
                                  String whisperTarget, String text) {
        append("message", messageId, account, accountName, characterId,
                identityName, channelId, whisperTarget, text);
    }

    public static void logEdit(long messageId, UUID account,
                               String accountName, String text) {
        append("edit", messageId, account, accountName, null, "", "", "",
                text);
    }

    public static void logDelete(long messageId, UUID account,
                                 String accountName) {
        append("delete", messageId, account, accountName, null, "", "", "",
                "");
    }

    private static synchronized void append(String event, long messageId,
                                            UUID account, String accountName,
                                            UUID characterId,
                                            String identityName,
                                            String channelId,
                                            String whisperTarget,
                                            String text) {
        if (!LostTalesConfig.chatAuditLogEnabled || directory == null
                || failedThisSession) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            Writer out = writerFor(now);
            out.write(buildLine(now, event, messageId, account, accountName,
                    characterId, identityName, channelId, whisperTarget,
                    text));
            out.write('\n');
            out.flush();
        } catch (IOException exception) {
            failedThisSession = true;
            closeWriter();
            FMLLog.severe("[%s] Chat audit log disabled for this session: %s",
                    LostTalesMetaData.MOD_ID, exception.toString());
        }
    }

    /** One JSONL record; pure so the format is testable without a file. */
    static String buildLine(long atMillis, String event, long messageId,
                            UUID account, String accountName,
                            UUID characterId, String identityName,
                            String channelId, String whisperTarget,
                            String text) {
        StringBuilder line = new StringBuilder(160);
        line.append("{\"at\":\"").append(utcTimestamp(atMillis));
        line.append("\",\"event\":\"").append(jsonEscape(event));
        line.append("\",\"messageId\":").append(messageId);
        line.append(",\"account\":\"")
                .append(account == null ? "" : account.toString());
        line.append("\",\"accountName\":\"").append(jsonEscape(accountName));
        if (characterId != null) {
            line.append("\",\"characterId\":\"")
                    .append(characterId.toString());
        }
        if (identityName != null && identityName.length() > 0) {
            line.append("\",\"identity\":\"").append(jsonEscape(identityName));
        }
        if (channelId != null && channelId.length() > 0) {
            line.append("\",\"channel\":\"").append(jsonEscape(channelId));
        }
        if (whisperTarget != null && whisperTarget.length() > 0) {
            line.append("\",\"target\":\"").append(jsonEscape(whisperTarget));
        }
        line.append("\",\"text\":\"").append(jsonEscape(text)).append("\"}");
        return line.toString();
    }

    /** Minimal JSON string escaping: quotes, backslashes, and controls. */
    static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\') {
                escaped.append('\\').append(character);
            } else if (character == '\n') {
                escaped.append("\\n");
            } else if (character == '\r') {
                escaped.append("\\r");
            } else if (character == '\t') {
                escaped.append("\\t");
            } else if (character < 0x20) {
                escaped.append(String.format(Locale.ROOT, "\\u%04x",
                        Integer.valueOf(character)));
            } else {
                escaped.append(character);
            }
        }
        return escaped.toString();
    }

    /** The UTC day's file name, {@code chat-2026-09-01.jsonl}. */
    static String fileNameFor(long atMillis) {
        return FILE_PREFIX + utcDay(atMillis) + FILE_SUFFIX;
    }

    /**
     * Whether an audit file's name dates it past the retention window.
     * Anything in the directory that does not parse as one of ours is
     * left alone.
     */
    static boolean isExpiredFileName(String fileName, long nowMillis,
                                     int retentionDays) {
        if (fileName == null || !fileName.startsWith(FILE_PREFIX)
                || !fileName.endsWith(FILE_SUFFIX)) {
            return false;
        }
        String day = fileName.substring(FILE_PREFIX.length(),
                fileName.length() - FILE_SUFFIX.length());
        SimpleDateFormat format = utcFormat("yyyy-MM-dd");
        format.setLenient(false);
        Date parsed;
        try {
            parsed = format.parse(day);
        } catch (java.text.ParseException ignored) {
            return false;
        }
        long keepDays = Math.max(1, retentionDays);
        return parsed.getTime() < nowMillis - keepDays * DAY_MILLIS;
    }

    private static Writer writerFor(long nowMillis) throws IOException {
        String fileName = fileNameFor(nowMillis);
        if (writer != null && fileName.equals(writerFileName)) {
            return writer;
        }
        closeWriter();
        pruneExpiredFiles(nowMillis);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("cannot create " + directory);
        }
        writer = new OutputStreamWriter(new FileOutputStream(
                new File(directory, fileName), true), UTF_8);
        writerFileName = fileName;
        return writer;
    }

    private static void pruneExpiredFiles(long nowMillis) {
        File dir = directory;
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file != null && file.isFile() && isExpiredFileName(
                    file.getName(), nowMillis,
                    LostTalesConfig.chatAuditRetentionDays)
                    && !file.delete()) {
                FMLLog.warning(
                        "[%s] Could not delete expired chat audit file %s",
                        LostTalesMetaData.MOD_ID, file.getName());
            }
        }
    }

    private static void closeWriter() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
            writerFileName = null;
        }
    }

    private static String utcTimestamp(long atMillis) {
        return utcFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(
                new Date(atMillis));
    }

    private static String utcDay(long atMillis) {
        return utcFormat("yyyy-MM-dd").format(new Date(atMillis));
    }

    private static SimpleDateFormat utcFormat(String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format;
    }
}
