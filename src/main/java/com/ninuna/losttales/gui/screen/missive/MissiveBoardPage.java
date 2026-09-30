package com.ninuna.losttales.gui.screen.missive;

import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.client.window.BarItem;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageTab;
import com.ninuna.losttales.client.window.ToolStrip;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowBar;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.client.window.WorldPageReach;
import com.ninuna.losttales.client.window.WorldPageWatch;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiClip;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesMissiveAcceptPacket;
import com.ninuna.losttales.network.packet.LostTalesMissiveBoardRequestPacket;
import com.ninuna.losttales.network.packet.LostTalesMissiveBoardStatePacket;
import com.ninuna.losttales.quest.LostTalesQuestTimeText;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.MissiveAcceptance;
import com.ninuna.losttales.quest.missive.MissiveBoardStateReason;
import com.ninuna.losttales.quest.missive.MissiveNotice;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * A missive board, a page a window holds: the board's notices in
 * a list at the left — each its title, and who posted it and how long it
 * has left on the board under that — and the picked notice's letter at
 * the right, the same letter a letter's own page shows
 * ({@link MissiveLetterView}). On the bar: Accept (A), which starts the
 * notice's quest straight from the board; Take Letter (T), which takes it
 * down into the inventory; Pin Letter (P), which puts a letter carried
 * back up — the one letter at once, or one picked from a menu of them
 * where several are carried ({@link MissivePinMenu}) — and how many
 * notices the board posts of how many it holds.
 *
 * <p>Using a board opens it: the server checks the board and sends its
 * notices as an opening. One board is shown at a time; using another
 * turns the page to it. Every request is answered with the board's
 * notices and why, which stands over the page's bar, as do the
 * server's lines in the chat about a missive while the page is shown.
 * The server sends the notices again whenever the board changes while the
 * player stands at it — somebody else's take, pin or accept, a notice
 * posted or taken down — so the list follows the board.</p>
 *
 * <p>The tab closes by itself, fading as tabs close, once the player is
 * more than eight blocks away, in another world, or the board is gone,
 * and a notice over the window's bar says which.</p>
 */
