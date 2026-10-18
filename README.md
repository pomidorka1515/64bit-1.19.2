# 64-bit Minecraft. It's real!

A playable Minecraft 1.19.2 where horizontal coordinates are `long`, not `int` or a lossy `double`. 
The world border is gone. X/Z run to the ends of a signed 64-bit integer, and movement, collision, rendering, sound, and terrain no longer fall apart once doubles stop being able to count whole blocks.

The original, incomplete mod was made by mckuhei. Their sites are now down. The pre-fork code is commit `f1b40fcf28655ebf01aa5dd77bc8e0cdc9dc97af`. That version does not really run. Use this fork.

## Key features

- **A world the size of a `long`.** Block X/Z go from `Long.MIN_VALUE` to `Long.MAX_VALUE`. Y stays the normal build height. Chunk and region math saturates instead of wrapping to the opposite edge.
- **Exact positions past the double limit.** `SectorVec3` stores X/Z as a block coordinate plus a fraction in `[0, 1)`. Physics never subtracts two huge world doubles.
- **Collision that still works out there.** Movement is solved in a small local frame, then applied back to the exact position. This includes the last representable block, not just "pretty far".
- **Every entity and block entity is exact.** Players, mobs, vehicles, projectiles, and block entities keep a real position. Spawn, AI, pathfinding, riding, and dismount use that position.
- **Camera-relative client.** Terrain, block entities, particles, explosions, the sky, clouds, and audio are drawn and heard relative to the camera, so the view does not jitter into noise.
- **Five terrain modes, saved with the world.** `/farlands` switches them. Confirm with `/farlands confirm`.
  - `32bit` — original mod behavior. Far Lands at 12,550,824.
  - `32bit-hybrid` — those classic Far Lands, but the noise router, caves, aquifers, and ores use 64-bit math.
  - `64bit` — 64-bit generator. Far Lands show up where double noise loses integer steps, around 2^53.
  - `off` — same 64-bit generator with the Far Lands patched out.
  - `64bit-precise` — like `off`, but noise keeps an exact lattice past the 53-bit double limit.
- **End rings are optional.** `/endrings` turns the outer End rings on or off. Disabling asks for `/endrings confirm`.
- **Debug that tells the truth at range.** F3 shows precision, separates block and region coordinates, and prints the generator mode. `/wraprouter` dumps NoiseRouter overflow. `/frustum` toggles frustum culling when the cull is wrong.

## What was rewritten

Vanilla's `int` positions and `double` physics were not patched in one place. These systems were replaced or rewritten so they never build a huge world X/Z double.

### Coordinates and world limits

- `Vec3i`, `BlockPos`, `ChunkPos`, and `SectionPos`: X/Z are `long`. Y stays `int`.
- `WorldBounds`: one set of block, chunk, and region limits, with saturating add/multiply so an edge request cannot overflow into the other side of the world.
- Chunk tickets, chunk cache, region-file indexing, and "chunk out of bounds" handling.
- The world border. It is removed, not pushed outward.
- Level save/load. Exact positions are stored as split coordinates. They used to be written back out of the lossy doubles.

### Movement and collision

- `SectorVec3`, `SectorAABB`, and `SectorPhysicsOrigin`. Exact position, exact box, one fixed local origin per collision solve.
- `SectorBlockCollisions`. Block shapes are translated by a small origin-relative offset. They are not moved by a global double.
- Entity `move()`, stepping, fluids, suffocation, and edge back-off for sector entities.
- `SectorClipper`. Block raycasts walk long block addresses. Hit positions stay exact.
- Block placing, breaking, outlines, and the break animation.
- Boundary collision, including the last `2^63` block. The old failure mode was quicksand.

### Entities, AI, and vehicles

- `Entity` exact position, old position, eye position, and distance checks. Legacy `getX()` / `getZ()` remain approximate compatibility mirrors.
- Every entity type, not just the player. Spawn eggs and natural spawning included.
- Every block entity.
- Mob AI targeting, look goals, and villager pathfinding. Navigation nodes use exact coordinates.
- Boats, minecarts, horses, and dismount. Dismount offsets are split-coordinate math, not `double` subtraction.
- Projectiles, explosions, experience orbs, and container drops.
- Entity interaction, which the original mod only half-fixed.

### Networking

