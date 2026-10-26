package net.minecraft.core;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrimitiveTraversalTest {
    @Test
    void cursorResetSwitchesBetweenLocalWideAndEmptyRanges() {
        Cursor3D cursor = new Cursor3D(0, 0, 0, 2, 2, 2);
        int count = 0;
        while (cursor.advance()) {
            int type = (cursor.nextXLo() == 1 ? 0 : 1)
                    + (cursor.nextY() == 1 ? 0 : 1) + (cursor.nextZLo() == 1 ? 0 : 1);
            assertEquals(type, cursor.getNextType());
            ++count;
        }
        assertEquals(27, count);
        cursor.reset(Int128.MIN, Integer.MIN_VALUE, Int128.MIN,
                Int128.MAX, Integer.MAX_VALUE, Int128.MAX);
        for (int i = 0; i < 3; ++i) {
            assertTrue(cursor.advance());
            assertEquals(Int128.MIN.add(Int128.fromLong(i)), cursor.nextX());
        }
        cursor.reset(Int128.ONE, 0, Int128.ZERO, Int128.ZERO, 0, Int128.ZERO);
        assertFalse(cursor.advance());
        cursor.reset(Int128.MAX, Integer.MAX_VALUE, Int128.MAX,
                Int128.MAX, Integer.MAX_VALUE, Int128.MAX);
        assertTrue(cursor.advance());
        assertEquals(Int128.MAX, cursor.nextX());
        assertFalse(cursor.advance());
    }

    @Test
    void blockIteratorPreservesReuseExhaustionAndWideRanges() {
        var iterable = BlockPos.betweenClosed(0, 0, 0, 1, 0, 0);
        var iterator = iterable.iterator();
        BlockPos first = iterator.next();
        assertTrue(iterator.hasNext());
        assertEquals(1, first.getXLo());
        assertSame(first, iterator.next());
        assertEquals(1, first.getXLo());
        assertFalse(iterator.hasNext());
        assertThrows(java.util.NoSuchElementException.class, iterator::next);
        assertEquals(0, iterable.iterator().next().getXLo());
        var wide = BlockPos.betweenClosed(Int128.MIN, Integer.MIN_VALUE, Int128.MIN,
                Int128.MAX, Integer.MAX_VALUE, Int128.MAX).iterator();
        for (int i = 0; i < 3; ++i) {
            assertEquals(Int128.MIN.add(Int128.fromLong(i)), wide.next().getX());
        }
    }

    @Test
    void chunkHashesUseEveryLimbAndRemainConsistent() {
        ChunkPos base = new ChunkPos(123, -456);
        assertEquals(base.hashCode(), ChunkPos.hash(123, -456));
        assertEquals(base.hashCode(), ChunkPos.hash(base.x(), base.z()));
        assertNotEquals(base.hashCode(), new ChunkPos(Int128.of(1, 123), base.z()).hashCode());
        assertNotEquals(base.hashCode(), new ChunkPos(base.x(), Int128.of(-2, -456)).hashCode());
        java.util.Set<Integer> hashes = new java.util.HashSet<>();
        for (int x = -16; x <= 16; ++x) {
            for (int z = -16; z <= 16; ++z) hashes.add(new ChunkPos(x, z).hashCode());
        }
        assertTrue(hashes.size() > 1000);
    }

    @Test
    void traversalMatchesExactOrderAcrossCarriesAndEndpoints() {
        for (Int128 start : List.of(Int128.MIN, Int128.of(-4, -1), Int128.of(7, -1),
                Int128.MAX.subtract(Int128.ONE))) {
            Int128 end = start.add(Int128.ONE);
            Cursor3D cursor = new Cursor3D(start, Integer.MAX_VALUE - 1, start,
                    end, Integer.MAX_VALUE, end);
            List<BlockPos> expected = new ArrayList<>();
            for (int z = 0; z < 2; ++z) {
                for (int y = 0; y < 2; ++y) {
                    for (int x = 0; x < 2; ++x) {
                        BlockPos pos = new BlockPos(start.add(Int128.fromLong(x)),
                                Integer.MAX_VALUE - 1 + y, start.add(Int128.fromLong(z)));
                        expected.add(pos);
                        assertTrue(cursor.advance());
                        assertEquals(pos.getX(), Int128.of(cursor.nextXHi(), cursor.nextXLo()));
                        assertEquals(pos.getZ(), Int128.of(cursor.nextZHi(), cursor.nextZLo()));
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
