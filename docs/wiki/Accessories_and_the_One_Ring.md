# Accessories and the One Ring

Lost Tales appends one ring slot to the player's normal container. The server owns the equipped stack and synchronizes a revisioned view to the client.

Compatible items are registered explicitly. The current catalogue accepts:

- the Lost Tales One Ring;
- LOTR Legacy gold, silver, mithril, Hobbit, and Dwarven rings.

Right-clicking the One Ring asks the server to swap it with the equipped ring. Normal container interaction, creative inventory behavior, death drops, cloning, character switching, and pick-block slot numbering have dedicated compatibility handling.

An invalid stored accessory is not silently trusted. The system tries to return rejected items to a safe player inventory path and records whether recovery was needed.

## One Ring effects

When equipped, the One Ring produces three server-derived effects:

- a wearer-only wraith-world visual effect;
- public concealment from normal rendering and targeting;
- server-side concealment for AI suitability, sight, collision targeting, player attack and interaction events, party and combat tracking, and LOTR map player locations.

The client cannot activate concealment by editing its local cache. Public effects are derived from the authoritative equipped slot and synchronized with a monotonic sequence.

Concealment depends on coremod hooks for some vanilla and LOTR paths. If a hook cannot be installed, Lost Tales logs the missing protection. Operators should treat such a warning as a compatibility failure before running a competitive multiplayer server.

The accessory registry is frozen after startup so another late mutation cannot change which items or effects are accepted while players are online.

