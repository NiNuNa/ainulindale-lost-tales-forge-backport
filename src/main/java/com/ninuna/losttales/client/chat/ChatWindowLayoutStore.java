package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * File-backed persistence for {@link ChatWindowLayout}: a per-installation
 * presentation preference like the emote favourites, kept under
 * {@code config/} and never synchronized or cleared between worlds. The
 * file is a few plain lines — one per window, one per closed channel,
 * one per notification preference (muted, hidden from the feed, mention
 * cue silenced) — so a hand edit or a stale entry from an older version
 * cannot corrupt anything: whatever does not parse is skipped and the
 * layout repairs itself on load.
 *
 * <pre>
 * window w1 locked=false x=0.00 y=0.00 active=console tabs=console,admin
 * window w2 locked=true x=62.50 y=100.00 lines=12.40 width=320 active=all tabs=all,ooc,party link=w1:above
 * feed x=0.00 y=100.00
 * toolbar collapsed=false
 * closed faction
 * muted ooc
 * nofeed proximity
 * noping party
 * </pre>
 */
public final class ChatWindowLayoutStore {
    static final String FILE_PATH = "losttales/chat_layout.txt";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static File storeFile;

    private ChatWindowLayoutStore() {}

    public static synchronized void initialize(File configDirectory) {
        storeFile = configDirectory == null
                ? null : new File(configDirectory, FILE_PATH);
        List<String> lines = readLines(storeFile);
        if (lines != null) {
            load(lines);
        } else {
            ChatWindowLayout.reset();
        }
        ChatWindowLayout.setChangeListener(new Runnable() {
            @Override
            public void run() {
                save();
            }
        });
    }

