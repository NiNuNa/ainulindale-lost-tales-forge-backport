package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.character.LostTalesClientAccount;
import com.ninuna.losttales.config.LostTalesConfigFiles;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.EnumMap;

/**
 * File-backed persistence for {@link ChatWindowLayout}: a presentation
 * preference of the signed-in account, kept under {@code config/} in a
 * file named after the account and never synchronized or cleared
 * between worlds. Two people sharing a machine keep their own windows,
 * and one person with two accounts keeps an arrangement for each. The
 * file is a few plain lines
 * — one per window, one per small window the player has placed, one per
 * closed channel, one per preference (muted, mentions muted, hidden) —
 * so a hand edit or a stale entry cannot corrupt anything: whatever does
 * not parse is skipped and the layout repairs itself on load.
 *
 * <pre>
 * window w1 locked=false x=0.00 y=0.00 active=client_console tabs=client_console,operator
 * window w2 locked=true x=62.50 y=100.00 lines=12.40 width=320 fill=full area=hidden members=hidden members_width=90.00 active=global tabs=global,ooc,party link=w1:above
 * small emoji x=100.00 y=62.50 w=120 h=160
 * small tab x=12.00 y=40.00
 * feed x=0.00 y=100.00
 * toolbar collapsed=false
 * closed faction
 * muted ooc
 * noping party
 * hidden operator
 * </pre>
 *
 * <p>A whisper tab is remembered per place on a line of its own, its
 * fields tab-separated since a name or a place may hold spaces:
 * {@code conversation}, the place, the window and the tab id for one
 * that was open; {@code closedconversation}, the place and the tab id
 * for one closed by hand.</p>
 */
public final class ChatWindowLayoutStore {
    /** The folder under the client's, one file per account. */
    static final String FOLDER = LostTalesConfigFiles.CHAT_LAYOUTS;
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static File storeFile;

    /**
     * The file as it was read, kept so it can be read again.
     *
     * <p>The layout is loaded at client start-up, which is long before
     * any server has said which channels it defines: a tab on one of
     * those cannot be resolved yet, and a tab that cannot be resolved is
     * skipped. Keeping the lines lets the layout be built again from the
     * same file once the server's channels are in force.</p>
     */
    private static List<String> loadedLines;

    /** Whether the player has moved anything since the layout was built. */
    private static boolean layoutTouched;

    private ChatWindowLayoutStore() {}

    /**
     * Reads the signed-in account's layout as the client starts. The
     * account is the session's own, the one the template file is named
     * after, so every world reads the same file whatever id a server
     * hands out; with no account to name, nothing is read or written.
     */
    public static synchronized void initialize(File configDirectory) {
        initialize(configDirectory, configDirectory == null ? null
                : LostTalesClientAccount.templateId());
    }

    /** As above, for a named account; visible for tests. */
    static synchronized void initialize(File configDirectory, UUID accountId) {
        storeFile = fileFor(configDirectory, accountId);
        List<String> lines = readLines(storeFile);
        if (lines != null) {
            load(lines);
        } else {
            loadedLines = null;
            layoutTouched = false;
            ChatWindowLayout.reset();
            ChatSmallWindowPlacements.load(null);
        }
        ChatWindowLayout.setChangeListener(new Runnable() {
            @Override
            public void run() {
                layoutTouched = true;
                save();
            }
        });
    }

    /**
     * Where an account's layout lives: one file per account in the
     * layouts folder. Null with no folder or no account, and nothing is
     * then written.
     */
    static File fileFor(File configDirectory, UUID accountId) {
        if (configDirectory == null || accountId == null) {
            return null;
        }
        return new File(new File(configDirectory, FOLDER),
                accountId.toString() + ".txt");
    }

    /**
     * Builds the layout again from the file, now that the server's own
     * channels are in force, so a window arranged around one of them is
     * restored rather than quietly dropped and then written away.
     *
     * <p>Only while the player has not moved anything: once they have,
     * what is on screen is newer than the file and is what gets saved.
     * Reading a file that was never written leaves the layout alone.</p>
     */
    public static synchronized void reloadForNewChannels() {
        if (loadedLines != null && !layoutTouched) {
            load(loadedLines);
        }
    }

