package com.ninuna.losttales.gui.screen.missive;

import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.client.window.BarItem;
import com.ninuna.losttales.client.window.PageContent;
import com.ninuna.losttales.client.window.PageTab;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowPages;
import com.ninuna.losttales.client.window.WindowScreen;
import com.ninuna.losttales.client.window.WorldPageWatch;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesMissiveAcceptPacket;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveNbt;
import com.ninuna.losttales.quest.missive.MissiveAcceptance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * A missive letter, a page a window holds (Q9 a): the letter in the
 * inventory slot it was used from, on the sheet the board's page shows a
 * notice on ({@link MissiveLetterView}), and Accept on the bar, greyed
 * with its reason where the letter cannot be accepted. Using a letter
 * opens it; using another turns the page to that one.
 *
 * <p>The page closes by itself once that slot no longer holds that
 * letter, checked every tick: moved, dropped, or used up by its own
 * Accept, which closes it without a word, since the quest's own banner
 * says it started. The server reads the slot again before it starts the
 * quest, and says a refusal in the chat.</p>
 */
public final class MissiveLetterPage extends PageContent
        implements WorldPageWatch.Watched {
    /** The code name the page is registered under. */
    public static final String PAGE_ID = "missive_letter";

    /** The item the tab wears until the letter has artwork of its own. */
    public static final ItemStack ICON = new ItemStack(Items.paper);

    /** The bar's item: its id, which the page is told when it is pressed. */
    private static final String ACCEPT = "accept";
    /** Clear pixels round the sheet. */
    private static final int MARGIN = 8;
    /** The widest the sheet grows, so its lines stay a letter's. */
    private static final int MAX_WIDTH = 300;
    /** Ticks an Accept waits for the letter to be used up before it can be pressed again: five seconds. */
    private static final int ANSWER_TICKS = 100;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final MissiveLetterView view = new MissiveLetterView();
    /** The inventory slot the letter was used from; -1 while no letter is open. */
    private int slot = -1;
    /** The letter's quest id as it was read; empty for one that cannot be read. */
    private String questId = "";
    /** The letter; null for one that cannot be read. */
    private LostTalesMissiveData missive;
    /** Whether Accept was sent, and how long it has waited. */
    private boolean accepted;
    private int waitedTicks;
    /** The box the page was last drawn in, which the page keys turn the sheet by. */
    private LostTalesUiHitBox lastBox;
    /** The stack the slot held when last looked at, and its letter's quest id, so an unchanged slot is not read again. */
    private ItemStack lookedAt;
    private String lookedAtId;

    /** The page's content; null before the page is registered. */
    static MissiveLetterPage current() {
        WindowPages.Page page = WindowPages.byId(PAGE_ID);
        PageContent content = page == null ? null : page.content();
        return content instanceof MissiveLetterPage
                ? (MissiveLetterPage)content : null;
    }

    /**
     * The player used the letter in inventory slot {@code slot}: the page
     * turns to it and comes forward, on the window screen already open
     * or on a new one. A slot holding no letter opens nothing.
     */
    public static void open(int slot) {
        MissiveLetterPage page = current();
        if (page != null && page.show(slot)) {
            WindowScreen.openPage(PAGE_ID);
        }
    }

    /** The quest id of the letter in a stack; empty for one that cannot be read, null for no letter. */
    private static String questIdOf(ItemStack stack) {
        if (!MissiveAcceptance.isLetter(stack)) {
            return null;
        }
        LostTalesMissiveData read = LostTalesMissiveNbt.readFromItemStack(stack);
        return read == null ? "" : read.getQuestId();
    }

    private ItemStack stackIn(int inventorySlot) {
        EntityPlayer player = this.mc.thePlayer;
        return player == null || inventorySlot < 0
                || inventorySlot >= player.inventory.getSizeInventory() ? null
                : player.inventory.getStackInSlot(inventorySlot);
    }

    /**
     * Turns the page to the letter in {@code inventorySlot}. The same
     * letter again, its tab still open, keeps its place; another reads
     * from its top.
     */
    private boolean show(int inventorySlot) {
        ItemStack stack = stackIn(inventorySlot);
        String read = questIdOf(stack);
        if (read == null) {
            return false;
        }
        PageTab tab = WindowPages.tab(PAGE_ID);
        boolean same = tab != null && WindowLayout.isOpen(tab)
                && inventorySlot == this.slot && read.equals(this.questId);
        this.slot = inventorySlot;
        this.questId = read;
        this.missive = LostTalesMissiveNbt.readFromItemStack(stack);
        if (!same) {
            this.accepted = false;
            this.view.toTop();
        }
        return true;
    }

    /** The page lets its letter go. */
    private void forget() {
        this.slot = -1;
        this.questId = "";
        this.missive = null;
        this.accepted = false;
        this.lookedAt = null;
        this.lookedAtId = null;
        this.view.toTop();
    }

    /* ---- The letter leaving ---- */

    /** Once a game tick while the page is in front: the slot watched, and an Accept left unanswered let go. */
    @Override
    public void tick() {
        watch();
        if (this.accepted && ++this.waitedTicks >= ANSWER_TICKS) {
            this.accepted = false;
        }
    }

    /**
     * Closes the tab once the slot no longer holds the letter: with a
     * notice saying so, unless its own Accept used it up.
     */
    @Override
    public void watch() {
        PageTab tab = WindowPages.tab(PAGE_ID);
        if (tab == null || !WindowLayout.isOpen(tab)) {
            return;
        }
        if (this.slot >= 0 && MissiveActions.stillHolds(this.questId,
                questIdNow())) {
            return;
        }
        WorldPageWatch.close(tab, this.accepted ? ""
                : StatCollector.translateToLocalFormatted(
                        "gui.losttales.missive_letter.left", title()));
        forget();
    }

    /** The quest id of the letter the slot holds now; read again only once the stack there changed. */
    private String questIdNow() {
        ItemStack stack = stackIn(this.slot);
        if (stack != this.lookedAt) {
            this.lookedAt = stack;
            this.lookedAtId = questIdOf(stack);
        }
        return this.lookedAtId;
    }

    private String title() {
        return this.missive == null || this.missive.getTitle().length() == 0
                ? StatCollector.translateToLocal("item.missive_letter.name")
                : this.missive.getTitle();
    }

    /** The tab closed, by hand or by itself: the letter goes with it. */
    @Override
    public void hidden() {
        PageTab tab = WindowPages.tab(PAGE_ID);
        if (tab == null || !WindowLayout.isOpen(tab)) {
            forget();
        }
    }

    /* ---- Drawing ---- */

    /** The sheet: the page less its margin, no wider than a letter, centred. */
    private static LostTalesUiHitBox sheet(LostTalesUiHitBox box) {
        int boxWidth = (int)Math.floor(box.width);
        int width = Math.max(0, Math.min(boxWidth - MARGIN * 2, MAX_WIDTH));
        return new LostTalesUiHitBox(Math.floor(box.left)
                + LostTalesUiInk.centredStart(boxWidth, width),
                Math.floor(box.top) + MARGIN, width,
                Math.max(0.0D, Math.floor(box.height) - MARGIN * 2));
    }

    @Override
    public void draw(Minecraft minecraft, LostTalesUiHitBox box, double clipX,
                     double clipY, double pointerX, double pointerY,
                     float partialTicks, int alpha) {
        this.lastBox = box;
        if (this.slot < 0) {
            return;
        }
        this.view.draw(minecraft, sheet(box), this.missive,
                StatCollector.translateToLocal(
                        "gui.losttales.missive_letter.invalid"), alpha);
    }

    @Override
    public boolean scroll(LostTalesUiHitBox box, double x, double y,
                          int lines) {
        LostTalesUiHitBox sheet = sheet(box);
        return this.slot >= 0 && sheet.contains(x, y)
                && this.view.scroll(sheet, lines);
    }

    /** The page keys turn the letter. */
    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (this.slot < 0 || keyCode != Keyboard.KEY_PRIOR
                && keyCode != Keyboard.KEY_NEXT) {
            return false;
        }
        LostTalesUiHitBox shown = this.lastBox;
        if (shown != null) {
            this.view.page(sheet(shown), keyCode == Keyboard.KEY_PRIOR ? -1 : 1);
        }
        return true;
    }

    /* ---- The window's bar ---- */

    /** Accept, there whatever the letter, greyed with the reason where it cannot be accepted. */
    @Override
    public List<BarItem> barItems() {
        List<BarItem> items = new ArrayList<BarItem>(1);
        if (this.slot < 0) {
            return items;
        }
        boolean active = this.missive != null
                && LostTalesClientQuestProgressStore.isQuestActive(this.questId);
        BarItem accept = BarItem.button(ACCEPT, StatCollector.translateToLocal(
                "gui.losttales.missive_letter.accept"), LostTalesUiSheet.QUEST,
                LostTalesUiSheet.QUEST_HOVER).tip(StatCollector.translateToLocal(
                        "gui.losttales.missive_letter.accept.tip"));
        String why = MissiveActions.whyNotAcceptLetter(this.missive != null,
                active, this.accepted);
        items.add(why.length() == 0 ? accept
                : accept.unavailable(StatCollector.translateToLocal(why)));
        return items;
    }

    /** Accept asks the server to start the letter's quest; the letter's leaving closes the page. */
    @Override
    public void barPressed(String id, int offer) {
        if (!ACCEPT.equals(id) || this.slot < 0 || this.missive == null
                || this.accepted) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                LostTalesMissiveAcceptPacket.fromPlayerInventory(this.slot,
                        this.questId));
        this.accepted = true;
        this.waitedTicks = 0;
    }
}
