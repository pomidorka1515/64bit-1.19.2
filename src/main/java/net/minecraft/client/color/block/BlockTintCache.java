package net.minecraft.client.color.block;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BlockTintCache {
    private static final int MAX_CACHE_ENTRIES = 256;
    private final ThreadLocal<BlockTintCache.LatestCacheInfo> latestChunkOnThread = ThreadLocal.withInitial(BlockTintCache.LatestCacheInfo::new);
    private final Object2ObjectLinkedOpenHashMap<ChunkPos, BlockTintCache.CacheData> cache = new Object2ObjectLinkedOpenHashMap<>(256, 0.25F);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ToIntFunction<BlockPos> source;

    public BlockTintCache(ToIntFunction<BlockPos> pSource) {
        this.source = pSource;
    }

    public int getColor(BlockPos pPos) {
        Int128 i = SectionPos.blockToSectionCoord(pPos.getX());
        Int128 j = SectionPos.blockToSectionCoord(pPos.getZ());
        BlockTintCache.LatestCacheInfo blocktintcache$latestcacheinfo = this.latestChunkOnThread.get();
        if (!i.equals(blocktintcache$latestcacheinfo.x) || !j.equals(blocktintcache$latestcacheinfo.z)
                || blocktintcache$latestcacheinfo.cache == null || blocktintcache$latestcacheinfo.cache.invalid) {
            blocktintcache$latestcacheinfo.x = i;
            blocktintcache$latestcacheinfo.z = j;
            blocktintcache$latestcacheinfo.cache = this.findOrCreateChunkCache(i, j);
        }

        int[] aint = blocktintcache$latestcacheinfo.cache.getLayer(pPos.getY());
        int k = (int) (pPos.getX().lo() & 15);
        int l = (int) (pPos.getZ().lo() & 15);
        int i1 = l << 4 | k;
        int j1 = aint[i1];
        if (j1 != -1) {
            return j1;
        } else {
            int k1 = this.source.applyAsInt(pPos);
            aint[i1] = k1;
            return k1;
        }
    }

    public void invalidateForChunk(long pChunkX, long pChunkZ) {
        this.invalidateForChunk(Int128.fromLong(pChunkX), Int128.fromLong(pChunkZ));
    }

    public void invalidateForChunk(Int128 pChunkX, Int128 pChunkZ) {
        try {
            this.lock.writeLock().lock();

            for (int i = -1; i <= 1; ++i) {
                for (int j = -1; j <= 1; ++j) {
                    Int128 x = WorldBounds.tryAddChunkOffset(pChunkX, i);
                    Int128 z = WorldBounds.tryAddChunkOffset(pChunkZ, j);
                    if (x != null && z != null) {
                        CacheData removed = this.cache.remove(new ChunkPos(x, z));
                        if (removed != null)
                            removed.invalid = true;
                    }
                }
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void invalidateAll() {
        try {
            this.lock.writeLock().lock();
            this.cache.values().forEach(data -> data.invalid = true);
            this.cache.clear();
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private BlockTintCache.CacheData findOrCreateChunkCache(Int128 pChunkX, Int128 pChunkZ) {
        ChunkPos i = new ChunkPos(pChunkX, pChunkZ);
        this.lock.readLock().lock();

        try {
            BlockTintCache.CacheData blocktintcache$cachedata = this.cache.get(i);
            if (blocktintcache$cachedata != null) {
                return blocktintcache$cachedata;
            }
        } finally {
            this.lock.readLock().unlock();
        }

        this.lock.writeLock().lock();

        BlockTintCache.CacheData blocktintcache$cachedata2;
        try {
            BlockTintCache.CacheData blocktintcache$cachedata1 = this.cache.get(i);
            if (blocktintcache$cachedata1 == null) {
                blocktintcache$cachedata2 = new BlockTintCache.CacheData();
                if (this.cache.size() >= 256) {
                    this.cache.removeFirst().invalid = true;
                }

                this.cache.put(i, blocktintcache$cachedata2);
                return blocktintcache$cachedata2;
            }

            blocktintcache$cachedata2 = blocktintcache$cachedata1;
        } finally {
            this.lock.writeLock().unlock();
        }

        return blocktintcache$cachedata2;
    }

    @OnlyIn(Dist.CLIENT)
    static class CacheData {
        volatile boolean invalid;
        private final Int2ObjectArrayMap<int[]> cache = new Int2ObjectArrayMap<>(16);
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private static final int BLOCKS_PER_LAYER = Mth.square(16);

        public int[] getLayer(int pHeight) {
            this.lock.readLock().lock();

            try {
                int[] aint = this.cache.get(pHeight);
                if (aint != null) {
                    return aint;
                }
            } finally {
                this.lock.readLock().unlock();
            }

            this.lock.writeLock().lock();

            int[] aint1;
            try {
                aint1 = this.cache.computeIfAbsent(pHeight, (p_193826_) -> {
                    return this.allocateLayer();
                });
            } finally {
                this.lock.writeLock().unlock();
            }

            return aint1;
        }

        private int[] allocateLayer() {
            int[] aint = new int[BLOCKS_PER_LAYER];
            Arrays.fill(aint, -1);
            return aint;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class LatestCacheInfo {
        public Int128 x;
        public Int128 z;
        @Nullable
        BlockTintCache.CacheData cache;

        private LatestCacheInfo() {
        }
    }
}
