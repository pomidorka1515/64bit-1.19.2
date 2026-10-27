package net.minecraft.world.level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.lang.management.ManagementFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ChunkPosMapTest {
    @Test
    void inlineKeysMatchValueConstructionAndDoNotAliasMixedOrUpperWordKeys() {
        ChunkPosMap<Integer> map = new ChunkPosMap<>(1);
        for (long x : new long[] {0, -1, 1, Long.MIN_VALUE, Long.MAX_VALUE, 1L << 32, -(1L << 32)}) {
            for (long z : new long[] {0, -1, Long.MIN_VALUE, Long.MAX_VALUE, 1L << 40}) {
                ChunkPos raw = new ChunkPos(x, z);
                ChunkPos values = new ChunkPos(Int1088.fromLong(x), Int1088.fromLong(z));
                assertEquals(raw, values);
                assertEquals(raw.hashCode(), values.hashCode());
                assertEquals(ChunkPos.hash(x, z), raw.hashCode());
                map.put(raw, map.size());
                assertEquals(map.get(values), map.get(x, z));
                assertEquals(Int1088.fromLong(x), raw.x());
                assertEquals(Int1088.fromLong(z), raw.z());
                for (int limb = 0; limb < Int1088.LIMBS; ++limb) {
                    assertEquals(raw.x().limb(limb), raw.xLimb(limb));
                    assertEquals(raw.z().limb(limb), raw.zLimb(limb));
                }
                assertNotEquals(raw, new ChunkPos(Int1088.of(x, 7), raw.z()));
                assertNotEquals(raw, new ChunkPos(raw.x(), Int1088.of(z, -7)));
            }
        }
        map.trim();
        for (ChunkPos key : map.keySet()) assertEquals(map.get(key), map.get(key.x(), key.z()));
        assertNotEquals(ChunkPos.hash(0, 0), ChunkPos.hash(1L << 32, 0));
        assertNotEquals(ChunkPos.hash(0, 0), ChunkPos.hash(0, 1L << 32));
    }

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
            map.put(new ChunkPos(i, -i), Integer.valueOf(i));
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
            checksum += map.get(i & 255, -(i & 255)).intValue();
        }
        return checksum;
    }

    @Test
    void rawProbesRetainEveryLimbAndDefaultReturnValue() {
        ChunkPosMap<String> map = new ChunkPosMap<>(1);
        map.defaultReturnValue("missing");
        List<ChunkPos> keys = List.of(ChunkPos.ZERO, new ChunkPos(-1L, Long.MIN_VALUE),
                new ChunkPos(Int1088.of(13L, 7L), Int1088.of(17L, -8L)),
                new ChunkPos(Int1088.of(13L, 8L), Int1088.of(17L, -8L)),
                new ChunkPos(Int1088.of(14L, 7L), Int1088.of(17L, -8L)),
                new ChunkPos(Int1088.of(13L, 7L), Int1088.of(17L, -9L)),
                new ChunkPos(Int1088.of(13L, 7L), Int1088.of(18L, -8L)),
                new ChunkPos(WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK));
        for (int i = 0; i < keys.size(); ++i) map.put(keys.get(i), "value" + i);
        map.put(null, "null key");
        for (ChunkPos key : keys) {
            assertEquals(map.get(key), probe(map, key));
            assertEquals(key.hashCode(), ChunkPos.hash(key.x(), key.z()));
            assertTrue(key.equals(key.x(), key.z()));
        }
        assertEquals("value0", map.get(0L, 0L));
        assertEquals("missing", map.get(1L, 1L));
        map.put(ChunkPos.ZERO, null);
        assertNull(map.get(0L, 0L));
        map.clear();
        assertEquals("missing", probe(map, keys.get(2)));
    }

    @Test
    void rawProbesMatchObjectMapAfterGrowthAndDeletion() {
        ChunkPosMap<Integer> map = new ChunkPosMap<>(1);
        Map<ChunkPos, Integer> expected = new HashMap<>();
        Random random = new Random(0x128L);
        for (int i = 0; i < 4096; ++i) {
            ChunkPos key = new ChunkPos(Int1088.of(random.nextLong(), random.nextInt(128) - 64),
                    Int1088.of(random.nextLong(), random.nextInt(128) - 64));
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
        ChunkPos first = new ChunkPos(Int1088.of(13L, 7L), Int1088.of(17L, -8L));
        ChunkPos second = null;
        int bucket = it.unimi.dsi.fastutil.HashCommon.mix(first.hashCode()) & 7;
        for (int i = 0; i < 1000; ++i) {
            ChunkPos candidate = new ChunkPos(i, 42);
            if ((it.unimi.dsi.fastutil.HashCommon.mix(candidate.hashCode()) & 7) == bucket) { second = candidate; break; }
        }
        assertNotNull(second);
        assertNotEquals(first, second);
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
        for (Int1088 coordinate : List.of(Int1088.MIN, Int1088.MAX, Int1088.of(-1L, 7L),
                Int1088.of(0L, -8L), Int1088.fromLong(-1L))) {
            BlockPos block = new BlockPos(coordinate, 64, coordinate);
            map.put(new ChunkPos(block), "found");
            assertEquals("found", map.getContaining(block));
            map.clear();
        }
    }

    private static <V> V probe(ChunkPosMap<V> map, ChunkPos key) {
        return map.get(key.x(), key.z());
    }

    @Test
    void everyLimbRemainsDistinctAfterRehashAndRemoval() {
        ChunkPosMap<Integer> map = new ChunkPosMap<>(1);
        Map<ChunkPos, Integer> expected = new HashMap<>();
        for (int limb = 0; limb < Int1088.LIMBS; ++limb) {
            for (int sign : new int[] {-1, 1}) {
                long[] words = new long[Int1088.LIMBS];
                words[limb] = 1L;
                Int1088 coordinate = Int1088.fromFixedLimbs(words);
                if (sign < 0) coordinate = coordinate.negate();
                for (ChunkPos key : List.of(new ChunkPos(coordinate, Int1088.ZERO),
                        new ChunkPos(Int1088.ZERO, coordinate))) {
                    int value = expected.size();
                    assertNull(expected.put(key, value));
                    map.put(key, value);
                }
            }
        }
        assertEquals(4 * Int1088.LIMBS, map.size());
        for (var entry : expected.entrySet()) {
            ChunkPos key = entry.getKey();
            assertEquals(entry.getValue(), probe(map, key));
            assertEquals(entry.getValue(), map.get(new ChunkPos(key.x(), key.z())));
            if (!key.xFitsLong() || !key.zFitsLong()) {
                assertNull(map.get(key.xLow(), key.zLow()), "raw probe must not match a wide alias");
            }
        }
        for (var entry : expected.entrySet()) {
            if ((entry.getValue() & 1) == 0) map.remove(entry.getKey());
        }
        map.trim();
        for (var entry : expected.entrySet()) {
            assertEquals((entry.getValue() & 1) == 0 ? null : entry.getValue(), probe(map, entry.getKey()));
        }
    }

    @Test
    void valueProbesUseRawPathOnlyWhenBothAxesFitLong() {
        class ProbeMap extends ChunkPosMap<String> {
            int narrowProbes;

            @Override
            public String get(long x, long z) {
                ++this.narrowProbes;
                return super.get(x, z);
            }
        }
        ProbeMap map = new ProbeMap();
        Int1088 wide = Int1088.ONE.shiftLeftSaturated(1024);
        map.put(new ChunkPos(Long.MIN_VALUE, Long.MAX_VALUE), "narrow");
        map.put(new ChunkPos(wide, Int1088.ZERO), "wide x");
        map.put(new ChunkPos(Int1088.ZERO, wide), "wide z");
        assertEquals("narrow", map.get(Int1088.fromLong(Long.MIN_VALUE), Int1088.fromLong(Long.MAX_VALUE)));
        assertEquals(1, map.narrowProbes);
        assertEquals("wide x", map.get(wide, Int1088.ZERO));
        assertEquals("wide z", map.get(Int1088.ZERO, wide));
        assertEquals(1, map.narrowProbes);
    }
}
