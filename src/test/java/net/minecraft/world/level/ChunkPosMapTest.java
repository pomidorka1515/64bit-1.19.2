package net.minecraft.world.level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.lang.management.ManagementFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ChunkPosMapTest {
    private static volatile long lookupChecksum;

    @Test
    void warmedRawProbesDoNotAllocate() {
        var bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var allocations = (com.sun.management.ThreadMXBean) bean;
        assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);
        ChunkPosMap<Integer> map = new ChunkPosMap<>(256);
        for (int i = 0; i < 256; ++i) {
            map.put(new ChunkPos(Int128.of(7L, i), Int128.of(-8L, i)), Integer.valueOf(i));
        }
        for (int i = 0; i < 20; ++i) lookupChecksum = rawProbeLoop(map);
        long thread = Thread.currentThread().getId();
        long before = allocations.getThreadAllocatedBytes(thread);
        lookupChecksum = rawProbeLoop(map);
        long bytes = allocations.getThreadAllocatedBytes(thread) - before;
        assertEquals(0L, bytes, "raw chunk probes must not allocate");
        assertTrue(lookupChecksum > 0L);
    }

    private static long rawProbeLoop(ChunkPosMap<Integer> map) {
        long checksum = 0L;
        for (int i = 0; i < 100_000; ++i) {
            checksum += map.get(7L, i & 255, -8L, i & 255).intValue();
        }
        return checksum;
    }

    @Test
    void rawProbesRetainEveryLimbAndDefaultReturnValue() {
        ChunkPosMap<String> map = new ChunkPosMap<>(1);
        map.defaultReturnValue("missing");
        List<ChunkPos> keys = List.of(ChunkPos.ZERO, new ChunkPos(-1L, Long.MIN_VALUE),
                new ChunkPos(Int128.of(7L, 13L), Int128.of(-8L, 17L)),
                new ChunkPos(Int128.of(8L, 13L), Int128.of(-8L, 17L)),
                new ChunkPos(Int128.of(7L, 14L), Int128.of(-8L, 17L)),
                new ChunkPos(Int128.of(7L, 13L), Int128.of(-9L, 17L)),
                new ChunkPos(Int128.of(7L, 13L), Int128.of(-8L, 18L)),
                new ChunkPos(WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK));
        for (int i = 0; i < keys.size(); ++i) map.put(keys.get(i), "value" + i);
        map.put(null, "null key");
        for (ChunkPos key : keys) {
            assertEquals(map.get(key), probe(map, key));
            assertEquals(key.hashCode(), ChunkPos.hash(key.xHi(), key.xLo(), key.zHi(), key.zLo()));
            assertTrue(key.equals(key.xHi(), key.xLo(), key.zHi(), key.zLo()));
        }
        assertEquals("value0", map.get(0L, 0L, 0L, 0L));
        assertEquals("missing", map.get(0L, 1L, 0L, 1L));
        map.put(ChunkPos.ZERO, null);
        assertNull(map.get(0L, 0L, 0L, 0L));
        map.clear();
        assertEquals("missing", probe(map, keys.get(2)));
    }

    @Test
    void rawProbesMatchObjectMapAfterGrowthAndDeletion() {
        ChunkPosMap<Integer> map = new ChunkPosMap<>(1);
        Map<ChunkPos, Integer> expected = new HashMap<>();
        Random random = new Random(0x128L);
        for (int i = 0; i < 4096; ++i) {
            ChunkPos key = new ChunkPos(Int128.of(random.nextInt(128) - 64, random.nextLong()),
                    Int128.of(random.nextInt(128) - 64, random.nextLong()));
            map.put(key, Integer.valueOf(i));
            expected.put(key, i);
        }
        for (ChunkPos key : List.copyOf(expected.keySet())) {
            assertEquals(expected.get(key), probe(map, key));
            if ((expected.get(key) & 1) == 0) {
                map.remove(key);
                expected.remove(key);
                assertNull(probe(map, key));
            }
        }
        map.trim();
        for (var entry : expected.entrySet()) assertEquals(entry.getValue(), probe(map, entry.getKey()));
        assertEquals(expected.size(), map.size());
    }

    @Test
    void collidingProbesSurviveClusterDeletion() {
        ChunkPosMap<String> map = new ChunkPosMap<>(4);
        ChunkPos first = new ChunkPos(Int128.of(7L, 13L), Int128.of(-8L, 17L));
        ChunkPos second = new ChunkPos(Int128.of((1L << 32) ^ 6L, 13L), Int128.of(-8L, 17L));
        assertNotEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        map.put(first, "first");
        map.put(second, "second");
        assertEquals("second", probe(map, second));
        map.remove(first);
        assertNull(probe(map, first));
        assertEquals("second", probe(map, second));
    }

    @Test
    void containingBlockProbeUsesSignedFullWidthShift() {
        ChunkPosMap<String> map = new ChunkPosMap<>();
        for (Int128 coordinate : List.of(Int128.MIN, Int128.MAX, Int128.of(7L, -1L),
                Int128.of(-8L, 0L), Int128.fromLong(-1L))) {
            BlockPos block = new BlockPos(coordinate, 64, coordinate);
            map.put(new ChunkPos(block), "found");
            assertEquals("found", map.getContaining(block));
            map.clear();
        }
    }

    private static <V> V probe(ChunkPosMap<V> map, ChunkPos key) {
        return map.get(key.xHi(), key.xLo(), key.zHi(), key.zLo());
    }
}
