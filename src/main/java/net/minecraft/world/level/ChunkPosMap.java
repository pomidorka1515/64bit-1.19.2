package net.minecraft.world.level;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;

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
    public V get(Int1088 x, Int1088 z) {
        int slot = HashCommon.mix(ChunkPos.hash(x, z)) & this.mask;
        // View as Object[]: fastutil's generic backing array is not a ChunkPos[].
        Object[] keys = this.key;
        ChunkPos current;
        while ((current = (ChunkPos) keys[slot]) != null) {
            if (current.equals(x, z)) {
                return this.value[slot];
            }
            slot = (slot + 1) & this.mask;
        }
        return this.defRetValue;
    }

    public V get(long x, long z) {
        int slot = HashCommon.mix(ChunkPos.hash(x, z)) & this.mask;
        Object[] keys = this.key;
        ChunkPos current;
        while ((current = (ChunkPos) keys[slot]) != null) {
            if (current.equals(x, z)) return this.value[slot];
            slot = (slot + 1) & this.mask;
        }
        return this.defRetValue;
    }

    /** Looks up the chunk containing a block, including negative and full-width positions. */
    public V getContaining(BlockPos pos) {
        return this.get(pos.getX().shiftRight(4), pos.getZ().shiftRight(4));
    }
}
