package net.minecraft.client.renderer;

import java.lang.management.ManagementFactory;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.phys.SectorVec3;

/** Headless real RenderChunk visibility path; supports the pre-fix classpath too. */
public final class RenderPerformanceProbe {
    private static volatile int sink;

    public static void main(String[] args) throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var originField = ChunkRenderDispatcher.RenderChunk.class.getDeclaredField("origin");
        originField.setAccessible(true);
        var memory = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        memory.setThreadAllocatedMemoryEnabled(true);
        Matrix4f identity = new Matrix4f();
        identity.setIdentity();
        Matrix4f projection = Matrix4f.createScaleMatrix(0.002F, 0.002F, 0.002F);
        for (int bits : new int[]{0, 160, 1024}) {
            Int1088 base = bits == 0 ? Int1088.ZERO : Int1088.ONE.shiftLeftSaturated(bits);
            SectorVec3 position = SectorVec3.fromBlockAndFraction(base, 0.5, 128.5, base, 0.5);
            Camera camera = new Camera() {
                @Override public SectorVec3 getExactPosition() { return position; }
            };
            Frustum frustum = new Frustum(projection, identity);
            frustum.prepare(camera);
            var chunks = new ChunkRenderDispatcher.RenderChunk[37 * 37 * 24];
            int index = 0;
            for (int z = -18; z <= 18; ++z) for (int x = -18; x <= 18; ++x) for (int y = -4; y < 20; ++y) {
                var chunk = (ChunkRenderDispatcher.RenderChunk) unsafe.allocateInstance(ChunkRenderDispatcher.RenderChunk.class);
                BlockPos origin = new BlockPos(base.add(Int1088.fromLong(x * 16L)), y * 16, base.add(Int1088.fromLong(z * 16L)));
                originField.set(chunk, originField.getType() == BlockPos.MutableBlockPos.class ? origin.mutable() : origin);
                chunks[index++] = chunk;
            }
            for (int i = 0; i < 30; ++i) sink = cull(chunks, frustum);
            long allocated = memory.getThreadAllocatedBytes(Thread.currentThread().getId());
            long start = System.nanoTime();
            for (int i = 0; i < 30; ++i) sink = cull(chunks, frustum);
            System.out.printf("bits=%d sections=%d ms/cull=%.3f MB/cull=%.6f visible=%d%n", bits, chunks.length,
                    (System.nanoTime() - start) / 30e6,
                    (memory.getThreadAllocatedBytes(Thread.currentThread().getId()) - allocated) / 30e6, sink);
        }
    }

    private static int cull(ChunkRenderDispatcher.RenderChunk[] chunks, Frustum frustum) {
        int count = 0;
        for (var chunk : chunks) if (chunk.isVisible(frustum)) ++count;
        return count;
    }
}
