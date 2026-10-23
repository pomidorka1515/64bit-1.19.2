package net.minecraft.client.gui.components;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DebugOverlayOptimizationTest {
    @Test
    void coordinateFormattingMatchesDecimalReference() {
        Int128[] blocks = {Int128.MIN, Int128.MAX, Int128.fromLong(-123), Int128.fromLong(-1), Int128.ZERO, Int128.ONE};
        double[] fractions = {0, -0.0, Double.MIN_VALUE, 1e-7, 0.0005, 0.0015, 0.1235, 0.5, 0.9995, Math.nextDown(1.0)};
        Random random = new Random(42);
        for (Int128 block : blocks) {
            for (double fraction : fractions) checkCoordinate(block, fraction);
            for (int i = 0; i < 1000; i++) checkCoordinate(block, random.nextDouble());
        }
    }

    private static void checkCoordinate(Int128 block, double fraction) {
        assertEquals(SectorVec3.formatCoordinate(block, fraction, 3), DebugScreenOverlay.formatCoordinate(block, fraction),
                () -> block + " + " + fraction);
    }

    @Test
    void unchangedPositionReusesStringsAndChangesInvalidateOnlyRelevantAxes() {
        DebugScreenOverlay.PositionDisplayCache cache = new DebugScreenOverlay.PositionDisplayCache();
        SectorVec3 position = SectorVec3.fromBlockAndFraction(Int128.MAX, 0.125, 10.25, Int128.MIN, 0.5);
        cache.update(position);
        String x = cache.xLine;
        String z = cache.zLine;
        String y = cache.yLine;
        String halves = cache.xHalves;
        String sub = cache.subLine;
        cache.update(SectorVec3.fromBlockAndFraction(Int128.MAX, 0.125, 10.25, Int128.MIN, 0.5));
        assertSame(x, cache.xLine);
        assertSame(z, cache.zLine);
        assertSame(y, cache.yLine);
        assertSame(halves, cache.xHalves);
        assertSame(sub, cache.subLine);
        cache.update(SectorVec3.fromBlockAndFraction(Int128.MAX, 0.25, 11.5, Int128.MIN, 0.5));
        assertNotEquals(x, cache.xLine);
        assertSame(z, cache.zLine);
        assertSame(halves, cache.xHalves);
        assertEquals("Y: 11.50000", cache.yLine);
    }

    @Test
    void regionCacheHandlesNegativeBoundariesAndHighLimbs() {
        DebugScreenOverlay.RegionDisplayCache cache = new DebugScreenOverlay.RegionDisplayCache();
        cache.update(new BlockPos(-1, 0, -1));
        assertEquals("Region X: -1", cache.firstLine);
        String line = cache.firstLine;
        cache.update(new BlockPos(-16, 0, -16));
        assertSame(line, cache.firstLine);
        cache.update(new BlockPos(-512, 0, -512));
        assertSame(line, cache.firstLine);
        cache.update(new BlockPos(-513, 0, -513));
        assertEquals("Region X: -2", cache.firstLine);
        cache.update(new BlockPos(Int128.MAX, 0, Int128.MIN));
        assertEquals("Region X: " + Int128.MAX.shiftRight(9), cache.firstLine);
        assertEquals("Region Z: " + Int128.MIN.shiftRight(9), cache.secondLine);
    }

    @Test
    void memoryCacheUsesDisplayedValuesAsKeys() {
        long mb = 1024L * 1024L;
        DebugScreenOverlay.MemoryDisplayCache cache = new DebugScreenOverlay.MemoryDisplayCache();
        cache.update(5 * mb, 100 * mb, 200 * mb, 7 * mb);
        assertEquals("Mem:   2% 005/200MB", cache.memoryLine);
        assertEquals("Allocated:  50% 100MB", cache.allocatedLine);
        assertEquals("Allocation rate: 007MB /s", cache.rateLine);
        String memory = cache.memoryLine;
        String allocated = cache.allocatedLine;
        String rate = cache.rateLine;
        cache.update(5 * mb + 1, 100 * mb + 1, 200 * mb, 7 * mb + 1);
        assertSame(memory, cache.memoryLine);
        assertSame(allocated, cache.allocatedLine);
        assertSame(rate, cache.rateLine);
        cache.update(6 * mb, 101 * mb, 200 * mb, 8 * mb);
        assertEquals("Mem:   3% 006/200MB", cache.memoryLine);
        assertEquals("Allocated:  50% 101MB", cache.allocatedLine);
        assertEquals("Allocation rate: 008MB /s", cache.rateLine);
    }
}
