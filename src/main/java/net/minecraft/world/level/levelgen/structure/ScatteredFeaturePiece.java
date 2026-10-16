package net.minecraft.world.level.levelgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public abstract class ScatteredFeaturePiece extends StructurePiece {
    protected final int width;
    protected final int height;
    protected final int depth;
    protected int heightPosition = -1;

    protected ScatteredFeaturePiece(StructurePieceType pType, long pX, int pY, long pZ, int pWidth, int pHeight, int pDepth, Direction pOrientation) {
        super(pType, 0, StructurePiece.makeBoundingBox(pX, pY, pZ, pOrientation, pWidth, pHeight, pDepth));
        this.width = pWidth;
        this.height = pHeight;
        this.depth = pDepth;
        this.setOrientation(pOrientation);
    }

    protected ScatteredFeaturePiece(StructurePieceType pType, CompoundTag pTag) {
        super(pType, pTag);
        this.width = pTag.getInt("Width");
        this.height = pTag.getInt("Height");
        this.depth = pTag.getInt("Depth");
        this.heightPosition = pTag.getInt("HPos");
    }

    protected void addAdditionalSaveData(StructurePieceSerializationContext pContext, CompoundTag pTag) {
        pTag.putInt("Width", this.width);
        pTag.putInt("Height", this.height);
        pTag.putInt("Depth", this.depth);
        pTag.putInt("HPos", this.heightPosition);
    }

    protected boolean updateAverageGroundHeight(LevelAccessor pLevel, BoundingBox pBounds, int pHeight) {
        if (this.heightPosition >= 0) {
            return true;
        } else {
            int i = 0;
            int j = 0;
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            long k = this.boundingBox.minZ();
            long l = this.boundingBox.maxZ();
            long i1 = this.boundingBox.minX();
            long j1 = this.boundingBox.maxX();

            for (long k1 = k;; k1 = WorldBounds.addBlockOffset(k1, 1L)) {
                for (long l1 = i1;; l1 = WorldBounds.addBlockOffset(l1, 1L)) {
                    blockpos$mutableblockpos.set(l1, 64, k1);
                    if (pBounds.isInside(blockpos$mutableblockpos)) {
                        i += pLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockpos$mutableblockpos).getY();
                        ++j;
                    }

                    if (l1 == j1) {
                        break;
                    }
                }

                if (k1 == l) {
                    break;
                }
            }

            if (j == 0) {
                return false;
            } else {
                this.heightPosition = i / j;
                this.boundingBox.move(0, this.heightPosition - this.boundingBox.minY() + pHeight, 0);
                return true;
            }
        }
    }

    protected boolean updateHeightPositionToLowestGroundHeight(LevelAccessor p_192468_, int p_192469_) {
        if (this.heightPosition >= 0) {
            return true;
        } else {
            int i = p_192468_.getMaxBuildHeight();
            boolean flag = false;
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            long j = this.boundingBox.minZ();
            long k = this.boundingBox.maxZ();
            long l = this.boundingBox.minX();
            long i1 = this.boundingBox.maxX();

            for (long j1 = j;; j1 = WorldBounds.addBlockOffset(j1, 1L)) {
                for (long k1 = l;; k1 = WorldBounds.addBlockOffset(k1, 1L)) {
                    blockpos$mutableblockpos.set(k1, 0, j1);
                    i = Math.min(i, p_192468_.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockpos$mutableblockpos).getY());
                    flag = true;

                    if (k1 == i1) {
                        break;
                    }
                }

                if (j1 == k) {
                    break;
                }
            }

            if (!flag) {
                return false;
            } else {
                this.heightPosition = i;
                this.boundingBox.move(0, this.heightPosition - this.boundingBox.minY() + p_192469_, 0);
                return true;
            }
        }
    }
}
