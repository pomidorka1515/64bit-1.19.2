package net.minecraft.world.level.chunk.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import net.minecraft.core.Int1088;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionPathComponentsTest {
    @TempDir Path directory;

    @Test
    void fixedShapeAndSignExtension() {
        for (Int1088 coordinate : new Int1088[] {Int1088.ZERO, Int1088.ONE,
                Int1088.fromLong(-1), Int1088.MIN, Int1088.MAX}) {
            Path path = file(directory, coordinate, coordinate);
            Path relative = directory.relativize(path);
            assertEquals(22, relative.getNameCount());
            assertEquals("region", relative.getName(0).toString());
            assertEquals("x", relative.getName(1).toString());
            assertEquals("z", relative.getName(11).toString());
            assertEquals("r.mca", relative.getName(21).toString());
            assertEquals(coordinate.signum() < 0 ? "ffffffffffffffff" : "0000000000000000",
                    relative.getName(2).toString().substring(0, 16));
            for (int i = 2; i <= 20; ++i) {
                if (i != 11) assertTrue(relative.getName(i).toString().matches("[0-9a-f]{32}"));
            }
            assertRoundTrip(directory, path, coordinate, coordinate);
        }
        assertFalse(Files.exists(directory.resolve("region")), "building a path must not create directories");
    }

    @Test
    void allLimbsRoundTripAndHaveDistinctPaths() {
        Path zero = file(directory, Int1088.ZERO, Int1088.ZERO);
        for (int limb = 0; limb < Int1088.LIMBS; ++limb) {
            long[] words = new long[Int1088.LIMBS];
            words[limb] = 1;
            Int1088 value = Int1088.fromFixedLimbs(words);
            Path xPath = file(directory, value, Int1088.ZERO);
            Path zPath = file(directory, Int1088.ZERO, value);
            assertNotEquals(zero, xPath);
            assertNotEquals(zero, zPath);
            assertNotEquals(xPath, zPath);
            assertRoundTrip(directory, xPath, value, Int1088.ZERO);
            assertRoundTrip(directory, zPath, Int1088.ZERO, value);
        }
        Random random = new Random(1088);
        for (int i = 0; i < 1000; ++i) {
            long[] x = new long[Int1088.LIMBS], z = new long[Int1088.LIMBS];
            for (int limb = 0; limb < Int1088.LIMBS; ++limb) {
                x[limb] = random.nextLong();
                z[limb] = random.nextLong();
            }
            Int1088 a = Int1088.fromFixedLimbs(x), b = Int1088.fromFixedLimbs(z);
            assertRoundTrip(directory, file(directory, a, b), a, b);
        }
    }

    @Test
    void rejectsMalformedDepthCaseSignAndTraversal() {
        Path valid = file(directory, Int1088.ZERO, Int1088.ZERO);
        Path relative = directory.relativize(valid);
        String zero = "0".repeat(32);
        String[] replacements = {"0".repeat(31), "0".repeat(33), "A" + zero.substring(1),
                "g" + zero.substring(1), "-" + zero.substring(1), ".", ".."};
        for (int index : new int[] {2, 3, 10, 12, 13, 20}) {
            for (String replacement : replacements) {
                Path changed = directory;
                for (int i = 0; i < relative.getNameCount(); ++i) {
                    changed = changed.resolve(i == index ? replacement : relative.getName(i).toString());
                }
                assertInvalid(changed);
            }
        }
        for (int index : new int[] {2, 12}) {
            Path changed = directory;
            for (int i = 0; i < relative.getNameCount(); ++i) {
                changed = changed.resolve(i == index ? "ffffffffffffffff" + "0".repeat(16)
                        : relative.getName(i).toString());
            }
            assertInvalid(changed);
        }
        assertInvalid(valid.getParent());
        assertInvalid(valid.resolve("r.mca"));
        assertInvalid(directory.resolve("other").resolve("..").resolve(relative));
        assertInvalid(directory.resolve("..").resolve(relative));
        assertInvalid(directory.resolve("elsewhere").resolve(relative));
        assertTrue(RegionPathComponents.parse(directory, valid, "chunks", "c.mcc").isEmpty());
    }

    @Test
    void eachStoreAndExternalTreeCanCreateIndependentFiles() throws Exception {
        Int1088 x = Int1088.ONE.shiftLeftSaturated(1024);
        Int1088 z = Int1088.MIN;
        int marker = 0;
        for (Path root : new Path[] {directory, directory.resolve("entities"), directory.resolve("poi")}) {
            for (boolean external : new boolean[] {false, true}) {
                String tree = external ? "chunks" : "region";
                String leaf = external ? "c.mcc" : "r.mca";
                Path path = RegionPathComponents.file(root, tree, leaf, x, z);
                Files.createDirectories(path.getParent());
                assertFalse(Files.exists(path));
                Files.write(path, new byte[] {(byte) ++marker});
                assertEquals(marker, Files.readAllBytes(path)[0]);
                assertArrayEquals(new Int1088[] {x, z},
                        RegionPathComponents.parse(root, path, tree, leaf).orElseThrow());
            }
        }
        try (var paths = Files.walk(directory)) {
            assertEquals(6, paths.filter(Files::isRegularFile).count());
        }
    }

    private Path file(Path root, Int1088 x, Int1088 z) {
        return RegionPathComponents.file(root, "region", "r.mca", x, z);
    }

    private void assertRoundTrip(Path root, Path path, Int1088 x, Int1088 z) {
        assertArrayEquals(new Int1088[] {x, z},
                RegionPathComponents.parse(root, path, "region", "r.mca").orElseThrow());
    }

    private void assertInvalid(Path path) {
        assertTrue(RegionPathComponents.parse(directory, path, "region", "r.mca").isEmpty(), path.toString());
    }
}
