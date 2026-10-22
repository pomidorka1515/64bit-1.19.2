package net.minecraft.world.level;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.nbt.DoubleTag;
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
    void generationPlacementIsTranslationInvariantIncludingEdgeChunks() {
        for (Int128 origin : new Int128[] {Int128.ZERO, Int128.fromLong(1L << 60),
                Int128.fromLong(-(1L << 60)), Int128.of(1L, -16L), Int128.of(-2L, -16L),
                Int128.MIN, Int128.MAX.subtract(Int128.fromLong(15L))}) {
            for (int offset : new int[] {0, 1, 8, 15}) {
                Int128 coordinate = origin.add(Int128.fromLong(offset));
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
        for (Int128 coordinate : interiorCoordinates()) {
            SectorVec3 position = BaseSpawner.getSpawnPosition(new BlockPos(coordinate, 64, coordinate),
                    new ListTag(), -0.25D, 65.0D, 1.75D);
            assertEquals(coordinate.subtract(Int128.ONE), position.blockX());
            assertEquals(coordinate.add(Int128.ONE), position.blockZ());
            assertEquals(0.75D, position.subX());
            assertEquals(0.75D, position.subZ());
            assertEquals(65.0D, position.y());
        }
    }

    @Test
    void partialExplicitSpawnerPositionDoesNotApproximateUnspecifiedAxis() {
        ListTag explicit = new ListTag();
        explicit.add(DoubleTag.valueOf(-2.25D));
        Int128 huge = Int128.of(123L, 0L);
        SectorVec3 position = BaseSpawner.getSpawnPosition(new BlockPos(huge, 64, huge), explicit, 0.0D, 65.0D, 0.375D);
        assertEquals(Int128.fromLong(-3L), position.blockX());
        assertEquals(0.75D, position.subX());
        assertEquals(huge, position.blockZ());
        assertEquals(0.375D, position.subZ());
    }

    @Test
    void liquidObstructionUsesExactBlockAddresses() {
        for (Int128 coordinate : interiorCoordinates()) {
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
        SectorVec3 position = SectorVec3.fromBlockAndFraction(Int128.of(123L, -1L), 0.5D, 64.0D,
                Int128.of(-123L, 0L), 0.5D);
        var bounds = EntityType.PIG.getAABB(position).getSectorBounds();
        assertNotNull(bounds);
        var local = bounds.toLocalAABB(SectorPhysicsOrigin.from(position));
        assertEquals(EntityType.PIG.getWidth(), local.getXsize(), 1.0E-12D);
        assertEquals(EntityType.PIG.getHeight(), local.getYsize(), 1.0E-12D);
    }

    private static Int128[] interiorCoordinates() {
        return new Int128[] {Int128.fromLong(1L << 60), Int128.fromLong(-(1L << 60)),
                Int128.of(123L, 0L), Int128.of(-123L, -1L),
                Int128.MIN.add(Int128.fromLong(8L)), Int128.MAX.subtract(Int128.fromLong(8L))};
    }
}
