package net.minecraft.client.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.math.BigInteger;
import net.minecraft.core.Int128;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class ViewAreaTest {
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
            assertTrue(WorldBounds.isValidChunkCoordinate(chunk));
            chunks.add(chunk);
            slots.add(ViewArea.floorMod(chunk, width));
        }

        assertEquals(width, chunks.size());
        assertEquals(width, slots.size());
        assertEquals(expectedFirst.add(Int128.fromLong(width - 1L)), first.add(Int128.fromLong(width - 1L)));
    }
}
