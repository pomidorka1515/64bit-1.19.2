# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Agent Behavior & Output
- **Silent Tool Execution**: NEVER output text, status updates, or thought narration before or between tool calls.
- Run all searches, edits, and terminal commands consecutively without intermediate chatter.
- Output text ONLY once all actions are completed.
- The final response must consist strictly of a summary of changes and verification/test results.

## Project overview

This is `mcp-reborn`, a heavily modified Minecraft 1.19.2 MCP/ForgeGradle workspace migrating horizontal coordinates to signed `Int128`. The source tree is a patched/deobfuscated Minecraft client and server implementation rather than a small standalone library.

The current model is described in `README.md` and the migration plan under `docs/128bit/`. Follow the requested step's execution rules, design and file allowlist. `prompt.md` describes earlier sector-physics work, not the scope of the current migration.

## Save break and deferred worldgen seam

- Block X/Z use the full signed 128-bit domain (`-2^127` through `2^127 - 1`); Y remains unchanged. Exact moving positions pair an `Int128` block coordinate with a normalized double fraction in `[0, 1)`.
- This is a strict save-format break: new worlds only. Vanilla, old 64-bit fork and other foreign saves are rejected, not migrated. Do not add old-world support, legacy decode fallback or migration hints. Matching 128-bit client/server builds are required.
- Storage, networking, chunk/section keys, region names, entity positions and persisted placement identity retain full `Int128` coordinates.
- Only worldgen sampling explicitly projects coordinates to the low 64 bits (`lo()`, `*Lo` accessors, documented long overloads or `WorldBounds.noiseCoordinate(Int128)`), interpreted as signed `long`. Do not saturate, substitute the high half, warn or add a fallback. Do not narrow authoritative identity at this seam or change noise algorithms in this iteration.
- Terrain behavior beyond the signed 64-bit range is undefined and may repeat, seam or crash. Sampling aliases do not guarantee identical complete chunks or successful generation.
- A later worldgen iteration will extend the exact noise lattice to `Int128`. Per §19 default 9, 128-bit farlands behavior is fully deferred: no farlands decision or mode branch for 128-bit values belongs in this conversion. Existing modes operate on projected 64-bit inputs.

## Build and test commands

The Gradle wrapper is the authoritative build entry point. The build is configured for Java 17 (`gradle.properties` points at `/opt/jdk17` in this workspace), and ForgeGradle uses Gradle 7.5. Use `./gradlew` to run anything.


