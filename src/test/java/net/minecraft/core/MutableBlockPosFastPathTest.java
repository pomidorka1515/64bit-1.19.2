package net.minecraft.core;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MutableBlockPosFastPathTest {
    private static final BigInteger MIN = BigInteger.ONE.shiftLeft(1087).negate();
    private static final BigInteger MAX = MIN.negate().subtract(BigInteger.ONE);

    @Test
    void narrowOffsetsAndAliasingMatchIndependentSaturatedArithmetic() {
        Random random = new Random(14004);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 10000; ++i) {
            long x = random.nextLong(), z = random.nextLong();
            int y = random.nextInt(), dy = random.nextInt();
            long dx = i % 2 == 0 ? random.nextLong() : random.nextInt(33) - 16;
            long dz = i % 2 == 0 ? random.nextLong() : random.nextInt(33) - 16;
            BlockPos source = new BlockPos(x, y, z);
            cursor.set(source);
            cursor.setWithOffset(cursor, dx, dy, dz);
            check(cursor, sum(source.getX(), Int1088.fromLong(dx)), addY(y, dy),
                    sum(source.getZ(), Int1088.fromLong(dz)));
            cursor.setWithOffset(source, new BlockPos(dx, dy, dz));
            check(cursor, sum(source.getX(), Int1088.fromLong(dx)), addY(y, dy),
                    sum(source.getZ(), Int1088.fromLong(dz)));
        }
    }

    @Test
    void wideTransitionsSaturationAndAllAliasCombinationsKeepCanonicalCounts() {
        Int1088[] values = {Int1088.MIN, Int1088.MAX, Int1088.of(-1, 0),
                Int1088.of(0, -1), Int1088.ONE.shiftLeftSaturated(1024),
                Int1088.fromLong(Long.MIN_VALUE), Int1088.fromLong(Long.MAX_VALUE),
                Int1088.ZERO, Int1088.fromLong(-1)};
        for (Int1088 x : values) for (Int1088 z : values) {
            BlockPos source = new BlockPos(x, Integer.MAX_VALUE, z);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, source.getY(), z);
            check(cursor, x, source.getY(), z);
            cursor.set(cursor);
            check(cursor, x, source.getY(), z);
            BlockPos snapshot = cursor.immutable();
            cursor.setWithOffset(cursor, cursor);
            check(cursor, sum(x, x), Integer.MAX_VALUE, sum(z, z));
            cursor.set(source);
            cursor.setWithOffset(source, cursor);
            check(cursor, sum(x, x), Integer.MAX_VALUE, sum(z, z));
            cursor.set(source);
            cursor.setWithOffset(cursor, source);
            check(cursor, sum(x, x), Integer.MAX_VALUE, sum(z, z));
            for (long offset : new long[]{-1, 1, Long.MIN_VALUE, Long.MAX_VALUE}) {
                cursor.set(source).setWithOffset(cursor, offset, 1, offset);
                check(cursor, sum(x, Int1088.fromLong(offset)), Integer.MAX_VALUE,
                        sum(z, Int1088.fromLong(offset)));
            }
            cursor.setX(7).setZ(-8).setY(-1);
            check(cursor, Int1088.fromLong(7), -1, Int1088.fromLong(-8));
            assertEquals(source, snapshot);
            cursor.setX(x).setZ(z);
            check(cursor, x, -1, z);
            cursor.move(Direction.UP).clamp(Direction.Axis.Y, -1, 1);
            check(cursor, x, 0, z);
        }
    }

    @Test
    void iteratorsMaintainCountsAfterDirectWritesAndExternalCursorMutation() {
        for (Int1088 origin : new Int1088[]{Int1088.ZERO, Int1088.of(-1, 7),
                Int1088.MAX.addSaturated(-1), Int1088.MIN}) {
            var iterator = BlockPos.betweenClosed(origin, 0, origin,
                    origin.addSaturated(1), 0, origin.addSaturated(1)).iterator();
            BlockPos.MutableBlockPos cursor = (BlockPos.MutableBlockPos) iterator.next();
            check(cursor, origin, 0, origin);
            cursor.set(Int1088.of(15, 1), 12, Int1088.of(15, -1));
            cursor = (BlockPos.MutableBlockPos) iterator.next();
            check(cursor, origin.addSaturated(1), 0, origin);
        }
    }

    @Test
    void narrowCursorsDoNotAllocateArithmeticScratchUntilWideFallback() throws Exception {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(1, 2, 3);
        for (int i = 0; i < 100; ++i) {
            cursor.move(1, 0, -1).set(cursor).setWithOffset(cursor, new BlockPos(1, 0, -1));
        }
        for (String name : new String[]{"scratchX", "scratchZ", "scratchOther"}) {
            var field = BlockPos.MutableBlockPos.class.getDeclaredField(name);
            field.setAccessible(true);
            assertNull(field.get(cursor));
        }
        cursor.set(Int1088.MAX, 0, Int1088.MIN).move(1, 0, -1);
        check(cursor, Int1088.MAX, 0, Int1088.MIN);
    }

    private static int addY(int first, int second) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, (long) first + second));
    }

    private static Int1088 sum(Int1088 first, Int1088 second) {
        BigInteger value = new BigInteger(first.toString()).add(new BigInteger(second.toString()));
        return Int1088.parseDecimal(value.max(MIN).min(MAX).toString());
    }

    private static void check(BlockPos.MutableBlockPos cursor, Int1088 x, int y, Int1088 z) {
        assertEquals(x, cursor.getX());
        assertEquals(z, cursor.getZ());
        assertEquals(y, cursor.getY());
        assertEquals(x.limbCount(), cursor.xLimbCount());
        assertEquals(z.limbCount(), cursor.zLimbCount());
        assertEquals(x.fitsLong(), cursor.xFitsLong());
        assertEquals(z.fitsLong(), cursor.zFitsLong());
        BlockPos expected = new BlockPos(x, y, z);
        assertEquals(expected, cursor);
        assertEquals(cursor, expected);
        assertEquals(expected.hashCode(), cursor.hashCode());
        for (int limb = 0; limb < Int1088.LIMBS; ++limb) {
            assertEquals(x.limb(limb), cursor.xLimb(limb));
            assertEquals(z.limb(limb), cursor.zLimb(limb));
        }
    }
}
