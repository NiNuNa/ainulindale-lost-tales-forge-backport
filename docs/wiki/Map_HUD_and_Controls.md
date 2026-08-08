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
| Create personal waypoint (map only) | C |
| Current location (map only) | R |
| Find location (map only) | F |
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

Markers whose icons overlap are drawn as one stack: a leading marker with up to two companions fanned out behind it, and a "+X more" label for anything the fan cannot show. Only markers of the same kind ever share a stack, so a quest objective, a party Go Here marker, a player waystone and a location never merge into each other. Some kinds never stack at all: a player's own and shared waypoints, quest objectives, and combat contacts are each placed or reported for their own reason and stay individually readable at every zoom.

A marker joins a stack by coming to rest on one particular marker, and that is what holds it: it stays while it still overlaps that marker and leaves the moment it does not, taking anything resting on it along. Zooming back in therefore takes a stack apart in the same order and at the same points that zooming out put it together. Whole stacks merge by their leading markers meeting, so a stack never reaches further than the icon it is drawn as, however many markers it has gathered.

The map zooms smoothly rather than in fixed steps, and grouping follows the icons as they are actually drawn: markers merge at the moment they overlap and separate at the moment they stop, instead of jumping to whatever the next zoom level would have looked like.

Every leading marker is drawn above every fan, so the markers meant to be read are never buried behind another stack's background sprites, and hover and click ownership follow that same order. Marker names sit above the artwork and the "+X more" summary below it, both the same distance out. Tooltips follow the pointer only: a click never leaves one behind. The map draws its own hover cards in the shared HUD colours rather than LOTR's, so a marker can carry a description as well as a name, and only the icon grows when it is highlighted.

Left-clicking empty map places the party Go Here marker. Once one exists it is shared with the party and is what everyone is walking towards, so moving it is asked for rather than assumed: clicking empty map again opens a confirmation offering Move It, Leave It or Remove It, and clicking the marker itself opens the same question without a destination to move to. Removing the marker therefore always takes an explicit answer. Placing it from the fast-travel popup's Place Marker action repositions it immediately, because that click already named the destination. The marker belongs to the player's active roleplaying character and is shared with their party; a player who has no character selected owns it themselves instead, so the map is usable before a character exists. The server resolves that owner independently for every request, and a character-less player is sent nothing but their own marker. Placement happens on release and only when the pointer has not moved, so dragging the map or clicking a marker never drops one by accident.

The map has one camera, and every action that moves it shares that camera. Left-clicking a stack frames it: the camera pans so the marker the stack is drawn as sits at the centre of the map, and zooms only as far as the same grouping rule says its members need to stand apart. Once the markers stand apart, clicking one reaches the marker itself. Opening a fast-travel confirmation frames its destination the same way, but at an anchor worked out from the popup's own layout: the marker comes to rest centred just above the panel, at any resolution or GUI scale, and the popup leaves an opening in the shade around it so it stays as readable as it was on the open map. Both movements pan and zoom on one eased progress and follow a path that leads along the axis the camera is mostly travelling on, so a movement downward sets off downward rather than sliding along a ruler. A second click retargets the movement from the point the camera is framing at that moment rather than restarting it. The map holds still while a popup is open, except for a focus the popup itself asked for.

Holding the right mouse button and dragging turns and leans the map. Sideways turns it, up to 22.5° either way; only horizontal movement counts, so a steep diagonal barely turns it. The turn is not linear: square has a detent, so leaving true north takes a deliberate movement and returning lands exactly on it, and resistance grows steeply with the angle — the last couple of degrees cost about nine times the drag the first ones did. Dragging down in the same gesture lowers the eye towards the sheet, through the same detent, the same drag distance and the same stiffening, so leaning and turning cost the same and reach their limits together: the near edge spreads outward and the far edge draws in, as though looking across the map rather than straight down at it. Dragging back up lays it flat again.

The map image, the paper grain over it and the region names written on it are all drawn through one matrix, so they turn and lean together as one sheet — the region names are ink on the paper and lean with it. What is pinned to a place rather than painted on it stays upright and readable: markers, their names, their hover cards and the fast-travel popup. Panning, zooming, marker placement, hit testing and grouping all use the same transform and its exact inverse, so what is under the pointer is what responds. The compass rose sits above the control strip and turns with the map, so it always shows where north now lies. Closing the map returns it to square.

