package net.minecraft.client.resources.sounds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

class BiomeAmbientInt1088Test {
    @Test
    void moodSearchRetainsExactEyeAnchorAcrossLowWordSeams() {
        for (Int1088 base : new Int1088[] {Int1088.ZERO, Int1088.of(-1, 12), Int1088.of(Long.MAX_VALUE, -12)}) {
            SectorVec3 eye = SectorVec3.fromBlockAndFraction(base, 0.75D, 65.625D, base, 0.25D);
            BlockPos sample = BiomeAmbientSoundsHandler.moodSample(eye, 2, -3, -2);
            assertEquals(base.add(Int1088.fromLong(2)), sample.getX());
            assertEquals(62, sample.getY());
            assertEquals(base.subtract(Int1088.fromLong(2)), sample.getZ());
        }
    }
}
