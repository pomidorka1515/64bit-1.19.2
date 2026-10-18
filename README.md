# 64-bit minecraft

a playable minecraft 1.19.2 where horizontal coordinates are `long`, not `int` and not a lossy `double`.

the world border is gone. X/Z run to the very ends of a signed 64-bit integer, and movement, collision, rendering, sound and terrain gen keep working long after doubles stop being able to count whole blocks.

the original unfinished mod was written by mckuhei, whose sites are dead now. code before the fork is commit `f1b40fcf28655ebf01aa5dd77bc8e0cdc9dc97af` (it barely works)

## highlights

- a world the size of a `long`. block X/Z span `Long.MIN_VALUE` to `Long.MAX_VALUE`, Y keeps the usual build height. chunk and region math saturates instead of wrapping around to the far side of the world.
- positions stay lossless past the double limit. `SectorVec3` keeps X/Z as a block coordinate plus a fraction in `[0, 1)`, so physics never subtracts two gigantic world doubles.
- collision still works out there. movement gets solved in a tiny local frame, then applied back to the real position — including on the last representable block, not just "pretty far out".
- every entity and block entity keeps a true position: players, mobs, vehicles, projectiles, all of them. spawning, AI, pathfinding, riding and dismounting use it.
- camera-relative client. terrain, block entities, particles, explosions, sky, clouds and audio are drawn and heard relative to the camera, so the view doesn't dissolve into jitter.
- five terrain modes, stored with the world. `/farlands` switches them, `/farlands confirm` applies.
  - `32bit` — how the original mod behaved. far lands at 12,550,824.
  - `32bit-hybrid` — the classic far lands, but the noise router, caves, aquifers and ores run on 64-bit math.
  - `64bit` — 64-bit generator. far lands show up where double noise loses integer steps, around 2^53.
  - `off` — same generator with the far lands patched out.
  - `64bit-precise` — like `off`, but noise keeps an exact lattice past the 53-bit double limit.
- outer end rings are optional. `/endrings` flips them on or off; turning them off asks for `/endrings confirm`.
- debug that isn't lying to you at range. F3 shows precision, splits block vs region coords, and prints the generator mode. `/wraprouter` dumps NoiseRouter overflow. `/frustum` toggles frustum culling when the cull turns out wrong.

## what got redone

vanilla's `int` positions and `double` physics couldn't be patched in one spot. these systems were swapped out or rebuilt so a huge world X/Z double never gets built in the first place.

### coords and world limits

- `Vec3i`, `BlockPos`, `ChunkPos`, `SectionPos`: X/Z are `long`, Y stays `int`.
- `WorldBounds`: one set of block, chunk and region limits, with saturating add/multiply so an edge request can't overflow into the other side of the world.
- chunk tickets, chunk cache, region-file indexing, "chunk out of bounds" handling.
- the world border — removed, not just pushed further out.
- level save/load. true positions are stored as split coordinates; they used to be written back from the lossy doubles.

### movement and collision

- `SectorVec3`, `SectorAABB`, `SectorPhysicsOrigin`: lossless position, lossless box, one fixed local origin per collision solve.
- `SectorBlockCollisions`: block shapes get shifted by a small origin-relative offset, not by a global double.
- entity `move()`, stepping, fluids, suffocation, edge back-off for sector entities.
- `SectorClipper`: block raycasts walk long block addresses, hit positions stay true.
- block placing, breaking, outlines, the break animation.
- boundary collision, the last `2^63` block included. it used to behave like quicksand.

### entities, ai, vehicles

- `Entity` position, previous position, eye position, distance checks. legacy `getX()` / `getZ()` stay behind as approximate compatibility mirrors.
- every entity type, not just the player — spawn eggs and natural spawning included.
- every block entity.
- mob targeting, look goals, villager pathfinding. navigation nodes carry real coordinates.
- boats, minecarts, horses, dismounting. dismount offsets are split-coordinate math rather than `double` subtraction.
- projectiles, explosions, xp orbs, container drops.
- entity interaction, which the old mod only half fixed.

