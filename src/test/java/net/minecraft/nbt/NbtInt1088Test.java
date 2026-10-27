package net.minecraft.nbt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityLoadingTestLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NbtInt1088Test {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @TempDir
    Path directory;

    @Test
    void fixedArraysRoundTripThroughCompressedNbt() throws IOException {
        Int1088[] values = {Int1088.MIN, Int1088.MAX, Int1088.ZERO, Int1088.ONE,
            Int1088.fromLong(-1), Int1088.fromLong(Long.MIN_VALUE), Int1088.of(0, 1)};
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < values.length; ++i) {
            String key = "axis" + i;
            NbtUtils.writeInt1088(tag, key, values[i]);
            assertTrue(tag.contains(key, Tag.TAG_LONG_ARRAY));
            long[] words = tag.getLongArray(key);
            assertEquals(17, words.length);
            for (int j = 0; j < words.length; ++j) assertEquals(values[i].limb(j), words[j]);
        }
        assertEquals(values.length, tag.size());
        Path file = directory.resolve("coordinates.dat");
        NbtIo.writeCompressed(tag, file.toFile());
        CompoundTag restored = NbtIo.readCompressed(file.toFile());
        for (int i = 0; i < values.length; ++i) {
            assertEquals(values[i], NbtUtils.readInt1088(restored, "axis" + i));
        }
    }

    @Test
    void arbitraryLimbsAndCanonicalSignExtension() {
        Random random = new Random(1088L);
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < 1000; ++i) {
            long[] words = new long[17];
            for (int j = 0; j < words.length; ++j) words[j] = random.nextLong();
            Int1088 value = Int1088.of(words);
            NbtUtils.writeInt1088(tag, "X", value);
            assertArrayEquals(words, tag.getLongArray("X"));
            assertEquals(value, NbtUtils.readInt1088(tag, "X"));
        }
        long[] negative = new long[17];
        Arrays.fill(negative, -1L);
        tag.putLongArray("X", negative);
        assertEquals(1, NbtUtils.readInt1088(tag, "X").limbCount());
        assertEquals(Int1088.fromLong(-1), NbtUtils.readInt1088(tag, "X"));
    }

    @Test
    void arraysDoNotAliasValues() {
        long[] words = new long[17];
        words[16] = 42;
        Int1088 value = Int1088.of(words);
        CompoundTag tag = new CompoundTag();
        NbtUtils.writeInt1088(tag, "X", value);
        Int1088 decoded = NbtUtils.readInt1088(tag, "X");
        tag.getLongArray("X")[16] = 7;
        assertEquals(42, value.limb(16));
        assertEquals(value, decoded);
        assertNotEquals(value, NbtUtils.readInt1088(tag, "X"));
    }

    @Test
    void blockPositionsRoundTripAndRejectLegacyCoordinates() {
        for (BlockPos pos : new BlockPos[] {
                new BlockPos(Int1088.MIN, Integer.MIN_VALUE, Int1088.MAX),
                new BlockPos(Int1088.ONE.shiftLeftSaturated(127), 65, Int1088.ONE.shiftLeftSaturated(1024)),
                new BlockPos(Int1088.of(0, 1), 64, Int1088.fromLong(-1))}) {
            CompoundTag encoded = NbtUtils.writeBlockPos(pos);
            assertEquals(pos, NbtUtils.readBlockPos(encoded));
            assertEquals(Tag.TAG_INT, encoded.getTagType("Y"));
            for (String axis : new String[] {"X", "Z"}) {
                CompoundTag missing = encoded.copy();
                missing.remove(axis);
                CompoundTag before = missing.copy();
                assertThrows(IllegalArgumentException.class, () -> NbtUtils.readBlockPos(missing));
                assertEquals(before, missing);
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.putLong("X", 0);
        tag.putLong("XHi", 0);
        tag.putInt("Y", 0);
        NbtUtils.writeInt1088(tag, "Z", Int1088.ZERO);
        assertThrows(IllegalArgumentException.class, () -> NbtUtils.readBlockPos(tag));
    }

    @Test
    void rejectsMissingWrongTypeLegacyAndWrongLengths() {
        CompoundTag tag = new CompoundTag();
        assertRejected(tag);
        tag.putLong("X", 1);
        tag.putLong("XHi", 0);
        assertRejected(tag);
        Tag[] invalid = {ByteTag.valueOf((byte) 1), IntTag.valueOf(1), StringTag.valueOf("1"),
            new ByteArrayTag(new byte[17]), new IntArrayTag(new int[17]), new ListTag(), new CompoundTag()};
        for (Tag value : invalid) {
            tag.put("X", value);
            assertRejected(tag);
        }
        for (int length : new int[] {0, 1, 2, 16, 18, 34}) {
            tag.putLongArray("X", new long[length]);
            assertRejected(tag);
        }
    }

    private static void assertRejected(CompoundTag tag) {
        CompoundTag before = tag.copy();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> NbtUtils.readInt1088(tag, "X"));
        assertTrue(error.getMessage().contains("X"));
        assertTrue(error.getMessage().contains("LongArray[17]"));
        assertEquals(before, tag);
    }

    @Test
    void netherEntryPositionUsesExactArraysAndRejectsDoubleMirrors() throws Exception {
        Method write = ServerPlayer.class.getDeclaredMethod("writeNetherEntryPosition", SectorVec3.class);
        Method read = ServerPlayer.class.getDeclaredMethod("readNetherEntryPosition", CompoundTag.class);
        write.setAccessible(true);
        read.setAccessible(true);
        for (Int1088 coordinate : wideCoordinates()) {
            SectorVec3 pos = SectorVec3.fromBlockAndFraction(coordinate, 0.25D, 64.5D, coordinate.not(), 0.75D);
            CompoundTag tag = (CompoundTag) write.invoke(null, pos);
            assertEquals(pos, read.invoke(null, tag));
            assertEquals(Tag.TAG_DOUBLE, tag.getTagType("y"));
            for (String axis : new String[] {"x", "z"}) {
                assertEquals(17, tag.getLongArray(axis).length);
                CompoundTag invalid = tag.copy();
                invalid.putDouble(axis, 0.0D);
                assertReflectiveRejection(read, invalid);
                invalid.putLongArray(axis, new long[16]);
                assertReflectiveRejection(read, invalid);
            }
            for (String key : new String[] {"SubX", "SubZ", "y"}) {
                CompoundTag invalid = tag.copy();
                invalid.remove(key);
                assertReflectiveRejection(read, invalid);
                invalid.putDouble(key, Double.NaN);
                assertReflectiveRejection(read, invalid);
            }
        }
    }

    @Test
    void jigsawNbtRejectsNumericListsAsWellAsLegacyAndTruncatedAxes() {
        var registryOps = net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE, net.minecraft.core.RegistryAccess.builtinCopy());
        for (Int1088 coordinate : wideCoordinates()) {
            JigsawJunction junction = new JigsawJunction(coordinate, 65, coordinate.not(), -3, StructureTemplatePool.Projection.RIGID);
            CompoundTag tag = (CompoundTag) junction.serialize(NbtOps.INSTANCE).getValue();
            assertEquals(junction, JigsawJunction.deserialize(new Dynamic<>(NbtOps.INSTANCE, tag)));
            assertEquals(Tag.TAG_INT, tag.getTagType("source_ground_y"));
            for (String axis : new String[] {"source_x", "source_z"}) {
                ListTag list = new ListTag();
                for (long limb : tag.getLongArray(axis)) list.add(LongTag.valueOf(limb));
                for (Tag invalid : new Tag[] {list, LongTag.valueOf(0), new LongArrayTag(new long[16]), new LongArrayTag(new long[18])}) {
                    CompoundTag malformed = tag.copy();
                    malformed.put(axis, invalid);
                    assertThrows(IllegalArgumentException.class, () -> JigsawJunction.deserialize(new Dynamic<>(NbtOps.INSTANCE, malformed)));
                    assertThrows(IllegalArgumentException.class, () -> JigsawJunction.deserialize(new Dynamic<>(registryOps, malformed)));
                }
            }
        }
    }

    @Test
    void entityAndAuxiliaryPositionsRoundTripAndRejectPartialOrLegacyAxes() {
        Level level = EntityLoadingTestLevel.create();
        EntityType<?>[] types = {EntityType.DOLPHIN, EntityType.TURTLE, EntityType.TURTLE, EntityType.PHANTOM,
                EntityType.VEX, EntityType.ITEM_FRAME, EntityType.ARMOR_STAND};
        String[][] keys = {{"TreasurePosX", "TreasurePosY", "TreasurePosZ"}, {"HomePosX", "HomePosY", "HomePosZ"},
                {"TravelPosX", "TravelPosY", "TravelPosZ"}, {"AX", "AY", "AZ"}, {"BoundX", "BoundY", "BoundZ"},
                {"TileX", "TileY", "TileZ"}, {"SleepingX", "SleepingY", "SleepingZ"}};
        for (int i = 0; i < types.length; ++i) {
            for (Int1088 coordinate : wideCoordinates()) {
                Entity entity = types[i] == EntityType.ITEM_FRAME
                        ? new net.minecraft.world.entity.decoration.ItemFrame(level, new BlockPos(coordinate, 65, coordinate.not()), net.minecraft.core.Direction.NORTH)
                        : types[i].create(level);
                assertNotNull(entity);
                SectorVec3 pos = SectorVec3.fromBlockAndFraction(coordinate, 0.25D, 64.5D, coordinate.not(), 0.75D);
                entity.applyExactPosition(pos);
                CompoundTag tag = entity.saveWithoutId(new CompoundTag());
                NbtUtils.writeInt1088(tag, keys[i][0], coordinate);
                tag.putInt(keys[i][1], 65);
                NbtUtils.writeInt1088(tag, keys[i][2], coordinate.not());
                entity.load(tag);
                CompoundTag restored = entity.saveWithoutId(new CompoundTag());
                assertEquals(coordinate, NbtUtils.readInt1088(restored, keys[i][0]), types[i].toString());
                assertEquals(coordinate.not(), NbtUtils.readInt1088(restored, keys[i][2]));
                assertEquals(Tag.TAG_INT, restored.getTagType(keys[i][1]));
                for (String axis : new String[] {keys[i][0], keys[i][2]}) {
                    CompoundTag invalid = tag.copy();
                    invalid.remove(axis);
                    assertThrows(net.minecraft.ReportedException.class, () -> entity.load(invalid));
                    invalid.putLong(axis, 0);
                    invalid.putLong(axis + "Hi", 0);
                    assertThrows(net.minecraft.ReportedException.class, () -> entity.load(invalid));
                    invalid.putLongArray(axis, new long[16]);
                    assertThrows(net.minecraft.ReportedException.class, () -> entity.load(invalid));
                }
                CompoundTag invalidExact = tag.copy();
                invalidExact.getCompound("ExactPos").putLongArray("BlockZ", new long[18]);
                assertThrows(net.minecraft.ReportedException.class, () -> entity.load(invalidExact));
            }
        }
    }

    private static Int1088[] wideCoordinates() {
        return new Int1088[] {Int1088.ONE.shiftLeftSaturated(127), Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX};
    }

    @Test
    void savedTicksForcedChunksMapsRaidsAndBeehivesUseStrictPositionArrays() {
        for (Int1088 coordinate : wideCoordinates()) {
            BlockPos pos = new BlockPos(coordinate, 65, coordinate.not());
            var tick = new net.minecraft.world.ticks.SavedTick<>("test", pos, 3, net.minecraft.world.ticks.TickPriority.NORMAL);
            CompoundTag tickTag = tick.save(value -> value);
            assertEquals(tick, net.minecraft.world.ticks.SavedTick.loadTick(tickTag, java.util.Optional::of).orElseThrow());
            assertAxesRejected(tickTag, "x", "z", tag -> net.minecraft.world.ticks.SavedTick.loadTick(tag, java.util.Optional::of));

            var forced = new net.minecraft.world.level.ForcedChunksSavedData();
            var chunk = new net.minecraft.world.level.ChunkPos(pos);
            forced.getChunks().add(chunk);
            CompoundTag forcedTag = forced.save(new CompoundTag());
            assertEquals(forced.getChunks(), net.minecraft.world.level.ForcedChunksSavedData.load(forcedTag).getChunks());
            CompoundTag badForced = forcedTag.copy();
            badForced.getList("Forced", Tag.TAG_COMPOUND).getCompound(0).putLongArray("z", new long[16]);
            assertThrows(IllegalArgumentException.class, () -> net.minecraft.world.level.ForcedChunksSavedData.load(badForced));

            CompoundTag mapTag = new CompoundTag();
            mapTag.putString("dimension", "minecraft:overworld");
            NbtUtils.writeInt1088(mapTag, "xCenter", coordinate);
            NbtUtils.writeInt1088(mapTag, "zCenter", coordinate.not());
            var map = net.minecraft.world.level.saveddata.maps.MapItemSavedData.load(mapTag);
            CompoundTag savedMap = map.save(new CompoundTag());
            assertEquals(coordinate, NbtUtils.readInt1088(savedMap, "xCenter"));
            assertEquals(coordinate.not(), NbtUtils.readInt1088(savedMap, "zCenter"));
            assertAxesRejected(savedMap, "xCenter", "zCenter", net.minecraft.world.level.saveddata.maps.MapItemSavedData::load);

            CompoundTag raidTag = new CompoundTag();
            NbtUtils.writeInt1088(raidTag, "CX", coordinate);
            raidTag.putInt("CY", 65);
            NbtUtils.writeInt1088(raidTag, "CZ", coordinate.not());
            var raid = new net.minecraft.world.entity.raid.Raid(null, raidTag);
            CompoundTag savedRaid = raid.save(new CompoundTag());
            assertEquals(coordinate, NbtUtils.readInt1088(savedRaid, "CX"));
            assertEquals(coordinate.not(), NbtUtils.readInt1088(savedRaid, "CZ"));
            assertEquals(Tag.TAG_INT, savedRaid.getTagType("CY"));
            assertAxesRejected(savedRaid, "CX", "CZ", tag -> new net.minecraft.world.entity.raid.Raid(null, tag));

            var hive = new net.minecraft.world.level.block.entity.BeehiveBlockEntity(pos,
                    net.minecraft.world.level.block.Blocks.BEEHIVE.defaultBlockState());
            CompoundTag hiveTag = new CompoundTag();
            hiveTag.put("FlowerPos", NbtUtils.writeBlockPos(pos));
            hive.load(hiveTag);
            CompoundTag savedHive = hive.saveWithFullMetadata();
            assertEquals(pos, net.minecraft.world.level.block.entity.BlockEntity.getPosFromTag(savedHive));
            assertEquals(pos, NbtUtils.readBlockPos(savedHive.getCompound("FlowerPos")));
            assertAxesRejected(savedHive, "x", "z", net.minecraft.world.level.block.entity.BlockEntity::getPosFromTag);
            hiveTag.getCompound("FlowerPos").putLongArray("Z", new long[18]);
            assertThrows(IllegalArgumentException.class, () -> hive.load(hiveTag));
        }
    }

    @Test
    void primarySpawnAndBorderRoundTripWithoutWideningY() {
        var registries = net.minecraft.core.RegistryAccess.builtinCopy();
        var settings = new net.minecraft.world.level.LevelSettings("nbt-test", net.minecraft.world.level.GameType.SURVIVAL,
                false, net.minecraft.world.Difficulty.NORMAL, false, new net.minecraft.world.level.GameRules(),
                net.minecraft.world.level.DataPackConfig.DEFAULT);
        var generator = net.minecraft.world.level.levelgen.presets.WorldPresets.createNormalWorldFromPreset(registries, 0L);
        for (Int1088 coordinate : wideCoordinates()) {
            var data = new net.minecraft.world.level.storage.PrimaryLevelData(settings, generator, com.mojang.serialization.Lifecycle.stable());
            data.setXSpawn(coordinate);
            data.setYSpawn(65);
            data.setZSpawn(coordinate.not());
            CompoundTag tag = data.createTag(registries, null);
            var version = net.minecraft.world.level.storage.LevelVersion.parse(new Dynamic<Tag>(NbtOps.INSTANCE, tag));
            java.util.function.Function<CompoundTag, net.minecraft.world.level.storage.PrimaryLevelData> read = value ->
                    net.minecraft.world.level.storage.PrimaryLevelData.parse(new Dynamic<Tag>(NbtOps.INSTANCE, value), null,
                            settings, version, generator, com.mojang.serialization.Lifecycle.stable());
            var restored = read.apply(tag);
            assertEquals(coordinate, restored.getXSpawn());
            assertEquals(coordinate.not(), restored.getZSpawn());
            assertEquals(65, restored.getYSpawn());
            assertEquals(Tag.TAG_INT, tag.getTagType("SpawnY"));
            assertAxesRejected(tag, "SpawnX", "SpawnZ", read);
            assertAxesRejected(tag, "BorderCenterX", "BorderCenterZ", read);
        }
    }

    private static void assertAxesRejected(CompoundTag valid, String x, String z, java.util.function.Function<CompoundTag, ?> read) {
        for (String axis : new String[] {x, z}) {
            for (Tag bad : new Tag[] {IntTag.valueOf(0), LongTag.valueOf(0), new LongArrayTag(new long[16]), new LongArrayTag(new long[18])}) {
                CompoundTag invalid = valid.copy();
                invalid.put(axis, bad);
                invalid.putLong(axis + "Hi", 0);
                CompoundTag before = invalid.copy();
                assertThrows(IllegalArgumentException.class, () -> read.apply(invalid));
                assertEquals(before, invalid);
            }
        }
    }

    @Test
    void templatePiecesPoolPiecesAndStartsRetainWideAnchors() throws Exception {
        var registries = net.minecraft.core.RegistryAccess.builtinCopy();
        var resources = net.minecraft.server.packs.resources.ResourceManager.Empty.INSTANCE;
        var source = net.minecraft.world.level.storage.LevelStorageSource.createDefault(directory);
        try (var access = source.createAccess("structures")) {
            var manager = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager(resources, access);
            var context = new net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext(resources, registries, manager);
            var rotation = net.minecraft.world.level.block.Rotation.NONE;
            var processors = registries.registryOrThrow(net.minecraft.core.Registry.PROCESSOR_LIST_REGISTRY)
                    .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.Registry.PROCESSOR_LIST_REGISTRY,
                            new net.minecraft.resources.ResourceLocation("minecraft:empty")));
            var element = net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement.single("nbt-test", processors)
                    .apply(StructureTemplatePool.Projection.RIGID);
            var structure = registries.registryOrThrow(net.minecraft.core.Registry.STRUCTURE_REGISTRY)
                    .get(new net.minecraft.resources.ResourceLocation("minecraft:village_plains"));
            assertNotNull(structure);
            for (Int1088 coordinate : wideCoordinates()) {
                BlockPos pos = new BlockPos(coordinate, 65, coordinate.not());
                var piece = new net.minecraft.world.level.levelgen.structure.structures.IglooPieces.IglooPiece(manager,
                        new net.minecraft.resources.ResourceLocation("igloo/top"), pos, rotation, 0);
                CompoundTag tag = piece.createTag(context);
                assertEquals(pos, new net.minecraft.world.level.levelgen.structure.structures.IglooPieces.IglooPiece(manager, tag).templatePosition());
                assertAxesRejected(tag, "TPX", "TPZ", value -> new net.minecraft.world.level.levelgen.structure.structures.IglooPieces.IglooPiece(manager, value));

                var pool = new net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece(manager, element, pos,
                        0, rotation, new net.minecraft.world.level.levelgen.structure.BoundingBox(pos));
                pool.addJunction(new JigsawJunction(coordinate, 65, coordinate.not(), 0, StructureTemplatePool.Projection.RIGID));
                CompoundTag poolTag = pool.createTag(context);
                var restoredPool = new net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece(context, poolTag);
                assertEquals(pos, restoredPool.getPosition());
                assertEquals(pool.getJunctions(), restoredPool.getJunctions());
                assertAxesRejected(poolTag, "PosX", "PosZ", value -> new net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece(context, value));

                var chunk = new net.minecraft.world.level.ChunkPos(pos);
                var start = new net.minecraft.world.level.levelgen.structure.StructureStart(structure, chunk, 0,
                        new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(java.util.List.of(pool)));
                CompoundTag startTag = start.createTag(context, chunk);
                var restoredStart = net.minecraft.world.level.levelgen.structure.StructureStart.loadStaticStart(context, startTag, 0L);
                assertNotNull(restoredStart);
                assertEquals(chunk, restoredStart.getChunkPos());
                assertAxesRejected(startTag, "ChunkX", "ChunkZ", value ->
                        net.minecraft.world.level.levelgen.structure.StructureStart.loadStaticStart(context, value, 0L));
            }
        }
    }

    private static void assertReflectiveRejection(Method read, CompoundTag tag) {
        CompoundTag before = tag.copy();
        InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> read.invoke(null, tag));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertEquals(before, tag);
    }

    @Test
    void inheritedPlayerDeathTargetRequiresPerAxisNbtArrays() {
        var target = net.minecraft.core.GlobalPos.of(Level.OVERWORLD,
                new BlockPos(Int1088.ONE.shiftLeftSaturated(1024), 65, Int1088.MIN));
        CompoundTag encoded = (CompoundTag) net.minecraft.core.GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, target)
                .result().orElseThrow();
        // S5.2 / storage §2.2: a persisted BlockPos is X/Z LongArray[17], Y int.
        CompoundTag position = assertInstanceOf(CompoundTag.class, encoded.get("pos"));
        assertEquals(17, position.getLongArray("X").length);
        assertEquals(17, position.getLongArray("Z").length);
        assertEquals(Tag.TAG_INT, position.getTagType("Y"));
    }

    @Test
    void globalTargetsUseStrictNbtIncludingRegistryOpsWithoutChangingJson() {
        var registryOps = net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE, net.minecraft.core.RegistryAccess.builtinCopy());
        var codec = net.minecraft.core.GlobalPos.CODEC;
        for (Int1088 coordinate : wideCoordinates()) {
            var target = net.minecraft.core.GlobalPos.of(Level.NETHER, new BlockPos(coordinate, Integer.MIN_VALUE, coordinate.not()));
            for (var ops : java.util.List.of(NbtOps.INSTANCE, registryOps)) {
                CompoundTag encoded = (CompoundTag) codec.encodeStart(ops, target).result().orElseThrow();
                assertEquals(target, codec.parse(ops, encoded).result().orElseThrow());
                for (String axis : new String[] {"X", "Z", "Y"}) {
                    for (Tag bad : new Tag[] {LongTag.valueOf(0), new LongArrayTag(new long[16]),
                            new LongArrayTag(new long[18]), new ListTag()}) {
                        CompoundTag malformed = encoded.copy();
                        malformed.getCompound("pos").put(axis, bad);
                        assertTrue(codec.parse(ops, malformed).error().isPresent());
                    }
                    CompoundTag missing = encoded.copy();
                    missing.getCompound("pos").remove(axis);
                    assertTrue(codec.parse(ops, missing).error().isPresent());
                }
                CompoundTag flattened = encoded.copy();
                flattened.put("pos", BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, target.pos()).result().orElseThrow());
                assertTrue(codec.parse(ops, flattened).error().isPresent());
            }
            var json = codec.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, target).result().orElseThrow();
            assertEquals(35, json.getAsJsonObject().getAsJsonArray("pos").size());
            assertEquals(target, codec.parse(com.mojang.serialization.JsonOps.INSTANCE, json).result().orElseThrow());

            var memories = java.util.List.of(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME,
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE,
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.POTENTIAL_JOB_SITE,
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.MEETING_POINT,
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.LIKED_NOTEBLOCK_POSITION);
            for (var memory : memories) {
                var memoryCodec = memory.getCodec().orElseThrow();
                var value = net.minecraft.world.entity.ai.memory.ExpirableValue.of(target, 40L);
                CompoundTag encoded = (CompoundTag) memoryCodec.encodeStart(NbtOps.INSTANCE, value).result().orElseThrow();
                var restored = memoryCodec.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();
                assertEquals(target, restored.getValue());
                assertEquals(40L, restored.getTimeToLive());
                encoded.getCompound("value").getCompound("pos").putLong("X", 0L);
                assertTrue(memoryCodec.parse(NbtOps.INSTANCE, encoded).error().isPresent());
            }
        }
    }

    @Test
    void playerDeathTargetsRoundTripAndMalformedTargetsRejectLoad() {
        var player = new net.minecraft.world.entity.player.Player(EntityLoadingTestLevel.create(), BlockPos.ZERO, 0.0F,
                new com.mojang.authlib.GameProfile(new java.util.UUID(0, 1), "nbt-test"), null) {
            public boolean isSpectator() { return false; }
            public boolean isCreative() { return false; }
        };
        for (Int1088 coordinate : wideCoordinates()) {
            var target = net.minecraft.core.GlobalPos.of(Level.OVERWORLD, new BlockPos(coordinate, 65, coordinate.not()));
            player.setLastDeathLocation(java.util.Optional.of(target));
            CompoundTag saved = new CompoundTag();
            player.addAdditionalSaveData(saved);
            player.setLastDeathLocation(java.util.Optional.empty());
            player.readAdditionalSaveData(saved);
            assertEquals(java.util.Optional.of(target), player.getLastDeathLocation());
            for (String axis : new String[] {"X", "Z", "Y"}) {
                CompoundTag malformed = saved.copy();
                malformed.getCompound("LastDeathLocation").getCompound("pos").remove(axis);
                assertThrows(IllegalArgumentException.class, () -> player.readAdditionalSaveData(malformed));
            }
            for (Tag bad : new Tag[] {StringTag.valueOf("invalid"), new CompoundTag(), new LongArrayTag(new long[35])}) {
                CompoundTag malformed = saved.copy();
                malformed.put("LastDeathLocation", bad);
                assertThrows(IllegalArgumentException.class, () -> player.readAdditionalSaveData(malformed));
            }
        }
    }
}
