# Roleplay characters

Each Minecraft account owns a roster of roleplay characters. The server owns the roster and validates every create, select, edit, delete, restore, and lore-character request.

Press **Caps Lock** by default to open the character menu.

## Creation and appearance

A new roster starts with one unlocked slot and can contain at most nine characters. Slot unlocking exists in the data model, but ordinary gameplay does not currently provide a documented unlock progression.

Creation includes:

- name, description, and age;
- one of the supported races and compatible genders;
- a starting LOTR faction allowed by the race and server configuration;
- a server-approved skin from LOTR Legacy;
- an optional LOTR cosmetic cape.

Names are limited to 32 characters, descriptions to 256 characters, and age to a validated non-negative value. Stable identifiers are stored instead of client-supplied resource paths. Lost Tales references compatible models, skins, and capes from the installed LOTR mod; it does not redistribute those assets.

The active character name is used in compatible chat, death-message, bounty, and display paths while the Minecraft account UUID remains the authoritative player identity.

## Per-character state

Switching does more than change the model. The following state is captured and restored separately for each character:

- inventory and ender chest;
- health, food, absorption, experience, potion effects, and spawn point;
- current dimension and location;
- vanilla statistics and achievements;
- the Lost Tales accessory slot and quest progress;
- LOTR alignment, faction counters, pledge, achievements, title, miniquests, bounties, visited fast-travel regions, waypoint use counts, custom waypoints, shield, alcohol tolerance, and last-death marker.

The first character imports the account's live state once. Later characters begin from validated defaults. Switching is blocked while dead, in combat, teleporting, fast-travelling, moving unsafely, or during another player lifecycle transition.

Switch cooldowns escalate after repeated changes. The default stages are 1, 3, 5, 15, 30, and 60 minutes and decay after increasing periods of inactivity. Servers can configure these values.

## Durability and recovery

Character switches use a transaction journal. The active state is checkpointed periodically, on logout, during server shutdown, and at important lifecycle events. The server can reconcile an interrupted switch after a crash instead of guessing which snapshot is current.

Malformed, oversized, incomplete, or newer-version character data is rejected or made read-only. A failed validation keeps the last known-good snapshot. Administrative recovery, rollback, freeze, and deletion-restore commands are available under `/losttales character`.

Deleted characters are retained as tombstones for 30 days by default. Purging or changing that retention is an administrative action.

## Lore characters

The bundled catalogue contains 82 named lore definitions. A lore character is unique across the world, not per account. Claim, release, deletion, and transfer operations use server-owned world data so two players cannot own the same identity.

All 82 bundled definitions include complete appearance data and can be offered for use. Each appearance is validated against the server's supported race, gender, model, and LOTR skin catalogue before the definition is registered. Characters without an equivalent playable body, such as Smaug and Durin's Bane, use the closest supported player-model approximation; they do not use the original creature model.

Server-local definitions may still use `appearance: null` while they are being prepared. They remain visible to validation tools but cannot be claimed until a complete compatible appearance is supplied.

## Compatibility rules

- The old saved race ID `losttales:troll` migrates to `losttales:half_troll`; a full Troll is not a playable race.
- Existing character, registry, and lore IDs must remain stable for world compatibility.
- Half-troll equipment is restricted to compatible LOTR half-troll armor. Rejected equipment is returned safely rather than silently deleted.
- Parties identify members by character UUID, while ownership and networking still use the Minecraft account UUID.
