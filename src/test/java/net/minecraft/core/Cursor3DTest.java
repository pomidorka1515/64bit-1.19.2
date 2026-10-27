package net.minecraft.core;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Cursor3DTest {
    @Test
    void narrowSignCrossingsAndResetFromWideRetainEveryLimb() {
        Cursor3D cursor = new Cursor3D(Int1088.MIN, 0, Int1088.MAX, Int1088.MIN, 0, Int1088.MAX);
        cursor.reset(Int1088.fromLong(-1), -1, Int1088.fromLong(-1), Int1088.ONE, 1, Int1088.ONE);
        var blocks = BlockPos.betweenClosed(-1, -1, -1, 1, 1, 1).iterator();
        for (int z = -1; z <= 1; ++z) for (int y = -1; y <= 1; ++y) for (int x = -1; x <= 1; ++x) {
            assertTrue(cursor.advance());
            assertTrue(blocks.hasNext());
            BlockPos block = blocks.next();
            for (int limb = 0; limb < Int1088.LIMBS; ++limb) {
                assertEquals(Int1088.fromLong(x).limb(limb), cursor.nextXLimb(limb));
                assertEquals(Int1088.fromLong(z).limb(limb), cursor.nextZLimb(limb));
                assertEquals(cursor.nextXLimb(limb), block.xLimb(limb));
                assertEquals(cursor.nextZLimb(limb), block.zLimb(limb));
            }
            assertEquals(Int1088.fromLong(x), cursor.nextX());
            assertEquals(Int1088.fromLong(z), cursor.nextZ());
            assertEquals(y, block.getY());
            assertEquals((x == 0 ? 0 : 1) + (y == 0 ? 0 : 1) + (z == 0 ? 0 : 1), cursor.getNextType());
        }
        assertFalse(cursor.advance());
        assertFalse(blocks.hasNext());
        assertThrows(IndexOutOfBoundsException.class, () -> cursor.nextXLimb(17));
        assertThrows(IndexOutOfBoundsException.class, () -> cursor.nextZLimb(-1));
    }

    @Test
    void narrowIteratorOverwritesCallerMutationsOnLookahead() {
        var blocks = BlockPos.betweenClosed(1, 0, 1, 2, 0, 1).iterator();
        BlockPos.MutableBlockPos first = (BlockPos.MutableBlockPos) blocks.next();
        first.set(Int1088.MIN, 99, Int1088.MIN);
        assertTrue(blocks.hasNext());
        assertEquals(new BlockPos(2, 0, 1), first);
        assertSame(first, blocks.next());
        assertFalse(blocks.hasNext());
    }

    @Test
    void localOffsetsPreserveOrderAndBoundaryTypesAtEveryLimbSeam() {
        for (int bit = 64; bit <= 1024; bit += 64) {
            for (int sign : new int[] {-1, 1}) {
                BigInteger origin = BigInteger.ONE.shiftLeft(bit).multiply(BigInteger.valueOf(sign)).subtract(BigInteger.ONE);
                Int1088 first = value(origin), last = value(origin.add(BigInteger.TWO));
                Cursor3D cursor = new Cursor3D(first, -1, first, last, 1, last);
                for (int z = 0; z < 3; ++z) {
                    for (int y = 0; y < 3; ++y) {
                        for (int x = 0; x < 3; ++x) {
                            assertTrue(cursor.advance());
                            assertEquals(value(origin.add(BigInteger.valueOf(x))), cursor.nextX());
                            assertEquals(y - 1, cursor.nextY());
                            assertEquals(value(origin.add(BigInteger.valueOf(z))), cursor.nextZ());
                            assertEquals((x == 1 ? 0 : 1) + (y == 1 ? 0 : 1) + (z == 1 ? 0 : 1), cursor.getNextType());
                        }
                    }
                }
                assertFalse(cursor.advance());
                assertFalse(cursor.advance());
            }
        }
    }

    @Test
    void resetHandlesLongSpanBoundariesFullDomainAndEmptyAxes() {
        Cursor3D cursor = new Cursor3D(0, 0, 0, 0, 0, 0);
        for (BigInteger span : new BigInteger[] {BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE),
                BigInteger.valueOf(Long.MAX_VALUE), BigInteger.ONE.shiftLeft(63), BigInteger.ONE.shiftLeft(1088).subtract(BigInteger.ONE)}) {
            BigInteger origin = new BigInteger(Int1088.MIN.toString());
            cursor.reset(Int1088.MIN, Integer.MIN_VALUE, Int1088.ZERO, value(origin.add(span)), Integer.MAX_VALUE, Int1088.ZERO);
            for (int i = 0; i < 4; ++i) {
                assertTrue(cursor.advance());
                assertEquals(value(origin.add(BigInteger.valueOf(i))), cursor.nextX());
                assertEquals(Integer.MIN_VALUE, cursor.nextY());
                assertEquals(i == 0 ? 3 : 2, cursor.getNextType());
            }
        }
        for (int axis = 0; axis < 3; ++axis) {
            cursor.reset(Int1088.ZERO, 0, Int1088.ZERO, Int1088.fromLong(axis == 0 ? -1 : 0),
                    axis == 1 ? -1 : 0, Int1088.fromLong(axis == 2 ? -1 : 0));
            assertFalse(cursor.advance());
        }
    }

    @Test
    void bothDomainEdgesAndFullIntHeightTerminateWithoutWrapping() {
        for (Int1088 first : new Int1088[] {Int1088.MIN, Int1088.MAX.subtractSaturated(Int1088.ONE)}) {
            Int1088 last = first.addSaturated(Int1088.ONE);
            Cursor3D cursor = new Cursor3D(first, Integer.MAX_VALUE - 1, first, last, Integer.MAX_VALUE, last);
            int count = 0;
            while (cursor.advance()) {
                assertEquals(3, cursor.getNextType());
                assertTrue(cursor.nextX().compareTo(first) >= 0 && cursor.nextX().compareTo(last) <= 0);
                assertTrue(++count <= 8);
            }
            assertEquals(8, count);
            cursor.reset(last, Integer.MAX_VALUE, last, last, Integer.MAX_VALUE, last);
            assertTrue(cursor.advance());
            assertEquals(last, cursor.nextX());
            assertFalse(cursor.advance());
        }
    }

    private static Int1088 value(BigInteger value) { return Int1088.parseDecimal(value.toString()); }
}
