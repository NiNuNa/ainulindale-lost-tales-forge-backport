package com.ninuna.losttales.world.room;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;

/**
 * Keeps a character room a room: the player stands where the layout says,
 * nothing spawns, the clock and the weather hold still, and no block can
 * be broken or placed.
 *
 * <p>The room is entered in adventure mode with an empty inventory, which
 * already stops the player from changing it; the guards here hold if
 * either of those is ever not the case. Every check keys on the world's
 * type, so an ordinary world is never touched.</p>
 */
public final class CharacterRoomWorldHandler {

    /** Midday, so the sky's colour and the ambient light are constant. */
    private static final long NOON = 6000L;
    /** Far enough off that the weather never changes while the room is open. */
    private static final int WEATHER_HOLD_TICKS = Integer.MAX_VALUE / 2;

    /** The layout places the spawn; vanilla's biome search never runs. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCreateSpawnPosition(WorldEvent.CreateSpawnPosition event) {
        if (event == null || !CharacterRoomWorldType.isRoom(event.world)) {
            return;
        }
        event.world.getWorldInfo().setSpawnPosition(CharacterRoomLayout.SPAWN_X,
                CharacterRoomLayout.SPAWN_Y, CharacterRoomLayout.SPAWN_Z);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event == null || event.world == null || event.world.isRemote
                || event.world.provider == null
                || event.world.provider.dimensionId != 0
                || !CharacterRoomWorldType.isRoom(event.world)) {
            return;
        }
        holdStill(event.world);
    }

    private static void holdStill(World world) {
        GameRules rules = world.getGameRules();
        if (rules != null) {
            rules.setOrCreateGameRule("doMobSpawning", "false");
            rules.setOrCreateGameRule("doDaylightCycle", "false");
            rules.setOrCreateGameRule("doFireTick", "false");
            rules.setOrCreateGameRule("mobGriefing", "false");
        }
        world.setWorldTime(NOON);
        WorldInfo info = world.getWorldInfo();
        if (info != null) {
            info.setRaining(false);
            info.setThundering(false);
            info.setRainTime(WEATHER_HOLD_TICKS);
            info.setThunderTime(WEATHER_HOLD_TICKS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event != null && CharacterRoomWorldType.isRoom(event.world)) {
            event.setCanceled(true);
        }
    }

    /** Both clicks on a block, on both sides, so nothing is placed or dug. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null || event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR) {
            return;
        }
        World world = event.world != null ? event.world
                : event.entityPlayer != null ? event.entityPlayer.worldObj : null;
        if (CharacterRoomWorldType.isRoom(world)) {
            event.setCanceled(true);
        }
    }
}
