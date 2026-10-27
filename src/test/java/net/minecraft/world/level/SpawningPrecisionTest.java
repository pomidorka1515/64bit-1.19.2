package net.minecraft.world.level;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.SectorPhysicsOrigin;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SpawningPrecisionTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void explicitSpawnerPositionDecodesFullWidthCoordinates() {
        for (Int1088 coordinate : interiorCoordinates()) {
            CompoundTag tag = new CompoundTag();
            NbtUtils.writeInt1088(tag, "BlockX", coordinate);
            NbtUtils.writeInt1088(tag, "BlockZ", coordinate);
            tag.putDouble("SubX", 0.25D);
            tag.putDouble("SubZ", 0.75D);
            tag.putDouble("Y", 64.5D);
            assertEquals(SectorVec3.fromBlockAndFraction(coordinate, 0.25D, 64.5D, coordinate, 0.75D),
                    BaseSpawner.getExactSpawnPosition(tag));
        }
    }

    @Test
    void spawnerDisplayLoadsIdOnlyTemplateWithoutMutatingSpawnData() {
        BaseSpawner spawner = new BaseSpawner() {
            public void broadcastEvent(Level level, BlockPos pos, int eventId) {}
        };
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:marker");
        spawner.setNextSpawnData(null, BlockPos.ZERO, new SpawnData(tag, java.util.Optional.empty()));
        Level level = net.minecraft.world.entity.EntityLoadingTestLevel.create();
        assertNotNull(spawner.getOrCreateDisplayEntity(level));
        assertEquals(1, tag.size());
        assertSame(spawner.getOrCreateDisplayEntity(level), spawner.getOrCreateDisplayEntity(level));
    }

    @Test
    void generationPlacementIsTranslationInvariantIncludingEdgeChunks() {
        for (Int1088 origin : new Int1088[] {Int1088.ZERO, Int1088.fromLong(1L << 60),
                Int1088.fromLong(-(1L << 60)), Int1088.of(-16L, 1L), Int1088.of(-16L, -2L),
                Int1088.MIN, Int1088.MAX.subtract(Int1088.fromLong(15L))}) {
            for (int offset : new int[] {0, 1, 8, 15}) {
                Int1088 coordinate = origin.add(Int1088.fromLong(offset));
                SectorVec3 position = NaturalSpawner.getGenerationSpawnPosition(coordinate, 64,
                        coordinate, origin, origin, 0.6F);
                double expected = Math.max((double) 0.6F, offset);
                assertEquals(expected, position.toLocal(origin, 64, origin).x, 1.0E-12D);
                assertEquals(expected, position.toLocal(origin, 64, origin).z, 1.0E-12D);
                assertEquals(origin.shiftRight(4), position.blockX().shiftRight(4));
                assertEquals(origin.shiftRight(4), position.blockZ().shiftRight(4));
            }
        }
    }

    @Test
    void spawnerOffsetsKeepFractionsAndNegativeFloorSemantics() {
        for (Int1088 coordinate : interiorCoordinates()) {
            SectorVec3 position = BaseSpawner.getSpawnPosition(new BlockPos(coordinate, 64, coordinate),
                    new ListTag(), -0.25D, 65.0D, 1.75D);
            assertEquals(coordinate.subtract(Int1088.ONE), position.blockX());
            assertEquals(coordinate.add(Int1088.ONE), position.blockZ());
            assertEquals(0.75D, position.subX());
            assertEquals(0.75D, position.subZ());
            assertEquals(65.0D, position.y());
        }
    }

    @Test
    void partialExplicitDoublePositionIsRejectedInsteadOfReconstructingAnExactAxis() {
        ListTag explicit = new ListTag();
        explicit.add(DoubleTag.valueOf(-2.25D));
        Int1088 huge = Int1088.of(0L, 123L);
        assertThrows(IllegalArgumentException.class, () -> BaseSpawner.getSpawnPosition(
                new BlockPos(huge, 64, huge), explicit, 0.0D, 65.0D, 0.375D));
    }

    @Test
    void malformedExactSpawnerDataNeverFallsBackToApproximatePos() {
        for (Int1088 coordinate : new Int1088[] {Int1088.ONE.shiftLeftSaturated(127),
                Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX}) {
            CompoundTag exact = new CompoundTag();
            NbtUtils.writeInt1088(exact, "BlockX", coordinate);
            NbtUtils.writeInt1088(exact, "BlockZ", coordinate);
            exact.putDouble("SubX", 0.25D);
            exact.putDouble("SubZ", 0.75D);
            exact.putDouble("Y", 64.5D);
            assertEquals(coordinate, BaseSpawner.getExactSpawnPosition(exact).blockX());
            for (String key : new String[] {"BlockX", "BlockZ", "SubX", "SubZ", "Y"}) {
                CompoundTag invalid = exact.copy();
                invalid.remove(key);
                assertThrows(IllegalArgumentException.class, () -> BaseSpawner.getExactSpawnPosition(invalid));
            }
        }
        BaseSpawner spawner = new BaseSpawner() {
            public void broadcastEvent(Level level, BlockPos pos, int eventId) {}
        };
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:marker");
        tag.putString("ExactPos", "malformed");
        spawner.setNextSpawnData(null, BlockPos.ZERO, new SpawnData(tag, java.util.Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> spawner.getOrCreateDisplayEntity(
                net.minecraft.world.entity.EntityLoadingTestLevel.create()));
    }

    @Test
    void liquidObstructionUsesExactBlockAddresses() {
        for (Int1088 coordinate : interiorCoordinates()) {
            BlockPos water = new BlockPos(coordinate, 64, coordinate);
            LevelReader level = (LevelReader) java.lang.reflect.Proxy.newProxyInstance(LevelReader.class.getClassLoader(),
                    new Class<?>[] {LevelReader.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getFluidState")) {
                            return water.equals(args[0]) ? net.minecraft.world.level.material.Fluids.WATER.defaultFluidState()
                                    : net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
                        }
                        return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                    });
            SectorVec3 position = SectorVec3.fromBlockAndFraction(coordinate, 0.5D, 64.0D, coordinate, 0.5D);
            assertTrue(level.containsAnyLiquid(EntityType.PIG.getAABB(position)));
            assertFalse(level.containsAnyLiquid(EntityType.PIG.getAABB(position.add(1.0D, 0.0D, 0.0D))));
        }
    }

    @Test
    void spawnBoundsPreserveDimensionsBeyondDoublePrecision() {
        SectorVec3 position = SectorVec3.fromBlockAndFraction(Int1088.of(-1L, 123L), 0.5D, 64.0D,
                Int1088.of(0L, -123L), 0.5D);
        var bounds = EntityType.PIG.getAABB(position).getSectorBounds();
        assertNotNull(bounds);
        var local = bounds.toLocalAABB(SectorPhysicsOrigin.from(position));
        assertEquals(EntityType.PIG.getWidth(), local.getXsize(), 1.0E-12D);
        assertEquals(EntityType.PIG.getHeight(), local.getYsize(), 1.0E-12D);
    }

    private static Int1088[] interiorCoordinates() {
        return new Int1088[] {Int1088.fromLong(1L << 60), Int1088.fromLong(-(1L << 60)),
                Int1088.of(0L, 123L), Int1088.of(-1L, -123L),
                Int1088.MIN.add(Int1088.fromLong(8L)), Int1088.MAX.subtract(Int1088.fromLong(8L))};
    }
}
