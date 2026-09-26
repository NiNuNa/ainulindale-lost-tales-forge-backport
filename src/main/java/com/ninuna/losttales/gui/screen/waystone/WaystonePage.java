package com.ninuna.losttales.gui.screen.waystone;

import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.block.custom.LostTalesBlockWaystone;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.client.diagnostics.LostTalesClientDiagnostics;
import com.ninuna.losttales.client.mapmarker.LostTalesClientWaystoneTravelContext;
import com.ninuna.losttales.client.mapmarker.LostTalesLotrMapGui;
import com.ninuna.losttales.client.mapmarker.LostTalesLotrMapMarkerIconOverlay;
import com.ninuna.losttales.client.mapmarker.LostTalesLotrWaypointText;
import com.ninuna.losttales.client.mapmarker.LostTalesMapPage;
import com.ninuna.losttales.client.window.BarItem;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageRows;
import com.ninuna.losttales.client.window.PageTab;
import com.ninuna.losttales.client.window.Settings;
import com.ninuna.losttales.client.window.SubWindowAnchor;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowFrame;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowMenus;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WorldPageReach;
import com.ninuna.losttales.client.window.WorldPageWatch;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarker;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerEditableSettings;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerIdResolver;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneStateReason;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesWaystoneSettingsRequestPacket;
import com.ninuna.losttales.network.packet.LostTalesWaystoneStatePacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lotr.common.LOTRDimension;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fellowship.LOTRFellowshipClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/**
 * A waystone, a page a window holds (Q10 a): its settings in one
 * scrolling column of Settings' own rows, in four sections — Marker,
 * Location, Rules and Sharing ({@link WaystoneRows}) — and on the bar
 * Destinations and Save. Using a waystone opens it: the server checks the
 * waystone and sends its state as an opening. One waystone is shown at a
 * time; using another turns the page to it, and what was changed on the
 * one before and not saved is let go, which the status line says.
 *
 * <p>Every change stays on the page ({@link WaystoneDraft}) until Save
 * sends it with the revision it was read at, lit only once something
 * changed. The server answers every request with the waystone's state
 * and why; a stale revision brings back what stands now and the page
 * says so. Share and Unshare are sent at once, as before.</p>
 *
 * <p>Destinations opens the map in travel mode beside the waystone's tab
 * (Q11 a). The tab closes by itself, fading as tabs close, once the
 * player is more than eight blocks away, in another world, or the
 * waystone is gone (Q8 a), and a notice over the window's bar says
 * which; the server checks every request whatever the page thinks.</p>
 */