Full-suite verification command (only after the migration's test-red window ends at S10.07; main compilation is currently green, but test conversion and final acceptance are pending):

```bash
./gradlew test                   # Run all JUnit 5 tests (compiles automatically, no need for `compileJava`)
```

Run one test class or one test method:

```bash
./gradlew test --tests 'net.minecraft.world.phys.SectorVec3Test'
./gradlew test --tests 'net.minecraft.world.phys.SectorVec3Test.preservesLargeBlockCoordinatesAndSubBlocks'
```

Useful development/runtime tasks:

```bash
./gradlew tasks --all            # Inspect ForgeGradle and project tasks
./gradlew copyAssets             # Download/copy assets without full setup
./gradlew runclient              # Launch the client; working directory is run/
./gradlew clean build            # Clean rebuild
```

There is no separate formatter, linter, or static-analysis task configured in `build.gradle`; `check`/`build` are the normal automated checks. CI runs `./gradlew setup` followed by `./gradlew build` on JDK 17. ForgeGradle may emit Gradle deprecation or JVM native-access warnings that are unrelated to source failures.

## Repository architecture

- `src/main/java/net/minecraft/**` is the main patched Minecraft 1.19.2 codebase. The usual Minecraft boundaries remain meaningful: `world` contains simulation/gameplay, `world/level` contains world/block/chunk access and collision queries, `world/entity` contains entity and player behavior, `world/phys` contains vectors/AABBs/hit results, and `client` contains client-only input, GUI, rendering, and local-player code.
- `src/main/java/com/mojang/**` contains Mojang support/client libraries included in the patched source set.
- `src/test/java/**` contains focused JUnit 5 tests. The sector-coordinate tests are the most relevant regression suite for precision work; `SectorBlockCollisionsTest` uses a manually implemented collision getter rather than terrain generation.
- `projects/mcp/` is the included MCP/ForgeGradle subproject used as the patcher/dependency parent. Root `build.gradle` applies the ForgeGradle patcher to `src/main/java` and maps Minecraft 1.19.2 official mappings.
- `run/` is the client runtime directory. It is not the source architecture; assets and runtime state are placed there by the setup/copy-assets tasks.
- `build/`, `.gradle/`, and generated IDE/run files are build outputs or tooling state, not hand-maintained implementation layers.

The normal execution path is vanilla-style: client/server ticks call entity and player logic; entities query `Level`/`CollisionGetter` for blocks, fluids, and entity interactions; collision resolution uses local `AABB`/`VoxelShape` geometry. Rendering and networking must consume exact position state, not reconstruct it from approximate double mirrors.

## Sector-coordinate architecture

Exact split positions apply to entities throughout the migration. Ordinary `Vec3` values still represent velocity and small local deltas:

- `SectorVec3` (`world/phys`) stores exact X/Z as `(Int128 block coordinate, double normalized fraction)` and keeps Y as a normal double. Its invariant is `0 <= subX, subZ < 1`. Use floor semantics for negative coordinates. `toApproximateVec3()` is explicitly lossy and is for compatibility only.
- `SectorPhysicsOrigin` identifies one small local physics frame using exact `Int128` X/Z and integer Y. Subtract integer coordinates before converting to doubles. One origin must remain fixed across a collision-resolution operation; it is not the camera origin.
- `SectorAABB` stores exact X/Z endpoints in the same split form and Y as doubles. It supplies exact block ranges and converts to a local ordinary `AABB` only when all geometry is in the local frame.
- `SectorBlockCollisions` iterates exact global block addresses with `Int128` X/Z (`Cursor3D`, `SectionPos`, `ChunkPos`, and `BlockPos`), then translates block voxel shapes by small origin-relative offsets. Do not pass huge global doubles to `VoxelShape.move`, and do not make ordinary `BlockCollisions` interpret local boxes.
- `SectorClipper` is the exact ray/block traversal path. DDA traversal uses local coordinates and exact world block reconstruction; hit results carry exact positions where supported.

`Entity` owns authoritative split position state and exact old positions. Legacy coordinate doubles and approximate position/bounding-box APIs are compatibility mirrors, not sources of exact world identity. Construct exact positions from full-width anchors, never by reconstructing a huge double.

When changing exact player position, update exact block/chunk caches and movement callbacks through the existing Entity setter path, then mirror to legacy fields. Internal exact movement must use the sector setter and local deltas, never `setPos(getX() + dx, ...)`. Legacy `getX()/getZ()`, `position()`, and `getBoundingBox()` remain approximate compatibility APIs and must not be treated as exact physics state.

Collision resolution uses a bounded local frame and applies the resolved local `Vec3` delta directly to `SectorVec3`. Keep velocity, input, directions, and other small deltas as ordinary `Vec3`. Audit tick paths that derive block positions, collision boxes, fluid ranges, eye positions, suffocation state, stepping, edge back-off, or movement statistics; a change only in `Entity.move()` is not sufficient.

## Scope constraints for precision changes

The migration spans entities, networking, storage, rendering, lighting and pathfinding, but each requested step remains bounded by its file allowlist. In particular:

- Do not broaden a step into another subsystem without operator authorization.
- Do not silently change legacy APIs to return local coordinates.
- Do not use global coordinate-to-double conversions for exact X/Z subtraction, shape translation, block lookup, or collision geometry.
- Keep exact world addressing, local physics coordinates, legacy approximate doubles, and velocity/delta vectors clearly distinct at conversion boundaries.
- Preserve the full-width coordinate protocol; worldgen projection is not a networking or persistence fallback.

The repository currently contains ongoing work and may have unrelated uncommitted changes. Inspect `git status` and the diff before editing, and avoid overwriting existing user changes. Run the requested step's verification levels and inspect the final diff; do not claim full-suite success during the test-red window. For physics changes, also search for huge `getX()/getZ()` subtraction, global-double AABBs, lossy block-position construction, and global-double voxel-shape translations.
