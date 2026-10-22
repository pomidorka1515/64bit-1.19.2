package net.minecraft.core;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrimitiveTraversalTest {
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
