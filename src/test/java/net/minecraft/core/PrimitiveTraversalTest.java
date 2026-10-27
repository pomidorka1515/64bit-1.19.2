package net.minecraft.core;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrimitiveTraversalTest {
    @Test
    void cursorAndBlockTraversalCarryAcrossEveryLimbWithoutAliasing() {
        for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
            java.math.BigInteger boundary = java.math.BigInteger.ONE.shiftLeft(64 * limb);
            for (int sign : new int[] {-1, 1}) {
                java.math.BigInteger start = boundary.multiply(java.math.BigInteger.valueOf(sign))
                        .subtract(java.math.BigInteger.ONE);
                Int1088 first = Int1088.parseDecimal(start.toString());
                Int1088 last = Int1088.parseDecimal(start.add(java.math.BigInteger.TWO).toString());
                Cursor3D cursor = new Cursor3D(first, 9, first, last, 9, first);
                var blocks = BlockPos.betweenClosed(first, 9, first, last, 9, first).iterator();
                for (int offset = 0; offset < 3; ++offset) {
                    Int1088 expected = Int1088.parseDecimal(start.add(java.math.BigInteger.valueOf(offset)).toString());
                    assertTrue(cursor.advance());
                    assertEquals(expected, cursor.nextX());
                    assertEquals(first, cursor.nextZ());
                    assertEquals(new BlockPos(expected, 9, first), blocks.next().immutable());
                }
                assertFalse(cursor.advance());
                assertFalse(blocks.hasNext());
            }
        }
    }

    @Test
    void cursorResetSwitchesBetweenLocalWideAndEmptyRanges() {
        Cursor3D cursor = new Cursor3D(0, 0, 0, 2, 2, 2);
        int count = 0;
        while (cursor.advance()) {
            int type = (cursor.nextXLimb(0) == 1 ? 0 : 1)
                    + (cursor.nextY() == 1 ? 0 : 1) + (cursor.nextZLimb(0) == 1 ? 0 : 1);
            assertEquals(type, cursor.getNextType());
            ++count;
        }
        assertEquals(27, count);
        cursor.reset(Int1088.MIN, Integer.MIN_VALUE, Int1088.MIN,
                Int1088.MAX, Integer.MAX_VALUE, Int1088.MAX);
        for (int i = 0; i < 3; ++i) {
            assertTrue(cursor.advance());
            assertEquals(Int1088.MIN.add(Int1088.fromLong(i)), cursor.nextX());
        }
        cursor.reset(Int1088.ONE, 0, Int1088.ZERO, Int1088.ZERO, 0, Int1088.ZERO);
        assertFalse(cursor.advance());
        cursor.reset(Int1088.MAX, Integer.MAX_VALUE, Int1088.MAX,
                Int1088.MAX, Integer.MAX_VALUE, Int1088.MAX);
        assertTrue(cursor.advance());
        assertEquals(Int1088.MAX, cursor.nextX());
        assertFalse(cursor.advance());
    }

    @Test
    void blockIteratorPreservesReuseExhaustionAndWideRanges() {
        var iterable = BlockPos.betweenClosed(0, 0, 0, 1, 0, 0);
        var iterator = iterable.iterator();
        BlockPos first = iterator.next();
        assertTrue(iterator.hasNext());
        assertEquals(1, first.xLimb(0));
        assertSame(first, iterator.next());
        assertEquals(1, first.xLimb(0));
        assertFalse(iterator.hasNext());
        assertThrows(java.util.NoSuchElementException.class, iterator::next);
        assertEquals(0, iterable.iterator().next().xLimb(0));
        var wide = BlockPos.betweenClosed(Int1088.MIN, Integer.MIN_VALUE, Int1088.MIN,
                Int1088.MAX, Integer.MAX_VALUE, Int1088.MAX).iterator();
        for (int i = 0; i < 3; ++i) {
            assertEquals(Int1088.MIN.add(Int1088.fromLong(i)), wide.next().getX());
        }
    }

    @Test
    void chunkHashesUseEveryLimbAndRemainConsistent() {
        ChunkPos base = new ChunkPos(123, -456);
        assertEquals(base.hashCode(), ChunkPos.hash(123, -456));
        assertEquals(base.hashCode(), ChunkPos.hash(base.x(), base.z()));
        assertNotEquals(base.hashCode(), new ChunkPos(Int1088.of(123, 1), base.z()).hashCode());
        assertNotEquals(base.hashCode(), new ChunkPos(base.x(), Int1088.of(-456, -2)).hashCode());
        java.util.Set<Integer> hashes = new java.util.HashSet<>();
        for (int x = -16; x <= 16; ++x) {
            for (int z = -16; z <= 16; ++z) hashes.add(new ChunkPos(x, z).hashCode());
        }
        assertTrue(hashes.size() > 1000);
    }

    @Test
    void traversalMatchesExactOrderAcrossCarriesAndEndpoints() {
        for (Int1088 start : List.of(Int1088.MIN, Int1088.of(-1, -4), Int1088.of(-1, 7),
                Int1088.MAX.subtract(Int1088.ONE))) {
            Int1088 end = start.add(Int1088.ONE);
            Cursor3D cursor = new Cursor3D(start, Integer.MAX_VALUE - 1, start,
                    end, Integer.MAX_VALUE, end);
            List<BlockPos> expected = new ArrayList<>();
            for (int z = 0; z < 2; ++z) {
                for (int y = 0; y < 2; ++y) {
                    for (int x = 0; x < 2; ++x) {
                        BlockPos pos = new BlockPos(start.add(Int1088.fromLong(x)),
                                Integer.MAX_VALUE - 1 + y, start.add(Int1088.fromLong(z)));
                        expected.add(pos);
                        assertTrue(cursor.advance());
                        assertEquals(pos.getX(), cursor.nextX());
                        assertEquals(pos.getZ(), cursor.nextZ());
                        assertEquals(pos.getY(), cursor.nextY());
                        assertEquals(3, cursor.getNextType());
                    }
                }
            }
            assertFalse(cursor.advance());
            assertFalse(cursor.advance());
            List<BlockPos> actual = new ArrayList<>();
            BlockPos.betweenClosed(start, Integer.MAX_VALUE - 1, start,
                    end, Integer.MAX_VALUE, end).forEach(pos -> actual.add(pos.immutable()));
            assertEquals(expected, actual);
        }
    }
}
