package net.minecraft.server.level.progress;

import java.lang.management.ManagementFactory;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StoringChunkProgressListenerTest {
    private static volatile int lookupChecksum;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void rawStatusProbesMatchOffsetKeysIncludingCarryAndClamping() {
        StoringChunkProgressListener listener = new StoringChunkProgressListener(2);
        int radius = listener.getDiameter() / 2;
        List<Int1088> coordinates = List.of(Int1088.ZERO, Int1088.fromLong(-1L),
                Int1088.fromLong(Long.MAX_VALUE), Int1088.fromLong(Long.MIN_VALUE),
                Int1088.of(-1L, 7L), Int1088.of(0L, -8L),
                WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK);
        for (Int1088 x : coordinates) {
            for (Int1088 z : coordinates) {
                listener.start();
                ChunkPos center = new ChunkPos(x, z);
                listener.updateSpawnPos(center);
                for (int dx : new int[]{-radius, -1, 0, 1, radius}) {
                    for (int dz : new int[]{-radius, -1, 0, 1, radius}) {
                        ChunkPos key = new ChunkPos(WorldBounds.addBlockOffset(x, dx), WorldBounds.addBlockOffset(z, dz));
                        listener.onStatusChange(key, ChunkStatus.FULL);
                        assertSame(ChunkStatus.FULL, listener.getStatus(radius + dx, radius + dz));
                        listener.onStatusChange(key, ChunkStatus.EMPTY);
                        assertSame(ChunkStatus.EMPTY, listener.getStatus(radius + dx, radius + dz));
                        listener.onStatusChange(key, null);
                        assertNull(listener.getStatus(radius + dx, radius + dz));
                    }
                }
            }
        }
    }

    @Test
    void startClearsStatusesAndStoppedUpdatesAreIgnored() {
        StoringChunkProgressListener listener = new StoringChunkProgressListener(2);
        int radius = listener.getDiameter() / 2;
        listener.start();
        listener.updateSpawnPos(ChunkPos.ZERO);
        listener.onStatusChange(ChunkPos.ZERO, ChunkStatus.FULL);
        listener.stop();
        listener.onStatusChange(ChunkPos.ZERO, null);
        listener.updateSpawnPos(new ChunkPos(100L, 100L));
        assertSame(ChunkStatus.FULL, listener.getStatus(radius, radius));
        listener.start();
        assertNull(listener.getStatus(radius, radius));
    }

    @Test
    void warmedStatusProbesDoNotAllocate() {
        var bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var allocations = (com.sun.management.ThreadMXBean) bean;
        assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);
        StoringChunkProgressListener listener = new StoringChunkProgressListener(2);
        int radius = listener.getDiameter() / 2;
        listener.start();
        listener.updateSpawnPos(new ChunkPos(Int1088.of(-1L, 7L), Int1088.of(0L, -8L)));
        listener.onStatusChange(new ChunkPos(Int1088.of(0L, 8L), Int1088.of(-1L, -9L)), ChunkStatus.FULL);
        for (int i = 0; i < 30; ++i) lookupChecksum = probeLoop(listener, radius);
        long thread = Thread.currentThread().getId();
        long before = allocations.getThreadAllocatedBytes(thread);
        lookupChecksum = probeLoop(listener, radius);
        assertEquals(0L, allocations.getThreadAllocatedBytes(thread) - before);
        assertEquals(100_000, lookupChecksum);
    }

    private static int probeLoop(StoringChunkProgressListener listener, int radius) {
        int found = 0;
        for (int i = 0; i < 100_000; ++i) {
            if (listener.getStatus(radius + 1, radius - 1) == ChunkStatus.FULL) ++found;
        }
        return found;
    }
}
