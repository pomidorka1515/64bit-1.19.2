package net.minecraft.world.entity.ai.navigation;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public abstract class PathNavigation {
    private static final int MAX_TIME_RECOMPUTE = 20;
    protected final Mob mob;
    protected final Level level;
    @Nullable
    protected Path path;
    protected double speedModifier;
    protected int tick;
    protected int lastStuckCheck;
    @Nullable
    protected SectorVec3 lastStuckCheckPos;
    protected Vec3i timeoutCachedNode = Vec3i.ZERO;
    protected long timeoutTimer;
    protected long lastTimeoutCheck;
    protected double timeoutLimit;
    protected float maxDistanceToWaypoint = 0.5F;
    protected boolean hasDelayedRecomputation;
    protected long timeLastRecompute;
    protected NodeEvaluator nodeEvaluator;
    @Nullable
    private BlockPos targetPos;
    private int reachRange;
    private float maxVisitedNodesMultiplier = 1.0F;
    private final PathFinder pathFinder;
    private boolean isStuck;

    public PathNavigation(Mob pMob, Level pLevel) {
        this.mob = pMob;
        this.level = pLevel;
        int i = Mth.floor(pMob.getAttributeValue(Attributes.FOLLOW_RANGE) * 16.0D);
        this.pathFinder = this.createPathFinder(i);
    }

    public void resetMaxVisitedNodesMultiplier() {
        this.maxVisitedNodesMultiplier = 1.0F;
    }

    public void setMaxVisitedNodesMultiplier(float pMultiplier) {
        this.maxVisitedNodesMultiplier = pMultiplier;
    }

    @Nullable
    public BlockPos getTargetPos() {
        return this.targetPos;
    }

    protected abstract PathFinder createPathFinder(int pMaxVisitedNodes);

    public void setSpeedModifier(double pSpeed) {
        this.speedModifier = pSpeed;
    }

    public void recomputePath() {
        if (this.level.getGameTime() - this.timeLastRecompute > 20L) {
            if (this.targetPos != null) {
                this.path = null;
                this.path = this.createPath(this.targetPos, this.reachRange);
                this.timeLastRecompute = this.level.getGameTime();
                this.hasDelayedRecomputation = false;
            }
        } else {
            this.hasDelayedRecomputation = true;
        }
    }

    @Nullable
    public final Path createPath(long pX, int pY, long pZ, int pAccuracy) {
        return this.createPath(new BlockPos(pX, pY, pZ), pAccuracy);
    }

    @Nullable
    public final Path createPath(double pX, double pY, double pZ, int pAccuracy) {
        return this.createPath(new BlockPos(pX, pY, pZ), pAccuracy);
    }

    @Nullable
    public Path createPath(Stream<BlockPos> pTargets, int pAccuracy) {
        return this.createPath(pTargets.collect(Collectors.toSet()), 8, false, pAccuracy);
    }

    @Nullable
    public Path createPath(Set<BlockPos> pPositions, int pDistance) {
        return this.createPath(pPositions, 8, false, pDistance);
    }

    @Nullable
    public Path createPath(BlockPos pPos, int pAccuracy) {
        return this.createPath(ImmutableSet.of(pPos), 8, false, pAccuracy);
    }

    @Nullable
    public Path createPath(BlockPos pPos, int pRegionOffset, int pAccuracy) {
        return this.createPath(ImmutableSet.of(pPos), 8, false, pRegionOffset, (float) pAccuracy);
    }

    @Nullable
    public Path createPath(Entity pEntity, int pAccuracy) {
        return this.createPath(ImmutableSet.of(pEntity.blockPosition()), 16, true, pAccuracy);
    }

    @Nullable
    protected Path createPath(Set<BlockPos> pTargets, int pRegionOffset, boolean pOffsetUpward, int pAccuracy) {
        return this.createPath(pTargets, pRegionOffset, pOffsetUpward, pAccuracy, (float) this.mob.getAttributeValue(Attributes.FOLLOW_RANGE));
    }

    @Nullable
    protected Path createPath(Set<BlockPos> pTargets, int pRegionOffset, boolean pOffsetUpward, int pAccuracy, float pFollowRange) {
        if (pTargets.isEmpty()) {
            return null;
        } else if (this.mob.getY() < (double) this.level.getMinBuildHeight()) {
            return null;
        } else if (!this.canUpdatePath()) {
            return null;
        } else if (this.path != null && !this.path.isDone() && pTargets.contains(this.targetPos)) {
            return this.path;
        } else {
            this.level.getProfiler().push("pathfind");
            BlockPos blockpos = pOffsetUpward ? this.mob.blockPosition().above() : this.mob.blockPosition();
            int i = (int) (pFollowRange + (float) pRegionOffset);
            PathNavigationRegion pathnavigationregion = new PathNavigationRegion(this.level, blockpos.offset(-i, -i, -i), blockpos.offset(i, i, i));
            Path path = this.pathFinder.findPath(pathnavigationregion, this.mob, pTargets, pFollowRange, pAccuracy, this.maxVisitedNodesMultiplier);
            this.level.getProfiler().pop();
            if (path != null && path.getTarget() != null) {
                this.targetPos = path.getTarget();
                this.reachRange = pAccuracy;
                this.resetStuckTimeout();
            }

            return path;
        }
    }

    public boolean moveTo(double pX, double pY, double pZ, double pSpeed) {
        return this.moveTo(this.createPath(pX, pY, pZ, 1), pSpeed);
    }

    public boolean moveTo(BlockPos position, double speedModifier) {
        return this.moveTo(this.createPath(position, 1), speedModifier);
    }

    public boolean moveTo(SectorVec3 position, double speedModifier) {
        return this.moveTo(position.blockPosition(), speedModifier);
    }

    public boolean moveTo(Entity pEntity, double pSpeed) {
        Path path = this.createPath(pEntity, 1);
        return path != null && this.moveTo(path, pSpeed);
    }

    public boolean moveTo(@Nullable Path pPathentity, double pSpeed) {
        if (pPathentity == null) {
            this.path = null;
            return false;
        } else {
            if (!pPathentity.sameAs(this.path)) {
                this.path = pPathentity;
            }

            if (this.isDone()) {
                return false;
            } else {
                this.trimPath();
                if (this.path.getNodeCount() <= 0) {
                    return false;
                } else {
                    this.speedModifier = pSpeed;
                    this.lastStuckCheck = this.tick;
                    this.lastStuckCheckPos = this.getTempMobSectorPos();
                    return true;
                }
            }
        }
    }

    @Nullable
    public Path getPath() {
        return this.path;
    }

    public void tick() {
        ++this.tick;
        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }

        if (!this.isDone()) {
            if (this.canUpdatePath()) {
                this.followThePath();
            } else if (this.path != null && !this.path.isDone()) {
                SectorVec3 next = this.path.getNextExactEntityPos(this.mob);
                SectorVec3 mobPosition = this.getTempMobSectorPos();
                if (mobPosition.y() > next.y() && !this.mob.isOnGround()
                        && mobPosition.blockX() == next.blockX() && mobPosition.blockZ() == next.blockZ()) {
                    this.path.advance();
                }
            }

            DebugPackets.sendPathFindingPacket(this.level, this.mob, this.path, this.maxDistanceToWaypoint);
            if (!this.isDone()) {
                net.minecraft.world.phys.SectorVec3 exactTarget = this.path.getNextExactEntityPos(this.mob);
                this.mob.getMoveControl().setWantedPosition(exactTarget.withY(this.getGroundY(
                                                                    exactTarget.blockPosition(), exactTarget.y())),
                        this.speedModifier);
            }
        }
    }

    protected double getGroundY(Vec3 pVec) {
        return this.getGroundY(new BlockPos(pVec), pVec.y);
    }

    protected double getGroundY(BlockPos blockPos, double targetY) {
        return this.level.getBlockState(blockPos.below()).isAir() ? targetY
                                                                  : WalkNodeEvaluator.getFloorLevel(this.level, blockPos);
    }

    protected void followThePath() {
        this.maxDistanceToWaypoint = this.mob.getBbWidth() > 0.75F ? this.mob.getBbWidth() / 2.0F : 0.75F - this.mob.getBbWidth() / 2.0F;
        Vec3i vec3i = this.path.getNextNodePos();
        Vec3 nodeDelta = SectorVec3.fromBlockAndFraction(vec3i.getX(), 0.5D, (double) vec3i.getY(),
                                           vec3i.getZ(), 0.5D)
                                 .relativeTo(this.mob.sectorPosition());
        double d0 = Math.abs(nodeDelta.x);
        double d1 = Math.abs(nodeDelta.y);
        double d2 = Math.abs(nodeDelta.z);
        boolean flag = d0 < (double) this.maxDistanceToWaypoint && d2 < (double) this.maxDistanceToWaypoint && d1 < 1.0D;
        if (flag || this.mob.canCutCorner(this.path.getNextNode().type) && this.shouldTargetNextNodeInDirection()) {
            this.path.advance();
        }

        this.doStuckDetection();
    }

    private boolean shouldTargetNextNodeInDirection() {
        if (this.path.getNextNodeIndex() + 1 >= this.path.getNodeCount()) {
            return false;
        }
        SectorVec3 currentNode = SectorVec3.fromBlockAndFraction(this.path.getNextNode().x, 0.5D,
                (double) this.path.getNextNode().y, this.path.getNextNode().z, 0.5D);
        SectorVec3 mobPosition = this.getTempMobSectorPos();
        Vec3 mobFromNode = mobPosition.relativeTo(currentNode);
        if (mobFromNode.lengthSqr() >= 4.0D)
            return false;

        SectorVec3 target = this.path.getNextExactEntityPos(this.mob);
        if (this.canMoveDirectlyLocal(mobPosition, target))
            return true;

        Node following = this.path.getNode(this.path.getNextNodeIndex() + 1);
        Vec3 nextDirection = SectorVec3.fromBlockAndFraction(following.x, 0.5D, (double) following.y,
                                               following.z, 0.5D)
                                     .relativeTo(currentNode);
        return nextDirection.dot(mobFromNode) > 0.0D;
    }

    protected boolean canMoveDirectlyLocal(SectorVec3 from, SectorVec3 to) {
        return false;
    }

    protected void doStuckDetection() {
        SectorVec3 mobPosition = this.getTempMobSectorPos();
        if (this.tick - this.lastStuckCheck > 100) {
            if (this.lastStuckCheckPos != null
                    && mobPosition.relativeTo(this.lastStuckCheckPos).lengthSqr() < 2.25D) {
                this.isStuck = true;
                this.stop();
            } else {
                this.isStuck = false;
            }

            this.lastStuckCheck = this.tick;
            this.lastStuckCheckPos = mobPosition;
        }

        if (this.path != null && !this.path.isDone()) {
            Vec3i vec3i = this.path.getNextNodePos();
            if (vec3i.equals(this.timeoutCachedNode)) {
                this.timeoutTimer += Util.getMillis() - this.lastTimeoutCheck;
            } else {
                this.timeoutCachedNode = vec3i;
                SectorVec3 nodePosition = SectorVec3.fromBlockAndFraction(vec3i.getX(), 0.5D,
                        (double) vec3i.getY(), vec3i.getZ(), 0.5D);
                double d0 = nodePosition.relativeTo(mobPosition).length();
                this.timeoutLimit = this.mob.getSpeed() > 0.0F ? d0 / (double) this.mob.getSpeed() * 1000.0D : 0.0D;
            }

            if (this.timeoutLimit > 0.0D && (double) this.timeoutTimer > this.timeoutLimit * 3.0D) {
                this.timeoutPath();
            }

            this.lastTimeoutCheck = Util.getMillis();
        }
    }

    private void timeoutPath() {
        this.resetStuckTimeout();
        this.stop();
    }

    private void resetStuckTimeout() {
        this.timeoutCachedNode = Vec3i.ZERO;
        this.timeoutTimer = 0L;
        this.timeoutLimit = 0.0D;
        this.lastStuckCheckPos = this.getTempMobSectorPos();
        this.isStuck = false;
    }

    public boolean isDone() {
        return this.path == null || this.path.isDone();
    }

    public boolean isInProgress() {
        return !this.isDone();
    }

    public void stop() {
        this.path = null;
    }

    /**
    * Navigation's reference point. Specialized navigators override this to
    * preserve their vanilla surface/body-center semantics in exact coordinates.
    */
    protected SectorVec3 getTempMobSectorPos() {
        return this.mob.sectorPosition();
    }

    protected abstract boolean canUpdatePath();

    protected boolean isInLiquid() {
        return this.mob.isInWaterOrBubble() || this.mob.isInLava();
    }

    protected void trimPath() {
        if (this.path != null) {
            for (int i = 0; i < this.path.getNodeCount(); ++i) {
                Node node = this.path.getNode(i);
                Node node1 = i + 1 < this.path.getNodeCount() ? this.path.getNode(i + 1) : null;
                BlockState blockstate = this.level.getBlockState(new BlockPos(node.x, node.y, node.z));
                if (blockstate.is(BlockTags.CAULDRONS)) {
                    this.path.replaceNode(i, node.cloneAndMove(node.x, node.y + 1, node.z));
                    if (node1 != null && node.y >= node1.y) {
                        this.path.replaceNode(i + 1, node.cloneAndMove(node1.x, node.y + 1, node1.z));
                    }
                }
            }
        }
    }

    protected boolean canMoveDirectly(Vec3 pPosVec31, Vec3 pPosVec32) {
        return false;
    }

    protected static boolean isClearForMovementBetween(Mob p_217804_, Vec3 p_217805_, Vec3 p_217806_) {
        Vec3 vec3 = new Vec3(p_217806_.x, p_217806_.y + (double) p_217804_.getBbHeight() * 0.5D, p_217806_.z);
        return p_217804_.level.clip(new ClipContext(p_217805_, vec3, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p_217804_)).getType() == HitResult.Type.MISS;
    }

    public boolean isStableDestination(BlockPos pPos) {
        BlockPos blockpos = pPos.below();
        return this.level.getBlockState(blockpos).isSolidRender(this.level, blockpos);
    }

    public NodeEvaluator getNodeEvaluator() {
        return this.nodeEvaluator;
    }

    public void setCanFloat(boolean pCanSwim) {
        this.nodeEvaluator.setCanFloat(pCanSwim);
    }

    public boolean canFloat() {
        return this.nodeEvaluator.canFloat();
    }

    public boolean shouldRecomputePath(BlockPos pPos) {
        if (this.hasDelayedRecomputation) {
            return false;
        } else if (this.path != null && !this.path.isDone() && this.path.getNodeCount() != 0) {
            Node node = this.path.getEndNode();
            SectorVec3 nodePosition = SectorVec3.fromBlockAndFraction(node.x, 0.5D, (double) node.y,
                    node.z, 0.5D);
            SectorVec3 midpoint = this.mob.sectorPosition().lerpTo(nodePosition, 0.5D);
            Vec3 delta = SectorVec3.fromBlockAndFraction(pPos.getX(), 0.5D,
                                           (double) pPos.getY() + 0.5D, pPos.getZ(), 0.5D)
                                 .relativeTo(midpoint);
            double range = (double) (this.path.getNodeCount() - this.path.getNextNodeIndex());
            return delta.lengthSqr() < range * range;
        } else {
            return false;
        }
    }

    public float getMaxDistanceToWaypoint() {
        return this.maxDistanceToWaypoint;
    }

    public boolean isStuck() {
        return this.isStuck;
    }
}
