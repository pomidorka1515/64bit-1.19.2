package net.minecraft.commands;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.coordinates.WorldCoordinate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.commands.CloneCommands;
import net.minecraft.server.commands.FillCommand;
import net.minecraft.server.commands.ForceLoadCommand;
import net.minecraft.server.commands.ParticleCommand;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.server.commands.PlaySoundCommand;
import net.minecraft.server.commands.SetBlockCommand;
import net.minecraft.server.commands.SpreadPlayersCommand;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionPaths;
import net.minecraft.world.level.chunk.storage.RegionPos;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.ticks.LevelTicks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real command dispatch and chunk serialization; server services are isolated test doubles. */
class CommandCoordinateGateTest {
    @TempDir Path directory;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<BigInteger> matrix() {
        BigInteger one = BigInteger.ONE;
        return List.of(BigInteger.ZERO, one, one.negate(), one.shiftLeft(53).add(one),
                one.shiftLeft(127), one.shiftLeft(127).negate(), one.shiftLeft(1024),
                one.shiftLeft(1024).negate(), new BigDecimal(Double.MAX_VALUE).toBigIntegerExact(),
                one.shiftLeft(1087).subtract(one), one.shiftLeft(1087).negate());
    }

    @Test
    void fullParseFormatMatrixAndOutOfDomainNeighbors() throws Exception {
        for (BigInteger integer : matrix()) {
            String text = integer.toString();
            WorldCoordinate parsed = WorldCoordinate.parseInt(new StringReader(text));
            assertEquals(text, parsed.getAbsoluteBlock().toDecimalString());
            assertEquals(text, parsed.toString());
            assertEquals(parsed, WorldCoordinate.parseInt(new StringReader(parsed.toString())));
            String decimal = text + ".25";
            if (integer.equals(BigInteger.ONE.shiftLeft(1087).negate())) decimal = text + ".0";
            WorldCoordinate fraction = WorldCoordinate.parseDouble(new StringReader(decimal), false);
            BigDecimal expected = new BigDecimal(decimal);
            BigInteger floor = expected.setScale(0, java.math.RoundingMode.FLOOR).toBigIntegerExact();
            assertEquals(floor.toString(), fraction.getAbsoluteBlock().toDecimalString());
            assertEquals(expected.subtract(new BigDecimal(floor)).doubleValue(), fraction.getAbsoluteFraction());
            assertEquals(fraction, WorldCoordinate.parseDouble(new StringReader(fraction.toString()), false));
        }
        for (BigInteger outside : List.of(BigInteger.ONE.shiftLeft(1087),
                BigInteger.ONE.shiftLeft(1087).negate().subtract(BigInteger.ONE))) {
            assertThrows(CommandSyntaxException.class, () -> WorldCoordinate.parseInt(new StringReader(outside.toString())));
            assertThrows(CommandSyntaxException.class, () -> WorldCoordinate.parseDouble(new StringReader(outside.toString()), false));
        }
    }

