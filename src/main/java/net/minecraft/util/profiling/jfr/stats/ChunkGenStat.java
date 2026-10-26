package net.minecraft.util.profiling.jfr.stats;

import java.time.Duration;
import jdk.jfr.consumer.RecordedEvent;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;

public record ChunkGenStat(Duration duration, ChunkPos chunkPos, ColumnPos worldPos, ChunkStatus status, String level) implements TimedStat {
    public static ChunkGenStat from(RecordedEvent pEvent) {
        return new ChunkGenStat(pEvent.getDuration(), new ChunkPos(net.minecraft.core.Int128.parseDecimal(pEvent.getString("chunkPosX")), net.minecraft.core.Int128.parseDecimal(pEvent.getString("chunkPosZ"))), new ColumnPos(net.minecraft.core.Int128.parseDecimal(pEvent.getString("worldPosX")), net.minecraft.core.Int128.parseDecimal(pEvent.getString("worldPosZ"))), ChunkStatus.byName(pEvent.getString("status")), pEvent.getString("level"));
    }

    public Duration duration() {
        return this.duration;
    }
}
