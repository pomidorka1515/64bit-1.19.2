package net.minecraft.world.level;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;

public class LocalMobCapCalculator {
    private final ChunkPosMap<List<ServerPlayer>> playersNearChunk = new ChunkPosMap<>();
    private final Map<ServerPlayer, LocalMobCapCalculator.MobCounts> playerMobCounts = Maps.newHashMap();
    private final ChunkMap chunkMap;

    public LocalMobCapCalculator(ChunkMap pChunkMap) {
        this.chunkMap = pChunkMap;
    }

    private List<ServerPlayer> getPlayersNear(ChunkPos pPos) {
        List<ServerPlayer> cached = this.playersNearChunk.get(pPos.xHi(), pPos.xLo(), pPos.zHi(), pPos.zLo());
        if (cached != null) {
            return cached;
        }
        return this.playersNearChunk.computeIfAbsent(pPos, (p_186511_) -> {
            return this.chunkMap.getPlayersCloseForSpawning(pPos);
        });
    }

    public void addMob(ChunkPos pPos, MobCategory pCategory) {
        for (ServerPlayer serverplayer : this.getPlayersNear(pPos)) {
            this.playerMobCounts.computeIfAbsent(serverplayer, (p_186503_) -> {
                                    return new LocalMobCapCalculator.MobCounts();
                                }).add(pCategory);
        }
    }

    public boolean canSpawn(MobCategory pCategory, ChunkPos pPos) {
        for (ServerPlayer serverplayer : this.getPlayersNear(pPos)) {
            LocalMobCapCalculator.MobCounts localmobcapcalculator$mobcounts = this.playerMobCounts.get(serverplayer);
            if (localmobcapcalculator$mobcounts == null || localmobcapcalculator$mobcounts.canSpawn(pCategory)) {
                return true;
            }
        }

        return false;
    }

    static class MobCounts {
        private final Object2IntMap<MobCategory> counts = new Object2IntOpenHashMap<>(MobCategory.values().length);

        public void add(MobCategory pCategory) {
            this.counts.computeInt(pCategory, (p_186520_, p_186521_) -> {
                return p_186521_ == null ? 1 : p_186521_ + 1;
            });
        }

        public boolean canSpawn(MobCategory pCategory) {
            return this.counts.getOrDefault(pCategory, 0) < pCategory.getMaxInstancesPerChunk();
        }
    }
}
