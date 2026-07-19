# Map, HUD, controls, and third-person camera

Lost Tales adds client displays on top of server-owned quest, party, combat, and container state. Display settings are local to each client; the server decides what information the client is allowed to receive.

## Default controls

| Action | Default input |
| --- | --- |
| Open character menu | Caps Lock |
| Open quest journal | J |
| Toggle Lost Tales HUD | H |
| Open HUD placement editor | Left Alt + H |
| Quick-loot use | R |
| Modifier | Left Alt |
| Swap camera shoulder | C |
| Toggle target lock | Middle mouse |
| Cycle target left or right | Unbound |

All bindings can be changed in Minecraft's Controls menu. While looking at a supported quick-loot container, hold the Modifier key and use the mouse wheel to change the selected row. Press the Use key to ask the server to remove the selected full stack and drop it above the container.

## HUD

The client can display:

- a compass with discovered markers, eligible LOTR waypoints, hostile combat contacts, party members, and the party go-here marker;
- a four-member party status panel;
- a quick-loot container preview;
- pinned quest objectives;
- quest notification toasts;
- location-discovery banners and area-name notices;
- discovered and active quest markers in the world.

Open the placement editor, click a HUD box to select it, and drag it. Panels cannot cross the four-pixel screen margin. A panel snaps when its center comes within six scaled pixels of the horizontal or vertical screen axis. The background uses a ten-pixel grid with stronger fifty-pixel divisions. Press Escape to save and return. A selected panel can also be nudged one pixel with the arrow keys, or ten pixels while holding the Modifier key.

The editor includes every fixed-position Lost Tales panel: Compass, Party, Quick Loot, Quest Tracker, Quest Notifications, Location Discovery, and Area Name. World-space markers, the aiming reticle, projectile trajectory, and full-screen visual effects are not placement panels because their screen position is derived from the world, crosshair, or full display.

HUD positions use bounded percentage offsets. Quick Loot uses the full screen and existing right-side positions migrate automatically. Built-in placement presets remain available through configuration and `/losttales hud`: `default`, `lotr-safe`, `compact`, and `minimal`; `custom` preserves manual positions.

The `/losttales hud` operator helper only changes settings in an integrated server, where client and server share one process. A dedicated server refuses the command because it cannot edit a remote client's HUD configuration.

## Map markers and waypoints

The bundled resources define 291 static map markers across cities, towns, settlements, forts, camps, caves, and LOTR waypoint mappings. A marker can be hidden until discovered, pinned, shown on the compass, linked to quest stages, or used to unlock an associated public LOTR waypoint.

Lost Tales adds its overlay to LOTR's Middle-earth map and uses the normal LOTR biome or faction fast-travel region whenever possible. Fast travel remains subject to LOTR's own region and waypoint rules. Combat markers are transient, server-approved, and never saved as discovered locations.

Every bundled LOTR waypoint marker uses the same discovery and visibility rules, including the Sun Elves, Moon Elves, Odane, and Orocarni. Entering the biome associated with a waypoint unlocks its LOTR fast-travel region and reveals the marker on the map. Moving close enough discovers the exact location and stores that state on the server. Added faction waypoints use their territory's existing biome region; Odane uses its generated Odane biome region.

`/losttales mapmarker` provides permission-level-2 tools to inspect, discover, forget, track, or untrack markers for testing and administration.

## Quick loot

Quick loot works only for supported inventories in reach. The client requests a preview; the server re-resolves the block, distance, inventory, seal state, dimension, and slot before any mutation. A stale or fabricated slot request is ignored. Sealed urns can be displayed but cannot be emptied through quick loot.

The feature drops the selected full stack into the world above the container. It does not transfer items directly into the player's inventory.

## Third-person camera and combat

The optional third-person overhaul provides profile-based camera distance and offsets for standing, moving, sprinting, sneaking, swimming, riding, combat, aiming, and attacking. It includes smoothing, motion effects, shoulder switching, zoom, block collision, FOV changes, directional movement, target lock, camera-intent actions, and a projectile trajectory guide for compatible vanilla and LOTR weapons.

Target lock and trajectory rendering are client aids. Entity attacks, interactions, block actions, and ranged charge bonuses are checked again on the server for distance, line of sight, current item, timing, player state, target validity, and request rate.

After a compatible weapon reaches its normal full draw, the default server charge tiers begin at 10, 24, and 42 additional ticks. Their default damage multipliers are 1.12, 1.30, and 1.60. Their default velocity multipliers are 1.04, 1.09, and 1.16. Servers can disable or tune the system.

If a camera transformer does not match the installed game or dependency bytecode, the affected path falls back to vanilla behavior and logs a warning.
