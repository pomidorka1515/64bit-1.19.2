package net.minecraft.client.renderer.culling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.math.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.Int1088;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorAABB;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

class FrustumInt1088Test {
    private static volatile int visibleCount;

    @Test
    void blockOriginCullingMatchesChunkCullingWithoutPerSectionAllocations() {
        Matrix4f identity = new Matrix4f();
        identity.setIdentity();
        Matrix4f projection = Matrix4f.createScaleMatrix(0.001F, 0.001F, 0.001F);
        var memory = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(memory.isThreadAllocatedMemorySupported());
        memory.setThreadAllocatedMemoryEnabled(true);
        for (Int1088 base : new Int1088[]{Int1088.ZERO, Int1088.ONE.shiftLeftSaturated(160), Int1088.MIN,
                WorldBounds.chunkToBlock(WorldBounds.MAX_CHUNK)}) {
            SectorVec3 position = SectorVec3.fromBlockAndFraction(base, 0.5D, 8.5D, base, 0.5D);
            Camera camera = new Camera() {
                @Override public SectorVec3 getExactPosition() { return position; }
            };
            Frustum frustum = new Frustum(projection, identity);
            frustum.prepare(camera);
            BlockPos[] origins = new BlockPos[129];
            for (int i = 0; i < origins.length; ++i) {
                Int1088 block = base.addSaturated(Int1088.fromLong((i - 64L) * 16)).shiftRight(4).shiftLeftSaturated(4);
                origins[i] = new BlockPos(block, 0, base);
                assertEquals(frustum.isChunkVisible(block.shiftRight(4), 0, base.shiftRight(4)), frustum.isRenderSectionVisible(origins[i]));
            }
            for (int i = 0; i < 30; ++i) visibleCount = cull(frustum, origins);
            long before = memory.getThreadAllocatedBytes(Thread.currentThread().getId());
            visibleCount = cull(frustum, origins);
            long bytes = memory.getThreadAllocatedBytes(Thread.currentThread().getId()) - before;
            assertTrue(bytes < 2048, "culling allocated " + bytes + " bytes");
        }
    }

    private static int cull(Frustum frustum, BlockPos[] origins) {
        int count = 0;
        for (int i = 0; i < 100; ++i) for (BlockPos origin : origins) if (frustum.isRenderSectionVisible(origin)) ++count;
        return count;
    }

    @Test
    void chunkCullingRejectsUnsafeSpansEvenInsideAnEnormousFrustum() {
        Matrix4f identity = new Matrix4f();
        identity.setIdentity();
        Matrix4f projection = Matrix4f.createScaleMatrix(0x1.0p-60F, 0x1.0p-60F, 0x1.0p-60F);
        for (Int1088 base : new Int1088[] {Int1088.ZERO, Int1088.ONE.shiftLeftSaturated(127),
                Int1088.ONE.shiftLeftSaturated(1024), Int1088.ONE.shiftLeftSaturated(1024).negate(),
                Int1088.MIN, WorldBounds.chunkToBlock(WorldBounds.MAX_CHUNK)}) {
            SectorVec3 position = SectorVec3.fromBlockAndFraction(base, 0.5D, 8.5D, base, 0.5D);
            Camera camera = new Camera() {
                @Override
                public SectorVec3 getExactPosition() { return position; }
            };
            Frustum frustum = new Frustum(projection, identity);
            frustum.prepare(camera);
            assertTrue(frustum.isChunkVisible(base.shiftRight(4), 0, base.shiftRight(4)));
            // Step inward at the positive edge; all other origins have room to the east.
            long direction = base.equals(WorldBounds.chunkToBlock(WorldBounds.MAX_CHUNK)) ? -1 : 1;
            Int1088 atLimit = base.addSaturated(Int1088.fromLong(direction * WorldBounds.MAX_SAFE_LOCAL_SPAN));
            assertTrue(frustum.isChunkVisible(atLimit.shiftRight(4), 0, base.shiftRight(4)));
            Int1088 outside = atLimit.addSaturated(Int1088.fromLong(direction * 16));
            assertFalse(frustum.isChunkVisible(outside.shiftRight(4), 0, base.shiftRight(4)));
            assertFalse(frustum.isChunkVisible(base.shiftRight(4), 0, outside.shiftRight(4)));
            Frustum copy = new Frustum(frustum);
            assertTrue(copy.isChunkVisible(base.shiftRight(4), 0, base.shiftRight(4)));
            assertFalse(copy.isChunkVisible(outside.shiftRight(4), 0, base.shiftRight(4)));
        }
    }

    @Test
    void translatedExactBoxesAndTheirMirrorsCullIdentically() {
        Matrix4f identity = new Matrix4f();
        identity.setIdentity();
        for (Int1088 base : new Int1088[] {Int1088.ZERO, Int1088.of(-1, 17), Int1088.of(0, -17),
                Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN.addSaturated(Int1088.fromLong(32)),
                Int1088.MAX.subtractSaturated(Int1088.fromLong(32))}) {
            SectorVec3 position = SectorVec3.fromBlockAndFraction(base, 0.5D, 0.5D, base, 0.5D);
            Camera camera = new Camera() {
                @Override
                public SectorVec3 getExactPosition() { return position; }
            };
            Frustum frustum = new Frustum(identity, identity);
            frustum.prepare(camera);
            SectorAABB near = new SectorAABB(base, 0.25D, 0.25D, base, 0.25D,
                    base, 0.75D, 0.75D, base, 0.75D);
            assertTrue(frustum.isVisible(near));
            assertTrue(frustum.isVisible(new BlockPos(base, 0, base),
                    new AABB(0.25D, 0.25D, 0.25D, 0.75D, 0.75D, 0.75D)));
            assertEquals(frustum.isVisible(near), frustum.isVisible(AABB.fromSectorBounds(near)));
            SectorAABB far = near.move(32.0D, 0.0D, 32.0D);
            assertEquals(false, frustum.isVisible(far));
            assertEquals(frustum.isVisible(far), frustum.isVisible(AABB.fromSectorBounds(far)));
        }
    }
}
