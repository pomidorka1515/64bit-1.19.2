package net.minecraft.world.level.levelgen.structure.structures;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.Int1088;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class StructurePiecesInt1088Test {
    @Test
    void concretePieceAnchorsPreserveHighHalvesAndLocalDimensions() {
        Int1088 x = Int1088.of(-16, 42);
        Int1088 z = Int1088.of(16, -23);
        MineshaftPieces.MineShaftRoom room = new MineshaftPieces.MineShaftRoom(0,
            RandomSource.create(1), x, z, MineshaftStructure.Type.NORMAL);
        assertEquals(x, room.getBoundingBox().minX());
        assertEquals(z, room.getBoundingBox().minZ());
        assertTrue(room.getBoundingBox().getXSpan().compareTo(Int1088.fromLong(13)) <= 0);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            StrongholdPieces.StartPiece stronghold = new StrongholdPieces.StartPiece(RandomSource.create(2), x, z);
            NetherFortressPieces.StartPiece fortress = new NetherFortressPieces.StartPiece(RandomSource.create(3), x, z);
            OceanMonumentPieces.MonumentBuilding monument = new OceanMonumentPieces.MonumentBuilding(RandomSource.create(4), x, z, direction);
            for (BoundingBox box : new BoundingBox[] {stronghold.getBoundingBox(), fortress.getBoundingBox(), monument.getBoundingBox()}) {
                assertTrue(box.isInside(new net.minecraft.core.BlockPos(x, box.minY(), z)));
                assertTrue(box.getXSpan().compareTo(Int1088.fromLong(58)) <= 0);
                assertTrue(box.getZSpan().compareTo(Int1088.fromLong(58)) <= 0);
                assertFalse(box.isInside(new net.minecraft.core.BlockPos(Int1088.fromLong(x.lowLimb()), box.minY(), z)));
            }
        }
    }
}
