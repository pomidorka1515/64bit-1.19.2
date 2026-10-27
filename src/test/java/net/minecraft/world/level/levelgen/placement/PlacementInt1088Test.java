package net.minecraft.world.level.levelgen.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlacementInt1088Test {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void squareAndOffsetPlacementKeepExactAnchorAndRandomSequence() {
        BlockPos origin = new BlockPos(Int1088.of(-1L, 123L), 70, Int1088.of(-1L, -98L));
        RandomSource expected = RandomSource.create(42L);
        RandomSource actual = RandomSource.create(42L);
        BlockPos pos = InSquarePlacement.spread().getPositions(null, actual, origin).findFirst().orElseThrow();
        assertEquals(origin.offset(expected.nextInt(16), 0, expected.nextInt(16)), pos);
        assertEquals(expected.nextLong(), actual.nextLong());
        assertEquals(origin.offset(3, 2, 3), RandomOffsetPlacement.of(
                net.minecraft.util.valueproviders.ConstantInt.of(3), net.minecraft.util.valueproviders.ConstantInt.of(2))
                .getPositions(null, actual, origin).findFirst().orElseThrow());
    }

    @Test
    void spikeChunkMembershipDoesNotAliasHighHalf() {
        SpikeFeature.EndSpike spike = new SpikeFeature.EndSpike(0, 0, 2, 70, false);
        assertEquals(true, spike.isCenterWithinChunk(BlockPos.ZERO));
        assertFalse(spike.isCenterWithinChunk(new BlockPos(Int1088.of(0L, 1L), 0, Int1088.ZERO)));
    }
}
