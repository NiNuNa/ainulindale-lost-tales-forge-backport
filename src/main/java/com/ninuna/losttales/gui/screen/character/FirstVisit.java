package com.ninuna.losttales.gui.screen.character;

import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.client.character.ClientCharacterNetwork;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.window.PageCategory;
import com.ninuna.losttales.client.window.View;
import com.ninuna.losttales.client.window.Views;
import com.ninuna.losttales.client.window.WindowScreen;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/**
 * A first visit (CHAR-01): the player waits as a ghost in the Character
 * Editor until their first character is made. Whenever no screen is open
 * while they wait, the window screen stands on the Profile view with the
 * editor in front, as they left it. Closing the screen opens the game
 * menu, where they may leave, and the editor comes back as that menu
 * closes. The views a character is needed for stay closed meanwhile: the
 * Map, the Quest Journal, Fellowships, Skills and the custom views. While
 * the server cannot read characters, it is asked again every half minute.
 *
 * <p>A player whose last switch waits for an administrator is held the
 * same way (CHAR-45), with the Characters page in front, saying why, in
 * place of the editor.</p>
 */
@SideOnly(Side.CLIENT)
public final class FirstVisit {
    /** Ticks between asks while the server cannot read characters. */
    private static final int UNREADABLE_RETRY_TICKS = 600;
    /** The categories' views a first visit cannot open. */
    private static final Set<PageCategory> CLOSED = EnumSet.of(
            PageCategory.MAP, PageCategory.QUEST_JOURNAL,
            PageCategory.FELLOWSHIPS, PageCategory.SKILLS);

    /** Ticks until the roster is asked for again while the store cannot be read. */
    private static int ticksToRetry = UNREADABLE_RETRY_TICKS;
    /** Whether the window screen stood open at the last tick: closed by the player, the game menu comes. */
    private static boolean screenWasOpen;

    private FirstVisit() {}

    /** Keeps the views a character is needed for closed on a first visit, and watches for one. */
    public static void install() {
        Views.addGate(new Views.Gate() {
            @Override
            public String closedWhy(View view) {
                if (!isWaiting() || !closes(view)) {
                    return "";
                }
                return I18n.format(isJournalHeld()
                        ? "gui.losttales.character.error.switch_journal_held"
                        : "gui.losttales.character.first.view_closed");
            }
        });
        FMLCommonHandler.instance().bus().register(new FirstVisit());
    }

    /** Whether a first visit keeps {@code view} closed: a custom view, or one a character is needed for. */
    static boolean closes(View view) {
        return view != null && (view.isCustom()
                || CLOSED.contains(view.category()));
    }

    /** Whether the player waits for their first character, or for an administrator to settle their last switch. */
    static boolean isWaiting() {
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        return roster != null && (roster.isWaitingForFirstCharacter()
                || roster.isJournalHeld());
    }

    /** Whether the player waits for an administrator to settle their last switch (CHAR-45). */
    static boolean isJournalHeld() {
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        return roster != null && roster.isJournalHeld();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.theWorld == null
                || minecraft.thePlayer == null) {
            return;
        }
        CharacterRosterSnapshot roster = ClientCharacterRosterCache.getSnapshot();
        if (roster == null || !roster.isWaitingForFirstCharacter()
                && !roster.isJournalHeld()) {
            screenWasOpen = false;
            return;
        }
        if (!roster.isStoreUnreadable()) {
            ticksToRetry = UNREADABLE_RETRY_TICKS;
        } else if (--ticksToRetry <= 0) {
            ticksToRetry = UNREADABLE_RETRY_TICKS;
            ClientCharacterNetwork.requestRoster();
        }
        GuiScreen current = minecraft.currentScreen;
        if (current == null) {
            if (screenWasOpen) {
                screenWasOpen = false;
                minecraft.displayGuiScreen(new GuiIngameMenu());
            } else if (roster.isWaitingForFirstCharacter()) {
                CharacterEditorPage.openFirst();
            } else {
                WindowScreen.openPage(CharactersPage.PAGE_ID);
            }
            return;
        }
        screenWasOpen = current instanceof WindowScreen;
    }

    /** Leaving the world ends the visit; the next begins afresh. */
    public static void clear() {
        ticksToRetry = UNREADABLE_RETRY_TICKS;
        screenWasOpen = false;
    }
}
