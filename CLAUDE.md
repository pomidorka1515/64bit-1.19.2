# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Agent Behavior & Output
- **Silent Tool Execution**: NEVER output text, status updates, or thought narration before or between tool calls.
- Run all searches, edits, and terminal commands consecutively without intermediate chatter.
- Output text ONLY once all actions are completed.
- The final response must consist strictly of a summary of changes and verification/test results.

## Project overview

This is `mcp-reborn`, a heavily modified Minecraft 1.19.2 MCP/ForgeGradle workspace. The project is pursuing long-safe X/Z coordinates, primarily to make player physics work beyond the precision limit of ordinary doubles. The source tree is a patched/deobfuscated Minecraft client and server implementation rather than a small standalone library.

The repository's current feature direction is tracked in `README.md`, while `prompt.md` contains the detailed design/specification for the sector-coordinate player-physics work. Read the relevant portions of both before changing this subsystem.

## Build and test commands

The Gradle wrapper is the authoritative build entry point. The build is configured for Java 17 (`gradle.properties` points at `/opt/jdk17` in this workspace), and ForgeGradle uses Gradle 7.5. Use `./gradlew` to run anything.


Common verification commands:

```bash
./gradlew compileJava --no-daemon# Compile main sources (no daemon is crucial)
./gradlew test                   # Run all JUnit 5 tests
./gradlew check                  # Verification lifecycle (currently includes tests)
./gradlew build                  # Compile, test, and assemble
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

The normal execution path is vanilla-style: client/server ticks call entity and player logic; entities query `Level`/`CollisionGetter` for blocks, fluids, and entity interactions; collision geometry is represented by `AABB` and `VoxelShape`; rendering and networking consume legacy double-based entity APIs. Preserve these boundaries for ordinary entities.

## Sector-coordinate architecture

The sector system is deliberately opt-in, not a global replacement for `Vec3` or `AABB`:

- `SectorVec3` (`world/phys`) stores exact X/Z as `(long block coordinate, double normalized fraction)` and keeps Y as a normal double. Its invariant is `0 <= subX, subZ < 1`. Use floor semantics for negative coordinates. `toApproximateVec3()` is explicitly lossy and is for compatibility only.
- `SectorPhysicsOrigin` identifies one small local physics frame using exact long X/Z and integer Y. Subtract integer coordinates before converting to doubles. One origin must remain fixed across a collision-resolution operation; it is not the camera origin.
- `SectorAABB` stores exact X/Z endpoints in the same split form and Y as doubles. It supplies exact block ranges and converts to a local ordinary `AABB` only when all geometry is in the local frame.
- `SectorBlockCollisions` iterates exact global block addresses with long X/Z (`Cursor3D`, `SectionPos`, `ChunkPos`, and `BlockPos`), then translates block voxel shapes by small origin-relative offsets. Do not pass huge global doubles to `VoxelShape.move`, and do not make ordinary `BlockCollisions` interpret local boxes.
- `SectorClipper` is the exact ray/block traversal path. DDA traversal uses local coordinates and exact world block reconstruction; hit results carry exact positions where supported.

`Entity` owns the optional exact state because important base physics methods and legacy accessors cannot safely be overridden from `Player`. A sector-enabled player has an authoritative `SectorVec3` plus an exact old position; the existing `position`, bounding box, coordinate doubles, packet codec, and related caches are compatibility mirrors. `Player` enables this mode during construction from its long `BlockPos` rather than first reconstructing a huge double. `ServerPlayer` and `LocalPlayer` inherit the mode.

When changing exact player position, update exact block/chunk caches and movement callbacks through the existing Entity setter path, then mirror to legacy fields. Internal exact movement must use the sector setter and local deltas, never `setPos(getX() + dx, ...)`. Legacy `getX()/getZ()`, `position()`, and `getBoundingBox()` remain approximate compatibility APIs and must not be treated as exact physics state.

The intended movement conversion is incremental: ordinary entities retain the vanilla double path, while sector-enabled players use local AABB/VoxelShape collision resolution and apply the resolved local `Vec3` delta directly to `SectorVec3`. Keep velocity, input, directions, and other small deltas as ordinary `Vec3`. Audit all player-tick call paths that derive block positions, collision boxes, fluid ranges, eye positions, suffocation state, stepping, edge back-off, or movement statistics; a change only in `Entity.move()` is not sufficient.

## Scope constraints for precision changes

The sector design is intentionally isolated to player physics. Do not broaden a task into a global conversion of entities, `Vec3`, `AABB`, networking, rendering, terrain/world generation, lighting, or pathfinding. In particular:

- Do not convert ordinary non-player entities to `SectorVec3` unless asked.
- Do not silently change legacy APIs to return local coordinates.
- Do not use global `(double) longCoordinate` values for exact X/Z subtraction, shape translation, block lookup, or collision geometry.
- Keep exact world addressing, local physics coordinates, legacy approximate doubles, and velocity/delta vectors clearly distinct at conversion boundaries.
- Dedicated multiplayer/protocol transport for exact coordinates is outside the current sector-physics phase. Avoid packet-format and remote-entity refactors unless a narrowly required compile adjustment is unavoidable.

The repository currently contains ongoing work and may have unrelated uncommitted changes. Inspect `git status` and the diff before editing, and avoid overwriting existing user changes. Before completing a precision change, run the focused tests, `./gradlew compileJava`, and then inspect the final diff. For sector changes, also search for remaining player-physics uses of huge `getX()/getZ()` subtraction, global-double player AABBs, lossy player block-position construction, and global-double voxel-shape translations.
If you're explicitly asked to convert entities to `SectorVec3`, its fine to do so.
