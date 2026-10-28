package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.switching.CharacterSwitchCoordinator;
import com.ninuna.losttales.character.switching.CharacterSwitchSourceRestore;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.compat.discord.DiscordGameEventRelay;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.AchievementEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerPickupXpEvent;
import net.minecraftforge.event.world.BlockEvent;

/**
 * A player's first visit to a world, before they have made a character.
 * They wait in the character creator as a ghost: held where the world put
 * them, seen faintly by the others, untouchable and touching nothing,
 * earning nothing. They may read and write the out-of-character channels,
 * and type commands. The world does not announce them yet: their join
 * line and Discord's notice wait, and anything else naming them (a leave,
 * an achievement) is said to nobody. Once their first character is made
 * they are played as it at once, standing at its starting waypoint, and
 * the world announces them.
 *
 * <p>Whether a player waits is decided as the server reads their saved
 * data, before the game announces the join, from whether their roster
 * plays a character. A player whose characters the store cannot read
 * waits the same way (CHAR-01): the creator says the server cannot read
 * characters right now, and staff are told in the Server Log. Who waits is
 * kept for the session only, cleared with the server's other state.</p>
 *
 * <p>A player whose last switch waits for an administrator (a held
 * journal) is held the same way, as a ghost touching nothing, until an
 * operator settles it (CHAR-45); the world knows them, so their join and
 * their lines are said as anyone's.</p>
 */
public final class CharacterJoin {
    /** The players waiting, by account id. Touched on the server thread; read from any. */
    private static final Map<UUID, Waiting> WAITING =
            new ConcurrentHashMap<UUID, Waiting>();
    /** The same players by their account name, lower case, for the lines that name them. */
    private static final Map<String, UUID> NAMES =
            new ConcurrentHashMap<String, UUID>();
    /** Further than this from where they are held, a waiting player is put back. */
    private static final double HELD_SLACK_SQUARED = 0.01D;

    /** Whether the entity is a player still waiting for their first character. */
    public static boolean isWaiting(Entity entity) {
        Waiting waiting = entity instanceof EntityPlayerMP
                ? WAITING.get(entity.getUniqueID()) : null;
        return waiting != null && waiting.firstVisit;
    }

    /**
     * Whether the entity is a player held as a ghost: waiting for their
     * first character, or for an administrator to settle their last
     * switch (CHAR-45). A held player touches nothing and nothing touches
     * them.
     */
    public static boolean isHeld(Entity entity) {
        return entity instanceof EntityPlayerMP
                && WAITING.containsKey(entity.getUniqueID());
    }

    /** Whether the player is held because their last switch waits for an administrator (CHAR-45). */
    public static boolean isHeldForJournal(Entity entity) {
        Waiting waiting = entity instanceof EntityPlayerMP
                ? WAITING.get(entity.getUniqueID()) : null;
        return waiting != null && waiting.journalHeld;
    }

    /**
     * Why the player may not speak in character, as a lang key; null when
     * they may: one waiting for their first character, or held for their
     * last switch (CHAR-45).
     */
    public static String inCharacterRefusal(Entity entity) {
        if (isWaiting(entity)) {
            return "chat.losttales.identity.first_character";
        }
        return isHeldForJournal(entity) ? "chat.losttales.identity.journal_held" : null;
    }

