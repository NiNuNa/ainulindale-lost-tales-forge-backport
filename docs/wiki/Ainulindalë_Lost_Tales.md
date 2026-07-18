# Ainulindalë: Lost Tales

Ainulindalë: Lost Tales is a Minecraft Forge 1.7.10 mod that extends The Lord of the Rings Mod: Legacy. The backport adds roleplay characters, parties, quests, map and HUD tools, an accessory slot, third-person combat features, and new Middle-earth content.

The current release is `0.1.3`. It targets:

- Minecraft 1.7.10;
- Forge `10.13.4.1614`;
- LOTR Legacy Update `v36.15`;
- Geckolib Unofficial `1.0.4` (`geckolib3` API version `3.0.40`).

Both the client and dedicated server need the mod and its two dependencies. Lost Tales includes a coremod because some integrations cannot be implemented through Forge events alone. The integration is tied closely to the dependency versions listed above.

## Implemented systems

- Server-owned roleplay character creation, appearance, race physics, switching, deletion, and recovery.
- A world-wide catalogue of 82 unique lore characters. Every bundled entry has a validated appearance and can be claimed once per world.
- Persistent parties of up to four roleplay characters, with invitations, leadership, member colors, HUD status, tracking, go-here markers, and nearby shared quest-kill progress.
- Bundled multi-stage quests with prerequisites, rewards, journal entries, objective tracking, map markers, and repeatable or one-time completion.
- Dynamic missive boards and readable missive letters, including optional deadlines.
- A compass, quest tracker, party HUD, quick-loot preview, world markers, map-marker discovery, and LOTR map integration.
- An optional third-person camera with shoulder switching, camera collision, target lock, action targeting, projectile prediction, and server-authoritative ranged charge tiers.
- One dedicated ring accessory slot and One Ring concealment effects.
- New factions, waypoints, roads, biomes, NPCs, an Odane structure, items, weapons, armor, food, decorative blocks, containers, and Geckolib-rendered content.

## Not implemented

The long-term RPG direction does not mean every planned system exists. There is currently no class system, skill tree, government system, player-created faction system, subfaction system, general powers-and-abilities framework, or standalone custom status-effect framework. Their pages are kept only to make that boundary explicit.

## Documentation scope

These pages describe the current source and bundled resources. Experimental registry entries are marked as such. Registry names, packet identifiers, and saved-data formats are compatibility-sensitive and should not be renamed casually.

See [Known Limitations](Known_Limitations.md) before using this build in a long-running world.
