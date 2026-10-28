package net.minecraft.client.renderer.chunk;

import java.util.ArrayDeque;
import java.util.concurrent.PriorityBlockingQueue;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkCompileQueueTest {
    @Test
    void cancelledTasksAreDrainedBeforeReturningWork() throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var dispatcher = (ChunkRenderDispatcher) unsafe.allocateInstance(ChunkRenderDispatcher.class);
        var chunk = (ChunkRenderDispatcher.RenderChunk) unsafe.allocateInstance(ChunkRenderDispatcher.RenderChunk.class);
        var high = new PriorityBlockingQueue<ChunkRenderDispatcher.RenderChunk.ChunkCompileTask>();
        var low = new ArrayDeque<ChunkRenderDispatcher.RenderChunk.ChunkCompileTask>();
        for (String name : new String[]{"toBatchHighPriority", "toBatchLowPriority"}) {
            var field = ChunkRenderDispatcher.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(dispatcher, name.equals("toBatchHighPriority") ? high : low);
        }
        for (int i = 0; i < 1000; ++i) {
            var cancelled = task(chunk, i, i % 2 == 0);
            cancelled.cancel();
            if (i % 2 == 0) high.add(cancelled); else low.add(cancelled);
        }
        var live = task(chunk, 2000, false);
        low.add(live);
        var poll = ChunkRenderDispatcher.class.getDeclaredMethod("pollTask");
        poll.setAccessible(true);
        assertSame(live, poll.invoke(dispatcher));
        assertTrue(high.isEmpty());
        assertTrue(low.isEmpty());
        assertNull(poll.invoke(dispatcher));
    }

    private static ChunkRenderDispatcher.RenderChunk.ChunkCompileTask task(ChunkRenderDispatcher.RenderChunk chunk, double distance, boolean high) {
        return chunk.new ChunkCompileTask(distance, high) {
            public java.util.concurrent.CompletableFuture<ChunkRenderDispatcher.ChunkTaskResult> doTask(ChunkBufferBuilderPack buffers) {
                throw new AssertionError("Polling must not execute a task");
            }
            public void cancel() { this.isCancelled.set(true); }
            protected String name() { return "queue-test"; }
        };
    }
}
