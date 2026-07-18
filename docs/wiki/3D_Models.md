# 3D models

Lost Tales initializes Geckolib Unofficial during common startup and registers client renderers separately so a dedicated server never loads Minecraft client classes.

Geckolib resources currently support:

- the Moon Elf light-armor set;
- urn, amphora, and loutrophoros block entities;
- bear, fox, and Gandalf plushies;
- the watch-stone statue;
- the test lamp;
- development models for a test hammer and test armor.

Models, textures, and animation JSON files are bundled under the `losttales` resource domain. Block-entity animation state is derived from synchronized tile data. The feature does not replace every player or NPC with a Geckolib model; playable race rendering primarily adapts public LOTR Legacy player-compatible models.

The old documentation described broad animated player and creature systems that were not implemented. Those claims have been removed.

