package net.minecraft.world.level.chunk.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionFileStorageTest {
    @TempDir Path root;

    @Test void independentRootsRoundTripExternalAndClear() throws Exception {
        ChunkPos pos = new ChunkPos(Int1088.of(7, 0, 1), Int1088.fromLong(-1));
        byte[] bytes = new byte[1100000];
        new Random(1088).nextBytes(bytes);
        Path[] roots = {root, root.resolve("entities"), root.resolve("poi")};
        String[] markers = {"terrain", "entities", "poi"};
        CompoundTag[] external = new CompoundTag[roots.length];
        CompoundTag[] internal = new CompoundTag[roots.length];
        for (int i = 0; i < roots.length; ++i) {
            external[i] = new CompoundTag();
            external[i].putString("marker", markers[i]);
            external[i].putByteArray("payload", bytes);
            internal[i] = new CompoundTag();
            internal[i].putString("marker", markers[i] + "-internal");
            try (RegionFileStorage storage = new RegionFileStorage(roots[i], false)) {
                storage.write(pos, external[i]);
            }
        }
        for (int i = 0; i < roots.length; ++i) {
            assertTrue(Files.exists(RegionPaths.regionFile(roots[i], new RegionPos(pos.getRegionX(), pos.getRegionZ()))));
            assertTrue(Files.exists(RegionPaths.externalChunkFile(roots[i], pos)));
            try (RegionFileStorage storage = new RegionFileStorage(roots[i], false)) {
                assertEquals(external[i], storage.read(pos));
                storage.write(pos, internal[i]);
            }
            assertFalse(Files.exists(RegionPaths.externalChunkFile(roots[i], pos)));
        }
        for (int i = 0; i < roots.length; ++i) {
            try (RegionFileStorage storage = new RegionFileStorage(roots[i], false)) {
                assertEquals(internal[i], storage.read(pos));
                storage.write(pos, external[i]);
                assertTrue(Files.exists(RegionPaths.externalChunkFile(roots[i], pos)));
                storage.write(pos, null);
            }
            assertFalse(Files.exists(RegionPaths.externalChunkFile(roots[i], pos)));
        }
        for (Path store : roots) {
            try (RegionFileStorage storage = new RegionFileStorage(store, false)) {
                assertNull(storage.read(pos));
            }
        }
    }

    @Test void evictionAndHighLimbRegionsRemainDistinct() throws Exception {
        long[] words = new long[Int1088.LIMBS];
        words[16] = 1L << 5;
        ChunkPos high = new ChunkPos(Int1088.fromFixedLimbs(words), Int1088.ZERO);
        ChunkPos zero = ChunkPos.ZERO;
        CompoundTag first = new CompoundTag();
        first.putString("value", "zero");
        CompoundTag second = new CompoundTag();
        second.putString("value", "high");
        try (RegionFileStorage storage = new RegionFileStorage(root, false)) {
            storage.write(zero, first);
            storage.write(high, second);
            for (int i = 1; i <= 256; ++i) {
                storage.write(new ChunkPos(i * 32L, 0), first);
            }
            assertEquals(first, storage.read(zero));
            assertEquals(second, storage.read(high));
        }
        assertNotEquals(RegionPaths.regionFile(root, new RegionPos(zero.getRegionX(), zero.getRegionZ())),
                RegionPaths.regionFile(root, new RegionPos(high.getRegionX(), high.getRegionZ())));
    }
}
