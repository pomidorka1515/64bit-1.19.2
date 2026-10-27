package net.minecraft.world.level.levelgen.carver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.Int1088;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class WorldCarverCoordinateTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void neighboringStartsRemainLocalOnBothSidesOfSignedLongSeam() {
        for (long edge : new long[] {Long.MIN_VALUE, Long.MAX_VALUE - 15L}) {
            ChunkPos target = new ChunkPos(Int1088.fromLong(edge).shiftRight(4), Int1088.fromLong(edge).shiftRight(4));
            for (int delta = -1; delta <= 1; ++delta) {
                ChunkPos source = new ChunkPos(target.x().addSaturated(Int1088.fromLong(delta)), target.z());
                assertEquals(delta * 16.0D + 7.0D, WorldCarver.startCoordinate(source, target, 7, true));
                assertEquals(7.0D, WorldCarver.startCoordinate(source, target, 7, false));
            }
        }
    }
}
