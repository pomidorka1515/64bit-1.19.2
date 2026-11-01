package net.minecraft.client.multiplayer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Int1088;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientBiomeCoordinateReuseTest {
    private record Sample(Int1088 x, int y, Int1088 z) { }

    @Test
    void reusedCoordinatesPreserveEveryCallbackAndResultBit() {
        Int1088[] anchors = {Int1088.ZERO, Int1088.fromLong(-17),
                Int1088.of(-1, 7), Int1088.of(0, -7),
                Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX};
        for (Int1088 x : anchors) for (Int1088 z : anchors) {
            for (double fraction : new double[]{0, .125, .5, Math.nextDown(1.0)}) {
                SectorVec3 position = SectorVec3.fromBlockAndFraction(x, fraction, -63.25, z, fraction);
                List<Sample> expectedSamples = new ArrayList<>();
                List<Sample> actualSamples = new ArrayList<>();
                Vec3 expected = reference(position, (sx, y, sz) -> {
                    expectedSamples.add(new Sample(sx, y, sz));
                    return color(sx, y, sz);
                });
                Vec3 actual = ClientLevel.sampleBiomeColors(position, (sx, y, sz) -> {
                    actualSamples.add(new Sample(sx, y, sz));
                    return color(sx, y, sz);
                });
                assertEquals(216, actualSamples.size());
                assertEquals(expectedSamples, actualSamples);
                assertBits(expected, actual);
            }
        }
    }

    @Test
    void nestedSamplingCannotOverwriteOuterCoordinates() {
        SectorVec3 outer = SectorVec3.fromBlockAndFraction(Int1088.of(-1, 7), .25, 70, Int1088.of(0, -7), .75);
        SectorVec3 inner = SectorVec3.fromBlockAndFraction(17, .125, 65, -19, .5);
        Vec3 expected = reference(outer, ClientBiomeCoordinateReuseTest::color);
        Vec3 actual = ClientLevel.sampleBiomeColors(outer, (x, y, z) -> {
            ClientLevel.sampleBiomeColors(inner, ClientBiomeCoordinateReuseTest::color);
            return color(x, y, z);
        });
        assertBits(expected, actual);
    }

    private static Vec3 color(Int1088 x, int y, Int1088 z) {
        return new Vec3((x.lowLimb() & 255) / 255.0, y / 255.0, (z.lowLimb() & 255) / 255.0);
    }

    private static void assertBits(Vec3 expected, Vec3 actual) {
        assertEquals(Double.doubleToRawLongBits(expected.x), Double.doubleToRawLongBits(actual.x));
        assertEquals(Double.doubleToRawLongBits(expected.y), Double.doubleToRawLongBits(actual.y));
        assertEquals(Double.doubleToRawLongBits(expected.z), Double.doubleToRawLongBits(actual.z));
    }

    private static Vec3 reference(SectorVec3 position, ClientLevel.BiomeColorFetcher fetcher) {
        Int1088 quartX = position.blockX().shiftRight(2);
        Int1088 quartZ = position.blockZ().shiftRight(2);
        Vec3 local = new Vec3(((position.blockX().lowLimb() & 3L) + position.subX() - 2.0D) * 0.25D,
                (position.y() - 2.0D) * 0.25D,
                ((position.blockZ().lowLimb() & 3L) + position.subZ() - 2.0D) * 0.25D);
        return CubicSampler.gaussianSampleVec3(local, (x, y, z) -> fetcher.fetch(
                quartX.addSaturated(Int1088.fromLong(x)), y, quartZ.addSaturated(Int1088.fromLong(z))));
    }
}
