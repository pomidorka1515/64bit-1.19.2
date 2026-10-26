package net.minecraft.world.level.pathfinder;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;

public abstract class NodeEvaluator {
    protected PathNavigationRegion level;
    protected Mob mob;
    protected final Object2ObjectMap<Node.NodeKey, Node> nodes = new Object2ObjectOpenHashMap<>();
    protected int entityWidth;
    protected int entityHeight;
    protected int entityDepth;
    protected boolean canPassDoors;
    protected boolean canOpenDoors;
    protected boolean canFloat;

    public void prepare(PathNavigationRegion pLevel, Mob pMob) {
        this.level = pLevel;
        this.mob = pMob;
        this.nodes.clear();
        this.entityWidth = Mth.floor(pMob.getBbWidth() + 1.0F);
        this.entityHeight = Mth.floor(pMob.getBbHeight() + 1.0F);
        this.entityDepth = Mth.floor(pMob.getBbWidth() + 1.0F);
    }

    public void done() {
        this.level = null;
        this.mob = null;
    }

    @Nullable
    protected Node getNode(BlockPos pPos) {
        return this.getNode(pPos.getX(), pPos.getY(), pPos.getZ());
    }

    @Nullable
    protected Node getNode(Int128 pX, int pY, Int128 pZ) {
        return this.getNodeUnchecked(pX, pY, pZ);
    }

    /**
    * Creates the coordinate node without invoking an evaluator's passability
    * filtering override. Goals are allowed to be blocked; the search still needs
    * a non-null target node so it can find the closest reachable position.
    */
    protected final Node getNodeUnchecked(Int128 x, int y, Int128 z) {
        return this.nodes.computeIfAbsent(new Node.NodeKey(x, y, z), key -> new Node(x, y, z));
    }

    @Nullable
    public abstract Node getStart();

    @Nullable
    public abstract Target getGoal(double pX, double pY, double pZ);

    public Target getGoal(BlockPos position) {
        return new Target(this.getNodeUnchecked(position.getX(), position.getY(), position.getZ()));
    }

    @Nullable
    protected Target getTargetFromNode(@Nullable Node p_230616_) {
        return p_230616_ != null ? new Target(p_230616_) : null;
    }

    public abstract int getNeighbors(Node[] p_77353_, Node p_77354_);

    public abstract BlockPathTypes getBlockPathType(BlockGetter pBlockaccess, Int128 pX, int pY, Int128 pZ, Mob pEntityliving, int pXSize, int pYSize, int pZSize, boolean pCanBreakDoors, boolean pCanEnterDoors);

    public abstract BlockPathTypes getBlockPathType(BlockGetter pLevel, Int128 pX, int pY, Int128 pZ);

    public void setCanPassDoors(boolean pCanEnterDoors) {
        this.canPassDoors = pCanEnterDoors;
    }

    public void setCanOpenDoors(boolean pCanOpenDoors) {
        this.canOpenDoors = pCanOpenDoors;
    }

    public void setCanFloat(boolean pCanSwim) {
        this.canFloat = pCanSwim;
    }

    public boolean canPassDoors() {
        return this.canPassDoors;
    }

    public boolean canOpenDoors() {
        return this.canOpenDoors;
    }

    public boolean canFloat() {
        return this.canFloat;
    }
}