    /**
     * Holds the player as a ghost while their account's switch journal
     * waits for an administrator, and lets them go once it does not
     * (CHAR-45): asked at each join, respawn and change of dimension, and
     * after an operator's recover or discard. An account whose entry the
     * switch store had to set aside is held too. A player let go plays as
     * their game mode allows again and is sent their roster; one newly
     * held is sent it too. Answers whether they are held for it now.
     */
    public static boolean settleJournalHold(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || player.getUniqueID() == null) {
            return false;
        }
        boolean held = CharacterSwitchSourceRestore.characterRemovalRefusal(
                player.worldObj, player.getUniqueID()) == CharacterErrorId.SWITCH_JOURNAL_HELD;
        Waiting waiting = WAITING.get(player.getUniqueID());
        boolean wasHeld = waiting != null && waiting.journalHeld;
        if (held) {
            if (waiting == null) {
                waiting = new Waiting();
                waiting.firstVisit = false;
                WAITING.put(player.getUniqueID(), waiting);
            }
            waiting.journalHeld = true;
            // Held where they stand now, after a respawn too.
            waiting.hold(player.posX, player.posY, player.posZ);
            player.capabilities.disableDamage = true;
            player.sendPlayerAbilities();
        } else if (wasHeld) {
            waiting.journalHeld = false;
            if (!waiting.firstVisit) {
                WAITING.remove(player.getUniqueID());
                player.theItemInWorldManager.getGameType()
                        .configurePlayerCapabilities(player.capabilities);
                player.sendPlayerAbilities();
            }
        }
        if (held != wasHeld) {
            sendRoster(player);
        }
        return held;
    }

    private static void sendRoster(EntityPlayerMP player) {
        try {
            CharacterRoster roster = CharacterStorage.get(player.worldObj)
                    .getRoster(player.getUniqueID());
            if (roster != null) {
                CharacterSyncManager.sendRoster(player,
                        CharacterSyncManager.UNSOLICITED_REQUEST_ID, roster);
            }
        } catch (RuntimeException unreadable) {
            // The roster request the page sends brings it.
        }
    }

    /** Whether the account of that name is still waiting for its first character. */
    public static boolean isWaitingAccount(String account) {
        return account != null
                && NAMES.containsKey(account.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Keeps a waiting player's join line, to be said once they join; the
     * game announces them before they could have made anyone.
     */
    public static void holdJoinLine(String account, IChatComponent line) {
        UUID id = account == null ? null
                : NAMES.get(account.trim().toLowerCase(Locale.ROOT));
        Waiting waiting = id == null ? null : WAITING.get(id);
        if (waiting != null) {
            waiting.joinLine = line;
        }
    }

    /**
     * The player's saved data has been read: they wait when their roster
     * plays no character, which is a first visit, or a visit after a first
     * character's making that did not finish.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLoadFromFile(PlayerEvent.LoadFromFile event) {
        if (event == null || !(event.entityPlayer instanceof EntityPlayerMP)
                || event.entityPlayer.worldObj == null
                || event.entityPlayer.worldObj.isRemote) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP)event.entityPlayer;
        Boolean noCharacter = playsNoCharacter(player);
        if (Boolean.FALSE.equals(noCharacter)) {
            return;
        }
        Waiting waiting = new Waiting();
        // Null: the store could not say, so the player waits as a ghost
        // until it can, rather than walking in as the account.
        waiting.storeUnreadable = noCharacter == null;
        WAITING.put(player.getUniqueID(), waiting);
        if (player.getGameProfile() != null
                && player.getGameProfile().getName() != null) {
            NAMES.put(player.getGameProfile().getName().trim()
                    .toLowerCase(Locale.ROOT), player.getUniqueID());
        }
    }

    /**
     * The player has logged in and their roster is settled: one waiting
     * whose first character was made on an earlier visit is played as it
     * now; anyone else waiting is held where the world put them, out of
     * harm's way.
     */
    static void arrive(EntityPlayerMP player, CharacterRoster roster) {
        Waiting waiting = player == null ? null : WAITING.get(player.getUniqueID());
        if (waiting == null || !waiting.firstVisit) {
            return;
        }
        waiting.storeUnreadable = false;
        if (roster != null && roster.getActiveCharacterId() != null) {
            endFirstVisit(player);
            return;
        }
        if (roster != null && !roster.getCharacters().isEmpty()
                && join(player, roster.getCharacters().get(0).getCharacterId())
                        .isSuccessful()) {
            return;
        }
        waiting.hold(player.posX, player.posY, player.posZ);
        player.capabilities.disableDamage = true;
        player.sendPlayerAbilities();
    }

    /**
     * Plays a waiting player as their first character, just made, and lets
     * the world announce them: the join line held for them goes out, then
     * Discord's notice, both unless they are Invisible as that character,
     * and everyone's channels follow. Answers the switch's result; a
     * refused one leaves them waiting.
     */
    static CharacterOperationResult join(EntityPlayerMP player, UUID characterId) {
        CharacterOperationResult result = CharacterSwitchCoordinator
                .getInstance().joinAs(player, characterId);
        if (!result.isSuccessful()) {
            FMLLog.warning("[%s] %s could not be played as their first character: %s",
                    LostTalesMetaData.MOD_ID, player.getUniqueID(),
                    result.getErrorId().getId());
            return result;
        }
        Waiting waiting = endFirstVisit(player);
        // What the game mode grants comes back with the ghost's hold gone.
        player.theItemInWorldManager.getGameType()
                .configurePlayerCapabilities(player.capabilities);
        player.sendPlayerAbilities();
        MinecraftServer server = MinecraftServer.getServer();
        if (waiting != null && waiting.joinLine != null && server != null
                && server.getConfigurationManager() != null) {
            server.getConfigurationManager().sendChatMsg(waiting.joinLine);
        }
        DiscordGameEventRelay.announceJoin(player);
        LostTalesChatService.sendAccessToAll(null);
        return result;
    }

    /**
     * Whether the player's roster plays no character; null where the store
     * cannot say: one of another version, or one that fails to read.
     */
    static Boolean playsNoCharacter(EntityPlayerMP player) {
        try {
            CharacterWorldData data = CharacterStorage.get(player.worldObj);
            if (data.isReadOnlyForNewerVersion()) {
                return null;
            }
            CharacterRoster roster = data.getRoster(player.getUniqueID());
            return Boolean.valueOf(roster == null || roster.getActiveCharacterId() == null);
        } catch (RuntimeException unreadable) {
            FMLLog.warning("[%s] Could not read the roster of %s on arrival: %s",
                    LostTalesMetaData.MOD_ID, player.getUniqueID(),
                    unreadable.toString());
            return null;
        }
    }

    /** Whether the player waits because the server cannot read their characters now. */
    public static boolean waitsOnUnreadableStore(EntityPlayerMP player) {
        Waiting waiting = player == null ? null : WAITING.get(player.getUniqueID());
        return waiting != null && waiting.storeUnreadable;
    }

    /**
     * The player has logged in and the server cannot read their characters:
     * they are held as a ghost where the world put them, told so in the
     * creator, and staff are told in the Server Log (CHAR-01).
     */
    static void arriveUnreadable(EntityPlayerMP player) {
        Waiting waiting = player == null ? null : WAITING.get(player.getUniqueID());
        if (waiting == null) {
            return;
        }
        waiting.storeUnreadable = true;
        waiting.hold(player.posX, player.posY, player.posZ);
        player.capabilities.disableDamage = true;
        player.sendPlayerAbilities();
        String account = player.getGameProfile() == null
                ? player.getCommandSenderName() : player.getGameProfile().getName();
        LostTalesChatService.console(ChatConsoleEvent.Kind.WARNING,
                ChatConsoleEvent.Severity.WARNING, "",
                account + " joined while the server cannot read characters; they wait"
                        + " as a ghost until the character store reads again");
    }

    /** The store reads again: a player held for it waits for their first character as anyone else. */
    static void readsAgain(EntityPlayerMP player) {
        Waiting waiting = player == null ? null : WAITING.get(player.getUniqueID());
        if (waiting != null) {
            waiting.storeUnreadable = false;
        }
    }

    private static Waiting forget(EntityPlayerMP player) {
        Waiting waiting = WAITING.remove(player.getUniqueID());
        NAMES.values().remove(player.getUniqueID());
        return waiting;
    }

    /**
     * The first visit is over; a hold for a journal stays. Answers what
     * the player waited with.
     */
    private static Waiting endFirstVisit(EntityPlayerMP player) {
        NAMES.values().remove(player.getUniqueID());
        Waiting waiting = WAITING.get(player.getUniqueID());
        if (waiting == null) {
            return null;
        }
        if (waiting.journalHeld) {
            waiting.firstVisit = false;
            return waiting;
        }
        return WAITING.remove(player.getUniqueID());
    }

    /** A player who leaves still waiting was never there; their join line goes with them. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLogout(PlayerLoggedOutEvent event) {
        if (event != null && event.player instanceof EntityPlayerMP) {
            forget((EntityPlayerMP)event.player);
        }
    }

    /** Held where they stand: a waiting player who moves is put back. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isHeld(event.player)) {
            return;
        }
        Waiting waiting = WAITING.get(event.player.getUniqueID());
        if (waiting == null || !waiting.held) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP)event.player;
        player.fallDistance = 0.0F;
        if (player.getDistanceSq(waiting.x, waiting.y, waiting.z)
                > HELD_SLACK_SQUARED) {
            player.playerNetServerHandler.setPlayerLocation(waiting.x,
                    waiting.y, waiting.z, player.rotationYaw,
                    player.rotationPitch);
        }
    }

    /** Untouchable: nothing hurts a waiting player, and they hurt nothing. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttacked(LivingAttackEvent event) {
        if (isHeld(event.entityLiving)
                || event.source != null && isHeld(event.source.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** No creature takes a waiting player for its target. */
    @SubscribeEvent
    public void onTargeted(LivingSetAttackTargetEvent event) {
        if (isHeld(event.target) && event.entityLiving instanceof EntityLiving) {
            ((EntityLiving)event.entityLiving).setAttackTarget(null);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEntityEvent event) {
        refuse(event.entityPlayer, event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        refuse(event.entityPlayer, event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteractWith(EntityInteractEvent event) {
        refuse(event.entityPlayer, event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        refuse(event.getPlayer(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        refuse(event.player, event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPickup(EntityItemPickupEvent event) {
        refuse(event.entityPlayer, event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPickupXp(PlayerPickupXpEvent event) {
        refuse(event.entityPlayer, event);
    }

    /** Nothing is earned while waiting: the world has not seen them yet. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAchievement(AchievementEvent event) {
        refuse(event.entityPlayer, event);
    }

    /**
     * Nothing is dropped either: the item goes back where it came from.
     * What finds no room there is the dropped item's still, and falls.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onToss(ItemTossEvent event) {
        if (!isHeld(event.player) || event.entityItem == null) {
            return;
        }
        ItemStack stack = event.entityItem.getEntityItem();
        if (stack != null && event.player.inventory.addItemStackToInventory(stack)
                && stack.stackSize <= 0) {
            event.setCanceled(true);
        }
    }

    private static void refuse(Entity player,
                               cpw.mods.fml.common.eventhandler.Event event) {
        if (isHeld(player) && event.isCancelable()) {
            event.setCanceled(true);
        }
    }

    /** Cleared with the server's other state as it starts and stops. */
    public static void clear() {
        WAITING.clear();
        NAMES.clear();
    }

    /** A player waiting: where they are held once placed, and their join line. */
    private static final class Waiting {
        /** Whether they wait for their first character; a held journal alone is not one. */
        volatile boolean firstVisit = true;
        /** Whether their last switch waits for an administrator (CHAR-45). */
        volatile boolean journalHeld;
        volatile boolean held;
        volatile double x;
        volatile double y;
        volatile double z;
        volatile IChatComponent joinLine;
        /** Whether they wait because the server cannot read their characters. */
        volatile boolean storeUnreadable;

        void hold(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.held = true;
        }
    }
}
