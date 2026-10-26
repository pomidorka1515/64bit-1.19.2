package net.minecraft.client.gui.components;

import net.minecraft.core.Int128;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionDisplayInt128Test {
    @Test
    void exactFormatAndNegativeFloor() {
        DebugScreenOverlay.RegionDisplayCache cache = new DebugScreenOverlay.RegionDisplayCache();
        cache.update(new ChunkPos(-1L, 32L));
        assertEquals("Region X: -1", cache.firstLine);
        assertEquals("Region Z: 1", cache.secondLine);
        assertEquals(" in r.ffffffffffffffffffffffffffffffff.00000000000000000000000000000001.mca", cache.thirdLine);
    }

    @Test
    void cachesUntilFullWidthRegionPairChanges() {
        DebugScreenOverlay.RegionDisplayCache cache = new DebugScreenOverlay.RegionDisplayCache();
        cache.update(new ChunkPos(0L, 0L));
        String first = cache.firstLine;
        String second = cache.secondLine;
        String third = cache.thirdLine;
        cache.update(new ChunkPos(31L, 31L));
        assertSame(first, cache.firstLine);
        assertSame(second, cache.secondLine);
        assertSame(third, cache.thirdLine);
        cache.update(new ChunkPos(Int128.of(1L, 0L), Int128.ZERO));
        assertNotEquals(first, cache.firstLine);
        assertEquals("Region X: 576460752303423488", cache.firstLine);
        assertEquals("Region Z: 0", cache.secondLine);
        assertEquals(" in r.00000000000000000800000000000000.00000000000000000000000000000000.mca", cache.thirdLine);
        cache.update(new ChunkPos(Int128.of(1L, 31L), Int128.ZERO));
        String high = cache.firstLine;
        cache.update(new ChunkPos(Int128.of(1L, 30L), Int128.ZERO));
        assertSame(high, cache.firstLine);
    }

    @Test
    void fullBlockDomainRegionEndpoints() {
        DebugScreenOverlay.RegionDisplayCache cache = new DebugScreenOverlay.RegionDisplayCache();
        cache.update(new ChunkPos(Int128.MIN.shiftRight(4), Int128.MAX.shiftRight(4)));
        assertEquals("Region X: -332306998946228968225951765070086144", cache.firstLine);
        assertEquals("Region Z: 332306998946228968225951765070086143", cache.secondLine);
        assertEquals(" in r.ffc00000000000000000000000000000.003fffffffffffffffffffffffffffff.mca", cache.thirdLine);
    }
}
