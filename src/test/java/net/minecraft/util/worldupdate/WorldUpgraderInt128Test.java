package net.minecraft.util.worldupdate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Int128;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldUpgraderInt128Test {
    @TempDir Path directory;

    @Test
    void enumeratesHexRegionsWithoutNarrowingOrAliasing() throws Exception {
        Set<ChunkPos> expected = new HashSet<>();
        for (Int128 x : new Int128[] {Int128.ZERO, Int128.fromLong(-1L), Int128.of(1L, 0L),
                WorldBounds.MIN_CHUNK.shiftRight(5), WorldBounds.MAX_CHUNK.shiftRight(5)}) {
            Int128 z = x.negate();
            // The maximum positive region has one fewer value than the negative edge.
            if (z.compareTo(WorldBounds.MAX_CHUNK.shiftRight(5)) > 0) z = WorldBounds.MAX_CHUNK.shiftRight(5);
            Path path = directory.resolve(RegionFileNames.regionFileName(x, z));
            try (RegionFile file = new RegionFile(path, directory, true)) {
                for (int offset : new int[] {0, 31}) {
                    ChunkPos chunk = new ChunkPos(x.shiftLeftSaturated(5).add(Int128.fromLong(offset)),
                            z.shiftLeftSaturated(5).add(Int128.fromLong(31 - offset)));
                    try (DataOutputStream output = file.getChunkDataOutputStream(chunk)) {
                        output.writeByte(0);
                    }
                    expected.add(chunk);
                }
            }
        }
        // Neither legacy names nor syntactically canonical out-of-domain regions are opened.
        Path legacy = directory.resolve("r.-1.-1.mca");
        Path invalid = directory.resolve(RegionFileNames.regionFileName(Int128.MAX, Int128.ZERO));
        Files.write(legacy, new byte[] {1, 2, 3});
        Files.write(invalid, new byte[] {4, 5, 6});
        var actual = WorldUpgrader.getAllChunkPos(directory.toFile());
        assertEquals(expected.size(), actual.size());
        assertEquals(expected, new HashSet<>(actual));
        assertEquals(3L, Files.size(legacy));
        assertEquals(3L, Files.size(invalid));
    }
}
