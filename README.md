# 1088-bit minecraft

minecraft 1.19.2 with signed 1088-bit horizontal coordinates (`Int1088`), not `int`, `long`, or a lossy `double`. compilation and automated coordinate gates pass; live wide-client rendering/audio verification and final acceptance are still pending.

the world border is gone. block X/Z span `-2^1087` through `2^1087 - 1`; Y keeps the usual build height. exact positions, storage, networking, camera-relative math and terrain generation consume the full coordinate.

## command coordinates

world X/Z inputs (including `~` offsets, 2D positions and selector `x/z/dx/dz`) accept either a scientific literal such as `5.123456e+306` / `-223.293E302`, or a no-whitespace arithmetic expression. scientific notation cant be mixed with arithmetic in the same coordinate.
Y, rotations and local `^` coordinates keep their existing syntax

- expressions support decimal literals, parentheses, unary `+/-`, `+ - * / // ^ ^^`; multiplication must be explicit (`1(10)` is invalid).
- `/` is exact division; `//` floors toward negative infinity
- precedence, highest first: parentheses, `^^`, `^`, unary signs, `* / //`, `+ -`. oowers and tetration associate rightward
- out of bounds final coordinates fail during command parsing, making the command red in chat. Relative destinations are checked again when resolved
- evaluation is bounded: 4096 characters, 64 recursive parser levels, 1024 syntax operations, and 16384-bit intermediate numerator/denominator storage. oversized powers/towers fail with a complexity error, not an approximate result

## save break and worldgen boundary

- **new worlds only.** vanilla, old 64-bit fork and legacy 128-bit saves are rejected read-only, not migrated; there is no fallback decoder. matching 1088-bit client/server builds are required.
- save identity: storage version `0x4C534303`, series `mcp-int1088-coordinates`, marker `int1088-coordinates-v1`. the network protocol is `0x4C534303`; older clients are rejected.
- exact horizontal positions use an `Int1088` block coordinate plus a normalized double fraction in `[0, 1)`. chunk/section keys, region files and persisted placement identity retain all 17 sign-extended limbs. NBT stores a fixed `LongArray[17]` per axis; network coordinates use a significant-limb count (1–17) and zig-zag VarLongs.
- worldgen carries full-width horizontal limb views through density sampling, biomes, surfaces, aquifers and positional seeds. the precise mode scales full-width integers before reducing noise lattice indices; other modes retain their floating-point and Far Lands behavior.
- the five modes keep their existing signed-long sampling results. full-width sampling does not imply unique terrain at every address: noise permutations, finite seed spaces, deliberate clamps, floating-point precision loss and Far Lands arithmetic still apply.
- full-width addresses do not wrap into their low-word aliases. address arithmetic saturates at the signed-1088 endpoints. no old-save migration or new generator mode is introduced.
- **long-path support required.** Anvil paths use nested hexadecimal components (up to 32 characters each per axis), ending in `r.mca`; oversized chunks use the independent `chunks/` tree ending in `c.mcc`. paths can exceed 600 characters plus the world root. enable Windows long paths and use backup/restore tools that preserve the entire directory tree, including external chunks; flat `region/*.mca` scans are insufficient.

the original unfinished mod was written by mckuhei, whose sites are dead now. code before the fork is commit `f1b40fcf28655ebf01aa5dd77bc8e0cdc9dc97af` (it barely works)

## highlights

- a world addressed by `Int1088`. block X/Z span the signed 1088-bit domain, Y keeps the usual build height. coordinate arithmetic saturates instead of wrapping around to the far side of the world.
- positions stay lossless past the double limit. `SectorVec3` keeps X/Z as a block coordinate plus a fraction in `[0, 1)`, so physics never subtracts two gigantic world doubles.
- collision still works out there. movement gets solved in a tiny local frame, then applied back to the real position — including on the last representable block, not just "pretty far out".
- every entity and block entity keeps a true position: players, mobs, vehicles, projectiles, all of them. spawning, AI, pathfinding, riding and dismounting use it.
- camera-relative client. terrain, block entities, particles, explosions, sky, clouds and audio are drawn and heard relative to the camera, so the view doesn't dissolve into jitter.
- independent generator mode and coordinate precision, both stored in world NBT. `/generator mode ...` or `/generator precision ...` stages a change; `/generator confirm` applies and saves it.
  - `32bit` — how the original mod behaved. far lands at 12,550,824.
  - `64bit` — 64-bit generator with intentional Far Lands arithmetic.
  - `32bit-stable-shifts` — same as `32bit`, except `shift_x`/`shift_z` use overflow-safe, full-1088-bit sampling in both stacks (including the rotated noise Y axis). The protected shifts always retain exact coordinates; all other samplers retain 32-bit Far Lands arithmetic and follow the selected precision.
  - `64bit-stable-shifts` — same as `64bit`, except `shift_x`/`shift_z` use overflow-safe, full-1088-bit sampling in both stacks (including the rotated noise Y axis). All other noise overflow remains. The protected shifts always retain exact coordinates; other samplers still follow the selected precision.
  - `64bit-patched` — same generator with the Far Lands arithmetic patched out.
  - precision: `precise` retains exact integer positions; `lossy` uses absolute doubles. Precision is independent of mode and does not patch intentional generator limits.
