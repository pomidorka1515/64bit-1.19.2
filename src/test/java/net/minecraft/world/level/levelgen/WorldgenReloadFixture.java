package net.minecraft.world.level.levelgen;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.Int1088;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelData;

/** Real serializer/Anvil IO with only server services replaced, as in ChunkSerializerTest. */
final class WorldgenReloadFixture {
    static ProtoChunk reload(Path root, ProtoChunk chunk, NoiseSettings settings) throws Exception {
        TestLevel level = allocate(TestLevel.class);
        level.settings = settings;
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
        var tag = ChunkSerializer.write(level, chunk);
        try (var worker = new Worker(root)) {
            worker.store(chunk.getPos(), tag).join();
            worker.synchronize(true).join();
        }
        try (var worker = new Worker(root)) {
            var encoded = worker.loadAsync(chunk.getPos()).join().orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(tag, encoded);
            ProtoChunk loaded = ChunkSerializer.read(level, allocate(TestPoi.class), chunk.getPos(), encoded);
            org.junit.jupiter.api.Assertions.assertEquals(tag, ChunkSerializer.write(level, loaded));
            return loaded;
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static class Worker extends IOWorker {
        Worker(Path root) { super(root, true, "worldgen-reload"); }
    }

    private static class TestLevel extends ServerLevel {
        NoiseSettings settings;
        RegistryAccess.Frozen registries;
        TestServer server;
        TestChunkSource source;
        LevelData data;
        private TestLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false); }
        @Override public RegistryAccess registryAccess() { return registries; }
        @Override public DedicatedServer getServer() { return server; }
        @Override public ServerChunkCache getChunkSource() { return source; }
        @Override public int getMinBuildHeight() { return settings.minY(); }
        @Override public int getHeight() { return settings.height(); }
        @Override public long getGameTime() { return 0; }
        @Override public long getSeed() { return 1234; }
        @Override public LevelData getLevelData() { return data; }
        @Override public DimensionType dimensionType() {
            return registries.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        }
    }

    private static class TestServer extends DedicatedServer {
        RegistryAccess.Frozen registries;
        private TestServer() { super(null, null, null, null, null, null, null); }
        @Override public RegistryAccess.Frozen registryAccess() { return registries; }
        @Override public net.minecraft.server.packs.resources.ResourceManager getResourceManager() { return null; }
    }

    private static class TestChunkSource extends ServerChunkCache {
        ThreadedLevelLightEngine light;
        private TestChunkSource() { super(null, null, null, null, null, 0, 0, false, null, null); }
        @Override public ThreadedLevelLightEngine getLightEngine() { return light; }
    }

    private static class TestPoi extends PoiManager {
        private TestPoi() { super(null, false, null, null); }
        @Override public void checkConsistencyWithBlocks(ChunkPos pos, LevelChunkSection section) { }
    }
}
