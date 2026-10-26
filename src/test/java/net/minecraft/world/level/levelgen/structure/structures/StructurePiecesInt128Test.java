package net.minecraft.world.level.levelgen.structure.structures;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.Int128;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class StructurePiecesInt128Test {
    @Test
    void concretePieceAnchorsPreserveHighHalvesAndLocalDimensions() {
        Int128 x = Int128.of(42, -16);
        Int128 z = Int128.of(-23, 16);
        MineshaftPieces.MineShaftRoom room = new MineshaftPieces.MineShaftRoom(0,
            RandomSource.create(1), x, z, MineshaftStructure.Type.NORMAL);
        assertEquals(x, room.getBoundingBox().minX());
        assertEquals(z, room.getBoundingBox().minZ());
        assertTrue(room.getBoundingBox().getXSpan().compareTo(Int128.fromLong(13)) <= 0);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            StrongholdPieces.StartPiece stronghold = new StrongholdPieces.StartPiece(RandomSource.create(2), x, z);
            NetherFortressPieces.StartPiece fortress = new NetherFortressPieces.StartPiece(RandomSource.create(3), x, z);
            OceanMonumentPieces.MonumentBuilding monument = new OceanMonumentPieces.MonumentBuilding(RandomSource.create(4), x, z, direction);
            for (BoundingBox box : new BoundingBox[] {stronghold.getBoundingBox(), fortress.getBoundingBox(), monument.getBoundingBox()}) {
                assertTrue(box.isInside(new net.minecraft.core.BlockPos(x, box.minY(), z)));
                assertTrue(box.getXSpan().compareTo(Int128.fromLong(58)) <= 0);
                assertTrue(box.getZSpan().compareTo(Int128.fromLong(58)) <= 0);
                assertFalse(box.isInside(new net.minecraft.core.BlockPos(Int128.fromLong(x.lo()), box.minY(), z)));
            }
        }
    }
}