- outer end rings are optional. `/endrings` flips them on or off; turning them off asks for `/endrings confirm`.
- debug that isn't lying to you at range. F3 shows precision, splits block vs region coords, and prints the generator mode. `/wraprouter` dumps NoiseRouter overflow.

## what got redone

vanilla's `int` positions and `double` physics couldn't be patched in one spot. these systems were swapped out or rebuilt so a huge world X/Z double never gets built in the first place.

### coords and world limits

- `Vec3i`, `BlockPos`, `ChunkPos`, `SectionPos`: X/Z are `Int1088`, Y stays `int`.
- `WorldBounds`: one set of block, chunk and region limits, with saturating add/multiply so an edge request can't overflow into the other side of the world.
- chunk tickets, chunk cache, region-file indexing, "chunk out of bounds" handling.
- the world border — removed, not just pushed further out.
- level save/load. horizontal block addresses use fixed 17-limb NBT arrays, with separate entity fractions; they are never reconstructed from lossy doubles.

### movement and collision

- `SectorVec3`, `SectorAABB`, `SectorPhysicsOrigin`: lossless position, lossless box, one fixed local origin per collision solve.
- `SectorBlockCollisions`: block shapes get shifted by a small origin-relative offset, not by a global double.
- entity `move()`, stepping, fluids, suffocation, edge back-off for sector entities.
- `SectorClipper`: block raycasts walk exact 1088-bit block addresses, hit positions stay true.
- block placing, breaking, outlines, the break animation.
- boundary collision uses exact split coordinates and bounded local frames rather than global doubles.

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
- `PreciseNoiseCoordinate` retains the established long path; `WorldgenCoordinate` supplies primitive 1088-by-53-bit scaling with 18-limb product scratch, carries and full-width conversion for distant samples.
- `FarlandsMode` retains all five existing arithmetic policies; legacy world saves are not supported.
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
- block-entity poses: the exact block position is subtracted before it reaches the float matrix stack.
- the skybox used to be a flat color; it's a proper per-dimension sky again, with long-safe world math.
- clouds.
- particles, the particle limiter included, which now runs in camera space.
- frustum culling. the old cull froze the game and could pin hundreds of thousands of chunks in memory.
- F3 and F3+G chunk borders.

### audio

- `SoundEngine`: a world sound gets moved into the listener's sector before it's queued for OpenAL. UI sounds stay local.

### commands

- teleport and the other position commands move sector positions instead of adding to `getX()` / `getZ()`.
- `/generator`, `/endrings`, `/wraprouter`, `/coords`.

## installing

- grab the jar from the [latest release](https://github.com/pomidorka1515/64bit-1.19.2/releases)
- get [prism launcher](https://prismlauncher.org) if you don't have it already
- make a **vanilla** 1.19.2 instance
- open its settings, then Version
- "Add to Minecraft.jar" and pick the jar you downloaded

## source

### termux
use native OpenJDK 17 (no root or proot needed, doesnt require glibc):
```sh
pkg update && pkg install openjdk-17
ln -s "$PREFIX/lib/jvm/java-17-openjdk" "$HOME/jdk17"
./gradlew compileJava test
```

the wrapper automatically uses `~/jdk17` when `JAVA_HOME` is unset.
it also disables gradle's incompatible linux native services and file watching,
and limits gradle to two workers and a 1536 MiB heap.
an explicit `JAVA_HOME` still takes precedence. 
compilation and tests do not require client assets or the `setup` / `runclient` tasks.

`./gradlew test` runs the default suite. the slow full-height worldgen generation/reload matrix is separate: `./gradlew worldgenMatrixTest`. final automated acceptance requires both tasks and `bash tools/check_1088bit_invariants.sh --strict`; passing them does not replace live client/server acceptance.

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
