package net.minecraft.world.level.pathfinder;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class SwimNodeEvaluator extends NodeEvaluator {
    private final boolean allowBreaching;
    private final Object2ObjectMap<BlockPos, BlockPathTypes> pathTypesByPosCache = new Object2ObjectOpenHashMap<>();

    public SwimNodeEvaluator(boolean pAllowBreaching) {
        this.allowBreaching = pAllowBreaching;
    }

    public void prepare(PathNavigationRegion pLevel, Mob pMob) {
        super.prepare(pLevel, pMob);
        this.pathTypesByPosCache.clear();
    }

    public void done() {
        super.done();
        this.pathTypesByPosCache.clear();
    }

    @Nullable
    public Node getStart() {
        net.minecraft.world.phys.SectorAABB box = this.mob.getSectorBoundingBox();
        return super.getNode(box.minBlockXForRange(), Mth.floor(box.minY() + 0.5D),
                box.minBlockZForRange());
    }

    @Nullable
    public Target getGoal(double pX, double pY, double pZ) {
        return this.getTargetFromNode(super.getNode(Int128.fromDoubleFloorSaturated(pX), Mth.floor(pY), Int128.fromDoubleFloorSaturated(pZ)));
    }

    public int getNeighbors(Node[] p_77483_, Node p_77484_) {
        int i = 0;
        Map<Direction, Node> map = Maps.newEnumMap(Direction.class);

        for (Direction direction : Direction.values()) {
            Node node = this.getNode(WorldBounds.addBlockOffset(p_77484_.x, direction.getStepX()), p_77484_.y + direction.getStepY(), WorldBounds.addBlockOffset(p_77484_.z, direction.getStepZ()));
            map.put(direction, node);
            if (this.isNodeValid(node)) {
                p_77483_[i++] = node;
            }
        }

        for (Direction direction1 : Direction.Plane.HORIZONTAL) {
            Direction direction2 = direction1.getClockWise();
            Node node1 = this.getNode(WorldBounds.addBlockOffset(p_77484_.x, direction1.getStepX() + direction2.getStepX()), p_77484_.y, WorldBounds.addBlockOffset(p_77484_.z, direction1.getStepZ() + direction2.getStepZ()));
            if (this.isDiagonalNodeValid(node1, map.get(direction1), map.get(direction2))) {
                p_77483_[i++] = node1;
            }
        }

        return i;
    }

    protected boolean isNodeValid(@Nullable Node pNode) {
        return pNode != null && !pNode.closed;
    }

    protected boolean isDiagonalNodeValid(@Nullable Node p_192964_, @Nullable Node p_192965_, @Nullable Node p_192966_) {
        return this.isNodeValid(p_192964_) && p_192965_ != null && p_192965_.costMalus >= 0.0F && p_192966_ != null && p_192966_.costMalus >= 0.0F;
    }

    @Nullable
    protected Node getNode(Int128 pX, int pY, Int128 pZ) {
        Node node = null;
        BlockPathTypes blockpathtypes = this.getCachedBlockType(pX, pY, pZ);
        if (this.allowBreaching && blockpathtypes == BlockPathTypes.BREACH || blockpathtypes == BlockPathTypes.WATER) {
            float f = this.mob.getPathfindingMalus(blockpathtypes);
            if (f >= 0.0F) {
                node = super.getNode(pX, pY, pZ);
                if (node != null) {
                    node.type = blockpathtypes;
                    node.costMalus = Math.max(node.costMalus, f);
                    if (this.level.getFluidState(new BlockPos(pX, pY, pZ)).isEmpty()) {
                        node.costMalus += 8.0F;
                    }
                }
            }
        }

        return node;
    }

    protected BlockPathTypes getCachedBlockType(Int128 pX, int pY, Int128 pZ) {
        return this.pathTypesByPosCache.computeIfAbsent(new BlockPos(pX, pY, pZ), (p_192957_) -> {
            return this.getBlockPathType(this.level, pX, pY, pZ);
        });
    }

    public BlockPathTypes getBlockPathType(BlockGetter pLevel, Int128 pX, int pY, Int128 pZ) {
        return this.getBlockPathType(pLevel, pX, pY, pZ, this.mob, this.entityWidth, this.entityHeight, this.entityDepth, this.canOpenDoors(), this.canPassDoors());
    }

    public BlockPathTypes getBlockPathType(BlockGetter pBlockaccess, Int128 pX, int pY, Int128 pZ, Mob pEntityliving, int pXSize, int pYSize, int pZSize, boolean pCanBreakDoors, boolean pCanEnterDoors) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        Int128 maxX = WorldBounds.addBlockOffset(pX, (long) pXSize - 1L);
        Int128 maxZ = WorldBounds.addBlockOffset(pZ, (long) pZSize - 1L);
        boolean moreX = pXSize > 0;
        for (Int128 i = pX; moreX; moreX = i.compareTo(maxX) < 0, i = WorldBounds.addBlockOffset(i, 1L)) {
            for (int j = pY; j < pY + pYSize; ++j) {
                boolean moreZ = pZSize > 0;
                for (Int128 k = pZ; moreZ; moreZ = k.compareTo(maxZ) < 0, k = WorldBounds.addBlockOffset(k, 1L)) {
                    FluidState fluidstate = pBlockaccess.getFluidState(blockpos$mutableblockpos.set(i, j, k));
                    BlockState blockstate = pBlockaccess.getBlockState(blockpos$mutableblockpos.set(i, j, k));
                    if (fluidstate.isEmpty() && blockstate.isPathfindable(pBlockaccess, blockpos$mutableblockpos.below(), PathComputationType.WATER) && blockstate.isAir()) {
                        return BlockPathTypes.BREACH;
                    }

                    if (!fluidstate.is(FluidTags.WATER)) {
                        return BlockPathTypes.BLOCKED;
                    }
                }
            }
        }

        BlockState blockstate1 = pBlockaccess.getBlockState(blockpos$mutableblockpos);
        return blockstate1.isPathfindable(pBlockaccess, blockpos$mutableblockpos, PathComputationType.WATER) ? BlockPathTypes.WATER : BlockPathTypes.BLOCKED;
    }
}
