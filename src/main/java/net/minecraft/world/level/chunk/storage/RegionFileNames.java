package net.minecraft.world.level.chunk.storage;

import java.util.Optional;
import net.minecraft.core.Int128;
import net.minecraft.world.level.ChunkPos;

/** Canonical, fixed-width two's-complement names for region and external chunk files. */
public final class RegionFileNames {
    private RegionFileNames() {
    }

    public static String regionFileName(Int128 regionX, Int128 regionZ) {
        return "r." + regionX.toHex() + "." + regionZ.toHex() + ".mca";
    }

    public static String externalChunkFileName(Int128 chunkX, Int128 chunkZ) {
        return "c." + chunkX.toHex() + "." + chunkZ.toHex() + ".mcc";
    }

    public static Optional<RegionPos> parseRegionFileName(String fileName) {
        return parse(fileName, "r.", ".mca");
    }

    public static Optional<ChunkPos> parseExternalChunkFileName(String fileName) {
        return parse(fileName, "c.", ".mcc")
            .filter(pos -> isChunkCoordinate(pos.x()) && isChunkCoordinate(pos.z()))
            .map(pos -> new ChunkPos(pos.x(), pos.z()));
    }

    private static boolean isChunkCoordinate(Int128 coordinate) {
        return coordinate.compareTo(ChunkPos.MIN_CHUNK_COORDINATE) >= 0
            && coordinate.compareTo(ChunkPos.MAX_CHUNK_COORDINATE) <= 0;
    }

    private static Optional<RegionPos> parse(String fileName, String prefix, String suffix) {
        if (fileName == null || fileName.length() != 71 || !fileName.startsWith(prefix)
            || fileName.charAt(34) != '.' || !fileName.endsWith(suffix)) {
            return Optional.empty();
        }
        for (int i = 2; i < 67; ++i) {
            if (i == 34) {
                continue;
            }
            char digit = fileName.charAt(i);
            if (!((digit >= '0' && digit <= '9') || (digit >= 'a' && digit <= 'f'))) {
                return Optional.empty();
            }
        }
        return Optional.of(new RegionPos(Int128.fromHex(fileName.substring(2, 34)),
            Int128.fromHex(fileName.substring(35, 67))));
    }
}
