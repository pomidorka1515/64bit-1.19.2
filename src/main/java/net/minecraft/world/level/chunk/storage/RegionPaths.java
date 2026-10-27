package net.minecraft.world.level.chunk.storage;

import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;

/** Nested Anvil paths relative to an explicit, independent storage root. */
public final class RegionPaths {
    private RegionPaths() {}

    public static Path regionFile(Path root, RegionPos pos) {
        return RegionPathComponents.file(root, "region", "r.mca", pos.x(), pos.z());
    }

    public static Path externalChunkFile(Path root, ChunkPos pos) {
        return RegionPathComponents.file(root, "chunks", "c.mcc", pos.x(), pos.z());
    }

    private static boolean inRange(Int1088 value, Int1088 min, Int1088 max) {
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    public static Optional<RegionPos> parseRegionFile(Path root, Path file) {
        Int1088 min = WorldBounds.MIN_CHUNK.shiftRight(5);
        Int1088 max = WorldBounds.MAX_CHUNK.shiftRight(5);
        return RegionPathComponents.parse(root, file, "region", "r.mca")
            .filter(v -> inRange(v[0], min, max) && inRange(v[1], min, max))
            .map(v -> new RegionPos(v[0], v[1]));
    }

    public static Optional<ChunkPos> parseExternalChunkFile(Path root, Path file) {
        return RegionPathComponents.parse(root, file, "chunks", "c.mcc")
            .filter(v -> inRange(v[0], WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK)
                && inRange(v[1], WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK))
            .map(v -> new ChunkPos(v[0], v[1]));
    }

    public static String displayRegionPath(RegionPos pos) {
        return "r/x[" + pos.x().limbCount() + " limbs]/z[" + pos.z().limbCount() + " limbs]";
    }
}
