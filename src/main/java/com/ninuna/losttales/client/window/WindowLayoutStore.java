package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.character.LostTalesClientAccount;
import com.ninuna.losttales.config.LostTalesConfigFiles;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * File-backed persistence for the {@link WindowLayout}: a presentation
 * preference of the signed-in account, kept under {@code config/} in a
 * file named after the account and never synchronized or cleared
 * between worlds. Two people sharing a machine keep their own windows,
 * and one person with two accounts keeps an arrangement for each. The
 * file is a few plain lines — one per window, one per sub-window the
 * player has placed, one per page whose window was closed — and the
 * lines of each system with windows of its own ({@link Part}: the chat
 * keeps its closed channels and preferences here), so a hand edit or a
 * stale entry cannot corrupt anything: whatever does not parse is
 * skipped and the layout repairs itself on load.
 *
 * <pre>
 * window w1 locked=false x=0.00 y=0.00 active=client_console tabs=client_console,operator
 * window w2 locked=true x=62.50 y=100.00 height=180.40 width=326 fill=full active=global tabs=global,ooc,page:journal link=w1:above
 * sub emoji from=br dx=0.00 dy=0.00 w=120 h=160
 * sub tab from=tl dx=12.00 dy=40.00
 * place page:party x=40.00 y=60.00 height=292.00 width=366 fill=full
 * </pre>
 */
public final class WindowLayoutStore {
    /**
     * A system's own lines in the layout file, and its own attributes on
     * a window's line.
     */
    public interface Part {
        /** Back to nothing read: before a file is read, and when there is none. */
        void clear();

        /** A line that is not the window system's own; answers whether it was this part's. */
        boolean read(String line);

        /** An attribute on a window's line the window system does not know; answers whether it was this part's. */
        boolean readWindow(String windowId, String key, String value);

        /** Once every line is read and the windows stand. */
        void loaded();

        /** This part's attributes on a window's line, each written as {@code " key=value"}. */
        void describeWindow(Window window, StringBuilder line);

        /** This part's own lines. */
        void describe(List<String> lines);
    }

    /** The folder under the client's, one file per account. */
    static final String FOLDER = LostTalesConfigFiles.WINDOW_LAYOUTS;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    /** The most lines a layout file is read to; a layout is a few dozen. */
    static final int MAX_LINES = 4096;
    private static final List<Part> PARTS = new CopyOnWriteArrayList<Part>();

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

    private WindowLayoutStore() {}

