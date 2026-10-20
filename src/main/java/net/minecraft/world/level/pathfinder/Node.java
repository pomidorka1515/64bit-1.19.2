package net.minecraft.world.level.pathfinder;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

public class Node {
    public final Int128 x;
    public final int y;
    public final Int128 z;
    private final int hash;
    public int heapIdx = -1;
    public float g;
    public float h;
    public float f;
    @Nullable
    public Node cameFrom;
    public boolean closed;
    public float walkedDistance;
    public float costMalus;
    public BlockPathTypes type = BlockPathTypes.BLOCKED;

    public Node(long pX, int pY, long pZ) {
        this(Int128.fromLong(pX), pY, Int128.fromLong(pZ));
    }

    public Node(Int128 pX, int pY, Int128 pZ) {
        this.x = pX;
        this.y = pY;
        this.z = pZ;
        this.hash = createHash(pX, pY, pZ);
    }

    public long xHi() { return this.x.hi(); }
    public long xLo() { return this.x.lo(); }
    public long zHi() { return this.z.hi(); }
    public long zLo() { return this.z.lo(); }

    public Node cloneAndMove(long pX, int pY, long pZ) {
        return this.cloneAndMove(Int128.fromLong(pX), pY, Int128.fromLong(pZ));
    }

    public Node cloneAndMove(Int128 pX, int pY, Int128 pZ) {
        Node node = new Node(pX, pY, pZ);
        node.heapIdx = this.heapIdx;
        node.g = this.g;
        node.h = this.h;
        node.f = this.f;
        node.cameFrom = this.cameFrom;
        node.closed = this.closed;
        node.walkedDistance = this.walkedDistance;
        node.costMalus = this.costMalus;
        node.type = this.type;
        return node;
    }

    public static int createHash(long pX, int pY, long pZ) {
        return createHash(Int128.fromLong(pX), pY, Int128.fromLong(pZ));
    }

    public static int createHash(Int128 pX, int pY, Int128 pZ) {
        int i = pX.hashCode();
        i = 31 * i + pY;
        return 31 * i + pZ.hashCode();
    }

    public float distanceTo(Node pPathpoint) {
        double dx = net.minecraft.world.level.WorldBounds.signedDifference(pPathpoint.x, this.x);
        double dy = (double) pPathpoint.y - this.y;
        double dz = net.minecraft.world.level.WorldBounds.signedDifference(pPathpoint.z, this.z);
        return saturatedCost(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    public float distanceToXZ(Node p_230614_) {
        double dx = net.minecraft.world.level.WorldBounds.signedDifference(p_230614_.x, this.x);
        double dz = net.minecraft.world.level.WorldBounds.signedDifference(p_230614_.z, this.z);
        return saturatedCost(Math.hypot(dx, dz));
    }

    public float distanceTo(BlockPos pPos) {
        double dx = net.minecraft.world.level.WorldBounds.signedDifference(pPos.getX(), this.x);
        double dy = (double) pPos.getY() - this.y;
        double dz = net.minecraft.world.level.WorldBounds.signedDifference(pPos.getZ(), this.z);
        return saturatedCost(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    public float distanceToSqr(Node pPathpoint) {
        double dx = net.minecraft.world.level.WorldBounds.signedDifference(pPathpoint.x, this.x);
        double dy = (double) pPathpoint.y - this.y;
        double dz = net.minecraft.world.level.WorldBounds.signedDifference(pPathpoint.z, this.z);
        return saturatedCost(dx * dx + dy * dy + dz * dz);
    }

    public float distanceToSqr(BlockPos pPos) {
        double dx = net.minecraft.world.level.WorldBounds.signedDifference(pPos.getX(), this.x);
        double dy = (double) pPos.getY() - this.y;
        double dz = net.minecraft.world.level.WorldBounds.signedDifference(pPos.getZ(), this.z);
        return saturatedCost(dx * dx + dy * dy + dz * dz);
    }

    public float distanceManhattan(Node pPathpoint) {
        double dx = net.minecraft.world.level.WorldBounds.distance(pPathpoint.x, this.x);
        double dy = Math.abs((double) pPathpoint.y - this.y);
        double dz = net.minecraft.world.level.WorldBounds.distance(pPathpoint.z, this.z);
        return saturatedCost(dx + dy + dz);
    }

    public float distanceManhattan(BlockPos pPos) {
        double dx = net.minecraft.world.level.WorldBounds.distance(pPos.getX(), this.x);
        double dy = Math.abs((double) pPos.getY() - this.y);
        double dz = net.minecraft.world.level.WorldBounds.distance(pPos.getZ(), this.z);
        return saturatedCost(dx + dy + dz);
    }

    static float saturatedCost(double cost) {
        return (float) Math.min(cost, Float.MAX_VALUE);
    }

    public BlockPos asBlockPos() {
        return new BlockPos(this.x, this.y, this.z);
    }

    public boolean equals(Object pOther) {
        if (!(pOther instanceof Node node)) {
            return false;
        } else {
            return this.hash == node.hash && this.x.equals(node.x) && this.y == node.y && this.z.equals(node.z);
        }
    }

    public int hashCode() {
        return this.hash;
    }

    public boolean inOpenSet() {
        return this.heapIdx >= 0;
    }

    public String toString() {
        return "Node{x=" + this.x + ", y=" + this.y + ", z=" + this.z + "}";
    }

    public void writeToStream(FriendlyByteBuf pBuffer) {
        pBuffer.writeLong(this.x.hi());
        pBuffer.writeLong(this.x.lo());
        pBuffer.writeInt(this.y);
        pBuffer.writeLong(this.z.hi());
        pBuffer.writeLong(this.z.lo());
        pBuffer.writeFloat(this.walkedDistance);
        pBuffer.writeFloat(this.costMalus);
        pBuffer.writeBoolean(this.closed);
        pBuffer.writeInt(this.type.ordinal());
        pBuffer.writeFloat(this.f);
    }

    public static Node createFromStream(FriendlyByteBuf pBuf) {
        Node node = new Node(Int128.of(pBuf.readLong(), pBuf.readLong()), pBuf.readInt(),
                Int128.of(pBuf.readLong(), pBuf.readLong()));
        node.walkedDistance = pBuf.readFloat();
        node.costMalus = pBuf.readFloat();
        node.closed = pBuf.readBoolean();
        node.type = BlockPathTypes.values()[pBuf.readInt()];
        node.f = pBuf.readFloat();
        return node;
    }

    /**
    * Complete node identity for evaluator cache lookups. The former packed
    * 32-bit key discarded high X/Z bits and merged distinct Far Lands nodes.
    */
    static record NodeKey(Int128 x, int y, Int128 z) {
    }
}