    /** Applies parsed lines to the layout; visible for tests. */
    static void load(List<String> lines) {
        List<ChatWindowLayout.WindowSpec> specs =
                new ArrayList<ChatWindowLayout.WindowSpec>();
        EnumSet<ChatChannel> closed = EnumSet.noneOf(ChatChannel.class);
        List<ChatTab> muted = new ArrayList<ChatTab>();
        List<ChatTab> feedHidden = new ArrayList<ChatTab>();
        List<ChatTab> pingSilenced = new ArrayList<ChatTab>();
        double feedX = 0.0D;
        double feedY = 100.0D;
        boolean collapsed = false;
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.length() == 0 || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length == 2 && "closed".equals(parts[0])) {
                ChatChannel channel = ChatChannel.fromId(parts[1]);
                if (channel != null) {
                    closed.add(channel);
                }
            } else if (parts.length == 2 && "muted".equals(parts[0])) {
                addTab(muted, parts[1]);
            } else if (parts.length == 2 && "nofeed".equals(parts[0])) {
                addTab(feedHidden, parts[1]);
            } else if (parts.length == 2 && "noping".equals(parts[0])) {
                addTab(pingSilenced, parts[1]);
            } else if (parts.length >= 2 && "window".equals(parts[0])) {
                ChatWindowLayout.WindowSpec spec = parseWindow(parts);
                if (spec != null) {
                    specs.add(spec);
                }
            } else if ("feed".equals(parts[0])) {
                for (int index = 1; index < parts.length; index++) {
                    if (parts[index].startsWith("x=")) {
                        feedX = parsePercent(parts[index].substring(2));
                    } else if (parts[index].startsWith("y=")) {
                        feedY = parsePercent(parts[index].substring(2));
                    }
                }
            } else if ("toolbar".equals(parts[0])) {
                for (int index = 1; index < parts.length; index++) {
                    if (parts[index].startsWith("collapsed=")) {
                        collapsed = "true".equalsIgnoreCase(
                                parts[index].substring(10));
                    }
                }
            }
        }
        ChatWindowLayout.load(specs, closed, muted, feedHidden, pingSilenced,
                feedX, feedY, collapsed);
    }

    private static void addTab(List<ChatTab> tabs, String id) {
        ChatTab tab = ChatTab.fromId(id);
        if (tab != null) {
            tabs.add(tab);
        }
    }

    private static ChatWindowLayout.WindowSpec parseWindow(String[] parts) {
        String id = parts[1].toLowerCase(Locale.ROOT);
        boolean legacyMain = ChatWindowLayout.LEGACY_MAIN_ID.equals(id);
        if (!legacyMain && !ChatWindowLayout.isWindowId(id)) {
            return null;
        }
        List<ChatTab> tabs = new ArrayList<ChatTab>();
        ChatTab active = null;
        boolean locked = false;
        // A file from before windows had positions leaves its main
        // window where vanilla draws the chat.
        double offsetX = 0.0D;
        double offsetY = legacyMain ? 100.0D : 0.0D;
        String linkTarget = null;
        ChatWindow.LinkSide linkSide = ChatWindow.LinkSide.BELOW;
        // No size of its own: the window follows the game's settings,
        // which is what every file written before resizing carries.
        double maxLines = 0.0D;
        int width = 0;
        for (int index = 2; index < parts.length; index++) {
            String part = parts[index];
            int equals = part.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = part.substring(0, equals);
            String value = part.substring(equals + 1);
            if ("tabs".equals(key)) {
                for (String tab : value.split(",")) {
                    ChatTab parsed = ChatTab.fromId(tab);
                    if (parsed != null) {
                        tabs.add(parsed);
                    }
                }
            } else if ("link".equals(key)) {
                int colon = value.indexOf(':');
                String target = colon < 0 ? value : value.substring(0, colon);
                if (ChatWindowLayout.isWindowId(target)) {
                    linkTarget = target;
                    linkSide = colon >= 0
                            ? ChatWindow.LinkSide.fromId(
                                    value.substring(colon + 1))
                            : ChatWindow.LinkSide.BELOW;
                }
            } else if ("active".equals(key)) {
                active = ChatTab.fromId(value);
            } else if ("locked".equals(key)) {
                locked = "true".equalsIgnoreCase(value);
            } else if ("x".equals(key)) {
                offsetX = parsePercent(value);
            } else if ("y".equals(key)) {
                offsetY = parsePercent(value);
            } else if ("lines".equals(key)) {
                maxLines = parseLines(value);
            } else if ("width".equals(key)) {
                width = parseChatWidth(value);
            }
        }
        return new ChatWindowLayout.WindowSpec(id, tabs, active, locked,
                offsetX, offsetY, linkTarget, linkSide, maxLines, width);
    }

    /** A stored chat width; anything unreadable follows the slider. */
    private static int parseChatWidth(String value) {
        try {
            return ChatWindowLayout.clampChatWidth(
                    Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * A stored window height in message lines, fractions included, so a
     * window keeps the exact pixel height it was dragged to; anything
     * unreadable follows the setting. Whole numbers, which is all older
     * files hold, read the same as they always did.
     */
    private static double parseLines(String value) {
        try {
            return ChatWindowLayout.clampWindowLines(
                    Double.parseDouble(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    private static double parsePercent(String value) {
        try {
            return ChatWindowLayout.clampPercent(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    /** The lines that describe the current layout; visible for tests. */
    static List<String> describe() {
        List<String> lines = new ArrayList<String>();
        lines.add("# Lost Tales chat window layout");
        for (ChatWindowLayout.WindowSpec spec : ChatWindowLayout.describe()) {
            StringBuilder line = new StringBuilder("window ").append(spec.id);
            line.append(" locked=").append(spec.locked);
            line.append(" x=").append(formatPercent(spec.offsetX));
            line.append(" y=").append(formatPercent(spec.offsetY));
            if (spec.maxLines > 0.0D) {
                line.append(" lines=").append(formatLines(spec.maxLines));
            }
            if (spec.width > 0) {
                line.append(" width=").append(spec.width);
            }
            if (spec.activeTab != null) {
                line.append(" active=").append(spec.activeTab.id());
            }
            if (spec.linkTarget != null) {
                line.append(" link=").append(spec.linkTarget)
                        .append(':').append(spec.linkSide.id());
            }
            line.append(" tabs=");
            for (int index = 0; index < spec.tabs.size(); index++) {
                if (index > 0) {
                    line.append(',');
                }
                line.append(spec.tabs.get(index).id());
            }
            lines.add(line.toString());
        }
        lines.add("feed x=" + formatPercent(ChatWindowLayout.feedOffsetX())
                + " y=" + formatPercent(ChatWindowLayout.feedOffsetY()));
        lines.add("toolbar collapsed="
                + ChatWindowLayout.isToolbarCollapsed());
        for (ChatChannel channel : ChatWindowLayout.closedChannels()) {
            lines.add("closed " + channel.getId());
        }
        for (ChatTab tab : ChatWindowLayout.mutedTabs()) {
            lines.add("muted " + tab.id());
        }
        for (ChatTab tab : ChatWindowLayout.feedHiddenTabs()) {
            lines.add("nofeed " + tab.id());
        }
        for (ChatTab tab : ChatWindowLayout.pingSilencedTabs()) {
            lines.add("noping " + tab.id());
        }
        return lines;
    }

    /** A window height with the fraction the drag left, kept short. */
    private static String formatLines(double lines) {
        return String.format(Locale.ROOT, "%.2f", lines);
    }

    private static String formatPercent(double value) {
        // Locale-independent, two decimals: plenty for a screen percent.
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static synchronized void save() {
        File file = storeFile;
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return;
        }
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(
                    new FileOutputStream(file), UTF_8);
            for (String line : describe()) {
                writer.write(line);
                writer.write('\n');
            }
        } catch (IOException ignored) {
            // Losing a layout write must never break chat.
        } finally {
            closeQuietly(writer);
        }
    }

    private static List<String> readLines(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), UTF_8));
            List<String> lines = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } catch (IOException ignored) {
            return null;
        } finally {
            closeQuietly(reader);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }
}
