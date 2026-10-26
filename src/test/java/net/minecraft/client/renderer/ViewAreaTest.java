package net.minecraft.client.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.math.BigInteger;
import net.minecraft.core.Int128;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class ViewAreaTest {
    @Test
    void smallInvalidationsVisitOnlyTheirExactSections() {
        for (Int128 first : new Int128[] {Int128.fromLong(-1), Int128.of(7, -1), Int128.of(-7, -1),
                WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK.subtract(Int128.ONE)}) {
            Set<String> visited = new HashSet<>();
            assertTrue(ViewArea.forEachDirtySection(first, -1, first, first.add(Int128.ONE), 0,
                    first.add(Int128.ONE), 15000, (x, y, z) -> {
                        assertTrue(x.equals(first) || x.equals(first.add(Int128.ONE)));
                        assertTrue(z.equals(first) || z.equals(first.add(Int128.ONE)));
                        assertTrue(y == -1 || y == 0);
                        assertTrue(visited.add(x + ":" + y + ":" + z));
                    }));
            assertEquals(8, visited.size());
        }
    }

    @Test
    void largeInvalidationsFallBackBeforeVisitingAnySections() {
        ViewArea.DirtySectionConsumer unexpected = (x, y, z) -> {
            throw new AssertionError("Oversized range must use the bounded scan");
        };
        assertFalse(ViewArea.forEachDirtySection(WorldBounds.MIN_CHUNK, -4, WorldBounds.MIN_CHUNK,
                WorldBounds.MAX_CHUNK, 19, WorldBounds.MAX_CHUNK, 15000, unexpected));
        assertFalse(ViewArea.forEachDirtySection(Int128.ZERO, 0, Int128.ZERO,
                Int128.fromLong(99), 0, Int128.fromLong(99), 9999, unexpected));
        assertTrue(ViewArea.forEachDirtySection(Int128.ZERO, 1, Int128.ZERO,
                Int128.ZERO, 0, Int128.ZERO, 0, unexpected));
    }

    @Test
    void exactLookupBudgetIsAcceptedWithoutOverflow() {
        int[] visits = {0};
        assertTrue(ViewArea.forEachDirtySection(Int128.ZERO, -4, Int128.ZERO,
                Int128.ONE, 19, Int128.ONE, 96, (x, y, z) -> ++visits[0]));
        assertEquals(96, visits[0]);
    }

    @Test
    void paddedBlockUpdatesIncludeBoundaryNeighborsWithoutWorldEdgeWraparound() {
        for (Int128 block : new Int128[] {Int128.ZERO, Int128.fromLong(-1), Int128.fromLong(15),
                Int128.of(7, 0), WorldBounds.MIN_BLOCK, WorldBounds.MAX_BLOCK}) {
            Int128 min = WorldBounds.addBlockOffset(block, -1).shiftRight(4);
            Int128 max = WorldBounds.addBlockOffset(block, 1).shiftRight(4);
            Set<Int128> visited = new HashSet<>();
            assertTrue(ViewArea.forEachDirtySection(min, 0, min, max, 0, max, 8, (x, y, z) -> {
                assertTrue(WorldBounds.isValidChunk(x, z));
                visited.add(x);
            }));
            assertTrue(visited.contains(block.shiftRight(4)));
            assertEquals(block.equals(WorldBounds.MIN_BLOCK) || block.equals(WorldBounds.MAX_BLOCK) ? 1 : 2,
                    visited.size());
        }
    }

    @Test
    void positiveWorldEdgeUsesAUniqueLegalRenderWindow() {
        assertContiguousWindow(WorldBounds.MAX_CHUNK, 33, WorldBounds.MAX_CHUNK.subtract(Int128.fromLong(32)));
    }

    @Test
    void aNearPositiveWorldEdgeShiftsTheFullWindowInsideTheWorld() {
        assertContiguousWindow(WorldBounds.MAX_CHUNK.subtract(Int128.ONE), 33, WorldBounds.MAX_CHUNK.subtract(Int128.fromLong(32)));
    }

    @Test
    void negativeWorldEdgeUsesAUniqueLegalRenderWindow() {
        assertContiguousWindow(WorldBounds.MIN_CHUNK, 33, WorldBounds.MIN_CHUNK);
    }

    @Test
    void highHalvesAndLowWordSeamsKeepUniqueGridSlots() {
        for (Int128 center : new Int128[] {Int128.of(7, -8), Int128.of(-7, 8), Int128.fromLong(-1)}) {
            assertContiguousWindow(center, 33, center.subtract(Int128.fromLong(16)));
        }
    }

    @Test
    void periodicIndicesUseTheFullSignedCoordinate() {
        for (Int128 value : new Int128[] {Int128.MIN, Int128.MAX, Int128.of(1, 0), Int128.of(-1, 0), Int128.fromLong(-1)}) {
            BigInteger exact = new BigInteger(value.toDecimalString());
            for (int period : new int[] {1, 3, 33, 65, 24576})
                assertEquals(exact.mod(BigInteger.valueOf(period)).intValue(), ViewArea.floorMod(value, period));
        }
    }

    private static void assertContiguousWindow(Int128 center, int width, Int128 expectedFirst) {
        Int128 first = ViewArea.firstChunkInView(center, width);
        assertEquals(expectedFirst, first);

        Set<Int128> chunks = new HashSet<>();
        Set<Integer> slots = new HashSet<>();
        for (int offset = 0; offset < width; ++offset) {
            Int128 chunk = first.add(Int128.fromLong(offset));
            assertTrue(chunk.compareTo(WorldBounds.MIN_CHUNK) >= 0);
            assertTrue(chunk.compareTo(WorldBounds.MAX_CHUNK) <= 0);
            chunks.add(chunk);
            slots.add(ViewArea.floorMod(chunk, width));
        }

        assertEquals(width, chunks.size());
        assertEquals(width, slots.size());
        assertEquals(expectedFirst.add(Int128.fromLong(width - 1L)), first.add(Int128.fromLong(width - 1L)));
    }
}