Press `R` on the map to bring the camera back to where you are standing, and `F` to open Find Location: type part of a name and pick a place from the list to fly the camera to it. The list holds only what the player is allowed to be told about — places they have found, their own and shared waypoints, and their party's markers — so a location that has not been discovered cannot be found by name either.

The control strip names every map input as a key icon with a label, including the operator teleport key when the player is an operator; LOTR's own "Press 'M' to /tp" subtitle is filtered out so the action is named once rather than twice in two styles. The fullscreen map drops LOTR's fullscreen, zoom-in, zoom-out and create-waypoint widgets: the map is always fullscreen here, zooming is continuous and driven by the mouse wheel, and waypoints are created from the Lost Tales popup instead.

Press `C` on the map to create a personal waypoint; the map's control strip shows the binding. The popup takes a name, an optional description and an icon colour, then hands the name to LOTR's own create-waypoint request, so LOTR still places the waypoint at the player's position and still enforces its name rules and per-player limit. Clicking one of your own waypoints reopens that popup as its editor: rename it, recolour it, change its description, travel to it, delete it, or toggle which of your fellowships it is shared with. The editor works on the name you typed rather than the "(Custom)" one LOTR displays, which is also the key the colour and description are stored under — editing under the displayed name saved both somewhere nothing read. Renaming, deleting and sharing all go through LOTR's own requests, so ownership and permissions are enforced by the server exactly as before, and the share rows always show LOTR's own state. A waypoint shared with you by someone else opens the ordinary fast-travel popup instead, because it is not yours to change. Personal and shared waypoints are drawn with the Lost Tales personal marker icon and are labelled `Personal:` or `Shared:` rather than carrying a "(Custom)" suffix. The colour and description are client-side presentation only: they are stored in `customWaypointColors` and `customWaypointNotes` in the Lost Tales config, keyed by the waypoint's own name, and are never sent to the server or to other players.

A place is called whatever the bundled marker definition calls it, on the map and on the compass alike, so the two can never disagree about the same location. The names of markers mapped onto LOTR waypoints are the names LOTR itself uses, because that is what is written in those files.

A location that has not been discovered yet keeps its position and gives up everything else: a question mark on the map, a hover card that says only that it has not been found, and a travel popup that names it no more precisely. One rule decides that for all three, so the name and description cannot leak through whichever of them is drawn.

Clicking an eligible destination — a map marker or a LOTR waypoint — opens the fast-travel confirmation. Standing at a waystone travels through the waystone; otherwise the request goes through LOTR's own fast travel. A destination that cannot be used right now still opens the popup with the reason on it. Discovery, region unlocks, cooldowns and destination safety are re-checked on the server for every request, whichever route it took.

`/losttales mapmarker` provides permission-level-2 tools to inspect, discover, forget, track, or untrack markers for testing and administration.

## Quick loot

Quick loot works only for supported inventories in reach. The client requests a preview; the server re-resolves the block, distance, inventory, seal state, dimension, and slot before any mutation. A stale or fabricated slot request is ignored. Sealed urns can be displayed but cannot be emptied through quick loot.

The feature drops the selected full stack into the world above the container. It does not transfer items directly into the player's inventory.

## Third-person camera and combat

The optional third-person overhaul provides profile-based camera distance and offsets for standing, moving, sprinting, sneaking, swimming, riding, combat, aiming, and attacking. It includes smoothing, motion effects, shoulder switching, zoom, block collision, FOV changes, directional movement, target lock, camera-intent actions, and a projectile trajectory guide for compatible vanilla and LOTR weapons.

Target lock and trajectory rendering are client aids. Entity attacks, interactions, block actions, and ranged charge bonuses are checked again on the server for distance, line of sight, current item, timing, player state, target validity, and request rate.

After a compatible weapon reaches its normal full draw, the default server charge tiers begin at 10, 24, and 42 additional ticks. Their default damage multipliers are 1.12, 1.30, and 1.60. Their default velocity multipliers are 1.04, 1.09, and 1.16. Servers can disable or tune the system.

If a camera transformer does not match the installed game or dependency bytecode, the affected path falls back to vanilla behavior and logs a warning.
