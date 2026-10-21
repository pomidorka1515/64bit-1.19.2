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
        assertEquals("Region: -1 1 in r.ffffffffffffffffffffffffffffffff.00000000000000000000000000000001.mca", cache.firstLine);
        assertEquals(" (r.-1.1.mca)", cache.secondLine);
    }

    @Test
    void cachesUntilFullWidthRegionPairChanges() {
        DebugScreenOverlay.RegionDisplayCache cache = new DebugScreenOverlay.RegionDisplayCache();
        cache.update(new ChunkPos(0L, 0L));
        String first = cache.firstLine;
        String second = cache.secondLine;
        cache.update(new ChunkPos(31L, 31L));
        assertSame(first, cache.firstLine);
        assertSame(second, cache.secondLine);
        cache.update(new ChunkPos(Int128.of(1L, 0L), Int128.ZERO));
        assertNotEquals(first, cache.firstLine);
        assertEquals(" (r.576460752303423488.0.mca)", cache.secondLine);
        cache.update(new ChunkPos(Int128.of(1L, 31L), Int128.ZERO));
        String high = cache.firstLine;
        cache.update(new ChunkPos(Int128.of(1L, 30L), Int128.ZERO));
        assertSame(high, cache.firstLine);
    }

    @Test
    void fullBlockDomainRegionEndpoints() {
        DebugScreenOverlay.RegionDisplayCache cache = new DebugScreenOverlay.RegionDisplayCache();
        cache.update(new ChunkPos(Int128.MIN.shiftRight(4), Int128.MAX.shiftRight(4)));
        assertEquals(" (r.-332306998946228968225951765070086144.332306998946228968225951765070086143.mca)", cache.secondLine);
        assertEquals("Region: -332306998946228968225951765070086144 332306998946228968225951765070086143 in r.ffc00000000000000000000000000000.003fffffffffffffffffffffffffffff.mca", cache.firstLine);
    }
}
