package net.minecraft.world.level.chunk.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import net.minecraft.core.Int128;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class RegionFileNamesTest {
    @Test
    void appendixExamplesUseExactTwosComplementNames() {
        assertRegion(0L, 0L, "r.00000000000000000000000000000000.00000000000000000000000000000000.mca");
        assertRegion(-1L, -1L, "r.ffffffffffffffffffffffffffffffff.ffffffffffffffffffffffffffffffff.mca");
        assertRegion(1L, -1L, "r.00000000000000000000000000000001.ffffffffffffffffffffffffffffffff.mca");
        assertRegion(Long.MIN_VALUE, Long.MAX_VALUE,
            "r.ffffffffffffffff8000000000000000.00000000000000007fffffffffffffff.mca");
        assertExternal(Int128.ZERO, Int128.ZERO,
            "c.00000000000000000000000000000000.00000000000000000000000000000000.mcc");
        assertExternal(Int128.fromLong(10L), Int128.fromLong(-10L),
            "c.0000000000000000000000000000000a.fffffffffffffffffffffffffffffff6.mcc");
    }

    @Test
    void fullWidthNamesRoundTripWithoutLosingHighBits() {
        Int128[] edges = {Int128.MIN, Int128.MAX, Int128.ZERO, Int128.ONE, Int128.fromLong(-1L),
            Int128.of(1L, 0L), Int128.of(-2L, -1L)};
        for (Int128 x : edges) {
            for (Int128 z : edges) {
                assertRegionRoundTrip(x, z);
            }
        }
        Random random = new Random(501L);
        for (int i = 0; i < 1000; ++i) {
            Int128 x = Int128.of(random.nextLong(), random.nextLong());
            Int128 z = Int128.of(random.nextLong(), random.nextLong());
            assertRegionRoundTrip(x, z);
            x = x.shiftRight(4);
            z = z.shiftRight(4);
            assertExternal(x, z, RegionFileNames.externalChunkFileName(x, z));
        }
    }

    @Test
    void externalParserRejectsCoordinatesThatChunkPosWouldClamp() {
        Int128 min = ChunkPos.MIN_CHUNK_COORDINATE;
        Int128 max = ChunkPos.MAX_CHUNK_COORDINATE;
        assertExternal(min, max, RegionFileNames.externalChunkFileName(min, max));
        for (Int128 invalid : new Int128[] {min.subtract(Int128.ONE), max.add(Int128.ONE), Int128.MIN, Int128.MAX}) {
            assertTrue(RegionFileNames.parseExternalChunkFileName(
                RegionFileNames.externalChunkFileName(invalid, Int128.ZERO)).isEmpty());
            assertTrue(RegionFileNames.parseExternalChunkFileName(
                RegionFileNames.externalChunkFileName(Int128.ZERO, invalid)).isEmpty());
        }
    }

    @Test
    void parsersRejectNonCanonicalNames() {
        String zero = "00000000000000000000000000000000";
        String[] invalidAxes = {zero.substring(1), zero + "0", "A" + zero.substring(1),
            "g" + zero.substring(1), "0x" + zero.substring(2), "+" + zero.substring(1),
            "-" + zero.substring(1), "." + zero.substring(1), " " + zero.substring(1),
            "\u00e9" + zero.substring(1)};
        for (String axis : invalidAxes) {
            assertInvalid("r." + axis + "." + zero + ".mca");
            assertInvalid("r." + zero + "." + axis + ".mca");
            assertInvalid("c." + axis + "." + zero + ".mcc");
            assertInvalid("c." + zero + "." + axis + ".mcc");
        }
        String region = RegionFileNames.regionFileName(Int128.ZERO, Int128.ZERO);
        String external = RegionFileNames.externalChunkFileName(Int128.ZERO, Int128.ZERO);
        for (String name : new String[] {null, "", "r.0.0.mca", "c.0.0.mcc", " " + region,
                region + " ", region + ".bak", region.substring(0, 67), region.replace(".mca", ".MCA"),
                region.replace("r.", "R."), "dir/" + region, "../" + external,
                region.replace(".mca", ".mcc"), external.replace(".mcc", ".mca")}) {
            assertInvalid(name);
        }
        assertTrue(RegionFileNames.parseRegionFileName(external).isEmpty());
        assertTrue(RegionFileNames.parseExternalChunkFileName(region).isEmpty());
    }

    @Test
    void regionKeysHaveValueIdentityAndSignedXThenZOrder() {
        RegionPos first = new RegionPos(Int128.of(1L, 0L), Int128.fromLong(-1L));
        RegionPos equal = new RegionPos(Int128.of(1L, 0L), Int128.fromLong(-1L));
        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        assertEquals(0, first.compareXThenZ(equal));
        assertNotEquals(first, new RegionPos(Int128.ZERO, first.z()));
        assertTrue(new RegionPos(Int128.MIN, Int128.MAX).compareXThenZ(first) < 0);
        assertTrue(first.compareXThenZ(new RegionPos(first.x(), Int128.ZERO)) < 0);
        assertTrue(first.compareXThenZ(new RegionPos(Int128.ZERO, Int128.MAX)) > 0);
    }

    private static void assertRegion(long x, long z, String expected) {
        assertEquals(expected, RegionFileNames.regionFileName(Int128.fromLong(x), Int128.fromLong(z)));
        assertEquals(new RegionPos(Int128.fromLong(x), Int128.fromLong(z)),
            RegionFileNames.parseRegionFileName(expected).orElseThrow());
    }

    private static void assertRegionRoundTrip(Int128 x, Int128 z) {
        String name = RegionFileNames.regionFileName(x, z);
        assertEquals(71, name.length());
        assertTrue(name.matches("r\\.[0-9a-f]{32}\\.[0-9a-f]{32}\\.mca"));
        assertEquals(new RegionPos(x, z), RegionFileNames.parseRegionFileName(name).orElseThrow());
    }

    private static void assertExternal(Int128 x, Int128 z, String expected) {
        assertEquals(expected, RegionFileNames.externalChunkFileName(x, z));
        ChunkPos parsed = RegionFileNames.parseExternalChunkFileName(expected).orElseThrow();
        assertEquals(x, parsed.x());
        assertEquals(z, parsed.z());
        assertEquals(71, expected.length());
    }

    private static void assertInvalid(String name) {
        assertFalse(RegionFileNames.parseRegionFileName(name).isPresent(), name);
        assertFalse(RegionFileNames.parseExternalChunkFileName(name).isPresent(), name);
    }
}
