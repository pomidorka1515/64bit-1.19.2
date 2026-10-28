package net.minecraft.client.renderer;

import java.util.LinkedHashSet;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderChunkTrackingTest {
    @Test
    void movingViewRingCannotCorruptTheVisibilitySet() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        // No GL resources are needed to exercise visibility tracking identity.
        var chunk = (ChunkRenderDispatcher.RenderChunk) unsafe.allocateInstance(ChunkRenderDispatcher.RenderChunk.class);
        var origin = ChunkRenderDispatcher.RenderChunk.class.getDeclaredField("origin");
        origin.setAccessible(true);
        origin.set(chunk, new BlockPos(0, 64, 0));
        var info = new LevelRenderer.RenderChunkInfo(chunk, null, 0);
        var visible = new LinkedHashSet<LevelRenderer.RenderChunkInfo>();
        assertTrue(visible.add(info));
        int hash = info.hashCode();
        for (Int1088 base : new Int1088[]{Int1088.fromLong(16), Int1088.ONE.shiftLeftSaturated(160), Int1088.MIN}) {
            origin.set(chunk, new BlockPos(base, 64, base));
            assertEquals(hash, info.hashCode());
            assertTrue(visible.contains(info));
            assertFalse(visible.add(new LevelRenderer.RenderChunkInfo(chunk, null, 1)));
            assertEquals(1, visible.size());
        }
        assertTrue(visible.remove(info));
        var other = (ChunkRenderDispatcher.RenderChunk) unsafe.allocateInstance(ChunkRenderDispatcher.RenderChunk.class);
        origin.set(other, origin.get(chunk));
        assertNotEquals(info, new LevelRenderer.RenderChunkInfo(other, null, 0));
    }
}
