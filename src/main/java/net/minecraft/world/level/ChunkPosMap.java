package net.minecraft.world.level;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128Math;

/** A normal ChunkPos map with allocation-free, read-only raw-coordinate probes. */
public class ChunkPosMap<V> extends Object2ObjectOpenHashMap<ChunkPos, V> {
    public ChunkPosMap() {
        super();
    }

    public ChunkPosMap(int expected) {
        super(expected);
    }

    /**
     * Probes exact limbs (no clamping). Callers must supply chunk-domain coordinates,
     * just as stored ChunkPos keys do. Missing keys return the map's default value.
     */
    public V get(long xHi, long xLo, long zHi, long zLo) {
        int slot = HashCommon.mix(ChunkPos.hash(xHi, xLo, zHi, zLo)) & this.mask;
        // View as Object[]: fastutil's generic backing array is not a ChunkPos[].
        Object[] keys = this.key;
        ChunkPos current;
        while ((current = (ChunkPos) keys[slot]) != null) {
            if (current.equals(xHi, xLo, zHi, zLo)) {
                return this.value[slot];
            }
            slot = (slot + 1) & this.mask;
        }
        return this.defRetValue;
    }

    /** Looks up the chunk containing a block, including negative and full-width positions. */
    public V getContaining(BlockPos pos) {
        return this.get(pos.xHi() >> 4, Int128Math.rightShiftLo(pos.xHi(), pos.xLo(), 4),
                pos.zHi() >> 4, Int128Math.rightShiftLo(pos.zHi(), pos.zLo(), 4));
    }
}
