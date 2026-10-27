package net.minecraft.client.multiplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.Int1088;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ClientBiomeSamplingTest {
    @Test
    void translatedSamplesRetainHighHalvesAndLocalWeights() {
        SectorVec3 local = SectorVec3.fromBlockAndFraction(3L, 0.75D, 64.5D, 1L, 0.125D);
        Vec3 expected = CubicSampler.gaussianSampleVec3(new Vec3((3.75D - 2) / 4,
                (64.5D - 2) / 4, (1.125D - 2) / 4), (x, y, z) -> new Vec3(x, y, z));
        for (Int1088 anchor : new Int1088[] {Int1088.ZERO, Int1088.of(-4, 7), Int1088.of(-4, -7),
                Int1088.MIN, Int1088.MAX.subtract(Int1088.fromLong(3))}) {
            Int1088 quart = anchor.shiftRight(2);
            AtomicInteger samples = new AtomicInteger();
            SectorVec3 position = SectorVec3.fromBlockAndFraction(anchor.add(Int1088.fromLong(3)),
                    0.75D, 64.5D, anchor.add(Int1088.ONE), 0.125D);
            Vec3 actual = ClientLevel.sampleBiomeColors(position, (x, y, z) -> {
                samples.incrementAndGet();
                return new Vec3(WorldBounds.signedDifference(x, quart), y, WorldBounds.signedDifference(z, quart));
            });
            assertEquals(216, samples.get());
            assertEquals(expected.x, actual.x, 1.0E-12);
            assertEquals(expected.y, actual.y, 1.0E-12);
            assertEquals(expected.z, actual.z, 1.0E-12);
        }
    }
}
