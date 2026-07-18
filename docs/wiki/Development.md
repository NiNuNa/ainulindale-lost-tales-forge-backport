# Development

## Requirements

- A Java 8 JDK. Gradle 4.6 and the legacy ForgeGradle toolchain are not compatible with current Java releases.
- The included Gradle wrapper.
- `libs/LOTRMod v36.15-deobf.jar`.
- `libs/geckolib-unofficial-1.7.10-1.0.4-deobf.jar`.

Dependency jars are local development inputs and are not redistributed by Lost Tales.

## Common tasks

From the repository root:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

The build targets Forge `10.13.4.1614` and Java 8. The release jar manifest loads `com.ninuna.losttales.core.LostTalesCorePlugin`. ForgeGradle run tasks pass the equivalent development property because a classes directory has no jar manifest.

Use `runClient` or `runServer` only with a prepared legacy Forge development environment and the required dependencies. A dedicated-server run also requires accepting Minecraft's EULA in its run directory.

## Source layout

- `src/main/java/com/ninuna/losttales`: common mod entry point, proxies, registries, content, integrations, networking, and gameplay systems.
- `src/main/java/com/ninuna/losttales/client`: client-only caches, camera code, render behavior, and input.
- `src/main/java/com/ninuna/losttales/character`: roleplay-character domain, validation, storage, switching, state components, lore ownership, and sync models.
- `src/main/java/com/ninuna/losttales/party`: party domain, storage, services, quest sharing, and synchronization.
- `src/main/java/com/ninuna/losttales/quest`: bundled and dynamic quest parsing, validation, progress, missives, events, and NBT.
- `src/main/java/com/ninuna/losttales/core`: bytecode transformations and compatibility hooks.
- `src/main/resources/assets/losttales`: language, models, animations, textures, sounds, shaders, quests, lore characters, markers, camera presets, and map resources.
- `src/test/java`: JUnit 4 unit and transformation tests.

## Compatibility conventions

- Keep registry names, entity IDs, biome IDs, packet discriminators, NBT keys, and public JSON IDs stable unless a migration is part of the same change.
- Put client-only imports and registration behind the client proxy.
- Perform world mutations on the server thread and derive state from the live server object instead of trusting packet fields.
- Bound every collection, string, NBT structure, stack, coordinate, and numeric value that crosses a trust boundary.
- Use public Forge or LOTR APIs first. Keep reflection and transformers narrow, version-tested, and fail-safe.
- Do not copy LOTR source code, textures, models, or other assets. Reference public APIs and resource locations from the required installation.
- The old `LostTales` class prefix is widespread and sometimes public. Avoid adding it where the package already supplies enough context, but do not mass-rename existing APIs without a compatibility plan.

Quest and marker JSON are bundled resources, not datapacks. Update their indexes, tests, language entries, and this documentation together.

