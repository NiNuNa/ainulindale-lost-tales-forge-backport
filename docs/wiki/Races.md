# Playable races

Lost Tales provides seven roleplay races. Race choice affects the player model, physical size, eye height, collision box, starting-faction choices, health, movement, and attack damage.

Combat values are normally derived from a representative LOTR Legacy NPC at runtime. The values below are safe fallbacks used when that integration cannot be probed.

| Race | Available genders | Size (width × height) | Health | Speed | Attack |
| --- | --- | ---: | ---: | ---: | ---: |
| Human | Male, female | 0.60 × 1.80 | 20 | 1.00 | 2 |
| Elf | Male, female | 0.60 × 1.80 | 20 | 1.00 | 2 |
| Dwarf | Male, female | 0.50 × 1.50 | 20 | 0.90 | 3 |
| Hobbit | Male, female | 0.45 × 1.20 | 16 | 1.00 | 2 |
| Orc | Unisex model | 0.50 × 1.55 | 20 | 1.00 | 3 |
| Uruk | Unisex model | 0.60 × 1.80 | 24 | 1.05 | 4 |
| Half-troll | Unisex model | 1.00 × 2.40 | 40 | 0.90 | 6 |

The Orc, Uruk, and Half-troll catalogues use the non-binary roleplay identifier because LOTR Legacy provides one compatible body catalogue for each of those models. This is a model-selection constraint, not a character-class system.

Human, Elf, Dwarf, Orc, Uruk, and Half-troll factions are filtered by their LOTR faction type. Hobbits can start with the Hobbit or Bree faction. A server-wide allow-list can narrow the catalogue, and a deny-list always takes priority.

Short and tall races use transformed camera height, hitbox, renderer scale, projectile origin, and mount-aware behavior. If a required transformer fails to apply, Lost Tales logs a warning and falls back safely; visual or hitbox alignment may then be imperfect.

There are no race skill trees, selectable racial powers, or in-game race-changing service. Those are possible future systems, not current features.

