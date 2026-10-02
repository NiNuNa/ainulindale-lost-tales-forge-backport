package com.ninuna.losttales.config.client;

import com.ninuna.losttales.client.window.PageKeys;
import com.ninuna.losttales.client.chat.ClientChatChannelState;
import com.ninuna.losttales.client.window.BarItem;
import com.ninuna.losttales.client.window.MenuWindow;
import com.ninuna.losttales.client.window.PageAnswer;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageRows;
import com.ninuna.losttales.client.window.OtherPage;
import com.ninuna.losttales.client.window.Settings;
import com.ninuna.losttales.client.window.SubWindowAnchor;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowFrame;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowMenus;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.config.server.ServerConfigApplyResult;
import com.ninuna.losttales.config.server.ServerConfigChange;
import com.ninuna.losttales.config.server.ServerConfigEntry;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesServerConfigApplyPacket;
import com.ninuna.losttales.network.packet.LostTalesServerConfigRequestPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * The server's settings, a page a window holds: every setting the server
 * offers in one scrolling column of Settings' own rows, a category to a
 * section ({@link ServerSettingsRows}), and on the bar Save (S), Discard
 * and Refresh (R). It is there for a player the server lets edit its
 * settings, and asks the server for them each time it comes on screen;
 * the server checks every request again.
 *
 * <p>Every change waits on the page ({@link ServerSettingsDraft}), its
 * new value in honey, until Save sends them all at once. What the
 * server made of them stands over the bar: saved, or the first refusal
 * and its reason, the refused changes still waiting.</p>
 */
public final class ServerSettingsPage extends PageContent {
    /** The code name the page is registered under. */
    public static final String PAGE_ID = "server_settings";

    /** The item the tab wears. */
    public static final ItemStack ICON = new ItemStack(Blocks.command_block);
    /**
     * The most one Save sends: under the 32,767 bytes the game lets a
     * client send in one packet, with room for what the channel adds.
     */
    private static final int MAX_SENT_BYTES = 32000;

    private static final String LANG = "gui.losttales.server_settings.";
    /** The bar's items: their ids, which the page is told when one is pressed. */
    private static final String SAVE = "save";
    private static final String DISCARD = "discard";
    private static final String REFRESH = "refresh";
    /** The keys the bar's buttons answer to while the page holds the keys. */
    private static final int SAVE_KEY = Keyboard.KEY_S;
    private static final int REFRESH_KEY = Keyboard.KEY_R;
    /** Ticks a request waits for its answer: ten seconds. */
    private static final int ANSWER_TICKS = 200;
    /** Clear room above and below the rows. */
    private static final int TOP = 2;
    /** The widest the column grows, so a name and its value stay near. */
    private static final int MAX_COLUMN_WIDTH = 360;

    /** What the page waits for from the server. */
    private enum Wait { NONE, SETTINGS, RESULT }

    private final ServerSettingsDraft draft = new ServerSettingsDraft();
    private final ServerSettingsRows rows;
    private final PageRows list;
    /** Counts the snapshots read: a value window opened on an earlier one closes. */
    private int generation;
    private Wait awaiting = Wait.NONE;
    /** The answer's sequence number, and how long it has been waited for. */
    private int expected;
    private int waitedTicks;
    /** The changes the Save on its way sent; null for none. */
    private List<ServerConfigChange> sent;
    /** The words in the window's well; empty while its search is closed. */
    private String query = "";

    public ServerSettingsPage() {
        this.rows = new ServerSettingsRows(new ServerSettingsRows.Host() {
            @Override
            public ServerSettingsDraft draft() {
                return ServerSettingsPage.this.draft;
            }

            @Override
            public int generation() {
                return ServerSettingsPage.this.generation;
            }

            @Override
            public boolean stands(int generation) {
                OtherPage tab = WindowPages.tab(PAGE_ID);
                return generation == ServerSettingsPage.this.generation
                        && tab != null && WindowLayout.isOpen(tab);
            }
        });
        this.list = new PageRows(new PageRows.Taker() {
            @Override
            public void take(MenuWindow.Entry entry, String part,
                             boolean back, LostTalesUiHitBox row) {
                ServerSettingsPage.this.take(entry, part, back, row);
            }
        });
    }

