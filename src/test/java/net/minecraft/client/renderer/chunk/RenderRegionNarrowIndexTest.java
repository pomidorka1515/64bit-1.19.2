package net.minecraft.client.renderer.chunk;

import java.math.BigInteger;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderRegionNarrowIndexTest {
    @Test
    void narrowLookupMatchesIndependentArithmeticIncludingOverflow() {
        Random random = new Random(1441);
        long[] edges = {Long.MIN_VALUE, Long.MIN_VALUE + 1, -4097, -17, -16,
                -1, 0, 1, 15, 16, 4096, Long.MAX_VALUE};
        for (int i = 0; i < 10000; ++i) {
            long block = i < edges.length ? edges[i] : random.nextLong();
            long origin = i % 3 == 0 ? random.nextLong() : (block >> 4) + random.nextInt(9) - 4;
            check(block, origin, 4);
            check(block, Long.MIN_VALUE, 4);
            check(block, Long.MAX_VALUE, 4);
            check(block, block >> 4, 0);
        }
    }

    private static void check(long block, long origin, int size) {
        BigInteger delta = BigInteger.valueOf(block).shiftRight(4).subtract(BigInteger.valueOf(origin));
        int expected = delta.signum() >= 0 && delta.compareTo(BigInteger.valueOf(size)) < 0
                ? delta.intValueExact() : -1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Int1088.fromLong(block), 0, Int1088.fromLong(block)) {
            @Override public Int1088 getX() { throw new AssertionError("materialized X"); }
            @Override public Int1088 getZ() { throw new AssertionError("materialized Z"); }
        };
        for (boolean x : new boolean[]{false, true}) {
            assertEquals(expected, RenderChunkRegion.localBlockIndex(pos, x, Int1088.fromLong(origin), size));
        }
    }
}
