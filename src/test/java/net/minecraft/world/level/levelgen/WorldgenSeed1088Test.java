package net.minecraft.world.level.levelgen;

import net.minecraft.core.Int1088;
import net.minecraft.world.level.levelgen.synth.WorldgenCoordinate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldgenSeed1088Test {
    @Test
    void seedsUseUpperLimbsWithoutChangingNarrowSeeds() {
        for (long value : new long[]{Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE}) {
            assertEquals(value, WorldgenCoordinate.seedCoordinate(Int1088.fromLong(value)));
        }
        long origin = WorldgenCoordinate.seedCoordinate(Int1088.ZERO);
        for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
            long[] words = new long[Int1088.LIMBS];
            words[limb] = 1L;
            assertNotEquals(origin, WorldgenCoordinate.seedCoordinate(Int1088.fromFixedLimbs(words)));
        }
    }
}