public final class WaystonePage extends PageContent
        implements WorldPageWatch.Watched {
    /** The code name the page is registered under. */
    public static final String PAGE_ID = "waystone";

    /** The item the tab wears until the waystone has artwork of its own. */
    public static final ItemStack ICON = new ItemStack(Items.compass);

    /** The bar's items: their ids, which the page is told when one is pressed. */
    private static final String DESTINATIONS = "destinations";
    private static final String SAVE = "save";
    /** Clear room above the rows. */
    private static final int TOP = 2;
    /** The room the status line takes under the rows. */
    private static final int STATUS_ROOM = 14;
    /** The widest the column of rows grows, so a name and its value stay near. */
    private static final int MAX_COLUMN_WIDTH = 300;
    /** Where the status line's words stand in from the column's edge: where the rows' names do. */
    private static final int STATUS_INSET = 6;
    /** Ticks a request waits for its answer before the page stops waiting: five seconds. */
    private static final int ANSWER_TICKS = 100;
    /** The most names the share field offers at once. */
    private static final int MAX_OFFERS = 8;

    private final Minecraft mc = Minecraft.getMinecraft();
    /**
     * The rows of the waystone shown, made afresh for each waystone, so
     * a field opened for one closes once the page lets it go.
     */
    private WaystoneRows rows;
    /** Counts the waystones shown: rows made for an earlier one no longer stand. */
    private int shownCount;
    private final PageRows list;
    /** The waystone's state as the server last sent it; null while no waystone is open. */
    private LostTalesWaystoneStatePacket state;
    private WaystoneDraft draft;
    /** The lore LOTR gives the waystone's place, which an empty description shows. */
    private String lore = "";
    private boolean shareWithFellowship;
    private String shareTarget = "";
    /** Whether a request is on its way, and how long it has waited. */
    private boolean waiting;
    private int waitedTicks;
    /** The settings the Save on its way sent; null for none. */
    private LostTalesMapMarkerEditableSettings sent;
    private String status = "";
    private boolean statusError;
    /** The words in the window's well; empty while its search is closed. */
    private String query = "";

    public WaystonePage() {
        this.rows = newRows();
        this.list = new PageRows(new PageRows.Taker() {
            @Override
            public void take(MenuWindow.Entry entry, String part,
                             boolean back, LostTalesUiHitBox row) {
                WaystonePage.this.take(entry, part, back, row);
            }
        });
    }

    /**
     * A waystone's state from the server. An opening turns the page to
     * that waystone and brings it forward, on the window screen already
     * open or on a new one; any other state answers a request, and is
     * read only while the page shows that very waystone.
     */
    public static void accept(LostTalesWaystoneStatePacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }
        WindowPages.Page page = WindowPages.byId(PAGE_ID);
        PageContent content = page == null ? null : page.content();
        if (!(content instanceof WaystonePage)) {
            return;
        }
        WaystonePage waystone = (WaystonePage)content;
        if (packet.isOpening()) {
            waystone.open(packet);
            WindowScreen.openPage(PAGE_ID);
        } else {
            waystone.answer(packet);
        }
    }

    private static String word(String key) {
        return StatCollector.translateToLocal("gui.losttales.waystone." + key);
    }

    private static String word(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(
                "gui.losttales.waystone." + key, args);
    }

    /* ---- The waystone ---- */

    /**
     * The page turned to the waystone the player used. The same waystone
     * again, its tab still open, keeps what was changed on top of the
     * newer state; another waystone starts afresh, and the status line
     * says what was changed on the one before was not saved.
     */
    private void open(LostTalesWaystoneStatePacket packet) {
        PageTab tab = WindowPages.tab(PAGE_ID);
        boolean tabOpen = tab != null && WindowLayout.isOpen(tab);
        if (tabOpen && isShown(packet)) {
            this.draft.rebase(this.state.getSettings(), packet.getSettings());
            this.state = packet;
            this.lore = loreOf(packet);
            return;
        }
        String dropped = tabOpen && this.draft != null
                && this.draft.isChanged() ? this.state.getName() : null;
        forget();
        this.state = packet;
        this.draft = new WaystoneDraft(packet.getSettings());
        this.lore = loreOf(packet);
        this.list.toTop();
        if (dropped != null) {
            say(word("dropped", dropped), false);
        }
    }

    /**
     * The server's answer to a request: saved, or refused. A stale
     * revision brings back what stands now in place of every change;
     * otherwise each change the player made since the request was sent
     * stays on top of the newer state.
     */
    private void answer(LostTalesWaystoneStatePacket packet) {
        if (!isShown(packet)) {
            return;
        }
        LostTalesWaystoneStateReason reason = packet.getReason();
        if (reason == LostTalesWaystoneStateReason.STALE) {
            this.draft.reset(packet.getSettings());
        } else {
            this.draft.rebase(reason == LostTalesWaystoneStateReason.SAVED
                    && this.sent != null ? this.sent
                    : this.state.getSettings(), packet.getSettings());
        }
        this.state = packet;
        this.lore = loreOf(packet);
        this.waiting = false;
        this.sent = null;
        say(StatCollector.translateToLocal(reason.getMessageKey()),
                reason.isRefusal());
    }

    /** Whether the page shows the waystone a state is about. */
    private boolean isShown(LostTalesWaystoneStatePacket packet) {
        return this.state != null && this.draft != null
                && packet.getDimensionId() == this.state.getDimensionId()
                && packet.getX() == this.state.getX()
                && packet.getY() == this.state.getY()
                && packet.getZ() == this.state.getZ()
                && packet.getMarkerId().equals(this.state.getMarkerId());
    }

    /**
     * The page lets its waystone go: what was changed and not saved goes
     * with it, and a field opened on it closes.
     */
    private void forget() {
        this.shownCount++;
        this.rows = newRows();
        this.state = null;
        this.draft = null;
        this.lore = "";
        this.waiting = false;
        this.sent = null;
        this.shareWithFellowship = false;
        this.shareTarget = "";
        this.status = "";
        this.statusError = false;
    }

    private WaystoneRows newRows() {
        return new WaystoneRows(new Host(this.shownCount),
                LostTalesLotrMapMarkerIconOverlay.EDITOR_ICON_SIZE);
    }

    private String loreOf(LostTalesWaystoneStatePacket packet) {
        return LostTalesLotrWaypointText.resolveDescription("",
                LostTalesMapMarkerIdResolver.resolveLotrWaypointId(
                        packet.getMarkerId()), this.mc.thePlayer);
    }

    private void say(String words, boolean error) {
        this.status = words == null ? "" : words;
        this.statusError = error;
    }

    /* ---- Walking away (Q8 a) ---- */

    /** Once a game tick while the page is in front: the waystone watched, and a request left unanswered let go. */
    @Override
    public void tick() {
        watch();
        if (this.waiting && ++this.waitedTicks >= ANSWER_TICKS) {
            this.waiting = false;
            this.sent = null;
            say(word("no_answer"), true);
        }
    }

    /** Closes the tab where the player no longer stands at the waystone. */
    @Override
    public void watch() {
        PageTab tab = WindowPages.tab(PAGE_ID);
        if (tab == null || !WindowLayout.isOpen(tab)) {
            return;
        }
        WorldPageReach.Leave leave = leave();
        if (leave != null) {
            close(tab, leave);
        }
    }

    /** Why the player no longer stands at the waystone; null while they do. */
    private WorldPageReach.Leave leave() {
        EntityPlayer player = this.mc.thePlayer;
        World world = this.mc.theWorld;
        if (this.state == null || player == null || world == null) {
            return WorldPageReach.Leave.GONE;
        }
        int x = this.state.getX();
        int y = this.state.getY();
        int z = this.state.getZ();
        boolean standing = world.provider != null
                && world.provider.dimensionId == this.state.getDimensionId()
                && world.getBlock(x, y, z) == ELostTalesBlock.WAYSTONE.getBlock()
                && world.getBlockMetadata(x, y, z)
                        != LostTalesBlockWaystone.UPPER_METADATA;
        return WorldPageReach.check(player.dimension, player.posX,
                player.boundingBox.minY, player.posZ,
                this.state.getDimensionId(), x, y, z, standing,
                LostTalesTileEntityWaystone.REACH_SQ);
    }

    /**
     * The tab closes by itself, as a tab closed by hand does, and the
     * notice over its window's bar says why.
     */
    private void close(PageTab tab, WorldPageReach.Leave leave) {
        String name = this.state == null ? StatCollector.translateToLocal(
                "gui.losttales.page.waystone") : this.state.getName();
        WorldPageWatch.close(tab, StatCollector.translateToLocalFormatted(
                leave.messageKey("waystone"), name));
        forget();
    }

    /** The tab closed, by hand or by itself: the waystone goes with it. */
    @Override
    public void hidden() {
        PageTab tab = WindowPages.tab(PAGE_ID);
        if (tab == null || !WindowLayout.isOpen(tab)) {
            forget();
        }
    }

    /* ---- The rows ---- */

    /**
     * A row taken: a number's chevrons step it and its value opens the
     * field it is typed into, as does a line; a switch flips, a few-word
     * option steps on, or back with the right button; Share and Unshare
     * are sent at once. A row greyed is never taken.
     */
    private void take(MenuWindow.Entry entry, String part, boolean back,
                      LostTalesUiHitBox row) {
        if (this.state == null || this.draft == null) {
            return;
        }
        Settings.Setting typed = this.rows.typedFor(entry.id);
        if (typed instanceof Settings.Numeric) {
            Settings.Numeric number = (Settings.Numeric)typed;
            if (MenuWindow.PART_VALUE.equals(part) && !back) {
                openValue(number, row);
            } else {
                boolean up = MenuWindow.PART_MORE.equals(part)
                        || !MenuWindow.PART_LESS.equals(part) && !back;
                number.move(up, GuiScreen.isShiftKeyDown());
            }
            return;
        }
        if (typed != null) {
            if (!back) {
                openValue(typed, row);
            }
            return;
        }
        String id = entry.id;
        if (WaystoneRows.ICON.equals(id)) {
            this.draft.stepIcon(back);
        } else if (WaystoneRows.COLOR.equals(id)) {
            this.draft.stepColor(back);
        } else if (WaystoneRows.RELEVANCE.equals(id)) {
            this.draft.stepRelevance(back);
        } else if (WaystoneRows.DISCOVERABLE.equals(id)) {
            this.draft.setDiscoverable(!this.draft.isDiscoverable());
        } else if (WaystoneRows.HIDDEN.equals(id)) {
            this.draft.setHidden(!this.draft.isHidden());
        } else if (WaystoneRows.REGION.equals(id)) {
            this.draft.setRequiresRegion(!this.draft.requiresRegion());
        } else if (WaystoneRows.FAST_TRAVEL.equals(id)) {
            this.draft.setFastTravel(!this.draft.hasFastTravel());
        } else if (WaystoneRows.VISIBILITY.equals(id)) {
            this.draft.stepVisibility(back, this.state.canMakePublic());
        } else if (WaystoneRows.SHARE_KIND.equals(id)) {
            this.shareWithFellowship = !this.shareWithFellowship;
            this.shareTarget = "";
        } else if (WaystoneRows.SHARE.equals(id) && !back) {
            share(false);
        } else if (WaystoneRows.UNSHARE.equals(id) && !back) {
            share(true);
        }
    }

    /** Settings' own field for a line or a number, hung from its row in the page's window. */
    private void openValue(Settings.Setting setting, LostTalesUiHitBox row) {
        WindowScreen screen = WindowScreen.current();
        PageTab tab = WindowPages.tab(PAGE_ID);
        Window window = tab == null ? null : WindowLayout.windowOf(tab);
        if (screen == null || window == null || row == null) {
            return;
        }
        SubWindowAnchor anchor = SubWindowAnchor.inward(
                (int)Math.floor(row.left), (int)Math.floor(row.top),
                (int)Math.ceil(row.right()), (int)Math.ceil(row.bottom()),
                WindowFrame.find(window.getId()), screen.width,
                screen.height);
        screen.settings().openValue(setting,
                WindowMenus.hangingFrom(anchor));
        // The field takes the keys the page held for the press.
        screen.leavePage();
        screen.syncTypingFocus();
    }

    /* ---- Requests ---- */

    /** Sends every change, with the revision the page read, and waits for the answer. */
    private void save() {
        if (this.state == null || this.draft == null || this.waiting
                || !this.state.canEdit() || !this.draft.isChanged()) {
            return;
        }
        LostTalesMapMarkerEditableSettings settings;
        LostTalesWaystoneSettingsRequestPacket packet;
        try {
            settings = this.draft.toSettings();
            packet = LostTalesWaystoneSettingsRequestPacket.save(
                    this.state.getX(), this.state.getY(), this.state.getZ(),
                    this.state.getMarkerId(), this.state.getRevision(),
                    settings);
        } catch (IllegalArgumentException invalid) {
            say(word("invalid_settings"), true);
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(packet);
        this.sent = settings;
        beginWaiting();
    }

    /** Shares the waystone with the player or fellowship named, or stops sharing it. */
    private void share(boolean remove) {
        String name = this.shareTarget.trim();
        if (this.state == null || this.waiting || !this.state.canEdit()
                || name.length() == 0) {
            return;
        }
        LostTalesWaystoneSettingsRequestPacket packet;
        try {
            packet = this.shareWithFellowship
                    ? LostTalesWaystoneSettingsRequestPacket.shareFellowship(
                            remove, this.state.getX(), this.state.getY(),
                            this.state.getZ(), this.state.getMarkerId(),
                            this.state.getRevision(), name)
                    : LostTalesWaystoneSettingsRequestPacket.share(
                            remove, this.state.getX(), this.state.getY(),
                            this.state.getZ(), this.state.getMarkerId(),
                            this.state.getRevision(), name);
        } catch (IllegalArgumentException invalid) {
            say(word("invalid_share_target"), true);
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(packet);
        this.sent = null;
        beginWaiting();
    }

    private void beginWaiting() {
        this.waiting = true;
        this.waitedTicks = 0;
        say(word("saving"), false);
    }

    /* ---- Destinations (Q11 a) ---- */

    /**
     * The map in travel mode, beside the waystone's tab in its window:
     * the next place picked on the map is travelled to from this
     * waystone, which the server checks again. Where the map cannot stand
     * in a window it opens on a screen of its own, as before.
     */
    private void travel() {
        if (this.state == null || !this.state.hasFastTravel()
                || !inMiddleEarth()) {
            return;
        }
        LostTalesClientWaystoneTravelContext.begin(
                this.mc.thePlayer.dimension, this.state.getX(),
                this.state.getY(), this.state.getZ(),
                this.state.getMarkerId());
        PageTab map = WindowPages.tab(LostTalesMapPage.PAGE_ID);
        PageTab own = WindowPages.tab(PAGE_ID);
        Window window = own == null ? null : WindowLayout.windowOf(own);
        if (map == null || !LostTalesMapPage.standsInWindow()
                || WindowScreen.current() == null) {
            LostTalesLotrMapGui.open();
            return;
        }
        if (!WindowLayout.isOpen(map) && window != null
                && WindowLayout.addTab(window.getId(), map)) {
            WindowLayout.moveTab(map, window.getId(),
                    window.getTabs().indexOf(own) + 1);
        }
        WindowScreen.openPage(LostTalesMapPage.PAGE_ID);
    }

    private boolean inMiddleEarth() {
        return this.mc.thePlayer != null && this.mc.thePlayer.dimension
                == LOTRDimension.MIDDLE_EARTH.dimensionID;
    }

    /* ---- Drawing ---- */

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        if (this.draft == null) {
            return;
        }
        List<MenuWindow.Entry> built = this.rows.build(this.query);
        if (built.isEmpty()) {
            built.add(MenuWindow.Entry.passive(StatCollector.translateToLocal(
                    "gui.losttales.window.settings.none")));
        }
        this.list.setRows(built);
        LostTalesUiHitBox column = column(box);
        this.list.draw(minecraft, column, clipX + (column.left - box.left),
                clipY + (column.top - box.top), pointerX, pointerY, alpha);
        drawStatus(minecraft, box, column, alpha);
    }

    /**
     * The line under the rows: what the last request came to, in red for
     * a refusal; else, on a waystone the player may not edit, why.
     */
    private void drawStatus(Minecraft minecraft, LostTalesUiHitBox box,
                            LostTalesUiHitBox column, int alpha) {
        String said = this.status;
        boolean error = this.statusError;
        if (said.length() == 0 && this.state != null
                && !this.state.canEdit()) {
            said = word("read_only");
            error = false;
        }
        if (said.length() == 0) {
            return;
        }
        int left = (int)column.left + STATUS_INSET;
        int width = (int)column.width - STATUS_INSET * 2;
        int top = (int)(box.top + box.height) - STATUS_ROOM
                + LostTalesUiInk.centredStart(STATUS_ROOM,
                        LostTalesUiInk.CAP_HEIGHT);
        LostTalesUiInk.drawText(minecraft.fontRenderer,
                LostTalesSkyrimUiStyle.trimToWidth(minecraft.fontRenderer,
                        said, Math.max(0, width)), left, top,
                error ? LostTalesColors.rgb(LostTalesColors.RED)
                        : WindowStyle.asideRgb(), alpha);
    }

    /** The column the rows stand in: the page's width up to a limit, centred, above the status line. */
    private static LostTalesUiHitBox column(LostTalesUiHitBox box) {
        int boxWidth = (int)Math.floor(box.width);
        int width = Math.max(0, Math.min(boxWidth, MAX_COLUMN_WIDTH));
        return new LostTalesUiHitBox(Math.floor(box.left)
                + LostTalesUiInk.centredStart(boxWidth, width),
                Math.floor(box.top) + TOP, width,
                Math.max(0.0D, Math.floor(box.height) - TOP - STATUS_ROOM));
    }

    /* ---- The pointer ---- */

    @Override
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        return this.draft != null && this.list.acts(column(box), x, y);
    }

    @Override
    public String tipAt(LostTalesUiHitBox box, double x, double y) {
        return this.draft == null ? "" : this.list.tipAt(column(box), x, y);
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box,
                                double x, double y, int button) {
        return this.draft != null
                && this.list.press(column(box), x, y, button);
    }

    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        if (this.draft == null || lines == 0
                || !column(box).contains(x, y)) {
            return false;
        }
        this.list.scroll(lines);
        return true;
    }

    /* ---- The window's strip ---- */

    @Override
    public String searchPrompt() {
        return word("search");
    }

    @Override
    public void search(String words) {
        this.query = words == null ? "" : words.trim();
    }

    @Override
    public int found() {
        return this.query.length() == 0 || this.draft == null ? -1
                : PageRows.found(this.rows.build(this.query));
    }

    /* ---- The window's bar ---- */

    /**
     * Destinations and Save, each there whatever the waystone allows,
     * greyed with the reason where it cannot be taken. Save is lit once
     * something changed.
     */
    @Override
    public List<BarItem> barItems() {
        if (this.state == null || this.draft == null) {
            return Collections.emptyList();
        }
        List<BarItem> items = new ArrayList<BarItem>(2);
        items.add(orWhy(BarItem.button(DESTINATIONS, word("destinations"),
                new ItemStack(Items.map)).tip(word("destinations.tip")),
                !this.state.hasFastTravel() ? word("why.no_fast_travel")
                        : !inMiddleEarth() ? word("why.not_middle_earth")
                        : ""));
        boolean changed = this.draft.isChanged();
        items.add(orWhy(BarItem.button(SAVE, word("save"),
                new ItemStack(Items.paper)).lit(changed)
                .tip(word("save.tip")),
                !this.state.canEdit() ? word("read_only")
                        : this.waiting ? word("why.waiting")
                        : changed ? "" : word("why.nothing_changed")));
        return items;
    }

    private static BarItem orWhy(BarItem item, String why) {
        return why == null || why.length() == 0 ? item : item.unavailable(why);
    }

    @Override
    public void barPressed(String id, int offer) {
        if (DESTINATIONS.equals(id)) {
            travel();
        } else if (SAVE.equals(id)) {
            save();
        }
    }

    /* ---- What the rows read ---- */

    /** The page as the rows of one waystone see it. */
    private final class Host implements WaystoneRows.Host {
        /** Which waystone shown the rows were made for. */
        private final int made;

        Host(int made) {
            this.made = made;
        }

        @Override
        public WaystoneDraft draft() {
            return WaystonePage.this.draft;
        }

        @Override
        public boolean canEdit() {
            return WaystonePage.this.state != null
                    && WaystonePage.this.state.canEdit();
        }

        @Override
        public boolean isWaiting() {
            return WaystonePage.this.waiting;
        }

        @Override
        public String markerId() {
            return WaystonePage.this.state == null ? ""
                    : WaystonePage.this.state.getMarkerId();
        }

        @Override
        public int sharedPlayers() {
            return WaystonePage.this.state == null ? 0
                    : WaystonePage.this.state.getSharedPlayerCount();
        }

        @Override
        public int sharedFellowships() {
            return WaystonePage.this.state == null ? 0
                    : WaystonePage.this.state.getSharedFellowshipCount();
        }

        @Override
        public boolean sharesWithFellowship() {
            return WaystonePage.this.shareWithFellowship;
        }

        @Override
        public String shareTarget() {
            return WaystonePage.this.shareTarget;
        }

        @Override
        public void setShareTarget(String name) {
            WaystonePage.this.shareTarget = name == null ? "" : name.trim();
        }

        @Override
        public String nativeLore() {
            return WaystonePage.this.lore;
        }

        @Override
        public List<String> offers(String typed) {
            return names(typed);
        }

        @Override
        public boolean stands() {
            PageTab tab = WindowPages.tab(PAGE_ID);
            return this.made == WaystonePage.this.shownCount
                    && WaystonePage.this.draft != null && tab != null
                    && WindowLayout.isOpen(tab);
        }

        @Override
        public MenuWindow.Picture iconPicture() {
            final String icon = WaystonePage.this.draft.icon();
            final String color = WaystonePage.this.draft.color();
            return new MenuWindow.Picture() {
                @Override
                public void draw(Minecraft minecraft, float x,
                                 float labelTop, int alpha) {
                    float half = LostTalesLotrMapMarkerIconOverlay
                            .EDITOR_ICON_SIZE / 2.0F;
                    LostTalesLotrMapMarkerIconOverlay.renderEditorIconPreview(
                            minecraft, icon, color, x + half,
                            labelTop + LostTalesUiInk.CAP_HEIGHT / 2.0F,
                            alpha / 255.0F);
                }
            };
        }

        /** The marker's own colour, as the map and the compass draw it. */
        @Override
        public int colorRgb(String color) {
            float[] rgb = LostTalesCompassMarker.parseColor(color);
            return channel(rgb[0]) << 16 | channel(rgb[1]) << 8
                    | channel(rgb[2]);
        }
    }

    private static int channel(float share) {
        return Math.max(0, Math.min(255, Math.round(share * 255.0F)));
    }

    /**
     * The names the share field offers for what is typed: the players
     * online but the player, or the player's fellowships, whichever the
     * waystone is shared with, the typed name itself left out.
     */
    private List<String> names(String typed) {
        String words = typed == null ? "" : typed.trim();
        String wanted = words.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<String>();
        for (String name : this.shareWithFellowship ? fellowshipNames()
                : playerNames()) {
            if (found.size() >= MAX_OFFERS) {
                break;
            }
            if (!name.equalsIgnoreCase(words) && (wanted.length() == 0
                    || name.toLowerCase(Locale.ROOT).contains(wanted))) {
                found.add(name);
            }
        }
        return found;
    }

    private List<String> playerNames() {
        List<String> names = new ArrayList<String>();
        EntityPlayer self = this.mc.thePlayer;
        if (self == null || this.mc.thePlayer.sendQueue == null
                || this.mc.thePlayer.sendQueue.playerInfoList == null) {
            return names;
        }
        for (Object value : this.mc.thePlayer.sendQueue.playerInfoList) {
            if (value instanceof GuiPlayerInfo) {
                String name = ((GuiPlayerInfo)value).name;
                if (name != null && name.length() > 0
                        && !name.equalsIgnoreCase(
                                self.getCommandSenderName())) {
                    names.add(name);
                }
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private List<String> fellowshipNames() {
        List<String> names = new ArrayList<String>();
        if (this.mc.thePlayer == null) {
            return names;
        }
        try {
            LOTRPlayerData data = LOTRLevelData.getData(this.mc.thePlayer);
            List<LOTRFellowshipClient> fellowships = data == null ? null
                    : data.getClientFellowships();
            if (fellowships != null) {
                for (LOTRFellowshipClient fellowship : fellowships) {
                    if (fellowship != null && fellowship.getName() != null) {
                        names.add(fellowship.getName());
                    }
                }
            }
        } catch (RuntimeException unavailable) {
            // A changed LOTR fellowship API costs the offers, nothing more.
            LostTalesClientDiagnostics.warnOnce("waystone-fellowships",
                    "The player's fellowships could not be read for the waystone's share field",
                    unavailable);
        } catch (LinkageError unavailable) {
            LostTalesClientDiagnostics.warnOnce("waystone-fellowships",
                    "The player's fellowships could not be read for the waystone's share field",
                    unavailable);
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }
}