- Relative movement codec (`VecDeltaCodec`). Deltas apply to the last exact network base, not an interpolated render position.
- Player movement packets on the server. The tick baseline is the exact position.
- World-boundary checks at the packet layer, so a bad coordinate cannot ask for a chunk that does not exist.
- Sound, particle, and explosion packets. They carry positions that survive the trip to the client.

### Terrain

- Noise itself: improved, Perlin, simplex, blended, and normal noise, plus the noise router and density functions.
- `PreciseNoiseCoordinate`. One 64-by-53-bit product, split into a lattice and a fraction, so the precise generator does not collapse onto a chunk grid past 2^53.
- `FarlandsMode`, including the old boolean save format.
- Surface rules and `SurfaceSystem`.
- Ores, ore veins, and heightmaps.
- Structures and jigsaw placement, including mineshafts. Structure starts used to crash on the boundary with no log.
- `WorldGenRegion` cache indexing. Bad indexes deadlocked the server thread.
- Nether generation and the Nether sky.
- End generation, end islands, and the end-ring toggle.
- Chunk stripelands. Fixed. The old stripe-land behavior is not the default.

### Lighting

- The lighting engine, so section coordinates do not overflow at the world edge.
- Light updates after a chunk reload. They used to flash a black outline.
- Grass and foliage tint. Boundary samples used to go dark.

### Rendering

- `LevelRenderer` and the camera. World rendering is camera-relative, and the render loop does not overflow its chunk walk.
- Block-entity poses. The long block position is subtracted before it hits the float matrix stack.
- The skybox. It was a flat color; it is a real per-dimension sky again, with long-safe world math.
- Clouds.
- Particles, including the particle limiter, which now runs in camera space.
- Frustum culling. The old cull both froze the game and could pin hundreds of thousands of chunks in memory.
- F3 and F3+G chunk borders.

### Audio

- `SoundEngine`. A world sound is converted into the listener's sector before it is queued for OpenAL. UI sounds stay local.

### Commands

- Teleport and the other position commands. They move sector positions instead of adding to `getX()` / `getZ()`.
- `/farlands`, `/endrings`, `/frustum`, and `/wraprouter`. These did not exist in vanilla.

## Playing this

- Download the jar from the [latest release](https://github.com/pomidorka1515/64bit-1.19.2/releases).
- Install [Prism Launcher](https://prismlauncher.org) if you do not have it.
- Create a **vanilla** 1.19.2 instance.
- Open its settings, then Version.
- Add to Minecraft.jar, and select the jar you downloaded.

In game, `/farlands help` prints the generator modes. A mode change is saved with the world and only applies after `/farlands confirm`.

## Source

### Building the client and dedicated server

With Java 17 installed, run:

```bash
./gradlew jar
```

This builds both artifacts from `src/main/`:

- `build/libs/dev.jar` — the client jar for the launcher instructions above.
- `build/libs/dev-server.jar` — a standalone dedicated server, including runtime libraries and vanilla data resources.

If `GIT_COMMIT` is set, its value replaces `dev` in both filenames.
To build only the dedicated server, use `./gradlew serverJar`.

Copy the server jar into your server directory and launch it with Java 17:

```bash
java -Xmx2G -jar dev-server.jar nogui
```

On first launch, review `eula.txt` and set `eula=true` if you agree to the Minecraft EULA, then launch again. Server configuration and worlds are stored in the current working directory. Multiplayer clients need the matching modified client jar because the coordinate protocol differs from vanilla.

If new terrain takes a long time to load and then arrives in a burst, set `sync-chunk-writes=false` in `server.properties` and restart the server. The dedicated server defaults to synchronous region-file writes, which can hold up chunk generation on slower storage. The internal server defaults to asynchronous writes on Linux. Asynchronous writes improve throughput but can lose recent saves if the OS crashes or power is lost.

## Original mod history

Backport status of mckuhei's releases. `[+]` is in this fork. `[+/-]` was only partly fixed upstream and is rewritten here.

```
==== history ====
1.0.0:
      Inital release [+]
=================
1.0.1:
      Add floating point precision display. [+]
      Fix "No chunk holder after ticket has been added" exception [+]
=================
1.0.2: // original source code is based on this version
      Removed world border. [+]
      Add a patch to fix chunk stripe lands. [+]
      Add a patch to fix Frustum cause game froze. [+]
      Add a patch to fix 'chunk out of bound' exception. [+]
=================
1.0.3:
      Add a patch to restore farlands. [+]
=================
1.0.4:
       Fix game stuck at "Saving level..." screen. [+]
       Fix cannot interact with entity. [+]
=================
```
