package net.minecraft.world.level.chunk.storage;

import java.util.Objects;
import net.minecraft.core.Int1088;

/** Immutable region address, distinct from a chunk address. */
public record RegionPos(Int1088 x, Int1088 z) {
    public RegionPos {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(z, "z");
    }

    public int compareXThenZ(RegionPos other) {
        int comparison = this.x.compareTo(other.x);
        return comparison != 0 ? comparison : this.z.compareTo(other.z);
    }
}