    @Test
    void blockCommandsModifyExactChunksAndReloadFromNestedAnvil() throws Exception {
        TestLevel level = TestLevel.create();
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(level);
        int index = 0;
        for (BigInteger integer : matrix()) {
            Int1088 axis = Int1088.parseDecimal(integer.toString());
            BlockPos block = new BlockPos(axis, 100, axis);
            level.chunks.clear();
            level.chunk(block);
            Source source = new Source(level, null, SectorVec3.fromBlockAndFraction(axis, 0, 100, axis, 0));
            String pos = position(block);
            assertEquals(1, dispatcher.execute("setblock " + pos + " stone", source));
            assertEquals(Blocks.STONE.defaultBlockState(), level.getBlockState(block));
            TranslatableContents feedback = (TranslatableContents) source.messages.get(0).getContents();
            assertEquals("commands.setblock.success", feedback.getKey());
            assertEquals(integer.toString(), feedback.getArgs()[0].toString());
            assertEquals(integer.toString(), feedback.getArgs()[2].toString());
            // Vertical ranges keep the horizontal edge address intact, including MAX/MIN.
            assertEquals(2, dispatcher.execute("fill " + pos + " " + position(block.above()) + " gold_block", source));
            assertEquals(2, dispatcher.execute("clone " + pos + " " + position(block.above()) + " " + position(block.above(3)), source));
            for (int y : new int[] {100, 101, 103, 104})
                assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), level.getBlockState(new BlockPos(axis, y, axis)));
            assertEquals(1, dispatcher.execute("setblock ~ ~6 ~ diamond_block", source));
            assertEquals(1, dispatcher.execute("setblock ^ ^7 ^ emerald_block", source));
            assertEquals(Blocks.DIAMOND_BLOCK.defaultBlockState(), level.getBlockState(block.above(6)));
            assertEquals(Blocks.EMERALD_BLOCK.defaultBlockState(), level.getBlockState(block.above(7)));

