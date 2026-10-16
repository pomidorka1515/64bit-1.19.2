package net.minecraft.world.level;

import com.google.common.collect.AbstractIterator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockCollisions extends AbstractIterator<VoxelShape> {
    private final AABB box;
    private final CollisionContext context;
    private final Cursor3D cursor;
    private final BlockPos.MutableBlockPos pos;
    private final VoxelShape entityShape;
    private final CollisionGetter collisionGetter;
    private final boolean onlySuffocatingBlocks;
    @Nullable
    private BlockGetter cachedBlockGetter;
    private ChunkPos cachedBlockGetterPos;

    public BlockCollisions(CollisionGetter pCollisionGetter, @Nullable Entity pEntity, AABB pBox) {
        this(pCollisionGetter, pEntity, pBox, false);
    }

    public BlockCollisions(CollisionGetter pCollisionGetter, @Nullable Entity pEntity, AABB pBox, boolean pOnlySuffocatingBlocks) {
        this.context = pEntity == null ? CollisionContext.empty() : CollisionContext.of(pEntity);
        this.pos = new BlockPos.MutableBlockPos();
        this.entityShape = Shapes.create(pBox);
        this.collisionGetter = pCollisionGetter;
        this.box = pBox;
        this.onlySuffocatingBlocks = pOnlySuffocatingBlocks;
        long i = WorldBounds.addBlockOffset(Mth.lfloor(pBox.minX - 1.0E-7D), -1L);
        long j = WorldBounds.addBlockOffset(Mth.lfloor(pBox.maxX + 1.0E-7D), 1L);
        int k = Mth.floor(pBox.minY - 1.0E-7D) - 1;
        int l = Mth.floor(pBox.maxY + 1.0E-7D) + 1;
        long i1 = WorldBounds.addBlockOffset(Mth.lfloor(pBox.minZ - 1.0E-7D), -1L);
        long j1 = WorldBounds.addBlockOffset(Mth.lfloor(pBox.maxZ + 1.0E-7D), 1L);
        this.cursor = new Cursor3D(i, k, i1, j, l, j1);
    }

    @Nullable
    private BlockGetter getChunk(long pX, long pZ) {
        long i = SectionPos.blockToSectionCoord(pX);
        long j = SectionPos.blockToSectionCoord(pZ);
        ChunkPos k = new ChunkPos(i, j);
        if (this.cachedBlockGetter != null && k.equals(cachedBlockGetterPos)) {
            return this.cachedBlockGetter;
        } else {
            BlockGetter blockgetter = this.collisionGetter.getChunkForCollisions(i, j);
            this.cachedBlockGetter = blockgetter;
            this.cachedBlockGetterPos = k;
            return blockgetter;
        }
    }

    protected VoxelShape computeNext() {
        while (true) {
            if (this.cursor.advance()) {
                long i = this.cursor.nextX();
                int j = this.cursor.nextY();
                long k = this.cursor.nextZ();
                int l = this.cursor.getNextType();
                if (l == 3) {
                    continue;
                }

                BlockGetter blockgetter = this.getChunk(i, k);
                if (blockgetter == null) {
                    continue;
                }

                this.pos.set(i, j, k);
                BlockState blockstate = blockgetter.getBlockState(this.pos);
                if (this.onlySuffocatingBlocks && !blockstate.isSuffocating(blockgetter, this.pos) || l == 1 && !blockstate.hasLargeCollisionShape() || l == 2 && !blockstate.is(Blocks.MOVING_PISTON)) {
                    continue;
                }

                VoxelShape voxelshape = blockstate.getCollisionShape(this.collisionGetter, this.pos, this.context);
                if (voxelshape == Shapes.block()) {
                    if (!this.box.intersects((double) i, (double) j, (double) k, (double) i + 1.0D, (double) j + 1.0D, (double) k + 1.0D)) {
                        continue;
                    }

                    return voxelshape.move((double) i, (double) j, (double) k);
                }

                VoxelShape voxelshape1 = voxelshape.move((double) i, (double) j, (double) k);
                if (!Shapes.joinIsNotEmpty(voxelshape1, this.entityShape, BooleanOp.AND)) {
                    continue;
                }

                return voxelshape1;
            }

            return this.endOfData();
        }
    }
}