public final class MissiveBoardPage extends PageContent
        implements WorldPageWatch.Watched {
    /** The code name the page is registered under. */
    public static final String PAGE_ID = "missive_board";

    /** The item the tab wears until the board has artwork of its own. */
    public static final ItemStack ICON = new ItemStack(Items.sign);

    /** Where the page's words live in the lang file. */
    private static final String LANG = "gui.losttales.missive_board.";

    /** The list's button at the strip's left end: the quest glyph, until the board has one of its own. */
    private static final ToolStrip.Panel LIST_PANEL = new ToolStrip.Panel(
            LostTalesUiSheet.QUEST, LostTalesUiSheet.QUEST_HOVER,
            LANG + "list.show", LANG + "list.hide");

    /** The bar's items: their ids, which the page is told when one is pressed. */
    private static final String ACCEPT = "accept";
    private static final String TAKE = "take";
    private static final String PIN = "pin";
    /** The keys the bar's buttons answer to while the page holds the keys. */
    private static final int ACCEPT_KEY = Keyboard.KEY_A;
    private static final int TAKE_KEY = Keyboard.KEY_T;
    private static final int PIN_KEY = Keyboard.KEY_P;
    /** Ticks a request waits for its answer before the page stops waiting: five seconds. */
    private static final int ANSWER_TICKS = 100;
    /** The wheel moves the list half a notice a line. */
    private static final int WHEEL_STEP = MissiveBoardLayout.ROW_HEIGHT / 2;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final MissiveLetterView view = new MissiveLetterView();
    /** The board's notices as the server last sent them; null while no board is open. */
    private LostTalesMissiveBoardStatePacket state;
    /** The world's clock as the notices came, which each notice's time left counts from. */
    private long receivedAt;
    /** The notice picked, by its slot and its letter; -1 for none. */
    private int pickedSlot = -1;
    private String pickedQuestId = "";
    /** The letter a Pin on its way puts up, picked once the board says it is there; empty for none. */
    private String pinning = "";
    /** Whether a request is on its way, and how long it has waited. */
    private boolean waiting;
    private int waitedTicks;
    /**
     * Whether the quest has said in the chat which of its requirements an
     * Accept on its way does not meet, which then stands over the bar in
     * place of the board's plainer answer.
     */
    private boolean requirementSaid;
    /** The words in the window's well; empty while its search is closed. */
    private String query = "";
    private boolean listOut = true;
    /**
     * Whether the page was wide enough for both halves when last drawn;
     * null before it was. Crossing into a narrow page folds the list to
     * show the letter, and crossing back brings it out.
     */
    private Boolean wasWide;
    private int width = -1;
    private int height = -1;
    /** Where the list was scrolled to, and where it stands on screen, gliding there. */
    private int listScroll;
    private double shownListScroll;
    private long glideNanos;
    /** The row under the pointer this frame; -1 for none. */
    private int hoveredRow = -1;

    /** Registers the page's menu of letters to pin, before the layout file names its kind. */
    public static void install() {
        MissivePinMenu.install();
    }

    /** The page's content; null before the page is registered. */
    static MissiveBoardPage current() {
        WindowPages.Page page = WindowPages.byId(PAGE_ID);
        PageContent content = page == null ? null : page.content();
        return content instanceof MissiveBoardPage
                ? (MissiveBoardPage)content : null;
    }

    /**
     * A board's notices from the server. An opening turns the page to
     * that board and brings it forward, on the window screen already
     * open or on a new one; anything else is read only while the page
     * shows that very board.
     */
    public static void accept(LostTalesMissiveBoardStatePacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }
        MissiveBoardPage board = current();
        if (board == null) {
            return;
        }
        if (packet.isOpening()) {
            board.open(packet);
            WindowScreen.openPage(PAGE_ID);
        } else {
            board.answer(packet);
        }
    }

    private static String word(String key) {
        return StatCollector.translateToLocal(LANG + key);
    }

    private static String word(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(LANG + key, args);
    }

    /* ---- The board ---- */

    /**
     * The page turned to the board the player used. The same board again,
     * its tab still open, keeps the notice picked; another board starts
     * from its first notice.
     */
    private void open(LostTalesMissiveBoardStatePacket packet) {
        PageTab tab = WindowPages.tab(PAGE_ID);
        boolean same = tab != null && WindowLayout.isOpen(tab)
                && isShown(packet);
        if (!same) {
            forget();
        }
        read(packet);
        this.waiting = false;
        clearAnswer();
    }

    /**
     * The board's notices again: the answer to a request, said over the
     * bar, or a change somebody else made, followed quietly — unless it
     * took down the notice being read.
     */
    private void answer(LostTalesMissiveBoardStatePacket packet) {
        if (!isShown(packet)) {
            return;
        }
        String reading = this.pickedQuestId;
        read(packet);
        MissiveBoardStateReason reason = packet.getReason();
        if (reason.isAnswer()) {
            this.waiting = false;
            if (reason == MissiveBoardStateReason.PINNED) {
                pickLetter(this.pinning);
            }
            this.pinning = "";
            String words = StatCollector.translateToLocal(
                    reason.getMessageKey());
            if (!reason.isRefusal()) {
                sayDone(words);
            } else if (reason != MissiveBoardStateReason.REQUIREMENTS
                    || !this.requirementSaid) {
                sayRefused(words);
            }
        } else if (reading.length() > 0 && !reading.equals(this.pickedQuestId)) {
            sayDone(word("said.picked_gone"));
        }
    }

    /**
     * The server's lines about a missive, and a quest's about its
     * requirements, answer the board's requests while the page is shown.
     */
    @Override
    public boolean answersLine(String key) {
        return MissiveActions.answersRequest(key);
    }

    /** The line is the request's answer, or the first half of it: it stands over the bar, and the request waits no more. */
    @Override
    public void answerLine(String key, String words) {
        this.requirementSaid |= MissiveActions.saysRequirement(key);
        this.waiting = false;
        sayRefused(words);
    }

    /** Takes the board's notices, keeping the notice picked where it still stands. */
    private void read(LostTalesMissiveBoardStatePacket packet) {
        this.state = packet;
        this.receivedAt = worldTime();
        this.pickedSlot = MissiveNoticeList.keepPick(packet.getNotices(),
                this.pickedSlot, this.pickedQuestId);
        MissiveNotice picked = picked();
        this.pickedQuestId = picked == null ? "" : picked.getQuestId();
    }

    /** Whether the page shows the board a state is about. */
    private boolean isShown(LostTalesMissiveBoardStatePacket packet) {
        return this.state != null && packet.isAbout(
                this.state.getDimensionId(), this.state.getX(),
                this.state.getY(), this.state.getZ());
    }

    /** The page lets its board go. */
    private void forget() {
        this.state = null;
        this.pickedSlot = -1;
        this.pickedQuestId = "";
        this.pinning = "";
        this.waiting = false;
        clearAnswer();
        this.listScroll = 0;
        this.shownListScroll = 0.0D;
        this.view.toTop();
    }

    /** Whether the page shows a board now. */
    boolean showsBoard() {
        return this.state != null;
    }

    private MissiveNotice picked() {
        return this.state == null ? null
                : MissiveNoticeList.inSlot(this.state.getNotices(),
                        this.pickedSlot);
    }

    private void pick(int slot) {
        if (slot == this.pickedSlot || this.state == null) {
            return;
        }
        MissiveNotice notice = MissiveNoticeList.inSlot(
                this.state.getNotices(), slot);
        if (notice != null) {
            this.pickedSlot = slot;
            this.pickedQuestId = notice.getQuestId();
            this.view.toTop();
        }
    }

    /** Picks the notice of {@code questId}, where it stands on the board. */
    private void pickLetter(String questId) {
        if (this.state == null || questId == null || questId.length() == 0) {
            return;
        }
        for (MissiveNotice notice : this.state.getNotices()) {
            if (questId.equals(notice.getQuestId())) {
                pick(notice.getSlot());
                return;
            }
        }
    }

    /** The notices the search keeps, in slot order. */
    private List<MissiveNotice> listed() {
        return this.state == null ? Collections.<MissiveNotice>emptyList()
                : MissiveNoticeList.of(this.state.getNotices(), this.query);
    }

    private long worldTime() {
        World world = this.mc.theWorld;
        return world == null ? 0L : world.getTotalWorldTime();
    }

    /* ---- Walking away ---- */

    /** Once a game tick while the page is in front: the board watched, and a request left unanswered let go. */
    @Override
    public void tick() {
        watch();
        if (this.waiting && ++this.waitedTicks >= ANSWER_TICKS) {
            this.waiting = false;
            sayRefused(word("said.no_answer"));
        }
    }

    /** Closes the tab where the player no longer stands at the board. */
    @Override
    public void watch() {
        PageTab tab = WindowPages.tab(PAGE_ID);
        if (tab == null || !WindowLayout.isOpen(tab)) {
            return;
        }
        WorldPageReach.Leave leave = leave();
        if (leave != null) {
            WorldPageWatch.close(tab, StatCollector.translateToLocal(
                    leave.messageKey("missive_board")));
            forget();
        }
    }

    /** Why the player no longer stands at the board; null while they do. */
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
                && world.getBlock(x, y, z)
                        == ELostTalesBlock.MISSIVE_BOARD.getBlock();
        return WorldPageReach.check(player.dimension, player.posX,
                player.boundingBox.minY, player.posZ,
                this.state.getDimensionId(), x, y, z, standing,
                LostTalesTileEntityMissiveBoard.REACH_SQ);
    }

    /** The tab closed, by hand or by itself: the board goes with it. */
    @Override
    public void hidden() {
        PageTab tab = WindowPages.tab(PAGE_ID);
        if (tab == null || !WindowLayout.isOpen(tab)) {
            forget();
        }
    }

    /* ---- Requests ---- */

    /** Starts the picked notice's quest straight from the board. */
    private void acceptPicked() {
        MissiveNotice picked = picked();
        if (this.state == null || picked == null || !picked.isReadable()
                || this.waiting) {
            return;
        }
        send(LostTalesMissiveAcceptPacket.fromBoard(
                this.state.getDimensionId(), this.state.getX(),
                this.state.getY(), this.state.getZ(), picked.getSlot(),
                picked.getQuestId()));
    }

    /** Takes the picked notice down into the inventory. */
    private void takePicked() {
        MissiveNotice picked = picked();
        if (this.state == null || picked == null || this.waiting) {
            return;
        }
        send(LostTalesMissiveBoardRequestPacket.take(
                this.state.getDimensionId(), this.state.getX(),
                this.state.getY(), this.state.getZ(), picked.getSlot(),
                picked.getQuestId()));
    }

    /**
     * Pin Letter: the one letter carried goes up at once; where several
     * are carried, a menu of them hangs from the pointer, the one in hand
     * first.
     */
    private void pinFromBar() {
        List<Integer> carried = carriedLetters();
        if (carried.size() == 1) {
            pin(carried.get(0).intValue());
        } else if (carried.size() > 1) {
            MissivePinMenu.show(windowId());
        }
    }

    /** Pins the letter in the inventory's {@code slot} onto the board. */
    void pin(int slot) {
        EntityPlayer player = this.mc.thePlayer;
        if (this.state == null || this.waiting || player == null) {
            return;
        }
        String questId = MissiveAcceptance.pageQuestId(
                player.inventory.getStackInSlot(slot));
        if (questId == null) {
            return;
        }
        send(LostTalesMissiveBoardRequestPacket.pin(
                this.state.getDimensionId(), this.state.getX(),
                this.state.getY(), this.state.getZ(), slot, questId));
        this.pinning = questId;
    }

    /** Sends a request and waits for the board's answer. */
    private void send(IMessage request) {
        LostTalesNetworkHandler.CHANNEL.sendToServer(request);
        this.waiting = true;
        this.waitedTicks = 0;
        this.requirementSaid = false;
        sayWorking(word("said.waiting"));
    }

    /**
     * The main inventory's slots that hold a missive letter, the one in
     * hand first and then in slot order.
     */
    List<Integer> carriedLetters() {
        List<Integer> slots = new ArrayList<Integer>();
        EntityPlayer player = this.mc.thePlayer;
        if (player == null) {
            return slots;
        }
        int hand = player.inventory.currentItem;
        if (hand >= 0 && hand < player.inventory.mainInventory.length
                && MissiveAcceptance.isLetter(player.inventory.mainInventory[hand])) {
            slots.add(Integer.valueOf(hand));
        }
        for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
            if (slot != hand && MissiveAcceptance.isLetter(
                    player.inventory.mainInventory[slot])) {
                slots.add(Integer.valueOf(slot));
            }
        }
        return slots;
    }

    private boolean inventoryFull() {
        EntityPlayer player = this.mc.thePlayer;
        return player == null || player.inventory.getFirstEmptyStack() < 0;
    }

    /** The window the page's tab stands in; null while none holds it. */
    private static String windowId() {
        PageTab tab = WindowPages.tab(PAGE_ID);
        Window window = tab == null ? null : WindowLayout.windowOf(tab);
        return window == null ? null : window.getId();
    }

    /* ---- Drawing ---- */

    private MissiveBoardLayout layout() {
        return new MissiveBoardLayout(this.width, this.height, this.listOut);
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        FontRenderer font = minecraft.fontRenderer;
        this.width = (int)Math.floor(box.width);
        this.height = (int)Math.floor(box.height);
        long now = System.nanoTime();
        double seconds = this.glideNanos == 0L ? 0.0D
                : Math.max(0.0D, (now - this.glideNanos) / 1.0E9D);
        this.glideNanos = now;
        MissiveBoardLayout layout = layout();
        boolean wide = layout.isWide();
        if (this.wasWide == null || wide != this.wasWide.booleanValue()) {
            this.listOut = wide;
            layout = layout();
        }
        this.wasWide = Boolean.valueOf(wide);
        List<MissiveNotice> shown = listed();
        this.listScroll = Math.max(0, Math.min(this.listScroll,
                layout.maxListScroll(shown.size())));
        this.shownListScroll = Motions.followTravel(MotionIds.WINDOW_SCROLL,
                this.shownListScroll, this.listScroll, seconds);
        this.hoveredRow = Double.isNaN(pointerX) || Double.isNaN(pointerY)
                ? -1 : layout.rowAt(pointerX - box.left, pointerY - box.top,
                        this.shownListScroll, shown.size());
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef((float)box.left, (float)box.top, 0.0F);
            drawList(font, layout, shown, alpha);
            LostTalesUiHitBox divider = layout.divider();
            if (divider.width > 0) {
                LostTalesUiInk.fillRect((float)divider.left,
                        (float)divider.top, (float)divider.right(),
                        (float)divider.bottom(),
                        MissiveLetterView.faded(LostTalesColors.BORDER_DIM, alpha));
            }
            LostTalesUiHitBox letter = layout.letter();
            if (letter.width > 0 && letter.height > 0) {
                MissiveNotice picked = picked();
                this.view.draw(minecraft, letter, picked == null ? null
                        : picked.getMissive(), emptyWords(picked), alpha);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** What the sheet says where it has no letter to show. */
    private String emptyWords(MissiveNotice picked) {
        if (this.state == null || this.state.getNotices().isEmpty()) {
            return word("empty");
        }
        if (picked == null) {
            return word("pick");
        }
        return StatCollector.translateToLocal(
                "gui.losttales.missive_letter.invalid");
    }

    /** The notices, clipped to the list and scrolled as one. */
    private void drawList(FontRenderer font, MissiveBoardLayout layout,
                          List<MissiveNotice> shown, int alpha) {
        LostTalesUiHitBox list = layout.list();
        if (list.width <= 0 || list.height <= 0) {
            return;
        }
        if (shown.isEmpty()) {
            String note = this.query.length() > 0 ? word("search.none")
                    : word("empty");
            int y = (int)list.top;
            for (Object line : font.listFormattedStringToWidth(note,
                    Math.max(1, (int)list.width))) {
                LostTalesUiInk.drawText(font, String.valueOf(line),
                        (int)list.left, y + WindowStyle.ROW_TEXT_TOP,
                        WindowStyle.asideRgb(), alpha);
                y += WindowStyle.LINE_HEIGHT;
            }
            return;
        }
        long now = worldTime();
        boolean clipped = LostTalesUiClip.beginLocal(this.mc,
                (float)list.left - MissiveBoardLayout.ROW_BLEED,
                (float)list.top, (float)list.right(), (float)list.bottom());
        GL11.glPushMatrix();
        try {
            int whole = (int)Math.floor(this.shownListScroll);
            GL11.glTranslatef(0.0F, (float)(whole - this.shownListScroll), 0.0F);
            for (int index = 0; index < shown.size(); index++) {
                LostTalesUiHitBox row = layout.row(index, whole);
                if (row.bottom() >= list.top - 1 && row.top <= list.bottom() + 1) {
                    drawRow(font, shown.get(index), row, index == this.hoveredRow,
                            now, alpha);
                }
            }
        } finally {
            GL11.glPopMatrix();
            LostTalesUiClip.end(clipped);
        }
    }

    /**
     * One notice: its title, and under it who posted it and, at the right,
     * how long it has left on the board. The picked notice and the one
     * under the pointer each take one surface of their own.
     */
    private void drawRow(FontRenderer font, MissiveNotice notice,
                         LostTalesUiHitBox row, boolean hovered, long now,
                         int alpha) {
        int left = (int)row.left;
        int right = (int)row.right();
        int top = (int)row.top;
        boolean isPicked = notice.getSlot() == this.pickedSlot;
        if (isPicked || hovered) {
            LostTalesUiInk.fillRect(left - MissiveBoardLayout.ROW_BLEED, top, right,
                    top + MissiveBoardLayout.ROW_HEIGHT,
                    MissiveLetterView.faded(isPicked
                            ? LostTalesColors.withAlpha(LostTalesColors.PLUM_GRAY, 0xB4)
                            : LostTalesColors.withAlpha(LostTalesColors.PLUM_DARK, 0x72),
                            alpha));
        }
        LostTalesMissiveData missive = notice.getMissive();
        String title = missive == null ? word("unreadable") : missive.getTitle();
        int titleRgb = missive == null ? WindowStyle.asideRgb()
                : isPicked ? LostTalesUiInk.IVORY
                : LostTalesColors.rgb(LostTalesColors.TEXT);
        int width = Math.max(0, right - left);
        LostTalesUiInk.drawText(font, LostTalesSkyrimUiStyle.trimToWidth(font,
                        title, width), left, top + WindowStyle.ROW_TEXT_TOP,
                titleRgb, alpha);
        int second = top + WindowStyle.LINE_HEIGHT + WindowStyle.ROW_TEXT_TOP;
        long remaining = MissiveNoticeList.ticksLeft(notice, this.receivedAt,
                now);
        String time = remaining < 0L ? ""
                : LostTalesQuestTimeText.shortForm(remaining);
        int timeWidth = time.length() == 0 ? 0 : font.getStringWidth(time);
        if (timeWidth > 0) {
            LostTalesUiInk.drawText(font, time, right - timeWidth, second,
                    WindowStyle.asideRgb(), alpha);
        }
        String issuer = missive == null ? "" : missive.getIssuer();
        if (issuer.length() > 0) {
            LostTalesUiInk.drawText(font, LostTalesSkyrimUiStyle.trimToWidth(
                            font, issuer, Math.max(0, width - timeWidth
                                    - (timeWidth > 0 ? 6 : 0))), left, second,
                    WindowStyle.asideRgb(), alpha);
        }
    }

    /* ---- The pointer ---- */

    /** The row under a point in the window's space; -1 for none. */
    private int rowAt(LostTalesUiHitBox box, double x, double y) {
        return this.width < 0 || this.state == null ? -1
                : layout().rowAt(x - box.left, y - box.top,
                        this.shownListScroll, listed().size());
    }

    @Override
    public boolean acts(LostTalesUiHitBox box, double x, double y) {
        return rowAt(box, x, y) >= 0;
    }

    /** A press on a notice picks it, and a narrow page folds the list to show its letter. */
    @Override
    public boolean mousePressed(Minecraft minecraft, LostTalesUiHitBox box,
                                double x, double y, int button) {
        int row = rowAt(box, x, y);
        if (button != 0 || row < 0) {
            return false;
        }
        pick(listed().get(row).getSlot());
        if (!layout().isWide()) {
            this.listOut = false;
        }
        return true;
    }

    /** The wheel scrolls the list or the letter, whichever it is turned over. */
    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        if (lines == 0 || this.width < 0 || this.state == null) {
            return false;
        }
        MissiveBoardLayout layout = layout();
        double pageX = x - box.left;
        double pageY = y - box.top;
        if (layout.list().contains(pageX, pageY)) {
            this.listScroll += lines * WHEEL_STEP;
            return true;
        }
        LostTalesUiHitBox letter = layout.letter();
        return letter.contains(pageX, pageY) && this.view.scroll(letter, lines);
    }

    /* ---- The keys ---- */

    /**
     * The arrows walk the notices, the page keys turn the letter, and A,
     * T and P take the bar's Accept, Take Letter and Pin Letter while
     * each can be taken, as their tips name them.
     */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.state == null) {
            return false;
        }
        if (keyCode == ACCEPT_KEY || keyCode == TAKE_KEY
                || keyCode == PIN_KEY) {
            String why = keyCode == ACCEPT_KEY ? whyNotAccept()
                    : keyCode == TAKE_KEY ? whyNotTake() : whyNotPin();
            if (why == null || why.length() == 0) {
                barPressed(keyCode == ACCEPT_KEY ? ACCEPT
                        : keyCode == TAKE_KEY ? TAKE : PIN, -1);
            }
            return true;
        }
        if (this.width < 0) {
            return false;
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            walk(keyCode == Keyboard.KEY_UP ? -1 : 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_PRIOR || keyCode == Keyboard.KEY_NEXT) {
            this.view.page(layout().letter(),
                    keyCode == Keyboard.KEY_PRIOR ? -1 : 1);
            return true;
        }
        return false;
    }

    private void walk(int step) {
        int slot = MissiveNoticeList.step(listed(), this.pickedSlot, step);
        if (slot >= 0) {
            pick(slot);
        }
    }

    /** The board's tab wears tan, the letters' paper. */
    @Override
    public int tone() {
        return LostTalesColors.rgb(LostTalesColors.TAN);
    }

    /* ---- The window's strip ---- */

    @Override
    public ToolStrip.Panel panel() {
        return LIST_PANEL;
    }

    @Override
    public boolean isPanelOut() {
        return this.listOut;
    }

    @Override
    public void togglePanel() {
        this.listOut = !this.listOut;
    }

    @Override
    public String searchPrompt() {
        return word("search");
    }

    /**
     * New words read the list from its top and bring it out; the first
     * notice found is picked where the one picked is not among them.
     */
    @Override
    public void search(String words) {
        String typed = words == null ? "" : words.trim();
        if (typed.equals(this.query)) {
            return;
        }
        this.query = typed;
        this.listScroll = 0;
        if (typed.length() > 0) {
            this.listOut = true;
            List<MissiveNotice> shown = listed();
            if (MissiveNoticeList.inSlot(shown, this.pickedSlot) == null
                    && !shown.isEmpty()) {
                pick(shown.get(0).getSlot());
            }
        }
    }

    @Override
    public int found() {
        return this.query.length() == 0 ? -1 : listed().size();
    }

    /** The arrows walk the notices found; Return gives the page the keys, and a narrow page shows the letter. */
    @Override
    public boolean searchKey(int keyCode) {
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            walk(keyCode == Keyboard.KEY_UP ? -1 : 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (this.width >= 0 && !layout().isWide()) {
                this.listOut = false;
            }
            return true;
        }
        return false;
    }

    /* ---- The window's bar ---- */

    /**
     * Accept, Take Letter and Pin Letter, each there whatever is picked,
     * greyed with the reason where it cannot be taken, and at the right
     * how many notices the board posts of how many it holds.
     */
    @Override
    public List<BarItem> barItems() {
        if (this.state == null) {
            return Collections.emptyList();
        }
        List<BarItem> items = new ArrayList<BarItem>(4);
        items.add(orWhy(BarItem.button(ACCEPT, word("accept"),
                LostTalesUiSheet.QUEST, LostTalesUiSheet.QUEST_HOVER)
                .tip(WindowBar.withKey(word("accept.tip"), ACCEPT_KEY)),
                whyNotAccept()));
        items.add(orWhy(BarItem.button(TAKE, word("take"),
                new ItemStack(Items.paper)).tip(WindowBar.withKey(
                        word("take.tip"), TAKE_KEY)),
                whyNotTake()));
        items.add(orWhy(BarItem.button(PIN, word("pin"),
                new ItemStack(Items.sign)).tip(WindowBar.withKey(
                        word("pin.tip"), PIN_KEY)),
                whyNotPin()));
        items.add(BarItem.words(word("available",
                Integer.valueOf(this.state.getNotices().size()),
                Integer.valueOf(this.state.getMaxNotices()))));
        return items;
    }

    /** Why Accept cannot be taken now, as a lang key; empty while it can. */
    private String whyNotAccept() {
        MissiveNotice picked = picked();
        boolean active = picked != null && picked.isReadable()
                && LostTalesClientQuestProgressStore.isQuestActive(
                        picked.getQuestId());
        return MissiveActions.whyNotAccept(picked, active, this.waiting);
    }

    /** Why Take Letter cannot be taken now, as a lang key; empty while it can. */
    private String whyNotTake() {
        return MissiveActions.whyNotTake(picked(), inventoryFull(),
                this.waiting);
    }

    /** Why Pin Letter cannot be taken now, as a lang key; empty while it can. */
    private String whyNotPin() {
        return MissiveActions.whyNotPin(carriedLetters().size(),
                this.state.getNotices().size(), this.state.getMaxNotices(),
                LostTalesTileEntityMissiveBoard.INVENTORY_SIZE, this.waiting);
    }

    /** Greyed with the words of {@code whyKey}; as it is for an empty key. */
    private static BarItem orWhy(BarItem item, String whyKey) {
        return whyKey == null || whyKey.length() == 0 ? item
                : item.unavailable(StatCollector.translateToLocal(whyKey));
    }

    @Override
    public void barPressed(String id, int offer) {
        if (ACCEPT.equals(id)) {
            acceptPicked();
        } else if (TAKE.equals(id)) {
            takePicked();
        } else if (PIN.equals(id)) {
            pinFromBar();
        }
    }
}
