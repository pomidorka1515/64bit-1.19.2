package net.minecraft.world.level.chunk.storage;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionFileTest {
    @TempDir Path root;

    @Test void failedExternalInstallPreservesPreviousChunk() throws Exception {
        Path regionPath = root.resolve("r.mca");
        ChunkPos pos = ChunkPos.ZERO;
        Path externalPath = RegionPaths.externalChunkFile(root, pos);

        try (RegionFile region = new RegionFile(regionPath, root, RegionFileVersion.VERSION_NONE, false)) {
            try (DataOutputStream output = region.getChunkDataOutputStream(pos)) {
                output.writeInt(12345);
            }
            IntOpenHashSet originalSectors = new IntOpenHashSet(region.usedSectors.getUsed());

            // A non-empty directory at the destination makes the final move fail.
            Files.createDirectories(externalPath);
            Files.writeString(externalPath.resolve("blocker"), "keep");
            assertThrows(IOException.class, () -> {
                try (DataOutputStream output = region.getChunkDataOutputStream(pos)) {
                    output.write(new byte[256 * 4096]);
                }
            });

            assertEquals(originalSectors, region.usedSectors.getUsed());
            try (DataInputStream input = region.getChunkDataInputStream(pos)) {
                assertNotNull(input);
                assertEquals(12345, input.readInt());
            }
            try (var paths = Files.list(externalPath.getParent())) {
                assertEquals(1, paths.count(), "temporary external file must be removed");
            }
        }

        try (RegionFile region = new RegionFile(regionPath, root, RegionFileVersion.VERSION_NONE, false);
             DataInputStream input = region.getChunkDataInputStream(pos)) {
            assertNotNull(input);
            assertEquals(12345, input.readInt());
        }
    }
}