    private static String word(String key) {
        return StatCollector.translateToLocal(LANG + key);
    }

    private static String word(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(LANG + key, args);
    }

    /* ---- Reading and saving ---- */

    /**
     * On screen: the settings asked of the server again, from the window
     * screen only; a page drawn pinned sends nothing.
     */
    @Override
    public void shown() {
        if (WindowScreen.current() != null) {
            read();
        }
    }

    /** The tab closed: what waited goes with it. */
    @Override
    public void hidden() {
        OtherPage tab = WindowPages.tab(PAGE_ID);
        if (tab == null || !WindowLayout.isOpen(tab)) {
            forget();
        }
    }

    private void forget() {
        this.draft.clear();
        this.generation++;
        this.awaiting = Wait.NONE;
        this.sent = null;
        clearAnswer();
    }

    /** Asks the server for its settings. */
    private void read() {
        if (this.awaiting != Wait.NONE) {
            return;
        }
        this.expected = ClientServerConfigCache.getSnapshotSequence() + 1;
        this.awaiting = Wait.SETTINGS;
        this.waitedTicks = 0;
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesServerConfigRequestPacket());
        sayWorking(word("loading"));
    }

    private void load(List<ServerConfigEntry> snapshot) {
        this.draft.load(snapshot);
        this.generation++;
    }

    /** How many bytes a packet takes, written as it would be sent. */
    private static int sizeOf(LostTalesServerConfigApplyPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            return buffer.readableBytes();
        } finally {
            buffer.release();
        }
    }

    /** Sends every change that waits at once. */
    private void save() {
        List<ServerConfigChange> changes = this.draft.changes();
        if (changes.isEmpty() || this.awaiting != Wait.NONE) {
            return;
        }
        this.sent = changes;
        LostTalesServerConfigApplyPacket packet =
                new LostTalesServerConfigApplyPacket(changes);
        if (sizeOf(packet) > MAX_SENT_BYTES) {
            // The game refuses what a client sends past that size.
            sayRefused(word("too_much"));
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(packet);
        this.expected = ClientServerConfigCache.getResultSequence() + 1;
        this.awaiting = Wait.RESULT;
        this.waitedTicks = 0;
        sayWorking(word("applying"));
    }

    /**
     * Reads the settings again; while changes wait, a question inside the
     * page's window asks first whether they may go.
     */
    private void refresh() {
        if (this.awaiting != Wait.NONE) {
            return;
        }
        if (this.draft.count() == 0) {
            read();
            return;
        }
        WindowScreen screen = WindowScreen.current();
        OtherPage tab = WindowPages.tab(PAGE_ID);
        if (screen != null && tab != null) {
            screen.ask(tab, word("discard.title"), word("discard.question"),
                    word("discard.confirm"), new Runnable() {
                        @Override
                        public void run() {
                            ServerSettingsPage.this.draft.discard();
                            read();
                        }
                    });
        }
    }

    /**
     * Once a game tick: the server's settings or its answer to a Save read
     * as they arrive, and a request left unanswered let go.
     */
    @Override
    public void tick() {
        if (this.awaiting == Wait.SETTINGS
                && ClientServerConfigCache.getSnapshotSequence() >= this.expected) {
            this.awaiting = Wait.NONE;
            load(ClientServerConfigCache.getSnapshot());
            if (lastAnswer().kind() == PageAnswer.Kind.WORKING) {
                clearAnswer();
            }
        } else if (this.awaiting == Wait.RESULT
                && ClientServerConfigCache.getResultSequence() >= this.expected) {
            this.awaiting = Wait.NONE;
            answer(ClientServerConfigCache.getResult());
        } else if (this.awaiting != Wait.NONE && ++this.waitedTicks >= ANSWER_TICKS) {
            this.awaiting = Wait.NONE;
            this.sent = null;
            sayRefused(word("no_answer"));
        }
    }

    /**
     * What the server made of a Save, over the bar: why it turned all of
     * it down, the first refusal and how many more, or saved and what it
     * restarted. The changes applied are what the server holds now.
     */
    private void answer(ServerConfigApplyResult result) {
        List<ServerConfigChange> changes = this.sent;
        this.sent = null;
        if (result == null) {
            sayRefused(word("no_answer"));
            return;
        }
        this.draft.settle(changes, result);
        List<ServerConfigApplyResult.Refusal> refused = result.getRefused();
        if (result.getMessage().length() > 0) {
            sayRefused(result.getMessage());
        } else if (!refused.isEmpty()) {
            ServerConfigApplyResult.Refusal first = refused.get(0);
            String name = settingName(first.getName());
            sayRefused(refused.size() == 1
                    ? word("refused", name, first.getReason())
                    : word("refused.more", name, first.getReason(),
                            Integer.valueOf(refused.size() - 1)));
        } else if (result.getApplied().isEmpty()) {
            sayDone(word("nothing_changed"));
        } else if (result.getRestarted().isEmpty()) {
            sayDone(word("saved"));
        } else {
            sayDone(word("saved.restarted", join(result.getRestarted())));
        }
    }

    /** A setting's words from its {@code category.key}. */
    private static String settingName(String qualifiedName) {
        int point = qualifiedName.indexOf('.');
        return ServerSettingNames.name(point < 0 ? qualifiedName
                : qualifiedName.substring(point + 1));
    }

    private static String join(List<String> words) {
        StringBuilder joined = new StringBuilder();
        for (String word : words) {
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(word);
        }
        return joined.toString();
    }

    /* ---- The rows ---- */

    /**
     * A row taken: a number's chevrons step it and its value opens the
     * value window, as does a line, and a few-word option opens its
     * words; a switch flips.
     */
    private void take(MenuWindow.Entry entry, String part, boolean back,
                      LostTalesUiHitBox row) {
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
        ServerConfigEntry setting = this.rows.entryFor(entry.id);
        if (setting == null) {
            return;
        }
        if (setting.getType() == ServerConfigEntry.Type.BOOLEAN) {
            this.draft.set(setting, String.valueOf(
                    !ServerSettingsRows.isOn(this.draft.value(setting))));
        }
    }

    /** Settings' own value window for a number or a line, or the words of a few-word option, hung from its row in the page's window. */
    private void openValue(Settings.Setting setting, LostTalesUiHitBox row) {
        WindowScreen screen = WindowScreen.current();
        OtherPage tab = WindowPages.tab(PAGE_ID);
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
        // The window takes the keys the page held for the press.
        screen.leavePage();
        screen.syncTypingFocus();
    }

    /* ---- Drawing ---- */

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        List<MenuWindow.Entry> built = this.rows.build(this.query);
        if (built.isEmpty() && this.draft.isLoaded()) {
            built.add(MenuWindow.Entry.passive(StatCollector.translateToLocal(
                    "gui.losttales.window.settings.none")));
        }
        this.list.setRows(built);
        LostTalesUiHitBox rowsBox = rowsBox(box);
        this.list.draw(minecraft, rowsBox, clipX + (rowsBox.left - box.left),
                clipY + (rowsBox.top - box.top), pointerX, pointerY, alpha);
    }

    /**
     * Where the rows stand: a column as wide as the page up to a limit,
     * centred, with clear room above and below.
     */
    private static LostTalesUiHitBox rowsBox(LostTalesUiHitBox box) {
        int boxWidth = (int)Math.floor(box.width);
        int width = Math.max(0, Math.min(boxWidth, MAX_COLUMN_WIDTH));
        return new LostTalesUiHitBox(Math.floor(box.left)
                + LostTalesUiInk.centredStart(boxWidth, width),
                Math.floor(box.top) + TOP, width,
                Math.max(0.0D, Math.floor(box.height) - 2 * TOP));
    }

    /* ---- The pointer ---- */

    @Override
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        return this.draft.isLoaded() && this.list.acts(rowsBox(box), x, y);
    }

    @Override
    public String tipAt(LostTalesUiHitBox box, double x, double y) {
        return this.draft.isLoaded() ? this.list.tipAt(rowsBox(box), x, y) : "";
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box,
                                double x, double y, int button) {
        return this.draft.isLoaded()
                && this.list.press(rowsBox(box), x, y, button);
    }

    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        if (!this.draft.isLoaded() || lines == 0
                || !rowsBox(box).contains(x, y)) {
            return false;
        }
        this.list.scroll(lines);
        return true;
    }

    /** The Server Log's grey. */
    @Override
    public int tone() {
        return LostTalesColors.rgb(LostTalesColors.CONSOLE_TONE);
    }

    /** While the server says the player may edit its settings. */
    @Override
    public boolean isAvailable() {
        return ClientChatChannelState.canEditServerConfig();
    }

    /* ---- The window's strip ---- */

    /** The keys the Server Settings page answers to, for its help. */
    @Override
    public List<PageKeys.Area> keyAreas() {
        return PageKeys.pageArea("gui.losttales.page.server_settings",
                PageKeys.pageKey(PAGE_ID, "save", Keyboard.KEY_S),
                PageKeys.pageKey(PAGE_ID, "refresh", Keyboard.KEY_R),
                PageKeys.pageKey(PAGE_ID, "step", PageKeys.CLICK),
                PageKeys.pageKey(PAGE_ID, "back", PageKeys.RIGHT_CLICK),
                PageKeys.pageKey(PAGE_ID, "ten",
                        Keyboard.KEY_LSHIFT, PageKeys.PLUS, PageKeys.CLICK),
                PageKeys.pageKey(PAGE_ID, "type", PageKeys.CLICK),
                PageKeys.pageKey(PAGE_ID, "wheel", PageKeys.WHEEL));
    }

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
        return this.query.length() == 0 || !this.draft.isLoaded() ? -1
                : PageRows.found(this.rows.build(this.query));
    }

    /* ---- The window's bar ---- */

    /**
     * Save, Discard and Refresh, each there whatever waits, greyed with
     * the reason where it cannot be taken, and at the right how many
     * changes wait. Save is lit while any does.
     */
    @Override
    public List<BarItem> barItems() {
        List<BarItem> items = new ArrayList<BarItem>(4);
        int waiting = this.draft.count();
        items.add(orWhy(BarItem.button(SAVE, word("save"),
                new ItemStack(Items.paper)).lit(waiting > 0)
                .tip(WindowBar.withKey(word("save.tip"), SAVE_KEY)),
                whyNoSave()));
        items.add(orWhy(BarItem.button(DISCARD, word("discard"),
                LostTalesUiSheet.REPLY, LostTalesUiSheet.REPLY_HOVER)
                .tip(word("discard.tip")), waiting > 0 ? ""
                        : word("why.nothing_waits")));
        items.add(orWhy(BarItem.button(REFRESH, word("refresh"),
                new ItemStack(Items.clock)).tip(WindowBar.withKey(
                        word("refresh.tip"), REFRESH_KEY)), whyNoRefresh()));
        if (waiting > 0) {
            items.add(BarItem.words(waiting == 1 ? word("waiting.one")
                    : word("waiting.many", Integer.valueOf(waiting))));
        }
        return items;
    }

    /** Why Save cannot be taken now; empty while it can. */
    private String whyNoSave() {
        return this.awaiting != Wait.NONE ? word("why.waiting")
                : this.draft.count() == 0 ? word("why.nothing_waits") : "";
    }

    /** Why Refresh cannot be taken now; empty while it can. */
    private String whyNoRefresh() {
        return this.awaiting != Wait.NONE ? word("why.waiting") : "";
    }

    private static BarItem orWhy(BarItem item, String why) {
        return why == null || why.length() == 0 ? item : item.unavailable(why);
    }

    @Override
    public void barPressed(String id, int offer) {
        if (SAVE.equals(id)) {
            save();
        } else if (DISCARD.equals(id)) {
            this.draft.discard();
        } else if (REFRESH.equals(id)) {
            refresh();
        }
    }

    /** S takes Save and R Refresh, as the bar's tips name them, while each can be taken. */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (keyCode == SAVE_KEY) {
            if (whyNoSave().length() == 0) {
                save();
            }
            return true;
        }
        if (keyCode == REFRESH_KEY) {
            if (whyNoRefresh().length() == 0) {
                refresh();
            }
            return true;
        }
        return false;
    }
}