    /** Applies parsed lines to the layout; visible for tests. */
    static void load(List<String> lines) {
        // The file this layout was built from, and a layout nobody has
        // moved yet: both are what lets it be built again once the
        // server's own channels are in force.
        loadedLines = lines;
        layoutTouched = false;
        List<ChatWindowLayout.WindowSpec> specs =
                new ArrayList<ChatWindowLayout.WindowSpec>();
        Set<ChatChannel> closed = new LinkedHashSet<ChatChannel>();
        List<ChatTab> muted = new ArrayList<ChatTab>();
        List<ChatTab> pingsMuted = new ArrayList<ChatTab>();
        List<ChatTab> hidden = new ArrayList<ChatTab>();
        double feedX = 0.0D;
        double feedY = 100.0D;
        boolean collapsed = false;
        Map<String, List<String[]>> conversations =
                new LinkedHashMap<String, List<String[]>>();
        Map<String, Set<String>> closedConversations =
                new LinkedHashMap<String, Set<String>>();
        Map<ChatSmallWindowKind, ChatSmallWindowPlacements.Placement> placed =
                new EnumMap<ChatSmallWindowKind,
                        ChatSmallWindowPlacements.Placement>(
                        ChatSmallWindowKind.class);
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.length() == 0 || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("conversation\t")
                    || line.startsWith("closedconversation\t")) {
                String[] fields = line.split("\t");
                if ("conversation".equals(fields[0]) && fields.length == 4) {
                    List<String[]> open = conversations.get(fields[1]);
                    if (open == null) {
                        open = new ArrayList<String[]>();
                        conversations.put(fields[1], open);
                    }
                    open.add(new String[] {fields[2], fields[3]});
                } else if ("closedconversation".equals(fields[0])
                        && fields.length == 3) {
                    Set<String> closedHere = closedConversations.get(fields[1]);
                    if (closedHere == null) {
                        closedHere = new LinkedHashSet<String>();
                        closedConversations.put(fields[1], closedHere);
                    }
                    closedHere.add(fields[2]);
                }
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
            } else if (parts.length == 2 && "noping".equals(parts[0])) {
                addTab(pingsMuted, parts[1]);
            } else if (parts.length == 2 && "hidden".equals(parts[0])) {
                addTab(hidden, parts[1]);
            } else if (parts.length >= 2 && "window".equals(parts[0])) {
                ChatWindowLayout.WindowSpec spec = parseWindow(parts);
                if (spec != null) {
                    specs.add(spec);
                }
            } else if (parts.length >= 2 && "small".equals(parts[0])) {
                ChatSmallWindowKind kind = ChatSmallWindowKind.fromId(parts[1]);
                ChatSmallWindowPlacements.Placement placement =
                        parseSmallWindow(parts);
                if (kind != null && placement != null) {
                    placed.put(kind, placement);
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
        ChatWindowLayout.load(specs, closed, muted, pingsMuted, hidden,
                feedX, feedY, collapsed);
        ChatWindowLayout.loadConversations(conversations, closedConversations);
        ChatSmallWindowPlacements.load(placed);
    }

    /**
     * A small window's remembered place: both shares, and both sizes for
     * a kind the player resized or neither for one they only moved; null
     * where a share is missing, a size stands alone or anything is
     * unreadable, which leaves the kind to open where its popup did.
     */
    private static ChatSmallWindowPlacements.Placement parseSmallWindow(
            String[] parts) {
        double x = Double.NaN;
        double y = Double.NaN;
        int width = 0;
        int height = 0;
        for (int index = 2; index < parts.length; index++) {
            String part = parts[index];
            try {
                if (part.startsWith("x=")) {
                    x = Double.parseDouble(part.substring(2));
                } else if (part.startsWith("y=")) {
                    y = Double.parseDouble(part.substring(2));
                } else if (part.startsWith("w=")) {
                    width = Integer.parseInt(part.substring(2));
                } else if (part.startsWith("h=")) {
                    height = Integer.parseInt(part.substring(2));
                }
            } catch (NumberFormatException unreadable) {
                return null;
            }
        }
        if (Double.isNaN(x) || Double.isNaN(y) || width < 0 || height < 0
                || (width > 0) != (height > 0)) {
            return null;
        }
        return new ChatSmallWindowPlacements.Placement(x, y, width, height);
    }

    /** What the file says of a window's area or member list put away. */
    private static final String HIDDEN = "hidden";

    private static void addTab(List<ChatTab> tabs, String id) {
        ChatTab tab = ChatTab.fromId(id);
        if (tab != null) {
            tabs.add(tab);
        }
    }

    private static ChatWindowLayout.WindowSpec parseWindow(String[] parts) {
        String id = parts[1].toLowerCase(Locale.ROOT);
        if (!ChatWindowLayout.isWindowId(id)) {
            return null;
        }
        List<ChatTab> tabs = new ArrayList<ChatTab>();
        ChatTab active = null;
        boolean locked = false;
        double offsetX = 0.0D;
        double offsetY = 0.0D;
        String linkTarget = null;
        ChatWindow.LinkSide linkSide = ChatWindow.LinkSide.BELOW;
        // No size of its own: the window follows the game's settings.
        double maxLines = 0.0D;
        int width = 0;
        ChatWindow.ScreenFill fill = ChatWindow.ScreenFill.NONE;
        boolean areaHidden = false;
        boolean membersHidden = false;
        double membersWidth = 0.0D;
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
            } else if ("fill".equals(key)) {
                fill = ChatWindow.ScreenFill.fromId(value);
            } else if ("area".equals(key)) {
                areaHidden = HIDDEN.equalsIgnoreCase(value);
            } else if ("members".equals(key)) {
                membersHidden = HIDDEN.equalsIgnoreCase(value);
            } else if ("members_width".equals(key)) {
                membersWidth = parseMembersWidth(value);
            }
        }
        return new ChatWindowLayout.WindowSpec(id, tabs, active, locked,
                offsetX, offsetY, linkTarget, linkSide, maxLines, width,
                fill, areaHidden, membersHidden, membersWidth);
    }

    /** A stored member list width; anything unreadable keeps the list's own. */
    private static double parseMembersWidth(String value) {
        try {
            return ChatWindowLayout.clampMembersWidth(
                    Double.parseDouble(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
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

    /** A stored percent; a window's may hang past the margins, the feed's is clamped again by the layout. */
    private static double parsePercent(String value) {
        try {
            return ChatWindowLayout.clampWindowPercent(Double.parseDouble(value));
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
            if (spec.fill != ChatWindow.ScreenFill.NONE) {
                line.append(" fill=").append(spec.fill.id());
            }
            if (spec.areaHidden) {
                line.append(" area=").append(HIDDEN);
            }
            if (spec.membersHidden) {
                line.append(" members=").append(HIDDEN);
            }
            if (spec.membersWidth > 0.0D) {
                line.append(" members_width=")
                        .append(formatLines(spec.membersWidth));
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
        for (Map.Entry<ChatSmallWindowKind, ChatSmallWindowPlacements.Placement>
                small : ChatSmallWindowPlacements.all().entrySet()) {
            ChatSmallWindowPlacements.Placement placement = small.getValue();
            lines.add("small " + small.getKey().id
                    + " x=" + formatPercent(placement.xPercent)
                    + " y=" + formatPercent(placement.yPercent)
                    + (placement.isSized() ? " w=" + placement.width
                            + " h=" + placement.height : ""));
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
        for (ChatTab tab : ChatWindowLayout.pingsMutedTabs()) {
            lines.add("noping " + tab.id());
        }
        for (ChatTab tab : ChatWindowLayout.hiddenTabs()) {
            lines.add("hidden " + tab.id());
        }
        for (Map.Entry<String, List<String[]>> place
                : ChatWindowLayout.rememberedConversations().entrySet()) {
            for (String[] entry : place.getValue()) {
                lines.add("conversation\t" + place.getKey() + "\t" + entry[0]
                        + "\t" + entry[1]);
            }
        }
        for (Map.Entry<String, Set<String>> place
                : ChatWindowLayout.rememberedClosedConversations().entrySet()) {
            for (String id : place.getValue()) {
                lines.add("closedconversation\t" + place.getKey() + "\t" + id);
            }
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
        write(storeFile, describe());
    }

    /**
     * Writes the feed's position. While nothing has changed the layout
     * since it was read, the file is written as it was read with only its
     * feed line replaced: a window on a server's own channel, which the
     * layout cannot place before that server's channels are in force, is
     * then still in the file for {@link #reloadForNewChannels} to
     * restore. Once the layout has been changed it is written whole.
     */
    public static synchronized void saveFeedPosition() {
        if (loadedLines == null || layoutTouched) {
            ChatWindowLayout.persist();
            return;
        }
        List<String> lines = new ArrayList<String>(loadedLines.size() + 1);
        for (String raw : loadedLines) {
            String line = raw == null ? "" : raw.trim();
            // Every feed line goes, since a read keeps the last one.
            if (!"feed".equals(line.split("\\s+")[0])) {
                lines.add(raw);
            }
        }
        lines.add("feed x=" + formatPercent(ChatWindowLayout.feedOffsetX())
                + " y=" + formatPercent(ChatWindowLayout.feedOffsetY()));
        loadedLines = lines;
        write(storeFile, lines);
    }

    private static void write(File file, List<String> lines) {
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
            for (String line : lines) {
                writer.write(line == null ? "" : line);
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
