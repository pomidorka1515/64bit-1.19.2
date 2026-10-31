package net.minecraft.client.gui.components;

import net.minecraft.core.Int1088;
import net.minecraft.util.CoordinateFormat;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionPaths;
import net.minecraft.world.level.chunk.storage.RegionPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionDisplayInt1088Test {
    @Test
    void exactFormatAndNegativeFloor() {
        DebugScreenOverlay.RegionDisplayCache cache = new DebugScreenOverlay.RegionDisplayCache();
        cache.update(new ChunkPos(-1L, 32L));
        assertEquals("Region X: -1", cache.firstLine);
        assertEquals("Region Z: 1", cache.secondLine);
        assertEquals(" in r/x[1 limbs]/z[1 limbs]", cache.thirdLine);
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
        cache.update(new ChunkPos(Int1088.of(0L, 1L), Int1088.ZERO));
        assertNotEquals(first, cache.firstLine);
        assertEquals("Region X: 576460752303423488", cache.firstLine);
        assertEquals("Region Z: 0", cache.secondLine);
        assertEquals(" in r/x[1 limbs]/z[1 limbs]", cache.thirdLine);
        cache.update(new ChunkPos(Int1088.of(31L, 1L), Int1088.ZERO));
        String high = cache.firstLine;
        cache.update(new ChunkPos(Int1088.of(30L, 1L), Int1088.ZERO));
        assertSame(high, cache.firstLine);
    }

    @Test
    void fullBlockDomainRegionEndpoints() {
        DebugScreenOverlay.RegionDisplayCache cache = new DebugScreenOverlay.RegionDisplayCache();
        cache.update(new ChunkPos(Int1088.MIN.shiftRight(4), Int1088.MAX.shiftRight(4)));
        Int1088 x = Int1088.MIN.shiftRight(9);
        Int1088 z = Int1088.MAX.shiftRight(9);
        assertEquals("Region X: " + CoordinateFormat.overlayInteger(x), cache.firstLine);
        assertEquals("Region Z: " + CoordinateFormat.overlayInteger(z), cache.secondLine);
        assertEquals(" in " + RegionPaths.displayRegionPath(new RegionPos(x, z)), cache.thirdLine);
    }
}
