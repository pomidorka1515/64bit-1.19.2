package net.minecraft.server.level;

import net.minecraft.core.SectionPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.ChunkPos;

public record ColumnPos(Int1088 x, Int1088 z) {
    public ColumnPos(long x, long z) {
        this(Int1088.fromLong(x), Int1088.fromLong(z));
    }
    public ChunkPos toChunkPos() {
        return new ChunkPos(SectionPos.blockToSectionCoord(this.x), SectionPos.blockToSectionCoord(this.z));
    }

    public String toString() {
        return "[" + this.x + ", " + this.z + "]";
    }

    public int hashCode() {
        return ChunkPos.hash(this.x, this.z);
    }
}
