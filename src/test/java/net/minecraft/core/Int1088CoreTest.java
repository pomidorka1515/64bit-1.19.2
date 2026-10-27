package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

class Int1088CoreTest {
    @Test
    void ordinaryPositionOperationsKeepTheirNumericResults() {
        BlockPos pos = new BlockPos(-17, 64, 31);
        assertEquals(new Vec3i(-17, 64, 31), pos);
        assertEquals(new BlockPos(-15, 61, 35), pos.offset(2, -3, 4));
        assertEquals(new BlockPos(-19, 67, 27), pos.subtract(new Vec3i(2, -3, 4)));
        assertEquals(new BlockPos(-34, 128, 62), pos.multiply(2));
        assertEquals(new BlockPos(-14, 64, 31), pos.relative(Direction.EAST, 3));
        assertEquals(new BlockPos(-17, 64, 29), pos.relative(Direction.Axis.Z, -2));
        assertEquals(new BlockPos(-31, 64, -17), pos.rotate(Rotation.CLOCKWISE_90));
        assertEquals(new BlockPos(17, 64, -31), pos.rotate(Rotation.CLOCKWISE_180));
        assertEquals(new BlockPos(31, 64, 17), pos.rotate(Rotation.COUNTERCLOCKWISE_90));
        assertEquals(new BlockPos(-19, 64, 31), pos.offset(-1.1, 0.5, 0.9));
        assertEquals(new BlockPos(-2, 3, -5), new BlockPos(-1.1, 3.9, -4.1));
    }

    @Test
    void everyLimbParticipatesInPositionIdentityOrderingAndRehash() {
        Map<Vec3i, Integer> positions = new HashMap<>(1);
        for (int limb = 0; limb < Int1088.LIMBS; ++limb) {
            Int1088 value = Int1088.parseDecimal(BigInteger.ONE.shiftLeft(64 * limb).toString());
            Vec3i x = new Vec3i(value, 7, Int1088.ZERO);
            Vec3i z = new Vec3i(Int1088.ZERO, 7, value);
            assertNotEquals(new Vec3i(0, 7, 0), x);
            assertNotEquals(new Vec3i(0, 7, 0), z);
            assertTrue(x.compareTo(z) > 0);
            positions.put(x, 2 * limb);
            positions.put(z, 2 * limb + 1);
        }
        assertEquals(34, positions.size());
        positions.forEach((pos, index) -> {
            BlockPos copy = new BlockPos(pos);
            assertEquals(pos, copy);
            assertEquals(pos.hashCode(), copy.hashCode());
            assertEquals(0, pos.compareTo(copy));
            assertEquals(index, positions.get(copy));
        });
        assertTrue(new Vec3i(-1, 100, 100).compareTo(new Vec3i(0, -100, -100)) < 0);
        assertTrue(new Vec3i(0, -1, 100).compareTo(new Vec3i(0, 0, -100)) < 0);
        assertTrue(new Vec3i(0, 0, -1).compareTo(new Vec3i(0, 0, 0)) < 0);
    }

    @Test
    void wideOffsetsMutableCopiesAndRotationsNeverLoseUpperLimbsOrWrap() {
        Int1088 wide = Int1088.ONE.shiftLeftSaturated(1024);
        BlockPos pos = new BlockPos(wide, 12, wide.negate());
        assertEquals(new BlockPos(wide.addSaturated(Int1088.ONE), 12, wide.negate()), pos.east());
        assertEquals(new BlockPos(wide, 12, wide), pos.rotate(Rotation.CLOCKWISE_90));
        assertEquals(new BlockPos(0, 0, 0), pos.subtract(pos));
        BlockPos.MutableBlockPos mutable = pos.mutable();
        BlockPos snapshot = mutable.immutable();
        mutable.set(Int1088.MIN, 13, Int1088.MAX);
        assertEquals(pos, snapshot);
        assertEquals(new BlockPos(Int1088.MIN, 13, Int1088.MAX), mutable);
        assertEquals(Int1088.MAX, mutable.south().getZ());
        assertEquals(Int1088.MIN, mutable.west().getX());
        assertEquals(Int1088.MAX, mutable.rotate(Rotation.CLOCKWISE_180).getX());
        assertEquals(Int1088.MIN, new Vec3i(Int1088.MIN, 0, wide).multiply(2).getX());
        assertEquals(Int1088.MAX, new Vec3i(Int1088.MAX, 0, wide).offset(1, 0, 0).getX());
    }
}