    /** Adds a system's lines to the file; before the file is read. */
    public static void addPart(Part part) {
        if (part != null && !PARTS.contains(part)) {
            PARTS.add(part);
        }
    }

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
            for (Part part : PARTS) {
                part.clear();
            }
            WindowLayout.reset();
            SubWindowPlaces.load(null);
        }
        WindowLayout.setChangeListener(new Runnable() {
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
     * Builds the layout again from the file, now that a server has said
     * which tabs it has — its own channels — so a window arranged around
     * one of them is restored rather than quietly dropped and then
     * written away.
     *
     * <p>Only while the player has not moved anything: once they have,
     * what is on screen is newer than the file and is what gets saved.
     * Reading a file that was never written leaves the layout alone.</p>
     */
    public static synchronized void reload() {
        if (loadedLines != null && !layoutTouched) {
            load(loadedLines);
        }
    }

    /** Applies parsed lines to the layout; visible for tests. */
    public static void load(List<String> lines) {
        // The file this layout was built from, and a layout nobody has
        // moved yet: both are what lets it be built again later.
        loadedLines = lines;
        layoutTouched = false;
        for (Part part : PARTS) {
            part.clear();
        }
        List<WindowLayout.WindowSpec> specs =
                new ArrayList<WindowLayout.WindowSpec>();
        Map<SubWindowKind, SubWindowPlaces.Placement> placed =
                new LinkedHashMap<SubWindowKind, SubWindowPlaces.Placement>();
        Map<String, WindowLayout.Place> places =
                new LinkedHashMap<String, WindowLayout.Place>();
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.length() == 0 || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length >= 2 && "window".equals(parts[0])) {
                WindowLayout.WindowSpec spec = parseWindow(parts);
                if (spec != null) {
                    specs.add(spec);
                }
            } else if (parts.length >= 2 && "place".equals(parts[0])) {
                WindowLayout.Place place = parsePlace(parts);
                if (place != null) {
                    places.put(parts[1], place);
                }
            } else if (parts.length >= 2 && "sub".equals(parts[0])) {
                SubWindowKind kind = SubWindowKind.fromId(parts[1]);
                SubWindowPlaces.Placement placement = parseSubWindow(parts);
                if (kind != null && placement != null) {
                    placed.put(kind, placement);
                }
            } else {
                for (Part part : PARTS) {
                    if (part.read(line)) {
                        break;
                    }
                }
            }
        }
        WindowLayout.load(specs);
        SubWindowPlaces.load(placed);
        WindowLayout.loadPlaces(places);
        for (Part part : PARTS) {
            part.loaded();
        }
    }

    /**
     * Where a page's window last stood: its place and its size, all four
     * or null, which leaves the page to open cascaded at a page's size.
     */
    private static WindowLayout.Place parsePlace(String[] parts) {
        double x = Double.NaN;
        double y = Double.NaN;
        double height = Double.NaN;
        int width = -1;
        Window.ScreenFill fill = Window.ScreenFill.NONE;
        for (int index = 2; index < parts.length; index++) {
            String part = parts[index];
            try {
                if (part.startsWith("fill=")) {
                    fill = Window.ScreenFill.fromId(part.substring(5));
                } else if (part.startsWith("x=")) {
                    x = parsePercent(part.substring(2));
                } else if (part.startsWith("y=")) {
                    y = parsePercent(part.substring(2));
                } else if (part.startsWith("height=")) {
                    height = Double.parseDouble(part.substring(7));
                } else if (part.startsWith("width=")) {
                    width = Integer.parseInt(part.substring(6));
                }
            } catch (NumberFormatException unreadable) {
                return null;
            }
        }
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(height)
                || height < 0.0D || width < 0) {
            return null;
        }
        return new WindowLayout.Place(x, y, height, width, fill);
    }

    /**
     * A sub-window's remembered place: the corner of its window it is
     * measured from, how far in from it, and both sizes for a kind the
     * player resized or neither for one they only moved; null where the
     * corner or a distance is missing, a size stands alone or anything is
     * unreadable, which leaves the kind to open where its popup did.
     */
    private static SubWindowPlaces.Placement parseSubWindow(
            String[] parts) {
        String corner = null;
        double x = Double.NaN;
        double y = Double.NaN;
        int width = 0;
        int height = 0;
        for (int index = 2; index < parts.length; index++) {
            String part = parts[index];
            try {
                if (part.startsWith("from=")) {
                    corner = part.substring(5);
                } else if (part.startsWith("dx=")) {
                    x = Double.parseDouble(part.substring(3));
                } else if (part.startsWith("dy=")) {
                    y = Double.parseDouble(part.substring(3));
                } else if (part.startsWith("w=")) {
                    width = Integer.parseInt(part.substring(2));
                } else if (part.startsWith("h=")) {
                    height = Integer.parseInt(part.substring(2));
                }
            } catch (NumberFormatException unreadable) {
                return null;
            }
        }
        if (corner == null || corner.length() != 2
                || "tb".indexOf(corner.charAt(0)) < 0
                || "lr".indexOf(corner.charAt(1)) < 0
                || Double.isNaN(x) || Double.isNaN(y) || width < 0
                || height < 0 || (width > 0) != (height > 0)) {
            return null;
        }
        return new SubWindowPlaces.Placement(corner.charAt(1) == 'r',
                corner.charAt(0) == 'b', x, y, width, height);
    }

    private static WindowLayout.WindowSpec parseWindow(String[] parts) {
        String id = parts[1].toLowerCase(Locale.ROOT);
        if (!WindowLayout.isWindowId(id)) {
            return null;
        }
        List<WindowTab> tabs = new ArrayList<WindowTab>();
        WindowTab active = null;
        boolean locked = false;
        double offsetX = 0.0D;
        double offsetY = 0.0D;
        String linkTarget = null;
        Window.LinkSide linkSide = Window.LinkSide.BELOW;
        // No size of its own: the window follows the game's settings.
        double height = 0.0D;
        int width = 0;
        Window.ScreenFill fill = Window.ScreenFill.NONE;
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
                    WindowTab parsed = WindowTab.fromId(tab);
                    if (parsed != null) {
                        tabs.add(parsed);
                    }
                }
            } else if ("link".equals(key)) {
                int colon = value.indexOf(':');
                String target = colon < 0 ? value : value.substring(0, colon);
                if (WindowLayout.isWindowId(target)) {
                    linkTarget = target;
                    linkSide = colon >= 0
                            ? Window.LinkSide.fromId(
                                    value.substring(colon + 1))
                            : Window.LinkSide.BELOW;
                }
            } else if ("active".equals(key)) {
                active = WindowTab.fromId(value);
            } else if ("locked".equals(key)) {
                locked = "true".equalsIgnoreCase(value);
            } else if ("x".equals(key)) {
                offsetX = parsePercent(value);
            } else if ("y".equals(key)) {
                offsetY = parsePercent(value);
            } else if ("height".equals(key)) {
                height = WindowLayout.clampWindowHeight(parseDouble(value));
            } else if ("width".equals(key)) {
                width = parseWidth(value);
            } else if ("fill".equals(key)) {
                fill = Window.ScreenFill.fromId(value);
            } else {
                for (Part owner : PARTS) {
                    if (owner.readWindow(id, key, value)) {
                        break;
                    }
                }
            }
        }
        return new WindowLayout.WindowSpec(id, tabs, active, locked,
                offsetX, offsetY, linkTarget, linkSide, height, width,
                fill);
    }

    /** A number written in the file; zero for anything unreadable. */
    public static double parseDouble(String value) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isNaN(parsed) || Double.isInfinite(parsed)
                    ? 0.0D : parsed;
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    /** A stored width; anything unreadable follows the game's setting. */
    private static int parseWidth(String value) {
        try {
            return WindowLayout.clampWindowWidth(
                    Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /** A stored percent; a window's may hang past the margins. */
    private static double parsePercent(String value) {
        return WindowLayout.clampWindowPercent(parseDouble(value));
    }

    /** The lines that describe the current layout; visible for tests. */
    public static List<String> describe() {
        List<String> lines = new ArrayList<String>();
        lines.add("# Lost Tales window layout");
        for (WindowLayout.WindowSpec spec : WindowLayout.describe()) {
            StringBuilder line = new StringBuilder("window ").append(spec.id);
            line.append(" locked=").append(spec.locked);
            line.append(" x=").append(format(spec.offsetX));
            line.append(" y=").append(format(spec.offsetY));
            if (spec.height > 0.0D) {
                line.append(" height=").append(format(spec.height));
            }
            if (spec.width > 0) {
                line.append(" width=").append(spec.width);
            }
            if (spec.fill != Window.ScreenFill.NONE) {
                line.append(" fill=").append(spec.fill.id());
            }
            Window window = WindowLayout.window(spec.id);
            for (Part part : PARTS) {
                part.describeWindow(window, line);
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
        for (Map.Entry<SubWindowKind, SubWindowPlaces.Placement>
                sub : SubWindowPlaces.all().entrySet()) {
            SubWindowPlaces.Placement placement = sub.getValue();
            lines.add("sub " + sub.getKey().id
                    + " from=" + placement.corner()
                    + " dx=" + format(placement.dx)
                    + " dy=" + format(placement.dy)
                    + (placement.isSized() ? " w=" + placement.width
                            + " h=" + placement.height : ""));
        }
        for (Map.Entry<String, WindowLayout.Place> entry
                : WindowLayout.places().entrySet()) {
            WindowLayout.Place place = entry.getValue();
            lines.add("place " + entry.getKey()
                    + " x=" + format(place.x)
                    + " y=" + format(place.y)
                    + " height=" + format(place.height)
                    + " width=" + place.width
                    + (place.fill != Window.ScreenFill.NONE
                            ? " fill=" + place.fill.id() : ""));
        }
        for (Part part : PARTS) {
            part.describe(lines);
        }
        return lines;
    }

    /** Locale-independent, two decimals: plenty for a percent or a place in pixels. */
    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static synchronized void save() {
        write(storeFile, describe());
    }

    /**
     * Writes one line of a part's, starting with {@code keyword}. While
     * nothing has changed the layout since it was read, the file is
     * written as it was read with only that line replaced: a window on a
     * server's own channel, which the layout cannot place before that
     * server's channels are in force, is then still in the file for
     * {@link #reload} to restore. Once the layout has been changed it is
     * written whole.
     */
    public static synchronized void saveLine(String keyword, String line) {
        if (loadedLines == null || layoutTouched) {
            WindowLayout.persist();
            return;
        }
        List<String> lines = new ArrayList<String>(loadedLines.size() + 1);
        for (String raw : loadedLines) {
            String read = raw == null ? "" : raw.trim();
            // Every such line goes, since a read keeps the last one.
            if (!keyword.equals(read.split("\\s+")[0])) {
                lines.add(raw);
            }
        }
        lines.add(line);
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
            // Losing a layout write must never break the windows.
        } finally {
            closeQuietly(writer);
        }
    }

    /**
     * The file's lines, at most {@link #MAX_LINES} of them: a layout is a
     * few dozen, so a longer file is broken and only its start is read.
     */
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
            while (lines.size() < MAX_LINES
                    && (line = reader.readLine()) != null) {
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
