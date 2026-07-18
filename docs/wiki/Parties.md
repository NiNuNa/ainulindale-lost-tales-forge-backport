# Parties

A party is a persistent server-owned group of up to four active roleplay characters. Party membership belongs to the character, not to every character on the same Minecraft account.

## Player features

- Create a party, invite a player, accept or decline an invitation, leave, or disband.
- Transfer leadership and assign one unique display color to each member.
- View health and connection status in the party HUD.
- Track party members on the compass and in the world when server rules allow it.
- Place a temporary go-here marker for the group.
- Share eligible nearby Lost Tales kill-objective progress. The default range is 32 blocks.

Party actions are available from the character interface and synchronized with revisioned snapshots. Invitations expire and are validated against the invited account and active character. A stale client cannot overwrite a newer party state.

## Server behavior

The server owns leadership, membership, colors, invitations, tracking visibility, status data, and shared quest credit. Status and position updates are rate-limited and sent only to relevant party members. One Ring concealment and other visibility rules can suppress tracking.

If a leader leaves, the persistent model selects a successor deterministically. Invalid saved parties are rejected or repaired through controlled server paths rather than trusted as-is. Administrators can inspect, validate, repair, or clear combat state with `/losttales party`.

Parties do not provide shared inventories, loot ownership, faction membership, voice chat, guild progression, or player-versus-player protection.

