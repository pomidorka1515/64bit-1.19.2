package net.minecraft.world.phys;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class BlockHitResult extends HitResult {
    private final Direction direction;
    private final BlockPos blockPos;
    private final boolean miss;
    private final boolean inside;
    /** Exact split-coordinate hit point used by sector-aware picking and the wire codec. */
    @Nullable
    private final SectorVec3 exactLocation;

    public static BlockHitResult miss(Vec3 pLocation, Direction pDirection, BlockPos pPos) {
        return new BlockHitResult(true, pLocation, pDirection, pPos, false, null);
    }

    public static BlockHitResult missExact(SectorVec3 pLocation, Direction pDirection, BlockPos pPos) {
        return new BlockHitResult(true, pLocation.toApproximateVec3(), pDirection, pPos, false, pLocation);
    }

    public BlockHitResult(Vec3 pLocation, Direction pDirection, BlockPos pBlockPos, boolean pInside) {
        this(false, pLocation, pDirection, pBlockPos, pInside, null);
    }

    /** Creates a hit whose world X/Z are retained exactly. */
    public BlockHitResult(SectorVec3 pLocation, Direction pDirection, BlockPos pBlockPos, boolean pInside) {
        this(false, pLocation.toApproximateVec3(), pDirection, pBlockPos, pInside, pLocation);
    }

    private BlockHitResult(boolean pMiss, Vec3 pLocation, Direction pDirection, BlockPos pBlockPos, boolean pInside, @Nullable SectorVec3 exactLocation) {
        super(pLocation);
        this.miss = pMiss;
        this.direction = pDirection;
        this.blockPos = pBlockPos;
        this.inside = pInside;
        this.exactLocation = exactLocation;
    }

    @Nullable
    public SectorVec3 getExactLocation() {
        return this.exactLocation;
    }

    public BlockHitResult withDirection(Direction pNewFace) {
        return new BlockHitResult(this.miss, this.location, pNewFace, this.blockPos, this.inside, this.exactLocation);
    }

    public BlockHitResult withPosition(BlockPos pPos) {
        return new BlockHitResult(this.miss, this.location, this.direction, pPos, this.inside, this.exactLocation);
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    public Direction getDirection() {
        return this.direction;
    }

    public HitResult.Type getType() {
        return this.miss ? HitResult.Type.MISS : HitResult.Type.BLOCK;
    }

    public boolean isInside() {
        return this.inside;
    }
}
