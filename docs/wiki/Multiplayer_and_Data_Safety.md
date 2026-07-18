# Multiplayer and saved-data behavior

Lost Tales is designed around dedicated-server authority. The client supplies requests and presentation choices; the server resolves the current player, world, character, inventory, target, quest, party, and accessory state before changing anything.

## Networking

The network channel currently registers 31 fixed packet discriminators. Existing discriminator numbers and valid wire layouts are compatibility-sensitive.

Client-to-server requests are scheduled on the server thread and pass through request-type rate limits. Operations check relevant conditions such as dimension, reach, line of sight, lifecycle state, ownership, expected revision, current item, slot range, and active character. Server-to-client packets are decoded with bounded counts, strings, stacks, positions, and numeric values before their work is queued on the client thread.

Malformed, truncated, oversized, non-finite, or trailing packet data is rejected. A packet failure must not partially update a client cache or mutate the world.

## Persistent data

World data is versioned and validated before mutation. This includes character rosters and snapshots, switching journals, deleted-character tombstones, lore ownership and transfer journals, parties and invitations, dynamic missives, quests, and map-marker progress.

When data is newer than the running mod or structurally unsafe, the normal policy is fail-closed: preserve it, make the affected store read-only or block that owner, log the reason, and require administrative recovery. The mod avoids silently truncating a list because truncation could lose ownership or progression.

Character switching uses last-known-good snapshots, durable journals, lifecycle epochs, periodic checkpoints, and recovery commands. Death-pending state prevents a reconnect from restoring a pre-death inventory after drops have already occurred.

## Visibility and privacy

Party status, location tracking, combat contacts, and One Ring concealment are derived by the server. The client only receives information relevant to its active party and allowed view. Hostile markers represent current server-observed aggression and are not permanent radar data.

## Deployment guidance

- Install the same Lost Tales, LOTR Legacy, and Geckolib versions on every client and the server.
- Back up a world before changing mod versions, registry IDs, or dependency builds.
- Treat coremod transformer warnings as compatibility problems, especially warnings about character state, player containers, concealment, or action targeting.
- Keep the server clock stable because invitations, cooldowns, tombstone retention, and timed missives use elapsed time.
- Test login, death, respawn, dimension travel, character switching, party reconnects, and shutdown recovery in a staging copy before deploying an update.

The automated suite covers packet validation, storage codecs, switching rules, coremod transformations, accessories, parties, quests, maps, and camera math. It does not replace a real multi-client dedicated-server smoke test.

