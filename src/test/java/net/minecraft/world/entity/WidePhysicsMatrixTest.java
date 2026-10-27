package net.minecraft.world.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorAABB;
import net.minecraft.world.phys.SectorPhysicsOrigin;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Automated local-world gate, not a substitute for live client/server acceptance. */
class WidePhysicsMatrixTest {
    @TempDir Path directory;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void movesMatchIndependentSplitOracleAndSurviveTheEntityLifecycle() throws Exception {
        TestLevel level = level();
        TestEntity vehicle = new TestEntity(level);
        TestEntity passenger = new TestEntity(level);
        vehicle.noPhysics = true;
        int fixture = 0;
        for (Int1088 coordinate : SectorEntityCoordinatesTest.coordinates()) {
            SectorVec3 start = SectorEntityCoordinatesTest.position(coordinate);
            for (Vec3 delta : new Vec3[] {new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                    new Vec3(0.5, 0, -0.5), new Vec3(-0.5, 0, 0.5),
                    new Vec3(0x1.0p53, 0, -0x1.0p53), new Vec3(-0x1.0p53, 0, 0x1.0p53),
                    new Vec3(1, 0.5, 1)}) {
                vehicle.moveTo(start, 0, 0);
                vehicle.move(MoverType.SELF, delta);
                assertAxis(start.blockX(), start.subX(), delta.x,
                        vehicle.exactPosition().blockX(), vehicle.exactPosition().subX());
                assertAxis(start.blockZ(), start.subZ(), delta.z,
                        vehicle.exactPosition().blockZ(), vehicle.exactPosition().subZ());
                assertEquals(start.y() + delta.y, vehicle.exactPosition().y());
            }
            assertTrue(passenger.startRiding(vehicle, true));
            vehicle.teleportTo(start);
            assertEquals(start.withY(start.y() + vehicle.getPassengersRidingOffset()
                    + passenger.getMyRidingOffset()), passenger.exactPosition());
            passenger.stopRiding();
            SectorVec3 destination = start.add(-0.5, 1, 0.5);
            passenger.dismountToExact(destination);
            assertFalse(passenger.isPassenger());
            assertEquals(destination, passenger.exactPosition());
            assertEquals(destination, passenger.oldExactPosition());

            CompoundTag tag = new CompoundTag();
            passenger.saveWithoutId(tag);
            Path file = directory.resolve("entity-" + fixture++ + ".nbt");
            NbtIo.writeCompressed(tag, file.toFile());
            TestEntity restored = new TestEntity(level);
            restored.load(NbtIo.readCompressed(file.toFile()));
            assertEquals(destination, restored.exactPosition());
            assertEquals(destination, restored.oldExactPosition());
            assertEquals(destination.blockPosition(), restored.blockPosition());
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new ClientboundAddEntityPacket(restored).write(buffer);
                ClientboundAddEntityPacket packet = new ClientboundAddEntityPacket(buffer);
                TestEntity receiver = new TestEntity(level);
                receiver.recreateFromPacket(packet);
                assertEquals(destination, receiver.exactPosition());
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void pointPlayerLargeAndSeamBoxesStayLocalAndLandOnTheSameFloor() throws Exception {
        TestLevel level = level();
        for (Int1088 coordinate : SectorEntityCoordinatesTest.coordinates()) {
            SectorVec3 position = SectorVec3.fromBlockAndFraction(coordinate, 0.5, 64, coordinate.not(), 0.5);
            SectorPhysicsOrigin origin = SectorPhysicsOrigin.from(position);
            for (double[] size : new double[][] {{0, 0}, {0.6, 1.8}, {10, 10}, {2, 2}}) {
                SectorAABB box = SectorAABB.around(position, size[0], size[1]);
                assertTrue(origin.isSafe(box.minBlockX(), box.minBlockZ()));
                assertTrue(origin.isSafe(box.maxBlockX(), box.maxBlockZ()));
                AABB local = box.toLocalAABB(origin);
                assertTrue(Double.isFinite(local.getXsize()));
                assertTrue(local.getXsize() <= size[0] + 1.0E-12);
                assertTrue(local.getZsize() <= size[0] + 1.0E-12);
                if (size[0] == 0) continue; // A point has no collision volume.
                Vec3 movement = new Vec3(0.5, -0.5, -0.5);
                Vec3 resolved = Entity.collideSectorBoundingBox(null, movement, box, local, level, List.of(), origin);
                assertEquals(new Vec3(0.5, 0, -0.5), resolved);
            }
        }
    }

    @Test
    void realEntitySolverClipsWallsAndStepsOntoSlabsAcrossTheMatrix() throws Exception {
        TestLevel level = level();
        TestEntity entity = new TestEntity(level);
        entity.onGround = true;
        Method solve = Entity.class.getDeclaredMethod("collideSector", Vec3.class);
        solve.setAccessible(true);
        for (Int1088 coordinate : SectorEntityCoordinatesTest.coordinates()) {
            SectorVec3 start = SectorVec3.fromBlockAndFraction(coordinate, 0.5, 64, coordinate.not(), 0.5);
            entity.moveTo(start, 0, 0);
            int direction = coordinate.equals(Int1088.MAX) ? -1 : 1;
            level.obstacle = start.blockPosition().offset(direction, 0, 0);
            level.state = Blocks.STONE.defaultBlockState();
            entity.maxUpStep = 0;
            Vec3 requested = new Vec3(direction, 0, 0);
            Vec3 clipped = (Vec3) solve.invoke(entity, requested);
            assertEquals(direction * (0.5 - entity.getBbWidth() / 2.0), clipped.x, 1.0E-7);
            assertEquals(0, clipped.y);
            assertEquals(0, clipped.z);
            level.state = Blocks.OAK_SLAB.defaultBlockState();
            entity.maxUpStep = 0.6F;
            Vec3 stepped = (Vec3) solve.invoke(entity, requested);
            assertEquals(direction, stepped.x, 1.0E-7);
            assertEquals(0.5, stepped.y, 1.0E-7);
            assertEquals(0, stepped.z);
            assertEquals(start, entity.exactPosition(), "A solve returns a delta without changing state");
        }
    }

    private static void assertAxis(Int1088 block, double fraction, double delta,
            Int1088 actualBlock, double actualFraction) {
        // The split contract rounds fraction + delta to double before extracting carry.
        double rounded = new BigDecimal(fraction).add(new BigDecimal(delta)).doubleValue();
        BigDecimal expected = new BigDecimal(new BigInteger(block.toDecimalString()))
                .add(new BigDecimal(rounded));
        BigInteger floor = expected.setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
        BigInteger min = BigInteger.ONE.shiftLeft(1087).negate();
        BigInteger max = BigInteger.ONE.shiftLeft(1087).subtract(BigInteger.ONE);
        assertEquals(Int1088.parseDecimal(floor.max(min).min(max).toString()), actualBlock);
        double sub = floor.compareTo(min) < 0 ? 0 : floor.compareTo(max) > 0 ? Math.nextDown(1.0)
                : expected.subtract(new BigDecimal(floor)).doubleValue();
        assertEquals(sub, actualFraction);
    }

    private static TestLevel level() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        TestLevel level = (TestLevel) unsafe.allocateInstance(TestLevel.class);
        level.source = (TestChunkSource) unsafe.allocateInstance(TestChunkSource.class);
        level.source.chunk = (TestChunk) unsafe.allocateInstance(TestChunk.class);
        level.source.chunk.fixture = level;
        return level;
    }

    private static class TestEntity extends Entity {
        TestEntity(TestLevel level) { super(EntityType.PIG, level); }
        protected void defineSynchedData() {}
        protected void readAdditionalSaveData(CompoundTag tag) {}
        protected void addAdditionalSaveData(CompoundTag tag) {}
        public Packet<?> getAddEntityPacket() { return new ClientboundAddEntityPacket(this); }
    }

    // Only collision queries are supported. No generation, ticking or server services.
    private static class TestLevel extends ServerLevel {
        private BlockPos obstacle;
        private BlockState state;
        private TestChunkSource source;
        private TestLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false); }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public int getHeight() { return 384; }
        @Override public ServerChunkCache getChunkSource() { return source; }
        @Override public BlockGetter getChunkForCollisions(Int1088 x, Int1088 z) { return this; }
        @Override public BlockGetter getChunkForCollisions(long x, long z) {
            throw new AssertionError("Collision lookup must retain full-width axes");
        }
        @Override public BlockState getBlockState(BlockPos pos) {
            if (pos.equals(obstacle)) return state;
            return pos.getY() == 63 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        @Override public List<VoxelShape> getSectorEntityCollisions(Entity entity, SectorAABB box,
                AABB local, SectorPhysicsOrigin origin) { return List.of(); }
    }

    private static class TestChunkSource extends ServerChunkCache {
        private TestChunk chunk;
        private TestChunkSource() { super(null, null, null, null, null, 0, 0, false, null, null); }
        @Override public LevelChunk getChunkNow(Int1088 x, Int1088 z) { return chunk; }
    }

    private static class TestChunk extends LevelChunk {
        private TestLevel fixture;
        private TestChunk() { super(null, (net.minecraft.world.level.ChunkPos) null); }
        @Override public BlockState getBlockState(BlockPos position) { return fixture.getBlockState(position); }
    }
}