            ChunkPos chunkPos = new ChunkPos(block);
            CompoundTag written = ChunkSerializer.write(level, level.chunks.get(chunkPos));
            Path root = directory.resolve("command-world-" + index++);
            try (TestStorage storage = new TestStorage(root)) {
                storage.store(chunkPos, written).join();
                storage.synchronize(true).join();
            }
            assertTrue(Files.isRegularFile(RegionPaths.regionFile(root, new RegionPos(chunkPos.getRegionX(), chunkPos.getRegionZ()))));
            level.chunks.clear();
            try (TestStorage storage = new TestStorage(root)) {
                CompoundTag encoded = storage.loadAsync(chunkPos).join().orElseThrow();
                assertEquals(written, encoded);
                ProtoChunk loaded = ChunkSerializer.read(level, allocate(TestPoi.class), chunkPos, encoded);
                assertEquals(chunkPos, loaded.getPos());
                level.chunks.put(chunkPos, loaded);
                for (int y : new int[] {100, 101, 103, 104})
                    assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), loaded.getBlockState(new BlockPos(axis, y, axis)));
                assertEquals(Blocks.DIAMOND_BLOCK.defaultBlockState(), loaded.getBlockState(block.above(6)));
                assertEquals(Blocks.EMERALD_BLOCK.defaultBlockState(), loaded.getBlockState(block.above(7)));
                assertEquals(written, ChunkSerializer.write(level, loaded));
            }
            assertEquals(1, dispatcher.execute("forceload add " + axis + " " + axis, source));
            assertTrue(level.forced.contains(chunkPos));
            assertEquals(1, dispatcher.execute("forceload remove " + axis + " " + axis, source));
            assertFalse(level.forced.contains(chunkPos));
        }
    }

    @Test
    void summonTeleportSelectorsSpreadAndTemplateUseExactPositions() throws Exception {
        TestLevel level = TestLevel.create();
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(level);
        for (BigInteger integer : matrix()) {
            Int1088 axis = Int1088.parseDecimal(integer.toString());
            SectorVec3 origin = SectorVec3.fromBlockAndFraction(axis, 0.25, 100, axis, 0.75);
            Source source = new Source(level, null, origin);
            assertEquals(1, dispatcher.execute("summon minecraft:marker " + axis + " 100 " + axis, source));
            Entity entity = level.spawned;
            assertEquals(integer.toString(), entity.exactPosition().blockX().toDecimalString());
            assertEquals(integer.toString(), entity.exactPosition().blockZ().toDecimalString());
            assertEquals(0.5, entity.exactPosition().subX());
            Source self = new Source(level, entity, entity.exactPosition());
            assertEquals(1, dispatcher.execute("tp @s[x=" + axis + ",z=" + axis + ",distance=..1] ~.25 ~1 ~-.25", self));
            assertEquals(SectorVec3.fromBlockAndFraction(axis, 0.75, 101, axis, 0.25), entity.exactPosition());

            BlockPos templatePos = new BlockPos(axis, 110, axis);
            assertEquals(1, dispatcher.execute("place template test:single " + position(templatePos), source));
            assertEquals(Blocks.DIAMOND_BLOCK.defaultBlockState(), level.getBlockState(templatePos));
            TranslatableContents feedback = (TranslatableContents) source.messages.get(source.messages.size() - 1).getContents();
            assertEquals("commands.place.template.success", feedback.getKey());
            assertEquals(integer.toString(), feedback.getArgs()[1].toString());
            assertEquals(integer.toString(), feedback.getArgs()[3].toString());

            // Spread's terrain and ticket adapter are isolated, not its wide center/local-offset arithmetic.
            MovingEntity target = new MovingEntity(level);
            target.moveTo(origin, 0, 0);
            level.flatFloor = true;
            try {
                assertEquals(1, dispatcher.execute("spreadplayers " + axis + " " + axis + " 0 1 under 105 false @s",
                        new Source(level, target, origin)));
            } finally {
                level.flatFloor = false;
            }
            BigInteger min = BigInteger.ONE.shiftLeft(1087).negate();
            BigInteger max = min.negate().subtract(BigInteger.ONE);
            for (Int1088 result : new Int1088[] {target.exactPosition().blockX(), target.exactPosition().blockZ()}) {
                BigInteger actual = new BigInteger(result.toDecimalString());
                assertTrue(actual.compareTo(integer.subtract(BigInteger.ONE).max(min)) >= 0);
                assertTrue(actual.compareTo(integer.add(BigInteger.ONE).min(max)) <= 0);
            }
            assertEquals(101, target.getY());
        }
    }

    @Test
    void particleAndSoundCommandsDeliverSplitPositionsToTheirConsumers() throws Exception {
        TestLevel level = TestLevel.create();
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(level);
        TestPlayer player = allocate(TestPlayer.class);
        CapturingConnection connection = allocate(CapturingConnection.class);
        player.connection = connection;
        for (BigInteger integer : matrix()) {
            Int1088 axis = Int1088.parseDecimal(integer.toString());
            SectorVec3 position = SectorVec3.fromBlockAndFraction(axis, 0.25, 100, axis, 0.75);
            var field = Entity.class.getDeclaredField("sectorPosition");
            field.setAccessible(true);
            field.set(player, position);
            Source source = new Source(level, player, position);
            assertEquals(1, dispatcher.execute("particle minecraft:flame ~.5 ~1 ~-.5 0 0 0 0 1 force @s", source));
            assertEquals(SectorVec3.fromBlockAndFraction(axis, 0.75, 101, axis, 0.25), level.particle);
            assertEquals(1, dispatcher.execute("playsound minecraft:entity.pig.ambient master @s " + axis + " 100 " + axis, source));
            ClientboundCustomSoundPacket sound = assertInstanceOf(ClientboundCustomSoundPacket.class, connection.packet);
            assertEquals(axis, sound.getExactPosition().blockX());
            assertEquals(axis, sound.getExactPosition().blockZ());
            assertEquals(0.5, sound.getExactPosition().subX());
            assertEquals(0.5, sound.getExactPosition().subZ());
        }
    }

    @Test
    void dispatcherRejectsOutOfDomainCoordinatesBeforeWorldMutation() throws Exception {
        TestLevel level = TestLevel.create();
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(level);
        Source source = new Source(level, null, SectorVec3.fromBlockAndFraction(0, 0, 100, 0, 0));
        for (BigInteger outside : List.of(BigInteger.ONE.shiftLeft(1087),
                BigInteger.ONE.shiftLeft(1087).negate().subtract(BigInteger.ONE))) {
            String pos = outside + " 100 0";
            for (String command : List.of("setblock " + pos + " stone", "fill " + pos + " 0 100 0 stone",
                    "clone 0 100 0 0 100 0 " + pos, "summon minecraft:marker " + pos,
                    "place template test:single " + pos, "forceload add " + outside + " 0")) {
                assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(command, source), command);
            }
        }
        assertTrue(level.chunks.isEmpty());
        assertTrue(level.forced.isEmpty());
        assertNull(level.spawned);
        assertTrue(source.messages.isEmpty());
    }

    private static String position(BlockPos pos) { return pos.getX() + " " + pos.getY() + " " + pos.getZ(); }

    private static CommandDispatcher<CommandSourceStack> dispatcher(TestLevel level) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CommandBuildContext context = new CommandBuildContext(level.registryAccess());
        SetBlockCommand.register(dispatcher, context);
        FillCommand.register(dispatcher, context);
        CloneCommands.register(dispatcher, context);
        TeleportCommand.register(dispatcher);
        SummonCommand.register(dispatcher);
        SpreadPlayersCommand.register(dispatcher);
        PlaceCommand.register(dispatcher);
        ForceLoadCommand.register(dispatcher);
        ParticleCommand.register(dispatcher);
        PlaySoundCommand.register(dispatcher);
        return dispatcher;
    }

    private static class Source extends CommandSourceStack {
        final List<Component> messages = new ArrayList<>();
        Source(TestLevel level, Entity entity, SectorVec3 position) {
            super(null, position.toExactVec3(), Vec2.ZERO, level, 2, "test", Component.literal("test"), level.getServer(), entity);
        }
        @Override public void sendSuccess(Component message, boolean broadcast) { messages.add(message); }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static class MovingEntity extends Entity {
        MovingEntity(ServerLevel level) { super(EntityType.PIG, level); }
        @Override public Component getDisplayName() { return Component.literal("test"); }
        protected void defineSynchedData() { }
        protected void readAdditionalSaveData(CompoundTag tag) { }
        protected void addAdditionalSaveData(CompoundTag tag) { }
        public Packet<?> getAddEntityPacket() { throw new UnsupportedOperationException(); }
    }

    private static class TestPlayer extends ServerPlayer {
        private TestPlayer() { super(null, null, null, null); }
        @Override public Component getDisplayName() { return Component.literal("listener"); }
    }

    private static class CapturingConnection extends ServerGamePacketListenerImpl {
        Packet<?> packet;
        private CapturingConnection() { super(null, null, null); }
        @Override public void send(Packet<?> packet) { this.packet = packet; }
    }

    private static class TestStorage extends IOWorker {
        TestStorage(Path root) { super(root, true, "command-gate"); }
    }

    private static class TestLevel extends ServerLevel {
        RegistryAccess.Frozen registries;
        TestServer server;
        TestChunkSource source;
        Map<ChunkPos, ProtoChunk> chunks;
        Set<ChunkPos> forced;
        LevelTicks<Block> ticks;
        TestTemplates templates;
        net.minecraft.world.level.storage.LevelData data;
        net.minecraft.server.ServerScoreboard scoreboard;
        Entity spawned;
        SectorVec3 particle;
        boolean flatFloor;
        private TestLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false); }

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
            level.chunks = new HashMap<>();
            level.forced = new HashSet<>();
            level.scoreboard = new net.minecraft.server.ServerScoreboard(null);
            level.data = (net.minecraft.world.level.storage.LevelData) java.lang.reflect.Proxy.newProxyInstance(
                    net.minecraft.world.level.storage.LevelData.class.getClassLoader(),
                    new Class<?>[] {net.minecraft.world.level.storage.LevelData.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getGameTime")) return 0L;
                        throw new AssertionError("Unexpected level-data call: " + method);
                    });
            level.ticks = new LevelTicks<>(pos -> true, () -> InactiveProfiler.INSTANCE);
            level.templates = allocate(TestTemplates.class);
            CompoundTag tag = new CompoundTag();
            ListTag size = new ListTag();
            for (int i = 0; i < 3; i++) size.add(IntTag.valueOf(1));
            tag.put("size", size);
            ListTag palette = new ListTag();
            CompoundTag state = new CompoundTag();
            state.putString("Name", "minecraft:diamond_block");
            palette.add(state);
            tag.put("palette", palette);
            CompoundTag block = new CompoundTag();
            ListTag local = new ListTag();
            for (int i = 0; i < 3; i++) local.add(IntTag.valueOf(0));
            block.put("pos", local);
            block.putInt("state", 0);
            ListTag blocks = new ListTag();
            blocks.add(block);
            tag.put("blocks", blocks);
            level.templates.template = new StructureTemplate();
            level.templates.template.load(tag);
            return level;
        }

        ProtoChunk chunk(BlockPos pos) {
            return chunks.computeIfAbsent(new ChunkPos(pos), key -> new ProtoChunk(key, UpgradeData.EMPTY, this,
                    registries.registryOrThrow(Registry.BIOME_REGISTRY), null));
        }
        @Override public BlockState getBlockState(BlockPos pos) {
            if (flatFloor) return (pos.getY() <= 100 ? Blocks.STONE : Blocks.AIR).defaultBlockState();
            return chunk(pos).getBlockState(pos);
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) {
            return chunk(pos).setBlockState(pos, state, false) != state;
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public void blockUpdated(BlockPos pos, Block block) { }
        @Override public boolean hasChunkAt(BlockPos pos) { return true; }
        @Override public boolean hasChunksAt(BlockPos from, BlockPos to) { return true; }
        @Override public boolean isLoaded(BlockPos pos) { return true; }
        @Override public net.minecraft.world.level.chunk.LevelChunk getChunk(Int1088 x, Int1088 z) { return null; }
        @Override public boolean setChunkForced(Int1088 x, Int1088 z, boolean add) {
            return add ? forced.add(new ChunkPos(x, z)) : forced.remove(new ChunkPos(x, z));
        }
        @Override public boolean tryAddFreshEntityWithPassengers(Entity entity) { spawned = entity; return true; }
        @Override public <T extends ParticleOptions> boolean sendParticles(ServerPlayer player, T particle, boolean force,
                SectorVec3 position, int count, double x, double y, double z, double speed) {
            this.particle = position;
            return true;
        }
        @Override public LevelTicks<Block> getBlockTicks() { return ticks; }
        @Override public StructureTemplateManager getStructureManager() { return templates; }
        @Override public RegistryAccess registryAccess() { return registries; }
        @Override public DedicatedServer getServer() { return server; }
        @Override public ServerChunkCache getChunkSource() { return source; }
        @Override public int getMinBuildHeight() { return 0; }
        @Override public int getHeight() { return 128; }
        @Override public long getGameTime() { return 0; }
        @Override public net.minecraft.world.level.storage.LevelData getLevelData() { return data; }
        @Override public net.minecraft.server.ServerScoreboard getScoreboard() { return scoreboard; }
        @Override public long getSeed() { return 0; }
        @Override public RandomSource getRandom() { return RandomSource.create(1088); }
        @Override public net.minecraft.resources.ResourceKey<Level> dimension() { return Level.OVERWORLD; }
        @Override public DimensionType dimensionType() {
            return registries.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        }
    }

    private static class TestTemplates extends StructureTemplateManager {
        StructureTemplate template;
        private TestTemplates() { super(null, null); }
        @Override public Optional<StructureTemplate> get(ResourceLocation id) {
            assertEquals(new ResourceLocation("test:single"), id);
            return Optional.of(template);
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
        @Override public <T> void addRegionTicket(net.minecraft.server.level.TicketType<T> type, ChunkPos pos, int distance, T value) { }
    }

    private static class TestPoi extends PoiManager {
        private TestPoi() { super(null, false, null, null); }
        @Override public void checkConsistencyWithBlocks(ChunkPos pos, LevelChunkSection section) { }
    }
}
