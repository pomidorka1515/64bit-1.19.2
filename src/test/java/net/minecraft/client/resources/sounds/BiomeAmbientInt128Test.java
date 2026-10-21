package net.minecraft.client.resources.sounds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

class BiomeAmbientInt128Test {
    @Test
    void moodSearchRetainsExactEyeAnchorAcrossLowWordSeams() {
        for (Int128 base : new Int128[] {Int128.ZERO, Int128.of(12, -1), Int128.of(-12, Long.MAX_VALUE)}) {
            SectorVec3 eye = SectorVec3.fromBlockAndFraction(base, 0.75D, 65.625D, base, 0.25D);
            BlockPos sample = BiomeAmbientSoundsHandler.moodSample(eye, 2, -3, -2);
            assertEquals(base.add(Int128.fromLong(2)), sample.getX());
            assertEquals(62, sample.getY());
            assertEquals(base.subtract(Int128.fromLong(2)), sample.getZ());
        }
    }
}
