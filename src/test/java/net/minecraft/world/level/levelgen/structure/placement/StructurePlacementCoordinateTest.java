package net.minecraft.world.level.levelgen.structure.placement;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Int128;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StructurePlacementCoordinateTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static class PointPlacement extends StructurePlacement {
        private final Int128 point;

        PointPlacement(Int128 point, Optional<ExclusionZone> exclusion) {
            super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1.0F, 0, exclusion);
            this.point = point;
        }

        protected boolean isPlacementChunk(ChunkGenerator generator, RandomState random, long seed, long x, long z) {
            return point.equals(Int128.fromLong(x)) && z == 0L;
        }

        protected boolean isPlacementChunk(ChunkGenerator generator, RandomState random, long seed, Int128 x, Int128 z) {
            return point.equals(x) && z.equals(Int128.ZERO);
        }

        public StructurePlacementType<?> type() { return StructurePlacementType.RANDOM_SPREAD; }
    }

    @Test
    void exclusionZoneChecksNeighborsAcrossSignedLongSeam() {
        Int128 x = Int128.fromLong(Long.MAX_VALUE);
        var other = new PointPlacement(x.addSaturated(Int128.fromLong(1)), Optional.empty());
        var zone = new StructurePlacement.ExclusionZone(Holder.direct(new StructureSet(List.of(), other)), 1);
        var placement = new PointPlacement(x, Optional.of(zone));
        assertFalse(placement.isStructureChunk(null, null, 0L, x, Int128.ZERO));
        assertFalse(placement.isStructureChunk(null, null, 0L, Long.MAX_VALUE, 0L));
    }
}
