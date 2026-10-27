package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class SectorPhysicsOriginTest {
    @Test
    void localConversionsCrossEveryLimbSeamWithoutProjectingAbsoluteCoordinates() {
        for (int bit = 64; bit <= 1024; bit += 64) {
            for (int sign : new int[] {-1, 1}) {
                Int1088 seam = Int1088.ONE.shiftLeftSaturated(bit);
                if (sign < 0) seam = seam.negate();
                SectorVec3 position = SectorVec3.fromBlockAndFraction(seam.subtract(Int1088.ONE), 0.75D,
                        -10.25D, seam, 0.25D);
                SectorPhysicsOrigin origin = SectorPhysicsOrigin.from(position);
                assertEquals(-11, origin.originBlockY());
                assertEquals(position.blockX(), origin.originBlockX());
                assertEquals(position.blockZ(), origin.originBlockZ());
                assertEquals(new Vec3(0.75D, 0.75D, 0.25D), origin.toLocal(position));
                assertEquals(new Vec3(1.0D, 2.0D, -1.0D),
                        origin.toLocal(new BlockPos(seam, -9, seam.subtract(Int1088.ONE))));
                assertEquals(new Vec3(1.25D, 1.25D, -0.25D),
                        origin.toLocal(position.add(0.5D, 0.5D, -0.5D)));
            }
        }
    }

    @Test
    void safeSpanIsInclusiveOnBothAxesAndInBothDirections() {
        Int1088 wide = Int1088.ONE.shiftLeftSaturated(1024);
        SectorPhysicsOrigin origin = new SectorPhysicsOrigin(wide, Integer.MIN_VALUE, wide.negate());
        for (int sign : new int[] {-1, 1}) {
            Int1088 limit = Int1088.fromLong(sign * WorldBounds.MAX_SAFE_LOCAL_SPAN);
            Int1088 outside = Int1088.fromLong(sign * (WorldBounds.MAX_SAFE_LOCAL_SPAN + 1L));
            Int1088 x = wide.add(limit);
            Int1088 z = wide.negate().add(limit);
            assertTrue(origin.isSafe(x, z));
            assertEquals(new Vec3(sign * (double) WorldBounds.MAX_SAFE_LOCAL_SPAN,
                    4294967295.0D, sign * (double) WorldBounds.MAX_SAFE_LOCAL_SPAN),
                    origin.toLocal(new BlockPos(x, Integer.MAX_VALUE, z)));
            assertFalse(origin.isSafe(wide.add(outside), wide.negate()));
            assertFalse(origin.isSafe(wide, wide.negate().add(outside)));
            assertThrows(IllegalArgumentException.class,
                    () -> origin.toLocal(new BlockPos(wide.add(outside), 0, wide.negate())));
            assertThrows(IllegalArgumentException.class, () -> origin.toLocal(
                    SectorVec3.fromBlockAndFraction(wide, 0.5D, 0, wide.negate().add(outside), 0.5D)));
        }
    }

    @Test
    void oppositeEdgesAreNeverLocalAndInwardEdgeMovementIsExact() {
        for (Int1088 edge : new Int1088[] {Int1088.MIN, Int1088.MAX}) {
            SectorPhysicsOrigin origin = new SectorPhysicsOrigin(edge, 100, edge);
            double inward = edge.equals(Int1088.MIN) ? 1.0D : -1.0D;
            SectorVec3 position = SectorVec3.fromBlockAndFraction(edge, 0.25D, 100.5D, edge, 0.75D);
            assertEquals(new Vec3(inward + 0.25D, 0.5D, inward + 0.75D),
                    origin.toLocal(position.add(inward, 0, inward)));
            Int1088 opposite = edge.equals(Int1088.MIN) ? Int1088.MAX : Int1088.MIN;
            assertFalse(origin.isSafe(opposite, edge));
            assertFalse(origin.isSafe(edge, opposite));
            assertThrows(IllegalArgumentException.class,
                    () -> origin.toLocal(new BlockPos(opposite, 100, opposite)));
        }
    }

    @Test
    void originKeysRetainEveryLimbOnEitherAxis() {
        Map<SectorPhysicsOrigin, Integer> origins = new HashMap<>();
        SectorPhysicsOrigin zero = new SectorPhysicsOrigin(0, 64, 0);
        origins.put(zero, -1);
        int index = 0;
        for (int bit = 64; bit <= 1024; bit += 64) {
            for (int sign : new int[] {-1, 1}) {
                Int1088 coordinate = Int1088.ONE.shiftLeftSaturated(bit);
                if (sign < 0) coordinate = coordinate.negate();
                origins.put(new SectorPhysicsOrigin(coordinate, 64, Int1088.ZERO), index++);
                origins.put(new SectorPhysicsOrigin(Int1088.ZERO, 64, coordinate), index++);
            }
        }
        assertEquals(65, origins.size());
        for (Map.Entry<SectorPhysicsOrigin, Integer> entry : origins.entrySet()) {
            SectorPhysicsOrigin key = entry.getKey();
            SectorPhysicsOrigin copy = new SectorPhysicsOrigin(key.originBlockX(), key.originBlockY(), key.originBlockZ());
            assertEquals(key, copy);
            assertEquals(key.hashCode(), copy.hashCode());
            assertEquals(entry.getValue(), origins.get(copy));
            assertNotEquals(key, new SectorPhysicsOrigin(key.originBlockX(), 65, key.originBlockZ()));
        }
    }
}
