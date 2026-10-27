package net.minecraft.world.level.chunk.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionPathTest {
    @TempDir Path root;

    @Test void pathsRoundTripAndRemainIndependent() {
        Int1088 min = WorldBounds.MIN_CHUNK.shiftRight(5);
        Int1088 max = WorldBounds.MAX_CHUNK.shiftRight(5);
        for (Int1088 x : new Int1088[] {Int1088.ZERO, Int1088.ONE, Int1088.fromLong(-1), min, max}) {
            RegionPos pos = new RegionPos(x, x);
            Path path = RegionPaths.regionFile(root, pos);
            assertEquals(pos, RegionPaths.parseRegionFile(root, path).orElseThrow());
            assertTrue(path.toString().length() > 600);
            assertNotEquals(path, RegionPaths.regionFile(root.resolve("entities"), pos));
        }
        ChunkPos chunk = new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MIN_CHUNK);
        assertEquals(chunk, RegionPaths.parseExternalChunkFile(root, RegionPaths.externalChunkFile(root, chunk)).orElseThrow());
    }

    @Test void rejectsMalformedAndOutsidePaths() {
        Path valid = RegionPaths.regionFile(root, new RegionPos(Int1088.ZERO, Int1088.ZERO));
        Path relative = root.relativize(valid);
        assertTrue(RegionPaths.parseRegionFile(root.resolve("other"), valid).isEmpty());
        assertTrue(RegionPaths.parseRegionFile(root, root.resolve("..").resolve(root.getFileName()).resolve(relative)).isEmpty());
        assertTrue(RegionPaths.parseRegionFile(root, valid.resolveSibling("other.mca")).isEmpty());
        String path = valid.toString();
        assertTrue(RegionPaths.parseRegionFile(root, Path.of(path.replaceFirst("00000000000000000000000000000000", "F0000000000000000000000000000000"))).isEmpty());
        // The upper half of the first component is sign-extension padding;
        // changing its lower half alone would encode a valid bit-1024 address.
        assertTrue(RegionPaths.parseRegionFile(root, Path.of(path.replaceFirst("00000000000000000000000000000000", "00000000000000010000000000000000"))).isEmpty());
        RegionPos highLimb = new RegionPos(Int1088.ONE.shiftLeftSaturated(1024), Int1088.ZERO);
        assertEquals(highLimb, RegionPaths.parseRegionFile(root, RegionPaths.regionFile(root, highLimb)).orElseThrow());
    }

    @Test void knownZeroAndNegativeOneComponents() {
        Path zero = root.resolve("region/x");
        Path negative = root.resolve("region/x");
        for (int i = 0; i < 9; ++i) {
            zero = zero.resolve("0".repeat(32));
            negative = negative.resolve("f".repeat(32));
        }
        zero = zero.resolve("z");
        negative = negative.resolve("z");
        for (int i = 0; i < 9; ++i) {
            zero = zero.resolve("0".repeat(32));
            negative = negative.resolve("f".repeat(32));
        }
        assertEquals(zero.resolve("r.mca"), RegionPaths.regionFile(root, new RegionPos(Int1088.ZERO, Int1088.ZERO)));
        assertEquals(negative.resolve("r.mca"), RegionPaths.regionFile(root, new RegionPos(Int1088.fromLong(-1), Int1088.fromLong(-1))));
    }

    @Test void rejectsRegionCoordinatesOutsideChunkDomain() {
        Int1088 min = WorldBounds.MIN_CHUNK.shiftRight(5);
        Int1088 max = WorldBounds.MAX_CHUNK.shiftRight(5);
        for (Int1088 invalid : new Int1088[] {min.subtract(Int1088.ONE), max.add(Int1088.ONE), Int1088.MIN, Int1088.MAX}) {
            assertTrue(RegionPaths.parseRegionFile(root, RegionPaths.regionFile(root, new RegionPos(invalid, Int1088.ZERO))).isEmpty());
            assertTrue(RegionPaths.parseRegionFile(root, RegionPaths.regionFile(root, new RegionPos(Int1088.ZERO, invalid))).isEmpty());
        }
    }
}
