package net.minecraft.world.level.chunk.storage;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkSerializerTest {
    @TempDir Path directory;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void chunkPositionArraysRetainEveryLimbAndBothDomainEdges() throws Exception {
        Method read = method("readChunkPos", CompoundTag.class, String.class, String.class);
        for (Int1088 coordinate : coordinates()) {
            CompoundTag tag = position(coordinate, "xPos", "zPos");
            assertEquals(new ChunkPos(coordinate, coordinate.not()), read.invoke(null, tag, "xPos", "zPos"));
            assertEquals(17, tag.getLongArray("xPos").length);
            assertEquals(17, tag.getLongArray("zPos").length);
        }
    }

    @Test
    void chunkPositionRejectsBothMalformedAxesAndOutOfDomainWithoutMutation() throws Exception {
        Method read = method("readChunkPos", CompoundTag.class, String.class, String.class);
        for (String axis : new String[] {"xPos", "zPos"}) {
            for (Tag invalid : new Tag[] {IntTag.valueOf(1), LongTag.valueOf(1), new ListTag(),
                    new LongArrayTag(new long[2]), new LongArrayTag(new long[16]), new LongArrayTag(new long[18])}) {
                CompoundTag tag = position(Int1088.ZERO, "xPos", "zPos");
                tag.put(axis, invalid);
                tag.putLong(axis + "Hi", 0);
                reject(read, tag, "xPos", "zPos");
            }
            CompoundTag missing = position(Int1088.ZERO, "xPos", "zPos");
            missing.remove(axis);
            missing.putLong(axis + "Hi", 1);
            reject(read, missing, "xPos", "zPos");
            for (Int1088 outside : new Int1088[] {ChunkPos.MIN_CHUNK_COORDINATE.subtractSaturated(Int1088.ONE),
                    ChunkPos.MAX_CHUNK_COORDINATE.addSaturated(Int1088.ONE), Int1088.MIN, Int1088.MAX}) {
                CompoundTag tag = position(Int1088.ZERO, "xPos", "zPos");
                NbtUtils.writeInt1088(tag, axis, outside);
                reject(read, tag, "xPos", "zPos");
            }
        }
    }

    @Test
    void structureReferencesRoundTripFullCoordinatesAndRejectCorruptAxes() throws Exception {
        Method write = method("toTag", ObjectSet.class);
        Method read = method("fromTag", ListTag.class);
        ObjectSet<ChunkPos> positions = new ObjectOpenHashSet<>();
        for (Int1088 coordinate : coordinates()) positions.add(new ChunkPos(coordinate, coordinate.not()));
        ListTag encoded = (ListTag) write.invoke(null, positions);
        assertEquals(positions, read.invoke(null, encoded));
        for (String axis : new String[] {"x", "z"}) {
            ListTag malformed = encoded.copy();
            malformed.getCompound(0).putLongArray(axis, new long[16]);
            InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> read.invoke(null, malformed));
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
        }
    }

    @Test
    void completeProtoChunksRoundTripThroughCompressedNbtAtWideAndEdgeAddresses() throws Exception {
        TestLevel level = TestLevel.create();
        TestPoiManager poi = allocate(TestPoiManager.class);
        var biomes = level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        var structure = level.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY).getOrThrow(BuiltinStructures.MINESHAFT);
        for (Int1088 coordinate : coordinates()) {
            ChunkPos pos = new ChunkPos(coordinate, coordinate.not());
            ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, biomes, null);
            BlockPos block = pos.getBlockAt(3, 5, 7);
            chunk.setBlockState(block, Blocks.STONE.defaultBlockState(), false);
            CompoundTag blockEntity = new CompoundTag();
            NbtUtils.writeInt1088(blockEntity, "x", block.getX());
            blockEntity.putInt("y", block.getY());
            NbtUtils.writeInt1088(blockEntity, "z", block.getZ());
            blockEntity.putString("id", "minecraft:chest");
            chunk.setBlockEntityNbt(blockEntity);
            chunk.getBlockTicks().schedule(new ScheduledTick<>(Blocks.STONE, block, 23, TickPriority.HIGH, 0));
            chunk.addReferenceForStructure(structure, pos);
            chunk.setStartForStructure(structure, new StructureStart(structure, pos, 2,
                    new PiecesContainer(List.of(new MineshaftPieces.MineShaftRoom(0, RandomSource.create(1088),
                            pos.getMinBlockX(), pos.getMinBlockZ(), MineshaftStructure.Type.NORMAL)))));
            chunk.setInhabitedTime(1234);

            CompoundTag written = ChunkSerializer.write(level, chunk);
            Path file = directory.resolve("chunk.dat");
            NbtIo.writeCompressed(written, file.toFile());
            CompoundTag encoded = NbtIo.readCompressed(file.toFile());
            ProtoChunk loaded = ChunkSerializer.read(level, poi, pos, encoded);
            assertEquals(pos, loaded.getPos());
            assertEquals(Blocks.STONE.defaultBlockState(), loaded.getBlockState(block));
            assertEquals(chunk.getStatus(), loaded.getStatus());
            assertEquals(1234, loaded.getInhabitedTime());
            assertEquals(blockEntity, loaded.getBlockEntityNbt(block));
            assertEquals(chunk.getAllReferences(), loaded.getAllReferences());
            assertEquals(pos, loaded.getStartForStructure(structure).getChunkPos());
            assertEquals(chunk.getStartForStructure(structure).getBoundingBox(), loaded.getStartForStructure(structure).getBoundingBox());
            assertEquals(written, ChunkSerializer.write(level, loaded));
            assertEquals(pos, poi.checked);
            for (String axis : new String[] {"xPos", "zPos"}) {
                CompoundTag corrupt = encoded.copy();
                corrupt.putLong(axis, 0);
                assertThrows(IllegalArgumentException.class, () -> ChunkSerializer.read(level, poi, pos, corrupt));
            }
        }
    }

    @Test
    void wrongLocationWarningPrintsBothFullAddressesWithoutRewritingInput() throws Exception {
        TestLevel level = TestLevel.create();
        ChunkPos stored = new ChunkPos(Int1088.ONE.shiftLeftSaturated(1024), Int1088.ZERO);
        ChunkPos expected = new ChunkPos(Int1088.ONE.shiftLeftSaturated(127), Int1088.fromLong(-1));
        ProtoChunk chunk = new ProtoChunk(stored, UpgradeData.EMPTY, level,
                level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY), null);
        CompoundTag tag = ChunkSerializer.write(level, chunk);
        CompoundTag before = tag.copy();
        List<String> messages = new java.util.ArrayList<>();
        var logger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger(ChunkSerializer.class);
        var appender = new org.apache.logging.log4j.core.appender.AbstractAppender("chunk-location-test", null,
                org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout(), false,
                org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
            @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            assertEquals(expected, ChunkSerializer.read(level, allocate(TestPoiManager.class), expected, tag).getPos());
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
        assertEquals(before, tag);
        assertTrue(messages.stream().anyMatch(message -> message.contains("wrong location")
                && message.contains(stored.toString()) && message.contains(expected.toString())));
    }

    // Constructor-free doubles isolate the serializer from server threads and disk
    // services. Chunks, palettes, registries, ticks and the light reader are real.
    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static class TestLevel extends ServerLevel {
        private RegistryAccess.Frozen registries;
        private TestServer server;
        private TestChunkSource source;
        private LevelData data;

        private TestLevel() {
            super(null, null, null, null, null, null, null, false, 0, List.of(), false);
        }

        static TestLevel create() throws Exception {
            TestLevel level = allocate(TestLevel.class);
            level.registries = RegistryAccess.builtinCopy().freeze();
            level.server = allocate(TestServer.class);
            level.server.registries = level.registries;
            level.source = allocate(TestChunkSource.class);
            level.source.light = new ThreadedLevelLightEngine(new LightChunkGetter() {
                public BlockGetter getChunkForLighting(Int1088 x, Int1088 z) { return null; }
                public BlockGetter getLevel() { return level; }
            }, null, false, null, null);
            level.data = (LevelData) java.lang.reflect.Proxy.newProxyInstance(LevelData.class.getClassLoader(),
                    new Class<?>[] {LevelData.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getGameTime")) return 0L;
                        throw new AssertionError("Unexpected level-data call: " + method);
                    });
            return level;
        }

        @Override public RegistryAccess registryAccess() { return registries; }
        @Override public DedicatedServer getServer() { return server; }
        @Override public ServerChunkCache getChunkSource() { return source; }
        @Override public int getMinBuildHeight() { return 0; }
        @Override public int getHeight() { return 16; }
        @Override public long getGameTime() { return 0; }
        @Override public long getSeed() { return 0; }
        @Override public LevelData getLevelData() { return data; }
        @Override public DimensionType dimensionType() {
            return registries.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        }
    }

    private static class TestServer extends DedicatedServer {
        private RegistryAccess.Frozen registries;
        private TestServer() { super(null, null, null, null, null, null, null); }
        @Override public RegistryAccess.Frozen registryAccess() { return registries; }
        @Override public net.minecraft.server.packs.resources.ResourceManager getResourceManager() { return null; }
    }

    private static class TestChunkSource extends ServerChunkCache {
        private ThreadedLevelLightEngine light;
        private TestChunkSource() { super(null, null, null, null, null, 0, 0, false, null, null); }
        @Override public ThreadedLevelLightEngine getLightEngine() { return light; }
    }

    private static class TestPoiManager extends PoiManager {
        private ChunkPos checked;
        private TestPoiManager() { super(null, false, null, null); }
        @Override public void checkConsistencyWithBlocks(ChunkPos pos, LevelChunkSection section) { checked = pos; }
    }

    private static Int1088[] coordinates() {
        java.util.List<Int1088> result = new java.util.ArrayList<>();
        result.add(Int1088.ZERO);
        result.add(ChunkPos.MIN_CHUNK_COORDINATE);
        result.add(ChunkPos.MAX_CHUNK_COORDINATE);
        result.add(Int1088.ONE.shiftLeftSaturated(127));
        result.add(Int1088.ONE.shiftLeftSaturated(1024));
        for (int limb = 1; limb < 17; ++limb) {
            Int1088 value = Int1088.ONE.shiftLeftSaturated(limb * 64);
            result.add(value);
            result.add(value.negate());
        }
        return result.toArray(Int1088[]::new);
    }

    private static CompoundTag position(Int1088 value, String x, String z) {
        CompoundTag tag = new CompoundTag();
        NbtUtils.writeInt1088(tag, x, value);
        NbtUtils.writeInt1088(tag, z, value.not());
        return tag;
    }

    private static Method method(String name, Class<?>... types) throws Exception {
        Method method = ChunkSerializer.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private static void reject(Method read, CompoundTag tag, String x, String z) {
        CompoundTag before = tag.copy();
        InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> read.invoke(null, tag, x, z));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertEquals(before, tag);
    }
}
