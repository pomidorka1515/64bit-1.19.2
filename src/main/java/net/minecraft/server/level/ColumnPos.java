package net.minecraft.server.level;

import net.minecraft.core.SectionPos;
import net.minecraft.core.Int128;
import net.minecraft.world.level.ChunkPos;

public record ColumnPos(Int128 x, Int128 z) {
    public ColumnPos(long x, long z) {
        this(Int128.fromLong(x), Int128.fromLong(z));
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