### networking

- relative movement codec (`VecDeltaCodec`): deltas apply to the last true network base, not an interpolated render position.
- player movement packets on the server: the tick baseline is the real position.
- world-boundary checks at the packet layer, so a bogus coordinate can't ask for a chunk that doesn't exist.
- sound, particle and explosion packets carry positions that survive the trip to the client.

### terrain

- the noise itself: improved, perlin, simplex, blended and normal noise, plus the noise router and density functions.
- `PreciseNoiseCoordinate`: one 64-by-53-bit product, split into a lattice and a fraction, so the precise generator doesn't collapse onto a chunk grid past 2^53.
- `FarlandsMode`, old boolean save format included.
- surface rules and `SurfaceSystem`.
- ores, ore veins, heightmaps.
- structures and jigsaw placement, mineshafts among them. structure starts used to crash on the boundary with no log.
- `WorldGenRegion` cache indexing. bad indexes deadlocked the server thread.
- nether generation and the nether sky.
- end generation, end islands, the end-ring toggle.
- chunk stripelands — fixed. stripe-land behavior is no longer the default.

### lighting

- the lighting engine, so section coords don't overflow at the world edge.
- light updates after a chunk reload; they used to flash a black outline.
- grass and foliage tint — boundary samples used to go dark.

### rendering

- `LevelRenderer` and the camera. world drawing is camera-relative, and the render loop no longer overflows its chunk walk.
- block-entity poses: the long block position is subtracted before it reaches the float matrix stack.
- the skybox used to be a flat color; it's a proper per-dimension sky again, with long-safe world math.
- clouds.
- particles, the particle limiter included, which now runs in camera space.
- frustum culling. the old cull froze the game and could pin hundreds of thousands of chunks in memory.
- F3 and F3+G chunk borders.

### audio

- `SoundEngine`: a world sound gets moved into the listener's sector before it's queued for OpenAL. UI sounds stay local.

### commands

- teleport and the other position commands move sector positions instead of adding to `getX()` / `getZ()`.
- `/farlands`, `/endrings`, `/frustum`, `/wraprouter`.

## installing

- grab the jar from the [latest release](https://github.com/pomidorka1515/64bit-1.19.2/releases)
- get [prism launcher](https://prismlauncher.org) if you don't have it already
- make a **vanilla** 1.19.2 instance
- open its settings, then Version
- "Add to Minecraft.jar" and pick the jar you downloaded

in game, `/farlands help` lists the generator modes. mode changes are saved with the world and only kick in after `/farlands confirm`.

## source

### building the client and dedicated server

with java 17 installed:

```bash
./gradlew jar
```

both artifacts come out of `src/main/`:

- `build/libs/dev.jar` — the client jar for the launcher steps above
- `build/libs/dev-server.jar` — a standalone dedicated server, runtime libraries and vanilla data resources included

if `GIT_COMMIT` is set, its value replaces `dev` in both filenames.
want just the server? `./gradlew serverJar`.

drop the server jar into your server folder and start it with java 17:

```bash
java -Xmx2G -jar dev-server.jar nogui
```

on first boot, read `eula.txt` and set `eula=true` if you're okay with the minecraft EULA, then start it again. server config and worlds live in the current working directory. multiplayer clients need the matching modified client jar, since the coordinate protocol differs from vanilla.

if fresh terrain takes ages to load and then shows up all at once, put `sync-chunk-writes=false` in `server.properties` and restart. the dedicated server defaults to synchronous region-file writes, which can hold up chunk generation on slower drives. the internal server defaults to asynchronous writes on linux. async writes are faster but can lose recent saves if the OS crashes or the power cuts out.

## original mod history

how mckuhei's releases carried over into this fork. `[+]` means it's in here, `[+/-]` was only partly fixed upstream and is redone here.

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
