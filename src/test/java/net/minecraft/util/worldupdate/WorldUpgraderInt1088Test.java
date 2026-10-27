package net.minecraft.util.worldupdate;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionPaths;
import net.minecraft.world.level.chunk.storage.RegionPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldUpgraderInt1088Test {
    @TempDir Path root;

    @Test void scansNestedRegionAndIgnoresInvalidPaths() throws Exception {
        RegionPos region = new RegionPos(Int1088.of(0, 1), Int1088.fromLong(-1));
        Path file = RegionPaths.regionFile(root, region);
        Files.createDirectories(file.getParent());
        ChunkPos chunk = new ChunkPos(region.x().shiftLeftSaturated(5).add(Int1088.ONE), region.z().shiftLeftSaturated(5));
        try (RegionFile regionFile = new RegionFile(file, root, true);
             var stream = regionFile.getChunkDataOutputStream(chunk)) {
            stream.writeInt(42);
        }
        Path relative = root.relativize(file);
        for (String bad : new String[] {"short", "..like", "A" + "0".repeat(31), "0".repeat(15) + "1" + "0".repeat(16)}) {
            Path stray = root;
            for (int i = 0; i < relative.getNameCount(); ++i) {
                stray = stray.resolve(i == 2 ? bad : relative.getName(i).toString());
            }
            Files.createDirectories(stray.getParent());
            Files.copy(file, stray);
        }
        Files.writeString(root.resolve("region/stray.mca"), "ignored");
        assertEquals(java.util.List.of(chunk), WorldUpgrader.getAllChunkPos(root.toFile()));
    }

    @Test void scansFullWidthEdgeRegionsAndExternalChunk() throws Exception {
        long[] wide = new long[Int1088.LIMBS];
        wide[16] = 1;
        List<ChunkPos> chunks = List.of(
                new ChunkPos(WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK),
                new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MIN_CHUNK),
                new ChunkPos(Int1088.of(0, Long.MIN_VALUE, 0), Int1088.ZERO),
                new ChunkPos(Int1088.fromFixedLimbs(wide), Int1088.fromLong(-1)));
        byte[] external = new byte[1100000];
        new Random(1088).nextBytes(external);
        for (int i = 0; i < chunks.size(); ++i) {
            ChunkPos chunk = chunks.get(i);
            Path file = RegionPaths.regionFile(root, new RegionPos(chunk.getRegionX(), chunk.getRegionZ()));
            Files.createDirectories(file.getParent());
            try (RegionFile regionFile = new RegionFile(file, root, true);
                 var stream = regionFile.getChunkDataOutputStream(chunk)) {
                if (i == chunks.size() - 1) stream.write(external);
                else stream.writeInt(i);
            }
        }
        assertTrue(Files.exists(RegionPaths.externalChunkFile(root, chunks.get(chunks.size() - 1))));
        List<ChunkPos> found = WorldUpgrader.getAllChunkPos(root.toFile());
        assertEquals(chunks.size(), found.size());
        assertEquals(new HashSet<>(chunks), new HashSet<>(found));
    }
}
