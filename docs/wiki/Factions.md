# Factions

Lost Tales extends LOTR Legacy's faction enum and related catalogues during startup. Active additions receive map information, relations, control zones, ranks, achievements, titles, and starting-character eligibility through the LOTR systems.

## Active additions

| Faction | LOTR category | Region |
| --- | --- | --- |
| Lossoth | Men | North |
| Moria Goblins | Orcs | West |
| Orocarni | Dwarves | East |
| Tharbad | Men | West |
| Moon Elves | Elves and Free Peoples | North |
| Sun Elves | Elves and Free Peoples | East |
| Odane | Men | East |
| Blue Goblins | Orcs | West |

Arnor, the Lost Tales Lothlórien entry, and the internal Neutral entry are currently inactive. They remain registered for source and save compatibility but are removed from normal player-facing LOTR faction lists and do not provide active progression.

The active factions use the normal LOTR alignment and pledge model. Lost Tales does not add a separate faction currency, shop, role hierarchy, election, government, player-created faction, or territorial-conquest system.

## Relations and territory

Selected default relations are registered between new factions and LOTR factions. Control zones use Lost Tales or public LOTR waypoints. Lossoth and Orocarni each have a dedicated waypoint at the center of their own map region; both keep the existing 175-block zone radius. The new waypoints have matching discoverable map-marker metadata and were appended after the existing waypoint registrations so older Lost Tales waypoint identifiers keep their order.

Orocarni progression uses Red Mountain Dwarf titles from Stonebearer through Lord or Lady of the Red Mountains. The map regions, relations, ranks, and zones are startup integration data and should be tested whenever the LOTR dependency changes.

## Content integration

The mod also adds faction-themed equipment, NPC spawns, map waypoints, roads, and biomes. Four Moria Goblin equipment conversions are appended to the LOTR Gundabad crafting table. Lost Tales does not call LOTR's global recipe initializer a second time, which avoids duplicate legacy recipes.
