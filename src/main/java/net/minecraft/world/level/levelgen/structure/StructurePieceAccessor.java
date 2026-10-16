package net.minecraft.world.level.levelgen.structure;

import javax.annotation.Nullable;

public interface StructurePieceAccessor {
    void addPiece(StructurePiece pPiece);

    int size();

    @Nullable
    StructurePiece findCollisionPiece(BoundingBox pBox);
}
